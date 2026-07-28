# How-To: Floor Mapping & Indoor Positioning with c3nav Android APK

This guide explains how to use the `c3nav-android` app in combination with a `c3nav` backend server to collect Wi-Fi and Bluetooth (BLE) signal data for mapping indoor floor positioning.

---

## 1. Overview & Architecture

Indoor positioning in `c3nav` matches scanned Wi-Fi Access Point BSSIDs and Bluetooth iBeacons against reference measurements to calculate real-time user coordinates on a map.

```
 +------------------------------------------------------------------------+
 |                            Android App                                 |
 |                                                                        |
 |  Native Wi-Fi & BLE Scanners <---> Javascript Bridge ("mobileclient")  |
 +------------------------------------+-----------------------------------+
                                      |
                                      | Web Editor Interface
                                      v
 +------------------------------------------------------------------------+
 |                           c3nav Backend                                |
 |                                                                        |
 |  BeaconMeasurement Objects ---> RangingBeacon BSSIDs ---> Locator Engine |
 +------------------------------------------------------------------------+
```

### A. Android App Native Bridge (`mobileclient`)
The app exposes a native Java object `mobileclient` to the embedded WebView:
* **Scanning Control**: When starting measurement mode, the web interface calls `mobileclient.wificollectorStart()`, which flags `FLAG_KEEP_SCREEN_ON` to keep the screen awake during data collection, and `mobileclient.wificollectorStop()` when finished.
* **Periodic Scans**: Web JS calls `mobileclient.scanNow()` and ranging methods (`startWifiRanging()`).
* **iBeacon Ranging**: Web JS registers/unregisters iBeacon UUIDs (e.g., default `a142621a-2f42-09b3-245b-e1ac6356e9b0`) using `mobileclient.registerBeaconUuid(uuid)` and `mobileclient.unregisterBeaconUuid(uuid)`.
* **Callbacks & Data Transfer**: When native scans complete, the app evaluates JavaScript callbacks `nearby_stations_available()` (Wi-Fi) and `ibeacon_results_available()` (Bluetooth). Web JS then retrieves scan arrays via `mobileclient.getNearbyStations()` and `mobileclient.getNearbyBeacons()`.

### B. Backend Location Processing
* Scans are stored inside **Beacon Measurement** (`BeaconMeasurement`) objects attached to map spaces/levels.
* Upon saving, `c3nav` automatically extracts scanned BSSIDs and maps them to `RangingBeacon` objects (`EVENT_WIFI`).
* The server's `Locator` engine dynamically calculates real-time indoor user position using these beacon mappings.

---

## 2. Prerequisites & Device Setup

### A. Android Device Configuration

1. **Required App Permissions**:
   * **Location**: `ACCESS_FINE_LOCATION` (Required for Wi-Fi AP scanning).
   * **Bluetooth Scan**: `BLUETOOTH_SCAN` (Required on Android 12+ / API 31+ for iBeacon detection).
2. **System Settings**:
   * **Location Services (GPS)**: Must be turned **ON** in Android settings (Android OS enforces Location Services to allow Wi-Fi BSSID scanning).
   * **Wi-Fi**: Turned **ON** (Does not require connection to an AP, but scanning must be enabled).
   * **Bluetooth**: Turned **ON** for iBeacon detection.
3. **Disable Wi-Fi Scan Throttling (Crucial for Floor Mapping!)**:
   * Android limits background/foreground Wi-Fi scans to 4 per 2 minutes by default.
   * Go to **Settings** > **Developer Options** > enable **Developer Options**.
   * Turn **OFF** **Wi-Fi scan throttling**. This permits continuous real-time Wi-Fi scanning during mapping.

### B. Server Setup
1. Deploy your `c3nav` backend server instance (e.g., `https://c3nav.example.com`).
2. Upload vector floor plans (SVG/PNG), scale/georeference them, and draw spaces, rooms, and corridors in the Map Editor.

---

## 3. Step-by-Step Floor Mapping Workflow

### Step 1: Open the App & Log In
1. Launch the compiled `c3nav` Android app configured for your `c3nav` server instance.
2. Log in with an account having **Editor** permissions.

### Step 2: Open Map Editor & Add a Beacon Measurement
1. Open the **Map Editor** from the top menu/sidebar.
2. Select the floor/level and space you are mapping.
3. Add a new **Beacon Measurement** object (or select an existing one).
4. Place its point marker precisely on your current physical coordinate on the floor map.

### Step 3: Record Signal Data with Scan Collector
1. In the **Beacon Measurement** form sidebar, locate the **Scan Collector** widget.
2. Tap **Start**:
   * The app activates measurement mode (`wificollectorStart`), keeping the screen awake and initiating Wi-Fi/iBeacon scans.
   * Detected Wi-Fi APs and Bluetooth beacons populate the live list, and counts (`wifi-count`, `ibeacon-count`) increment in real-time.
3. Stand still at the physical point for 5–10 seconds to allow multiple scan frames to accumulate.
4. Tap **Stop**:
   * Halts measurement mode (`wificollectorStop`) and serializes the recorded signal data into the measurement form payload.
5. Tap **Save** to store the `BeaconMeasurement` object.

### Step 4: Repeat Grid Points & Verify Positioning
1. Move to adjacent reference points across the floor (aim for key locations every few meters in corridors and rooms) and repeat creating `BeaconMeasurement` records.
2. Saving automatically maps scanned BSSIDs to `RangingBeacon` entries on the server.
3. Exit the editor, return to the main map view, and tap the **Location** button to test live indoor positioning accuracy.

---

## 4. Troubleshooting & Technical Notes

| Issue / Scenario | Cause | Solution |
| :--- | :--- | :--- |
| **No Wi-Fi APs found during scan** | Location services or permissions disabled | Enable System Location (GPS) and Wi-Fi. Ensure `ACCESS_FINE_LOCATION` and `BLUETOOTH_SCAN` permissions are granted to the app. |
| **Scans update very slowly (every 30+ s)** | Android Wi-Fi scan throttling active | Disable **Wi-Fi scan throttling** in Android Developer Options (**Settings > Developer Options**). |
| **Screen turns off during mapping** | Collector not started | Tap **Start** in the Scan Collector widget; `mobileclient.wificollectorStart()` sets `FLAG_KEEP_SCREEN_ON` while scanning. Do not manually lock the screen. |
| **Wi-Fi RTT Support** | Device & AP support 802.11mc FTM | `c3nav` supports Wi-Fi RTT Fine Timing Measurement ranging (`startWifiRanging()`), providing distance estimation where RTT-capable hardware is available. |
| **Connection Errors** | App domain or SSL mismatch | Ensure `WEB_URL` in [app/build.gradle.kts](file:///home/idt/workspace/nav/c3nav-android/CongressRoutePlanner/app/build.gradle.kts#L21) matches your server URL and check [network_security_config.xml](file:///home/idt/workspace/nav/c3nav-android/CongressRoutePlanner/app/src/main/res/xml/network_security_config.xml#L4). |
