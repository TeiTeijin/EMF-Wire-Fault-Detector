/*
  ESP32 Bluetooth Relay - Capstone Project

  Receives status lines from the Arduino Uno over a wired UART
  connection and forwards them to a phone over classic Bluetooth
  (SPP). Works with any generic serial Bluetooth terminal app
  (e.g. "Serial Bluetooth Terminal" on Android).

  WIRING (voltage safety - Arduino is 5V, ESP32 GPIO is 3.3V only):
    Arduino D3 (TX) --[voltage divider down to 3.3V]--> ESP32 GPIO16 (RX2)
    Arduino D2 (RX) <-------------------------------- ESP32 GPIO17 (TX2)
    Arduino GND     <-------------------------------> ESP32 GND  (REQUIRED)

  The ESP32 is powered separately (e.g. power bank via USB) - that's
  fine, as long as the GND wire above is connected so both boards
  share a common reference.

  Expected incoming line format (from Arduino):
    STATUS,signal,hallPct,opampPct,jitterPct
    e.g.  WIRE,44,12,44,20
*/

#include "BluetoothSerial.h"
#include "esp_bt_main.h"
#include "esp_bt_device.h"
#include "esp_gap_bt_api.h"

BluetoothSerial SerialBT;

// Use the ESP32's second hardware serial port for the Arduino link,
// keeping the USB serial (Serial) free for your own debugging.
HardwareSerial ArduinoLink(2); // UART2: RX=16, TX=17 by default

void setup() {
  Serial.begin(115200);                 // USB debug monitor
  ArduinoLink.begin(9600, SERIAL_8N1, 16, 17); // RX2=16, TX2=17, matches Arduino's 9600 baud
  SerialBT.begin("EMF_Wire_Detector");  // Bluetooth device name shown when pairing

  // Force a fixed PIN (1234) for pairing. Some newer Android versions
  // fail to pair with the ESP32's default pairing method otherwise.
  esp_bt_pin_code_t pinCode = {'1', '2', '3', '4'};
  esp_bt_gap_set_pin(ESP_BT_PIN_TYPE_FIXED, 4, pinCode);

  Serial.println("ESP32 ready. Pair with 'EMF_Wire_Detector' over Bluetooth. PIN: 1234");
}

void loop() {
  // --- DEBUG: prove whether ANY bytes are arriving on UART2 at all ---
  if (ArduinoLink.available()) {
    Serial.print("UART2 bytes available: ");
    Serial.println(ArduinoLink.available());
  }

  // Relay: Arduino -> Bluetooth
  if (ArduinoLink.available()) {
    String line = ArduinoLink.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) {
      SerialBT.println(line);   // send to phone
      Serial.println(line);     // also show on USB monitor for debugging
    }
  }

  // Optional: let the phone send simple commands back to the Arduino
  // (e.g. typing "CAL" in the Bluetooth terminal app could trigger
  // something later if you want two-way control)
  if (SerialBT.available()) {
    String cmd = SerialBT.readStringUntil('\n');
    cmd.trim();
    if (cmd.length() > 0) {
      Serial.print("From phone: ");
      Serial.println(cmd);
      // ArduinoLink.println(cmd); // uncomment to forward commands back
    }
  }
}
