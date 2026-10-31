# ADR-0008: Pairing window + type-in PIN (locked by default)

- **Status:** Accepted
- **Date:** 2026-10-31
- **Deciders:** Project team (Embedded Developer, Systems Architect)

## Context

ADR-0007 moved pairing to a per-attempt random display PIN, and the relay
was later given an `ESP_BT_IO_CAP_OUT` capability so phones would not pair
silently. On real hardware this produced the opposite of the intent: the
ESP32's SSP handlers (KEY_REQ / KEY_NOTIF / CFM_REQ) answered
passkey/numeric-comparison requests, and phones negotiated a **numeric
comparison / Just Works** confirmation — a tap to confirm, never a prompt
to TYPE a code. Worse, the variable-PIN path accepted pairing requests at
ANY time, so the device was an always-open pairing target instead of a
locked one.

## Decision

Lock the device by default and move to the **legacy fixed-PIN path**,
where the phone shows the classic type-in dialog:

1. The relay runs with `esp_bt_gap_set_pin(ESP_BT_PIN_TYPE_FIXED, 6, ...)`.
   Normally the PIN is a random 6-digit secret nobody knows, so nothing
   can pair.
2. Holding the detector's button for 2 s (or sending `p` on the Uno's
   USB serial) makes the Uno send `PAIRON` over Link A. The relay opens a
   **60 s pairing window**: it installs a fresh random 6-digit PIN, sends
   `PIN,<code>` (repeated every 500 ms) for the OLED, and the phone's
   dialog prompts the user to TYPE the code.
3. The window code stays valid for the WHOLE window: wrong-PIN retries
   reuse the same code.
4. On success the relay sends `PINOK`; on success or expiry it installs a
   NEW random secret and sends `PINOFF`. The Uno clears the overlay on
   `PINOFF` with no "Paired!" flash (that is reserved for `PINOK`).
5. A `PAIRON` arriving during an open window is ignored (no reset).
6. The SSP confirm handlers (KEY_REQ / KEY_NOTIF / CFM_REQ) and the
   `ESP_BT_SP_IOCAP_MODE` / `ESP_BT_IO_CAP_OUT` capability lines are
   REMOVED: they caused the numeric-comparison path this decision
   replaces.
7. Bonded phones reconnect silently, with no window needed.

## Alternatives considered

| Option | Why not |
|---|---|
| Keep variable-PIN + SSP confirm | Phones negotiated numeric comparison / Just Works and paired without a typed code; pairing was always open. |
| Fixed well-known PIN (e.g. `1234`) | Public static credential; anyone who read the README could pair (ADR-0007 already rejected this). |
| Numeric comparison with a confirm button on the device | Needs another button/UI on the detector and still yields no typed secret. |
| Windowed SSP passkey entry | The ESP32 Arduino core does not reliably force the type-in dialog; the legacy fixed-PIN path is the well-trodden one (consumer keyboards use it). |

## Consequences

- **Positive:** the device is a locked target except during a short,
  user-initiated window; a fresh code per window; wrong-code retries work
  within the window; bonded phones keep working; no app or wiring
  changes.
- **Negative:** the code is shown in plain sight on the OLED while the
  window is open, so anyone who can see the device and is within radio
  range can pair during the window; opening the window requires physical
  access to the device or serial access to the Uno (by design).
- **Neutral:** Link A now also carries `PAIRON` (Uno→relay) and `PINOFF`
  (relay→Uno) lines in addition to the status stream and `PIN,`/`PINOK`;
  documented in `docs/api-specification.md` §2.
- **Assumption (to verify on hardware):** that Android shows the legacy
  type-in dialog for a FIXED-pin legacy pairing. If a phone shows numeric
  comparison instead, the finding is reported rather than changing the
  design.

---

## Revision 2026-10-31 (hardware finding)

**Status:** Accepted (supersedes parts of the original decision above;
the original text is kept for the record).

### Hardware finding

The original decision's central assumption was falsified on a real
phone: with FIXED-pin mode and the SSP handlers / IO-capability lines
removed, the phone paired **silently via SSP "Just Works"** — no dialog,
no PIN asked. The fixed secret PIN never came into play, so the "lock"
did not exist: anybody in radio range could pair. A second observation:
`AUTH_CMPL` also fires for a bonded device reconnecting with its stored
link key, which made the relay print "PINOK - pairing completed" on a
mere reconnect.

### Revised decision

The lock is now enforced **at the pairing-event level**, regardless of
which mechanism the phone chooses:

1. `setup()` sets the SSP IO capability to **DisplayOnly**
   (`ESP_BT_SP_IOCAP_MODE` / `ESP_BT_IO_CAP_OUT`) again, so Android
   negotiates SSP **numeric comparison**, which fires events the relay
   can gate.
2. `gapCallback` handles `CFM_REQ`, `KEY_REQ`, and `KEY_NOTIF`, each
   gated on `windowOpen`:
   - **Locked:** `CFM_REQ` → `ssp_confirm_reply(..., false)`;
     `KEY_REQ` → `ssp_passkey_reply(..., false, 0)`; nothing is
     displayed. Every pairing attempt is refused outright.
   - **Window open:** `CFM_REQ` → accept; `KEY_REQ` → roll a passkey,
     reply, show it on the OLED; `KEY_NOTIF` → show the stack-chosen
     passkey on the OLED (the phone shows the same number; the user
     verifies and taps Pair).
3. The fixed PIN is kept (`ESP_BT_PIN_TYPE_FIXED`): while locked it is an
   unknowable secret; inside the window it is the window code, which the
   stack auto-answers for legacy type-in devices (no `PIN_REQ` handler is
   needed — the stack replies with the fixed pin itself).
4. `AUTH_CMPL` success now distinguishes: with the window open it sends
   `PINOK` and closes the window (re-lock); without the window it is a
   bonded-device reconnect (stored link key) — logged, no `PINOK`, window
   state untouched. Failure leaves the window unchanged (same code valid
   until expiry).

### Consequences of the revision

- **Positive:** the lock is real — while locked, every SSP pairing event
  is refused, so Just Works cannot slip through; inside the window the
  pairing is confirm-style on Android (phone + OLED show the same code).
- **Negative (honest):** Android cannot be made to show a type-in dialog
  with this stack; the window's authentication on Android is "verify the
  number and tap Pair" (numeric comparison), which is weaker than typing
  a secret but still requires physical access to open the window.
- **Unverifiable without hardware:** that rejecting `CFM_REQ` while
  locked makes the phone show a pairing failure ("Couldn't pair"). The
  rejection itself is confirmed by serial log; the phone's exact UX must
  be observed on a real device.
