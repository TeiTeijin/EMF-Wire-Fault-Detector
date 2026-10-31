// Host tests for PressHold (v14 button press/hold classifier logic).
// Build: g++ -std=c++11 -Wall -Wextra test_presshold.cpp -o test_presshold.exe
#include "../../firmware/EMF_Wire_Fault_Detector_v14/presshold.h"

#include <cstdio>

static int failures = 0;

#define CHECK(cond, msg)                                                \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL: %s (line %d)\n", msg, __LINE__);         \
            failures++;                                                 \
        }                                                               \
    } while (0)

static void test_no_event_while_idle_or_initial_not_pressed() {
    PressHold ph;
    CHECK(ph.takeEvent() == PressHold::NONE, "no event before any update");
    ph.update(false, 100);
    ph.update(false, 200);
    ph.update(false, 5000);
    CHECK(ph.takeEvent() == PressHold::NONE, "no event from idle updates");
}

static void test_short_press_fires_once_on_release() {
    PressHold ph;
    ph.update(true, 1000);
    CHECK(ph.takeEvent() == PressHold::NONE, "no event while still held");
    ph.update(true, 1500);
    ph.update(false, 1600);   // released after 600 ms: short press
    CHECK(ph.takeEvent() == PressHold::SHORT_PRESS, "SHORT_PRESS on release");
    CHECK(ph.takeEvent() == PressHold::NONE, "event returned exactly once");
    ph.update(false, 2000);
    CHECK(ph.takeEvent() == PressHold::NONE, "no ghost event afterwards");
}

static void test_long_hold_fires_once_at_2s_and_no_short_press_on_release() {
    PressHold ph;
    ph.update(true, 0);
    ph.update(true, 1000);
    ph.update(true, 1999);
    CHECK(ph.takeEvent() == PressHold::NONE, "no LONG_HOLD before the 2 s mark");
    ph.update(true, 2000);
    CHECK(ph.takeEvent() == PressHold::LONG_HOLD, "LONG_HOLD at the 2 s mark");
    CHECK(ph.takeEvent() == PressHold::NONE, "LONG_HOLD returned exactly once");
    ph.update(true, 3000);
    ph.update(true, 5000);
    CHECK(ph.takeEvent() == PressHold::NONE, "no repeat while still held");
    ph.update(false, 6000);
    CHECK(ph.takeEvent() == PressHold::NONE, "no SHORT_PRESS on release after hold");
    ph.update(false, 7000);
    CHECK(ph.takeEvent() == PressHold::NONE, "no ghost event after hold release");
}

static void test_repeated_short_presses() {
    PressHold ph;
    for (int i = 0; i < 3; i++) {
        ph.update(true, (unsigned long)i * 1000);
        ph.update(false, (unsigned long)i * 1000 + 500);
        CHECK(ph.takeEvent() == PressHold::SHORT_PRESS, "SHORT_PRESS on each quick press");
        CHECK(ph.takeEvent() == PressHold::NONE, "exactly one event per press");
    }
}

static void test_short_press_then_long_hold() {
    PressHold ph;
    ph.update(true, 0);
    ph.update(false, 400);
    CHECK(ph.takeEvent() == PressHold::SHORT_PRESS, "first quick press is SHORT_PRESS");
    ph.update(true, 1000);
    ph.update(true, 3000);
    CHECK(ph.takeEvent() == PressHold::LONG_HOLD, "second press holds to LONG_HOLD");
    ph.update(false, 3400);
    CHECK(ph.takeEvent() == PressHold::NONE, "hold release adds no SHORT_PRESS");
}

static void test_millis_wraparound_is_safe() {
    PressHold ph;
    ph.update(true, 0xFFFFFF00UL);          // pressed near millis() wrap
    ph.update(true, 0xFFFFFF00UL + 1999);   // still short of the 2 s mark
    CHECK(ph.takeEvent() == PressHold::NONE, "no event before 2 s across wrap");
    ph.update(true, 0xFFFFFF00UL + 2000);   // 2 s mark (wraps past 0)
    CHECK(ph.takeEvent() == PressHold::LONG_HOLD, "LONG_HOLD fires across millis wrap");
    ph.update(false, 0xFFFFFF00UL + 2500);
    CHECK(ph.takeEvent() == PressHold::NONE, "wrap-safe release has no SHORT_PRESS");
}

int main() {
    test_no_event_while_idle_or_initial_not_pressed();
    test_short_press_fires_once_on_release();
    test_long_hold_fires_once_at_2s_and_no_short_press_on_release();
    test_repeated_short_presses();
    test_short_press_then_long_hold();
    test_millis_wraparound_is_safe();
    if (failures == 0) {
        std::printf("test_presshold: all tests passed\n");
        return 0;
    }
    std::printf("test_presshold: %d check(s) failed\n", failures);
    return 1;
}
