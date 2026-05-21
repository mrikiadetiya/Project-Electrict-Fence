package com.example.electricfence;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class VoltageActivity extends AppCompatActivity {

    private TextView tvVoltageVal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voltage);

        initViews();
        setupNavbar();
        observeData();
    }

    private void initViews() {
        tvVoltageVal = findViewById(R.id.tv_voltage_val);
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
    }

    private void setupNavbar() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
        
        findViewById(R.id.nav_gps).setOnClickListener(v -> {
            startActivity(new Intent(this, GPSActivity.class));
            finish();
        });
        
        findViewById(R.id.btn_notifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationActivity.class));
            finish();
        });
        
        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
        
        // Halaman ini (Tegangan) sudah aktif, tidak perlu listener pindah
    }

    private void observeData() {
        FirebaseManager.getInstance().listenToSensorData(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        Object voltageObj = snapshot.child("voltage").getValue();
                        if (voltageObj != null) {
                            double voltage = Double.parseDouble(voltageObj.toString());
                            // Tampilkan dalam format kV (misal 11.983)
                            tvVoltageVal.setText(String.format("%.3f", voltage / 1000));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}