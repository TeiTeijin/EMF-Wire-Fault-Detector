#pragma once

// Verdict classification, kept pure so it can be host-tested.
//
// Signal is the headline 0-100% change. Jitter is the erratic part of the
// signal. A hand-held detector also has to survive its own movement: while
// the device is being moved/rotated the magnetometer sees large changes that
// are not a wire. The caller flags that condition (motionRecent) and the
// verdict is held Clear until the device has been still long enough for the
// zero-point follower to catch up.

enum AlertLevel { ALERT_CLEAR = 0, ALERT_WIRE = 1, ALERT_FAULT = 2 };

inline AlertLevel classifyVerdict(int signal, int jitterPercent,
                                  bool motionRecent, int lowThreshold,
                                  int faultThreshold) {
  if (signal < lowThreshold) return ALERT_CLEAR;
  if (motionRecent) return ALERT_CLEAR;  // moving, not a wire
  if (jitterPercent >= faultThreshold) return ALERT_FAULT;
  return ALERT_WIRE;
}
