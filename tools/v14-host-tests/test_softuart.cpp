// Host tests for SoftUartRx (Timer2-driven software UART receiver).
// The firmware samples the RX line every 32 us (31.25 kHz = 3.255
// samples per 9600-baud bit); these tests feed scripted waveforms with
// exactly that cadence and check the decoded bytes.
// Build: g++ -std=c++11 -Wall -Wextra test_softuart.cpp -o test_softuart.exe
#include "../../firmware/EMF_Wire_Fault_Detector_v14/softuart.h"

#include <cstdio>
#include <cstring>

static int failures = 0;

#define CHECK(cond, msg)                                                \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL: %s (line %d)\n", msg, __LINE__);         \
            failures++;                                                 \
        }                                                               \
    } while (0)

// Build a 32 us-per-sample waveform for one byte (LSB first, start low,
// stop high) and feed it to the receiver.
static void txByte(SoftUartRx &rx, uint8_t b) {
    for (int s = 0; s < 32; s++) {
        int bitIndex = (int)((s * 32UL) / 104UL);  // 9600 baud in 32us units
        bool high;
        if (bitIndex == 0) high = false;             // start bit
        else if (bitIndex <= 8) high = (b >> (bitIndex - 1)) & 1;
        else high = true;                            // stop bit / idle
        rx.sample(high);
    }
}

static void test_single_byte_decodes() {
    SoftUartRx rx;
    txByte(rx, 'P');
    CHECK(rx.available() == 1, "one byte received");
    CHECK(rx.read() == 'P', "byte value correct");
    CHECK(rx.available() == 0, "buffer drained");
}

static void test_full_pin_line_decodes_in_order() {
    SoftUartRx rx;
    const char *line = "PIN,123456\r\n";
    for (const char *p = line; *p; p++) txByte(rx, (uint8_t)*p);
    CHECK(rx.available() == (int)strlen(line), "all bytes buffered");
    for (const char *p = line; *p; p++) {
        CHECK(rx.read() == (uint8_t)*p, "byte order preserved");
    }
    CHECK(rx.available() == 0, "buffer empty after drain");
}

static void test_idle_high_produces_nothing() {
    SoftUartRx rx;
    for (int s = 0; s < 100; s++) rx.sample(true);
    CHECK(rx.available() == 0, "idle line -> no bytes");
}

static void test_framing_error_discarded() {
    SoftUartRx rx;
    // A byte whose stop bit is held LOW (framing error): must be dropped.
    for (int s = 0; s < 32; s++) {
        int bitIndex = (int)((s * 32UL) / 104UL);
        bool high;
        if (bitIndex == 0) high = false;
        else if (bitIndex <= 8) high = (0x55 >> (bitIndex - 1)) & 1;
        else high = false;                          // broken stop bit
        rx.sample(high);
    }
    CHECK(rx.available() == 0, "framing error byte dropped");
}

static void test_buffer_bounds_hold() {
    SoftUartRx rx;
    for (int i = 0; i < 30; i++) txByte(rx, (uint8_t)('A' + (i % 26)));
    CHECK(rx.available() <= SoftUartRx::BUFFER_SIZE, "buffer never exceeds its size");
    // Draining must yield only valid bytes and terminate.
    int n = 0;
    while (rx.available() > 0 && n < 30) { rx.read(); n++; }
    CHECK(n >= SoftUartRx::BUFFER_SIZE - 1, "full buffer drainable");
}

int main() {
    test_single_byte_decodes();
    test_full_pin_line_decodes_in_order();
    test_idle_high_produces_nothing();
    test_framing_error_discarded();
    test_buffer_bounds_hold();
    if (failures == 0) {
        std::printf("test_softuart: all tests passed\n");
        return 0;
    }
    std::printf("test_softuart: %d check(s) failed\n", failures);
    return 1;
}
