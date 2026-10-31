#pragma once

#include <stdint.h>

// SoftUartRx - minimal software UART receiver.
//
// Pure logic (no Arduino dependencies) so it can be tested on the host.
// The firmware samples the RX line every 32 us from a Timer2 compare
// interrupt (31.25 kHz = 3.255 samples per 9600-baud bit) and feeds the
// level to sample(). Bytes are assembled LSB-first with a start-bit
// edge, mid-bit sampling at precomputed sample counts, and a stop-bit
// sanity check (a LOW stop bit discards the byte as a framing error).
// Decoded bytes land in a 16-byte ring buffer; when it is full, new
// bytes are dropped rather than overwriting the oldest ones.

class SoftUartRx {
public:
  static const uint8_t BUFFER_SIZE = 16;

  SoftUartRx()
      : state_(IDLE), counter_(0), bits_(0), shift_(0), head_(0), tail_(0) {}

  // Call every 32 us with the raw line level (true = high / idle).
  void sample(bool high) {
    if (state_ == IDLE) {
      if (!high) {                    // start-bit edge
        state_ = RXING;
        counter_ = 0;
        bits_ = 0;
        shift_ = 0;
      }
      return;
    }

    counter_++;

    // Mid-bit samples for data bits 0..7 (3.255 samples per bit).
    while (bits_ < 8 && counter_ >= SAMPLE_AT[bits_]) {
      if (high) shift_ |= (uint8_t)(1 << bits_);
      bits_++;
    }

    // Stop bit: must be HIGH; otherwise drop the byte (framing error).
    if (bits_ == 8 && counter_ >= STOP_AT) {
      state_ = IDLE;
      if (high) push(shift_);
    }
  }

  int available() const {
    return (int)((uint8_t)(head_ - tail_) & (BUFFER_SIZE - 1));
  }

  uint8_t read() {
    uint8_t b = buf_[tail_];
    tail_ = (uint8_t)((tail_ + 1) & (BUFFER_SIZE - 1));
    return b;
  }

private:
  static const uint8_t SAMPLE_AT[8];
  static const uint8_t STOP_AT = 30;

  void push(uint8_t b) {
    uint8_t next = (uint8_t)((head_ + 1) & (BUFFER_SIZE - 1));
    if (next == tail_) return;        // full: drop the newest byte
    buf_[head_] = b;
    head_ = next;
  }

  enum State { IDLE, RXING };
  State state_;
  uint8_t counter_;
  uint8_t bits_;
  uint8_t shift_;
  uint8_t buf_[BUFFER_SIZE];
  uint8_t head_, tail_;
};

const uint8_t SoftUartRx::SAMPLE_AT[8] = {5, 8, 11, 15, 18, 21, 24, 28};
