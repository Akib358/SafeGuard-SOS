package com.example.safeguardsosfinal.models;

public class SOSNotification {
    private String alertId;
    private String senderName;
    private String senderPhone;
    private double latitude;
    private double longitude;
    private long timestamp;
    private boolean isDirectGuardianAlert; // true = আমাকে যে ট্রাস্টেড বানিয়েছে, false = ১ কিমি এলাকার অ্যালার্ট

    public SOSNotification() {}

    public SOSNotification(String alertId, String senderName, String senderPhone, double latitude, double longitude, long timestamp, boolean isDirectGuardianAlert) {
        this.alertId = alertId;
        this.senderName = senderName;
        this.senderPhone = senderPhone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.isDirectGuardianAlert = isDirectGuardianAlert;
    }

    public String getAlertId() { return alertId; }
    public String getSenderName() { return senderName; }
    public String getSenderPhone() { return senderPhone; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getTimestamp() { return timestamp; }
    public boolean isDirectGuardianAlert() { return isDirectGuardianAlert; }
}