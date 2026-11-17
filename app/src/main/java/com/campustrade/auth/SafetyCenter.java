package com.campustrade.auth;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Campus safety guidance, emergency contacts and permission transparency. */
public final class SafetyCenter {
    private SafetyCenter() { }

    public static void show(MainActivity host) {
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Safety center", "Meet safely and protect your account", page::dismiss));

        android.widget.ScrollView scroll = new android.widget.ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        content.addView(host.ui.titleText("Before you meet", host.text, 16));
        content.addView(bullet(host, "Meet in a public campus spot during daylight and bring a friend if you can."));
        content.addView(bullet(host, "Inspect the item before paying. CampusTrade does not process payments or refunds."));
        content.addView(bullet(host, "Never share your password, bank PIN or one-time codes."));
        content.addView(bullet(host, "Keep chat inside CampusTrade until you trust the other student."));

        content.addView(host.ui.titleText("Scam warning", host.text, 16));
        content.addView(host.ui.bodyText("Be careful of prices that are too good to be true, requests to pay before meeting, or pressure to move to another app. Report suspicious behaviour immediately.", host.muted, 12));

        content.addView(host.ui.titleText("Emergency", host.text, 16));
        Button emergency = host.ui.primaryButton("Call emergency services");
        emergency.setOnClickListener(v -> {
            try { host.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))); }
            catch (Exception e) { host.ui.showSimpleMessage("Emergency", "Dial 112 from your phone."); }
        });
        content.addView(emergency, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        content.addView(host.ui.bodyText("Campus security: save your campus security number in your phone before meeting.", host.muted, 11));

        content.addView(host.ui.titleText("Permissions", host.text, 16));
        content.addView(host.ui.bodyText("Camera is used for profile/listing photos and collection proof. Location is used only while a delivery map is open. Notifications can be turned off in Settings.", host.muted, 12));

        content.addView(host.ui.titleText("Reporting", host.text, 16));
        content.addView(host.ui.bodyText("Use Report on a listing, review or student profile. Reports are sent to campus moderators and can hide a listing automatically after repeated reports.", host.muted, 12));
        Button report = host.ui.secondaryButton("Open profile and report tools");
        report.setOnClickListener(v -> { page.dismiss(); host.showAppScreen("Profile"); });
        content.addView(report, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44)));

        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static TextView bullet(MainActivity host, String text) {
        TextView view = host.ui.bodyText("•  " + text, host.muted, 12);
        view.setPadding(0, host.ui.dp(4), 0, host.ui.dp(4));
        return view;
    }
}