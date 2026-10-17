# ADR-0002: Two-board edge design — Arduino Uno sensing node + ESP32 relay

- **Status:** Accepted
- **Date:** 2026-10-17
- **Deciders:** Project team (Embedded Developer, Systems Architect)

## Context

The sensor front end (LM358 amplifier stage on 5 V, QMC5883P, OLED, buzzer,
button) was already working on an Arduino Uno. The phone link needs Bluetooth
Classic (ADR-0001), which the Uno does not provide. The realistic choices were
to move everything onto one ESP32, or to keep the Uno and add a small radio
board.

## Decision

Keep the **Uno as the sensing and classification node** and add an **ESP32 as
a dedicated communications relay**, linked by UART at 9600 baud:

- Uno `D3` (5 V TX) → voltage divider → ESP32 `GPIO16` (RX2)
- ESP32 `GPIO17` (TX2, 3.3 V) → Uno `D2` (RX), no divider needed
- Common ground is required; the ESP32 may be powered from a separate source
  (e.g. a power bank).

The relay is deliberately dumb: it forwards complete lines and mirrors them on
its USB serial; all sensing logic stays on the Uno.

## Alternatives considered

| Option | Why not |
|---|---|
| Single ESP32 for everything | Requires rewriting all 5 V sensor I/O for 3.3 V, re-validating the analog front end on a different ADC, and re-testing a working sensing stack mid-project. |
| Uno + HC-05 Bluetooth module | Works, but the HC-05 adds the same level-shifting need, has no spare USB debug channel alongside the radio, and no headroom for future extensions (the ESP32 gives us a second UART and Wi-Fi if ever needed). |
| Uno + ESP32 on I2C instead of UART | I2C is already carrying the magnetometer and OLED; a second bus master protocol on the same pins adds risk for no benefit. |

## Consequences

- **Positive:** each board can be flashed and debugged independently; both
  sides have a serial monitor; the working analog design was preserved.
- **Negative:** two power supplies and a mandatory common ground; the 5 V→3.3 V
  divider on Uno TX is a wiring error hotspot and is called out in the README
  and in the app's `SILENT` failure message.
- **Neutral:** at 9600 baud the link has ~30× headroom over the 500 ms report
  cadence, so the UART is never the bottleneck.
