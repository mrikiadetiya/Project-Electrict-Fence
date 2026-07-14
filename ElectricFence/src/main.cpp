#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <TinyGPSPlus.h>
#include <math.h>

// ============================================================
// WIFI MODEM / ROUTER 4G
// ============================================================
const char WIFI_SSID[] = "tselhome-6D9C";
const char WIFI_PASSWORD[] = "N61006FNR9G";

// ============================================================
// FIREBASE
// ============================================================
#define DATABASE_URL "https://electricfence-1487b-default-rtdb.asia-southeast1.firebasedatabase.app"
#define DATABASE_SECRET "RovQ4vMYkpTHchd2o0TRp9F0gqViXoVLilrGLeAA"

const String BASE_PATH = "/ElectricFence";

// Database Secret dipakai langsung melalui parameter ?auth=.
// Tidak perlu login Email/Password dan tidak perlu header Bearer panjang.
bool firebaseAuthenticated = false;

// ============================================================
// PIN
// ============================================================
#define ACS_PIN 34
#define RELAY_PIN 26
#define PULSE_PIN 27
#define ALARM_PIN 25

#define GPS_RX 33
#define GPS_TX 32

#define RELAY_ON HIGH
#define RELAY_OFF LOW

// ============================================================
// SERIAL
// ============================================================
HardwareSerial gpsSerial(1);
TinyGPSPlus gps;

// ============================================================
// STATUS WIFI / FIREBASE
// ============================================================
bool wifiConnected = false;
bool httpBusy = false;
String wifiStatus = "BELUM TERHUBUNG";
String firebaseStatus = "BELUM TERHUBUNG";
int lastHttpStatus = 0;

// ============================================================
// RELAY / PROTEKSI
// ============================================================
// relayCommandOn = perintah pengguna/aplikasi.
// relayStatus    = kondisi keluaran relay yang benar-benar sedang aktif.
bool relayCommandOn = false;
bool relayStatus = false;

// Otomatis OFF hanya boleh dipicu oleh alarm.
// Pulse hilang, arus tidak normal, GPS, jaringan, dan Firebase error
// hanya menghasilkan status/notifikasi dan tidak mematikan relay.
bool automaticOffByAlarmOnly = true;

bool contactDetected = false;
bool protectionActive = false;
bool manualLockAfterRetry = false;
bool syncControlOffPending = false;

int retryCount = 0;
const int MAX_RETRY = 3;

unsigned long relayOnTime = 0;
unsigned long relayOffTime = 0;
unsigned long contactStartTime = 0;
unsigned long protectionActionTime = 0;

const unsigned long CONTACT_DETECT_TIME = 200;
const unsigned long AUTO_OFF_TIME = 2500;
const unsigned long STARTUP_GRACE_TIME = 5000;
const unsigned long RETRY_REARM_TIME = 1000;

// ============================================================
// MODE UJI RELAY
// ============================================================
// true  = alarm diabaikan sementara selama TEST_IGNORE_ALARM_TIME
//         setelah relay ON. Hanya untuk mencari penyebab relay langsung OFF.
// false = proteksi alarm kembali bekerja normal.
const bool TEST_IGNORE_ALARM_AFTER_ON = true;
const unsigned long TEST_IGNORE_ALARM_TIME = 30000UL; // 30 detik

String outputStatus = "MENUNGGU";
String protectionStatus = "NORMAL";
String lastNotification = "";
String lastNotificationType = "";

// ============================================================
// ACS758
// ============================================================
float sensitivity = 0.040;
float acsOffset = 0.0;
float current = 0.0;

// ============================================================
// PULSE
// ============================================================
volatile unsigned long pulseCount = 0;
volatile bool pulseDetected = false;
volatile unsigned long lastPulseTime = 0;
volatile float pulseInterval = 0.0;
volatile unsigned long lastInterruptTime = 0;

portMUX_TYPE pulseMux = portMUX_INITIALIZER_UNLOCKED;

const unsigned long PULSE_DEBOUNCE_TIME = 80;

// Normal energizer sekitar 1.3 detik.
// 6.5 detik memberi toleransi beberapa pulse pickup yang sesekali terlewat.
const unsigned long PULSE_LOST_TIME = 6500;

const float MIN_NORMAL_INTERVAL = 0.80;
const float MAX_NORMAL_INTERVAL = 1.80;
const float TOO_FAST_INTERVAL = 0.25;
const float MISSED_PULSE_MAX_INTERVAL = 3.80;

int consecutiveFastPulses = 0;
int estimatedMissedPulses = 0;
bool continuousPulseConfirmed = false;

// ============================================================
// GPS
// ============================================================
double gpsLat = 0.0;
double gpsLng = 0.0;
int gpsSat = 0;
bool gpsValid = false;

double homeLat = 0.0;
double homeLng = 0.0;
bool homeSet = false;

double gpsDistance = 0.0;
bool theftDetected = false;
const double THEFT_DISTANCE_LIMIT = 20.0;

