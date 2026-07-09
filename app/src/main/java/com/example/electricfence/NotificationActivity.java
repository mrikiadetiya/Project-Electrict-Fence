package com.example.electricfence;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class NotificationActivity extends AppCompatActivity {

    // Current notification card
    private View cardCurrentNotif;
    private TextView tvNotifType, tvNotifMsg;
    private LinearLayout layoutNoNotif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        initViews();
        setupNavbar();
        loadNotifications();
    }

    private void initViews() {
        cardCurrentNotif = findViewById(R.id.card_current_notif);
        tvNotifType = findViewById(R.id.tv_current_notif_type);
        tvNotifMsg = findViewById(R.id.tv_current_notif_msg);
        layoutNoNotif = findViewById(R.id.layout_no_notif);

        // Back button
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                startActivity(new Intent(this, DashboardActivity.class));
                finish();
            });
        }
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
        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
        // Stay on current page
        findViewById(R.id.btn_notifications).setOnClickListener(v -> {});
    }

    private void loadNotifications() {
        // Listen ke /ElectricFence/Notification node (Type + LastMessage)
        FirebaseManager.getInstance().listenToNotification(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    showNoNotification();
                    return;
                }
                try {
                    String type = snapshot.child("Type").getValue(String.class);
                    String lastMsg = snapshot.child("LastMessage").getValue(String.class);

                    if (type == null || type.isEmpty()) {
                        showNoNotification();
                    } else {
                        showNotification(type, lastMsg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showNoNotification();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showNoNotification();
            }
        });
    }

    private void showNotification(String type, String lastMsg) {
        if (cardCurrentNotif != null) cardCurrentNotif.setVisibility(View.VISIBLE);
        if (layoutNoNotif != null) layoutNoNotif.setVisibility(View.GONE);

        int color;
        String prefix;
        switch (type.toUpperCase()) {
            case "CONTACT_DETECTED":
                color = getColor(R.color.neon_red);
                prefix = "⚠ KONTAK TERDETEKSI";
                break;
            case "EMERGENCY_STOP":
                color = getColor(R.color.neon_red);
                prefix = "🚨 EMERGENCY STOP";
                break;
            case "THEFT_DETECTED":
                color = getColor(R.color.neon_red);
                prefix = "🔒 PENCURIAN TERDETEKSI";
                break;
            case "AUTO_RESTART":
                color = getColor(R.color.neon_yellow);
                prefix = "🔄 AUTO RESTART";
                break;
            default:
                color = getColor(R.color.neon_blue);
                prefix = type.replace("_", " ");
                break;
        }

        if (tvNotifType != null) {
            tvNotifType.setText(prefix);
            tvNotifType.setTextColor(color);
        }
        if (tvNotifMsg != null) {
            tvNotifMsg.setText(lastMsg != null && !lastMsg.isEmpty() ? lastMsg : "-");
        }
    }

    private void showNoNotification() {
        if (cardCurrentNotif != null) cardCurrentNotif.setVisibility(View.GONE);
        if (layoutNoNotif != null) layoutNoNotif.setVisibility(View.VISIBLE);
    }
}
