# ADR-0003: Self-calibrating relative thresholds instead of fixed levels

- **Status:** Accepted
- **Date:** 2026-10-17
- **Deciders:** Project team (Embedded Developer, QA)

## Context

Absolute readings depend on things that change per unit, per room and per
session: the LM358 gain stage's offset and noise, the local 50/60 Hz mains
hum, and the Earth's field where the device is used. Fixed thresholds tuned on
a bench produce false "wire" hits in a noisy room and missed hits in a quiet
one. The device is handheld, so the user cannot be trusted to keep the exact
same posture between sessions either.

## Decision

Calibration learns the environment instead of assuming it. For each sensing
path the firmware records, over ~1 second of stable holding:

1. the **resting level** (baseline),
2. the **natural wobble** (highest peak-to-peak seen, band width),
3. the **natural jumpiness** (average sample-to-sample change).

Detection then only counts **excess beyond what calibration learned**:
`signal = observed − learned`, `jitter = jump − learned jump`, each clamped at
zero and mapped onto 0–100% with fixed sensitivity scales (150 counts for the
antenna, 250 for the field channel). Classification thresholds operate on the
normalised percentages (15% / 50%), so they are environment-independent.

## Alternatives considered

| Option | Why not |
|---|---|
| Fixed thresholds for both channels | Fragile across rooms and units; the main source of false positives. |
| Manual trim pots | Not digital, not repeatable, not reportable in the logs. |
| Adaptive filters / FFT-based hum rejection | Correct engineering, but significantly beyond the project timeline and untestable without a signal lab. |
| ML classifier on recorded samples | No labelled dataset exists; a capstone cannot ship a model that was never trained. |

## Consequences

- **Positive:** "nothing is hard-coded" — the device zeroes itself to whatever
  state it is in; recalibration is one button press (or `c` in the serial
  monitor); the sampled statistics appear in the serial log for validation.
- **Negative:** calibration is mandatory after power-up and takes ~5 s of
  holding still; results depend on the user holding the device the way it will
  be used while calibrating (documented in the README and the app caption).
- **Neutral:** the 15%/50% decision thresholds can be re-tuned in one place;
  both the firmware and the app's recomputation know them (`EmfReadings.kt`).
