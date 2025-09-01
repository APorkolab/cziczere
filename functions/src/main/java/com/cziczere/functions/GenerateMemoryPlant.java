package com.cziczere.functions;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
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
import com.google.gson.JsonParseException;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;

public class GenerateMemoryPlant implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GenerateMemoryPlant.class.getName());
    private static final Gson gson = new Gson();

    private static final String PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id");
    private static final String REGION = System.getenv().getOrDefault("GCP_REGION", "us-central1");

    private final Firestore db;
    private final VertexAI vertexAI;

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

    public GenerateMemoryPlant() throws IOException {
        this.db = FirestoreOptions.getDefaultInstance().getService();
        this.vertexAI = new VertexAI(PROJECT_ID, REGION);
    }

    GenerateMemoryPlant(Firestore db, VertexAI vertexAI) {
        this.db = db;
        this.vertexAI = vertexAI;
    }

    private static class AuthException extends Exception {
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

                RequestData requestData = gson.fromJson(request.getReader(), RequestData.class);
                if (requestData == null || requestData.text() == null || requestData.text().isBlank()) {
                    response.setStatusCode(400, "Bad Request: 'text' field is required and cannot be empty.");
                    writer.write("{\"error\":\"'text' field is required and cannot be empty.\"}");
                    return;
                }
                String userText = requestData.text();

                String imagePrompt = generateImagePromptWithGemini(userText);
                String imageUrl = generateImageWithImagen(imagePrompt);

                MemoryData newMemory = new MemoryData(userId, userText, imagePrompt, imageUrl, System.currentTimeMillis(), "memory");
                saveToFirestore(newMemory);

                response.setStatusCode(200, "OK");
                writer.write(gson.toJson(newMemory));

            } catch (AuthException e) {
                logger.warning("Authentication failed: " + e.getMessage());
                response.setStatusCode(401, "Unauthorized");
                writer.write("{\"error\":\"" + e.getMessage() + "\"}");
            } catch (JsonParseException e) {
                logger.severe("Error parsing JSON request: " + e.getMessage());
                response.setStatusCode(400, "Bad Request: Invalid JSON format.");
                writer.write("{\"error\":\"Invalid JSON format.\"}");
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

    String generateImagePromptWithGemini(String userText) throws IOException {
        GenerativeModel model = new GenerativeModel("gemini-1.5-flash-001", this.vertexAI);
        String systemPrompt = "You are a creative assistant. Based on the user's text, generate an English, " +
                "artistic prompt for an image generation AI. The prompt should be descriptive, emotional, and visual. " +
                "Style: 'digital painting, surreal, magical realism, glowing elements'. " +
                "Focus on: main theme, mood, colors. Output only the prompt and nothing else.";
        String fullPrompt = systemPrompt + "\nUser's text: \"" + userText + "\"";

        try {
            logger.info("Generating prompt with Gemini for text: " + userText);
            GenerateContentResponse response = model.generateContent(fullPrompt);
            String generatedPrompt = response.getCandidates(0).getContent().getParts(0).getText();
            logger.info("Generated prompt: " + generatedPrompt);
            return generatedPrompt.trim();
        } catch (Exception e) {
            logger.severe("Error generating prompt with Gemini: " + e.getMessage());
            return String.format("A beautiful digital painting of %s, magical realism style.", userText);
        }
    }

    String generateImageWithImagen(String imagePrompt) throws IOException {
        // This implementation is based on the official Google Cloud documentation.
        // It is difficult to unit test directly without a major refactoring
        // to inject the PredictionServiceClient. For now, we trust the logic is correct
        // and stub this method in our tests to verify the overall orchestration.
        String endpoint = String.format("%s-aiplatform.googleapis.com:443", REGION);
        PredictionServiceSettings settings = PredictionServiceSettings.newBuilder().setEndpoint(endpoint).build();

        try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {
            EndpointName endpointName = EndpointName.ofProjectLocationPublisherModelName(PROJECT_ID, REGION, "google", "imagegeneration@006");

            String instanceJson = String.format("{\"prompt\": \"%s\"}", imagePrompt);
            Value.Builder instanceBuilder = Value.newBuilder();
            JsonFormat.parser().merge(instanceJson, instanceBuilder);

            PredictResponse response = client.predict(endpointName, List.of(instanceBuilder.build()), null);

            String base64Image = response.getPredictions(0).getStructValue().getFieldsMap().get("bytesBase64Encoded").getStringValue();

            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generating image with Imagen, returning placeholder.", e);
            return "https://storage.googleapis.com/cziczere-static-assets/placeholder-plant-error.png";
        }
    }

    void saveToFirestore(MemoryData data) throws ExecutionException, InterruptedException {
        logger.info("Saving memory data to Firestore collection 'memories': " + data);
        db.collection("memories").document().set(data).get();
        logger.info("Successfully saved data to Firestore.");
    }
}
