package com.roni.library.platform.camera;

import androidx.annotation.NonNull;

import com.roni.library.contracts.camera.CameraErrorCodes;
import com.roni.library.contracts.camera.CameraState;

/**
 * Guards every lifecycle transition so an interrupted session can never leave the engine in an
 * impossible state (for example recording while closed).
 */
public final class CameraStateMachine {

    private CameraState state = CameraState.IDLE;

    @NonNull
    public synchronized CameraState getState() {
        return state;
    }

    public synchronized boolean isOpen() {
        return state.isOpen();
    }

    public synchronized boolean isRecording() {
        return state.isRecording();
    }

    public synchronized void reset() {
        state = CameraState.IDLE;
    }

    /** @throws CameraException when the transition is not legal from the current state. */
    public synchronized void transition(@NonNull CameraState next) throws CameraException {
        if (!isAllowed(state, next)) {
            throw new CameraException(CameraErrorCodes.BUSY,
                    "Cannot move from " + state.name() + " to " + next.name());
        }
        state = next;
    }

    /** Moves to {@code next} only when legal; used by recovery paths that must not throw. */
    public synchronized boolean tryTransition(@NonNull CameraState next) {
        if (!isAllowed(state, next)) {
            return false;
        }
        state = next;
        return true;
    }

    private static boolean isAllowed(@NonNull CameraState from, @NonNull CameraState to) {
        if (from == to) {
            return true;
        }
        switch (from) {
            case IDLE:
                return to == CameraState.OPENING || to == CameraState.CLOSED;
            case OPENING:
                return to == CameraState.PREVIEW || to == CameraState.ERROR
                        || to == CameraState.CLOSED;
            case PREVIEW:
                return to == CameraState.CAPTURING || to == CameraState.RECORDING
                        || to == CameraState.CLOSED || to == CameraState.ERROR;
            case CAPTURING:
                return to == CameraState.PREVIEW || to == CameraState.ERROR
                        || to == CameraState.CLOSED;
            case RECORDING:
                return to == CameraState.PAUSED || to == CameraState.PREVIEW
                        || to == CameraState.ERROR || to == CameraState.CLOSED;
            case PAUSED:
                return to == CameraState.RECORDING || to == CameraState.PREVIEW
                        || to == CameraState.ERROR || to == CameraState.CLOSED;
            case CLOSED:
                return to == CameraState.OPENING || to == CameraState.IDLE;
            case ERROR:
                return to == CameraState.OPENING || to == CameraState.IDLE
                        || to == CameraState.CLOSED;
            default:
                return false;
        }
    }
}
