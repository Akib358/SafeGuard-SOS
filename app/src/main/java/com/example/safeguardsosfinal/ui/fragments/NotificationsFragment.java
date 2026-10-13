package com.example.safeguardsosfinal.ui.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.safeguardsosfinal.adapters.SOSNotificationAdapter;
import com.example.safeguardsosfinal.databinding.FragmentNotificationsBinding;
import com.example.safeguardsosfinal.models.SOSNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private ListenerRegistration notifListener;
    private final List<SOSNotification> notifList = new ArrayList<>();
    private Location myLocation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        fetchCurrentLocation();
        listenToIncomingAlerts();
    }

    private void fetchCurrentLocation() {
        if (getContext() == null) return;
        LocationManager lm = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            try {
                myLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (myLocation == null) myLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            } catch (SecurityException ignored) {}
        }
    }

    private void listenToIncomingAlerts() {
        if (getContext() == null) return;
        SharedPreferences sp = requireContext().getSharedPreferences("SafeGuardUser", Context.MODE_PRIVATE);
        String myPhone = sp.getString("user_phone", "");
        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        notifListener = FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("NotificationsFrag", "Firestore query with orderBy error: ", error);
                        // Fallback in case orderBy requires index or has schema issues
                        FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                                .limit(100)
                                .get()
                                .addOnSuccessListener(fallbackSnapshots -> {
                                    if (binding != null && getContext() != null && fallbackSnapshots != null) {
                                        processAlerts(fallbackSnapshots.getDocuments(), myUid, myPhone);
                                    }
                                });
                        return;
                    }

                    if (snapshots == null || binding == null || getContext() == null) return;
                    processAlerts(snapshots.getDocuments(), myUid, myPhone);
                });
    }

    private void processAlerts(List<DocumentSnapshot> documents, String myUid, String myPhone) {
        List<SOSNotification> ownAlerts = new ArrayList<>();
        List<SOSNotification> guardianAlerts = new ArrayList<>();
        List<SOSNotification> radarAlerts = new ArrayList<>();

        for (DocumentSnapshot doc : documents) {
            String id = doc.getString("alertId");
            String name = doc.getString("userName");
            String phone = doc.getString("phone");
            String userId = doc.getString("userId");
            Double lat = doc.getDouble("latitude");
            Double lng = doc.getDouble("longitude");
            Long time = doc.getLong("timestamp");
            String status = doc.getString("status");
            String safeNote = doc.getString("safeNote");
            Long resolvedTime = doc.getLong("resolvedTimestamp");
            List<String> targetGuardians = (List<String>) doc.get("trustedGuardians");

            Long msgCount = doc.getLong("messageCount");
            int totalMessages = msgCount != null ? msgCount.intValue() : 0;

            if (lat != null && lng != null && time != null) {
                boolean isOwn = (myUid != null && myUid.equals(userId))
                        || (phone != null && !myPhone.isEmpty() && phone.equals(myPhone));

                boolean isMyGuardianAlert = false;
                if (!isOwn && targetGuardians != null && !myPhone.isEmpty()) {
                    isMyGuardianAlert = targetGuardians.contains(myPhone);
                }

                SOSNotification item = new SOSNotification(
                        id != null ? id : doc.getId(),
                        (name != null && !name.isEmpty() ? name : (isOwn ? "You" : "Emergency Victim")),
                        (phone != null ? phone : "Unknown"),
                        lat,
                        lng,
                        time,
                        isMyGuardianAlert,
                        (status != null ? status : "ACTIVE_DANGER"),
                        (safeNote != null ? safeNote : ""),
                        totalMessages,
                        (resolvedTime != null ? resolvedTime : 0L)
                );
                item.setOwnAlert(isOwn);

                if (isOwn) {
                    ownAlerts.add(item);
                } else if (isMyGuardianAlert) {
                    guardianAlerts.add(item);
                } else {
                    radarAlerts.add(item);
                }
            }
        }

        notifList.clear();
        notifList.addAll(ownAlerts);
        notifList.addAll(guardianAlerts);
        notifList.addAll(radarAlerts);

        if (binding == null) return;

        if (notifList.isEmpty()) {
            binding.tvCountBadge.setText("0 ALERTS");
            binding.tvCountBadge.setBackgroundColor(0xFF424242);
            binding.layoutEmptyNotifications.setVisibility(View.VISIBLE);
            binding.rvNotifications.setVisibility(View.GONE);
        } else {
            binding.tvCountBadge.setText(notifList.size() + " TOTAL ALERTS");
            binding.tvCountBadge.setBackgroundColor(0xFFD50000);
            binding.layoutEmptyNotifications.setVisibility(View.GONE);
            binding.rvNotifications.setVisibility(View.VISIBLE);
        }

        binding.rvNotifications.setAdapter(new SOSNotificationAdapter(getContext(), notifList, myLocation));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notifListener != null) notifListener.remove();
        binding = null;
    }
}