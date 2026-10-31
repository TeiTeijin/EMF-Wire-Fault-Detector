#pragma once

// EdgeButtonPoller - interrupt-driven press/hold button classifier.
//
// Pure logic (no Arduino dependencies) so it can be tested on the host.
// The pin-change ISR calls edge() on EVERY electrical transition, so a
// press is seen the instant it happens even while the main loop is busy
// with I2C / display / serial. Classification happens on the release
// edge (no sampling gaps can swallow a tap):
//   - held >= HOLD_MICROS      -> LONG_HOLD (latched once; no SHORT)
//   - held >= DEBOUNCE_MICROS  -> SHORT_PRESS (latched once)
//   - shorter                  -> ignored (contact bounce / noise)
// Chatter (edges closer than BOUNCE_MICROS) is ignored and does not
// restart the press timer. All times are unsigned long micros(); every
// elapsed-time check is wrap-safe.

class EdgeButtonPoller {
public:
  static const unsigned long BOUNCE_MICROS = 10000UL;
  static const unsigned long DEBOUNCE_MICROS = 50000UL;
  static const unsigned long HOLD_MICROS = 2000000UL;

  enum Event { NONE = 0, SHORT_PRESS = 1, LONG_HOLD = 2 };

  EdgeButtonPoller()
      : lastRaw_(false), lastEdgeMicros_(0), pressStartMicros_(0), latched_(NONE) {}

  // Call from the pin-change ISR on the current raw level (true = pressed).
  void edge(bool pressed, unsigned long nowMicros) {
    if (pressed == lastRaw_) return;                  // not an edge
    if ((nowMicros - lastEdgeMicros_) < BOUNCE_MICROS) {
      lastRaw_ = pressed;                             // chatter: track but ignore
      return;
    }
    lastEdgeMicros_ = nowMicros;
    lastRaw_ = pressed;

    if (pressed) {
      pressStartMicros_ = nowMicros;                  // press down
    } else {
      unsigned long dur = nowMicros - pressStartMicros_;
      if (dur >= HOLD_MICROS) {
        if (latched_ == NONE) latched_ = LONG_HOLD;
      } else if (dur >= DEBOUNCE_MICROS) {
        if (latched_ == NONE) latched_ = SHORT_PRESS;
      }
      // shorter than the debounce: bounce or noise, ignore
    }
  }

  // Returns a latched event exactly once, then NONE.
  Event takeEvent() {
    Event e = latched_;
    latched_ = NONE;
    return e;
  }

private:
  bool lastRaw_;
  unsigned long lastEdgeMicros_;
  unsigned long pressStartMicros_;
  Event latched_;
};
