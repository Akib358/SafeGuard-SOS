package com.example.safeguardsosfinal.models;

public class SOSNotification {
    private String alertId;
    private String senderName;
    private String senderPhone;
    private double latitude;
    private double longitude;
    private long timestamp;
    private boolean isDirectGuardianAlert;
    private String status;
    private String safeNote;
    private int messageCount;
    private long resolvedTimestamp;

    public SOSNotification() {}

    public SOSNotification(String alertId, String senderName, String senderPhone, double latitude, double longitude, long timestamp, boolean isDirectGuardianAlert) {
        this.alertId = alertId;
        this.senderName = senderName;
        this.senderPhone = senderPhone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.isDirectGuardianAlert = isDirectGuardianAlert;
        this.status = "ACTIVE_DANGER";
        this.safeNote = "";
        this.messageCount = 0;
        this.resolvedTimestamp = 0;
    }

    public SOSNotification(String alertId, String senderName, String senderPhone, double latitude, double longitude, long timestamp, boolean isDirectGuardianAlert, String status, String safeNote, int messageCount, long resolvedTimestamp) {
        this.alertId = alertId;
        this.senderName = senderName;
        this.senderPhone = senderPhone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.isDirectGuardianAlert = isDirectGuardianAlert;
        this.status = status;
        this.safeNote = safeNote;
        this.messageCount = messageCount;
        this.resolvedTimestamp = resolvedTimestamp;
    }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isDirectGuardianAlert() { return isDirectGuardianAlert; }
    public void setDirectGuardianAlert(boolean directGuardianAlert) { isDirectGuardianAlert = directGuardianAlert; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSafeNote() { return safeNote; }
    public void setSafeNote(String safeNote) { this.safeNote = safeNote; }

    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    public long getResolvedTimestamp() { return resolvedTimestamp; }
    public void setResolvedTimestamp(long resolvedTimestamp) { this.resolvedTimestamp = resolvedTimestamp; }
}