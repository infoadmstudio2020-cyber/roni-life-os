package com.roni.life.os.app.source;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.roni.life.os.app.runtime.BootstrapSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Management surface for the Default/Runtime source system.
 *
 * <p>One dialog per surface. Every action delegates to {@link SourceManager}, so the UI never
 * touches files directly and the same rules apply from code and from the screen.</p>
 */
public final class SourceManagerDialog {

    /** Request code used for the runtime ZIP import picker. */
    public static final int REQUEST_IMPORT_ZIP = 0x5A11;

    private static SourceKind pendingKind;

    private SourceManagerDialog() {
    }

    /** Shows the actions available for {@code kind}. */
    public static void show(final Activity activity, final SourceKind kind) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final SourceConfig config = SourceConfigStore.load(activity, kind);
        final SourceResolver.Result resolved = SourceResolver.resolve(activity, kind, config);

        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        labels.add("Active source: " + config.getActiveSource().getLabel()
                + (resolved.isSuccess() && resolved.getSource().isFallbackUsed()
                ? " (fallback in use)" : ""));
        actions.add(null);

        if (kind.hasMode()) {
            labels.add("Mode: " + (config.isWebMode() ? "Web" : "Native") + " — tap to switch");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    report(activity, SourceManager.setMode(activity, kind,
                            config.isWebMode() ? SourceConfig.MODE_NATIVE : SourceConfig.MODE_WEB));
                    show(activity, kind);
                }
            });
        }

        labels.add("Fallback: " + (config.isFallbackEnabled() ? "on" : "off") + " — tap to switch");
        actions.add(new Runnable() {
            @Override
            public void run() {
                report(activity, SourceManager.setFallback(activity, kind,
                        !config.isFallbackEnabled()));
                show(activity, kind);
            }
        });

        labels.add("Create runtime source from default");
        actions.add(new Runnable() {
            @Override
            public void run() {
                report(activity, SourceManager.create(activity, kind));
                show(activity, kind);
            }
        });

        labels.add("Import runtime ZIP…");
        actions.add(new Runnable() {
            @Override
            public void run() {
                pendingKind = kind;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                activity.startActivityForResult(intent, REQUEST_IMPORT_ZIP);
            }
        });

        labels.add("Validate runtime source");
        actions.add(new Runnable() {
            @Override
            public void run() {
                report(activity, SourceManager.validate(activity, kind));
            }
        });

        labels.add("Activate runtime source");
        actions.add(new Runnable() {
            @Override
            public void run() {
                report(activity, SourceManager.activate(activity, kind));
                show(activity, kind);
            }
        });

        labels.add("Deactivate (use default)");
        actions.add(new Runnable() {
            @Override
            public void run() {
                report(activity, SourceManager.deactivate(activity, kind));
                show(activity, kind);
            }
        });

        labels.add("Remove runtime (keep files)");
        actions.add(new Runnable() {
            @Override
            public void run() {
                report(activity, SourceManager.remove(activity, kind));
                show(activity, kind);
            }
        });

        labels.add("Delete runtime files");
        actions.add(new Runnable() {
            @Override
            public void run() {
                confirm(activity, "Delete the runtime " + kind.getLabel() + " source?",
                        "The files are removed permanently. The default source is untouched.",
                        new Runnable() {
                            @Override
                            public void run() {
                                report(activity, SourceManager.delete(activity, kind));
                                show(activity, kind);
                            }
                        });
            }
        });

        labels.add("Reset to default");
        actions.add(new Runnable() {
            @Override
            public void run() {
                report(activity, SourceManager.reset(activity, kind));
                show(activity, kind);
            }
        });

        if (kind == SourceKind.BOOTSTRAP) {
            labels.add("Preview resolved bootstrap");
            actions.add(new Runnable() {
                @Override
                public void run() {
                    BootstrapSource.Selection selection = BootstrapSource.resolve(activity);
                    if (!selection.isUsable()) {
                        toast(activity, selection.getError());
                        return;
                    }
                    toast(activity, selection.getMessage() + " · " + selection.getEntry());
                }
            });
        }

        new AlertDialog.Builder(activity)
                .setTitle(kind.getLabel() + " sources")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Runnable action = actions.get(which);
                        if (action != null) {
                            action.run();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Handles the ZIP picker result. Returns true when the result belonged to this dialog. */
    public static boolean handleActivityResult(Activity activity, int requestCode, int resultCode,
                                               Intent data) {
        if (requestCode != REQUEST_IMPORT_ZIP) {
            return false;
        }
        SourceKind kind = pendingKind != null ? pendingKind : SourceKind.SPLASH;
        pendingKind = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        Uri uri = data.getData();
        try {
            InputStream stream = activity.getContentResolver().openInputStream(uri);
            if (stream == null) {
                toast(activity, "The selected file could not be opened.");
                return true;
            }
            try {
                report(activity, SourceManager.importZip(activity, kind, stream));
            } finally {
                stream.close();
            }
        } catch (Throwable error) {
            toast(activity, "Import failed: " + error.getMessage());
        }
        return true;
    }

    private static void confirm(Activity activity, String title, String message,
                                final Runnable onConfirm) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onConfirm.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void report(Activity activity, SourceManager.Result result) {
        toast(activity, result.getMessage());
    }

    private static void toast(Activity activity, String message) {
        if (activity != null && !activity.isFinishing()) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        }
    }
}
