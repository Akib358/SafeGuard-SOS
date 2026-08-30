package com.example.safeguardsosfinal.ui.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.safeguardsosfinal.databinding.FragmentSafetyDashboardBinding;
import com.example.safeguardsosfinal.services.PanicSOSService;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Locale;

public class SafetyDashboardFragment extends Fragment implements LocationListener {

    private FragmentSafetyDashboardBinding binding;
    private LocationManager locationManager;
    private ListenerRegistration sosListener;
    private ListenerRegistration zoneListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSafetyDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        // সোয়াইপ টু রিফ্রেশ
        binding.swipeRefreshDashboard.setColorSchemeColors(0xFF448AFF, 0xFFE53935);
        binding.swipeRefreshDashboard.setOnRefreshListener(() -> {
            requestAccurateGPSLocation();
            listenToDashboardCounts();
            binding.swipeRefreshDashboard.postDelayed(() -> {
                if (binding != null) binding.swipeRefreshDashboard.setRefreshing(false);
                Toast.makeText(getContext(), "Dashboard & GPS Updated", Toast.LENGTH_SHORT).show();
            }, 1000);
        });

        // বিগ প্যানিক SOS বাটন
        binding.cardEmergencySOS.setOnClickListener(v -> {
            // ১. ব্যাকগ্রাউন্ড সার্ভিস ট্রিগার (এসএমএস + ক্লাউড ব্রডকাস্ট)
            Intent sosIntent = new Intent(requireContext(), PanicSOSService.class);
            sosIntent.setAction("TRIGGER_PANIC_SOS");
            requireContext().startService(sosIntent);

            // ২. সরাসরি কল ডায়াল না করে সরাসরি 999 এ কল প্লেস করা
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Intent directCall = new Intent(Intent.ACTION_CALL, Uri.parse("tel:999"));
                startActivity(directCall);
            } else {
                ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CALL_PHONE}, 101);
            }

            Toast.makeText(getContext(), "🚨 EMERGENCY SOS: Calling 999 & Sending GPS...", Toast.LENGTH_LONG).show();
        });

        requestAccurateGPSLocation();
        listenToDashboardCounts();
    }

    private void requestAccurateGPSLocation() {
        if (getContext() == null || locationManager == null) return;

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);
            }

            Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastGps != null) {
                updateLocationUI(lastGps);
            } else {
                Location lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (lastNet != null) updateLocationUI(lastNet);
            }
        }
    }

    private void updateLocationUI(Location loc) {
        if (loc == null || binding == null || getContext() == null) return;

        double lat = loc.getLatitude();
        double lng = loc.getLongitude();

        binding.tvDashCoordinates.setText("Lat: " + String.format(Locale.US, "%.5f", lat) + ", Lng: " + String.format(Locale.US, "%.5f", lng));

        try {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(lat, lng, 1);
            if (list != null && !list.isEmpty()) {
                Address addr = list.get(0);
                String subLocality = addr.getSubLocality() != null ? addr.getSubLocality() : "";
                String locality = addr.getLocality() != null ? addr.getLocality() : addr.getFeatureName();
                String area = !subLocality.isEmpty() ? subLocality + ", " + locality : locality;
                binding.tvDashLocation.setText("📍 " + (area != null ? area : "Current Location"));
            }
        } catch (Exception e) {
            binding.tvDashLocation.setText("📍 Live GPS Active");
        }
    }

    private void listenToDashboardCounts() {
        sosListener = FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .whereEqualTo("status", "ACTIVE_DANGER")
                .addSnapshotListener((snapshots, error) -> {
                    if (snapshots != null && binding != null) {
                        binding.tvActiveIncidents.setText(String.valueOf(snapshots.size()));
                    }
                });

        zoneListener = FirebaseFirestore.getInstance().collection("safety_zones")
                .addSnapshotListener((snapshots, error) -> {
                    if (snapshots != null && binding != null) {
                        binding.tvRegisteredSafeZones.setText(String.valueOf(snapshots.size()));
                    }
                });
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        updateLocationUI(location);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (locationManager != null) locationManager.removeUpdates(this);
        if (sosListener != null) sosListener.remove();
        if (zoneListener != null) zoneListener.remove();
        binding = null;
    }
}