
package com.roni.life.os.app.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.roni.life.os.app.R;
import com.roni.life.os.app.project.BootstrapRenderingMode;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.ProjectType;
import com.roni.life.os.app.project.ProjectValidator;
import com.roni.life.os.app.util.UiUtils;

import java.util.Collections;
import java.util.List;

/**
 * প্রজেক্ট তৈরি ও সম্পাদনার ডায়ালগ।
 *
 * <p>প্রজেক্টের Name, বিবরণ, আইকন রঙ, সোর্স টাইপ,
 * সোর্স Path, এন্ট্রি পয়েন্ট, বেস URL, Inline HTML,
 * স্প্ল্যাশ, বুটস্ট্র্যাপ এবং ডিফল্ট প্রজেক্ট
 * কনফিগার করার জন্য ব্যবহৃত হয়।</p>
 */
public final class ProjectEditDialog {

    private static final String TAG = "ProjectEditDialog";

    /** Save সম্পন্ন হলে Listener কল হবে। */
    public interface Listener {
        void onSaved(Project project);
    }

    /** প্রজেক্টের আইকনের জন্য রঙের তালিকা। */
    private static final int[] PALETTE = {
            0xFF6C5CE7,
            0xFF00B894,
            0xFF0984E3,
            0xFFE17055,
            0xFFD63031,
            0xFFFDCB6E
    };

    /** সমর্থিত প্রজেক্ট সোর্স টাইপ। */
    private static final ProjectSourceType[] SOURCE_TYPES = {
            ProjectSourceType.ASSET,
            ProjectSourceType.ZIP_ASSET,
            ProjectSourceType.FILE,
            ProjectSourceType.ZIP_FILE,
            ProjectSourceType.REMOTE_URL,
            ProjectSourceType.INLINE_HTML,
            // SAF folders are normally created by the folder picker; keeping the type in the
            // list means editing a linked project never silently rewrites it to ASSET.
            ProjectSourceType.SAF_FOLDER
    };

    private ProjectEditDialog() {
        // Utility class
    }

    /**
     * New project তৈরি অথবা পুরোনো Edit project করে।
     *
     * @param context Activity context
     * @param source সম্পাদনার প্রজেক্ট; null হলে নতুন তৈরি হবে
     * @param existing বিদ্যমান প্রজেক্টের তালিকা
     * @param listener Saveের পর callback
     */
    // ==================== Type-driven fields ====================

    /** Controls that only apply to some project types. */
    private static final class TypeSection {
        Spinner typeSpinner;
        Spinner renderModeSpinner;
        SwitchCompat bootstrapAtStartupSwitch;
        View renderModeRow;
        View bootstrapAtStartupRow;
    }

    /**
     * Adds the project-type selector and the fields that depend on it.
     *
     * <p>The block is inserted at the top of the existing form so the type is chosen before the
     * source fields it governs. Rendering mode and the startup opt-in are only shown for
     * BOOTSTRAP projects — a WEB project has no rendering mode, and a SPLASH project never
     * opens at startup.</p>
     */
    private static TypeSection attachTypeSection(Context context, View root, Project working) {
        TypeSection section = new TypeSection();
        if (!(root instanceof ScrollView)) {
            return section;
        }
        View child = ((ScrollView) root).getChildAt(0);
        if (!(child instanceof LinearLayout)) {
            return section;
        }
        LinearLayout form = (LinearLayout) child;

        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * context.getResources().getDisplayMetrics().density);
        block.setPadding(0, 0, 0, pad);

        block.addView(caption(context, "Project type"));

