# Dynamic Pairing PIN Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fixed Bluetooth pairing PIN `1234` with a random 6-digit code generated per pairing attempt, shown on the OLED, and typed in the phone's system Bluetooth settings (pair once, then trusted forever).

**Architecture:** The ESP32 (Bluetooth endpoint) switches to variable-PIN mode: on each PIN request from a phone it rolls a code with `esp_random()`, replies to the phone via `esp_bt_gap_pin_reply`, and forwards the code to the Arduino Uno over the existing UART2→D2 wire as `PIN,<code>` (repeated every 500 ms while pending) and `PINOK` on auth success. The Uno shows the code as an overlay on the OLED for 60 s (sensing keeps running underneath) and flashes "Paired!" on `PINOK`.

**Tech Stack:** Arduino AVR (Uno), ESP32 Arduino core (`BluetoothSerial`, `esp_gap_bt_api.h`), g++ host tests, arduino-cli 1.5.1 (bundled with IDE 2.3.10).

**Spec:** Design approved in conversation (2026-10-31): dynamic per-attempt code, 6 digits, 60 s display window, new code per attempt (incl. wrong-code retry), "Paired!" flash, no app changes, no wiring changes. My calls: code generation on ESP32, `PIN,`/`PINOK` line protocol over UART2.

## Global Constraints

- Pairing happens in the phone's **system** Bluetooth settings; the Android app is unchanged.
- Wiring unchanged: ESP32 `GPIO17 (TX2)` → Uno `D2` (already in the build); common GND.
- Sensing pipeline (readings, buzzer, status lines to the app) is never paused by the pair-code overlay.
- Line protocol over UART2: ASCII, newline-terminated, `PIN,<6 digits>` and `PINOK`. Malformed lines are ignored by the Uno.
- Working tree stays uncommitted unless the user asks (their ruling from the v13 work).

## Review Focus

1. A pairing request arriving during the boot countdown must not lose the code (countdown loop must poll the relay line and show the code right after "Ready").
2. Wrong-code retry must produce a fresh code (PIN request event fires again; ESP32 regenerates).
3. `PIN,` lines arriving while the Uno is busy beeping/drawing must not be missed (ESP32 repeats every 500 ms; Uno buffers to newline).
4. The overlay must expire on its own (60 s) even if `PINOK` never arrives (pairing aborted).
5. `PINOK` must return the OLED to the readings screen early (3 s "Paired!" flash, then normal).

---

### Task 1: PairCodeOverlay module + host tests (Uno side)

**Files:**
- Create: `firmware/EMF_Wire_Fault_Detector_v14/paircode.h`
- Test: `tools/v14-host-tests/test_paircode.cpp`

**Interfaces:**
- Produces: `class PairCodeOverlay` with:
  - `bool feed(const char* line, unsigned long nowMs)` — true if the line was `PIN,` or `PINOK` (malformed lines return false and change nothing)
  - `bool pending(unsigned long nowMs) const` — true while a code is being shown
  - `bool confirmed(unsigned long nowMs) const` — true within the "Paired!" flash window
  - `const char* code() const` — the current 6-digit code
  - constants: `SHOW_MILLIS = 60000UL`, `CONFIRM_MILLIS = 3000UL`

- [ ] **Step 1: Write the failing test**

```cpp
// test_paircode.cpp — cases:
// 1. pin_line_starts_pending_overlay: feed("PIN,483920", 1000) → true; pending(1000); code()=="483920"; pending(61000) is false (expired at 60 s).
// 2. pinok_confirms: after feed("PIN,483920", 0), feed("PINOK", 2000) → true; confirmed(2000); !pending(2000); confirmed(5000) is false (3 s window); !pending(65000).
// 3. malformed_lines_ignored: feed("pin,123456", 0) → false; feed("PIN,12A456", 0) → false; feed("PIN,12345", 0) → false; feed("", 0) → false; pending(0) false.
// 4. new_pin_replaces_old: feed("PIN,111111", 0); feed("PIN,222222", 1000) → true; code()=="222222"; pending(1000).
// 5. pinok_without_pin_is_ignored: feed("PINOK", 0) → false; confirmed(0) false.
```

- [ ] **Step 2: Run test to verify it fails**

Run: `g++ -std=c++11 -Wall -Wextra test_paircode.cpp -o test_paircode.exe && ./test_paircode.exe`
Expected: FAIL (compile error: `paircode.h` not found).

- [ ] **Step 3: Implement `PairCodeOverlay` in `paircode.h`**

Approach: 7-byte code buffer, two timestamps, parse by prefix match (`PIN,` + exactly 6 digits then end-of-line; `PINOK` exact match). No Arduino dependencies.

- [ ] **Step 4: Run test to verify it passes**

Run: same command. Expected: `test_paircode: all tests passed`.

- [ ] **Step 5: No commit (user ruling: working tree stays uncommitted unless asked).**

### Task 2: ESP32 relay — variable PIN mode

**Files:**
- Modify: `backend/ESP32_BT_Relay_2/ESP32_BT_Relay_2.ino` (whole file, it is 45 lines)

**Interfaces:**
- Consumes: nothing (independent of Task 1).
- Produces (over UART2 at 9600 baud, and mirrored on USB serial): `PIN,<6 digits>` repeated every 500 ms while a pairing attempt is pending, `PINOK` once on auth success.

