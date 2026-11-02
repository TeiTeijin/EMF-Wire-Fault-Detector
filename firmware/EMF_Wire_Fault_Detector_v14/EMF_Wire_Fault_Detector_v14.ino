/*
  EMF Wire Fault Detector - Capstone Project (v14)
  Hardware: Arduino Uno
    - Op-amp output (antenna + cap + LM358 gain stage)  -> A0
    - QMC5883P magnetometer (I2C, address 0x2C)         -> A4 = SDA, A5 = SCL
    - SSD1306 OLED 128x64 (I2C, address 0x3C)           -> same A4 = SDA, A5 = SCL
    - 3-pin active buzzer module (VCC, GND, I/O)        -> pin 8
    - Push button (manual recalibration)                -> pin 7 (other leg to GND)
    - Link to ESP32 for Bluetooth: D12 (RX) / D11 (TX, through voltage
      divider) via SoftwareSerial (moved off D2/D3 after a solder bridge
      shorted those pins on this build)

  v14 CHANGES (from v13):
    - Pairing-code overlay: the ESP32 relay now sends "PIN,<code>" over
      the D2 link when a phone asks to pair, and "PINOK" when pairing
      succeeds. The Uno shows the code (or a short "Paired!" flash) on
      the OLED for up to 60 s and then returns to the normal readings
      screen. Sensing never stops while the overlay is shown.
      Parsing/timing logic lives in paircode.h (host-tested).
    - Pairing window: the Bluetooth link is locked by default. Holding
      the button 2 s (or sending p over USB serial) sends "PAIRON" to
      the relay, which opens a 60 s pairing window and shows a fresh
      6-digit code to type on the phone. "PINOFF" clears the overlay
      when the window closes. Press/hold classification lives in
      presshold.h (host-tested); a held press never also recalibrates.
    - Display driver switched from Adafruit SSD1306 (1 KB framebuffer on
      the heap) to U8g2 page mode (128 B buffer): the Adafruit buffer
      malloc was failing on the ATmega328P's 2 KB RAM ("SSD1306 not
      found" despite a healthy display).

  v13 CHANGES (from v12):
    - No more manual "turn in all directions" calibration ritual.
      The hard-iron offsets are learned AUTOMATICALLY in the background
      from normal handling: raw X/Y/Z min/max are tracked while the
      device is used, and once ALL THREE axes have swept enough
      (>= 600 counts each), the offsets are locked and saved to EEPROM.
      They keep refining as new orientations are seen. Requiring all
      three axes (instead of v12's "two of three") avoids the planar
      spin that saved location-locked offsets.
    - New EEPROM marker (0xB7): offsets saved by older versions (which
      were learned in one room) are discarded on first v13 boot.
    - Verdict debounce: Clear / Wire / Fault only changes after 4
      consecutive samples, so brief movement spikes no longer flip it.
    - Softer movement response: FIELD_MAX_DELTA 250->500,
      FIELD_MAX_JITTER 100->200, faster zero-point follower
      (5 s while Clear) that also keeps following during Wire (60 s).
    - Serial commands: c = recalibrate rest baseline, t = forget the
      learned offsets and re-learn them from normal use.

  Two sensing paths:
    1) Antenna + op-amp: picks up CHANGING electric fields (mains hum,
       arcing). Measured as peak-to-peak swing over a 40 ms window.
    2) QMC5883P: measures the magnetic field around a wire carrying
       current, including steady DC current. Measured as the change in
       field STRENGTH from the calibrated rest value, so turning or
       tilting the board does not count.

  CALIBRATION (nothing is hard-coded):
  Whatever state the device is in when you calibrate - including held in
  your hand - becomes zero. For each path, calibration learns three things:
    - the resting level,
    - how much that reading naturally wobbles,
    - how much it naturally jumps from one reading to the next.
  After that, only changes that go BEYOND that learned wobble count as a
  signal, and only extra jumpiness beyond what it learned counts as jitter.
  Calibrate while holding the device the way you will use it, and keep
  holding it the same way.

  Status logic: the stronger path gives the signal. A steady signal =
  Wire Detected. A signal that keeps jumping around = POSSIBLE FAULT,
  as a proxy for arcing / a loose joint.

  Press the push button (if you have one) or type c in the Serial Monitor to
  re-run the rest-baseline calibration. Hold the button 2 s or type p to
  open the Bluetooth pairing window. Type t to forget the learned hard-iron
  offsets so they are re-learned from normal use.

  IMPORTANT SAFETY NOTE:
  Never let the antenna wire or your hands touch exposed/live house
  wiring. Sense from a safe distance through the wall/insulation only.
  For fault-vs-normal testing, use a low-voltage source or a
  de-energized frayed cord, never live mains current.

  Requires libraries (Library Manager):
    - U8g2 (display driver, page mode - only 128 bytes of buffer, so the
      ATmega328P's 2 KB RAM is never squeezed by a 1 KB framebuffer)
  (The QMC5883P is read directly over Wire, no extra library needed.)
*/

#include <Wire.h>
#include <U8g2lib.h>
#include <SoftwareSerial.h>
#include <EEPROM.h>
#include "autocal.h"
#include "classify.h"
#include "verdict.h"
#include "paircode.h"
#include "edgebutton.h"
#include "softuart.h"

// Link to ESP32: Arduino D11 (TX, through voltage divider) -> ESP32 RX
//                Arduino D10 (RX) <- ESP32 TX (direct, no divider needed)
// SoftwareSerial is TX-only here (dummy RX on unused pin 6): the real
// receiver is the Timer2 software UART on D10.
SoftwareSerial espSerial(6, 11); // RX (dummy), TX
unsigned long lastEspSend = 0;
const int ESP_SEND_INTERVAL = 500; // ms

