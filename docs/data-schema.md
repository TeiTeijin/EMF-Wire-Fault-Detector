# Data Schema — EMF Wire Fault Detector

**Companion diagram:** [`erd.svg`](erd.svg) ([PNG](erd.png)) — Chen-notation
ERD of the logical model below.

**Important note on scope.** This system has **no relational database and no
server**. It is a real-time instrument: data lives in the wire between the
devices and in each device's memory. The schema therefore documents (a) the
*logical* data model — the entities and relationships the system reasons
about — and (b) the *physical* stores each entity actually lives in, mapped
truthfully. Every entity below exists in the running system; nothing is
aspirational.

```
ERD summary (see erd.svg for the full diagram)

  ALERT_LEVEL ──classifies── READING ══captures══ SESSION ──binds── DEVICE ══saves══ CALIBRATION
  (lookup:                 (weak entity:  (app RAM)   (bond       (optional)   (weak entity,
   CLEAR/WIRE/FAULT)        identified                  table)                  identified by
                            by session)                                          device; EEPROM)
```

---

## 1. Entity: `READING` — one 500 ms sample (the wire record)

The status line **is** the `READING` row; a session is the stream of them.
Primary key: `(session_id, sequence)` — a reading is meaningless outside its
session (weak entity, see the ERD).

| Attribute | Type | Domain / constraint | Notes |
|---|---|---|---|
| `sequence` | integer | 1…n, increases per session | Client-assigned arrival counter |
| `signal_pct` | integer | **0–100** | Headline signal, `max(field_pct, opamp_pct)` on the device |
| `field_pct` | integer | **0–100** | Magnetometer channel (QMC5883P field-strength change) |
| `opamp_pct` | integer | **0–100** | Antenna/op-amp channel (peak-to-peak change) |
| `jitter_pct` | integer | **0–100** | Erraticness; feeds the FAULT rule |
| `alert_code` | FK | `ALERT_LEVEL.code` | Carried by status lines; recomputed for legacy debug lines |

Wire mapping — the status line `STATUS,signal,fieldPct,opampPct,jitterPct`:

```
WIRE,44,0,44,20   →   alert_code=WIRE, signal_pct=44, field_pct=0,
                      opamp_pct=44, jitter_pct=20   (sequence: client-assigned)
```

Integrity rules at the boundary: each percentage must parse as an integer in
0–100 or the **whole line is rejected** and the previous reading stays on
screen (client rule C-1/C-2, `docs/api-specification.md`). The device clamps
its own values before transmission.

**Physical store:** transient (Bluetooth stream); the client keeps the most
recent 30 readings — the 15 s trace window — in a ring buffer; older rows are
evicted (multi-valued `trace` attribute of SESSION on the ERD).

## 2. Entity: `ALERT_LEVEL` — the verdict lookup

| Attribute | Type | Domain / constraint | Notes |
|---|---|---|---|
| `code` | text (PK) | `CLEAR` \| `WIRE` \| `FAULT` | The status word on the wire |
| `label` | text | Clear / Wire Detected / POSSIBLE FAULT | OLED + app wording |
| `rule` | text | see below | Kept in firmware and app identically |

```
signal_pct < 15                  → CLEAR
signal_pct ≥ 15 and jitter < 50  → WIRE
signal_pct ≥ 15 and jitter ≥ 50  → FAULT
```

**Physical store:** derived, not stored. The device sends its own verdict on
status lines; the app carries it through, or recomputes it with the same rule
when an older sketch sends raw debug lines.

## 3. Entity: `SESSION` — one connected measurement sitting

| Attribute | Type | Domain / constraint | Notes |
|---|---|---|---|
| `session_id` | integer | app-scoped, +1 per connect | Not persisted across app restarts |
| `phase` | enum | `Connecting` \| `Live` \| `Reconnecting(n)` \| `Finished` | Drives the screen state |
| `muted` | boolean | true/false | Silences the phone's fault buzz only |
| `reconnect_seconds` | integer | 5…1 countdown | Transient, shown in the banner |
| `trace` | multivalued | 30 × `READING` | Ring buffer, oldest evicted (double ellipse on the ERD) |
| `peak` | integer | 0–100 | **Derived:** max `signal_pct` of the current window (dashed ellipse) |

