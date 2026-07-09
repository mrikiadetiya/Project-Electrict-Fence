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

/**
 * Monitoring Activity — menampilkan:
 * - OutputStatus (status output pagar dari /ElectricFence/Status/OutputStatus)
 * - Arus input energizer (dari /ElectricFence/Data/Current)
 * - Status arus (dari /ElectricFence/Status/CurrentStatus)
 * - Pulse Count, Interval, PulseLost, PulseStatus, RetryCount (dari /Data + /Status)
 * - Alarm internal energizer (dari /Status/Alarm)
 * - ContactDetected, EmergencyStop, ProtectionStatus (dari /Status)
 */
public class VoltageActivity extends AppCompatActivity {

    // Output Status
    private TextView tvOutputStatusVal;
    private TextView tvProtectionStatusVal;

    // Arus
    private TextView tvCurrentVal, tvCurrentStatusVal;

    // Pulse
    private TextView tvPulseCount, tvPulseInterval, tvPulseStatusVal, tvPulseLostVal, tvRetryCount;
    private LinearLayout layoutPulseWarning;
    private TextView tvPulseWarningMsg;

    // Alarm
    private TextView tvAlarmVal, tvAlarmDetail;
    private LinearLayout layoutAlarmCard;

    // Contact & Emergency
    private LinearLayout layoutContactAlert, layoutEmergencyAlert;
    private TextView tvContactDetectedVal, tvRetryCountContact, tvProtectionStatusContact, tvEmergencyStopVal;
    private MaterialButton btnResetEmergencyVoltage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voltage);

        initViews();
        setupNavbar();
        observeData();
    }

    private void initViews() {
        // Output Status
        tvOutputStatusVal = findViewById(R.id.tv_output_status_val);
        tvProtectionStatusVal = findViewById(R.id.tv_protection_status_val);

        // Arus
        tvCurrentVal = findViewById(R.id.tv_current_val);
        tvCurrentStatusVal = findViewById(R.id.tv_current_status_val);

        // Pulse
        tvPulseCount = findViewById(R.id.tv_pulse_count);
        tvPulseInterval = findViewById(R.id.tv_pulse_interval);
        tvPulseStatusVal = findViewById(R.id.tv_pulse_status_val);
        tvPulseLostVal = findViewById(R.id.tv_pulse_lost_val);
        tvRetryCount = findViewById(R.id.tv_retry_count);
        layoutPulseWarning = findViewById(R.id.layout_pulse_warning);
        tvPulseWarningMsg = findViewById(R.id.tv_pulse_warning_msg);

        // Alarm
        tvAlarmVal = findViewById(R.id.tv_alarm_val);
        tvAlarmDetail = findViewById(R.id.tv_alarm_detail);
        layoutAlarmCard = findViewById(R.id.layout_alarm_card);

        // Contact & Emergency
        layoutContactAlert = findViewById(R.id.layout_contact_alert);
        layoutEmergencyAlert = findViewById(R.id.layout_emergency_alert);
        tvContactDetectedVal = findViewById(R.id.tv_contact_detected_val);
        tvRetryCountContact = findViewById(R.id.tv_retry_count_contact);
        tvProtectionStatusContact = findViewById(R.id.tv_protection_status_contact);
        tvEmergencyStopVal = findViewById(R.id.tv_emergency_stop_val);

        btnResetEmergencyVoltage = findViewById(R.id.btn_reset_emergency_voltage);
        if (btnResetEmergencyVoltage != null) {
            btnResetEmergencyVoltage.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Reset Emergency Stop")
                        .setMessage("Reset Emergency Stop dan aktifkan kembali sistem pagar listrik?")
                        .setPositiveButton("Ya, Reset", (dialog, which) -> {
                            FirebaseManager.getInstance().setResetEmergency(true);
                            Toast.makeText(this, "Perintah Reset Emergency dikirim", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });
        }

        // Back button
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
        // Voltage/Monitoring is current page, no action
        findViewById(R.id.nav_voltage).setOnClickListener(v -> {});
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
    }

    private void observeData() {
        // Listen to Data node
        FirebaseManager.getInstance().listenToData(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    Object currentObj = snapshot.child("Current").getValue();
                    Object pulseCountObj = snapshot.child("PulseCount").getValue();
                    Object pulseIntervalObj = snapshot.child("PulseInterval").getValue();
                    Object retryCountObj = snapshot.child("RetryCount").getValue();

                    float current = currentObj != null ? Float.parseFloat(currentObj.toString()) : 0f;
                    int pulseCount = pulseCountObj != null ? Integer.parseInt(pulseCountObj.toString()) : 0;
                    float pulseInterval = pulseIntervalObj != null ? Float.parseFloat(pulseIntervalObj.toString()) : 0f;
                    int retryCount = retryCountObj != null ? Integer.parseInt(retryCountObj.toString()) : 0;

                    if (tvCurrentVal != null)
                        tvCurrentVal.setText(String.format("%.3f A", current));
                    if (tvPulseCount != null)
                        tvPulseCount.setText(String.valueOf(pulseCount));
                    if (tvPulseInterval != null)
                        tvPulseInterval.setText(String.format("%.2f detik", pulseInterval));
                    if (tvRetryCount != null)
                        tvRetryCount.setText(String.valueOf(retryCount));
                    if (tvRetryCountContact != null)
                        tvRetryCountContact.setText(String.valueOf(retryCount));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Listen to Status node
        FirebaseManager.getInstance().listenToStatus(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    Boolean alarm = snapshot.child("Alarm").getValue(Boolean.class);
                    Boolean pulseLost = snapshot.child("PulseLost").getValue(Boolean.class);
                    Boolean contactDetected = snapshot.child("ContactDetected").getValue(Boolean.class);
                    Boolean emergencyStop = snapshot.child("EmergencyStop").getValue(Boolean.class);
                    String pulseStatus = snapshot.child("PulseStatus").getValue(String.class);
                    String currentStatus = snapshot.child("CurrentStatus").getValue(String.class);
                    String outputStatus = snapshot.child("OutputStatus").getValue(String.class);
                    String protectionStatus = snapshot.child("ProtectionStatus").getValue(String.class);

                    updateOutputStatusUI(outputStatus);
                    updateProtectionStatusUI(protectionStatus);
                    updateCurrentStatusUI(currentStatus);
                    updatePulseUI(pulseLost != null && pulseLost, pulseStatus);
                    updateAlarmUI(alarm != null && alarm);
                    updateContactUI(contactDetected != null && contactDetected);
                    updateEmergencyUI(emergencyStop != null && emergencyStop);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateOutputStatusUI(String outputStatus) {
        if (tvOutputStatusVal == null) return;
        if (outputStatus == null || outputStatus.isEmpty()) {
            tvOutputStatusVal.setText("-");
            tvOutputStatusVal.setTextColor(getColor(R.color.text_secondary));
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
        tvOutputStatusVal.setText(outputStatus);
        tvOutputStatusVal.setTextColor(color);
    }

    private void updateProtectionStatusUI(String protectionStatus) {
        String display = (protectionStatus != null && !protectionStatus.isEmpty()) ? protectionStatus : "-";
        if (tvProtectionStatusVal != null) {
            tvProtectionStatusVal.setText(display);
            tvProtectionStatusVal.setTextColor(getColor(R.color.neon_blue));
        }
        if (tvProtectionStatusContact != null) {
            tvProtectionStatusContact.setText(display);
            tvProtectionStatusContact.setTextColor(getColor(R.color.neon_blue));
        }
    }

    private void updateCurrentStatusUI(String statusStr) {
        if (tvCurrentStatusVal == null) return;
        if (statusStr == null || statusStr.isEmpty()) {
            tvCurrentStatusVal.setText("Belum ada data");
            tvCurrentStatusVal.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        if (statusStr.toUpperCase().contains("TIDAK NORMAL")) {
            tvCurrentStatusVal.setText("⚠ Arus Tidak Normal");
            tvCurrentStatusVal.setTextColor(getColor(R.color.neon_red));
        } else if (statusStr.equalsIgnoreCase("ENERGIZER OFF")) {
            tvCurrentStatusVal.setText("○ Energizer OFF");
            tvCurrentStatusVal.setTextColor(getColor(R.color.text_secondary));
        } else {
            tvCurrentStatusVal.setText("✓ " + statusStr);
            tvCurrentStatusVal.setTextColor(getColor(R.color.status_green));
        }
    }

    private void updatePulseUI(boolean pulseLost, String pulseStatus) {
        // Pulse warning banner
        if (layoutPulseWarning != null) {
            if (pulseLost) {
                layoutPulseWarning.setVisibility(View.VISIBLE);
                if (tvPulseWarningMsg != null)
                    tvPulseWarningMsg.setText("Pulse output pagar hilang. Periksa koneksi energizer ke pagar.");
            } else {
                layoutPulseWarning.setVisibility(View.GONE);
            }
        }

        // Pulse status label
        if (tvPulseStatusVal != null) {
            if (pulseLost) {
                tvPulseStatusVal.setText("✗ Hilang");
                tvPulseStatusVal.setTextColor(getColor(R.color.neon_red));
            } else {
                String label = (pulseStatus != null && !pulseStatus.isEmpty()) ? pulseStatus : "Normal";
                tvPulseStatusVal.setText("✓ " + label);
                tvPulseStatusVal.setTextColor(getColor(R.color.status_green));
            }
        }

        // PulseLost label
        if (tvPulseLostVal != null) {
            if (pulseLost) {
                tvPulseLostVal.setText("⚠ Hilang");
                tvPulseLostVal.setTextColor(getColor(R.color.neon_red));
            } else {
                tvPulseLostVal.setText("✓ Normal");
                tvPulseLostVal.setTextColor(getColor(R.color.status_green));
            }
        }
    }

    private void updateAlarmUI(boolean alarmActive) {
        if (tvAlarmVal == null) return;
        if (alarmActive) {
            tvAlarmVal.setText("⚠ ALARM AKTIF");
            tvAlarmVal.setTextColor(getColor(R.color.neon_red));
            if (tvAlarmDetail != null) {
                tvAlarmDetail.setText("Alarm internal energizer aktif. Kemungkinan terjadi gangguan pada pagar, ground, atau output energizer.");
                tvAlarmDetail.setVisibility(View.VISIBLE);
            }
            if (layoutAlarmCard != null) {
                layoutAlarmCard.setBackgroundColor(0xFFFFECE8);
            }
        } else {
            tvAlarmVal.setText("✓ Normal");
            tvAlarmVal.setTextColor(getColor(R.color.status_green));
            if (tvAlarmDetail != null) {
                tvAlarmDetail.setVisibility(View.GONE);
            }
            if (layoutAlarmCard != null) {
                layoutAlarmCard.setBackgroundColor(0x00000000);
            }
        }
    }

    private void updateContactUI(boolean contactDetected) {
        // Alert banner
        if (layoutContactAlert != null) {
            layoutContactAlert.setVisibility(contactDetected ? View.VISIBLE : View.GONE);
        }
        // Detail card
        if (tvContactDetectedVal != null) {
            if (contactDetected) {
                tvContactDetectedVal.setText("⚠ Terdeteksi");
                tvContactDetectedVal.setTextColor(getColor(R.color.neon_red));
            } else {
                tvContactDetectedVal.setText("✓ Tidak ada");
                tvContactDetectedVal.setTextColor(getColor(R.color.status_green));
            }
        }
    }

    private void updateEmergencyUI(boolean emergencyStop) {
        // Alert banner
        if (layoutEmergencyAlert != null) {
            layoutEmergencyAlert.setVisibility(emergencyStop ? View.VISIBLE : View.GONE);
        }
        // Detail card
        if (tvEmergencyStopVal != null) {
            if (emergencyStop) {
                tvEmergencyStopVal.setText("🚨 AKTIF");
                tvEmergencyStopVal.setTextColor(getColor(R.color.neon_red));
            } else {
                tvEmergencyStopVal.setText("✓ Normal");
                tvEmergencyStopVal.setTextColor(getColor(R.color.status_green));
            }
        }
    }
}
