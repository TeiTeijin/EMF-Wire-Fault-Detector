// Host tests for the v14 verdict classifier (movement gate).
// Build: g++ -std=c++11 -Wall -Wextra test_classify.cpp -o test_classify.exe
#include "../../firmware/EMF_Wire_Fault_Detector_v14/classify.h"

#include <cstdio>

static int failures = 0;

#define CHECK(cond, msg)                                                \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL: %s (line %d)\n", msg, __LINE__);         \
            failures++;                                                 \
        }                                                               \
    } while (0)

static void test_clear_below_signal_threshold() {
    CHECK(classifyVerdict(0, 100, false, 15, 50) == ALERT_CLEAR,
          "no signal is clear even with high jitter");
    CHECK(classifyVerdict(14, 100, false, 15, 50) == ALERT_CLEAR,
          "14% is below the 15% threshold");
}

static void test_steady_signal_is_wire() {
    CHECK(classifyVerdict(15, 0, false, 15, 50) == ALERT_WIRE,
          "15% steady signal is wire");
    CHECK(classifyVerdict(80, 49, false, 15, 50) == ALERT_WIRE,
          "jitter just below threshold is wire");
}

static void test_erratic_signal_is_fault() {
    CHECK(classifyVerdict(20, 50, false, 15, 50) == ALERT_FAULT,
          "jitter at threshold is fault");
    CHECK(classifyVerdict(100, 100, false, 15, 50) == ALERT_FAULT,
          "full-scale erratic signal is fault");
}

static void test_motion_suppresses_wire_and_fault() {
    CHECK(classifyVerdict(100, 100, true, 15, 50) == ALERT_CLEAR,
          "movement is not a wire");
    CHECK(classifyVerdict(20, 0, true, 15, 50) == ALERT_CLEAR,
          "movement with steady signal is still suppressed");
}

int main() {
    test_clear_below_signal_threshold();
    test_steady_signal_is_wire();
    test_erratic_signal_is_fault();
    test_motion_suppresses_wire_and_fault();
    if (failures == 0) {
        std::printf("test_classify: all tests passed\n");
        return 0;
    }
    std::printf("test_classify: %d check(s) failed\n", failures);
    return 1;
}
