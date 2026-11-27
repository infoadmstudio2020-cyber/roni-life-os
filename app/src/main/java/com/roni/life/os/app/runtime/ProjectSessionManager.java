package com.roni.life.os.app.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks every project runtime session that is open, minimised or recoverable.
 *
 * <p>Three states exist:</p>
 * <ul>
 *   <li>{@link State#ACTIVE} — the project task is in the foreground and its WebView is alive.</li>
 *   <li>{@link State#MINIMIZED} — the project task is in the background, its WebView is still
 *       alive and its navigation state is intact.</li>
 *   <li>{@link State#RECOVERABLE} — the process that owned the runtime is gone. The project can
 *       be reopened, but the runtime must be rebuilt; no in-memory WebView survives.</li>
 * </ul>
 *
 * <p><strong>Minimising never evicts another session.</strong> The previous implementation
 * silently dropped the least-recently-used minimised session when a sixth project was opened,
 * which is exactly the behaviour this class now forbids. When {@link #MAX_SESSIONS} is reached a
 * registration is rejected and the caller must ask the user to close a session explicitly.</p>
 *
 * <p>Pure Java on purpose — no Android types — so every rule here is unit-testable.</p>
 */
public final class ProjectSessionManager {

    /** Maximum number of concurrent project sessions. */
    public static final int MAX_SESSIONS = 5;

    /** Value used when a session has no live Android task. */
    public static final int NO_TASK = -1;

    /** Lifecycle state of a session. */
    public enum State {
        ACTIVE,
        MINIMIZED,
        RECOVERABLE
    }

    /** A single project runtime session. */
    public static final class Session {
        private final String projectId;
        private final String name;
        private final boolean preview;
        private final int iconColor;
        private final String sourceKey;
        private State state;
        private long lastActiveAt;
        private int taskId;

        Session(String projectId, String name, boolean preview, int iconColor,
                String sourceKey, long now) {
            this.projectId = projectId;
            this.name = name != null ? name : "";
            this.preview = preview;
            this.iconColor = iconColor;
            this.sourceKey = sourceKey != null ? sourceKey : "";
            this.state = State.ACTIVE;
            this.lastActiveAt = now;
            this.taskId = NO_TASK;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getName() {
            return name;
        }

        public boolean isPreview() {
            return preview;
        }

        public int getIconColor() {
            return iconColor;
        }

        public String getSourceKey() {
            return sourceKey;
        }

        public State getState() {
            return state;
        }

        public long getLastActiveAt() {
            return lastActiveAt;
        }

        public int getTaskId() {
            return taskId;
        }

        /** @return {@code true} when an in-memory runtime still backs this session */
        public boolean isLive() {
            return state != State.RECOVERABLE;
        }

        @Override
        public String toString() {
            return name + " (" + state + ")";
        }
    }

    /** Result of a registration attempt. */
    public static final class Registration {
        private final boolean accepted;
        private final boolean created;
        private final Session session;
        private final String message;

        Registration(boolean accepted, boolean created, Session session, String message) {
            this.accepted = accepted;
            this.created = created;
            this.session = session;
            this.message = message != null ? message : "";
        }

        public boolean isAccepted() {
            return accepted;
        }

        /** @return {@code true} when a brand-new session was created by this call */
        public boolean isCreated() {
            return created;
        }

        public Session getSession() {
            return session;
        }

        /**
         * Always {@code null}. Kept for source compatibility with the previous implementation,
         * which evicted a session to make room. Eviction no longer happens.
         */
        public String getEvictedProjectId() {
            return null;
        }

        public String getMessage() {
            return message;
        }
    }

    private static volatile ProjectSessionManager instance;

    private final Map<String, Session> sessions = new LinkedHashMap<>();

    private ProjectSessionManager() {
    }

    public static ProjectSessionManager get() {
        if (instance == null) {
            synchronized (ProjectSessionManager.class) {
                if (instance == null) {
                    instance = new ProjectSessionManager();
                }
            }
        }
        return instance;
    }

    /** Test seam. */
    public static synchronized void reset() {
        instance = null;
    }

    /**
     * Registers a session without ever evicting another one.
     *
     * <p>An existing session is returned as-is: a live one keeps its state and task, a
     * recoverable one stays recoverable until {@link #activate(String, int)} is called by the
     * runtime that rebuilds it.</p>
     */
    public synchronized Registration register(String projectId, String name, boolean preview) {
        return register(projectId, name, preview, 0, "");
    }

    /** Registers a session carrying the project's icon colour and source key. */
    public synchronized Registration register(String projectId, String name, boolean preview,
                                              int iconColor, String sourceKey) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return new Registration(false, false, null, "A project id is required.");
        }
        long now = System.currentTimeMillis();

        Session existing = sessions.get(projectId);
        if (existing != null) {
            if (existing.state != State.RECOVERABLE) {
                existing.lastActiveAt = now;
            }
            return new Registration(true, false, existing, "");
        }

        if (sessions.size() >= MAX_SESSIONS) {
            return new Registration(false, false, null,
                    "All " + MAX_SESSIONS + " sessions are open. Close one to continue.");
        }

        Session session = new Session(projectId, name, preview, iconColor, sourceKey, now);
        sessions.put(projectId, session);
        return new Registration(true, true, session, "");
    }

    /** Records the Android task that hosts this project's runtime. */
    public synchronized void attachTask(String projectId, int taskId) {
        Session session = sessions.get(projectId);
        if (session != null) {
            session.taskId = taskId;
        }
    }

    /**
     * Promotes a recoverable session to a live one after its runtime has been rebuilt.
     *
     * @return {@code true} when the session existed and is now live
     */
    public synchronized boolean activate(String projectId, int taskId) {
        Session session = sessions.get(projectId);
        if (session == null) {
            return false;
        }
        session.state = State.ACTIVE;
        session.taskId = taskId;
        session.lastActiveAt = System.currentTimeMillis();
        return true;
    }

    /** Marks a session as minimised; it stays counted against the limit and keeps its task. */
    public synchronized void minimize(String projectId) {
        Session session = sessions.get(projectId);
        if (session != null && session.state != State.RECOVERABLE) {
            session.state = State.MINIMIZED;
            session.lastActiveAt = System.currentTimeMillis();
        }
    }

    /** Brings a minimised session back to the foreground. */
    public synchronized boolean resume(String projectId) {
        Session session = sessions.get(projectId);
        if (session == null || session.state == State.RECOVERABLE) {
            return false;
        }
        session.state = State.ACTIVE;
        session.lastActiveAt = System.currentTimeMillis();
        return true;
    }

    /**
     * Demotes a session whose runtime no longer exists.
     *
     * <p>Called when the hosting activity is destroyed without an explicit close, or when a
     * snapshot is restored after process death. The session stays listed so the user can reopen
     * it, but it is never reported as live.</p>
     */
    public synchronized void markRecoverable(String projectId) {
        Session session = sessions.get(projectId);
        if (session != null) {
            session.state = State.RECOVERABLE;
            session.taskId = NO_TASK;
        }
    }

    /** Removes a session entirely. Only an explicit close may call this. */
    public synchronized void close(String projectId) {
        sessions.remove(projectId);
    }

    public synchronized boolean isOpen(String projectId) {
        return projectId != null && sessions.containsKey(projectId);
    }

    /** @return {@code true} when an in-memory runtime still backs this project */
    public synchronized boolean isLive(String projectId) {
        Session session = projectId != null ? sessions.get(projectId) : null;
        return session != null && session.isLive();
    }

    public synchronized Session find(String projectId) {
        return projectId != null ? sessions.get(projectId) : null;
    }

    public synchronized int count() {
        return sessions.size();
    }

    public synchronized int liveCount() {
        int count = 0;
        for (Session session : sessions.values()) {
            if (session.isLive()) {
                count++;
            }
        }
        return count;
    }

    public synchronized int minimizedCount() {
        int count = 0;
        for (Session session : sessions.values()) {
            if (session.state == State.MINIMIZED) {
                count++;
            }
        }
        return count;
    }

    public synchronized int recoverableCount() {
        int count = 0;
        for (Session session : sessions.values()) {
            if (session.state == State.RECOVERABLE) {
                count++;
            }
        }
        return count;
    }

    /** @return an immutable snapshot, most recently active first */
    public synchronized List<Session> getSessions() {
        List<Session> snapshot = new ArrayList<>(sessions.values());
        Collections.sort(snapshot, (left, right) ->
                Long.compare(right.lastActiveAt, left.lastActiveAt));
        return Collections.unmodifiableList(snapshot);
    }

    /** @return every session that is not in the foreground, most recently active first */
    public synchronized List<Session> getBackgroundSessions() {
        List<Session> background = new ArrayList<>();
        for (Session session : getSessions()) {
            if (session.state != State.ACTIVE) {
                background.add(session);
            }
        }
        return Collections.unmodifiableList(background);
    }

    /** @return a persistable snapshot of every session */
    public synchronized List<SessionSnapshot> snapshot() {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Session session : sessions.values()) {
            snapshots.add(new SessionSnapshot(session.projectId, session.name,
                    session.lastActiveAt, session.iconColor, session.sourceKey,
                    session.preview));
        }
        return snapshots;
    }

    /**
     * Restores persisted sessions as {@link State#RECOVERABLE}.
     *
     * <p>Existing sessions are never overwritten, and the cap is respected. Returns the number of
     * sessions actually added.</p>
     */
    public synchronized int restore(List<SessionSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (SessionSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.getProjectId() == null
                    || snapshot.getProjectId().trim().isEmpty()) {
                continue;
            }
            if (sessions.containsKey(snapshot.getProjectId())) {
                continue;
            }
            if (sessions.size() >= MAX_SESSIONS) {
                break;
            }
            Session session = new Session(snapshot.getProjectId(), snapshot.getName(),
                    snapshot.isPreview(), snapshot.getIconColor(), snapshot.getSourceKey(),
                    snapshot.getLastActiveAt());
            session.state = State.RECOVERABLE;
            session.taskId = NO_TASK;
            sessions.put(session.projectId, session);
            added++;
        }
        return added;
    }

    /** Drops every recoverable session — used when the user clears the list. */
    public synchronized void clearRecoverable() {
        List<String> removable = new ArrayList<>();
        for (Session session : sessions.values()) {
            if (session.state == State.RECOVERABLE) {
                removable.add(session.projectId);
            }
        }
        for (String projectId : removable) {
            sessions.remove(projectId);
        }
    }
}
