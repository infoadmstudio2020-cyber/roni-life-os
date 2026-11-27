package com.roni.library.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Certificate pin registry.
 *
 * <p>Pinning is opt-in per host. {@link #verify(String, String)} is fail-closed: when at least one
 * pin is configured for a host, a presented pin that matches none of them is rejected. A host with
 * no configured pins is treated as "pinning disabled" and accepted.</p>
 */
public class SslPinningManager {

    private final Map<String, List<String>> hostPins = new ConcurrentHashMap<>();

    public SslPinningManager() {}

    public void addPin(String host, String pin) {
        if (host == null || pin == null) return;
        hostPins.computeIfAbsent(host.toLowerCase(Locale.US), k -> new CopyOnWriteArrayList<>()).add(pin);
    }

    public boolean hasPinsForHost(String host) {
        if (host == null) return false;
        List<String> pins = hostPins.get(host.toLowerCase(Locale.US));
        return pins != null && !pins.isEmpty();
    }

    public List<String> getPins(String host) {
        if (host == null) return Collections.emptyList();
        List<String> pins = hostPins.get(host.toLowerCase(Locale.US));
        return pins != null ? new ArrayList<>(pins) : Collections.emptyList();
    }

    /**
     * Verifies a presented certificate pin against the configured set for {@code host}.
     *
     * @param host          the host being connected to
     * @param presentedPin  the pin computed from the server certificate
     * @return {@code true} when pinning is disabled for the host or the pin matches;
     *         {@code false} when pins are configured and none match
     */
    public boolean verify(String host, String presentedPin) {
        List<String> pins = getPins(host);
        if (pins.isEmpty()) {
            return true;
        }
        if (presentedPin == null) {
            return false;
        }
        for (String pin : pins) {
            if (pin.equalsIgnoreCase(presentedPin)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        hostPins.clear();
    }
}
