#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>

// ================= WIFI HOTSPOT =================
const char* ssid = "Galaxy A04e002a";
const char* password = "123456789";

// ================= FIREBASE =================
#define DATABASE_URL "https://electricfence-1487b-default-rtdb.asia-southeast1.firebasedatabase.app"
#define API_KEY "AIzaSyCB6SKslQk1B_EoaSpZvArrxETnZ6xtLYU"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
bool signupOK = false;

// Path Firebase (Silakan sesuaikan dengan struktur Realtime Database Anda)
String pathControlRelay = "/ElectricFence/Control/Relay";
String pathStatusRelay = "/ElectricFence/Status/Relay";
String pathStatusAlarm = "/ElectricFence/Status/Alarm";
String pathStatusPulse = "/ElectricFence/Status/PulseLost";
String pathDataCurrent = "/ElectricFence/Data/Current";
String pathDataPulseCount = "/ElectricFence/Data/PulseCount";

// ================= PIN =================
#define ACS_PIN       34
#define RELAY_PIN     26
#define PULSE_PIN     27
#define ALARM_PIN     25

// ================= RELAY =================
#define RELAY_ON   LOW
#define RELAY_OFF  HIGH

// ================= ACS758 =================
float sensitivity = 0.040;
float acsOffset = 0.0;

// ================= PULSE =================
volatile unsigned long pulseCount = 0;
volatile bool pulseDetected = false;
volatile unsigned long lastPulseTime = 0;
volatile unsigned long previousPulseTime = 0;
volatile float pulseInterval = 0.0;

portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;

// ================= TIMER =================
unsigned long lastPrint = 0;
unsigned long lastNotifTime = 0;

// ================= STATUS =================
bool relayStatus = false;
bool lastAlarmStatus = false;
bool lastPulseLost = false;

// ================= INTERRUPT PULSE =================
void IRAM_ATTR pulseISR() {
  unsigned long now = millis();

  portENTER_CRITICAL_ISR(&mux);

  if (lastPulseTime != 0) {
    pulseInterval = (now - lastPulseTime) / 1000.0;
  }

  previousPulseTime = lastPulseTime;
  lastPulseTime = now;

  pulseCount++;
  pulseDetected = true;

  portEXIT_CRITICAL_ISR(&mux);
}

// ================= WIFI =================
void connectWiFi() {
  Serial.print("Menghubungkan ke WiFi");

  WiFi.begin(ssid, password);

  int retry = 0;
  while (WiFi.status() != WL_CONNECTED && retry < 30) {
    delay(500);
    Serial.print(".");
    retry++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi Terhubung");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\nWiFi gagal terhubung");
  }
}

// ================= FIREBASE =================
void connectFirebase() {
  Serial.println("Menghubungkan ke Firebase...");
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  if (Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("Firebase Terhubung (Signup OK)");
    signupOK = true;
  } else {
    Serial.printf("Firebase error: %s\n", config.signer.signupError.message.c_str());
  }

  config.token_status_callback = tokenStatusCallback;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}

// ================= NOTIF SEMENTARA =================
void sendNotif(String message) {
  Serial.println("NOTIFIKASI:");
  Serial.println(message);
}

// ================= RELAY =================
void relayOn() {
  digitalWrite(RELAY_PIN, RELAY_ON);
  relayStatus = true;
}

void relayOff() {
  digitalWrite(RELAY_PIN, RELAY_OFF);
  relayStatus = false;
}

// ================= KALIBRASI ACS =================
void calibrateACS() {
  long total = 0;

  Serial.println("Kalibrasi ACS758...");
  Serial.println("Pastikan beban utama belum aktif saat kalibrasi.");

  for (int i = 0; i < 500; i++) {
    total += analogRead(ACS_PIN);
    delay(2);
  }

  float adc = total / 500.0;
  acsOffset = adc * 3.3 / 4095.0;

  Serial.print("Offset ACS758: ");
  Serial.print(acsOffset, 3);
  Serial.println(" V");
}

// ================= BACA ARUS =================
float readCurrent() {
  long total = 0;

  for (int i = 0; i < 50; i++) {
    total += analogRead(ACS_PIN);
    delay(1);
  }

  float adc = total / 50.0;
  float voltage = adc * 3.3 / 4095.0;

  float current = abs((voltage - acsOffset) / sensitivity);

  if (current < 0.10) {
    current = 0.0;
  }

  return current;
}

// ================= SETUP =================
void setup() {
  Serial.begin(115200);

  pinMode(RELAY_PIN, OUTPUT);
  pinMode(PULSE_PIN, INPUT_PULLUP);
  pinMode(ALARM_PIN, INPUT_PULLUP);

  relayOff();

  attachInterrupt(digitalPinToInterrupt(PULSE_PIN), pulseISR, FALLING);

  delay(1000);

  Serial.println("================================");
  Serial.println(" ELECTRIC FENCE IOT - WIFI MODE");
  Serial.println("================================");

  connectWiFi();
  connectFirebase();
  calibrateACS();

  portENTER_CRITICAL(&mux);
  lastPulseTime = millis();
  portEXIT_CRITICAL(&mux);

  Serial.println("Ketik perintah:");
  Serial.println("ON");
  Serial.println("OFF");
  Serial.println("STATUS");
}

