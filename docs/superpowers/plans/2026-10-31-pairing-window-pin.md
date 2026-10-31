# Pairing Window + Type-in PIN Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:test-driven-development for every step below. Do not flash hardware, do not commit. Verify with host tests + arduino-cli compile only.

**Goal:** Replace the always-open SSP "confirm" pairing with a locked-by-default device: a 60-second pairing window (opened by holding the detector's button 2 s, or serial command `p`) shows a fresh 6-digit code on the OLED that the phone prompts the user to TYPE (legacy PIN dialog), after which the device re-locks with an unknown random PIN. Bonded phones keep working without a window.

**Architecture:** The ESP32 relay runs with `esp_bt_gap_set_pin(ESP_BT_PIN_TYPE_FIXED, 6, pin)` so Android shows the legacy type-in dialog. Normally the pin is a random secret nobody knows. When the Uno sends `PAIRON` over the existing UART link (Uno D3 → ESP32 GPIO16), the relay swaps in a fresh random 6-digit pin, sends `PIN,<code>` to the Uno (repeat 500 ms) for the OLED, and after 60 s (or successful auth) swaps back to a new random secret and sends `PINOFF`. On auth success it sends `PINOK`.

**Tech Stack:** Arduino AVR (Uno, SoftwareSerial), ESP32 Arduino core 3.3.11 (`esp_gap_bt_api.h`), g++ host tests (MSYS2), bundled arduino-cli 1.5.1 for compile checks.

**Spec:** Approved in conversation 2026-10-31. Key decisions (treat as rulings, do not change):
- Short press of the button = rest-baseline recalibration (unchanged). Hold ≥ 2 s = open pairing window (fires once at the 2 s mark). Serial `p` also opens the window.
- Window length 60 s; the window code is valid for the WHOLE window (wrong-PIN retries reuse the same code); relock with a fresh random secret on success or expiry.
- 6-digit codes only (legacy fixed pin).
- New UART lines: `PAIRON` (Uno→ESP32, sent 3× over ~300 ms for robustness), `PINOFF` (ESP32→Uno, window closed). Existing `PIN,<code>` (repeat 500 ms) and `PINOK` unchanged. CRLF tolerance must remain.
- FIXED pin mode; REMOVE the `ESP_BT_SP_IOCAP_MODE` / `ESP_BT_IO_CAP_OUT` lines added earlier (they caused the SSP numeric-comparison path).
- Pairing-window state is re-armed on reboot (locked).
- Working tree stays uncommitted. Do NOT upload/flash anything.

## Global Constraints

- Protocol to the phone app (STATUS lines) unchanged; app untouched.
- Wiring unchanged (Uno D3→ESP32 GPIO16 TX path already exists).
- Sensing pipeline never pauses while the overlay shows.
- All new pure logic must be host-tested (g++), all changes must compile clean on both targets.

## Review Focus

1. Button hold must NOT also trigger a recalibrate (one action per press).
2. A `PAIRON` arriving during an open window must not reset the window.
3. The relay must re-lock even if auth never completes (expiry path).
4. `PINOFF` must clear the OLED overlay without a "Paired!" flash.
5. Wrong-PIN retries during the window must succeed with the same code.

---

### Task 1: PressHold classifier + host tests (Uno side)

**Files:** Create `firmware/EMF_Wire_Fault_Detector_v14/presshold.h`; test `tools/v14-host-tests/test_presshold.cpp`

**Interfaces (exact):**
```cpp
class PressHold {
public:
  static const unsigned long HOLD_MILLIS = 2000UL;
  enum Event { NONE = 0, SHORT_PRESS = 1, LONG_HOLD = 2 };
  void update(bool pressed, unsigned long nowMs);  // feed debounced button state
  Event takeEvent();                                // returns pending event once, then NONE
};
```

- [ ] Step 1: Write failing tests: short press (<2 s, release) → exactly one SHORT_PRESS; hold ≥2 s → one LONG_HOLD at the 2 s mark and NO SHORT_PRESS on release; press-release without reaching 2 s multiple times → SHORT_PRESS each time; no event while idle; no event on initial not-pressed.
- [ ] Step 2: Run `g++ -std=c++11 -Wall -Wextra test_presshold.cpp -o test_presshold.exe && ./test_presshold.exe` → FAIL (header missing).
- [ ] Step 3: Implement `presshold.h` (pure logic, no Arduino deps; timestamps + edge/hold state machine).
- [ ] Step 4: Re-run → PASS.

### Task 2: PairCodeOverlay PINOFF support + host tests

**Files:** Modify `firmware/EMF_Wire_Fault_Detector_v14/paircode.h`; extend `tools/v14-host-tests/test_paircode.cpp`

- [ ] Step 1: Add failing tests: `PINOFF` after `PIN,` clears pending (no confirm, `pending()` false, `confirmed()` false); `PINOFF` with no active code is not consumed (returns false); `PINOFF\r` accepted (CRLF).
- [ ] Step 2: Run paircode tests → FAIL on the new cases.
- [ ] Step 3: Implement: exact-match `PINOFF` (optional trailing `\r`), sets `pending_=false`, `confirmed_=false`, returns true only when a code was pending.
- [ ] Step 4: Run all paircode tests → PASS.

### Task 3: ESP32 relay pairing window

**Files:** Modify `backend/ESP32_BT_Relay_2/ESP32_BT_Relay_2.ino`

- [ ] Step 1: Rewrite security state machine:
  - Globals: `char windowPin[7]; uint8_t pinDigits[6]; bool windowOpen; unsigned long windowStartMs; unsigned long pinSentMs;` (remove `min_16_digit`-era PIN_REQ/KEY_REQ/KEY_NOTIF/CFM_REQ handlers and `currentPin` legacy paths).
  - `setFixedPin(const char* p)`: fills `pinDigits` and calls `esp_bt_gap_set_pin(ESP_BT_PIN_TYPE_FIXED, 6, pinDigits)`.
  - Boot/`setup()`: remove the `ESP_BT_SP_IOCAP_MODE` lines; generate a random secret pin, `setFixedPin(secret)`, window closed. Print "Locked - pairing needs the detector's pairing window (hold button 2 s or send PAIRON)."
  - `openPairingWindow()`: if already open, ignore. Generate fresh 6-digit code into `windowPin`, `setFixedPin(windowPin)`, `windowOpen=true`, `windowStartMs=millis()`, send `PIN,` line, print "Pairing window open: <code> (60 s)".
  - `closePairingWindow()`: generate a NEW random secret pin, `setFixedPin(secret)`, `windowOpen=false`, send `PINOFF`, print "Pairing window closed - device locked".
  - `loop()`: parse inbound UART2 lines (add a small line buffer; currently the relay only sends). On `PAIRON` → `openPairingWindow()`. While `windowOpen`: resend `PIN,` every 500 ms; at 60 s → `closePairingWindow()`.
  - `gapCallback`: keep ONLY `ESP_BT_GAP_AUTH_CMPL_EVT`; on success → send `PINOK`, print, `closePairingWindow()`; on failure → print "Pairing attempt failed" and KEEP the window (same code valid until expiry).
- [ ] Step 2: Compile: `arduino-cli compile --fqbn esp32:esp32:esp32 backend/ESP32_BT_Relay_2` → exit 0, no errors. (arduino-cli path: `C:\Users\Lily Ann\AppData\Local\Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe`)

### Task 4: Uno v14 integration

**Files:** Modify `firmware/EMF_Wire_Fault_Detector_v14/EMF_Wire_Fault_Detector_v14.ino`

- [ ] Step 1: Wire the pieces:
  - Include `presshold.h`; global `PressHold pressHold;` and a small TX helper `void sendPairOn()` that writes `"PAIRON\n"` to `espSerial` 3 times with ~150 ms between (blocking OK).
  - In `loop()`, feed `pressHold.update(stableButtonState == LOW, millis())` and handle `takeEvent()`: `SHORT_PRESS` → recalibrate (existing path, replaces the current press-edge check); `LONG_HOLD` → `sendPairOn()` + Serial print "Pairing window requested". Keep the `c` command = recalibrate; add `p` command = `sendPairOn()`.
  - Remove the old `checkButtonPressed()` edge trigger from the recalibrate path (the PressHold SHORT_PRESS replaces it; the debounce itself stays).
  - `pollPairLines()` unchanged (already strips `\r`, feeds `pairCode`).
  - OLED: while `pairCode.pending()` show the code (existing); add a `"Window: <s>s"` hint by computing `60 - elapsed` — simplest: the ESP32 does not send the countdown, so show static `"Type on phone"` (existing) — do NOT add countdown logic.
- [ ] Step 2: Compile: `arduino-cli compile --fqbn arduino:avr:uno firmware/EMF_Wire_Fault_Detector_v14` → exit 0; RAM must stay ≤ 1500 B (U8g2 page mode is in place).

### Task 5: Docs

**Files:** Modify `docs/api-specification.md` (Authentication section: pairing window, type-in, lock default; Link A note: add `PAIRON`/`PINOFF` lines), `README.md` (pairing steps: hold button 2 s, type OLED code; serial `p`), `docs/validation-report.md` (IT-3 row update: wrong code rejected, same-code retry, relock after window), create `docs/adr/0008-pairing-window-type-in-pin.md` + index row in `docs/adr/README.md` (eight ADRs now).

- [ ] Step 1: Write all doc changes (values per this plan; keep the "confirm vs type-in" explanation in ADR-0008).
- [ ] Step 2: `grep -rn "1234\|numeric comparison\|Just Works" README.md docs/` → no stale active claims remain (historical mentions may say "was").

### Task 6: Final verification + report

- [ ] Step 1: Run ALL host tests (v13 + v14 suites) — every suite green.
- [ ] Step 2: Compile BOTH sketches — both exit 0.
- [ ] Step 3: Read your own diff critically (review focus list above; self-review, no subagent).
- [ ] Step 4: Report: summary of changes, test/compile evidence, and the ONE runtime assumption that could not be verified without hardware: that FIXED-pin legacy pairing makes Android show the type-in dialog (if it shows numeric comparison instead, report it — do not change the design).
