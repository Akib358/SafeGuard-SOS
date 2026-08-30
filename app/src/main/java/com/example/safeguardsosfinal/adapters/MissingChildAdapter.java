package com.example.safeguardsosfinal.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.databinding.ItemMissingChildBinding;
import com.example.safeguardsosfinal.models.MissingChild;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MissingChildAdapter extends RecyclerView.Adapter<MissingChildAdapter.MissingViewHolder> {

    private List<MissingChild> list = new ArrayList<>();

    public void updateList(List<MissingChild> newList) {
        this.list = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MissingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMissingChildBinding binding = ItemMissingChildBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new MissingViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MissingViewHolder holder, int position) {
        MissingChild item = list.get(position);
        if (item == null) return;

        Context context = holder.itemView.getContext();

        String displayName = item.getChildName() != null ? item.getChildName() : "Unknown";
        if (item.getEdited() != null && item.getEdited()) {
            displayName += " (Edited)";
        }
        holder.binding.tvChildName.setText(displayName);
        holder.binding.tvChildAge.setText("Age: " + item.getAge() + " Yrs");
        holder.binding.tvLastSeenLocation.setText("📍 " + item.getLastSeenLocation());

        if (item.getPhotoUrl() != null && !item.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(item.getPhotoUrl())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(holder.binding.ivChildThumb);
        } else {
            holder.binding.ivChildThumb.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        holder.itemView.setOnClickListener(v -> showDetailsDialog(context, item));
    }

    private void showDetailsDialog(Context context, MissingChild item) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_view_details, null);
        ImageView photo = view.findViewById(R.id.ivPopupPhoto);
        TextView title = view.findViewById(R.id.tvPopupTitle);
        TextView badge = view.findViewById(R.id.tvPopupBadge);
        TextView loc = view.findViewById(R.id.tvPopupLocation);
        TextView phone = view.findViewById(R.id.tvPopupPhone);
        TextView details = view.findViewById(R.id.tvPopupDetails);
        MaterialButton btnCall = view.findViewById(R.id.btnPopupCall);

        String titleText = item.getChildName();
        if (item.getEdited() != null && item.getEdited()) {
            titleText += " (Edited)";
        }
        title.setText(titleText);

        if ("FOUND / RESOLVED".equalsIgnoreCase(item.getStatus()) || "RESOLVED".equalsIgnoreCase(item.getStatus())) {
            badge.setText("Status: FOUND / RESOLVED | Age: " + item.getAge());
            badge.setTextColor(0xFF00E676);
        } else {
            badge.setText("Status: ACTIVE SEARCH | Age: " + item.getAge());
            badge.setTextColor(0xFF448AFF);
        }

        loc.setText("📍 Last Seen: " + item.getLastSeenLocation());
        phone.setText("📞 Contact: " + item.getContactPhone());
        details.setText("📋 Description: " + item.getDescription());

        if (item.getPhotoUrl() != null && !item.getPhotoUrl().isEmpty()) {
            photo.setVisibility(View.VISIBLE);
            Glide.with(context).load(item.getPhotoUrl()).into(photo);
        } else {
            photo.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setNegativeButton("Close", (d, w) -> d.dismiss())
                .create();

        btnCall.setOnClickListener(v -> {
            if (item.getContactPhone() != null && !item.getContactPhone().isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.getContactPhone()));
                context.startActivity(intent);
            }
        });

        dialog.show();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class MissingViewHolder extends RecyclerView.ViewHolder {
        public final ItemMissingChildBinding binding;

        public MissingViewHolder(@NonNull ItemMissingChildBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}