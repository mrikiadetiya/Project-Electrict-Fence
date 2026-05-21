package com.example.electricfence;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private List<NotificationModel> notificationList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        rvNotifications = findViewById(R.id.rv_notifications);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        
        notificationList = new ArrayList<>();
        adapter = new NotificationAdapter(notificationList);
        rvNotifications.setAdapter(adapter);

        setupNavbar();
        loadNotifications();
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
        findViewById(R.id.btn_notifications).setOnClickListener(v -> {
            rvNotifications.smoothScrollToPosition(0);
        });
    }

    private void loadNotifications() {
        FirebaseManager.getInstance().listenToLogs(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationList.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    NotificationModel notif = postSnapshot.getValue(NotificationModel.class);
                    if (notif != null) {
                        notificationList.add(notif);
                    }
                }
                Collections.reverse(notificationList); // Show newest first
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }
}