// ============================================================
// TIMER
// ============================================================
unsigned long lastPrint = 0;
unsigned long lastFirebaseRead = 0;
unsigned long lastFirebaseWrite = 0;
unsigned long lastWifiReconnectAttempt = 0;
const unsigned long WIFI_RECONNECT_INTERVAL = 15000;

unsigned long lastNetworkHealthCheck = 0;
const unsigned long NETWORK_HEALTH_INTERVAL = 30000;

unsigned long lastNotifTime = 0;
unsigned long lastContinuousNotifTime = 0;
unsigned long lastPulseLostNotifTime = 0;

const unsigned long FIREBASE_READ_INTERVAL = 10000;
const unsigned long FIREBASE_WRITE_INTERVAL = 10000;
const unsigned long PRINT_INTERVAL = 5000;

// ============================================================
// INTERRUPT PULSE
// ============================================================
void IRAM_ATTR pulseISR() {
  unsigned long now = millis();

  if (now - lastInterruptTime < PULSE_DEBOUNCE_TIME) {
    return;
  }

  lastInterruptTime = now;

  portENTER_CRITICAL_ISR(&pulseMux);

  if (lastPulseTime != 0) {
    pulseInterval = (now - lastPulseTime) / 1000.0f;
  }

  lastPulseTime = now;
  pulseCount++;
  pulseDetected = true;

  portEXIT_CRITICAL_ISR(&pulseMux);
}

// ============================================================
// GPS
// ============================================================
void readGPS() {
  while (gpsSerial.available()) {
    gps.encode(gpsSerial.read());
  }

  gpsValid = gps.location.isValid();
  gpsSat = gps.satellites.isValid() ? gps.satellites.value() : 0;

  if (gpsValid) {
    gpsLat = gps.location.lat();
    gpsLng = gps.location.lng();
  }
}

double distanceMeter(double lat1, double lon1,
                     double lat2, double lon2) {
  const double earthRadius = 6371000.0;

  double dLat = radians(lat2 - lat1);
  double dLon = radians(lon2 - lon1);

  lat1 = radians(lat1);
  lat2 = radians(lat2);

  double a =
    sin(dLat / 2.0) * sin(dLat / 2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0);

  return earthRadius * 2.0 * atan2(sqrt(a), sqrt(1.0 - a));
}

void setHomeLocationFromGPS() {
  Serial.println("===== SET HOME =====");
  Serial.printf("GPS: %s | Satelit: %d | Lat/Lng: %.6f, %.6f\n",
                gpsValid ? "VALID" : "BELUM FIX",
                gpsSat,
                gpsLat,
                gpsLng);

  if (!gpsValid || gpsLat == 0.0 || gpsLng == 0.0) {
    Serial.println("GPS belum valid. Lokasi awal belum bisa disimpan.");
    return;
  }

  homeLat = gpsLat;
  homeLng = gpsLng;
  homeSet = true;
  gpsDistance = 0.0;
  theftDetected = false;

  Serial.println("Lokasi awal GPS berhasil disimpan.");
}

void resetHomeLocation() {
  homeLat = 0.0;
  homeLng = 0.0;
  homeSet = false;
  gpsDistance = 0.0;
  theftDetected = false;

  Serial.println("Lokasi awal GPS di-reset.");
}

void checkTheftGPS() {
  if (!gpsValid || !homeSet) {
    gpsDistance = 0.0;
    theftDetected = false;
    return;
  }

  gpsDistance =
    distanceMeter(homeLat, homeLng, gpsLat, gpsLng);

  theftDetected =
    gpsDistance > THEFT_DISTANCE_LIMIT;
}

// ============================================================
// UTILITAS STRING / JSON
// ============================================================
String jsonEscape(const String &input) {
  String output;
  output.reserve(input.length() + 16);

  for (size_t i = 0; i < input.length(); i++) {
    char c = input[i];

    switch (c) {
      case '\\': output += "\\\\"; break;
      case '"': output += "\\\""; break;
      case '\n': output += "\\n"; break;
      case '\r': output += "\\r"; break;
      case '\t': output += "\\t"; break;
      default: output += c; break;
    }
  }

  return output;
}

bool extractJsonBool(const String &json,
                     const String &key,
                     bool defaultValue) {
  String marker = "\"" + key + "\":";
  int index = json.indexOf(marker);

  if (index < 0) {
    return defaultValue;
  }

  index += marker.length();

  while (index < json.length() && (json[index] == ' ' || json[index] == '\r' || json[index] == '\n')) {
    index++;
  }

  if (json.startsWith("true", index)) {
    return true;
  }

  if (json.startsWith("false", index)) {
    return false;
  }

  return defaultValue;
}

