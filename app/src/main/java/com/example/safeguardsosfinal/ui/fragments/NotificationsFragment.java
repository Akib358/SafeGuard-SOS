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
        SharedPreferences sp = requireContext().getSharedPreferences("SafeGuardUser", Context.MODE_PRIVATE);
        String myPhone = sp.getString("user_phone", "");

        notifListener = FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .whereEqualTo("status", "ACTIVE_DANGER")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || binding == null) return;

                    List<SOSNotification> guardianAlerts = new ArrayList<>();
                    List<SOSNotification> radarAlerts = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String id = doc.getString("alertId");
                        String name = doc.getString("userName");
                        String phone = doc.getString("phone");
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        Long time = doc.getLong("timestamp");
                        List<String> targetGuardians = (List<String>) doc.get("trustedGuardians");

                        if (lat != null && lng != null && time != null) {
                            boolean isMyGuardianAlert = false;
                            if (targetGuardians != null && !myPhone.isEmpty()) {
                                isMyGuardianAlert = targetGuardians.contains(myPhone);
                            }

                            SOSNotification item = new SOSNotification(
                                    id,
                                    name != null ? name : "Emergency Contact",
                                    phone != null ? phone : "Unknown",
                                    lat, lng, time, isMyGuardianAlert
                            );

                            if (isMyGuardianAlert) {
                                guardianAlerts.add(item); // প্রায়োরিটি ১: ডিরেক্ট গার্ডিয়ান
                            } else {
                                // ১ কিমি এর ভেতরে আছে কিনা চেক
                                if (myLocation != null) {
                                    Location vicLoc = new Location("Vic");
                                    vicLoc.setLatitude(lat);
                                    vicLoc.setLongitude(lng);
                                    if (myLocation.distanceTo(vicLoc) <= 1500) {
                                        radarAlerts.add(item); // প্রায়োরিটি ২: এলাকার রেডার অ্যালার্ট
                                    }
                                } else {
                                    radarAlerts.add(item);
                                }
                            }
                        }
                    }

                    notifList.clear();
                    notifList.addAll(guardianAlerts); // আগে গার্ডিয়ান অ্যালার্ট বসবে
                    notifList.addAll(radarAlerts);    // নিচে এলাকার অ্যালার্ট বসবে

                    binding.tvCountBadge.setText(notifList.size() + " ACTIVE ALERTS");
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