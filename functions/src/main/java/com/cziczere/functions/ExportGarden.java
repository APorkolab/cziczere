package com.cziczere.functions;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.Query;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ExportGarden implements HttpFunction {

    private static final Logger logger = Logger.getLogger(ExportGarden.class.getName());
    private static final Gson gson = new Gson();
    private final Firestore db;

    // A4 at 150 DPI
    private static final int POSTER_WIDTH = 1240;
    private static final int POSTER_HEIGHT = 1754;
    private static final int PADDING = 50;

    public ExportGarden() throws IOException {
        this.db = FirestoreOptions.getDefaultInstance().getService();
    }

    // Constructor for testing
    ExportGarden(Firestore db) {
        this.db = db;
    }

    static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }

    record PosterResponse(String base64Image) {}

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        // This is required for the AWT toolkit to work in a headless environment (like a Cloud Function)
        System.setProperty("java.awt.headless", "true");

        response.appendHeader("Content-Type", "application/json");
        response.appendHeader("Access-Control-Allow-Origin", "*"); // Basic CORS for development

        try {
            String userId = getUserIdFromAuthToken(request);
            List<MemoryData> memories = getMemoriesForUser(userId);

            if (memories.isEmpty()) {
                response.setStatusCode(404, "Not Found");
                response.getWriter().write("{\"error\":\"No memories found to create a poster.\"}");
                return;
            }

            BufferedImage poster = createPoster(memories);
            String base64Image = encodeImageToBase64(poster);

            response.setStatusCode(200, "OK");
            response.getWriter().write(gson.toJson(new PosterResponse(base64Image)));
            response.getWriter().flush();

        } catch (AuthException e) {
            logger.warning("Authentication failed: " + e.getMessage());
            response.setStatusCode(401, "Unauthorized");
            response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Internal Server Error during poster generation", e);
            response.setStatusCode(500, "Internal Server Error.");
            response.getWriter().write("{\"error\":\"An unexpected error occurred while generating the poster.\"}");
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
        Query query = memoriesCollection.whereEqualTo("userId", userId).orderBy("timestamp", Query.Direction.ASCENDING);
        return query.get().get().getDocuments().stream()
                .map(doc -> doc.toObject(MemoryData.class))
                .collect(Collectors.toList());
    }

    BufferedImage createPoster(List<MemoryData> memories) throws IOException {
        BufferedImage posterImage = new BufferedImage(POSTER_WIDTH, POSTER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = posterImage.createGraphics();

        // Set background and quality
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, POSTER_WIDTH, POSTER_HEIGHT);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // Simple grid layout logic
        int numImages = memories.size();
        if (numImages == 0) return posterImage;

        int cols = (int) Math.ceil(Math.sqrt(numImages));
        int rows = (int) Math.ceil((double) numImages / cols);

        int cellWidth = (POSTER_WIDTH - 2 * PADDING) / cols;
        int cellHeight = (POSTER_HEIGHT - 2 * PADDING) / rows;
        int imageSize = Math.min(cellWidth, cellHeight) - PADDING;

        for (int i = 0; i < numImages; i++) {
            int row = i / cols;
            int col = i % cols;

            int x = PADDING + col * cellWidth + (cellWidth - imageSize) / 2;
            int y = PADDING + row * cellHeight + (cellHeight - imageSize) / 2;

            MemoryData memory = memories.get(i);
            try {
                BufferedImage memoryImage = fetchImage(memory.imageUrl());
                if (memoryImage != null) {
                    g2d.drawImage(memoryImage, x, y, imageSize, imageSize, null);
                }
            } catch (IOException e) {
                logger.warning("Could not fetch or draw image for memory: " + memory.userText() + " from " + memory.imageUrl());
            }
        }

        g2d.dispose();
        return posterImage;
    }

    BufferedImage fetchImage(String imageUrl) throws IOException {
        // Handle placeholder for testing or errors
        if (imageUrl == null || imageUrl.startsWith("https://storage.googleapis.com/cziczere-static-assets/placeholder-plant-error.png")) {
            return null;
        }
        // Handle base64 encoded images from Imagen
        if (imageUrl.startsWith("data:image/png;base64,")) {
            byte[] imageBytes = Base64.getDecoder().decode(imageUrl.substring("data:image/png;base64,".length()));
            return ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        }
        // Handle standard URLs
        URL url = new URL(imageUrl);
        try (InputStream is = url.openStream()) {
            return ImageIO.read(is);
        }
    }

    String encodeImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        logger.info("Attempting to write image to byte array stream...");
        boolean success = ImageIO.write(image, "png", baos);
        if (!success) {
            throw new IOException("ImageIO.write failed to encode image.");
        }
        byte[] imageBytes = baos.toByteArray();
        logger.info("Image encoded successfully. Byte array size: " + imageBytes.length);
        if (imageBytes.length == 0) {
            throw new IOException("Encoded image resulted in an empty byte array.");
        }
        return Base64.getEncoder().encodeToString(imageBytes);
    }
}