// ---------- OLED CONFIG ----------
#define OLED_ADDR    0x3C   // try 0x3D if this doesn't init
// U8g2 page mode: 128-byte buffer instead of Adafruit's 1 KB framebuffer.
U8G2_SSD1306_128X64_NONAME_1_HW_I2C u8g2(U8G2_R0, U8X8_PIN_NONE);

// Fonts: small text (6x10) and big digits/letters (logisoso, 24 px).
const uint8_t* const FONT_SMALL = u8g2_font_6x10_tf;
const uint8_t* const FONT_BIG   = u8g2_font_logisoso24_tr;

// Scratch buffer for formatted lines (U8g2 draws RAM strings).
char oledLine[22];

// Centers a string horizontally at the given baseline y (must be called
// inside a firstPage/nextPage loop). Measuring beats guessing, so wide
// codes or text can never run off the right edge.
void drawCentered(const uint8_t* font, u8g2_uint_t y, const char* s) {
  u8g2.setFont(font);
  u8g2_uint_t w = u8g2.getStrWidth(s);   // measures with the font just set
  u8g2.drawStr((u8g2.getDisplayWidth() - w) / 2, y, s);
}

// ---------- QMC5883P ----------
#define QMC_ADDR 0x2C
bool qmcOk = false;
bool qmcFault = false;     // true while the chip is not giving fresh readings
unsigned long lastGoodQmcMs = 0;  // last time the chip gave a fresh reading
int lastGoodFieldPct = 0;         // last field % from a fresh reading
int qmcFailCount = 0;      // consecutive failed reads
int qmcSameCount = 0;      // consecutive identical readings (a frozen chip)
int qmcReinits = 0;        // how many times the chip had to be restarted

// ---------- AUTO HARD-IRON CALIBRATION (v13) ----------
// Learns the board's own fixed magnetism from normal handling - no
// manual turning step. Logic lives in autocal.h (host-tested).
EmfAutoCal autocal;

// ---------- VERDICT DEBOUNCE (v13) ----------
// Clear / Wire / Fault only changes after 4 consecutive samples.
// Logic lives in verdict.h (host-tested).
VerdictDebounce debounce(4, 4);

// ---------- PAIRING-CODE OVERLAY (v14) ----------
// Shows the Bluetooth pairing code from the ESP32 relay on the OLED.
// Logic lives in paircode.h (host-tested).
PairCodeOverlay pairCode;
char espLineBuf[21];   // longest line: "PIN," + 16 digits + NUL = 21
uint8_t espLineLen = 0;

// Software UART receiver: Timer2 samples the RX line (D10) every 32 us
// and SoftUartRx (softuart.h, host-tested) assembles the bytes. This
// replaces SoftwareSerial's receiver, which proved unreliable on this
// build. The transmitter stays on SoftwareSerial (D11).
SoftUartRx softRx;

ISR(TIMER2_COMPA_vect) {
  softRx.sample(digitalRead(10) != LOW);
}

// Reads pending bytes from the ESP32 link and feeds complete lines to
// the pairing overlay. The relay repeats the PIN line every 500 ms, so
// missing a poll window only delays the display, never loses the code.
void pollPairLines() {
  while (softRx.available() > 0) {
    char c = (char)softRx.read();
    if (c == '\r') continue;   // relay println sends CRLF; strip the CR
    if (c == '\n') {
      espLineBuf[espLineLen] = '\0';
      pairCode.feed(espLineBuf, millis());
      espLineLen = 0;
    } else if (espLineLen < sizeof(espLineBuf) - 1) {
      espLineBuf[espLineLen++] = c;
    } else {
      espLineLen = 0;   // overlong line: discard
    }
  }
}

// Asks the ESP32 relay to open the Bluetooth pairing window. Sent 3x
// over ~300 ms for robustness (the UART link has no retransmission).
void sendPairOn() {
  for (int i = 0; i < 3; i++) {
    espSerial.println(F("PAIRON"));
    if (i < 2) delay(150);
  }
}

// ---------- PINS ----------
const int opampPin  = A0;  // op-amp output (analog)
const int buzzerPin = 8;   // 3-pin active buzzer module I/O
const int buttonPin = 7;   // push button, other leg to GND, uses INPUT_PULLUP

// Some active buzzer modules trigger on LOW (not HIGH). If your buzzer
// buzzes when it shouldn't, flip this.
const bool BUZZER_ACTIVE_LOW = true;

void buzzerWrite(bool on) {
  if (BUZZER_ACTIVE_LOW) {
    digitalWrite(buzzerPin, on ? LOW : HIGH);
  } else {
    digitalWrite(buzzerPin, on ? HIGH : LOW);
  }
}

// ---------- LEARNED AT CALIBRATION ----------
// Antenna path
int baselineOpamp  = 0;   // average peak-to-peak while calibrating
int calP2pCeil     = 0;   // highest peak-to-peak seen while calibrating
int calP2pJitter   = 0;   // average jump between readings while calibrating
// Magnetometer path (uses the field STRENGTH, which does not change when you turn the board)
int baseMag        = 0;   // average field strength while calibrating
int calFieldBand   = 0;   // how far the strength wobbled while calibrating (counts)
int calFieldJitter = 0;   // average jump between strength readings while calibrating
float baseMagF     = 0;   // running zero point: starts at baseMag, then slowly follows the device

