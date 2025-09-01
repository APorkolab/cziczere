package com.cziczere.functions;

public record MemoryData(
        String userId,
        String userText,
        String imagePrompt,
        String imageUrl,
        long timestamp,
        String type
) {
}
