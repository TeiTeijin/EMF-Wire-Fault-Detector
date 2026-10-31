# ADR-0007: Dynamic display-PIN pairing for the Bluetooth link

- **Status:** Accepted
- **Date:** 2026-10-31
- **Deciders:** Project team (Embedded Developer, Systems Architect)

## Context

The relay authenticated Bluetooth pairing with a fixed, public PIN
(`1234`, set once in the relay sketch). Anyone within radio range who
knew the PIN — and the PIN was in the README — could pair with the
detector. For a device whose selling point is "safe, touch-free
inspection," a well-known static credential undermined that story even
though the sensor data itself is low-value.

## Decision

Replace the fixed PIN with a **dynamic display PIN**:

1. The relay switches to variable PIN mode
   (`esp_bt_gap_set_pin(ESP_BT_PIN_TYPE_VARIABLE, ...)`) and registers a
   GAP callback. On each PIN request it generates a fresh random code
   with `esp_random()` — 6 digits, or 16 when the phone demands it —
   replies to the phone via `esp_bt_gap_pin_reply`, and forwards the code
   to the Uno over the existing UART link as `PIN,<code>` (repeated every
   500 ms while the attempt is pending). On auth success it sends
   `PINOK`.
2. The Uno shows the code as an overlay on the OLED for 60 s (or a 3 s
   "Paired!" flash on `PINOK`), then returns to the normal readings
   screen. Sensing never stops while the overlay is shown. The
   parse/timing logic (`paircode.h`) is host-tested.
3. Pairing stays in the phone's system Bluetooth settings; the Android
   app is unchanged.

Wrong-code retries get a new code because each PIN request event
regenerates it.

## Alternatives considered

| Option | Why not |
|---|---|
| Keep the fixed PIN, make it "secret" (printed label) | Still static: anyone who has seen the device knows it forever. |
| App-level code entry on every connection | Requires rebuilding the app and a command protocol; the user wanted pair-once semantics. |
| SSP passkey entry instead of legacy PIN | The legacy variable-PIN path is the well-trodden one (consumer keyboards use it) and works with the ESP32 Arduino core as-is. |
| Generate the code on the Uno | The ESP32 owns the Bluetooth exchange; the code must be known to the radio at reply time, so generation belongs there. |

## Consequences

- **Positive:** a fresh credential per pairing attempt; no public static
  PIN; bonded phones keep working silently; no app or wiring changes.
- **Negative:** the code is shown in plain sight on the OLED, so anyone
  who can see the device and is in radio range can pair (the same trust
  model as TVs and keyboards — better than `1234`, not account-grade).
  If the Uno is unpowered during a pairing attempt, the code cannot be
  displayed and the attempt will time out (60 s).
- **Neutral:** the UART link now carries `PIN,`/`PINOK` lines in
  addition to the status stream; documented in
  `docs/api-specification.md` §2.
