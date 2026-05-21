package com.example.electricfence;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvVoltage, tvStatusDot, tvCurrent, tvInternet, tvBattery, tvInverter, tvCoords;
    private MaterialButton btnPower;
    private FirebaseManager firebaseManager;
    private boolean isSystemActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        firebaseManager = FirebaseManager.getInstance();
        initViews();
        setupListeners();
        observeData();
    }

    private void initViews() {
        tvVoltage = findViewById(R.id.tv_voltage_main);
        tvStatusDot = findViewById(R.id.tv_status_dot);
        tvCurrent = findViewById(R.id.tv_current);
        tvInternet = findViewById(R.id.tv_internet);
        tvBattery = findViewById(R.id.tv_battery);
        tvInverter = findViewById(R.id.tv_inverter);
        tvCoords = findViewById(R.id.tv_coords_main);
        btnPower = findViewById(R.id.switch_power);

        // Navigation
        findViewById(R.id.nav_voltage).setOnClickListener(v -> startActivity(new Intent(this, VoltageActivity.class)));
        findViewById(R.id.nav_gps).setOnClickListener(v -> startActivity(new Intent(this, GPSActivity.class)));
        findViewById(R.id.nav_gps_card).setOnClickListener(v -> startActivity(new Intent(this, GPSActivity.class)));
        findViewById(R.id.nav_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_notifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationActivity.class)));
    }

    private void setupListeners() {
        btnPower.setOnClickListener(v -> {
            showControlConfirmation(!isSystemActive);
        });
    }

    private void showControlConfirmation(boolean targetState) {
        String action = targetState ? "MENGAKTIFKAN" : "MEMATIKAN";
        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Konfirmasi Keamanan")
                .setMessage("Apakah Anda yakin ingin " + action + " sistem pagar listrik?")
                .setPositiveButton("Ya, Konfirmasi", (dialog, which) -> {
                    firebaseManager.setSystemStatus(targetState);
                    Toast.makeText(this, "Perintah " + action + " dikirim", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void observeData() {
        firebaseManager.listenToSensorData(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        String voltage = snapshot.child("voltage").getValue().toString();
                        String current = "↯ " + snapshot.child("current").getValue().toString() + "A";
                        String battery = snapshot.child("battery").getValue().toString() + "%";
                        boolean inverterStatus = false;
                        if (snapshot.child("inverter").getValue() != null) {
                            inverterStatus = snapshot.child("inverter").getValue(Boolean.class);
                        }
                        
                        tvVoltage.setText(voltage);
                        tvCurrent.setText(current);
                        tvBattery.setText(battery);
                        tvInverter.setText(inverterStatus ? "ON" : "OFF");
                        tvInverter.setTextColor(inverterStatus ? 
                            getResources().getColor(R.color.neon_blue) : getResources().getColor(R.color.neon_red));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        firebaseManager.listenToSystemStatus(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean status = snapshot.getValue(Boolean.class);
                if (status != null) {
                    isSystemActive = status;
                    updateUIStatus(status);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
        
        firebaseManager.listenToGPS(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String lat = snapshot.child("lat").getValue().toString();
                    String lng = snapshot.child("lng").getValue().toString();
                    tvCoords.setText(lat + ", " + lng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateUIStatus(boolean active) {
        if (active) {
            tvStatusDot.setText("● SISTEM AKTIF");
            tvStatusDot.setTextColor(getResources().getColor(R.color.status_green));
            btnPower.setText("OFF MATIKAN PAGAR LISTRIK");
            btnPower.setTextColor(getResources().getColor(R.color.neon_red));
            btnPower.setStrokeColorResource(R.color.neon_red);
            btnPower.setIconTintResource(R.color.neon_red);
        } else {
            tvStatusDot.setText("○ SISTEM NONAKTIF");
            tvStatusDot.setTextColor(getResources().getColor(R.color.neon_red));
            btnPower.setText("ON AKTIFKAN PAGAR LISTRIK");
            btnPower.setTextColor(getResources().getColor(R.color.status_green));
            btnPower.setStrokeColorResource(R.color.status_green);
            btnPower.setIconTintResource(R.color.status_green);
        }
    }
}