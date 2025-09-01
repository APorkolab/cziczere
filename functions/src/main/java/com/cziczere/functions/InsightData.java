package com.cziczere.functions;

public record InsightData(
        String userId,
        String text,
        long timestamp,
        String type
) {
}
