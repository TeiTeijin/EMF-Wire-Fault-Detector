# Validation Report — EMF Wire Fault Detector

**Scope:** firmware `EMF_Wire_Fault_Detector_v14`, relay `ESP32_BT_Relay_2`,
Android app `EMFReader`. **Status:** test plan complete — measured results to
be filled in during validation runs.

**Rules for this report**

1. Record the exact environment first (Section 2). A result without a
   recorded environment is not evidence.
2. Fill every *Measured result* cell; mark the *Verdict* `Pass` / `Fail` /
   `Blocked`. Keep raw evidence (serial logs, phone screen recordings,
   photos) in the `evidence/` folder of your submission.
3. **Safety first:** all fault-injection tests use low-voltage sources only.
   Never contact live mains wiring with the device, the antenna, or your
   hands — sense through insulation from a safe distance at most.

---

## 1. System under test

| Component | Version / identity |
|---|---|
| Firmware | `EMF_Wire_Fault_Detector_v14.ino` |
| Relay | `ESP32_BT_Relay_2.ino` (BLE peripheral advertising `EMF_Wire_Detector` with the Nordic UART Service; locked by default — static passkey is a random secret, 60 s pairing window shows a 6-digit code on the OLED that the phone user types) |
| App | `EMFReader` (commit on `main` at time of test) |
| Reporting cadence | 1 status line / 500 ms · trace window 15 s (30 points) |

## 2. Test environment (fill in)

| Item | Value |
|---|---|
| Phone model / Android version (API level) | |
| ESP32 board / core version | |
| Arduino board | |
| Power sources (phone battery, power bank for ESP32, USB for Uno) | |
| Bench conditions (indoor, known wiring nearby) | |
| Tester / date | |

## 3. Performance tests

| ID | Test | Procedure | Expected result | Measured result | Verdict |
|---|---|---|---|---|---|
| PT-1 | Report cadence | Capture 60 consecutive timestamped lines (relay USB log or a terminal app with timestamps); measure mean interval | 500 ms ± 100 ms (~2.0 lines/s) | | |
| PT-2 | Detection latency | With the app live, bring a low-voltage current loop up to the sensor; time from "wire present" to status change on the phone | ≤ 2 s (report interval + smoothing window) | | |
| PT-3 | Connect time | From tapping **Connect** to the live screen showing data | ≤ 5 s typical; hard bound 10 s, then `REFUSED` | | |
| PT-4 | First-line timeout | Relay powered, Arduino unpowered. Connect and wait | Distinct **silent** message after ≈ 6 s ("but it sent no readings") | | |
| PT-5 | Alert cadence | Trigger `WIRE` and `FAULT`; observe OLED + buzzer | Wire: 100 ms beep / 800 ms gap. Fault: 60 ms beep / 150 ms gap (audibly faster) | | |

## 4. Interoperability tests

| ID | Test | Procedure | Expected result | Measured result | Verdict |
|---|---|---|---|---|---|
| IT-1 | Android version coverage | Install and run on Android 7 (API 24) and a modern version (API 34+); connect to the relay on each | Scan list shows the relay; live screen works identically | | |
| IT-2 | Pairing flow (locked vs window) | While locked, tap the sensor in the app and Connect; then open the pairing window (hold the button 2 s or send `p`) and connect again | Locked: the phone shows a passkey dialog but the code cannot succeed — pairing fails and the app shows the pairing-failed advice. Window open: the OLED shows a 6-digit code; the phone shows a type-in dialog; typing the OLED code completes pairing and the live screen appears | | |
| IT-3 | Wrong code + same-code retry | With the window open, type a wrong code; then retry with the correct OLED code inside the same window | Wrong code: pairing fails, app shows pairing-failed advice, window stays open. Retry with the same OLED code succeeds (same code valid for the whole window) | | |
| IT-4 | Pairing window + re-lock | Pair a phone inside a window; afterwards try pairing a second phone without a window; open a second window, let it lapse without pairing, and try again | Bonded phone reconnects silently without a window. While locked a second phone cannot pair (unknown secret). After a lapsed window the device re-locks: the old code no longer works, new attempts fail until a fresh window | | |
| IT-5 | Legacy sketch formats | Flash detector v3 and v8 sketches; connect | App shows live readings for both (verdict recomputed) | | |
| IT-6 | Third-party client | Connect with a BLE UART app (e.g. nRF Connect / Serial Bluetooth Terminal BLE mode) and subscribe to the TX characteristic | Same status lines visible verbatim (relay is not app-specific) | | |
| IT-7 | Toolchain | Build/upload firmware and relay from the Arduino IDE versions in use | Both build and upload cleanly from the repository sources | | |

