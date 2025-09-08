package com.cziczere.functions;

import com.google.auth.oauth2.GoogleCredentials;
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
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cloud Function that provides AI-powered poetic rephrasing of user memories
 * Based on the Creative Assistant feature specification.
 */
public class PoeticRephrasing implements HttpFunction {

    private static final Logger logger = Logger.getLogger(PoeticRephrasing.class.getName());
    private static final Gson gson = new Gson();

    private static final String PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id");
    private static final String REGION = System.getenv().getOrDefault("GCP_REGION", "us-central1");

    private final VertexAI vertexAI;
    private final FirebaseAuth firebaseAuth;

    // Request/Response data classes
    record RephrasingRequest(String originalText) {}
    record RephrasingResponse(String poeticVersion, String suggestion) {}

    // Static initializer for Firebase Admin SDK
    static {
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

    public PoeticRephrasing() throws IOException {
        this.vertexAI = new VertexAI(PROJECT_ID, REGION);
        this.firebaseAuth = FirebaseAuth.getInstance();
    }

    // Constructor for testing
    PoeticRephrasing(VertexAI vertexAI, FirebaseAuth firebaseAuth) {
        this.vertexAI = vertexAI;
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        response.appendHeader("Content-Type", "application/json");
        response.appendHeader("Access-Control-Allow-Origin", "*");
        response.appendHeader("Access-Control-Allow-Methods", "POST");
        response.appendHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");

        if ("OPTIONS".equals(request.getMethod())) {
            response.setStatusCode(200);
            return;
        }

        try (BufferedWriter writer = response.getWriter()) {
            try {
                // Authenticate user
                String userId = getUserIdFromAuthToken(request);

                // Parse request
                RephrasingRequest requestData = gson.fromJson(request.getReader(), RephrasingRequest.class);
                if (requestData == null || requestData.originalText() == null || requestData.originalText().isBlank()) {
                    response.setStatusCode(400, "Bad Request");
                    writer.write("{\"error\":\"Original text is required.\"}");
                    return;
                }

                // Generate poetic rephrasing
                RephrasingResponse result = generatePoeticRephrasing(requestData.originalText());

                response.setStatusCode(200, "OK");
                writer.write(gson.toJson(result));

            } catch (AuthException e) {
                logger.warning("Authentication failed: " + e.getMessage());
                response.setStatusCode(401, "Unauthorized");
                writer.write("{\"error\":\"" + e.getMessage() + "\"}");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Internal Server Error", e);
                response.setStatusCode(500, "Internal Server Error");
                writer.write("{\"error\":\"An unexpected error occurred.\"}");
            }
        }
    }

    private String getUserIdFromAuthToken(HttpRequest request) throws AuthException {
        Optional<String> authHeader = request.getFirstHeader("Authorization");
        if (authHeader.isEmpty() || !authHeader.get().startsWith("Bearer ")) {
            throw new AuthException("Authorization header is missing or not Bearer type.");
        }

        String idToken = authHeader.get().substring(7);
        try {
            FirebaseToken decodedToken = this.firebaseAuth.verifyIdToken(idToken);
            return decodedToken.getUid();
        } catch (FirebaseAuthException e) {
            throw new AuthException("Invalid Firebase ID token: " + e.getMessage());
        }
    }

    private RephrasingResponse generatePoeticRephrasing(String originalText) throws IOException {
        GenerativeModel model = new GenerativeModel("gemini-1.5-flash-001", vertexAI);
        
        String systemPrompt = "You are a creative writing assistant specializing in transforming everyday memories into beautiful, poetic expressions. " +
            "Your task is to take the user's memory and create a more artistic, lyrical version that captures the same essence but with enhanced imagery and emotion. " +
            "Keep the core meaning intact but elevate the language. " +
            "Respond with a JSON object containing 'poeticVersion' (the beautifully rephrased memory) and 'suggestion' (a brief explanation of what you enhanced). " +
            "Example input: 'I walked my dog in the park this morning.' " +
            "Example output: {\"poeticVersion\": \"This morning, my faithful companion and I wandered through nature's embrace, our footsteps creating a gentle rhythm on the dew-kissed pathways.\", \"suggestion\": \"I transformed your simple walk into a more vivid scene with sensory details and emotional connection.\"}";

        String fullPrompt = systemPrompt + "\n\nOriginal memory: \"" + originalText + "\"";

        try {
            logger.info("Generating poetic rephrasing with Gemini");
            GenerateContentResponse response = model.generateContent(fullPrompt);
            String responseText = response.getCandidates(0).getContent().getParts(0).getText();

            // Clean up the response to extract JSON
            String jsonResponse = responseText.trim();
            if (!jsonResponse.startsWith("{")) {
                // Try to extract JSON from response
                int jsonStart = jsonResponse.indexOf("{");
                int jsonEnd = jsonResponse.lastIndexOf("}") + 1;
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    jsonResponse = jsonResponse.substring(jsonStart, jsonEnd);
                }
            }

            logger.info("Generated poetic rephrasing response: " + jsonResponse);
            return gson.fromJson(jsonResponse, RephrasingResponse.class);

        } catch (Exception e) {
            logger.severe("Error generating poetic rephrasing: " + e.getMessage());
            // Fallback response
            return new RephrasingResponse(
                "Your memory sparkles with its own unique beauty, waiting to unfold in the garden of your heart.",
                "I encountered an issue, but your original memory is already meaningful as it is."
            );
        }
    }

    // Custom exception for auth errors
    static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }
}
