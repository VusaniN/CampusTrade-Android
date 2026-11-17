package com.campustrade.auth;

import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Short, swipe-free onboarding tour for new students and returning users. */
public final class Tutorial {

    private static final String[][] SLIDES = {
            {"Welcome to CampusTrade", "This is your Eduvos campus marketplace and study companion. You can buy and sell items, message other students, manage orders, share notes and stay on top of deadlines from one app.", "bag"},
            {"Browse the Marketplace", "Open Market to search, filter and sort listings. Tap a listing for photos, price, condition, seller details, delivery options and tech specs. Saving a listing shows Saved only to you; other students see Reserved.", "tag"},
            {"List something for sale", "Market > List an item. Add at least one photo, a title, category, condition, price and description. Choose Campus pickup or Delivery. Technology listings have a tech-specs field. You can save a draft before publishing.", "note"},
            {"Offers and checkout", "Buyers can make offers. A seller can accept, counter or decline. An accepted offer reserves the item for 24 hours at the agreed price. Checkout supports Cash on Collection or EFT between students — CampusTrade never processes or holds money.", "tag"},
            {"Delivery and collection", "Delivery orders ask for an address and show a live map while the map is open. At handover the buyer photographs the item and the seller verifies that photo before the order completes. The verified sale generates an invoice email.", "pin"},
            {"Orders and notifications", "Orders has Buying and Selling tabs with live updates and status filters. Order notifications are separate from Chats and Updates, so every purchase decision opens the exact order. Pickup changes are versioned and need both students to confirm again.", "checklist"},
            {"Chat and safety", "Use Inbox for direct messages and Marketplace conversations. Meet in public campus spots, inspect items before paying and never share passwords or one-time codes. Report unsafe listings, reviews or students. The Safety center has emergency dial and scam guidance.", "shield"},
            {"Study and groups", "Study holds notes, deadlines, events and a calendar. Join or create study groups, share notes and post updates. Use Wanted for items you need and save searches to get alerts when matching listings appear.", "team"},
            {"Your profile and Settings", "Profile and Settings cover your photo, programme, reviews, notifications, quiet hours, privacy, app lock, legal terms, feedback and this tutorial. The CampusTrade Helper can answer questions about any screen.", "profile"}
    };

    private Tutorial() { }

    /** Called after a successful sign-in; shows once per student account. */
    public static void showOnboarding(MainActivity host) {
        if (host == null || host.currentStudentEmail.isEmpty()) return;
        String key = "seen:" + host.currentStudentEmail;
        if (host.getSharedPreferences(host.PREFS + "_tutorial", android.content.Context.MODE_PRIVATE)
                .getBoolean(key, false)) return;
        host.getWindow().getDecorView().postDelayed(() -> show(host), 700);
    }

    public static void show(MainActivity host) {
        if (host == null) return;
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("CampusTrade tutorial", "A quick tour of the app", page::dismiss));

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(24), host.ui.dp(10), host.ui.dp(24), host.ui.dp(18));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView step = host.ui.tinyCaps("STEP 1 OF " + SLIDES.length, host.muted);
        step.setGravity(Gravity.CENTER);
        content.addView(step);
        View icon = host.new CampusAssetView(host, SLIDES[0][2], host.blue);
        icon.setPadding(host.ui.dp(22), host.ui.dp(22), host.ui.dp(22), host.ui.dp(22));
        icon.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), 999));
        final LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(host.ui.dp(92), host.ui.dp(92));
        iconParams.topMargin = host.ui.dp(18);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(icon, iconParams);
        final View[] iconRef = {icon};
        TextView title = host.ui.titleText(SLIDES[0][0], host.text, 24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, host.ui.dp(18), 0, host.ui.dp(8));
        content.addView(title);
        TextView body = host.ui.bodyText(SLIDES[0][1], host.muted, 14);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(host.ui.dp(3), 1f);
        content.addView(body);

        LinearLayout footer = new LinearLayout(host);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(host.ui.dp(24), host.ui.dp(10), host.ui.dp(24), host.ui.dp(18));
        footer.setBackgroundColor(host.surface());
        LinearLayout buttons = host.ui.horizontal();
        Button back = host.ui.secondaryButton("Back");
        Button next = host.ui.primaryButton("Next");
        buttons.addView(back, new LinearLayout.LayoutParams(0, host.ui.dp(50), 1));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, host.ui.dp(50), 1);
        nextParams.leftMargin = host.ui.dp(8);
        buttons.addView(next, nextParams);
        footer.addView(buttons);
        TextView skip = host.ui.textButton("Skip tutorial");
        skip.setGravity(Gravity.CENTER);
        footer.addView(skip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(38)));
        root.addView(footer);

        final int[] index = {0};
        final Runnable render = () -> {
            int i = index[0];
            step.setText("STEP " + (i + 1) + " OF " + SLIDES.length);
            title.setText(SLIDES[i][0]);
            body.setText(SLIDES[i][1]);
            back.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
            next.setText(i == SLIDES.length - 1 ? "Finish" : "Next");
            // Replace the icon without rebuilding the whole page.
            ViewGroup parent = (ViewGroup) iconRef[0].getParent();
            if (parent != null) {
                int position = parent.indexOfChild(iconRef[0]);
                View replacement = host.new CampusAssetView(host, SLIDES[i][2], host.blue);
                replacement.setPadding(host.ui.dp(22), host.ui.dp(22), host.ui.dp(22), host.ui.dp(22));
                replacement.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), 999));
                parent.removeViewAt(position);
                parent.addView(replacement, position, iconParams);
                iconRef[0] = replacement;
            }
        };
        back.setOnClickListener(v -> {
            if (index[0] > 0) {
                index[0]--;
                render.run();
            }
        });
        next.setOnClickListener(v -> {
            if (index[0] < SLIDES.length - 1) {
                index[0]++;
                render.run();
            } else {
                page.dismiss();
            }
        });
        skip.setOnClickListener(v -> page.dismiss());
        page.setOnDismissListener(d -> markSeen(host));
        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static void markSeen(MainActivity host) {
        if (host.currentStudentEmail.isEmpty()) return;
        host.getSharedPreferences(host.PREFS + "_tutorial", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("seen:" + host.currentStudentEmail, true).apply();
    }
}