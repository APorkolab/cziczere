package com.cziczere.functions;

// The text field is not used by all functions, so we make it optional.
// The analysisType field will be used by AnalyzeMemories.
public record RequestData(String text, String analysisType) {
}
