package com.example.electricfence;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsActivity extends AppCompatActivity {

    private SwitchCompat switchPush, switchSound, switchVibrate;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AppPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        initViews();
        setupNavbar();
        loadSettings();
    }

    private void initViews() {
        // Tampilkan Email User Aktif
        TextView tvUserEmail = findViewById(R.id.tv_user_email);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            tvUserEmail.setText(user.getEmail());
        }

        // Switches
        switchPush = findViewById(R.id.switch_push);
        switchSound = findViewById(R.id.switch_sound);
        switchVibrate = findViewById(R.id.switch_vibrate);

        // Save settings on change
        switchPush.setOnCheckedChangeListener((v, isChecked) -> saveSetting("push", isChecked));
        switchSound.setOnCheckedChangeListener((v, isChecked) -> saveSetting("sound", isChecked));
        switchVibrate.setOnCheckedChangeListener((v, isChecked) -> saveSetting("vibrate", isChecked));

        // Tombol Back di Header
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });

        // Tombol Logout
        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Toast.makeText(this, "Berhasil keluar", Toast.LENGTH_SHORT).show();
        });
    }

    private void saveSetting(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    private void loadSettings() {
        switchPush.setChecked(sharedPreferences.getBoolean("push", true));
        switchSound.setChecked(sharedPreferences.getBoolean("sound", true));
        switchVibrate.setChecked(sharedPreferences.getBoolean("vibrate", true));
    }

    private void setupNavbar() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
        findViewById(R.id.nav_voltage).setOnClickListener(v -> {
            startActivity(new Intent(this, VoltageActivity.class));
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
    }
}