## 5. Reliability tests

| ID | Test | Procedure | Expected result | Measured result | Verdict |
|---|---|---|---|---|---|
| RT-1 | Soak | 30-minute continuous run; count relay log lines vs app readings | No crash; dropped lines are rare; cadence stable (PT-1 re-check) | | |
| RT-2 | Link loss & recovery | Walk out of range until the link drops | Banner "Link lost. Reconnecting in Ns." counts 5→1, then the device list; reconnect works | | |
| RT-3 | Relay power cycle | Power-cycle the ESP32 while connected | App reports link loss, then recovers after retry | | |
| RT-4 | Arduino reset mid-session | Press the Uno's reset while connected | Relay link stays up; known behaviour: the app keeps the last reading (no mid-stream silence timeout) — record how long until noticed | | |
| RT-5 | Magnetometer fault recovery | Induce an I2C glitch/brownout on the QMC5883P | Serial shows "QMC5883P stopped updating - restarting it"; field channel recovers within a few seconds | | |
| RT-6 | Calibration persistence | Power-cycle 5× with saved offsets | Normal boots reload the hard-iron offsets (no *Calibrating...* hint); auto-learn resumes only when nothing was saved yet | | |
| RT-7 | Recalibration in use | Press the button mid-run | 3 s countdown on the OLED, then normal operation resumes | | |
| RT-8 | Auto hard-iron learning | With no saved offsets, move the device through all orientations over a few minutes | Serial shows `Auto-calibrated: offsets XYZ=...` once every axis swept ≥ 600 counts; OLED *Calibrating...* disappears | | |

## 6. Functional / fault-injection tests (low voltage only)

| ID | Test | Procedure | Expected result | Measured result | Verdict |
|---|---|---|---|---|---|
| VF-1 | DC current detection | Battery + resistor through a wire loop; hold sensor near the loop | Status becomes `WIRE` within a few seconds; field% rises with proximity | | |
| VF-2 | Source removed | Switch off the current | Returns to `CLEAR` as the zero point refollows (within ~5 s scale) | | |
| VF-3 | Intermittent contact (fault proxy) | Jiggle a frayed low-voltage connection carrying current | Status becomes `FAULT` (jitter ≥ 50%) | | |
| VF-4 | Distance response | Wire at 2 / 5 / 10 cm; record field% at each | field% decreases monotonically with distance | | |
| VF-5 | Antenna path response | Bring a low-voltage AC adapter's cable (no contact) near the antenna; watch `p2p` on the serial monitor | `p2p`/op-amp% rise while AC is present; falls when removed | | |

Record VF-4 data here:

| Distance | field% (run 1) | field% (run 2) | field% (run 3) |
|---|---|---|---|
| 2 cm | | | |
| 5 cm | | | |
| 10 cm | | | |

## 7. Known behaviours and limitations

Documented on purpose, so they are not mistaken for defects during testing:

1. **Mid-stream silence is not detected.** The 6 s first-line timeout applies
   before the first line ever arrives. If the Arduino later resets while the
   BLE link stays up, the app keeps the last reading (RT-4).
2. **Zero-point re-follow.** Holding the device still near a wire gradually
   absorbs a steady reading (τ 5 s while clear, 60 s while wire) — slow
   approach is intentionally less sensitive than a fast change.
3. **Pairing-window code is the authentication.** The device is locked by
   default: the static passkey is a random 6-digit secret nobody knows,
   so pairing cannot succeed outside a window. A 60 s window (button hold
   2 s or serial `p`) shows a fresh 6-digit code on the OLED (plain
   sight, one-time per phone) and the phone prompts the user to TYPE that
   code. Wrong or cancelled attempts retry with the same code inside the
   window; the device re-locks after success or expiry. Bonded phones
   reconnect silently. See `docs/api-specification.md` §Authentication
   for the trust model.
4. **Calibration is posture-dependent.** Calibrate while holding the device the
   way it will be used; recalibrate after moving to a very different setting.

## 8. Results summary (fill in)

| Category | Passed | Failed | Blocked |
|---|---|---|---|
| Performance (PT-1…5) | | | |
| Interoperability (IT-1…5) | | | |
| Reliability (RT-1…7) | | | |
| Functional (VF-1…5) | | | |

**Conclusion / remarks:**

&nbsp;

---

**Tested by:** ________________  **Date:** ____________

**Reviewed by:** ________________  **Date:** ____________
