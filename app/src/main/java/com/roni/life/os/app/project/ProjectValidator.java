package com.roni.life.os.app.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * প্রজেক্ট কনফিগারেশনের ভ্যালিডেশন নিয়ম।
 *
 * <p>শুধুমাত্র ডেটা-স্তরের যাচাই এখানে হয়; ফাইল/অ্যাসেটের প্রকৃত উপস্থিতি
 * {@code ProjectSourceInspector} যাচাই করে।</p>
 */
public final class ProjectValidator {

    private ProjectValidator() {
    }

    /** একটি ভ্যালিডেশন ইস্যু। */
    public static final class Issue {
        private final String message;
        private final boolean error;

        Issue(String message, boolean error) {
            this.message = message;
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public boolean isError() {
            return error;
        }

        @Override
        public String toString() {
            return (error ? "ERROR: " : "WARN: ") + message;
        }
    }

    /** ভ্যালিডেশনের ফলাফল। */
    public static final class Result {
        private final List<Issue> issues;

        Result(List<Issue> issues) {
            this.issues = Collections.unmodifiableList(issues);
        }

        public boolean isValid() {
            for (Issue issue : issues) {
                if (issue.isError()) {
                    return false;
                }
            }
            return true;
        }

        public List<Issue> getIssues() {
            return issues;
        }

        public List<String> getErrors() {
            List<String> errors = new ArrayList<>();
            for (Issue issue : issues) {
                if (issue.isError()) {
                    errors.add(issue.getMessage());
                }
            }
            return errors;
        }

        public List<String> getWarnings() {
            List<String> warnings = new ArrayList<>();
            for (Issue issue : issues) {
                if (!issue.isError()) {
                    warnings.add(issue.getMessage());
                }
            }
            return warnings;
        }

        public String getFirstError() {
            List<String> errors = getErrors();
            return errors.isEmpty() ? "" : errors.get(0);
        }
    }

    /**
     * প্রজেক্ট যাচাই করে।
     *
     * @param project  যাচাইয়ের 대상
     * @param existing একই স্টোরে থাকা অন্য প্রজেক্টসমূহ (Nameের পুনরাবৃত্তি ধরার জন্য)
     */
    public static Result validate(Project project, List<Project> existing) {
        List<Issue> issues = new ArrayList<>();
        if (project == null) {
            issues.add(new Issue("The project was not found.", true));
            return new Result(issues);
        }

        String name = project.getName();
        if (name == null || name.trim().isEmpty()) {
            issues.add(new Issue("The project name cannot be empty.", true));
        } else if (name.trim().length() > 60) {
            issues.add(new Issue("The project name cannot exceed 60 characters.", true));
        }

        if (existing != null && name != null && !name.trim().isEmpty()) {
            for (Project other : existing) {
                if (other == null || other.getId() == null) {
                    continue;
                }
                if (other.getId().equals(project.getId())) {
                    continue;
                }
                if (name.trim().equalsIgnoreCase(other.getName() != null ? other.getName().trim() : "")) {
                    issues.add(new Issue("A project with this name already exists: " + name.trim(), true));
                    break;
                }
            }
        }

        ProjectSourceType type = project.getSourceType();
        if (type == null) {
            issues.add(new Issue("No source type was selected.", true));
            type = ProjectSourceType.ASSET;
        }

        // Role rules. A Splash project is a presentation, so it can never double as the
        // application project; and only one Bootstrap project may open at startup.
        if (project.getProjectType() == ProjectType.SPLASH && project.isDefault()) {
            issues.add(new Issue(
                    "A Splash project cannot be the default application project.", true));
        }
        if (project.isBootstrapAtStartup() && existing != null) {
            for (Project other : existing) {
                if (other == null || other.getId() == null) {
                    continue;
                }
                if (other.getId().equals(project.getId())) {
                    continue;
                }
                if (other.isBootstrapAtStartup()) {
                    issues.add(new Issue(
                            "Another Bootstrap project already opens at startup: "
                                    + other.getName(), true));
                    break;
                }
            }
        }

        String path = project.getSourcePath();
        if (type.requiresPath() && (path == null || path.trim().isEmpty())) {
            issues.add(new Issue("A source path is required (" + type.getKey() + ")।", true));
        }

        if (type == ProjectSourceType.REMOTE_URL && path != null && !path.trim().isEmpty()) {
            String lower = path.trim().toLowerCase();
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                issues.add(new Issue("Inline HTML content cannot be empty.", true));
            }
        }

        if (type == ProjectSourceType.INLINE_HTML) {
            String html = project.getInlineHtml();
            if (html == null || html.trim().isEmpty()) {
                issues.add(new Issue("Inline HTML content cannot be empty.", true));
            }
        }

        if (type.requiresEntryPoint()) {
            String entry = project.getEntryPoint();
            if (entry == null || entry.trim().isEmpty()) {
                issues.add(new Issue("The entry point is empty — index.html will be used.", false));
            }
        }

        if (project.getSplashDurationMs() < Project.MIN_SPLASH_DURATION_MS) {
            issues.add(new Issue("The splash duration is too short.", true));
        } else if (project.getSplashDurationMs() > 4000) {
            issues.add(new Issue("The splash duration exceeds 4 seconds — users may wait.", false));
        }

        String description = project.getDescription();
        if (description != null && description.length() > 240) {
            issues.add(new Issue("Keep the description within 240 characters.", false));
        }

        return new Result(issues);
    }
}