// ---------- RUNNING VALUES ----------
const int SMOOTH_SAMPLES = 10;
int opampReadings[SMOOTH_SAMPLES];        // recent peak-to-peak values
int jitterReadings[SMOOTH_SAMPLES];       // recent p2p jumps
int fieldReadings[SMOOTH_SAMPLES];        // recent field distances from rest
int fieldJitterReadings[SMOOTH_SAMPLES];  // recent field jumps
int smoothIndex = 0;
long opampTotal       = 0;
long jitterTotal      = 0;
long fieldTotal       = 0;
long fieldJitterTotal = 0;
int prevP2P = 0;
int prevX = 0, prevY = 0, prevZ = 0;
int prevMag = 0;

// ---------- SENSITIVITY SCALES ----------
// These are NOT baselines. They only set how much change (beyond the
// calibrated rest and wobble) counts as 100%. Lower = more sensitive.
const int OPAMP_MAX_DEVIATION = 150;   // antenna: p2p counts above the calibration ceiling
const int OPAMP_MAX_JITTER    = 150;   // antenna: extra jumpiness
// Magnetometer: change in field STRENGTH (counts) that counts as 100%. A wire only adds to
// the strength along the direction of the Earth's field, so expect a few hundred counts at most.
// v13: 250 -> 500 so ordinary movement while walking maps to a smaller percentage.
const int FIELD_MAX_DELTA     = 500;
const int FIELD_MAX_JITTER    = 200;   // v13: 100 -> 200

// Handheld: how fast the field zero point follows the device (time constant, seconds).
// v13: faster while Clear (15 -> 5 s) so moving between rooms re-zeroes quickly,
// and it keeps following (slowly) during Wire so a spike does not stick forever.
const float FOLLOW_TAU_CLEAR  = 5.0;
const float FOLLOW_TAU_WIRE   = 60.0;
const float FOLLOW_TAU_ACTIVE = 120.0;
// The antenna baseline has the same problem as the magnetometer zero point:
// body coupling / display noise can move the resting p2p level after
// calibration. Track it slowly while Clear so the op-amp channel stays
// sensitive instead of being pinned below a stale ceiling.
const float FOLLOW_TAU_ANTENNA        = 60.0; // slow: a steady source must not be learned away
const float FOLLOW_TAU_ANTENNA_JITTER = 20.0;
const float FOLLOW_TAU_SETTLE         = 2.0;
const int   OPAMP_MARGIN              = 25;   // counts above the adaptive baseline
const int   MOTION_FIELD_JITTER       = 55;   // field-only jitter % that means "moving"
const unsigned long MOTION_HOLD_MS    = 2500; // keep Clear this long after motion stops
const unsigned long ANTENNA_HOLD_MS   = 5000; // don't learn the antenna baseline right after a detection
const unsigned long SETTLE_MS         = 5000; // ignore alerts while baselines settle
unsigned long lastFollowMs = 0;
unsigned long lastMotionMs = 0;
unsigned long lastAntennaActiveMs = 0;
unsigned long settleUntilMs = 0;
float opampBaseF = 0;
float opampJitterBaseF = 0;

// ---------- THRESHOLDS (0-100% scale) ----------
const int LOW_THRESHOLD    = 15;  // below this: nothing detected
const int FAULT_THRESHOLD  = 50;  // jitter % at/above this: possible fault

// ---------- STATE ----------
AlertLevel currentAlert = ALERT_CLEAR;

unsigned long lastBeepTime = 0;
const int WIRE_BEEP_ON  = 100;  // ms, short pulse for "wire detected"
const int WIRE_BEEP_GAP = 800;  // ms between pulses
const int FAULT_BEEP_GAP = 150; // ms, fast urgent beep for possible fault

// ---------- BUTTON ----------
// 100 Hz Timer1 interrupt + press/hold classifier. Sampling the button
// from an interrupt means a short tap can never fall between main-loop
// samples. Quick press = SHORT_PRESS (recalibrate), hold reaching 2 s =
// LONG_HOLD, fired AT the 2 s mark while still held (pairing window).
// Pure logic lives in edgebutton.h (host-tested).
EdgeButtonPoller buttonPoller;

// Timer1 compare ISR at 100 Hz: feed the button level into the edge
// classifier. (Pin-change interrupts would collide with SoftwareSerial,
// which owns the PCINT2 group for pins 2/3.)
ISR(TIMER1_COMPA_vect) {
  buttonPoller.tick(digitalRead(buttonPin) == LOW, micros());
}

// ---------- QMC5883P helpers ----------
void writeQmc(byte reg, byte val) {
  Wire.beginTransmission(QMC_ADDR);
  Wire.write(reg);
  Wire.write(val);
  Wire.endTransmission();
}

bool qmcInit() {
  Wire.beginTransmission(QMC_ADDR);
  if (Wire.endTransmission() != 0) return false;   // nothing answered at 0x2C
  writeQmc(0x0B, 0x08);   // control 2: +/-8 G range, SET/RESET on (0x08 is what
                           // this chip needs; 0x01 makes it hang continuously)
  writeQmc(0x0A, 0xCF);   // control 1: continuous mode, 200 Hz, 8x oversampling
  // Read the chip ID (should be 0x80) so the Serial Monitor shows it is talking
  Wire.beginTransmission(QMC_ADDR);
  Wire.write(0x00);
  Wire.endTransmission(false);
  Wire.requestFrom((uint8_t)QMC_ADDR, (uint8_t)1);
  if (Wire.available()) {
    byte id = Wire.read();
    Serial.print(F("QMC5883P chip ID: 0x")); Serial.println(id, HEX);
  }
  return true;
}

