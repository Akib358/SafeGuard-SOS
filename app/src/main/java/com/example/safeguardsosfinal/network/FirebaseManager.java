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

            // Only consider currently active emergency broadcasts (status == ACTIVE_DANGER)
            db.collection("emergency_broadcasts")
                    .whereEqualTo("status", "ACTIVE_DANGER")
                    .get()
                    .addOnSuccessListener(snapshots -> {
                        int activeNearbyDangerCount = 0;
                        int activeCautionCount = 0;
                        Set<String> uniqueVictims = new HashSet<>();

                        if (snapshots != null) {
                            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                                Double lat = doc.getDouble("latitude");
                                Double lng = doc.getDouble("longitude");
                                String uid = doc.getString("userId");

                                if (lat == null || lng == null) continue;

                                float[] distUser = new float[1];
                                Location.distanceBetween(currentLat, currentLng, lat, lng, distUser);

                                // Active danger nearby (within 1.5 KM)
                                if (distUser[0] <= 1500) {
                                    activeNearbyDangerCount++;
                                    if (uid != null) uniqueVictims.add(uid);
                                } else if (distUser[0] <= 3500) {
                                    activeCautionCount++;
                                }
                            }
                        }

                        String zone = "SAFE";
                        if (staticDanger || activeNearbyDangerCount > 0) {
                            zone = "DANGER";
                        } else if (staticModerate || activeCautionCount > 0) {
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

    // Clean up any auto-generated danger zone documents from safety_zones collection in database
    public void cleanupAutoGeneratedSafetyZones() {
        db.collection("safety_zones")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null) return;
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Boolean autoGen = doc.getBoolean("autoGenerated");
                        String id = doc.getId();
                        if (Boolean.TRUE.equals(autoGen) || id.startsWith("danger_zone_") || id.startsWith("dynamic_")) {
                            db.collection("safety_zones").document(id).delete();
                        }
                    }
                });
    }
}