**Physical store:** the app's RAM (`EmfSessionViewModel`); survives screen
rotation, dies with the app process. A session that connects but receives
zero readings still exists with an empty `trace` (the `SILENT` failure case —
hence partial participation on the ERD).

## 4. Entity: `DEVICE` — a paired sensor unit

| Attribute | Type | Domain / constraint | Notes |
|---|---|---|---|
| `address` | text (PK) | MAC address | Assigned by the OS when pairing |
| `name` | text | `EMF_Wire_Detector` | Relay's advertised name |

**Physical store:** the phone OS's Bluetooth bond table; the app lists the
bonded entries whose name is the relay's or whose services expose the SPP
UUID. No readings are stored on or discovered from the device itself.

## 5. Entity: `CALIBRATION` — saved board-field offsets (weak entity)

Identified by its `DEVICE` through the identifying relationship `saves`; a
recalibration overwrites the single saved row for that device.

| Attribute | Type | Domain / constraint | Notes |
|---|---|---|---|
| `marker` | byte | must equal `0xA5` | Validity flag: without it the offsets are ignored |
| `off_x` | int16 | counts | Board's own fixed field, X axis |
| `off_y` | int16 | counts | Y axis |
| `off_z` | int16 | counts | Z axis |

**Physical store:** Arduino **EEPROM**, exactly 7 bytes:

| EEPROM byte(s) | Content |
|---|---|
| `0` | marker `0xA5` |
| `1–2` | `off_x` (int16) |
| `3–4` | `off_y` (int16) |
| `5–6` | `off_z` (int16) |

Write rule: offsets are saved only if at least **two axes** were really swept
during the turning calibration (each with a min–max span ≥ 600 counts), so a
bad sweep is never persisted.

### Runtime calibration state (RAM-only, re-learned every boot)

Not part of the ERD (no identity, single instance, volatile), listed for
completeness of the schema:

| Field | Meaning | Lifetime |
|---|---|---|
| `baseline_opamp` | Mean antenna peak-to-peak while calibrating | Until next calibration |
| `ceil_p2p` | Highest p2p seen while calibrating (the "wobble ceiling") | Until next calibration |
| `jitter_p2p` | Average reading-to-reading jump while calibrating | Until next calibration |
| `base_mag` | Mean field strength while calibrating | Until next calibration |
| `field_band` | Field strength wobble while calibrating | Until next calibration |
| `field_jitter` | Average field jump while calibrating | Until next calibration |
| `zero_point` | **Derived:** field strength zero point; starts at `base_mag` and follows the device (τ 15 s clear / 120 s active) | Session |

## 6. Storage mapping (summary)

| Entity | Store | Survives power cycle? |
|---|---|---|
| `READING` | BT stream + 15 s RAM ring buffer | No |
| `ALERT_LEVEL` | Derived (rule in firmware + app) | n/a |
| `SESSION` | App RAM | No |
| `DEVICE` | Phone OS bond table | Yes (until unpaired) |
| `CALIBRATION` | Arduino EEPROM (7 bytes) | Yes |
| Runtime calibration state | Arduino RAM | No |

## 7. Integrity rules (as implemented)

1. **Domain checks at the boundary:** every percentage is 0–100 or the line
   is dropped (client), and clamped at the source (device).
2. **Referential integrity:** `READING.alert_code` must exist in
   `ALERT_LEVEL`; unknown status words invalidate the line.
3. **Weak-entity identification:** readings belong to exactly one session;
   the stored calibration belongs to exactly one device.
4. **Bounded history:** the trace holds at most 30 rows; the oldest is
   evicted (no unbounded growth anywhere in the system).
5. **Write-conditioned persistence:** EEPROM is written only behind the
   2-axes/600-count sweep check; the reload path requires the `0xA5` marker.
