package com.lunartag.app.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunartag.app.R;
import com.lunartag.app.model.ManualLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter to display the list of saved workplaces in the Manual Location Dialog.
 * Supports Logic #1: Multiple workplaces and active selection.
 * UPDATED: Sanitized landmark display to remove brackets (Issue #2).
 */
public class ManualLocationAdapter extends RecyclerView.Adapter<ManualLocationAdapter.WorkplaceViewHolder> {

    private List<ManualLocation> locationList = new ArrayList<>();
    private final OnWorkplaceSelectedListener listener;

    /**
     * Interface to handle user clicks on a workplace item.
     */
    public interface OnWorkplaceSelectedListener {
        void onWorkplaceSelected(ManualLocation location);
    }

    public ManualLocationAdapter(OnWorkplaceSelectedListener listener) {
        this.listener = listener;
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

        // Visibility of the active checkmark
        if (location.isActive) {
            holder.imageCheck.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(1.0f);
        } else {
            holder.imageCheck.setVisibility(View.GONE);
            holder.itemView.setAlpha(0.7f);
        }

        // Handle item click to select workplace
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onWorkplaceSelected(location);
            }
        });
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

        WorkplaceViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_workplace_name);
            textDetails = itemView.findViewById(R.id.text_workplace_details);
            imageCheck = itemView.findViewById(R.id.image_active_check);
        }
    }
}