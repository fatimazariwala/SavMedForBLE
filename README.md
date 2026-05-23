# SavMed — BLE-Powered Emergency Response Platform

> **Reducing emergency response latency to under 5 seconds.**
> A distributed, real-time emergency coordination system integrating Bluetooth Low Energy proximity detection, SIP VoIP calling, live GPS tracking, and a real-time web dashboard — built end-to-end as a startup venture.

<br>

📍 Presented at **IEEE RTC Conference 2024 · Chicago, Illinois**
🏆 Recognised by **Mumbai Police**, investors, and technical stakeholders for social impact and technical innovation

---

## What is SavMed?

Every second counts in an emergency. SavMed was built around one question: *what if help could find you before an ambulance could even be dispatched?*

SavMed is a first-responder coordination platform designed for real-world emergencies where traditional infrastructure is too slow or unavailable. A registered network of "Saviours" — trained bystanders, medical volunteers, or security personnel — are discoverable via Bluetooth, reachable by voice call, and guided to a victim's exact GPS location, all triggered by a **single button press**.

The entire flow — BLE device discovery → SOS alert → SIP voice call → live location share — completes in **under 5 seconds**, validated across 30+ Android devices in real-world pilot tests.

---

## The Problem

In urban emergencies, the average ambulance response time in Indian cities exceeds 10–15 minutes. In that window, a trained bystander nearby could make the difference between life and death — but there is no reliable, fast, offline-capable system to connect them.

SavMed fills that gap.

---

## How It Works

```
Victim presses SOS
        │
        ▼
App scans for nearby registered SavMed devices over BLE
        │
        ▼
Closest Saviour receives push alert + in-app SOS notification
        │
        ▼
SIP voice call established between victim and Saviour (< 5 seconds)
        │
        ▼
Victim's live GPS location streamed to Saviour's map in real time
        │
        ▼
Saviour navigates directly to victim using shared live map
```

No complex menus. No long forms. One button — and help is on the way.

---

## App Screenshots & Workflow

