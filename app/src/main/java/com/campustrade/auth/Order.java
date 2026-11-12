package com.campustrade.auth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.math.BigDecimal;
import org.json.JSONArray;
import org.json.JSONObject;
import com.campustrade.auth.data.CloudSync;
import com.campustrade.auth.data.RatingRepository;
import android.text.TextUtils;

// Orders, cancellations, receipts and reviews.
public class Order {

    private final MainActivity host;
    private final java.util.Set<String> cloudMergeRetried = new java.util.HashSet<>();
    private final java.util.Set<String> pushRetried = new java.util.HashSet<>();
    private boolean pickupsPulling = false;
    private long lastPickupsPullAt = 0;

    public Order(MainActivity host) {
        this.host = host;
    }

    interface OrderChange { boolean apply(); }

    // Buyers must review after collection; ratings save here, then sync.
    public void reviewOrder(MainActivity.TransactionRecord order) {
        if (!order.status.equals(MainActivity.TransactionRecord.COMPLETED)
                || (!order.isBuyer(host.currentStudentEmail) && !order.isSeller(host.currentStudentEmail))) return;
        if (hasOrderReview(order)) {
            host.ui.showSimpleMessage("Already reviewed", "You've reviewed this order already."); return;
        }
        boolean required = order.isBuyer(host.currentStudentEmail);
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Review purchase", required ? "Required after collection" : "Optional seller feedback", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        form.addView(host.ui.bodyText(order.itemTitle, host.text, 15));
        form.addView(host.ui.bodyText("Rate each category from 1 (poor) to 5 (excellent). Buyer ratings are required after collection; written feedback is optional.", host.muted, 12));
        Spinner communication = host.ui.listingSelector(form, "Communication", new String[] {"Choose", "1", "2", "3", "4", "5"});
        Spinner accuracy = host.ui.listingSelector(form, "Item accuracy", new String[] {"Choose", "1", "2", "3", "4", "5"});
        Spinner punctuality = host.ui.listingSelector(form, "Punctuality", new String[] {"Choose", "1", "2", "3", "4", "5"});
        EditText comment = host.ui.listingField(form, "Review (optional)", "Your experience", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        comment.setSingleLine(false); comment.setGravity(Gravity.TOP | Gravity.START);
        comment.setPadding(host.ui.dp(14), host.ui.dp(12), host.ui.dp(14), host.ui.dp(12));
        comment.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(120)));
        comment.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button submit = host.ui.primaryButton("Submit review");
        footer.addView(submit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);

