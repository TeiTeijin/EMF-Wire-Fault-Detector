#pragma once

// PairCodeOverlay - pairing-code display state for the OLED.
//
// Pure logic (no Arduino dependencies) so it can be tested on the host.
// The ESP32 relay sends line types over the UART link:
//   PIN,<6 digits>   - a phone is asking to pair; show this code
//   PINOK            - pairing succeeded; show a short "Paired!" flash
//   PINOFF           - the pairing window closed; clear the overlay with
//                      NO "Paired!" flash (ignored when nothing pending)
// The code shows for SHOW_MILLIS then expires on its own (pairing
// aborted or timed out). Malformed lines change nothing.

class PairCodeOverlay {
public:
  static const unsigned long SHOW_MILLIS = 60000UL;
  static const unsigned long CONFIRM_MILLIS = 3000UL;

  PairCodeOverlay() {
    code_[0] = '\0';
    pending_ = false;
    shownAtMs_ = 0;
    confirmed_ = false;
    confirmedAtMs_ = 0;
  }

  // Feed one line from the relay. Returns true when the line was
  // a valid PIN, PINOK (which must follow an active code), or PINOFF
  // (which must clear an active code).
  // The relay sends CRLF (Print::println), so an optional trailing
  // '\r' is tolerated on every line.
  bool feed(const char* line, unsigned long nowMs) {
    if (line == nullptr) return false;

    // "PINOK" exactly, plus an optional trailing CR.
    if (line[0] == 'P' && line[1] == 'I' && line[2] == 'N' &&
        line[3] == 'O' && line[4] == 'K' &&
        (line[5] == '\0' || (line[5] == '\r' && line[6] == '\0'))) {
      if (!pending_) return false;   // nothing to confirm
      confirmed_ = true;
      confirmedAtMs_ = nowMs;
      pending_ = false;
      return true;
    }

    // "PINOFF" exactly, plus an optional trailing CR. Clears a pending
    // code with no "Paired!" flash; ignored when nothing is pending.
    if (line[0] == 'P' && line[1] == 'I' && line[2] == 'N' &&
        line[3] == 'O' && line[4] == 'F' && line[5] == 'F' &&
        (line[6] == '\0' || (line[6] == '\r' && line[7] == '\0'))) {
      if (!pending_) return false;   // nothing to clear
      pending_ = false;
      confirmed_ = false;
      return true;
    }

    // "PIN," + digits, nothing after (optional trailing CR). Most phones
    // ask for 6 digits; some demand 16 - accept both, reject the rest.
    if (line[0] != 'P' || line[1] != 'I' || line[2] != 'N' || line[3] != ',') return false;
    int n = 0;
    while (line[4 + n] >= '0' && line[4 + n] <= '9') n++;
    if (line[4 + n] != '\0' && !(line[4 + n] == '\r' && line[5 + n] == '\0')) return false;
    if (n != 6 && n != 16) return false;

    for (int i = 0; i < n; i++) code_[i] = line[4 + i];
    code_[n] = '\0';
    pending_ = true;
    shownAtMs_ = nowMs;
    confirmed_ = false;
    return true;
  }

  bool pending(unsigned long nowMs) const {
    return pending_ && (nowMs - shownAtMs_) < SHOW_MILLIS;
  }

  bool confirmed(unsigned long nowMs) const {
    return confirmed_ && (nowMs - confirmedAtMs_) < CONFIRM_MILLIS;
  }

  const char* code() const { return code_; }

private:
  char code_[17];
  bool pending_;
  unsigned long shownAtMs_;
  bool confirmed_;
  unsigned long confirmedAtMs_;
};
