package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.service.SessionManager;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager(60000, 100);
    }

    @Test
    void testCreateSession() {
        SessionManager.Session session = sessionManager.getOrCreate("session-1");
        assertNotNull(session);
        assertEquals("session-1", session.getSessionId());
    }

    @Test
    void testGetExistingSession() {
        sessionManager.getOrCreate("session-1");
        SessionManager.Session session = sessionManager.get("session-1");
        assertNotNull(session);
        assertEquals("session-1", session.getSessionId());
    }

    @Test
    void testGetNonExistentSession() {
        SessionManager.Session session = sessionManager.get("nonexistent");
        assertNull(session);
    }

    @Test
    void testNullSessionId() {
        SessionManager.Session session = sessionManager.getOrCreate(null);
        assertNull(session);
    }

    @Test
    void testRemoveSession() {
        sessionManager.getOrCreate("session-1");
        sessionManager.remove("session-1");
        assertNull(sessionManager.get("session-1"));
    }

    @Test
    void testHistoryTracking() {
        SessionManager.Session session = sessionManager.getOrCreate("session-1");
        session.addHistory("list all employees", "SELECT ...");
        session.addHistory("who works for CS", "SELECT ...");
        assertEquals(2, session.getHistory().size());
        assertEquals("who works for CS", session.getHistory().get(0).getQuestion());
        assertEquals("list all employees", session.getHistory().get(1).getQuestion());
    }

    @Test
    void testHistoryMaxSize() {
        SessionManager.Session session = sessionManager.getOrCreate("session-1");
        for (int i = 0; i < 10; i++) {
            session.addHistory("q" + i, "sparql" + i);
        }
        assertEquals(5, session.getHistory().size());
        assertEquals("q9", session.getHistory().get(0).getQuestion());
    }

    @Test
    void testCleanupExpired() throws Exception {
        SessionManager shortTtl = new SessionManager(10, 100);
        shortTtl.getOrCreate("session-1");
        shortTtl.getOrCreate("session-2");
        Thread.sleep(30);
        shortTtl.cleanupExpired();
        assertNull(shortTtl.get("session-1"));
        assertNull(shortTtl.get("session-2"));
    }

    @Test
    void testMaxSessionsEviction() {
        SessionManager small = new SessionManager(60000, 3);
        small.getOrCreate("s1");
        small.getOrCreate("s2");
        small.getOrCreate("s3");
        small.getOrCreate("s4");
        int active = 0;
        for (String id : new String[]{"s1", "s2", "s3", "s4"}) {
            if (small.get(id) != null) active++;
        }
        assertEquals(3, active);
    }

    @Test
    void testSessionCreatedAt() {
        SessionManager.Session session = sessionManager.getOrCreate("session-1");
        assertTrue(session.getCreatedAt() > 0);
    }
}
