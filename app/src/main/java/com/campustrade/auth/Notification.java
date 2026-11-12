package com.campustrade.auth;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import com.campustrade.auth.model.Chat;

// In-app alerts, phone notifications and push messages.
public class Notification {

    private final MainActivity host;

    public Notification(MainActivity host) {
        this.host = host;
    }

    void addNotification(String email, String title, String body) {
        addNotification(email, title, body, "general", "");
    }

    // Each student has a list; keep the newest 100.
    void addNotification(String email, String title, String body, String category, String peer) {
        SharedPreferences prefs = host.getSharedPreferences(host.PREFS + "_alerts", Context.MODE_PRIVATE);
        try {
            JSONArray data = new JSONArray(prefs.getString(email, "[]"));
            data.put(new JSONObject().put("title", title).put("body", body).put("time", System.currentTimeMillis())
                    .put("category", category).put("read", false).put("peer", peer));
            while (data.length() > 100) data.remove(0);
            prefs.edit().putString(email, data.toString()).apply();
        } catch (org.json.JSONException e) { }
    }

    void sendPush(String recipientEmail, String title, String body) {
        sendPush(recipientEmail, title, body, "", "");
    }

    // Fetch a fresh Firebase token before sending a push.
    void sendPush(String recipientEmail, String title, String body, String category, String peer) {
        if (recipientEmail == null || recipientEmail.trim().isEmpty() || title == null || body == null) return;
        if (!host.settingsPrefs().getBoolean("push", true)) return;

        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        user.getIdToken(false).addOnSuccessListener(result -> {
            String idToken = result == null ? null : result.getToken();
            if (idToken == null || idToken.isEmpty()) return;

            // Send in the background and log any failure.
            new Thread(() -> {
                java.net.HttpURLConnection connection = null;
                try {
                    java.net.URL url = new java.net.URL("https://campustrade-fcm.vercel.app/api/send");
                    connection = (java.net.HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Authorization", "Bearer " + idToken);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);

                    org.json.JSONObject payload = new org.json.JSONObject();
                    String recipient = recipientEmail.trim();
                    if (recipient.indexOf('@') >= 0) {
                        payload.put("recipientEmail", recipient);
                    } else {
                        payload.put("recipientUid", recipient);
                    }
                    payload.put("title", title);
                    payload.put("body", body);
                    payload.put("category", category == null || category.isEmpty() ? "general" : category);
                    payload.put("peer", peer == null ? "" : peer);

                    byte[] bytes = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    try (java.io.OutputStream output = connection.getOutputStream()) {
                        output.write(bytes);
                    }
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) {
                        android.util.Log.w("CampusTrade", "Push send returned HTTP " + code);
                    }
                } catch (Exception e) {
                    android.util.Log.w("CampusTrade", "Push send failed: " + e.getMessage());
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }).start();
        });
    }

    void showNotificationHistory() {
        if (!host.currentTab.equals("Notifications")) host.notificationsReturnTab = host.currentTab;
        host.showAppScreen("Notifications");
    }

    public JSONArray notificationItems() {
        try {
            return new JSONArray(host.getSharedPreferences(host.PREFS + "_alerts", Context.MODE_PRIVATE).getString(host.currentStudentEmail, "[]"));
        } catch (org.json.JSONException e) { return new JSONArray(); }
    }

    public int unreadNotifications() {
        JSONArray data = notificationItems();
        int unread = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item != null && !item.optBoolean("read", false)) unread++;
        }
        return unread;
    }

    public void saveNotificationItems(JSONArray items) {
        host.getSharedPreferences(host.PREFS + "_alerts", Context.MODE_PRIVATE).edit().putString(host.currentStudentEmail, items.toString()).apply();
    }

    // Old alerts have no category, so guess one from the title.
    public String alertCategory(JSONObject alert) {
        String category = alert.optString("category");
        if (!category.isEmpty() && !category.equals("general")) return category;
        String title = alert.optString("title").toLowerCase(Locale.ROOT);
        if (title.contains("payment") || title.contains("receipt") || title.contains("payout")) return "payment";
        if (title.contains("message") || title.contains("replied")) return "messages";
        if (title.contains("deadline") || title.contains("assignment")) return "deadline";
        if (title.contains("group")) return "groups";
        if (title.contains("listing") || title.contains("price")) return "marketplace";
        return "general";
    }

    String alertTime(long time) {
        long minutes = Math.max(0, System.currentTimeMillis() - time) / 60000;
        if (minutes == 0) return "Just now";
        if (minutes < 60) return minutes + " min ago";
        if (minutes < 24 * 60) return minutes / 60 + " hr ago";
        if (minutes < 48 * 60) return "Yesterday";
        return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(time));
    }

    String notificationGroup(long time) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar then = java.util.Calendar.getInstance();
        then.setTimeInMillis(time);
        boolean today = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR);
        if (today) return "Today";
        java.util.Calendar yesterday = (java.util.Calendar) now.clone();
        yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1);
        boolean wasYesterday = yesterday.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
                && yesterday.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR);
        return wasYesterday ? "Yesterday" : "Earlier";
    }

    public void buildNotifications(LinearLayout content) {
        JSONArray data = notificationItems();
        LinearLayout toolbar = host.ui.horizontal();
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView settings = host.ui.textButton("Notification settings");
        settings.setOnClickListener(v -> host.showAppScreen("Settings"));
        toolbar.addView(settings, new LinearLayout.LayoutParams(0, host.ui.dp(44), 1));
        TextView allRead = host.ui.textButton("Mark all read");
        allRead.setOnClickListener(v -> {
            for (int i = 0; i < data.length(); i++) {
                try { data.getJSONObject(i).put("read", true); } catch (org.json.JSONException ignored) { }
            }
            saveNotificationItems(data); host.showAppScreen("Notifications");
        });
        toolbar.addView(allRead);
        content.addView(toolbar);

        LinearLayout history = new LinearLayout(host);
        history.setOrientation(LinearLayout.VERTICAL);
        String lastGroup = "";
        for (int i = data.length() - 1; i >= 0; i--) {
            JSONObject alert = data.optJSONObject(i);
            if (alert == null) continue;
            String category = alertCategory(alert);
            boolean unread = !alert.optBoolean("read", false);
            String group = notificationGroup(alert.optLong("time"));
            if (!group.equals(lastGroup)) {
                TextView heading = host.ui.tinyCaps(group.toUpperCase(Locale.ROOT), host.muted);
                heading.setPadding(host.ui.dp(4), host.ui.dp(lastGroup.isEmpty() ? 6 : 18), 0, host.ui.dp(6));
                history.addView(heading);
                lastGroup = group;
            }
            String icon = category.equals("payment") ? "payment" : category.equals("deadline") ? "calendar"
                    : category.equals("messages") ? "chat" : category.equals("groups") ? "team" : "bag";
            int accent = category.equals("deadline") ? Color.rgb(217, 139, 0)
                    : category.equals("groups") ? host.blue
                    : category.equals("payment") || category.equals("marketplace") ? host.green : host.blue;
            int iconBackground = category.equals("deadline") ? Color.rgb(255, 244, 218)
                    : category.equals("groups") ? Color.rgb(240, 236, 255)
                    : accent == host.green ? Color.rgb(229, 247, 239) : Color.rgb(234, 242, 255);
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.TOP);
            row.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
            row.setBackground(host.ui.strokedRound(unread
                    ? (host.nightActive ? Color.rgb(34, 45, 62) : Color.rgb(234, 242, 255))
                    : host.surface(), 12, unread ? (host.nightActive ? host.blue : Color.rgb(198, 216, 242)) : host.line));
            View symbol = host.new CampusAssetView(host, icon, accent);
            symbol.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
            symbol.setBackground(host.ui.round(iconBackground, 12));
            row.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(38), host.ui.dp(38)));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
            copy.addView(host.ui.titleText(alert.optString("title"), host.text, 12));
            TextView body = host.ui.bodyText(alert.optString("body"), host.muted, 11);
            body.setPadding(0, host.ui.dp(3), 0, host.ui.dp(4));
            copy.addView(body);
            copy.addView(host.ui.bodyText(alertTime(alert.optLong("time")), host.muted, 10));
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (unread) {
                View dot = new View(host);
                dot.setBackground(host.ui.round(host.blue, 999));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(host.ui.dp(7), host.ui.dp(7));
                params.topMargin = host.ui.dp(5);
                row.addView(dot, params);
            }
            row.addView(host.new CampusAssetView(host, "chevron", host.muted),
                    new LinearLayout.LayoutParams(host.ui.dp(14), host.ui.dp(14)));
            // Every alert opens the most useful screen.
            row.setOnClickListener(v -> {
                try { alert.put("read", true); } catch (org.json.JSONException ignored) { }
                saveNotificationItems(data);
                String peer = alert.optString("peer");
                if (category.equals("messages") && !peer.isEmpty() && !peer.equals(host.currentStudentEmail)) {
                    host.showAppScreen("Notifications");
                    host.inbox.showConversation(peer);
                } else if (category.equals("payment")) {
                    host.order.showTransactionHistory();
                } else if (category.equals("marketplace")) {
                    host.showAppScreen("Market");
                } else if (category.equals("deadline")) {
                    host.showAppScreen("Academics");
                } else if (category.equals("groups")) {
                    host.showAppScreen("Groups");
                } else {
                    host.showAppScreen("Notifications");
                    host.ui.showSimpleMessage(alert.optString("title"), alert.optString("body"));
                }
            });
            host.ui.attachPressAnimation(row);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = host.ui.dp(6);
            history.addView(row, rowParams);
        }
        if (history.getChildCount() == 0) {
            history.addView(host.ui.emptyState("bell", "No notifications yet",
                    "Order updates, messages and campus alerts will appear here."));
        }
        content.addView(history);

        Button openSettings = host.ui.secondaryButton("Open notification settings");
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        settingsParams.topMargin = host.ui.dp(14);
        openSettings.setOnClickListener(v -> host.showAppScreen("Settings"));
        content.addView(openSettings, settingsParams);
    }

    // Android 8+ needs a channel; making it again is safe.
    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) host.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "CampusTrade alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Messages, orders and campus activity that pop up on screen.");
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    // Android 13+ asks for notification permission.
    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (host.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        host.requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, host.NOTIFICATION_PERMISSION_REQUEST);
    }

    public boolean canShowNotifications() {
        NotificationManager manager = (NotificationManager) host.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && manager != null && !manager.areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || host.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    void showCampusNotification(String title, String message) {
        showCampusNotification(title, message, "general");
    }

    void showCampusNotification(String title, String message, String category) {
        showCampusNotification(title, message, category, "");
    }

    // Save the alert first, then show the phone notification.
    void showCampusNotification(String title, String message, String category, String peer) {
        addNotification(host.currentStudentEmail, title, message, category, peer);
        String preference = category.equals("deadline") ? "deadlines" : category;
        // Payment alerts ignore quiet hours and category switches.
        boolean allowed = canShowNotifications() && host.settingsPrefs().getBoolean("push", true)
                && (category.equals("payment") || !host.profile.inQuietHours())
                && (category.equals("payment") || host.settingsPrefs().getBoolean(preference, true));
        if (!allowed) {
            android.util.Log.d("CampusTrade", "system notification suppressed: category=" + category
                    + " enabled=" + canShowNotifications()
                    + " push=" + host.settingsPrefs().getBoolean("push", true)
                    + " quiet=" + host.profile.inQuietHours());
            return;
        }

        // Hide message text on the lock screen.
        String displayMessage = host.settingsPrefs().getBoolean("screen_privacy", false)
                ? "Open CampusTrade to view this notification." : message;
        Intent intent = new Intent(host, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // Chat alerts open that chat; other alerts open the notification list.
        if (category.equals("messages")) {
            intent.putExtra("open_inbox", true);
            if (peer != null && !peer.isEmpty()) intent.putExtra("open_peer", peer);
        } else intent.putExtra("open_notifications", true);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(host, 0, intent, flags);

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(host, Constants.NOTIFICATION_CHANNEL_ID)
                : new android.app.Notification.Builder(host);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(displayMessage)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(displayMessage))
                .setContentIntent(pendingIntent)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setDefaults(android.app.Notification.DEFAULT_ALL)
                .setCategory(category.equals("messages") ? android.app.Notification.CATEGORY_MESSAGE : android.app.Notification.CATEGORY_STATUS)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) host.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                // One slot per chat, so messages replace instead of stacking.
                int id = ("campustrade:" + title).hashCode() & 0x7fffffff;
                manager.notify(id, builder.build());
            } catch (SecurityException e) {
                android.util.Log.w("CampusTrade", "notification blocked: " + e.getMessage());
            }
        }
    }
}