#pragma once

// EdgeButtonPoller - interrupt-driven press/hold button classifier.
//
// Pure logic (no Arduino dependencies) so it can be tested on the host.
// A timer ISR calls tick() at 1 kHz with the raw level, so a press is
// seen the instant it happens even while the main loop is busy with
// I2C / display / serial. Classification:
//   - held >= HOLD_MICROS       -> LONG_HOLD, latched AT the 2 s mark
//     while the button is still down (the pairing code appears without
//     waiting for a release); its later release fires nothing;
//   - released after >= DEBOUNCE_MICROS but before the mark
//                                -> SHORT_PRESS (latched once);
//   - shorter                    -> ignored (contact bounce / noise).
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
      : lastRaw_(false), lastEdgeMicros_(0), pressStartMicros_(0), held_(false), latched_(NONE) {}

  // Call from the timer ISR on the current raw level (true = pressed).
  void tick(bool pressed, unsigned long nowMicros) {
    // Edge processing.
    if (pressed != lastRaw_) {
      if ((nowMicros - lastEdgeMicros_) >= BOUNCE_MICROS) {
        lastEdgeMicros_ = nowMicros;
        if (pressed) {
          pressStartMicros_ = nowMicros;   // press down
          held_ = false;
        } else if (!held_) {
          // Release before the hold mark.
          unsigned long dur = nowMicros - pressStartMicros_;
          if (dur >= DEBOUNCE_MICROS && latched_ == NONE) latched_ = SHORT_PRESS;
          // shorter than the debounce: bounce or noise, ignore
        }
        // release after the hold mark: LONG_HOLD already latched, nothing
      }
      lastRaw_ = pressed;                  // chatter: tracked but not processed
    }

    // Hold timeout: fires at the 2 s mark while the button is still down.
    if (pressed && !held_ && (nowMicros - pressStartMicros_) >= HOLD_MICROS) {
      held_ = true;
      if (latched_ == NONE) latched_ = LONG_HOLD;
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
  bool held_;
  Event latched_;
};