// ============================================================
// WIFI MODEM / ROUTER 4G
// ============================================================
void connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    wifiStatus = "TERHUBUNG";
    firebaseAuthenticated = true;
    firebaseStatus = "TERHUBUNG";
    return;
  }

  Serial.println("================================");
  Serial.println("MENGHUBUNGKAN ESP32 KE MODEM WIFI");
  Serial.print("SSID: ");
  Serial.println(WIFI_SSID);
  Serial.println("================================");

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  unsigned long start = millis();

  while (WiFi.status() != WL_CONNECTED &&
         millis() - start < 30000UL) {
    readGPS();
    delay(500);
    Serial.print(".");
  }

  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    wifiStatus = "TERHUBUNG";
    firebaseAuthenticated = true;
    firebaseStatus = "TERHUBUNG";

    Serial.println("WiFi modem berhasil terhubung.");
    Serial.print("IP ESP32: ");
    Serial.println(WiFi.localIP());
    Serial.print("RSSI WiFi: ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
  } else {
    wifiConnected = false;
    wifiStatus = "GAGAL TERHUBUNG";
    firebaseAuthenticated = false;
    firebaseStatus = "WIFI TERPUTUS";

    Serial.println("WiFi modem gagal terhubung.");
  }
}

void maintainWiFi() {
  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    wifiStatus = "TERHUBUNG";
    firebaseAuthenticated = true;
    firebaseStatus = "TERHUBUNG";
    return;
  }

  wifiConnected = false;
  wifiStatus = "TERPUTUS";
  firebaseAuthenticated = false;
  firebaseStatus = "WIFI TERPUTUS";

  unsigned long now = millis();

  if (now - lastWifiReconnectAttempt >= WIFI_RECONNECT_INTERVAL) {
    lastWifiReconnectAttempt = now;
    Serial.println("WiFi modem terputus. Mencoba menyambung ulang...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  }
}

// ============================================================
// FIREBASE REST MELALUI WIFI
// ============================================================
String firebaseUrl(const String &path) {
  return String(DATABASE_URL) +
         path +
         ".json?auth=" +
         String(DATABASE_SECRET);
}

bool firebaseRequest(const String &method,
                     const String &url,
                     const String &body,
                     String &responseBody,
                     int &statusCode) {
  if (!wifiConnected ||
      WiFi.status() != WL_CONNECTED ||
      httpBusy) {
    return false;
  }

  httpBusy = true;
  responseBody = "";
  statusCode = 0;

  WiFiClientSecure client;
  client.setInsecure();

  HTTPClient http;
  http.setConnectTimeout(15000);
  http.setTimeout(20000);

  if (!http.begin(client, url)) {
    Serial.println("HTTP begin gagal.");
    httpBusy = false;
    return false;
  }

  http.addHeader("Content-Type", "application/json");

  if (method == "GET") {
    statusCode = http.GET();
  } else if (method == "PATCH") {
    statusCode = http.sendRequest("PATCH", body);
  } else {
    Serial.println("Metode HTTP tidak didukung.");
    http.end();
    httpBusy = false;
    return false;
  }

  lastHttpStatus = statusCode;

  if (statusCode > 0) {
    responseBody = http.getString();
  } else {
    responseBody = http.errorToString(statusCode);
  }

  http.end();
  httpBusy = false;

  return statusCode >= 200 && statusCode < 300;
}

bool firebasePatch(const String &path,
                   const String &json) {
  if (!firebaseAuthenticated) return false;

  String responseBody;
  int statusCode = 0;

  bool success =
    firebaseRequest(
      "PATCH",
      firebaseUrl(path),
      json,
      responseBody,
      statusCode);

  if (!success) {
    Serial.print("Firebase PATCH gagal, HTTP ");
    Serial.println(statusCode);
    Serial.println(responseBody);
  }

  return success;
}

bool firebaseGet(const String &path,
                 String &json) {
  if (!firebaseAuthenticated) return false;

  int statusCode = 0;

  bool success =
    firebaseRequest(
      "GET",
      firebaseUrl(path),
      "",
      json,
      statusCode);

  if (!success) {
    Serial.print("Firebase GET gagal, HTTP ");
    Serial.println(statusCode);
    Serial.println(json);
  }

  return success;
}


void syncSafetyControlToFirebase() {
  if (!syncControlOffPending ||
      !firebaseAuthenticated ||
      httpBusy ||
      WiFi.status() != WL_CONNECTED) {
    return;
  }

  const String safetyJson =
    "{\"Relay\":false}";

  if (firebasePatch(
        BASE_PATH + "/Control",
        safetyJson)) {

    syncControlOffPending = false;

    Serial.println(
      "Control/Relay Firebase disinkronkan menjadi OFF karena lockout proteksi.");
  } else {
    Serial.println(
      "Sinkronisasi OFF lockout ke Firebase gagal. Akan dicoba kembali.");
  }
}

// ============================================================
// RELAY
// ============================================================
void forceRelayOn() {
  digitalWrite(RELAY_PIN, RELAY_ON);
  relayStatus = true;
  relayOnTime = millis();

  if (TEST_IGNORE_ALARM_AFTER_ON) {
    Serial.println(
      "MODE UJI: proteksi alarm diabaikan selama 30 detik setelah relay ON.");
  }
}

void forceRelayOff() {
  digitalWrite(RELAY_PIN, RELAY_OFF);
  relayStatus = false;
  relayOffTime = millis();
}

String getRelayCondition() {
  if (manualLockAfterRetry) {
    return "OFF TERKUNCI - MENUNGGU ON MANUAL";
  }

  if (protectionActive) {
    return "OFF SEMENTARA PROTEKSI";
  }

  if (relayStatus) {
    return "ON";
  }

  if (relayCommandOn) {
    return "PERINTAH ON - OUTPUT BELUM ON";
  }

  return "OFF MANUAL";
}

void relayOnManual() {
  relayCommandOn = true;
  manualLockAfterRetry = false;
  retryCount = 0;
  protectionActive = false;
  contactDetected = false;
  syncControlOffPending = false;
  protectionStatus = "ON MANUAL";

  forceRelayOn();
}

void relayOffManual() {
  relayCommandOn = false;
  protectionActive = false;
  contactDetected = false;
  retryCount = 0;
  manualLockAfterRetry = false;
  protectionStatus = "OFF MANUAL";

  forceRelayOff();
}

void syncManualRelayCommandToFirebase(bool isOn) {
  if (!firebaseAuthenticated || httpBusy) {
    return;
  }

  String json =
    String("{\"Relay\":") +
    (isOn ? "true" : "false") +
    "}";

  firebasePatch(
    BASE_PATH + "/Control",
    json);
}

void sendNotif(const String &type,
               const String &message) {
  lastNotificationType = type;
  lastNotification = message;

  Serial.println(
    "NOTIFIKASI: " + type + " - " + message);
}

// ============================================================
// PROTEKSI
// ============================================================
void handleProtection(bool alarmActive) {
  unsigned long now = millis();

  // Satu-satunya jalur AUTO-OFF relay adalah alarmActive.
  // Pulse hilang, arus abnormal, GPS, jaringan, dan Firebase error
  // tidak boleh mematikan relay secara otomatis.

  // Saat sudah terkunci, output harus tetap OFF sampai ada ON manual baru.
  if (manualLockAfterRetry) {
    relayCommandOn = false;

    if (relayStatus) {
      forceRelayOff();
    }

    protectionStatus =
      "BAHAYA - SISTEM OFF TOTAL, MENUNGGU ON MANUAL";

    return;
  }

  // Proteksi hanya bekerja jika pengguna memang memerintahkan sistem ON.
  if (!relayCommandOn) {
    contactDetected = false;
    protectionActive = false;
    retryCount = 0;
    protectionStatus = "OFF MANUAL";

    if (relayStatus) {
      forceRelayOff();
    }

    return;
  }

  // MODE UJI:
  // Selama 30 detik setelah relay ON, alarm hanya dipantau tetapi
  // tidak boleh mematikan relay. Pulse dan arus memang dari awal
  // hanya menjadi status/notifikasi dan tidak mematikan relay.
  if (TEST_IGNORE_ALARM_AFTER_ON &&
      relayStatus &&
      now - relayOnTime < TEST_IGNORE_ALARM_TIME) {

    contactDetected = false;
    contactStartTime = 0;
    protectionActive = false;
    retryCount = 0;

    if (alarmActive) {
      protectionStatus =
        "MODE UJI - ALARM AKTIF TETAPI DIABAIKAN SEMENTARA";
    } else {
      protectionStatus =
        "MODE UJI - MENUNGGU 30 DETIK";
    }

    return;
  }

  // Saat OFF sementara, tunggu tepat 2.5 detik sebelum keputusan berikutnya.
  if (protectionActive) {
    if (now - protectionActionTime < AUTO_OFF_TIME) {
      protectionStatus =
        "OFF SEMENTARA " + String(retryCount) + "/" + String(MAX_RETRY);

      return;
    }

    protectionActive = false;

    if (retryCount >= MAX_RETRY) {
      relayCommandOn = false;
      manualLockAfterRetry = true;
      syncControlOffPending = true;

      forceRelayOff();

      protectionStatus =
        "BAHAYA - 3 KALI ALARM, SISTEM OFF TOTAL";

      sendNotif(
        "DANGER_LOCKOUT",
        "Gangguan berulang terdeteksi sebanyak tiga kali. Sistem Pagar Listrik dinonaktifkan dan memerlukan pengaktifan manual.");

      return;
    }

    // Belum mencapai 3 kali: hidupkan lagi dan uji alarm kembali.
    forceRelayOn();
    protectionStatus =
      "ON KEMBALI - MENUNGGU PEMERIKSAAN ALARM";

    sendNotif(
      "AUTO_RESTART",
      "Pemeriksaan selesai. Sistem Pagar Listrik diaktifkan kembali secara otomatis.");

    return;
  }

  // Jika alarm sudah normal, siklus dianggap selesai dan hitungan direset.
  if (!alarmActive) {
    contactDetected = false;
    contactStartTime = 0;

    if (retryCount > 0) {
      retryCount = 0;
    }

    protectionStatus = "NORMAL";
    return;
  }

  // Beri grace 5 detik pada ON pertama, lalu 1 detik pada pengulangan.
  unsigned long requiredOnTime =
    retryCount == 0
      ? STARTUP_GRACE_TIME
      : RETRY_REARM_TIME;

  if (!relayStatus || now - relayOnTime < requiredOnTime) {
    return;
  }

  if (!contactDetected) {
    contactDetected = true;
    contactStartTime = now;
    return;
  }

  if (now - contactStartTime < CONTACT_DETECT_TIME) {
    return;
  }

  contactDetected = false;
  retryCount++;
  protectionActive = true;
  protectionActionTime = now;

  forceRelayOff();

  protectionStatus =
    "ALARM - OFF SEMENTARA " + String(retryCount) + "/" + String(MAX_RETRY);

  sendNotif(
    "ALARM_ACTIVE",
    "Proteksi mendeteksi gangguan pada Sistem Pagar Listrik. Output tegangan tinggi dinonaktifkan sementara untuk pemeriksaan.");
}

// ============================================================
// ACS758
// ============================================================
void calibrateACS() {
  long total = 0;

  Serial.println(
    "Kalibrasi ACS758. Pastikan energizer OFF.");

  for (int i = 0; i < 500; i++) {
    total += analogRead(ACS_PIN);
    readGPS();
    delay(2);
  }

  acsOffset =
    (total / 500.0) * 3.3 / 4095.0;

  Serial.printf(
    "Offset ACS758: %.4f V\n",
    acsOffset);
}

float readCurrent() {
  long total = 0;

  for (int i = 0; i < 30; i++) {
    total += analogRead(ACS_PIN);
    readGPS();
    delay(1);
  }

  float adc = total / 30.0;
  float voltage = adc * 3.3 / 4095.0;

  float value =
    fabs((acsOffset - voltage) / sensitivity);

  return value < 0.05 ? 0.0 : value;
}

// ============================================================
// STATUS TEXT
// ============================================================
void updatePulseQuality(bool newPulse,
                        float interval) {
  if (!newPulse || interval <= 0) {
    return;
  }

  if (interval < TOO_FAST_INTERVAL) {
    consecutiveFastPulses++;
  } else {
    consecutiveFastPulses = 0;
    continuousPulseConfirmed = false;
  }

  // Notifikasi continuous baru valid setelah 3 pulse sangat cepat berturut-turut.
  if (consecutiveFastPulses >= 3) {
    continuousPulseConfirmed = true;
  }

  if (interval > MAX_NORMAL_INTERVAL && interval <= MISSED_PULSE_MAX_INTERVAL) {
    int estimatedPulseSlots =
      int((interval / 1.30f) + 0.5f);

    estimatedMissedPulses =
      max(1, estimatedPulseSlots - 1);
  } else {
    estimatedMissedPulses = 0;
  }
}

String getPulseStatus(bool pulseLost,
                      float interval) {
  if (pulseLost) {
    return "PULSE HILANG";
  }

  if (continuousPulseConfirmed) {
    return "ARUS/PULSE TERUS MENERUS";
  }

  if (interval >= MIN_NORMAL_INTERVAL && interval <= MAX_NORMAL_INTERVAL) {
    return "NORMAL";
  }

  if (interval > MAX_NORMAL_INTERVAL && interval <= MISSED_PULSE_MAX_INTERVAL) {
    return "PULSE TERLEWAT " + String(estimatedMissedPulses);
  }

  if (interval > MISSED_PULSE_MAX_INTERVAL) {
    return "JEDA PULSE TERLALU LAMA";
  }

  if (interval > 0 && interval < MIN_NORMAL_INTERVAL) {
    return "TERLALU CEPAT / NOISE";
  }

  return "MENUNGGU PULSE";
}

String getCurrentStatus(float value) {
  if (value <= 0.05) return "ENERGIZER OFF";
  if (value <= 0.25) return "NORMAL";
  return "ARUS TIDAK NORMAL";
}

void updateOutputStatus(
  bool alarmActive,
  bool pulseLost,
  const String &pulseStatusText) {
  if (manualLockAfterRetry) {
    outputStatus =
      "BAHAYA - SISTEM OFF TOTAL, ON MANUAL DIPERLUKAN";
  } else if (protectionActive) {
    outputStatus =
      "RELAY OFF SEMENTARA KARENA ALARM";
  } else if (!relayStatus && relayCommandOn) {
    outputStatus =
      "PERINTAH ON, TETAPI OUTPUT RELAY OFF";
  } else if (!relayStatus && pulseStatusText != "PULSE HILANG") {
    outputStatus =
      "RELAY OFF TAPI PULSE MASIH TERDETEKSI";
  } else if (!relayStatus) {
    outputStatus =
      "RELAY OFF MANUAL";
  } else if (alarmActive) {
    outputStatus =
      "ALARM INTERNAL AKTIF";
  } else if (pulseLost) {
    outputStatus =
      "RELAY TETAP ON - PERINGATAN PULSE TIDAK TERDETEKSI";
  } else if (continuousPulseConfirmed) {
    outputStatus =
      "WARNING: ARUS/PULSE TERUS MENERUS";
  } else if (pulseStatusText.startsWith("PULSE TERLEWAT")) {
    outputStatus =
      "OUTPUT AKTIF - ADA PULSE PICKUP TERLEWAT";
  } else {
    outputStatus = "OUTPUT NORMAL";
  }
}

// ============================================================
// FIREBASE CONTROL
// ============================================================
void readFirebaseControl() {
  if (!firebaseAuthenticated || httpBusy) return;

  String json;

  if (!firebaseGet(
        BASE_PATH + "/Control",
        json)) {
    Serial.println(
      "Gagal membaca Control Firebase.");
    return;
  }

  bool relayRequest =
    extractJsonBool(
      json,
      "Relay",
      relayCommandOn);

  bool resetCounter =
    extractJsonBool(
      json,
      "ResetEmergency",
      false);

  bool setHome =
    extractJsonBool(
      json,
      "SetHome",
      false);

  bool resetHome =
    extractJsonBool(
      json,
      "ResetHome",
      false);

  if (resetCounter) {
    retryCount = 0;
    manualLockAfterRetry = false;
    protectionActive = false;
    contactDetected = false;
    protectionStatus = "NORMAL";
  }

  if (relayRequest != relayCommandOn) {
    if (relayRequest) {
      relayOnManual();
    } else {
      relayOffManual();
    }
  }

  if (setHome) {
    setHomeLocationFromGPS();
  }

  if (resetHome) {
    resetHomeLocation();
  }

  if (resetCounter || setHome || resetHome) {
    String resetJson =
      "{\"ResetEmergency\":false,"
      "\"SetHome\":false,"
      "\"ResetHome\":false}";

    firebasePatch(
      BASE_PATH + "/Control",
      resetJson);
  }
}

// ============================================================
// FIREBASE DATA
// ============================================================
void writeFirebaseData(
  bool alarmActive,
  bool pulseLost,
  const String &pulseStatusText,
  const String &currentStatusText,
  unsigned long safePulseCount,
  float safePulseInterval) {
  if (!firebaseAuthenticated || httpBusy) return;

  String theftStatus =
    !gpsValid
      ? "GPS BELUM FIX"
    : !homeSet
      ? "LOKASI AWAL BELUM ADA"
    : theftDetected
      ? "DIDUGA DICURI"
      : "AMAN";

  String json;
  json.reserve(1400);

  json += "{";

  json += "\"Status\":{";
  // Relay = kondisi output fisik sebenarnya.
  json += "\"Relay\":" + String(relayStatus ? "true" : "false") + ",";
  // RelayCommand = perintah pengguna/aplikasi.
  json += "\"RelayCommand\":" + String(relayCommandOn ? "true" : "false") + ",";
  json += "\"RelayTemporaryOff\":" + String(protectionActive ? "true" : "false") + ",";
  json += "\"ManualLock\":" + String(manualLockAfterRetry ? "true" : "false") + ",";
  json += "\"AutomaticOffByAlarmOnly\":" + String(automaticOffByAlarmOnly ? "true" : "false") + ",";
  json += "\"AlarmTestIgnoreActive\":" + String(
    TEST_IGNORE_ALARM_AFTER_ON &&
    relayStatus &&
    millis() - relayOnTime < TEST_IGNORE_ALARM_TIME
      ? "true"
      : "false") + ",";
  json += "\"RelayCondition\":\"" + jsonEscape(getRelayCondition()) + "\",";
  json += "\"Alarm\":" + String(alarmActive ? "true" : "false") + ",";
  json += "\"PulseLost\":" + String(pulseLost ? "true" : "false") + ",";
  json += "\"PulseStatus\":\"" + jsonEscape(pulseStatusText) + "\",";
  json += "\"CurrentStatus\":\"" + jsonEscape(currentStatusText) + "\",";
  json += "\"OutputStatus\":\"" + jsonEscape(outputStatus) + "\",";
  json += "\"ContactDetected\":" + String(contactDetected ? "true" : "false") + ",";
  json += "\"EmergencyStop\":false,";
  json += "\"ProtectionStatus\":\"" + jsonEscape(protectionStatus) + "\",";
  json += "\"WiFiStatus\":\"" + jsonEscape(wifiStatus) + "\",";
  json += "\"GPSStatus\":\"" + String(gpsValid ? "VALID" : "BELUM FIX") + "\"},";

  json += "\"Data\":{";
  json += "\"Current\":" + String(current, 3) + ",";
  json += "\"PulseCount\":" + String(safePulseCount) + ",";
  json += "\"PulseInterval\":" + String(safePulseInterval, 3) + ",";
  json += "\"EstimatedMissedPulses\":" + String(estimatedMissedPulses) + ",";
  json += "\"ContinuousPulseConfirmed\":" + String(continuousPulseConfirmed ? "true" : "false") + ",";
  json += "\"RetryCount\":" + String(retryCount) + "},";

  json += "\"GPS\":{";
  json += "\"Latitude\":" + String(gpsLat, 6) + ",";
  json += "\"Longitude\":" + String(gpsLng, 6) + ",";
  json += "\"Satellite\":" + String(gpsSat) + ",";
  json += "\"HomeLatitude\":" + String(homeLat, 6) + ",";
  json += "\"HomeLongitude\":" + String(homeLng, 6) + ",";
  json += "\"HomeSet\":" + String(homeSet ? "true" : "false") + ",";
  json += "\"Distance\":" + String(gpsDistance, 2) + ",";
  json += "\"TheftDetected\":" + String(theftDetected ? "true" : "false") + ",";
  json += "\"TheftStatus\":\"" + jsonEscape(theftStatus) + "\"},";

  json += "\"Network\":{";
  json += "\"Type\":\"WIFI_MODEM_4G\",";
  json += "\"SSID\":\"" + jsonEscape(String(WIFI_SSID)) + "\",";
  json += "\"RSSI\":" + String(WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : -127) + ",";
  json += "\"Connected\":" + String(WiFi.status() == WL_CONNECTED ? "true" : "false") + ",";
  json += "\"IPAddress\":\"" + WiFi.localIP().toString() + "\"},";

  json += "\"Notification\":{";
  json += "\"Type\":\"" + jsonEscape(lastNotificationType) + "\",";
  json += "\"LastMessage\":\"" + jsonEscape(lastNotification) + "\"}";

  json += "}";

  bool success =
    firebasePatch(BASE_PATH, json);

  Serial.println(
    success
      ? "Data Firebase berhasil diperbarui."
      : "Data Firebase gagal diperbarui.");
}

// ============================================================
// SERIAL MONITOR
// ============================================================
void printStatus(
  bool alarmActive,
  const String &pulseStatusText,
  const String &currentStatusText,
  unsigned long safePulseCount,
  float safePulseInterval) {
  Serial.println("================================");
  Serial.println("WiFi Modem  : " + wifiStatus);
  Serial.print("SSID        : ");
  Serial.println(WIFI_SSID);
  Serial.print("RSSI WiFi   : ");
  Serial.print(WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : -127);
  Serial.println(" dBm");
  Serial.print("IP ESP32    : ");
  Serial.println(WiFi.localIP());
  Serial.println(
    String("Firebase    : ") + (firebaseAuthenticated ? "TERHUBUNG" : firebaseStatus));
  Serial.printf("HTTP Status : %d\n", lastHttpStatus);
  Serial.println(
    String("Perintah    : ") + (relayCommandOn ? "ON" : "OFF"));
  Serial.println(
    String("Relay Fisik : ") + (relayStatus ? "ON" : "OFF"));
  Serial.println("Kondisi     : " + getRelayCondition());
  Serial.println("Auto OFF    : HANYA JIKA ALARM AKTIF");
  Serial.println(
    String("Mode Uji    : ") +
    (TEST_IGNORE_ALARM_AFTER_ON
      ? "ALARM DIABAIKAN 30 DETIK SETELAH ON"
      : "NONAKTIF"));
  Serial.println("Output      : " + outputStatus);
  Serial.println(
    String("Alarm       : ") + (alarmActive ? "AKTIF" : "NORMAL"));
  Serial.println("Pulse       : " + pulseStatusText);
  Serial.printf(
    "Pulse Count : %lu\n",
    safePulseCount);
  Serial.printf(
    "Jeda Pulse  : %.3f detik\n",
    safePulseInterval);
  Serial.printf(
    "Arus Input  : %.3f A\n",
    current);
  Serial.println(
    "Status Arus : " + currentStatusText);
  Serial.printf(
    "Retry       : %d / %d\n",
    retryCount,
    MAX_RETRY);
  Serial.println(
    "Proteksi    : " + protectionStatus);
  Serial.println(
    String("GPS         : ") + (gpsValid ? "VALID" : "BELUM FIX"));
  Serial.printf(
    "Lat/Lng     : %.6f, %.6f\n",
    gpsLat,
    gpsLng);
  Serial.printf(
    "Satelit     : %d\n",
    gpsSat);
  Serial.println(
    String("Home Set    : ") + (homeSet ? "YA" : "BELUM"));
  Serial.printf(
    "Jarak GPS   : %.2f m\n",
    gpsDistance);
  Serial.println("================================");
}

void handleSerialCommand() {
  if (!Serial.available() || httpBusy) return;

  String command = Serial.readStringUntil('\n');
  command.trim();

  String commandUpper = command;
  commandUpper.toUpperCase();

  if (commandUpper == "ON") {
    relayOnManual();
    syncManualRelayCommandToFirebase(true);
    Serial.println("Sistem Pagar Listrik ON manual.");
  } else if (commandUpper == "OFF") {
    relayOffManual();
    syncManualRelayCommandToFirebase(false);
    Serial.println("Sistem Pagar Listrik OFF manual.");
  } else if (commandUpper == "HOME") {
    setHomeLocationFromGPS();
  } else if (commandUpper == "RESETHOME") {
    resetHomeLocation();
  } else if (commandUpper == "RESET") {
    retryCount = 0;
    manualLockAfterRetry = false;
    protectionActive = false;
    contactDetected = false;
    protectionStatus = "NORMAL";
    Serial.println("Proteksi di-reset.");
  } else if (commandUpper == "CAL") {
    relayOffManual();
    calibrateACS();
  } else if (commandUpper == "WIFI") {
    connectWiFi();
  } else if (commandUpper == "SEND") {
    lastFirebaseWrite = 0;
  } else if (commandUpper == "READ") {
    lastFirebaseRead = 0;
  } else {
    Serial.println(
      "Perintah: ON / OFF / HOME / RESETHOME / "
      "RESET / CAL / WIFI / SEND / READ");
  }
}

// ============================================================
// SETUP
// ============================================================
void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(RELAY_PIN, OUTPUT);
  pinMode(PULSE_PIN, INPUT_PULLUP);
  pinMode(ALARM_PIN, INPUT_PULLUP);

  relayCommandOn = false;
  forceRelayOff();

  analogReadResolution(12);
  analogSetPinAttenuation(ACS_PIN, ADC_11db);

  attachInterrupt(
    digitalPinToInterrupt(PULSE_PIN),
    pulseISR,
    FALLING);

  gpsSerial.begin(
    9600,
    SERIAL_8N1,
    GPS_RX,
    GPS_TX);

  calibrateACS();

  connectWiFi();

  unsigned long now = millis();

  lastPrint = now;
  lastFirebaseRead = now;
  lastFirebaseWrite = now;
  lastWifiReconnectAttempt = now;
  lastNetworkHealthCheck = now;

  Serial.println("Sistem Electric Fence dimulai.");
}

