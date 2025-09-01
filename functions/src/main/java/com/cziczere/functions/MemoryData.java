package com.cziczere.functions;

// Using a Java Record for an immutable data carrier.
// Firestore can serialize public records automatically.
public record MemoryData(
    String userId,
    String userText,
    String imagePrompt,
    String imageUrl,
    java.time.Instant createdAt
) {
    // No-arg constructor for Firestore deserialization if needed, though for saving it's not strictly necessary.
    public MemoryData() {
        this(null, null, null, null, null);
    }

    public MemoryData(String userId, String userText, String imagePrompt, String imageUrl) {
        this(userId, userText, imagePrompt, imageUrl, java.time.Instant.now());
    }
}
