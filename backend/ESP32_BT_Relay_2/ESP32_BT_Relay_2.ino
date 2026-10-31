
// ESP32_BT_Relay - BLE UART (GATT) relay with a 60 s pairing window.
//
// The relay forwards the Arduino Uno's status lines to a bonded phone
// over BLE using the Nordic UART Service (NUS): TX (notify) carries the
// status lines, RX (write) carries phone -> device commands (logged).
// Both characteristics require an encrypted MITM link, so reading data
// is impossible without pairing. The device is LOCKED by default: the
// static passkey is a random secret nobody knows, so a phone shows a
// type-in dialog but cannot guess the code. When the Uno sends "PAIRON"
// over UART2 (button held 2 s, or serial 'p'), the relay opens a 60 s
// pairing window: a fresh 6-digit passkey is installed and sent to the
// Uno as "PIN,<code>" (repeated every 500 ms) for the OLED; the phone
// prompts the user to TYPE that code (MITM static-passkey pairing).
// Wrong-code/aborted retries keep the same code while the window is
// open. When the window closes (bond success or 60 s expiry) a NEW
// random secret is installed and "PINOFF" tells the Uno to clear the
// overlay; on success "PINOK" is sent first. Bonded phones reconnect
// silently (stored LTK - no window, no PINOK).
//
// Security parameters are set through the core's BLE library helpers
// (verified against esp32 core 3.3.11 headers): BLESecurity::setPassKey
// installs the static passkey BOTH as the Bluedroid security parameter
// and as the library state its passkey-request handler answers with;
// the per-characteristic ESP_GATT_PERM_*_ENC_MITM permissions make the
// stack require an authenticated link before RX/TX/CCCD are usable.

#include "BLEDevice.h"
#include "BLEServer.h"
#include "BLESecurity.h"
#include "BLE2902.h"
#include "esp_system.h"   // esp_random

// Nordic UART Service (NUS) UUIDs.
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // phone -> device (write)
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  // device -> phone (notify)

HardwareSerial ArduinoLink(2);

BLECharacteristic* pTxCharacteristic = nullptr;
bool deviceConnected = false;

char windowPin[7];                 // the 6-digit code shown while the window is open
uint32_t windowCode = 0;           // the installed passkey (secret unless the window is open)
volatile bool windowOpen = false;  // written in the security callback, read in loop()
unsigned long windowStartMs = 0;   // when the current window opened
unsigned long pinSentMs = 0;       // last time the code was sent to the Uno
const unsigned long PIN_RESEND_MS = 500;
const unsigned long PIN_TIMEOUT_MS = 60000;

// Installs code as the MITM static passkey. The core's BLE library
// answers the stack's passkey request with exactly this value.
void setPasskey(uint32_t code) {
  BLESecurity::setPassKey(true, code);
}

// Sends a 6-digit code to the Uno for the OLED (and mirrors it on USB).
void sendCodeToUno(const char* code) {
  ArduinoLink.print("PIN,");
  ArduinoLink.println(code);
  Serial.print("Pair code: ");
  Serial.println(code);
}

// Opens the 60 s pairing window. A PAIRON arriving during an open window
// must not reset it, so an already-open window is ignored.
void openPairingWindow() {
  if (windowOpen) return;
  windowCode = esp_random() % 1000000;
  snprintf(windowPin, sizeof(windowPin), "%06lu", (unsigned long)windowCode);
  setPasskey(windowCode);
  windowOpen = true;
  windowStartMs = millis();
  sendCodeToUno(windowPin);
  Serial.print("Pairing window open: ");
  Serial.print(windowPin);
  Serial.println(" (60 s)");
  pinSentMs = millis();
}

// Locks the device again: installs a NEW random secret passkey (never
// printed, never sent to the Uno) and tells the Uno to clear the overlay.
void closePairingWindow() {
  windowCode = esp_random() % 1000000;
  setPasskey(windowCode);
  windowOpen = false;
  ArduinoLink.println("PINOFF");
  Serial.println("Pairing window closed - device locked");
}

// Forwards one status line to the phone over the TX characteristic and
// mirrors it on USB. No client -> no notify.
void forwardToPhone(const String& line) {
  if (deviceConnected && pTxCharacteristic != nullptr) {
    pTxCharacteristic->setValue(line);
    pTxCharacteristic->notify();
  }
}

