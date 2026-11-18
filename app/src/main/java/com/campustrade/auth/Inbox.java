package com.campustrade.auth;

import android.app.Dialog;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import com.campustrade.auth.data.ChatRepository;
import com.campustrade.auth.data.CloudSync;
import com.campustrade.auth.data.HelperBot;
import com.campustrade.auth.data.HelperBotAI;
import com.campustrade.auth.model.Message;
import android.text.TextUtils;

// Chats, groups and the helper.
public class Inbox {

    private final MainActivity host;
    private com.google.firebase.firestore.ListenerRegistration typingListener;
    private long lastTypingPing = 0;
    private boolean helperThinking = false;
    private android.os.Handler helperDotsHandler;
    private Runnable helperDotsRunnable;
    private TextView helperDotsView;
    private static final long ADMIN_CHAT_IDLE_MS = 60L * 60L * 1000L;

    public Inbox(MainActivity host) {
        this.host = host;
    }

    // Build Inbox and create the helper chat.
    public void buildInbox(LinearLayout content) {
        LinearLayout searchRow = host.ui.horizontal();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(host.ui.dp(12), 0, host.ui.dp(4), 0);
        searchRow.setBackground(host.ui.strokedRound(host.surface(), 8, host.border));
        searchRow.setMinimumHeight(host.ui.dp(50));
        searchRow.addView(host.new CampusAssetView(host, "search", host.muted), new LinearLayout.LayoutParams(host.ui.dp(20), host.ui.dp(20)));
        EditText search = host.ui.input("Search conversations", InputType.TYPE_CLASS_TEXT);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setTextSize(13);
        search.setText(host.inboxQuery);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        View filter = host.new CampusAssetView(host, "sliders", host.inboxUnreadOnly ? host.blue : host.muted);
        filter.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        filter.setContentDescription("Filter conversations");
        filter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        filter.setOnClickListener(v -> host.ui.dialog().setTitle("Show conversations")
                .setSingleChoiceItems(new String[] {"All conversations", "Unread only"}, host.inboxUnreadOnly ? 1 : 0,
                        (d, which) -> { host.inboxUnreadOnly = which == 1; d.dismiss(); host.showAppScreen("Inbox"); }).show());
        searchRow.addView(filter, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        content.addView(searchRow);
        LinearLayout tabs = host.ui.horizontal();
        tabs.setPadding(host.ui.dp(3), host.ui.dp(3), host.ui.dp(3), host.ui.dp(3));
        tabs.setBackground(host.ui.round(host.nightActive ? host.surface() : Color.rgb(242, 240, 235), 12));
        int tabIndex = 0;
        for (String mode : new String[] {"All", "Groups", "Marketplace"}) {
            TextView tab = host.ui.filterPill(mode, mode.equals(host.inboxFilter));
            tab.setSingleLine(true);
            tab.setTextSize(12);
            tab.setOnClickListener(v -> { host.inboxFilter = mode; host.showAppScreen("Inbox"); });
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, host.ui.dp(40), 1);
            if (tabIndex++ > 0) tabParams.leftMargin = host.ui.dp(4);
            tabs.addView(tab, tabParams);
        }
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        tabsParams.topMargin = host.ui.dp(12);
        content.addView(tabs, tabsParams);

        // Pin the helper as a small card instead of a chat row.
        LinearLayout help = host.ui.horizontal();
        help.setGravity(Gravity.CENTER_VERTICAL);
        help.setPadding(host.ui.dp(12), host.ui.dp(9), host.ui.dp(12), host.ui.dp(9));
        help.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        View helpIcon = host.new CampusAssetView(host, "chat", host.blue);
        helpIcon.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
        helpIcon.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), 10));
        help.addView(helpIcon, new LinearLayout.LayoutParams(host.ui.dp(36), host.ui.dp(36)));
        LinearLayout helpCopy = new LinearLayout(host);
        helpCopy.setOrientation(LinearLayout.VERTICAL);
        helpCopy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
        helpCopy.addView(host.ui.titleText("Need help?", host.text, 13));
        helpCopy.addView(host.ui.bodyText("Ask the CampusTrade helper about orders or pickups.", host.muted, 11));
        help.addView(helpCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView helpAction = host.ui.textButton("Open");
        helpAction.setTextSize(11);
        help.addView(helpAction, new LinearLayout.LayoutParams(host.ui.dp(58), host.ui.dp(40)));
        help.setOnClickListener(v -> showConversation(HelperBot.PEER));
        host.ui.attachPressAnimation(help);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helpParams.topMargin = host.ui.dp(10);
        content.addView(help, helpParams);
        if (unreadMessages() > 0) {
            int unread = unreadMessages();
            content.addView(host.ui.coloredNotice("pin", "New messages",
                    unread == 1 ? "1 unread message" : unread + " unread messages",
                    Color.rgb(255, 244, 218), Color.rgb(217, 139, 0), v -> host.notification.showNotificationHistory()));
        }
        LinearLayout heading = host.ui.horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, host.ui.dp(16), 0, host.ui.dp(4));
        heading.addView(host.ui.titleText("Recent conversations", host.text, 16), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView markRead = host.ui.linkText("Mark all read");
        markRead.setTextSize(11);
        // Mark only my received messages as read, then save.
        markRead.setOnClickListener(v -> {
            for (MainActivity.ChatMessage m : host.chatMessages) if (m.recipient.equals(host.currentStudentEmail)) m.read = true;
            host.messageCount = 0;
            saveMessages();
            Toast.makeText(host, "All messages marked as read.", Toast.LENGTH_SHORT).show();
            host.showAppScreen("Inbox");
        });
        heading.addView(markRead);
        content.addView(heading);
        LinearLayout conversations = new LinearLayout(host);
        conversations.setOrientation(LinearLayout.VERTICAL);
        content.addView(conversations);
        ensureHelperThread();
        renderConversations(conversations, host.inboxQuery);
        search.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                host.inboxQuery = s.toString(); renderConversations(conversations, host.inboxQuery);
            }
        });
        cloudPullInbox();
    }

    // Show one row per person with the latest message and unread count.
    public void renderConversations(LinearLayout container, String query) {
        container.removeAllViews();
        LinkedHashSet<String> peers = new LinkedHashSet<>();
        for (int i = host.chatMessages.size() - 1; i >= 0; i--) {
            MainActivity.ChatMessage m = host.chatMessages.get(i);
            if (m.sender.equals(host.currentStudentEmail)) peers.add(m.recipient);
            else if (m.recipient.equals(host.currentStudentEmail)) peers.add(m.sender);
        }
        peers.remove(HelperBot.PEER);
        for (String peer : peers) {
            if (host.safety.blockedEither(host.currentStudentEmail, peer)) continue;
            if (host.inboxFilter.equals("Groups")) continue;
            if (host.inboxFilter.equals("Marketplace") && !isMarketplaceChat(peer)) continue;
            MainActivity.ChatMessage latest = null;
            int unread = 0;
            for (MainActivity.ChatMessage m : host.chatMessages) {
                if (!inConversation(m, peer)) continue;
                latest = m;
                if (m.recipient.equals(host.currentStudentEmail) && !m.read) unread++;
            }
            String fullName = isAdminConversation(peer) ? "Admin" : host.studentName(peer);
            if (host.inboxUnreadOnly && unread == 0) continue;
            if (latest == null || !(peer + " " + fullName + " " + latest.body + " " + latest.senderName)
                    .toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(host.ui.dp(14), host.ui.dp(12), host.ui.dp(14), host.ui.dp(12));
            row.setBackground(host.ui.bottomRule(unread > 0 ? host.blue : host.line));
            int avatarColor = (peer.hashCode() & 1) == 0 ? host.blue : host.green;
            TextView avatar = host.ui.titleText(host.ui.initials(fullName), avatarColor, 14);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(host.ui.round(avatarColor == host.blue ? Color.rgb(225, 236, 255) : Color.rgb(229, 247, 239), 999));
            row.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(44)));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(12), 0, host.ui.dp(8), 0);
            TextView person = host.ui.titleText(fullName, host.text, 13);
            person.setSingleLine(true);
            person.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(person);
            TextView preview = host.ui.bodyText((latest.sender.equals(host.currentStudentEmail) ? "You: " : "")
                    + conversationPreview(latest.body), host.muted, 12);
            preview.setSingleLine(true);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            preview.setPadding(0, host.ui.dp(6), 0, host.ui.dp(6));
            copy.addView(preview);
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout status = new LinearLayout(host);
            status.setOrientation(LinearLayout.VERTICAL);
            status.setGravity(Gravity.END);
            String date = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(latest.time));
            String today = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
            status.addView(host.ui.bodyText(new SimpleDateFormat(date.equals(today) ? "HH:mm" : "dd MMM", Locale.getDefault())
                    .format(new Date(latest.time)), unread > 0 ? host.blue : host.muted, 10));
            if (unread > 0) {
                TextView badge = host.ui.bodyText(String.valueOf(unread), Color.WHITE, 11);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(host.ui.round(host.blue, 999));
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24));
                badgeParams.topMargin = host.ui.dp(6);
                status.addView(badge, badgeParams);
            }
            row.addView(status);
            row.setOnClickListener(v -> {
                host.chatReturnTab = "Inbox";
                showConversation(peer);
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = host.ui.dp(12);
            container.addView(row, rowParams);
        }
        if (!host.inboxFilter.equals("Marketplace")) host.community.addGroupConversationRows(container, query);
        if (container.getChildCount() == 0) {
            container.addView(host.ui.emptyState("chat", query.isEmpty() ? "No conversations yet" : "No matches",
                    host.inboxFilter.equals("Groups") ? "Join a study group to start a group chat."
                            : query.isEmpty() ? "Message a seller or classmate and the thread will appear here."
                            : "Try a different name or clear the search to see every conversation."));
        }
    }

    // Keep order IDs out of chat previews.
    public String conversationPreview(String body) {
        if (body == null) return "";
        String cleaned = body.replace("[Admin]", "").replace("[Chat closed]", "")
                .replace("[Chat reopened]", "")
                .replaceAll("\\bCT-[A-Z0-9]{8}\\b", "")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("^[\\s\\-–—:]+|[\\s\\-–—:]+$", "")
                .trim();
        return cleaned.isEmpty() ? "Order update" : cleaned;
    }

    // Sort both emails so both students use the same chat key.
    public String marketplaceChatKey(String peer) {
        return host.currentStudentEmail.compareTo(peer) < 0 ? host.currentStudentEmail + ":" + peer : peer + ":" + host.currentStudentEmail;
    }

    // Save the marketplace flag so the filter survives a restart.
    public boolean isMarketplaceChat(String peer) {
        if (peer == null || peer.isEmpty()) return false;
        if (com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_messages")
                .getBoolean("market:" + marketplaceChatKey(peer), false)) return true;
        // Order conversations belong to Marketplace on both devices, even if
        // the local flag was never stored on this phone.
        String peerEmail = peer.contains("@") ? peer : CloudSync.emailForUid(host, peer);
        if (peerEmail == null || peerEmail.isEmpty()) return false;
        for (MainActivity.TransactionRecord order : host.transactions) {
            boolean buyerMatch = order.buyerEmail.equalsIgnoreCase(host.currentStudentEmail)
                    && order.sellerEmail.equalsIgnoreCase(peerEmail);
            boolean sellerMatch = order.sellerEmail.equalsIgnoreCase(host.currentStudentEmail)
                    && order.buyerEmail.equalsIgnoreCase(peerEmail);
            if (buyerMatch || sellerMatch) return true;
        }
        return false;
    }

    public void markMarketplaceChat(String peer) {
        if (peer == null || peer.isEmpty()) return;
        com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_messages")
                .edit().putBoolean("market:" + marketplaceChatKey(peer), true).apply();
    }

    /** Admin replies use a reserved peer id and must always show as "Admin". */
    public boolean isAdminConversation(String peer) {
        if (peer == null || peer.isEmpty() || peer.equals(HelperBot.PEER)) return false;
        if (peer.equals(Constants.ADMIN_CHAT_EMAIL)) return true;
        if (host.adminOnlySession || host.adminAccess || host.adminAccount) return true;
        String email = CloudSync.emailForUid(host, peer);
        if (email != null && (email.equalsIgnoreCase(Constants.STRICT_ADMIN_EMAIL)
                || email.equalsIgnoreCase(Constants.ADMIN_CHAT_EMAIL))) return true;
        for (MainActivity.ChatMessage message : host.chatMessages) {
            if (!inConversation(message, peer)) continue;
            if ("Admin".equalsIgnoreCase(message.senderName)
                    || (message.body != null && message.body.startsWith("[Admin]"))) return true;
        }
        return false;
    }

    public boolean isClosureMessage(String body) {
        return body != null && body.contains("[Chat closed]");
    }

    public String closureReason(String body) {
        if (!isClosureMessage(body)) return "";
        int index = body.indexOf("[Chat closed]") + "[Chat closed]".length();
        return body.substring(index).trim();
    }

    public MainActivity.ChatMessage latestMessage(String peer) {
        MainActivity.ChatMessage latest = null;
        for (MainActivity.ChatMessage message : host.chatMessages) {
            if (!inConversation(message, peer)) continue;
            if (latest == null || message.time >= latest.time) latest = message;
        }
        return latest;
    }

    /** A resolved report or one quiet hour ends an admin conversation. */
    public boolean adminChatClosed(String peer) {
        if (peer == null || peer.isEmpty() || !isAdminConversation(peer)) return false;
        MainActivity.ChatMessage latest = latestMessage(peer);
        if (latest == null) return false;
        if (isClosureMessage(latest.body)) return true;
        return System.currentTimeMillis() - latest.time > ADMIN_CHAT_IDLE_MS;
    }

    public String adminChatClosedReason(String peer) {
        MainActivity.ChatMessage latest = latestMessage(peer);
        if (latest == null) return "";
        String reason = closureReason(latest.body);
        if (!reason.isEmpty()) return reason;
        if (isAdminConversation(peer)
                && System.currentTimeMillis() - latest.time > ADMIN_CHAT_IDLE_MS) {
            return "Closed automatically after 1 hour of inactivity.";
        }
        return "";
    }

    public boolean isReopenMessage(String body) {
        return body != null && body.contains("[Chat reopened]");
    }

    /** An explicit closure marker ends the chat; admin idle time also ends it. */
    public boolean chatClosed(String peer) {
        if (peer == null || peer.isEmpty()) return false;
        MainActivity.ChatMessage latest = latestMessage(peer);
        if (latest != null && isClosureMessage(latest.body)) return true;
        return adminChatClosed(peer);
    }

    public String chatClosedReason(String peer) {
        MainActivity.ChatMessage latest = latestMessage(peer);
        if (latest == null) return "";
        String reason = closureReason(latest.body);
        if (!reason.isEmpty()) return reason;
        if (isAdminConversation(peer)
                && System.currentTimeMillis() - latest.time > ADMIN_CHAT_IDLE_MS) {
            return "Closed automatically after 1 hour of inactivity.";
        }
        return "";
    }

    /** Finish an order conversation without using the admin-report wording. */
    public void closeMarketplaceChat(String peer, String reason) {
        if (peer == null || peer.isEmpty() || peer.equals(HelperBot.PEER)) return;
        MainActivity.ChatMessage latest = latestMessage(peer);
        if (latest != null && isClosureMessage(latest.body)) return;
        String detail = reason == null ? "" : reason.trim();
        if (detail.length() > 300) detail = detail.substring(0, 300).trim();
        String body = "[Chat closed]"
                + (detail.isEmpty() ? " This order conversation has ended." : " " + detail);
        host.chatMessages.add(new MainActivity.ChatMessage(host.currentStudentEmail, peer,
                host.currentStudentName, body, System.currentTimeMillis(), false));
        saveMessages();
        if (peer.equals(host.openChatPeer) && host.openChatContainer != null) {
            renderChat(host.openChatContainer, peer);
            refreshOpenChatComposer();
        }
        cloudSendToPeer(peer, body);
    }

    public void closeMarketplaceChatForEmail(String email, String reason) {
        final String clean = host.normalizeEmail(email);
        if (clean.isEmpty()) return;
        CloudSync.peerUid(host, clean, new CloudSync.StrCallback() {
            @Override public void onOk(String peerUid) {
                closeMarketplaceChat(peerUid, reason);
            }
            @Override public void onBad(String message) {
                closeMarketplaceChat(clean, reason);
            }
        });
    }

    public void reopenMarketplaceChat(String peer, String note) {
        if (peer == null || peer.isEmpty() || peer.equals(HelperBot.PEER)) return;
        String detail = note == null ? "" : note.trim();
        String body = "[Chat reopened]" + (detail.isEmpty() ? "" : " " + detail);
        host.chatMessages.add(new MainActivity.ChatMessage(host.currentStudentEmail, peer,
                host.currentStudentName, body, System.currentTimeMillis(), false));
        saveMessages();
        if (peer.equals(host.openChatPeer) && host.openChatContainer != null) {
            renderChat(host.openChatContainer, peer);
            refreshOpenChatComposer();
        }
        cloudSendToPeer(peer, body);
    }

    /** Open an order chat under the Marketplace filter. */
    public void openOrderChat(String emailOrUid, boolean allowReopen) {
        final String original = emailOrUid == null ? "" : emailOrUid.trim();
        if (original.isEmpty()) return;
        host.inboxFilter = "Marketplace";
        host.inboxUnreadOnly = false;
        host.chatReturnTab = "Inbox";
        markMarketplaceChat(original);
        CloudSync.peerUid(host, original, new CloudSync.StrCallback() {
            @Override public void onOk(String peerUid) {
                rekeyPeer(original, peerUid);
                markMarketplaceChat(peerUid);
                if (allowReopen && chatClosed(peerUid)) {
                    reopenMarketplaceChat(peerUid, "You can continue this order conversation.");
                }
                showConversation(peerUid);
            }
            @Override public void onBad(String message) {
                if (allowReopen && chatClosed(original)) {
                    reopenMarketplaceChat(original, "You can continue this order conversation.");
                }
                showConversation(original);
            }
        });
    }

    /** Close a report conversation, leaving a visible marker for both sides. */
    public void closeAdminChat(String peer, String reason) {
        if (peer == null || peer.isEmpty() || peer.equals(HelperBot.PEER)) return;
        MainActivity.ChatMessage latest = latestMessage(peer);
        if (latest != null && isClosureMessage(latest.body)) return;
        String detail = reason == null ? "" : reason.trim();
        if (detail.length() > 300) detail = detail.substring(0, 300).trim();
        String body = "[Admin] [Chat closed]"
                + (detail.isEmpty() ? " This conversation has been closed." : " " + detail)
                + " Email " + Constants.ADMIN_CHAT_EMAIL + " to open a new report conversation.";
        host.chatMessages.add(new MainActivity.ChatMessage(host.currentStudentEmail, peer,
                host.currentStudentName, body, System.currentTimeMillis(), false));
        saveMessages();
        if (peer.equals(host.openChatPeer) && host.openChatContainer != null) {
            renderChat(host.openChatContainer, peer);
            refreshOpenChatComposer();
        }
        cloudSendToPeer(peer, body);
    }

    /** Resolve an email to its Firebase ID first so one student keeps one thread. */
    public void closeAdminChatForEmail(String email, String reason) {
        final String clean = host.normalizeEmail(email);
        if (clean.isEmpty()) return;
        CloudSync.peerUid(host, clean, new CloudSync.StrCallback() {
            @Override public void onOk(String peerUid) {
                closeAdminChat(peerUid, reason);
            }
            @Override public void onBad(String message) {
                closeAdminChat(clean, reason);
            }
        });
    }

    /** Turn an over-age admin thread into a closed thread before showing it. */
    public void expireAdminChatIfNeeded(String peer) {
        if (peer == null || peer.isEmpty() || !isAdminConversation(peer)) return;
        MainActivity.ChatMessage latest = latestMessage(peer);
        if (latest == null || isClosureMessage(latest.body)) return;
        if (System.currentTimeMillis() - latest.time > ADMIN_CHAT_IDLE_MS) {
            closeAdminChat(peer, "Closed automatically after 1 hour of inactivity.");
        }
    }

    public void refreshOpenChatComposer() {
        if (host.openChatComposer == null || host.openChatClosedNotice == null) return;
        String peer = host.openChatPeer;
        boolean closed = chatClosed(peer);
        host.openChatComposer.setVisibility(closed ? View.GONE : View.VISIBLE);
        host.openChatClosedNotice.setVisibility(closed ? View.VISIBLE : View.GONE);
        if (closed) {
            String reason = chatClosedReason(peer);
            boolean admin = isAdminConversation(peer);
            boolean moderator = host.adminOnlySession || host.adminAccess || host.adminAccount;
            host.openChatClosedNotice.setText("Chat closed"
                    + (reason.isEmpty() ? "" : "\n" + reason)
                    + (admin
                    ? (moderator
                    ? "\nTap here to reopen this conversation."
                    : "\nEmail " + Constants.ADMIN_CHAT_EMAIL + " to open a new report conversation.")
                    : "\nThis order conversation has ended."));
        }
    }

    /** A moderator can reopen a closed thread for an active report. */
    public void reopenAdminChat(String peer) {
        if (peer == null || peer.isEmpty() || peer.equals(HelperBot.PEER)) return;
        String body = "[Admin] Chat reopened by the campus moderator.";
        host.chatMessages.add(new MainActivity.ChatMessage(host.currentStudentEmail, peer,
                host.currentStudentName, body, System.currentTimeMillis(), false));
        saveMessages();
        if (peer.equals(host.openChatPeer) && host.openChatContainer != null) {
            renderChat(host.openChatContainer, peer);
            refreshOpenChatComposer();
        }
        cloudSendToPeer(peer, body);
    }

    /** Open a report thread, reopening it if the one-hour window expired. */
    public void openReportChat(String emailOrUid) {
        host.chatReturnTab = "AdminChat";
        final String original = emailOrUid == null ? "" : emailOrUid.trim();
        if (original.isEmpty()) return;
        CloudSync.peerUid(host, original, new CloudSync.StrCallback() {
            @Override public void onOk(String peerUid) {
                rekeyPeer(original, peerUid);
                if (adminChatClosed(peerUid)) reopenAdminChat(peerUid);
                showConversation(peerUid);
            }
            @Override public void onBad(String message) {
                if (adminChatClosed(original)) reopenAdminChat(original);
                showConversation(original);
            }
        });
    }

    // Save the message here first; show a toast if the cloud send fails.
    /** Copy an outgoing message to the Firebase chat. */
    public void cloudSendToPeer(String peerEmail, String body) {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty() || peerEmail == null || peerEmail.isEmpty()) {
            return;
        }
        CloudSync.peerUid(host, peerEmail, new CloudSync.StrCallback() {
            @Override public void onOk(String peerUid) {
                rekeyPeer(peerEmail, peerUid);
                sendCloudMessage(uid, peerUid, body);
            }
            @Override public void onBad(String message) {
                Toast.makeText(host,
                        "Saved on this phone, but it hasn't sent yet: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Use the saved chat ID so offline messages can send later. */
    public void sendCloudMessage(final String uid, final String peerUid, final String body) {
        final String cached = cachedChatId(peerUid);
        if (!cached.isEmpty()) {
            new ChatRepository().send(cached, uid, body, new ChatRepository.DoneCallback() {
                @Override public void onOk() { }
                @Override public void onBad(String message) { cloudSendFailed(cached, peerUid, message); }
            });
            return;
        }
        CloudSync.directChat(uid, peerUid, new ChatRepository.IdCallback() {
            @Override public void onOk(String chatId) {
                cacheChatId(peerUid, chatId);
                new ChatRepository().send(chatId, uid, body, new ChatRepository.DoneCallback() {
                    @Override public void onOk() { }
                    @Override public void onBad(String message) { cloudSendFailed(chatId, peerUid, message); }
                });
            }
            @Override public void onBad(String message) {
                Toast.makeText(host,
                        "Saved on this phone, but it hasn't sent yet: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void cloudSendFailed(String chatId, String peerUid, String message) {
        // If the saved chat ID fails, drop it and look it up again.
        if (chatId.equals(cachedChatId(peerUid))) {
            com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_chats").edit().remove("chat:" + peerUid).apply();
        }
        Toast.makeText(host, "Saved on this phone, but it hasn't sent yet: "
                + (message == null ? "check your connection" : message), Toast.LENGTH_LONG).show();
    }

    public String cachedChatId(String peerUid) {
        if (peerUid == null || peerUid.isEmpty()) return "";
        return com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_chats").getString("chat:" + peerUid, "");
    }

    public void cacheChatId(String peerUid, String chatId) {
        if (peerUid == null || peerUid.isEmpty() || chatId == null || chatId.isEmpty()) return;
        com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_chats").edit().putString("chat:" + peerUid, chatId).apply();
    }

    // Replace email keys with student IDs so one person stays one chat.
    /** Merge a chat saved by email after we learn the student ID. */
    public void rekeyPeer(String from, String to) {
        if (from == null || to == null || from.isEmpty() || to.isEmpty() || from.equals(to)) return;
        boolean changed = false;
        for (int i = 0; i < host.chatMessages.size(); i++) {
            MainActivity.ChatMessage m = host.chatMessages.get(i);
            if (m.sender.equals(host.currentStudentEmail) && m.recipient.equals(from)) {
                host.chatMessages.set(i, new MainActivity.ChatMessage(m.sender, to, m.senderName, m.body,
                        m.time, m.read, m.remoteId, m.readAt));
                changed = true;
            } else if (m.recipient.equals(host.currentStudentEmail) && m.sender.equals(from)) {
                host.chatMessages.set(i, new MainActivity.ChatMessage(to, m.recipient, m.senderName, m.body,
                        m.time, m.read, m.remoteId, m.readAt));
                changed = true;
            }
        }
        // Keep the marketplace filter on the chat after the key changes.
        SharedPreferences prefs = com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_messages");
        if (prefs.getBoolean("market:" + marketplaceChatKey(from), false)) {
            prefs.edit().putBoolean("market:" + marketplaceChatKey(to), true).apply();
        }
        // If this chat is open, keep sending into the same screen.
        if (host.openChatPeer.equals(from) && host.openChatDialog != null && host.openChatDialog.isShowing()) {
            host.openChatPeer = to;
            if (host.openChatContainer != null) renderChat(host.openChatContainer, to);
            refreshOpenChatComposer();
        }
        if (changed) saveMessages();
    }

    /** Load this student's messages from Firebase. */
    public void cloudMergeConversation(String peerEmail, Runnable done) {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty()) {
            done.run();
            return;
        }
        CloudSync.peerUid(host, peerEmail, new CloudSync.StrCallback() {
            @Override public void onOk(String peerUid) {
                rekeyPeer(peerEmail, peerUid);
                String cached = cachedChatId(peerUid);
                if (!cached.isEmpty()) {
                    mergeConversation(uid, peerUid, cached, done);
                    return;
                }
                CloudSync.directChat(uid, peerUid, new ChatRepository.IdCallback() {
                    @Override public void onOk(String chatId) {
                        cacheChatId(peerUid, chatId);
                        mergeConversation(uid, peerUid, chatId, done);
                    }
                    @Override public void onBad(String message) {
                        done.run();
                    }
                });
            }
            @Override public void onBad(String message) {
                done.run();
            }
        });
    }

    public void mergeConversation(final String uid, final String peerUid, String chatId, final Runnable done) {
        new ChatRepository().messages(chatId, new ChatRepository.MessageListCallback() {
            @Override public void onOk(java.util.List<Message> items) {
                if (host.isDestroyed() || host.isFinishing()) {
                    done.run();
                    return;
                }
                // Use student IDs for remote messages so each person has one chat.
                mergeRemoteMessages(uid, peerUid, items, true);
                new ChatRepository().markRead(chatId, peerUid, new ChatRepository.DoneCallback() {
                    @Override public void onOk() { done.run(); }
                    @Override public void onBad(String message) { done.run(); }
                });
            }
            @Override public void onBad(String message) {
                done.run();
            }
        });
    }

    // Treat the same text sent within 60 seconds as one message.
    /** Add Firebase messages for one student to the local chat. */
    public boolean mergeRemoteMessages(String uid, String peer, java.util.List<Message> items, boolean markRead) {
        boolean changed = false;
        String peerEmail = CloudSync.emailForUid(host, peer);
        for (Message m : items) {
            if (m.getMessage() == null) continue;
            long remoteTime = m.getTimestamp() != null ? m.getTimestamp().toDate().getTime() : 0;
            long remoteRead = m.getReadAt() != null ? m.getReadAt().toDate().getTime() : 0;
            boolean mine = uid.equals(m.getSenderID());
            String expectedSender = mine ? host.currentStudentEmail : peer;
            MainActivity.ChatMessage duplicate = null;
            for (MainActivity.ChatMessage local : host.chatMessages) {
                if (!local.body.equals(m.getMessage())) continue;
                if (Math.abs(local.time - remoteTime) >= 60000) continue;
                // Only match the same sender.
                // The other student may send the same text.
                boolean sameSender = local.sender.equals(expectedSender)
                        || (!mine && !peerEmail.isEmpty() && local.sender.equals(peerEmail));
                if (sameSender && (inConversation(local, peer)
                        || (!peerEmail.isEmpty() && inConversation(local, peerEmail)))) {
                    duplicate = local;
                    break;
                }
            }
            if (duplicate != null) {
                boolean touched = false;
                if (!m.getDocId().isEmpty() && !duplicate.remoteId.equals(m.getDocId())) {
                    duplicate.remoteId = m.getDocId();
                    touched = true;
                }
                if (mine && remoteRead > duplicate.readAt) {
                    duplicate.readAt = remoteRead;
                    touched = true;
                }
                if (touched) changed = true;
                continue;
            }
            MainActivity.ChatMessage local = new MainActivity.ChatMessage(
                    mine ? host.currentStudentEmail : peer,
                    mine ? peer : host.currentStudentEmail,
                    mine ? host.currentStudentName : peer,
                    m.getMessage(),
                    m.getTimestamp() != null ? m.getTimestamp().toDate().getTime() : System.currentTimeMillis(),
                    markRead || mine,
                    m.getDocId(),
                    remoteRead);
            host.chatMessages.add(local);
            changed = true;
        }
        if (changed) saveMessages();
        return changed;
    }

    // A chat can list me as buyer or seller, so check both.
    /** Load the signed-in student's chats from Firebase. */
    public void cloudPullInbox() {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("chats").whereEqualTo("buyerID", uid).get().addOnSuccessListener(buySnap -> {
            db.collection("chats").whereEqualTo("sellerID", uid).get().addOnSuccessListener(sellSnap -> {
                if (host.isDestroyed() || host.isFinishing()) return;
                java.util.ArrayList<com.google.firebase.firestore.DocumentSnapshot> chats = new java.util.ArrayList<>();
                for (com.google.firebase.firestore.DocumentSnapshot d : buySnap.getDocuments()) chats.add(d);
                for (com.google.firebase.firestore.DocumentSnapshot d : sellSnap.getDocuments()) chats.add(d);
                pullChatThreads(uid, chats, 0, false);
            }).addOnFailureListener(e -> { });
        }).addOnFailureListener(e -> { });
    }

    /** Pull recent direct chats for the moderator's Student chat screen. */
    public void syncAdminChats(Runnable done) {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty()) {
            if (done != null) done.run();
            return;
        }
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("chats").whereEqualTo("buyerID", uid).limit(40).get()
                .addOnCompleteListener(buyTask -> db.collection("chats").whereEqualTo("sellerID", uid).limit(40)
                        .get().addOnCompleteListener(sellTask -> {
                            if (host.isDestroyed() || host.isFinishing()) {
                                if (done != null) done.run();
                                return;
                            }
                            java.util.LinkedHashMap<String, com.google.firebase.firestore.DocumentSnapshot> unique =
                                    new java.util.LinkedHashMap<>();
                            try {
                                if (buyTask.getResult() != null) {
                                    for (com.google.firebase.firestore.DocumentSnapshot doc
                                            : buyTask.getResult().getDocuments()) unique.put(doc.getId(), doc);
                                }
                            } catch (Exception ignored) { }
                            try {
                                if (sellTask.getResult() != null) {
                                    for (com.google.firebase.firestore.DocumentSnapshot doc
                                            : sellTask.getResult().getDocuments()) unique.put(doc.getId(), doc);
                                }
                            } catch (Exception ignored) { }
                            pullAdminChatThreads(uid, new java.util.ArrayList<>(unique.values()), 0, done);
                        }));
    }

    private void pullAdminChatThreads(String uid,
                                      java.util.List<com.google.firebase.firestore.DocumentSnapshot> chats,
                                      int index, Runnable done) {
        if (index >= chats.size() || index >= 30) {
            if (done != null) done.run();
            return;
        }
        com.google.firebase.firestore.DocumentSnapshot chat = chats.get(index);
        String buyerId = chat.getString("buyerID");
        String sellerId = chat.getString("sellerID");
        final String peer = uid.equals(buyerId) ? sellerId : buyerId;
        if (peer == null || peer.isEmpty()) {
            pullAdminChatThreads(uid, chats, index + 1, done);
            return;
        }
        cacheChatId(peer, chat.getId());
        CloudSync.peerUid(host, peer, new CloudSync.StrCallback() {
            @Override public void onOk(String value) { }
            @Override public void onBad(String message) { }
        });
        new ChatRepository().messages(chat.getId(), new ChatRepository.MessageListCallback() {
            @Override public void onOk(java.util.List<Message> items) {
                if (host.isDestroyed() || host.isFinishing()) {
                    if (done != null) done.run();
                    return;
                }
                mergeRemoteMessages(uid, peer, items, false);
                pullAdminChatThreads(uid, chats, index + 1, done);
            }
            @Override public void onBad(String message) {
                pullAdminChatThreads(uid, chats, index + 1, done);
            }
        });
    }

    // Load up to 12 chats so sync stays quick.
    /** Merge the loaded chats and redraw Inbox once if anything new arrived. */
    private void pullChatThreads(String uid, java.util.List<com.google.firebase.firestore.DocumentSnapshot> chats,
                                 int index, boolean changedSoFar) {
        if (index >= chats.size() || index >= 12) {
            if (changedSoFar && !host.isDestroyed() && !host.isFinishing() && host.currentTab.equals("Inbox")) host.showAppScreen("Inbox");
            return;
        }
        com.google.firebase.firestore.DocumentSnapshot chat = chats.get(index);
        String buyerId = chat.getString("buyerID");
        String sellerId = chat.getString("sellerID");
        final String peer = uid.equals(buyerId) ? sellerId : buyerId;
        if (peer == null || peer.isEmpty()) {
            pullChatThreads(uid, chats, index + 1, changedSoFar);
            return;
        }
        cacheChatId(peer, chat.getId());
        CloudSync.peerUid(host, peer, new CloudSync.StrCallback() {
            @Override public void onOk(String value) { }
            @Override public void onBad(String message) { android.util.Log.w("CampusTrade", message); }
        });
        new ChatRepository().messages(chat.getId(), new ChatRepository.MessageListCallback() {
            @Override public void onOk(java.util.List<Message> items) {
                if (host.isDestroyed() || host.isFinishing()) return;
                boolean changed = mergeRemoteMessages(uid, peer, items, false) || changedSoFar;
                pullChatThreads(uid, chats, index + 1, changed);
            }
            @Override public void onBad(String message) {
                pullChatThreads(uid, chats, index + 1, changedSoFar);
            }
        });
    }

    // Watch live chats; only recent messages on the first load count as alerts.
    public void listenToChat(final String chatId, final String peerUid) {
        if (peerUid == null || peerUid.isEmpty() || !host.listenedChatIds.add(chatId)) return;
        final String uid = CloudSync.uid(host);
        CloudSync.peerUid(host, peerUid, new CloudSync.StrCallback() {
            @Override public void onOk(String value) { }
            @Override public void onBad(String message) { android.util.Log.w("CampusTrade", message); }
        });
        final boolean[] initialSnapshot = {true};
        host.messageListeners.add(com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("chats").document(chatId).collection("messages").orderBy("timestamp")
                .addSnapshotListener((snap, error) -> {
                    if (host.isFinishing() || host.isDestroyed()) return;
                    if (error != null) { android.util.Log.w("CampusTrade", "message listener: " + error.getMessage()); return; }
                    if (snap == null) return;
                    boolean isInitial = initialSnapshot[0];
                    initialSnapshot[0] = false;
                    for (com.google.firebase.firestore.DocumentChange change : snap.getDocumentChanges()) {
                        if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.REMOVED) continue;
                        com.google.firebase.firestore.DocumentSnapshot doc = change.getDocument();
                        String sender = doc.getString("senderID");
                        String body = doc.getString("message");
                        if (sender == null || body == null) continue;
                        com.google.firebase.Timestamp stamp = doc.getTimestamp("timestamp");
                        boolean mine = sender.equals(uid);
                        // Only the first load has history; new live messages always alert.
                        long sentAt = stamp != null ? stamp.toDate().getTime() : 0;
                        boolean recent = stamp != null && sentAt >= System.currentTimeMillis() - 120000;
                        boolean notify = change.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED
                                && !mine && (!isInitial || recent);
                        boolean open = peerUid.equals(host.openChatPeer)
                                && host.openChatDialog != null && host.openChatDialog.isShowing();
                        java.util.List<Message> one = new java.util.ArrayList<>();
                        Message message = new Message(sender, body, stamp);
                        message.setDocId(doc.getId());
                        message.setReadAt(doc.getTimestamp("readAt"));
                        one.add(message);
                        boolean merged = mergeRemoteMessages(uid, peerUid, one, open);
                        if (open && !mine && change.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            new ChatRepository().markRead(chatId, peerUid, new ChatRepository.DoneCallback() {
                                @Override public void onOk() { }
                                @Override public void onBad(String message) { }
                            });
                        }
                        android.util.Log.d("CampusTrade", "msg chat=" + chatId + " mine=" + mine + " initial=" + isInitial
                                + " merged=" + merged + " open=" + open + " notify=" + (notify && !open));
                        if (open) {
                            if (merged && host.openChatContainer != null) {
                                renderChat(host.openChatContainer, peerUid);
                                refreshOpenChatComposer();
                                final ScrollView target = host.openChatScroll;
                                if (target != null) target.post(() -> target.fullScroll(View.FOCUS_DOWN));
                            }
                        } else if (notify) {
                            host.notification.showCampusNotification(host.studentName(peerUid), body, "messages", peerUid);
                            if (host.currentTab.equals("Inbox")) host.showAppScreen("Inbox");
                        }
                    }
                }));
    }

    /** Create the helper chat for the signed-in student. */
    public void ensureHelperThread() {
        for (MainActivity.ChatMessage m : host.chatMessages) if (inConversation(m, HelperBot.PEER)) return;
        host.chatMessages.add(new MainActivity.ChatMessage(HelperBot.PEER, host.currentStudentEmail, HelperBot.NAME,
                HelperBot.welcome(), System.currentTimeMillis(), true));
        saveMessages();
    }

    private void startHelperDotsAnimation(TextView dots) {
        stopHelperDotsAnimation();
        if (dots == null) return;
        helperDotsView = dots;
        helperDotsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final String[] frames = {".", "..", "...", ".."};
        final int[] frame = {0};
        helperDotsRunnable = new Runnable() {
            @Override public void run() {
                if (!helperThinking || helperDotsView != dots || dots.getParent() == null) return;
                frame[0] = (frame[0] + 1) % frames.length;
                dots.setText(frames[frame[0]]);
                float drift = (frame[0] - 1.5f) * host.ui.dp(2);
                dots.animate().translationX(drift).setDuration(260).start();
                if (helperDotsHandler != null) helperDotsHandler.postDelayed(this, 320);
            }
        };
        helperDotsRunnable.run();
    }

    private void stopHelperDotsAnimation() {
        if (helperDotsHandler != null) {
            helperDotsHandler.removeCallbacksAndMessages(null);
            helperDotsHandler = null;
        }
        if (helperDotsView != null) {
            helperDotsView.animate().cancel();
            helperDotsView.setTranslationX(0f);
        }
        helperDotsView = null;
        helperDotsRunnable = null;
    }

    // Use live AI answers but keep the CampusTrade Helper name.
    // If it fails, fall back to the offline keyword answers.
    void scheduleHelperReply(String question, final LinearLayout messages, final ScrollView scroll,
                             final Dialog dialog, final TextView typingStatus) {
        final long started = System.currentTimeMillis();
        helperThinking = true;
        if (typingStatus != null) typingStatus.setText("Virtual assistant");
        if (dialog.isShowing()) {
            renderChat(messages, HelperBot.PEER);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        HelperBotAI.ask(question, new HelperBotAI.Callback() {
            @Override public void onReply(String reply) {
                finishHelperReply(reply, started, messages, scroll, dialog, typingStatus);
            }
            @Override public void onFailure(String reason) {
                finishHelperReply(HelperBot.reply(question), started, messages, scroll, dialog, typingStatus);
            }
        });
    }

    private void finishHelperReply(String answer, long started, LinearLayout messages, ScrollView scroll,
                                    Dialog dialog, TextView typingStatus) {
        long delay = Math.max(0, 700 - (System.currentTimeMillis() - started));
        messages.postDelayed(() -> {
            if (host.isDestroyed() || host.isFinishing()) return;
            helperThinking = false;
            stopHelperDotsAnimation();
            if (typingStatus != null) typingStatus.setText("Virtual assistant");
            host.chatMessages.add(new MainActivity.ChatMessage(HelperBot.PEER, host.currentStudentEmail, HelperBot.NAME,
                    answer, System.currentTimeMillis(), true));
            saveMessages();
            if (dialog.isShowing()) {
                renderChat(messages, HelperBot.PEER);
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
        }, delay);
    }

    // Save here first, then notify and try the cloud copy.
    void localMessage(String peer, String message) {
        host.chatMessages.add(new MainActivity.ChatMessage(host.currentStudentEmail, peer, host.currentStudentName, message, System.currentTimeMillis(), false));
        saveMessages(); host.notification.addNotification(peer, "Message from " + host.currentStudentName, message, "messages", host.currentStudentEmail); host.notification.sendPush(peer, "Message from " + host.currentStudentName, message, "messages", host.currentUid.isEmpty() ? host.currentStudentEmail : host.currentUid);
        cloudSendToPeer(peer, message);
    }

    public void showMessageDialog() {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Start a conversation with another Eduvos student.", host.muted, 12));
        EditText recipientInput = host.ui.listingField(form, "Student email", "Student's @vossie.net email",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        Button open = host.ui.primaryButton("Open chat");
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        openParams.topMargin = host.ui.dp(10);
        form.addView(open, openParams);
        Dialog sheet = host.ui.showBottomSheet("New message", form);
        open.setOnClickListener(v -> {
            String peer = host.normalizeEmail(host.ui.text(recipientInput));
            if (!host.auth.isStudentEmail(peer)) {
                recipientInput.setError("Enter a student @vossie.net email");
                return;
            }
            if (peer.equals(host.currentStudentEmail)) {
                recipientInput.setError("Choose another student");
                return;
            }
            sheet.dismiss();
            host.inboxFilter = "All";
            host.inboxUnreadOnly = false;
            host.chatReturnTab = "Inbox";
            showConversation(peer);
        });
    }

    /** Let a student start a fresh live conversation with the campus moderator. */
    public void openAdminChat() {
        host.chatReturnTab = "Inbox";
        showConversation(Constants.ADMIN_CHAT_EMAIL);
    }

    public boolean inConversation(MainActivity.ChatMessage m, String peer) {
        return (m.sender.equals(host.currentStudentEmail) && m.recipient.equals(peer))
                || (m.recipient.equals(host.currentStudentEmail) && m.sender.equals(peer));
    }

    public int unreadMessages() {
        int count = 0;
        for (MainActivity.ChatMessage m : host.chatMessages) if (m.recipient.equals(host.currentStudentEmail) && !m.read) count++;
        return count;
    }

    // Mark the chat read and remember the open chat for live updates.
    void showConversation(String peer) {
        if (host.safety.blockedEither(host.currentStudentEmail, peer)) {
            Toast.makeText(host, "This chat is blocked. Unblock the student in Settings first.", Toast.LENGTH_LONG).show();
            return;
        }
        expireAdminChatIfNeeded(peer);
        for (MainActivity.ChatMessage m : host.chatMessages) if (inConversation(m, peer) && m.recipient.equals(host.currentStudentEmail)) m.read = true;
        saveMessages();
        Dialog dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout layout = new LinearLayout(host);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(host.page);
        LinearLayout header = host.ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(host.ui.dp(8), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        header.setBackgroundColor(host.page);
        TextView back = host.ui.textButton("Back");
        back.setOnClickListener(v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(48)));
        String conversationName = isAdminConversation(peer) ? "Admin"
                : peer.equals(HelperBot.PEER) ? HelperBot.NAME : host.studentName(peer);
        int avatarColor = isAdminConversation(peer) ? host.blue
                : peer.equals(HelperBot.PEER) ? host.blue : (peer.hashCode() & 1) == 0 ? host.blue : host.green;
        TextView avatar = host.ui.titleText(host.ui.initials(conversationName), avatarColor, 12);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(host.ui.round(avatarColor == host.blue ? Color.rgb(225, 236, 255) : Color.rgb(229, 247, 239), 999));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(host.ui.dp(38), host.ui.dp(38));
        avatarParams.leftMargin = host.ui.dp(4);
        header.addView(avatar, avatarParams);
        LinearLayout heading = new LinearLayout(host);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
        TextView title = host.ui.titleText(conversationName, host.text, 15);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(title);
        TextView typingStatus = host.ui.bodyText(isAdminConversation(peer) ? "Campus moderator"
                : peer.equals(HelperBot.PEER) ? "Virtual assistant" : "Verified student", host.muted, 10);
        typingStatus.setSingleLine(true);
        heading.addView(typingStatus);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton close = host.iconButton(android.R.drawable.ic_menu_close_clear_cancel, "Close conversation");
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        View headerRule = new View(host);
        headerRule.setBackgroundColor(host.line);
        layout.addView(header);
        layout.addView(headerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        ScrollView scroll = new ScrollView(host);
        LinearLayout messages = new LinearLayout(host);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(host.ui.dp(16), host.ui.dp(8), host.ui.dp(16), host.ui.dp(16));
        scroll.addView(messages);
        layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView closedNotice = host.ui.bodyText("", host.muted, 12);
        closedNotice.setGravity(Gravity.CENTER);
        closedNotice.setPadding(host.ui.dp(16), host.ui.dp(12), host.ui.dp(16), host.ui.dp(12));
        closedNotice.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        closedNotice.setVisibility(View.GONE);
        closedNotice.setOnClickListener(v -> {
            String active = host.openChatPeer.isEmpty() ? peer : host.openChatPeer;
            if (!isAdminConversation(active)) {
                Toast.makeText(host, "This order conversation has ended.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (host.adminOnlySession || host.adminAccess || host.adminAccount) {
                reopenAdminChat(active);
                return;
            }
            android.content.Intent mail = new android.content.Intent(android.content.Intent.ACTION_SENDTO,
                    android.net.Uri.parse("mailto:" + Constants.STRICT_ADMIN_EMAIL));
            mail.putExtra(android.content.Intent.EXTRA_SUBJECT, "CampusTrade report conversation");
            mail.putExtra(android.content.Intent.EXTRA_TEXT,
                    "Hello campus team,\n\nI'd like to open a new report conversation.\n\nStudent: "
                            + host.currentStudentEmail + "\n");
            try {
                host.startActivity(android.content.Intent.createChooser(mail, "Email campus admin"));
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(host, Constants.STRICT_ADMIN_EMAIL, Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams closedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closedParams.setMargins(host.ui.dp(16), host.ui.dp(4), host.ui.dp(16), host.ui.dp(10));
        layout.addView(closedNotice, closedParams);
        LinearLayout composer = host.ui.horizontal();
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(16));
        composer.setBackgroundColor(host.page);
        EditText message = host.ui.input("Message", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        message.setSingleLine(false);
        message.setMaxLines(4);
        message.setFilters(new InputFilter[] { new InputFilter.LengthFilter(2000) });
        message.setPadding(host.ui.dp(12), host.ui.dp(10), host.ui.dp(12), host.ui.dp(10));
        composer.addView(message, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        composer.addView(host.ui.space(host.ui.dp(8), 1));
        ImageButton send = host.iconButton(android.R.drawable.ic_menu_send, "Send message");
        composer.addView(send, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        send.setEnabled(false);
        styleSend(send, false);
        message.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                boolean valid = !s.toString().trim().isEmpty();
                send.setEnabled(valid);
                styleSend(send, valid);
                if (valid && !peer.equals(HelperBot.PEER)
                        && System.currentTimeMillis() - lastTypingPing > 3000) {
                    lastTypingPing = System.currentTimeMillis();
                    String active = host.openChatPeer.isEmpty() ? peer : host.openChatPeer;
                    pingTyping(active);
                }
            }
        });
        send.setOnClickListener(v -> {
            String body = host.ui.text(message).trim();
            if (body.isEmpty()) return;
            String active = dialog.isShowing() && !host.openChatPeer.isEmpty() ? host.openChatPeer : peer;
            if (chatClosed(active)) {
                Toast.makeText(host, isAdminConversation(active)
                                ? "This chat is closed. Email " + Constants.ADMIN_CHAT_EMAIL
                                + " to open a new report conversation."
                                : "This order conversation has ended.",
                        Toast.LENGTH_LONG).show();
                refreshOpenChatComposer();
                return;
            }
            boolean adminSender = host.adminAccount || host.adminAccess || host.adminOnlySession;
            if (adminSender && !active.equals(HelperBot.PEER)) body = "[Admin] " + body;
            if (!adminSender && !active.equals(HelperBot.PEER)) {
                if (host.safety.postingPausedDialog(host.currentStudentEmail)) return;
                CampusSafety.ContentScan scan = host.safety.reviewContent(host.currentStudentEmail,
                        host.currentUid, "chat", body);
                if (scan.flagged) {
                    host.safety.warnIfFlagged(scan, null);
                    return;
                }
            }
            host.chatMessages.add(new MainActivity.ChatMessage(host.currentStudentEmail, active, host.currentStudentName, body, System.currentTimeMillis(), false));
            saveMessages();
            message.setText("");
            renderChat(messages, active);
            refreshOpenChatComposer();
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            if (active.equals(HelperBot.PEER)) {
                scheduleHelperReply(body, messages, scroll, dialog, typingStatus);
                return;
            }
            cloudSendToPeer(active, body);
            host.notification.addNotification(active, "Message from " + host.currentStudentName, body, "messages", host.currentStudentEmail); host.notification.sendPush(active, "Message from " + host.currentStudentName, body, "messages", host.currentUid.isEmpty() ? host.currentStudentEmail : host.currentUid);
        });
        layout.addView(composer);
        dialog.setContentView(layout);
        dialog.setOnDismissListener(d -> {
            if (typingListener != null) {
                typingListener.remove();
                typingListener = null;
            }
            helperThinking = false;
            stopHelperDotsAnimation();
            String returnTab = host.chatReturnTab == null || host.chatReturnTab.isEmpty()
                    ? "Inbox" : host.chatReturnTab;
            host.openChatPeer = "";
            host.openChatContainer = null;
            host.openChatScroll = null;
            host.openChatDialog = null;
            host.openChatComposer = null;
            host.openChatClosedNotice = null;
            host.showAppScreen(returnTab);
        });
        dialog.show();
        host.openChatPeer = peer;
        host.openChatContainer = messages;
        host.openChatScroll = scroll;
        host.openChatDialog = dialog;
        host.openChatComposer = composer;
        host.openChatClosedNotice = closedNotice;
        refreshOpenChatComposer();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        renderChat(messages, peer);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        if (!peer.equals(HelperBot.PEER)) {
            cloudMergeConversation(peer, () -> {
                if (host.isDestroyed() || host.isFinishing()) return;
                String active = dialog.isShowing() && !host.openChatPeer.isEmpty() ? host.openChatPeer : peer;
                renderChat(messages, active);
                refreshOpenChatComposer();
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                listenForTyping(active, typingStatus, dialog);
            });
        }
    }

    void styleSend(ImageButton send, boolean active) {
        send.setAlpha(1f);
        send.setBackground(host.ui.round(active ? host.primary
                : host.nightActive ? Color.rgb(54, 54, 60) : Color.rgb(232, 230, 225), 10));
        send.setImageTintList(android.content.res.ColorStateList.valueOf(active ? Color.WHITE : host.muted));
    }

    /** Watch for the other student's typing hint. */
    private void listenForTyping(String peer, TextView status, Dialog dialog) {
        if (typingListener != null) {
            typingListener.remove();
            typingListener = null;
        }
        CloudSync.peerUid(host, peer, new CloudSync.StrCallback() {
            @Override public void onOk(String peerUid) {
                String chatId = cachedChatId(peerUid);
                if (chatId.isEmpty() || host.isDestroyed() || host.isFinishing()) return;
                typingListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("chats").document(chatId)
                        .addSnapshotListener((snap, error) -> {
                            if (error != null || snap == null || !dialog.isShowing()) return;
                            String typingUid = snap.getString("typingUid");
                            com.google.firebase.Timestamp typedAt = snap.getTimestamp("typingAt");
                            long stamp = typedAt == null ? 0 : typedAt.toDate().getTime();
                            boolean typing = peerUid.equals(typingUid) && stamp > 0
                                    && System.currentTimeMillis() - stamp < 6000;
                            status.setText(typing ? "typing..." : "");
                        });
            }
            @Override public void onBad(String message) { }
        });
    }

    /** Send a typing hint at most every few seconds. */
    private void pingTyping(String peer) {
        CloudSync.peerUid(host, peer, new CloudSync.StrCallback() {
            @Override public void onOk(String peerUid) {
                String chatId = cachedChatId(peerUid);
                if (chatId.isEmpty()) return;
                new ChatRepository().typing(chatId, CloudSync.uid(host));
            }
            @Override public void onBad(String message) { }
        });
    }

    public void renderChat(LinearLayout container, String peer) {
        container.removeAllViews();
        int bubbleMax = (int) (host.getResources().getDisplayMetrics().widthPixels * 0.70f);

        if (peer.equals(HelperBot.PEER)) {
            LinearLayout helper = host.ui.card(host.page, 0, host.ui.dp(14));
            helper.setBackground(host.ui.strokedRound(host.nightActive ? Color.rgb(48, 38, 36) : Color.rgb(255, 246, 242),
                    14, host.nightActive ? host.blue : Color.rgb(198, 216, 242)));
            LinearLayout top = host.ui.horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            View bot = host.new CampusAssetView(host, "chat", host.blue);
            bot.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
            bot.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(226, 237, 255), 10));
            top.addView(bot, new LinearLayout.LayoutParams(host.ui.dp(34), host.ui.dp(34)));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(10), 0, 0, 0);
            copy.addView(host.ui.titleText("Need help?", host.text, 14));
            copy.addView(host.ui.bodyText("Ask about buying, reservations, payments or safety.", host.muted, 11));
            top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            helper.addView(top);
            LinearLayout.LayoutParams helperParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            helperParams.bottomMargin = host.ui.dp(14);
            container.addView(helper, helperParams);
        }

        int shown = 0;
        String lastDay = "";
        for (MainActivity.ChatMessage m : host.chatMessages) {
            if (!inConversation(m, peer)) continue;
            String day = chatDayKey(m.time);
            if (!day.equals(lastDay)) {
                TextView separator = host.ui.tinyCaps(chatDayLabel(m.time).toUpperCase(Locale.ROOT), host.muted);
                separator.setGravity(Gravity.CENTER);
                separator.setPadding(0, host.ui.dp(10), 0, host.ui.dp(8));
                container.addView(separator, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                lastDay = day;
            }
            if (isClosureMessage(m.body)) {
                container.addView(chatEventCard("Chat closed", closureReason(m.body)));
                shown++;
                continue;
            }
            if (isReopenMessage(m.body)) {
                String detail = m.body.substring(m.body.indexOf("[Chat reopened]")
                        + "[Chat reopened]".length()).trim();
                container.addView(chatEventCard("Chat reopened", detail));
                shown++;
                continue;
            }
            boolean mine = m.sender.equals(host.currentStudentEmail);
            LinearLayout line = host.ui.horizontal();
            line.setGravity(mine ? Gravity.END : Gravity.START);
            line.setPadding(mine ? host.ui.dp(38) : 0, 0, mine ? 0 : host.ui.dp(38), 0);
            LinearLayout bubble = new LinearLayout(host);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(host.ui.dp(13), host.ui.dp(9), host.ui.dp(13), host.ui.dp(8));
            bubble.setBackground(mine ? host.ui.round(host.blue, 14)
                    : host.ui.strokedRound(host.surface(), 14, host.line));
            String shownBody = m.body.startsWith("[Admin] ")
                    ? m.body.substring("[Admin] ".length()) : m.body;
            TextView body = host.ui.bodyText(shownBody, mine ? Color.WHITE : host.text, 14);
            body.setMaxWidth(bubbleMax - host.ui.dp(26));
            bubble.addView(body);
            String stamp = host.messageTime(m.time);
            if (mine && !peer.equals(HelperBot.PEER)) {
                stamp += m.readAt > 0 ? " / Seen" : " / Sent";
            }
            TextView time = host.ui.bodyText(stamp, mine ? Color.WHITE : host.muted, 10);
            time.setPadding(0, host.ui.dp(5), 0, 0);
            bubble.addView(time);
            line.addView(bubble, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lineParams.topMargin = host.ui.dp(5);
            container.addView(line, lineParams);
            shown++;
        }
        if (peer.equals(HelperBot.PEER) && helperThinking) {
            LinearLayout line = host.ui.horizontal();
            line.setGravity(Gravity.START);
            line.setPadding(0, host.ui.dp(5), host.ui.dp(38), 0);
            LinearLayout bubble = new LinearLayout(host);
            bubble.setPadding(host.ui.dp(15), host.ui.dp(7), host.ui.dp(15), host.ui.dp(7));
            bubble.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
            TextView dots = host.ui.bodyText("…", host.muted, 20);
            dots.setContentDescription("CampusTrade Helper is typing");
            dots.setGravity(Gravity.CENTER);
            dots.setMinWidth(host.ui.dp(32));
            dots.setTypeface(android.graphics.Typeface.MONOSPACE);
            bubble.addView(dots);
            line.addView(bubble, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            container.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            startHelperDotsAnimation(dots);
            shown++;
        }
        if (shown == 0) {
            container.addView(host.ui.emptyState("chat", "Start the conversation",
                    "Say hello and keep the details of your trade in one place."));
        }
    }

    private LinearLayout chatEventCard(String title, String detail) {
        LinearLayout card = host.ui.card(host.page, 0, host.ui.dp(12));
        card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        TextView titleView = host.ui.titleText(title, host.text, 12);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);
        if (detail != null && !detail.isEmpty()) {
            TextView detailView = host.ui.bodyText(detail, host.muted, 11);
            detailView.setGravity(Gravity.CENTER);
            detailView.setPadding(0, host.ui.dp(4), 0, 0);
            card.addView(detailView);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = host.ui.dp(8);
        params.bottomMargin = host.ui.dp(8);
        card.setLayoutParams(params);
        return card;
    }

    String chatDayKey(long time) {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(time));
    }

    String chatDayLabel(long time) {
        String key = chatDayKey(time);
        if (key.equals(chatDayKey(System.currentTimeMillis()))) return "Today";
        if (key.equals(chatDayKey(System.currentTimeMillis() - 24L * 60 * 60 * 1000))) return "Yesterday";
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(time));
    }

    public void reloadMessages() {
        host.chatMessages.clear();
        loadMessages();
    }

    public void loadMessages() {
        try {
            JSONArray data = new JSONArray(com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_messages").getString("messages", "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject m = data.getJSONObject(i);
                host.chatMessages.add(new MainActivity.ChatMessage(m.getString("sender"), m.getString("recipient"),
                        m.optString("name"), m.getString("body"), m.getLong("time"), m.optBoolean("read"),
                        m.optString("remoteId", ""), m.optLong("readAt", 0)));
            }
        } catch (org.json.JSONException e) {
            Toast.makeText(host, "Some saved conversations couldn't be loaded.", Toast.LENGTH_LONG).show();
        }
    }

    public void saveMessages() {
        JSONArray data = new JSONArray();
        for (MainActivity.ChatMessage m : host.chatMessages) {
            JSONObject item = new JSONObject();
            try {
                item.put("sender", m.sender).put("recipient", m.recipient).put("name", m.senderName)
                        .put("body", m.body).put("time", m.time).put("read", m.read)
                        .put("remoteId", m.remoteId).put("readAt", m.readAt);
                data.put(item);
            } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        }
        com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_messages").edit().putString("messages", data.toString()).apply();
    }
}