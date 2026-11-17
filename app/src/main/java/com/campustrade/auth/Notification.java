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
        addNotification(email, title, body, "general", "", "");
    }

    void addNotification(String email, String title, String body, String category, String peer) {
        addNotification(email, title, body, category, peer, "");
    }

    // Each student has a list; keep the newest 100. orderId lets an order alert
    // open the exact order instead of merely the Payments tab.
    void addNotification(String email, String title, String body, String category, String peer, String orderId) {
        SharedPreferences prefs = host.getSharedPreferences(host.PREFS + "_alerts", Context.MODE_PRIVATE);
        try {
            JSONArray data = new JSONArray(prefs.getString(email, "[]"));
            data.put(new JSONObject().put("title", title).put("body", body).put("time", System.currentTimeMillis())
                    .put("category", category).put("read", false).put("peer", peer == null ? "" : peer)
                    .put("orderId", orderId == null ? "" : orderId));
            while (data.length() > 100) data.remove(0);
            prefs.edit().putString(email, data.toString()).apply();
        } catch (org.json.JSONException e) { }
    }

    void sendPush(String recipientEmail, String title, String body) {
        sendPush(recipientEmail, title, body, "", "", "");
    }

    void sendPush(String recipientEmail, String title, String body, String category, String peer) {
        sendPush(recipientEmail, title, body, category, peer, "");
    }

    // Fetch a fresh Firebase token before sending a push.
    void sendPush(String recipientEmail, String title, String body, String category, String peer, String orderId) {
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
                    payload.put("orderId", orderId == null ? "" : orderId);

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

    public boolean isOrderCategory(String category) {
        return "payment".equals(category) || "order".equals(category) || "orders".equals(category);
    }

    public boolean isChatCategory(String category) {
        return "messages".equals(category) || "chat".equals(category) || "chats".equals(category);
    }

    public String notificationGroupFor(String category) {
        if (isOrderCategory(category)) return "Orders";
        if (isChatCategory(category)) return "Chats";
        return "Updates";
    }

    // Old alerts have no category, so guess one from the title.
    public String alertCategory(JSONObject alert) {
        String category = alert.optString("category");
        if (!category.isEmpty() && !category.equals("general")) return category;
        String title = alert.optString("title").toLowerCase(Locale.ROOT);
        if (title.contains("order") || title.contains("payment") || title.contains("receipt")
                || title.contains("collection") || title.contains("payout") || title.contains("return")) return "payment";
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

    private int unreadIn(JSONArray data, String filter) {
        int count = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONObject alert = data.optJSONObject(i);
            if (alert == null || alert.optBoolean("read", false)) continue;
            String group = notificationGroupFor(alertCategory(alert));
            if (filter.equals("All") || filter.equals(group)) count++;
        }
        return count;
    }

    private boolean matchesFilter(JSONObject alert, String filter) {
        if (filter.equals("All")) return true;
        return notificationGroupFor(alertCategory(alert)).equals(filter);
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

        // Orders and chats are separated so an order decision never gets lost
        // inside the chat stream.
        LinearLayout tabs = host.ui.horizontal();
        tabs.setPadding(host.ui.dp(3), host.ui.dp(3), host.ui.dp(3), host.ui.dp(3));
        tabs.setBackground(host.ui.round(host.nightActive ? host.surface() : Color.rgb(242, 240, 235), 12));
        String selected = host.notificationFilter == null ? "All" : host.notificationFilter;
        int index = 0;
        for (String mode : new String[] {"All", "Orders", "Chats", "Updates"}) {
            int unread = unreadIn(data, mode);
            String label = unread > 0 ? mode + " (" + unread + ")" : mode;
            TextView tab = host.ui.filterPill(label, mode.equals(selected));
            tab.setOnClickListener(v -> {
                host.notificationFilter = mode;
                host.showAppScreen("Notifications");
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, host.ui.dp(46), 1);
            if (index++ > 0) params.leftMargin = host.ui.dp(4);
            tabs.addView(tab, params);
        }
        content.addView(tabs);
        TextView scope = host.ui.bodyText(
                selected.equals("Orders") ? "Purchase requests, collection checks and returns."
                        : selected.equals("Chats") ? "Messages from your classmates."
                        : selected.equals("Updates") ? "Campus, listings, deadlines and groups."
                        : "Orders, chats and campus updates in one place.",
                host.muted, 11);
        scope.setPadding(host.ui.dp(4), host.ui.dp(10), host.ui.dp(4), host.ui.dp(4));
        content.addView(scope);

        LinearLayout history = new LinearLayout(host);
        history.setOrientation(LinearLayout.VERTICAL);
        String lastGroup = "";
        for (int i = data.length() - 1; i >= 0; i--) {
            JSONObject alert = data.optJSONObject(i);
            if (alert == null || !matchesFilter(alert, selected)) continue;
            String category = alertCategory(alert);
            boolean unread = !alert.optBoolean("read", false);
            String group = notificationGroup(alert.optLong("time"));
            if (!group.equals(lastGroup)) {
                TextView heading = host.ui.tinyCaps(group.toUpperCase(Locale.ROOT), host.muted);
                heading.setPadding(host.ui.dp(4), host.ui.dp(lastGroup.isEmpty() ? 6 : 18), 0, host.ui.dp(6));
                history.addView(heading);
                lastGroup = group;
            }
            String icon = isOrderCategory(category) ? "payment" : category.equals("deadline") ? "calendar"
                    : isChatCategory(category) ? "chat" : category.equals("groups") ? "team" : "bag";
            int accent = category.equals("deadline") ? Color.rgb(217, 139, 0)
                    : category.equals("groups") ? host.blue
                    : isOrderCategory(category) || category.equals("marketplace") ? host.green : host.blue;
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
            // Every alert opens the most useful screen. Order alerts carry an
            // order id so they open that order, not just the Payments list.
            row.setOnClickListener(v -> openAlert(data, alert, category));
            host.ui.attachPressAnimation(row);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = host.ui.dp(6);
            history.addView(row, rowParams);
        }
        if (history.getChildCount() == 0) {
            history.addView(host.ui.emptyState("bell",
                    selected.equals("Orders") ? "No order notifications"
                            : selected.equals("Chats") ? "No chat notifications"
                            : selected.equals("Updates") ? "No campus updates" : "No notifications yet",
                    selected.equals("Orders") ? "Purchase requests, collection checks and returns will appear here."
                            : selected.equals("Chats") ? "Messages from classmates will appear here."
                            : "Order updates, messages and campus alerts will appear here."));
        }
        content.addView(history);

        Button openSettings = host.ui.secondaryButton("Open notification settings");
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        settingsParams.topMargin = host.ui.dp(14);
        openSettings.setOnClickListener(v -> host.showAppScreen("Settings"));
        content.addView(openSettings, settingsParams);
    }

    private void openAlert(JSONArray data, JSONObject alert, String category) {
        try { alert.put("read", true); } catch (org.json.JSONException ignored) { }
        saveNotificationItems(data);
        String peer = alert.optString("peer");
        String orderId = alert.optString("orderId");
        if (isChatCategory(category) && !peer.isEmpty() && !peer.equals(host.currentStudentEmail)) {
            host.notificationFilter = "Chats";
            host.showAppScreen("Notifications");
            host.inbox.reloadMessages();
            host.inbox.showConversation(peer);
        } else if (isOrderCategory(category)) {
            host.notificationFilter = "Orders";
            MainActivity.TransactionRecord target = host.order.orderById(orderId);
            if (target != null) {
                host.buyingOrders = target.isBuyer(host.currentStudentEmail);
            }
            host.order.showTransactionHistory();
            if (target != null) host.order.showOrderDetails(target);
        } else if (category.equals("marketplace")) {
            host.notificationFilter = "Updates";
            host.showAppScreen("Market");
        } else if (category.equals("deadline")) {
            host.notificationFilter = "Updates";
            host.showAppScreen("Academics");
        } else if (category.equals("groups")) {
            host.notificationFilter = "Updates";
            host.showAppScreen("Groups");
        } else {
            // Standalone campus alerts have no order or chat destination, so
            // show their own detail instead of dropping the student on a list.
            host.showAppScreen("Notifications");
            host.ui.showSimpleMessage(alert.optString("title"), alert.optString("body"));
        }
    }

    // Android 8+ needs a channel; making it again is safe.
    public void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) host.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel updates = new NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "CampusTrade updates",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        updates.setDescription("Campus announcements, listings, deadlines and groups.");
        updates.enableVibration(true);
        updates.setShowBadge(true);
        manager.createNotificationChannel(updates);

        NotificationChannel orders = new NotificationChannel(
                Constants.ORDER_CHANNEL_ID,
                "CampusTrade orders",
                NotificationManager.IMPORTANCE_HIGH
        );
        orders.setDescription("Purchase requests, collection checks, returns and invoices.");
        orders.enableVibration(true);
        orders.setShowBadge(true);
        manager.createNotificationChannel(orders);

        NotificationChannel chats = new NotificationChannel(
                Constants.CHAT_CHANNEL_ID,
                "CampusTrade chats",
                NotificationManager.IMPORTANCE_HIGH
        );
        chats.setDescription("Messages from buyers, sellers and classmates.");
        chats.enableVibration(true);
        chats.setShowBadge(true);
        manager.createNotificationChannel(chats);
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
        showCampusNotification(title, message, "general", "", "");
    }

    void showCampusNotification(String title, String message, String category) {
        showCampusNotification(title, message, category, "", "");
    }

    void showCampusNotification(String title, String message, String category, String peer) {
        showCampusNotification(title, message, category, peer, "");
    }

    // Save the alert first, then show the phone notification.
    void showCampusNotification(String title, String message, String category, String peer, String orderId) {
        addNotification(host.currentStudentEmail, title, message, category, peer, orderId);
        String preference = category.equals("deadline") ? "deadlines" : category;
        // Order alerts ignore quiet hours and category switches.
        boolean order = isOrderCategory(category);
        boolean allowed = canShowNotifications() && host.settingsPrefs().getBoolean("push", true)
                && (order || !host.profile.inQuietHours())
                && (order || host.settingsPrefs().getBoolean(preference, true));
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
        // Chats open that chat, orders open that order, everything else opens
        // the matching notification stream.
        if (isChatCategory(category)) {
            intent.putExtra("open_inbox", true);
            if (peer != null && !peer.isEmpty()) intent.putExtra("open_peer", peer);
        } else if (order) {
            intent.putExtra("open_orders", true);
            if (orderId != null && !orderId.isEmpty()) intent.putExtra("open_order", orderId);
        } else {
            intent.putExtra("open_notifications", true);
            intent.putExtra("open_notification_filter", notificationGroupFor(category));
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        int requestCode = (title + "|" + category + "|" + peer + "|" + orderId).hashCode() & 0x7fffffff;
        PendingIntent pendingIntent = PendingIntent.getActivity(host, requestCode, intent, flags);

        String channelId = isOrderCategory(category) ? Constants.ORDER_CHANNEL_ID
                : isChatCategory(category) ? Constants.CHAT_CHANNEL_ID : Constants.NOTIFICATION_CHANNEL_ID;
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(host, channelId)
                : new android.app.Notification.Builder(host);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(displayMessage)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(displayMessage))
                .setContentIntent(pendingIntent)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setDefaults(android.app.Notification.DEFAULT_ALL)
                .setCategory(isChatCategory(category) ? android.app.Notification.CATEGORY_MESSAGE : android.app.Notification.CATEGORY_STATUS)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) host.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                // One slot per chat, order or alert title.
                int id = ("campustrade:" + title + ":" + peer + ":" + orderId).hashCode() & 0x7fffffff;
                manager.notify(id, builder.build());
            } catch (SecurityException e) {
                android.util.Log.w("CampusTrade", "notification blocked: " + e.getMessage());
            }
        }
    }
}
