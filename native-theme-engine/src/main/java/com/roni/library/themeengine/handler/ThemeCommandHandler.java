package com.roni.library.themeengine.handler;

import com.roni.library.theme.manager.ThemeManager;

/**
 * Forwarding alias to {@code com.roni.library.theme.handler.ThemeCommandHandler}.
 *
 * @deprecated duplicate of {@code com.roni.library.theme.handler.ThemeCommandHandler}; it exists
 *     only so old imports keep compiling. Use the {@code theme.handler} package instead. This
 *     class is not registered on the bridge and must not be, otherwise two handlers would claim
 *     the {@code theme} route.
 */
@Deprecated
public class ThemeCommandHandler extends com.roni.library.theme.handler.ThemeCommandHandler {

    public ThemeCommandHandler(ThemeManager themeManager) {
        super(themeManager);
    }
}
