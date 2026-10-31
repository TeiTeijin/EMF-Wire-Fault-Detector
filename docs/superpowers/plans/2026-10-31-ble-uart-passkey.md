# BLE UART + Passkey Pairing (Type-in) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:test-driven-development for every step. Do NOT flash hardware, do NOT commit. Verify with host tests + compiles (arduino-cli + gradlew). If a toolchain is missing, REPORT it — do not install software.

**Goal:** Move the phone link from Bluetooth Classic SPP to **BLE UART (GATT) with MITM static-passkey pairing**, so pairing shows a **type-in dialog** on the phone and the 6-digit code exists **only on the OLED** — the Samsung-keyboard experience. The lock stays: no pairing window → the passkey is an unknown random secret → nobody can pair.

**Architecture:** The ESP32 becomes a BLE peripheral (Bluedroid BLE from the installed core, NO NimBLE dependency) advertising "EMF_Wire_Detector" with the Nordic UART Service, RX characteristic (phone→device, WRITE) and TX characteristic (device→phone, NOTIFY), both requiring encryption+MITM. IO capability DisplayOnly + static passkey. Locked: random secret passkey. Pairing window (`PAIRON` from the Uno, 60 s): fresh 6-digit passkey, `PIN,<code>` to the Uno (repeat 500 ms), `PINOFF` on close, `PINOK` on bond. The **Uno firmware is UNCHANGED** (it already sends `PAIRON` on button-hold 2 s / serial `p` and displays `PIN,`/`PINOK`/`PINOFF`). The **Android app** swaps its RFCOMM session for a BLE GATT session; the status-line protocol and all parsing/UI stay identical.

**Tech Stack:** ESP32 Arduino core 3.3.11 (BLE library in-core: `BLEDevice.h`, `esp_gap_ble_api.h` security params `ESP_BLE_SM_SET_STATIC_PASSKEY`, `ESP_BLE_SM_IOCAP_MODE`, `ESP_IO_CAP_OUT` — verified present in the installed SDK); Android (Kotlin, minSdk 24, target/compile 36, AGP 9.3.3); g++ host tests; bundled arduino-cli 1.5.1.

