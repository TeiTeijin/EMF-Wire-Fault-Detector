# Validation Report — EMF Wire Fault Detector

**Scope:** firmware `EMF_Wire_Fault_Detector_v11`, relay `ESP32_BT_Relay_2`,
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
| Firmware | `EMF_Wire_Fault_Detector_v11.ino` |
| Relay | `ESP32_BT_Relay_2.ino` (advertises `EMF_Wire_Detector`, PIN 1234) |
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
| IT-1 | Android version coverage | Install and run on Android 7 (API 24) and a modern version (API 34+); connect to the relay on each | Scan list shows the paired relay; live screen works identically | | |
| IT-2 | Pairing flow | Pair from system Bluetooth settings: device shows as `EMF_Wire_Detector`, PIN 1234 accepted; then open the app | Device appears in the app's list without scanning | | |
| IT-3 | Legacy sketch formats | Flash detector v3 and v8 sketches; connect | App shows live readings for both (verdict recomputed) | | |
| IT-4 | Third-party client | Connect with **Serial Bluetooth Terminal** and read lines | Same status lines visible verbatim (relay is not app-specific) | | |
| IT-5 | Toolchain | Build/upload firmware and relay from the Arduino IDE versions in use | Both build and upload cleanly from the repository sources | | |

## 5. Reliability tests

| ID | Test | Procedure | Expected result | Measured result | Verdict |
|---|---|---|---|---|---|
| RT-1 | Soak | 30-minute continuous run; count relay log lines vs app readings | No crash; dropped lines are rare; cadence stable (PT-1 re-check) | | |
| RT-2 | Link loss & recovery | Walk out of range until the link drops | Banner "Link lost. Reconnecting in Ns." counts 5→1, then the device list; reconnect works | | |
| RT-3 | Relay power cycle | Power-cycle the ESP32 while connected | App reports link loss, then recovers after retry | | |
| RT-4 | Arduino reset mid-session | Press the Uno's reset while connected | Relay link stays up; known behaviour: the app keeps the last reading (no mid-stream silence timeout) — record how long until noticed | | |
| RT-5 | Magnetometer fault recovery | Induce an I2C glitch/brownout on the QMC5883P | Serial shows "QMC5883P stopped updating - restarting it"; field channel recovers within a few seconds | | |
| RT-6 | Calibration persistence | Power-cycle 5×; then boot with the button held | Normal boots do **not** re-prompt; button-held boot re-runs turn calibration | | |
| RT-7 | Recalibration in use | Press the button mid-run | 3 s countdown on the OLED, then normal operation resumes | | |

## 6. Functional / fault-injection tests (low voltage only)

| ID | Test | Procedure | Expected result | Measured result | Verdict |
|---|---|---|---|---|---|
| VF-1 | DC current detection | Battery + resistor through a wire loop; hold sensor near the loop | Status becomes `WIRE` within a few seconds; field% rises with proximity | | |
| VF-2 | Source removed | Switch off the current | Returns to `CLEAR` as the zero point refollows (within ~15 s scale) | | |
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
   RFCOMM socket stays open, the app keeps the last reading (RT-4).
2. **Zero-point re-follow.** Holding the device still near a wire gradually
   absorbs a steady reading (τ 15 s while clear) — slow approach is
   intentionally less sensitive than a fast change.
3. **Fixed PIN is the authentication.** See `docs/api-specification.md`
   §Authentication for the trust model.
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