bool readQmc(int &x, int &y, int &z) {
  Wire.beginTransmission(QMC_ADDR);
  Wire.write(0x01);                                // X LSB is register 0x01
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom((uint8_t)QMC_ADDR, (uint8_t)6) != 6) return false;
  byte b[6];
  for (int i = 0; i < 6; i++) b[i] = Wire.read();
  x = (int16_t)(b[0] | (b[1] << 8));
  y = (int16_t)(b[2] | (b[3] << 8));
  z = (int16_t)(b[4] | (b[5] << 8));
  return true;
}

// Field strength in counts: sqrt(x^2 + y^2 + z^2). Turning the board changes x, y and z
// but not this number, because the Earth's field has the same strength in every direction.
// v13: the offsets come from the auto-calibrated hard-iron estimate.
int fieldMagnitude(int x, int y, int z) {
  float fx = (float)x - autocal.offsetX(), fy = (float)y - autocal.offsetY(), fz = (float)z - autocal.offsetZ();
  float m = sqrt(fx * fx + fy * fy + fz * fz);
  return (int)constrain(m, 0.0, 30000.0);
}

// ---------- EEPROM (hard-iron offsets) ----------
// Layout: byte 0 = marker (0xB7 = v13), then offX, offY, offZ (2 bytes each).
// Older versions used marker 0xA5; their room-locked offsets are discarded.
const byte OFFSET_MARKER_V13 = 0xB7;

void saveOffsets() {
  EEPROM.update(0, OFFSET_MARKER_V13);
  EEPROM.put(1, autocal.offsetX());
  EEPROM.put(3, autocal.offsetY());
  EEPROM.put(5, autocal.offsetZ());
}

bool loadOffsets() {
  if (EEPROM.read(0) != OFFSET_MARKER_V13) return false;
  int ox, oy, oz;
  EEPROM.get(1, ox);
  EEPROM.get(3, oy);
  EEPROM.get(5, oz);
  autocal.loadFrom(ox, oy, oz);
  return true;
}

// Peak-to-peak of the op-amp output over a 40 ms window.
int measureP2P() {
  int lo = 1023, hi = 0;
  unsigned long start = millis();
  while (millis() - start < 40) {
    int v = analogRead(opampPin);
    if (v < lo) lo = v;
    if (v > hi) hi = v;
  }
  return hi - lo;
}

void setup() {
  Serial.begin(9600);
  espSerial.begin(9600);
  pinMode(buzzerPin, OUTPUT);
  pinMode(buttonPin, INPUT_PULLUP);
  buzzerWrite(false); // start silent

  // 100 Hz button sampling via Timer1 compare (CTC, top = OCR1A):
  // 16 MHz / (159999 + 1) = 100 Hz - 10 ms resolution, plenty for a
  // 50 ms-debounced button, and gentle enough not to disturb the
  // software UART receiver. Timer1 is otherwise unused, and this
  // avoids the PCINT2 group that SoftwareSerial owns for pins 2/3.
  noInterrupts();
  TCCR1A = 0;
  TCCR1B = 0;
  OCR1A  = 159999;
  TCCR1B |= (1 << WGM12);   // CTC mode
  TCCR1B |= (1 << CS10);    // prescaler 1
  TIMSK1 |= (1 << OCIE1A);  // enable compare A interrupt

  // 31.25 kHz sampling for the software UART receiver (Timer2 CTC):
  // 16 MHz / 64 / (7 + 1) = 31250 Hz = 3.255 samples per 9600-baud bit.
  TCCR2A = 0;
  TCCR2B = 0;
  OCR2A  = 7;
  TCCR2A |= (1 << WGM21);   // CTC mode
  TCCR2B |= (1 << CS22);    // prescaler 64
  TIMSK2 |= (1 << OCIE2A);  // enable compare A interrupt
  interrupts();

  // Probe the OLED on the I2C bus (U8g2 has no failure report of its
  // own), then bring up the display.
  Wire.begin();
  Wire.beginTransmission(OLED_ADDR);
  if (Wire.endTransmission() != 0) {
    Serial.println(F("SSD1306 not found - check wiring/address"));
    while (true); // halt
  }

#ifdef WIRE_HAS_TIMEOUT
  Wire.setWireTimeout(3000, true);   // a stuck I2C bus must not freeze the sketch
#endif
  u8g2.begin();
  qmcOk = qmcInit();
  if (!qmcOk) {
    Serial.println(F("QMC5883P not found at 0x2C - check SDA/SCL (A4/A5). Running antenna-only."));
  }

  // Restore hard-iron offsets learned by v13, or start fresh (older
  // versions' offsets were saved with a different marker and are ignored).
  if (loadOffsets()) {
    Serial.print(F("Hard-iron offsets loaded: "));
  } else {
    autocal.reset();
    Serial.print(F("No saved offsets - will auto-learn from normal use: "));
  }
  Serial.print(autocal.offsetX()); Serial.print(F(" "));
  Serial.print(autocal.offsetY()); Serial.print(F(" "));
  Serial.println(autocal.offsetZ());

  runCalibrationSequence(5);
}

