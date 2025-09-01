package com.cziczere.functions;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.Query;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AnalyzeMemories implements HttpFunction {

    private static final Logger logger = Logger.getLogger(AnalyzeMemories.class.getName());
    private static final Gson gson = new Gson();

    private static final String PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id");
    private static final String REGION = System.getenv().getOrDefault("GCP_REGION", "us-central1");

    private final Firestore db;
    private final VertexAI vertexAI;

    static {
        // Firebase Admin SDK initialization
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(PROJECT_ID)
                    .build();
                FirebaseApp.initializeApp(options);
                logger.info("Firebase Admin SDK initialized successfully.");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Firebase Admin SDK initialization failed.", e);
        }
    }

    public AnalyzeMemories() throws IOException {
        this.db = FirestoreOptions.getDefaultInstance().getService();
        this.vertexAI = new VertexAI(PROJECT_ID, REGION);
    }

    // Constructor for testing
    AnalyzeMemories(Firestore db, VertexAI vertexAI) {
        this.db = db;
        this.vertexAI = vertexAI;
    }

    // Custom exception for auth errors
    static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        response.appendHeader("Content-Type", "application/json");

        try (BufferedWriter writer = response.getWriter()) {
            try {
                String userId = getUserIdFromAuthToken(request);

                // 1. Fetch memories
                List<MemoryData> memories = getMemoriesForUser(userId);
                if (memories.isEmpty()) {
                    response.setStatusCode(404, "Not Found");
                    writer.write("{\"message\":\"No memories found to analyze.\"}");
                    return;
                }

                // 2. Generate insight
                String insightText = generateInsightWithGemini(memories);

                // 3. Save insight
                InsightData newInsight = new InsightData(userId, insightText, System.currentTimeMillis());
                saveInsightToFirestore(newInsight);

                // 4. Return insight
                response.setStatusCode(200, "OK");
                writer.write(gson.toJson(newInsight));

            } catch (AuthException e) {
                logger.warning("Authentication failed: " + e.getMessage());
                response.setStatusCode(401, "Unauthorized");
                writer.write("{\"error\":\"" + e.getMessage() + "\"}");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Internal Server Error", e);
                response.setStatusCode(500, "Internal Server Error.");
                writer.write("{\"error\":\"An unexpected error occurred.\"}");
            }
        }
    }

    String getUserIdFromAuthToken(HttpRequest request) throws AuthException {
        Optional<String> authHeader = request.getFirstHeader("Authorization");
        if (authHeader.isEmpty() || !authHeader.get().startsWith("Bearer ")) {
            throw new AuthException("Authorization header is missing or not Bearer type.");
        }

        String idToken = authHeader.get().substring(7);
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            return decodedToken.getUid();
        } catch (FirebaseAuthException e) {
            throw new AuthException("Invalid Firebase ID token: " + e.getMessage());
        }
    }

    List<MemoryData> getMemoriesForUser(String userId) throws ExecutionException, InterruptedException {
        CollectionReference memoriesCollection = db.collection("memories");
        Query query = memoriesCollection.whereEqualTo("userId", userId);
        List<com.google.cloud.firestore.QueryDocumentSnapshot> documents = query.get().get().getDocuments();

        return documents.stream()
                .map(doc -> {
                    try {
                        return doc.toObject(MemoryData.class);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to deserialize memory document: " + doc.getId(), e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }


    String generateInsightWithGemini(List<MemoryData> memories) throws IOException {
        String combinedMemories = memories.stream()
                .map(MemoryData::userText)
                .collect(Collectors.joining("\n---\n"));

        GenerativeModel model = new GenerativeModel("gemini-1.5-flash-001", this.vertexAI);
        String systemPrompt = "You are the Gardener's Assistant. Your role is to help users reflect on their memories. " +
                "You will be given a list of a user's memories, separated by '---'. " +
                "Your task is to identify a recurring positive theme, pattern, or source of joy in these memories. " +
                "Synthesize this observation into a single, gentle, and insightful sentence. " +
                "Start your response with 'I've noticed that...' or a similar gentle opening. " +
                "Address the user directly in your reflection. " +
                "Do not be generic; base your insight directly on the content provided. " +
                "For example: 'I've noticed that walks in nature seem to be a consistent source of peace for you.' " +
                "or 'It seems that spending time with your family brings you a great deal of happiness.' " +
                "Output only the single insightful sentence and nothing else.";

        String fullPrompt = systemPrompt + "\n\nHere are the user's memories:\n" + combinedMemories;

        try {
            logger.info("Generating insight with Gemini for user.");
            GenerateContentResponse response = model.generateContent(fullPrompt);
            String generatedInsight = response.getCandidates(0).getContent().getParts(0).getText();
            logger.info("Generated insight: " + generatedInsight);
            return generatedInsight.trim();
        } catch (Exception e) {
            logger.severe("Error generating insight with Gemini: " + e.getMessage());
            // Provide a fallback response
            return "There was an error analyzing your memories, but I'm sure your garden is growing beautifully.";
        }
    }

    void saveInsightToFirestore(InsightData data) throws ExecutionException, InterruptedException {
        logger.info("Saving insight data to Firestore collection 'insights': " + data);
        db.collection("insights").document().set(data).get();
        logger.info("Successfully saved insight to Firestore.");
    }
}
