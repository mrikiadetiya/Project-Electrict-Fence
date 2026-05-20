package com.example.electricfence;

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

        tvVoltageVal = findViewById(R.id.tv_voltage_val);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        FirebaseManager.getInstance().listenToSensorData(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChild("voltage")) {
                    double voltage = Double.parseDouble(snapshot.child("voltage").getValue().toString());
                    // Convert V to kV if needed, assuming the requirement was kV for display
                    tvVoltageVal.setText(String.format("%.1f", voltage / 1000));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}