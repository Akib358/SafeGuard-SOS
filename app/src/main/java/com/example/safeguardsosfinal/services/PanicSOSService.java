package com.example.safeguardsosfinal.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.telephony.SmsManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.safeguardsosfinal.ui.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PanicSOSService extends Service {

    private static final String CHANNEL_ID = "SafeGuard_SOS_Foreground_Channel";
    private static final String CHANNEL_GUARDIAN = "CHANNEL_GUARDIAN_CRITICAL";
    private static final String CHANNEL_RADAR = "CHANNEL_RADAR_1KM";
    private ListenerRegistration radarListener;

    private static final Set<String> notifiedAlertIds = new HashSet<>();
    private long serviceStartTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceStartTime = System.currentTimeMillis();
        createNotificationChannels();
        startForeground(1001, buildForegroundNotification("SafeGuard Protection Active"));
        listenForProximityAndGuardianAlerts();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "TRIGGER_PANIC_SOS".equals(intent.getAction())) {
            double extraLat = intent.getDoubleExtra("latitude", Double.NaN);
            double extraLng = intent.getDoubleExtra("longitude", Double.NaN);
            executePanicProtocol(extraLat, extraLng);
        }
        return START_STICKY;
    }

    private void executePanicProtocol(double passedLat, double passedLng) {
        double lat = !Double.isNaN(passedLat) ? passedLat : 23.8759;
        double lng = !Double.isNaN(passedLng) ? passedLng : 90.3795;

        if (Double.isNaN(passedLat) || Double.isNaN(passedLng)) {
            Location location = getLastKnownLocation();
            if (location != null) {
                lat = location.getLatitude();
                lng = location.getLongitude();
            }
        }

        SharedPreferences sp = getSharedPreferences("SafeGuardContacts", MODE_PRIVATE);
        String savedNumbers = sp.getString("trusted_numbers_list", "");
        List<String> guardiansList = Arrays.asList(savedNumbers.split(","));

        String alertId = "sos_" + System.currentTimeMillis();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = user != null ? user.getUid() : "unknown";

        SharedPreferences userSp = getSharedPreferences("SafeGuardUser", MODE_PRIVATE);
        String currentPhone = userSp.getString("user_phone", "");
        String currentName = userSp.getString("user_name", "");

        if (user != null) {
            if (currentName.isEmpty() && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                currentName = user.getDisplayName();
            }
            if (currentPhone.isEmpty() && user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
                currentPhone = user.getPhoneNumber();
            }
        }
        if (currentName.isEmpty()) currentName = "SafeGuard Victim";
        if (currentPhone.isEmpty()) currentPhone = "N/A";

        // Prevent self-notification: add alertId to notified list immediately
        notifiedAlertIds.add(alertId);
        notifiedAlertIds.add(alertId + "_radar");
        notifiedAlertIds.add(alertId + "_guardian");

        Map<String, Object> sosData = new HashMap<>();
        sosData.put("alertId", alertId);
        sosData.put("userId", currentUid);
        sosData.put("userName", currentName);
        sosData.put("phone", currentPhone);
        sosData.put("latitude", lat);
        sosData.put("longitude", lng);
        sosData.put("timestamp", System.currentTimeMillis());
        sosData.put("status", "ACTIVE_DANGER");
        sosData.put("messageCount", 0);
        sosData.put("safeNote", "");
        sosData.put("trustedGuardians", guardiansList);

        FirebaseFirestore.getInstance().collection("emergency_broadcasts").document(alertId).set(sosData);

        // If phone or name was not in local cache, fetch from Firestore users collection and update document
        if ("SafeGuard Victim".equals(currentName) || "N/A".equals(currentPhone)) {
            if (user != null) {
                FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                        .addOnSuccessListener(doc -> {
                            if (doc != null && doc.exists()) {
                                String n = doc.getString("name");
                                String p = doc.getString("phone");
                                Map<String, Object> updates = new HashMap<>();
                                if (n != null && !n.isEmpty()) {
                                    updates.put("userName", n);
                                    userSp.edit().putString("user_name", n).apply();
                                }
                                if (p != null && !p.isEmpty()) {
                                    updates.put("phone", p);
                                    userSp.edit().putString("user_phone", p).apply();
                                }
                                if (!updates.isEmpty()) {
                                    FirebaseFirestore.getInstance().collection("emergency_broadcasts").document(alertId).update(updates);
                                }
                            }
                        });
            }
        }

        // Send SMS with GPS coordinates
        String mapLink = "https://maps.google.com/?q=" + lat + "," + lng;
        sendSmsToTrustedContacts("EMERGENCY SOS ALERT! I am in critical danger. Live GPS: " + mapLink);

        // Direct 999 Dialer
        if (getSharedPreferences("SafeGuardSettings", MODE_PRIVATE).getBoolean("auto_call_999", true)) {
            trigger999EmergencyDial();
        }

        // Show immediate local heads-up notification to victim device confirming active broadcast
        try {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 1002, mainIntent, PendingIntent.FLAG_IMMUTABLE);

            Notification alertNotification = new NotificationCompat.Builder(this, CHANNEL_GUARDIAN)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("🚨 EMERGENCY SOS BROADCAST ACTIVE!")
                    .setContentText("Live GPS broadcasted to trusted contacts & radar. Tap to view rescue status.")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(1002, alertNotification);
            }
        } catch (Exception ignored) {}
    }

    private void listenForProximityAndGuardianAlerts() {
        SharedPreferences sp = getSharedPreferences("SafeGuardUser", MODE_PRIVATE);
        String myPhone = sp.getString("user_phone", "");

        radarListener = FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .whereEqualTo("status", "ACTIVE_DANGER")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    Location myLoc = getLastKnownLocation();
                    String myUid = FirebaseAuth.getInstance().getUid();

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            String alertId = dc.getDocument().getString("alertId");
                            if (alertId == null) alertId = dc.getDocument().getId();
                            Long timestamp = dc.getDocument().getLong("timestamp");
                            String userId = dc.getDocument().getString("userId");
                            String docPhone = dc.getDocument().getString("phone");

                            // Ignore own alerts (by userId and by phone)
                            if (userId != null && myUid != null && userId.equals(myUid)) {
                                continue;
                            }
                            if (docPhone != null && !myPhone.isEmpty() && docPhone.equals(myPhone)) {
                                continue;
                            }

                            // Anti-Spam: Past alerts created before app launch should NOT spam the phone lockscreen
                            if (timestamp == null || timestamp < (serviceStartTime - 45000)) {
                                notifiedAlertIds.add(alertId);
                                continue;
                            }

                            // Already notified this alert
                            if (notifiedAlertIds.contains(alertId)) {
                                continue;
                            }
                            notifiedAlertIds.add(alertId);

                            Double lat = dc.getDocument().getDouble("latitude");
                            Double lng = dc.getDocument().getDouble("longitude");
                            String sender = dc.getDocument().getString("userName");
                            List<String> guardians = (List<String>) dc.getDocument().get("trustedGuardians");

                            if (lat != null && lng != null) {
                                boolean isMyGuardian = (guardians != null && !myPhone.isEmpty() && guardians.contains(myPhone));
                                int stableNotifId = Math.abs(alertId.hashCode());

                                if (isMyGuardian) {
                                    showHeadsUpNotification(
                                            CHANNEL_GUARDIAN,
                                            "🚨 GUARDIAN SOS: " + (sender != null ? sender : "Emergency Victim") + " Needs Help!",
                                            "Tap for shortest navigation route to their exact location.",
                                            lat, lng, stableNotifId
                                    );
                                } else if (myLoc != null) {
                                    Location vicLoc = new Location("Vic");
                                    vicLoc.setLatitude(lat);
                                    vicLoc.setLongitude(lng);
                                    float dist = myLoc.distanceTo(vicLoc);
                                    if (dist <= 1500) {
                                        showHeadsUpNotification(
                                                CHANNEL_RADAR,
                                                "⚠️ 1.5KM Radar: Danger Alert Nearby!",
                                                "Someone triggered SOS ~" + ((int) dist) + "m away. Tap for route.",
                                                lat, lng, stableNotifId
                                        );
                                    }
                                }
                            }
                        }
                    }
                });
    }

    private void showHeadsUpNotification(String channelId, String title, String msg, double lat, double lng, int notifId) {
        String uri = "google.navigation:q=" + lat + "," + lng + "&mode=d";
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        PendingIntent pi = PendingIntent.getActivity(this, notifId, mapIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(notifId, notification);
        }
    }

    private void sendSmsToTrustedContacts(String message) {
        SharedPreferences sp = getSharedPreferences("SafeGuardContacts", MODE_PRIVATE);
        String savedNumbers = sp.getString("trusted_numbers_list", "");
        if (savedNumbers.isEmpty()) return;

        SmsManager smsManager = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? getSystemService(SmsManager.class) : SmsManager.getDefault();
        if (smsManager == null) return;

        for (String phone : savedNumbers.split(",")) {
            String target = phone.trim();
            if (!target.isEmpty()) {
                try {
                    ArrayList<String> parts = smsManager.divideMessage(message);
                    smsManager.sendMultipartTextMessage(target, null, parts, null, null);
                } catch (Exception ignored) {}
            }
        }
    }

    private void trigger999EmergencyDial() {
        try {
            Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:999"));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
        } catch (Exception ignored) {}
    }

    private Location getLastKnownLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        return loc != null ? loc : lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    }

    private Notification buildForegroundNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeGuard SOS Protection")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();

            // Channel 1: Guardian Alert (Critical)
            NotificationChannel guardianChannel = new NotificationChannel(
                    CHANNEL_GUARDIAN,
                    "Guardian Direct SOS (Critical)",
                    NotificationManager.IMPORTANCE_HIGH
            );
            guardianChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes);
            guardianChannel.enableVibration(true);

            // Channel 2: Proximity Radar Alert
            NotificationChannel radarChannel = new NotificationChannel(
                    CHANNEL_RADAR,
                    "1.5KM Proximity Radar Alert",
                    NotificationManager.IMPORTANCE_HIGH
            );
            radarChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);
            radarChannel.enableVibration(true);

            // Background Service Channel
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Defense Service",
                    NotificationManager.IMPORTANCE_LOW
            );

            manager.createNotificationChannel(guardianChannel);
            manager.createNotificationChannel(radarChannel);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (radarListener != null) radarListener.remove();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}