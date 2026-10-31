// Host tests for EmfAutoCal (v13 auto hard-iron calibration logic).
// Build: g++ -std=c++11 -Wall -Wextra test_autocal.cpp -o test_autocal.exe
#include "../../firmware/EMF_Wire_Fault_Detector_v13/autocal.h"

#include <cstdio>
#include <cstdlib>

static int failures = 0;

#define CHECK(cond, msg)                                                \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL: %s (line %d)\n", msg, __LINE__);         \
            failures++;                                                 \
        }                                                               \
    } while (0)

static void test_not_ready_until_all_three_axes_sweep() {
    EmfAutoCal cal;
    // X and Y sweep enough; Z never moves.
    for (int i = 0; i < 9; i++) {
        int x = (i < 5) ? 400 * i : 400 * (8 - i);   // 0..1600..0
        int y = (i < 5) ? 350 * i : 350 * (8 - i);   // 0..1400..0
        bool saved = cal.update(x, y, 30, (unsigned long)i);
        CHECK(!saved, "no save while an axis is un-swept");
    }
    CHECK(!cal.ready(), "not ready when Z never sweeps");
    CHECK(cal.sweepX() >= EmfAutoCal::MIN_SWEEP, "X did sweep");
    CHECK(cal.sweepY() >= EmfAutoCal::MIN_SWEEP, "Y did sweep");
    CHECK(cal.sweepZ() < EmfAutoCal::MIN_SWEEP, "Z did not sweep");
}

static void test_ready_when_all_axes_sweep_with_midpoint_offsets() {
    EmfAutoCal cal;
    CHECK(!cal.update(0, 0, 0, 0), "first point is not a save");
    bool saved = cal.update(-400, -450, -350, 1);
    CHECK(!saved, "not saved before all axes swept");
    saved = cal.update(300, 200, 300, 2);
    CHECK(saved, "saves once all three axes swept");
    CHECK(cal.ready(), "ready after full sweep");
    CHECK(cal.offsetX() == -50, "X midpoint");
    CHECK(cal.offsetY() == -125, "Y midpoint");
    CHECK(cal.offsetZ() == -25, "Z midpoint");
}

static void test_frozen_readings_do_not_count() {
    EmfAutoCal cal;
    for (int i = 0; i < 5; i++) {
        bool saved = cal.update(5, 5, 5, (unsigned long)i);
        CHECK(!saved, "frozen reading never saves");
    }
    CHECK(cal.sweepX() == 0, "frozen X readings add no sweep");
    CHECK(cal.sweepY() == 0, "frozen Y readings add no sweep");
    CHECK(cal.sweepZ() == 0, "frozen Z readings add no sweep");
}

static void test_refinement_gates_on_drift_and_interval() {
    EmfAutoCal cal;
    cal.update(0, 0, 0, 0);
    cal.update(-400, -450, -350, 1);
    cal.update(300, 200, 300, 2);                 // ready, first save
    // New X extreme within the throttle interval: RAM updates, no save.
    bool saved = cal.update(-700, 0, 0, 3);
    CHECK(!saved, "refinement inside save interval does not save");
    CHECK(cal.offsetX() == -200, "X midpoint refined in RAM");
    // Bigger drift again but after the interval: saves.
    saved = cal.update(-720, 0, 0, EmfAutoCal::SAVE_MIN_INTERVAL_MS + 4);
    CHECK(saved, "refinement after interval saves");
    // Small drift (below threshold) never saves even after interval.
    saved = cal.update(-730, 0, 0, 2 * EmfAutoCal::SAVE_MIN_INTERVAL_MS + 8);
    CHECK(!saved, "sub-threshold drift does not save");
    CHECK(cal.offsetX() == -215, "sub-threshold drift still refines in RAM");
}

static void test_loadFrom_starts_ready_and_still_refines() {
    EmfAutoCal cal;
    cal.loadFrom(10, 20, 30);
    CHECK(cal.ready(), "loadFrom starts ready");
    CHECK(cal.offsetX() == 10 && cal.offsetY() == 20 && cal.offsetZ() == 30,
          "loadFrom applies offsets");
    // Expanding extremes refine RAM offsets; first save waits out interval.
    cal.update(-700, 0, 0, 0);
    cal.update(300, 0, 0, 1);   // X sweep 1000 >= 600, mid = -200
    CHECK(cal.offsetX() == -200, "loaded offsets refine from new sweep");
}

static void test_reset_clears_learning() {
    EmfAutoCal cal;
    cal.update(0, 0, 0, 0);
    cal.update(-800, -700, -600, 1);
    cal.update(400, 100, 700, 2);
    CHECK(cal.ready(), "precondition: ready");
    cal.reset();
    CHECK(!cal.ready(), "reset clears ready");
    CHECK(cal.offsetX() == 0 && cal.offsetY() == 0 && cal.offsetZ() == 0,
          "reset zeroes offsets");
    CHECK(cal.sweepX() == 0 && cal.sweepY() == 0 && cal.sweepZ() == 0,
          "reset clears sweeps");
}

int main() {
    test_not_ready_until_all_three_axes_sweep();
    test_ready_when_all_axes_sweep_with_midpoint_offsets();
    test_frozen_readings_do_not_count();
    test_refinement_gates_on_drift_and_interval();
    test_loadFrom_starts_ready_and_still_refines();
    test_reset_clears_learning();
    if (failures == 0) {
        std::printf("test_autocal: all tests passed\n");
        return 0;
    }
    std::printf("test_autocal: %d check(s) failed\n", failures);
    return 1;
}
