package com.roni.library.ui.handler;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.model.BatteryStatusDTO;
import com.roni.library.platform.model.DeviceInfoDTO;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.ui.host.NativeUIHost;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Native device, app, clipboard, sharing, orientation and system utilities
 * exposed through the {@code device.*} bridge namespace.
 */
public class NativeDeviceCommandHandler implements IApiHandler {

    private static final String TAG = "NativeDeviceHandler";

    public static final String[] SUPPORTED_ACTIONS = {
            "getInfo", "getAppInfo", "getDeviceInfo", "getScreenInfo", "getLocale",
            "getNetworkState", "getBattery", "getSafeArea",
            "copyToClipboard", "setClipboard", "readClipboard", "getClipboard",
            "shareText", "share", "vibrate", "haptic", "openUrl", "openExternal",
            "setOrientation", "setKeepScreenOn", "setBrightness", "getBrightness",
            "finish", "openAppSettings", "isAppInstalled", "openApp", "openFile", "log"
    };

    private final NativeUIHost uiHost;

    public NativeDeviceCommandHandler(NativeUIHost uiHost) {
        this.uiHost = uiHost != null ? uiHost : new NativeUIHost();
    }

    @Override
    public String getPath() {
        return "device";
    }

    @Override
    public String getAction() {
        return "*";
    }

