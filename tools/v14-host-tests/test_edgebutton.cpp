// Host tests for EdgeButtonPoller (interrupt-driven button classifier).
// The ISR calls edge() on every electrical transition, so presses are
// seen even while the main loop is busy; classification happens on the
// release edge. Build: g++ -std=c++11 -Wall -Wextra test_edgebutton.cpp -o test_edgebutton.exe
#include "../../firmware/EMF_Wire_Fault_Detector_v14/edgebutton.h"

#include <cstdio>

static int failures = 0;

#define CHECK(cond, msg)                                                \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL: %s (line %d)\n", msg, __LINE__);         \
            failures++;                                                 \
        }                                                               \
    } while (0)

static void test_quick_press_fires_short_on_release() {
    EdgeButtonPoller eb;
    eb.tick(true, 0);
    eb.tick(false, 100000);   // 100 ms press
    CHECK(eb.takeEvent() == EdgeButtonPoller::SHORT_PRESS, "100 ms press -> SHORT_PRESS");
    CHECK(eb.takeEvent() == EdgeButtonPoller::NONE, "event fires once");
}

static void test_long_hold_fires_long_hold_at_the_mark_no_short_on_release() {
    EdgeButtonPoller eb;
    eb.tick(true, 0);            // press down
    eb.tick(true, 1000000);      // still held at 1 s
    CHECK(eb.takeEvent() == EdgeButtonPoller::NONE, "nothing at 1 s");
    eb.tick(true, 2000000);      // 2 s mark while still held
    CHECK(eb.takeEvent() == EdgeButtonPoller::LONG_HOLD, "LONG_HOLD fires at the 2 s mark");
    eb.tick(false, 2500000);     // release after the hold
    CHECK(eb.takeEvent() == EdgeButtonPoller::NONE, "no SHORT_PRESS after a hold");
}

static void test_sub_debounce_press_fires_nothing() {
    EdgeButtonPoller eb;
    eb.tick(true, 0);
    eb.tick(false, 30000);    // 30 ms press
    CHECK(eb.takeEvent() == EdgeButtonPoller::NONE, "30 ms press -> no event");
}

static void test_bounce_chatter_is_ignored_and_press_duration_survives() {
    EdgeButtonPoller eb;
    eb.tick(true, 0);          // real press down
    eb.tick(false, 3000);      // bounce chatter
    eb.tick(true, 4000);       // bounce chatter
    eb.tick(false, 5000);      // bounce chatter
    eb.tick(true, 6000);       // bounce chatter
    eb.tick(false, 100000);    // real release, 100 ms after the first edge
    CHECK(eb.takeEvent() == EdgeButtonPoller::SHORT_PRESS, "bounce ignored, 100 ms press detected");
}

static void test_idle_edges_fire_nothing() {
    EdgeButtonPoller eb;
    eb.tick(true, 0);
    eb.tick(false, 100000);
    eb.takeEvent();
    // No further edges: nothing new.
    CHECK(eb.takeEvent() == EdgeButtonPoller::NONE, "no phantom events");
}

int main() {
    test_quick_press_fires_short_on_release();
    test_long_hold_fires_long_hold_at_the_mark_no_short_on_release();
    test_sub_debounce_press_fires_nothing();
    test_bounce_chatter_is_ignored_and_press_duration_survives();
    test_idle_edges_fire_nothing();
    if (failures == 0) {
        std::printf("test_edgebutton: all tests passed\n");
        return 0;
    }
    std::printf("test_edgebutton: %d check(s) failed\n", failures);
    return 1;
}
