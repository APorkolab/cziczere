package com.cziczere.functions;

// This record models the incoming JSON request body, e.g., {"text": "some memory"}
public record RequestData(String text) {
}
