package com.roni.life.os.app.quickball;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.roni.life.os.app.R;

import java.util.List;

/**
 * Customization screen for the Quick Ball.
 *
 * <p>Every registered action can be enabled, reordered, renamed and given a different icon.
 * Appearance (ball size, opacity, panel layout, labels) is stored in the same preference store,
 * so the panel and the floating button pick the changes up on the next open without a restart.</p>
 */
public final class QuickBallSettingsActivity extends AppCompatActivity {

    private static final int[] ICONS = {
            R.drawable.ic_diagnostics,
            R.drawable.ic_settings,
            R.drawable.ic_info,
            R.drawable.ic_refresh,
            R.drawable.ic_warning,
            R.drawable.ic_more_vert,
            R.drawable.ic_add,
            R.drawable.ic_back
    };

    private QuickBallPreferences preferences;
    private LinearLayout container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_ball_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Quick Ball");
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        container = findViewById(R.id.settings_container);
        preferences = QuickBallPreferences.get(this);
        NativeActionRegistry.get().ensureDefaults(this);
        render();
    }

    private void render() {
        container.removeAllViews();
        renderAppearance();
        renderActions();
        renderReset();
    }

    // ==================== appearance ====================

    private void renderAppearance() {
        addHeader("Appearance");

        LinearLayout sizeRow = row();
        TextView sizeLabel = label("Ball size");
        sizeRow.addView(sizeLabel, weight(1));
        Spinner size = new Spinner(this);
        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Small", "Medium", "Large"});
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        size.setAdapter(sizeAdapter);
        int current = preferences.getBallSizeDp();
        size.setSelection(current <= 42 ? 0 : (current >= 54 ? 2 : 1));
        size.setOnItemSelectedListener(new SimpleItemSelected(position -> {
            int dp = position == 0 ? 40 : (position == 2 ? 56 : 48);
            preferences.setBallSizeDp(dp);
        }));
        sizeRow.addView(size);
        container.addView(sizeRow);

        LinearLayout opacityRow = row();
        TextView opacityLabel = label("Ball opacity");
        opacityRow.addView(opacityLabel, weight(1));
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(70);
        opacity.setProgress(Math.round(preferences.getBallOpacity() * 100) - 30);
        opacity.setOnSeekBarChangeListener(new SimpleSeekListener(progress ->
                preferences.setBallOpacity(progress + 30)));
        opacityRow.addView(opacity, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        container.addView(opacityRow);

        Switch grid = new Switch(this);
        grid.setText("Grid layout");
        grid.setChecked(preferences.isGridLayout());
        grid.setOnCheckedChangeListener((button, checked) ->
                preferences.setGridLayout(checked));
        container.addView(grid);

        Switch labels = new Switch(this);
        labels.setText("Show action labels");
        labels.setChecked(preferences.isShowLabels());
        labels.setOnCheckedChangeListener((button, checked) ->
                preferences.setShowLabels(checked));
        container.addView(labels);
    }

    // ==================== actions ====================

    private void renderActions() {
        NativeAction.Category[] categories = NativeAction.Category.values();
        for (NativeAction.Category category : categories) {
            List<NativeAction> actions = NativeActionRegistry.get().getByCategory(category);
            if (actions.isEmpty()) {
                continue;
            }
            addHeader(category.getTitle());
            for (NativeAction action : actions) {
                container.addView(actionRow(action));
            }
        }
    }

    private View actionRow(NativeAction action) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(8), 0, dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(preferences.getIcon(action.getId(), action.getIconRes()));
        icon.setColorFilter(ContextCompat.getColor(this, R.color.text_primary));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.setMarginEnd(dp(12));
        top.addView(icon, iconParams);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(preferences.getLabel(action.getId(), action.getLabel()));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText(action.getDescription());
        subtitle.setTextSize(11.5f);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        text.addView(title);
        text.addView(subtitle);
        top.addView(text, weight(1));

        CheckBox enabled = new CheckBox(this);
        enabled.setChecked(preferences.isEnabled(action.getId()));
        enabled.setOnCheckedChangeListener((button, checked) ->
                preferences.setEnabled(action.getId(), checked));
        top.addView(enabled);

        wrapper.addView(top);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(36), dp(4), 0, 0);

        controls.addView(smallButton("Up", view -> {
            preferences.move(action.getId(), -1);
            render();
        }));
        controls.addView(smallButton("Down", view -> {
            preferences.move(action.getId(), 1);
            render();
        }));
        controls.addView(smallButton("Rename", view -> promptRename(action)));
        controls.addView(smallButton("Icon", view -> cycleIcon(action)));

        wrapper.addView(controls);
        return wrapper;
    }

    private void promptRename(NativeAction action) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(preferences.getLabel(action.getId(), action.getLabel()));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Action label")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    preferences.setLabel(action.getId(), input.getText().toString());
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void cycleIcon(NativeAction action) {
        int current = preferences.getIcon(action.getId(), action.getIconRes());
        int next = ICONS[0];
        for (int i = 0; i < ICONS.length; i++) {
            if (ICONS[i] == current) {
                next = ICONS[(i + 1) % ICONS.length];
                break;
            }
        }
        preferences.setIcon(action.getId(), next);
        render();
    }

    // ==================== reset ====================

    private void renderReset() {
        Button reset = new Button(this);
        reset.setText("Reset Quick Ball");
        reset.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Reset Quick Ball?")
                .setMessage("Every action becomes visible again and appearance returns to "
                        + "the defaults. Existing project actions are not affected.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    preferences.reset();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(24);
        container.addView(reset, params);
    }

    // ==================== helpers ====================

    private void addHeader(String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextSize(13f);
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        header.setPadding(0, dp(20), 0, dp(6));
        container.addView(header);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        return row;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14f);
        view.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        return view;
    }

    private Button smallButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11f);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(6));
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout.LayoutParams weight(float value) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, value);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private interface OnItemSelected {
        void onSelected(int position);
    }

    private static final class SimpleItemSelected
            implements android.widget.AdapterView.OnItemSelectedListener {

        private final OnItemSelected callback;

        SimpleItemSelected(OnItemSelected callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                   int position, long id) {
            callback.onSelected(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }

    private interface OnProgress {
        void onProgress(int progress);
    }

    private static final class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {

        private final OnProgress callback;

        SimpleSeekListener(OnProgress callback) {
            this.callback = callback;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                callback.onProgress(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
