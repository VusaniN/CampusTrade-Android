package com.campustrade.auth;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.campustrade.auth.data.CloudSync;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Seller sales and performance from Firebase. */
public final class SellerDashboard {

    private final MainActivity host;
    private String range = "All time";

    public SellerDashboard(MainActivity host) {
        this.host = host;
    }

    public void build(LinearLayout content) {
        content.addView(host.ui.bodyText("Your sales, listings and ratings - pulled from the marketplace so the numbers stay in sync.",
                host.muted, 12));

        LinearLayout filters = host.ui.horizontal();
        filters.setPadding(0, host.ui.dp(12), 0, host.ui.dp(4));
        for (String option : new String[]{"All time", "7 days", "30 days", "90 days"}) {
            TextView pill = host.ui.filterPill(option, option.equals(range));
            pill.setOnClickListener(v -> {
                range = option;
                host.showAppScreen("SellerDashboard");
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, host.ui.dp(44), 1);
            if (!option.equals("All time")) params.leftMargin = host.ui.dp(6);
            filters.addView(pill, params);
        }
        content.addView(filters);

        LinearLayout results = new LinearLayout(host);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        load(results);
    }

    private void load(LinearLayout results) {
        results.removeAllViews();
        TextView loading = host.ui.bodyText("Pulling your dashboard together...", host.muted, 12);
        loading.setPadding(0, host.ui.dp(20), 0, host.ui.dp(12));
        results.addView(loading);

        final String uid = CloudSync.uid(host);
        if (uid.isEmpty()) {
            results.removeAllViews();
            results.addView(host.ui.emptyCard("Sign in again to load your seller dashboard."));
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.android.gms.tasks.Task<QuerySnapshot> listings = db.collection("listings")
                .whereEqualTo("sellerID", uid).get();
        com.google.android.gms.tasks.Task<QuerySnapshot> orders = db.collection("orders")
                .whereEqualTo("sellerId", uid).get();
        com.google.android.gms.tasks.Task<QuerySnapshot> ratings = db.collection("ratings")
                .whereEqualTo("reviewedUserID", uid).get();

        Tasks.whenAllSuccess(listings, orders, ratings)
                .addOnSuccessListener(list -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    Stats stats = Stats.fromCloud(host, (QuerySnapshot) list.get(0), (QuerySnapshot) list.get(1),
                            (QuerySnapshot) list.get(2), cutoff());
                    stats.source = "Live from Firestore";
                    host.runOnUiThread(() -> {
                        if (!host.isDestroyed() && !host.isFinishing()) render(results, stats);
                    });
                })
                .addOnFailureListener(error -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    Stats stats = Stats.fromPhone(host, cutoff());
                    stats.source = "Saved on this phone";
                    host.runOnUiThread(() -> {
                        if (!host.isDestroyed() && !host.isFinishing()) render(results, stats);
                        results.addView(host.ui.bodyText("Offline copy shown. Pull down or reopen to refresh from Firestore.",
                                host.muted, 11));
                    });
                });
    }

