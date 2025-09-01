package com.cziczere.functions;

import com.google.cloud.texttospeech.v1.TextToSpeechClient;

import java.io.IOException;

public interface TextToSpeechClientFactory {
    TextToSpeechClient create() throws IOException;
}
