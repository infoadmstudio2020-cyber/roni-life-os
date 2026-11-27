/**
 * LauncherIconAssets — shared vector launcher icon resource set.
 *
 * Generates a complete, buildable `@mipmap/ic_launcher` resource set that
 * resolves correctly across minSdk 21 → targetSdk 26+:
 *
 *   - mipmap-anydpi-v26/ic_launcher.xml   → adaptive icon (Android 8.0+)
 *   - mipmap-anydpi/ic_launcher.xml       → legacy vector fallback (API 21–25)
 *   - drawable/ic_launcher_foreground.xml → Android robot vector foreground
 *   - values/ic_launcher_background.xml   → #3DDC84 background color resource
 *
 * Both AndroidLibraryTemplate (sample app module) and AndroidAppTemplate
 * consume this module, so generated projects never reference a missing
 * `@mipmap/ic_launcher` resource and never rely on the Android system
 * default icon (`@android:drawable/sym_def_app_icon`).
 */

/** Background color used by the launcher icon (Android green). */
const ICON_BACKGROUND_COLOR = '#3DDC84';

/**
 * Android robot glyph — Material-style vector artwork.
 * 108×108 viewport; all artwork stays inside the 66dp adaptive-icon safe zone.
 */
const ROBOT_PATHS_XML = `
    <!-- Left antenna -->
    <path
        android:pathData="M44,37 L40,33"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="2.2"
        android:strokeLineCap="round" />

    <!-- Right antenna -->
    <path
        android:pathData="M64,37 L68,33"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="2.2"
        android:strokeLineCap="round" />

    <!-- Head dome -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M35,54 a19,19 0 0 1 38,0 z" />

    <!-- Eyes (background-colored cut-outs) -->
    <path
        android:fillColor="${ICON_BACKGROUND_COLOR}"
        android:pathData="M47,48 m-2.6,0 a2.6,2.6 0 1 0 5.2,0 a2.6,2.6 0 1 0 -5.2,0" />
    <path
        android:fillColor="${ICON_BACKGROUND_COLOR}"
        android:pathData="M61,48 m-2.6,0 a2.6,2.6 0 1 0 5.2,0 a2.6,2.6 0 1 0 -5.2,0" />

    <!-- Body -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M35,57 h38 v16.5 a3.5,3.5 0 0 1 -3.5,3.5 h-31 a3.5,3.5 0 0 1 -3.5,-3.5 z" />

    <!-- Left arm -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M29.5,57 a3,3 0 0 1 3,3 v14 a3,3 0 0 1 -6,0 v-14 a3,3 0 0 1 3,-3 z" />

    <!-- Right arm -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M78.5,57 a3,3 0 0 1 3,3 v14 a3,3 0 0 1 -6,0 v-14 a3,3 0 0 1 3,-3 z" />`;

/** Adaptive-icon foreground drawable (referenced by mipmap-anydpi-v26). */
const FOREGROUND_XML = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">${ROBOT_PATHS_XML}
</vector>
`;

/** Adaptive icon definition (API 26+) — background color + vector foreground. */
const ADAPTIVE_ICON_XML = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
`;

/** Legacy launcher icon (API 21–25) — full-bleed vector with green background. */
const LEGACY_ICON_XML = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Solid background -->
    <path
        android:fillColor="${ICON_BACKGROUND_COLOR}"
        android:pathData="M0,0 L108,0 L108,108 L0,108 Z" />${ROBOT_PATHS_XML}
</vector>
`;

/** Background color resource. */
const BACKGROUND_COLOR_XML = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">${ICON_BACKGROUND_COLOR}</color>
</resources>
`;

/**
 * Build the launcher icon resource file map for a module's res directory.
 * @param {string} resPath — module res path, e.g. 'app/src/main/res'
 * @returns {Object.<string,string>} path → XML content map
 */
export function getLauncherIconFiles(resPath = 'app/src/main/res') {
  return {
    [`${resPath}/mipmap-anydpi-v26/ic_launcher.xml`]: ADAPTIVE_ICON_XML,
    [`${resPath}/mipmap-anydpi/ic_launcher.xml`]:      LEGACY_ICON_XML,
    [`${resPath}/drawable/ic_launcher_foreground.xml`]: FOREGROUND_XML,
    [`${resPath}/values/ic_launcher_background.xml`]:   BACKGROUND_COLOR_XML,
  };
}
