#pragma once

// EmfAutoCal - automatic hard-iron calibration for the QMC5883P.
//
// Pure logic (no Arduino dependencies) so it can be tested on the host.
// The device learns its own fixed magnetism (hard-iron offsets) from
// normal handling instead of a manual "turn in all directions" ritual:
//   - tracks min/max of raw X/Y/Z while the device is used,
//   - locks offsets once ALL THREE axes have swept at least MIN_SWEEP
//     counts (requiring all three avoids the planar-spin contamination
//     that made earlier versions' offsets location-locked),
//   - keeps refining in RAM as new extremes arrive, and reports a
//     "save" (return true) only when the midpoint drifted by at least
//     SAVE_MID_DRIFT and at least SAVE_MIN_INTERVAL_MS has passed, so
//     the caller can throttle EEPROM writes.
//   - ignores exact repeat triplets (a frozen chip must not count).

class EmfAutoCal {
public:
  static const int MIN_SWEEP = 600;                       // counts per axis
  static const int SAVE_MID_DRIFT = 25;                   // counts
  static const unsigned long SAVE_MIN_INTERVAL_MS = 10000UL;

  EmfAutoCal() { reset(); }

  void reset() {
    ready_ = false;
    frozen_ = false;
    offX_ = offY_ = offZ_ = 0;
    savedOffX_ = savedOffY_ = savedOffZ_ = 0;
    lastSaveMs_ = 0;
    beginTracking();
  }

  // Start from previously learned offsets (EEPROM restore); refinement
  // continues from here as new extremes arrive.
  void loadFrom(int ox, int oy, int oz) {
    offX_ = ox; offY_ = oy; offZ_ = oz;
    savedOffX_ = ox; savedOffY_ = oy; savedOffZ_ = oz;
    ready_ = true;
    frozen_ = false;
    lastSaveMs_ = 0;
    beginTracking();
  }

  // Feed one fresh raw reading. Returns true when the caller should
  // persist the offsets (first lock, or a refined save).
  bool update(int x, int y, int z, unsigned long nowMs) {
    // Once the offsets are locked, STOP learning. Continuous refinement
    // re-centers the midpoint on every new extreme and, during ordinary
    // handling (small asymmetric sweeps), slowly cancels the ambient
    // field until the magnitude reads ~0 and the channel goes blind.
    if (ready_) return false;
    if (frozen_ && x == lastX_ && y == lastY_ && z == lastZ_) return false;
    lastX_ = x; lastY_ = y; lastZ_ = z;
    frozen_ = true;

    if (x < minX_) minX_ = x;
    if (x > maxX_) maxX_ = x;
    if (y < minY_) minY_ = y;
    if (y > maxY_) maxY_ = y;
    if (z < minZ_) minZ_ = z;
    if (z > maxZ_) maxZ_ = z;

    bool needSave = false;
    if (!ready_) {
      if (sweepX() >= MIN_SWEEP && sweepY() >= MIN_SWEEP && sweepZ() >= MIN_SWEEP) {
        ready_ = true;
        recomputeOffsets();
        needSave = true;   // first lock always saves
      }
    } else {
      recomputeOffsets();
      if (driftExceeds(offX_, offY_, offZ_)
          && (nowMs - lastSaveMs_) >= SAVE_MIN_INTERVAL_MS) {
        needSave = true;
      }
    }

    if (needSave) {
      savedOffX_ = offX_; savedOffY_ = offY_; savedOffZ_ = offZ_;
      lastSaveMs_ = nowMs;
      return true;
    }
    return false;
  }

  bool ready() const { return ready_; }
  int offsetX() const { return offX_; }
  int offsetY() const { return offY_; }
  int offsetZ() const { return offZ_; }
  int sweepX() const { int s = maxX_ - minX_; return s < 0 ? 0 : s; }
  int sweepY() const { int s = maxY_ - minY_; return s < 0 ? 0 : s; }
  int sweepZ() const { int s = maxZ_ - minZ_; return s < 0 ? 0 : s; }

private:
  int minX_, maxX_, minY_, maxY_, minZ_, maxZ_;
  int offX_, offY_, offZ_;
  int savedOffX_, savedOffY_, savedOffZ_;
  bool ready_;
  bool frozen_;
  int lastX_, lastY_, lastZ_;
  unsigned long lastSaveMs_;

  void beginTracking() {
    minX_ = minY_ = minZ_ = 32767;
    maxX_ = maxY_ = maxZ_ = -32768;
  }

  void recomputeOffsets() {
    offX_ = (int)(((long)minX_ + maxX_) / 2);
    offY_ = (int)(((long)minY_ + maxY_) / 2);
    offZ_ = (int)(((long)minZ_ + maxZ_) / 2);
  }

  bool driftExceeds(int ox, int oy, int oz) const {
    long dx = (long)ox - savedOffX_; if (dx < 0) dx = -dx;
    long dy = (long)oy - savedOffY_; if (dy < 0) dy = -dy;
    long dz = (long)oz - savedOffZ_; if (dz < 0) dz = -dz;
    return dx >= SAVE_MID_DRIFT || dy >= SAVE_MID_DRIFT || dz >= SAVE_MID_DRIFT;
  }
};
