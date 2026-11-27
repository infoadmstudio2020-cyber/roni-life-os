package com.roni.library.themeengine.observer;

/**
 * Forwarding alias to com.roni.observer.theme.life.SystemThemeObserver for backward compatibility.
 */
public class SystemThemeObserver extends com.roni.library.theme.observer.SystemThemeObserver {

    public SystemThemeObserver() {
        super();
    }

    public SystemThemeObserver(boolean initialDark) {
        super(initialDark);
    }
}
