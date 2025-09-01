package com.cziczere.functions;

import java.util.Map;

public record MemoryData(
        String userId,
        String userText,
        String imagePrompt,
        String imageUrl,
        long timestamp,
        String type,
        Map<String, Double> emotions
) {
}
