package com.roni.library.platform.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.camera.CameraCapabilities;
import com.roni.library.contracts.camera.CameraConfiguration;
import com.roni.library.contracts.camera.CameraErrorCodes;
import com.roni.library.contracts.camera.CameraFlashMode;
import com.roni.library.contracts.camera.CameraLens;
import com.roni.library.contracts.camera.CameraLimits;
import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.contracts.camera.CameraState;
import com.roni.library.contracts.camera.PhotoCaptureRequest;
import com.roni.library.contracts.camera.VideoRecordingRequest;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

/** JVM tests for the camera engine policy layer (no device required). */
public final class CameraEngineTest {

    @After
    public void tearDown() {
        CameraProjectContext.clear();
    }

    private static CameraCapabilities supportedCapabilities() {
        return new CameraCapabilities.Builder()
                .supported(true)
                .addLens(CameraLens.BACK)
                .addLens(CameraLens.FRONT)
                .addAspectRatio("4:3")
                .addAspectRatio("16:9")
                .maxPhotoSize(4000, 3000)
                .zoom(true, 8f)
                .addFlashMode(CameraFlashMode.OFF)
                .addFlashMode(CameraFlashMode.AUTO)
                .addFlashMode(CameraFlashMode.ON)
                .hdr(true)
                .iso(true, 100, 3200)
                .shutter(true, 1_000_000L, 500_000_000L)
                .manualFocus(true, 10f)
                .whiteBalance(true, Arrays.asList("AUTO", "DAYLIGHT"))
                .exposureCompensation(true, -2f, 2f, 0.5f)
                .video(true, Arrays.asList("1920x1080", "3840x2160"),
                        Arrays.asList(30, 60))
                .audio(true)
                .build();
    }

    // ------------------------------------------------------------------
    // state machine
    // ------------------------------------------------------------------

    @Test
    public void stateMachineAllowsOpenFlow() throws Exception {
        CameraStateMachine machine = new CameraStateMachine();
        assertEquals(CameraState.IDLE, machine.getState());
        machine.transition(CameraState.OPENING);
        machine.transition(CameraState.PREVIEW);
        assertTrue(machine.isOpen());
        machine.transition(CameraState.CLOSED);
        assertFalse(machine.isOpen());
    }

    @Test
    public void stateMachineRejectsIllegalTransition() {
        CameraStateMachine machine = new CameraStateMachine();
        try {
            machine.transition(CameraState.RECORDING);
            fail("IDLE -> RECORDING must be rejected");
        } catch (CameraException expected) {
            assertEquals(CameraErrorCodes.BUSY, expected.getCode());
        }
        assertEquals(CameraState.IDLE, machine.getState());
    }

    @Test
    public void stateMachineTracksRecordingAndPause() throws Exception {
        CameraStateMachine machine = new CameraStateMachine();
        machine.transition(CameraState.OPENING);
        machine.transition(CameraState.PREVIEW);
        machine.transition(CameraState.RECORDING);
        assertTrue(machine.isRecording());
        machine.transition(CameraState.PAUSED);
        assertTrue(machine.isRecording());
        machine.transition(CameraState.RECORDING);
        machine.transition(CameraState.PREVIEW);
        assertFalse(machine.isRecording());
    }

    @Test
    public void tryTransitionNeverThrows() {
        CameraStateMachine machine = new CameraStateMachine();
        assertFalse(machine.tryTransition(CameraState.PAUSED));
        assertEquals(CameraState.IDLE, machine.getState());
        assertTrue(machine.tryTransition(CameraState.OPENING));
    }

    // ------------------------------------------------------------------
    // capabilities
    // ------------------------------------------------------------------

    @Test
    public void unsupportedCapabilitiesReportNoHardware() {
        CameraCapabilities capabilities = CameraCapabilities.unsupported();
        assertFalse(capabilities.isSupported());
        assertFalse(capabilities.supportsFlash(CameraFlashMode.AUTO));
        assertFalse(capabilities.isManualIsoSupported());
        assertTrue(capabilities.getLenses().isEmpty());
    }

    @Test
    public void capabilitiesExposeOnlyDeclaredFeatures() {
        CameraCapabilities capabilities = supportedCapabilities();
        assertTrue(capabilities.isSupported());
        assertTrue(capabilities.supportsLens(CameraLens.FRONT));
        assertTrue(capabilities.supportsFlash(CameraFlashMode.AUTO));
        assertFalse(capabilities.supportsFlash(CameraFlashMode.TORCH));
        assertEquals(3200, capabilities.getMaxIso());
        assertTrue(capabilities.getVideoResolutions().contains("3840x2160"));
    }

    // ------------------------------------------------------------------
    // configuration validation
    // ------------------------------------------------------------------

    @Test
    public void configurationRejectsUnsupportedDevice() {
        NativeApiError error = CameraConfiguration.defaults()
                .validate(CameraCapabilities.unsupported());
        assertNotNull(error);
        assertEquals(CameraErrorCodes.UNSUPPORTED, error.getCode());
    }

    @Test
    public void configurationRejectsQualityOutOfRange() {
        CameraConfiguration configuration = CameraConfiguration.defaults().toBuilder()
                .jpegQuality(CameraLimits.MAX_JPEG_QUALITY + 1)
                .build();
        NativeApiError error = configuration.validate(supportedCapabilities());
        assertNotNull(error);
        assertEquals(CameraErrorCodes.INVALID_REQUEST, error.getCode());
    }

