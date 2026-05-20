package com.example.electricfence;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FirebaseManager {
    private static FirebaseManager instance;
    private final DatabaseReference databaseReference;

    private FirebaseManager() {
        databaseReference = FirebaseDatabase.getInstance().getReference();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public void listenToSensorData(ValueEventListener listener) {
        databaseReference.child("sensors").addValueEventListener(listener);
    }

    public void listenToGPS(ValueEventListener listener) {
        databaseReference.child("gps").addValueEventListener(listener);
    }

    public void setSystemStatus(boolean isOn) {
        databaseReference.child("system").child("status").setValue(isOn);
    }

    public void listenToSystemStatus(ValueEventListener listener) {
        databaseReference.child("system").child("status").addValueEventListener(listener);
    }

    public void listenToLogs(ValueEventListener listener) {
        databaseReference.child("logs").child("notifications").limitToLast(20).addValueEventListener(listener);
    }
}