        section.typeSpinner = new Spinner(context);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, typeLabels());
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        section.typeSpinner.setAdapter(typeAdapter);
        section.typeSpinner.setSelection(typeIndex(working.getProjectType()));
        block.addView(section.typeSpinner);

        LinearLayout renderRow = new LinearLayout(context);
        renderRow.setOrientation(LinearLayout.VERTICAL);
        renderRow.addView(caption(context, "Bootstrap rendering"));
        section.renderModeSpinner = new Spinner(context);
        ArrayAdapter<String> renderAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, renderModeLabels());
        renderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        section.renderModeSpinner.setAdapter(renderAdapter);
        section.renderModeSpinner.setSelection(
                working.getBootstrapRenderingMode() == BootstrapRenderingMode.WEB ? 1 : 0);
        renderRow.addView(section.renderModeSpinner);
        section.renderModeRow = renderRow;
        block.addView(renderRow);

        LinearLayout startupRow = new LinearLayout(context);
        startupRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView startupLabel = new TextView(context);
        startupLabel.setText("Enable Bootstrap at startup");
        startupLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        startupRow.addView(startupLabel);
        section.bootstrapAtStartupSwitch = new SwitchCompat(context);
        section.bootstrapAtStartupSwitch.setChecked(working.isBootstrapAtStartup());
        startupRow.addView(section.bootstrapAtStartupSwitch);
        section.bootstrapAtStartupRow = startupRow;
        block.addView(startupRow);

        form.addView(block, 0);

        section.typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyTypeVisibility(section, ProjectType.values()[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Keep the current visibility.
            }
        });
        applyTypeVisibility(section, working.getProjectType());
        return section;
    }

    private static TextView caption(Context context, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(13f);
        return label;
    }

    private static void applyTypeVisibility(TypeSection section, ProjectType type) {
        boolean bootstrap = type == ProjectType.BOOTSTRAP;
        UiUtils.setVisible(section.renderModeRow, bootstrap);
        UiUtils.setVisible(section.bootstrapAtStartupRow, bootstrap);
    }

    private static String[] typeLabels() {
        ProjectType[] types = ProjectType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getLabel();
        }
        return labels;
    }

    private static String[] renderModeLabels() {
        BootstrapRenderingMode[] modes = BootstrapRenderingMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].getLabel();
        }
        return labels;
    }

    private static int typeIndex(ProjectType type) {
        ProjectType[] types = ProjectType.values();
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) {
                return i;
            }
        }
        return types.length - 1;
    }

    public static void show(
            final Context context,
            final Project source,
            final List<Project> existing,
            final Listener listener
    ) {

        if (context == null) {
            Log.e(TAG, "Context is null");
            return;
        }

        final Project working =
                source != null ? source : new Project();

        final boolean isNew = source == null;

        final List<Project> projectList =
                existing != null ? existing : Collections.<Project>emptyList();

        try {

            View view = LayoutInflater.from(context).inflate(
                    R.layout.dialog_project_edit,
                    null,
                    false
            );

            // Type first: it decides which of the fields below are meaningful.
            final TypeSection typeSection = attachTypeSection(context, view, working);

            // এখানেই বসাবেন
            SwitchCompat splashSwitch =
                    view.findViewById(R.id.switch_splash);

            SwitchCompat bootstrapSwitch =
                    view.findViewById(R.id.switch_bootstrap);

            SwitchCompat defaultSwitch =
                    view.findViewById(R.id.switch_default);

// Debug পরীক্ষা
            debugSwitch("SPLASH", splashSwitch);
            debugSwitch("BOOTSTRAP", bootstrapSwitch);
            debugSwitch("DEFAULT", defaultSwitch);

            // -------------------------------------------------
            // View Initialization
            // -------------------------------------------------

            final TextInputLayout nameLayout =
                    view.findViewById(R.id.input_name);

            final TextInputEditText nameInput =
                    view.findViewById(R.id.edit_name);

            final TextInputEditText descriptionInput =
                    view.findViewById(R.id.edit_description);

            final LinearLayout colorRow =
                    view.findViewById(R.id.color_row);

            final MaterialAutoCompleteTextView sourceTypeInput =
                    view.findViewById(R.id.edit_source_type);

            final TextInputLayout pathLayout =
                    view.findViewById(R.id.input_source_path);

            final TextInputEditText pathInput =
                    view.findViewById(R.id.edit_source_path);

            final TextInputLayout entryLayout =
                    view.findViewById(R.id.input_entry_point);

            final TextInputEditText entryInput =
                    view.findViewById(R.id.edit_entry_point);

            final TextInputLayout baseUrlLayout =
                    view.findViewById(R.id.input_base_url);

            final TextInputEditText baseUrlInput =
                    view.findViewById(R.id.edit_base_url);

            final TextInputLayout inlineLayout =
                    view.findViewById(R.id.input_inline_html);

            final TextInputEditText inlineInput =
                    view.findViewById(R.id.edit_inline_html);


            final SeekBar durationSeek =
                    view.findViewById(R.id.seek_splash_duration);

            final TextView durationLabel =
                    view.findViewById(R.id.text_splash_duration);

            // -------------------------------------------------
            // Switch Crash Protection
            // -------------------------------------------------

            configureSwitch(
                    splashSwitch,
                    "Project splash enabled"
            );

            configureSwitch(
                    bootstrapSwitch,
                    "Project bootstrap enabled"
            );

            configureSwitch(
                    defaultSwitch,
                    "Set as default project"
            );

            // -------------------------------------------------
            // Load Existing Project Data
            // -------------------------------------------------

            safeSetText(nameInput, working.getName());

            safeSetText(
                    descriptionInput,
                    working.getDescription()
            );

            safeSetText(
                    pathInput,
                    working.getSourcePath()
            );

            safeSetText(
                    entryInput,
                    working.getEntryPoint()
            );

            safeSetText(
                    baseUrlInput,
                    working.getBaseUrl()
            );

            safeSetText(
                    inlineInput,
                    working.getInlineHtml()
            );

            splashSwitch.setChecked(
                    working.isSplashEnabled()
            );

            bootstrapSwitch.setChecked(
                    working.isBootstrapEnabled()
            );

            defaultSwitch.setChecked(
                    working.isDefault()
            );

            // -------------------------------------------------
            // Color Selection
            // -------------------------------------------------

            final int[] selectedColor = {
                    working.getIconColor()
            };

            buildColorRow(
                    context,
                    colorRow,
                    selectedColor
            );

            // -------------------------------------------------
            // Source Type Dropdown
            // -------------------------------------------------

            ProjectSourceType initialType =
                    working.getSourceType();

            if (initialType == null) {
                initialType = ProjectSourceType.ASSET;
            }

            final ProjectSourceType[] selectedType = {
                    initialType
            };

            final String[] labels =
                    new String[SOURCE_TYPES.length];

            for (int i = 0; i < SOURCE_TYPES.length; i++) {
                labels[i] = labelFor(SOURCE_TYPES[i]);
            }

            ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(
                            context,
                            android.R.layout.simple_list_item_1,
                            labels
                    );

            sourceTypeInput.setAdapter(adapter);

            sourceTypeInput.setText(
                    labelFor(selectedType[0]),
                    false
            );

            sourceTypeInput.setOnItemClickListener(
                    new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(
                                AdapterView<?> parent,
                                View selectedView,
                                int position,
                                long id
                        ) {

                            if (position < 0 ||
                                    position >= SOURCE_TYPES.length) {
                                return;
                            }

                            selectedType[0] =
                                    SOURCE_TYPES[position];

                            applyTypeVisibility(
                                    selectedType[0],
                                    pathLayout,
                                    entryLayout,
                                    baseUrlLayout,
                                    inlineLayout
                            );
                        }
                    }
            );

            applyTypeVisibility(
                    selectedType[0],
                    pathLayout,
                    entryLayout,
                    baseUrlLayout,
                    inlineLayout
            );

            // -------------------------------------------------
            // Splash Duration
            // -------------------------------------------------

            int minDuration =
                    Project.MIN_SPLASH_DURATION_MS;

            int maxDuration =
                    Project.MAX_SPLASH_DURATION_MS;

            int duration =
                    working.getSplashDurationMs();

            duration = Math.max(
                    minDuration,
                    Math.min(duration, maxDuration)
            );

            durationSeek.setMax(
                    maxDuration - minDuration
            );

            durationSeek.setProgress(
                    duration - minDuration
            );

            durationLabel.setText(
                    duration + " ms"
            );

            durationSeek.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {

                        @Override
                        public void onProgressChanged(
                                SeekBar seekBar,
                                int progress,
                                boolean fromUser
                        ) {

                            int value =
                                    Project.MIN_SPLASH_DURATION_MS
                                            + progress;

                            durationLabel.setText(
                                    value + " ms"
                            );
                        }

                        @Override
                        public void onStartTrackingTouch(
                                SeekBar seekBar
                        ) {
                        }

                        @Override
                        public void onStopTrackingTouch(
                                SeekBar seekBar
                        ) {
                        }
                    }
            );

            // -------------------------------------------------
            // Splash Switch → duration controls
            // -------------------------------------------------

            final View durationRow =
                    view.findViewById(R.id.row_splash_duration);

            applySplashState(
                    splashSwitch.isChecked(),
                    durationSeek,
                    durationRow
            );

            splashSwitch.setOnCheckedChangeListener(
                    new android.widget.CompoundButton.OnCheckedChangeListener() {

                        @Override
                        public void onCheckedChanged(
                                android.widget.CompoundButton buttonView,
                                boolean isChecked
                        ) {
                            applySplashState(
                                    isChecked,
                                    durationSeek,
                                    durationRow
                            );
                        }
                    }
            );

            // -------------------------------------------------
            // Create Dialog
            // -------------------------------------------------

            final AlertDialog dialog =
                    new AlertDialog.Builder(context)
                            .setTitle(
                                    isNew
                                            ? "New project"
                                            : "Edit project"
                            )
                            .setView(view)
                            .setPositiveButton(
                                    "Save",
                                    null
                            )
                            .setNegativeButton(
                                    "Cancel",
                                    null
                            )
                            .create();

            dialog.show();

            // Cap the dialog height so the form scrolls instead of being clipped. Without
            // this the ScrollView measures at full content height and the lower fields can
            // become unreachable on short screens.
            android.view.Window dialogWindow = dialog.getWindow();
            if (dialogWindow != null) {
                int screenHeight =
                        context.getResources().getDisplayMetrics().heightPixels;
                dialogWindow.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (screenHeight * 0.85f)
                );
            }

            // -------------------------------------------------
            // Save Button
            // -------------------------------------------------

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {

                            nameLayout.setError(null);
                            pathLayout.setError(null);
                            inlineLayout.setError(null);

                            // Read Form Data

                            working.setName(
                                    text(nameInput)
                            );

                            working.setDescription(
                                    text(descriptionInput)
                            );

                            working.setIconColor(
                                    selectedColor[0]
                            );

                            working.setSourceType(
                                    selectedType[0]
                            );

                            working.setSourcePath(
                                    text(pathInput)
                            );

                            working.setEntryPoint(
                                    text(entryInput)
                            );

                            working.setBaseUrl(
                                    text(baseUrlInput)
                            );

                            working.setInlineHtml(
                                    text(inlineInput)
                            );

                            working.setSplashEnabled(
                                    splashSwitch.isChecked()
                            );

                            working.setBootstrapEnabled(
                                    bootstrapSwitch.isChecked()
                            );

                            working.setDefault(
                                    defaultSwitch.isChecked()
                            );

                            // Type-driven configuration. A SPLASH project is a presentation,
                            // so it can never also be the default application project.
                            ProjectType selectedType =
                                    ProjectType.values()[typeSection.typeSpinner.getSelectedItemPosition()];
                            working.setProjectType(selectedType);
                            working.setBootstrapRenderingMode(
                                    typeSection.renderModeSpinner.getSelectedItemPosition() == 1
                                            ? BootstrapRenderingMode.WEB
                                            : BootstrapRenderingMode.NATIVE);
                            working.setBootstrapAtStartup(
                                    selectedType == ProjectType.BOOTSTRAP
                                            && typeSection.bootstrapAtStartupSwitch.isChecked());
                            if (selectedType == ProjectType.SPLASH) {
                                working.setDefault(false);
                            }

                            working.setSplashDurationMs(
                                    Project.MIN_SPLASH_DURATION_MS
                                            + durationSeek.getProgress()
                            );

                            // Validate Project

                            ProjectValidator.Result result =
                                    ProjectValidator.validate(
                                            working,
                                            projectList
                                    );

                            if (result == null) {
                                nameLayout.setError(
                                        "The project could not be validated"
                                );
                                return;
                            }

                            if (!result.isValid()) {

                                String firstError =
                                        result.getFirstError();

                                if (firstError == null ||
                                        firstError.trim().isEmpty()) {
                                    firstError =
                                            "The project information is not valid";
                                }

                                if (firstError.contains("Name")) {

                                    nameLayout.setError(firstError);

                                } else if (
                                        firstError.contains("Path") ||
                                                firstError.contains("URL")
                                ) {

                                    pathLayout.setError(firstError);

                                } else if (
                                        firstError.contains("HTML")
                                ) {

                                    inlineLayout.setError(firstError);

                                } else {

                                    nameLayout.setError(firstError);
                                }

                                return;
                            }

                            // Save Successful

                            dialog.dismiss();

                            if (listener != null) {
                                listener.onSaved(working);
                            }
                        }
                    }
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Project dialog initialization failed",
                    e
            );

            android.widget.Toast.makeText(
                    context,
                    "The project dialog could not be created: "
                            + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG
            ).show();
        }
    }

    // =====================================================
    // Switch Configuration
    // =====================================================

    /**
     * Switch-এর অভ্যন্তরীণ ON/OFF text layout বন্ধ করে।
     *
     * <p>আমরা switch-এর নিজস্ব label ব্যবহার করছি।
     * তাই আলাদা ON/OFF text layout প্রয়োজন নেই।</p>
     */
    /**
     * Dims and disables the splash duration controls when the splash is switched off, so the
     * switch visibly drives the rest of the section instead of looking inert.
     */
    private static void applySplashState(
            boolean enabled,
            SeekBar seek,
            View row
    ) {
        if (seek != null) {
            seek.setEnabled(enabled);
            seek.setAlpha(enabled ? 1f : 0.4f);
        }
        if (row != null) {
            row.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private static void configureSwitch(
            SwitchCompat sw,
            String label
    ) {

        if (sw == null) {
            Log.e(TAG, "SwitchCompat view not found: " + label);
            return;
        }

        // StaticLayout-এর null text crash এড়াতে
        // অভ্যন্তরীণ ON/OFF text rendering বন্ধ করা।
        sw.setShowText(false);

        // Null ON/OFF text-এর জন্য অতিরিক্ত সুরক্ষা।
        sw.setTextOn("");
        sw.setTextOff("");

        // মূল label অনুপস্থিত হলে fallback label।
        if (sw.getText() == null) {
            sw.setText(label != null ? label : "");
        }
    }

    // =====================================================
    // Text Helpers
    // =====================================================

    private static void safeSetText(
            TextInputEditText input,
            String value
    ) {

        if (input != null) {
            input.setText(
                    value != null ? value : ""
            );
        }
    }

    private static String text(
            TextInputEditText input
    ) {

        if (input == null || input.getText() == null) {
            return "";
        }

        return input.getText().toString().trim();
    }

    /** খালি স্ট্রিং যাচাই করে। */
    public static boolean isBlank(String value) {
        return TextUtils.isEmpty(value)
                || value.trim().isEmpty();
    }

    // =====================================================
    // Color Selection
    // =====================================================

    private static void buildColorRow(
            final Context context,
            LinearLayout row,
            final int[] selectedColor
    ) {

        if (row == null) {
            Log.e(TAG, "Color row not found");
            return;
        }

        row.removeAllViews();

        for (final int color : PALETTE) {

            View dot = new View(context);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            UiUtils.dp(context, 36),
                            UiUtils.dp(context, 36)
                    );

            params.setMargins(
                    UiUtils.dp(context, 4),
                    0,
                    UiUtils.dp(context, 4),
                    0
            );

            dot.setLayoutParams(params);
            dot.setBackground(UiUtils.circle(color));

            dot.setAlpha(
                    color == selectedColor[0]
                            ? 1f
                            : 0.45f
            );

            dot.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {

                            selectedColor[0] = color;

                            for (int i = 0;
                                 i < row.getChildCount();
                                 i++) {

                                View child =
                                        row.getChildAt(i);

                                child.setAlpha(
                                        PALETTE[i] == color
                                                ? 1f
                                                : 0.45f
                                );
                            }
                        }
                    }
            );

            row.addView(dot);
        }
    }

    // =====================================================
    // Source Type Visibility
    // =====================================================

    private static void applyTypeVisibility(
            ProjectSourceType type,
            TextInputLayout pathLayout,
            TextInputLayout entryLayout,
            TextInputLayout baseUrlLayout,
            TextInputLayout inlineLayout
    ) {

        if (type == null) {
            type = ProjectSourceType.ASSET;
        }

        boolean inline =
                type == ProjectSourceType.INLINE_HTML;

        UiUtils.setVisible(
                pathLayout,
                !inline
        );

        UiUtils.setVisible(
                entryLayout,
                type.requiresEntryPoint()
        );

        UiUtils.setVisible(
                baseUrlLayout,
                inline
        );

        UiUtils.setVisible(
                inlineLayout,
                inline
        );
    }

    // =====================================================
    // Source Type Labels
    // =====================================================

    private static String labelFor(
            ProjectSourceType type
    ) {

        if (type == null) {
            return "APK asset (folder)";
        }

        switch (type) {

            case ASSET:
                return "APK asset (folder)";

            case ZIP_ASSET:
                return "APK asset (ZIP)";

            case FILE:
                return "Device file";

            case ZIP_FILE:
                return "Device ZIP";

            case REMOTE_URL:
                return "Remote URL";

            case INLINE_HTML:
                return "Inline HTML";

            default:

                String key = type.getKey();

                return key != null ? key : "Other";
        }
    }
    private static void debugSwitch(String name, SwitchCompat sw) {

        if (sw == null) {
            android.util.Log.e("SwitchDebug", name + " VIEW IS NULL");
            return;
        }

        android.util.Log.e(
                "SwitchDebug",
                "BEFORE FIX: " + name
                        + " | text=" + sw.getText()
                        + " | textOn=" + sw.getTextOn()
                        + " | textOff=" + sw.getTextOff()
                        + " | showText=" + sw.getShowText()
        );

        // Main label নিরাপদ করা
        if (sw.getText() == null) {
            sw.setText("");
        }

        // ON/OFF label নিরাপদ করা
        if (sw.getTextOn() == null) {
            sw.setTextOn("");
        }

        if (sw.getTextOff() == null) {
            sw.setTextOff("");
        }

        // Switch-এর ভিতরের ON/OFF text drawing বন্ধ
        sw.setShowText(false);

        sw.requestLayout();
        sw.invalidate();

        android.util.Log.e(
                "SwitchDebug",
                "AFTER FIX: " + name
                        + " | text=" + sw.getText()
                        + " | textOn=" + sw.getTextOn()
                        + " | textOff=" + sw.getTextOff()
                        + " | showText=" + sw.getShowText()
        );
    }
}