    @Test
    public void configurationRejectsOversizedPhoto() {
        CameraConfiguration configuration = CameraConfiguration.defaults().toBuilder()
                .photoSize(9000, 9000)
                .build();
        NativeApiError error = configuration.validate(supportedCapabilities());
        assertNotNull(error);
        assertEquals(CameraErrorCodes.INVALID_RESOLUTION, error.getCode());
    }

    @Test
    public void configurationAcceptsSupportedDefaults() {
        CameraConfiguration configuration = CameraConfiguration.defaults().toBuilder()
                .lens(CameraLens.BACK)
                .flashMode(CameraFlashMode.AUTO)
                .jpegQuality(95)
                .build();
        assertNull(configuration.validate(supportedCapabilities()));
    }

    // ------------------------------------------------------------------
    // request validation
    // ------------------------------------------------------------------

    @Test
    public void photoRequestRejectsOversizedBurst() {
        PhotoCaptureRequest request = new PhotoCaptureRequest.Builder()
                .burstCount(CameraLimits.MAX_BURST_COUNT + 1)
                .build();
        NativeApiError error = request.validate();
        assertNotNull(error);
        assertEquals(CameraErrorCodes.INVALID_REQUEST, error.getCode());
    }

    @Test
    public void photoRequestRejectsLongSelfTimer() {
        PhotoCaptureRequest request = new PhotoCaptureRequest.Builder()
                .selfTimerMs(CameraLimits.MAX_SELF_TIMER_MS + 1)
                .build();
        assertNotNull(request.validate());
    }

    @Test
    public void photoRequestAcceptsValidInput() {
        PhotoCaptureRequest request = new PhotoCaptureRequest.Builder()
                .key("photo")
                .selfTimerMs(3000)
                .burstCount(3)
                .jpegQuality(90)
                .build();
        assertNull(request.validate());
        assertEquals(3000, request.getSelfTimerMs());
        assertEquals(3, request.getBurstCount());
    }

    @Test
    public void videoRequestRejectsOverlongDuration() {
        VideoRecordingRequest request = new VideoRecordingRequest.Builder()
                .maxDurationMs(CameraLimits.MAX_VIDEO_DURATION_MS + 1)
                .build();
        NativeApiError error = request.validate();
        assertNotNull(error);
        assertEquals(CameraErrorCodes.INVALID_REQUEST, error.getCode());
    }

    @Test
    public void videoRequestAcceptsValidInput() {
        VideoRecordingRequest request = new VideoRecordingRequest.Builder()
                .key("video")
                .size(1920, 1080)
                .frameRate(30)
                .withAudio(true)
                .build();
        assertNull(request.validate());
        assertTrue(request.isWithAudio());
    }

    // ------------------------------------------------------------------
    // project isolation
    // ------------------------------------------------------------------

    @Test
    public void projectContextIsolatesProjects() {
        assertFalse(CameraProjectContext.hasActiveProject());
        CameraProjectContext.setActiveProject("project-a");
        assertEquals("project-a", CameraProjectContext.getActiveProject());
        CameraProjectContext.setActiveProject("project-b");
        assertEquals("project-b", CameraProjectContext.getActiveProject());
        CameraProjectContext.clear();
        assertFalse(CameraProjectContext.hasActiveProject());
        assertEquals("", CameraProjectContext.getActiveProject());
    }

    @Test
    public void projectContextIgnoresBlankIdentifiers() {
        CameraProjectContext.setActiveProject("project-a");
        CameraProjectContext.setActiveProject("   ");
        assertEquals("project-a", CameraProjectContext.getActiveProject());
        CameraProjectContext.setActiveProject(null);
        assertEquals("project-a", CameraProjectContext.getActiveProject());
    }

    // ------------------------------------------------------------------
    // media metadata
    // ------------------------------------------------------------------

    @Test
    public void codecRoundTripsMediaItem() {
        CameraMediaItem item = new CameraMediaItem.Builder()
                .id("media-1")
                .projectId("project-a")
                .type(CameraMediaItem.TYPE_VIDEO)
                .fileName("clip.mp4")
                .relativePath("project-a/clip.mp4")
                .thumbnailPath("project-a/clip.jpg")
                .mimeType("video/mp4")
                .sizeBytes(2048)
                .size(1920, 1080)
                .durationMs(4200)
                .createdAt(1_700_000_000_000L)
                .state(CameraMediaItem.STATE_READY)
                .build();
        CameraMediaItem decoded = CameraCodec.decode(CameraCodec.encode(item));
        assertNotNull(decoded);
        assertEquals(item.getId(), decoded.getId());
        assertEquals(item.getProjectId(), decoded.getProjectId());
        assertEquals(item.getRelativePath(), decoded.getRelativePath());
        assertEquals(item.getDurationMs(), decoded.getDurationMs());
        assertTrue(decoded.isVideo());
    }

    @Test
    public void codecRejectsCorruptLines() {
        assertNull(CameraCodec.decode(null));
        assertNull(CameraCodec.decode(""));
        assertNull(CameraCodec.decode("not-a-valid-record"));
    }

    @Test
    public void mediaItemMapRoundTripKeepsOwnership() {
        CameraMediaItem item = new CameraMediaItem.Builder()
                .id("media-2")
                .projectId("project-b")
                .type(CameraMediaItem.TYPE_PHOTO)
                .fileName("shot.jpg")
                .relativePath("project-b/shot.jpg")
                .sizeBytes(512)
                .build();
        CameraMediaItem copy = CameraMediaItem.fromMap(item.toMap());
        assertNotNull(copy);
        assertEquals("project-b", copy.getProjectId());
        assertEquals("project-b/shot.jpg", copy.getRelativePath());
        assertFalse(copy.isVideo());
        assertTrue(copy.isReady());
    }
}