    @Override
    public NativeApiResponse handleRequest(NativeApiRequest request) {
        if (request == null) {
            return NativeApiResponse.error("unknown",
                    new NativeApiError("INVALID_REQUEST", "Request is null"));
        }

        String reqId = request.getRequestId();
        String action = normalizeAction(request.getAction());
        if (action.isEmpty()) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("MISSING_ACTION", "Action cannot be empty"));
        }

        Context context = context();
        Activity activity = activity();

        try {
            switch (action) {
                case "getInfo": {
                    Map<String, Object> data = new HashMap<>();
                    putAppInfo(data, context);
                    putDeviceInfo(data);
                    putScreenInfo(data, activity);
                    putLocale(data);
                    putSafeArea(data, activity);
                    return NativeApiResponse.success(reqId, data);
                }
                case "getAppInfo": {
                    Map<String, Object> data = new HashMap<>();
                    putAppInfo(data, context);
                    return NativeApiResponse.success(reqId, data);
                }
                case "getDeviceInfo": {
                    Map<String, Object> data = new HashMap<>();
                    putDeviceInfo(data);
                    return NativeApiResponse.success(reqId, data);
                }
                case "getScreenInfo": {
                    Map<String, Object> data = new HashMap<>();
                    putScreenInfo(data, activity);
                    return NativeApiResponse.success(reqId, data);
                }
                case "getLocale": {
                    Map<String, Object> data = new HashMap<>();
                    putLocale(data);
                    return NativeApiResponse.success(reqId, data);
                }
                case "getNetworkState": {
                    Map<String, Object> data = new HashMap<>();
                    PlatformEngine platform = platform();
                    if (platform != null) {
                        try {
                            Map<String, Object> status =
                                    platform.getNetworkService().getNetworkStatus();
                            if (status != null) {
                                data.putAll(status);
                            }
                            data.put("connected", Boolean.TRUE.equals(data.get("connected")));
                            Object type = data.get("type");
                            data.put("type", type != null ? String.valueOf(type) : "none");
                            data.put("metered", Boolean.TRUE.equals(data.get("metered")));
                            data.put("roaming", Boolean.TRUE.equals(data.get("roaming")));
                            data.put("source", "platform");
                            return NativeApiResponse.success(reqId, data);
                        } catch (Throwable t) {
                            Log.w(TAG, "PlatformEngine network state unavailable, using system service", t);
                        }
                    }
                    if (context == null) return unavailable(reqId);
                    ConnectivityManager manager = (ConnectivityManager)
                            context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo info = manager != null ? manager.getActiveNetworkInfo() : null;
                    data.put("connected", info != null && info.isConnected());
                    data.put("type", info != null ? info.getTypeName() : "none");
                    data.put("metered", manager != null && manager.isActiveNetworkMetered());
                    data.put("roaming", info != null && info.isRoaming());
                    data.put("source", "system");
                    return NativeApiResponse.success(reqId, data);
                }
                case "getBattery": {
                    Map<String, Object> data = new HashMap<>();
                    PlatformEngine platform = platform();
                    if (platform != null) {
                        try {
                            BatteryStatusDTO status = platform.getBatteryService().getBatteryStatus();
                            if (status != null) {
                                int percent = status.getLevel();
                                data.put("level", percent);
                                data.put("scale", 100);
                                data.put("percent", percent);
                                data.put("status", status.isCharging() ? 2 : 3);
                                data.put("plugged", plugTypeCode(status.getPlugType()));
                                data.put("plugType", status.getPlugType());
                                data.put("temperature", (int) (status.getTemperature() * 10));
                                data.put("voltage", -1);
                                data.put("charging", status.isCharging());
                                data.put("health", status.getHealthStatus());
                                data.put("source", "platform");
                                return NativeApiResponse.success(reqId, data);
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "PlatformEngine battery unavailable, using broadcast", t);
                        }
                    }
                    if (context == null) return unavailable(reqId);
                    Intent battery = context.registerReceiver(null,
                            new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (battery != null) {
                        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        data.put("level", level);
                        data.put("scale", scale);
                        data.put("percent", scale > 0 ? (level * 100 / scale) : -1);
                        data.put("status", battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
                        data.put("plugged", battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
                        data.put("temperature", battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
                        data.put("voltage", battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
                    }
                    data.put("source", "system");
                    return NativeApiResponse.success(reqId, data);
                }
                case "getSafeArea": {
                    Map<String, Object> data = new HashMap<>();
                    putSafeArea(data, activity);
                    return NativeApiResponse.success(reqId, data);
                }
                case "copyToClipboard":
                case "setClipboard": {
                    String text = request.getStringParam("text", "");
                    String label = request.getStringParam("label", "NativeRuntime");
                    PlatformEngine platform = platform();
                    if (platform != null) {
                        try {
                            return success(reqId, "copied",
                                    platform.getClipboardService().setText(text));
                        } catch (Throwable t) {
                            Log.w(TAG, "PlatformEngine clipboard unavailable, using system service", t);
                        }
                    }
                    if (context == null) return unavailable(reqId);
                    ClipboardManager manager = (ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (manager != null) {
                        manager.setPrimaryClip(ClipData.newPlainText(label, text));
                    }
                    return success(reqId, "copied", true);
                }
                case "readClipboard":
                case "getClipboard": {
                    PlatformEngine platform = platform();
                    if (platform != null) {
                        try {
                            String text = platform.getClipboardService().getText();
                            return success(reqId, "text", text != null ? text : "");
                        } catch (Throwable t) {
                            Log.w(TAG, "PlatformEngine clipboard unavailable, using system service", t);
                        }
                    }
                    if (context == null) return unavailable(reqId);
                    ClipboardManager manager = (ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    String text = "";
                    if (manager != null && manager.getPrimaryClip() != null
                            && manager.getPrimaryClip().getItemCount() > 0) {
                        CharSequence value = manager.getPrimaryClip().getItemAt(0).coerceToText(context);
                        text = value != null ? value.toString() : "";
                    }
                    return success(reqId, "text", text);
                }
                case "shareText":
                case "share": {
                    if (context == null) return unavailable(reqId);
                    String text = request.getStringParam("text", "");
                    String subject = request.getStringParam("subject", "");
                    String title = request.getStringParam("title", "Share");
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    if (!subject.isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, subject);
                    Intent chooser = Intent.createChooser(send, title);
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(chooser);
                    return success(reqId, "shared", true);
                }
                case "vibrate":
                case "haptic": {
                    if (activity == null) return unavailable(reqId);
                    View decor = activity.getWindow().getDecorView();
                    decor.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    return success(reqId, "vibrated", true);
                }
                case "openUrl":
                case "openExternal": {
                    if (context == null) return unavailable(reqId);
                    String url = request.getStringParam("url", "");
                    if (url.isEmpty()) return success(reqId, "opened", false);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return success(reqId, "opened", true);
                }
                case "setOrientation": {
                    if (activity == null) return unavailable(reqId);
                    String orientation = request.getStringParam("orientation", "auto")
                            .toLowerCase(Locale.US);
                    int value;
                    switch (orientation) {
                        case "portrait": value = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; break;
                        case "landscape": value = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; break;
                        case "reverse_portrait": value = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT; break;
                        case "reverse_landscape": value = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE; break;
                        case "sensor": value = ActivityInfo.SCREEN_ORIENTATION_SENSOR; break;
                        default: value = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; break;
                    }
                    activity.setRequestedOrientation(value);
                    return success(reqId, "orientation", orientation);
                }
                case "setKeepScreenOn": {
                    if (activity == null) return unavailable(reqId);
                    boolean enabled = booleanValue(request.getParameters().get("enabled"), true);
                    if (enabled) {
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                    return success(reqId, "enabled", enabled);
                }
                case "setBrightness": {
                    if (activity == null) return unavailable(reqId);
                    double value = doubleValue(request.getParameters().get("value"), -1);
                    WindowManager.LayoutParams params = activity.getWindow().getAttributes();
                    params.screenBrightness = value < 0
                            ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            : (float) Math.max(0d, Math.min(1d, value));
                    activity.getWindow().setAttributes(params);
                    return success(reqId, "brightness", params.screenBrightness);
                }
                case "getBrightness": {
                    if (activity == null) return unavailable(reqId);
                    return success(reqId, "brightness",
                            activity.getWindow().getAttributes().screenBrightness);
                }
                case "finish": {
                    if (activity == null) return unavailable(reqId);
                    activity.finish();
                    return success(reqId, "finished", true);
                }
                case "openAppSettings": {
                    if (context == null) return unavailable(reqId);
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return success(reqId, "opened", true);
                }
                case "isAppInstalled": {
                    if (context == null) return unavailable(reqId);
                    String packageName = request.getStringParam("packageName", "");
                    boolean installed = false;
                    try {
                        context.getPackageManager().getPackageInfo(packageName, 0);
                        installed = true;
                    } catch (Throwable ignored) {
                    }
                    return success(reqId, "installed", installed);
                }
                case "openApp": {
                    if (context == null) return unavailable(reqId);
                    String packageName = request.getStringParam("packageName", "");
                    Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launch);
                    }
                    return success(reqId, "opened", launch != null);
                }
                case "openFile": {
                    if (context == null) return unavailable(reqId);
                    String uri = request.getStringParam("uri", "");
                    String mime = request.getStringParam("mimeType", "*/*");
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(uri), mime);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(intent);
                    return success(reqId, "opened", true);
                }
                case "log": {
                    Log.d(request.getStringParam("tag", "NativeRuntimeWeb"),
                            request.getStringParam("message", ""));
                    return success(reqId, "logged", true);
                }
                default:
                    return NativeApiResponse.error(reqId,
                            new NativeApiError("UNKNOWN_ACTION", "Action not supported: " + action));
            }
        } catch (Throwable t) {
            return NativeApiResponse.error(reqId,
                    new NativeApiError("INTERNAL_ERROR", t.getMessage()));
        }
    }

    private Context context() {
        Activity activity = activity();
        return activity != null ? activity : null;
    }

    private Activity activity() {
        Activity activity = uiHost.getActivity();
        if (activity != null) return activity;
        return uiHost.getUIComponentManager() != null
                ? uiHost.getUIComponentManager().getActivity() : null;
    }

    private NativeApiResponse unavailable(String reqId) {
        return NativeApiResponse.error(reqId,
                new NativeApiError("ACTIVITY_UNAVAILABLE", "No active Activity is attached"));
    }

    private NativeApiResponse success(String reqId, String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return NativeApiResponse.success(reqId, data);
    }

    private String normalizeAction(String action) {
        if (action == null) return "";
        String value = action.trim();
        if (value.startsWith("device.")) return value.substring(7);
        if (value.startsWith("/device/")) return value.substring(8);
        return value;
    }

    private void putAppInfo(Map<String, Object> data, Context context) {
        if (context == null) return;
        data.put("packageName", context.getPackageName());
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            data.put("versionName", info.versionName);
            data.put("versionCode", Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode);
            data.put("appLabel", context.getApplicationInfo()
                    .loadLabel(context.getPackageManager()).toString());
        } catch (Throwable ignored) {
        }
    }

    private void putDeviceInfo(Map<String, Object> data) {
        PlatformEngine platform = platform();
        if (platform != null) {
            try {
                DeviceInfoDTO info = platform.getDeviceService().getDeviceInfo();
                if (info != null) {
                    data.put("manufacturer", info.getManufacturer());
                    data.put("brand", info.getBrand());
                    data.put("model", info.getModel());
                    data.put("device", info.getDevice());
                    data.put("osVersion", info.getOsVersion());
                    data.put("sdkInt", info.getSdkInt());
                    data.put("architecture", info.getArchitecture());
                    data.put("totalMemoryBytes", info.getTotalMemoryBytes());
                    data.put("freeMemoryBytes", info.getFreeMemoryBytes());
                }
            } catch (Throwable t) {
                Log.w(TAG, "PlatformEngine device info unavailable, using Build", t);
            }
        }
        // Fill any gap left by the platform service so the response shape never changes.
        if (!data.containsKey("manufacturer")) data.put("manufacturer", Build.MANUFACTURER);
        if (!data.containsKey("brand")) data.put("brand", Build.BRAND);
        if (!data.containsKey("model")) data.put("model", Build.MODEL);
        if (!data.containsKey("device")) data.put("device", Build.DEVICE);
        if (!data.containsKey("osVersion")) data.put("osVersion", Build.VERSION.RELEASE);
        if (!data.containsKey("sdkInt")) data.put("sdkInt", Build.VERSION.SDK_INT);
        data.put("product", Build.PRODUCT);
    }

    /**
     * @return the platform engine when it is initialised, otherwise {@code null} so the caller
     *         can fall back to the Activity/Context-scoped implementation
     */
    private PlatformEngine platform() {
        try {
            PlatformEngine engine = PlatformEngine.getInstance();
            return engine != null && engine.isInitialized() ? engine : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int plugTypeCode(String plugType) {
        if (plugType == null) return 0;
        String value = plugType.toUpperCase(Locale.US);
        if (value.contains("AC")) return 1;
        if (value.contains("USB")) return 2;
        if (value.contains("WIRELESS")) return 4;
        return 0;
    }

    private void putScreenInfo(Map<String, Object> data, Activity activity) {
        if (activity == null) return;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        data.put("screenWidth", metrics.widthPixels);
        data.put("screenHeight", metrics.heightPixels);
        data.put("density", metrics.density);
        data.put("densityDpi", metrics.densityDpi);
        data.put("orientation", activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait");
        data.put("statusBarHeight", dimen(activity, "status_bar_height"));
        data.put("navigationBarHeight", dimen(activity, "navigation_bar_height"));
    }

    private void putLocale(Map<String, Object> data) {
        Locale locale = Locale.getDefault();
        data.put("locale", locale.toString());
        data.put("language", locale.getLanguage());
        data.put("country", locale.getCountry());
    }

    private void putSafeArea(Map<String, Object> data, Activity activity) {
        data.put("safeAreaTop", safeArea(activity, 0));
        data.put("safeAreaBottom", safeArea(activity, 1));
        data.put("safeAreaLeft", safeArea(activity, 2));
        data.put("safeAreaRight", safeArea(activity, 3));
    }

    private int dimen(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? activity.getResources().getDimensionPixelSize(id) : 0;
    }

    private int safeArea(Activity activity, int side) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 0;
        WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (insets == null) return 0;
        switch (side) {
            case 0: return insets.getSystemWindowInsetTop();
            case 1: return insets.getSystemWindowInsetBottom();
            case 2: return insets.getSystemWindowInsetLeft();
            case 3: return insets.getSystemWindowInsetRight();
            default: return 0;
        }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }
}