// Rest-baseline calibration - learns the resting level, wobble, and
// jumpiness of both sensors in whatever state the device is in right now.
void runCalibrationSequence(int countdownSeconds) {
  u8g2.firstPage();
  do {
    u8g2.setFont(FONT_SMALL);
    u8g2.drawStr(0, 10, "EMF Wire Detector");
    u8g2.drawStr(0, 20, "Hold it the way");
    u8g2.drawStr(0, 30, "you will use it");
  } while (u8g2.nextPage());

  for (int s = countdownSeconds; s > 0; s--) {
    pollPairLines();   // a pairing request during the countdown is captured
    oledLine[0] = '0' + s;
    oledLine[1] = '\0';
    u8g2.firstPage();
    do {
      u8g2.setFont(FONT_SMALL);
      u8g2.drawStr(0, 10, "Calibrating in:");
      drawCentered(FONT_BIG, 48, oledLine);
    } while (u8g2.nextPage());
    delay(1000);
  }

  u8g2.firstPage();
  do {
    u8g2.setFont(FONT_SMALL);
    u8g2.drawStr(0, 10, "Calibrating...");
    u8g2.drawStr(0, 20, "Keep holding it");
    u8g2.drawStr(0, 30, "the same way");
  } while (u8g2.nextPage());

  takeBaselineReadings();

  // Start the rolling values AT what calibration learned, so there is
  // no false spike for the first few cycles afterwards.
  for (int i = 0; i < SMOOTH_SAMPLES; i++) {
    opampReadings[i]       = baselineOpamp;
    jitterReadings[i]      = calP2pJitter;
    fieldReadings[i]       = 0;
    fieldJitterReadings[i] = calFieldJitter;
  }
  opampTotal       = (long)baselineOpamp  * SMOOTH_SAMPLES;
  jitterTotal      = (long)calP2pJitter   * SMOOTH_SAMPLES;
  fieldTotal       = 0;
  fieldJitterTotal = (long)calFieldJitter * SMOOTH_SAMPLES;
  smoothIndex      = 0;

  // The antenna path's resting level is taken from the mean, not the
  // calibration maximum, and is allowed to settle onto the real operating
  // level for a few seconds before it can raise an alert.
  opampBaseF       = baselineOpamp;
  opampJitterBaseF = calP2pJitter;
  lastMotionMs     = 0;
  lastAntennaActiveMs = 0;
  settleUntilMs    = millis() + SETTLE_MS;

  currentAlert = ALERT_CLEAR;
  debounce.resetTo((int)ALERT_CLEAR);

  u8g2.firstPage();
  do {
    drawCentered(FONT_BIG, 48, "Ready");
  } while (u8g2.nextPage());
  delay(1000);
}

// Calibration: sample for about 1 second and learn the rest level, the
// wobble, and the jumpiness of both sensors in whatever state the device
// is in right now.
void takeBaselineReadings() {
  const int N = 25;
  long p2pSum = 0, p2pJumpSum = 0;
  int p2pMax = 0, lastP = 0;

  long magSum = 0, fieldJumpSum = 0;
  int magMin = 32767, magMax = 0;
  int lastMag = 0, lastX = 0, lastY = 0, lastZ = 0;
  int n = 0;

  for (int i = 0; i < N; i++) {
    int p = measureP2P();
    p2pSum += p;
    if (p > p2pMax) p2pMax = p;
    if (i > 0) p2pJumpSum += abs(p - lastP);
    lastP = p;

    int x, y, z;
    if (qmcOk && readQmc(x, y, z)) {
      int m = fieldMagnitude(x, y, z);
      magSum += m;
      if (m < magMin) magMin = m;
      if (m > magMax) magMax = m;
      if (n > 0) fieldJumpSum += abs(m - lastMag);
      lastMag = m;
      lastX = x; lastY = y; lastZ = z;
      n++;
    }
  }

  baselineOpamp = p2pSum / N;
  calP2pCeil    = p2pMax;
  calP2pJitter  = p2pJumpSum / (N - 1);
  prevP2P       = lastP;

  if (n > 1) {
    baseMag        = (int)(magSum / n);
    baseMagF       = baseMag;
    calFieldBand   = magMax - magMin;
    calFieldJitter = (int)(fieldJumpSum / (n - 1));
    prevMag = lastMag;
    prevX = lastX; prevY = lastY; prevZ = lastZ;
  } else {
    calFieldBand = 0;
    calFieldJitter = 0;
  }

  Serial.print(F("Calibrated antenna: mean p2p=")); Serial.print(baselineOpamp);
  Serial.print(F(" ceiling=")); Serial.print(calP2pCeil);
  Serial.print(F(" jitter=")); Serial.println(calP2pJitter);
  Serial.print(F("Calibrated field: strength=")); Serial.print(baseMag);
  Serial.print(F(" band=")); Serial.print(calFieldBand);
  Serial.print(F(" jitter=")); Serial.println(calFieldJitter);
}

// Updates the rolling average and returns the average.
int smoothAverage(int rawValue, int *readings, long *total) {
  *total -= readings[smoothIndex];
  readings[smoothIndex] = rawValue;
  *total += rawValue;
  return *total / SMOOTH_SAMPLES;
}

