package com.cziczere.functions;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class GenerateMemoryPlant implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GenerateMemoryPlant.class.getName());
    private static final Gson gson = new Gson();

    private static final String PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id");
    private static final String REGION = System.getenv().getOrDefault("GCP_REGION", "your-gcp-region");

    private final Firestore db;
    private final VertexAI vertexAI;

    // A record to hold the structured response from Gemini.
    record GeminiResponse(String imagePrompt, Map<String, Double> emotions) {}

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

    // This constructor is used for testing, allowing injection of mocks.
    GenerateMemoryPlant(Firestore db, VertexAI vertexAI) {
        this.db = db;
        this.vertexAI = vertexAI;
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        response.appendHeader("Content-Type", "application/json");

        try (BufferedWriter writer = response.getWriter()) {
            try {
                RequestData requestData = gson.fromJson(request.getReader(), RequestData.class);
                if (requestData == null || requestData.text() == null || requestData.text().isBlank()) {
                    response.setStatusCode(400, "Bad Request: 'text' field is required and cannot be empty.");
                    writer.write("{\"error\":\"'text' field is required and cannot be empty.\"}");
                    return;
                }
                String userText = requestData.text();
                String userId = getUserIdFromAuthToken(request);

                GeminiResponse geminiResponse = generateAnalysisWithGemini(userText);
                String imageUrl = generateImageWithImagen(geminiResponse.imagePrompt());

                MemoryData newMemory = new MemoryData(
                    userId,
                    userText,
                    geminiResponse.imagePrompt(),
                    imageUrl,
                    System.currentTimeMillis(),
                    "memory",
                    geminiResponse.emotions()
                );
                saveToFirestore(newMemory);

                response.setStatusCode(200, "OK");
                writer.write(gson.toJson(newMemory));

            } catch (JsonParseException e) {
                logger.severe("Error parsing JSON request: " + e.getMessage());
                response.setStatusCode(400, "Bad Request: Invalid JSON format.");
                writer.write("{\"error\":\"Invalid JSON format.\"}");
            } catch (Exception e) {
                logger.severe("Internal Server Error: " + e.getMessage());
                response.setStatusCode(500, "Internal Server Error.");
                writer.write("{\"error\":\"An unexpected error occurred.\"}");
            }
        }
    }

    String getUserIdFromAuthToken(HttpRequest request) {
        return "static-user-id-for-testing";
    }

    GeminiResponse generateAnalysisWithGemini(String userText) throws IOException {
        GenerativeModel model = getGenerativeModel();
        String systemPrompt = "You are a creative and analytical assistant. Based on the user's text, perform two tasks: " +
            "1. Generate an English, artistic prompt for an image generation AI. The prompt should be descriptive, emotional, and visual. " +
            "Style: 'digital painting, surreal, magical realism, glowing elements'. " +
            "2. Analyze the text for nuanced emotions. Identify up to 5 key emotions and provide a score from 0.0 to 1.0. " +
            "Your output MUST be a valid JSON object with two keys: 'imagePrompt' (string) and 'emotions' (a map of emotion names to scores). " +
            "For example: {\"imagePrompt\": \"...\", \"emotions\": {\"nostalgia\": 0.8, \"joy\": 0.6}}. " +
            "Do not output anything else, just the raw JSON.";

        String fullPrompt = systemPrompt + "\nUser's text: \"" + userText + "\"";

        try {
            logger.info("Generating analysis with Gemini for text: " + userText);
            GenerateContentResponse response = model.generateContent(fullPrompt);
            String jsonResponse = response.getCandidates(0).getContent().getParts(0).getText()
                .replace("```json", "").replace("```", "").trim();

            logger.info("Generated response from Gemini: " + jsonResponse);
            GeminiResponse geminiResponse = gson.fromJson(jsonResponse, GeminiResponse.class);

            // Ensure we have a valid response, otherwise create a fallback
            if (geminiResponse == null || geminiResponse.imagePrompt == null) {
                 throw new JsonSyntaxException("Parsed Gemini response is null or lacks an image prompt.");
            }
            return geminiResponse;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generating or parsing Gemini response: " + e.getMessage(), e);
            // Fallback in case of any error
            String fallbackPrompt = String.format("A beautiful digital painting of %s, magical realism style.", userText);
            return new GeminiResponse(fallbackPrompt, Collections.singletonMap("unknown", 0.5));
        }
    }

    String generateImageWithImagen(String imagePrompt) {
        logger.info("Generating image with Imagen for prompt: " + imagePrompt);
        String generatedImageUrl = "https://storage.googleapis.com/cziczere-static-assets/placeholder-plant.png";
        logger.info("Generated image URL (placeholder): " + generatedImageUrl);
        return generatedImageUrl;
    }

    void saveToFirestore(MemoryData data) throws ExecutionException, InterruptedException {
        logger.info("Saving memory data to Firestore collection 'memories': " + data);
        db.collection("memories").document().set(data).get();
        logger.info("Successfully saved data to Firestore.");
    }

    /**
     * Factory method for creating a GenerativeModel.
     * This is to allow for easier mocking in tests.
     */
    GenerativeModel getGenerativeModel() throws IOException {
        return new GenerativeModel("gemini-1.5-flash-001", this.vertexAI);
    }
}
