package com.example.safeguardsosfinal.adapters;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.models.ChatMessage;
import com.example.safeguardsosfinal.models.SOSNotification;
import com.example.safeguardsosfinal.network.FirebaseManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

        boolean isSafe = "SAFE".equalsIgnoreCase(item.getStatus());

        // Top Status Badge
        if (item.isOwnAlert()) {
            if (isSafe) {
                holder.tvBadge.setText("✅ YOUR SOS (RESOLVED)");
                holder.tvBadge.setBackgroundColor(0xFF00E676);
                holder.tvTitle.setText("Your Emergency was resolved & marked safe");
            } else {
                holder.tvBadge.setText("🚨 YOUR ACTIVE SOS BROADCAST");
                holder.tvBadge.setBackgroundColor(0xFFD50000);
                holder.tvTitle.setText("You broadcasted an Emergency SOS!");
            }
        } else if (isSafe) {
            holder.tvBadge.setText("✅ RESOLVED / SAFE");
            holder.tvBadge.setBackgroundColor(0xFF00E676);
            holder.tvTitle.setText((item.getSenderName() != null ? item.getSenderName() : "Victim") + " is now Safe!");
        } else if (item.isDirectGuardianAlert()) {
            holder.tvBadge.setText("🛡️ DIRECT GUARDIAN ALERT");
            holder.tvBadge.setBackgroundColor(0xFFD50000);
            holder.tvTitle.setText((item.getSenderName() != null ? item.getSenderName() : "Emergency Victim") + " triggered Panic SOS!");
        } else {
            holder.tvBadge.setText("📡 1KM RADAR ALERT");
            holder.tvBadge.setBackgroundColor(0xFFFF6D00);
            holder.tvTitle.setText("Emergency nearby in your perimeter!");
        }

        // Top Date & Year & Time formatting: e.g. "07 Oct 2026, 02:30 AM"
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        holder.tvTime.setText(sdf.format(new Date(item.getTimestamp())));

        // Distance Calculation
        float distanceMeters = 0;
        if (myCurrentLoc != null) {
            Location targetLoc = new Location("Target");
            targetLoc.setLatitude(item.getLatitude());
            targetLoc.setLongitude(item.getLongitude());
            distanceMeters = myCurrentLoc.distanceTo(targetLoc);
        }

        String distanceStr = distanceMeters > 1000
                ? String.format(Locale.US, "%.1f KM", distanceMeters / 1000)
                : ((int) distanceMeters) + " Meters";

        if (item.isOwnAlert()) {
            holder.tvPhone.setText("Phone: " + (item.getSenderPhone() != null ? item.getSenderPhone() : "N/A") + " • (Your Device / Origin)");
            holder.btnNavigate.setText("📍 My Location");
        } else {
            holder.tvPhone.setText("Phone: " + (item.getSenderPhone() != null ? item.getSenderPhone() : "N/A") + " • Distance: " + distanceStr);
            holder.btnNavigate.setText("📍 Shortest Route");
        }

        // Story / Description / Resolution info
        if (item.getSafeNote() != null && !item.getSafeNote().trim().isEmpty()) {
            String updateTimeStr = item.getResolvedTimestamp() > 0 ? " (Updated " + sdf.format(new Date(item.getResolvedTimestamp())) + ")" : "";
            holder.tvDesc.setText("📝 Full Story / Resolution Note" + updateTimeStr + ":\n" + item.getSafeNote());
            holder.tvDesc.setVisibility(View.VISIBLE);
        } else if (isSafe) {
            holder.tvDesc.setText("📝 Status: Emergency resolved and marked safe.");
            holder.tvDesc.setVisibility(View.VISIBLE);
        } else {
            if (item.isOwnAlert()) {
                holder.tvDesc.setText("🚨 Critical Status: Your emergency broadcast is live. Helpers and guardians have received your location.");
            } else {
                holder.tvDesc.setText("🚨 Critical Status: Victim triggered active emergency. Tap chat to coordinate rescue.");
            }
            holder.tvDesc.setVisibility(View.VISIBLE);
        }

        // Chat message count
        holder.tvChatInfo.setText("💬 " + item.getMessageCount() + " Rescue Discussion Messages");

        // Action: Open Rescue Chat
        View.OnClickListener openChatListener = v -> openRescueChatDialog(item.getAlertId(), item.getSenderName());
        holder.btnJoinChat.setOnClickListener(openChatListener);
        holder.itemView.setOnClickListener(openChatListener);

        // Action: Navigate Route on Google Maps
        holder.btnNavigate.setOnClickListener(v -> {
            String uri;
            if (item.isOwnAlert()) {
                uri = "geo:" + item.getLatitude() + "," + item.getLongitude() + "?q=" + item.getLatitude() + "," + item.getLongitude() + "(My SOS Location)";
            } else {
                uri = "google.navigation:q=" + item.getLatitude() + "," + item.getLongitude() + "&mode=d";
            }
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            if (!item.isOwnAlert()) {
                mapIntent.setPackage("com.google.android.apps.maps");
            }
            try {
                if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(mapIntent);
                } else {
                    Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + item.getLatitude() + "," + item.getLongitude()));
                    context.startActivity(fallback);
                }
            } catch (Exception e) {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + item.getLatitude() + "," + item.getLongitude()));
                context.startActivity(fallback);
            }
        });
    }

    private void openRescueChatDialog(String alertId, String victimName) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_sos_chat, null);
        TextView tvTitle = view.findViewById(R.id.tvChatTitle);
        EditText etMsg = view.findViewById(R.id.etChatMessage);
        ImageButton btnSend = view.findViewById(R.id.btnSendMessage);
        RecyclerView rvChat = view.findViewById(R.id.rvChatMessages);
        MaterialButton btnClose = view.findViewById(R.id.btnCloseChat);

        TextView chipOnMyWay = view.findViewById(R.id.chipOnMyWay);
        TextView chipCalled999 = view.findViewById(R.id.chipCalled999);
        TextView chipNearby = view.findViewById(R.id.chipNearby);
        TextView chipStaySafe = view.findViewById(R.id.chipStaySafe);

        tvTitle.setText("Rescue Chat: " + (victimName != null ? victimName : "Victim"));

        List<ChatMessage> chatList = new ArrayList<>();
        rvChat.setLayoutManager(new LinearLayoutManager(context));

        RecyclerView.Adapter<RecyclerView.ViewHolder> chatAdapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View itemMsgView = LayoutInflater.from(context).inflate(R.layout.item_sos_chat_message, parent, false);
                return new RecyclerView.ViewHolder(itemMsgView) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ChatMessage m = chatList.get(position);
                TextView tvSender = holder.itemView.findViewById(R.id.tvChatSender);
                TextView tvText = holder.itemView.findViewById(R.id.tvChatText);
                TextView tvTime = holder.itemView.findViewById(R.id.tvChatTime);

                tvSender.setText(m.getSenderName() != null ? m.getSenderName() : "Helper");
                tvText.setText(m.getMessage() != null ? m.getMessage() : "");
                SimpleDateFormat timeFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
                tvTime.setText(timeFormat.format(new Date(m.getTimestamp())));
            }

            @Override
            public int getItemCount() { return chatList.size(); }
        };
        rvChat.setAdapter(chatAdapter);

        ListenerRegistration chatListener = FirebaseManager.getInstance().listenToRescueChat(alertId, (snapshots, error) -> {
            if (snapshots != null) {
                chatList.clear();
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    ChatMessage msg = doc.toObject(ChatMessage.class);
                    if (msg != null) chatList.add(msg);
                }
                chatAdapter.notifyDataSetChanged();
                if (!chatList.isEmpty()) rvChat.scrollToPosition(chatList.size() - 1);
            }
        });

        AlertDialog chatDialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .create();

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> chatDialog.dismiss());
        }

        // Quick chip actions
        if (chipOnMyWay != null) chipOnMyWay.setOnClickListener(v -> etMsg.setText("🏃 I'm on the way to help right now!"));
        if (chipCalled999 != null) chipCalled999.setOnClickListener(v -> etMsg.setText("📞 I have dialed Police 999 and shared your location!"));
        if (chipNearby != null) chipNearby.setOnClickListener(v -> etMsg.setText("📍 I am approaching and very close to your location!"));
        if (chipStaySafe != null) chipStaySafe.setOnClickListener(v -> etMsg.setText("🛡️ Please stay calm and try to reach a safe crowded place/shop!"));

        btnSend.setOnClickListener(v -> {
            String text = etMsg.getText().toString().trim();
            if (!text.isEmpty()) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                String senderName = (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                        ? user.getDisplayName() : "Rescue Volunteer";
                FirebaseManager.getInstance().sendRescueChatMessage(alertId, text, senderName);
                etMsg.setText("");
            }
        });

        chatDialog.setOnDismissListener(d -> {
            if (chatListener != null) chatListener.remove();
        });

        chatDialog.show();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadge, tvTime, tvTitle, tvPhone, tvDesc, tvChatInfo;
        MaterialButton btnJoinChat, btnNavigate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadge = itemView.findViewById(R.id.tvTagBadge);
            tvTime = itemView.findViewById(R.id.tvNotifTime);
            tvTitle = itemView.findViewById(R.id.tvNotifTitle);
            tvPhone = itemView.findViewById(R.id.tvNotifPhone);
            tvDesc = itemView.findViewById(R.id.tvNotifDesc);
            tvChatInfo = itemView.findViewById(R.id.tvNotifChatInfo);
            btnJoinChat = itemView.findViewById(R.id.btnJoinChat);
            btnNavigate = itemView.findViewById(R.id.btnNavigateRoute);
        }
    }
}