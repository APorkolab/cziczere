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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

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

    private AnalyzeMemories analyzeMemories;
    private MockedStatic<FirebaseApp> firebaseAppMockedStatic;
    @Mock private FirebaseApp mockFirebaseApp;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        firebaseAppMockedStatic = mockStatic(FirebaseApp.class);
        firebaseAppMockedStatic.when(FirebaseApp::getApps).thenReturn(List.of(mockFirebaseApp));

        analyzeMemories = spy(new AnalyzeMemories(db, vertexAI));

        responseWriter = new StringWriter();
        BufferedWriter writer = new BufferedWriter(responseWriter);
        lenient().when(response.getWriter()).thenReturn(writer);
    }

    @AfterEach
    void tearDown() {
        firebaseAppMockedStatic.close();
    }

    private void setupRequestWithBody(String jsonBody) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(jsonBody));
        lenient().when(request.getReader()).thenReturn(reader);
    }

    @Test
    void service_shouldGenerateWeeklySummary_whenTypeIsCorrect() throws Exception {
        // Arrange
        String testUserId = "test-user-123";
        List<MemoryData> memories = List.of(
            new MemoryData(testUserId, "A great week!", "p1", "u1", 1L, "memory", Collections.singletonMap("joy", 0.9))
        );
        String expectedSummary = "This week was full of joy.";
        String requestJson = new Gson().toJson(new RequestData(null, "weekly_summary"));
        setupRequestWithBody(requestJson);

        doReturn(testUserId).when(analyzeMemories).getUserIdFromAuthToken(any());
        doReturn(memories).when(analyzeMemories).getRecentMemoriesForUser(testUserId);
        doReturn(expectedSummary).when(analyzeMemories).generateWeeklySummary(any());
        doNothing().when(analyzeMemories).saveInsightToFirestore(any());

        // Act
        analyzeMemories.service(request, response);

        // Assert
        verify(response).setStatusCode(200, "OK");
        ArgumentCaptor<InsightData> insightCaptor = ArgumentCaptor.forClass(InsightData.class);
        verify(analyzeMemories).saveInsightToFirestore(insightCaptor.capture());
        assertEquals("weekly_summary", insightCaptor.getValue().type());
        assertEquals(expectedSummary, insightCaptor.getValue().text());
    }

    @Test
    void service_shouldGenerateMonthlyInsight_whenTypeIsCorrect() throws Exception {
        // Arrange
        String testUserId = "test-user-123";
        List<MemoryData> memories = List.of(
                new MemoryData(testUserId, "A great month!", "p1", "u1", 1L, "memory", Collections.singletonMap("pride", 0.8))
        );
        String expectedInsight = "This month you should be proud.";
        String requestJson = new Gson().toJson(new RequestData(null, "monthly_insight"));
        setupRequestWithBody(requestJson);

        doReturn(testUserId).when(analyzeMemories).getUserIdFromAuthToken(any());
        doReturn(memories).when(analyzeMemories).getMonthlyMemoriesForUser(testUserId);
        doReturn(expectedInsight).when(analyzeMemories).generateMonthlyInsight(any());
        doNothing().when(analyzeMemories).saveInsightToFirestore(any());

        // Act
        analyzeMemories.service(request, response);

        // Assert
        verify(response).setStatusCode(200, "OK");
        ArgumentCaptor<InsightData> insightCaptor = ArgumentCaptor.forClass(InsightData.class);
        verify(analyzeMemories).saveInsightToFirestore(insightCaptor.capture());
        assertEquals("monthly_insight", insightCaptor.getValue().type());
        assertEquals(expectedInsight, insightCaptor.getValue().text());
    }

    @Test
    void service_shouldReturnNotFound_whenNoMemoriesExist() throws Exception {
        // Arrange
        String testUserId = "test-user-123";
        String requestJson = new Gson().toJson(new RequestData(null, "weekly_summary"));
        setupRequestWithBody(requestJson);

        doReturn(testUserId).when(analyzeMemories).getUserIdFromAuthToken(request);
        doReturn(List.of()).when(analyzeMemories).getRecentMemoriesForUser(testUserId);

        // Act
        analyzeMemories.service(request, response);

        // Assert
        verify(response).setStatusCode(404, "Not Found");
        assertTrue(responseWriter.toString().contains("No recent memories"));
    }

    @Test
    void service_shouldReturnUnauthorized_whenAuthFails() throws Exception {
        // Arrange
        String requestJson = new Gson().toJson(new RequestData(null, "weekly_summary"));
        setupRequestWithBody(requestJson);
        doThrow(new AnalyzeMemories.AuthException("Invalid token"))
                .when(analyzeMemories).getUserIdFromAuthToken(request);

        // Act
        analyzeMemories.service(request, response);

        // Assert
        verify(response).setStatusCode(401, "Unauthorized");
        assertEquals("{\"error\":\"Invalid token\"}", responseWriter.toString());
    }
}
