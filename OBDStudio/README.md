# OBD Studio — Bluetooth-first Android Diagnostics (v0.1)

A native Kotlin / Jetpack Compose diagnostic application. **No account, subscription, advertisements, INTERNET permission, location permission, or cloud backend.** All current scan history is stored on-device using SQLite.

## What actually works in this source release

- Lists **already-paired** Bluetooth Classic devices using Android's Nearby Devices permission.
- Connects to ELM327-compatible **Bluetooth Classic SPP** hardware over RFCOMM (UUID `00001101-0000-1000-8000-00805F9B34FB`). BLE-only dongles are not supported yet.
- Initializes common ELM327 commands, detects an OBD engine ECU by its reply to `0100`, reads negotiated protocol and the adapter's reported supply voltage.
- Discovers supported Mode 01 PID sets at `00`, `20`, `40`; polls real-time RPM, speed, coolant temperature, voltage and additional supported data.
- Reads generic **emissions / powertrain** stored (`03`), pending (`07`), and permanent (`0A`) trouble codes; provides an initial small built-in explanation dictionary.
- Clears DTCs **only after explicit confirmation**, using service `04`; clears readiness and freeze-frame data too.
- Stores completed scan history in local SQLite.
- Provides a polished light UI with dashboard, trend chart, systems, DTCs and adapter screens.
- Includes a module **catalog** (engine, transmission, ABS, airbag, body, steering, hybrid, etc.).

## Intentionally not claimed as working yet

- Manufacturer-specific scans (TCM, ABS, SRS, BCM, immobilizer, etc.) **are not implemented** in v0.1. They are present in the catalog but correctly labeled `PROFILE NEEDED`. We do not report unsupported systems as healthy.
- Wi-Fi, Bluetooth LE, advanced J2534/DoIP interfaces, manufacturer-specific coding and active tests.
- Multi-ECU / ISO-TP multi-frame assembly is incomplete for extended diagnostic messages. Mode 01 simple PID parsing and common simple DTC responses are included.
- VIN identification, UDS / KWP / J1939 diagnostics, advanced manufacturer trouble-code dictionaries.
- Hardware/vehicle validation, instrumentation test suite and signed release APK.

## Build the Android APK

Use Android Studio with **JDK 17+**, **Android SDK API 36**, **Gradle 8.13** and an internet connection at build time to download Android dependencies. Import this folder as a Gradle project. The current archive includes Gradle build scripts and wrapper version metadata but **not** the Gradle wrapper JAR. Install/select Gradle 8.13 in your environment, or generate the wrapper by running `gradle wrapper --gradle-version 8.13`.

From the project folder:

```bash
gradle :app:assembleDebug
```

The unsigned-for-distribution debug APK will appear at `app/build/outputs/apk/debug/app-debug.apk`. Android Studio: **Build → Build APK(s)**.

The folder `.github/workflows/android-debug.yml` includes a reproducible build on GitHub Actions and uploads the debug APK artifact when this project is at the **repository root**. This temporary feature branch also includes a root-level GitHub workflow to build from its `OBDStudio/` subdirectory.

## Pair and connect

1. Plug the ELM327 adapter into the vehicle's OBD-II socket. Turn ignition to ON (or start engine if safe and required).
2. In phone Settings → Bluetooth, pair the adapter (PIN is vendor-dependent, often `1234` or `0000`).
3. Open OBD Studio → Adapter → Allow Nearby Devices → select the adapter → Connect.
4. On Overview, check live data; on Faults, scan stored/pending/permanent generic trouble codes.
5. Keep the vehicle stationary while handling your phone. Do not clear codes before saving the scan and understanding readiness consequences.

## Code layout

```
app/src/main/java/com/obdstudio/app/
├── MainActivity.kt
├── bluetooth/DiagnosticTransport.kt      # future interfaces plug in here
├── bluetooth/BluetoothSppTransport.kt     # real RFCOMM transport
├── core/ModuleCatalog.kt                  # catalog, not capability claim
├── core/ObdCodec.kt                       # PID/DTC decoding
├── core/DiagnosticController.kt           # state, workflow and polling
├── storage/ScanHistoryStore.kt            # local SQLite
└── ui/StudioApp.kt                         # Compose interface
```

## Technical notes

The app requests only BLUETOOTH_CONNECT on Android 12+. The ELM327 protocol and an adapter's advertised features **do not guarantee access** to proprietary modules. Future enhanced diagnostics need verified vehicle profile definitions (CAN headers, arbitration identifiers, UDS/KWP services, response framing, security limitations) and an adapter that supports them. The application does not attempt unsupported security bypasses or write operations to safety-critical systems.

**Current verification:** Pure Kotlin protocol/parser files can be compiled separately with a local Kotlin compiler. The whole Android application has not yet been built or tested on a physical adapter in this workspace, which does not have Android SDK or Gradle installed.
