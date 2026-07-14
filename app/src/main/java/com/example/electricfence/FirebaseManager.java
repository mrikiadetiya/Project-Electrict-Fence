package com.example.electricfence;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FirebaseManager {
    private static FirebaseManager instance;
    private final DatabaseReference rootRef;

    // Root path
    private static final String ROOT = "ElectricFence";

    private FirebaseManager() {
        // Explicitly set the Asia Southeast 1 URL because it's not in google-services.json
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://electricfence-1487b-default-rtdb.asia-southeast1.firebasedatabase.app");
        rootRef = database.getReference().child(ROOT);
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    // ===================== CONTROL WRITE =====================

    /** Set relay ON (true) atau OFF (false) */
    public void setRelayControl(boolean isOn) {
        rootRef.child("Control").child("Relay").setValue(isOn);
    }

    /** Set lokasi aman */
    public void setSetHome(boolean value) {
        rootRef.child("Control").child("SetHome").setValue(value);
    }

    /** Reset lokasi aman */
    public void setResetHome(boolean value) {
        rootRef.child("Control").child("ResetHome").setValue(value);
    }

    /** Reset Emergency Stop */
    public void setResetEmergency(boolean value) {
        rootRef.child("Control").child("ResetEmergency").setValue(value);
    }

    // ===================== CONTROL LISTEN =====================

    public void listenToControl(ValueEventListener listener) {
        rootRef.child("Control").addValueEventListener(listener);
    }

    // ===================== STATUS LISTEN =====================

    /** Listen semua node Status sekaligus */
    public void listenToStatus(ValueEventListener listener) {
        rootRef.child("Status").addValueEventListener(listener);
    }

    /** Listen hanya relay status */
    public void listenToRelayStatus(ValueEventListener listener) {
        rootRef.child("Status").child("Relay").addValueEventListener(listener);
    }

    // ===================== DATA LISTEN =====================

    /** Listen semua node Data (Current, PulseCount, PulseInterval, RetryCount) */
    public void listenToData(ValueEventListener listener) {
        rootRef.child("Data").addValueEventListener(listener);
    }

    // ===================== GPS LISTEN =====================

    /** Listen semua node GPS */
    public void listenToGPS(ValueEventListener listener) {
        rootRef.child("GPS").addValueEventListener(listener);
    }

    // ===================== WIFI LISTEN =====================

    /** Listen semua node WiFi (SSID, RSSI) */
    public void listenToWifi(ValueEventListener listener) {
        rootRef.child("WiFi").addValueEventListener(listener);
    }

    // ===================== NOTIFICATION LISTEN =====================

    /** Listen ke node Notification (Type, LastMessage) */
    public void listenToNotification(ValueEventListener listener) {
        rootRef.child("Notification").addValueEventListener(listener);
    }

    // ===================== LEGACY COMPAT =====================

    @Deprecated
    public void listenToSensorData(ValueEventListener listener) {
        listenToData(listener);
    }

    @Deprecated
    public void setSystemStatus(boolean isOn) {
        setRelayControl(isOn);
    }

    @Deprecated
    public void listenToSystemStatus(ValueEventListener listener) {
        listenToRelayStatus(listener);
    }

    public void listenToLogs(ValueEventListener listener) {
        FirebaseDatabase.getInstance("https://electricfence-1487b-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference()
                .child("logs").child("notifications")
                .limitToLast(20).addValueEventListener(listener);
    }
}