void loop() {
  // Pairing-code lines from the ESP32 relay (may arrive any time).
  pollPairLines();

  // Button: the ISR classifies presses the instant they happen; drain
  // any latched event here. Quick press = recalibrate, hold 2 s =
  // pairing window (fires at the 2 s mark while still held).
  EdgeButtonPoller::Event buttonEvent = buttonPoller.takeEvent();

  // Commands from the Serial Monitor (no button needed):
  //   c = recalibrate rest baseline
  //   t = forget the learned hard-iron offsets (re-learn from normal use)
  //   p = open the Bluetooth pairing window
  char cmd = 0;
  if (Serial.available()) cmd = Serial.read();

  if (cmd == 't' || cmd == 'T') {
    Serial.println(F("Forgetting hard-iron offsets - will re-learn from normal use"));
    autocal.reset();
    EEPROM.update(0, 0);            // clear the v13 marker
    runCalibrationSequence(3);
    return;
  }
  if (buttonEvent == EdgeButtonPoller::SHORT_PRESS || cmd == 'c' || cmd == 'C') {
    Serial.println(F("Recalibrating"));
    runCalibrationSequence(3);
    return;
  }
  if (buttonEvent == EdgeButtonPoller::LONG_HOLD || cmd == 'p' || cmd == 'P') {
    Serial.println(F("Pairing window requested"));
    sendPairOn();
    // No return here: sensing keeps running while the window is open.
  }

  // --- Antenna / op-amp path ---
  unsigned long nowMs = millis();
  int p2p = measureP2P();
  int opampAvg    = smoothAverage(p2p, opampReadings, &opampTotal);
  // Only a rise above the *tracked* resting level counts as signal. The
  // calibration maximum is deliberately not used here: it sits above the
  // normal operating level, which pinned this channel at 0% permanently.
  int opampSignal = max(0, opampAvg - (int)opampBaseF - OPAMP_MARGIN);
  int p2pJump     = abs(p2p - prevP2P);
  prevP2P = p2p;
  int opampJitterAvg    = smoothAverage(p2pJump, jitterReadings, &jitterTotal);
  int opampJitterExcess = max(0, opampJitterAvg - (int)opampJitterBaseF);

  // --- Magnetometer path ---
  int fieldDelta = 0;        // stays 0 while the chip is not giving fresh data
  int fieldJump  = 0;
  int x = prevX, y = prevY, z = prevZ;   // last good raw reading (for the debug print)
  int mag = prevMag;                     // last field strength
  if (qmcOk) {
    if (readQmc(x, y, z)) {
      qmcFailCount = 0;
      // A working chip never returns the exact same three numbers many times in a row.
      if (x == prevX && y == prevY && z == prevZ) qmcSameCount++; else qmcSameCount = 0;
      mag = fieldMagnitude(x, y, z);
      fieldDelta = abs(mag - (int)baseMagF);  // change in field STRENGTH from the zero point
      fieldJump  = abs(mag - prevMag);
      prevMag = mag;
      prevX = x; prevY = y; prevZ = z;

      // Auto hard-iron learning: feed fresh raw readings to the tracker.
      // (The tracker also ignores exact repeat triplets on its own.)
      // A stalled chip briefly returns 0,0,0 before the restart logic
      // kicks in - never let that corrupt the learned min/max range.
      if (qmcSameCount == 0 && !(x == 0 && y == 0 && z == 0)) {
        bool wasReady = autocal.ready();
        if (autocal.update(x, y, z, millis())) {
          saveOffsets();
          Serial.print(wasReady ? F("Auto-cal refined: ") : F("Auto-calibrated: "));
          Serial.print(F("offsets XYZ="));
          Serial.print(autocal.offsetX()); Serial.print(F(" "));
          Serial.print(autocal.offsetY()); Serial.print(F(" "));
          Serial.print(autocal.offsetZ());
          Serial.print(F("  sweep="));
          Serial.print(autocal.sweepX()); Serial.print(F(" "));
          Serial.print(autocal.sweepY()); Serial.print(F(" "));
          Serial.println(autocal.sweepZ());
          // The offsets just changed, so the magnitude reference jumped too:
          // re-zero the field zero point and the smoothing rings at the new
          // offsets so the correction itself never looks like a signal.
          mag = fieldMagnitude(x, y, z);
          baseMagF = mag;
          prevMag = mag;
          fieldDelta = 0;
          fieldJump = 0;
          for (int i = 0; i < SMOOTH_SAMPLES; i++) {
            fieldReadings[i] = 0;
            fieldJitterReadings[i] = 0;
          }
          fieldTotal = 0;
          fieldJitterTotal = 0;
        }
      }
    } else {
      qmcFailCount++;
    }

    qmcFault = (qmcFailCount > 0) || (qmcSameCount >= 5);
    if (qmcFault) { fieldDelta = 0; fieldJump = 0; }

    // Chip failed or froze (for example after a power glitch): restart it.
    // 8 frozen samples is enough - a hung chip recovers faster and the
    // display never sits on ERR for long.
    if (qmcFailCount >= 3 || qmcSameCount >= 8) {
      qmcReinits++;
      Serial.println(F("QMC5883P stopped updating - restarting it"));
      qmcInit();
      qmcFailCount = 0;
      qmcSameCount = 0;
    }
  }
  int fieldAvg    = smoothAverage(fieldDelta, fieldReadings, &fieldTotal);
  int fieldSignal = max(0, fieldAvg - calFieldBand);      // only movement beyond the calibration wobble
  int fieldJitterAvg    = smoothAverage(fieldJump, fieldJitterReadings, &fieldJitterTotal);
  int fieldJitterExcess = max(0, fieldJitterAvg - calFieldJitter);

  smoothIndex = (smoothIndex + 1) % SMOOTH_SAMPLES;

  // Map everything to 0-100%
  int opampPercent       = constrain(map(opampSignal,       0, OPAMP_MAX_DEVIATION, 0, 100), 0, 100);
  int opampJitterPercent = constrain(map(opampJitterExcess, 0, OPAMP_MAX_JITTER,    0, 100), 0, 100);
  int fieldPercent       = constrain(map(fieldSignal,       0, FIELD_MAX_DELTA,     0, 100), 0, 100);
  int fieldJitterPercent = constrain(map(fieldJitterExcess, 0, FIELD_MAX_JITTER,    0, 100), 0, 100);

  // The stronger path gives the signal, the more erratic path gives the jitter.
  int signal        = max(opampPercent, fieldPercent);
  int jitterPercent = max(opampJitterPercent, fieldJitterPercent);

  // Keep the last good field value on screen during a brief chip freeze;
  // only flag ERR if the chip stays dead for a while.
  bool fieldErr = false;
  int fieldDisplayPct = fieldPercent;
  if (qmcFault) {
    fieldDisplayPct = lastGoodFieldPct;
    if (nowMs - lastGoodQmcMs > 3000) fieldErr = true;
  } else {
    lastGoodFieldPct = fieldPercent;
    lastGoodQmcMs = nowMs;
  }

  // Movement gate. A magnetometer cannot tell "the device moved" from "a
  // field appeared"; both change the vector. While the field is large *and*
  // jumpy, and the antenna is quiet, treat it as handling: hold Clear and
  // let the zero-point follower catch up. A real steady wire has low field
  // jitter and is still reported; an arcing/faulty wire lights the antenna,
  // so it is not mistaken for movement.
  bool settling = (long)(settleUntilMs - nowMs) > 0;
  bool motion = (fieldPercent >= LOW_THRESHOLD &&
                 fieldJitterPercent >= MOTION_FIELD_JITTER &&
                 opampPercent < LOW_THRESHOLD);
  if (motion) lastMotionMs = nowMs;
  bool motionRecent = lastMotionMs != 0 && (nowMs - lastMotionMs < MOTION_HOLD_MS);

  // Hold the antenna baseline for a few seconds after any detection. An
  // intermittent contact with a live cord would otherwise fall below the
  // alert threshold, get learned as the new resting level, and make the
  // next touch look like nothing changed.
  bool antennaActive = (opampSignal > 0) || (currentAlert != ALERT_CLEAR);
  if (antennaActive) lastAntennaActiveMs = nowMs;
  bool antennaHold = lastAntennaActiveMs != 0 &&
                     (nowMs - lastAntennaActiveMs < ANTENNA_HOLD_MS);

  // --- DEBUG: watch these while testing, comment out once tuned ---
  static byte dbgTick = 0;
  if (++dbgTick >= 4) {
  dbgTick = 0;
  Serial.print(F("p2p=")); Serial.print(p2p);
  Serial.print(F(" opampPct=")); Serial.print(opampPercent);
  Serial.print(F(" field=")); Serial.print(fieldDelta);
  Serial.print(F(" mag=")); Serial.print(mag);
  Serial.print(F(" xyz=")); Serial.print(x); Serial.print(F(","));
  Serial.print(y); Serial.print(F(",")); Serial.print(z);
  Serial.print(F(" fails=")); Serial.print(qmcFailCount);
  Serial.print(F(" same=")); Serial.print(qmcSameCount);
  Serial.print(F(" reinit=")); Serial.print(qmcReinits);
  Serial.print(F(" fieldPct=")); Serial.print(fieldPercent);
  Serial.print(F(" jitterPct=")); Serial.print(jitterPercent);
  // Split-path diagnostics: which path owns the signal/jitter, and what
  // calibration learned. Temporary while tuning the front end.
  Serial.print(F(" fj=")); Serial.print(fieldJitterPercent);
  Serial.print(F(" oj=")); Serial.print(opampJitterPercent);
  Serial.print(F(" fja=")); Serial.print(fieldJitterAvg);
  Serial.print(F(" oja=")); Serial.print(opampJitterAvg);
  Serial.print(F(" pj=")); Serial.print(p2pJump);
  Serial.print(F(" fjump=")); Serial.print(fieldJump);
  Serial.print(F(" os=")); Serial.print(opampSignal);
  Serial.print(F(" fs=")); Serial.print(fieldSignal);
  Serial.print(F(" cpc=")); Serial.print(calP2pCeil);
  Serial.print(F(" cpj=")); Serial.print(calP2pJitter);
  Serial.print(F(" cfb=")); Serial.print(calFieldBand);
  Serial.print(F(" cfj=")); Serial.print(calFieldJitter);
  Serial.print(F(" bm=")); Serial.print((int)baseMagF);
  Serial.print(F(" ob=")); Serial.print((int)opampBaseF);
  Serial.print(F(" ojb=")); Serial.print((int)opampJitterBaseF);
  Serial.print(F(" ah=")); Serial.print(antennaHold ? 1 : 0);
  Serial.print(F(" mo=")); Serial.print(motionRecent ? 1 : 0);
  Serial.print(F(" st=")); Serial.print(settling ? 1 : 0);
  Serial.print(F(" cal=")); Serial.println(autocal.ready() ? 1 : 0);
  }

  // Classify (raw), then let the debounce decide what to act on. During
  // motion (and for a short settle after it stops) the verdict is held
  // Clear: the change is the device moving, not a wire.
  AlertLevel rawAlert = classifyVerdict(signal, jitterPercent,
                                        motionRecent || settling,
                                        LOW_THRESHOLD, FAULT_THRESHOLD);
  currentAlert = (AlertLevel)debounce.update((int)rawAlert);

  // Handheld: the zero point follows the device. Fast while Clear or
  // settling, slower during Wire, very slow during Fault. The same logic
  // applies to the antenna's resting level and jitter floor.
  float dt = (nowMs - lastFollowMs) / 1000.0;
  lastFollowMs = nowMs;
  if (dt > 1.0) dt = 1.0;

  if (settling || motionRecent) {
    baseMagF += ((float)mag - baseMagF) * (dt / FOLLOW_TAU_SETTLE);
    opampBaseF += (opampAvg - opampBaseF) * (dt / FOLLOW_TAU_SETTLE);
    opampJitterBaseF += (opampJitterAvg - opampJitterBaseF) * (dt / FOLLOW_TAU_SETTLE);
  } else {
    if (qmcOk && !qmcFault) {
      float tau;
      if (currentAlert == ALERT_CLEAR)      tau = FOLLOW_TAU_CLEAR;
      else if (currentAlert == ALERT_WIRE)  tau = FOLLOW_TAU_WIRE;
      else                                  tau = FOLLOW_TAU_ACTIVE;
      baseMagF += ((float)mag - baseMagF) * (dt / tau);
    }
    if (currentAlert == ALERT_CLEAR && !antennaHold) {
      opampBaseF += (opampAvg - opampBaseF) * (dt / FOLLOW_TAU_ANTENNA);
      opampJitterBaseF += (opampJitterAvg - opampJitterBaseF) * (dt / FOLLOW_TAU_ANTENNA_JITTER);
    }
  }

  driveBuzzer();
  updateDisplay(fieldDisplayPct, opampPercent, jitterPercent, fieldErr);
  sendStatusToEsp(signal, fieldPercent, opampPercent, jitterPercent);

}

