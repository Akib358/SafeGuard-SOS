package com.example.safeguardsosfinal.network;

import android.location.Location;

import com.example.safeguardsosfinal.models.BloodRequest;
import com.example.safeguardsosfinal.models.ChatMessage;
import com.example.safeguardsosfinal.models.MissingChild;
import com.example.safeguardsosfinal.models.SOSAlert;
import com.example.safeguardsosfinal.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FirebaseManager {

    private static FirebaseManager instance;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public interface OnCompleteListener {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface OnBloodLoadedListener {
        void onLoaded(List<BloodRequest> list);
    }

    public interface OnMissingLoadedListener {
        void onLoaded(List<MissingChild> list);
    }

    public interface OnZoneCalculatedListener {
        void onZoneCalculated(String zoneType, int activeIncidents, int uniqueUsersCount);
    }

    public interface OnActiveSOSLoadedListener {
        void onLoaded(SOSAlert activeAlert);
    }

    private FirebaseManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public void saveUser(User user, OnCompleteListener listener) {
        db.collection("users").document(user.getUid()).set(user)
                .addOnSuccessListener(aVoid -> { if (listener != null) listener.onSuccess(); })
                .addOnFailureListener(e -> { if (listener != null) listener.onFailure(e); });
    }

    public void postBloodRequest(BloodRequest request, OnCompleteListener listener) {
        db.collection("blood_requests").document(request.getRequestId()).set(request)
                .addOnSuccessListener(aVoid -> { if (listener != null) listener.onSuccess(); })
                .addOnFailureListener(e -> { if (listener != null) listener.onFailure(e); });
    }

    public ListenerRegistration listenToBloodRequests(OnBloodLoadedListener listener) {
        return db.collection("blood_requests")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        if (listener != null) listener.onLoaded(new ArrayList<>());
                        return;
                    }
                    List<BloodRequest> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(BloodRequest.class));
                    }
                    if (listener != null) listener.onLoaded(list);
                });
    }

    public void postMissingChild(MissingChild report, OnCompleteListener listener) {
        db.collection("missing_children").document(report.getReportId()).set(report)
                .addOnSuccessListener(aVoid -> { if (listener != null) listener.onSuccess(); })
                .addOnFailureListener(e -> { if (listener != null) listener.onFailure(e); });
    }

    public ListenerRegistration listenToMissingChildren(OnMissingLoadedListener listener) {
        return db.collection("missing_children")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        if (listener != null) listener.onLoaded(new ArrayList<>());
                        return;
                    }
                    List<MissingChild> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(MissingChild.class));
                    }
                    if (listener != null) listener.onLoaded(list);
                });
    }

    // Broadcast emergency SOS
    public void broadcastSOS(double lat, double lng, String senderPhone, OnCompleteListener listener) {
        String alertId = "sos_" + System.currentTimeMillis();
        FirebaseUser currentUser = auth.getCurrentUser();
        String currentUid = currentUser != null ? currentUser.getUid() : "unknown";
        String currentName = (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty())
                ? currentUser.getDisplayName() : "SafeGuard User";

        Map<String, Object> sosData = new HashMap<>();
        sosData.put("alertId", alertId);
        sosData.put("userId", currentUid);
        sosData.put("userName", currentName);
        sosData.put("phone", senderPhone);
        sosData.put("latitude", lat);
        sosData.put("longitude", lng);
        sosData.put("status", "ACTIVE_DANGER");
        sosData.put("timestamp", System.currentTimeMillis());
        sosData.put("messageCount", 0);
        sosData.put("safeNote", "");

        db.collection("emergency_broadcasts").document(alertId).set(sosData)
                .addOnSuccessListener(aVoid -> { if (listener != null) listener.onSuccess(); })
                .addOnFailureListener(e -> { if (listener != null) listener.onFailure(e); });
    }

    // Listen to current user's active SOS
    public ListenerRegistration listenToMyActiveSOS(OnActiveSOSLoadedListener listener) {
        String currentUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        if (currentUid.isEmpty()) {
            if (listener != null) listener.onLoaded(null);
            return null;
        }

        return db.collection("emergency_broadcasts")
                .whereEqualTo("userId", currentUid)
                .whereEqualTo("status", "ACTIVE_DANGER")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || snapshots.isEmpty()) {
                        if (listener != null) listener.onLoaded(null);
                        return;
                    }
                    QueryDocumentSnapshot doc = (QueryDocumentSnapshot) snapshots.getDocuments().get(0);
                    SOSAlert alert = new SOSAlert();
                    alert.setAlertId(doc.getString("alertId"));
                    alert.setUserId(doc.getString("userId"));
                    alert.setUserName(doc.getString("userName"));
                    alert.setUserPhone(doc.getString("phone"));
                    alert.setStatus(doc.getString("status"));
                    Double lat = doc.getDouble("latitude");
                    Double lng = doc.getDouble("longitude");
                    alert.setLatitude(lat != null ? lat : 0.0);
                    alert.setLongitude(lng != null ? lng : 0.0);
                    Long time = doc.getLong("timestamp");
                    alert.setTimestamp(time != null ? time : 0L);
                    alert.setSafeNote(doc.getString("safeNote"));
                    Long msgCount = doc.getLong("messageCount");
                    alert.setMessageCount(msgCount != null ? msgCount.intValue() : 0);
                    if (listener != null) listener.onLoaded(alert);
                });
    }

    // Mark SOS as Safe with description
    public void markSOSAsSafe(String alertId, String safeNote, OnCompleteListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "SAFE");
        updates.put("safeNote", safeNote);
        updates.put("resolvedTimestamp", System.currentTimeMillis());

        db.collection("emergency_broadcasts").document(alertId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Send system notification in rescue chat
                    String sysMsgId = db.collection("emergency_broadcasts").document(alertId).collection("chat").document().getId();
                    ChatMessage sysMsg = new ChatMessage(sysMsgId, "system", "SYSTEM NOTICE", "✅ Victim is now SAFE. Resolution Note: " + safeNote, System.currentTimeMillis());
                    db.collection("emergency_broadcasts").document(alertId).collection("chat").document(sysMsgId).set(sysMsg);
                    db.collection("emergency_broadcasts").document(alertId).update("messageCount", FieldValue.increment(1));

                    if (listener != null) listener.onSuccess();
                })
                .addOnFailureListener(e -> { if (listener != null) listener.onFailure(e); });
    }

    // Send chat message in SOS rescue discussion
    public void sendRescueChatMessage(String alertId, String messageText, String senderName) {
        String currentUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "guest";
        String msgId = db.collection("emergency_broadcasts").document(alertId).collection("chat").document().getId();

        ChatMessage msg = new ChatMessage(msgId, currentUid, senderName, messageText, System.currentTimeMillis());

        db.collection("emergency_broadcasts").document(alertId).collection("chat").document(msgId).set(msg);
        db.collection("emergency_broadcasts").document(alertId).update("messageCount", FieldValue.increment(1));
    }

    // Listen to rescue chat messages
    public ListenerRegistration listenToRescueChat(String alertId, EventListener<QuerySnapshot> listener) {
        return db.collection("emergency_broadcasts").document(alertId).collection("chat")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    // Calculate zone threat status dynamically based on user's current GPS location, registered safety_zones & active SOS alerts
    public void calculateZoneThreat(double currentLat, double currentLng, OnZoneCalculatedListener listener) {
        if (currentLat == 0.0 && currentLng == 0.0) {
            if (listener != null) listener.onZoneCalculated("SAFE", 0, 0);
            return;
        }

        db.collection("safety_zones").get().addOnCompleteListener(zoneTask -> {
            boolean inDangerZone = false;
            boolean inModerateZone = false;

            if (zoneTask.isSuccessful() && zoneTask.getResult() != null) {
                for (DocumentSnapshot doc : zoneTask.getResult().getDocuments()) {
                    Object latObj = doc.get("latitude");
                    Object lngObj = doc.get("longitude");
                    if (latObj instanceof Number && lngObj instanceof Number) {
                        double zLat = ((Number) latObj).doubleValue();
                        double zLng = ((Number) lngObj).doubleValue();

                        double rad = 1000.0;
                        Object radObj = doc.get("radiusInMeters");
                        if (radObj == null) radObj = doc.get("radius");
                        if (radObj instanceof Number) rad = ((Number) radObj).doubleValue();

                        float[] dist = new float[1];
                        Location.distanceBetween(currentLat, currentLng, zLat, zLng, dist);

                        if (dist[0] <= rad) {
                            String rLevel = doc.getString("riskLevel");
                            if (rLevel == null) rLevel = doc.getString("zoneType");
                            if (rLevel == null) rLevel = doc.getString("type");
                            if (rLevel == null) rLevel = doc.getId();

                            String upper = rLevel.toUpperCase();
                            if (upper.contains("DANGER") || upper.contains("CRITICAL") || upper.equals("RED")) {
                                inDangerZone = true;
                            } else if (upper.contains("MODERATE") || upper.contains("CAUTION") || upper.contains("WARNING") || upper.equals("YELLOW")) {
                                inModerateZone = true;
                            }
                        }
                    }
                }
            }

            final boolean staticDanger = inDangerZone;
            final boolean staticModerate = inModerateZone;

            db.collection("emergency_broadcasts")
                    .get()
                    .addOnSuccessListener(snapshots -> {
                        int activeNearbyDangerCount = 0;
                        int activeCautionCount = 0;
                        Set<String> uniqueVictims = new HashSet<>();
                        boolean dynamicDangerFromCluster = false;
                        boolean dynamicModerateFromCluster = false;

                        long now = System.currentTimeMillis();
                        long oneMonthAgo = now - (30L * 24 * 60 * 60 * 1000);
                        List<DocumentSnapshot> allDocs = snapshots.getDocuments();

                        for (DocumentSnapshot doc : allDocs) {
                            Double lat = doc.getDouble("latitude");
                            Double lng = doc.getDouble("longitude");
                            String status = doc.getString("status");
                            String uid = doc.getString("userId");

                            if (lat == null || lng == null) continue;

                            float[] distUser = new float[1];
                            Location.distanceBetween(currentLat, currentLng, lat, lng, distUser);

                            // Active danger nearby
                            if ("ACTIVE_DANGER".equalsIgnoreCase(status)) {
                                if (distUser[0] <= 1500) {
                                    activeNearbyDangerCount++;
                                    if (uid != null) uniqueVictims.add(uid);
                                } else if (distUser[0] <= 3500) {
                                    activeCautionCount++;
                                }
                            }

                            // Density calculation within 100m radius of this SOS point
                            int count1M = 0;
                            int countTot = 0;
                            for (DocumentSnapshot other : allDocs) {
                                Double oLat = other.getDouble("latitude");
                                Double oLng = other.getDouble("longitude");
                                Long oTime = other.getLong("timestamp");

                                if (oLat != null && oLng != null) {
                                    float[] distCluster = new float[1];
                                    Location.distanceBetween(lat, lng, oLat, oLng, distCluster);
                                    if (distCluster[0] <= 100) {
                                        countTot++;
                                        if (oTime != null && oTime >= oneMonthAgo) {
                                            count1M++;
                                        }
                                    }
                                }
                            }

                            // 100m 7/15 SOS rule -> 2 KM Danger Zone
                            if (count1M >= 7 || countTot >= 15) {
                                if (distUser[0] <= 2000) {
                                    dynamicDangerFromCluster = true;
                                }
                            } else if (count1M >= 3 || countTot >= 6) {
                                if (distUser[0] <= 1000) {
                                    dynamicModerateFromCluster = true;
                                }
                            }
                        }

                        String zone = "SAFE";
                        if (staticDanger || dynamicDangerFromCluster || activeNearbyDangerCount > 0) {
                            zone = "DANGER";
                        } else if (staticModerate || dynamicModerateFromCluster || activeCautionCount > 0) {
                            zone = "MODERATE";
                        }

                        if (listener != null) {
                            listener.onZoneCalculated(zone, activeNearbyDangerCount, uniqueVictims.size());
                        }
                    })
                    .addOnFailureListener(e -> {
                        String zone = staticDanger ? "DANGER" : (staticModerate ? "MODERATE" : "SAFE");
                        if (listener != null) listener.onZoneCalculated(zone, 0, 0);
                    });
        });
    }

    // Evaluate 100m cluster for specific SOS location and save/update into safety_zones in Firestore
    public void evaluateAndSyncDangerZonesToFirestore(double triggerLat, double triggerLng) {
        db.collection("emergency_broadcasts")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) return;

                    long now = System.currentTimeMillis();
                    long oneMonthAgo = now - (30L * 24 * 60 * 60 * 1000);
                    int count1M = 0;
                    int countTot = 0;

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        Long time = doc.getLong("timestamp");

                        if (lat != null && lng != null) {
                            float[] dist = new float[1];
                            Location.distanceBetween(triggerLat, triggerLng, lat, lng, dist);
                            // 100 meters radius
                            if (dist[0] <= 100) {
                                countTot++;
                                if (time != null && time >= oneMonthAgo) {
                                    count1M++;
                                }
                            }
                        }
                    }

                    // Rule: 7+ in last 1 month OR 15+ total in 100m radius -> 2 KM DANGER ZONE in safety_zones
                    if (count1M >= 7 || countTot >= 15) {
                        String zoneDocId = "danger_zone_" + String.format(Locale.US, "%.3f_%.3f", triggerLat, triggerLng).replace(".", "_");
                        Map<String, Object> zoneData = new HashMap<>();
                        zoneData.put("title", "🚨 CRITICAL DANGER ZONE (2 KM Perimeter)");
                        zoneData.put("description", "High Incident Frequency: " + count1M + " SOS in past month (" + countTot + " total within 100m cluster). Designated as 2 KM Danger Zone.");
                        zoneData.put("riskLevel", "DANGER");
                        zoneData.put("zoneType", "RED");
                        zoneData.put("latitude", triggerLat);
                        zoneData.put("longitude", triggerLng);
                        zoneData.put("radiusInMeters", 2000.0); // 2 KM
                        zoneData.put("radius", 2000.0);
                        zoneData.put("autoGenerated", true);
                        zoneData.put("incidentCount1Month", count1M);
                        zoneData.put("incidentCountTotal", countTot);
                        zoneData.put("updatedAt", now);

                        db.collection("safety_zones").document(zoneDocId)
                                .set(zoneData, SetOptions.merge());
                    }
                });
    }

    // Evaluate all SOS locations across database and save/update any qualifying 100m clusters into safety_zones in Firestore
    public void evaluateAndSyncAllDangerZones() {
        db.collection("emergency_broadcasts")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) return;

                    long now = System.currentTimeMillis();
                    long oneMonthAgo = now - (30L * 24 * 60 * 60 * 1000);
                    List<DocumentSnapshot> allDocs = snapshots.getDocuments();

                    class ClusterRecord {
                        double lat, lng;
                        int c1M = 0;
                        int cTot = 0;
                    }
                    List<ClusterRecord> qualifyingClusters = new ArrayList<>();

                    for (DocumentSnapshot doc : allDocs) {
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        if (lat == null || lng == null) continue;

                        int c1M = 0;
                        int cTot = 0;

                        for (DocumentSnapshot other : allDocs) {
                            Double oLat = other.getDouble("latitude");
                            Double oLng = other.getDouble("longitude");
                            Long oTime = other.getLong("timestamp");

                            if (oLat != null && oLng != null) {
                                float[] dist = new float[1];
                                Location.distanceBetween(lat, lng, oLat, oLng, dist);
                                if (dist[0] <= 100) {
                                    cTot++;
                                    if (oTime != null && oTime >= oneMonthAgo) {
                                        c1M++;
                                    }
                                }
                            }
                        }

                        if (c1M >= 7 || cTot >= 15) {
                            ClusterRecord matched = null;
                            for (ClusterRecord cr : qualifyingClusters) {
                                float[] dist = new float[1];
                                Location.distanceBetween(lat, lng, cr.lat, cr.lng, dist);
                                if (dist[0] <= 200) {
                                    matched = cr;
                                    break;
                                }
                            }
                            if (matched != null) {
                                if (c1M > matched.c1M) matched.c1M = c1M;
                                if (cTot > matched.cTot) matched.cTot = cTot;
                            } else {
                                ClusterRecord cr = new ClusterRecord();
                                cr.lat = lat;
                                cr.lng = lng;
                                cr.c1M = c1M;
                                cr.cTot = cTot;
                                qualifyingClusters.add(cr);
                            }
                        }
                    }

                    for (ClusterRecord cr : qualifyingClusters) {
                        String zoneDocId = "danger_zone_" + String.format(Locale.US, "%.3f_%.3f", cr.lat, cr.lng).replace(".", "_");
                        Map<String, Object> zoneData = new HashMap<>();
                        zoneData.put("title", "🚨 CRITICAL DANGER ZONE (2 KM Perimeter)");
                        zoneData.put("description", "High Incident Frequency: " + cr.c1M + " SOS in past month (" + cr.cTot + " total within 100m cluster). Designated as 2 KM Danger Zone.");
                        zoneData.put("riskLevel", "DANGER");
                        zoneData.put("zoneType", "RED");
                        zoneData.put("latitude", cr.lat);
                        zoneData.put("longitude", cr.lng);
                        zoneData.put("radiusInMeters", 2000.0); // 2 KM
                        zoneData.put("radius", 2000.0);
                        zoneData.put("autoGenerated", true);
                        zoneData.put("incidentCount1Month", cr.c1M);
                        zoneData.put("incidentCountTotal", cr.cTot);
                        zoneData.put("updatedAt", now);

                        db.collection("safety_zones").document(zoneDocId)
                                .set(zoneData, SetOptions.merge());
                    }
                });
    }
}