package com.cziczere.functions;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.functions.CloudEventsFunction;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SendReminderNotifications implements CloudEventsFunction {
    private static final Logger logger = Logger.getLogger(SendReminderNotifications.class.getName());

    private final Firestore db;
    private final FirebaseMessaging fcm;

    public SendReminderNotifications() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder().setProjectId(System.getenv("GCP_PROJECT_ID")).build();
            FirebaseApp.initializeApp(options);
        }
        this.db = com.google.cloud.firestore.FirestoreOptions.getDefaultInstance().getService();
        this.fcm = FirebaseMessaging.getInstance();
    }

    @Override
    public void accept(CloudEvent event) {
        try {
            List<QueryDocumentSnapshot> users = db.collection("users").get().get().getDocuments();
            long sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();

            for (QueryDocumentSnapshot user : users) {
                String userId = user.getId();
                long lastActive = user.contains("lastActive") ? user.getLong("lastActive") : 0;

                if (lastActive < sevenDaysAgo) {
                    sendReminder(userId);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending reminder notifications", e);
        }
    }

    private void sendReminder(String userId) {
        try {
            com.google.cloud.firestore.DocumentSnapshot tokenDoc = db.collection("fcm_tokens").document(userId).get().get();
            if (tokenDoc.exists() && tokenDoc.contains("token")) {
                String token = tokenDoc.getString("token");
                Message message = Message.builder()
                    .setNotification(Notification.builder()
                        .setTitle("A gentle reminder from your garden")
                        .setBody("It's been a while since you've tended to your memories. Your garden misses you!")
                        .build())
                    .setToken(token)
                    .build();
                fcm.send(message);
                logger.info("Sent reminder to user: " + userId);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to send reminder to user: " + userId, e);
        }
    }
}
