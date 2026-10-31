#pragma once

#include "presshold.h"

// ButtonPoller - high-rate button sampling with debounce + press/hold
// classification.
//
// Pure logic (no Arduino dependencies) so it can be tested on the host.
// The caller feeds RAW pin state at a fast cadence (the firmware polls
// inside the 40 ms measurement window, so a short tap can never fall
// between samples). The 50 ms debounce commits the raw state to a stable
// state, which feeds the PressHold classifier. Events (SHORT_PRESS /
// LONG_HOLD) are latched and returned once by takeEvent().

class ButtonPoller {
public:
  static const unsigned long DEBOUNCE_MILLIS = 50UL;

  enum Event { NONE = 0, SHORT_PRESS = 1, LONG_HOLD = 2 };

  ButtonPoller() : lastRaw_(false), stable_(false), lastChangeMs_(0) {}

  // Feed one raw sample (true = pressed). Call this often.
  void feedRaw(bool pressed, unsigned long nowMs) {
    if (pressed != lastRaw_) {
      lastChangeMs_ = nowMs;      // raw reading changed, restart the timer
      lastRaw_ = pressed;
    }
    if ((nowMs - lastChangeMs_) > DEBOUNCE_MILLIS && pressed != stable_) {
      stable_ = pressed;
    }
    ph_.update(stable_, nowMs);
  }

  // Returns a latched event exactly once, then NONE.
  Event takeEvent() {
    return static_cast<Event>(ph_.takeEvent());
  }

private:
  bool lastRaw_;
  bool stable_;
  unsigned long lastChangeMs_;
  PressHold ph_;
};
