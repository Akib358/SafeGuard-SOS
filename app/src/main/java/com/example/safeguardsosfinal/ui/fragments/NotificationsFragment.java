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

        // 6 months retention window (180 days)
        long sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000);

        notifListener = FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .whereGreaterThanOrEqualTo("timestamp", sixMonthsAgo)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || binding == null || getContext() == null) return;

                    List<SOSNotification> guardianAlerts = new ArrayList<>();
                    List<SOSNotification> radarAlerts = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
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

                        // Exclude victim's own alerts from incoming notifications list
                        if (myUid != null && myUid.equals(userId)) {
                            continue;
                        }
                        if (phone != null && !myPhone.isEmpty() && phone.equals(myPhone)) {
                            continue;
                        }

                        Long msgCount = doc.getLong("messageCount");
                        int totalMessages = msgCount != null ? msgCount.intValue() : 0;

                        if (lat != null && lng != null && time != null) {
                            boolean isMyGuardianAlert = false;
                            if (targetGuardians != null && !myPhone.isEmpty()) {
                                isMyGuardianAlert = targetGuardians.contains(myPhone);
                            }

                            SOSNotification item = new SOSNotification(
                                    id != null ? id : doc.getId(),
                                    (name != null && !name.isEmpty() ? name : "Emergency Victim"),
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

                            if (isMyGuardianAlert) {
                                guardianAlerts.add(item);
                            } else {
                                radarAlerts.add(item);
                            }
                        }
                    }

                    notifList.clear();
                    notifList.addAll(guardianAlerts);
                    notifList.addAll(radarAlerts);

                    binding.tvCountBadge.setText(notifList.size() + " TOTAL ALERTS");
                    binding.rvNotifications.setAdapter(new SOSNotificationAdapter(getContext(), notifList, myLocation));
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notifListener != null) notifListener.remove();
        binding = null;
    }
}