    private void render(LinearLayout results, Stats stats) {
        results.removeAllViews();
        results.addView(host.ui.bodyText(stats.source, host.muted, 10));
        ArrayList<MainActivity.TransactionRecord> completed = stats.completed;
        long cutoff = cutoff();
        int pending = 0, held = 0;
        BigDecimal salesValue = BigDecimal.ZERO;
        BigDecimal returnedValue = BigDecimal.ZERO;
        int approvedReturns = 0;
        for (MainActivity.TransactionRecord order : stats.orders) {
            if (order.createdMillis() < cutoff) continue;
            if (order.status.equals(MainActivity.TransactionRecord.REQUESTED)) pending++;
            else if (order.status.equals(MainActivity.TransactionRecord.HELD)) held++;
        }
        // Keep the money figure on the same filtered set as the completed-sale
        // count, then subtract every approved return from both.
        for (MainActivity.TransactionRecord order : completed) {
            salesValue = salesValue.add(order.value());
            if ("Approved".equalsIgnoreCase(order.returnOutcome)) {
                approvedReturns++;
                returnedValue = returnedValue.add(order.value());
                salesValue = salesValue.subtract(order.value());
            }
        }

        int active = 0, reserved = 0, sold = 0, other = 0, views = 0, favourites = 0;
        for (MainActivity.Product product : stats.products) {
            views += product.viewCount;
            favourites += product.favouriteCount;
            if (product.hidden || product.status.equals("Removed")) other++;
            else if (product.status.equals("Available")) active++;
            else if (product.status.equals("Reserved") || product.status.equals("Ordered")) reserved++;
            else if (product.status.equals("Sold")) sold++;
            else other++;
        }

        host.profile.settingsHeading(results, "Performance");
        LinearLayout first = host.ui.horizontal();
        first.addView(kpi("My listings", String.valueOf(stats.products.size()), "All statuses"), cell(false));
        first.addView(kpi("Active now", String.valueOf(active), "Visible to buyers"), cell(true));
        results.addView(first);
        LinearLayout second = host.ui.horizontal();
        second.addView(kpi("Completed sales", String.valueOf(completed.size()), "In selected period"), cell(false));
        second.addView(kpi("Recorded value", "R" + money(salesValue), "After approved returns"), cell(true));
        results.addView(second);
        LinearLayout third = host.ui.horizontal();
        third.addView(kpi("Pending enquiries", String.valueOf(pending + held), pending + " new / " + held + " held"), cell(false));
        third.addView(kpi("Average rating", stats.ratingText(),
                stats.ratingCount + " review" + (stats.ratingCount == 1 ? "" : "s")), cell(true));
        results.addView(third);
        LinearLayout fourth = host.ui.horizontal();
        fourth.addView(kpi("Approved returns", String.valueOf(approvedReturns), "Deducted from value"), cell(false));
        fourth.addView(kpi("Returned value", "R" + money(returnedValue), "Not platform refunds"), cell(true));
        results.addView(fourth);

        List<AnalyticsChart.Entry> sales = salesSeries(completed);
        if (hasChartData(sales)) {
            host.profile.settingsHeading(results, "Sales value over time");
            addChart(results, "Recorded sales value", sales, AnalyticsChart.Mode.LINE,
                    "Completed orders minus approved returns. Recorded value isn't platform revenue.");
        }

        List<AnalyticsChart.Entry> status = statusSeries(active, reserved, sold, other);
        List<AnalyticsChart.Entry> interest = interestSeries(views, favourites);
        if (hasChartData(status) || hasChartData(interest)) {
            host.profile.settingsHeading(results, "Listings and demand");
            if (hasChartData(status)) {
                addChart(results, "Listing status", status, AnalyticsChart.Mode.BAR,
                        "Current state of your listings.");
            }
            if (hasChartData(interest)) {
                addChart(results, "Views and saves", interest, AnalyticsChart.Mode.BAR,
                        "Across all your listings.");
            }
        }

        List<AnalyticsChart.Entry> categories = categorySeries(completed);
        if (hasChartData(categories)) {
            host.profile.settingsHeading(results, "Sales by category");
            addChart(results, "Completed sales by category", categories, AnalyticsChart.Mode.HORIZONTAL_BAR,
                    "Categories saved with each completed order.");
        }

        List<AnalyticsChart.Entry> enquiries = enquirySeries(stats.orders);
        if (hasChartData(enquiries)) {
            host.profile.settingsHeading(results, "Enquiries");
            addChart(results, "Enquiries and outcomes", enquiries, AnalyticsChart.Mode.BAR,
                    "Purchase requests from the selected period.");
        }

        host.profile.settingsHeading(results, "Top listings");
        addTopListings(results, stats.products);

        host.profile.settingsHeading(results, "Quick actions");
        Button manage = host.ui.primaryButton("Manage my listings");
        manage.setOnClickListener(v -> {
            host.marketFilter = "Mine";
            host.showAppScreen("Market");
        });
        results.addView(manage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        Button add = host.ui.secondaryButton("List another item");
        add.setOnClickListener(v -> host.market.showAddProductDialog());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        addParams.topMargin = host.ui.dp(8);
        results.addView(add, addParams);
        results.addView(host.ui.space(1, host.ui.dp(20)));
    }

    private long cutoff() {
        if (range.equals("7 days")) return System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        if (range.equals("30 days")) return System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        if (range.equals("90 days")) return System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000;
        return 0;
    }

    private LinearLayout.LayoutParams cell(boolean right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.topMargin = host.ui.dp(10);
        if (right) params.leftMargin = host.ui.dp(10);
        return params;
    }

    private LinearLayout kpi(String label, String value, String note) {
        LinearLayout card = host.ui.card(host.surface(), 16, host.ui.dp(12));
        card.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        TextView title = host.ui.bodyText(label.toUpperCase(Locale.ROOT), host.muted, 10);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);
        TextView amount = host.ui.titleText(value, host.blue, 18);
        amount.setPadding(0, host.ui.dp(6), 0, host.ui.dp(2));
        amount.setSingleLine(true);
        amount.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(amount);
        card.addView(host.ui.bodyText(note, host.muted, 10));
        return card;
    }

