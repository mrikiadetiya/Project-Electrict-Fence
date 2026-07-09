package com.example.electricfence;

/**
 * Model data untuk Electric Fence IoT
 * Berisi semua class POJO yang merepresentasikan struktur Firebase Realtime Database
 * /ElectricFence/Status, /Data, /GPS, /SIM7600, /Control, /Notification
 */
public class ElectricFenceModel {

    // ===================== STATUS =====================
    public static class Status {
        public boolean Relay;
        public boolean Alarm;
        public boolean PulseLost;
        public String PulseStatus;
        public String CurrentStatus;
        public String OutputStatus;
        public boolean ContactDetected;
        public boolean EmergencyStop;
        public String ProtectionStatus;
        public String SIMStatus;
        public String GPSStatus;

        public Status() {}
    }

    // ===================== DATA =====================
    public static class Data {
        public float Current;
        // Power sudah tidak digunakan
        public int PulseCount;
        public float PulseInterval;
        public int RetryCount;

        public Data() {}
    }

    // ===================== GPS =====================
    public static class GPS {
        public double Latitude;
        public double Longitude;
        public int Satellite;
        public double HomeLatitude;
        public double HomeLongitude;
        public boolean HomeSet;
        public double Distance;
        public boolean TheftDetected;
        public String TheftStatus;

        public GPS() {}
    }

    // ===================== SIM7600 =====================
    public static class SIM7600 {
        public int CSQ;
        public String LastResponse;

        public SIM7600() {}
    }

    // ===================== CONTROL =====================
    public static class Control {
        public boolean Relay;
        public boolean SetHome;
        public boolean ResetHome;
        public boolean ResetEmergency;

        public Control() {}
    }

    // ===================== NOTIFICATION =====================
    public static class Notification {
        public String Type;
        public String LastMessage;

        public Notification() {}
    }

    // ===================== HELPER METHODS =====================

    /**
     * Interpretasikan nilai CSQ menjadi label kualitas sinyal
     */
    public static String getCSQLabel(int csq) {
        if (csq == -1 || csq == 99) return "Tidak Terbaca";
        if (csq >= 0 && csq <= 9)  return "Sinyal Lemah";
        if (csq >= 10 && csq <= 14) return "Cukup";
        if (csq >= 15 && csq <= 20) return "Bagus";
        if (csq > 20)               return "Sangat Bagus";
        return "-";
    }

    /**
     * Relay status ke string
     */
    public static String relayToString(boolean relay) {
        return relay ? "ON" : "OFF";
    }

    /**
     * Alarm status ke string
     */
    public static String alarmToString(boolean alarm) {
        return alarm ? "AKTIF" : "NORMAL";
    }

    /**
     * PulseLost ke string
     */
    public static String pulseLostToString(boolean pulseLost) {
        return pulseLost ? "HILANG" : "NORMAL";
    }

    /**
     * TheftDetected ke string
     */
    public static String theftToString(boolean theft) {
        return theft ? "DIDUGA DICURI" : "AMAN";
    }

    /**
     * GPSStatus ke string yang mudah dibaca
     */
    public static String gpsStatusToLabel(String gpsStatus) {
        if (gpsStatus == null) return "-";
        if (gpsStatus.equalsIgnoreCase("VALID")) return "GPS Valid";
        if (gpsStatus.equalsIgnoreCase("BELUM FIX")) return "Belum Fix";
        return gpsStatus;
    }

    /**
     * SIMStatus ke string yang mudah dibaca
     */
    public static String simStatusToLabel(String simStatus) {
        if (simStatus == null) return "-";
        if (simStatus.equalsIgnoreCase("TERHUBUNG")) return "Terhubung";
        if (simStatus.equalsIgnoreCase("TIDAK ADA RESPON")) return "Tidak Ada Respon";
        return simStatus;
    }

    /**
     * OutputStatus ke warna: merah/abu/kuning/hijau
     * Return: "green", "gray", "yellow", "red"
     */
    public static String getOutputStatusColor(String outputStatus) {
        if (outputStatus == null || outputStatus.isEmpty()) return "gray";
        switch (outputStatus.toUpperCase()) {
            case "OUTPUT NORMAL":           return "green";
            case "ENERGIZER OFF":           return "gray";
            case "MENUNGGU PULSE":          return "yellow";
            case "ALARM INTERNAL AKTIF":    return "red";
            case "PULSE HILANG":            return "red";
            case "ALARM DAN PULSE HILANG":  return "red";
            case "ARUS/PULSE TERUS MENERUS":return "red";
            case "EMERGENCY STOP":          return "red";
            default:                        return "gray";
        }
    }
}
