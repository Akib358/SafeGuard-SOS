package com.example.safeguardsosfinal.network;

import com.example.safeguardsosfinal.models.BloodRequest;
import com.example.safeguardsosfinal.models.MissingChild;
import com.example.safeguardsosfinal.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // ইউজার প্রোফাইল ডেটাবেজে সেভ করার মেথড
    public void saveUser(User user, OnCompleteListener listener) {
        db.collection("users")
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onFailure(e);
                });
    }

    public void postBloodRequest(BloodRequest request, OnCompleteListener listener) {
        db.collection("blood_requests")
                .document(request.getRequestId())
                .set(request)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onFailure(e);
                });
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
                        BloodRequest req = doc.toObject(BloodRequest.class);
                        list.add(req);
                    }
                    if (listener != null) listener.onLoaded(list);
                });
    }

    public void postMissingChild(MissingChild report, OnCompleteListener listener) {
        db.collection("missing_children")
                .document(report.getReportId())
                .set(report)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onFailure(e);
                });
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
                        MissingChild item = doc.toObject(MissingChild.class);
                        list.add(item);
                    }
                    if (listener != null) listener.onLoaded(list);
                });
    }

    public void broadcastSOS(double lat, double lng, String senderPhone, OnCompleteListener listener) {
        String alertId = "sos_" + System.currentTimeMillis();
        Map<String, Object> sosData = new HashMap<>();
        sosData.put("alertId", alertId);
        sosData.put("userId", auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown");
        sosData.put("latitude", lat);
        sosData.put("longitude", lng);
        sosData.put("phone", senderPhone);
        sosData.put("timestamp", System.currentTimeMillis());
        sosData.put("status", "ACTIVE_DANGER");

        db.collection("emergency_broadcasts")
                .document(alertId)
                .set(sosData)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onFailure(e);
                });
    }
}