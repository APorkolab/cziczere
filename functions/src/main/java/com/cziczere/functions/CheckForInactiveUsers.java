package com.cziczere.functions;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.UserRecord;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CheckForInactiveUsers implements BackgroundFunction<CheckForInactiveUsers.PubSubMessage> {

    private static final Logger logger = Logger.getLogger(CheckForInactiveUsers.class.getName());
    private static final long SEVEN_DAYS_AGO = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
    private static final String REMINDER_TEXT = "It's been a little while since you've planted a memory. Is there a small moment of joy from your day you'd like to add to your garden?";

    private final Firestore db;
    private final FirebaseAuth firebaseAuth;

    // PubSubMessage class to represent the Pub/Sub message payload.
    // This can be customized if the message contains data.
    public static class PubSubMessage {
        String data;
        // other fields can be added here
    }

    public CheckForInactiveUsers() throws IOException {
        this.db = FirestoreOptions.getDefaultInstance().getService();
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setProjectId(System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id"))
                .build();
            FirebaseApp.initializeApp(options);
        }
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    // Constructor for testing
    CheckForInactiveUsers(Firestore db, FirebaseAuth firebaseAuth) {
        this.db = db;
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public void accept(PubSubMessage message, Context context) throws Exception {
        logger.info("Starting inactive user check job.");
        try {
            ListUsersPage page = firebaseAuth.listUsers(null);
            for (ExportedUserRecord user : page.iterateAll()) {
                processUser(user);
            }
            logger.info("Successfully completed inactive user check job.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during inactive user check job", e);
            throw e; // Re-throw to signal function failure
        }
    }

    private void processUser(ExportedUserRecord user) {
        String userId = user.getUid();
        try {
            CollectionReference memoriesCollection = db.collection("memories");
            Query query = memoriesCollection
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1);

            List<QueryDocumentSnapshot> documents = query.get().get().getDocuments();

            if (documents.isEmpty()) {
                // User has never posted a memory.
                logger.info("User " + userId + " has no memories. Creating a welcome/reminder insight.");
                createReminderInsight(userId);
            } else {
                MemoryData lastMemory = documents.get(0).toObject(MemoryData.class);
                if (lastMemory.timestamp() < SEVEN_DAYS_AGO) {
                    logger.info("User " + userId + " is inactive. Last memory was on " + lastMemory.timestamp() + ". Creating reminder.");
                    createReminderInsight(userId);
                } else {
                    logger.info("User " + userId + " is active. Skipping.");
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.log(Level.WARNING, "Failed to process user " + userId, e);
        }
    }

    private void createReminderInsight(String userId) throws ExecutionException, InterruptedException {
        // Check if a reminder already exists for the user to avoid spamming
        Query reminderQuery = db.collection("insights")
                                .whereEqualTo("userId", userId)
                                .whereEqualTo("type", "reminder")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .limit(1);

        List<QueryDocumentSnapshot> existingReminders = reminderQuery.get().get().getDocuments();
        if (!existingReminders.isEmpty()) {
            InsightData lastReminder = existingReminders.get(0).toObject(InsightData.class);
            // If the last reminder was also within the last 7 days, don't send another one.
            if (lastReminder.timestamp() > SEVEN_DAYS_AGO) {
                logger.info("User " + userId + " already has a recent reminder. Skipping.");
                return;
            }
        }

        InsightData reminder = new InsightData(
            userId,
            REMINDER_TEXT,
            System.currentTimeMillis(),
            "reminder",
            null
        );
        db.collection("insights").document().set(reminder).get();
        logger.info("Successfully created reminder insight for user " + userId);
    }
}
