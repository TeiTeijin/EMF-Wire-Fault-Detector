# API Specification — EMF Wire Fault Detector

**Status:** current · **Applies to:** firmware `EMF_Wire_Fault_Detector_v11` + relay `ESP32_BT_Relay_2` + frontend `EMFReader`

This document defines the integration interface between the edge node and the
frontend, plus the internal link inside the edge node. There is no HTTP
server and no database in this system: the "API" is a line-oriented text
stream carried over Bluetooth Classic (Serial Port Profile).

```
Arduino Uno (firmware) ──UART 9600──► ESP32 (relay) ──Bluetooth SPP──► Android app (frontend)
        Link A (internal)                    Link B (integration interface)
```

---

## 1. Link B — Bluetooth Classic SPP (integration interface)

| Property | Value |
|---|---|
| Transport | Bluetooth Classic RFCOMM, Serial Port Profile |
| Role | The **ESP32 relay is the server**; it accepts incoming connections. The app is the client. |
| Service UUID | `00001101-0000-1000-8000-00805F9B34FB` (standard SPP) |
| Advertised device name | `EMF_Wire_Detector` |
| **Authentication** | Bluetooth bonding with a **fixed PIN `1234`** (`esp_bt_gap_set_pin`, `ESP_BT_PIN_TYPE_FIXED` in the relay sketch). The link also inherits Bluetooth link-layer encryption once bonded. |
| Discovery | No scanning. The app lists the phone's **bond table** and keeps devices whose name is `EMF_Wire_Detector` **or** that expose the SPP UUID. Pairing happens outside the app, in system Bluetooth settings. |
| Payload | Newline-terminated ASCII/UTF-8 lines, one message per line. |
| Cadence | The relay forwards one status line per device report — every **500 ms**. |
| Byte rate | 9600 baud on Link A; far below the SPP capacity, so lines arrive promptly. |

### Authentication — what it does and does not do

- **Implemented:** pairing requires the fixed PIN `1234`; the RFCOMM channel is
  only accepted from a bonded peer, and the traffic runs over the encrypted
  Bluetooth link. This is the standard authentication model for SPP devices.
- **Limitations (documented for honesty):** the PIN is fixed and public; there
  is no per-user account or token; anyone within radio range who knows the PIN
  can pair. This is acceptable for a bench/field measurement instrument that
  carries no personal data — it is **not** suitable for untrusted environments.
- The link carries sensor percentages only. Nothing is stored, forwarded to
  the internet, or logged off-device.

### Client permission requirements (Android)

| Android version | Required permission |
|---|---|
| 12 (API 31) and newer | `android.permission.BLUETOOTH_CONNECT` (runtime, "Nearby devices") |
| 11 and older | `BLUETOOTH` + `BLUETOOTH_ADMIN` manifest permissions |

The app never scans for nearby devices, so `BLUETOOTH_SCAN` and location
permissions are not requested.

---

## 2. Link A — UART (internal, device-to-relay)

| Property | Value |
|---|---|
| Format | 9600 baud, 8N1 |
| Wiring | Arduino `D3` (TX, 5 V) → voltage divider → ESP32 `GPIO16` (RX2); Arduino `D2` (RX) ← ESP32 `GPIO17` (TX2); common GND required |
| Framing | Newline-terminated lines (`println`), accumulates bytes into ASCII text |

The relay is a transparent forwarder: it sends each complete line to the
Bluetooth peer and mirrors it on its USB serial for debugging. It does not
alter or buffer payloads.

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
| Detector v11 (QMC5883P) | `p2p=41 opampPct=44 field=12 mag=12345 xyz=1,2,3 fails=0 same=0 reinit=0 fieldPct=12 jitterPct=20` | `fieldPct` is preferred; raw `field`/`mag`/`xyz` counters are ignored |

Routing rule: a line containing `=` is a debug line, anything else is tried
as a status line. (The v11 debug line embeds commas inside `xyz=x,y,z`, so a
comma check alone would misroute it.) On debug lines the client recomputes
`signal`/verdict with the same thresholds the firmware uses, so a re-tuned
sketch is still honoured.

### 3.3 Commands (client → device) — reserved

The relay reads lines sent by the phone and logs them (USB serial); a
commented-out hook exists to forward them to the Uno. No commands are defined
yet. Future use is reserved for calibration triggers (`CAL`) and configuration
writes. Clients must not assume commands are executed today.

---

## 4. Client behaviour requirements (normative)

| ID | Requirement |
|---|---|
| C-1 | Validate each numeric field as an integer in 0–100; reject the whole line otherwise. |
| C-2 | Keep the last good reading on screen when a line is rejected or the stream is briefly disturbed. |
| C-3 | Bound line accumulation at 160 bytes; discard an unterminated overlong line. |
| C-4 | If the socket opens but no line arrives within **6 s**, treat the device as `SILENT` (Arduino unpowered or UART link broken) and surface that distinctly. |
| C-5 | If the RFCOMM connect does not complete within **10 s**, report `REFUSED` (peer off or unreachable). |
| C-6 | On link loss, show a reconnect banner for **5 s**, then return to the device list. |
| C-7 | On a status line, carry the device verdict through untouched. On a debug line, recompute the verdict using thresholds `15` (clear) and `50` (fault), matching the firmware. |
| C-8 | Keep a 30-point trace (15 s at the 500 ms cadence) and report the peak signal of the window. |

## 5. Failure taxonomy (client-visible)

| Condition | Reported as | User-facing advice (app) |
|---|---|---|
| No answer to RFCOMM connect (device off) | `REFUSED` | "Power-cycle the ESP32 and try again." |
| Channel refused/busy | `BUSY` | "Make sure another app is not connected." |
| Peer vanishes mid-connect | `DISAPPEARED` / `OUT_OF_RANGE` | "Move closer to the sensor and try again." |
| Socket open, zero lines | `SILENT` | "Check the Arduino is powered and its TX wire reaches the ESP32." |

## 6. Compatibility matrix

| Device build | Lines emitted | App support |
|---|---|---|
| v11 firmware + relay 2 (current) | Status line | Full — verdict carried through |
| v3 firmware | Debug line (`magnet=`) | Full — verdict recomputed |
| v8 firmware (analog Hall) | Debug line (`hallPct=`) | Full — verdict recomputed |
| Relay 1 / HC-05 style relays | Forwarded lines unchanged | Full, if the line format matches one above |

## 7. Change control

Any change to the message format or cadence must update, in one commit:
`firmware/`, `backend/`, this document, and the app-side parser
(`frontend/EMFReader/.../data/EmfSppContract.kt`), whose unit tests pin the
exact behaviour of every format above.
