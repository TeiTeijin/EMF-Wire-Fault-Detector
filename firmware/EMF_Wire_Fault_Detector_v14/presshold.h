#pragma once

// PressHold - one-action-per-press button classifier.
//
// Pure logic (no Arduino dependencies) so it can be tested on the host.
// Feed it the debounced button state (true = pressed) and read back
// events with takeEvent():
//   - a press released before HOLD_MILLIS fires SHORT_PRESS on release;
//   - a press still down at the HOLD_MILLIS mark fires LONG_HOLD once,
//     at the mark, and its later release fires nothing;
//   - a held press never also produces a SHORT_PRESS.
// Time is an unsigned long millis() timestamp; all elapsed-time checks
// are wrap-safe.

class PressHold {
public:
  static const unsigned long HOLD_MILLIS = 2000UL;
  enum Event { NONE = 0, SHORT_PRESS = 1, LONG_HOLD = 2 };

  PressHold() : state_(IDLE), pressStartMs_(0), event_(NONE) {}

  void update(bool pressed, unsigned long nowMs) {
    switch (state_) {
      case IDLE:
        if (pressed) {
          state_ = PRESSED;
          pressStartMs_ = nowMs;
        }
        break;

      case PRESSED:
        if (!pressed) {
          state_ = IDLE;                            // quick press
          if (event_ == NONE) event_ = SHORT_PRESS;
        } else if ((nowMs - pressStartMs_) >= HOLD_MILLIS) {
          state_ = HELD;                            // fires once, at the mark
          if (event_ == NONE) event_ = LONG_HOLD;
        }
        break;

      case HELD:
        if (!pressed) state_ = IDLE;   // release after hold: no SHORT_PRESS
        break;
    }
  }

  Event takeEvent() {
    Event e = event_;
    event_ = NONE;
    return e;
  }

private:
  enum State { IDLE, PRESSED, HELD };
  State state_;
  unsigned long pressStartMs_;
  Event event_;
};
