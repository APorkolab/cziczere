package com.cziczere.functions;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

public class GenerateMemoryPlant implements HttpFunction {

    // Use a Gson instance that can handle Instant objects, which is part of MemoryData
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(java.time.Instant.class, new InstantAdapter().nullSafe())
            .create();

    private static final String GCP_PROJECT_ID = System.getenv("GCP_PROJECT_ID");
    private static final String GCP_REGION = System.getenv("GCP_REGION");
    private static Firestore firestore;

    // Statically initialize the Firestore client to reuse the connection across function invocations
    static {
        try {
            // Use the project ID from the environment variable for explicit configuration
            FirestoreOptions firestoreOptions = FirestoreOptions.newBuilder()
                    .setProjectId(GCP_PROJECT_ID)
                    .build();
            firestore = firestoreOptions.getService();
        } catch (Exception e) {
            System.err.println("Failed to initialize Firestore: " + e.getMessage());
            // The function can still run, but saving to Firestore will fail.
            // This will be logged in the saveToFirestore method.
        }
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        // 1. Extract user data from the request, now using the RequestData DTO
        RequestData requestData = gson.fromJson(request.getReader(), RequestData.class);
        String userText = (requestData != null && requestData.text() != null) ? requestData.text() : "A default memory about a quiet, peaceful moment.";

        // TODO: Implement actual user ID extraction from Firebase Auth token
        String userId = "test-user-id"; // Placeholder

        // 2. Generate AI Prompt with Gemini
        String imagePrompt = generateImagePromptWithGemini(userText);

        // 3. Generate Image with Imagen
        String imageUrl = generateImageWithImagen(imagePrompt);

        // 4. Create the MemoryData object and save it to the Firestore database
        MemoryData newMemory = new MemoryData(userId, userText, imagePrompt, imageUrl);
        saveToFirestore(newMemory);

        // 5. Send the newly created MemoryData object back to the frontend
        response.setContentType("application/json; charset=" + StandardCharsets.UTF_8.name().toLowerCase());
        response.getWriter().write(gson.toJson(newMemory));
        response.setStatusCode(200);
    }

    private String generateImagePromptWithGemini(String userText) throws IOException {
        try (VertexAI vertexAI = new VertexAI(GCP_PROJECT_ID, GCP_REGION)) {
            GenerativeModel model = new GenerativeModel("gemini-1.5-flash-001", vertexAI);

            String systemPrompt = "You are a creative assistant. Based on the user's text, generate an English, " +
                                  "artistic prompt for an image-generating AI. The prompt should be descriptive, emotional, and visual. " +
                                  "Style: 'digital painting, surreal, magical realism, glowing elements'. " +
                                  "Focus on: main theme, mood, colors. Do not add anything else, only the prompt.";

            String fullPrompt = systemPrompt + "\nUser text: \"" + userText + "\"";

            GenerateContentResponse response = model.generateContent(fullPrompt);
            return com.google.cloud.vertexai.generativeai.ResponseHandler.getText(response).trim();
        }
    }

    private String generateImageWithImagen(String imagePrompt) {
        System.out.println("Generating image for prompt: " + imagePrompt); // Log for verification
        return "https://storage.googleapis.com/cziczere-static-assets/placeholder-plant.png";
    }

    private void saveToFirestore(MemoryData data) throws ExecutionException, InterruptedException {
        if (firestore == null) {
            System.err.println("Firestore client is not initialized. Skipping save.");
            return;
        }
        // Add a new document with a generated ID to the "memories" collection
        ApiFuture<WriteResult> future = firestore.collection("memories").document().set(data);
        // Block on write to ensure data is saved before function exits in a serverless environment
        System.out.println("Firestore update time: " + future.get().getUpdateTime());
    }
}
