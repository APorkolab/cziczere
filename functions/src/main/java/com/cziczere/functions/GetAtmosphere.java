package com.cziczere.functions;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
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
import com.google.gson.JsonSyntaxException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GetAtmosphere implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GetAtmosphere.class.getName());
    private static final Gson gson = new Gson();

    private static final String PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id");
    private static final String REGION = System.getenv().getOrDefault("GCP_REGION", "us-central1");

    private final Firestore db;
    private final VertexAI vertexAI;

    // Simple record for the response
    public record AtmosphereData(String weather, String backgroundColor, String musicUrl) {}

    // The MemoryData record is defined in its own file.
    // This local definition is no longer needed.

    static {
        // Static initializer for Firebase Admin SDK
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(PROJECT_ID)
                    .build();
                FirebaseApp.initializeApp(options);
                logger.info("Firebase Admin SDK initialized successfully for GetAtmosphere.");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Firebase Admin SDK initialization failed.", e);
        }
    }

    public GetAtmosphere() throws IOException {
        this.db = FirestoreOptions.getDefaultInstance().getService();
        this.vertexAI = new VertexAI(PROJECT_ID, REGION);
    }

    // Constructor for testing
    GetAtmosphere(Firestore db, VertexAI vertexAI) {
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

                // 1. Fetch recent memories
                List<MemoryData> memories = getRecentMemoriesForUser(userId);
                if (memories.isEmpty()) {
                    // Return a default atmosphere if no recent memories
                    AtmosphereData defaultAtmosphere = new AtmosphereData("Clear", "#87CEEB", "https://storage.googleapis.com/cziczere-music/sunny.mp3"); // Sky Blue
                    writer.write(gson.toJson(defaultAtmosphere));
                    response.setStatusCode(200);
                    return;
                }

                // 2. Generate atmosphere from memories
                AtmosphereData atmosphere = generateAtmosphereWithGemini(memories);

                // 3. Return atmosphere
                response.setStatusCode(200, "OK");
                writer.write(gson.toJson(atmosphere));

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

    List<MemoryData> getRecentMemoriesForUser(String userId) throws ExecutionException, InterruptedException {
        CollectionReference memoriesCollection = db.collection("memories");

        // Calculate timestamp for 7 days ago
        long sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();

        Query query = memoriesCollection
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("timestamp", sevenDaysAgo);

        List<com.google.cloud.firestore.QueryDocumentSnapshot> documents = query.get().get().getDocuments();

        return documents.stream()
                .map(doc -> doc.toObject(com.cziczere.functions.MemoryData.class))
                .collect(Collectors.toList());
    }


    AtmosphereData generateAtmosphereWithGemini(List<com.cziczere.functions.MemoryData> memories) throws IOException {
        String combinedMemories = memories.stream()
                .map(memory -> "Memory: " + memory.userText() + " (Mood: " + memory.mood() + ")")
                .collect(Collectors.joining("\n---\n"));

        GenerativeModel model = new GenerativeModel("gemini-1.5-flash-001", this.vertexAI);
        String systemPrompt = "You are an AI that determines the atmosphere of a digital garden based on a user's recent memories. " +
                "Analyze the overall sentiment and themes from the memories provided. " +
                "Based on your analysis, you must choose ONE weather condition from this list: [Clear, Sunny, Rainy, Snowy, Foggy, Stormy]. " +
                "You must also determine a single HEX color code for the sky that reflects the mood. " +
                "Finally, select the most fitting background music URL from the following list: [https://storage.googleapis.com/cziczere-music/sunny.mp3, https://storage.googleapis.com/cziczere-music/rain.mp3]. " +
                "Your response MUST be a valid JSON object with three keys: 'weather', 'backgroundColor', and 'musicUrl'. " +
                "For example: {\"weather\": \"Sunny\", \"backgroundColor\": \"#87CEEB\", \"musicUrl\": \"https://storage.googleapis.com/cziczere-music/sunny.mp3\"}. " +
                "Do not add any other text or explanation. Your output must be only the JSON object.";

        String fullPrompt = systemPrompt + "\n\nHere are the user's memories:\n" + combinedMemories;

        try {
            logger.info("Generating atmosphere with Gemini.");
            GenerateContentResponse response = model.generateContent(fullPrompt);
            String rawResponse = response.getCandidates(0).getContent().getParts(0).getText();

            // Clean up the response to ensure it's valid JSON
            String jsonResponse = rawResponse.trim().replace("```json", "").replace("```", "").trim();

            logger.info("Generated atmosphere JSON: " + jsonResponse);
            return gson.fromJson(jsonResponse, AtmosphereData.class);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generating or parsing atmosphere with Gemini: " + e.getMessage(), e);
            // Provide a fallback/default atmosphere on error
            return new AtmosphereData("Clear", "#DDDDDD", "https://storage.googleapis.com/cziczere-music/sunny.mp3"); // Light grey as a fallback
        }
    }
}