- [ ] **Step 1: No failing test possible host-side (SDK callbacks). Verification is compile + line-format check.**
- [ ] **Step 2: Implement in `ESP32_BT_Relay_2.ino`**

Exact changes: include `esp_system.h`; globals `bool pinPending=false; char currentPin[7]; uint8_t pinDigits[6]; unsigned long pinSentMs=0; const unsigned long PIN_RESEND_MS=500; const unsigned long PIN_TIMEOUT_MS=60000;`; `static void gapCallback(esp_bt_gap_cb_event_t event, esp_bt_gap_cb_param_t* param)` handling `ESP_BT_GAP_PIN_REQ_EVT` (generate `esp_random() % 1000000`, format `%06lu` into `currentPin`, copy ASCII digits into `pinDigits`, `esp_bt_gap_pin_reply(param->pin_req.bda, true, 6, pinDigits)`, send `PIN,` line on `ArduinoLink`, set `pinPending=true; pinSentMs=millis();`) and `ESP_BT_GAP_AUTH_CMPL_EVT` (`param->auth_cmpl.success` → send `PINOK`, `pinPending=false`); in `setup()` replace the fixed-pin block with `esp_bt_gap_register_callback(gapCallback); esp_bt_gap_set_pin(ESP_BT_PIN_TYPE_VARIABLE, 0, NULL);`; in `loop()` add the 500 ms resend + 60 s timeout of the `PIN,` line. Keep all existing relay behavior (forwarding, logging).

- [ ] **Step 3: Compile for ESP32**

Run: `arduino-cli compile --fqbn esp32:esp32:esp32 backend/ESP32_BT_Relay_2`
Expected: exit 0, no errors. (Upload only if an ESP32 port is present; otherwise the user flashes from the IDE.)

### Task 3: Uno firmware v14

**Files:**
- Create: `firmware/EMF_Wire_Fault_Detector_v14/EMF_Wire_Fault_Detector_v14.ino` (copy of v13 + changes)
- Create: `firmware/EMF_Wire_Fault_Detector_v14/paircode.h` (from Task 1)

**Interfaces:**
- Consumes: `PairCodeOverlay` (Task 1).
- Produces: OLED overlay screen "PAIR CODE / <code> / type on phone" (big text) and "Paired!" screen.

- [ ] **Step 1: Implement the v14 sketch changes**

Exact changes from v13: include `paircode.h`; global `PairCodeOverlay pairCode;` and a line buffer `char espLineBuf[24]; uint8_t espLineLen;`; helper `void pollPairLines()` (read all available `espSerial` bytes, append to `espLineBuf`, on `\n` call `pairCode.feed(espLineBuf, millis())`, reset buffer; cap at 23 chars) — called at the top of `loop()` and inside the `runCalibrationSequence` countdown loop; in `updateDisplay()`: if `pairCode.confirmed(millis())` → "Paired!" screen; else if `pairCode.pending(millis())` → pair-code screen (code at `setTextSize(3)`); else the normal readings screen. Header comment updated to v14.

- [ ] **Step 2: Compile for Uno**

Run: `arduino-cli compile --fqbn arduino:avr:uno firmware/EMF_Wire_Fault_Detector_v14`
Expected: exit 0, no warnings, RAM ≤ ~45%.

- [ ] **Step 3: No commit (user ruling).**

### Task 4: Docs

**Files:**
- Modify: `docs/api-specification.md` (Link B Authentication row + §Authentication section + a Link A note about the PIN line)
- Modify: `README.md` (backend pairing steps: code on OLED instead of 1234; repo layout: add v14 folder)
- Modify: `docs/validation-report.md` (new security test row: pair with displayed code / wrong code rejected / re-pair after "Forget")
- Create: `docs/adr/0007-dynamic-display-pin-pairing.md` + index row in `docs/adr/README.md`

- [ ] **Step 1: Write the doc changes** (values: PIN is dynamic 6-digit per attempt, fixed `1234` removed; `PIN,`/`PINOK` line protocol documented; pairing flow unchanged — system settings)
- [ ] **Step 2: Verify with grep**

Run: `grep -rn "1234" README.md docs/` → expected: no remaining reference that presents 1234 as the pairing PIN (historical mentions may say "was 1234").
Run: `grep -rn "v13" README.md docs/api-specification.md docs/validation-report.md` → expected: only version-history mentions.

### Task 5: Flash + verify + review

- [ ] **Step 1: Flash the Uno** — `arduino-cli upload -p COM5 --fqbn arduino:avr:uno firmware/EMF_Wire_Fault_Detector_v14` (close the IDE Serial Monitor first if the port is busy).
- [ ] **Step 2: Flash the ESP32** — only if an ESP32 port exists; otherwise hand the user the IDE steps.
- [ ] **Step 3: Serial sanity check** — read COM5 at 9600 for ~10 s: v14 debug lines present, no crash, pair overlay code dormant (no PIN yet).
- [ ] **Step 4: Whole-branch review** — fresh subagent reviews the diff (relay sketch, v14 sketch, paircode.h + tests, docs) against this plan and the review focus list.
