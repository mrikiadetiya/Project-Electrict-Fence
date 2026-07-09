package com.example.electricfence;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * NotificationService — dua fungsi:
 * 1. FCM Push Notification (jika Firebase Cloud Messaging dikonfigurasi)
 * 2. Juga digunakan sebagai helper untuk showSystemNotification() dari DashboardActivity
 *
 * Untuk push notification dari Firebase RTDB, DashboardActivity akan memanggil
 * NotificationService.showSystemNotification() secara langsung saat data berubah.
 */
public class NotificationService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "fence_alerts";
    private static final String CHANNEL_NAME = "Electric Fence Alerts";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "Electric Fence Alert";
        String body = "";

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null
                    ? remoteMessage.getNotification().getTitle() : title;
            body = remoteMessage.getNotification().getBody() != null
                    ? remoteMessage.getNotification().getBody() : body;
        } else if (!remoteMessage.getData().isEmpty()) {
            // Data-only message
            title = remoteMessage.getData().getOrDefault("title", title);
            body = remoteMessage.getData().getOrDefault("body", "");
        }

        showNotificationInternal(this, title, body);
    }

    /**
     * Tampilkan system notification dari kode manapun (DashboardActivity, dll)
     * Dipanggil ketika Firebase RTDB /Notification node berubah
     */
    public static void showSystemNotification(Context context, String type, String message) {
        if (context == null || type == null || type.isEmpty()) return;

        String title;
        switch (type.toUpperCase()) {
            case "CONTACT_DETECTED":
                title = "⚠ Kontak Terdeteksi!";
                break;
            case "EMERGENCY_STOP":
                title = "🚨 Emergency Stop Aktif!";
                break;
            case "THEFT_DETECTED":
                title = "🔒 Perangkat Berpindah!";
                break;
            case "AUTO_RESTART":
                title = "🔄 Sistem Auto-Restart";
                break;
            case "ALARM_ACTIVE":
                title = "⚡ Alarm Energizer Aktif";
                break;
            case "RETRY_LIMIT":
                title = "⚠ Batas Percobaan Tercapai";
                break;
            default:
                title = "Electric Fence: " + type.replace("_", " ");
                break;
        }

        showNotificationInternal(context, title, message != null ? message : "-");
    }

    private static void showNotificationInternal(Context context, String title, String messageBody) {
        Intent intent = new Intent(context, NotificationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alert dari sistem pagar listrik");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 200, 300});
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[]{0, 300, 200, 300})
                .setContentIntent(pendingIntent);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}