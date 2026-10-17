
#include "BluetoothSerial.h"
#include "esp_bt_main.h"
#include "esp_bt_device.h"
#include "esp_gap_bt_api.h"

BluetoothSerial SerialBT;

HardwareSerial ArduinoLink(2);

void setup() {
  Serial.begin(115200);
  ArduinoLink.begin(9600, SERIAL_8N1, 16, 17);
  SerialBT.begin("EMF_Wire_Detector");

  esp_bt_pin_code_t pinCode = {'1', '2', '3', '4'};
  esp_bt_gap_set_pin(ESP_BT_PIN_TYPE_FIXED, 4, pinCode);

  Serial.println("ESP32 ready. Pair with 'EMF_Wire_Detector' over Bluetooth. PIN: 1234");
}

void loop() {
  if (ArduinoLink.available()) {
    Serial.print("UART2 bytes available: ");
    Serial.println(ArduinoLink.available());
  }

  if (ArduinoLink.available()) {
    String line = ArduinoLink.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) {
      SerialBT.println(line);
      Serial.println(line);
    }
  }

  if (SerialBT.available()) {
    String cmd = SerialBT.readStringUntil('\n');
    cmd.trim();
    if (cmd.length() > 0) {
      Serial.print("From phone: ");
      Serial.println(cmd);
    }
  }
}
