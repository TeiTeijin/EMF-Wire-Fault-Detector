// Host tests for VerdictDebounce (v13 classification smoothing).
// Build: g++ -std=c++11 -Wall -Wextra test_verdict.cpp -o test_verdict.exe
#include "../../firmware/EMF_Wire_Fault_Detector_v13/verdict.h"

#include <cstdio>

static int failures = 0;

#define CHECK(cond, msg)                                                \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL: %s (line %d)\n", msg, __LINE__);         \
            failures++;                                                 \
        }                                                               \
    } while (0)

enum { CLEAR = 0, WIRE = 1, FAULT = 2 };

static void test_single_spike_is_ignored() {
    VerdictDebounce deb(4, 4);
    CHECK(deb.update(CLEAR) == CLEAR, "clear stays clear");
    CHECK(deb.update(WIRE) == CLEAR, "one spike ignored");
    CHECK(deb.update(CLEAR) == CLEAR, "still clear after spike");
    CHECK(deb.current() == CLEAR, "current is clear");
}

static void test_rises_after_four_consecutive_samples() {
    VerdictDebounce deb(4, 4);
    deb.update(CLEAR);
    CHECK(deb.update(WIRE) == CLEAR, "1st wire sample ignored");
    CHECK(deb.update(WIRE) == CLEAR, "2nd wire sample ignored");
    CHECK(deb.update(WIRE) == CLEAR, "3rd wire sample ignored");
    CHECK(deb.update(WIRE) == WIRE, "4th wire sample commits");
    CHECK(deb.current() == WIRE, "current is wire");
}

static void test_falls_after_four_consecutive_clear_samples() {
    VerdictDebounce deb(4, 4);
    for (int i = 0; i < 4; i++) deb.update(WIRE);
    CHECK(deb.current() == WIRE, "precondition: wire");
    CHECK(deb.update(CLEAR) == WIRE, "1st clear sample held");
    CHECK(deb.update(CLEAR) == WIRE, "2nd clear sample held");
    CHECK(deb.update(CLEAR) == WIRE, "3rd clear sample held");
    CHECK(deb.update(CLEAR) == CLEAR, "4th clear sample commits");
    CHECK(deb.current() == CLEAR, "current is clear");
}

static void test_fault_rises_with_same_threshold() {
    VerdictDebounce deb(4, 4);
    deb.update(CLEAR);
    deb.update(FAULT);
    deb.update(FAULT);
    CHECK(deb.update(FAULT) == CLEAR, "3rd fault sample ignored");
    CHECK(deb.update(FAULT) == FAULT, "4th fault sample commits");
}

// Regression for the review finding: alternating non-current raws must
// never commit — a verdict has to be seen for N *consecutive* updates.
static void test_alternating_noncurrent_raws_never_commit() {
    VerdictDebounce deb(4, 4);
    CHECK(deb.update(CLEAR) == CLEAR, "base clear");
    CHECK(deb.update(WIRE) == CLEAR, "wire sample held");
    CHECK(deb.update(FAULT) == CLEAR, "fault sample held");
    CHECK(deb.update(WIRE) == CLEAR, "wire sample held");
    CHECK(deb.update(FAULT) == CLEAR, "no 4 consecutive non-current samples - held");
    CHECK(deb.current() == CLEAR, "still clear after alternation");
    // And a fall-side version: current FAULT, alternating raws.
    VerdictDebounce deb2(4, 4);
    for (int i = 0; i < 4; i++) deb2.update(FAULT);
    CHECK(deb2.current() == FAULT, "precondition: fault");
    CHECK(deb2.update(WIRE) == FAULT, "wire held");
    CHECK(deb2.update(CLEAR) == FAULT, "clear held");
    CHECK(deb2.update(WIRE) == FAULT, "wire held");
    CHECK(deb2.update(CLEAR) == FAULT, "no 4 consecutive clear samples - held");
    CHECK(deb2.current() == FAULT, "still fault after alternation");
}

int main() {
    test_single_spike_is_ignored();
    test_rises_after_four_consecutive_samples();
    test_falls_after_four_consecutive_clear_samples();
    test_fault_rises_with_same_threshold();
    test_alternating_noncurrent_raws_never_commit();
    if (failures == 0) {
        std::printf("test_verdict: all tests passed\n");
        return 0;
    }
    std::printf("test_verdict: %d check(s) failed\n", failures);
    return 1;
}
