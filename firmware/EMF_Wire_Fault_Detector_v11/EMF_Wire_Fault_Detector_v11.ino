/*
  EMF Wire Fault Detector - Capstone Project (v4)
  Hardware: Arduino Uno
    - Op-amp output (antenna + cap + LM358 gain stage)  -> A0
    - QMC5883P magnetometer (I2C, address 0x2C)         -> A4 = SDA, A5 = SCL
    - SSD1306 OLED 128x64 (I2C, address 0x3C)           -> same A4 = SDA, A5 = SCL
    - 3-pin active buzzer module (VCC, GND, I/O)        -> pin 8
    - Push button (manual recalibration)                -> pin 7 (other leg to GND)
    - Link to ESP32 for Bluetooth: D2 (RX) / D3 (TX, through voltage
      divider) via SoftwareSerial

  The OLED and the QMC5883P share the same two I2C wires. That works
  because they have different addresses (0x3C and 0x2C).

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

  HANDHELD USE: the first time it powers up (or when you hold the button
  while powering on) you turn the device slowly in all directions for a few
  seconds. That finds the board's own fixed magnetism so tilting it stops
  changing the field reading, and it is SAVED, so you only do it once after
  the device is assembled. After that the field zero point slowly follows
  the device, so moving around re-zeroes itself, while quickly moving toward
  a wire shows up as a rise.

  Press the push button (if you have one) or type c in the Serial Monitor to
  re-run calibration. Type t to redo the turning step.

  IMPORTANT SAFETY NOTE:
  Never let the antenna wire or your hands touch exposed/live house
  wiring. Sense from a safe distance through the wall/insulation only.
  For fault-vs-normal testing, use a low-voltage source or a
  de-energized frayed cord, never live mains current.

  Requires libraries (Library Manager):
    - Adafruit GFX Library
    - Adafruit SSD1306
  (The QMC5883P is read directly over Wire, no extra library needed.)
*/

#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <SoftwareSerial.h>
#include <EEPROM.h>

// Link to ESP32: Arduino D3 (TX, through voltage divider) -> ESP32 RX
//                Arduino D2 (RX) <- ESP32 TX (direct, no divider needed)
SoftwareSerial espSerial(2, 3); // RX, TX
unsigned long lastEspSend = 0;
const int ESP_SEND_INTERVAL = 500; // ms

// ---------- OLED CONFIG ----------
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET   -1
#define OLED_ADDR    0x3C   // try 0x3D if this doesn't init
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// ---------- QMC5883P ----------
#define QMC_ADDR 0x2C
bool qmcOk = false;
bool qmcFault = false;     // true while the chip is not giving fresh readings
int qmcFailCount = 0;      // consecutive failed reads
int qmcSameCount = 0;      // consecutive identical readings (a frozen chip)
int qmcReinits = 0;        // how many times the chip had to be restarted

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

// The board's own fixed magnetic field, found by turning it around at power-up.
const int ROTATE_CAL_SECONDS = 8;     // set to 0 to skip the turning step
int offX = 0, offY = 0, offZ = 0;

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
int prevFieldDelta = 0;
int prevX = 0, prevY = 0, prevZ = 0;
int prevMag = 0;

// ---------- SENSITIVITY SCALES ----------
// These are NOT baselines. They only set how much change (beyond the
// calibrated rest and wobble) counts as 100%. Lower = more sensitive.
const int OPAMP_MAX_DEVIATION = 150;   // antenna: p2p counts above the calibration ceiling
const int OPAMP_MAX_JITTER    = 150;   // antenna: extra jumpiness
// Magnetometer: change in field STRENGTH (counts) that counts as 100%. A wire only adds to
// the strength along the direction of the Earth's field, so expect a few hundred counts at most.
const int FIELD_MAX_DELTA     = 250;
const int FIELD_MAX_JITTER    = 100;