void beepOnce(int ms) {
  buzzerWrite(true);
  delay(ms);
  buzzerWrite(false);
  delay(20);              // let the supply settle before the next measurement
}

void driveBuzzer() {
  unsigned long now = millis();
  switch (currentAlert) {
    case ALERT_CLEAR:
      break;                                  // buzzer is already off
    case ALERT_WIRE:
      if (now - lastBeepTime >= WIRE_BEEP_GAP) {
        beepOnce(WIRE_BEEP_ON);
        lastBeepTime = millis();
      }
      break;
    case ALERT_FAULT:
      if (now - lastBeepTime >= FAULT_BEEP_GAP) {
        beepOnce(60);
        lastBeepTime = millis();
      }
      break;
  }
}

void updateDisplay(int fieldPercent, int opampPercent, int jitterPercent, bool fieldErr) {
  // Pairing overlay: brief "Paired!" flash, or the code to type on the
  // phone. Sensing continues underneath; the screen returns to normal
  // on its own (3 s flash / 60 s code window).
  if (pairCode.confirmed(millis())) {
    u8g2.firstPage();
    do {
      drawCentered(FONT_BIG, 48, "Paired!");
    } while (u8g2.nextPage());
    return;
  }
  if (pairCode.pending(millis())) {
    const char* code = pairCode.code();
    const bool longCode = strlen(code) > 8;
    u8g2.firstPage();
    do {
      u8g2.setFont(FONT_SMALL);
      u8g2.drawStr(0, 12, "Pair code");
      if (longCode) {
        // 16 digits at 6x10 = 96 px, fits on one line.
        drawCentered(FONT_SMALL, 36, code);
      } else {
        drawCentered(FONT_BIG, 48, code);
      }
      u8g2.setFont(FONT_SMALL);
      u8g2.drawStr(0, 62, "Type on phone");
    } while (u8g2.nextPage());
    return;
  }

  // Normal readings screen.
  u8g2.firstPage();
  do {
    u8g2.setFont(FONT_SMALL);
    u8g2.drawStr(0, 10, "EMF Wire Detector");

    if (!qmcOk) {
      u8g2.drawStr(0, 20, "Field:  n/a");
    } else if (fieldErr) {
      u8g2.drawStr(0, 20, "Field:  ERR");
    } else {
      snprintf_P(oledLine, sizeof(oledLine), PSTR("Field:  %d%%"), fieldPercent);
      u8g2.drawStr(0, 20, oledLine);
    }
    snprintf_P(oledLine, sizeof(oledLine), PSTR("Op-Amp: %d%%"), opampPercent);
    u8g2.drawStr(0, 30, oledLine);
    snprintf_P(oledLine, sizeof(oledLine), PSTR("Jitter: %d%%"), jitterPercent);
    u8g2.drawStr(0, 40, oledLine);

    switch (currentAlert) {
      case ALERT_CLEAR: u8g2.drawStr(0, 50, "Status: Clear");          break;
      case ALERT_WIRE:  u8g2.drawStr(0, 50, "Status: Wire Detected");  break;
      case ALERT_FAULT:
        // "Status: POSSIBLE FAULT" is 132 px > 128 px, so split it.
        u8g2.drawStr(0, 50, "Status: POSSIBLE");
        u8g2.drawStr(0, 60, "FAULT!");
        break;
    }

    // While the hard-iron offsets are still being learned, moving the
    // device can change the field reading - show it honestly. (A live
    // FAULT display takes priority over this hint.)
    if (!autocal.ready() && currentAlert != ALERT_FAULT) {
      u8g2.drawStr(0, 60, "Calibrating...");
    }
  } while (u8g2.nextPage());
}

// Sends a short status line to the ESP32 every ESP_SEND_INTERVAL ms,
// e.g. "WIRE,44,0,44,20" = status,signal,field%,opamp%,jitter%
void sendStatusToEsp(int signal, int fieldPercent, int opampPercent, int jitterPercent) {
  unsigned long now = millis();
  if (now - lastEspSend < (unsigned long)ESP_SEND_INTERVAL) return;
  lastEspSend = now;

  switch (currentAlert) {
    case ALERT_CLEAR: espSerial.print(F("CLEAR")); break;
    case ALERT_WIRE:  espSerial.print(F("WIRE"));  break;
    case ALERT_FAULT: espSerial.print(F("FAULT")); break;
  }
  espSerial.print(F(","));
  espSerial.print(signal);
  espSerial.print(F(","));
  espSerial.print(fieldPercent);
  espSerial.print(F(","));
  espSerial.print(opampPercent);
  espSerial.print(F(","));
  espSerial.println(jitterPercent);
}
