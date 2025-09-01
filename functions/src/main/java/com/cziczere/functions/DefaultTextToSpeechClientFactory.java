package com.cziczere.functions;

import com.google.cloud.texttospeech.v1.TextToSpeechClient;

import java.io.IOException;

public class DefaultTextToSpeechClientFactory implements TextToSpeechClientFactory {
    @Override
    public TextToSpeechClient create() throws IOException {
        return TextToSpeechClient.create();
    }
}