// Handheld: how fast the field zero point follows the device (time constant, seconds).
// Short while Clear, so moving around re-zeroes itself. Long while something is detected,
// so a real detection does not fade away at once.
const float FOLLOW_TAU_CLEAR  = 15.0;
const float FOLLOW_TAU_ACTIVE = 120.0;
unsigned long lastFollowMs = 0;

// ---------- THRESHOLDS (0-100% scale) ----------
const int LOW_THRESHOLD    = 15;  // below this: nothing detected
const int FAULT_THRESHOLD  = 50;  // jitter % at/above this: possible fault

// ---------- STATE ----------
enum AlertLevel { ALERT_CLEAR, ALERT_WIRE, ALERT_FAULT };
AlertLevel currentAlert = ALERT_CLEAR;

unsigned long lastBeepTime = 0;
const int WIRE_BEEP_ON  = 100;  // ms, short pulse for "wire detected"
const int WIRE_BEEP_GAP = 800;  // ms between pulses
const int FAULT_BEEP_GAP = 150; // ms, fast urgent beep for possible fault

// ---------- BUTTON DEBOUNCE ----------
bool lastButtonState = HIGH;      // HIGH = not pressed (INPUT_PULLUP)
unsigned long lastButtonChangeTime = 0;
const unsigned long DEBOUNCE_DELAY = 50; // ms

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
  writeQmc(0x0B, 0x08);   // control 2: +/-8 G range, SET/RESET on
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
int fieldMagnitude(int x, int y, int z) {
  float fx = (float)x - offX, fy = (float)y - offY, fz = (float)z - offZ;
  float m = sqrt(fx * fx + fy * fy + fz * fz);
  return (int)constrain(m, 0.0, 30000.0);
}

// Turn the device slowly in all directions; the centre of the readings on each axis
// is the board's own fixed field (steel, magnets, buzzer), which we then subtract.
void rotationCalibration() {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println(F("Turn the device"));
  display.println(F("slowly in ALL"));
  display.println(F("directions"));
  display.println(F("(like a figure 8)"));
  display.display();
  delay(2000);

  int minX = 32767, maxX = -32768;
  int minY = 32767, maxY = -32768;
  int minZ = 32767, maxZ = -32768;
  unsigned long start = millis();
  int lastShown = -1;
  while (millis() - start < (unsigned long)ROTATE_CAL_SECONDS * 1000UL) {
    int x, y, z;
    if (readQmc(x, y, z)) {
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
      if (z < minZ) minZ = z;
      if (z > maxZ) maxZ = z;
    }
    int left = ROTATE_CAL_SECONDS - (int)((millis() - start) / 1000UL);
    if (left != lastShown) {
      lastShown = left;
      display.clearDisplay();
      display.setCursor(0, 0);
      display.println(F("Keep turning it"));
      display.println(F("in ALL directions"));
      display.setTextSize(3);
      display.setCursor(50, 30);
      display.println(left);
      display.setTextSize(1);
      display.display();
    }
    delay(20);
  }

  // Only trust an axis that was really swept through the Earth's field.
  const long MIN_SWEEP = 600;
  offX = (((long)maxX - minX) >= MIN_SWEEP) ? (int)(((long)maxX + minX) / 2) : 0;
  offY = (((long)maxY - minY) >= MIN_SWEEP) ? (int)(((long)maxY + minY) / 2) : 0;
  offZ = (((long)maxZ - minZ) >= MIN_SWEEP) ? (int)(((long)maxZ + minZ) / 2) : 0;

  Serial.print(F("Board field offset XYZ=")); Serial.print(offX);
  Serial.print(F(" ")); Serial.print(offY);
  Serial.print(F(" ")); Serial.print(offZ);
  Serial.print(F("  sweep XYZ=")); Serial.print((long)maxX - minX);
  Serial.print(F(" ")); Serial.print((long)maxY - minY);
  Serial.print(F(" ")); Serial.println((long)maxZ - minZ);

  // Save it only if at least two axes were really swept through the Earth's field.
  int swept = (((long)maxX - minX) >= MIN_SWEEP) + (((long)maxY - minY) >= MIN_SWEEP)
            + (((long)maxZ - minZ) >= MIN_SWEEP);
  if (swept >= 2) {
    saveOffsets();
    Serial.println(F("Saved - no need to turn it again"));
  } else {
    Serial.println(F("Not turned enough - not saved, it will ask again next power-up"));
  }
}

