# SafeGuard SOS 🚨
### Real-Time Emergency Response, Dynamic Threat Zones & Community Safety Network

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Language-Java%208-ED8B00.svg?style=flat&logo=openjdk)](https://www.oracle.com/java/)
[![Backend](https://img.shields.io/badge/Backend-Firebase%20Firestore-FFCA28.svg?style=flat&logo=firebase)](https://firebase.google.com/)
[![Maps](https://img.shields.io/badge/Maps-Leaflet%20%7C%20OpenStreetMap-199900.svg?style=flat&logo=leaflet)](https://leafletjs.com/)
[![Release](https://img.shields.io/badge/Release-v2.2-blue.svg)](https://github.com/Akib358/SafeGuard-SOS/releases)
[![Author](https://img.shields.io/badge/Developer-Akib%20(Akib358)-black.svg?style=flat&logo=github)](https://github.com/Akib358)

---

## 📌 Overview

**SafeGuard SOS** is an advanced personal safety, emergency distress broadcasting, and community rescue Android application developed to provide immediate protection in critical situations. 

With real-time GPS tracking, hardware-accelerated mapping, dynamic high-risk threat zone clustering, emergency blood matching, and active incident response hubs, SafeGuard SOS empowers victims to summon help within seconds while coordinating with trusted guardians and nearby responders.

---

## 🚀 Key Features

### 1. 🚨 One-Tap Instant Panic SOS Protocol
- **Foreground Panic Service**: Instantly broadcasts distress signals with exact GPS coordinates even when the app is minimized or the screen is locked.
- **Automated Emergency Dispatch**: Sends automated emergency SMS containing live Google Maps location links to all registered trusted contacts.
- **Direct 999 Dialing**: Direct integration with national emergency services dialer (999).
- **Anti-Spam & Single-Alert Integrity**: Debounced trigger logic ensures each incident generates a single, verified broadcast document associated with the victim's authenticated profile.

### 2. 🔴 Intelligent Dynamic Threat Zones (100m Clustering & 2 KM Danger Radius)
- **Automated Threat Density Algorithm**: Analyzes historical and real-time SOS frequency across coordinates.
- **Threshold Rule**: Any area with **$\ge 7$ SOS broadcasts within the last 1 month** OR **$\ge 15$ total broadcasts** within a 100-meter radius is automatically classified as a **2 KM Critical Danger Zone**.
- **Real-Time Database Sync**: Automatically persists newly identified danger zones into Firebase Firestore (`safety_zones`), alerting all nearby users in real time.
- **Color-Coded Risk Levels**:
  - 🔴 **Danger Zone** (`#FF1744`): High incident frequency or active emergency within 2 KM perimeter.
  - 🟡 **Moderate Zone** (`#FFD600`): Elevated incident risk within 1 KM perimeter.
  - 🟢 **Safe Zone** (`#00E676`): Verified secure areas, police stations, and hospitals.

### 3. 🗺️ Live Map Radar & Proximity Scanner
- Powered by Leaflet.js and OpenStreetMap inside an optimized Android WebView.
- 15 KM dynamic perimeter scanner with real-time GPS positioning.
- Interactive filter toggles for Danger, Moderate, and Safe zones.
- Single-tap GPS coordinates copy and external Google Maps navigation routing.

### 4. 💬 Active SOS Hub & Real-Time Incident Chat
- **Rescue Incident Hub**: Live dashboard showing all ongoing emergency broadcasts, distances from the user, and victim details.
- **Real-Time Communication**: In-app rescue chat room allowing responders, guardians, and the victim to share updates, location milestones, and rescue status.
- **Safe Resolution Workflow**: Victims can resolve active broadcasts with detailed safe notes once the danger has passed.

### 5. 🩸 Emergency Blood Donation Network
- Peer-to-peer blood request broadcasting across all major blood groups (A+, A-, B+, B-, AB+, AB-, O+, O-).
- Filter by blood group, urgency status, and hospital location with direct one-tap calling to donors and requesters.

### 6. 👶 Missing Child Alert System
- Community-driven rapid notification board for missing children.
- Uploads child photos, physical descriptions, last known location, and direct guardian contact numbers.

### 7. 🛡️ Background Safety Sensors
- Shake and impact detection to trigger emergency protocol hands-free.

---

## 📱 App Screenshots & UI Architecture

| Safety Dashboard | Live Map Radar | Active SOS Hub |
|:---:|:---:|:---:|
| Quick SOS Trigger & Live Threat Badge | Leaflet Heatmap & 2 KM Danger Zones | Real-Time Incident Chat & Rescue Ops |

---

## 🛠️ Tech Stack & Architecture

- **Operating System / Platform**: Android (Minimum SDK 24, Target SDK 34)
- **Programming Language**: Java (Java 8 compatibility)
- **UI / Frontend**: Material 3 Components, ViewBinding, Custom Drawables & Glassmorphic Cards
- **Maps Engine**: Leaflet.js + OpenStreetMap engine integrated via Android WebView & JavaScriptInterface
- **Backend & Cloud Database**:
  - **Firebase Authentication**: Phone & Email authentication
  - **Cloud Firestore**: Real-time collections for `emergency_broadcasts`, `safety_zones`, `blood_requests`, `missing_children`, and incident chat subcollections
- **Architecture**: Modular Fragment Architecture, Android Foreground Services, Location Services (FusedLocationProviderClient / LocationManager)

---

## 📥 Download APK

Get the latest stable release of the application:
- 📦 **Download Version 2.2**: [SafeGuard.SOS.apk](https://github.com/Akib358/SafeGuard-SOS/releases/tag/SafeGuard_SOS--V2.2)

---

## ⚙️ Build and Installation

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17 or higher
- Android SDK 34

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/Akib358/SafeGuard-SOS.git
   cd SafeGuard-SOS
   ```

2. Open the project in Android Studio.

3. Configure Firebase:
   - Place your `google-services.json` inside the `app/` directory.

4. Build the Debug or Release APK:
   ```bash
   ./gradlew assembleDebug
   ```

5. Install on device via ADB:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 👤 Developer

**Akib**
- GitHub: [@Akib358](https://github.com/Akib358)
- Project: University Final Year / Capstone Defense

---

## 📄 License

This project is licensed under the MIT License - feel free to use and adapt it for research and educational purposes.
