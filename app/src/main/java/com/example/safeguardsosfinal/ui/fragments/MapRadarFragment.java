package com.example.safeguardsosfinal.ui.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.safeguardsosfinal.databinding.FragmentMapRadarBinding;
import com.example.safeguardsosfinal.ui.MainActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MapRadarFragment extends Fragment implements LocationListener {

    private FragmentMapRadarBinding binding;
    private LocationManager locationManager;
    private ListenerRegistration customZonesListener;
    private ListenerRegistration emergencyRadarListener;

    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private boolean isMapLoaded = false;
    private String activeFilter = "ALL";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMapRadarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        setupWebViewMap();
        startImmediateLiveGPS();

        binding.btnCopyCoordinates.setOnClickListener(v -> {
            if (currentLat == 0.0 && currentLng == 0.0) {
                Toast.makeText(getContext(), "Locating GPS, please wait...", Toast.LENGTH_SHORT).show();
                return;
            }
            String coords = currentLat + ", " + currentLng;
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("GPS Coordinates", coords);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "GPS Copied: " + coords, Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnOpenGoogleMaps.setOnClickListener(v -> {
            if (currentLat == 0.0 && currentLng == 0.0) {
                Toast.makeText(getContext(), "Locating GPS, please wait...", Toast.LENGTH_SHORT).show();
                return;
            }
            String uri = "geo:" + currentLat + "," + currentLng + "?q=" + currentLat + "," + currentLng + "(My Current Location)";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Intent webMap = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + currentLat + "," + currentLng));
                startActivity(webMap);
            }
        });

        binding.fabMyLocation.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).promptUserToEnableGPS();
            }
            startImmediateLiveGPS();
            if (isMapLoaded && currentLat != 0.0) {
                binding.mapWebView.evaluateJavascript("recenterUser(" + currentLat + ", " + currentLng + ");", null);
            }
        });

        setupFilterListeners();
    }

    private void setupFilterListeners() {
        binding.btnFilterAll.setOnClickListener(v -> applyFilter("ALL", binding.btnFilterAll));
        binding.btnFilterRed.setOnClickListener(v -> applyFilter("RED", binding.btnFilterRed));
        binding.btnFilterYellow.setOnClickListener(v -> applyFilter("YELLOW", binding.btnFilterYellow));
        binding.btnFilterGreen.setOnClickListener(v -> applyFilter("GREEN", binding.btnFilterGreen));
        binding.btnFilterNone.setOnClickListener(v -> applyFilter("NONE", binding.btnFilterNone));
    }

    private void applyFilter(String filterMode, View selectedButton) {
        this.activeFilter = filterMode;
        if (isMapLoaded) {
            binding.mapWebView.evaluateJavascript("filterZones('" + filterMode + "');", null);
        }

        int defaultBg = 0xFF26263B;
        binding.btnFilterAll.setBackgroundColor(defaultBg);
        binding.btnFilterRed.setBackgroundColor(defaultBg);
        binding.btnFilterYellow.setBackgroundColor(defaultBg);
        binding.btnFilterGreen.setBackgroundColor(defaultBg);
        binding.btnFilterNone.setBackgroundColor(defaultBg);

        if (selectedButton != null) {
            selectedButton.setBackgroundColor(0xFF448AFF);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebViewMap() {
        WebView webView = binding.mapWebView;
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isMapLoaded = true;
                if (currentLat != 0.0 && currentLng != 0.0) {
                    webView.evaluateJavascript("recenterUser(" + currentLat + ", " + currentLng + ");", null);
                }
                listenToFirestoreZonesAndBroadcasts();
            }
        });

        double initLat = currentLat != 0.0 ? currentLat : 23.8759;
        double initLng = currentLng != 0.0 ? currentLng : 90.3795;

        String htmlContent = "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />"
                + "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css' />"
                + "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"
                + "<style>"
                + "html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #0F0F14; }"
                + ".leaflet-control-attribution { display: none !important; }"
                + ".user-pulse {"
                + "  width: 18px; height: 18px; background: #448AFF; border-radius: 50%;"
                + "  box-shadow: 0 0 0 8px rgba(68, 138, 255, 0.4); border: 2.5px solid #FFFFFF;"
                + "}"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div id='map'></div>"
                + "<script>"
                + "var map = L.map('map', {zoomControl: false, maxZoom: 19}).setView([" + initLat + ", " + initLng + "], 15);"
                + "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);"
                + "var userIcon = L.divIcon({ className: 'user-pulse', iconSize: [18, 18], iconAnchor: [9, 9] });"
                + "var userMarker = L.marker([" + initLat + ", " + initLng + "], {icon: userIcon}).addTo(map).bindPopup('<b>Your Live GPS Location</b>');"
                + "var radarCircle15km = L.circle([" + initLat + ", " + initLng + "], {"
                + "  color: '#448AFF', fillColor: '#448AFF', fillOpacity: 0.04, radius: 15000, weight: 1.5, dashArray: '4, 8'"
                + "}).addTo(map);"
                + "var redZones = [], yellowZones = [], greenZones = [];"
                + "function recenterUser(lat, lng) {"
                + "  map.setView([lat, lng], 15);"
                + "  userMarker.setLatLng([lat, lng]);"
                + "  radarCircle15km.setLatLng([lat, lng]);"
                + "}"
                + "function clearAllZones() {"
                + "  [...redZones, ...yellowZones, ...greenZones].forEach(layer => map.removeLayer(layer));"
                + "  redZones = []; yellowZones = []; greenZones = [];"
                + "}"
                + "function addZone(type, lat, lng, title, desc, radiusMeters) {"
                + "  var color = type === 'RED' ? '#FF5252' : (type === 'YELLOW' ? '#FFD600' : '#00E676');"
                + "  var circle = L.circle([lat, lng], { color: color, fillColor: color, fillOpacity: 0.25, radius: radiusMeters, weight: 2 }).addTo(map);"
                + "  circle.bindPopup('<b>' + title + '</b><br/>' + desc);"
                + "  if(type === 'RED') redZones.push(circle);"
                + "  else if(type === 'YELLOW') yellowZones.push(circle);"
                + "  else if(type === 'GREEN') greenZones.push(circle);"
                + "}"
                + "function filterZones(mode) {"
                + "  redZones.forEach(l => mode === 'ALL' || mode === 'RED' ? map.addLayer(l) : map.removeLayer(l));"
                + "  yellowZones.forEach(l => mode === 'ALL' || mode === 'YELLOW' ? map.addLayer(l) : map.removeLayer(l));"
                + "  greenZones.forEach(l => mode === 'ALL' || mode === 'GREEN' ? map.addLayer(l) : map.removeLayer(l));"
                + "}"
                + "</script>"
                + "</body>"
                + "</html>";

        webView.loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null);
    }

    private void startImmediateLiveGPS() {
        if (getContext() == null || locationManager == null) return;

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isGpsEnabled && !isNetworkEnabled) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).promptUserToEnableGPS();
                }
                return;
            }

            if (isGpsEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
            }
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);
            }

            Location lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLoc == null) {
                lastLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastLoc != null) {
                applyLocationData(lastLoc);
            }
        }
    }

    private void applyLocationData(Location loc) {
        if (loc == null || binding == null) return;

        currentLat = loc.getLatitude();
        currentLng = loc.getLongitude();

        binding.tvCoordinates.setText("Lat: " + String.format(Locale.US, "%.5f", currentLat) + ", Lng: " + String.format(Locale.US, "%.5f", currentLng));

        if (isMapLoaded) {
            binding.mapWebView.evaluateJavascript("recenterUser(" + currentLat + ", " + currentLng + ");", null);
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            String resolvedArea = "Live GPS Active";
            try {
                if (getContext() != null) {
                    Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(currentLat, currentLng, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address a = addresses.get(0);
                        String feature = a.getFeatureName() != null ? a.getFeatureName() : "";
                        String subLocality = a.getSubLocality() != null ? a.getSubLocality() : "";
                        String locality = a.getLocality() != null ? a.getLocality() : "";

                        if (!subLocality.isEmpty() && !locality.isEmpty()) {
                            resolvedArea = subLocality + ", " + locality;
                        } else if (!subLocality.isEmpty()) {
                            resolvedArea = subLocality;
                        } else if (!locality.isEmpty()) {
                            resolvedArea = locality;
                        } else {
                            resolvedArea = feature;
                        }
                    }
                }
            } catch (Exception ignored) {}

            final String finalText = "📍 " + resolvedArea;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (binding != null) {
                    binding.tvLocationName.setText(finalText);
                }
            });
        });
    }

    private void listenToFirestoreZonesAndBroadcasts() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        customZonesListener = db.collection("safety_zones")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || !isMapLoaded) return;

                    binding.mapWebView.evaluateJavascript("clearAllZones();", null);

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        String type = doc.getString("type");
                        String title = doc.getString("title");
                        String desc = doc.getString("description");
                        Double radius = doc.getDouble("radius");

                        if (lat != null && lng != null) {
                            String zoneType = type != null ? type.toUpperCase() : "GREEN";
                            String zTitle = title != null ? title : "Safety Zone";
                            String zDesc = desc != null ? desc : "Registered Zone Info";
                            double rad = radius != null ? radius : 1000.0;

                            binding.mapWebView.evaluateJavascript("addZone('" + zoneType + "', " + lat + ", " + lng + ", '" + zTitle + "', '" + zDesc + "', " + rad + ");", null);
                        }
                    }

                    db.collection("emergency_broadcasts")
                            .whereEqualTo("status", "ACTIVE_DANGER")
                            .get()
                            .addOnSuccessListener(queryDocs -> {
                                for (DocumentSnapshot d : queryDocs) {
                                    Double lat = d.getDouble("latitude");
                                    Double lng = d.getDouble("longitude");
                                    String phone = d.getString("phone");
                                    if (lat != null && lng != null) {
                                        String p = phone != null ? phone : "SOS Victim";
                                        binding.mapWebView.evaluateJavascript("addZone('RED', " + lat + ", " + lng + ", '🔴 DANGER SOS ALERT', 'Emergency active! Contact: " + p + "', 1000);", null);
                                    }
                                }
                                binding.mapWebView.evaluateJavascript("filterZones('" + activeFilter + "');", null);
                            });
                });
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        applyLocationData(location);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(@NonNull String provider) {
        startImmediateLiveGPS();
    }
    @Override public void onProviderDisabled(@NonNull String provider) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (locationManager != null) locationManager.removeUpdates(this);
        if (customZonesListener != null) customZonesListener.remove();
        if (emergencyRadarListener != null) emergencyRadarListener.remove();
        binding = null;
    }
}