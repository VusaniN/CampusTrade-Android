package com.campustrade.auth;

import android.app.Dialog;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// Shared Terms & Conditions page used before login and from Settings.
public final class Terms {

    private Terms() {
    }

    public static void show(MainActivity host) {
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Terms & Conditions",
                "CampusTrade v1.5.3 - Last updated 16 November 2026", page::dismiss));

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        body(host, content,
                "These terms apply to every student who signs in to CampusTrade. You must read and agree to them before you log in.",
                12);
        note(host, content,
                "CampusTrade is a student-built campus marketplace. It helps students discover, list, negotiate and arrange trades. It is not a party to any trade, payment, meeting or handover.");
        section(host, content, "1. The service");
        body(host, content,
                "CampusTrade provides a campus-only space for students to list, buy, sell or trade school supplies and related items. Accounts must use a verified @vossie.net student email. You are responsible for the accuracy of your profile, listings, messages and reports.",
                11);
        section(host, content, "2. Trades and payments");
        body(host, content,
                "Trades are between students. CampusTrade does not process, hold, guarantee or refund payments. The supported payment preferences are Cash on Collection or EFT arranged directly between the student buyer and student seller. Never send card details, passwords or one-time codes through the app.",
                11);
        body(host, content,
                "CampusTrade does not inspect, verify, store or deliver items. The buyer and seller are responsible for checking the item, agreeing the price and completing the handover.",
                11);
        section(host, content, "3. Safe campus exchanges");
        body(host, content,
                "Meet in a public campus location during reasonable hours. Tell someone where you are going. Inspect the item before paying. Keep communication and payment arrangements clear. Report suspicious behaviour, threats or theft to campus security or the police as well as using the in-app report tools.",
                11);
        section(host, content, "4. Prohibited items and behaviour");
        body(host, content,
                "You may not use CampusTrade to list or trade stolen, illegal, dangerous, counterfeit or prohibited items; impersonate another student; harass, threaten or discriminate against anyone; post spam or misleading listings; manipulate ratings; or attempt to access another student's account or private data.",
                11);
        section(host, content, "5. Content and moderation");
        body(host, content,
                "You are responsible for the content you post. CampusTrade may remove listings, posts or reviews and may suspend or deactivate accounts that break these terms or campus rules. Moderation actions and reports may be logged for safety and audit purposes.",
                11);
        section(host, content, "6. Limitation of liability");
        body(host, content,
                "To the fullest extent permitted by law, CampusTrade, its student developers and the campus team are not responsible or liable for any theft, fraud, loss of money or property, injury, non-delivery, misrepresentation, item quality, or any other loss or damage arising from a trade, payment, meeting, handover or other interaction arranged through or outside the application.",
                11);
        body(host, content,
                "You use CampusTrade at your own risk. You must take reasonable steps to protect yourself and your property. Nothing in these terms excludes or limits liability that cannot lawfully be excluded or limited.",
                11);
        section(host, content, "7. Privacy and data");
        body(host, content,
                "CampusTrade uses your campus email, profile, listings, orders, messages and reports to operate the marketplace and keep the campus community safe. Do not post another student's personal information without permission. Use the in-app safety and reporting tools if you believe data has been misused.",
                11);
        section(host, content, "8. Changes and contact");
        body(host, content,
                "We may update these terms as CampusTrade changes. The latest version is available in Settings > Legal > Terms & Conditions. Continuing to use CampusTrade after an update means you accept the updated terms. For help, use Settings > Help & support.",
                11);
        note(host, content,
                "By ticking the acceptance box and signing in, you confirm that you have read, understood and agree to these Terms & Conditions.");
        body(host, content,
                "If you do not agree, do not sign in or use CampusTrade.",
                11);

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button close = host.ui.primaryButton("Close");
        close.setOnClickListener(v -> page.dismiss());
        footer.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        root.addView(footer);

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static void section(MainActivity host, LinearLayout content, String title) {
        TextView heading = host.ui.titleText(title, host.text, 15);
        heading.setPadding(0, host.ui.dp(20), 0, host.ui.dp(6));
        content.addView(heading);
    }

    private static void body(MainActivity host, LinearLayout content, String text, int size) {
        TextView paragraph = host.ui.bodyText(text, host.muted, size);
        paragraph.setLineSpacing(host.ui.dp(3), 1f);
        paragraph.setPadding(0, 0, 0, host.ui.dp(8));
        content.addView(paragraph);
    }

    private static void note(MainActivity host, LinearLayout content, String text) {
        TextView note = host.ui.bodyText(text,
                host.nightActive ? Color.rgb(255, 226, 168) : Color.rgb(92, 74, 30), 11);
        note.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        note.setBackground(host.ui.round(host.nightActive ? Color.rgb(58, 48, 26) : Color.rgb(255, 240, 203), 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = host.ui.dp(8);
        params.bottomMargin = host.ui.dp(8);
        content.addView(note, params);
    }
}

