package com.example.safeguardsosfinal.models;

public class SOSAlert {
    private String alertId;
    private String userId;
    private String userName;
    private String userPhone;
    private double latitude;
    private double longitude;
    private String status;
    private long timestamp;

    public SOSAlert() {}

    public SOSAlert(String alertId, String userId, String userName, String userPhone, double latitude, double longitude, String status, long timestamp) {
        this.alertId = alertId;
        this.userId = userId;
        this.userName = userName;
        this.userPhone = userPhone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.timestamp = timestamp;
    }

    public SOSAlert(String alertId, String userId, double latitude, double longitude, long timestamp) {
        this.alertId = alertId;
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.status = "Active";
        this.userName = "Unknown";
        this.userPhone = "Unknown";
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public void setUserPhone(String userPhone) {
        this.userPhone = userPhone;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}