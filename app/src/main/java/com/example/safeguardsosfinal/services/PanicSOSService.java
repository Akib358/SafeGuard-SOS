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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.network.FirebaseManager;
import com.example.safeguardsosfinal.ui.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PanicSOSService extends Service {

    private static final String CHANNEL_ID = "SafeGuard_SOS_Foreground_Channel";
    private static final String CHANNEL_GUARDIAN = "CHANNEL_GUARDIAN_CRITICAL"; // গার্ডিয়ান চ্যানেল (সাইরেন সাউন্ড)
    private static final String CHANNEL_RADAR = "CHANNEL_RADAR_1KM";            // রেডার চ্যানেল (স্ট্যান্ডার্ড এলার্ম)
    private ListenerRegistration radarListener;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        startForeground(1001, buildForegroundNotification("SafeGuard Protection Active"));
        listenForProximityAndGuardianAlerts();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "TRIGGER_PANIC_SOS".equals(intent.getAction())) {
            executePanicProtocol();
        }
        return START_STICKY;
    }

    private void executePanicProtocol() {
        Location location = getLastKnownLocation();
        double lat = location != null ? location.getLatitude() : 23.8759;
        double lng = location != null ? location.getLongitude() : 90.3795;

        SharedPreferences sp = getSharedPreferences("SafeGuardContacts", MODE_PRIVATE);
        String savedNumbers = sp.getString("trusted_numbers_list", "");
        List<String> guardiansList = Arrays.asList(savedNumbers.split(","));

        // ক্লাউড ডেটাবেজে গার্ডিয়ান লিস্ট সহ ব্রডকাস্ট পাঠানো
        String alertId = "sos_" + System.currentTimeMillis();
        Map<String, Object> sosData = new HashMap<>();
        sosData.put("alertId", alertId);
        sosData.put("userId", FirebaseAuth.getInstance().getUid());
        sosData.put("userName", "SafeGuard User");
        sosData.put("phone", getSharedPreferences("SafeGuardUser", MODE_PRIVATE).getString("user_phone", "Unknown"));
        sosData.put("latitude", lat);
        sosData.put("longitude", lng);
        sosData.put("timestamp", System.currentTimeMillis());
        sosData.put("status", "ACTIVE_DANGER");
        sosData.put("trustedGuardians", guardiansList);

        FirebaseFirestore.getInstance().collection("emergency_broadcasts").document(alertId).set(sosData);

        // ট্রাস্টেড নাম্বারে জিপিএস সহ এসএমএস পাঠানো
        String mapLink = "https://maps.google.com/?q=" + lat + "," + lng;
        sendSmsToTrustedContacts("EMERGENCY SOS ALERT! I am in critical danger. Live GPS: " + mapLink);

        // ৯৯৯ ডায়ালারে পাঠানো
        if (getSharedPreferences("SafeGuardSettings", MODE_PRIVATE).getBoolean("auto_call_999", true)) {
            trigger999EmergencyDial();
        }
    }

    private void listenForProximityAndGuardianAlerts() {
        SharedPreferences sp = getSharedPreferences("SafeGuardUser", MODE_PRIVATE);
        String myPhone = sp.getString("user_phone", "");

        radarListener = FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .whereEqualTo("status", "ACTIVE_DANGER")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    Location myLoc = getLastKnownLocation();

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            Double lat = dc.getDocument().getDouble("latitude");
                            Double lng = dc.getDocument().getDouble("longitude");
                            String sender = dc.getDocument().getString("userName");
                            List<String> guardians = (List<String>) dc.getDocument().get("trustedGuardians");

                            if (lat != null && lng != null) {
                                boolean isMyGuardian = (guardians != null && !myPhone.isEmpty() && guardians.contains(myPhone));

                                if (isMyGuardian) {
                                    // ১. আমাকে যে ইমার্জেন্সিতে যুক্ত করেছে তার উচ্চ-তীব্রতার সাইরেন নোটিফিকেশন
                                    showHeadsUpNotification(
                                            CHANNEL_GUARDIAN,
                                            "🚨 GUARDIAN SOS: " + sender + " Needs Help!",
                                            "Tap for shortest navigation route to their exact location.",
                                            lat, lng, 101
                                    );
                                } else if (myLoc != null) {
                                    Location vicLoc = new Location("Vic");
                                    vicLoc.setLatitude(lat);
                                    vicLoc.setLongitude(lng);
                                    float dist = myLoc.distanceTo(vicLoc);
                                    if (dist <= 1000) {
                                        // ২. ১ কিমি রেডিয়াসের জন্য এলাকার নোটিফিকেশন
                                        showHeadsUpNotification(
                                                CHANNEL_RADAR,
                                                "⚠️ 1KM Radar: Danger Alert Nearby!",
                                                "Someone triggered SOS ~" + ((int) dist) + "m away. Tap for route.",
                                                lat, lng, 102
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
        if (nm != null) nm.notify(notifId + (int) System.currentTimeMillis(), notification);
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

            // চ্যানেল ১: গার্ডিয়ান অ্যালার্ট (সাইরেন বা এলার্ম রিংটোন সাউন্ড)
            NotificationChannel guardianChannel = new NotificationChannel(
                    CHANNEL_GUARDIAN,
                    "Guardian Direct SOS (Critical)",
                    NotificationManager.IMPORTANCE_HIGH
            );
            guardianChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes);
            guardianChannel.enableVibration(true);

            // চ্যানেল ২: ১ কিমি এরিয়া রেডার অ্যালার্ট (নোটিফিকেশন সাউন্ড)
            NotificationChannel radarChannel = new NotificationChannel(
                    CHANNEL_RADAR,
                    "1KM Proximity Radar Alert",
                    NotificationManager.IMPORTANCE_HIGH
            );
            radarChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);
            radarChannel.enableVibration(true);

            // ব্যাকগ্রাউন্ড সার্ভিস চ্যানেল
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