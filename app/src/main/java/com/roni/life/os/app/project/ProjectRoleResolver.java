package com.roni.life.os.app.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic selection policy for the three project roles.
 *
 * <p>At most one project may be active for each role. When several projects of the same type
 * are registered, the resolver picks one predictably instead of leaving the choice to
 * iteration order:</p>
 *
 * <ol>
 *   <li>the explicitly configured id, if it still exists and its type matches the role;</li>
 *   <li>otherwise the most recently opened project of that type;</li>
 *   <li>otherwise the oldest project of that type (stable, creation order);</li>
 *   <li>otherwise {@code null}.</li>
 * </ol>
 *
 * <p>No Android imports — every branch is unit-testable on the JVM.</p>
 */
public final class ProjectRoleResolver {

    private ProjectRoleResolver() {
    }

    /** A role plus the project that currently fills it. */
    public static final class RoleSelection {
        private final ProjectType role;
        private final Project project;
        private final List<Project> candidates;

        RoleSelection(ProjectType role, Project project, List<Project> candidates) {
            this.role = role;
            this.project = project;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        }

        public ProjectType getRole() {
            return role;
        }

        /** The active project for this role, or {@code null} when none is registered. */
        public Project getProject() {
            return project;
        }

        public boolean hasProject() {
            return project != null;
        }

        /** The active project's id, or {@code null} when the role is empty. */
        public String getIdOrNull() {
            return project != null ? project.getId() : null;
        }

        /** Every registered project that could fill this role, in resolution order. */
        public List<Project> getCandidates() {
            return candidates;
        }

        /** True when more than one project competes for this role. */
        public boolean isAmbiguous() {
            return candidates.size() > 1;
        }
    }

    /** Projects of {@code type}, ordered by the resolution policy. */
    public static List<Project> candidates(List<Project> projects, ProjectType type) {
        List<Project> matches = new ArrayList<>();
        if (projects == null || type == null) {
            return matches;
        }
        for (Project project : projects) {
            if (project != null && project.getProjectType() == type) {
                matches.add(project);
            }
        }
        Collections.sort(matches, RESOLUTION_ORDER);
        return matches;
    }

    /**
     * Resolves the active project for a role.
     *
     * @param projects     every registered project
     * @param type         the role being resolved
     * @param configuredId the explicitly configured id, or {@code null}/empty
     */
    public static RoleSelection resolve(List<Project> projects, ProjectType type, String configuredId) {
        List<Project> candidates = candidates(projects, type);

        if (configuredId != null && !configuredId.trim().isEmpty()) {
            for (Project candidate : candidates) {
                if (configuredId.trim().equals(candidate.getId())) {
                    return new RoleSelection(type, candidate, candidates);
                }
            }
            // The configured id is stale or points at the wrong type. Fall through to the
            // policy rather than returning nothing — a deleted project must not break startup.
        }

        Project selected = candidates.isEmpty() ? null : candidates.get(0);
        return new RoleSelection(type, selected, candidates);
    }

    /** Convenience wrapper for the Splash role. */
    public static RoleSelection resolveSplash(List<Project> projects, String configuredId) {
        return resolve(projects, ProjectType.SPLASH, configuredId);
    }

    /** Convenience wrapper for the Bootstrap role. */
    public static RoleSelection resolveBootstrap(List<Project> projects, String configuredId) {
        return resolve(projects, ProjectType.BOOTSTRAP, configuredId);
    }

    /**
     * Resolves the default application project.
     *
     * <p>Only {@link ProjectType#WEB} projects qualify. Priority preserves the behaviour the
     * application already had: the explicitly configured id, then the most recently opened
     * project, then the project flagged {@code default}, then the oldest candidate.</p>
     */
    public static RoleSelection resolveDefaultWeb(List<Project> projects,
                                                  String configuredId,
                                                  String lastOpenedId) {
        List<Project> candidates = candidates(projects, ProjectType.WEB);

        if (configuredId != null && !configuredId.trim().isEmpty()) {
            for (Project candidate : candidates) {
                if (configuredId.trim().equals(candidate.getId())) {
                    return new RoleSelection(ProjectType.WEB, candidate, candidates);
                }
            }
        }
        // The explicit default flag is the next authority. lastOpenedId is deliberately NOT
        // consulted here: an explicitly selected default must never be overridden by the last
        // opened project.
        for (Project candidate : candidates) {
            if (candidate.isDefault()) {
                return new RoleSelection(ProjectType.WEB, candidate, candidates);
            }
        }
        // Nothing is configured: report "no default" instead of silently picking a project.
        return new RoleSelection(ProjectType.WEB, null, candidates);
    }

    /**
     * Reports every role that currently has more than one candidate, so the UI can surface the
     * conflict instead of silently picking one.
     */
    public static List<RoleSelection> ambiguousRoles(List<Project> projects,
                                                     String splashId,
                                                     String bootstrapId,
                                                     String defaultWebId) {
        List<RoleSelection> ambiguous = new ArrayList<>();
        RoleSelection splash = resolveSplash(projects, splashId);
        if (splash.isAmbiguous()) {
            ambiguous.add(splash);
        }
        RoleSelection bootstrap = resolveBootstrap(projects, bootstrapId);
        if (bootstrap.isAmbiguous()) {
            ambiguous.add(bootstrap);
        }
        RoleSelection web = resolveDefaultWeb(projects, defaultWebId, null);
        if (web.isAmbiguous()) {
            ambiguous.add(web);
        }
        return ambiguous;
    }

    /**
     * True when {@code projectId} is the active holder of any role. Used to warn before a
     * delete, never to block one.
     */
    public static boolean holdsAnyRole(List<Project> projects,
                                       String projectId,
                                       String splashId,
                                       String bootstrapId,
                                       String defaultWebId) {
        if (projectId == null) {
            return false;
        }
        return projectId.equals(resolveSplash(projects, splashId).getIdOrNull())
                || projectId.equals(resolveBootstrap(projects, bootstrapId).getIdOrNull())
                || projectId.equals(resolveDefaultWeb(projects, defaultWebId, null).getIdOrNull());
    }

    /** Most recently opened first, then oldest created, then name. */
    private static final Comparator<Project> RESOLUTION_ORDER = new Comparator<Project>() {
        @Override
        public int compare(Project left, Project right) {
            long leftOpened = left.getLastOpenedAt();
            long rightOpened = right.getLastOpenedAt();
            if (leftOpened != rightOpened) {
                return leftOpened > rightOpened ? -1 : 1;
            }
            long leftCreated = left.getCreatedAt();
            long rightCreated = right.getCreatedAt();
            if (leftCreated != rightCreated) {
                return leftCreated < rightCreated ? -1 : 1;
            }
            String leftName = left.getName() != null ? left.getName() : "";
            String rightName = right.getName() != null ? right.getName() : "";
            return leftName.compareToIgnoreCase(rightName);
        }
    };
}