### BLE Device Discovery
![BLE Feature Set](https://github.com/fatimazariwala/SavMedForBLE/screenshots/step1_ble_discovery.png)

### Device Identified & SOS Alert Received
![SOS Workflow](https://github.com/fatimazariwala/SavMedForBLE/screenshots/step2_sos_workflow.png)

### VoIP Call & Emergency Contact Messaging
![VoIP and Chat](https:/github.com/fatimazariwala/SavMedForBLE/screenshots/step3_voip_chat.png)

### Live Location Streamed to Security Room
![Live Tracking](https://github.com/fatimazariwala/SavMedForBLE/screenshots/step4_live_tracking.png)

---

## Core Features

### 🔵 BLE Offline SOS
The app continuously scans for nearby registered SavMed devices using Bluetooth Low Energy. This works **without an active internet connection**, making it reliable in low-connectivity scenarios like basements, rural areas, or network-congested emergencies.

### 📞 SIP VoIP Calling
Voice calls between the help-seeker and responder are established over a custom-deployed **Kamailio SIP proxy**, secured with TLS. Call setup is optimised to complete in under 5 seconds from the moment an SOS is triggered.

### 💬 Real-Time Chat
An in-app messaging channel opens between the victim and their active responder for text-based coordination when voice isn't possible.

### 📍 Live Location Tracking
The victim's GPS coordinates are streamed continuously to the responder the moment an SOS event begins. The responder sees the victim's position updating in real time on a shared map.

### 🗺️ Single Shared Navigation Map
Both the victim and the Saviour see a unified map view — showing both their positions, the distance between them, and a live navigable route. No need for a separate navigation app.

### 📎 Multimedia Sharing
Responders and help-seekers can share images, audio clips, and files through the in-app chat — useful for communicating injury details or scene conditions.

### 🏥 Medical Data Storage
Users can securely store critical medical information — blood type, allergies, chronic conditions, emergency contacts — within the app. This data is accessible to the responding Saviour at the time of the incident.

### 🔔 Push Notifications
Firebase Cloud Messaging (FCM) ensures SOS alerts are delivered to nearby Saviours even when the app is in the background or the screen is off — with no alert missed.

### 🔐 Authentication
Secure user registration and login with role-based access — users register as either a **Help-Seeker** or a **Saviour**, with different app experiences and permissions for each role.

### ⚙️ Foreground Service Optimisation
A persistent Android foreground service keeps BLE scanning and location tracking active at all times, optimised extensively to minimise battery consumption and prevent service termination by the OS.

### 📊 Real-Time Web Dashboard
All incident data — SOS events, responder assignments, location streams, response times — is synced live to a web dashboard via WebSocket. This gives operators and administrators a real-time situational overview across all active incidents.

### 🎨 Clean UI/UX
The Android app follows a role-aware Material Design approach — minimal interaction steps, clear visual hierarchy, and purpose-built screens for both the help-seeker and the Saviour.

---

## System Architecture

SavMed is a distributed system spanning multiple protocols and infrastructure layers, designed and built end-to-end.

```
┌──────────────────────────────────────────────────────────────┐
│                    Android Application                        │
│                                                              │
│   ┌─────────────┐   ┌──────────────┐   ┌─────────────────┐  │
│   │  BLE Engine │   │  SIP Client  │   │  Location &     │  │
│   │  (Offline   │   │  (VoIP over  │   │  Chat Engine    │  │
│   │   SOS scan) │   │   TLS)       │   │  (WebSocket/FCM)│  │
│   └──────┬──────┘   └──────┬───────┘   └────────┬────────┘  │
└──────────┼────────────────┼─────────────────────┼───────────┘
           │ BLE             │ SIP / TLS            │ WebSocket / FCM
           ▼                 ▼                      ▼
   ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │ Nearby BLE   │  │ Kamailio SIP     │  │ Firebase Backend │
   │ Devices      │  │ Proxy (Ubuntu    │  │ FCM · Firestore  │
   │ (Saviours)   │  │ VPS) · RTP/RTCP  │  │ Auth · Storage   │
   └──────────────┘  └──────────────────┘  └────────┬─────────┘
                                                     │
                                              WebSocket / APIs
                                                     ▼
                                         ┌───────────────────┐
                                         │  Web Dashboard    │
                                         │  Real-time events │
                                         │  Live map · Logs  │
                                         └───────────────────┘
```

**Protocol Stack:** BLE · SIP · RTP/RTCP · TLS · WebSocket · TCP/UDP · FCM (HTTP/2)

---

## Infrastructure & Network Engineering

Beyond the application layer, SavMed required building and operating substantial backend and network infrastructure from the ground up.

**SIP Proxy Deployment**
Deployed and configured a Kamailio SIP proxy on a Linux VPS (Ubuntu) to handle dynamic registration of 30+ SIP endpoints. Custom Python scripts were written to automate user registration management — reducing manual operations and ensuring continuous proxy availability.

**End-to-End Network Architecture**
Designed the full network architecture spanning SIP, RTP/RTCP, TLS, WebSocket, BLE, and TCP/UDP — integrating real-time voice, location, and data streams across a distributed system.

**Deep-Level Debugging**
Diagnosed and resolved 35+ network and system-level issues including VoIP call drops, foreground service failures, and packet-level anomalies using Wireshark to inspect SIP/RTP traffic at the TCP/IP layer.

**Performance Optimisation**
Optimised the SIP call setup flow and live location sharing pipeline to achieve emergency response latency consistently under 5 seconds — validated in real-world tests across 30+ Android devices.

---

## Technology Stack

| Layer | Technology | Docs |
|-------|------------|------|
| Mobile Platform | Android (Kotlin) | [kotlinlang.org](https://kotlinlang.org/docs/home.html) |
| VoIP / SIP Client | Linphone SDK (liblinphone) | [docs.linphone.org](https://docs.linphone.org/liblinphone/index.html) |
| BLE | Android Bluetooth LE API — scanning, advertisement, GATT | [developer.android.com/bluetooth](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview) |
| HTTP / REST | Retrofit 2 | [square.github.io/retrofit](https://square.github.io/retrofit/) |
| Maps & Location | Google Maps SDK · FusedLocationProviderClient | [developers.google.com/maps/android](https://developers.google.com/maps/documentation/android-sdk/overview) |
| Push Notifications | Firebase Cloud Messaging (FCM) | [firebase.google.com/docs/cloud-messaging](https://firebase.google.com/docs/cloud-messaging) |
| Authentication | Firebase Authentication | [firebase.google.com/docs/auth](https://firebase.google.com/docs/auth) |
| Database | Firebase Firestore · Room (local) | [firebase.google.com/docs/firestore](https://firebase.google.com/docs/firestore) |
| SIP Proxy | Kamailio | [kamailio.org/docs](https://www.kamailio.org/dokuwiki/doku.php) |
| Media Transport | RTP / RTCP | [RFC 3550](https://datatracker.ietf.org/doc/html/rfc3550) |
| Real-Time Sync | WebSocket | [RFC 6455](https://datatracker.ietf.org/doc/html/rfc6455) |
| Debugging & Analysis | Wireshark | [wireshark.org/docs](https://www.wireshark.org/docs/) |
| Dashboard | WebSocket-driven real-time web interface | — |

---

## Resources & References

Core libraries, tools, and documentation used in building SavMed:

- 📘 **[Kotlin](https://kotlinlang.org/docs/home.html)** — Primary language for Android development
- 📗 **[liblinphone / Linphone SDK](https://docs.linphone.org/liblinphone/index.html)** — SIP stack powering VoIP calls; supports SIP, RTP, TLS, and SRTP
- 🔁 **[Retrofit 2](https://square.github.io/retrofit/)** — Type-safe HTTP client for Android; used for REST API communication
- 🗺️ **[Google Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/overview)** — Live map rendering, GPS marker updates, and route navigation
- 🔥 **[Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)** — Push notification delivery for SOS alerts
- 🔐 **[Firebase Authentication](https://firebase.google.com/docs/auth)** — Role-based user login and registration
- 🗄️ **[Firebase Firestore](https://firebase.google.com/docs/firestore)** — Real-time NoSQL cloud database for incident and user data
- 📡 **[Kamailio SIP Server](https://www.kamailio.org/dokuwiki/doku.php)** — Open-source SIP proxy; deployed on Ubuntu VPS for call routing and endpoint registration
- 🔬 **[Wireshark](https://www.wireshark.org/docs/)** — Network protocol analyser; used for SIP/RTP packet inspection and call-drop debugging
- 📄 **[RFC 3550 — RTP](https://datatracker.ietf.org/doc/html/rfc3550)** — Protocol specification for real-time audio/video transport
- 📄 **[RFC 3261 — SIP](https://datatracker.ietf.org/doc/html/rfc3261)** — Core SIP protocol specification

---

## Pilot Results

| Metric | Result |
|--------|--------|
| Emergency response latency | **< 5 seconds** |
| BLE discovery range | **Up to ~45 metres** |
| Devices tested | **30+ Android devices** |
| SIP endpoints managed | **30+ concurrent registrations** |
| Network/system issues resolved | **35+** |
| SIP proxy availability | High — automated via Python registration scripts |

---

## Recognition & Impact

- 🎤 Presented the infrastructure architecture at **IEEE RTC Conference 2024, Chicago, Illinois**
- 🤝 Validated with and presented to **Mumbai Police** as a deployable emergency coordination solution
- 💡 Recognised by **investors and technical stakeholders** for social impact and real-world feasibility
- 🏙️ Piloted in **Mumbai**, tested across diverse urban environments

---

## Project Status

SavMed is a proprietary startup project. The source code is currently publicly available. This repository serves as a technical reference and portfolio showcase.

For collaboration, partnership, or licensing enquiries, please reach out directly.

---

## Authors

**Fatima Zariwala**
GitHub · [fatimazariwala](https://github.com/fatimazariwala)
Repository · [SavMedForBLE](https://github.com/fatimazariwala/SavMedForBLE)

**Aditya Gupta**
GitHub · [adigupta20368](https://github.com/adigupta20368)

---

<p align="center"><i>Built with urgency. Deployed for emergencies.</i></p>
