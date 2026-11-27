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

/**
 * Compact project list used by the slot management screens.
 *
 * <p>Unlike {@link ProjectAdapter} this list is scoped to one slot, so it shows the source and
 * the live state instead of the splash/bootstrap role chips.</p>
 */
public class SlotProjectAdapter extends RecyclerView.Adapter<SlotProjectAdapter.Holder> {

    /** Interaction callbacks. */
    public interface Listener {
        void onSlotProjectClicked(Project project);

        void onSlotProjectMenu(Project project, View anchor);
    }

    private final Listener listener;
    private final List<Project> items = new ArrayList<>();

    public SlotProjectAdapter(Listener listener) {
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
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_slot_project, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {

        private final TextView badge;
        private final TextView name;
        private final TextView meta;
        private final TextView status;
        private final ImageButton more;

        Holder(@NonNull View itemView) {
            super(itemView);
            badge = itemView.findViewById(R.id.slot_item_badge);
            name = itemView.findViewById(R.id.slot_item_name);
            meta = itemView.findViewById(R.id.slot_item_meta);
            status = itemView.findViewById(R.id.slot_item_status);
            more = itemView.findViewById(R.id.slot_item_more);
        }

        void bind(final Project project, final Listener listener) {
            name.setText(project.getName());

            String entry = project.getEntryPoint() != null && !project.getEntryPoint().isEmpty()
                    ? project.getEntryPoint() : "index.html";
            meta.setText(project.getSourceType().getKey() + " · " + entry);

            String initial = project.getName() != null && !project.getName().isEmpty()
                    ? project.getName().substring(0, 1).toUpperCase() : "?";
            badge.setText(initial);
            badge.setBackground(UiUtils.circle(project.getIconColor()));

            boolean live = ProjectSessionManager.get().isLive(project.getId());
            status.setText(live ? "Running" : "Registered");
            status.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    live ? R.color.brand_accent : R.color.text_secondary));

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        listener.onSlotProjectClicked(project);
                    }
                }
            });

            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        listener.onSlotProjectMenu(project, view);
                    }
                }
            });
        }
    }
}
