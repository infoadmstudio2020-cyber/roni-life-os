package com.roni.library.platform.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.ServiceStatus;
import com.roni.library.contracts.service.IService;
import com.roni.library.service.ServiceRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Lifecycle facade over the existing NativeServiceSDK registry.
 *
 * <p>No second service container is created: services are discovered, started and stopped
 * through {@link ServiceRegistry}.</p>
 */
public final class ServiceEngine {

    @NonNull
    public List<ServiceStatus> list() {
        List<ServiceStatus> result = new ArrayList<>();
        try {
            for (IService service : ServiceRegistry.getInstance().getAllServices()) {
                result.add(toStatus(service));
            }
        } catch (Throwable ignored) {
            // A missing registry means no services are available yet.
        }
        return result;
    }

    @NonNull
    public ServiceStatus status(@NonNull String name) throws AutomationException {
        IService service = find(name);
        if (service == null) {
            throw new AutomationException(AutomationErrorCodes.SERVICE_NOT_FOUND,
                    "Unknown service: " + name);
        }
        return toStatus(service);
    }

    public boolean start(@NonNull String name) throws AutomationException {
        IService service = find(name);
        if (service == null) {
            throw new AutomationException(AutomationErrorCodes.SERVICE_NOT_FOUND,
                    "Unknown service: " + name);
        }
        try {
            service.onStart();
            return true;
        } catch (Throwable error) {
            throw new AutomationException(AutomationErrorCodes.IO_ERROR,
                    "Cannot start service: " + error.getMessage(), error);
        }
    }

    public boolean stop(@NonNull String name) throws AutomationException {
        IService service = find(name);
        if (service == null) {
            throw new AutomationException(AutomationErrorCodes.SERVICE_NOT_FOUND,
                    "Unknown service: " + name);
        }
        try {
            service.onStop();
            return true;
        } catch (Throwable error) {
            throw new AutomationException(AutomationErrorCodes.IO_ERROR,
                    "Cannot stop service: " + error.getMessage(), error);
        }
    }

    @Nullable
    private IService find(@NonNull String name) {
        try {
            return ServiceRegistry.getInstance().getServiceByName(name);
        } catch (Throwable error) {
            return null;
        }
    }

    @NonNull
    private static ServiceStatus toStatus(@NonNull IService service) {
        String name;
        String state;
        try {
            name = service.getServiceName();
        } catch (Throwable error) {
            name = service.getClass().getSimpleName();
        }
        try {
            state = service.isRunning() ? "RUNNING" : "STOPPED";
        } catch (Throwable error) {
            state = "UNKNOWN";
        }
        return new ServiceStatus(name, state, service.getClass().getSimpleName(),
                System.currentTimeMillis());
    }
}
