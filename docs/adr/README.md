# Architecture Decision Records

One file per decision, in the standard format (Status / Context / Decision /
Alternatives / Consequences). New decisions are appended with the next number
and never edited after acceptance — a later change gets its own ADR.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-bluetooth-classic-spp-for-phone-link.md) | Bluetooth Classic SPP for the phone link | Superseded by ADR-0009 |
| [0002](0002-two-board-edge-uno-plus-esp32.md) | Two-board edge design: Arduino Uno + ESP32 relay | Accepted |
| [0003](0003-self-calibrating-relative-thresholds.md) | Self-calibrating relative thresholds | Accepted |
| [0004](0004-field-strength-magnitude-handheld.md) | Field-strength magnitude + turn calibration for handheld use | Superseded by ADR-0006 (calibration part) |
| [0005](0005-monorepo-for-all-tiers.md) | One monorepo for firmware, backend, and frontend | Accepted |
| [0006](0006-auto-hard-iron-calibration.md) | Automatic background hard-iron calibration (replaces the turn ritual) | Accepted |
| [0007](0007-dynamic-display-pin-pairing.md) | Dynamic display-PIN pairing for the Bluetooth link | Superseded by ADR-0009 |
| [0008](0008-pairing-window-type-in-pin.md) | Pairing window + type-in PIN (locked by default) | Superseded by ADR-0009 |
| [0009](0009-ble-uart-passkey-pairing.md) | BLE UART (GATT) + static-passkey type-in pairing (replaces the classic SPP link) | Accepted |
