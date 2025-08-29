package com.cziczere.functions;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.vertexai.VertexAI;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetAtmosphereTest {

    @Mock
    private Firestore db;
    @Mock
    private VertexAI vertexAI;
    @Mock
    private HttpRequest request;
    @Mock
    private HttpResponse response;
    @Mock
    private FirebaseApp mockFirebaseApp;

    private GetAtmosphere getAtmosphere;
    private MockedStatic<FirebaseApp> firebaseAppMockedStatic;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        firebaseAppMockedStatic = mockStatic(FirebaseApp.class);
        firebaseAppMockedStatic.when(FirebaseApp::getApps).thenReturn(java.util.List.of(mockFirebaseApp));

        getAtmosphere = spy(new GetAtmosphere(db, vertexAI));

        responseWriter = new StringWriter();
        BufferedWriter writer = new BufferedWriter(responseWriter);
        when(response.getWriter()).thenReturn(writer);
    }

    @AfterEach
    void tearDown() {
        firebaseAppMockedStatic.close();
    }

    @Test
    void service_shouldReturnAtmosphere_whenMemoriesExist() throws Exception {
        String testUserId = "test-user-123";
        List<MemoryData> memories = List.of(new MemoryData(testUserId, "A happy memory", "prompt", "url", System.currentTimeMillis()));
        GetAtmosphere.AtmosphereData expectedAtmosphere = new GetAtmosphere.AtmosphereData("Sunny", "#FFD700");

        doReturn(testUserId).when(getAtmosphere).getUserIdFromAuthToken(request);
        doReturn(memories).when(getAtmosphere).getRecentMemoriesForUser(testUserId);
        doReturn(expectedAtmosphere).when(getAtmosphere).generateAtmosphereWithGemini(memories);

        getAtmosphere.service(request, response);

        verify(response).setStatusCode(200, "OK");
        assertEquals("{\"weather\":\"Sunny\",\"backgroundColor\":\"#FFD700\"}", responseWriter.toString());
    }

    @Test
    void service_shouldReturnDefaultAtmosphere_whenNoMemoriesExist() throws Exception {
        String testUserId = "test-user-123";
        doReturn(testUserId).when(getAtmosphere).getUserIdFromAuthToken(request);
        doReturn(Collections.emptyList()).when(getAtmosphere).getRecentMemoriesForUser(testUserId);

        getAtmosphere.service(request, response);

        verify(response).setStatusCode(200);
        assertEquals("{\"weather\":\"Clear\",\"backgroundColor\":\"#87CEEB\"}", responseWriter.toString());
        verify(getAtmosphere, never()).generateAtmosphereWithGemini(any());
    }

    @Test
    void service_shouldReturnUnauthorized_whenAuthFails() throws Exception {
        doThrow(new GetAtmosphere.AuthException("Invalid token")).when(getAtmosphere).getUserIdFromAuthToken(request);

        getAtmosphere.service(request, response);

        verify(response).setStatusCode(401, "Unauthorized");
        assertEquals("{\"error\":\"Invalid token\"}", responseWriter.toString());
    }
}
