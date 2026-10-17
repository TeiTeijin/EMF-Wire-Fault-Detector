# ADR-0001: Bluetooth Classic SPP for the phone link

- **Status:** Accepted
- **Date:** 2026-10-17
- **Deciders:** Project team (Systems Architect, Embedded Developer, Middleware Developer)

## Context

The phone app must receive a continuous text stream (~2 lines/s) from a
battery-powered handheld sensor, used in the field where there is no Wi-Fi or
mobile data to depend on. The device itself is built on an Arduino Uno
(ADR-0002), so a separate radio node carries the data to the phone. The
interface must be simple enough to also debug with a generic terminal app.

## Decision

Use **Bluetooth Classic, Serial Port Profile (RFCOMM)** as the single
integration interface, implemented by the ESP32 relay and consumed by the
Android app. The relay advertises a stable name and accepts one bonded peer at
a time; every device report is forwarded as one newline-terminated ASCII line
(see `docs/api-specification.md`).

## Alternatives considered

| Option | Why not |
|---|---|
| BLE GATT notifications | Client/server attribute ceremony, MTU and chunking handling, notification subscription state machine — all code with no payoff for a 30-byte line stream; generic terminal apps are also less useful over BLE. |
| Wi-Fi HTTP / WebSocket | Needs an access point or phone hotspot; a TCP server introduces an IP stack, DHCP and timeout handling; power draw is far above Bluetooth for the same bits. |
| Wi-Fi MQTT to a broker | Introduces an external dependency for a device that must work offline. |
| USB OTG cable to the phone | Cables defeat handheld use; limited phone compatibility. |

## Consequences

- **Positive:** the stream is trivially inspectable with any Bluetooth serial
  terminal app; pairing is handled by the OS bond table; the same interface
  worked across the whole project history (Reader 1–8 → v11) without redesign.
- **Negative:** Bluetooth Classic pairing is a prerequisite (system settings,
  PIN 1234); RFCOMM has no message framing, so the protocol must be line-based
  and defensive against partial lines and noise; a fixed PIN is weak
  authentication (documented as such).
- **Neutral:** one peer at a time is a relay design choice, not a limitation
  of SPP.
