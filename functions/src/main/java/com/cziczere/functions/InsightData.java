package com.cziczere.functions;

import javax.annotation.Nullable;

public record InsightData(
        String userId,
        String text,
        long timestamp,
        String type,
        @Nullable String collageUrl
) {
    // Overloaded constructor for convenience when no collage is present.
    public InsightData(String userId, String text, long timestamp, String type) {
        this(userId, text, timestamp, type, null);
    }
}
