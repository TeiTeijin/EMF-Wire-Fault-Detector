// Host tests for ButtonPoller (debounce + press/hold classifier fed at a
// fast sample rate, so short taps can't fall between loop samples).
// Build: g++ -std=c++11 -Wall -Wextra test_buttonpoll.cpp -o test_buttonpoll.exe
#include "../../firmware/EMF_Wire_Fault_Detector_v14/buttonpoll.h"

#include <cstdio>

static int failures = 0;

#define CHECK(cond, msg)                                                \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL: %s (line %d)\n", msg, __LINE__);         \
            failures++;                                                 \
        }                                                               \
    } while (0)

// Feed raw samples at the given cadence from tStart to tEnd.
static ButtonPoller::Event pollWindow(ButtonPoller &bp, bool pressed,
                                      unsigned long tStart, unsigned long tEnd,
                                      unsigned long step) {
    ButtonPoller::Event last = ButtonPoller::NONE;
    for (unsigned long t = tStart; t <= tEnd; t += step) {
        bp.feedRaw(pressed, t);
        ButtonPoller::Event e = bp.takeEvent();
        if (e != ButtonPoller::NONE) last = e;
    }
    return last;
}

static void test_quick_tap_at_40ms_cadence_fires_short_press() {
    ButtonPoller bp;
    pollWindow(bp, false, 0, 40, 40);                 // idle
    pollWindow(bp, true, 80, 200, 40);                // ~120 ms tap
    ButtonPoller::Event e = pollWindow(bp, false, 240, 400, 40);   // release
    CHECK(e == ButtonPoller::SHORT_PRESS, "quick tap -> SHORT_PRESS");
}

static void test_hold_fires_long_hold_once_and_no_short_on_release() {
    ButtonPoller bp;
    pollWindow(bp, false, 0, 40, 40);
    ButtonPoller::Event during = pollWindow(bp, true, 80, 2500, 40);  // > 2 s hold
    CHECK(during == ButtonPoller::LONG_HOLD, "2 s hold -> LONG_HOLD (at the mark)");
    ButtonPoller::Event release = pollWindow(bp, false, 2540, 3000, 40);
    CHECK(release == ButtonPoller::NONE, "no SHORT_PRESS after a hold");
}

static void test_bouncing_press_never_fires() {
    ButtonPoller bp;
    ButtonPoller::Event last = ButtonPoller::NONE;
    for (unsigned long t = 0; t <= 1000; t += 10) {
        bool raw = ((t / 10) % 2) == 0;   // toggles every 10 ms, never stable
        bp.feedRaw(raw, t);
        ButtonPoller::Event e = bp.takeEvent();
        if (e != ButtonPoller::NONE) last = e;
    }
    CHECK(last == ButtonPoller::NONE, "bouncing contact -> no event");
}

static void test_sub_debounce_tap_is_rejected() {
    ButtonPoller bp;
    pollWindow(bp, false, 0, 40, 40);
    pollWindow(bp, true, 80, 120, 40);    // only ~40 ms down
    ButtonPoller::Event e = pollWindow(bp, false, 160, 400, 40);
    CHECK(e == ButtonPoller::NONE, "shorter than the debounce -> no event");
}

int main() {
    test_quick_tap_at_40ms_cadence_fires_short_press();
    test_hold_fires_long_hold_once_and_no_short_on_release();
    test_bouncing_press_never_fires();
    test_sub_debounce_tap_is_rejected();
    if (failures == 0) {
        std::printf("test_buttonpoll: all tests passed\n");
        return 0;
    }
    std::printf("test_buttonpoll: %d check(s) failed\n", failures);
    return 1;
}