// ================= LOOP =================
void loop() {
  unsigned long now = millis();

  bool alarmActive = digitalRead(ALARM_PIN) == LOW;

  unsigned long safePulseCount;
  unsigned long safeLastPulseTime;
  bool safePulseDetected;
  float safePulseInterval;

  portENTER_CRITICAL(&mux);
  safePulseCount = pulseCount;
  safeLastPulseTime = lastPulseTime;
  safePulseDetected = pulseDetected;
  safePulseInterval = pulseInterval;
  pulseDetected = false;
  portEXIT_CRITICAL(&mux);

  bool pulseLost = (now - safeLastPulseTime > 5000);

  float current = readCurrent();

  // ================= TAMPILKAN PULSE LANGSUNG =================
  if (safePulseDetected) {
    Serial.print("PULSE TERDETEKSI | Count: ");
    Serial.print(safePulseCount);
    Serial.print(" | Jeda: ");
    Serial.print(safePulseInterval, 2);
    Serial.println(" detik");
  }

  // ================= SERIAL COMMAND =================
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    cmd.toUpperCase();

    Serial.print("Perintah diterima: ");
    Serial.println(cmd);

    if (cmd == "ON") {
      relayOn();
      Serial.println("RELAY DINYALAKAN");
    }

    else if (cmd == "OFF") {
      relayOff();
      Serial.println("RELAY DIMATIKAN");
    }

    else if (cmd == "STATUS") {
      Serial.println("========== STATUS ==========");
      Serial.print("WiFi        : ");
      Serial.println(WiFi.status() == WL_CONNECTED ? "TERHUBUNG" : "TERPUTUS");

      Serial.print("Relay       : ");
      Serial.println(relayStatus ? "ON" : "OFF");

      Serial.print("Alarm       : ");
      Serial.println(alarmActive ? "AKTIF" : "NORMAL");

      Serial.print("Pulse       : ");
      Serial.println(pulseLost ? "HILANG" : "NORMAL");

      Serial.print("Pulse Count : ");
      Serial.println(safePulseCount);

      Serial.print("Jeda Pulse  : ");
      Serial.print(safePulseInterval, 2);
      Serial.println(" detik");

      Serial.print("Arus ACS758 : ");
      Serial.print(current, 3);
      Serial.println(" A");

      Serial.println("============================");
    }

    else {
      Serial.println("Perintah tidak dikenali.");
      Serial.println("Gunakan: ON / OFF / STATUS");
    }
  }

  // ================= FIREBASE UPDATE (SETIAP 2 DETIK) =================
  static unsigned long lastFirebaseUpdate = 0;
  if (Firebase.ready() && signupOK && (now - lastFirebaseUpdate >= 2000)) {
    lastFirebaseUpdate = now;

    // 1. Baca Perintah Relay dari Firebase
    if (Firebase.RTDB.getBool(&fbdo, pathControlRelay)) {
      if (fbdo.dataType() == "boolean") {
        bool fbRelay = fbdo.boolData();
        if (fbRelay && !relayStatus) {
           relayOn();
           Serial.println("RELAY DINYALAKAN via Firebase");
        } else if (!fbRelay && relayStatus) {
           relayOff();
           Serial.println("RELAY DIMATIKAN via Firebase");
        }
      }
    }

    // 2. Kirim Data ke Firebase
    Firebase.RTDB.setBool(&fbdo, pathStatusRelay, relayStatus);
    Firebase.RTDB.setBool(&fbdo, pathStatusAlarm, alarmActive);
    Firebase.RTDB.setBool(&fbdo, pathStatusPulse, pulseLost);
    Firebase.RTDB.setFloat(&fbdo, pathDataCurrent, current);
    Firebase.RTDB.setInt(&fbdo, pathDataPulseCount, safePulseCount);
  }

  // ================= PRINT STATUS SETIAP 1 DETIK =================
  if (now - lastPrint >= 1000) {
    lastPrint = now;

    Serial.println("================================");
    Serial.print("WiFi        : ");
    Serial.println(WiFi.status() == WL_CONNECTED ? "TERHUBUNG" : "TERPUTUS");

    Serial.print("Relay       : ");
    Serial.println(relayStatus ? "ON" : "OFF");

    Serial.print("Alarm       : ");
    Serial.println(alarmActive ? "AKTIF" : "NORMAL");

    Serial.print("Pulse       : ");
    Serial.println(pulseLost ? "HILANG" : "NORMAL");

    Serial.print("Pulse Count : ");
    Serial.println(safePulseCount);

    Serial.print("Jeda Pulse  : ");
    Serial.print(safePulseInterval, 2);
    Serial.println(" detik");

    Serial.print("Arus ACS758 : ");
    Serial.print(current, 3);
    Serial.println(" A");
  }

  // ================= NOTIF ALARM =================
  if (alarmActive && !lastAlarmStatus) {
    if (now - lastNotifTime > 30000) {
      sendNotif("PERINGATAN: Alarm internal energizer aktif.");
      lastNotifTime = now;
    }
  }

  // ================= NOTIF PULSE HILANG =================
  if (pulseLost && !lastPulseLost) {
    if (now - lastNotifTime > 30000) {
      sendNotif("PERINGATAN: Pulse output pagar hilang.");
      lastNotifTime = now;
    }
  }

  lastAlarmStatus = alarmActive;
  lastPulseLost = pulseLost;

  delay(50);
}