#pragma once

// VerdictDebounce - smooths the Clear / Wire / Fault classification.
//
// Pure logic (no Arduino dependencies) so it can be tested on the host.
// A brief spike (walking past a door frame, a twitch while moving)
// must not flip the verdict: a new verdict only commits after it has
// been seen for `riseSamples` consecutive updates when it is more
// severe than the current one, or `fallSamples` when less severe.

class VerdictDebounce {
public:
  VerdictDebounce(int riseSamples, int fallSamples)
      : rise_(riseSamples), fall_(fallSamples), current_(0), pending_(0), count_(0) {}

  // Feed the raw verdict for this sample (0 = clear, 1 = wire, 2 = fault).
  // Returns the debounced verdict to act on.
  int update(int raw) {
    if (raw == current_) {
      count_ = 0;
      pending_ = current_;
      return current_;
    }
    if (raw != pending_) {
      pending_ = raw;    // a different candidate: start counting it from scratch
      count_ = 1;
      return current_;
    }
    count_++;
    bool rising = pending_ > current_;
    int need = rising ? rise_ : fall_;
    if (count_ >= need) {
      current_ = pending_;
      count_ = 0;
      pending_ = current_;
    }
    return current_;
  }

  int current() const { return current_; }

  void resetTo(int raw) {
    current_ = raw;
    pending_ = raw;
    count_ = 0;
  }

private:
  int rise_, fall_;
  int current_;
  int pending_;   // the candidate verdict currently being counted
  int count_;
};
