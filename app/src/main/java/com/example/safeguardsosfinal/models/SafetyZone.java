package com.example.safeguardsosfinal.models;

public class SafetyZone {
    private String zoneId;
    private String zoneName;
    private String zoneType; // SAFE, CAUTION, DANGER, CRITICAL
    private double latitude;
    private double longitude;
    private double radius;

    public SafetyZone() {}

    public SafetyZone(String zoneId, String zoneName, String zoneType, double latitude, double longitude, double radius) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.zoneType = zoneType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getZoneType() { return zoneType; }
    public void setZoneType(String zoneType) { this.zoneType = zoneType; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = radius; }

    // --- MapRadarFragment-এর জন্য সাপোর্টিং অ্যালিয়াস মেথড ---

    public double getRadiusInMeters() {
        return radius > 0 ? radius : 1200.0;
    }

    public void setRadiusInMeters(double radiusInMeters) {
        this.radius = radiusInMeters;
    }

    public String getRiskLevel() {
        return zoneType != null ? zoneType : "SAFE";
    }

    public void setRiskLevel(String riskLevel) {
        this.zoneType = riskLevel;
    }

    public String getTitle() {
        return zoneName != null ? zoneName : "Safe Area";
    }

    public void setTitle(String title) {
        this.zoneName = title;
    }
}