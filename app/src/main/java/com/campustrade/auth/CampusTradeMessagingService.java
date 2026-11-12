package com.campustrade.auth;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

// Receive push messages and add them to the alert list.
public class CampusTradeMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "campustrade_updates_v2";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.isEmpty() || token == null || token.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("fcmToken", token)
            .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "FCM token save failed: " + e.getMessage()));
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        String title = "CampusTrade";
        String body = "New update";
        String category = "general";
        String peer = "";
        if (message.getData() != null && !message.getData().isEmpty()) {
            if (message.getData().containsKey("title")) title = message.getData().get("title");
            if (message.getData().containsKey("body")) body = message.getData().get("body");
            if (message.getData().containsKey("category")) category = message.getData().get("category");
            if (message.getData().containsKey("peer")) peer = message.getData().get("peer");
        } else if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }

        SharedPreferences session = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String email = session.getString("student_email", "");
        SharedPreferences settings = getSharedPreferences(MainActivity.PREFS + "_settings_" + email, Context.MODE_PRIVATE);
        boolean pushEnabled = settings.getBoolean("push", true);
        boolean screenPrivacy = settings.getBoolean("screen_privacy", false);
        if (!pushEnabled) return;

        boolean allowedCategory = category.equals("payment") || settings.getBoolean(category.equals("messages") ? "messages" : category.equals("marketplace") ? "marketplace" : category.equals("groups") ? "groups" : category.equals("deadlines") ? "deadlines" : "push", true);
        if (!allowedCategory) return;

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "CampusTrade alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Messages, orders and campus activity that pop up on screen.");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            manager.createNotificationChannel(channel);
        }

        String displayBody = screenPrivacy ? "Open CampusTrade to view this notification." : body;
        String displayTitle = screenPrivacy ? "CampusTrade" : title;

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (category.equals("messages")) {
            intent.putExtra("open_inbox", true);
            if (peer != null && !peer.isEmpty()) intent.putExtra("open_peer", peer);
        } else {
            intent.putExtra("open_notifications", true);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new android.app.Notification.Builder(this, CHANNEL_ID)
            : new android.app.Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setStyle(new android.app.Notification.BigTextStyle().bigText(displayBody))
            .setContentIntent(pendingIntent)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(category.equals("messages") ? android.app.Notification.CATEGORY_MESSAGE : android.app.Notification.CATEGORY_STATUS);
        try {
            manager.notify((title + (peer == null ? "" : peer)).hashCode() & 0x7fffffff, builder.build());
        } catch (SecurityException e) {
            android.util.Log.w("CampusTrade", "notification blocked: " + e.getMessage());
        }

        if (!email.isEmpty()) {
            try {
                SharedPreferences alertsPrefs = getSharedPreferences(MainActivity.PREFS + "_alerts", Context.MODE_PRIVATE);
                JSONArray data = new JSONArray(alertsPrefs.getString(email, "[]"));
                data.put(new JSONObject().put("title", title).put("body", body).put("time", System.currentTimeMillis())
                        .put("category", category).put("read", false).put("peer", peer));
                while (data.length() > 100) data.remove(0);
                alertsPrefs.edit().putString(email, data.toString()).apply();
            } catch (org.json.JSONException ignored) { }
        }
    }
}