    private boolean hasChartData(List<AnalyticsChart.Entry> data) {
        for (AnalyticsChart.Entry entry : data) if (entry.value > 0) return true;
        return false;
    }

    private void addChart(LinearLayout content, String title, List<AnalyticsChart.Entry> data,
                          AnalyticsChart.Mode mode, String note) {
        if (!hasChartData(data)) return;
        LinearLayout card = host.ui.card(host.surface(), 16, host.ui.dp(14));
        card.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        card.addView(host.ui.titleText(title, host.text, 14));
        card.addView(host.ui.bodyText(note, host.muted, 10));
        AnalyticsChart chart = new AnalyticsChart(host);
        chart.setData(data, mode, host.blue, host.text, host.muted);
        card.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(200)));
        content.addView(card);
    }

    private List<AnalyticsChart.Entry> salesSeries(List<MainActivity.TransactionRecord> completed) {
        Map<String, Float> grouped = new LinkedHashMap<>();
        for (MainActivity.TransactionRecord order : completed) {
            String key = range.equals("7 days") || range.equals("30 days")
                    ? new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(order.createdMillis()))
                    : new SimpleDateFormat("MMM yy", Locale.getDefault()).format(new Date(order.createdMillis()));
            float amount = "Approved".equalsIgnoreCase(order.returnOutcome) ? -order.value().floatValue()
                    : order.value().floatValue();
            grouped.put(key, Math.max(0f,
                    (grouped.containsKey(key) ? grouped.get(key) : 0f) + amount));
        }
        return entries(grouped);
    }

    private List<AnalyticsChart.Entry> categorySeries(List<MainActivity.TransactionRecord> completed) {
        Map<String, Float> grouped = new LinkedHashMap<>();
        for (MainActivity.TransactionRecord order : completed) {
            String key = order.category == null || order.category.isEmpty() ? "Other" : order.category;
            float amount = "Approved".equalsIgnoreCase(order.returnOutcome) ? -order.value().floatValue()
                    : order.value().floatValue();
            grouped.put(key, Math.max(0f,
                    (grouped.containsKey(key) ? grouped.get(key) : 0f) + amount));
        }
        return entries(grouped);
    }

    private List<AnalyticsChart.Entry> enquirySeries(List<MainActivity.TransactionRecord> orders) {
        int requested = 0, held = 0, completed = 0, cancelled = 0;
        long cutoff = cutoff();
        for (MainActivity.TransactionRecord order : orders) {
            if (order.createdMillis() < cutoff) continue;
            if (order.status.equals(MainActivity.TransactionRecord.REQUESTED)) requested++;
            else if (order.status.equals(MainActivity.TransactionRecord.HELD)) held++;
            else if (order.status.equals(MainActivity.TransactionRecord.COMPLETED)) completed++;
            else if (order.status.equals(MainActivity.TransactionRecord.CANCELLED)) cancelled++;
        }
        List<AnalyticsChart.Entry> out = new ArrayList<>();
        out.add(new AnalyticsChart.Entry("Requested", requested));
        out.add(new AnalyticsChart.Entry("Held", held));
        out.add(new AnalyticsChart.Entry("Completed", completed));
        out.add(new AnalyticsChart.Entry("Cancelled", cancelled));
        return out;
    }

    private List<AnalyticsChart.Entry> statusSeries(int active, int reserved, int sold, int other) {
        List<AnalyticsChart.Entry> out = new ArrayList<>();
        out.add(new AnalyticsChart.Entry("Active", active));
        out.add(new AnalyticsChart.Entry("Reserved", reserved));
        out.add(new AnalyticsChart.Entry("Sold", sold));
        out.add(new AnalyticsChart.Entry("Other", other));
        return out;
    }

    private List<AnalyticsChart.Entry> interestSeries(int views, int favourites) {
        List<AnalyticsChart.Entry> out = new ArrayList<>();
        out.add(new AnalyticsChart.Entry("Views", views));
        out.add(new AnalyticsChart.Entry("Saves", favourites));
        return out;
    }

    private void addTopListings(LinearLayout content, List<MainActivity.Product> products) {
        if (products.isEmpty()) {
            content.addView(host.ui.emptyCard("No listings yet. Your first listing will show up here."));
            return;
        }
        ArrayList<MainActivity.Product> sorted = new ArrayList<>(products);
        Collections.sort(sorted, (a, b) -> Integer.compare(b.viewCount, a.viewCount));
        LinearLayout card = host.ui.card(host.surface(), 16, host.ui.dp(14));
        card.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        int shown = 0;
        for (MainActivity.Product product : sorted) {
            if (shown++ >= 5) break;
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            TextView title = host.ui.titleText(product.title, host.text, 13);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(title);
            copy.addView(host.ui.bodyText(product.status + " / " + product.favouriteCount + " saves", host.muted, 10));
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(host.ui.bodyText(product.viewCount + " views", host.blue, 11));
            card.addView(row);
            if (shown < Math.min(5, sorted.size())) host.ui.addMenuDivider(card);
        }
        content.addView(card);
    }

    private List<AnalyticsChart.Entry> entries(Map<String, Float> map) {
        List<AnalyticsChart.Entry> out = new ArrayList<>();
        for (Map.Entry<String, Float> entry : map.entrySet()) {
            out.add(new AnalyticsChart.Entry(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static final class Stats {
        final ArrayList<MainActivity.Product> products = new ArrayList<>();
        final ArrayList<MainActivity.TransactionRecord> orders = new ArrayList<>();
        final ArrayList<MainActivity.TransactionRecord> completed = new ArrayList<>();
        int ratingCount = 0;
        double ratingAverage = 0;
        String source = "";

        static Stats fromCloud(MainActivity host, QuerySnapshot listingSnap, QuerySnapshot orderSnap,
                               QuerySnapshot ratingSnap, long cutoff) {
            Stats stats = new Stats();

            for (DocumentSnapshot doc : listingSnap.getDocuments()) {
                String title = doc.getString("listingN") == null ? "Listing" : doc.getString("listingN");
                double price = number(doc.get("price"));
                String description = doc.getString("description") == null ? "" : doc.getString("description");
                String category = doc.getString("category") == null ? "Other" : doc.getString("category");
                String condition = doc.getString("condition") == null ? "" : doc.getString("condition");
                String location = doc.getString("location") == null ? "" : doc.getString("location");
                String delivery = doc.getString("delivery") == null ? "" : doc.getString("delivery");
                String status = titleStatus(doc.getString("status"));
                MainActivity.Product product = new MainActivity.Product(title, String.valueOf(price), description,
                        host.currentStudentEmail, category, condition, location, delivery, status, status,
                        doc.getId());
                product.status = status;
                product.createdTime = time(doc.get("createdAt"));
                product.updatedTime = time(doc.get("updatedAt"));
                product.viewCount = (int) number(doc.get("viewCount"));
                product.favouriteCount = (int) number(doc.get("favouriteCount"));
                stats.products.add(product);
            }

            for (DocumentSnapshot doc : orderSnap.getDocuments()) {
                String status = localStatus(doc.getString("status"));
                String buyerEmail = doc.getString("buyerEmail");
                if (buyerEmail == null || buyerEmail.isEmpty()) {
                    buyerEmail = CloudSync.emailForUid(host, doc.getString("buyerId"));
                }
                if (buyerEmail == null || buyerEmail.isEmpty()) buyerEmail = "buyer@campus.local";
                String title = doc.getString("title") == null ? "Order" : doc.getString("title");
                String category = doc.getString("category") == null ? "Other" : doc.getString("category");
                String method = doc.getString("method") == null ? "Cash on collection" : doc.getString("method");
                String buyerName = doc.getString("buyerName") == null ? "Student" : doc.getString("buyerName");
                try {
                    MainActivity.TransactionRecord order = new MainActivity.TransactionRecord(doc.getId(), title,
                            String.valueOf(number(doc.get("price"))), method, buyerName,
                            doc.getString("buyerPhone") == null ? "" : doc.getString("buyerPhone"),
                            doc.getString("reference") == null ? "" : doc.getString("reference"),
                            dateText(doc.get("createdAt")), host.currentStudentEmail, buyerEmail, category, status,
                            doc.getString("listingId") == null ? "" : doc.getString("listingId"));
                    order.createdTime = time(doc.get("createdAt"));
                    String sellerName = doc.getString("sellerName");
                    if (sellerName != null) order.sellerName = sellerName;
                    order.returnOutcome = doc.getString("returnOutcome") == null ? "" : doc.getString("returnOutcome");
                    order.returnReason = doc.getString("returnReason") == null ? "" : doc.getString("returnReason");
                    Long returnTime = doc.getLong("returnTime");
                    order.returnTime = returnTime == null ? 0 : returnTime;
                    stats.orders.add(order);
                    if (status.equals(MainActivity.TransactionRecord.COMPLETED)
                            && order.createdMillis() >= cutoff) stats.completed.add(order);
                } catch (IllegalArgumentException | ArithmeticException ignored) { }
            }

            double ratingTotal = 0;
            for (DocumentSnapshot doc : ratingSnap.getDocuments()) {
                double rating = number(doc.get("rating"));
                if (rating > 0) {
                    ratingTotal += rating;
                    stats.ratingCount++;
                }
            }
            stats.ratingAverage = stats.ratingCount == 0 ? 0 : ratingTotal / stats.ratingCount;
            return stats;
        }

        static Stats fromPhone(MainActivity host, long cutoff) {
            Stats stats = new Stats();
            for (MainActivity.Product product : host.products) {
                if (!host.market.ownsListing(product)) continue;
                stats.products.add(product);
            }
            for (MainActivity.TransactionRecord order : host.transactions) {
                if (!order.isSeller(host.currentStudentEmail)) continue;
                stats.orders.add(order);
                if (order.status.equals(MainActivity.TransactionRecord.COMPLETED)
                        && order.createdMillis() >= cutoff) stats.completed.add(order);
            }
            double rating = host.featureStore.data.rating(host.currentStudentEmail);
            stats.ratingAverage = rating;
            stats.ratingCount = rating > 0 ? 1 : 0;
            return stats;
        }

        String ratingText() {
            return ratingAverage <= 0 ? "New seller" : RatingStars.of(ratingAverage);
        }

        private static String titleStatus(String cloud) {
            if (cloud == null) return "Other";
            if (cloud.equalsIgnoreCase("AVAILABLE")) return "Available";
            if (cloud.equalsIgnoreCase("RESERVED")) return "Reserved";
            if (cloud.equalsIgnoreCase("ORDERED")) return "Ordered";
            if (cloud.equalsIgnoreCase("SOLD")) return "Sold";
            if (cloud.equalsIgnoreCase("PAUSED")) return "Paused";
            return "Other";
        }

        private static String localStatus(String cloud) {
            if (cloud == null) return MainActivity.TransactionRecord.HELD;
            if (cloud.equalsIgnoreCase("REQUESTED")) return MainActivity.TransactionRecord.REQUESTED;
            if (cloud.equalsIgnoreCase("COMPLETED")) return MainActivity.TransactionRecord.COMPLETED;
            if (cloud.equalsIgnoreCase("CANCELLED")) return MainActivity.TransactionRecord.CANCELLED;
            return MainActivity.TransactionRecord.HELD;
        }

        private static long time(Object value) {
            if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
            if (value instanceof Number) return ((Number) value).longValue();
            return 0;
        }

        private static String dateText(Object value) {
            long millis = time(value);
            return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(new Date(millis > 0 ? millis : System.currentTimeMillis()));
        }

        private static double number(Object value) {
            return value instanceof Number ? ((Number) value).doubleValue() : 0;
        }
    }
}
