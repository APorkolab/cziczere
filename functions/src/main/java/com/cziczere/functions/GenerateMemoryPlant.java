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

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class GenerateMemoryPlant implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GenerateMemoryPlant.class.getName());
    private static final Gson gson = new Gson();

    private static final String PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id");
    private static final String REGION = System.getenv().getOrDefault("GCP_REGION", "your-gcp-region");

    private final Firestore db;
    private final VertexAI vertexAI;

    // This constructor is used by the Cloud Functions framework.
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

                String imagePrompt = generateImagePromptWithGemini(userText);
                String imageUrl = generateImageWithImagen(imagePrompt);

                MemoryData newMemory = new MemoryData(userId, userText, imagePrompt, imageUrl, System.currentTimeMillis());
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
}
