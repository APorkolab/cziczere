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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @Mock
    private FirebaseAuth firebaseAuth;
    @Mock
    private FirebaseToken decodedToken;

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
        when(response.getWriter()).thenReturn(bufferedWriter);
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
        String inputText = "A beautiful day in the park";
        String requestJson = new Gson().toJson(new RequestData(inputText));
        BufferedReader reader = new BufferedReader(new StringReader(requestJson));
        when(request.getReader()).thenReturn(reader);

        GenerateMemoryPlant spy = spy(generateMemoryPlant);
        doReturn("A beautiful day in the park, digital painting").when(spy).generateImagePromptWithGemini(anyString());
        doReturn("data:image/png;base64,dGVzdA==").when(spy).generateImageWithImagen(anyString());
        doNothing().when(spy).saveToFirestore(any(MemoryData.class));

        // When
        spy.service(request, response);

        // Then
        verify(response).setStatusCode(200, "OK");
        String jsonResponse = responseWriter.toString();

        Gson gson = new Gson();
        MemoryData responseData = gson.fromJson(jsonResponse, MemoryData.class);

        assertEquals("test-user-id", responseData.userId());
        assertEquals(inputText, responseData.userText());
        assertEquals("data:image/png;base64,dGVzdA==", responseData.imageUrl());
    }

    @Test
    void service_shouldReturnUnauthorized_whenAuthHeaderIsMissing() throws Exception {
        // Given
        when(request.getFirstHeader("Authorization")).thenReturn(Optional.empty());

        // When
        generateMemoryPlant.service(request, response);

        // Then
        verify(response).setStatusCode(401, "Unauthorized");
        assertTrue(responseWriter.toString().contains("Authorization header is missing"));
    }

    @Test
    void service_shouldReturnUnauthorized_whenTokenIsInvalid() throws Exception {
        // Given
        when(request.getFirstHeader("Authorization")).thenReturn(Optional.of("Bearer invalid-token"));

        FirebaseAuthException mockException = mock(FirebaseAuthException.class);
        when(mockException.getMessage()).thenReturn("The token is invalid");
        when(firebaseAuth.verifyIdToken("invalid-token")).thenThrow(mockException);

        // When
        generateMemoryPlant.service(request, response);

        // Then
        verify(response).setStatusCode(401, "Unauthorized");
        assertTrue(responseWriter.toString().contains("Invalid Firebase ID token"));
    }

    @Test
    void service_shouldReturnBadRequest_whenBodyIsInvalid() throws Exception {
        // Given
        setupValidAuth();
        String invalidJson = "{\"text\":}";
        BufferedReader reader = new BufferedReader(new StringReader(invalidJson));
        when(request.getReader()).thenReturn(reader);

        // When
        generateMemoryPlant.service(request, response);

        // Then
        verify(response).setStatusCode(400, "Bad Request: Invalid JSON format.");
        assertTrue(responseWriter.toString().contains("Invalid JSON format."));
    }
}