// Remember the board's own field so the turning step is only needed once.
// EEPROM layout: byte 0 = marker, then offX, offY, offZ (2 bytes each).
const byte OFFSET_MARKER = 0xA5;

void saveOffsets() {
  EEPROM.update(0, OFFSET_MARKER);
  EEPROM.put(1, offX);
  EEPROM.put(3, offY);
  EEPROM.put(5, offZ);
}

bool loadOffsets() {
  if (EEPROM.read(0) != OFFSET_MARKER) return false;
  EEPROM.get(1, offX);
  EEPROM.get(3, offY);
  EEPROM.get(5, offZ);
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

  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR)) {
    Serial.println(F("SSD1306 not found - check wiring/address"));
    while (true); // halt
  }

#ifdef WIRE_HAS_TIMEOUT
  Wire.setWireTimeout(3000, true);   // a stuck I2C bus must not freeze the sketch
#endif
  qmcOk = qmcInit();
  if (!qmcOk) {
    Serial.println(F("QMC5883P not found at 0x2C - check SDA/SCL (A4/A5). Running antenna-only."));
  }

  // The turning step only runs if nothing is saved yet, or if the button is held at power-up.
  bool needTurn = !loadOffsets() || digitalRead(buttonPin) == LOW;
  runCalibrationSequence(5, needTurn);
}

// Shared calibration routine - used both on startup and when the
// button is pressed later.
void runCalibrationSequence(int countdownSeconds, bool rotate) {
  if (rotate && ROTATE_CAL_SECONDS > 0 && qmcOk) rotationCalibration();
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println(F("EMF Wire Detector"));
  display.println(F("Hold it the way"));
  display.println(F("you will use it"));
  display.display();

  for (int s = countdownSeconds; s > 0; s--) {
    display.clearDisplay();
    display.setCursor(0, 0);
    display.println(F("Calibrating in:"));
    display.setTextSize(3);
    display.setCursor(50, 25);
    display.println(s);
    display.setTextSize(1);
    display.display();
    delay(1000);
  }

  display.clearDisplay();
  display.setCursor(0, 0);
  display.println(F("Calibrating..."));
  display.println(F("Keep holding it"));
  display.println(F("the same way"));
  display.display();

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

  display.clearDisplay();
  display.setCursor(0, 0);
  display.println(F("Ready"));
  display.display();
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
  prevFieldDelta = 0;

  Serial.print(F("Calibrated antenna: mean p2p=")); Serial.print(baselineOpamp);
  Serial.print(F(" ceiling=")); Serial.print(calP2pCeil);
  Serial.print(F(" jitter=")); Serial.println(calP2pJitter);
  Serial.print(F("Calibrated field: strength=")); Serial.print(baseMag);
  Serial.print(F(" band=")); Serial.print(calFieldBand);
  Serial.print(F(" jitter=")); Serial.println(calFieldJitter);
}

// Updates a rolling average and returns the average.
int smoothAverage(int rawValue, int *readings, long *total) {
  *total -= readings[smoothIndex];
  readings[smoothIndex] = rawValue;
  *total += rawValue;
  return *total / SMOOTH_SAMPLES;
}

