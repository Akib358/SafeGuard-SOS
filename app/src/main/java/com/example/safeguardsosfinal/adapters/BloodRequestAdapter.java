package com.example.safeguardsosfinal.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.databinding.ItemBloodRequestBinding;
import com.example.safeguardsosfinal.models.BloodRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BloodRequestAdapter extends RecyclerView.Adapter<BloodRequestAdapter.BloodViewHolder> {

    private List<BloodRequest> requestList = new ArrayList<>();

    public void updateList(List<BloodRequest> newList) {
        this.requestList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BloodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBloodRequestBinding binding = ItemBloodRequestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new BloodViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BloodViewHolder holder, int position) {
        BloodRequest item = requestList.get(position);
        if (item == null) return;

        Context context = holder.itemView.getContext();

        holder.binding.tvPatientName.setText(item.getPatientName());
        holder.binding.tvBloodGroupBadge.setText(item.getBloodGroup());
        holder.binding.tvBloodUnits.setText(item.getRequiredUnits() + " • " + item.getGender());
        holder.binding.tvHospitalName.setText(item.getLocation());
        holder.binding.tvEmergencyReason.setText(item.getNotes());

        if (item.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
            holder.binding.tvBloodTime.setText(sdf.format(new Date(item.getTimestamp())));
        } else {
            holder.binding.tvBloodTime.setText("Just now");
        }

        holder.itemView.setOnClickListener(v -> showDetailsDialog(context, item));

        holder.binding.btnCallDonor.setOnClickListener(v -> {
            if (item.getPhone() != null && !item.getPhone().isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.getPhone()));
                context.startActivity(callIntent);
            }
        });
    }

    private void showDetailsDialog(Context context, BloodRequest item) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_view_details, null);
        TextView title = view.findViewById(R.id.tvPopupTitle);
        TextView badge = view.findViewById(R.id.tvPopupBadge);
        TextView loc = view.findViewById(R.id.tvPopupLocation);
        TextView phone = view.findViewById(R.id.tvPopupPhone);
        TextView details = view.findViewById(R.id.tvPopupDetails);
        MaterialButton btnCall = view.findViewById(R.id.btnPopupCall);

        title.setText(item.getPatientName());
        badge.setText("Required Blood Group: " + item.getBloodGroup());
        badge.setTextColor(0xFFFF5252);
        loc.setText("📍 Hospital: " + item.getLocation());
        phone.setText("📞 Phone: " + item.getPhone());
        details.setText("📝 Notes: " + item.getNotes());

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setNegativeButton("Close", (d, w) -> d.dismiss())
                .create();

        btnCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.getPhone()));
            context.startActivity(intent);
        });

        dialog.show();
    }

    @Override
    public int getItemCount() {
        return requestList.size();
    }

    public static class BloodViewHolder extends RecyclerView.ViewHolder {
        public final ItemBloodRequestBinding binding;

        public BloodViewHolder(@NonNull ItemBloodRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}