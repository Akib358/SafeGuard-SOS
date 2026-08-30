package com.example.safeguardsosfinal.models;

public class MissingChild {
    private String reportId;
    private String reporterId;
    private String childName;
    private String age;
    private String lastSeenLocation;
    private String contactPhone;
    private String description;
    private String photoUrl;
    private double latitude;
    private double longitude;
    private long timestamp;
    private String status;
    private Boolean edited;
    private long editedAt;

    public MissingChild() {}

    public MissingChild(String reportId, String reporterId, String childName, String age,
                        String lastSeenLocation, String contactPhone, String description,
                        String photoUrl, double latitude, double longitude, long timestamp,
                        String status) {
        this.reportId = reportId;
        this.reporterId = reporterId;
        this.childName = childName;
        this.age = age;
        this.lastSeenLocation = lastSeenLocation;
        this.contactPhone = contactPhone;
        this.description = description;
        this.photoUrl = photoUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.status = status;
        this.edited = false;
        this.editedAt = 0;
    }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }

    public String getUserId() { return reporterId; }
    public void setUserId(String userId) { this.reporterId = userId; }

    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public String getLastSeenLocation() { return lastSeenLocation; }
    public void setLastSeenLocation(String lastSeenLocation) { this.lastSeenLocation = lastSeenLocation; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getEdited() { return edited != null && edited; }
    public void setEdited(Boolean edited) { this.edited = edited; }

    public long getEditedAt() { return editedAt; }
    public void setEditedAt(long editedAt) { this.editedAt = editedAt; }
}