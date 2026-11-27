package com.roni.life.os.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.life.os.app.R;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.runtime.ProjectSessionManager;
import com.roni.life.os.app.util.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Project registry adapter — renders each project as an Industrial HUD console line.
 */
public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

    /** ব্যবহারকারীর ইন্টারঅ্যাকশন কলব্যাক। */
    public interface Listener {
        void onProjectClicked(Project project);

        void onProjectMenu(Project project, View anchor);
    }

    private final Listener listener;
    private final List<Project> items = new ArrayList<>();

    public ProjectAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Project> projects) {
        items.clear();
        if (projects != null) {
            items.addAll(projects);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        holder.bind(items.get(position), position, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ProjectViewHolder extends RecyclerView.ViewHolder {

        private final TextView index;
        private final TextView badge;
        private final TextView name;
        private final TextView description;
        private final TextView sourceChip;
        private final TextView splashChip;
        private final TextView bootstrapChip;
        private final TextView defaultChip;
        private final TextView runningChip;
        private final ImageButton more;

        ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            index = itemView.findViewById(R.id.project_index);
            badge = itemView.findViewById(R.id.project_badge);
            name = itemView.findViewById(R.id.project_name);
            description = itemView.findViewById(R.id.project_description);
            sourceChip = itemView.findViewById(R.id.project_source_chip);
            splashChip = itemView.findViewById(R.id.project_splash_chip);
            bootstrapChip = itemView.findViewById(R.id.project_bootstrap_chip);
            defaultChip = itemView.findViewById(R.id.project_default_chip);
            runningChip = itemView.findViewById(R.id.project_running_chip);
            more = itemView.findViewById(R.id.project_more);
        }

        void bind(final Project project, int position, final Listener listener) {
            index.setText(String.format(Locale.US, "%02d", position + 1));
            name.setText(project.getName());

            String desc = project.getDescription();
            if (desc == null || desc.trim().isEmpty()) {
                description.setText(project.getSourcePath() != null && !project.getSourcePath().isEmpty()
                        ? project.getSourcePath() : "No description");
            } else {
                description.setText(desc);
            }

            String initial = project.getName() != null && !project.getName().isEmpty()
                    ? project.getName().substring(0, 1).toUpperCase(Locale.US) : "?";
            badge.setText(initial);
            badge.setBackgroundResource(R.drawable.bg_hud_badge);
            badge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.hud_accent));

            sourceChip.setText((project.getProjectType().getLabel()
                    + " · " + project.getSourceType().getKey()).toUpperCase(Locale.US));
            splashChip.setText(project.isSplashEnabled() ? "SPLASH ON" : "SPLASH OFF");
            bootstrapChip.setText(project.isBootstrapEnabled() ? "BOOT ON" : "BOOT OFF");
            UiUtils.setVisible(defaultChip, project.isDefault());
            UiUtils.setVisible(runningChip,
                    ProjectSessionManager.get().isLive(project.getId()));

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        listener.onProjectClicked(project);
                    }
                }
            });

            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        listener.onProjectMenu(project, view);
                    }
                }
            });
        }
    }
}
