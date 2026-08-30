package com.example.safeguardsosfinal.models;

public class BloodRequest {
    private String requestId;
    private String requesterId;
    private String patientName;
    private String bloodGroup;
    private String hospitalName;
    private String location;
    private String phone;
    private String gender;
    private String requiredUnits;
    private String emergencyReason;
    private String notes;
    private double latitude;
    private double longitude;
    private long timestamp;
    private String status;

    public BloodRequest() {}

    public BloodRequest(String requestId, String requesterId, String patientName, String bloodGroup,
                        String location, String phone, String notes, double latitude, double longitude,
                        long timestamp, String status) {
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.patientName = patientName;
        this.bloodGroup = bloodGroup;
        this.location = location;
        this.hospitalName = location;
        this.phone = phone;
        this.notes = notes;
        this.emergencyReason = notes;
        this.gender = "Unspecified";
        this.requiredUnits = "1 Bag";
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.status = status;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getHospitalName() { return (hospitalName != null && !hospitalName.isEmpty()) ? hospitalName : location; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGender() { return gender != null ? gender : "Unspecified"; }
    public void setGender(String gender) { this.gender = gender; }

    public String getRequiredUnits() { return requiredUnits != null ? requiredUnits : "1 Bag"; }
    public void setRequiredUnits(String requiredUnits) { this.requiredUnits = requiredUnits; }

    public String getEmergencyReason() { return (emergencyReason != null && !emergencyReason.isEmpty()) ? emergencyReason : notes; }
    public void setEmergencyReason(String emergencyReason) { this.emergencyReason = emergencyReason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}