package com.cziczere.functions;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.ListUsersPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CheckForInactiveUsersTest {

    @Mock
    private Firestore db;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private ListUsersPage listUsersPage;

    @Mock
    private ExportedUserRecord userRecord;

    @Mock
    private CollectionReference memoriesCollection;

    @Mock
    private CollectionReference insightsCollection;

    @Mock
    private Query query;

    @Mock
    private QuerySnapshot querySnapshot;

    @Mock
    private QueryDocumentSnapshot documentSnapshot;

    @InjectMocks
    private CheckForInactiveUsers function;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Mock Firestore and collection chain
        when(db.collection("memories")).thenReturn(memoriesCollection);
        when(db.collection("insights")).thenReturn(insightsCollection);
        when(memoriesCollection.whereEqualTo(anyString(), anyString())).thenReturn(query);
        when(query.orderBy(anyString(), any(Query.Direction.class))).thenReturn(query);
        when(query.limit(anyInt())).thenReturn(query);

        // This is the key fix: mock the chain all the way to the documents
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        when(query.get()).thenReturn(future);
        when(future.get()).thenReturn(querySnapshot);

        // Mock Auth user listing
        when(firebaseAuth.listUsers(null)).thenReturn(listUsersPage);
        when(listUsersPage.iterateAll()).thenReturn(Collections.singletonList(userRecord));
        when(userRecord.getUid()).thenReturn("test-user-id");
    }

    @Test
    void testAccept_inactiveUser_createsReminder() throws Exception {
        // Arrange
        long twoWeeksAgo = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000);
        MemoryData oldMemory = new MemoryData("test-user-id", "text", "prompt", "url", twoWeeksAgo, "memory");

        when(querySnapshot.getDocuments()).thenReturn(Collections.singletonList(documentSnapshot));
        when(documentSnapshot.toObject(MemoryData.class)).thenReturn(oldMemory);

        // Mock the check for existing reminders to find none
        Query reminderQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> reminderFuture = mock(ApiFuture.class);
        QuerySnapshot reminderSnapshot = mock(QuerySnapshot.class);
        when(insightsCollection.whereEqualTo("userId", "test-user-id")).thenReturn(reminderQuery);
        when(reminderQuery.whereEqualTo("type", "reminder")).thenReturn(reminderQuery);
        when(reminderQuery.orderBy(anyString(), any(Query.Direction.class))).thenReturn(reminderQuery);
        when(reminderQuery.limit(1)).thenReturn(reminderQuery);
        when(reminderQuery.get()).thenReturn(reminderFuture);
        when(reminderFuture.get()).thenReturn(reminderSnapshot);
        when(reminderSnapshot.getDocuments()).thenReturn(Collections.emptyList());

        // Mock the write operation
        DocumentReference docRef = mock(DocumentReference.class);
        when(insightsCollection.document()).thenReturn(docRef);
        when(docRef.set(any(InsightData.class))).thenReturn(mock(ApiFuture.class));


        // Act
        function.accept(null, null);

        // Assert
        ArgumentCaptor<InsightData> insightCaptor = ArgumentCaptor.forClass(InsightData.class);
        verify(docRef).set(insightCaptor.capture());

        assertEquals("reminder", insightCaptor.getValue().type());
        assertEquals("test-user-id", insightCaptor.getValue().userId());
        assertNotNull(insightCaptor.getValue().text());
    }

    @Test
    void testAccept_activeUser_doesNotCreateReminder() throws Exception {
        // Arrange
        long yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        MemoryData recentMemory = new MemoryData("test-user-id", "text", "prompt", "url", yesterday, "memory");

        when(querySnapshot.getDocuments()).thenReturn(Collections.singletonList(documentSnapshot));
        when(documentSnapshot.toObject(MemoryData.class)).thenReturn(recentMemory);

        // Act
        function.accept(null, null);

        // Assert
        verify(insightsCollection, never()).document();
    }

    @Test
    void testAccept_userWithNoMemories_createsReminder() throws Exception {
        // Arrange
        when(querySnapshot.getDocuments()).thenReturn(Collections.emptyList());

        // Mock the check for existing reminders to find none
        Query reminderQuery = mock(Query.class);
        ApiFuture<QuerySnapshot> reminderFuture = mock(ApiFuture.class);
        QuerySnapshot reminderSnapshot = mock(QuerySnapshot.class);
        when(insightsCollection.whereEqualTo("userId", "test-user-id")).thenReturn(reminderQuery);
        when(reminderQuery.whereEqualTo("type", "reminder")).thenReturn(reminderQuery);
        when(reminderQuery.orderBy(anyString(), any(Query.Direction.class))).thenReturn(reminderQuery);
        when(reminderQuery.limit(1)).thenReturn(reminderQuery);
        when(reminderQuery.get()).thenReturn(reminderFuture);
        when(reminderFuture.get()).thenReturn(reminderSnapshot);
        when(reminderSnapshot.getDocuments()).thenReturn(Collections.emptyList());

        DocumentReference docRef = mock(DocumentReference.class);
        when(insightsCollection.document()).thenReturn(docRef);
        when(docRef.set(any(InsightData.class))).thenReturn(mock(ApiFuture.class));

        // Act
        function.accept(null, null);

        // Assert
        ArgumentCaptor<InsightData> insightCaptor = ArgumentCaptor.forClass(InsightData.class);
        verify(docRef).set(insightCaptor.capture());
        assertEquals("reminder", insightCaptor.getValue().type());
    }

    // Helper to mock ApiFuture
    @SuppressWarnings("rawtypes")
    private com.google.api.core.ApiFuture mockFuture(Object result) {
        com.google.api.core.ApiFuture future = mock(com.google.api.core.ApiFuture.class);
        try {
            when(future.get()).thenReturn(result);
        } catch (Exception e) {}
        return future;
    }
}
