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
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public class MapRadarFragment extends Fragment implements LocationListener {

    private FragmentMapRadarBinding binding;
    private LocationManager locationManager;
    private ListenerRegistration customZonesListener;
    private ListenerRegistration activeSosListener;
    private QuerySnapshot cachedZoneSnapshots;
    private QuerySnapshot cachedSosSnapshots;

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
                + "  var t = (type || '').toUpperCase();"
                + "  var color = '#00E676';"
                + "  var category = 'GREEN';"
                + "  if (t === 'RED' || t.indexOf('DANGER') !== -1 || t.indexOf('CRITICAL') !== -1) {"
                + "    color = '#FF1744';"
                + "    category = 'RED';"
                + "  } else if (t === 'YELLOW' || t.indexOf('MODERATE') !== -1 || t.indexOf('CAUTION') !== -1 || t.indexOf('WARNING') !== -1) {"
                + "    color = '#FFD600';"
                + "    category = 'YELLOW';"
                + "  } else {"
                + "    color = '#00E676';"
                + "    category = 'GREEN';"
                + "  }"
                + "  var circle = L.circle([lat, lng], { color: color, fillColor: color, fillOpacity: 0.28, radius: radiusMeters, weight: 2 }).addTo(map);"
                + "  circle.bindPopup('<b>' + title + '</b><br/>' + desc);"
                + "  if(category === 'RED') redZones.push(circle);"
                + "  else if(category === 'YELLOW') yellowZones.push(circle);"
                + "  else greenZones.push(circle);"
                + "}"
                + "function filterZones(mode) {"
                + "  var m = (mode || '').toUpperCase();"
                + "  redZones.forEach(function(l) { (m === 'ALL' || m === 'RED' || m === 'DANGER') ? map.addLayer(l) : map.removeLayer(l); });"
                + "  yellowZones.forEach(function(l) { (m === 'ALL' || m === 'YELLOW' || m === 'MODERATE' || m === 'CAUTION') ? map.addLayer(l) : map.removeLayer(l); });"
                + "  greenZones.forEach(function(l) { (m === 'ALL' || m === 'GREEN' || m === 'SAFE') ? map.addLayer(l) : map.removeLayer(l); });"
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

    // Real-time listener for Firestore registered zones and dynamic active SOS incident density
    private void listenToFirestoreZonesAndBroadcasts() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Listen for registered safety zones
        customZonesListener = db.collection("safety_zones")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || !isMapLoaded) return;
                    cachedZoneSnapshots = snapshots;
                    renderAllMapZones();
                });

        // 2. Real-time dynamic threat clustering from instant SOS broadcasts
        activeSosListener = db.collection("emergency_broadcasts")
                .addSnapshotListener((sosSnapshots, sosError) -> {
                    if (sosError != null || sosSnapshots == null || !isMapLoaded) return;
                    cachedSosSnapshots = sosSnapshots;
                    renderAllMapZones();
                    com.example.safeguardsosfinal.network.FirebaseManager.getInstance().evaluateAndSyncAllDangerZones();
                });
    }

    // Synchronize both static zones and calculated dynamic zones based on 100m radius clustering:
    // Rule: Within 100m radius, if last 1 month SOS >= 7 OR total SOS >= 15 -> 2 KM DANGER ZONE (RED)
    private void renderAllMapZones() {
        if (!isMapLoaded || binding == null) return;

        binding.mapWebView.evaluateJavascript("clearAllZones();", null);

        // 1. Render static registered safety zones from database
        if (cachedZoneSnapshots != null) {
            for (DocumentSnapshot doc : cachedZoneSnapshots.getDocuments()) {
                Double lat = null, lng = null;
                Object latObj = doc.get("latitude");
                if (latObj instanceof Number) lat = ((Number) latObj).doubleValue();
                Object lngObj = doc.get("longitude");
                if (lngObj instanceof Number) lng = ((Number) lngObj).doubleValue();

                if (lat != null && lng != null) {
                    String rawRisk = doc.getString("riskLevel");
                    if (rawRisk == null) rawRisk = doc.getString("zoneType");
                    if (rawRisk == null) rawRisk = doc.getString("type");

                    String docIdLower = doc.getId().toLowerCase();
                    String titleStr = doc.getString("title");
                    if (titleStr == null) titleStr = doc.getString("zoneName");
                    if (titleStr == null) titleStr = doc.getString("name");
                    if (titleStr == null) titleStr = "Safety Zone";

                    String zoneType = "GREEN";
                    String prefix = "🟢";

                    if (rawRisk != null) {
                        String upper = rawRisk.trim().toUpperCase();
                        if (upper.contains("DANGER") || upper.contains("CRITICAL") || upper.equals("RED")) {
                            zoneType = "RED";
                            prefix = "🔴";
                        } else if (upper.contains("MODERATE") || upper.contains("CAUTION") || upper.contains("WARNING") || upper.equals("YELLOW")) {
                            zoneType = "YELLOW";
                            prefix = "🟡";
                        } else {
                            zoneType = "GREEN";
                            prefix = "🟢";
                        }
                    } else if (docIdLower.contains("danger") || titleStr.toLowerCase().contains("danger") || docIdLower.contains("da_") || docIdLower.contains("critical")) {
                        zoneType = "RED";
                        prefix = "🔴";
                    } else if (docIdLower.contains("caution") || titleStr.toLowerCase().contains("caution") || docIdLower.contains("moderate") || titleStr.toLowerCase().contains("moderate") || docIdLower.contains("ca_")) {
                        zoneType = "YELLOW";
                        prefix = "🟡";
                    } else {
                        zoneType = "GREEN";
                        prefix = "🟢";
                    }

                    double rad = 1000.0;
                    Object radObj = doc.get("radiusInMeters");
                    if (radObj == null) radObj = doc.get("radius");
                    if (radObj instanceof Number) {
                        rad = ((Number) radObj).doubleValue();
                    }

                    String descStr = doc.getString("description");
                    if (descStr == null) descStr = doc.getString("desc");
                    if (descStr == null) descStr = doc.getString("details");
                    if (descStr == null) {
                        if ("RED".equals(zoneType)) {
                            descStr = "🚨 Critical Threat / Danger Zone (" + (int) rad + "m perimeter)";
                        } else if ("YELLOW".equals(zoneType)) {
                            descStr = "⚠️ Moderate Caution Risk Zone (" + (int) rad + "m perimeter)";
                        } else {
                            descStr = "✅ Verified Safe Secured Area (" + (int) rad + "m perimeter)";
                        }
                    }

                    String safeTitle = prefix + " " + titleStr.replace("'", "\\'").replace("\"", "\\\"");
                    String safeDesc = descStr.replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ");

                    binding.mapWebView.evaluateJavascript("addZone('" + zoneType + "', " + lat + ", " + lng + ", '" + safeTitle + "', '" + safeDesc + "', " + rad + ");", null);
                }
            }
        }

        // 2. Real-time dynamic threat clustering from instant SOS broadcasts
        if (cachedSosSnapshots != null) {
            long now = System.currentTimeMillis();
            long oneMonthAgo = now - (30L * 24 * 60 * 60 * 1000);
            List<DocumentSnapshot> allDocs = cachedSosSnapshots.getDocuments();

            // Structure to hold aggregated clusters
            class ThreatCluster {
                double lat, lng;
                int count1Month = 0;
                int countTotal = 0;
                boolean isDanger = false;
                boolean isModerate = false;
            }

            List<ThreatCluster> evaluatedClusters = new ArrayList<>();

            for (DocumentSnapshot doc : allDocs) {
                Double lat = doc.getDouble("latitude");
                Double lng = doc.getDouble("longitude");
                if (lat == null || lng == null) continue;

                // Density calculation within 100 meters radius
                int c1M = 0;
                int cTot = 0;

                for (DocumentSnapshot other : allDocs) {
                    Double oLat = other.getDouble("latitude");
                    Double oLng = other.getDouble("longitude");
                    Long oTime = other.getLong("timestamp");

                    if (oLat != null && oLng != null) {
                        float[] dist = new float[1];
                        Location.distanceBetween(lat, lng, oLat, oLng, dist);

                        // Within 100 meters
                        if (dist[0] <= 100) {
                            cTot++;
                            if (oTime != null && oTime >= oneMonthAgo) {
                                c1M++;
                            }
                        }
                    }
                }

                // Rule: If in 100m radius, last 1 month >= 7 OR total >= 15 -> DANGER ZONE (2 KM / 2000m)
                boolean danger = (c1M >= 7 || cTot >= 15);
                boolean moderate = (!danger && (c1M >= 3 || cTot >= 6));

                // Deduplicate nearby centers (within 200m) so multiple SOS in same 100m spot don't draw redundant circles
                ThreatCluster matched = null;
                for (ThreatCluster tc : evaluatedClusters) {
                    float[] dist = new float[1];
                    Location.distanceBetween(lat, lng, tc.lat, tc.lng, dist);
                    if (dist[0] <= 200) {
                        matched = tc;
                        break;
                    }
                }

                if (matched != null) {
                    if (c1M > matched.count1Month) matched.count1Month = c1M;
                    if (cTot > matched.countTotal) matched.countTotal = cTot;
                    if (danger) matched.isDanger = true;
                    if (moderate && !matched.isDanger) matched.isModerate = true;
                } else {
                    ThreatCluster tc = new ThreatCluster();
                    tc.lat = lat;
                    tc.lng = lng;
                    tc.count1Month = c1M;
                    tc.countTotal = cTot;
                    tc.isDanger = danger;
                    tc.isModerate = moderate;
                    evaluatedClusters.add(tc);
                }
            }

            // Draw evaluated clusters
            for (ThreatCluster tc : evaluatedClusters) {
                if (tc.isDanger) {
                    String label = "🚨 2 KM DANGER ZONE (High Incident Frequency)";
                    String details = "Critical Risk Area: " + tc.count1Month + " SOS in past month (" + tc.countTotal + " total within 100m). Full 2 KM area designated as Danger Zone.";
                    binding.mapWebView.evaluateJavascript("addZone('RED', " + tc.lat + ", " + tc.lng + ", '" + label + "', '" + details + "', 2000);", null);
                } else if (tc.isModerate) {
                    String label = "🟡 MODERATE RISK ZONE (1 KM Radius)";
                    String details = "Elevated Alert Activity: " + tc.count1Month + " SOS in past month (" + tc.countTotal + " total within 100m).";
                    binding.mapWebView.evaluateJavascript("addZone('YELLOW', " + tc.lat + ", " + tc.lng + ", '" + label + "', '" + details + "', 1000);", null);
                }
            }

            // Draw standalone active SOS broadcasts (not already part of a 2km danger cluster)
            for (DocumentSnapshot doc : allDocs) {
                if ("ACTIVE_DANGER".equalsIgnoreCase(doc.getString("status"))) {
                    Double lat = doc.getDouble("latitude");
                    Double lng = doc.getDouble("longitude");
                    if (lat == null || lng == null) continue;

                    boolean insideDangerCluster = false;
                    for (ThreatCluster tc : evaluatedClusters) {
                        if (tc.isDanger) {
                            float[] dist = new float[1];
                            Location.distanceBetween(lat, lng, tc.lat, tc.lng, dist);
                            if (dist[0] <= 2000) {
                                insideDangerCluster = true;
                                break;
                            }
                        }
                    }

                    if (!insideDangerCluster) {
                        String name = doc.getString("userName");
                        String phone = doc.getString("phone");
                        String label = "🔴 ACTIVE EMERGENCY SOS";
                        String details = "Victim: " + (name != null ? name : "Emergency Victim") + " | Phone: " + (phone != null ? phone : "N/A");
                        binding.mapWebView.evaluateJavascript("addZone('RED', " + lat + ", " + lng + ", '" + label + "', '" + details + "', 1000);", null);
                    }
                }
            }
        }

        binding.mapWebView.evaluateJavascript("filterZones('" + activeFilter + "');", null);
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
        if (activeSosListener != null) activeSosListener.remove();
        binding = null;
    }
}