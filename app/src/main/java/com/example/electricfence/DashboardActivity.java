package com.example.electricfence;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
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

    // Status cards
    private TextView tvRelayStatus, tvAlarmStatus, tvPulseStatus;
    private TextView tvCurrentStatus, tvGpsStatus, tvSimStatus, tvTheftStatus;
    private TextView tvOutputStatusDash, tvProtectionStatusDash, tvContactDash;

    // Alert banners
    private LinearLayout layoutAlertBanner;
    private TextView tvAlertMessage;

    // Emergency banner
    private LinearLayout layoutEmergencyBanner;
    private MaterialButton btnResetEmergencyBanner;

    // Notification card
    private View cardNotificationDash;
    private TextView tvNotifTypeDash, tvNotifMsgDash;

    // Relay control
    private MaterialButton btnPower;
    private TextView tvStatusDot;

    // GPS quick info
    private TextView tvCoordsMain;

    // Pulse/Current quick info
    private TextView tvCurrentMain, tvPulseMain;

    private FirebaseManager firebaseManager;
    private boolean isRelayOn = false;

    // State tracking for alerts
    private boolean alarmActive = false;
    private boolean pulseLost = false;
    private boolean theftDetected = false;
    private boolean emergencyStop = false;
    private boolean contactDetected = false;
    private String currentStatus = "";
    private String gpsStatus = "";
    private String lastNotifType = ""; // Track untuk hindari notifikasi duplikat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        firebaseManager = FirebaseManager.getInstance();
        initViews();
        setupNavigation();
        observeFirebase();

        // Request notification permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Start the background monitoring service
        Intent serviceIntent = new Intent(this, BackgroundMonitorService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void initViews() {
        tvStatusDot = findViewById(R.id.tv_status_dot);
        btnPower = findViewById(R.id.switch_power);
        tvCoordsMain = findViewById(R.id.tv_coords_main);
        tvCurrentMain = findViewById(R.id.tv_current_main);
        tvPulseMain = findViewById(R.id.tv_pulse_main);

        // Status cards
        tvRelayStatus = findViewById(R.id.tv_relay_status);
        tvAlarmStatus = findViewById(R.id.tv_alarm_status);
        tvPulseStatus = findViewById(R.id.tv_pulse_status_dash);
        tvCurrentStatus = findViewById(R.id.tv_current_status_dash);
        tvGpsStatus = findViewById(R.id.tv_gps_status_dash);
        tvSimStatus = findViewById(R.id.tv_sim_status_dash);
        tvTheftStatus = findViewById(R.id.tv_theft_status_dash);
        tvOutputStatusDash = findViewById(R.id.tv_output_status_dash);
        tvProtectionStatusDash = findViewById(R.id.tv_protection_status_dash);
        tvContactDash = findViewById(R.id.tv_contact_dash);

        // Alert banner
        layoutAlertBanner = findViewById(R.id.layout_alert_banner);
        tvAlertMessage = findViewById(R.id.tv_alert_message);

        // Emergency banner
        layoutEmergencyBanner = findViewById(R.id.layout_emergency_banner);
        btnResetEmergencyBanner = findViewById(R.id.btn_reset_emergency_banner);
        if (btnResetEmergencyBanner != null) {
            btnResetEmergencyBanner.setOnClickListener(v -> showResetEmergencyConfirmation());
        }

        // Notification card
        cardNotificationDash = findViewById(R.id.card_notification_dash);
        tvNotifTypeDash = findViewById(R.id.tv_notif_type_dash);
        tvNotifMsgDash = findViewById(R.id.tv_notif_msg_dash);

        btnPower.setOnClickListener(v -> showRelayConfirmation(!isRelayOn));
    }

    private void setupNavigation() {
        // Nav: Dashboard (current) - no action
        findViewById(R.id.nav_home).setOnClickListener(v -> {});
        // Nav: Monitoring
        findViewById(R.id.nav_voltage).setOnClickListener(v ->
                startActivity(new Intent(this, VoltageActivity.class)));
        // Nav: GPS
        findViewById(R.id.nav_gps).setOnClickListener(v ->
                startActivity(new Intent(this, GPSActivity.class)));
        // Nav: GPS card shortcut
        View gpsCard = findViewById(R.id.nav_gps_card);
        if (gpsCard != null) {
            gpsCard.setOnClickListener(v ->
                    startActivity(new Intent(this, GPSActivity.class)));
        }
        // Nav: Notifications
        findViewById(R.id.btn_notifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));
        // Nav: Settings
        findViewById(R.id.nav_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void showRelayConfirmation(boolean targetState) {
        String action = targetState ? "MENGAKTIFKAN" : "MENONAKTIFKAN";
        String consequence = targetState
            ? "Output tegangan tinggi akan aktif. Pastikan area pagar aman sebelum mengaktifkan sistem."
            : "Output tegangan tinggi akan dinonaktifkan. Sistem proteksi pagar akan berhenti beroperasi.";
        new AlertDialog.Builder(this)
                .setTitle(targetState ? "Konfirmasi Aktivasi Sistem" : "Konfirmasi Penonaktifan Sistem")
                .setMessage("Anda akan " + action + " output energizer Sistem Pagar Listrik.\n\n" + consequence)
                .setPositiveButton("Konfirmasi", (dialog, which) -> {
                    firebaseManager.setRelayControl(targetState);
                    Toast.makeText(this, "Perintah " + action + " sistem dikirim ke perangkat", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batalkan", null)
                .show();
    }

    private void showResetEmergencyConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Lockout Proteksi")
                .setMessage("Sistem saat ini dalam kondisi LOCKOUT akibat gangguan berulang.\n\nMelakukan reset akan mengaktifkan kembali output tegangan tinggi. Pastikan kondisi pagar dan lingkungan telah aman sebelum melanjutkan.")
                .setPositiveButton("Konfirmasi Reset", (dialog, which) -> {
                    firebaseManager.setResetEmergency(true);
                    Toast.makeText(this, "Perintah reset lockout dikirim ke perangkat", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batalkan", null)
                .show();
    }

    private void observeFirebase() {
        // Listen to Status node
        firebaseManager.listenToStatus(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    Boolean relay = snapshot.child("Relay").getValue(Boolean.class);
                    Boolean alarm = snapshot.child("Alarm").getValue(Boolean.class);
                    Boolean pulse = snapshot.child("PulseLost").getValue(Boolean.class);
                    Boolean contact = snapshot.child("ContactDetected").getValue(Boolean.class);
                    Boolean emergency = snapshot.child("EmergencyStop").getValue(Boolean.class);
                    String pulseStatusStr = snapshot.child("PulseStatus").getValue(String.class);
                    String currentStatusStr = snapshot.child("CurrentStatus").getValue(String.class);
                    String outputStatusStr = snapshot.child("OutputStatus").getValue(String.class);
                    String protectionStatusStr = snapshot.child("ProtectionStatus").getValue(String.class);
                    String simStatusStr = snapshot.child("SIMStatus").getValue(String.class);
                    String gpsStatusStr = snapshot.child("GPSStatus").getValue(String.class);

                    isRelayOn = relay != null && relay;
                    alarmActive = alarm != null && alarm;
                    pulseLost = pulse != null && pulse;
                    contactDetected = contact != null && contact;
                    emergencyStop = emergency != null && emergency;
                    currentStatus = currentStatusStr != null ? currentStatusStr : "";
                    gpsStatus = gpsStatusStr != null ? gpsStatusStr : "";

                    updateRelayUI(isRelayOn);
                    updateAlarmUI(alarmActive);
                    updatePulseStatusUI(pulseLost, pulseStatusStr);
                    updateCurrentStatusUI(currentStatusStr);
                    updateOutputStatusUI(outputStatusStr);
                    updateProtectionStatusUI(protectionStatusStr);
                    updateContactUI(contactDetected);
                    updateEmergencyUI(emergencyStop);
                    updateGpsStatusUI(gpsStatusStr);
                    updateSimStatusUI(simStatusStr);
                    updateAlertBanner();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Listen to Data node for quick stats
        firebaseManager.listenToData(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    Object currentObj = snapshot.child("Current").getValue();
                    Object pulseCountObj = snapshot.child("PulseCount").getValue();
                    Object pulseIntervalObj = snapshot.child("PulseInterval").getValue();

                    float current = currentObj != null ? Float.parseFloat(currentObj.toString()) : 0f;
                    int pulseCount = pulseCountObj != null ? Integer.parseInt(pulseCountObj.toString()) : 0;
                    float pulseInterval = pulseIntervalObj != null ? Float.parseFloat(pulseIntervalObj.toString()) : 0f;

                    if (tvCurrentMain != null)
                        tvCurrentMain.setText(String.format("%.3f A", current));
                    if (tvPulseMain != null)
                        tvPulseMain.setText(String.format("Count: %d  |  Jeda: %.2fs", pulseCount, pulseInterval));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Listen to GPS node for coords
        firebaseManager.listenToGPS(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    Object latObj = snapshot.child("Latitude").getValue();
                    Object lngObj = snapshot.child("Longitude").getValue();
                    Boolean theft = snapshot.child("TheftDetected").getValue(Boolean.class);
                    String theftStatusStr = snapshot.child("TheftStatus").getValue(String.class);

                    if (latObj != null && lngObj != null) {
                        double lat = Double.parseDouble(latObj.toString());
                        double lng = Double.parseDouble(lngObj.toString());
                        if (tvCoordsMain != null)
                            tvCoordsMain.setText(String.format("%.6f, %.6f", lat, lng));
                    }

                    theftDetected = theft != null && theft;
                    updateTheftStatusUI(theftDetected, theftStatusStr);
                    updateAlertBanner();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Listen to Notification node
        firebaseManager.listenToNotification(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (cardNotificationDash != null)
                        cardNotificationDash.setVisibility(View.GONE);
                    return;
                }
                try {
                    String type = snapshot.child("Type").getValue(String.class);
                    String lastMsg = snapshot.child("LastMessage").getValue(String.class);
                    updateNotificationCard(type, lastMsg);

                    // Tampilkan system notification HANYA jika tipe berubah (bukan setiap app dibuka)
                    if (type != null && !type.isEmpty() && !type.equals(lastNotifType)) {
                        lastNotifType = type;
                        NotificationService.showSystemNotification(DashboardActivity.this, type, lastMsg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // ===================== UI UPDATE METHODS =====================

    private void updateRelayUI(boolean isOn) {
        if (tvRelayStatus == null) return;
        if (isOn) {
            tvRelayStatus.setText("AKTIF");
            tvRelayStatus.setTextColor(getColor(R.color.status_green));
            tvStatusDot.setText("● OUTPUT TEGANGAN TINGGI AKTIF");
            tvStatusDot.setTextColor(getColor(R.color.status_green));
            btnPower.setText("NONAKTIFKAN SISTEM");
            btnPower.setTextColor(getColor(R.color.neon_red));
            btnPower.setStrokeColorResource(R.color.neon_red);
            btnPower.setIconTintResource(R.color.neon_red);
        } else {
            tvRelayStatus.setText("NONAKTIF");
            tvRelayStatus.setTextColor(getColor(R.color.text_secondary));
            tvStatusDot.setText("○ OUTPUT TEGANGAN TINGGI NONAKTIF");
            tvStatusDot.setTextColor(getColor(R.color.text_secondary));
            btnPower.setText("AKTIFKAN SISTEM");
            btnPower.setTextColor(getColor(R.color.status_green));
            btnPower.setStrokeColorResource(R.color.status_green);
            btnPower.setIconTintResource(R.color.status_green);
        }
    }

    private void updateAlarmUI(boolean isActive) {
        if (tvAlarmStatus == null) return;
        if (isActive) {
            tvAlarmStatus.setText("⚠ ALARM TERDETEKSI");
            tvAlarmStatus.setTextColor(getColor(R.color.neon_red));
        } else {
            tvAlarmStatus.setText("✓ Nominal");
            tvAlarmStatus.setTextColor(getColor(R.color.status_green));
        }
    }

    private void updatePulseStatusUI(boolean lost, String pulseStatusStr) {
        if (tvPulseStatus == null) return;
        if (lost) {
            tvPulseStatus.setText("✗ Tidak Terdeteksi");
            tvPulseStatus.setTextColor(getColor(R.color.neon_red));
        } else {
            String label = (pulseStatusStr != null && !pulseStatusStr.isEmpty()) ? pulseStatusStr : "Nominal";
            tvPulseStatus.setText("✓ " + label);
            tvPulseStatus.setTextColor(getColor(R.color.status_green));
        }
    }

    private void updateCurrentStatusUI(String statusStr) {
        if (tvCurrentStatus == null) return;
        if (statusStr == null || statusStr.isEmpty()) {
            tvCurrentStatus.setText("-");
            tvCurrentStatus.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        if (statusStr.toUpperCase().contains("TIDAK NORMAL")) {
            tvCurrentStatus.setText("✗ Arus Abnormal");
            tvCurrentStatus.setTextColor(getColor(R.color.neon_red));
        } else if (statusStr.equalsIgnoreCase("ENERGIZER OFF")) {
            tvCurrentStatus.setText("○ Energizer Nonaktif");
            tvCurrentStatus.setTextColor(getColor(R.color.text_secondary));
        } else {
            tvCurrentStatus.setText("✓ " + statusStr);
            tvCurrentStatus.setTextColor(getColor(R.color.status_green));
        }
    }

    private void updateOutputStatusUI(String outputStatus) {
        if (tvOutputStatusDash == null) return;
        if (outputStatus == null || outputStatus.isEmpty()) {
            tvOutputStatusDash.setText("-");
            tvOutputStatusDash.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        String colorKey = ElectricFenceModel.getOutputStatusColor(outputStatus);
        int color;
        switch (colorKey) {
            case "green":  color = getColor(R.color.status_green); break;
            case "yellow": color = getColor(R.color.neon_yellow); break;
            case "red":    color = getColor(R.color.neon_red); break;
            default:       color = getColor(R.color.text_secondary); break;
        }
        tvOutputStatusDash.setText(outputStatus);
        tvOutputStatusDash.setTextColor(color);
    }

    private void updateProtectionStatusUI(String protectionStatus) {
        if (tvProtectionStatusDash == null) return;
        if (protectionStatus == null || protectionStatus.isEmpty()) {
            tvProtectionStatusDash.setText("-");
            tvProtectionStatusDash.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        tvProtectionStatusDash.setText(protectionStatus);
        tvProtectionStatusDash.setTextColor(getColor(R.color.neon_blue));
    }

    private void updateContactUI(boolean detected) {
        if (tvContactDash == null) return;
        if (detected) {
            tvContactDash.setText("⚠ Gangguan Terdeteksi");
            tvContactDash.setTextColor(getColor(R.color.neon_red));
        } else {
            tvContactDash.setText("✓ Kondisi Aman");
            tvContactDash.setTextColor(getColor(R.color.status_green));
        }
    }

    private void updateEmergencyUI(boolean isEmergency) {
        if (layoutEmergencyBanner == null) return;
        layoutEmergencyBanner.setVisibility(isEmergency ? View.VISIBLE : View.GONE);
    }

    private void updateGpsStatusUI(String statusStr) {
        if (tvGpsStatus == null) return;
        if (statusStr == null || statusStr.isEmpty()) {
            tvGpsStatus.setText("-");
            tvGpsStatus.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        if (statusStr.equalsIgnoreCase("VALID")) {
            tvGpsStatus.setText("✓ Fix Diperoleh");
            tvGpsStatus.setTextColor(getColor(R.color.status_green));
        } else {
            tvGpsStatus.setText("⚠ Menunggu Fix");
            tvGpsStatus.setTextColor(getColor(R.color.neon_yellow));
        }
    }

    private void updateSimStatusUI(String statusStr) {
        if (tvSimStatus == null) return;
        if (statusStr == null || statusStr.isEmpty()) {
            tvSimStatus.setText("-");
            tvSimStatus.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        if (statusStr.equalsIgnoreCase("TERHUBUNG")) {
            tvSimStatus.setText("✓ Jaringan Aktif");
            tvSimStatus.setTextColor(getColor(R.color.status_green));
        } else {
            tvSimStatus.setText("✗ Koneksi Terputus");
            tvSimStatus.setTextColor(getColor(R.color.neon_red));
        }
    }

    private void updateTheftStatusUI(boolean isTheft, String theftStatusStr) {
        if (tvTheftStatus == null) return;
        if (isTheft) {
            tvTheftStatus.setText("🔒 LOKASI BERUBAH");
            tvTheftStatus.setTextColor(getColor(R.color.neon_red));
        } else {
            tvTheftStatus.setText("✓ Posisi Normal");
            tvTheftStatus.setTextColor(getColor(R.color.status_green));
        }
    }

    private void updateNotificationCard(String type, String lastMsg) {
        if (cardNotificationDash == null) return;
        if (type == null || type.isEmpty()) {
            cardNotificationDash.setVisibility(View.GONE);
            return;
        }
        cardNotificationDash.setVisibility(View.VISIBLE);
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
        if (tvNotifTypeDash != null) {
            tvNotifTypeDash.setText(type.replace("_", " "));
            tvNotifTypeDash.setTextColor(color);
        }
        if (tvNotifMsgDash != null) {
            tvNotifMsgDash.setText(lastMsg != null ? lastMsg : "-");
        }
    }

    private void updateAlertBanner() {
        if (layoutAlertBanner == null || tvAlertMessage == null) return;

        StringBuilder alertMsg = new StringBuilder();

        if (emergencyStop)
            alertMsg.append("🚨 SISTEM LOCKOUT — Perangkat memerlukan reset manual.\n");
        if (contactDetected)
            alertMsg.append("⚡ GANGGUAN TERDETEKSI — Kontak pada pagar aktif.\n");
        if (alarmActive)
            alertMsg.append("⚠ ALARM ENERGIZER — Kemungkinan gangguan pada output.\n");
        if (pulseLost)
            alertMsg.append("⚠ PULSE HILANG — Output tegangan tinggi tidak terdeteksi.\n");
        if (theftDetected)
            alertMsg.append("🔒 PERINGATAN LOKASI — Perangkat terdeteksi berpindah dari titik referensi.\n");
        if (currentStatus.toUpperCase().contains("TIDAK NORMAL"))
            alertMsg.append("⚠ ARUS ABNORMAL — Periksa kondisi energizer dan jalur kabel.\n");
        if (gpsStatus.equalsIgnoreCase("BELUM FIX"))
            alertMsg.append("⚠ GPS BELUM FIX — Sinyal satelit belum mencukupi.\n");

        if (alertMsg.length() > 0) {
            tvAlertMessage.setText(alertMsg.toString().trim());
            layoutAlertBanner.setVisibility(View.VISIBLE);
        } else {
            layoutAlertBanner.setVisibility(View.GONE);
        }
    }
}
