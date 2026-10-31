# ADR-0009: BLE UART (GATT) + static-passkey type-in pairing

- **Status:** Accepted
- **Date:** 2026-10-31
- **Deciders:** Project team (Embedded Developer, Systems Architect)

## Context

The phone link ran over Bluetooth Classic SPP (ADR-0001) with the
pairing-window lock from ADR-0008. ADR-0008's revision showed on real
hardware that the classic stack cannot force Android into a type-in
dialog: the phone either paired silently via Just Works or showed
numeric comparison ("verify the number, tap Pair"). The product goal
was the Samsung-keyboard experience — a code that exists ONLY on the
OLED and must be typed on the phone. Numeric comparison shows the same
code on the phone, which defeats the "type a secret" model, and the
legacy fixed-PIN path had already been falsified (it silently fell back
to Just Works).

The ESP32 Arduino core (3.3.11) bundles a Bluedroid BLE stack with
static-passkey support (`ESP_BLE_SM_SET_STATIC_PASSKEY`, IO capability
`ESP_IO_CAP_OUT`, `ESP_GATT_PERM_*_ENC_MITM` permissions), and the
Nordic UART Service is the de-facto standard serial-over-BLE profile —
a natural drop-in for the SPP relay.

## Decision

Move the phone link from Bluetooth Classic SPP to **BLE UART (GATT)
with MITM static-passkey pairing**:

1. The ESP32 becomes a BLE peripheral advertising `EMF_Wire_Detector`
   with the Nordic UART Service (`6E400001-B5A3-F393-E0A9-E50E24DCCA9E`):
   RX characteristic (write, phone→device) and TX characteristic
   (notify, device→phone), both — and the CCCD descriptor — requiring
   an encrypted MITM link (`ESP_GATT_PERM_*_ENC_MITM`), so subscribing
   itself forces pairing.
2. Security parameters: **DisplayOnly** IO capability, **bonding +
   MITM + secure connections**, and a **static passkey** — the phone
   shows the classic type-in dialog and the user types the OLED code.
3. The pairing-window state machine ports unchanged from the classic
   relay: boot → random secret passkey (locked); `PAIRON` from the Uno
   → fresh 6-digit passkey + `PIN,<code>` every 500 ms for 60 s;
   wrong-code retries reuse the same code; bond success → `PINOK` +
   re-lock with a new random secret; expiry → re-lock + `PINOFF`.
   The secret is never printed or sent to the Uno.
4. The Uno↔ESP32 UART protocol (`PAIRON` / `PIN,` / `PINOK` /
   `PINOFF`) and the status-line protocol are unchanged; the Uno
   firmware is untouched.
5. The Android app swaps its RFCOMM session for a BLE GATT session
   implementing the same `DeviceSession` seam: scan by the NUS service
   UUID, GATT connect (10 s → `REFUSED`), first line (6 s → `SILENT`),
   bond cancel/wrong code → new `ConnectFailure.PAIRING_FAILED` with
   honest advice, link loss → existing 5 s banner. GATT notification
   chunks are reassembled by a pure, host-tested `LineBuffer`.
6. Permissions: `BLUETOOTH_SCAN` (API 31+) plus
   `ACCESS_FINE_LOCATION` (API ≤ 30) join the existing
   `BLUETOOTH_CONNECT` flow.

## Alternatives considered

| Option | Why not |
|---|---|
| Keep classic SPP + numeric comparison (ADR-0008 revised) | The phone shows the same code instead of prompting to type it; never delivered the type-in experience. |
| Classic SPP + legacy fixed PIN | Falsified on hardware: phones paired silently via Just Works, so the lock did not exist. |
| BLE with a fixed well-known passkey | Public static credential; anyone who read the docs could pair. |
| BLE with Just Works + out-of-band gating | No type-in secret; weaker than the passkey model. |
| NimBLE instead of the core's Bluedroid BLE | Extra dependency; the installed core already ships Bluedroid BLE with static-passkey support. |

## Consequences

- **Positive:** the phone genuinely prompts the user to TYPE the code
  from the OLED (MITM static-passkey pairing); the lock is the secrecy
  of the passkey — no window, no known code, no pairing; the relay
  keeps forwarding/logging exactly as before; the Uno firmware and the
  status-line contract are unchanged; bonded phones reconnect silently.
- **Negative:** the code is still shown in plain sight on the OLED
  while the window is open (unchanged from ADR-0008); BLE requires
  `BLUETOOTH_SCAN` / location permission handling that SPP did not;
  pairing now starts from the app instead of system settings, so the
  app must surface pairing failures honestly (`PAIRING_FAILED`).
- **Neutral:** the classic SPP build and ADR-0001 remain as history;
  a phone with only classic Bluetooth cannot connect.
- **Assumptions (to verify on hardware):** that Android shows the
  type-in dialog for a DisplayOnly peripheral with a static passkey and
  MITM auth-req, that the CCCD-write-triggered system bonding appears
  when the user taps Connect, and that BLE notification timing keeps
  the 500 ms status cadence intact. Host tests and compiles verify
  everything except the actual dialog and radio timing.
