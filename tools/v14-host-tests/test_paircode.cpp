// Host tests for PairCodeOverlay (v14 pairing-code OLED overlay logic).
// Build: g++ -std=c++11 -Wall -Wextra test_paircode.cpp -o test_paircode.exe
#include "../../firmware/EMF_Wire_Fault_Detector_v14/paircode.h"

#include <cstdio>
#include <string>

static int failures = 0;

#define CHECK(cond, msg)                                                \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::printf("FAIL: %s (line %d)\n", msg, __LINE__);         \
            failures++;                                                 \
        }                                                               \
    } while (0)

static void test_pin_line_starts_pending_overlay() {
    PairCodeOverlay pc;
    CHECK(pc.feed("PIN,483920", 1000), "PIN line is consumed");
    CHECK(pc.pending(1000), "overlay active right after PIN line");
    CHECK(std::string(pc.code()) == "483920", "code stored verbatim");
    CHECK(pc.pending(1000 + PairCodeOverlay::SHOW_MILLIS - 1), "active just before expiry");
    CHECK(!pc.pending(1000 + PairCodeOverlay::SHOW_MILLIS), "expired at 60 s");
    CHECK(!pc.confirmed(1000), "not confirmed without PINOK");
}

static void test_pinok_confirms() {
    PairCodeOverlay pc;
    pc.feed("PIN,483920", 0);
    CHECK(pc.feed("PINOK", 2000), "PINOK is consumed");
    CHECK(pc.confirmed(2000), "confirmed window active");
    CHECK(!pc.pending(2000), "pending cleared by PINOK");
    CHECK(pc.confirmed(2000 + PairCodeOverlay::CONFIRM_MILLIS - 1), "flash just before 3 s");
    CHECK(!pc.confirmed(2000 + PairCodeOverlay::CONFIRM_MILLIS), "flash ends at 3 s");
    CHECK(!pc.pending(65000), "nothing pending later");
}

static void test_malformed_lines_ignored() {
    PairCodeOverlay pc;
    CHECK(!pc.feed("pin,123456", 0), "lowercase prefix rejected");
    CHECK(!pc.feed("PIN,12A456", 0), "non-digit rejected");
    CHECK(!pc.feed("PIN,12345", 0), "5 digits rejected");
    CHECK(!pc.feed("PIN,1234567", 0), "7 digits rejected");
    CHECK(!pc.feed("PIN,123456789012345", 0), "15 digits rejected");
    CHECK(!pc.feed("PIN,12345678901234567", 0), "17 digits rejected");
    CHECK(!pc.feed("", 0), "empty line rejected");
    CHECK(!pc.feed("PIN,123456,9", 0), "trailing junk rejected");
    CHECK(!pc.pending(0), "nothing pending after garbage");
}

static void test_sixteen_digit_pin_accepted() {
    PairCodeOverlay pc;
    CHECK(pc.feed("PIN,1234567890123456", 0), "16-digit PIN consumed");
    CHECK(std::string(pc.code()) == "1234567890123456", "16-digit code stored");
    CHECK(pc.pending(0), "16-digit overlay active");
}

static void test_new_pin_replaces_old() {
    PairCodeOverlay pc;
    pc.feed("PIN,111111", 0);
    CHECK(pc.feed("PIN,222222", 1000), "second PIN consumed");
    CHECK(std::string(pc.code()) == "222222", "new code replaces old");
    CHECK(pc.pending(1000), "still pending under new code");
}

static void test_pinok_without_pin_is_ignored() {
    PairCodeOverlay pc;
    CHECK(!pc.feed("PINOK", 0), "PINOK with no code is not consumed");
    CHECK(!pc.confirmed(0), "no false confirmation");
}

static void test_pinoff_clears_pending_overlay_without_flash() {
    PairCodeOverlay pc;
    pc.feed("PIN,483920", 0);
    CHECK(pc.pending(1000), "overlay active before PINOFF");
    CHECK(pc.feed("PINOFF", 2000), "PINOFF is consumed while a code is pending");
    CHECK(!pc.pending(2000), "pending cleared by PINOFF");
    CHECK(!pc.confirmed(2000), "no Paired! flash from PINOFF");
    CHECK(!pc.pending(65000), "still nothing pending later");
}

static void test_pinoff_without_code_is_ignored() {
    PairCodeOverlay pc;
    CHECK(!pc.feed("PINOFF", 0), "PINOFF with no active code is not consumed");
    CHECK(!pc.pending(0), "no overlay state from stray PINOFF");
    CHECK(!pc.confirmed(0), "no false confirm from stray PINOFF");
}

static void test_pinoff_crlf_accepted() {
    PairCodeOverlay pc;
    pc.feed("PIN,111111", 0);
    CHECK(pc.feed("PINOFF\r", 100), "PINOFF with trailing CR accepted");
    CHECK(!pc.pending(100), "cleared via CRLF PINOFF");
    CHECK(!pc.confirmed(100), "no flash via CRLF PINOFF");
}

static void test_pinoff_after_pinok_is_ignored() {
    PairCodeOverlay pc;
    pc.feed("PIN,483920", 0);
    pc.feed("PINOK", 1000);
    CHECK(!pc.feed("PINOFF", 2000), "PINOFF after PINOK has nothing to clear");
    CHECK(pc.confirmed(2000), "the Paired! flash survives a stray PINOFF");
}

static void test_crlf_from_relay_println_is_accepted() {
    PairCodeOverlay pc;
    CHECK(pc.feed("PIN,483920\r", 0), "PIN line with trailing CR accepted");
    CHECK(std::string(pc.code()) == "483920", "code parsed despite CR");
    CHECK(pc.pending(0), "overlay active");
    PairCodeOverlay pc2;
    CHECK(pc2.feed("PIN,1234567890123456\r", 0), "16-digit line with CR accepted");
    PairCodeOverlay pc3;
    pc3.feed("PIN,111111", 0);
    CHECK(pc3.feed("PINOK\r", 10), "PINOK with trailing CR accepted");
    CHECK(pc3.confirmed(10), "confirmed via CRLF PINOK");
}

int main() {
    test_pin_line_starts_pending_overlay();
    test_pinok_confirms();
    test_malformed_lines_ignored();
    test_sixteen_digit_pin_accepted();
    test_new_pin_replaces_old();
    test_pinok_without_pin_is_ignored();
    test_pinoff_clears_pending_overlay_without_flash();
    test_pinoff_without_code_is_ignored();
    test_pinoff_crlf_accepted();
    test_pinoff_after_pinok_is_ignored();
    test_crlf_from_relay_println_is_accepted();
    if (failures == 0) {
        std::printf("test_paircode: all tests passed\n");
        return 0;
    }
    std::printf("test_paircode: %d check(s) failed\n", failures);
    return 1;
}
