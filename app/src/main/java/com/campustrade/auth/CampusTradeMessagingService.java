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
    public static final String CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID;

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
        String orderId = "";
        if (message.getData() != null && !message.getData().isEmpty()) {
            if (message.getData().containsKey("title")) title = message.getData().get("title");
            if (message.getData().containsKey("body")) body = message.getData().get("body");
            if (message.getData().containsKey("category")) category = message.getData().get("category");
            if (message.getData().containsKey("peer")) peer = message.getData().get("peer");
            if (message.getData().containsKey("orderId")) orderId = message.getData().get("orderId");
        } else if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }

        SharedPreferences session = com.campustrade.auth.data.SecurePrefs.get(this, MainActivity.PREFS);
        String email = session.getString("student_email", "");
        SharedPreferences settings = com.campustrade.auth.data.SecurePrefs.get(this, MainActivity.PREFS + "_settings_" + email);
        boolean pushEnabled = settings.getBoolean("push", true);
        boolean screenPrivacy = settings.getBoolean("screen_privacy", false);
        if (!pushEnabled) return;

        boolean orderCategory = category.equals("payment") || category.equals("order") || category.equals("orders");
        boolean chatCategory = category.equals("messages") || category.equals("chat") || category.equals("chats");
        // Admin replies arrive as a push but also need to appear as a
        // conversation in the student's Inbox, even when the admin is using
        // the passcode-only admin session.
        if (chatCategory && "admin@campustrade.local".equals(peer) && !email.isEmpty()) {
            try {
                SharedPreferences messagePrefs = com.campustrade.auth.data.SecurePrefs.get(this, MainActivity.PREFS + "_messages");
                JSONArray messages = new JSONArray(messagePrefs.getString("messages", "[]"));
                String remoteId = message.getMessageId() == null ? "" : message.getMessageId();
                boolean duplicate = false;
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject existing = messages.optJSONObject(i);
                    if (existing != null && !remoteId.isEmpty()
                            && remoteId.equals(existing.optString("remoteId"))) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    messages.put(new JSONObject()
                            .put("sender", "admin@campustrade.local")
                            .put("recipient", email)
                            .put("name", "Admin")
                            .put("body", body)
                            .put("time", System.currentTimeMillis())
                            .put("read", false)
                            .put("remoteId", remoteId)
                            .put("readAt", 0));
                    while (messages.length() > 500) messages.remove(0);
                    messagePrefs.edit().putString("messages", messages.toString()).apply();
                }
            } catch (org.json.JSONException ignored) { }
        }
        boolean allowedCategory = orderCategory || settings.getBoolean(chatCategory ? "messages" : category.equals("marketplace") ? "marketplace" : category.equals("groups") ? "groups" : category.equals("deadlines") ? "deadlines" : "push", true);
        if (!allowedCategory) return;

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) return;

        String channelId = orderCategory ? Constants.ORDER_CHANNEL_ID
                : chatCategory ? Constants.CHAT_CHANNEL_ID : CHANNEL_ID;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(manager, Constants.NOTIFICATION_CHANNEL_ID, "CampusTrade updates",
                    "Campus announcements, listings, deadlines and groups.", NotificationManager.IMPORTANCE_DEFAULT);
            createChannel(manager, Constants.ORDER_CHANNEL_ID, "CampusTrade orders",
                    "Purchase requests, collection checks, returns and invoices.", NotificationManager.IMPORTANCE_HIGH);
            createChannel(manager, Constants.CHAT_CHANNEL_ID, "CampusTrade chats",
                    "Messages from buyers, sellers and classmates.", NotificationManager.IMPORTANCE_HIGH);
        }

        String displayBody = screenPrivacy ? "Open CampusTrade to view this notification." : body;
        String displayTitle = screenPrivacy ? "CampusTrade" : title;

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (chatCategory) {
            intent.putExtra("open_inbox", true);
            if (peer != null && !peer.isEmpty()) intent.putExtra("open_peer", peer);
        } else if (orderCategory) {
            intent.putExtra("open_orders", true);
            if (orderId != null && !orderId.isEmpty()) intent.putExtra("open_order", orderId);
        } else {
            intent.putExtra("open_notifications", true);
            intent.putExtra("open_notification_filter", orderCategory ? "Orders" : chatCategory ? "Chats" : "Updates");
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        int requestCode = (title + "|" + category + "|" + peer + "|" + orderId).hashCode() & 0x7fffffff;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent, flags);

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new android.app.Notification.Builder(this, channelId)
            : new android.app.Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setStyle(new android.app.Notification.BigTextStyle().bigText(displayBody))
            .setContentIntent(pendingIntent)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(chatCategory ? android.app.Notification.CATEGORY_MESSAGE : android.app.Notification.CATEGORY_STATUS);
        try {
            manager.notify((title + (peer == null ? "" : peer) + (orderId == null ? "" : orderId)).hashCode() & 0x7fffffff, builder.build());
        } catch (SecurityException e) {
            android.util.Log.w("CampusTrade", "notification blocked: " + e.getMessage());
        }

        if (!email.isEmpty()) {
            try {
                SharedPreferences alertsPrefs = com.campustrade.auth.data.SecurePrefs.get(this, MainActivity.PREFS + "_alerts");
                JSONArray data = new JSONArray(alertsPrefs.getString(email, "[]"));
                data.put(new JSONObject().put("title", title).put("body", body).put("time", System.currentTimeMillis())
                        .put("category", category).put("read", false).put("peer", peer)
                        .put("orderId", orderId == null ? "" : orderId));
                while (data.length() > 100) data.remove(0);
                alertsPrefs.edit().putString(email, data.toString()).apply();
            } catch (org.json.JSONException ignored) { }
        }
    }

    private void createChannel(NotificationManager manager, String id, String name, String description, int importance) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description);
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }
}