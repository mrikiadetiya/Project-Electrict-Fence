package com.example.electricfence;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class SettingsActivity extends AppCompatActivity {

    private SwitchCompat switchPush, switchSound, switchVibrate;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AppPrefs";

    // WiFi views
    private TextView tvWifiStatus, tvWifiSsid, tvWifiRssi, tvWifiRssiLabel;

    // Notification views
    private View cardNotificationSettings;
    private TextView tvNotifTypeSettings, tvNotifMsgSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initViews();
        setupNavbar();
        loadSettings();
        observeWifi();
        observeNotification();
    }

    private void initViews() {
        // User email
        TextView tvUserEmail = findViewById(R.id.tv_user_email);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && tvUserEmail != null) {
            tvUserEmail.setText(user.getEmail());
        }

        // Switches
        switchPush = findViewById(R.id.switch_push);
        switchSound = findViewById(R.id.switch_sound);
        switchVibrate = findViewById(R.id.switch_vibrate);

        if (switchPush != null) switchPush.setOnCheckedChangeListener((v, isChecked) -> saveSetting("push", isChecked));
        if (switchSound != null) switchSound.setOnCheckedChangeListener((v, isChecked) -> saveSetting("sound", isChecked));
        if (switchVibrate != null) switchVibrate.setOnCheckedChangeListener((v, isChecked) -> saveSetting("vibrate", isChecked));

        // WiFi views
        tvWifiStatus = findViewById(R.id.tv_wifi_status);
        tvWifiSsid = findViewById(R.id.tv_wifi_ssid);
        tvWifiRssi = findViewById(R.id.tv_wifi_rssi);
        tvWifiRssiLabel = findViewById(R.id.tv_wifi_rssi_label);

        // Notification views
        cardNotificationSettings = findViewById(R.id.card_notification_settings);
        tvNotifTypeSettings = findViewById(R.id.tv_notif_type_settings);
        tvNotifMsgSettings = findViewById(R.id.tv_notif_msg_settings);

        // Back button
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                startActivity(new Intent(this, DashboardActivity.class));
                finish();
            });
        }

        // Logout
        View btnLogout = findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                Toast.makeText(this, "Berhasil keluar", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void observeWifi() {
        // Listen to Status node for WiFi info
        FirebaseManager.getInstance().listenToStatus(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    String wifiStatus = snapshot.child("WiFiStatus").getValue(String.class);
                    updateWifiStatusUI(wifiStatus);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Listen to WiFi node for SSID and RSSI
        FirebaseManager.getInstance().listenToWifi(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    String ssid = snapshot.child("SSID").getValue(String.class);
                    Object rssiObj = snapshot.child("RSSI").getValue();

                    int rssi = rssiObj != null ? Integer.parseInt(rssiObj.toString()) : 0;
                    String rssiLabel = getRssiLabel(rssi);

                    if (tvWifiSsid != null)
                        tvWifiSsid.setText(ssid != null && !ssid.isEmpty() ? ssid : "-");
                    if (tvWifiRssi != null)
                        tvWifiRssi.setText(rssi != 0 ? rssi + " dBm" : "-");
                    if (tvWifiRssiLabel != null) {
                        tvWifiRssiLabel.setText(rssiLabel);
                        if (rssi >= -60) tvWifiRssiLabel.setTextColor(getColor(R.color.status_green));
                        else if (rssi >= -70) tvWifiRssiLabel.setTextColor(getColor(R.color.neon_yellow));
                        else if (rssi < -70) tvWifiRssiLabel.setTextColor(getColor(R.color.neon_red));
                        else tvWifiRssiLabel.setTextColor(getColor(R.color.text_secondary));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String getRssiLabel(int rssi) {
        if (rssi >= -60) return "Sangat Kuat";
        else if (rssi >= -70) return "Baik";
        else if (rssi >= -80) return "Lemah";
        else if (rssi < -80) return "Sangat Lemah";
        else return "-";
    }

    private void observeNotification() {
        FirebaseManager.getInstance().listenToNotification(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (cardNotificationSettings != null)
                        cardNotificationSettings.setVisibility(android.view.View.GONE);
                    return;
                }
                try {
                    String type = snapshot.child("Type").getValue(String.class);
                    String lastMsg = snapshot.child("LastMessage").getValue(String.class);
                    updateNotificationCard(type, lastMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateNotificationCard(String type, String lastMsg) {
        if (cardNotificationSettings == null) return;
        if (type == null || type.isEmpty()) {
            cardNotificationSettings.setVisibility(android.view.View.GONE);
            return;
        }
        cardNotificationSettings.setVisibility(android.view.View.VISIBLE);
        int color;
        switch (type.toUpperCase()) {
            case "CONTACT_DETECTED":
            case "EMERGENCY_STOP":
            case "THEFT_DETECTED":
                color = getColor(R.color.neon_red); break;
            case "AUTO_RESTART":
                color = getColor(R.color.neon_yellow); break;
            default:
                color = getColor(R.color.neon_blue); break;
        }
        if (tvNotifTypeSettings != null) {
            tvNotifTypeSettings.setText(type.replace("_", " "));
            tvNotifTypeSettings.setTextColor(color);
        }
        if (tvNotifMsgSettings != null) {
            tvNotifMsgSettings.setText(lastMsg != null ? lastMsg : "-");
        }
    }

    private void updateWifiStatusUI(String wifiStatus) {
        if (tvWifiStatus == null) return;
        if (wifiStatus == null || wifiStatus.isEmpty()) {
            tvWifiStatus.setText("-");
            tvWifiStatus.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        if (wifiStatus.equalsIgnoreCase("TERHUBUNG") || wifiStatus.equalsIgnoreCase("CONNECTED")) {
            tvWifiStatus.setText("✓ Terhubung");
            tvWifiStatus.setTextColor(getColor(R.color.status_green));
        } else {
            tvWifiStatus.setText("✗ Tidak Terhubung");
            tvWifiStatus.setTextColor(getColor(R.color.neon_red));
        }
    }

    private void saveSetting(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    private void loadSettings() {
        if (switchPush != null) switchPush.setChecked(sharedPreferences.getBoolean("push", true));
        if (switchSound != null) switchSound.setChecked(sharedPreferences.getBoolean("sound", true));
        if (switchVibrate != null) switchVibrate.setChecked(sharedPreferences.getBoolean("vibrate", true));
    }

    private void setupNavbar() {
        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) navHome.setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
        View navVoltage = findViewById(R.id.nav_voltage);
        if (navVoltage != null) navVoltage.setOnClickListener(v -> {
            startActivity(new Intent(this, VoltageActivity.class));
            finish();
        });
        View navGps = findViewById(R.id.nav_gps);
        if (navGps != null) navGps.setOnClickListener(v -> {
            startActivity(new Intent(this, GPSActivity.class));
            finish();
        });
        View btnNotif = findViewById(R.id.btn_notifications);
        if (btnNotif != null) btnNotif.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationActivity.class));
            finish();
        });
        // Settings is current page
        View navSettings = findViewById(R.id.nav_settings);
        if (navSettings != null) navSettings.setOnClickListener(v -> {});
    }
}