// ============================================================
// LOOP
// ============================================================
void loop() {
  unsigned long now = millis();

  readGPS();
  checkTheftGPS();
  maintainWiFi();

  if (now - lastNetworkHealthCheck >= NETWORK_HEALTH_INTERVAL) {
    lastNetworkHealthCheck = now;

    if (WiFi.status() == WL_CONNECTED) {
      wifiConnected = true;
      wifiStatus = "TERHUBUNG";
      firebaseAuthenticated = true;
      firebaseStatus = "TERHUBUNG";
    } else {
      wifiConnected = false;
      wifiStatus = "TERPUTUS";
      firebaseAuthenticated = false;
      firebaseStatus = "WIFI TERPUTUS";
    }
  }

  bool alarmActive =
    digitalRead(ALARM_PIN) == LOW;

  unsigned long safePulseCount;
  unsigned long safeLastPulseTime;
  bool safePulseDetected;
  float safePulseInterval;

  portENTER_CRITICAL(&pulseMux);

  safePulseCount = pulseCount;
  safeLastPulseTime = lastPulseTime;
  safePulseDetected = pulseDetected;
  safePulseInterval = pulseInterval;
  pulseDetected = false;

  portEXIT_CRITICAL(&pulseMux);

  bool pulseLost =
    safeLastPulseTime == 0 || now - safeLastPulseTime > PULSE_LOST_TIME;

  current = readCurrent();

  updatePulseQuality(
    safePulseDetected,
    safePulseInterval);

  String pulseStatusText =
    getPulseStatus(
      pulseLost,
      safePulseInterval);

  String currentStatusText =
    getCurrentStatus(current);

  handleProtection(alarmActive);

  // Output status dihitung setelah proteksi mengubah relay fisik.
  updateOutputStatus(
    alarmActive,
    pulseLost,
    pulseStatusText);

  handleSerialCommand();
  syncSafetyControlToFirebase();

  if (safePulseDetected) {
    Serial.printf(
      "PULSE | Relay: %s | Count: %lu | Jeda: %.3f detik\n",
      relayStatus ? "ON" : "OFF",
      safePulseCount,
      safePulseInterval);
  }

  if (firebaseAuthenticated && !httpBusy && now - lastFirebaseRead >= FIREBASE_READ_INTERVAL) {
    lastFirebaseRead = now;
    readFirebaseControl();
  }

  if (firebaseAuthenticated && !httpBusy && now - lastFirebaseWrite >= FIREBASE_WRITE_INTERVAL) {
    lastFirebaseWrite = now;

    writeFirebaseData(
      alarmActive,
      pulseLost,
      pulseStatusText,
      currentStatusText,
      safePulseCount,
      safePulseInterval);
  }

  if (now - lastPrint >= PRINT_INTERVAL) {
    lastPrint = now;

    printStatus(
      alarmActive,
      pulseStatusText,
      currentStatusText,
      safePulseCount,
      safePulseInterval);
  }

  if (theftDetected && now - lastNotifTime > 30000) {
    lastNotifTime = now;

    sendNotif(
      "THEFT_DETECTED",
      "Peringatan: Perangkat Sistem Pagar Listrik berpindah dari lokasi yang telah ditetapkan.");
  }

  if (relayStatus && relayCommandOn && continuousPulseConfirmed && now - lastContinuousNotifTime > 30000) {
    lastContinuousNotifTime = now;

    sendNotif(
      "CONTINUOUS_CURRENT",
      "Peringatan: arus atau pulse tegangan tinggi terdeteksi terus-menerus tanpa jeda normal. Sistem Pagar Listrik tetap aktif; segera lakukan pemeriksaan.");
  }

  if (relayStatus && relayCommandOn && !protectionActive && pulseLost && now - relayOnTime >= PULSE_LOST_TIME && now - lastPulseLostNotifTime > 30000) {
    lastPulseLostNotifTime = now;
    sendNotif(
      "PULSE_LOST",
      "Peringatan: pulse tegangan tinggi tidak terdeteksi. Sistem Pagar Listrik tetap aktif; periksa energizer, kabel pagar, pickup pulse, dan sumber daya.");
  }

  delay(5);
}