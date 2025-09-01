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
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonSyntaxException;
import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerateMemoryPlant implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GenerateMemoryPlant.class.getName());
    private static final Gson gson = new Gson();

    private static final String PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id");
    private static final String REGION = System.getenv().getOrDefault("GCP_REGION", "your-gcp-region");

    private final Firestore db;
    private final VertexAI vertexAI;
    private final Storage storage;

    private static final String GCS_BUCKET_NAME = System.getenv().getOrDefault("GCS_BUCKET_NAME", "your-gcs-bucket-name");

    // A record to hold the structured response from Gemini.
    record GeminiResponse(String imagePrompt, Map<String, Double> emotions) {}

    // Static initializer for Firebase Admin SDK
    static {
        try {
            // Check if the default app is already initialized
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
            // Use Level.SEVERE for logging exceptions
            logger.log(Level.SEVERE, "Firebase Admin SDK initialization failed.", e);
        }
    }

    public GenerateMemoryPlant() throws IOException {
        this.db = FirestoreOptions.getDefaultInstance().getService();
        this.vertexAI = new VertexAI(PROJECT_ID, REGION);
        this.storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
    }

    // This constructor is used for testing, allowing injection of mocks.
    GenerateMemoryPlant(Firestore db, VertexAI vertexAI, Storage storage) {
        this.db = db;
        this.vertexAI = vertexAI;
        this.storage = storage;
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
                // Authenticate user first
                String userId = getUserIdFromAuthToken(request);

                // Then process the request
                RequestData requestData = gson.fromJson(request.getReader(), RequestData.class);
                if (requestData == null || requestData.text() == null || requestData.text().isBlank()) {
                    response.setStatusCode(400, "Bad Request: 'text' field is required and cannot be empty.");
                    writer.write("{\"error\":\"'text' field is required and cannot be empty.\"}");
                    return;
                }
                String userText = requestData.text();

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
            String responseText = response.getCandidates(0).getContent().getParts(0).getText();

            // Use regex to find the JSON block, making it more robust
            Pattern pattern = Pattern.compile("(?s)\\{.*\\}");
            Matcher matcher = pattern.matcher(responseText);

            if (matcher.find()) {
                String jsonResponse = matcher.group(0);
                logger.info("Extracted JSON response from Gemini: " + jsonResponse);
                GeminiResponse geminiResponse = gson.fromJson(jsonResponse, GeminiResponse.class);

                if (geminiResponse != null && geminiResponse.imagePrompt() != null) {
                    return geminiResponse;
                }
            }

            logger.warning("Failed to find or parse a valid JSON object from Gemini response. Using fallback.");
            throw new Exception("No valid JSON found in Gemini response");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generating or parsing Gemini response: " + e.getMessage(), e);
            // Fallback in case of any error
            String fallbackPrompt = String.format("A beautiful digital painting of %s, magical realism style.", userText);
            return new GeminiResponse(fallbackPrompt, Collections.singletonMap("unknown", 0.5));
        }
    }

    String generateImageWithImagen(String imagePrompt) throws IOException {
        String endpoint = String.format("%s-aiplatform.googleapis.com:443", REGION);
        PredictionServiceSettings predictionServiceSettings =
            PredictionServiceSettings.newBuilder().setEndpoint(endpoint).build();

        try (PredictionServiceClient predictionServiceClient =
            PredictionServiceClient.create(predictionServiceSettings)) {
            final EndpointName endpointName =
                EndpointName.of(PROJECT_ID, REGION, "imagegeneration@006");

            com.google.protobuf.Value.Builder instanceBuilder = com.google.protobuf.Value.newBuilder();
            JsonFormat.parser().merge("{\"prompt\": \"" + imagePrompt + "\"}", instanceBuilder);
            List<com.google.protobuf.Value> instances = new ArrayList<>();
            instances.add(instanceBuilder.build());

            com.google.protobuf.Value.Builder parametersBuilder = com.google.protobuf.Value.newBuilder();
            JsonFormat.parser().merge("{\"sampleCount\": 1}", parametersBuilder);

            PredictResponse predictResponse =
                predictionServiceClient.predict(endpointName, instances, parametersBuilder.build());

            String base64Image = predictResponse.getPredictions(0).getStructValue().getFieldsMap().get("bytesBase64Encoded").getStringValue();
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);

            String blobName = UUID.randomUUID().toString() + ".png";
            BlobId blobId = BlobId.of(GCS_BUCKET_NAME, blobName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/png").build();
            storage.create(blobInfo, imageBytes);

            return String.format("https://storage.googleapis.com/%s/%s", GCS_BUCKET_NAME, blobName);
        }
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
