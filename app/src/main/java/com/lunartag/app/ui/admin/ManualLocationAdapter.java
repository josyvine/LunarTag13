package com.lunartag.app.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunartag.app.R;
import com.lunartag.app.model.ManualLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter to display the list of saved workplaces in the Manual Location Dialog.
 * Supports Logic #1: Multiple workplaces and active selection.
 * UPDATED: Sanitized landmark display to remove brackets (Issue #2).
 * UPDATED: Added multi-selection mode and listeners for the deletion feature (Glitch #1).
 */
public class ManualLocationAdapter extends RecyclerView.Adapter<ManualLocationAdapter.WorkplaceViewHolder> {

    private List<ManualLocation> locationList = new ArrayList<>();
    private final OnWorkplaceSelectedListener listener;
    
    // --- Selection & Deletion Logic ---
    private boolean isSelectionMode = false;
    private final Set<Long> selectedIds = new HashSet<>();
    private OnSelectionChangeListener selectionListener;

    /**
     * Interface to handle user clicks on a workplace item.
     */
    public interface OnWorkplaceSelectedListener {
        void onWorkplaceSelected(ManualLocation location);
    }

    /**
     * Interface to notify the Dialog when the selection count changes.
     */
    public interface OnSelectionChangeListener {
        void onSelectionChanged(int count);
    }

    public ManualLocationAdapter(OnWorkplaceSelectedListener listener) {
        this.listener = listener;
    }

    public void setSelectionListener(OnSelectionChangeListener listener) {
        this.selectionListener = listener;
    }

    /**
     * Updates the data in the list and refreshes the UI.
     */
    public void setLocations(List<ManualLocation> locations) {
        this.locationList = locations;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WorkplaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manual_location, parent, false);
        return new WorkplaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WorkplaceViewHolder holder, int position) {
        ManualLocation location = locationList.get(position);

        // Logic #1: Display the descriptive name
        holder.textName.setText(location.locationName);

        // FIX ISSUE #2: Clean brackets from landmark before displaying in the list
        String cleanLandmark = location.landmark != null ? location.landmark.replace("(", "").replace(")", "") : "";

        // Logic #3: Combine cleaned Landmark and Pincode for a descriptive sub-line
        String details = cleanLandmark;
        if (location.pincode != null && !location.pincode.isEmpty()) {
            details += (details.isEmpty() ? "" : ", ") + location.pincode;
        }
        if (location.state != null && !location.state.isEmpty()) {
            details += (details.isEmpty() ? "" : ", ") + location.state;
        }
        holder.textDetails.setText(details);

        // --- Selection & Activation UI Logic ---
        if (isSelectionMode) {
            // While deleting, hide the "Active" checkmark and show the "Select" checkbox
            holder.imageCheck.setVisibility(View.GONE);
            holder.checkBoxDelete.setVisibility(View.VISIBLE);
            holder.checkBoxDelete.setChecked(selectedIds.contains(location.getId()));
            holder.itemView.setAlpha(1.0f);
        } else {
            // Normal mode logic
            holder.checkBoxDelete.setVisibility(View.GONE);
            if (location.isActive) {
                holder.imageCheck.setVisibility(View.VISIBLE);
                holder.itemView.setAlpha(1.0f);
            } else {
                holder.imageCheck.setVisibility(View.GONE);
                holder.itemView.setAlpha(0.7f);
            }
        }

        // --- Interaction Logic ---
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(location.getId());
            } else {
                if (listener != null) {
                    listener.onWorkplaceSelected(location);
                }
            }
        });

        // Long click to enter Selection Mode for deletion
        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                toggleSelection(location.getId());
                notifyDataSetChanged();
                return true;
            }
            return false;
        });
    }

    private void toggleSelection(long id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }

        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedIds.size());
        }

        if (selectedIds.isEmpty()) {
            isSelectionMode = false;
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        isSelectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(0);
        }
    }

    public List<Long> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    @Override
    public int getItemCount() {
        return locationList != null ? locationList.size() : 0;
    }

    /**
     * ViewHolder class for the workplace item.
     */
    static class WorkplaceViewHolder extends RecyclerView.ViewHolder {
        final TextView textName;
        final TextView textDetails;
        final ImageView imageCheck;
        final CheckBox checkBoxDelete;

        WorkplaceViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_workplace_name);
            textDetails = itemView.findViewById(R.id.text_workplace_details);
            imageCheck = itemView.findViewById(R.id.image_active_check);
            // Referencing the checkbox added in the item_manual_location.xml update
            checkBoxDelete = itemView.findViewById(R.id.checkbox_select_workplace);
        }
    }
}