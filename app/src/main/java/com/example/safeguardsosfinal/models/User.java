package com.example.safeguardsosfinal.models;

public class User {
    private String uid;
    private String name;
    private String email;
    private String phone;
    private String profession;
    private String address;
    private String bloodGroup;
    private String emergencyContact;
    private long createdAt;

    public User() {
        // Firebase Firestore-এর জন্য প্রয়োজনীয় ফাঁকা কনস্ট্রাক্টর
    }

    // RegisterActivity-র জন্য কনস্ট্রাক্টর (uid, name, email, phone, profession, address/etc, createdAt)
    public User(String uid, String name, String email, String phone, String profession, String address, long createdAt) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.profession = profession;
        this.address = address;
        this.createdAt = createdAt;
    }

    // RegisterActivity-র জন্য ৬টি স্ট্রিং কনস্ট্রাক্টর (createdAt ছাড়া)
    public User(String uid, String name, String email, String phone, String profession, String address) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.profession = profession;
        this.address = address;
        this.createdAt = System.currentTimeMillis();
    }

    // ৮-প্যারামিটার কনস্ট্রাক্টর (সব ফিল্ড সহ)
    public User(String uid, String name, String email, String phone, String profession, String address, String bloodGroup, long createdAt) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.profession = profession;
        this.address = address;
        this.bloodGroup = bloodGroup;
        this.createdAt = createdAt;
    }

    // ৫-প্যারামিটার কনস্ট্রাক্টর
    public User(String uid, String name, String email, String phone, long createdAt) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.createdAt = createdAt;
    }

    // ৪-প্যারামিটার কনস্ট্রাক্টর
    public User(String uid, String name, String email, String phone) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.createdAt = System.currentTimeMillis();
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getProfession() { return profession != null ? profession : ""; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getAddress() { return address != null ? address : ""; }
    public void setAddress(String address) { this.address = address; }

    public String getBloodGroup() { return bloodGroup != null ? bloodGroup : ""; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getEmergencyContact() { return emergencyContact != null ? emergencyContact : ""; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}