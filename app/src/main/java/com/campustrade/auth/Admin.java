package com.campustrade.auth;

import android.content.Context;
import android.content.Intent;
import android.app.Dialog;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

import com.campustrade.auth.data.CloudSync;
import com.campustrade.auth.data.HelperBot;
import com.campustrade.auth.data.ListingRepository;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

// Admin tools for reports, accounts and listings.
public class Admin {

    private final MainActivity host;
    private boolean reportsLoading = false;
    private long reportsLoadedAt = 0;
    private boolean reportCloudWarningShown = false;
    private boolean adminChatsLoading = false;
    private long adminChatsLoadedAt = 0;
    private com.google.firebase.firestore.ListenerRegistration presenceListener;
    private TextView liveStudentsValue;
    private int liveStudents = 0;

    public Admin(MainActivity host) {
        this.host = host;
    }

    public static final class AdminAccess {
        // Admin needs a debug build, a signed-in student and the passcode.
        public static boolean allowed(boolean debugBuild, boolean signedIn, boolean unlocked) {
            return debugBuild && signedIn && unlocked;
        }
    }

    public boolean canUseAdmin() {
        // Admin tools belong only to the reserved campus moderator account.
        return host.adminOnlySession || (host.isSignedIn()
                && Constants.STRICT_ADMIN_EMAIL.equalsIgnoreCase(host.currentStudentEmail));
    }

    // Go back to the screen that opened Admin.
    public void exitAdminMode() {
        boolean fromAuth = host.adminOnlySession;
        stopLiveStudentListener();
        host.adminAccess = false;
        host.adminOnlySession = false;
        host.clearSensitiveVerification();
        if (fromAuth) host.auth.showAuthScreen();
        else host.showAppScreen("Profile");
    }

    // Clear both flags so the passcode is needed again.
    public void lockAdminMode() {
        boolean fromAuth = host.adminOnlySession;
        stopLiveStudentListener();
        host.adminAccess = false;
        host.adminOnlySession = false;
        host.clearSensitiveVerification();
        Toast.makeText(host, "Admin mode locked. You'll need the passcode again.", Toast.LENGTH_SHORT).show();
        if (fromAuth) host.auth.showAuthScreen();
        else host.showAppScreen("Settings");
    }

    // The passcode is checked on this phone. Release builds keep admin locked.
    public void attemptAdminLogin() {
        if (!host.isDebugBuild()) {
            host.adminPasscodeInput.setError("Admin access is restricted to authorised staff.");
            return;
        }
        if (MainActivity.ADMIN_PASSCODE.isEmpty() || !host.ui.text(host.adminPasscodeInput).trim().equals(MainActivity.ADMIN_PASSCODE)) {
            host.adminPasscodeInput.setError("Wrong passcode. Admin access stays locked.");
            return;
        }
        host.adminPasscodeInput.setError(null);
        host.adminPasscodeInput.setText("");
        InputMethodManager keyboard =
                (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(host.adminPasscodeInput.getWindowToken(), 0);
        }
        host.adminAccess = true;
        host.adminOnlySession = true;
        Toast.makeText(host, "Admin mode unlocked. Welcome back.", Toast.LENGTH_SHORT).show();
        loadReports(() -> showAdminOnlyScreen("Admin"));
    }

    // The same passcode check, opened from Settings.
    public void promptAdminPasscode() {
        LinearLayout form = host.ui.workflowForm();
        EditText code = host.ui.listingField(form, "Admin passcode", "Passcode",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        host.ui.workflowDialog("Admin login", form, "Unlock", () -> {
            if (!host.isDebugBuild()) {
                throw new RuntimeException("Admin access is restricted to authorised staff.");
            }
            if (MainActivity.ADMIN_PASSCODE.isEmpty() || !host.ui.text(code).trim().equals(MainActivity.ADMIN_PASSCODE)) {
                throw new RuntimeException("Wrong passcode. Admin access stays locked.");
            }
            host.adminAccess = true;
            Toast.makeText(host, "Admin mode unlocked. Welcome back.", Toast.LENGTH_SHORT).show();
            if (host.isSignedIn()) {
                loadReports(() -> host.showAppScreen("Admin"));
            } else {
                host.adminOnlySession = true;
                loadReports(() -> showAdminOnlyScreen("Admin"));
            }
        });
    }

    public int openReportCount() {
        int count = 0;
        for (MainActivity.ModerationReport report : host.moderationReports) if (report.isOpen()) count++;
        return count;
    }

    // Show listings that are still visible.
    public void adminRemoveListing() {
        if (!canUseAdmin()) return;
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Remove a listing", "Search and confirm before deleting", page::dismiss));
        EditText search = host.ui.input("Search by item, seller or category", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        LinearLayout searchWrap = new LinearLayout(host);
        searchWrap.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(8));
        searchWrap.addView(search);
        root.addView(searchWrap);
        Button broadcast = host.ui.secondaryButton("Remove by title and seller email");
        LinearLayout.LayoutParams broadcastParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44));
        broadcastParams.leftMargin = host.ui.dp(20);
        broadcastParams.rightMargin = host.ui.dp(20);
        broadcastParams.bottomMargin = host.ui.dp(6);
        broadcast.setOnClickListener(v -> broadcastListingRemoval());
        root.addView(broadcast, broadcastParams);
        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView loading = host.ui.bodyText("Loading marketplace listings...", host.muted, 12);
        loading.setPadding(0, host.ui.dp(20), 0, 0);
        list.addView(loading);

