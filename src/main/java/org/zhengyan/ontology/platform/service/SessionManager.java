package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final long sessionTtl;
    private final int maxSessions;

    public SessionManager(
            @Value("${ontology.nlq.session.ttl:1800000}") long sessionTtl,
            @Value("${ontology.nlq.session.max:1000}") int maxSessions) {
        this.sessionTtl = sessionTtl;
        this.maxSessions = maxSessions;
    }

    public Session getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            if (sessions.size() >= maxSessions) {
                evictOldest();
            }
            session = new Session(sessionId);
            sessions.put(sessionId, session);
            log.debug("Created session: {}", sessionId);
        }
        session.refreshTtl();
        return session;
    }

    public Session get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.refreshTtl();
        }
        return session;
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
        log.debug("Removed session: {}", sessionId);
    }

    @Scheduled(fixedRateString = "${ontology.nlq.session.cleanup-interval:300000}")
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Session> entry = it.next();
            if (now - entry.getValue().lastAccessTime > sessionTtl) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired sessions ({} remaining)", removed, sessions.size());
        }
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().lastAccessTime < oldestTime) {
                oldestTime = entry.getValue().lastAccessTime;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            sessions.remove(oldestKey);
            log.debug("Evicted oldest session: {}", oldestKey);
        }
    }

    public static class Session {
        private final String sessionId;
        private final LinkedList<HistoryEntry> history = new LinkedList<>();
        private final long createdAt;
        private long lastAccessTime;
        private static final int MAX_HISTORY = 5;

        Session(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessTime = this.createdAt;
        }

        public void refreshTtl() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        public void addHistory(String question, String sparql) {
            history.addFirst(new HistoryEntry(question, sparql));
            if (history.size() > MAX_HISTORY) {
                history.removeLast();
            }
        }

        public List<HistoryEntry> getHistory() {
            return List.copyOf(history);
        }

        public String getSessionId() { return sessionId; }
        public long getCreatedAt() { return createdAt; }
    }

    public static class HistoryEntry {
        private final String question;
        private final String sparql;

        HistoryEntry(String question, String sparql) {
            this.question = question;
            this.sparql = sparql;
        }

        public String getQuestion() { return question; }
        public String getSparql() { return sparql; }
    }
}