**Spec:** User decision 2026-10-31: Option B (type-in passkey). Rulings (do not change):
- Nordic UART Service UUIDs: service `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`, RX (write) `6E400002-...`, TX (notify) `6E400003-...`. Advertised name unchanged: `EMF_Wire_Detector`.
- Bluedroid BLE (core's `BLE` library), not NimBLE.
- MITM required, DisplayOnly, static passkey; both characteristics require encryption.
- Same passkey valid for the whole 60 s window (wrong-PIN retries reuse it); re-lock (new random secret) on bond success or expiry.
- App flow: scan for the BLE name → user opens the pairing window on the detector FIRST → app taps Connect → Android shows the type-in dialog → user types the OLED code → bond → notifications stream. App shows a hint on the connect screen: "Open the pairing window on the detector (hold button 2 s), then connect."
- Line protocol unchanged: `STATUS,signal,fieldPct,opampPct,jitterPct` every 500 ms; parser untouched.
- Failure mapping: GATT connect timeout (10 s) → `REFUSED`; no notification within 6 s → `SILENT`; bond failed/cancelled → NEW enum value `ConnectFailure.PAIRING_FAILED` with user-facing advice about the window/code; link loss → existing 5 s reconnect banner then back to the device list.
- API ≤ 30 requires `ACCESS_FINE_LOCATION` for BLE scanning (manifest + runtime request); API 31+ uses `BLUETOOTH_SCAN`. Keep existing `BLUETOOTH_CONNECT` handling.
- Working tree stays uncommitted.

## Global Constraints

- Uno firmware v14 untouched (verify it still compiles only).
- `EmfReadings`, `EmfSppContract` parsing, `ReadingHistory`, `EmfSessionViewModel` logic, all UI screens except the connection-layer swap and hint text, all existing unit tests: keep behavior.
- `DeviceSession` interface (data/DeviceSession.kt) is the seam: `events(): Flow<SessionEvent>`, `close()` — the new BLE session implements it.
- Pure logic must be host-tested; Android-hardware glue is verified by compile + gradle unit tests.

## Review Focus

1. GATT notification chunks arriving mid-line must reassemble correctly (pure LineBuffer, tested).
2. Bond failure must surface as `PAIRING_FAILED` with honest advice, not hang.
3. The locked state must make pairing impossible: passkey secret must NEVER be sent to the Uno.
4. The app must not request BLE scan on API 31+ without `BLUETOOTH_SCAN`, nor crash on API ≤ 30 without location grant handling.
5. The relay must keep forwarding/logging after the BLE swap, and `PAIRON` during an open window must not reset it.

---

### Task 1: LineBuffer (pure) + host tests

**Files:** Create `frontend/EMFReader/app/src/main/java/com/beep/emfreader/data/LineBuffer.kt`; test `frontend/EMFReader/app/src/test/java/com/beep/emfreader/LineBufferTest.kt`. (Host-test fallback: this is pure Kotlin — the gradle unit test runs it; ALSO provide a g++ mirror ONLY if gradle is unusable on this machine. First try gradle.)

**Interfaces (exact):**
```kotlin
class LineBuffer(private val maxLineLength: Int = 160) {
    fun feed(bytes: ByteArray, length: Int, onLine: (String) -> Unit)
    // splits on '\n' (tolerates trailing '\r'), drops overlong lines, emits complete lines only
}
```

- [ ] Step 1: Failing tests: single chunk one line; line split across 3 chunks; two lines in one chunk; CRLF tolerated; overlong line dropped without emitting; partial line not emitted until newline.
- [ ] Step 2: Run `.\gradlew.bat test --tests "*LineBufferTest*"` from `frontend/EMFReader` → FAIL (class missing). If gradle cannot run (missing JDK), STOP and report.
- [ ] Step 3: Implement `LineBuffer.kt`.
- [ ] Step 4: Re-run → PASS.

### Task 2: ESP32 BLE relay

**Files:** Rewrite `backend/ESP32_BT_Relay_2/ESP32_BT_Relay_2.ino` in place (BLE peripheral; keep the folder name). The Uno-facing protocol (UART2 9600, `PIN,`/`PINOK`/`PINOFF` out, `PAIRON` in) is unchanged.

- [ ] Step 1: Implement:
  - Includes: `BLEDevice.h`, `BLEServer.h`, `BLE2902.h`, `esp_gap_ble_api.h` (for security params; verify exact names/signatures against `C:\Users\Lily Ann\AppData\Local\Arduino15\packages\esp32\tools\esp32-libs\3.3.11\include\bt\host\bluedroid\api\include\api\esp_gap_ble_api.h`).
  - Setup: `BLEDevice::init("EMF_Wire_Detector")`; `esp_ble_gap_set_security_param(ESP_BLE_SM_IOCAP_MODE, &iocap=ESP_IO_CAP_OUT)`; `esp_ble_gap_set_security_param(ESP_BLE_SM_SET_STATIC_PASSKEY, &passkey)` (uint32); `BLEDevice::setEncryptionLevel(ESP_BLE_SEC_ENCRYPT_MITM)`; create server + service (NUS UUIDs) + RX (WRITE) + TX (NOTIFY, add `BLE2902` descriptor); start advertising.
  - Window state machine (port the classic version): `setPasskey(uint32)` helper writes the security param; boot → random secret passkey (print "Locked - pairing needs the pairing window..."); on `PAIRON` line from UART2 → if closed: fresh 6-digit code, `setPasskey(code)`, `PIN,` to Uno, 500 ms resend, 60 s expiry → `setPasskey(new random secret)` + `PINOFF`; on bond complete (BLE `onConnect` after encryption / `BLEDevice::setSecurityCallbacks` auth complete with success) → `PINOK` + close window; auth failure → keep window (same code).
  - Callbacks: `BLEServerCallbacks` (connected/disconnected prints), `BLECharacteristicCallbacks` on RX write → log phone→device commands (mirror classic behavior).
  - Forwarding: every line read from UART2 (the Uno's status lines) → `TX->setValue(line); TX->notify();` mirror on USB serial. Handle `BLECharacteristicCallbacks` only when a client is connected.
  - Verify against the installed BLE library header `BLEDevice.h` / `BLEServer.h` for exact method names (`setSecurityCallbacks`, `getAdvertising`, `startAdvertising`, etc.).
- [ ] Step 2: Compile `arduino-cli compile --fqbn esp32:esp32:esp32 backend\ESP32_BT_Relay_2` → exit 0. (arduino-cli: `C:\Users\Lily Ann\AppData\Local\Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe`)

### Task 3: Android app BLE layer

**Files:**
- Modify `frontend/EMFReader/app/src/main/AndroidManifest.xml` (add `BLUETOOTH_SCAN`, `ACCESS_FINE_LOCATION` maxSdkVersion 30, keep others).
- Create `frontend/EMFReader/app/src/main/java/com/beep/emfreader/data/BleDeviceSession.kt` (implements `DeviceSession`; uses `LineBuffer`; GATT connect 10 s timeout → REFUSED; first-line 6 s → SILENT; bond/cancel → PAIRING_FAILED; link lost → LinkLost).
- Modify `frontend/EMFReader/app/src/main/java/com/beep/emfreader/data/ConnectFailure.kt` (add `PAIRING_FAILED`).
- Modify `frontend/EMFReader/app/src/main/java/com/beep/emfreader/data/EmfSppContract.kt` (add BLE UUID constants; keep all parsing).
- Create `frontend/EMFReader/app/src/main/java/com/beep/emfreader/data/BleDeviceScanner.kt` (scan by name "EMF_Wire_Detector"; uses `BluetoothLeScanner`; needs Context + permissions).
- Modify `frontend/EMFReader/app/src/main/java/com/beep/emfreader/ui/EmfSessionViewModel.kt` ONLY to construct `BleDeviceSession(context, device)` instead of `SppDeviceSession` (keep everything else).
- Modify the connect/scan UI (`EmfScanScreen.kt` / `EmfGateScreen.kt` / whichever drives the device list) to scan for BLE devices and show the hint text: "Open the pairing window on the detector (hold button 2 s), then connect."
- Modify `frontend/EMFReader/app/src/main/java/com/beep/emfreader/ui/BluetoothPermissions.kt` for BLUETOOTH_SCAN + pre-31 location flow.
- Modify `frontend/EMFReader/app/src/main/java/com/beep/emfreader/ui/ConnectionFailedScreen.kt` for the PAIRING_FAILED message.
- Test: extend `EmfSppContractTest.kt` ONLY if you add parser-visible constants (otherwise leave); the LineBuffer test covers new pure logic. Keep all existing tests passing.

- [ ] Step 1: Implement the above (read the existing files first; match code style; keep `PairedDeviceLoader` if still referenced elsewhere — remove only if the UI no longer uses it).
- [ ] Step 2: `.\gradlew.bat test` from `frontend/EMFReader` → all tests pass (including the new LineBufferTest).
- [ ] Step 3: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL. If the JDK is missing, STOP and report exactly what's missing.

### Task 4: Docs

**Files:** `docs/api-specification.md` (replace the SPP/Bluetooth-Classic transport section with BLE GATT: NUS UUIDs, notify/write characteristics, MITM static-passkey auth, pairing window, permissions table with BLUETOOTH_SCAN + location note; keep the status-line protocol section; update compatibility matrix), `README.md` (architecture diagram + frontend/backend descriptions + pairing steps: open window first, then connect in app, type OLED code), `docs/validation-report.md` (scope + IT rows: locked → bond fails; window → type-in dialog accepts the OLED code; wrong code rejected, same-code retry; relock), create `docs/adr/0009-ble-uart-passkey-pairing.md` + `docs/adr/README.md` row (nine ADRs).

- [ ] Step 1: Write all doc changes.
- [ ] Step 2: `grep -rn "SPP\|RFCOMM\|Bluetooth Classic" README.md docs/` → only historical mentions (ADRs, compatibility matrix rows) remain; no ACTIVE instruction still says classic.

### Task 5: Final verification + report

- [ ] Step 1: All gradle tests + assembleDebug; ESP32 compile; Uno compile (unchanged, confirm); g++ suites still green.
- [ ] Step 2: Self-review against the Review Focus list (no subagent available).
- [ ] Step 3: Report: files changed, exact verification output lines, and unverifiable-on-host items (Android bond dialog behavior, BLE connection timing).
