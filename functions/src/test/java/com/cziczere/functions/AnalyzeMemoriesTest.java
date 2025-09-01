package com.cziczere.functions;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.vertexai.VertexAI;
import com.google.firebase.FirebaseApp;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyzeMemoriesTest {

    @Mock private Firestore db;
    @Mock private VertexAI vertexAI;
    @Mock private HttpRequest request;
    @Mock private HttpResponse response;

    // Use a real AnalyzeMemories instance but with mocked dependencies
    private AnalyzeMemories analyzeMemories;
    private MockedStatic<FirebaseApp> firebaseAppMockedStatic;

    @Mock
    private FirebaseApp mockFirebaseApp;


    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        firebaseAppMockedStatic = mockStatic(FirebaseApp.class);
        firebaseAppMockedStatic.when(FirebaseApp::getApps).thenReturn(java.util.List.of(mockFirebaseApp));

        // We need to spy on the real object to mock some of its methods
        analyzeMemories = spy(new AnalyzeMemories(db, vertexAI));

        // Mock the response writer
        responseWriter = new StringWriter();
        BufferedWriter writer = new BufferedWriter(responseWriter);
        when(response.getWriter()).thenReturn(writer);
    }

    @AfterEach
    void tearDown() {
        firebaseAppMockedStatic.close();
    }

    @Test
    void service_shouldReturnInsight_whenMemoriesExist() throws Exception {
        // Arrange
        String testUserId = "test-user-123";
        List<MemoryData> memories = List.of(
                new MemoryData(testUserId, "Loved walking my dog today.", "prompt1", "url1", 1L),
                new MemoryData(testUserId, "My dog is the best.", "prompt2", "url2", 2L)
        );
        String expectedInsight = "It seems that your dog brings you a lot of joy.";

        // Mock the authentication
        doReturn(testUserId).when(analyzeMemories).getUserIdFromAuthToken(request);
        // Mock the data fetching
        doReturn(memories).when(analyzeMemories).getMemoriesForUser(testUserId);
        // Mock the AI generation
        doReturn(expectedInsight).when(analyzeMemories).generateInsightWithGemini(memories);
        // Mock the save operation
        doNothing().when(analyzeMemories).saveInsightToFirestore(any(InsightData.class));

        // Act
        analyzeMemories.service(request, response);

        // Assert
        ArgumentCaptor<InsightData> insightCaptor = ArgumentCaptor.forClass(InsightData.class);
        verify(analyzeMemories).saveInsightToFirestore(insightCaptor.capture());

        assertEquals(testUserId, insightCaptor.getValue().userId());
        assertEquals(expectedInsight, insightCaptor.getValue().text());

        verify(response).setStatusCode(200, "OK");
        String jsonResponse = responseWriter.toString();
        // Use contains because the timestamp will be different
        assertTrue(jsonResponse.contains("\"userId\":\"" + testUserId + "\""));
        assertTrue(jsonResponse.contains("\"text\":\"" + expectedInsight.replace("\"", "\\\"") + "\""));
    }

    @Test
    void service_shouldReturnNotFound_whenNoMemoriesExist() throws Exception {
        // Arrange
        String testUserId = "test-user-123";
        doReturn(testUserId).when(analyzeMemories).getUserIdFromAuthToken(request);
        doReturn(List.of()).when(analyzeMemories).getMemoriesForUser(testUserId);

        // Act
        analyzeMemories.service(request, response);

        // Assert
        verify(response).setStatusCode(404, "Not Found");
        assertEquals("{\"message\":\"No memories found to analyze.\"}", responseWriter.toString());
        verify(analyzeMemories, never()).generateInsightWithGemini(any());
        verify(analyzeMemories, never()).saveInsightToFirestore(any());
    }

    @Test
    void service_shouldReturnUnauthorized_whenAuthFails() throws Exception {
        // Arrange
        doThrow(new AnalyzeMemories.AuthException("Invalid token"))
                .when(analyzeMemories).getUserIdFromAuthToken(request);

        // Act
        analyzeMemories.service(request, response);

        // Assert
        verify(response).setStatusCode(401, "Unauthorized");
        assertEquals("{\"error\":\"Invalid token\"}", responseWriter.toString());
    }
}
