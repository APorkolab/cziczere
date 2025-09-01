package com.cziczere.functions;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.vertexai.VertexAI;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private MockedStatic<FirebaseAuth> firebaseAuthMockedStatic;
    private MockedStatic<FirebaseApp> firebaseAppMockedStatic;
    @Mock
    private FirebaseApp mockFirebaseApp;


    @BeforeEach
    void setUp() throws IOException {
        // Mock all static Firebase methods
        firebaseAuthMockedStatic = mockStatic(FirebaseAuth.class);
        firebaseAuthMockedStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
        firebaseAppMockedStatic = mockStatic(FirebaseApp.class);
        firebaseAppMockedStatic.when(FirebaseApp::getApps).thenReturn(java.util.List.of(mockFirebaseApp));


        generateMemoryPlant = new GenerateMemoryPlant(db, vertexAI);

        responseWriter = new StringWriter();
        BufferedWriter bufferedWriter = new BufferedWriter(responseWriter);
        lenient().when(response.getWriter()).thenReturn(bufferedWriter);
    }

    @AfterEach
    void tearDown() {
        firebaseAuthMockedStatic.close();
        firebaseAppMockedStatic.close();
    }

    private void setupValidAuth() throws FirebaseAuthException {
        when(request.getFirstHeader("Authorization")).thenReturn(Optional.of("Bearer valid-token"));
        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(decodedToken);
        when(decodedToken.getUid()).thenReturn("test-user-id");
    }

    @Test
    void service_shouldReturnSuccess_onValidRequest() throws Exception {
        // Given
        setupValidAuth();
        String inputText = "A beautiful day in the park, feeling nostalgic and happy.";
        String requestJson = new Gson().toJson(new RequestData(inputText, null));
        BufferedReader reader = new BufferedReader(new StringReader(requestJson));
        when(request.getReader()).thenReturn(reader);
        when(db.collection(anyString())).thenReturn(collectionReference);
        when(collectionReference.document()).thenReturn(documentReference);
        when(documentReference.set(any(MemoryData.class))).thenReturn(mockApiFuture);

        // Create a real GenerateMemoryPlant instance, but spy on it to mock specific methods.
        GenerateMemoryPlant spy = spy(generateMemoryPlant);

        // Mock the response from our new Gemini analysis method
        Map<String, Double> emotions = Map.of("nostalgia", 0.8, "happiness", 0.9);
        var geminiResponse = new GenerateMemoryPlant.GeminiResponse("A beautiful park, digital painting", emotions);
        doReturn(geminiResponse).when(spy).generateAnalysisWithGemini(anyString());

        doReturn("data:image/png;base64,dGVzdA==").when(spy).generateImageWithImagen(anyString());
        // Use an ArgumentCaptor to capture the object passed to saveToFirestore
        ArgumentCaptor<MemoryData> memoryDataCaptor = ArgumentCaptor.forClass(MemoryData.class);
        doNothing().when(spy).saveToFirestore(memoryDataCaptor.capture());

        // When
        spy.service(request, response);

        // Then
        verify(response).setStatusCode(200, "OK");

        // Verify the data passed to saveToFirestore
        MemoryData capturedMemoryData = memoryDataCaptor.getValue();
        assertEquals("test-user-id", capturedMemoryData.userId());
        assertEquals(inputText, capturedMemoryData.userText());
        assertEquals("A beautiful park, digital painting", capturedMemoryData.imagePrompt());
        assertEquals("data:image/png;base64,dGVzdA==", capturedMemoryData.imageUrl());
        assertNotNull(capturedMemoryData.emotions());
        assertEquals(2, capturedMemoryData.emotions().size());
        assertEquals(0.8, capturedMemoryData.emotions().get("nostalgia"));

        // Also verify the JSON response sent to the client
        String jsonResponse = responseWriter.toString();
        MemoryData responseData = new Gson().fromJson(jsonResponse, MemoryData.class);
        assertEquals("test-user-id", responseData.userId());
        assertEquals(inputText, responseData.userText());
        assertEquals("data:image/png;base64,dGVzdA==", responseData.imageUrl());
        assertNotNull(responseData.emotions());
        assertEquals(0.9, responseData.emotions().get("happiness"));
    }

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
    void generateAnalysisWithGemini_shouldReturnFallback_onApiError() throws IOException {
        // Given
        // This test calls the method directly, so we don't need the full HttpRequest/HttpResponse setup.
        GenerateMemoryPlant plant = new GenerateMemoryPlant(db, vertexAI);
        GenerateMemoryPlant spy = spy(plant);

        // Mock the model and make it throw an error
        var mockModel = mock(com.google.cloud.vertexai.generativeai.GenerativeModel.class);
        when(mockModel.generateContent(anyString())).thenThrow(new RuntimeException("API call failed!"));

        // Make our factory method return the mock
        doReturn(mockModel).when(spy).getGenerativeModel();

        // When
        var response = spy.generateAnalysisWithGemini("some text");

        // Then
        assertNotNull(response);
        assertTrue(response.imagePrompt().contains("A beautiful digital painting of some text"));
        assertNotNull(response.emotions());
        assertEquals(1, response.emotions().size());
        assertEquals(0.5, response.emotions().get("unknown"));
    }
}
