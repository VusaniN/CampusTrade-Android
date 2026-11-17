package com.campustrade.auth;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Admin charts built from live Firebase data. */
public final class AdminAnalytics {

    private final MainActivity host;
    private String range = "All time";
    private boolean loading = false;

    public AdminAnalytics(MainActivity host) {
        this.host = host;
    }

    public void build(LinearLayout content) {
        if (!host.adminAccount) {
            content.addView(host.ui.emptyCard("Marketplace analytics require a moderator account. "
                    + "Set isAdmin = true on the moderator's users document in Firestore, then sign in again."));
            return;
        }
        content.addView(host.ui.bodyText("The whole marketplace, straight from Firestore. Recorded value is what students paid, not platform revenue.",
                host.muted, 12));
        LinearLayout filters = host.ui.horizontal();
        filters.setPadding(0, host.ui.dp(12), 0, host.ui.dp(4));
        for (String option : new String[]{"All time", "7 days", "30 days", "90 days"}) {
            TextView pill = host.ui.filterPill(option, option.equals(range));
            pill.setOnClickListener(v -> {
                range = option;
                host.showAppScreen("AdminAnalytics");
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
        loading = true;
        results.removeAllViews();
        TextView loadingText = host.ui.bodyText("Loading analytics from Firestore...", host.muted, 12);
        loadingText.setPadding(0, host.ui.dp(20), 0, host.ui.dp(12));
        results.addView(loadingText);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.android.gms.tasks.Task<QuerySnapshot> users = db.collection("users").get();
        com.google.android.gms.tasks.Task<QuerySnapshot> listings = db.collection("listings").get();
        com.google.android.gms.tasks.Task<QuerySnapshot> orders = db.collection("orders").get();
        com.google.android.gms.tasks.Task<QuerySnapshot> reports = db.collection("reports").get();

        Tasks.whenAllSuccess(users, listings, orders, reports)
                .addOnSuccessListener(list -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    Stats stats = new Stats();
                    stats.read((QuerySnapshot) list.get(0), (QuerySnapshot) list.get(1),
                            (QuerySnapshot) list.get(2), (QuerySnapshot) list.get(3), cutoff());
                    loading = false;
                    host.runOnUiThread(() -> {
                        if (!host.isDestroyed() && !host.isFinishing()) render(results, stats);
                    });
                })
                .addOnFailureListener(error -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    loading = false;
                    results.removeAllViews();
                    results.addView(host.ui.bodyText("Couldn't load analytics: " + safe(error.getMessage()),
                            host.readableInk(Color.rgb(180, 40, 40)), 12));
                    Button retry = host.ui.secondaryButton("Try again");
                    retry.setOnClickListener(v -> load(results));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
                    params.topMargin = host.ui.dp(12);
                    results.addView(retry, params);
                });
    }

    private void render(LinearLayout results, Stats stats) {
        results.removeAllViews();
        boolean anyActivity = stats.users > 0 || stats.listings > 0 || stats.completedOrders > 0
                || stats.pendingOrders > 0 || stats.cancelledOrders > 0 || stats.reports > 0
                || stats.openReportsAll > 0;
        if (!anyActivity) {
            results.addView(host.ui.emptyState("bag",
                    range.equals("All time") ? "No marketplace activity yet" : "No activity in this period",
                    range.equals("All time")
                            ? "Analytics will appear as students join, list items and trade."
                            : "Try a wider date range to see earlier activity."));
            return;
        }

        host.profile.settingsHeading(results, "Marketplace overview");
        LinearLayout first = host.ui.horizontal();
        first.addView(kpi("Registered users", String.valueOf(stats.users), "New in period"), cell(false));
        first.addView(kpi("Active users", String.valueOf(stats.activeUsers), "In selected period"), cell(true));
        results.addView(first);
        LinearLayout second = host.ui.horizontal();
        second.addView(kpi("Sellers", String.valueOf(stats.sellers), "Listed something"), cell(false));
        second.addView(kpi("Buyers", String.valueOf(stats.buyers), "Sent a request"), cell(true));
        results.addView(second);
        LinearLayout third = host.ui.horizontal();
        third.addView(kpi("Listings", String.valueOf(stats.listings), stats.activeListings + " active"), cell(false));
        third.addView(kpi("Sold listings", String.valueOf(stats.soldListings), stats.reservedListings + " reserved"), cell(true));
        results.addView(third);
        LinearLayout fourth = host.ui.horizontal();
        fourth.addView(kpi("Completed orders", String.valueOf(stats.completedOrders), "Sales in period"), cell(false));
        fourth.addView(kpi("Recorded value", "R" + money(stats.completedValue),
                "After approved returns"), cell(true));
        results.addView(fourth);
        LinearLayout fifth = host.ui.horizontal();
        fifth.addView(kpi("Pending orders", String.valueOf(stats.pendingOrders), stats.cancelledOrders + " cancelled"), cell(false));
        fifth.addView(kpi("Approved returns", String.valueOf(stats.approvedReturns),
                "R" + money(stats.returnedValue) + " subtracted"), cell(true));
        results.addView(fifth);
        LinearLayout sixth = host.ui.horizontal();
        sixth.addView(kpi("Reports", String.valueOf(stats.reports), stats.openReportsAll + " still open"), cell(false));
        sixth.addView(kpi("Report types", stats.reportListing + " / " + stats.reportReview + " / " + stats.reportUser,
                "Listing / review / student"), cell(true));
        results.addView(sixth);

        if (hasChartData(stats.userGrowth)) {
            host.profile.settingsHeading(results, "User growth");
            addChart(results, "New registrations", stats.userGrowth, AnalyticsChart.Mode.LINE,
                    "Students who joined in this period.");
        }

        if (hasChartData(stats.categories) || hasChartData(stats.listingStatus)) {
            host.profile.settingsHeading(results, "Listings");
            if (hasChartData(stats.categories)) {
                addChart(results, "Listings by category", stats.categories, AnalyticsChart.Mode.HORIZONTAL_BAR,
                        "New listings in this period, grouped by category.");
            }
            if (hasChartData(stats.listingStatus)) {
                addChart(results, "Listing status", stats.listingStatus, AnalyticsChart.Mode.BAR,
                        "Where the listings created in this period stand now.");
            }
        }

        if (hasChartData(stats.transactionValue) || hasChartData(stats.orderStatus)) {
            host.profile.settingsHeading(results, "Transactions");
            if (hasChartData(stats.transactionValue)) {
                addChart(results, "Recorded sale value", stats.transactionValue, AnalyticsChart.Mode.LINE,
                        "Completed orders minus approved returns. This is recorded value, not platform revenue.");
            }
            if (hasChartData(stats.orderStatus)) {
                addChart(results, "Orders by status", stats.orderStatus, AnalyticsChart.Mode.BAR,
                        "All orders created in this period.");
            }
        }

        if (hasChartData(stats.reportStatus) || hasChartData(stats.reportTypes) || stats.openReportsAll > 0) {
            host.profile.settingsHeading(results, "Reports");
            if (hasChartData(stats.reportStatus)) {
                addChart(results, "Reports by status", stats.reportStatus, AnalyticsChart.Mode.BAR,
                        "Reports created in this period.");
            }
            if (hasChartData(stats.reportTypes)) {
                addChart(results, "Reports by reported entity", stats.reportTypes, AnalyticsChart.Mode.BAR,
                        "Each listing, review and student account is counted by its real report target.");
            }
            addReportSummary(results, stats);
        }

        if (!stats.recentUsers.isEmpty() || !stats.recentListings.isEmpty() || !stats.recentOrders.isEmpty()) {
            host.profile.settingsHeading(results, "Recent activity");
            addRecent(results, "Newest users", stats.recentUsers);
            addRecent(results, "Newest listings", stats.recentListings);
            addRecent(results, "Newest orders", stats.recentOrders);
        }
        results.addView(host.ui.space(1, host.ui.dp(20)));
    }

    private boolean hasChartData(List<AnalyticsChart.Entry> data) {
        for (AnalyticsChart.Entry entry : data) if (entry.value > 0) return true;
        return false;
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

    private void addReportSummary(LinearLayout content, Stats stats) {
        LinearLayout card = host.ui.card(host.surface(), 16, host.ui.dp(14));
        card.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        card.addView(host.ui.titleText("Moderation summary", host.text, 14));
        card.addView(host.ui.bodyText(stats.reports + (stats.reports == 1 ? " new report" : " new reports")
                + " in this period · " + stats.openReports + " still open from this period", host.muted, 11));
        card.addView(host.ui.bodyText("Listings " + stats.reportListing + " · Reviews " + stats.reportReview
                + " · Students " + stats.reportUser, host.text, 12));
        card.addView(host.ui.bodyText("Reported targets are stored as listing, review or student entities, so totals never merge the three.", host.muted, 11));
        card.addView(host.ui.bodyText("Open moderation to review reports and account actions.", host.muted, 11));
        card.setOnClickListener(v -> host.showAppScreen("AdminReports"));
        content.addView(card);
    }

    private void addRecent(LinearLayout content, String title, List<String> rows) {
        if (rows.isEmpty()) return;
        LinearLayout card = host.ui.card(host.surface(), 16, host.ui.dp(14));
        card.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        card.addView(host.ui.titleText(title, host.text, 14));
        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            TextView row = host.ui.bodyText(rows.get(i), host.muted, 11);
            row.setPadding(0, host.ui.dp(6), 0, host.ui.dp(6));
            card.addView(row);
            if (i < Math.min(5, rows.size()) - 1) host.ui.addMenuDivider(card);
        }
        content.addView(card);
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "Check your connection and try again." : value;
    }

    private static final class Stats {
        static final String[] CATEGORIES = {"Books", "Technology", "Stationery", "Other"};
        int users, activeUsers, sellers, buyers;
        int listings, activeListings, reservedListings, soldListings;
        int completedOrders, pendingOrders, cancelledOrders, approvedReturns;
        int reports, openReports, openReportsAll;
        int reportListing, reportReview, reportUser;
        BigDecimal completedValue = BigDecimal.ZERO;
        BigDecimal returnedValue = BigDecimal.ZERO;
        final List<AnalyticsChart.Entry> userGrowth = new ArrayList<>();
        final List<AnalyticsChart.Entry> categories = new ArrayList<>();
        final List<AnalyticsChart.Entry> listingStatus = new ArrayList<>();
        final List<AnalyticsChart.Entry> transactionValue = new ArrayList<>();
        final List<AnalyticsChart.Entry> orderStatus = new ArrayList<>();
        final List<AnalyticsChart.Entry> reportStatus = new ArrayList<>();
        final List<AnalyticsChart.Entry> reportTypes = new ArrayList<>();
        final List<String> recentUsers = new ArrayList<>();
        final List<String> recentListings = new ArrayList<>();
        final List<String> recentOrders = new ArrayList<>();

        void read(QuerySnapshot userSnap, QuerySnapshot listingSnap, QuerySnapshot orderSnap,
                  QuerySnapshot reportSnap, long cutoff) {
            Set<String> sellerIds = new LinkedHashSet<>();
            Set<String> buyerIds = new LinkedHashSet<>();
            Map<String, Float> userMap = new TreeMap<>();
            Map<String, Float> categoryMap = new LinkedHashMap<>();
            for (String category : CATEGORIES) categoryMap.put(category, 0f);
            Map<String, Float> valueMap = new TreeMap<>();
            int active = 0, reserved = 0, sold = 0, other = 0;
            int completed = 0, pending = 0, cancelled = 0;
            int open = 0, resolved = 0, openAll = 0;
            int listingReports = 0, reviewReports = 0, userReports = 0;
            int returns = 0;
            BigDecimal value = BigDecimal.ZERO;
            BigDecimal returned = BigDecimal.ZERO;
            ArrayList<DocRow> recentUserRows = new ArrayList<>();
            ArrayList<DocRow> recentListingRows = new ArrayList<>();
            ArrayList<DocRow> recentOrderRows = new ArrayList<>();

            for (DocumentSnapshot doc : userSnap.getDocuments()) {
                long created = millis(doc.get("createdAt"));
                if (created <= 0) created = millis(doc.get("updatedAt"));
                boolean newInPeriod = cutoff == 0 || created >= cutoff;
                if (newInPeriod) users++;
                // Someone may have joined before the chosen period.
                // Count active users by their last active time.
                Long activeStamp = doc.getLong("lastActive");
                if (cutoff == 0 || (activeStamp != null && activeStamp >= cutoff)) activeUsers++;
                if (!newInPeriod) continue;
                String bucket = month(created);
                if (!bucket.isEmpty()) userMap.put(bucket,
                        (userMap.containsKey(bucket) ? userMap.get(bucket) : 0f) + 1f);
                String name = doc.getString("stuName");
                if (name == null || name.isEmpty()) name = doc.getString("stuEmail");
                if (name == null || name.isEmpty()) name = "Student";
                recentUserRows.add(new DocRow(created, name));
            }

            for (DocumentSnapshot doc : listingSnap.getDocuments()) {
                long created = millis(doc.get("createdAt"));
                if (cutoff > 0 && created < cutoff) continue;
                listings++;
                String status = safe(doc.getString("status")).toUpperCase(Locale.ROOT);
                String seller = safe(doc.getString("sellerID"));
                if (!seller.isEmpty()) sellerIds.add(seller);
                String category = safe(doc.getString("category")).trim();
                if (category.isEmpty()) category = "Other";
                boolean knownCategory = false;
                for (String option : CATEGORIES) {
                    if (option.equalsIgnoreCase(category)) { category = option; knownCategory = true; break; }
                }
                if (!knownCategory) category = "Other";
                categoryMap.put(category,
                        (categoryMap.containsKey(category) ? categoryMap.get(category) : 0f) + 1f);
                if (status.equals("AVAILABLE")) active++;
                else if (status.equals("RESERVED") || status.equals("ORDERED")) reserved++;
                else if (status.equals("SOLD")) sold++;
                else other++;
                String title = safe(doc.getString("listingN"));
                if (title.isEmpty()) title = "Listing";
                recentListingRows.add(new DocRow(created, title));
            }

            for (DocumentSnapshot doc : orderSnap.getDocuments()) {
                long created = millis(doc.get("createdAt"));
                if (cutoff > 0 && created < cutoff) continue;
                String buyer = safe(doc.getString("buyerId"));
                if (!buyer.isEmpty()) buyerIds.add(buyer);
                String status = safe(doc.getString("status")).toUpperCase(Locale.ROOT);
                String category = safe(doc.getString("category"));
                if (category.isEmpty()) category = "Other";
                double amount = number(doc.get("price"));
                if (status.equals("COMPLETED")) {
                    completed++;
                    value = value.add(BigDecimal.valueOf(amount));
                    String bucket = month(created);
                    if (!bucket.isEmpty()) valueMap.put(bucket,
                            (valueMap.containsKey(bucket) ? valueMap.get(bucket) : 0f) + (float) amount);
                    // An approved return reduces both the headline value and
                    // the monthly chart by the returned sale amount.
                    if ("Approved".equalsIgnoreCase(safe(doc.getString("returnOutcome")))) {
                        returns++;
                        returned = returned.add(BigDecimal.valueOf(amount));
                        value = value.subtract(BigDecimal.valueOf(amount));
                        if (!bucket.isEmpty()) {
                            valueMap.put(bucket, Math.max(0f,
                                    (valueMap.containsKey(bucket) ? valueMap.get(bucket) : 0f) - (float) amount));
                        }
                    }
                } else if (status.equals("REQUESTED") || status.equals("HELD")) pending++;
                else if (status.equals("CANCELLED")) cancelled++;
                String title = safe(doc.getString("title"));
                if (title.isEmpty()) title = "Order";
                recentOrderRows.add(new DocRow(created, title));
            }

            for (DocumentSnapshot doc : reportSnap.getDocuments()) {
                String state = safe(doc.getString("state"));
                if (state.equals("Open")) openAll++;
                long created = millis(doc.get("createdAt"));
                if (cutoff > 0 && created < cutoff) continue;
                reports++;
                if (state.equals("Open")) open++; else resolved++;
                String productId = safe(doc.getString("productId"));
                String entity = safe(doc.getString("entityType")).toLowerCase(Locale.ROOT);
                if (entity.isEmpty()) {
                    if (productId.startsWith("review:")) entity = "review";
                    else if (productId.startsWith("user:")) entity = "user";
                    else entity = "listing";
                }
                if (entity.equals("review")) reviewReports++;
                else if (entity.equals("user")) userReports++;
                else listingReports++;
            }

            activeListings = active;
            reservedListings = reserved;
            soldListings = sold;
            sellers = sellerIds.size();
            buyers = buyerIds.size();
            completedOrders = completed;
            pendingOrders = pending;
            cancelledOrders = cancelled;
            approvedReturns = returns;
            openReports = open;
            openReportsAll = openAll;
            reports = open + resolved;
            reportListing = listingReports;
            reportReview = reviewReports;
            reportUser = userReports;
            completedValue = value;
            returnedValue = returned;
            userGrowth.addAll(entriesTime(userMap));
            categories.addAll(entriesNonZero(categoryMap));
            transactionValue.addAll(entriesTime(valueMap));
            listingStatus.add(new AnalyticsChart.Entry("Active", active));
            listingStatus.add(new AnalyticsChart.Entry("Reserved", reserved));
            listingStatus.add(new AnalyticsChart.Entry("Sold", sold));
            listingStatus.add(new AnalyticsChart.Entry("Other", other));
            orderStatus.add(new AnalyticsChart.Entry("Completed", completed));
            orderStatus.add(new AnalyticsChart.Entry("Pending", pending));
            orderStatus.add(new AnalyticsChart.Entry("Cancelled", cancelled));
            reportStatus.add(new AnalyticsChart.Entry("Open", open));
            reportStatus.add(new AnalyticsChart.Entry("Resolved", resolved));
            reportTypes.add(new AnalyticsChart.Entry("Listings", listingReports));
            reportTypes.add(new AnalyticsChart.Entry("Reviews", reviewReports));
            reportTypes.add(new AnalyticsChart.Entry("Students", userReports));
            recentUsers.addAll(recent(recentUserRows));
            recentListings.addAll(recent(recentListingRows));
            recentOrders.addAll(recent(recentOrderRows));
        }

        private List<AnalyticsChart.Entry> entries(Map<String, Float> map) {
            List<AnalyticsChart.Entry> out = new ArrayList<>();
            for (Map.Entry<String, Float> entry : map.entrySet()) {
                out.add(new AnalyticsChart.Entry(entry.getKey(), entry.getValue()));
            }
            return out;
        }

        // Keep the category order and hide empty categories.
        private List<AnalyticsChart.Entry> entriesNonZero(Map<String, Float> map) {
            List<AnalyticsChart.Entry> out = new ArrayList<>();
            for (Map.Entry<String, Float> entry : map.entrySet()) {
                if (entry.getValue() > 0) out.add(new AnalyticsChart.Entry(entry.getKey(), entry.getValue()));
            }
            return out;
        }

        private List<AnalyticsChart.Entry> entriesTime(Map<String, Float> map) {
            List<AnalyticsChart.Entry> out = new ArrayList<>();
            for (Map.Entry<String, Float> entry : map.entrySet()) {
                String label = entry.getKey();
                try {
                    Date parsed = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(entry.getKey());
                    if (parsed != null) label = new SimpleDateFormat("MMM yy", Locale.getDefault()).format(parsed);
                } catch (java.text.ParseException ignored) { }
                out.add(new AnalyticsChart.Entry(label, entry.getValue()));
            }
            return out;
        }

        private List<String> recent(ArrayList<DocRow> rows) {
            Collections.sort(rows, (a, b) -> Long.compare(b.time, a.time));
            List<String> out = new ArrayList<>();
            for (int i = 0; i < Math.min(5, rows.size()); i++) out.add(rows.get(i).label);
            return out;
        }

        private static String month(long time) {
            if (time <= 0) return "";
            return new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date(time));
        }

        private static long millis(Object value) {
            if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
            if (value instanceof Number) return ((Number) value).longValue();
            return 0;
        }

        private static double number(Object value) {
            return value instanceof Number ? ((Number) value).doubleValue() : 0;
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static final class DocRow {
            final long time;
            final String label;
            DocRow(long time, String label) { this.time = time; this.label = label; }
        }
    }
}
