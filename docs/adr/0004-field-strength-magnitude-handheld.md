# ADR-0004: Field-strength magnitude and turn calibration for handheld use

- **Status:** Accepted
- **Date:** 2026-10-17
- **Deciders:** Project team (Embedded Developer, Systems Architect)

## Context

A magnetometer reads the sum of three fields: the Earth's field, the field of
the wire being sought, and the device's own fixed fields (steel hardware, the
buzzer's magnet). The Earth's field does not vanish when the device tilts — it
merely redistributes across the X/Y/Z axes. With raw per-axis readings, simply
rotating the detector produces swings far larger than the wire's contribution,
which makes a handheld device unusable.

## Decision

Two mechanisms, both implemented in firmware v11:

1. **Use the field magnitude, not the axes.** The firmware computes
   `|B| = sqrt((x−offX)² + (y−offY)² + (z−offZ)²)`. The Earth's field has the
   same magnitude in every direction, so tilting changes nothing; a wire
   carrying current adds to the magnitude regardless of orientation.
2. **Learn the board's own field by turning it.** On first power-up (or when
   the button is held during boot, or `t` in the serial monitor), the user
   turns the device slowly in all directions for 8 seconds. The midpoint of
   each axis's min/max sweep is the board's fixed field, subtracted as
   `offX/offY/offZ` above. An axis is only trusted (and the set saved to
   EEPROM — marker `0xA5`) if the sweep spanned at least 600 counts and at
   least two axes were really swept.

To keep the device usable while walking, the **zero point slowly follows**
the field magnitude (time constant 15 s while Clear, 120 s while detecting):
moving around re-zeroes itself, while quickly moving toward a wire shows up as
a rise and a real detection does not fade away at once.

## Alternatives considered

| Option | Why not |
|---|---|
| Raw per-axis thresholds | Tilt is a larger signal than the wire; unusable handheld. |
| Fixed-orientation operation (keep the board level) | Defeats the handheld premise of the project. |
| Accelerometer-based tilt compensation | Requires an extra sensor and its own calibration; the magnitude trick achieves the same goal with zero extra hardware. |
| Let the user hand-tune axis offsets | Not reproducible; the turning calibration derives them in one guided step instead. |

## Consequences

- **Positive:** tilting and turning no longer register as a signal (validated
  in RT/VF tests); the offsets persist in EEPROM so the turning step happens
  once per board assembly.
- **Negative:** the first-run experience includes an 8 s guided rotation; a
  genuinely wrong board-field estimate from a bad sweep is rejected and asked
  again next boot rather than silently saved.
- **Neutral:** the zero-follow trade-off is explicit: a very slow approach
  toward a steady wire is intentionally de-sensitised.
