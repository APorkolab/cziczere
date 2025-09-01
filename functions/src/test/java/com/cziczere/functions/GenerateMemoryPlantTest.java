package com.cziczere.functions;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.vertexai.VertexAI;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateMemoryPlantTest {

    @Mock
    private HttpRequest request;
    @Mock
    private HttpResponse response;
    @Mock
    private Firestore db;
    @Mock
    private VertexAI vertexAI;
    @Mock
    private CollectionReference collectionReference;
    @Mock
    private DocumentReference documentReference;
    @Mock
    @SuppressWarnings("rawtypes")
    private ApiFuture<WriteResult> mockApiFuture;

    private GenerateMemoryPlant generateMemoryPlant;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        generateMemoryPlant = new GenerateMemoryPlant(db, vertexAI);

        responseWriter = new StringWriter();
        BufferedWriter bufferedWriter = new BufferedWriter(responseWriter);
        when(response.getWriter()).thenReturn(bufferedWriter);
    }

    @Test
    void service_shouldReturnSuccess_onValidRequest() throws Exception {
        // Given
        String inputText = "A beautiful day in the park";
        String requestJson = new Gson().toJson(new RequestData(inputText));
        BufferedReader reader = new BufferedReader(new StringReader(requestJson));
        when(request.getReader()).thenReturn(reader);
        when(db.collection(anyString())).thenReturn(collectionReference);
        when(collectionReference.document()).thenReturn(documentReference);
        when(documentReference.set(any(MemoryData.class))).thenReturn(mockApiFuture);

        GenerateMemoryPlant spy = spy(generateMemoryPlant);
        doReturn("A beautiful day in the park, digital painting").when(spy).generateImagePromptWithGemini(anyString());
        doReturn("http://placeholder.com/image.png").when(spy).generateImageWithImagen(anyString());

        // When
        spy.service(request, response);

        // Then
        verify(response).setStatusCode(200, "OK");
        String jsonResponse = responseWriter.toString();
        assertTrue(jsonResponse.contains(inputText));
        assertTrue(jsonResponse.contains("A beautiful day in the park, digital painting"));

        MemoryData responseData = new Gson().fromJson(jsonResponse, MemoryData.class);
        assertNotNull(responseData.userId());
        assertNotNull(responseData.imageUrl());
        assertTrue(responseData.imageUrl().contains("http://placeholder.com/image.png"));
    }

    @Test
    void service_shouldReturnBadRequest_whenBodyIsInvalid() throws Exception {
        // Given
        String invalidJson = "{\"text\":}";
        BufferedReader reader = new BufferedReader(new StringReader(invalidJson));
        when(request.getReader()).thenReturn(reader);

        // When
        generateMemoryPlant.service(request, response);

        // Then
        verify(response).setStatusCode(400, "Bad Request: Invalid JSON format.");
        assertTrue(responseWriter.toString().contains("Invalid JSON format."));
    }

    @Test
    void service_shouldReturnBadRequest_whenTextFieldIsMissing() throws Exception {
        // Given
        String jsonWithoutText = "{\"other_field\":\"some_value\"}";
        BufferedReader reader = new BufferedReader(new StringReader(jsonWithoutText));
        when(request.getReader()).thenReturn(reader);

        // When
        generateMemoryPlant.service(request, response);

        // Then
        verify(response).setStatusCode(400, "Bad Request: 'text' field is required and cannot be empty.");
        assertTrue(responseWriter.toString().contains("'text' field is required"));
    }
}