        final ArrayList<com.google.firebase.firestore.DocumentSnapshot> listings = new ArrayList<>();
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            String query = host.ui.text(search).trim().toLowerCase(java.util.Locale.ROOT);
            int shown = 0;
            java.util.Set<String> cloudKeys = new java.util.HashSet<>();
            for (com.google.firebase.firestore.DocumentSnapshot doc : listings) {
                String t = doc.getString("listingN");
                String s = doc.getString("sellerEmail");
                if (t != null && s != null) cloudKeys.add((t + "|" + s).toLowerCase(java.util.Locale.ROOT));
            }
            for (com.google.firebase.firestore.DocumentSnapshot doc : new ArrayList<>(listings)) {
                String titleText = doc.getString("listingN");
                if (titleText == null || titleText.isEmpty()) titleText = "Listing";
                String category = doc.getString("category");
                if (category == null) category = "Other";
                String status = doc.getString("status");
                if (status == null) status = "AVAILABLE";
                String sellerEmail = doc.getString("sellerEmail");
                if (sellerEmail == null || sellerEmail.isEmpty()) {
                    sellerEmail = CloudSync.emailForUid(host, doc.getString("sellerID"));
                }
                String sellerName = host.studentName(sellerEmail);
                String haystack = titleText + " " + sellerName + " " + sellerEmail + " " + category + " " + status;
                if (!query.isEmpty() && !haystack.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                shown++;
                LinearLayout card = host.ui.card(host.surface(), 12, host.ui.dp(12));
                card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
                LinearLayout row = host.ui.horizontal();
                row.setGravity(Gravity.CENTER_VERTICAL);
                String image = doc.getString("imageURL");
                if (image != null && !image.isEmpty()) {
                    android.widget.ImageView thumb = new android.widget.ImageView(host);
                    thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    com.bumptech.glide.Glide.with(host).load(image).into(thumb);
                    row.addView(thumb, new LinearLayout.LayoutParams(host.ui.dp(52), host.ui.dp(52)));
                } else {
                    LinearLayout placeholder = new LinearLayout(host);
                    placeholder.setGravity(Gravity.CENTER);
                    placeholder.setBackground(host.ui.round(host.nightActive ? Color.rgb(48, 48, 54) : Color.rgb(232, 230, 224), 8));
                    placeholder.addView(host.new CampusAssetView(host, "bag", host.muted),
                            new LinearLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24)));
                    row.addView(placeholder, new LinearLayout.LayoutParams(host.ui.dp(52), host.ui.dp(52)));
                }
                LinearLayout copy = new LinearLayout(host);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
                TextView title = host.ui.titleText(titleText, host.text, 13);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(title);
                TextView seller = host.ui.bodyText(sellerName + " · " + category, host.muted, 11);
                seller.setSingleLine(true);
                seller.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(seller);
                copy.addView(host.ui.statusPill(status));
                row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView remove = host.ui.textButton("Remove");
                remove.setTextSize(11);
                row.addView(remove, new LinearLayout.LayoutParams(host.ui.dp(74), host.ui.dp(40)));
                card.addView(row);
                final String docId = doc.getId();
                final String reportedTitle = titleText;
                final String reportedSeller = sellerEmail;
                card.setOnClickListener(v -> confirmRemoveCloudListing(page, docId, reportedTitle,
                        reportedSeller, listings, render[0]));
                host.ui.attachPressAnimation(card);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = host.ui.dp(8);
                list.addView(card, cardParams);
            }
            // Show on-device copies too. Some demo listings were created
            // before cloud sync, so they have no Firestore document yet.
            for (MainActivity.Product product : new ArrayList<>(host.products)) {
                String key = (product.title + "|" + product.sellerEmail).toLowerCase(java.util.Locale.ROOT);
                if (cloudKeys.contains(key)) continue;
                String haystack = product.title + " " + host.studentName(product.sellerEmail) + " "
                        + product.sellerEmail + " " + product.category + " " + product.status;
                if (!query.isEmpty() && !haystack.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                shown++;
                LinearLayout card = host.ui.card(host.surface(), 12, host.ui.dp(12));
                card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
                LinearLayout row = host.ui.horizontal();
                row.setGravity(Gravity.CENTER_VERTICAL);
                View thumb = host.market.listingThumbnail(product, false);
                row.addView(thumb, new LinearLayout.LayoutParams(host.ui.dp(52), host.ui.dp(52)));
                LinearLayout copy = new LinearLayout(host);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
                TextView title = host.ui.titleText(product.title, host.text, 13);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(title);
                TextView seller = host.ui.bodyText(host.studentName(product.sellerEmail) + " · " + product.category,
                        host.muted, 11);
                seller.setSingleLine(true);
                seller.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(seller);
                copy.addView(host.ui.statusPill(product.hidden ? "Hidden (on device)" : product.status + " (on device)"));
                row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView remove = host.ui.textButton("Remove");
                remove.setTextSize(11);
                row.addView(remove, new LinearLayout.LayoutParams(host.ui.dp(74), host.ui.dp(40)));
                card.addView(row);
                card.setOnClickListener(v -> confirmRemoveLocalListing(page, product, render[0]));
                host.ui.attachPressAnimation(card);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = host.ui.dp(8);
                list.addView(card, cardParams);
            }
            if (shown == 0) {
                list.addView(host.ui.emptyState("bag", query.isEmpty() ? "No listings yet" : "No listings match",
                        query.isEmpty() ? "Marketplace listings will appear here when students post them."
                                : "Try a different item, seller or category."));
            }
        };
        search.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) { render[0].run(); }
        });
        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("listings")
                .limit(200).get()
                .addOnSuccessListener(snapshot -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    listings.clear();
                    listings.addAll(snapshot.getDocuments());
                    java.util.Collections.sort(listings, (a, b) -> {
                        com.google.firebase.Timestamp ta = a.getTimestamp("createdAt");
                        com.google.firebase.Timestamp tb = b.getTimestamp("createdAt");
                        long va = ta == null ? 0 : ta.toDate().getTime();
                        long vb = tb == null ? 0 : tb.toDate().getTime();
                        return Long.compare(vb, va);
                    });
                    render[0].run();
                })
                .addOnFailureListener(e -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    list.removeAllViews();
                    list.addView(host.ui.emptyState("bag", "Couldn't load marketplace listings",
                            "Check the moderator Firestore access and try again."));
                });
    }

    private void broadcastListingRemoval() {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Use this when a listing exists on a student's phone but is missing from the cloud list. It broadcasts a removal by exact title and seller email.",
                host.muted, 11));
        EditText title = host.ui.listingField(form, "Listing title", "Exact title", InputType.TYPE_CLASS_TEXT);
        EditText seller = host.ui.listingField(form, "Seller email", "student@vossie.net",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        Button remove = host.ui.primaryButton("Broadcast removal");
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        removeParams.topMargin = host.ui.dp(12);
        form.addView(remove, removeParams);
        Dialog sheet = host.ui.showBottomSheet("Broadcast listing removal", form);
        remove.setOnClickListener(v -> {
            String listingTitle = host.ui.text(title).trim();
            String sellerEmail = host.ui.text(seller).trim().toLowerCase(java.util.Locale.ROOT);
            if (listingTitle.length() < 3 || !sellerEmail.contains("@")) {
                host.ui.showSimpleMessage("Removal details", "Enter the exact title and seller email.");
                return;
            }
            promptRemovalReason(listingTitle, sellerEmail, reason -> {
                String tombstoneId = "local-" + Integer.toHexString(
                        (listingTitle + "|" + sellerEmail).toLowerCase(java.util.Locale.ROOT).hashCode());
                recordDeletedListing(tombstoneId, listingTitle, sellerEmail, reason);
                for (int i = host.products.size() - 1; i >= 0; i--) {
                    MainActivity.Product product = host.products.get(i);
                    if (product.title.equalsIgnoreCase(listingTitle)
                            && product.sellerEmail.equalsIgnoreCase(sellerEmail)) {
                        host.products.remove(i);
                    }
                }
                host.market.saveProducts();
                notifyListingRemoved(sellerEmail, listingTitle, reason);
                recordListingAudit(tombstoneId, listingTitle, sellerEmail, reason);
                sheet.dismiss();
                Toast.makeText(host, "Removal broadcast to the marketplace.", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmRemoveLocalListing(Dialog page, MainActivity.Product product, Runnable render) {
        host.ui.dialog().setTitle("Remove \"" + product.title + "\"?")
                .setMessage("This permanently deletes the on-device copy and broadcasts the removal to the marketplace.")
                .setNegativeButton("Keep listing", null)
                .setPositiveButton("Remove listing", (confirm, w) ->
                        promptRemovalReason(product.title, product.sellerEmail, reason -> {
                            host.products.remove(product);
                            host.market.saveProducts();
                            String remote = CloudSync.remoteId(host, product.id);
                            String tombstoneId = remote;
                            if (tombstoneId.isEmpty()) {
                                tombstoneId = "local-" + Integer.toHexString(
                                        (product.title + "|" + product.sellerEmail).toLowerCase(java.util.Locale.ROOT).hashCode());
                            }
                            recordDeletedListing(tombstoneId, product.title, product.sellerEmail, reason);
                            recordListingAudit(tombstoneId, product.title, product.sellerEmail, reason);
                            if (!remote.isEmpty()) {
                                new ListingRepository().delete(remote, new ListingRepository.DoneCallback() {
                                    @Override public void onOk() { }
                                    @Override public void onBad(String message) {
                                        Toast.makeText(host, "Marketplace removal broadcast; cloud delete failed: " + message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                            notifyListingRemoved(product.sellerEmail, product.title, reason);
                            Toast.makeText(host, "Listing removal broadcast to the marketplace.", Toast.LENGTH_SHORT).show();
                            render.run();
                        })).show();
    }

    private void confirmRemoveCloudListing(Dialog page, String docId, String title, String sellerEmail,
                                           ArrayList<com.google.firebase.firestore.DocumentSnapshot> listings,
                                           Runnable render) {
        host.ui.dialog().setTitle("Remove \"" + title + "\"?")
                .setMessage("This permanently deletes the listing by " + host.studentName(sellerEmail) + " for everyone.")
                .setNegativeButton("Keep listing", null)
                .setPositiveButton("Remove listing", (confirm, w) ->
                        promptRemovalReason(title, sellerEmail, reason -> {
                            String previousRemote = docId;
                            recordDeletedListing(docId, title, sellerEmail, reason);
                            recordListingAudit(docId, title, sellerEmail, reason);
                            for (int i = host.products.size() - 1; i >= 0; i--) {
                                MainActivity.Product product = host.products.get(i);
                                String remote = CloudSync.remoteId(host, product.id);
                                if (previousRemote.equals(remote) || previousRemote.equals(product.id)
                                        || ("remote-" + previousRemote).equals(product.id)) {
                                    host.products.remove(i);
                                }
                            }
                            host.market.saveProducts();
                            notifyListingRemoved(sellerEmail, title, reason);
                            new ListingRepository().delete(docId, new ListingRepository.DoneCallback() {
                                @Override public void onOk() {
                                    Toast.makeText(host, "Listing removed for everyone.", Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onBad(String message) {
                                    Toast.makeText(host, "Removed from the marketplace index; cloud delete failed: " + message,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                            for (int i = listings.size() - 1; i >= 0; i--) {
                                if (listings.get(i).getId().equals(docId)) listings.remove(i);
                            }
                            render.run();
                        })).show();
    }

    private interface ReasonCallback {
        void onReason(String reason);
    }

    /** No listing leaves the marketplace without a written moderator reason. */
    private void promptRemovalReason(String title, String sellerEmail, ReasonCallback callback) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("The reason is saved in the deleted-listing record and audit log, and sent to "
                + host.studentName(sellerEmail) + ".", host.muted, 11));
        EditText note = host.ui.listingField(form, "Removal reason", "Why is this listing being removed?",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setSingleLine(false);
        note.setMinLines(3);
        note.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
        android.app.AlertDialog dialog = host.ui.dialog()
                .setTitle("Remove \"" + title + "\"?")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove listing", null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String reason = host.ui.text(note).trim();
                    if (reason.length() < 8) {
                        note.setError("Add at least 8 characters explaining the removal");
                        return;
                    }
                    dialog.dismiss();
                    callback.onReason(reason);
                }));
        dialog.show();
    }

    private void recordDeletedListing(String docId, MainActivity.Product product, String reason) {
        if (product == null) return;
        recordDeletedListing(docId, product.title, product.sellerEmail, reason);
    }

    private void recordDeletedListing(String docId, String title, String sellerEmail, String reason) {
        if (docId == null || docId.isEmpty()) return;
        java.util.Map<String, Object> tombstone = new java.util.HashMap<>();
        tombstone.put("listingId", docId);
        tombstone.put("title", title == null ? "" : title);
        tombstone.put("sellerEmail", sellerEmail == null ? "" : sellerEmail);
        tombstone.put("reason", reason == null ? "" : reason);
        tombstone.put("removedBy", host.currentStudentEmail);
        tombstone.put("deletedAt", com.google.firebase.Timestamp.now());
        tombstone.put("expiresAt", new com.google.firebase.Timestamp(
                new java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)));
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("deletedListings").document(docId).set(tombstone)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade",
                        "deleted listing tombstone failed: " + e.getMessage()));
    }

    private void recordListingAudit(String listingId, String title, String sellerEmail, String reason) {
        java.util.Map<String, Object> action = new java.util.HashMap<>();
        action.put("adminId", host.currentUid);
        action.put("adminName", host.currentStudentName);
        action.put("action", "remove_listing");
        action.put("listingId", listingId == null ? "" : listingId);
        action.put("title", title == null ? "" : title);
        action.put("sellerEmail", sellerEmail == null ? "" : sellerEmail);
        action.put("reason", reason == null ? "" : reason);
        action.put("createdAt", com.google.firebase.Timestamp.now());
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("admin_actions").add(action)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade",
                        "listing audit action failed: " + e.getMessage()));
    }

    private void notifyListingRemoved(String sellerEmail, String title, String reason) {
        if (sellerEmail == null || sellerEmail.isEmpty()) return;
        String message = title + " was removed by a campus moderator. Reason: " + reason;
        host.notification.addNotification(sellerEmail, "Listing removed", message, "marketplace", "");
        host.notification.sendPush(sellerEmail, "Listing removed", message);
    }

    // Load student profiles, but skip the signed-in moderator.
    public void adminRemoveUser() {
        if (!canUseAdmin()) return;
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Remove a student", "Search and confirm before deleting", page::dismiss));
        EditText search = host.ui.input("Search by name, email or status", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        LinearLayout searchWrap = new LinearLayout(host);
        searchWrap.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(8));
        searchWrap.addView(search);
        root.addView(searchWrap);
        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView loading = host.ui.bodyText("Loading students...", host.muted, 12);
        loading.setPadding(0, host.ui.dp(20), 0, 0);
        list.addView(loading);
        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        final java.util.ArrayList<String> uids = new java.util.ArrayList<>();
        final java.util.ArrayList<String> names = new java.util.ArrayList<>();
        final java.util.ArrayList<String> emails = new java.util.ArrayList<>();
        final java.util.ArrayList<String> states = new java.util.ArrayList<>();
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            String query = host.ui.text(search).trim().toLowerCase(java.util.Locale.ROOT);
            int shown = 0;
            for (int i = 0; i < uids.size(); i++) {
                String haystack = names.get(i) + " " + emails.get(i) + " " + states.get(i);
                if (!query.isEmpty() && !haystack.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                shown++;
                final int index = i;
                LinearLayout card = host.ui.card(host.surface(), 12, host.ui.dp(12));
                card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
                LinearLayout row = host.ui.horizontal();
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView avatar = host.ui.titleText(host.ui.initials(names.get(i)), host.blue, 11);
                avatar.setGravity(Gravity.CENTER);
                avatar.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(225, 236, 255), 999));
                row.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
                LinearLayout copy = new LinearLayout(host);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
                LinearLayout nameRow = host.ui.horizontal();
                nameRow.setGravity(Gravity.CENTER_VERTICAL);
                nameRow.addView(host.ui.titleText(names.get(i), host.text, 13),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                nameRow.addView(host.ui.statusPill(accountStatusLabel(states.get(i))));
                copy.addView(nameRow);
                TextView email = host.ui.bodyText(emails.get(i), host.muted, 11);
                email.setSingleLine(true);
                email.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(email);
                row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView remove = host.ui.textButton("Remove");
                remove.setTextSize(11);
                row.addView(remove, new LinearLayout.LayoutParams(host.ui.dp(74), host.ui.dp(40)));
                card.addView(row);
                card.setOnClickListener(v -> confirmRemoveStudent(page, uids.get(index), names.get(index), emails.get(index)));
                host.ui.attachPressAnimation(card);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = host.ui.dp(8);
                list.addView(card, cardParams);
            }
            if (shown == 0) {
                list.addView(host.ui.emptyState("team", query.isEmpty() ? "No other students" : "No students match",
                        query.isEmpty() ? "There are no other student profiles to remove."
                                : "Try a different name, email or status."));
            }
        };
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").limit(50).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String email = doc.getString("stuEmail");
                        if (email == null || (host.isSignedIn() && email.equalsIgnoreCase(host.currentStudentEmail))) continue;
                        String name = doc.getString("stuName");
                        String status = doc.getString("accountStatus");
                        if (status == null || status.isEmpty()) status = "active";
                        uids.add(doc.getId());
                        names.add(name == null || name.isEmpty() ? "Student" : name);
                        emails.add(email == null ? "" : email);
                        states.add(status);
                    }
                    search.addTextChangedListener(new MainActivity.SimpleWatcher() {
                        @Override public void afterTextChanged(android.text.Editable s) { render[0].run(); }
                    });
                    render[0].run();
                })
                .addOnFailureListener(e -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    list.removeAllViews();
                    list.addView(host.ui.emptyState("team", "Couldn't load students",
                            "Check your connection and try again."));
                    Button retry = host.ui.secondaryButton("Try again");
                    retry.setOnClickListener(v -> { page.dismiss(); adminRemoveUser(); });
                    LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
                    retryParams.topMargin = host.ui.dp(8);
                    list.addView(retry, retryParams);
                });
    }

    // Also remove the public profile and listings.
    private void confirmRemoveStudent(Dialog page, final String uid, String name, String email) {
        host.ui.dialog().setTitle("Remove " + name + "?")
                .setMessage(email + "\n\nThis deletes the student profile and all of their listings. "
                        + "Their sign-in must be removed in the Firebase console for a full erasure.")
                .setNegativeButton("Keep student", null)
                .setPositiveButton("Remove student", (d, w) -> {
                    com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                    db.collection("users").document(uid).delete()
                            .addOnSuccessListener(v -> Toast.makeText(host, "Student profile removed.", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(host,
                                    "Profile didn't delete. Check your admin rights in Firebase.", Toast.LENGTH_LONG).show());
                    db.collection("publicProfiles").document(uid).delete();
                    db.collection("listings").whereEqualTo("sellerID", uid).get()
                            .addOnSuccessListener(snap -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                                    doc.getReference().delete();
                                }
                            });
                    if (!email.isEmpty()) {
                        for (int i = host.products.size() - 1; i >= 0; i--) {
                            if (host.products.get(i).sellerEmail.equalsIgnoreCase(email)) host.products.remove(i);
                        }
                        host.market.saveProducts();
                    }
                    page.dismiss();
                    host.showAppScreen("Admin");
                }).show();
    }

    // Actions depend on the report type and listing.
    public void showReportReview(MainActivity.ModerationReport report) {
        if (!canUseAdmin()) return;
        final boolean reviewReport = report.entityType().equals("review");
        final boolean userReport = report.entityType().equals("user");
        MainActivity.Product target = null;
        if (!reviewReport && !userReport) {
            for (MainActivity.Product product : host.products) if (product.id.equals(report.productId)) target = product;
        }
        final MainActivity.Product product = target;
        final String reportedEmail = report.reportedEmail();
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Report", report.reason, page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout hero = host.ui.card(host.surface(), 16, host.ui.dp(16));
        hero.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        LinearLayout top = host.ui.horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView typeChip = host.ui.chip(userReport ? "Student report" : reviewReport ? "Review report" : "Listing report",
                host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255),
                host.readableInk(host.blue));
        typeChip.setTextSize(10);
        top.addView(typeChip);
        top.addView(host.ui.space(1, 1), new LinearLayout.LayoutParams(0, 1, 1));
        top.addView(host.ui.statusPill(report.state));
        hero.addView(top);
        LinearLayout priorityRow = host.ui.horizontal();
        priorityRow.setPadding(0, host.ui.dp(8), 0, 0);
        priorityRow.addView(priorityChip(report.priority));
        hero.addView(priorityRow);
        TextView detail = host.ui.bodyText(report.detail, host.text, 14);
        detail.setPadding(0, host.ui.dp(10), 0, host.ui.dp(10));
        hero.addView(detail);
        if (!report.resolutionNote.isEmpty()) {
            TextView outcome = host.ui.bodyText("Resolution: " + report.resolutionNote, host.text, 12);
            outcome.setPadding(host.ui.dp(10), host.ui.dp(9), host.ui.dp(10), host.ui.dp(9));
            outcome.setBackground(host.ui.strokedRound(host.surface(), 10, host.line));
            LinearLayout.LayoutParams outcomeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            outcomeParams.topMargin = host.ui.dp(10);
            hero.addView(outcome, outcomeParams);
        }
        hero.addView(host.ui.bodyText("Reported by " + report.reporterName + " · " + host.notification.alertTime(report.createdAt),
                host.muted, 11));
        content.addView(hero);

        content.addView(host.ui.titleText(userReport ? "Reported student"
                : reviewReport ? "Reported review" : "Reported listing", host.text, 15));
        LinearLayout targetCard = host.ui.card(host.surface(), 14, host.ui.dp(14));
        targetCard.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
        LinearLayout targetTop = host.ui.horizontal();
        targetTop.setGravity(Gravity.CENTER_VERTICAL);
        targetTop.addView(host.ui.titleText(userReport && !reportedEmail.isEmpty()
                        ? host.studentName(reportedEmail) : report.productTitle, host.text, 14),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (product != null) targetTop.addView(host.ui.statusPill(product.hidden ? "Hidden" : product.status));
        targetCard.addView(targetTop);
        targetCard.addView(host.ui.bodyText(userReport
                ? (reportedEmail.isEmpty() ? "No student account is attached to this report."
                : reportedEmail + " · student account report")
                : reviewReport
                ? "A rating left on a seller profile. Removing it also resolves this report."
                : product == null ? "This listing is no longer in your local marketplace copy."
                : "Listed by " + host.studentName(product.sellerEmail) + " · " + product.category, host.muted, 11));
        if (userReport && !reportedEmail.isEmpty()) {
            Button message = host.ui.secondaryButton("Message reported student");
            message.setOnClickListener(v -> messageReportedStudent(reportedEmail,
                    "CampusTrade report about your account", page));
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
            messageParams.topMargin = host.ui.dp(10);
            targetCard.addView(message, messageParams);
        }
        if (product != null) {
            Button view = host.ui.secondaryButton("View listing");
            view.setOnClickListener(v -> { page.dismiss(); host.market.showProductDetails(product); });
            LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
            viewParams.topMargin = host.ui.dp(10);
            targetCard.addView(view, viewParams);
        }
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        targetParams.topMargin = host.ui.dp(10);
        content.addView(targetCard, targetParams);

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button actions = host.ui.primaryButton("Report actions");
        actions.setOnClickListener(v -> showReportActions(report, product, reviewReport, userReport, page));
        footer.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void showReportActions(MainActivity.ModerationReport report, MainActivity.Product product,
                                   boolean reviewReport, boolean userReport, Dialog page) {
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        if (product != null && !reviewReport && !userReport) {
            addReportAction(body, "bag", "View listing", "Open the reported marketplace item", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                page.dismiss();
                host.market.showProductDetails(product);
            });
            addReportAction(body, "chat", "Message seller", "Speak to " + host.studentName(product.sellerEmail) + " about this listing", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                messageSeller(product, page);
            });
            addReportAction(body, product.hidden ? "check" : "shield",
                    product.hidden ? "Restore listing" : "Hide listing",
                    product.hidden ? "Put it back in the marketplace" : "Remove it from public view", v -> {
                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                        boolean wasHidden = product.hidden;
                        product.hidden = !wasHidden;
                        if (!host.market.saveProducts()) {
                            product.hidden = wasHidden;
                            Toast.makeText(host, "Couldn't save the listing change.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        host.notification.addNotification(product.sellerEmail, "Listing update",
                                product.title + (product.hidden ? " was hidden from the marketplace."
                                        : " was restored to the marketplace."), "marketplace", "");
                        page.dismiss();
                        host.showAppScreen("AdminReports");
                    });
            addReportAction(body, "bag", "Remove listing",
                    "Delete it permanently and resolve this report", host.error, v -> {
                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                        confirmRemoveReportedListing(report, product, page);
                    });
        }
        if (reviewReport) {
            addReportAction(body, "star", "Remove reported review", "Delete the rating from the seller profile", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                removeReportedReview(report, page);
            });
        }
        if (userReport) {
            String email = report.reportedEmail();
            if (!email.isEmpty()) {
                addReportAction(body, "chat", "Message reported student",
                        "Contact " + host.studentName(email) + " about this account report", v -> {
                            if (sheetRef[0] != null) sheetRef[0].dismiss();
                            messageReportedStudent(email, "CampusTrade report about your account", page);
                        });
                addReportAction(body, "shield", "Copy reported email",
                        email + " · use this exact account in moderation", v -> {
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager) host.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Reported student", email));
                            }
                            Toast.makeText(host, "Reported email copied.", Toast.LENGTH_SHORT).show();
                        });
            }
        }
        addReportAction(body, "chat", "Reply to reporter", "Send an update to " + report.reporterName, v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            replyToReporter(report);
        });
        if (report.isOpen()) {
            addReportAction(body, "check", "Resolve report", "Mark it as handled and notify the reporter", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                promptCloseReport(report, false, page);
            });
            addReportAction(body, "close", "Dismiss report", "Close it with a recorded reason", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                promptCloseReport(report, true, page);
            });
        }
        sheetRef[0] = host.ui.showBottomSheet("Report actions", body);
    }

    private void addReportAction(LinearLayout parent, String icon, String title, String subtitle,
                                 View.OnClickListener listener) {
        addReportAction(parent, icon, title, subtitle, host.blue, listener);
    }

    private void addReportAction(LinearLayout parent, String icon, String title, String subtitle,
                                 int tint, View.OnClickListener listener) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(10), 0, host.ui.dp(10));
        View symbol = host.new CampusAssetView(host, icon, tint);
        symbol.setPadding(host.ui.dp(9), host.ui.dp(9), host.ui.dp(9), host.ui.dp(9));
        int symbolBackground = tint == host.error
                ? (host.nightActive ? Color.rgb(60, 36, 36) : Color.rgb(255, 236, 236))
                : (host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255));
        symbol.setBackground(host.ui.round(symbolBackground, 10));
        row.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(12), 0, 0, 0);
        copy.addView(host.ui.titleText(title, tint == host.error ? host.error : host.text, 13));
        copy.addView(host.ui.bodyText(subtitle, host.muted, 11));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(host.new CampusAssetView(host, "chevron", host.muted),
                new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16)));
        row.setOnClickListener(listener);
        host.ui.attachPressAnimation(row);
        parent.addView(row);
    }

    private void messageSeller(MainActivity.Product product, Dialog page) {
        page.dismiss();
        String sellerEmail = product.sellerEmail == null ? "" : product.sellerEmail.trim();
        if (sellerEmail.isEmpty()) {
            host.ui.showSimpleMessage("Seller contact", "No seller email is available for this listing.");
            return;
        }
        if (host.adminOnlySession) {
            Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + sellerEmail));
            email.putExtra(Intent.EXTRA_SUBJECT, "CampusTrade listing: " + product.title);
            email.putExtra(Intent.EXTRA_TEXT, "Hello, I'm contacting you as a CampusTrade moderator about your listing \""
                    + product.title + "\".");
            try {
                host.startActivity(Intent.createChooser(email, "Contact seller"));
            } catch (android.content.ActivityNotFoundException e) {
                host.ui.showSimpleMessage("Seller contact", host.studentName(sellerEmail) + "\n" + sellerEmail);
            }
        } else {
            host.inbox.showConversation(sellerEmail);
        }
    }

    private void messageReportedStudent(String studentEmail, String subject, Dialog page) {
        page.dismiss();
        String email = studentEmail == null ? "" : studentEmail.trim();
        if (email.isEmpty()) {
            host.ui.showSimpleMessage("Student contact", "No email is attached to this report.");
            return;
        }
        if (host.adminOnlySession) {
            Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
            mail.putExtra(Intent.EXTRA_SUBJECT, subject);
            mail.putExtra(Intent.EXTRA_TEXT, "Hello, I'm contacting you as a CampusTrade moderator about a student account report.");
            try {
                host.startActivity(Intent.createChooser(mail, "Contact student"));
            } catch (android.content.ActivityNotFoundException e) {
                host.ui.showSimpleMessage("Student contact", host.studentName(email) + "\n" + email);
            }
        } else {
            host.inbox.showConversation(email);
        }
    }

    private void confirmRemoveReportedListing(MainActivity.ModerationReport report,
                                              MainActivity.Product product, Dialog page) {
        host.ui.dialog().setTitle("Remove \"" + product.title + "\"?")
                .setMessage("This permanently deletes the listing by " + host.studentName(product.sellerEmail)
                        + " and resolves the report. It can't be undone.")
                .setNegativeButton("Keep listing", null)
                .setPositiveButton("Remove listing", (d, which) ->
                        promptRemovalReason(product.title, product.sellerEmail, reason -> {
                            host.products.remove(product);
                            if (!host.market.saveProducts()) {
                                host.products.add(product);
                                Toast.makeText(host, "Couldn't remove that listing.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String docId = CloudSync.remoteId(host, product.id);
                            String tombstoneId = docId.isEmpty()
                                    ? "local-" + Integer.toHexString((product.title + "|" + product.sellerEmail)
                                    .toLowerCase(java.util.Locale.ROOT).hashCode())
                                    : docId;
                            recordDeletedListing(tombstoneId, product, reason);
                            recordListingAudit(tombstoneId, product.title, product.sellerEmail, reason);
                            notifyListingRemoved(product.sellerEmail, product.title, reason);
                            if (!docId.isEmpty()) {
                                new ListingRepository().delete(docId, new ListingRepository.DoneCallback() {
                                    @Override public void onOk() {
                                        Toast.makeText(host, "Listing removed for everyone.", Toast.LENGTH_SHORT).show();
                                    }
                                    @Override public void onBad(String message) {
                                        Toast.makeText(host, "Removed here, but the cloud copy refused to delete: " + message,
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                            closeReport(report, false, page, "Listing removed. Reason: " + reason);
                        })).show();
    }

    /** A resolution or dismissal needs a written reason before it can close. */
    private void promptCloseReport(MainActivity.ModerationReport report, boolean dismiss, Dialog page) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText(dismiss
                        ? "Tell the reporter why this report is being dismissed. The reason is saved and sent to them."
                        : "Explain what you did. The reason is saved and sent to the reporter with a request for satisfaction feedback.",
                host.muted, 11));
        EditText note = host.ui.listingField(form, "Reason", "What action did you take?",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setSingleLine(false);
        note.setMinLines(3);
        note.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
        android.app.AlertDialog dialog = host.ui.dialog()
                .setTitle(dismiss ? "Dismiss report?" : "Resolve report?")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(dismiss ? "Dismiss" : "Resolve", null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String reason = host.ui.text(note).trim();
                    if (reason.length() < 8) {
                        note.setError("Add at least 8 characters so the reporter understands");
                        return;
                    }
                    dialog.dismiss();
                    closeReport(report, dismiss, page, reason);
                }));
        dialog.show();
    }

    private void closeReport(MainActivity.ModerationReport report, boolean dismiss, Dialog page, String note) {
        String previousState = report.state;
        String previousNote = report.resolutionNote;
        long previousResolvedAt = report.resolvedAt;
        long now = System.currentTimeMillis();
        if (!report.close(canUseAdmin(), dismiss, note, now)) return;
        if (!host.saveModerationReports()) {
            report.state = previousState;
            report.resolutionNote = previousNote;
            report.resolvedAt = previousResolvedAt;
            Toast.makeText(host, "Couldn't save that report.", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("state", report.state);
        fields.put("resolutionNote", report.resolutionNote);
        fields.put("resolvedAt", com.google.firebase.Timestamp.now());
        fields.put("resolvedBy", host.currentStudentEmail);
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reports")
                .document(report.id).update(fields);
        if (report.reporterEmail != null && !report.reporterEmail.isEmpty()) {
            String title = "Admin: Report " + report.state.toLowerCase(java.util.Locale.ROOT);
            String message = report.productTitle + " was " + report.state.toLowerCase(java.util.Locale.ROOT)
                    + ". Reason: " + note
                    + (dismiss ? "" : " Please rate how we handled it in Settings > My reports.");
            host.notification.addNotification(report.reporterEmail, title, message, "marketplace", "");
            host.notification.sendPush(report.reporterEmail, title, message, "marketplace", "");
            host.inbox.closeAdminChatForEmail(report.reporterEmail,
                    "This report was " + report.state.toLowerCase(java.util.Locale.ROOT) + ": " + note);
        }
        page.dismiss();
        host.showAppScreen("AdminReports");
    }

    private void replyToReporter(MainActivity.ModerationReport report) {
        if (report.reporterEmail == null || report.reporterEmail.isEmpty()) {
            Toast.makeText(host, "No reporter contact for this report.", Toast.LENGTH_SHORT).show();
            return;
        }
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.titleText("Reply to " + report.reporterName, host.text, 16));
        body.addView(host.ui.bodyText("This sends a notification to the student about \"" + report.productTitle + "\".",
                host.muted, 11));
        EditText message = host.ui.listingField(body, "Message", "Write a short reply",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        message.setSingleLine(false);
        message.setMinLines(3);
        message.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(1000)});
        Button send = host.ui.primaryButton("Send reply");
        send.setOnClickListener(v -> {
            String reply = host.ui.text(message).trim();
            if (reply.length() < 3) {
                message.setError("Write a short reply first");
                return;
            }
            host.notification.sendPush(report.reporterEmail, "Admin", reply, "messages", "admin@campustrade.local");
            java.util.Map<String, Object> fields = new java.util.HashMap<>();
            fields.put("lastReply", reply);
            fields.put("lastReplyAt", com.google.firebase.Timestamp.now());
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("reports").document(report.id).update(fields);
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            Toast.makeText(host, "Admin reply sent to " + report.reporterName + "'s Inbox.", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        sendParams.topMargin = host.ui.dp(12);
        body.addView(send, sendParams);
        sheetRef[0] = host.ui.showBottomSheet("Reply to reporter", body);
    }

    /** Remove the reported rating and close the report with a written reason. */
    private void removeReportedReview(MainActivity.ModerationReport report, Dialog page) {
        String docId = report.productId.substring("review:".length()).trim();
        if (docId.isEmpty()) {
            toast("That review link is missing its ID. Nothing was removed.");
            return;
        }
        host.ui.dialog().setTitle("Remove this review?")
                .setMessage("The review on \"" + report.productTitle + "\" will disappear from the seller's profile for everyone.")
                .setNegativeButton("Keep review", null)
                .setPositiveButton("Remove review", (d, w) ->
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("ratings").document(docId).delete()
                                .addOnSuccessListener(v -> {
                                    toast("Review removed. Add a reason to close the report.");
                                    promptCloseReport(report, false, page);
                                })
                                .addOnFailureListener(e -> toast("Couldn't remove that review. Check your admin access and try again.")))
                .show();
    }

    private void toast(String message) {
        Toast.makeText(host, message, Toast.LENGTH_SHORT).show();
    }

    // Load the cloud queue, but never throw away reports already saved on this phone.
    public void loadReports(Runnable after) {
        if (host.currentUid == null || host.currentUid.isEmpty()) {
            if (after != null) after.run();
            return;
        }
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reports").limit(50).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) { if (after != null) after.run(); return; }
                    java.util.LinkedHashMap<String, MainActivity.ModerationReport> merged = new java.util.LinkedHashMap<>();
                    for (MainActivity.ModerationReport existing : host.moderationReports) {
                        merged.put(existing.id, existing);
                    }
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String id = doc.getId();
                        String productId = doc.getString("productId");
                        String productTitle = doc.getString("productTitle");
                        String reporterEmail = doc.getString("reporterEmail");
                        if (reporterEmail == null) reporterEmail = doc.getString("reporterId");
                        String reporterName = doc.getString("reporterName");
                        String reason = doc.getString("reason");
                        String detail = doc.getString("detail");
                        String priority = doc.getString("priority");
                        String state = doc.getString("state");
                        long createdAt = 0;
                        Object created = doc.get("createdAt");
                        if (created instanceof com.google.firebase.Timestamp) createdAt = ((com.google.firebase.Timestamp) created).toDate().getTime();
                        else if (created instanceof Number) createdAt = ((Number) created).longValue();
                        MainActivity.ModerationReport report = new MainActivity.ModerationReport(id,
                                productId == null ? "" : productId,
                                productTitle == null ? "Listing" : productTitle,
                                reporterEmail == null ? "" : reporterEmail,
                                reporterName == null ? "Student" : reporterName,
                                reason == null ? "Report" : reason,
                                detail == null ? "" : detail,
                                priority == null ? "Medium" : priority,
                                state == null ? "Open" : state,
                                createdAt);
                        report.resolutionNote = doc.getString("resolutionNote") == null
                                ? "" : doc.getString("resolutionNote");
                        Object resolved = doc.get("resolvedAt");
                        if (resolved instanceof com.google.firebase.Timestamp) {
                            report.resolvedAt = ((com.google.firebase.Timestamp) resolved).toDate().getTime();
                        } else if (resolved instanceof Number) {
                            report.resolvedAt = ((Number) resolved).longValue();
                        }
                        Long satisfaction = doc.getLong("satisfaction");
                        if (satisfaction != null) report.satisfaction = satisfaction.intValue();
                        String satisfactionComment = doc.getString("satisfactionComment");
                        if (satisfactionComment != null) report.satisfactionComment = satisfactionComment;
                        merged.put(id, report);
                    }
                    host.moderationReports.clear();
                    host.moderationReports.addAll(merged.values());
                    reportsLoadedAt = System.currentTimeMillis();
                    reportCloudWarningShown = false;
                    if (after != null) after.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("CampusTrade", "report queue load failed: " + e.getMessage());
                    reportsLoadedAt = System.currentTimeMillis();
                    if (canUseAdmin() && !reportCloudWarningShown) {
                        reportCloudWarningShown = true;
                        Toast.makeText(host,
                                "Cloud reports unavailable. Showing reports saved on this device. Check the Firestore isAdmin role.",
                                Toast.LENGTH_LONG).show();
                    }
                    if (after != null) after.run();
                });
    }

    /** Refresh the queue when Admin/Reports is opened, without rebuilding in a loop. */
    public void refreshReportsIfStale() {
        if (!canUseAdmin() || reportsLoading) return;
        if (System.currentTimeMillis() - reportsLoadedAt < 6000) return;
        reportsLoading = true;
        loadReports(() -> {
            reportsLoading = false;
            reportsLoadedAt = System.currentTimeMillis();
            if (host.isDestroyed() || host.isFinishing()) return;
            if ("AdminReports".equals(host.currentTab)) host.showAppScreen("AdminReports");
            else if ("Admin".equals(host.currentTab)) host.showAppScreen("Admin");
        });
    }

    // The login Admin screen has no app shell, so build a small one.
    public void showAdminOnlyScreen(String tab) {
        if (!canUseAdmin()) {
            host.auth.showAuthScreen();
            return;
        }
        host.currentTab = "AdminReports".equals(tab) || "AdminChat".equals(tab)
                || "AdminAnalytics".equals(tab) ? tab : "Admin";
        LinearLayout screen = new LinearLayout(host);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(host.page);
        LinearLayout topBar = new LinearLayout(host);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(host.page);
        topBar.addView(host.appHeader(host.headerTitle(host.currentTab), host.headerSubtitle(host.currentTab), host.headerAction(host.currentTab)),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View headerRule = new View(host);
        headerRule.setBackgroundColor(host.line);
        topBar.addView(headerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        screen.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(28), host.ui.dp(12), host.ui.dp(28), host.ui.dp(16));
        scroll.addView(content);
        screen.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (!host.currentTab.equals("Admin")) {
            TextView back = host.ui.linkText("< Back to admin overview");
            back.setPadding(0, host.ui.dp(2), 0, host.ui.dp(10));
            back.setOnClickListener(v -> host.showAppScreen("Admin"));
            content.addView(back);
        }
        if (host.currentTab.equals("AdminReports")) buildAdminReports(content);
        else if (host.currentTab.equals("AdminChat")) buildAdminChats(content);
        else if (host.currentTab.equals("AdminAnalytics")) host.adminAnalytics.build(content);
        else buildAdmin(content);

        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(28), host.ui.dp(8), host.ui.dp(28), host.ui.dp(20));
        Button exit = host.ui.secondaryButton("Exit admin");
        exit.setOnClickListener(v -> exitAdminMode());
        footer.addView(exit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        screen.addView(footer);
        topBar.setElevation(host.ui.dp(6));
        host.setContentView(screen);
    }

    // Dashboard numbers come from this phone; "last 24 hours" uses the last active time.
    public void buildAdmin(LinearLayout content) {
        if (!canUseAdmin()) return;
        refreshReportsIfStale();
        String adminName = host.adminOnlySession ? "Campus moderator" : host.currentStudentName;
        LinearLayout banner = host.ui.horizontal();
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        banner.setBackground(host.ui.bottomRule(host.text));
        TextView avatar = host.ui.titleText(host.ui.initials(adminName), host.blue, 14);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(host.ui.round(Color.rgb(225, 236, 255), 999));
        banner.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(10), 0, host.ui.dp(6), 0);
        TextView name = host.ui.titleText(adminName, host.text, 18);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);
        copy.addView(host.ui.bodyText("Moderator - Eduvos", host.muted, 11));
        banner.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        banner.addView(host.ui.chip("Moderator", host.ui.statusBackground("Active"), host.ui.statusInk("Active")));
        content.addView(banner);

        android.widget.HorizontalScrollView adminTabScroll = new android.widget.HorizontalScrollView(host);
        adminTabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout adminTabs = host.ui.horizontal();
        adminTabs.setPadding(0, host.ui.dp(10), 0, host.ui.dp(2));
        for (String tab : new String[] {"Overview", "Reports", "Chat", "Feedback", "Analytics"}) {
            boolean active = tab.equals("Overview");
            TextView tabView = host.ui.filterPill(tab, active);
            tabView.setSingleLine(true);
            tabView.setOnClickListener(v -> {
                if (tab.equals("Reports")) host.showAppScreen("AdminReports");
                else if (tab.equals("Chat")) host.showAppScreen("AdminChat");
                else if (tab.equals("Feedback")) showFeedback();
                else if (tab.equals("Analytics")) host.showAppScreen("AdminAnalytics");
                else host.showAppScreen("Admin");
            });
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(40));
            if (!tab.equals("Overview")) tabParams.leftMargin = host.ui.dp(6);
            adminTabs.addView(tabView, tabParams);
        }
        adminTabScroll.addView(adminTabs);
        content.addView(adminTabScroll);

        LinearLayout heading = host.ui.horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, host.ui.dp(16), 0, host.ui.dp(2));
        heading.addView(host.ui.titleText("Platform health", host.text, 16), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView range = host.ui.linkText(host.adminLast24Hours ? "Last 24 hours" : "All time");
        range.setTextSize(11);
        range.setOnClickListener(v -> host.ui.dialog().setTitle("Overview period")
                .setItems(new String[] {"Last 24 hours", "All time"}, (d, which) -> {
                    host.adminLast24Hours = which == 0; host.showAppScreen("Admin");
                }).show());
        heading.addView(range);
        content.addView(heading);
        content.addView(host.ui.bodyText("Live students and the moderation queue update from Firebase. Other numbers are a quick on-device view.",
                host.muted, 10));
        content.addView(host.ui.bodyText("Live students = online now in Firebase. On-device = saved on this phone · Marketplace = synced listings · Needs review = open reports.",
                host.muted, 10));
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        int live = 0;
        for (MainActivity.Product product : host.products) if (!product.hidden && product.status.equals("Available")
                && (!host.adminLast24Hours || product.createdTime >= cutoff)) live++;
        startLiveStudentListener();
        LinearLayout first = host.ui.horizontal();
        first.addView(adminMetric("team", liveStudents, "Live students", "Online now", host.blue), adminCellParams(false));
        first.addView(adminMetric("bag", live, "Live listings", "Marketplace", host.blue), adminCellParams(true));
        content.addView(first);
        LinearLayout second = host.ui.horizontal();
        second.addView(adminMetric("flag", openReportCount(), "Open reports", "Needs review", host.blue), adminCellParams(false));
        second.addView(adminMetric("chat", host.featureStore.data.groups.size(), "Groups", "On-device", host.blue), adminCellParams(true));
        content.addView(second);

        LinearLayout queueHeading = host.ui.horizontal();
        queueHeading.setGravity(Gravity.CENTER_VERTICAL);
        queueHeading.setPadding(0, host.ui.dp(16), 0, host.ui.dp(4));
        queueHeading.addView(host.ui.titleText("Moderation queue", host.text, 16), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView all = host.ui.linkText("View all " + openReportCount());
        all.setTextSize(11);
        all.setOnClickListener(v -> { host.showClosedReports = false; host.showAppScreen("AdminReports"); });
        queueHeading.addView(all);
        content.addView(queueHeading);
        addReportQueue(content, false, 3);

        host.profile.settingsHeading(content, "Moderation and insights");
        LinearLayout tools = host.ui.card(host.surface(), 16, 0);
        tools.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        tools.addView(host.profile.settingsOption("shield", "Manage student accounts", "Search, suspend or restore access", "Open", v -> adminManageUsers()));
        host.ui.addMenuDivider(tools);
        tools.addView(host.profile.settingsOption("flag", "Account appeals", "Review suspended accounts and content warnings", "Open", v -> showAppeals()));
        host.ui.addMenuDivider(tools);
        tools.addView(host.profile.settingsOption("shield", "Content warning queue", "Review flagged listings, chats and posts", "Open", v -> showContentFlags()));
        host.ui.addMenuDivider(tools);
        tools.addView(host.profile.settingsOption("chat", "Student chat", "Reply live, close resolved conversations", "Open", v -> host.showAppScreen("AdminChat")));
        host.ui.addMenuDivider(tools);
        tools.addView(host.profile.settingsOption("checklist", "Marketplace analytics", "Users, listings, sales and reports", "Open", v -> host.showAppScreen("AdminAnalytics")));
        host.ui.addMenuDivider(tools);
        tools.addView(host.profile.settingsOption("chat", "View feedback", "Help and support messages from students", "Open", v -> showFeedback()));
        host.ui.addMenuDivider(tools);
        tools.addView(host.profile.settingsOption("shield", "Lock admin mode", "Passcode needed again", "Lock", v -> lockAdminMode()));
        content.addView(tools);

        host.profile.settingsHeading(content, "Destructive actions");
        content.addView(host.ui.bodyText("These permanently delete marketplace content. Search for the item and confirm by name before removing it.",
                host.muted, 11));
        LinearLayout destructive = host.ui.card(host.surface(), 16, 0);
        destructive.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        destructive.addView(host.profile.settingsOption("bag", "Remove a listing", "Delete any marketplace listing", "Remove", v -> adminRemoveListing()));
        host.ui.addMenuDivider(destructive);
        destructive.addView(host.profile.settingsOption("profile", "Remove a student", "Delete a profile and their listings", "Remove", v -> adminRemoveUser()));
        content.addView(destructive);
    }

    /** Live moderator conversations and the report threads behind them. */
    public void buildAdminChats(LinearLayout content) {
        if (!canUseAdmin()) return;
        if (!adminChatsLoading && System.currentTimeMillis() - adminChatsLoadedAt > 15000) {
            adminChatsLoading = true;
            host.inbox.syncAdminChats(() -> {
                adminChatsLoading = false;
                adminChatsLoadedAt = System.currentTimeMillis();
                if (host.isDestroyed() || host.isFinishing()) return;
                if ("AdminChat".equals(host.currentTab)) host.showAppScreen("AdminChat");
            });
        }

        LinearLayout hero = host.ui.card(host.surface(), 16, host.ui.dp(16));
        hero.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        LinearLayout heroTop = host.ui.horizontal();
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        View symbol = host.new CampusAssetView(host, "chat", host.blue);
        symbol.setPadding(host.ui.dp(9), host.ui.dp(9), host.ui.dp(9), host.ui.dp(9));
        symbol.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), 10));
        heroTop.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        LinearLayout heroCopy = new LinearLayout(host);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setPadding(host.ui.dp(10), 0, host.ui.dp(4), 0);
        heroCopy.addView(host.ui.titleText("Student chat", host.text, 17));
        heroCopy.addView(host.ui.bodyText("Reply live. Chats close when the report is resolved or after 1 hour of inactivity.",
                host.muted, 11));
        heroTop.addView(heroCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        hero.addView(heroTop);
        Button start = host.ui.primaryButton("Message a student");
        start.setOnClickListener(v -> promptAdminChatStudent());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        startParams.topMargin = host.ui.dp(12);
        hero.addView(start, startParams);
        content.addView(hero);

        int openReports = 0;
        ArrayList<MainActivity.ModerationReport> reportChats = new ArrayList<>();
        for (MainActivity.ModerationReport report : host.moderationReports) {
            if (!report.isOpen() || report.reporterEmail == null || report.reporterEmail.isEmpty()) continue;
            openReports++;
            reportChats.add(report);
        }
        java.util.Collections.sort(reportChats,
                (a, b) -> Long.compare(b.createdAt, a.createdAt));
        host.profile.settingsHeading(content, "Report conversations (" + openReports + ")");
        if (reportChats.isEmpty()) {
            content.addView(host.ui.emptyState("flag", "No open report conversations",
                    "New reports that need an admin reply will appear here."));
        } else {
            LinearLayout reports = host.ui.card(host.surface(), 16, 0);
            reports.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
            int shownReports = 0;
            for (MainActivity.ModerationReport report : reportChats) {
                if (shownReports++ > 0) host.ui.addMenuDivider(reports);
                LinearLayout row = host.ui.horizontal();
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(host.ui.dp(12), host.ui.dp(11), host.ui.dp(12), host.ui.dp(11));
                LinearLayout copy = new LinearLayout(host);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.addView(host.ui.titleText(report.reporterName, host.text, 13));
                TextView reason = host.ui.bodyText(report.reason + " - " + report.productTitle, host.muted, 11);
                reason.setSingleLine(true);
                reason.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(reason);
                copy.addView(host.ui.bodyText(host.notification.alertTime(report.createdAt), host.muted, 10));
                row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView chat = host.ui.linkText("Live chat");
                chat.setTextSize(11);
                chat.setPadding(host.ui.dp(6), host.ui.dp(8), host.ui.dp(6), host.ui.dp(8));
                chat.setOnClickListener(v -> host.inbox.openReportChat(report.reporterEmail));
                row.addView(chat);
                TextView email = host.ui.linkText("Email");
                email.setTextSize(11);
                email.setPadding(host.ui.dp(6), host.ui.dp(8), host.ui.dp(4), host.ui.dp(8));
                email.setOnClickListener(v -> emailStudent(report.reporterEmail,
                        "CampusTrade report: " + report.productTitle));
                row.addView(email);
                row.setOnClickListener(v -> showReportReview(report));
                reports.addView(row);
            }
            content.addView(reports);
        }

        host.profile.settingsHeading(content, "All student conversations");
        ArrayList<String> peers = adminChatPeers();
        if (peers.isEmpty()) {
            content.addView(host.ui.emptyState("chat", "No messages yet",
                    "Start a conversation above, or reply to a report conversation."));
            return;
        }
        LinearLayout conversations = host.ui.card(host.surface(), 16, 0);
        conversations.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        for (int i = 0; i < peers.size(); i++) {
            String peer = peers.get(i);
            MainActivity.ChatMessage latest = host.inbox.latestMessage(peer);
            if (latest == null) continue;
            if (conversations.getChildCount() > 0) host.ui.addMenuDivider(conversations);
            boolean closed = host.inbox.adminChatClosed(peer);
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(host.ui.dp(12), host.ui.dp(11), host.ui.dp(12), host.ui.dp(11));
            TextView avatar = host.ui.titleText(host.ui.initials(host.studentName(peer)), host.blue, 12);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), 999));
            row.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(38), host.ui.dp(38)));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(10), 0, host.ui.dp(6), 0);
            copy.addView(host.ui.titleText(host.studentName(peer), host.text, 13));
            String preview = latest.body == null ? "" : latest.body
                    .replace("[Admin]", "").replace("[Chat closed]", "").trim();
            if (closed) preview = "Chat closed" + (preview.isEmpty() ? "" : " - " + preview);
            TextView message = host.ui.bodyText(preview, host.muted, 11);
            message.setSingleLine(true);
            message.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(message);
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            int bg = closed ? (host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235))
                    : host.ui.statusBackground("Active");
            int ink = closed ? host.muted : host.ui.statusInk("Active");
            TextView state = host.ui.chip(closed ? "Closed" : "Open", bg, ink);
            state.setTextSize(9);
            row.addView(state);
            View chevron = host.new CampusAssetView(host, "chevron", host.muted);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16));
            chevronParams.leftMargin = host.ui.dp(6);
            row.addView(chevron, chevronParams);
            row.setOnClickListener(v -> {
                host.chatReturnTab = "AdminChat";
                host.inbox.showConversation(peer);
            });
            host.ui.attachPressAnimation(row);
            conversations.addView(row);
        }
        content.addView(conversations);
    }

    private ArrayList<String> adminChatPeers() {
        java.util.LinkedHashMap<String, Long> latest = new java.util.LinkedHashMap<>();
        for (MainActivity.ChatMessage message : host.chatMessages) {
            String peer = null;
            if (message.sender.equals(host.currentStudentEmail)) peer = message.recipient;
            else if (message.recipient.equals(host.currentStudentEmail)) peer = message.sender;
            if (peer == null || peer.isEmpty() || peer.equals(host.currentStudentEmail)
                    || peer.equals(HelperBot.PEER) || peer.startsWith("group:")) continue;
            Long when = latest.get(peer);
            if (when == null || message.time > when) latest.put(peer, message.time);
        }
        ArrayList<String> peers = new ArrayList<>(latest.keySet());
        java.util.Collections.sort(peers, (a, b) -> Long.compare(
                latest.get(b) == null ? 0 : latest.get(b),
                latest.get(a) == null ? 0 : latest.get(a)));
        return peers;
    }

    private void promptAdminChatStudent() {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Open a direct CampusTrade chat with a student. The student sees your messages as Admin.",
                host.muted, 11));
        EditText email = host.ui.listingField(form, "Student email", "student@vossie.net",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        Button open = host.ui.primaryButton("Open chat");
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        openParams.topMargin = host.ui.dp(10);
        form.addView(open, openParams);
        Dialog sheet = host.ui.showBottomSheet("Message a student", form);
        open.setOnClickListener(v -> {
            String peer = host.normalizeEmail(host.ui.text(email));
            if (!host.auth.isStudentEmail(peer)) {
                email.setError("Enter a student @vossie.net email");
                return;
            }
            if (peer.equals(host.currentStudentEmail)) {
                email.setError("Choose another student");
                return;
            }
            sheet.dismiss();
            host.chatReturnTab = "AdminChat";
            host.inbox.showConversation(peer);
        });
    }

    private void emailStudent(String email, String subject) {
        if (email == null || email.isEmpty()) {
            toast("No student email is attached to this chat.");
            return;
        }
        Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
        mail.putExtra(Intent.EXTRA_SUBJECT, subject == null ? "CampusTrade" : subject);
        mail.putExtra(Intent.EXTRA_TEXT, "Hello, I'm contacting you as a CampusTrade moderator.");
        try {
            host.startActivity(Intent.createChooser(mail, "Email student"));
        } catch (android.content.ActivityNotFoundException e) {
            host.ui.showSimpleMessage("Student email", email);
        }
    }

    private void startLiveStudentListener() {
        if (presenceListener != null) return;
        presenceListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").whereEqualTo("online", true).limit(500)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        android.util.Log.w("CampusTrade", "presence listener: " + error.getMessage());
                        return;
                    }
                    if (snap == null || host.isDestroyed() || host.isFinishing()) return;
                    long now = System.currentTimeMillis();
                    int count = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String email = doc.getString("stuEmail");
                        if (email == null || email.isEmpty()
                                || Constants.STRICT_ADMIN_EMAIL.equalsIgnoreCase(email)) continue;
                        String status = doc.getString("accountStatus");
                        if (status != null && (status.equalsIgnoreCase("suspended")
                                || status.equalsIgnoreCase("deactivated")
                                || status.equalsIgnoreCase("deleted"))) continue;
                        Long lastActive = doc.getLong("lastActive");
                        if (lastActive == null || now - lastActive > 180000) continue;
                        count++;
                    }
                    liveStudents = count;
                    if (liveStudentsValue != null) {
                        liveStudentsValue.setText(String.valueOf(count));
                    }
                });
    }

    private void stopLiveStudentListener() {
        if (presenceListener != null) {
            presenceListener.remove();
            presenceListener = null;
        }
        liveStudentsValue = null;
    }

    private LinearLayout.LayoutParams adminCellParams(boolean right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.topMargin = host.ui.dp(10);
        if (right) params.leftMargin = host.ui.dp(10);
        return params;
    }

    private LinearLayout adminMetric(String icon, int count, String label, String status, int accent) {
        LinearLayout metric = host.ui.card(host.surface(), 16, host.ui.dp(12));
        metric.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        LinearLayout top = host.ui.horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        View symbol = host.new CampusAssetView(host, icon, accent);
        symbol.setPadding(host.ui.dp(6), host.ui.dp(6), host.ui.dp(6), host.ui.dp(6));
        symbol.setBackground(host.ui.round(Color.argb(22, Color.red(accent), Color.green(accent), Color.blue(accent)), 10));
        top.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(32), host.ui.dp(32)));
        int bg = status.equals("Marketplace") ? host.ui.statusBackground("Active")
                : status.equals("Needs review") ? host.ui.statusBackground("Awaiting")
                : host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235);
        int ink = status.equals("Marketplace") ? host.ui.statusInk("Active")
                : status.equals("Needs review") ? host.ui.statusInk("Awaiting") : host.muted;
        TextView badge = host.ui.chip(status, bg, ink);
        badge.setTextSize(9);
        badge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.END;
        top.addView(badge, badgeParams);
        metric.addView(top);
        TextView value = host.ui.titleText(String.valueOf(count), host.text, 20);
        value.setPadding(0, host.ui.dp(8), 0, host.ui.dp(2));
        metric.addView(value);
        metric.addView(host.ui.bodyText(label, host.muted, 11));
        if (icon.equals("team")) {
            liveStudentsValue = value;
            value.setText(String.valueOf(liveStudents));
        }
        metric.setOnClickListener(v -> {
            if (icon.equals("flag")) host.showAppScreen("AdminReports");
            else if (icon.equals("bag")) {
                if (host.adminOnlySession) host.ui.showSimpleMessage(label, count + " live listings saved on this phone. Use Remove a listing in Destructive actions to moderate them.");
                else { host.marketFilter = "All"; host.showAppScreen("Market"); }
            } else if (icon.equals("chat")) {
                if (host.adminOnlySession) host.ui.showSimpleMessage(label, count + " campus groups in this on-device overview.");
                else host.showAppScreen("Groups");
            } else if (icon.equals("team")) {
                host.ui.showSimpleMessage(label, liveStudents + " student(s) are online right now.");
            } else host.ui.showSimpleMessage(label, count + " student profiles saved on this phone.");
        });
        return metric;
    }

    public void buildAdminReports(LinearLayout content) {
        if (!canUseAdmin()) return;
        refreshReportsIfStale();
        LinearLayout tabs = host.ui.horizontal();
        int reportTabIndex = 0;
        for (String mode : new String[] {"Open", "History"}) {
            TextView tab = host.ui.filterPill(mode, mode.equals("History") == host.showClosedReports);
            tab.setOnClickListener(v -> { host.showClosedReports = mode.equals("History"); host.showAppScreen("AdminReports"); });
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, host.ui.dp(48), 1);
            if (reportTabIndex++ > 0) tabParams.leftMargin = host.ui.dp(4);
            tabs.addView(tab, tabParams);
        }
        content.addView(tabs);

        // Every report type has its own live total, so a review or student
        // report is never counted as a listing report.
        android.widget.HorizontalScrollView typeScroll = new android.widget.HorizontalScrollView(host);
        typeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout typeRow = host.ui.horizontal();
        typeRow.setPadding(0, host.ui.dp(8), 0, 0);
        for (String type : new String[] {"All", "Listing", "Review", "Student"}) {
            int count = reportTypeCount(host.showClosedReports, type);
            String label = (type.equals("All") ? "All reports" : type.equals("Student") ? "Students" : type + "s")
                    + " (" + count + ")";
            TextView filter = host.ui.filterPill(label, type.equals(host.adminReportFilter));
            filter.setOnClickListener(v -> {
                host.adminReportFilter = type;
                host.showAppScreen("AdminReports");
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(40));
            if (typeRow.getChildCount() > 0) params.leftMargin = host.ui.dp(6);
            typeRow.addView(filter, params);
        }
        typeScroll.addView(typeRow);
        content.addView(typeScroll);

        EditText search = host.ui.input("Search reports by student, reason or reported item", InputType.TYPE_CLASS_TEXT);
        search.setText(host.adminReportQuery);
        search.setSingleLine(true);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        searchParams.topMargin = host.ui.dp(8);
        content.addView(search, searchParams);
        LinearLayout queue = new LinearLayout(host);
        queue.setOrientation(LinearLayout.VERTICAL);
        content.addView(queue);
        search.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                host.adminReportQuery = s.toString().trim();
                queue.removeAllViews();
                addReportQueue(queue, host.showClosedReports, Integer.MAX_VALUE);
            }
        });
        addReportQueue(queue, host.showClosedReports, Integer.MAX_VALUE);
    }

    /** Severity chips use grey, orange and red so priority is readable at a glance. */
    private TextView priorityChip(String priority) {
        String value = priority == null || priority.isEmpty() ? "Low" : priority;
        int bg;
        int ink;
        if (value.equalsIgnoreCase("High")) {
            bg = host.nightActive ? Color.rgb(64, 34, 34) : Color.rgb(255, 230, 230);
            ink = host.nightActive ? Color.rgb(255, 150, 150) : Color.rgb(180, 30, 30);
        } else if (value.equalsIgnoreCase("Medium")) {
            bg = host.nightActive ? Color.rgb(60, 42, 24) : Color.rgb(255, 239, 220);
            ink = host.nightActive ? Color.rgb(255, 184, 108) : Color.rgb(176, 86, 0);
        } else {
            bg = host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(238, 238, 238);
            ink = host.muted;
        }
        TextView chip = host.ui.chip(value, bg, ink);
        chip.setTextSize(10);
        return chip;
    }

    private int reportTypeCount(boolean closed, String type) {
        int count = 0;
        String wanted = type.equals("Student") ? "user" : type.toLowerCase(java.util.Locale.ROOT);
        for (MainActivity.ModerationReport report : host.moderationReports) {
            if (report.isOpen() == closed) continue;
            if (!type.equals("All") && !report.entityType().equals(wanted)) continue;
            count++;
        }
        return count;
    }

    // Newest reports come first, whatever their priority.
    private void addReportQueue(LinearLayout content, boolean closed, int limit) {
        ArrayList<MainActivity.ModerationReport> ordered = new ArrayList<>(host.moderationReports);
        java.util.Collections.sort(ordered, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        int shown = 0;
        String query = host.adminReportQuery == null ? "" : host.adminReportQuery.toLowerCase(java.util.Locale.ROOT);
        String typeFilter = host.adminReportFilter == null ? "All" : host.adminReportFilter;
        String wantedType = typeFilter.equals("Student") ? "user" : typeFilter.toLowerCase(java.util.Locale.ROOT);
        LinearLayout queue = host.ui.card(host.surface(), 16, host.ui.dp(12));
        queue.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        for (MainActivity.ModerationReport report : ordered) {
            if (report.isOpen() == closed || shown >= limit) continue;
            if (!typeFilter.equals("All") && !report.entityType().equals(wantedType)) continue;
            if (!query.isEmpty()) {
                String haystack = (report.reason + " " + report.productTitle + " " + report.reporterName
                        + " " + report.reportedEmail() + " " + report.entityLabel() + " " + report.detail)
                        .toLowerCase(java.util.Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            if (shown > 0) host.ui.addMenuDivider(queue);
            shown++;
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, host.ui.dp(10), 0, host.ui.dp(10));
            TextView avatar = host.ui.titleText(host.ui.initials(report.reporterName), host.blue, 13);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), 999));
            row.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(10), 0, host.ui.dp(6), 0);
            TextView title = host.ui.titleText(report.reason, host.text, 12);
            title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);
            String target = report.entityType().equals("user") ? report.reportedEmail()
                    : report.productTitle.isEmpty() ? report.productId : report.productTitle;
            TextView detail = host.ui.bodyText(report.entityLabel() + " report · " + target
                    + " · by " + report.reporterName, host.muted, 10);
            detail.setSingleLine(true); detail.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(detail);
            copy.addView(host.ui.bodyText(host.notification.alertTime(report.createdAt), host.muted, 10));
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (closed) {
                row.addView(host.ui.statusPill(report.state));
            } else {
                row.addView(priorityChip(report.priority));
            }
            row.addView(host.new CampusAssetView(host, "chevron", host.muted), new LinearLayout.LayoutParams(host.ui.dp(18), host.ui.dp(18)));
            row.setOnClickListener(v -> showReportReview(report));
            queue.addView(row);
        }
        if (shown == 0) {
            content.addView(host.ui.emptyState(closed ? "checklist" : "flag",
                    !query.isEmpty() ? "No reports match" : closed ? "No finished reports yet" : "No reports, nice work.",
                    !query.isEmpty() ? "Try another student, reason or reported item."
                            : closed ? "Resolved and dismissed reports will appear here."
                            : "Listing, review and student reports will show up here."));
            return;
        }
        content.addView(queue);
    }

    // Manage students on a full page with search and status pills.
    public void adminManageUsers() {
        if (!canUseAdmin()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").limit(50).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    ArrayList<String> ids = new ArrayList<>();
                    ArrayList<String> names = new ArrayList<>();
                    ArrayList<String> emails = new ArrayList<>();
                    ArrayList<String> states = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        if (doc.getId().equals(host.currentUid)) continue;
                        String name = doc.getString("stuName");
                        String email = doc.getString("stuEmail");
                        String status = doc.getString("accountStatus");
                        if (status == null || status.isEmpty()) status = "active";
                        ids.add(doc.getId());
                        names.add(name == null || name.isEmpty() ? "Student" : name);
                        emails.add(email == null ? "" : email);
                        states.add(status);
                    }
                    if (ids.isEmpty()) {
                        host.ui.showSimpleMessage("Student accounts", "No other student accounts found.");
                        return;
                    }
                    showUserPicker(ids, names, emails, states);
                })
                .addOnFailureListener(e -> host.ui.showSimpleMessage("Student accounts",
                        "We couldn't load the student list. Check your connection and try again."));
    }

    private void showUserPicker(ArrayList<String> ids, ArrayList<String> names,
                                ArrayList<String> emails, ArrayList<String> states) {
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Student accounts", "Search, suspend or restore access", page::dismiss));
        EditText search = host.ui.input("Search by name, email or status", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        LinearLayout searchWrap = new LinearLayout(host);
        searchWrap.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(8));
        searchWrap.addView(search);
        root.addView(searchWrap);
        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            String query = host.ui.text(search).trim().toLowerCase(java.util.Locale.ROOT);
            int shown = 0;
            for (int i = 0; i < ids.size(); i++) {
                String haystack = names.get(i) + " " + emails.get(i) + " " + states.get(i);
                if (!query.isEmpty() && !haystack.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                shown++;
                final int index = i;
                LinearLayout card = host.ui.card(host.surface(), 12, host.ui.dp(12));
                card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
                LinearLayout row = host.ui.horizontal();
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView avatar = host.ui.titleText(host.ui.initials(names.get(i)), host.blue, 11);
                avatar.setGravity(Gravity.CENTER);
                avatar.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(225, 236, 255), 999));
                row.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
                LinearLayout copy = new LinearLayout(host);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
                LinearLayout nameRow = host.ui.horizontal();
                nameRow.setGravity(Gravity.CENTER_VERTICAL);
                nameRow.addView(host.ui.titleText(names.get(i), host.text, 13),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                nameRow.addView(host.ui.statusPill(accountStatusLabel(states.get(i))));
                copy.addView(nameRow);
                TextView email = host.ui.bodyText(emails.get(i), host.muted, 11);
                email.setSingleLine(true);
                email.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(email);
                row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView manage = host.ui.textButton("Manage");
                manage.setTextSize(11);
                LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(40));
                row.addView(manage, manageParams);
                card.addView(row);
                card.setOnClickListener(v -> showAccountActions(page, ids.get(index), names.get(index),
                        emails.get(index), states.get(index), () -> {
                            states.set(index, states.get(index).equals("active") ? "suspended" : "active");
                            render[0].run();
                        }));
                host.ui.attachPressAnimation(card);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = host.ui.dp(8);
                list.addView(card, cardParams);
            }
            if (shown == 0) {
                list.addView(host.ui.emptyState("team", "No students match",
                        query.isEmpty() ? "No other student accounts found." : "Try a different name, email or status."));
            }
        };
        search.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) { render[0].run(); }
        });
        render[0].run();

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private String accountStatusLabel(String status) {
        if (status == null || status.isEmpty()) return "Active";
        String value = status.toLowerCase(java.util.Locale.ROOT);
        if (value.equals("active")) return "Active";
        if (value.equals("suspended")) return "Suspended";
        if (value.equals("deactivated")) return "Deactivated";
        if (value.equals("deleted")) return "Deleted";
        return status.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + status.substring(1);
    }

    private void showAccountActions(Dialog page, String uid, String name, String email,
                                    String current, Runnable after) {
        boolean blocked = current.equals("suspended") || current.equals("deactivated") || current.equals("deleted");
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.titleText(name, host.text, 17));
        body.addView(host.ui.bodyText(email, host.muted, 12));
        LinearLayout statusRow = host.ui.horizontal();
        statusRow.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
        statusRow.addView(host.ui.statusPill(accountStatusLabel(current)));
        body.addView(statusRow);
        body.addView(host.ui.bodyText(blocked
                ? "This student can't sign in. Restoring access lets them use CampusTrade again."
                : "Suspending signs the student out and blocks new sign-ins. Their listings stay visible unless you remove them.",
                host.muted, 12));
        Button action = blocked ? host.ui.primaryButton("Restore access") : host.ui.primaryButton("Suspend account");
        action.setOnClickListener(v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            confirmAccountChange(uid, name, email, current, after);
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        actionParams.topMargin = host.ui.dp(14);
        body.addView(action, actionParams);
        sheetRef[0] = host.ui.showBottomSheet("Account actions", body);
    }

    private void confirmAccountChange(String uid, String name, String email, String current, Runnable after) {
        boolean blocked = current.equals("suspended") || current.equals("deactivated") || current.equals("deleted");
        String next = blocked ? "active" : "suspended";
        host.ui.dialog().setTitle(blocked ? "Restore " + name + "?" : "Suspend " + name + "?")
                .setMessage(email + "\n\n" + (blocked
                        ? "They will be able to sign in again."
                        : "They will be signed out and blocked from signing in."))
                .setNegativeButton("Cancel", null)
                .setPositiveButton(blocked ? "Restore" : "Suspend", (confirm, action) ->
                        changeAccountStatus(uid, next, after))
                .show();
    }

    private void changeAccountStatus(String uid, String status, Runnable after) {
        new com.campustrade.auth.data.AuthRepository().setAccountStatus(uid, status,
                new com.campustrade.auth.data.AuthRepository.SimpleCallback() {
                    @Override public void onOk() {
                        java.util.Map<String, Object> action = new java.util.HashMap<>();
                        action.put("adminId", host.currentUid);
                        action.put("adminName", host.currentStudentName);
                        action.put("action", status.equals("active") ? "restore_account" : "suspend_account");
                        action.put("targetUserId", uid);
                        action.put("reason", status.equals("active") ? "Restored by moderator" : "Suspended by moderator");
                        action.put("createdAt", com.google.firebase.Timestamp.now());
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("admin_actions").add(action);
                        Toast.makeText(host, "Account updated to " + status + ".", Toast.LENGTH_SHORT).show();
                        if (after != null) after.run();
                    }
                    @Override public void onBad(String message) {
                        host.ui.showSimpleMessage("That didn't update", message);
                    }
                });
    }

    public void showReportListing(MainActivity.Product product) {
        if (host.market.ownsListing(product)) {
            Toast.makeText(host, "This one is yours.", Toast.LENGTH_SHORT).show(); return;
        }
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Reports go to the campus moderation queue. The seller is not told who reported the listing.",
                host.muted, 11));
        android.widget.Spinner reason = host.ui.listingSelector(form, "Reason", new String[] {"Misleading condition", "Prohibited item", "Suspected scam", "Other"});
        EditText detail = host.ui.listingField(form, "Details", "Describe the issue", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        detail.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(1000)});
        Button submit = host.ui.primaryButton("Submit report");
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        submitParams.topMargin = host.ui.dp(10);
        form.addView(submit, submitParams);
        Dialog sheet = host.ui.showBottomSheet("Report listing", form);
        submit.setOnClickListener(v -> {
            if (host.ui.text(detail).trim().length() < 10) { detail.setError("Add at least 10 characters of detail"); return; }
            for (MainActivity.ModerationReport report : host.moderationReports) if (report.isOpen() && report.productId.equals(product.id) && report.reporterEmail.equals(host.currentStudentEmail)) {
                Toast.makeText(host, "You already reported this listing - we're on it.", Toast.LENGTH_SHORT).show(); return;
            }
            String selected = reason.getSelectedItem().toString();
            String priority = selected.equals("Suspected scam") ? "High" : selected.equals("Other") ? "Low" : "Medium";
            MainActivity.ModerationReport report = new MainActivity.ModerationReport(java.util.UUID.randomUUID().toString(), product.id, product.title, host.currentStudentEmail,
                    host.currentStudentName, selected, host.ui.text(detail).trim(), priority, "Open", System.currentTimeMillis());
            host.moderationReports.add(report);
            if (!host.saveModerationReports()) { host.moderationReports.remove(report); Toast.makeText(host, "That report didn't save.", Toast.LENGTH_SHORT).show(); return; }
            java.util.Map<String, Object> reportDoc = new java.util.HashMap<>();
            reportDoc.put("reporterId", host.currentUid);
            reportDoc.put("reporterEmail", report.reporterEmail);
            reportDoc.put("reporterName", report.reporterName);
            reportDoc.put("productId", report.productId);
            reportDoc.put("productTitle", report.productTitle);
            reportDoc.put("reason", report.reason);
            reportDoc.put("detail", report.detail);
            reportDoc.put("priority", report.priority);
            reportDoc.put("state", report.state);
            reportDoc.put("createdAt", com.google.firebase.Timestamp.now());
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reports").document(report.id).set(reportDoc);
            sheet.dismiss();
            host.notification.showCampusNotification("Report submitted", product.title + " has been added to the moderation queue.", "marketplace");
        });
    }


    /** Review the three-strike content queue and clear false positives. */
    public void showContentFlags() {
        if (!canUseAdmin()) return;
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Content warning queue",
                "Flagged listings, chats and community posts", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView loading = host.ui.bodyText("Loading content warnings...", host.muted, 12);
        loading.setPadding(0, host.ui.dp(18), 0, 0);
        list.addView(loading);
        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("contentFlags")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    list.removeAllViews();
                    if (snap.getDocuments().isEmpty()) {
                        list.addView(host.ui.emptyState("shield", "No content warnings",
                                "Flagged content will appear here with the student and reason."));
                        return;
                    }
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String email = doc.getString("userEmail");
                        String uid = doc.getString("userId");
                        String surface = doc.getString("surface");
                        String content = doc.getString("content");
                        String reasons = doc.getString("reasons");
                        String status = doc.getString("status");
                        Long strike = doc.getLong("strike");
                        if (email == null) email = "";
                        if (uid == null) uid = "";
                        if (surface == null) surface = "app";
                        if (content == null) content = "";
                        if (reasons == null) reasons = "Content rule warning";
                        if (status == null) status = "Warning";
                        final String flagUid = uid;
                        final String flagEmail = email;
                        LinearLayout card = host.ui.card(host.surface(), 14, host.ui.dp(14));
                        card.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
                        LinearLayout top = host.ui.horizontal();
                        top.setGravity(Gravity.CENTER_VERTICAL);
                        top.addView(host.ui.titleText(host.studentName(flagEmail), host.text, 14),
                                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                        int chipBg = status.equalsIgnoreCase("Escalated")
                                ? (host.nightActive ? Color.rgb(64, 34, 34) : Color.rgb(255, 230, 230))
                                : (host.nightActive ? Color.rgb(60, 42, 24) : Color.rgb(255, 239, 220));
                        int chipInk = status.equalsIgnoreCase("Escalated")
                                ? (host.nightActive ? Color.rgb(255, 150, 150) : Color.rgb(180, 30, 30))
                                : (host.nightActive ? Color.rgb(255, 184, 108) : Color.rgb(176, 86, 0));
                        TextView chip = host.ui.chip(status, chipBg, chipInk);
                        chip.setTextSize(9);
                        top.addView(chip);
                        card.addView(top);
                        card.addView(host.ui.bodyText(flagEmail + " / " + surface
                                + " / warning " + (strike == null ? "?" : strike), host.muted, 10));
                        TextView why = host.ui.bodyText(reasons, host.text, 12);
                        why.setPadding(0, host.ui.dp(7), 0, 0);
                        card.addView(why);
                        TextView preview = host.ui.bodyText("\"" + content + "\"", host.muted, 11);
                        preview.setMaxLines(3);
                        preview.setEllipsize(TextUtils.TruncateAt.END);
                        preview.setPadding(0, host.ui.dp(6), 0, 0);
                        card.addView(preview);
                        if (status.equalsIgnoreCase("Escalated") || (strike != null && strike >= 3)) {
                            Button clear = host.ui.secondaryButton("Clear this account's warnings");
                            clear.setOnClickListener(v -> host.ui.dialog()
                                    .setTitle("Clear content warnings?")
                                    .setMessage("This removes the posting pause for " + flagEmail
                                            + " on every device. Use it only after reviewing the content.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Clear warnings", (d, which) -> {
                                        java.util.Map<String, Object> clearDoc = new java.util.HashMap<>();
                                        clearDoc.put("strikes", 0);
                                        clearDoc.put("clearedAt", com.google.firebase.Timestamp.now());
                                        clearDoc.put("clearedBy", host.currentStudentEmail);
                                        clearDoc.put("userEmail", flagEmail);
                                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                .collection("contentSanctions").document(flagUid)
                                                .set(clearDoc, com.google.firebase.firestore.SetOptions.merge());
                                        doc.getReference().update("status", "Cleared");
                                        host.safety.clearContentStrikes(flagEmail);
                                        host.notification.addNotification(flagEmail, "Content review",
                                                "A moderator reviewed your account and cleared the posting pause.",
                                                "messages", "");
                                        host.notification.sendPush(flagEmail, "Content review",
                                                "A moderator reviewed your account and cleared the posting pause.",
                                                "messages", "");
                                        page.dismiss();
                                        showContentFlags();
                                    }).show());
                            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42));
                            clearParams.topMargin = host.ui.dp(8);
                            card.addView(clear, clearParams);
                        }
                        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        cardParams.bottomMargin = host.ui.dp(10);
                        list.addView(card, cardParams);
                    }
                })
                .addOnFailureListener(e -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    list.removeAllViews();
                    list.addView(host.ui.emptyState("flag", "Couldn't load content warnings",
                            "Check the moderator Firestore access and try again."));
                });
    }

    /** Suspended accounts and content-warning appeals are reviewed here. */
    public void showAppeals() {
        if (!canUseAdmin()) return;
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Account appeals", "Suspensions and content-warning reviews", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView loading = host.ui.bodyText("Loading appeals...", host.muted, 12);
        loading.setPadding(0, host.ui.dp(18), 0, 0);
        list.addView(loading);
        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("appeals")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    list.removeAllViews();
                    if (snap.getDocuments().isEmpty()) {
                        list.addView(host.ui.emptyState("checklist", "No appeals",
                                "Suspended students and content-warning appeals will appear here."));
                        return;
                    }
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String uid = doc.getString("uid");
                        String email = doc.getString("email");
                        String name = doc.getString("name");
                        String reason = doc.getString("reason");
                        String status = doc.getString("status");
                        if (status == null || status.isEmpty()) status = "Pending";
                        if (uid == null) uid = doc.getId();
                        if (email == null) email = "";
                        if (name == null || name.isEmpty()) name = host.studentName(email);
                        if (reason == null) reason = "";
                        final String appealUid = uid;
                        final String appealEmail = email;
                        final String appealName = name;
                        final String appealReason = reason;
                        final boolean contentAppeal = reason.toUpperCase(java.util.Locale.ROOT)
                                .contains("CONTENT WARNING APPEAL");
                        LinearLayout card = host.ui.card(host.surface(), 14, host.ui.dp(14));
                        card.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
                        LinearLayout top = host.ui.horizontal();
                        top.setGravity(Gravity.CENTER_VERTICAL);
                        top.addView(host.ui.titleText(appealName, host.text, 14),
                                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                        top.addView(host.ui.statusPill(status));
                        card.addView(top);
                        card.addView(host.ui.bodyText(appealEmail
                                + (contentAppeal ? " / content warnings" : " / suspended account"), host.muted, 11));
                        TextView detail = host.ui.bodyText(contentAppeal
                                ? appealReason.replace("CONTENT WARNING APPEAL:", "").trim() : appealReason,
                                host.text, 12);
                        detail.setPadding(0, host.ui.dp(8), 0, 0);
                        card.addView(detail);
                        long created = 0;
                        Object stamp = doc.get("createdAt");
                        if (stamp instanceof com.google.firebase.Timestamp) {
                            created = ((com.google.firebase.Timestamp) stamp).toDate().getTime();
                        }
                        if (created > 0) {
                            card.addView(host.ui.bodyText("Sent " + host.notification.alertTime(created),
                                    host.muted, 10));
                        }
                        if (status.equalsIgnoreCase("Pending")) {
                            LinearLayout actions = host.ui.horizontal();
                            actions.setPadding(0, host.ui.dp(8), 0, 0);
                            Button approve = host.ui.primaryButton(contentAppeal ? "Clear warnings" : "Restore account");
                            approve.setOnClickListener(v -> approveAppeal(doc, appealUid, appealEmail,
                                    appealName, contentAppeal, page));
                            actions.addView(approve, new LinearLayout.LayoutParams(0, host.ui.dp(44), 1));
                            TextView reject = host.ui.textButton("Reject");
                            reject.setPadding(host.ui.dp(10), 0, host.ui.dp(4), 0);
                            reject.setOnClickListener(v -> rejectAppeal(doc, appealEmail, page));
                            actions.addView(reject, new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(44)));
                            card.addView(actions);
                        }
                        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        cardParams.bottomMargin = host.ui.dp(10);
                        list.addView(card, cardParams);
                    }
                })
                .addOnFailureListener(e -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    list.removeAllViews();
                    list.addView(host.ui.emptyState("flag", "Couldn't load appeals",
                            "Check your moderator Firestore access and try again."));
                });
    }

    private void approveAppeal(com.google.firebase.firestore.DocumentSnapshot doc, String uid,
                               String email, String name, boolean contentAppeal, Dialog page) {
        Runnable afterRestore = () -> {
            java.util.Map<String, Object> fields = new java.util.HashMap<>();
            fields.put("status", "Approved");
            fields.put("reviewedAt", com.google.firebase.Timestamp.now());
            fields.put("reviewedBy", host.currentStudentEmail);
            fields.put("resolutionNote", contentAppeal
                    ? "Content warnings cleared after review." : "Account restored after review.");
            doc.getReference().update(fields);
            if (contentAppeal) {
                java.util.Map<String, Object> clear = new java.util.HashMap<>();
                clear.put("strikes", 0);
                clear.put("clearedAt", com.google.firebase.Timestamp.now());
                clear.put("clearedBy", host.currentStudentEmail);
                clear.put("userEmail", email);
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("contentSanctions").document(uid)
                        .set(clear, com.google.firebase.firestore.SetOptions.merge());
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("contentFlags").whereEqualTo("userId", uid).limit(50).get()
                        .addOnSuccessListener(flags -> {
                            for (com.google.firebase.firestore.DocumentSnapshot flag : flags.getDocuments()) {
                                flag.getReference().update("status", "Cleared",
                                        "clearedAt", com.google.firebase.Timestamp.now(),
                                        "clearedBy", host.currentStudentEmail);
                            }
                        });
                host.safety.clearContentStrikes(email);
            }
            String message = contentAppeal
                    ? "Your content warnings were reviewed and cleared. You can post again."
                    : "Your appeal was approved. Your account can sign in again.";
            host.notification.addNotification(email, "Appeal approved", message, "messages", "");
            host.notification.sendPush(email, "Appeal approved", message, "messages", "");
            Toast.makeText(host, "Appeal approved.", Toast.LENGTH_SHORT).show();
            page.dismiss();
            showAppeals();
        };
        if (contentAppeal) {
            afterRestore.run();
            return;
        }
        new com.campustrade.auth.data.AuthRepository().setAccountStatus(uid, "active",
                new com.campustrade.auth.data.AuthRepository.SimpleCallback() {
                    @Override public void onOk() { afterRestore.run(); }
                    @Override public void onBad(String message) {
                        Toast.makeText(host, "Couldn't restore the account: " + message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void rejectAppeal(com.google.firebase.firestore.DocumentSnapshot doc, String email, Dialog page) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Give the student a short reason. It is saved with the appeal and sent to them.",
                host.muted, 11));
        EditText note = host.ui.listingField(form, "Reason", "Why is the appeal being rejected?",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setMinLines(2);
        android.app.AlertDialog dialog = host.ui.dialog().setTitle("Reject appeal?")
                .setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Reject", null).create();
        dialog.setOnShowListener(shown -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String reason = host.ui.text(note).trim();
                    if (reason.length() < 8) {
                        note.setError("Add at least 8 characters");
                        return;
                    }
                    java.util.Map<String, Object> fields = new java.util.HashMap<>();
                    fields.put("status", "Rejected");
                    fields.put("reviewedAt", com.google.firebase.Timestamp.now());
                    fields.put("reviewedBy", host.currentStudentEmail);
                    fields.put("resolutionNote", reason);
                    doc.getReference().update(fields);
                    host.notification.addNotification(email, "Appeal reviewed",
                            "Your appeal was not approved. Reason: " + reason, "messages", "");
                    host.notification.sendPush(email, "Appeal reviewed",
                            "Your appeal was not approved. Reason: " + reason, "messages", "");
                    dialog.dismiss();
                    page.dismiss();
                    showAppeals();
                }));
        dialog.show();
    }

    public void showFeedback() {
        if (!canUseAdmin()) return;
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Student feedback", "Questions, ideas and support messages", page::dismiss));
        EditText search = host.ui.input("Search feedback by student, type or message", InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        LinearLayout searchWrap = new LinearLayout(host);
        searchWrap.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(8));
        searchWrap.addView(search);
        root.addView(searchWrap);
        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView loading = host.ui.bodyText("Loading feedback...", host.muted, 12);
        loading.setPadding(0, host.ui.dp(20), 0, 0);
        list.addView(loading);
        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        final java.util.ArrayList<com.google.firebase.firestore.DocumentSnapshot> docs = new java.util.ArrayList<>();
        final java.util.Set<String> reviewed = new java.util.HashSet<>();
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            String query = host.ui.text(search).trim().toLowerCase(java.util.Locale.ROOT);
            int shown = 0;
            for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
                String type = doc.getString("type");
                if (type == null || type.isEmpty()) type = "Feedback";
                String message = doc.getString("description");
                if (message == null || message.isEmpty()) message = "No message provided.";
                String userID = doc.getString("userID");
                String name = CloudSync.displayName(host, userID);
                if (name == null || name.isEmpty() || "Seller".equalsIgnoreCase(name)) name = "Student";
                String haystack = type + " " + message + " " + name;
                if (!query.isEmpty() && !haystack.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                shown++;
                long stamp = feedbackTime(doc);
                boolean isReviewed = reviewed.contains(doc.getId()) || Boolean.TRUE.equals(doc.getBoolean("reviewed"));
                final String cardType = type;
                final String cardName = name;
                final String cardMessage = message;
                final boolean cardReviewed = isReviewed;
                final long cardStamp = stamp;
                LinearLayout card = host.ui.card(host.surface(), 14, host.ui.dp(14));
                card.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
                LinearLayout top = host.ui.horizontal();
                top.setGravity(Gravity.CENTER_VERTICAL);
                TextView typeChip = host.ui.chip(type,
                        host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255),
                        host.readableInk(host.blue));
                typeChip.setTextSize(10);
                top.addView(typeChip);
                top.addView(host.ui.space(1, 1), new LinearLayout.LayoutParams(0, 1, 1));
                top.addView(host.ui.statusPill(isReviewed ? "Reviewed" : "New"));
                card.addView(top);
                if (stamp > 0) {
                    TextView when = host.ui.bodyText(feedbackDate(stamp), host.muted, 10);
                    when.setPadding(0, host.ui.dp(6), 0, 0);
                    card.addView(when);
                }
                card.addView(host.ui.bodyText("Submitted by " + name, host.muted, 11));
                TextView summary = host.ui.bodyText(message, host.text, 12);
                summary.setMaxLines(3);
                summary.setEllipsize(TextUtils.TruncateAt.END);
                summary.setPadding(0, host.ui.dp(6), 0, host.ui.dp(4));
                card.addView(summary);
                TextView view = host.ui.textButton("View feedback");
                view.setTextSize(11);
                view.setPadding(0, host.ui.dp(6), 0, 0);
                card.addView(view);
                card.setOnClickListener(v -> {
                    final Dialog[] sheetRef = {null};
                    LinearLayout body = new LinearLayout(host);
                    body.setOrientation(LinearLayout.VERTICAL);
                    body.setPadding(0, host.ui.dp(4), 0, 0);
                    LinearLayout sheetTop = host.ui.horizontal();
                    sheetTop.setGravity(Gravity.CENTER_VERTICAL);
                    TextView sheetType = host.ui.chip(cardType,
                            host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255),
                            host.readableInk(host.blue));
                    sheetType.setTextSize(10);
                    sheetTop.addView(sheetType);
                    sheetTop.addView(host.ui.space(1, 1), new LinearLayout.LayoutParams(0, 1, 1));
                    sheetTop.addView(host.ui.statusPill(cardReviewed ? "Reviewed" : "New"));
                    body.addView(sheetTop);
                    if (cardStamp > 0) {
                        TextView when = host.ui.bodyText(feedbackDate(cardStamp), host.muted, 11);
                        when.setPadding(0, host.ui.dp(8), 0, 0);
                        body.addView(when);
                    }
                    body.addView(host.ui.bodyText("Submitted by " + cardName, host.muted, 12));
                    TextView full = host.ui.bodyText(cardMessage, host.text, 14);
                    full.setPadding(0, host.ui.dp(10), 0, host.ui.dp(10));
                    body.addView(full);
                    if (!cardReviewed) {
                        Button mark = host.ui.primaryButton("Mark as reviewed");
                        mark.setOnClickListener(v2 -> markFeedbackReviewed(doc, reviewed, render, sheetRef));
                        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
                        markParams.topMargin = host.ui.dp(10);
                        body.addView(mark, markParams);
                    } else {
                        body.addView(host.ui.bodyText("This feedback has already been reviewed.", host.muted, 11));
                    }
                    sheetRef[0] = host.ui.showBottomSheet("Feedback", body);
                });
                host.ui.attachPressAnimation(card);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = host.ui.dp(8);
                list.addView(card, cardParams);
            }
            if (shown == 0) {
                list.addView(host.ui.emptyState("chat", query.isEmpty() ? "No feedback yet" : "No feedback matches",
                        query.isEmpty() ? "Student questions and support messages will appear here."
                                : "Try a different student, type or message."));
            }
        };
        search.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) { render[0].run(); }
        });
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("feedback").limit(50).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    docs.clear();
                    docs.addAll(snap.getDocuments());
                    java.util.Collections.sort(docs, (a, b) -> Long.compare(feedbackTime(b), feedbackTime(a)));
                    for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
                        if (Boolean.TRUE.equals(doc.getBoolean("reviewed"))) reviewed.add(doc.getId());
                    }
                    render[0].run();
                })
                .addOnFailureListener(e -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    list.removeAllViews();
                    list.addView(host.ui.emptyState("chat", "Couldn't load feedback",
                            "Check your connection and try again."));
                    Button retry = host.ui.secondaryButton("Try again");
                    retry.setOnClickListener(v -> { page.dismiss(); showFeedback(); });
                    LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
                    retryParams.topMargin = host.ui.dp(8);
                    list.addView(retry, retryParams);
                });
    }

    private void markFeedbackReviewed(com.google.firebase.firestore.DocumentSnapshot doc,
                                      java.util.Set<String> reviewed, Runnable[] render, Dialog[] sheetRef) {
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("reviewed", true);
        fields.put("reviewedAt", com.google.firebase.Timestamp.now());
        fields.put("reviewedBy", host.currentStudentName);
        doc.getReference().update(fields)
                .addOnSuccessListener(v -> {
                    reviewed.add(doc.getId());
                    notifyFeedbackReviewed(doc);
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    render[0].run();
                    Toast.makeText(host, "Feedback marked as reviewed.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(host,
                        "Couldn't mark this feedback. Check your admin access and try again.",
                        Toast.LENGTH_LONG).show());
    }

    private void notifyFeedbackReviewed(com.google.firebase.firestore.DocumentSnapshot doc) {
        String email = doc.getString("userEmail");
        if (email == null || email.isEmpty()) {
            email = CloudSync.emailForUid(host, doc.getString("userID"));
        }
        if (email != null && !email.isEmpty()) {
            sendFeedbackReviewPush(email);
            return;
        }
        String uid = doc.getString("userID");
        if (uid == null || uid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).get()
                .addOnSuccessListener(user -> sendFeedbackReviewPush(user.getString("stuEmail")));
    }

    private void sendFeedbackReviewPush(String email) {
        if (email == null || email.isEmpty()) return;
        host.notification.sendPush(email, "Admin",
                "Your feedback has been received and is being reviewed by the campus team.",
                "messages", "admin@campustrade.local");
    }

    private String feedbackDate(long stamp) {
        return new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(stamp));
    }

    private long feedbackTime(com.google.firebase.firestore.DocumentSnapshot doc) {
        for (String key : new String[] {"createdAt", "created", "timestamp", "time", "date"}) {
            long value = feedbackStamp(doc.get(key));
            if (value > 0) return value;
        }
        return 0;
    }

    private long feedbackStamp(Object value) {
        if (value instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) value).toDate().getTime();
        }
        if (value instanceof java.util.Date) return ((java.util.Date) value).getTime();
        if (value instanceof Number) {
            long millis = ((Number) value).longValue();
            // Old feedback saved seconds, not milliseconds.
            return millis > 0 && millis < 100000000000L ? millis * 1000 : millis;
        }
        return 0;
    }
}