package com.example.electricfence;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class BackgroundMonitorService extends Service {

    private static final String CHANNEL_ID = "monitor_channel";
    private String lastNotifType = "";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sistem Keamanan Pagar Aktif")
                .setContentText("Memantau pagar secara realtime di latar belakang...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(101, notification);
        }

        // Mulai listen ke Firebase RTDB
        FirebaseManager.getInstance().listenToNotification(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    String type = snapshot.child("Type").getValue(String.class);
                    String lastMsg = snapshot.child("LastMessage").getValue(String.class);

                    // Jika ada notifikasi baru, tampilkan pop-up tinggi
                    if (type != null && !type.isEmpty() && !type.equals(lastNotifType)) {
                        lastNotifType = type;
                        // Panggil NotificationService untuk menampilkan Pop Up besar
                        NotificationService.showSystemNotification(getApplicationContext(), type, lastMsg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Agar terus berjalan
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Monitoring Latar Belakang",
                    NotificationManager.IMPORTANCE_LOW // Low agar tidak berisik
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
