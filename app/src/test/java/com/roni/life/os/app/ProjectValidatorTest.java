package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectFactory;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.ProjectValidator;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * প্রজেক্ট ভ্যালিডেশন নিয়মের যাচাই।
 */
public class ProjectValidatorTest {

    private Project validProject() {
        Project project = ProjectFactory.createBlank("বৈধ প্রজেক্ট");
        project.setSourceType(ProjectSourceType.ASSET);
        project.setSourcePath("webapp");
        project.setEntryPoint("index.html");
        return project;
    }

    @Test
    public void validProject_passes() {
        ProjectValidator.Result result = ProjectValidator.validate(validProject(), Collections.<Project>emptyList());
        assertTrue(result.getErrors().toString(), result.isValid());
    }

    @Test
    public void blankName_isRejected() {
        Project project = validProject();
        project.setName("   ");
        ProjectValidator.Result result = ProjectValidator.validate(project, Collections.<Project>emptyList());
        assertFalse(result.isValid());
        assertTrue(result.getFirstError().contains("নাম"));
    }

    @Test
    public void duplicateName_isRejected() {
        Project existing = validProject();
        existing.setName("একই নাম");

        Project candidate = validProject();
        candidate.setName("একই নাম");

        List<Project> others = new ArrayList<>();
        others.add(existing);

        ProjectValidator.Result result = ProjectValidator.validate(candidate, others);
        assertFalse(result.isValid());
        assertTrue(result.getFirstError().contains("ইতিমধ্যে"));
    }

    @Test
    public void editingSameProject_doesNotTriggerDuplicate() {
        Project project = validProject();
        project.setName("আমার প্রজেক্ট");

        List<Project> others = new ArrayList<>();
        others.add(project);

        ProjectValidator.Result result = ProjectValidator.validate(project, others);
        assertTrue(result.isValid());
    }

    @Test
    public void remoteUrlWithoutProtocol_isRejected() {
        Project project = validProject();
        project.setSourceType(ProjectSourceType.REMOTE_URL);
        project.setSourcePath("example.com/app");

        ProjectValidator.Result result = ProjectValidator.validate(project, Collections.<Project>emptyList());
        assertFalse(result.isValid());
        assertTrue(result.getFirstError().contains("http"));
    }

    @Test
    public void remoteUrlWithHttps_passes() {
        Project project = validProject();
        project.setSourceType(ProjectSourceType.REMOTE_URL);
        project.setSourcePath("https://example.com/app");

        ProjectValidator.Result result = ProjectValidator.validate(project, Collections.<Project>emptyList());
        assertTrue(result.isValid());
    }

    @Test
    public void missingPathForAsset_isRejected() {
        Project project = validProject();
        project.setSourcePath("");

        ProjectValidator.Result result = ProjectValidator.validate(project, Collections.<Project>emptyList());
        assertFalse(result.isValid());
        assertTrue(result.getFirstError().contains("পাথ"));
    }

    @Test
    public void inlineHtmlWithoutContent_isRejected() {
        Project project = validProject();
        project.setSourceType(ProjectSourceType.INLINE_HTML);
        project.setSourcePath("");
        project.setInlineHtml("");

        ProjectValidator.Result result = ProjectValidator.validate(project, Collections.<Project>emptyList());
        assertFalse(result.isValid());
        assertTrue(result.getFirstError().contains("HTML"));
    }

    @Test
    public void inlineHtmlWithContent_passes() {
        Project project = validProject();
        project.setSourceType(ProjectSourceType.INLINE_HTML);
        project.setSourcePath("");
        project.setInlineHtml("<html><body>হ্যালো</body></html>");

        ProjectValidator.Result result = ProjectValidator.validate(project, Collections.<Project>emptyList());
        assertTrue(result.isValid());
    }

    @Test
    public void missingEntryPoint_isOnlyWarning() {
        Project project = validProject();
        project.setEntryPoint("");

        ProjectValidator.Result result = ProjectValidator.validate(project, Collections.<Project>emptyList());
        assertTrue(result.isValid());
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    public void nullProject_isRejected() {
        ProjectValidator.Result result = ProjectValidator.validate(null, Collections.<Project>emptyList());
        assertFalse(result.isValid());
    }
}
