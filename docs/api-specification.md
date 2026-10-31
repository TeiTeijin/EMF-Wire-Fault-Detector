# API Specification — EMF Wire Fault Detector

**Status:** current · **Applies to:** firmware `EMF_Wire_Fault_Detector_v14` + relay `ESP32_BT_Relay_2` + frontend `EMFReader`

This document defines the integration interface between the edge node and the
frontend, plus the internal link inside the edge node. There is no HTTP
server and no database in this system: the "API" is a line-oriented text
stream carried over Bluetooth Low Energy (GATT, Nordic UART Service).

```
Arduino Uno (firmware) ──UART 9600──► ESP32 (relay) ──BLE GATT (NUS)──► Android app (frontend)
        Link A (internal)                    Link B (integration interface)
```

---

## 1. Link B — Bluetooth LE GATT (integration interface)

| Property | Value |
|---|---|
| Transport | Bluetooth Low Energy, GATT (Bluedroid stack on the ESP32) |
| Role | The **ESP32 relay is the BLE peripheral** advertising `EMF_Wire_Detector`; it accepts incoming connections. The app is the central. |
| Service | Nordic UART Service (NUS), UUID `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| RX characteristic | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` — phone → device, **write** (commands, logged) |
| TX characteristic | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` — device → phone, **notify** (status lines) |
| **Authentication** | MITM **static-passkey** pairing: DisplayOnly IO capability, bonding + MITM + secure connections. Both characteristics (and the CCCD descriptor) require an **encrypted, MITM-authenticated** link (`ESP_GATT_PERM_*_ENC_MITM`), so subscribing itself forces pairing. The device is **locked by default** — the static passkey is a random 6-digit secret nobody knows, so a phone shows a type-in dialog but cannot guess the code. Holding the detector's button 2 s (or the serial command `p`) makes the Uno send `PAIRON`; the relay then opens a 60 s window with a fresh random 6-digit passkey, forwards it to the Uno as `PIN,<code>` for display on the OLED, and the phone prompts the user to **type** that code. On success or after 60 s the device re-locks with a new random secret. |
| Discovery | The app **scans** for BLE advertisements carrying the NUS service UUID. Pairing is initiated from the app's Connect tap (the encrypted-attribute access triggers the system passkey dialog); no system-settings pairing is needed. |
| Payload | Newline-terminated ASCII/UTF-8 lines, one message per line. |
| Cadence | The relay forwards one status line per device report — every **500 ms**; the app requests an MTU of 185 so even the longest line (21 bytes) arrives as one notification. |

### Authentication — what it does and does not do

- **Implemented:** the device is **locked by default**. The static passkey
  is a random 6-digit secret nobody knows, and the secret is never
  printed or sent to the Uno — only the window code ever goes out. The
  user opens a 60 s **pairing window** by holding the detector's button
  2 s (or sending `p` over USB serial), which makes the Uno send `PAIRON`
  to the relay. Inside the window the relay installs a fresh random
  6-digit passkey, shows it on the device's own OLED as `PIN,<code>`
  (repeated every 500 ms), and the phone prompts the user to **type**
  the code (MITM static-passkey pairing — the Samsung-keyboard
  experience). Wrong-code or cancelled attempts can be retried inside
  the same window with the same code. On success the relay sends
  `PINOK`; on success or 60 s expiry it re-locks with a NEW random
  secret and sends `PINOFF`. A bonded phone reconnects silently (stored
  LTK), with no window needed, and all traffic runs over the encrypted
  MITM-authenticated link.
- **Limitations (documented for honesty):** the code is shown in plain
  sight on the OLED while the window is open, so anyone who can see the
  device and is within radio range can pair; there is no per-user
  account or token. The lock is the secrecy of the passkey, not a
  refusal of the pairing itself — while locked, a phone can open a
  type-in dialog but has nothing to type. Pairing is one-time per
  phone — a bonded phone reconnects silently afterwards. Acceptable for
  a bench/field measurement instrument that carries no personal data —
  it is **not** suitable for untrusted environments.
- The link carries sensor percentages only. Nothing is stored, forwarded to
  the internet, or logged off-device.

### Client permission requirements (Android)

| Android version | Required permission |
|---|---|
| 12 (API 31) and newer | `android.permission.BLUETOOTH_CONNECT` + `android.permission.BLUETOOTH_SCAN` (runtime, "Nearby devices") |
| 11 (API 30) and older | `BLUETOOTH` + `BLUETOOTH_ADMIN` manifest permissions + `ACCESS_FINE_LOCATION` (runtime — BLE scan results ride on location up to API 30) |

The app scans for the relay's advertisement (NUS service UUID), so
`BLUETOOTH_SCAN` (API 31+) or the location permission (API ≤ 30) is
required in addition to `BLUETOOTH_CONNECT`.

---

## 2. Link A — UART (internal, device-to-relay)

| Property | Value |
|---|---|
| Format | 9600 baud, 8N1 |
| Wiring | Arduino `D11` (TX, 5 V) → voltage divider → ESP32 `GPIO16` (RX2); Arduino `D10` (RX) ← ESP32 `GPIO18` (TX2); common GND required |
| Framing | Newline-terminated lines (`println`), accumulates bytes into ASCII text |

The relay is a transparent forwarder: it sends each complete line to the
Bluetooth peer and mirrors it on its USB serial for debugging. It does not
alter or buffer payloads.

The relay additionally uses this link for the pairing window, so the Uno
can display the code on the OLED: `PAIRON` (Uno→relay, sent 3× over
~300 ms) opens the 60 s window; `PIN,<code>` (relay→Uno, repeated every
500 ms while the window is open) shows the 6-digit code the phone user
must type; `PINOK` (relay→Uno) marks pairing success; `PINOFF`
(relay→Uno) clears the overlay when the window closes (success or
expiry). All lines are newline-terminated; CRLF is tolerated.

---

## 3. Message format (device → client)

### 3.1 Status line — the contract

```
STATUS,signal,fieldPct,opampPct,jitterPct
```

| # | Field | Type | Range | Meaning |
|---|---|---|---|---|
| 1 | `STATUS` | word | `CLEAR` \| `WIRE` \| `FAULT` | The device's own verdict (classification happens on the Uno) |
| 2 | `signal` | int | 0–100 | Headline signal = `max(fieldPct, opampPct)` |
| 3 | `fieldPct` | int | 0–100 | Magnetometer channel (QMC5883P, change in field strength) |
| 4 | `opampPct` | int | 0–100 | Antenna/op-amp channel (electric-field peak-to-peak) |
| 5 | `jitterPct` | int | 0–100 | Erraticness; `FAULT` needs `signal ≥ 15` and `jitter ≥ 50` |

Examples:

```
CLEAR,0,0,0,0
WIRE,44,0,44,20
FAULT,62,0,62,71
```

The device clamps every percentage with `constrain(1..100)`; a malformed
number, a wrong field count, or an unknown status word is **invalid** and is
dropped by the client (the previous reading stays on screen).

### 3.2 Debug lines — tolerated for compatibility

Older sketches transmit their verbose `Serial` output instead. The client
accepts the three known variants:

| Firmware | Example | Notes |
|---|---|---|
| `EMF_Reader_8` (analog Hall) | `hallPct=12 opampPct=44 jitterAvg=8 jitterPct=20 signal=44` | `hallPct` maps to the field channel |
| Detector v3 (switch Hall) | `p2p=41 opampPct=44 jitterAvg=8 jitterPct=20 magnet=0` | `magnet=0/1` maps to field 0/100; signal falls back to `opampPct` (v3 classifies on op-amp only) |
| Detector v11/v12 (QMC5883P) | `p2p=41 opampPct=44 field=12 mag=12345 xyz=1,2,3 fails=0 same=0 reinit=0 fieldPct=12 jitterPct=20` | `fieldPct` is preferred; raw `field`/`mag`/`xyz` counters are ignored |

Routing rule: a line containing `=` is a debug line, anything else is tried
as a status line. (The v11/v12 debug line embeds commas inside `xyz=x,y,z`, so a
comma check alone would misroute it.) On debug lines the client recomputes
`signal`/verdict with the same thresholds the firmware uses, so a re-tuned
sketch is still honoured.

### 3.3 Commands (client → device) — reserved

The relay reads writes to the RX characteristic and logs them (USB
serial); a commented-out hook exists to forward them to the Uno. No
commands are defined yet. Future use is reserved for calibration
triggers (`CAL`) and configuration writes. Clients must not assume
commands are executed today.

---

## 4. Client behaviour requirements (normative)

| ID | Requirement |
|---|---|
| C-1 | Validate each numeric field as an integer in 0–100; reject the whole line otherwise. |
| C-2 | Keep the last good reading on screen when a line is rejected or the stream is briefly disturbed. |
| C-3 | Bound line accumulation at 160 bytes; discard an unterminated overlong line. |
| C-4 | If the link is up but no line arrives within **6 s**, treat the device as `SILENT` (Arduino unpowered or UART link broken) and surface that distinctly. |
| C-5 | If the GATT connection does not come up within **10 s**, report `REFUSED` (peer off or unreachable). |
| C-6 | On link loss, show a reconnect banner for **5 s**, then return to the device list. |
| C-7 | On a status line, carry the device verdict through untouched. On a debug line, recompute the verdict using thresholds `15` (clear) and `50` (fault), matching the firmware. |
| C-8 | Keep a 30-point trace (15 s at the 500 ms cadence) and report the peak signal of the window. |
| C-9 | If pairing is cancelled or the typed code is rejected during connect, report `PAIRING_FAILED` with advice to open the pairing window on the detector and check the code on its screen. |

## 5. Failure taxonomy (client-visible)

| Condition | Reported as | User-facing advice (app) |
|---|---|---|
| No GATT connection within 10 s (device off) | `REFUSED` | "Power-cycle the ESP32 and try again." |
| Channel refused/busy | `BUSY` | "Make sure another app is not connected." |
| Peer vanishes mid-connect | `DISAPPEARED` / `OUT_OF_RANGE` | "Move closer to the sensor and try again." |
| Link up, zero lines | `SILENT` | "Check the Arduino is powered and its TX wire reaches the ESP32." |
| Pairing cancelled or wrong code | `PAIRING_FAILED` | "Open the pairing window on the detector (hold its button 2 s), check the code on its screen, then try again." |

## 6. Compatibility matrix

| Device build | Lines emitted | App support |
|---|---|---|
| v14 firmware + relay 2 BLE (current) | Status line | Full — verdict carried through |
| v13 firmware + relay 2 BLE | Status line | Full — verdict carried through |
| v12 firmware + relay 2 BLE | Status line | Full — verdict carried through |
| v11 firmware + relay 2 BLE | Status line | Full — verdict carried through |
| v3 firmware | Debug line (`magnet=`) | Full — verdict recomputed |
| v8 firmware (analog Hall) | Debug line (`hallPct=`) | Full — verdict recomputed |
| Relay 2 (Bluetooth Classic SPP build) | Status line | Historical — the SPP build predates the BLE relay (ADR-0001/0009) |
| Relay 1 / HC-05 style relays | Forwarded lines unchanged | Historical — see SPP build row |

## 7. Change control

Any change to the message format or cadence must update, in one commit:
`firmware/`, `backend/`, this document, and the app-side parser
(`frontend/EMFReader/.../data/EmfSppContract.kt`), whose unit tests pin the
exact behaviour of every format above.
