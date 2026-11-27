package com.roni.library.storage;

/**
 * Project-scoped key namespacing for {@link EncryptedStore} and {@link KeyValueStore}.
 *
 * <p>Every Web Project shares one {@code EncryptedStore} instance, so a raw key such as
 * {@code "theme"} would be visible to every project. This class derives a deterministic,
 * collision-free prefix from a project id and maps keys in and out of that namespace.</p>
 *
 * <p>Layout: {@code __p:<escaped-project-id>:<user-key>}. The {@code __p:} marker is reserved;
 * keys without it belong to the legacy (pre-scoping) namespace, which keeps existing installs
 * readable when no project id is configured.</p>
 *
 * <p>Escaping is injective: {@code '%'} becomes {@code "%25"} and {@code ':'} becomes
 * {@code "%3A"}, so two different project ids can never produce the same prefix.</p>
 */
public final class StorageScope {

    /** Reserved namespace marker. User keys starting with this are treated as project-scoped. */
    private static final String PREFIX = "__p:";

    private static final String SEP = ":";

    /**
     * Global marker recording that the legacy plaintext preferences were already claimed by
     * one project. It lives outside every project namespace so it is never listed to a project.
     */
    public static final String LEGACY_CLAIM_KEY = "__p:__legacy_claimed";

    private StorageScope() {
    }

    /**
     * @param projectId project identifier, may be {@code null} or blank
     * @return {@code "__p:<id>:"}, or {@code ""} for the legacy shared namespace
     */
    public static String prefixFor(String projectId) {
        if (projectId == null) {
            return "";
        }
        String id = projectId.trim();
        if (id.isEmpty()) {
            return "";
        }
        return PREFIX + escape(id) + SEP;
    }

    /** Maps a user key into the project namespace. */
    public static String scopedKey(String projectId, String key) {
        return prefixFor(projectId) + (key != null ? key : "");
    }

    /**
     * Maps a stored key back to the user key.
     *
     * @return the user key, or {@code null} when the stored key belongs to another namespace
     */
    public static String unscopedKey(String projectId, String storedKey) {
        if (storedKey == null) {
            return null;
        }
        String prefix = prefixFor(projectId);
        if (prefix.isEmpty()) {
            return isProjectScoped(storedKey) ? null : storedKey;
        }
        if (!storedKey.startsWith(prefix)) {
            return null;
        }
        return storedKey.substring(prefix.length());
    }

    /** @return {@code true} when {@code storedKey} is visible to {@code projectId} */
    public static boolean belongsTo(String projectId, String storedKey) {
        return unscopedKey(projectId, storedKey) != null;
    }

    /** @return {@code true} when the key lives in some project namespace */
    public static boolean isProjectScoped(String storedKey) {
        return storedKey != null && storedKey.startsWith(PREFIX);
    }

    /**
     * @return {@code true} when {@code key} cannot be stored in this namespace because it would
     *         collide with the reserved {@code __p:} marker. Only the legacy namespace is
     *         affected; project namespaces escape their own prefix, so any user key is legal there.
     */
    public static boolean isReserved(String projectId, String key) {
        return prefixFor(projectId).isEmpty() && isProjectScoped(key);
    }

    static String escape(String id) {
        StringBuilder out = new StringBuilder(id.length() + 8);
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '%') {
                out.append("%25");
            } else if (c == ':') {
                out.append("%3A");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
