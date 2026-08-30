package com.example.safeguardsosfinal.adapters;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.models.SOSNotification;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SOSNotificationAdapter extends RecyclerView.Adapter<SOSNotificationAdapter.ViewHolder> {

    private final Context context;
    private final List<SOSNotification> list;
    private final Location myCurrentLoc;

    public SOSNotificationAdapter(Context context, List<SOSNotification> list, Location myCurrentLoc) {
        this.context = context;
        this.list = list;
        this.myCurrentLoc = myCurrentLoc;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_sos_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SOSNotification item = list.get(position);

        if (item.isDirectGuardianAlert()) {
            holder.tvBadge.setText("🛡️ DIRECT GUARDIAN ALERT");
            holder.tvBadge.setBackgroundColor(0xFFD50000); // রেড
            holder.tvTitle.setText(item.getSenderName() + " triggered Panic SOS!");
        } else {
            holder.tvBadge.setText("📡 1KM RADAR ALERT");
            holder.tvBadge.setBackgroundColor(0xFFFF6D00); // অরেঞ্জ
            holder.tvTitle.setText("Emergency nearby in your 1KM perimeter!");
        }

        // দূরত্ব গণনা
        float distanceMeters = 0;
        if (myCurrentLoc != null) {
            Location targetLoc = new Location("Target");
            targetLoc.setLatitude(item.getLatitude());
            targetLoc.setLongitude(item.getLongitude());
            distanceMeters = myCurrentLoc.distanceTo(targetLoc);
        }

        String distanceStr = distanceMeters > 1000 ? String.format(Locale.US, "%.1f KM", distanceMeters / 1000) : ((int) distanceMeters) + " Meters";
        holder.tvDesc.setText("Phone: " + item.getSenderPhone() + "\nDistance from you: " + distanceStr);
        holder.tvTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(item.getTimestamp())));

        // শর্টেস্ট রুট ডিরেকশন গুগল ম্যাপে ওপেন
        holder.btnNavigate.setOnClickListener(v -> {
            String uri = "google.navigation:q=" + item.getLatitude() + "," + item.getLongitude() + "&mode=d";
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(mapIntent);
            } else {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + item.getLatitude() + "," + item.getLongitude()));
                context.startActivity(fallback);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadge, tvTime, tvTitle, tvDesc;
        MaterialButton btnNavigate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadge = itemView.findViewById(R.id.tvTagBadge);
            tvTime = itemView.findViewById(R.id.tvNotifTime);
            tvTitle = itemView.findViewById(R.id.tvNotifTitle);
            tvDesc = itemView.findViewById(R.id.tvNotifDesc);
            btnNavigate = itemView.findViewById(R.id.btnNavigateRoute);
        }
    }
}