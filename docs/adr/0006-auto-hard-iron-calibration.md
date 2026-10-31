# ADR-0006: Automatic background hard-iron calibration (no turn ritual)

- **Status:** Accepted
- **Date:** 2026-10-27
- **Deciders:** Project team (Embedded Developer, Systems Architect)

## Context

ADR-0004's turn calibration worked only when done well. Field validation
surfaced two failure modes:

1. **Location-locked offsets.** The offsets were learned in one room
   (the kitchen). A planar or asymmetric sweep lets the local environmental
   field leak into the saved midpoint, so the offsets were only correct
   where they were learned. Elsewhere in the house the computed magnitude
   became orientation-dependent — a slight tilt moved the reading from 0%
   to 100% — exactly the symptom the magnitude trick was meant to kill.
2. **Ritual friction.** The 8 s guided rotation re-appeared whenever the
   sweep quality was rejected, which read as "the device keeps asking me to
   turn it" rather than "the sweep was bad".

## Decision

Firmware v13 removes the manual turning step entirely and learns the
hard-iron offsets automatically:

1. **Background learning.** During normal operation the firmware tracks the
   running min/max of raw X/Y/Z and computes the midpoint per axis. It
   locks and saves to EEPROM only when **all three axes** have swept at
   least 600 counts each — the stricter gate (ADR-0004 allowed two of
   three) is what rejects the planar spins that caused location-locked
   offsets. The device keeps refining the offsets in RAM as new
   orientations are seen, re-saving only when the midpoint drifted at
   least 25 counts and 10 s have passed since the last save (EEPROM
   throttling). Frozen-chip repeats are ignored.
2. **Fresh start marker.** The EEPROM validity marker changes from `0xA5`
   to `0xB7`, so offsets learned by older versions (including the
   contaminated kitchen offsets) are discarded on the first v13 boot.
   `t` in the serial monitor now means "forget the offsets and re-learn
   from normal use".
3. **Verdict debounce.** Clear / Wire / Fault commits only after four
   consecutive samples of the same verdict, so brief movement spikes do
   not flip the classification (or the buzzer and phone vibration).
4. **Softer scales and faster follow.** Field scales relax from 250/100
   counts to 500/200 counts per 100%, and the zero point follows faster
   (τ 5 s Clear, 60 s Wire, 120 s Fault) so changing rooms re-zeroes
   quickly instead of pinning the reading at 100%.

The learning logic (`autocal.h`) and the debounce (`verdict.h`) are
pure-logic modules covered by host-side unit tests (`tools/v13-host-tests/`,
g++, no Arduino toolchain needed).

## Alternatives considered

| Option | Why not |
|---|---|
| Keep the manual turn, require all 3 axes | Still a ritual; still vulnerable to a distorted calibration spot; users found it confusing. |
| PC-side calibration tool (e.g. MotionCal-style ellipsoid fit) | Needs a laptop in the loop per device; a capstone handheld must stand alone. |
| External magnetometer library with built-in auto-adjust | None persist to EEPROM or gate on sweep quality; the rolling min/max midpoint is ~40 lines and fully host-tested. |
| Per-room recalibration workflow (button press on floor change) | Only re-zeroes the strength reference; it cannot repair contaminated offsets — tilting still swings the reading. |

## Consequences

- **Positive:** no turn ritual; offsets improve with use and are
  location-independent once all three axes are swept; contaminated v11/v12
  offsets are discarded automatically.
- **Negative:** until the first lock the field channel is tilt-sensitive
  (the OLED shows *Calibrating...*); the first lock can only happen after
  the device has been moved through enough orientations — normal handling
  satisfies this, but a device that is never moved never locks. The
  first-lock offset change is re-zeroed in firmware so the correction
  itself is not reported as a signal.
- **Neutral:** the learning depends on the user handling the device
  naturally; a device used in a single fixed orientation forever keeps the
  hint. `t` exists as an explicit "start over" escape hatch.