        submit.setOnClickListener(v -> {
            int comm = communication.getSelectedItemPosition();
            int acc = accuracy.getSelectedItemPosition();
            int punct = punctuality.getSelectedItemPosition();
            if (comm == 0 || acc == 0 || punct == 0) {
                Toast.makeText(host, "Choose a rating for each category.", Toast.LENGTH_LONG).show();
                return;
            }
            host.featureStore.update(f -> f.review(order.id, order.buyerEmail, order.sellerEmail, order.status.equals(MainActivity.TransactionRecord.COMPLETED), host.currentStudentEmail,
                    comm, acc, punct, host.ui.text(comment)));
            final double stars = (comm + acc + punct) / 3.0;
            final String reviewTarget = order.isBuyer(host.currentStudentEmail) ? order.sellerEmail : order.buyerEmail;
            final String reviewComment = host.ui.text(comment);
            CloudSync.peerUid(host, reviewTarget, new CloudSync.StrCallback() {
                @Override public void onOk(String targetUid) {
                    new RatingRepository().rate(CloudSync.uid(host), targetUid,
                            stars, reviewComment, order.id, new RatingRepository.DoneCallback() {
                                @Override public void onOk() { }
                                @Override public void onBad(String message) {
                                    Toast.makeText(host,
                                            "Review saved on this device, but the cloud copy failed. Give it another go.",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                }
                @Override public void onBad(String message) {
                    Toast.makeText(host,
                            "Review saved on this device, but the reviewer profile couldn't be synced.",
                            Toast.LENGTH_LONG).show();
                }
            });
            host.notification.addNotification(order.isBuyer(host.currentStudentEmail) ? order.sellerEmail : order.buyerEmail,
                    "New purchase review", order.itemTitle, "payment", "");
            host.notification.showCampusNotification("Review submitted", "Your ratings have been saved.", "payment");
            page.dismiss();
            host.showAppScreen("Payments");
        });

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    public boolean hasOrderReview(MainActivity.TransactionRecord order) {
        for (CampusFeatures.Review review : host.featureStore.data.reviews)
            if (review.order.equals(order.id) && review.author.equalsIgnoreCase(host.currentStudentEmail)) return true;
        return false;
    }

    public boolean needsBuyerReview(MainActivity.TransactionRecord order) {
        return order.isBuyer(host.currentStudentEmail) && order.status.equals(MainActivity.TransactionRecord.COMPLETED) && !hasOrderReview(order);
    }

    // Load purchases and sales; only one refresh runs at a time.
    public void cloudPullOrders(final Runnable onNew) {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty()) {
            return;
        }
        if (host.pullingOrders) {
            host.ordersPullQueued = true;
            return;
        }
        host.pullingOrders = true;
        host.ordersPulledChanged = false;
        pullOrdersBy("sellerId", uid, () ->
                pullOrdersBy("buyerId", uid, () -> {
                    host.pullingOrders = false;
                    if (host.isDestroyed() || host.isFinishing()) return;
                    if (host.ordersPulledChanged) {
                        host.ordersPulledChanged = false;
                        saveTransactions();
                        host.market.saveProducts();
                        if (onNew != null) onNew.run();
                    }
                    if (host.ordersPullQueued) {
                        host.ordersPullQueued = false;
                        cloudPullOrders(onNew);
                    }
                }));
    }

    /** Copy a pickup plan so both students see it. */
    public void cloudSavePickup(CampusFeatures.Pickup pickup) {
        if (pickup == null) return;
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("orders").document(pickup.order).get()
                .addOnSuccessListener(orderDoc -> {
                    String buyerId = orderDoc.getString("buyerId");
                    String sellerId = orderDoc.getString("sellerId");
                    if (buyerId == null || sellerId == null) return;
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("orderId", pickup.order);
                    data.put("buyerId", buyerId);
                    data.put("sellerId", sellerId);
                    data.put("buyerEmail", pickup.buyer);
                    data.put("sellerEmail", pickup.seller);
                    data.put("location", pickup.location);
                    data.put("time", pickup.time);
                    data.put("confirmed", new java.util.ArrayList<>(pickup.confirmed));
                    data.put("updatedAt", com.google.firebase.Timestamp.now());
                    db.collection("pickups").document(pickup.order).set(data)
                            .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "pickup save failed: " + e.getMessage()));
                })
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "pickup order lookup failed: " + e.getMessage()));
    }

    /** Load pickup plans at most every few seconds. */
    public void cloudPullPickups() {
        if (pickupsPulling || System.currentTimeMillis() - lastPickupsPullAt < 8000) return;
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        pickupsPulling = true;
        lastPickupsPullAt = System.currentTimeMillis();
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("pickups").whereEqualTo("buyerId", uid).get()
                .addOnSuccessListener(buySnap -> db.collection("pickups").whereEqualTo("sellerId", uid).get()
                        .addOnSuccessListener(sellSnap -> {
                            java.util.LinkedHashMap<String, com.google.firebase.firestore.DocumentSnapshot> docs =
                                    new java.util.LinkedHashMap<>();
                            for (com.google.firebase.firestore.DocumentSnapshot d : buySnap.getDocuments()) docs.put(d.getId(), d);
                            for (com.google.firebase.firestore.DocumentSnapshot d : sellSnap.getDocuments()) docs.put(d.getId(), d);
                            try {
                                host.featureStore.update(f -> {
                                    for (com.google.firebase.firestore.DocumentSnapshot doc : docs.values()) {
                                        CampusFeatures.Pickup target = f.pickup(doc.getId());
                                        if (target == null) {
                                            target = new CampusFeatures.Pickup();
                                            target.order = doc.getId();
                                            f.pickups.add(target);
                                        }
                                        String buyer = doc.getString("buyerEmail");
                                        if (buyer == null || buyer.isEmpty()) buyer = CloudSync.emailForUid(host, doc.getString("buyerId"));
                                        String seller = doc.getString("sellerEmail");
                                        if (seller == null || seller.isEmpty()) seller = CloudSync.emailForUid(host, doc.getString("sellerId"));
                                        target.buyer = buyer == null ? "" : buyer;
                                        target.seller = seller == null ? "" : seller;
                                        target.location = doc.getString("location") == null ? "" : doc.getString("location");
                                        Long time = doc.getLong("time");
                                        target.time = time == null ? 0 : time;
                                        target.confirmed.clear();
                                        java.util.List<String> confirmed = (java.util.List<String>) doc.get("confirmed");
                                        if (confirmed != null) target.confirmed.addAll(confirmed);
                                    }
                                });
                            } catch (RuntimeException e) {
                                android.util.Log.w("CampusTrade", "pickup merge failed: " + e.getMessage());
                            }
                            pickupsPulling = false;
                            if (!host.isDestroyed() && !host.isFinishing() && host.currentTab.equals("Payments")) {
                                host.showAppScreen("Payments");
                            }
                        })
                        .addOnFailureListener(e -> {
                            android.util.Log.w("CampusTrade", "pickup seller pull failed: " + e.getMessage());
                            pickupsPulling = false;
                        }))
                .addOnFailureListener(e -> {
                    android.util.Log.w("CampusTrade", "pickup buyer pull failed: " + e.getMessage());
                    pickupsPulling = false;
                });
    }

    /** Load one side of the order list and merge each order. */
    public void pullOrdersBy(String field, String uid, Runnable done) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo(field, uid).limit(30).get()
                .addOnSuccessListener(snap -> {
                    if (!host.isDestroyed() && !host.isFinishing()) {
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) mergeCloudOrder(doc);
                    }
                    done.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.d("CampusTrade", "order pull (" + field + ") failed: " + e.getMessage());
                    done.run();
                });
    }

    /** Add or update the local order; alert when a decision changes. */
    public void mergeCloudOrder(com.google.firebase.firestore.QueryDocumentSnapshot doc) {
        String cloudStatus = localOrderStatus(doc.getString("status"));
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (!order.id.equals(doc.getId())) continue;
            String cloudRequester = doc.getString("cancellationRequester") == null ? "" : doc.getString("cancellationRequester");
            String cloudReason = doc.getString("cancellationReason") == null ? "" : doc.getString("cancellationReason");
            String cloudOutcome = doc.getString("cancellationOutcome") == null ? "" : doc.getString("cancellationOutcome");
            Long cancellationTime = doc.getLong("cancellationTime");
            long cloudTime = cancellationTime == null ? 0 : cancellationTime;
            Long payoutTime = doc.getLong("payoutRequestedAt");
            long cloudPayout = payoutTime == null ? 0 : payoutTime;
            String cloudPayoutMethod = doc.getString("payoutMethod");
            String cloudPayoutSchedule = doc.getString("payoutSchedule");
            boolean payoutChanged = cloudPayout > 0 && (
                    order.payoutRequestedAt != cloudPayout
                    || (cloudPayoutMethod != null && !cloudPayoutMethod.equals(order.payoutMethod))
                    || (cloudPayoutSchedule != null && !cloudPayoutSchedule.equals(order.payoutSchedule)));
            boolean statusChanged = !order.status.equals(cloudStatus);
            boolean requestChanged = !order.cancellationRequester.equals(cloudRequester)
                    || !order.cancellationReason.equals(cloudReason)
                    || !order.cancellationOutcome.equals(cloudOutcome)
                    || order.cancellationTime != cloudTime;
            // Only update local orders that actually changed.
            if (!statusChanged && !requestChanged && !payoutChanged) return;
            // Requests only move forward; an old read must not undo a local decision.
            if (order.status.equals(MainActivity.TransactionRecord.HELD) && cloudStatus.equals(MainActivity.TransactionRecord.REQUESTED)) return;
            if (order.status.equals(MainActivity.TransactionRecord.COMPLETED) && !cloudStatus.equals(MainActivity.TransactionRecord.COMPLETED)) return;
            if (order.status.equals(MainActivity.TransactionRecord.CANCELLED) && !cloudStatus.equals(MainActivity.TransactionRecord.CANCELLED)) return;
            String oldStatus = order.status;
            order.status = cloudStatus;
            order.cancellationRequester = cloudRequester;
            order.cancellationReason = cloudReason;
            order.cancellationOutcome = cloudOutcome;
            order.cancellationTime = cloudTime;
            if (cloudPayout > 0) {
                order.payoutRequestedAt = cloudPayout;
                if (cloudPayoutMethod != null && !cloudPayoutMethod.isEmpty()) order.payoutMethod = cloudPayoutMethod;
                if (cloudPayoutSchedule != null && !cloudPayoutSchedule.isEmpty()) order.payoutSchedule = cloudPayoutSchedule;
            }
            host.ordersPulledChanged = true;
            if (statusChanged) {
                syncListingFor(order);
                notifyOrderChange(order, oldStatus);
            } else if (requestChanged) {
                notifyCancellationFieldChange(order);
            }
            return;
        }

        // Rebuild an order that only exists on another device.
        String listingId = doc.getString("listingId");
        String priceText = doc.get("price") == null ? "" : String.valueOf(doc.get("price"));
        if (priceText.isEmpty()) return;
        String method = doc.getString("method");
        if (method == null || method.isEmpty()) method = "Debit card";
        String uid = CloudSync.uid(host);
        String buyerUid = doc.getString("buyerId") == null ? "" : doc.getString("buyerId");
        String sellerUid = doc.getString("sellerId") == null ? "" : doc.getString("sellerId");
        String buyerEmail = doc.getString("buyerEmail");
        if (buyerEmail == null || buyerEmail.isEmpty()) buyerEmail = CloudSync.emailForUid(host, buyerUid);
        if (buyerEmail == null || buyerEmail.isEmpty()) buyerEmail = CloudSync.displayName(host, buyerUid);
        String sellerEmail = doc.getString("sellerEmail");
        if (sellerEmail == null || sellerEmail.isEmpty()) sellerEmail = CloudSync.emailForUid(host, sellerUid);
        if (sellerEmail == null || sellerEmail.isEmpty()) sellerEmail = CloudSync.displayName(host, sellerUid);
        if (buyerUid.equals(uid)) buyerEmail = host.currentStudentEmail;
        if (sellerUid.equals(uid)) sellerEmail = host.currentStudentEmail;
        if (buyerEmail == null || buyerEmail.isEmpty() || sellerEmail == null || sellerEmail.isEmpty()) return;
        String buyerName = doc.getString("buyerName");
        if (buyerName == null || buyerName.isEmpty()) buyerName = CloudSync.displayName(host, buyerUid);
        String sellerName = doc.getString("sellerName");
        String localProductId = listingId == null ? "" : CloudSync.localId(host, listingId);
        MainActivity.Product product = null;
        for (MainActivity.Product candidate : host.products) {
            if (candidate.id.equals(localProductId)) { product = candidate; break; }
        }
        String title = doc.getString("title");
        if (title == null || title.isEmpty()) title = product != null ? product.title : "Listing";
        String category = doc.getString("category");
        if (category == null || category.isEmpty()) category = product != null ? product.category : "Other";
        String paidAt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
        if (doc.getTimestamp("createdAt") != null) {
            paidAt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(doc.getTimestamp("createdAt").toDate());
        }
        MainActivity.TransactionRecord record;
        try {
            record = new MainActivity.TransactionRecord(doc.getId(), title, priceText, method, buyerName,
                    doc.getString("buyerPhone") == null ? "" : doc.getString("buyerPhone"),
                    doc.getString("reference") == null ? "" : doc.getString("reference"),
                    paidAt, sellerEmail, buyerEmail, category, cloudStatus,
                    product != null ? product.id : (listingId == null ? "" : listingId));
        } catch (IllegalArgumentException e) {
            android.util.Log.d("CampusTrade", "order pull skipped " + doc.getId() + ": " + e.getMessage());
            return;
        }
        if (sellerName != null && !sellerName.isEmpty()) record.sellerName = sellerName;
        if (doc.getTimestamp("createdAt") != null) record.createdTime = doc.getTimestamp("createdAt").toDate().getTime();
        record.cancellationRequester = doc.getString("cancellationRequester") == null ? "" : doc.getString("cancellationRequester");
        record.cancellationReason = doc.getString("cancellationReason") == null ? "" : doc.getString("cancellationReason");
        record.cancellationOutcome = doc.getString("cancellationOutcome") == null ? "" : doc.getString("cancellationOutcome");
        Long pulledCancellationTime = doc.getLong("cancellationTime");
        record.cancellationTime = pulledCancellationTime == null ? 0 : pulledCancellationTime;
        Long pulledPayoutAt = doc.getLong("payoutRequestedAt");
        record.payoutRequestedAt = pulledPayoutAt == null ? 0 : pulledPayoutAt;
        if (doc.getString("payoutMethod") != null) record.payoutMethod = doc.getString("payoutMethod");
        if (doc.getString("payoutSchedule") != null) record.payoutSchedule = doc.getString("payoutSchedule");
        host.transactions.add(record);
        host.ordersPulledChanged = true;
        syncListingFor(record);
        // Notify the seller only when the request still needs them.
        if (record.isSeller(host.currentStudentEmail) && record.status.equals(MainActivity.TransactionRecord.REQUESTED)) {
            host.notification.addNotification(host.currentStudentEmail, "New purchase request",
                    record.itemTitle, "payment", "");
            host.notification.showCampusNotification("New purchase request",
                    record.itemTitle + ". Open Orders to approve or decline.", "payment");
        }
    }

    /** Copy the order status to the listing and, for owners, to Firebase. */
    public void syncListingFor(MainActivity.TransactionRecord order) {
        for (MainActivity.Product product : host.products) {
            if (!product.id.equals(order.productId)) continue;
            if (!applyListingState(product)) return;
            host.market.saveProducts();
            host.market.cloudMirrorListing(product, false);
            return;
        }
    }

    /** Sync listing statuses with saved orders. */
    public void reconcileListingStates() {
        boolean changed = false;
        for (MainActivity.Product product : host.products) {
            if (applyListingState(product)) changed = true;
        }
        if (changed) host.market.saveProducts();
    }

    /** Apply the order status unless the seller chose one. */
    public boolean applyListingState(MainActivity.Product product) {
        if (product.status.equals("Paused") || product.status.equals("Draft")) return false;
        String next = listingStateFromOrders(product.id);
        if (next.equals(product.status)) return false;
        // Sold can also be set manually; never reopen it from an old cancelled order.
        if (next.equals("Available") && product.status.equals("Sold")) return false;
        product.status = next;
        return true;
    }

    /** Completed wins, then confirmed, then requested. Otherwise it is free. */
    public String listingStateFromOrders(String productId) {
        boolean requested = false;
        String confirmed = "";
        for (MainActivity.TransactionRecord record : host.transactions) {
            if (!record.productId.equals(productId)) continue;
            if (record.status.equals(MainActivity.TransactionRecord.COMPLETED)) return "Sold";
            if (record.status.equals(MainActivity.TransactionRecord.HELD)) confirmed = record.listingState();
            else if (record.status.equals(MainActivity.TransactionRecord.REQUESTED)) requested = true;
        }
        if (!confirmed.isEmpty()) return confirmed;
        return requested ? "Reserved" : "Available";
    }

    public boolean hasOrdersFor(String productId) {
        for (MainActivity.TransactionRecord record : host.transactions) if (record.productId.equals(productId)) return true;
        return false;
    }

    // Wording depends on whether the student is the buyer or seller.
    public void notifyOrderChange(MainActivity.TransactionRecord order, String oldStatus) {
        String title;
        if (order.status.equals(MainActivity.TransactionRecord.HELD) && order.isBuyer(host.currentStudentEmail)) {
            title = "Purchase confirmed";
        } else if (order.status.equals(MainActivity.TransactionRecord.CANCELLED)) {
            if (order.isSeller(host.currentStudentEmail) && oldStatus.equals(MainActivity.TransactionRecord.REQUESTED)) {
                title = "Request withdrawn";
            } else if (order.isBuyer(host.currentStudentEmail)
                    && ("Declined by seller".equals(order.cancellationOutcome)
                        || "Another request was accepted".equals(order.cancellationOutcome))) {
                title = "Purchase declined";
            } else {
                title = "Order cancelled";
            }
        } else if (order.status.equals(MainActivity.TransactionRecord.COMPLETED) && order.isSeller(host.currentStudentEmail)) {
            title = "Order completed";
        } else {
            return;
        }
        String body;
        if (order.status.equals(MainActivity.TransactionRecord.HELD)) {
            body = order.itemTitle + " is confirmed. Arrange collection.";
        } else if (order.status.equals(MainActivity.TransactionRecord.COMPLETED)) {
            body = order.itemTitle + " was collected.";
        } else if (order.isSeller(host.currentStudentEmail)) {
            body = "Approved".equals(order.cancellationOutcome)
                    ? order.itemTitle + " cancellation was approved."
                    : order.itemTitle + " was withdrawn by the buyer.";
        } else if ("Declined by seller".equals(order.cancellationOutcome)
                || "Another request was accepted".equals(order.cancellationOutcome)) {
            body = order.itemTitle + " wasn't confirmed by the seller.";
        } else {
            body = order.itemTitle + " is cancelled.";
        }
        host.notification.addNotification(host.currentStudentEmail, title, body, "payment", "");
        host.notification.showCampusNotification(title, body, "payment");
    }

    /** Tell the other student about a cancellation change. */
    public void notifyCancellationFieldChange(MainActivity.TransactionRecord order) {
        String title;
        if (!order.cancellationRequester.isEmpty()
                && !order.cancellationRequester.equalsIgnoreCase(host.currentStudentEmail)
                && order.status.equals(MainActivity.TransactionRecord.HELD)) {
            title = "Cancellation requested";
        } else if ("Declined".equals(order.cancellationOutcome)) {
            title = "Cancellation declined";
        } else if ("Withdrawn".equals(order.cancellationOutcome)) {
            title = "Cancellation request withdrawn";
        } else {
            return;
        }
        String body = order.itemTitle;
        host.notification.addNotification(host.currentStudentEmail, title, body, "payment", "");
        host.notification.showCampusNotification(title, body, "payment");
    }

    // Unknown cloud statuses fall back to HELD instead of breaking the list.
    public static String localOrderStatus(String cloud) {
        if (cloud == null) return MainActivity.TransactionRecord.HELD;
        if (cloud.equalsIgnoreCase("REQUESTED")) return MainActivity.TransactionRecord.REQUESTED;
        if (cloud.equalsIgnoreCase("COMPLETED")) return MainActivity.TransactionRecord.COMPLETED;
        if (cloud.equalsIgnoreCase("CANCELLED")) return MainActivity.TransactionRecord.CANCELLED;
        return MainActivity.TransactionRecord.HELD;
    }

    public static String cloudOrderStatus(String local) {
        if (local.equals(MainActivity.TransactionRecord.REQUESTED)) return "REQUESTED";
        if (local.equals(MainActivity.TransactionRecord.COMPLETED)) return "COMPLETED";
        if (local.equals(MainActivity.TransactionRecord.CANCELLED)) return "CANCELLED";
        return "HELD";
    }

    /** Create the cloud order with the fields Firebase rules expect. */
    public void cloudPushOrder(MainActivity.TransactionRecord order, MainActivity.Product product) {
        // A request cancelled here before the first upload
        // must not be re-created by an old retry.
        if (!MainActivity.TransactionRecord.REQUESTED.equals(order.status)) return;
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        final String listingId = CloudSync.remoteId(host, product.id);
        if (listingId.isEmpty()) {
            // The buyer's phone may not have the cloud link yet.
            // Refresh Market once, then retry.
            if (cloudMergeRetried.add(order.id)) {
                host.market.cloudMergeMarket();
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> cloudPushOrder(order, product), 2500);
                return;
            }
            host.ui.showCloudRetry("Request saved on this device",
                    "This listing has no cloud copy yet, so the seller may not see the request. "
                            + "Publish/sync the listing, then retry.",
                    () -> cloudPushOrder(order, product));
            return;
        }
        final double listPrice;
        try {
            listPrice = Double.parseDouble(product.price);
        } catch (NumberFormatException e) {
            host.ui.showCloudRetry("Request saved on this device",
                    "The listing price couldn't be read, so the seller can't be notified.",
                    () -> cloudPushOrder(order, product));
            return;
        }
        // Look up the seller ID from their email first.
        CloudSync.peerUid(host, product.sellerEmail, new CloudSync.StrCallback() {
            @Override public void onOk(String sellerUid) {
                java.util.Map<String, Object> doc = new java.util.HashMap<>();
                doc.put("listingId", listingId);
                doc.put("buyerId", uid);
                doc.put("sellerId", sellerUid);
                doc.put("title", order.itemTitle);
                doc.put("price", order.value().doubleValue());
                doc.put("listPrice", listPrice);
                doc.put("method", order.method);
                doc.put("buyerEmail", order.buyerEmail);
                doc.put("sellerEmail", order.sellerEmail);
                doc.put("buyerName", order.buyerName);
                doc.put("buyerPhone", order.buyerPhone);
                doc.put("reference", order.reference);
                doc.put("category", order.category);
                doc.put("status", "REQUESTED");
                doc.put("createdAt", com.google.firebase.Timestamp.now());
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("orders").document(order.id).set(doc)
                        .addOnFailureListener(e -> {
                            android.util.Log.d("CampusTrade", "order push failed: " + e.getMessage());
                            // A just-published listing may not be in Firebase yet, so refresh once and retry.
                            if (pushRetried.add(order.id)) {
                                host.market.cloudMergeMarket();
                                new Handler(Looper.getMainLooper()).postDelayed(
                                        () -> cloudPushOrder(order, product), 3000);
                                return;
                            }
                            host.ui.showCloudRetry("Request saved on this device",
                                    "The seller may not see this request until the cloud copy succeeds. "
                                            + e.getMessage(),
                                    () -> cloudPushOrder(order, product));
                        });
            }
            @Override public void onBad(String message) {
                host.ui.showCloudRetry("Request saved on this device",
                        "Couldn't reach the seller's account. " + message,
                        () -> cloudPushOrder(order, product));
            }
        });
    }

    /** Send a status decision to Firebase. */
    public void cloudUpdateOrder(MainActivity.TransactionRecord order) {
        java.util.Map<String, Object> update = new java.util.HashMap<>();
        update.put("status", cloudOrderStatus(order.status));
        update.put("updatedAt", com.google.firebase.Timestamp.now());
        // Only send cancellation fields when there is a cancellation.
        // Empty fields on a normal approval would look like a cancellation.
        if (order.cancellationTime > 0 || !order.cancellationOutcome.isEmpty()) {
            update.put("cancellationRequester", order.cancellationRequester);
            update.put("cancellationReason", order.cancellationReason);
            update.put("cancellationOutcome", order.cancellationOutcome);
            update.put("cancellationTime", order.cancellationTime);
        }
        // Sync payout details only when a request exists.
        if (order.payoutRequestedAt > 0) {
            update.put("payoutRequestedAt", order.payoutRequestedAt);
            update.put("payoutMethod", order.payoutMethod);
            update.put("payoutSchedule", order.payoutSchedule);
        }
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("orders")
                .document(order.id).update(update)
                .addOnFailureListener(e -> {
                    android.util.Log.d("CampusTrade", "order update failed: " + e.getMessage());
                    host.ui.showCloudRetry("Order saved on this device",
                            "The other student may not see this change until the cloud copy succeeds. "
                                    + e.getMessage(),
                            () -> cloudUpdateOrder(order));
                });
    }

    // Checkout checks the order and never stores payment details.
    public void showPaymentDialog(MainActivity.Product product) {
        if (host.market.ownsListing(product)) {
            Toast.makeText(host, "That's your own listing.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (product.hidden || !product.status.equals("Available")) {
            showTransactionHistory();
            return;
        }
        // Use the reserved amount when the current student holds the reservation.
        CampusFeatures.Offer reservation = host.featureStore.data.reservation(product.id, System.currentTimeMillis());
        if (reservation != null && !reservation.buyer.equals(host.currentStudentEmail)) {
            host.ui.showSimpleMessage("Item reserved", "Someone else has this reserved until " + host.messageTime(reservation.expires)); return;
        }
        final String quotedPrice = reservation == null ? product.price : reservation.amount;
        final boolean freeItem = new BigDecimal(quotedPrice).signum() == 0;
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(18), host.ui.dp(6), host.ui.dp(18), 0);

        TextView summary = host.ui.titleText(product.title + " - " + (freeItem ? "Free" : "R " + quotedPrice), host.text, 16);
        summary.setPadding(0, 0, 0, host.ui.dp(6));
        form.addView(summary);
        form.addView(host.ui.bodyText("Choose how you will pay the seller. CampusTrade does not process or hold money.", host.muted, 12));

        form.addView(host.ui.label("Payment preference"));
        // Only the chosen payment option is kept; card fields are ignored.
        final MainActivity.PaymentMethod[] selectedMethod = {preferredPaymentMethod()};
        LinearLayout cardBox = host.ui.workflowForm();
        cardBox.addView(host.ui.label("Card details"));
        cardBox.addView(host.ui.bodyText("Enter the card details for this payment. Card details aren't stored on your device.", host.muted, 12));
        EditText cardholder = host.ui.listingField(cardBox, "Name on card", "Cardholder name", InputType.TYPE_CLASS_TEXT);
        EditText cardNumber = host.ui.listingField(cardBox, "Card number", "1234 5678 9012 3456", InputType.TYPE_CLASS_PHONE);
        EditText cardExpiry = host.ui.listingField(cardBox, "Expiry (MM/YY)", "Tap to choose a month", InputType.TYPE_CLASS_DATETIME);
        cardExpiry.setFocusable(false);
        cardExpiry.setClickable(true);
        cardExpiry.setOnClickListener(v -> host.ui.showCardExpiryPicker(cardExpiry));
        EditText cardCode = host.ui.listingField(cardBox, "Security code", "CVV", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        cardNumber.setFilters(new InputFilter[] {new InputFilter.LengthFilter(16)});
        cardExpiry.setFilters(new InputFilter[] {new InputFilter.LengthFilter(5)});
        cardCode.setFilters(new InputFilter[] {new InputFilter.LengthFilter(3)});
        for (EditText field : new EditText[] {cardholder, cardNumber, cardExpiry, cardCode}) {
            field.setSaveEnabled(false);
            if (Build.VERSION.SDK_INT >= 26) field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        LinearLayout brandsRow = cardBrandsRow();
        form.addView(brandsRow);
        LinearLayout eftBox = host.ui.workflowForm();
        eftBox.addView(host.ui.label("Select your bank"));
        final Spinner bankSpinner = host.ui.listingSelector(eftBox, "Bank", host.SA_BANKS);
        eftBox.addView(host.ui.bodyText("Select your bank to complete the transfer. Payment is confirmed before collection.", host.muted, 12));
        form.addView(eftBox);
        form.addView(cardBox);
        Runnable updateCard = () -> {
            // Payment is settled directly between students;
            // the app collects no card or bank details.
            cardBox.setVisibility(View.GONE);
            brandsRow.setVisibility(View.GONE);
            eftBox.setVisibility(View.GONE);
            cardholder.setText(""); cardNumber.setText(""); cardExpiry.setText(""); cardCode.setText("");
        };
        if (freeItem) form.addView(host.ui.bodyText("Free collection / no payment required", host.readableInk(host.green), 14));
        else addPaymentChoices(form, selectedMethod, updateCard);
        updateCard.run();
        form.addView(host.ui.label("Order total"));
        addPaymentBreakdown(form, new BigDecimal(quotedPrice));
        form.addView(host.ui.label("Buyer details"));
        EditText buyerNameInput = host.ui.dialogInput("Buyer name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        buyerNameInput.setText(host.currentStudentName);
        EditText buyerPhoneInput = host.ui.dialogInput("Phone number", InputType.TYPE_CLASS_PHONE);
        EditText referenceInput = host.ui.dialogInput("Collection note (optional)", InputType.TYPE_CLASS_TEXT);
        form.addView(buyerNameInput);
        form.addView(buyerPhoneInput);
        form.addView(referenceInput);
        CheckBox preferMethod = new CheckBox(host);
        preferMethod.setText("Use this as my payment preference");
        preferMethod.setTextSize(12);
        host.ui.themedToggle(preferMethod);
        if (!freeItem) form.addView(preferMethod);

        form.addView(host.ui.label("Collection"));
        form.addView(host.ui.bodyText(product.location + " / " + product.delivery, host.text, 13));
        form.addView(host.ui.bodyText("Arrange pickup with the seller after placing your order. Pay them directly using your agreed method.", host.muted, 12));
        // Checkout is full screen; the action stays at the bottom.
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Checkout", product.title, page::dismiss));
        ScrollView checkoutScroll = new ScrollView(host);
        checkoutScroll.addView(form);
        root.addView(checkoutScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button pay = host.ui.primaryButton(freeItem ? "Request item" : "Send request");
        footer.addView(pay, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);
        page.setContentView(root);
        pay.setOnClickListener(v -> {
            String method = freeItem ? "Free collection" : selectedMethod[0].label;
            String buyer = host.ui.text(buyerNameInput).trim();
            String phone = host.ui.text(buyerPhoneInput).trim();
            String reference = host.ui.text(referenceInput).trim();
            if (buyer.length() < 2) {
                buyerNameInput.setError("Enter the buyer name");
                return;
            }
            if (!validPaymentPhone(phone)) {
                buyerPhoneInput.setError("Enter a valid phone number");
                checkoutScroll.smoothScrollTo(0, buyerPhoneInput.getTop());
                buyerPhoneInput.requestFocus();
                return;
            }
            if (!freeItem && selectedMethod[0].usesCard()) {
                String error = MainActivity.CardValidator.validationError(host.ui.text(cardholder), host.ui.text(cardNumber), host.ui.text(cardExpiry), host.ui.text(cardCode), System.currentTimeMillis());
                if (error != null) { host.ui.showSimpleMessage("Check card details", error); return; }
            }
            String bank = "";
            String finalReference = reference;
            showCheckoutConfirm(product, quotedPrice, method, bank, buyer, phone, finalReference, () -> {
                // Brief wait while the order is saved.
                pay.setEnabled(false);
                pay.setText("Sending...");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (host.isDestroyed() || !page.isShowing()) return;
                    // Check the listing again in case another student took it.
                    if (product.hidden || !product.status.equals("Available")) {
                        page.dismiss();
                        showTransactionHistory();
                        return;
                    }
                    CampusFeatures.Offer active = host.featureStore.data.reservation(product.id, System.currentTimeMillis());
                    String currentQuote = active == null ? product.price : active.amount;
                    if ((active != null && !active.buyer.equals(host.currentStudentEmail)) || !currentQuote.equals(quotedPrice)) {
                        pay.setEnabled(true); pay.setText("Try again"); host.ui.showSimpleMessage("Offer changed", "Reopen checkout to review the current price or reservation."); return;
                    }
                    // New orders are requested; they become held after seller approval.
                    String transactionId = "CT-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
                    String paidAt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
                    MainActivity.TransactionRecord order = new MainActivity.TransactionRecord(transactionId, product.title, quotedPrice,
                            method, buyer, phone, finalReference, paidAt, product.sellerEmail,
                            host.currentStudentEmail, product.category, MainActivity.TransactionRecord.REQUESTED, product.id);
                    order.sellerName = host.studentName(product.sellerEmail);
                    order.createdTime = System.currentTimeMillis();
                    host.transactions.add(order);
                    // Remove the order if saving fails so the screen matches storage.
                    if (!saveTransactions()) {
                        host.transactions.remove(order);
                        pay.setEnabled(true);
                        pay.setText(freeItem ? "Request item" : "Send request");
                        Toast.makeText(host, "The request didn't save. Give it another go.", Toast.LENGTH_LONG).show();
                        host.notification.showCampusNotification("Request not saved", "Please retry checkout.", "payment");
                        return;
                    }
                    product.status = order.listingState();
                    host.market.saveProducts();
                    host.market.cloudMirrorListing(product, false);
                    // Send the seller an Inbox message and a push.
                    host.inbox.localMessage(product.sellerEmail, "New purchase request " + transactionId + " for "
                            + product.title + " (R" + quotedPrice + ", " + order.listingState()
                            + "). Open Orders & transactions to approve or decline.");
                    cloudPushOrder(order, product);
                    try { host.featureStore.update(f -> f.closeOffers(product.id)); } catch (RuntimeException e) { Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show(); }
                    page.dismiss();
                    host.paymentsReturnTab = "Market";
                    host.buyingOrders = true;
                    if (!freeItem && preferMethod.isChecked()) host.settingsPrefs().edit().putString("payment_method", method).apply();
                    host.showAppScreen("Payments");
                    host.notification.showCampusNotification("Request sent", "Your request is waiting for the seller to confirm.", "payment");
                    host.notification.addNotification(product.sellerEmail, "Purchase request", product.title + " - " + transactionId, "payment", "");
                    host.notification.sendPush(product.sellerEmail, "Purchase request", product.title + " - " + transactionId, "payment", "");
                    // Show the receipt if the student's setting is on.
                    if (host.settingsPrefs().getBoolean("payment_receipt_auto", true)) showReceipt(order);
                }, 900);
            });
        });
        page.setOnDismissListener(d -> { cardholder.setText(""); cardNumber.setText(""); cardExpiry.setText(""); cardCode.setText(""); });
        page.show();
        Window checkoutWindow = page.getWindow();
        if (checkoutWindow != null) {
            checkoutWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            checkoutWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            checkoutWindow.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    public MainActivity.PaymentMethod preferredPaymentMethod() {
        MainActivity.PaymentMethod saved = MainActivity.PaymentMethod.fromLabel(
                host.settingsPrefs().getString("payment_method", "Cash on collection"));
        return saved.offered() ? saved : MainActivity.PaymentMethod.CASH;
    }

    /** Card logos drawn by the app. */
    public LinearLayout cardBrandsRow() {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(4), 0, host.ui.dp(4));
        View mastercard = new View(host) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                int w = getWidth(), h = getHeight();
                paint.setColor(Color.rgb(235, 0, 27));
                canvas.drawCircle(w * 0.36f, h * 0.5f, h * 0.32f, paint);
                paint.setColor(Color.rgb(255, 95, 0));
                canvas.drawCircle(w * 0.64f, h * 0.5f, h * 0.32f, paint);
            }
        };
        mastercard.setContentDescription("Mastercard mark");
        row.addView(mastercard, new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(28)));
        TextView visa = new TextView(host);
        visa.setText("VISA");
        visa.setTextColor(Color.rgb(26, 31, 113));
        visa.setTypeface(Typeface.DEFAULT, Typeface.BOLD | Typeface.ITALIC);
        visa.setTextSize(15);
        visa.setPadding(host.ui.dp(10), host.ui.dp(3), host.ui.dp(10), host.ui.dp(3));
        visa.setBackground(host.ui.round(Color.WHITE, 4));
        row.addView(visa);
        TextView testBadge = new TextView(host);
        testBadge.setText("SECURE");
        testBadge.setTextColor(Color.WHITE);
        testBadge.setTextSize(10);
        testBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        testBadge.setGravity(Gravity.CENTER);
        testBadge.setPadding(host.ui.dp(8), host.ui.dp(3), host.ui.dp(8), host.ui.dp(3));
        testBadge.setBackground(host.ui.round(Color.rgb(217, 139, 0), 999));
        row.addView(testBadge);
        row.addView(host.ui.bodyText("Secure checkout.", host.muted, 11));
        return row;
    }

    public void addPaymentChoices(LinearLayout parent, MainActivity.PaymentMethod[] selected) {
        addPaymentChoices(parent, selected, () -> { });
    }

    public void addPaymentChoices(LinearLayout parent, MainActivity.PaymentMethod[] selected, Runnable onChange) {
        ArrayList<RadioButton> choices = new ArrayList<>();
        ArrayList<LinearLayout> cards = new ArrayList<>();
        for (MainActivity.PaymentMethod method : MainActivity.PaymentMethod.values()) {
            if (!method.offered()) continue;
            LinearLayout option = new LinearLayout(host);
            option.setOrientation(LinearLayout.VERTICAL);
            option.setPadding(host.ui.dp(12), host.ui.dp(6), host.ui.dp(12), host.ui.dp(10));
            RadioButton radio = new RadioButton(host);
            radio.setText(method.label);
            radio.setTextSize(14);
            host.ui.themedToggle(radio);
            radio.setChecked(method == selected[0]);
            choices.add(radio);
            cards.add(option);
            option.addView(radio, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44)));
            TextView description = host.ui.bodyText(method.description, host.muted, 12);
            description.setPadding(host.ui.dp(12), 0, host.ui.dp(4), 0);
            option.addView(description);
            View.OnClickListener choose = v -> {
                selected[0] = method;
                for (int i = 0; i < choices.size(); i++) {
                    boolean active = choices.get(i) == radio;
                    choices.get(i).setChecked(active);
                    stylePaymentOption(cards.get(i), active);
                }
                onChange.run();
            };
            radio.setOnClickListener(choose);
            option.setOnClickListener(choose);
            stylePaymentOption(option, method == selected[0]);
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            optionParams.bottomMargin = host.ui.dp(10);
            parent.addView(option, optionParams);
        }
    }

    private void stylePaymentOption(LinearLayout option, boolean selected) {
        int background = selected
                ? (host.nightActive ? Color.rgb(54, 36, 33) : Color.rgb(255, 242, 237))
                : host.surface();
        option.setBackground(host.ui.strokedRound(background, 12, selected ? host.blue : host.line));
    }

    public void choosePreferredPayment() {
        MainActivity.PaymentMethod[] selected = {preferredPaymentMethod()};
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Choose how you will settle payment directly with the other student. CampusTrade does not process or hold money.", host.muted, 12));
        form.addView(host.ui.bodyText("The highlighted option is your current preference.", host.muted, 11));
        addPaymentChoices(form, selected);
        Button save = host.ui.primaryButton("Save preference");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        saveParams.topMargin = host.ui.dp(6);
        form.addView(save, saveParams);
        Dialog sheet = host.ui.showBottomSheet("Payment preference", form);
        save.setOnClickListener(v -> {
            host.settingsPrefs().edit().putString("payment_method", selected[0].label).apply();
            sheet.dismiss();
            host.showAppScreen("Payments");
        });
    }

    public void paymentLine(LinearLayout parent, String label, String value) {
        LinearLayout row = host.ui.horizontal();
        row.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
        TextView heading = host.ui.bodyText(label, host.muted, 12);
        row.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView detail = host.ui.bodyText(value, host.text, 13);
        detail.setGravity(Gravity.END);
        detail.setPadding(host.ui.dp(8), 0, 0, 0);
        row.addView(detail, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row);
    }

    // The app does not charge fees or arrange delivery.
    public void addPaymentBreakdown(LinearLayout parent, BigDecimal amount) {
        paymentLine(parent, "Item price", rand(amount));
        paymentLine(parent, "Total (ZAR)", rand(amount));
        parent.addView(host.ui.bodyText("No platform fee is added. Delivery and payment are arranged directly with the other student.", host.muted, 11));
    }

    static boolean validPaymentPhone(String phone) {
        String compact = phone.replaceAll("[\\s()-]", "");
        return compact.matches("\\+?[0-9]{8,15}");
    }

    public void showPaymentInformation(String topic) {
        if (topic.equals("Payment preference") || topic.equals("Payment options")) { choosePreferredPayment(); return; }
        if (topic.equals("Collection arrangements") || topic.equals("Collection & payouts")) { showPayoutPreferences(); return; }
        if (topic.equals("Cancellations & help") || topic.equals("Cancellations")) { showCancellationCenter(null); return; }
        host.ui.showSimpleMessage("Order help", "Contact the other student from your order first. If you still need help, use Settings > Help & support. Never share passwords, PINs or one-time codes.");
    }

    public ArrayList<MainActivity.TransactionRecord> participantOrders() {
        ArrayList<MainActivity.TransactionRecord> result = new ArrayList<>();
        for (int i = host.transactions.size() - 1; i >= 0; i--) {
            MainActivity.TransactionRecord order = host.transactions.get(i);
            if (order.isBuyer(host.currentStudentEmail) || order.isSeller(host.currentStudentEmail)) result.add(order);
        }
        return result;
    }

    public Spinner paymentOrderSelector(LinearLayout form, ArrayList<MainActivity.TransactionRecord> orders) {
        String[] labels = new String[orders.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = orders.get(i).itemTitle + " · " + orders.get(i).buyerName;
        return host.ui.listingSelector(form, "Select order", labels);
    }

    // Fee estimate only; it never changes an order.
    public void showFeeEstimator() {
        LinearLayout form = host.ui.paymentPanelForm();
        form.addView(host.ui.bodyText("Estimate only. All amounts are in ZAR and final totals are shown at checkout.", host.blue, 12));
        ArrayList<MainActivity.TransactionRecord> orders = participantOrders();
        String[] sources = new String[orders.size() + 1];
        sources[0] = "Custom estimate";
        for (int i = 0; i < orders.size(); i++) sources[i + 1] = orders.get(i).itemTitle + " / " + orders.get(i).id;
        Spinner source = host.ui.listingSelector(form, "Base amount from", sources);
        EditText amount = host.ui.listingField(form, "Item amount (R)", "0.00", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText discount = host.ui.listingField(form, "Discount (R)", "0.00", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        discount.setText("0.00");
        Spinner collection = host.ui.listingSelector(form, "Collection option", new String[] {"Campus pickup", "Delivery estimate"});
        EditText delivery = host.ui.listingField(form, "Delivery estimate (R)", "0.00", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        delivery.setText("0.00"); delivery.setEnabled(false);
        CheckBox includeFee = new CheckBox(host);
        includeFee.setText("Include a hypothetical service fee"); includeFee.setTextSize(12); host.ui.themedToggle(includeFee); form.addView(includeFee);
        EditText percent = host.ui.listingField(form, "Estimated service fee (%)", "0.00", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        percent.setText("0.00"); percent.setEnabled(false);
        Spinner payer = host.ui.listingSelector(form, "Who covers the estimated fee?", new String[] {"Buyer", "Seller"});
        payer.setEnabled(false);
        source.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) {
                if (position > 0) amount.setText(orders.get(position - 1).amount);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        collection.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) { delivery.setEnabled(position == 1); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        includeFee.setOnCheckedChangeListener((button, checked) -> { percent.setEnabled(checked); payer.setEnabled(checked); });
        LinearLayout result = new LinearLayout(host); result.setOrientation(LinearLayout.VERTICAL);
        Button calculate = host.ui.primaryButton("Calculate estimate");
        form.addView(calculate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        form.addView(result);
        calculate.setOnClickListener(v -> {
            result.removeAllViews();
            try {
                MainActivity.PaymentEstimate quote = new MainActivity.PaymentEstimate(host.ui.text(amount).trim(), host.ui.text(discount).trim(),
                        collection.getSelectedItemPosition() == 1 ? host.ui.text(delivery).trim() : "0",
                        includeFee.isChecked() ? host.ui.text(percent).trim() : "0", payer.getSelectedItemPosition() == 0);
                paymentLine(result, "Estimated service fee", rand(quote.fee));
                paymentLine(result, "Estimated buyer total", rand(quote.buyerTotal));
                paymentLine(result, "Estimated item proceeds", rand(quote.sellerProceeds));
                result.addView(host.ui.bodyText("Item proceeds exclude delivery charges. Final fees are shown at checkout. All amounts are in ZAR.", host.muted, 11));
            } catch (IllegalArgumentException e) { result.addView(host.ui.bodyText(e.getMessage(), host.error, 12)); }
        });
        AlertDialog dialog = host.ui.dialog().setTitle("Fees & totals / estimate").setView(host.ui.paymentPanelScroll(form)).setNegativeButton("Close", null).show();
        host.ui.addPageClose(dialog);
    }

    // Save a change, sync it, and undo it if saving fails.
    public boolean saveOrderChange(MainActivity.TransactionRecord order, OrderChange change) {
        String oldStatus = order.status, oldRequester = order.cancellationRequester, oldReason = order.cancellationReason, oldOutcome = order.cancellationOutcome;
        String oldPayoutMethod = order.payoutMethod, oldPayoutSchedule = order.payoutSchedule;
        long oldTime = order.cancellationTime, oldPayout = order.payoutRequestedAt;
        if (!change.apply()) { host.ui.showSimpleMessage("Order unchanged", "That action isn't available for this order."); return false; }
        if (!saveTransactions()) {
            order.status = oldStatus; order.cancellationRequester = oldRequester; order.cancellationReason = oldReason;
            order.cancellationOutcome = oldOutcome; order.cancellationTime = oldTime; order.payoutRequestedAt = oldPayout;
            order.payoutMethod = oldPayoutMethod; order.payoutSchedule = oldPayoutSchedule;
            host.ui.showSimpleMessage("Couldn't save", "The order wasn't changed. Give it another go."); return false;
        }
        for (MainActivity.Product product : host.products) {
            if (!hasOrdersFor(product.id) || !applyListingState(product)) continue;
            host.market.cloudMirrorListing(product, false);
        }
        host.market.saveProducts();
        cloudUpdateOrder(order);
        host.showAppScreen("Payments"); return true;
    }

    // Cancellation lists use order cards and reason chips.
    public void showCancellationCenter(MainActivity.TransactionRecord order) {
        if (order == null) { showCancellationOrderPicker(); return; }
        if (!order.isBuyer(host.currentStudentEmail) && !order.isSeller(host.currentStudentEmail)) return;
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        LinearLayout titleRow = host.ui.horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(host.ui.titleText(order.itemTitle, host.text, 16),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(host.ui.statusPill(order.statusLabel()));
        body.addView(titleRow);
        body.addView(host.ui.bodyText("Payment is settled directly between students. A cancellation needs both students to agree.",
                host.muted, 12));

        if (order.status.equals(MainActivity.TransactionRecord.REQUESTED)) {
            body.addView(host.ui.bodyText(order.isBuyer(host.currentStudentEmail)
                    ? "This request is still waiting for the seller. Withdraw it from the order page."
                    : "This purchase request is waiting for your approval. Open the order to approve or decline it.",
                    host.text, 13));
            Button open = host.ui.primaryButton("Open order");
            open.setOnClickListener(v -> { if (sheetRef[0] != null) sheetRef[0].dismiss(); showOrderDetails(order); });
            LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
            openParams.topMargin = host.ui.dp(14);
            body.addView(open, openParams);
        } else if (order.status.equals(MainActivity.TransactionRecord.CANCELLED)
                || order.status.equals(MainActivity.TransactionRecord.COMPLETED)) {
            body.addView(host.ui.bodyText(order.status.equals(MainActivity.TransactionRecord.CANCELLED)
                    ? "This order has already been cancelled."
                    : "Completed orders can't be cancelled. Contact the other student if there is a problem.",
                    host.text, 13));
            if (!order.cancellationReason.isEmpty()) body.addView(host.ui.bodyText("Reason: " + order.cancellationReason, host.muted, 12));
            if (!order.cancellationOutcome.isEmpty()) body.addView(host.ui.bodyText(order.cancellationOutcome, host.muted, 12));
            Button contact = host.ui.secondaryButton("Contact student");
            contact.setOnClickListener(v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                host.inbox.showConversation(order.isBuyer(host.currentStudentEmail) ? order.sellerEmail : order.buyerEmail);
            });
            LinearLayout.LayoutParams contactParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
            contactParams.topMargin = host.ui.dp(14);
            body.addView(contact, contactParams);
        } else if (order.cancellationPending()) {
            body.addView(host.ui.bodyText("Requested by " + host.studentName(order.cancellationRequester), host.text, 13));
            body.addView(host.ui.bodyText("Reason: " + order.cancellationReason, host.muted, 12));
            if (order.cancellationRequester.equalsIgnoreCase(host.currentStudentEmail)) {
                Button withdraw = host.ui.primaryButton("Withdraw request");
                withdraw.setOnClickListener(v -> {
                    if (saveOrderChange(order, () -> order.withdrawCancellation(host.currentStudentEmail, System.currentTimeMillis()))) {
                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                        host.notification.showCampusNotification("Cancellation request withdrawn", "The order remains awaiting collection.", "payment");
                        host.notification.addNotification(order.isBuyer(host.currentStudentEmail) ? order.sellerEmail : order.buyerEmail,
                                "Cancellation request withdrawn", order.itemTitle, "payment", "");
                    }
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
                params.topMargin = host.ui.dp(14);
                body.addView(withdraw, params);
            } else {
                Button approve = host.ui.primaryButton("Approve cancellation");
                approve.setOnClickListener(v -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    resolveOrderCancellation(order, true);
                });
                Button decline = host.ui.secondaryButton("Keep order");
                decline.setOnClickListener(v -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    resolveOrderCancellation(order, false);
                });
                LinearLayout.LayoutParams approveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
                approveParams.topMargin = host.ui.dp(14);
                body.addView(approve, approveParams);
                LinearLayout.LayoutParams declineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
                declineParams.topMargin = host.ui.dp(8);
                body.addView(decline, declineParams);
            }
        } else {
            body.addView(host.ui.label("Reason"));
            final String[] reasons = {"Changed my mind", "Item unavailable", "Pickup can't be arranged",
                    "Listing differs from description", "Other"};
            final String[] selectedReason = {reasons[0]};
            LinearLayout chipRow = host.ui.horizontal();
            HorizontalScrollView chipScroll = new HorizontalScrollView(host);
            chipScroll.setHorizontalScrollBarEnabled(false);
            ArrayList<TextView> chips = new ArrayList<>();
            for (String reason : reasons) {
                TextView chip = host.ui.filterPill(reason, reason.equals(selectedReason[0]));
                chip.setTextSize(11);
                chip.setOnClickListener(v -> {
                    selectedReason[0] = reason;
                    for (TextView item : chips) {
                        boolean active = item.getText().toString().equals(reason);
                        item.setBackground(active ? host.ui.round(host.blue, 999) : host.ui.strokedRound(host.surface(), 999, host.border));
                        item.setTextColor(active ? Color.WHITE : host.text);
                    }
                });
                chips.add(chip);
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(40));
                chipParams.rightMargin = host.ui.dp(8);
                chipRow.addView(chip, chipParams);
            }
            chipScroll.addView(chipRow);
            body.addView(chipScroll);
            EditText note = host.ui.listingField(body, "Additional details", "Optional note", InputType.TYPE_CLASS_TEXT);
            note.setFilters(new InputFilter[] {new InputFilter.LengthFilter(500)});
            Button send = host.ui.primaryButton("Send cancellation request");
            send.setOnClickListener(v -> {
                String details = host.ui.text(note).trim();
                if (selectedReason[0].equals("Other") && details.length() < 10) {
                    note.setError("Add at least 10 characters for Other");
                    return;
                }
                String explanation = selectedReason[0] + (details.isEmpty() ? "" : " / " + details);
                if (saveOrderChange(order, () -> order.requestCancellation(host.currentStudentEmail, explanation, System.currentTimeMillis()))) {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    host.notification.addNotification(order.isBuyer(host.currentStudentEmail) ? order.sellerEmail : order.buyerEmail,
                            "Cancellation requested", explanation, "payment", "");
                    host.notification.showCampusNotification("Cancellation request sent",
                            "Waiting for the other student's decision.", "payment");
                }
            });
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
            sendParams.topMargin = host.ui.dp(14);
            body.addView(send, sendParams);
            body.addView(host.ui.bodyText("The order stays active until the other student approves.", host.muted, 11));
        }
        sheetRef[0] = host.ui.showBottomSheet("Cancellation", body);
    }

    private void showCancellationOrderPicker() {
        ArrayList<MainActivity.TransactionRecord> orders = participantOrders();
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        if (orders.isEmpty()) {
            body.addView(host.ui.emptyState("flag", "No orders yet",
                    "Cancellation choices show up once you buy or sell something."));
            host.ui.showBottomSheet("Cancellations", body);
            return;
        }
        body.addView(host.ui.bodyText("Choose an order to review its cancellation status or ask to cancel it.",
                host.muted, 12));
        for (MainActivity.TransactionRecord order : orders) {
            LinearLayout card = host.ui.card(host.surface(), 14, host.ui.dp(14));
            card.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
            LinearLayout top = host.ui.horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(host.ui.titleText(order.itemTitle, host.text, 14),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(host.ui.statusPill(order.statusLabel()));
            card.addView(top);
            boolean buyer = order.isBuyer(host.currentStudentEmail);
            String counterpart = buyer
                    ? (order.sellerName != null && !order.sellerName.isEmpty() ? order.sellerName : host.studentName(order.sellerEmail))
                    : order.buyerName;
            card.addView(host.ui.bodyText(counterpart + " · " + order.paidAt, host.muted, 11));
            if (!order.cancellationOutcome.isEmpty() || !order.cancellationReason.isEmpty()) {
                String line = !order.cancellationOutcome.isEmpty() ? order.cancellationOutcome
                        : "Cancellation requested · " + order.cancellationReason;
                card.addView(host.ui.bodyText(line, host.muted, 11));
            }
            card.setOnClickListener(v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                showOrderDetails(order);
            });
            host.ui.attachPressAnimation(card);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = host.ui.dp(8);
            body.addView(card, cardParams);
        }
        sheetRef[0] = host.ui.showBottomSheet("Cancellations", body);
    }

    // Approving a cancellation closes the order; any money is settled between students.
    public void resolveOrderCancellation(MainActivity.TransactionRecord order, boolean approve) {
        if (saveOrderChange(order, () -> order.resolveCancellation(host.currentStudentEmail, approve, System.currentTimeMillis()))) {
            host.notification.addNotification(order.isBuyer(host.currentStudentEmail) ? order.sellerEmail : order.buyerEmail,
                    approve ? "Order cancelled" : "Cancellation declined", order.itemTitle + " · cancellation " + (approve ? "approved" : "declined"), "payment", "");
            host.notification.showCampusNotification(approve ? "Order cancelled" : "Cancellation declined", order.itemTitle, "payment");
        }
    }

    // Collection only; the app never handles money.
    public void showPayoutPreferences() {
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        LinearLayout panel = host.ui.card(host.nightActive ? Color.rgb(42, 42, 46) : Color.rgb(244, 242, 237), 14, host.ui.dp(14));
        panel.setBackground(host.ui.strokedRound(host.nightActive ? Color.rgb(42, 42, 46) : Color.rgb(244, 242, 237),
                14, host.line));
        panel.addView(host.ui.titleText("Payment stays between students", host.text, 14));
        panel.addView(host.ui.bodyText("• Cash or EFT only - no card or app payment.\n"
                + "• Agree the pickup plan before handing over.\n"
                + "• The Sales balance is a record of completed orders, not money held by CampusTrade.", host.muted, 12));
        body.addView(panel);
        int completed = 0;
        for (MainActivity.TransactionRecord order : participantOrders()) {
            if (order.isSeller(host.currentStudentEmail) && order.status.equals(MainActivity.TransactionRecord.COMPLETED)) completed++;
        }
        LinearLayout summary = host.ui.horizontal();
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(0, host.ui.dp(14), 0, 0);
        summary.addView(host.ui.bodyText("Completed sales recorded", host.muted, 12),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        summary.addView(host.ui.statusPill(completed + (completed == 1 ? " sale" : " sales")));
        body.addView(summary);
        host.ui.showBottomSheet("Collection arrangements", body);
    }

    // Kept for screens that need the order body in a scroll view.
    public ScrollView paymentOrderContent(MainActivity.TransactionRecord order) {
        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(8), host.ui.dp(20), host.ui.dp(20));
        content.addView(orderDetailContent(order));
        scroll.addView(content);
        return scroll;
    }

    public void showReceipt(MainActivity.TransactionRecord record) {
        if (!record.isBuyer(host.currentStudentEmail) && !record.isSeller(host.currentStudentEmail)) return;
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Confirmation", "Shareable order summary", page::dismiss));

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        boolean completed = record.status.equals(MainActivity.TransactionRecord.COMPLETED);
        boolean cancelled = record.status.equals(MainActivity.TransactionRecord.CANCELLED);
        LinearLayout hero = host.ui.card(host.surface(), 18, host.ui.dp(20));
        hero.setBackground(host.ui.strokedRound(host.surface(), 18, host.line));
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView tick = host.ui.titleText(cancelled ? "×" : completed ? "✓" : "•", Color.WHITE, 28);
        tick.setGravity(Gravity.CENTER);
        tick.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tick.setBackground(host.ui.round(cancelled ? host.muted : completed ? host.readableInk(host.green) : host.readableInk(Color.rgb(217, 139, 0)), 999));
        hero.addView(tick, new LinearLayout.LayoutParams(host.ui.dp(58), host.ui.dp(58)));
        TextView headline = host.ui.titleText(cancelled ? "Order cancelled" : completed ? "Order complete"
                : record.status.equals(MainActivity.TransactionRecord.HELD) ? "Order confirmed" : "Request sent", host.text, 20);
        headline.setGravity(Gravity.CENTER);
        headline.setPadding(0, host.ui.dp(12), 0, host.ui.dp(6));
        hero.addView(headline);
        hero.addView(host.ui.statusPill(record.statusLabel()));
        TextView heroNote = host.ui.bodyText(completed ? "Thanks for keeping campus trade simple."
                : cancelled ? "This order is closed. The listing returns to the market."
                : record.status.equals(MainActivity.TransactionRecord.HELD)
                ? "Arrange collection with the other student."
                : "The seller must confirm before collection.", host.muted, 12);
        heroNote.setGravity(Gravity.CENTER);
        heroNote.setPadding(0, host.ui.dp(10), 0, 0);
        hero.addView(heroNote);
        content.addView(hero);

        LinearLayout details = host.ui.card(host.surface(), 14, host.ui.dp(16));
        details.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
        details.addView(host.ui.titleText("Summary", host.text, 14));
        paymentLine(details, "Item", record.itemTitle);
        paymentLine(details, "Total (ZAR)", rand(record.value()));
        paymentLine(details, "Payment", record.method);
        paymentLine(details, "Date", record.paidAt);
        paymentLine(details, "Buyer", record.buyerName);
        paymentLine(details, "Seller", record.sellerName != null && !record.sellerName.isEmpty()
                ? record.sellerName : host.studentName(record.sellerEmail));
        if (!record.reference.isEmpty()) paymentLine(details, "Collection note", record.reference);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailsParams.topMargin = host.ui.dp(14);
        content.addView(details, detailsParams);
        content.addView(host.ui.bodyText("CampusTrade does not process or hold money. Payment and collection are arranged directly between students.",
                host.muted, 11));

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button shareButton = host.ui.primaryButton("Share confirmation");
        shareButton.setOnClickListener(v -> shareConfirmation(record));
        footer.addView(shareButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    public void showPendingPurchaseRequests() {
        java.util.ArrayList<MainActivity.TransactionRecord> pending = new java.util.ArrayList<>();
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (order.isSeller(host.currentStudentEmail) && order.status.equals(MainActivity.TransactionRecord.REQUESTED)) pending.add(order);
        }
        if (pending.isEmpty()) {
            host.ui.showSimpleMessage("Purchase requests", "No pending purchase requests.");
            return;
        }
        String[] titles = new String[pending.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = pending.get(i).itemTitle + " · " + pending.get(i).buyerName;
        host.ui.dialog().setTitle("Purchase requests").setItems(titles, (d, which) -> showOrderDetails(pending.get(which))).show();
    }

    public void showTransactionHistory() {
        if (!host.currentTab.equals("Payments")) host.paymentsReturnTab = host.currentTab;
        host.showAppScreen("Payments");
    }

    public void buildPayments(LinearLayout content) {
        LinearLayout tabs = host.ui.horizontal();
        tabs.setPadding(host.ui.dp(3), host.ui.dp(3), host.ui.dp(3), host.ui.dp(3));
        tabs.setBackground(host.ui.round(host.nightActive ? host.surface() : Color.rgb(242, 240, 235), 12));
        int modeIndex = 0;
        for (String mode : new String[] {"Buying", "Selling"}) {
            boolean buying = mode.equals("Buying");
            TextView tab = host.ui.filterPill(mode, buying == host.buyingOrders);
            tab.setSingleLine(true);
            tab.setOnClickListener(v -> { host.buyingOrders = buying; host.showAppScreen("Payments"); });
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, host.ui.dp(40), 1);
            if (modeIndex++ > 0) tabParams.leftMargin = host.ui.dp(4);
            tabs.addView(tab, tabParams);
        }
        content.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        // Refresh from Firebase and redraw if the screen is open.
        cloudPullOrders(() -> {
            if (!host.isDestroyed() && !host.isFinishing() && host.currentTab.equals("Payments")) host.showAppScreen("Payments");
        });
        cloudPullPickups();
        TextView paymentAlerts = host.ui.textButton("Order notifications");
        paymentAlerts.setPadding(host.ui.dp(2), host.ui.dp(12), 0, host.ui.dp(12));
        paymentAlerts.setOnClickListener(v -> host.notification.showNotificationHistory());
        content.addView(paymentAlerts);
        for (MainActivity.TransactionRecord order : host.transactions) if (needsBuyerReview(order)) {
            Button review = host.ui.primaryButton("Review purchase: " + order.itemTitle);
            review.setOnClickListener(v -> reviewOrder(order));
            content.addView(review);
        }

        MainActivity.OrderSummary summary = MainActivity.OrderSummary.forUser(host.transactions, host.currentStudentEmail, host.buyingOrders);
        int completedCount = 0;
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (!(host.buyingOrders ? order.isBuyer(host.currentStudentEmail) : order.isSeller(host.currentStudentEmail))) continue;
            if (order.status.equals(MainActivity.TransactionRecord.COMPLETED)) completedCount++;
        }
        LinearLayout balances = host.ui.horizontal();
        LinearLayout leftCard;
        LinearLayout rightCard;
        if (host.buyingOrders) {
            leftCard = paymentBalance("Awaiting collection", summary.held, host.blue);
            rightCard = paymentCount("Completed orders", completedCount, host.readableInk(host.green));
        } else {
            leftCard = paymentBalance("Sales balance", summary.completed, host.readableInk(host.green));
            leftCard.addView(host.ui.bodyText(completedCount == 1 ? "1 completed sale" : completedCount + " completed sales", host.muted, 11));
            rightCard = paymentBalance("Awaiting collection", summary.held, host.readableInk(Color.rgb(217, 139, 0)));
        }
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        left.rightMargin = host.ui.dp(8);
        left.topMargin = host.ui.dp(14);
        balances.addView(leftCard, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        right.topMargin = host.ui.dp(14);
        balances.addView(rightCard, right);
        content.addView(balances);
        int requestCount = 0;
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (order.isSeller(host.currentStudentEmail) && order.status.equals(MainActivity.TransactionRecord.REQUESTED)) requestCount++;
        }
        if (!host.buyingOrders && requestCount > 0) {
            Button reviewRequests = host.ui.primaryButton("Review purchase requests (" + requestCount + ")");
            reviewRequests.setOnClickListener(v -> showPendingPurchaseRequests());
            content.addView(reviewRequests);
        }
        TextView testNote = host.ui.bodyText("Payments are arranged directly between students. CampusTrade does not hold money.", host.muted, 11);
        testNote.setPadding(host.ui.dp(4), host.ui.dp(10), host.ui.dp(4), host.ui.dp(4));
        content.addView(testNote);

        LinearLayout preferences = host.ui.horizontal();
        preferences.setGravity(Gravity.CENTER_VERTICAL);
        preferences.setPadding(0, host.ui.dp(16), 0, host.ui.dp(16));
        preferences.setBackground(host.ui.bottomRule(host.line));
        LinearLayout paymentCopy = new LinearLayout(host);
        paymentCopy.setOrientation(LinearLayout.VERTICAL);
        paymentCopy.addView(host.ui.bodyText("Payment preference", host.text, 14));
        paymentCopy.addView(host.ui.bodyText(preferredPaymentMethod().label + " / preferred", host.muted, 12));
        preferences.addView(paymentCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView change = host.ui.linkText("Change");
        change.setPadding(host.ui.dp(8), host.ui.dp(12), 0, host.ui.dp(12));
        change.setOnClickListener(v -> choosePreferredPayment());
        preferences.addView(change);
        content.addView(preferences);

        TextView historyTitle = host.ui.titleText("Order history", host.text, 20);
        historyTitle.setPadding(0, host.ui.dp(24), 0, host.ui.dp(12));
        content.addView(historyTitle);
        if (host.paymentStatusFilter.equals("Requests")) {
            content.post(() -> {
                android.view.View parent = (android.view.View) content.getParent();
                if (parent instanceof android.widget.ScrollView) {
                    ((android.widget.ScrollView) parent).smoothScrollTo(0, historyTitle.getTop());
                }
            });
        }
        EditText search = host.ui.input("Search item or student", InputType.TYPE_CLASS_TEXT);
        search.setTextSize(13);
        search.setText(host.paymentQuery);
        content.addView(search);
        LinearLayout filters = host.ui.horizontal();
        HorizontalScrollView filterScroll = new HorizontalScrollView(host);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setClipToPadding(false);
        filters.setPadding(0, 0, host.ui.dp(8), 0);
        for (String status : new String[] {"All", "Requests", "Awaiting", "Completed", "Cancelled"}) {
            TextView filter = host.ui.filterPill(status, host.paymentStatusFilter.equals(status));
            filter.setPadding(host.ui.dp(14), 0, host.ui.dp(14), 0);
            filter.setOnClickListener(v -> { host.paymentStatusFilter = status; host.showAppScreen("Payments"); });
            LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(48));
            filterParams.rightMargin = host.ui.dp(8);
            filters.addView(filter, filterParams);
        }
        filterScroll.addView(filters); content.addView(filterScroll);
        LinearLayout history = new LinearLayout(host);
        history.setOrientation(LinearLayout.VERTICAL);
        content.addView(history);
        renderPaymentOrders(history);
        search.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable value) {
                host.paymentQuery = value.toString(); renderPaymentOrders(history);
            }
        });

        TextView information = host.ui.titleText("Order tools", host.text, 20);
        information.setPadding(0, host.ui.dp(24), 0, host.ui.dp(8));
        content.addView(information);
        for (String topic : new String[] {"Payment preference", "Collection arrangements", "Cancellations"}) {
            TextView help = host.ui.actionChip(topic);
            help.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            help.setOnClickListener(v -> showPaymentInformation(topic));
            content.addView(help, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        }
    }

    public void renderPaymentOrders(LinearLayout content) {
        content.removeAllViews();

        LinearLayout orders = host.ui.card(Color.WHITE, 16, host.ui.dp(6));
        int shown = 0;
        for (int i = host.transactions.size() - 1; i >= 0; i--) {
            MainActivity.TransactionRecord order = host.transactions.get(i);
            if (!(host.buyingOrders ? order.isBuyer(host.currentStudentEmail) : order.isSeller(host.currentStudentEmail))) continue;
            if (host.paymentStatusFilter.equals("Requests") && !(order.status.equals(MainActivity.TransactionRecord.REQUESTED) || order.cancellationPending())) continue;
            if (host.paymentStatusFilter.equals("Awaiting") && !order.status.equals(MainActivity.TransactionRecord.HELD)) continue;
            if (host.paymentStatusFilter.equals("Completed") && !order.status.equals(MainActivity.TransactionRecord.COMPLETED)) continue;
            if (host.paymentStatusFilter.equals("Cancelled") && !order.status.equals(MainActivity.TransactionRecord.CANCELLED)) continue;
            String searchable = order.itemTitle + " " + order.id + " " + order.buyerName + " " + order.sellerEmail + " " + order.method;
            if (!searchable.toLowerCase(Locale.ROOT).contains(host.paymentQuery.trim().toLowerCase(Locale.ROOT))) continue;
            if (shown++ > 0) host.ui.addMenuDivider(orders);
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(host.ui.dp(10), host.ui.dp(12), host.ui.dp(10), host.ui.dp(12));
            MainActivity.Product orderedProduct = null;
            for (MainActivity.Product product : host.products) if (product.id.equals(order.productId)) { orderedProduct = product; break; }
            if (orderedProduct != null) {
                View thumb = host.market.listingThumbnail(orderedProduct, false);
                row.addView(thumb, new LinearLayout.LayoutParams(host.ui.dp(52), host.ui.dp(52)));
            } else {
                View icon = host.new CampusAssetView(host, order.category.equals("Books") ? "book" : "box", host.blue);
                icon.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
                icon.setBackground(host.ui.round(Color.rgb(234, 242, 255), 12));
                row.addView(icon, new LinearLayout.LayoutParams(host.ui.dp(46), host.ui.dp(46)));
            }
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
            TextView title = host.ui.titleText(order.itemTitle, host.text, 12);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);
            String counterpart = host.buyingOrders ? (order.sellerName != null && !order.sellerName.isEmpty() ? order.sellerName : host.studentName(order.sellerEmail)) : order.buyerName;
            TextView subtitle = host.ui.bodyText(counterpart, host.muted, 10);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setPadding(0, host.ui.dp(4), 0, 0);
            copy.addView(subtitle);
            copy.addView(host.ui.bodyText(order.method + " · " + order.paidAt, host.muted, 10));
            if (needsBuyerReview(order)) copy.addView(host.ui.bodyText("Review required · tap to rate", host.blue, 11));
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout status = new LinearLayout(host);
            status.setOrientation(LinearLayout.VERTICAL);
            status.setGravity(Gravity.END);
            status.addView(host.ui.titleText(rand(order.value()), host.text, 12));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.topMargin = host.ui.dp(4);
            badgeParams.gravity = Gravity.END;
            status.addView(host.ui.statusPill(order.statusLabel()), badgeParams);
            row.addView(status);
            row.setOnClickListener(v -> showOrderDetails(order));
            host.ui.attachPressAnimation(row);
            orders.addView(row);
        }
        if (shown > 0) content.addView(orders);
        else {
            content.addView(host.ui.emptyState("bag",
                    !host.paymentQuery.trim().isEmpty() || !host.paymentStatusFilter.equals("All")
                            ? "No matching orders" : host.buyingOrders ? "No purchases yet" : "No sales yet",
                    !host.paymentQuery.trim().isEmpty() || !host.paymentStatusFilter.equals("All")
                            ? "Try a different search or status filter."
                            : host.buyingOrders
                            ? "Open a listing and tap Buy now to start an order."
                            : "Orders will appear when a student buys one of your listings."));
        }
    }

    public LinearLayout paymentBalance(String label, BigDecimal amount, int color) {
        LinearLayout balance = host.ui.card(Color.WHITE, 16, host.ui.dp(12));
        balance.addView(host.ui.bodyText(label, host.muted, 11));
        TextView value = host.ui.titleText(rand(amount), color, 18);
        value.setPadding(0, host.ui.dp(4), 0, 0);
        balance.addView(value);
        return balance;
    }

    public LinearLayout paymentCount(String label, int count, int color) {
        LinearLayout balance = host.ui.card(Color.WHITE, 16, host.ui.dp(12));
        balance.addView(host.ui.bodyText(label, host.muted, 11));
        TextView value = host.ui.titleText(String.valueOf(count), color, 18);
        value.setPadding(0, host.ui.dp(4), 0, 0);
        balance.addView(value);
        balance.addView(host.ui.bodyText(count == 1 ? "order collected" : "orders collected", host.muted, 11));
        return balance;
    }

    public static String rand(BigDecimal amount) {
        return "R" + amount.setScale(2).toPlainString();
    }

    // Order details use a full page; secondary actions use a sheet.
    public void showOrderDetails(MainActivity.TransactionRecord order) {
        if (!order.isBuyer(host.currentStudentEmail) && !order.isSeller(host.currentStudentEmail)) return;
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        LinearLayout header = host.ui.pageHeader("Order details", order.itemTitle, page::dismiss);
        View share = host.new CampusAssetView(host, "share", host.text);
        share.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        share.setContentDescription("Share confirmation");
        share.setBackground(host.ui.strokedRound(host.page, 999, host.border));
        share.setOnClickListener(v -> showReceipt(order));
        header.addView(share, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        root.addView(header);

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(orderDetailContent(order));

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(host.ui.dp(20), host.ui.dp(10), host.ui.dp(20), host.ui.dp(14));
        footer.setBackgroundColor(host.surface());
        Button primary = host.ui.primaryButton("Chat");
        footer.addView(primary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        TextView more = host.ui.textButton("More order actions");
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v -> orderActionsSheet(order, page));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(40));
        moreParams.topMargin = host.ui.dp(4);
        footer.addView(more, moreParams);
        root.addView(footer);

        boolean buyer = order.isBuyer(host.currentStudentEmail);
        if (!buyer && order.status.equals(MainActivity.TransactionRecord.REQUESTED)) {
            primary.setText("Approve purchase");
            primary.setOnClickListener(v -> { page.dismiss(); approvePurchaseRequest(order); });
        } else if (buyer && order.status.equals(MainActivity.TransactionRecord.HELD) && !order.cancellationPending()) {
            primary.setText("Confirm collection");
            primary.setOnClickListener(v -> confirmCollection(order, page));
        } else if (needsBuyerReview(order)) {
            primary.setText("Leave review");
            primary.setOnClickListener(v -> { page.dismiss(); reviewOrder(order); });
        } else if (order.status.equals(MainActivity.TransactionRecord.COMPLETED)) {
            primary.setText("Share confirmation");
            primary.setOnClickListener(v -> shareConfirmation(order));
        } else if (order.status.equals(MainActivity.TransactionRecord.CANCELLED)) {
            primary.setText("Contact student");
            primary.setOnClickListener(v -> {
                page.dismiss();
                host.inbox.showConversation(buyer ? order.sellerEmail : order.buyerEmail);
            });
        } else if (order.cancellationPending()) {
            primary.setText(order.cancellationRequester.equalsIgnoreCase(host.currentStudentEmail)
                    ? "Cancellation status" : "Review cancellation");
            primary.setOnClickListener(v -> showCancellationCenter(order));
        } else {
            primary.setText("Chat with " + (buyer ? "seller" : "buyer"));
            primary.setOnClickListener(v -> {
                page.dismiss();
                host.inbox.showConversation(buyer ? order.sellerEmail : order.buyerEmail);
            });
        }

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /** Content below the header on the order page. */
    private LinearLayout orderDetailContent(MainActivity.TransactionRecord order) {
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        boolean buyer = order.isBuyer(host.currentStudentEmail);
        String counterpart = buyer
                ? (order.sellerName != null && !order.sellerName.isEmpty() ? order.sellerName : host.studentName(order.sellerEmail))
                : order.buyerName;

        LinearLayout summary = host.ui.card(host.surface(), 16, host.ui.dp(16));
        summary.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        LinearLayout top = host.ui.horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout amountCopy = new LinearLayout(host);
        amountCopy.setOrientation(LinearLayout.VERTICAL);
        TextView amount = host.ui.titleText(rand(order.value()), host.text, 26);
        amountCopy.addView(amount);
        amountCopy.addView(host.ui.bodyText(buyer ? "Paid to " + counterpart : "From " + counterpart, host.muted, 12));
        top.addView(amountCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(host.ui.statusPill(order.statusLabel()));
        summary.addView(top);
        TextView method = host.ui.bodyText(order.method.equals("Cash on collection")
                ? "Cash on collection · no app payment" : order.method + " · arranged directly with " + (buyer ? "the seller" : "the buyer"),
                host.muted, 11);
        method.setPadding(0, host.ui.dp(10), 0, 0);
        summary.addView(method);
        content.addView(summary);

        TextView progressTitle = host.ui.titleText("Progress", host.text, 15);
        progressTitle.setPadding(0, host.ui.dp(22), 0, host.ui.dp(10));
        content.addView(progressTitle);
        LinearLayout steps = host.ui.card(host.surface(), 16, host.ui.dp(14));
        steps.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
        boolean confirmed = order.status.equals(MainActivity.TransactionRecord.HELD)
                || order.status.equals(MainActivity.TransactionRecord.COMPLETED);
        boolean cancelled = order.status.equals(MainActivity.TransactionRecord.CANCELLED);
        boolean completed = order.status.equals(MainActivity.TransactionRecord.COMPLETED);
        CampusFeatures.Pickup pickup = host.featureStore.data.pickup(order.id);
        boolean pickupAgreed = pickup != null && pickup.agreed();
        addOrderStep(steps, 1, "Request sent", order.paidAt, true, false, false);
        addOrderStep(steps, 2, "Seller confirmed",
                confirmed ? "Purchase confirmed and item reserved" : cancelled ? "Request closed" : "Waiting for the seller to confirm",
                confirmed, !confirmed && !cancelled, false);
        if (cancelled) {
            addOrderStep(steps, 3, "Order cancelled",
                    order.cancellationOutcome.isEmpty() ? "This order was cancelled" : order.cancellationOutcome,
                    true, false, true);
        } else {
            addOrderStep(steps, 3, "Collection",
                    pickup == null ? (confirmed ? "Arrange a campus pickup" : "Unlocks after confirmation")
                            : pickup.location + " · " + host.messageTime(pickup.time),
                    pickupAgreed, confirmed && !pickupAgreed && !completed, false);
            addOrderStep(steps, 4, "Collected",
                    completed ? "Buyer confirmed collection" : "Waiting for the buyer to confirm",
                    completed, false, false);
        }
        content.addView(steps);

        content.addView(orderSectionTitle("Pickup"));
        LinearLayout pickupCard = host.ui.card(host.surface(), 14, host.ui.dp(14));
        pickupCard.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
        if (pickup == null) {
            pickupCard.addView(host.ui.titleText(cancelled ? "Pickup closed" : "No pickup planned yet", host.text, 14));
            pickupCard.addView(host.ui.bodyText(cancelled ? "Cancelled orders no longer need a handover."
                            : confirmed ? "Agree a public campus spot and a time with the other student."
                            : "Pickup details unlock once the seller confirms this request.", host.muted, 12));
        } else {
            LinearLayout pickupTop = host.ui.horizontal();
            pickupTop.setGravity(Gravity.CENTER_VERTICAL);
            pickupTop.addView(host.ui.titleText(pickup.location, host.text, 14), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            pickupTop.addView(host.ui.statusPill(pickupAgreed ? "Agreed" : "Awaiting"));
            pickupCard.addView(pickupTop);
            pickupCard.addView(host.ui.bodyText(host.messageTime(pickup.time), host.muted, 12));
            pickupCard.addView(pickupTickRow("Buyer confirmed", pickup.confirmed.contains(order.buyerEmail)));
            pickupCard.addView(pickupTickRow("Seller confirmed", pickup.confirmed.contains(order.sellerEmail)));
        }
        if (confirmed && !completed && !cancelled) {
            Button managePickup = host.ui.secondaryButton(pickup == null ? "Arrange pickup" : "Manage pickup");
            LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
            manageParams.topMargin = host.ui.dp(12);
            managePickup.setOnClickListener(v -> host.market.pickupActions(order));
            pickupCard.addView(managePickup, manageParams);
        }
        content.addView(pickupCard);

        content.addView(orderSectionTitle("Contact"));
        LinearLayout contact = host.ui.card(host.surface(), 14, host.ui.dp(14));
        contact.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
        contact.addView(host.ui.titleText(buyer ? counterpart + " · Seller" : counterpart + " · Buyer", host.text, 14));
        if (buyer) {
            contact.addView(host.ui.bodyText("Phone numbers are shared in chat once the order is confirmed. Keep the handover inside CampusTrade.",
                    host.muted, 12));
            Button chat = host.ui.secondaryButton("Open chat");
            LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44));
            chatParams.topMargin = host.ui.dp(10);
            chat.setOnClickListener(v -> host.inbox.showConversation(order.sellerEmail));
            contact.addView(chat, chatParams);
        } else {
            final boolean[] revealed = {false};
            TextView phone = host.ui.bodyText("Phone · ••• ••• " + lastDigits(order.buyerPhone, 3), host.text, 13);
            phone.setPadding(0, host.ui.dp(6), 0, 0);
            contact.addView(phone);
            TextView reveal = host.ui.textButton("Reveal contact number");
            reveal.setOnClickListener(v -> {
                revealed[0] = true;
                phone.setText("Phone · " + order.buyerPhone);
                reveal.setVisibility(View.GONE);
            });
            contact.addView(reveal);
            contact.addView(host.ui.bodyText("Only reveal this when you are arranging the handover.", host.muted, 11));
        }
        content.addView(contact);

        content.addView(orderSectionTitle("Payment & collection"));
        LinearLayout collection = host.ui.card(host.nightActive ? Color.rgb(42, 42, 46) : Color.rgb(244, 242, 237), 14, host.ui.dp(14));
        paymentLine(collection, "Amount", rand(order.value()));
        paymentLine(collection, "Payment preference", order.method);
        paymentLine(collection, "Collection note", order.reference.isEmpty() ? "None" : order.reference);
        MainActivity.Product ordered = null;
        for (MainActivity.Product product : host.products) if (product.id.equals(order.productId)) { ordered = product; break; }
        paymentLine(collection, "Handover", ordered == null ? "Campus pickup" : ordered.location);
        collection.addView(host.ui.bodyText("CampusTrade does not process or hold money. Settle payment directly with the other student at collection.",
                host.muted, 11));
        content.addView(collection);

        if (!order.cancellationOutcome.isEmpty() || !order.cancellationReason.isEmpty()) {
            content.addView(orderSectionTitle("Cancellation"));
            LinearLayout cancel = host.ui.card(host.nightActive ? Color.rgb(52, 45, 32) : Color.rgb(255, 246, 228), 14, host.ui.dp(14));
            cancel.setBackground(host.ui.strokedRound(host.nightActive ? Color.rgb(52, 45, 32) : Color.rgb(255, 246, 228), 14,
                    host.nightActive ? Color.rgb(90, 72, 38) : Color.rgb(238, 213, 165)));
            cancel.addView(host.ui.titleText(order.cancellationOutcome.isEmpty() ? "Cancellation requested" : order.cancellationOutcome, host.text, 14));
            if (!order.cancellationReason.isEmpty()) cancel.addView(host.ui.bodyText(order.cancellationReason, host.muted, 12));
            if (order.cancellationTime > 0) cancel.addView(host.ui.bodyText(
                    new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(order.cancellationTime)), host.muted, 11));
            content.addView(cancel);
        }
        return content;
    }

    private TextView orderSectionTitle(String title) {
        TextView heading = host.ui.titleText(title, host.text, 15);
        heading.setPadding(0, host.ui.dp(22), 0, host.ui.dp(10));
        return heading;
    }

    private void addOrderStep(LinearLayout parent, int number, String title, String detail,
                              boolean done, boolean active, boolean cancelled) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.TOP);
        LinearLayout rail = new LinearLayout(host);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        if (number > 1) {
            View topLine = new View(host);
            topLine.setBackgroundColor(done || active ? host.ui.statusInk("Completed") : host.line);
            rail.addView(topLine, new LinearLayout.LayoutParams(host.ui.dp(2), host.ui.dp(10)));
        }
        TextView marker = host.ui.bodyText(done ? "✓" : cancelled ? "×" : String.valueOf(number),
                done || active ? Color.WHITE : host.muted, 12);
        marker.setGravity(Gravity.CENTER);
        marker.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        marker.setBackground(host.ui.round(done ? host.readableInk(host.green) : active ? host.blue
                : host.nightActive ? Color.rgb(64, 64, 68) : Color.rgb(236, 234, 229), 999));
        rail.addView(marker, new LinearLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24)));
        if (number < (cancelled ? 3 : 4)) {
            View bottomLine = new View(host);
            bottomLine.setBackgroundColor(done ? host.ui.statusInk("Completed") : host.line);
            rail.addView(bottomLine, new LinearLayout.LayoutParams(host.ui.dp(2), host.ui.dp(30)));
        }
        row.addView(rail, new LinearLayout.LayoutParams(host.ui.dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(10), 0, 0, host.ui.dp(14));
        copy.addView(host.ui.titleText(title, done || active ? host.text : host.muted, 13));
        copy.addView(host.ui.bodyText(detail, host.muted, 11));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row);
    }

    private LinearLayout pickupTickRow(String label, boolean confirmed) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(6), 0, 0);
        TextView tick = host.ui.bodyText(confirmed ? "✓" : "○", confirmed ? host.readableInk(host.green) : host.muted, 13);
        tick.setGravity(Gravity.CENTER);
        row.addView(tick, new LinearLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24)));
        row.addView(host.ui.bodyText(label, confirmed ? host.text : host.muted, 12),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(host.ui.statusPill(confirmed ? "Confirmed" : "Waiting"));
        return row;
    }

    private String lastDigits(String value, int count) {
        if (value == null) return "";
        String digits = value.replaceAll("\\D", "");
        return digits.length() <= count ? digits : digits.substring(digits.length() - count);
    }

    private void confirmCollection(MainActivity.TransactionRecord order, Dialog page) {
        host.ui.dialog()
                .setTitle("Confirm collection?")
                .setMessage("Confirm you've got the item? This marks the order as complete and asks for a review.")
                .setNegativeButton("Not yet", null)
                .setPositiveButton("Confirm", (confirmation, action) -> {
                    if (!order.completeByBuyer(host.currentStudentEmail)) return;
                    if (!saveTransactions()) {
                        order.status = MainActivity.TransactionRecord.HELD;
                        Toast.makeText(host, "The order didn't save. Give it another go.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    for (MainActivity.Product product : host.products) if (product.id.equals(order.productId)) {
                        product.status = order.listingState();
                        host.market.cloudMirrorListing(product, false);
                    }
                    host.market.saveProducts();
                    cloudUpdateOrder(order);
                    host.notification.addNotification(order.sellerEmail, "Order completed", order.itemTitle, "payment", "");
                    host.notification.sendPush(order.sellerEmail, "Order completed", order.itemTitle, "payment", "");
                    host.notification.showCampusNotification("Collection confirmed / review required", "The order is complete. Please rate your purchase.", "payment");
                    page.dismiss();
                    host.showAppScreen("Payments");
                    reviewOrder(order);
                }).show();
    }

    private void orderActionsSheet(MainActivity.TransactionRecord order, Dialog page) {
        boolean buyer = order.isBuyer(host.currentStudentEmail);
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        final Dialog[] sheetRef = {null};
        addOrderAction(body, "share", "Order confirmation", "Open a clean summary to share", v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            showReceipt(order);
        });
        addOrderAction(body, "chat", "Contact student", "Open the conversation", v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            page.dismiss();
            host.inbox.showConversation(buyer ? order.sellerEmail : order.buyerEmail);
        });
        if (order.status.equals(MainActivity.TransactionRecord.HELD) && !order.cancellationPending()) {
            addOrderAction(body, "calendar", "Campus pickup", "Confirm or change the handover plan", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                host.market.pickupActions(order);
            });
            addOrderAction(body, "flag", "Cancellation", "Ask to cancel this order", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                showCancellationCenter(order);
            });
        } else if (order.cancellationPending()) {
            addOrderAction(body, "flag", "Cancellation request", "Review the request and reason", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                showCancellationCenter(order);
            });
        }
        if (order.status.equals(MainActivity.TransactionRecord.REQUESTED)) {
            if (!buyer) {
                addOrderAction(body, "close", "Decline request", "Free the listing for other students", v -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    page.dismiss();
                    declinePurchaseRequest(order);
                });
            } else {
                addOrderAction(body, "close", "Cancel request", "Withdraw this purchase request", v -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    page.dismiss();
                    cancelPurchaseRequest(order);
                });
            }
        }
        if (order.status.equals(MainActivity.TransactionRecord.COMPLETED) && !hasOrderReview(order)) {
            addOrderAction(body, "star", "Leave a review", "Rate the handover", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                page.dismiss();
                reviewOrder(order);
            });
        }
        if (!buyer) {
            addOrderAction(body, "payment", "Collection arrangements", "How cash and EFT are settled", v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                showPayoutPreferences();
            });
        }
        sheetRef[0] = host.ui.showBottomSheet("Order actions", body);
    }

    private void addOrderAction(LinearLayout parent, String icon, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(10), 0, host.ui.dp(10));
        View symbol = host.new CampusAssetView(host, icon, host.blue);
        symbol.setPadding(host.ui.dp(9), host.ui.dp(9), host.ui.dp(9), host.ui.dp(9));
        symbol.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), 10));
        row.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(12), 0, 0, 0);
        copy.addView(host.ui.titleText(title, host.text, 13));
        copy.addView(host.ui.bodyText(subtitle, host.muted, 11));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(host.new CampusAssetView(host, "chevron", host.muted),
                new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16)));
        row.setOnClickListener(listener);
        host.ui.attachPressAnimation(row);
        parent.addView(row);
    }

    private void shareConfirmation(MainActivity.TransactionRecord order) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareableConfirmation(order));
        try {
            host.startActivity(Intent.createChooser(share, "Share confirmation"));
        } catch (android.content.ActivityNotFoundException e) {
            host.ui.showSimpleMessage("Sharing unavailable", "No app is available to share this confirmation.");
        }
    }

    private String shareableConfirmation(MainActivity.TransactionRecord order) {
        String buyer = order.buyerName == null || order.buyerName.isEmpty() ? "Student" : order.buyerName;
        String seller = order.sellerName == null || order.sellerName.isEmpty() ? host.studentName(order.sellerEmail) : order.sellerName;
        return "CampusTrade order summary\n"
                + "Item: " + order.itemTitle + "\n"
                + "Total: " + rand(order.value()) + "\n"
                + "Payment: " + order.method + "\n"
                + "Status: " + order.statusLabel() + "\n"
                + "Date: " + order.paidAt + "\n"
                + "Buyer: " + buyer + "\n"
                + "Seller: " + seller + "\n"
                + (order.reference.isEmpty() ? "" : "Collection note: " + order.reference + "\n")
                + "Payment and collection are arranged directly between the students.";
    }

    /** Seller confirms a request; other requests for the listing close. */
    public void approvePurchaseRequest(MainActivity.TransactionRecord order) {
        if (!order.approveBySeller(host.currentStudentEmail)) return;
        long now = System.currentTimeMillis();
        java.util.ArrayList<MainActivity.TransactionRecord> superseded = new java.util.ArrayList<>();
        for (MainActivity.TransactionRecord other : host.transactions) {
            if (other == order || !other.productId.equals(order.productId)
                    || !other.status.equals(MainActivity.TransactionRecord.REQUESTED)) continue;
            other.status = MainActivity.TransactionRecord.CANCELLED;
            other.cancellationOutcome = "Another request was accepted";
            other.cancellationTime = now;
            superseded.add(other);
        }
        if (!saveTransactions()) {
            order.status = MainActivity.TransactionRecord.REQUESTED;
            for (MainActivity.TransactionRecord other : superseded) {
                other.status = MainActivity.TransactionRecord.REQUESTED;
                other.cancellationOutcome = "";
                other.cancellationTime = 0;
            }
            Toast.makeText(host, "Couldn't approve that request. Give it another go.", Toast.LENGTH_LONG).show();
            return;
        }
        syncListingFor(order);
        cloudUpdateOrder(order);
        for (MainActivity.TransactionRecord other : superseded) cloudUpdateOrder(other);
        host.notification.addNotification(order.buyerEmail, "Purchase confirmed",
                order.itemTitle + " is confirmed. Arrange collection.", "payment", "");
        host.notification.sendPush(order.buyerEmail, "Purchase confirmed",
                order.itemTitle + " is confirmed. Arrange collection.");
        host.notification.showCampusNotification("Purchase confirmed",
                order.itemTitle + ". Arrange collection with the buyer.", "payment");
        host.showAppScreen("Payments");
    }

    /** Seller declines; the listing returns to Market. */
    public void declinePurchaseRequest(MainActivity.TransactionRecord order) {
        host.ui.dialog().setTitle("Decline purchase?")
                .setMessage("The listing goes back to the Marketplace and the buyer gets a heads-up.")
                .setNegativeButton("Back", null)
                .setPositiveButton("Decline", (d, which) -> {
                    if (!order.declineBySeller(host.currentStudentEmail, System.currentTimeMillis())) return;
                    if (!saveTransactions()) {
                        order.status = MainActivity.TransactionRecord.REQUESTED;
                        order.cancellationOutcome = "";
                        order.cancellationTime = 0;
                        Toast.makeText(host, "Couldn't decline that request. Give it another go.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    syncListingFor(order);
                    cloudUpdateOrder(order);
                    host.notification.addNotification(order.buyerEmail, "Purchase declined",
                            order.itemTitle + " was declined by the seller.", "payment", "");
                    host.notification.sendPush(order.buyerEmail, "Purchase declined",
                            order.itemTitle + " was declined by the seller.");
                    host.notification.showCampusNotification("Purchase declined",
                            order.itemTitle + ". The buyer has been notified.", "payment");
                    host.showAppScreen("Payments");
                }).show();
    }

    /** Buyer withdraws before the seller confirms. */
    public void cancelPurchaseRequest(MainActivity.TransactionRecord order) {
        host.ui.dialog().setTitle("Cancel request?")
                .setMessage("The seller won't see this request anymore.")
                .setNegativeButton("Keep request", null)
                .setPositiveButton("Cancel request", (d, which) -> {
                    if (!order.cancelRequestByBuyer(host.currentStudentEmail, System.currentTimeMillis())) return;
                    if (!saveTransactions()) {
                        order.status = MainActivity.TransactionRecord.REQUESTED;
                        order.cancellationOutcome = "";
                        order.cancellationTime = 0;
                        Toast.makeText(host, "Couldn't cancel that request. Give it another go.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    syncListingFor(order);
                    cloudUpdateOrder(order);
                    host.notification.addNotification(order.sellerEmail, "Request withdrawn",
                            order.itemTitle + " was withdrawn by the buyer.", "payment", "");
                    host.notification.showCampusNotification("Request cancelled",
                            order.itemTitle + ". The seller has been notified.", "payment");
                    host.showAppScreen("Payments");
                }).show();
    }

    // Orders are saved as JSON; skip a bad record.
    public void loadTransactions() {
        String saved = host.getSharedPreferences(host.PREFS + "_orders", Context.MODE_PRIVATE).getString("records", "[]");
        try {
            JSONArray records = new JSONArray(saved);
            boolean skipped = false;
            for (int i = 0; i < records.length(); i++) {
                try {
                    JSONObject record = records.getJSONObject(i);
                    MainActivity.TransactionRecord order = new MainActivity.TransactionRecord(record.getString("id"), record.getString("item"), record.getString("amount"),
                            record.getString("method"), record.getString("buyerName"), record.getString("phone"), record.optString("reference"),
                            record.getString("date"), record.getString("seller"), record.getString("buyer"), record.optString("category", "Other"),
                            record.optString("status", MainActivity.TransactionRecord.HELD), record.optString("productId", ""));
                    order.sellerName = record.optString("sellerName", "");
                    order.createdTime = record.optLong("createdTime", order.createdMillis());
                    order.cancellationRequester = record.optString("cancellationRequester", "");
                    order.cancellationReason = record.optString("cancellationReason", "");
                    order.cancellationOutcome = record.optString("cancellationOutcome", "");
                    order.cancellationTime = record.optLong("cancellationTime", 0);
                    order.payoutRequestedAt = record.optLong("payoutRequestedAt", 0);
                    order.payoutMethod = record.optString("payoutMethod", "Bank transfer");
                    order.payoutSchedule = record.optString("payoutSchedule", "On request");
                    host.transactions.add(order);
                } catch (org.json.JSONException | IllegalArgumentException | ArithmeticException e) {
                    skipped = true;
                }
            }
            if (skipped) {
                Toast.makeText(host, "Some saved orders didn't load.", Toast.LENGTH_LONG).show();
            }
        } catch (org.json.JSONException | IllegalArgumentException | ArithmeticException e) {
            Toast.makeText(host, "Saved orders didn't load.", Toast.LENGTH_LONG).show();
        }
        retryPendingOrderPushes();
    }

    // Wait a few seconds for the first sync, then retry.
    public void retryPendingOrderPushes() {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("orders").whereEqualTo("buyerId", uid).get()
                    .addOnSuccessListener(snap -> {
                        java.util.Set<String> cloudIds = new java.util.HashSet<>();
                        for (com.google.firebase.firestore.DocumentSnapshot d : snap) cloudIds.add(d.getId());
                        for (MainActivity.TransactionRecord order : new ArrayList<>(host.transactions)) {
                            if (!MainActivity.TransactionRecord.REQUESTED.equals(order.status)) continue;
                            if (!order.isBuyer(host.currentStudentEmail)) continue;
                            if (cloudIds.contains(order.id)) continue;
                            MainActivity.Product product = null;
                            for (MainActivity.Product p : host.products) {
                                if (p.id.equals(order.productId)) { product = p; break; }
                            }
                            if (product == null) continue;
                            cloudPushOrder(order, product);
                        }
                    });
        }, 5000);
    }

    // Save immediately so we know if it worked.
    public boolean saveTransactions() {
        JSONArray records = new JSONArray();
        try {
            for (MainActivity.TransactionRecord order : host.transactions) records.put(new JSONObject().put("id", order.id).put("item", order.itemTitle)
                    .put("amount", order.amount).put("method", order.method).put("buyerName", order.buyerName).put("phone", order.buyerPhone)
                    .put("reference", order.reference).put("date", order.paidAt).put("seller", order.sellerEmail)
                    .put("sellerName", order.sellerName).put("buyer", order.buyerEmail).put("category", order.category).put("status", order.status).put("productId", order.productId)
                    .put("cancellationRequester", order.cancellationRequester).put("cancellationReason", order.cancellationReason)
                    .put("cancellationOutcome", order.cancellationOutcome).put("cancellationTime", order.cancellationTime).put("payoutRequestedAt", order.payoutRequestedAt)
                    .put("payoutMethod", order.payoutMethod).put("payoutSchedule", order.payoutSchedule)
                    .put("createdTime", order.createdMillis()));
        } catch (org.json.JSONException e) { return false; }
        return host.getSharedPreferences(host.PREFS + "_orders", Context.MODE_PRIVATE).edit().putString("records", records.toString()).commit();
    }

    public void showCheckoutConfirm(MainActivity.Product product, String quotedPrice, String method, String bank,
                                     String buyer, String phone, String reference, Runnable confirmed) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Review your order request before sending it. The seller must confirm before you arrange collection.",
                host.muted, 12));
        form.addView(host.ui.titleText(product.title + " · R" + quotedPrice, host.text, 16));
        paymentLine(form, "Payment preference", bank.isEmpty() ? method : method + " (" + bank + ")");
        paymentLine(form, "Buyer", buyer);
        paymentLine(form, "Phone", phone);
        paymentLine(form, "Pickup", product.location + " / " + product.delivery);
        if (!reference.trim().isEmpty()) paymentLine(form, "Collection note", reference);
        paymentLine(form, "Total (ZAR)", "R" + quotedPrice);
        Button send = host.ui.primaryButton("Send request");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        sendParams.topMargin = host.ui.dp(10);
        form.addView(send, sendParams);
        Dialog sheet = host.ui.showBottomSheet("Confirm request", form);
        TextView back = host.ui.textButton("Back to checkout");
        back.setOnClickListener(v -> sheet.dismiss());
        form.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42)));
        send.setOnClickListener(v -> {
            sheet.dismiss();
            confirmed.run();
        });
    }
}