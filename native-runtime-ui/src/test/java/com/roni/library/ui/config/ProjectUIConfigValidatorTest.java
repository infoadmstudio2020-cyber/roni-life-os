package com.roni.library.ui.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.library.ui.manager.ComponentType;

import org.junit.Test;

import java.util.List;

/** Verifies the validation rules and their severities. */
public class ProjectUIConfigValidatorTest {

    @Test
    public void defaultConfigurationIsValid() {
        assertTrue(ProjectUIConfigValidator.isValid(new ProjectUIConfig()));
        assertEquals("", ProjectUIConfigValidator.firstError(new ProjectUIConfig()));
    }

    @Test
    public void nullConfigurationIsAnError() {
        List<ProjectUIConfigValidator.Issue> issues =
                ProjectUIConfigValidator.validate(null);

        assertEquals(1, issues.size());
        assertEquals(ProjectUIConfigValidator.Severity.ERROR,
                issues.get(0).getSeverity());
        assertFalse(ProjectUIConfigValidator.isValid(null));
    }

    @Test
    public void noEnabledComponentIsAnError() {
        ProjectUIConfig config = new ProjectUIConfig();
        for (ComponentType type : ComponentType.values()) {
            config.setEnabled(type, false);
        }

        assertFalse(ProjectUIConfigValidator.isValid(config));
        assertTrue(ProjectUIConfigValidator.firstError(config).contains("No component"));
    }

    @Test
    public void webDashboardWithoutSourceIsAnError() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.getDashboard()
                .setEnabled(true)
                .setMode(ProjectUIConfig.DashboardMode.WEB)
                .setWebSource("");

        assertFalse(ProjectUIConfigValidator.isValid(config));
        assertTrue(ProjectUIConfigValidator.firstError(config).contains("web source"));
    }

    @Test
    public void webDashboardWithSourceIsValid() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.getDashboard()
                .setEnabled(true)
                .setMode(ProjectUIConfig.DashboardMode.WEB)
                .setWebSource("https://example.test");

        assertTrue(ProjectUIConfigValidator.isValid(config));
    }

    @Test
    public void dashboardChromeWithoutComponentIsAWarning() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.getDashboard().setEnabled(true).setDrawerEnabled(true);
        config.setEnabled(ComponentType.DRAWER, false);

        List<ProjectUIConfigValidator.Issue> issues =
                ProjectUIConfigValidator.validate(config);

        boolean found = false;
        for (ProjectUIConfigValidator.Issue issue : issues) {
            if (issue.getField().equals("dashboard.drawer")) {
                found = true;
                assertEquals(ProjectUIConfigValidator.Severity.WARNING,
                        issue.getSeverity());
            }
        }
        assertTrue(found);
        // A warning alone must not invalidate the configuration.
        assertTrue(ProjectUIConfigValidator.isValid(config));
    }

    @Test
    public void bottomNavAndRailTogetherIsAWarning() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setEnabled(ComponentType.BOTTOM_NAV, true);
        config.setEnabled(ComponentType.NAVIGATION_RAIL, true);

        boolean found = false;
        for (ProjectUIConfigValidator.Issue issue
                : ProjectUIConfigValidator.validate(config)) {
            if (issue.getMessage().contains("navigation rail")) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void webViewComponentWithoutContainerIsAWarning() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setEnabled(ComponentType.WEB_VIEW, true);
        config.getWebView().setEnabled(false);

        boolean found = false;
        for (ProjectUIConfigValidator.Issue issue
                : ProjectUIConfigValidator.validate(config)) {
            if (issue.getField().equals("webView")) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void splashModeNoneWhileEnabledIsAWarning() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.getSplash().setEnabled(true).setMode(ProjectUIConfig.SplashMode.NONE);

        boolean found = false;
        for (ProjectUIConfigValidator.Issue issue
                : ProjectUIConfigValidator.validate(config)) {
            if (issue.getField().equals("splash.mode")) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void issueToStringIncludesSeverityAndField() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.getDashboard()
                .setEnabled(true)
                .setMode(ProjectUIConfig.DashboardMode.WEB)
                .setWebSource("");

        String text = ProjectUIConfigValidator.validate(config).get(0).toString();

        assertTrue(text.contains("ERROR"));
        assertTrue(text.contains("dashboard.webSource"));
    }
}
