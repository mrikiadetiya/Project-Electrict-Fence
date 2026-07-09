#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include <TinyGPSPlus.h>
#include <FirebaseJson.h> // Tambahan untuk optimasi data

// ================= WIFI & FIREBASE =================
const char* ssid = "Galaxy A04e002a";
const char* password = "123456789";

#define DATABASE_URL "https://electricfence-1487b-default-rtdb.asia-southeast1.firebasedatabase.app"
#define API_KEY "AIzaSyCB6SKslQk1B_EoaSpZvArrxETnZ6xtLYU"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
FirebaseJson json; // Objek untuk kirim data sekaligus

// ================= PIN =================
#define ACS_PIN 34
#define RELAY_PIN 26
#define PULSE_PIN 27
#define ALARM_PIN 25
#define GPS_RX 32
#define GPS_TX 33
#define SIM_RX 16
#define SIM_TX 17

// ================= GLOBAL VARIABLES =================
TinyGPSPlus gps;
HardwareSerial gpsSerial(1);
HardwareSerial sim7600(2);
portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;

float sensitivity = 0.040;
float acsOffset = 0.0;
volatile unsigned long pulseCount = 0;
volatile bool pulseDetected = false;
volatile unsigned long lastPulseTime = 0;
volatile float pulseInterval = 0.0;

bool relayStatus = false;
double gpsLat = 0.0, gpsLng = 0.0;
int gpsSat = 0;
bool gpsValid = false;
bool homeSet = false;
double homeLat = 0.0, homeLng = 0.0;
double gpsDistance = 0.0;
bool theftDetected = false;

// ================= FUNGSI PULSE & ACS =================
void IRAM_ATTR pulseISR() {
  unsigned long now = millis();
  portENTER_CRITICAL_ISR(&mux);
  if (lastPulseTime != 0) pulseInterval = (now - lastPulseTime) / 1000.0;
  lastPulseTime = now;
  pulseCount++;
  pulseDetected = true;
  portEXIT_CRITICAL_ISR(&mux);
}

void calibrateACS() {
  long total = 0;
  for (int i = 0; i < 500; i++) { total += analogRead(ACS_PIN); delay(2); }
  acsOffset = (total / 500.0) * 3.3 / 4095.0;
}

float readCurrent() {
  long total = 0;
  for (int i = 0; i < 200; i++) { total += analogRead(ACS_PIN); delay(1); }
  float voltage = (total / 200.0) * 3.3 / 4095.0;
  float currentValue = abs((acsOffset - voltage) / sensitivity);
  return (currentValue < 0.05) ? 0.0 : currentValue;
}

// ================= FIREBASE OPTIMIZED =================
void updateFirebase(bool alarmActive, bool pulseLost, String pStatus, String cStatus) {
  json.set("Status/Relay", relayStatus);
  json.set("Status/Alarm", alarmActive);
  json.set("Status/PulseLost", pulseLost);
  json.set("Status/PulseStatus", pStatus.c_str());
  json.set("Status/CurrentStatus", cStatus.c_str());
  json.set("Data/Current", readCurrent());
  json.set("GPS/Latitude", gpsLat);
  json.set("GPS/Longitude", gpsLng);
  json.set("GPS/TheftDetected", theftDetected);
  
  if (Firebase.RTDB.updateNode(&fbdo, "/ElectricFence", &json)) {
    // Data berhasil dikirim
  }
}

// ================= SETUP =================
void setup() {
  Serial.begin(115200);
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(PULSE_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(PULSE_PIN), pulseISR, FALLING);
  
  WiFi.begin(ssid, password);
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  Firebase.begin(&config, &auth);
  
  calibrateACS();
}

// ================= LOOP =================
void loop() {
  unsigned long now = millis();
  
  // Baca Sensor
  float current = readCurrent();
  
  // Update Firebase setiap 3 detik (lebih aman untuk heap)
  static unsigned long lastUpdate = 0;
  if (Firebase.ready() && (now - lastUpdate > 3000)) {
    lastUpdate = now;
    // Menggunakan fungsi optimasi
    updateFirebase(digitalRead(ALARM_PIN) == LOW, (now - lastPulseTime > 5000), "NORMAL", "NORMAL");
  }
  
  delay(100);
}