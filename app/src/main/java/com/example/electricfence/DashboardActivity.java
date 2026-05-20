package com.example.electricfence;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvVoltage, tvStatus, tvCurrent, tvInternet, tvBattery, tvInverter;
    private SwitchCompat switchPower;
    private FirebaseManager firebaseManager;
    private boolean isUpdatingFromFirebase = false;

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
        tvStatus = findViewById(R.id.tv_status);
        tvCurrent = findViewById(R.id.tv_current);
        tvInternet = findViewById(R.id.tv_internet);
        tvBattery = findViewById(R.id.tv_battery);
        tvInverter = findViewById(R.id.tv_inverter);
        switchPower = findViewById(R.id.switch_power);

        findViewById(R.id.nav_voltage).setOnClickListener(v -> startActivity(new Intent(this, VoltageActivity.class)));
        findViewById(R.id.nav_gps).setOnClickListener(v -> startActivity(new Intent(this, GPSActivity.class)));
        findViewById(R.id.nav_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_notifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationActivity.class)));
    }

    private void setupListeners() {
        switchPower.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUpdatingFromFirebase) {
                showControlConfirmation(isChecked);
            }
        });
    }

    private void showControlConfirmation(boolean targetState) {
        String action = targetState ? "ACTIVATE" : "DEACTIVATE";
        new AlertDialog.Builder(this)
                .setTitle("Security Confirmation")
                .setMessage("Are you sure you want to " + action + " the electric fence system?")
                .setPositiveButton("Confirm", (dialog, which) -> {
                    firebaseManager.setSystemStatus(targetState);
                    Toast.makeText(this, "System " + action + "D", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    isUpdatingFromFirebase = true;
                    switchPower.setChecked(!targetState);
                    isUpdatingFromFirebase = false;
                })
                .setCancelable(false)
                .show();
    }

    private void observeData() {
        firebaseManager.listenToSensorData(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String voltage = snapshot.child("voltage").getValue().toString() + " V";
                    String current = "Current: " + snapshot.child("current").getValue().toString() + "A";
                    String battery = snapshot.child("battery").getValue().toString() + "%";
                    String inverter = snapshot.child("inverter").getValue(Boolean.class) ? "Running" : "Stopped";
                    
                    tvVoltage.setText(voltage);
                    tvCurrent.setText(current);
                    tvBattery.setText(battery);
                    tvInverter.setText(inverter);
                    tvInverter.setTextColor(snapshot.child("inverter").getValue(Boolean.class) ? 
                        getResources().getColor(R.color.status_green) : getResources().getColor(R.color.neon_red));
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
                    isUpdatingFromFirebase = true;
                    switchPower.setChecked(status);
                    tvStatus.setText(status ? "ACTIVE" : "INACTIVE");
                    tvStatus.setTextColor(status ? getResources().getColor(R.color.status_green) : getResources().getColor(R.color.neon_red));
                    isUpdatingFromFirebase = false;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}