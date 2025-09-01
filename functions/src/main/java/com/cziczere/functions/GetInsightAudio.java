package com.cziczere.functions;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.texttospeech.v1.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;

import java.io.BufferedWriter;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GetInsightAudio implements HttpFunction {

    private static final Logger logger = Logger.getLogger(GetInsightAudio.class.getName());
    private static final Gson gson = new Gson();
    private static final String BUCKET_NAME = System.getenv().getOrDefault("GCP_PROJECT_ID", "your-gcp-project-id") + "-insight-audio";


    private final Firestore db;
    private final Storage storage;
    private final FirebaseAuth firebaseAuth;
    private final TextToSpeechClientFactory textToSpeechClientFactory;

    public GetInsightAudio() throws Exception {
        // This constructor will be used by the Functions Framework
        // It should initialize the clients using application default credentials
        this.db = com.google.cloud.firestore.FirestoreOptions.getDefaultInstance().getService();
        this.storage = com.google.cloud.storage.StorageOptions.getDefaultInstance().getService();
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.textToSpeechClientFactory = new DefaultTextToSpeechClientFactory();
    }

    // Constructor for testing
    GetInsightAudio(Firestore db, Storage storage, FirebaseAuth firebaseAuth, TextToSpeechClientFactory textToSpeechClientFactory) {
        this.db = db;
        this.storage = storage;
        this.firebaseAuth = firebaseAuth;
        this.textToSpeechClientFactory = textToSpeechClientFactory;
    }


    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        response.appendHeader("Content-Type", "application/json");
        response.appendHeader("Access-Control-Allow-Origin", "*"); // Basic CORS for development

        if ("OPTIONS".equals(request.getMethod())) {
            response.appendHeader("Access-Control-Allow-Methods", "GET");
            response.appendHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setStatusCode(204);
            return;
        }

        try (BufferedWriter writer = response.getWriter()) {
            // 1. Authenticate user
            getUserIdFromAuthToken(request);

            // 2. Get insightId from request
            Optional<String> insightIdOpt = request.getFirstQueryParameter("insightId");
            if (insightIdOpt.isEmpty()) {
                response.setStatusCode(400, "Bad Request");
                writer.write("{\"error\":\"insightId parameter is missing.\"}");
                return;
            }
            String insightId = insightIdOpt.get();

            // 3. Fetch insight text from Firestore
            DocumentSnapshot insightDoc = db.collection("insights").document(insightId).get().get();
            if (!insightDoc.exists()) {
                response.setStatusCode(404, "Not Found");
                writer.write("{\"error\":\"Insight not found.\"}");
                return;
            }
            String textToSynthesize = insightDoc.getString("text");
            if (textToSynthesize == null || textToSynthesize.isEmpty()) {
                 response.setStatusCode(400, "Bad Request");
                writer.write("{\"error\":\"Insight has no text to synthesize.\"}");
                return;
            }

            // 4. Synthesize speech
            byte[] audioBytes = synthesizeText(textToSynthesize);

            // 5. Save to Cloud Storage
            String blobName = insightId + ".mp3";
            BlobId blobId = BlobId.of(BUCKET_NAME, blobName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("audio/mpeg").build();
            storage.create(blobInfo, audioBytes);

            // 6. Return public URL
            String publicUrl = "https://storage.googleapis.com/" + BUCKET_NAME + "/" + blobName;

            writer.write(gson.toJson(java.util.Collections.singletonMap("audioUrl", publicUrl)));
            response.setStatusCode(200);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Internal Server Error in GetInsightAudio", e);
            response.setStatusCode(500, "Internal Server Error");
            response.getWriter().write("{\"error\":\"An unexpected error occurred.\"}");
        }
    }

    private byte[] synthesizeText(String text) throws Exception {
        // Instantiates a client
        try (TextToSpeechClient textToSpeechClient = textToSpeechClientFactory.create()) {
            // Set the text input to be synthesized
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

            // Build the voice request
            VoiceSelectionParams voice =
                VoiceSelectionParams.newBuilder()
                    .setLanguageCode("en-US")
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                    .setName("en-US-Journey-F") // A calm, pleasant voice
                    .build();

            // Select the type of audio file you want returned
            AudioConfig audioConfig =
                AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();

            // Perform the text-to-speech request
            SynthesizeSpeechResponse response =
                textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

            // Get the audio contents from the response
            ByteString audioContents = response.getAudioContent();
            return audioContents.toByteArray();
        }
    }

    private String getUserIdFromAuthToken(HttpRequest request) throws Exception {
        Optional<String> authHeader = request.getFirstHeader("Authorization");
        if (authHeader.isEmpty() || !authHeader.get().startsWith("Bearer ")) {
            throw new Exception("Authorization header is missing or not Bearer type.");
        }

        String idToken = authHeader.get().substring(7);
        try {
            FirebaseToken decodedToken = this.firebaseAuth.verifyIdToken(idToken);
            return decodedToken.getUid();
        } catch (Exception e) {
            throw new Exception("Invalid Firebase ID token: " + e.getMessage());
        }
    }
}