// Bond/auth outcomes. AUTH_CMPL fires for new pairings AND for bonded
// devices re-encrypting with their stored LTK, so the window state
// distinguishes the two (same semantics as the classic relay).
class RelaySecurityCallbacks : public BLESecurityCallbacks {
public:
  void onAuthenticationComplete(esp_ble_auth_cmpl_t desc) override {
    if (desc.success) {
      if (windowOpen) {
        ArduinoLink.println("PINOK");
        Serial.println("PINOK - pairing completed");
        closePairingWindow();
      } else {
        // A bonded phone reconnected with its stored key: no new pairing
        // happened, so no PINOK and the window state (locked) is left
        // untouched.
        Serial.println("Bonded device connected (stored key - no pairing needed)");
      }
    } else {
      // Failed or aborted attempt (wrong typed code): the window stays
      // open and the same code stays valid until it expires, so a retry
      // inside the window can still succeed.
      Serial.println("Pairing attempt failed");
    }
  }
};

class RelayServerCallbacks : public BLEServerCallbacks {
public:
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("Client connected");
  }

  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("Client disconnected");
  }
};

// Phone -> device commands are logged (forwarding reserved for the future).
class RelayCharacteristicCallbacks : public BLECharacteristicCallbacks {
public:
  void onWrite(BLECharacteristic* pCharacteristic) override {
    String cmd = pCharacteristic->getValue();
    cmd.trim();
    if (cmd.length() > 0) {
      Serial.print("From phone: ");
      Serial.println(cmd);
    }
  }
};

void setup() {
  Serial.begin(115200);
  ArduinoLink.begin(9600, SERIAL_8N1, 16, 17);

  BLEDevice::init("EMF_Wire_Detector");

  // MITM static-passkey pairing: DisplayOnly IO capability plus a static
  // passkey makes the phone show a TYPE-IN dialog; bonding keeps the link
  // usable without a window afterwards.
  BLESecurity::setCapability(ESP_IO_CAP_OUT);
  BLESecurity::setAuthenticationMode(true, true, true);  // bonding + MITM + secure connections
  BLEDevice::setSecurityCallbacks(new RelaySecurityCallbacks());

  // Locked by default: a random secret nobody knows. The secret is never
  // printed or sent to the Uno - only the window code goes out.
  setPasskey(esp_random() % 1000000);
  windowOpen = false;

  BLEServer* pServer = BLEDevice::createServer();
  pServer->setCallbacks(new RelayServerCallbacks());
  pServer->advertiseOnDisconnect(true);

  BLEService* pService = pServer->createService(SERVICE_UUID);

  // RX: phone -> device (write only), encrypted+MITM.
  BLECharacteristic* pRx = pService->createCharacteristic(
      CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE);
  pRx->setAccessPermissions(ESP_GATT_PERM_WRITE_ENC_MITM);
  pRx->setCallbacks(new RelayCharacteristicCallbacks());

  // TX: device -> phone (notify). The CCCD descriptor is gated the same
  // way, so even subscribing requires an authenticated link.
  pTxCharacteristic = pService->createCharacteristic(
      CHARACTERISTIC_UUID_TX, BLECharacteristic::PROPERTY_NOTIFY);
  pTxCharacteristic->setAccessPermissions(ESP_GATT_PERM_READ_ENC_MITM);
  BLE2902* pCccd = new BLE2902();
  pCccd->setAccessPermissions(ESP_GATT_PERM_READ_ENC_MITM | ESP_GATT_PERM_WRITE_ENC_MITM);
  pTxCharacteristic->addDescriptor(pCccd);

  pService->start();

  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // functions that help with iPhone connection issues
  pAdvertising->setMaxPreferred(0x12);
  pAdvertising->start();

  Serial.println("Locked - pairing attempts fail until a pairing window is opened (hold the detector button 2 s or send 'p').");
}

void loop() {
  // Lines from the Uno: "PAIRON" opens the pairing window; every other
  // line is forwarded to the phone (and mirrored on USB).
  if (ArduinoLink.available()) {
    String line = ArduinoLink.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) {
      if (line == "PAIRON") {
        openPairingWindow();
      } else {
        forwardToPhone(line);
        Serial.println(line);
      }
    }
  }

  // Keep the code on the OLED while the window is open; relock after a
  // minute if the pairing was abandoned.
  if (windowOpen) {
    if (millis() - pinSentMs >= PIN_RESEND_MS) {
      sendCodeToUno(windowPin);
      pinSentMs = millis();
    }
    if (millis() - windowStartMs >= PIN_TIMEOUT_MS) {
      closePairingWindow();
    }
  }
}