// Checks the button with simple debounce. Returns true exactly once
// per physical press (on the press-down edge), not while held.
bool checkButtonPressed() {
  bool reading = digitalRead(buttonPin);
  bool pressedEdge = false;

  if (reading != lastButtonState) {
    lastButtonChangeTime = millis();
  }

  if ((millis() - lastButtonChangeTime) > DEBOUNCE_DELAY) {
    if (reading == LOW && lastButtonState == HIGH) {
      pressedEdge = true; // just went from not-pressed to pressed
    }
  }

  lastButtonState = reading;
  return pressedEdge;
}

void loop() {
  // Commands from the Serial Monitor (no button needed):
  //   c = recalibrate,  t = redo the turning step and recalibrate
  char cmd = 0;
  if (Serial.available()) cmd = Serial.read();

  if (cmd == 't' || cmd == 'T') {
    Serial.println(F("Turn + recalibrate"));
    runCalibrationSequence(3, true);
    return;
  }
  if (checkButtonPressed() || cmd == 'c' || cmd == 'C') {
    Serial.println(F("Recalibrating"));
    runCalibrationSequence(3, false);
    return;
  }

  // --- Antenna / op-amp path ---
  int p2p = measureP2P();
  int opampAvg    = smoothAverage(p2p, opampReadings, &opampTotal);
  int opampSignal = max(0, opampAvg - calP2pCeil);        // only a rise beyond the calibration wobble
  int p2pJump     = abs(p2p - prevP2P);
  prevP2P = p2p;
  int opampJitterAvg    = smoothAverage(p2pJump, jitterReadings, &jitterTotal);
  int opampJitterExcess = max(0, opampJitterAvg - calP2pJitter);  // only extra jumpiness

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
    } else {
      qmcFailCount++;
    }

    qmcFault = (qmcFailCount > 0) || (qmcSameCount >= 5);
    if (qmcFault) { fieldDelta = 0; fieldJump = 0; }

    // Chip failed or froze (for example after a power glitch): restart it.
    if (qmcFailCount >= 3 || qmcSameCount >= 20) {
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
  Serial.print(F(" jitterPct=")); Serial.println(jitterPercent);
  }

  // Classify
  if (signal < LOW_THRESHOLD) {
    currentAlert = ALERT_CLEAR;
  } else if (jitterPercent >= FAULT_THRESHOLD) {
    currentAlert = ALERT_FAULT;
  } else {
    currentAlert = ALERT_WIRE;
  }

  // Handheld: the zero point follows the device. Fast while Clear, very slow while detecting.
  unsigned long nowMs = millis();
  float dt = (nowMs - lastFollowMs) / 1000.0;
  lastFollowMs = nowMs;
  if (dt > 1.0) dt = 1.0;
  if (qmcOk && !qmcFault) {
    float tau = (currentAlert == ALERT_CLEAR) ? FOLLOW_TAU_CLEAR : FOLLOW_TAU_ACTIVE;
    baseMagF += ((float)mag - baseMagF) * (dt / tau);
  }

  driveBuzzer();
  updateDisplay(fieldPercent, opampPercent, jitterPercent);
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

void updateDisplay(int fieldPercent, int opampPercent, int jitterPercent) {
  display.clearDisplay();
  display.setCursor(0, 0);
  display.setTextSize(1);
  display.println(F("EMF Wire Detector"));

  display.print(F("Field:  "));
  if (!qmcOk)        { display.println(F("n/a")); }
  else if (qmcFault) { display.println(F("ERR")); }
  else               { display.print(fieldPercent); display.println(F("%")); }
  display.print(F("Op-Amp: ")); display.print(opampPercent); display.println(F("%"));
  display.print(F("Jitter: ")); display.print(jitterPercent); display.println(F("%"));

  display.print(F("Status: "));
  switch (currentAlert) {
    case ALERT_CLEAR: display.println(F("Clear"));           break;
    case ALERT_WIRE:  display.println(F("Wire Detected"));   break;
    case ALERT_FAULT: display.println(F("POSSIBLE FAULT!")); break;
  }

  display.display();
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
