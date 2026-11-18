package com.campustrade.auth;

import android.Manifest;
import android.app.TimePickerDialog;
import android.app.KeyguardManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.net.Uri;
import android.text.InputType;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Switch;
import android.content.res.ColorStateList;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;
import com.bumptech.glide.Glide;
import com.campustrade.auth.data.AuthRepository;
import com.campustrade.auth.data.CloudSync;
import com.campustrade.auth.data.HelperBot;
import com.campustrade.auth.data.RatingRepository;
import com.campustrade.auth.model.Message;
import android.text.TextUtils;

// Profile, settings, help and reviews.
public class Profile {

    private final MainActivity host;

    public Profile(MainActivity host) {
        this.host = host;
    }

    // Settings use cards, and each row reads and writes the same prefs.
    public void buildSettings(LinearLayout content) {
        SharedPreferences prefs = host.settingsPrefs();
        LinearLayout account = host.ui.horizontal();
        account.setGravity(Gravity.CENTER_VERTICAL);
        account.setPadding(host.ui.dp(16), host.ui.dp(20), host.ui.dp(16), host.ui.dp(20));
        account.setBackground(host.ui.bottomRule(host.text));
        View avatar = profileAvatar(host.ui.dp(56), 22, host.blue, Color.WHITE);
        account.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(56), host.ui.dp(56)));
        LinearLayout identity = new LinearLayout(host);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(host.ui.dp(12), 0, host.ui.dp(8), 0);
        identity.addView(host.ui.titleText(host.currentStudentName, host.text, 22));
        TextView email = host.ui.bodyText(host.currentStudentEmail, host.muted, 12);
        email.setSingleLine(true);
        email.setEllipsize(TextUtils.TruncateAt.END);
        identity.addView(email);
        String course = com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles").getString("course:" + host.currentStudentEmail, "");
        if (!course.isEmpty()) identity.addView(host.ui.bodyText(course, host.muted, 12));
        TextView accountEdit = host.ui.textButton("Edit profile");
        accountEdit.setPadding(0, host.ui.dp(2), 0, 0);
        accountEdit.setOnClickListener(v -> { host.editingFromSettings = true; showEditProfile(); });
        identity.addView(accountEdit);
        account.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(account);

        settingsHeading(content, "Notifications");
        LinearLayout alerts = host.ui.card(Color.WHITE, 16, 0);
        alerts.addView(settingsToggle("bell", "Push notifications", "Alerts on this phone", "push", true, true));
        host.ui.addMenuDivider(alerts);
        alerts.addView(settingsToggle("calendar", "Deadline reminders", "Assignments, quizzes and exams", "deadlines", true, false));
        host.ui.addMenuDivider(alerts);
        alerts.addView(settingsOption("payment", "Order alerts", "Order and payment updates", "Always on", v -> host.notification.showNotificationHistory()));
        host.ui.addMenuDivider(alerts);
        alerts.addView(settingsToggle("moon", "Quiet hours", "Mute non-urgent alerts at night", "quiet", false, false));
        host.ui.addMenuDivider(alerts);
        alerts.addView(settingsOption("chevron", "From", "Quiet hours start", quietTime("quiet_start", 22 * 60),
                v -> chooseQuietTime("quiet_start", 22 * 60)));
        host.ui.addMenuDivider(alerts);
        alerts.addView(settingsOption("chevron", "Until", "Quiet hours end", quietTime("quiet_end", 7 * 60),
                v -> chooseQuietTime("quiet_end", 7 * 60)));
        content.addView(alerts);

        settingsHeading(content, "Marketplace");
        LinearLayout marketplace = host.ui.card(Color.WHITE, 16, 0);
        marketplace.addView(settingsOption("tag", "My marketplace listings", "Manage drafts, live and sold items", "Open",
                v -> { host.marketFilter = "Mine"; host.showAppScreen("Market"); }));
        host.ui.addMenuDivider(marketplace);
        marketplace.addView(settingsOption("grid", "Seller dashboard", "Sales, views, favourites and performance", "Open",
                v -> host.showAppScreen("SellerDashboard")));
        content.addView(marketplace);
        LinearLayout appearance = host.ui.card(Color.WHITE, 16, 0);
        appearance.addView(settingsOption("type", "Text size", "Make app text easier to read",
                Math.round(host.savedFontScale() * 100) + "%", v -> showTextSizeControls()));
        host.ui.addMenuDivider(appearance);
        appearance.addView(settingsOption("moon", "Dark mode", "Dark screens in the evening",
                host.themeModeLabel(), v -> host.showThemePicker()));
        content.addView(appearance);

        settingsHeading(content, "Security and privacy");
        LinearLayout privacy = host.ui.card(Color.WHITE, 16, 0);
        privacy.addView(settingsToggle("shield", "App lock", "Use your phone's screen lock", "app_lock", false, false));
        host.ui.addMenuDivider(privacy);
        privacy.addView(settingsToggle("shield", "Screen privacy", "Hide previews and block screenshots", "screen_privacy", false, false));
        host.ui.addMenuDivider(privacy);
        privacy.addView(settingsOption("shield", "Blocked students", "Manage blocked accounts", "Open", v -> showBlockedStudents()));
        host.ui.addMenuDivider(privacy);
        privacy.addView(settingsOption("profile", "My profile", "Your campus identity", "Open", v -> host.showAppScreen("Profile")));
        content.addView(privacy);

        settingsHeading(content, "Support");
        LinearLayout support = host.ui.card(Color.WHITE, 16, 0);
        support.addView(settingsOption("checklist", "App tutorial", "A quick tour for new and returning users", "Open", v -> Tutorial.show(host)));
        host.ui.addMenuDivider(support);
        support.addView(settingsOption("flag", "My reports", "Track reports, admin replies and rate the outcome", "Open", v -> showMyReports()));
        host.ui.addMenuDivider(support);
        support.addView(settingsOption("shield", "Content warnings",
                host.safety.postingBlocked(host.currentStudentEmail)
                        ? "Posting is paused - request a review" : "Warnings before posting is paused",
                host.safety.contentStrikes(host.currentStudentEmail) + "/3", v -> showContentStanding()));
        host.ui.addMenuDivider(support);
        support.addView(settingsOption("chat", "Live chat with admin", "Talk to the campus moderator about a report", "Open", v -> host.inbox.openAdminChat()));
        host.ui.addMenuDivider(support);
        support.addView(settingsOption("mail", "Email campus admin", "Use your mail app for a formal report", "Compose", v -> emailCampusAdmin()));
        host.ui.addMenuDivider(support);
        support.addView(settingsOption("chat", "Help & support", "Ask the helper or message the campus team", "Open", v -> showHelpSupport()));
        host.ui.addMenuDivider(support);
        support.addView(settingsOption("compose", "Send feedback", "Report a bug, problem or idea to the campus team", "Write", v -> showFeedbackForm()));
        content.addView(support);

        settingsHeading(content, "Account");
        LinearLayout accountActions = host.ui.card(Color.WHITE, 16, 0);
        accountActions.addView(settingsOption("profile", "Profile photo", "Change your profile picture", "Open", v -> pickProfilePhoto()));
        host.ui.addMenuDivider(accountActions);
        accountActions.addView(settingsOption("shield", "Change password", "Current password, new password and confirmation", "Open", v -> changePassword()));
        host.ui.addMenuDivider(accountActions);
        accountActions.addView(settingsOption("exit", "Deactivate account", "Temporarily disable sign-in", "Open", v -> confirmDeactivate()));
        host.ui.addMenuDivider(accountActions);
        accountActions.addView(settingsOption("exit", "Delete account", "Remove your public profile and sign out", "Open", v -> confirmDelete()));
        content.addView(accountActions);

        settingsHeading(content, "Legal");
        LinearLayout legal = host.ui.card(Color.WHITE, 16, 0);
        legal.addView(settingsOption("checklist", "Terms & Conditions",
                "Read the rules, safety and liability terms", "Read", v -> Terms.show(host)));
        content.addView(legal);

        TextView about = host.ui.bodyText("CampusTrade v1.5.3 - Eduvos campus marketplace", host.muted, 11);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, host.ui.dp(28), 0, host.ui.dp(16));
        content.addView(about);
    }

    void settingsHeading(LinearLayout content, String title) {
        TextView heading = host.ui.tinyCaps(title.toUpperCase(Locale.ROOT), host.text);
        heading.setTextSize(11);
        heading.setLetterSpacing(0.08f);
        heading.setPadding(host.ui.dp(4), host.ui.dp(24), 0, host.ui.dp(8));
        content.addView(heading);
    }

    public LinearLayout settingsRow(String icon, String title, String subtitle) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(host.ui.dp(48));
        row.setPadding(host.ui.dp(14), host.ui.dp(14), host.ui.dp(14), host.ui.dp(14));
        View symbol = host.new CampusAssetView(host, icon, icon.equals("payment") ? host.green : host.blue);
        symbol.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        symbol.setBackground(host.ui.round(icon.equals("payment") ? Color.rgb(229, 247, 239) : Color.rgb(234, 242, 255), 12));
        row.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(44)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(12), 0, host.ui.dp(8), 0);
        copy.addView(host.ui.titleText(title, host.text, 14));
        TextView detail = host.ui.bodyText(subtitle, host.muted, 12);
        detail.setPadding(0, host.ui.dp(3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    LinearLayout settingsOption(String icon, String title, String subtitle, String value, View.OnClickListener listener) {
        LinearLayout row = settingsRow(icon, title, subtitle);
        TextView action = host.ui.titleText(value, host.muted, 12);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(action);
        row.setOnClickListener(listener);
        return row;
    }

    // Check permissions or screen lock before saving a switch as on.
    LinearLayout settingsToggle(String icon, String title, String subtitle, String key, boolean defaultValue, boolean needsPermission) {
        SharedPreferences prefs = host.settingsPrefs();
        LinearLayout row = settingsRow(icon, title, subtitle);
        Switch toggle = new Switch(host);
        toggle.setMinimumWidth(host.ui.dp(48));
        toggle.setMinimumHeight(host.ui.dp(48));
        toggle.setContentDescription(title);
        toggle.setChecked(prefs.getBoolean(key, defaultValue) && (!needsPermission || host.notification.canShowNotifications()));
        int[][] states = {new int[] {android.R.attr.state_checked}, new int[] {}};
        // Use a strong dark track for on and mid-grey for off; the old track was too pale.
        toggle.setTrackTintList(new ColorStateList(states, new int[] {host.text, host.muted}));
        toggle.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            // App lock only works if the phone already has a PIN, pattern or password.
            if (enabled && key.equals("app_lock")) {
                KeyguardManager guard = (KeyguardManager) host.getSystemService(Context.KEYGUARD_SERVICE);
                if (guard == null || !guard.isDeviceSecure()) {
                    button.setChecked(false);
                    Toast.makeText(host, "Set a PIN, pattern or password in your phone settings first.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            prefs.edit().putBoolean(key, enabled).apply();
            if (enabled && needsPermission && !host.notification.canShowNotifications()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && host.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) host.notification.requestNotificationPermission();
                else host.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + host.getPackageName())));
            }
            if (key.equals("screen_privacy")) {
                if (enabled) host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                else host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        });
        row.addView(toggle, new LinearLayout.LayoutParams(host.ui.dp(52), host.ui.dp(48)));
        return row;
    }

    public String quietTime(String key, int fallback) {
        int minutes = host.settingsPrefs().getInt(key, fallback);
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    public void chooseQuietTime(String key, int fallback) {
        int minutes = host.settingsPrefs().getInt(key, fallback);
        new TimePickerDialog(host, (view, hour, minute) -> {
            host.settingsPrefs().edit().putInt(key, hour * 60 + minute).apply();
            host.showAppScreen("Settings");
        }, minutes / 60, minutes % 60, true).show();
    }

    // Quiet hours can cross midnight, so check the start and end both ways.
    boolean inQuietHours() {
        SharedPreferences prefs = host.settingsPrefs();
        if (!prefs.getBoolean("quiet", false)) return false;
        java.util.Calendar now = java.util.Calendar.getInstance();
        int minute = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        int start = prefs.getInt("quiet_start", 1320);
        int end = prefs.getInt("quiet_end", 420);
        return start != end && (start < end ? minute >= start && minute < end : minute >= start || minute < end);
    }

    private void showTextSizeControls() {
        final float[] scale = {host.savedFontScale()};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.bodyText("Adjust text across CampusTrade. The setting stays on this device.",
                host.muted, 11));
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(14), 0, host.ui.dp(8));
        TextView smaller = host.ui.secondaryButton("A smaller");
        TextView value = host.ui.titleText("", host.text, 18);
        value.setGravity(Gravity.CENTER);
        TextView larger = host.ui.secondaryButton("A larger");
        row.addView(smaller, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        row.addView(value, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        row.addView(larger, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        body.addView(row);
        TextView preview = host.ui.bodyText("Preview: CampusTrade marketplace, messages and orders.",
                host.text, 14);
        preview.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        preview.setBackground(host.ui.strokedRound(host.surface(), 10, host.line));
        body.addView(preview);
        Button apply = host.ui.primaryButton("Apply text size");
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        applyParams.topMargin = host.ui.dp(12);
        body.addView(apply, applyParams);
        TextView reset = host.ui.textButton("Reset to default");
        body.addView(reset, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42)));
        Dialog sheet = host.ui.showBottomSheet("Text size", body);
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            value.setText(Math.round(scale[0] * 100) + "%");
            preview.setTextSize(14f * scale[0] / host.savedFontScale());
            smaller.setAlpha(scale[0] <= 0.8f ? 0.4f : 1f);
            larger.setAlpha(scale[0] >= 1.4f ? 0.4f : 1f);
        };
        refresh[0].run();
        smaller.setOnClickListener(v -> {
            if (scale[0] <= 0.8f) return;
            scale[0] = Math.max(0.8f, scale[0] - 0.1f);
            refresh[0].run();
        });
        larger.setOnClickListener(v -> {
            if (scale[0] >= 1.4f) return;
            scale[0] = Math.min(1.4f, scale[0] + 0.1f);
            refresh[0].run();
        });
        reset.setOnClickListener(v -> {
            scale[0] = 1f;
            refresh[0].run();
        });
        apply.setOnClickListener(v -> {
            sheet.dismiss();
            host.setFontScale(scale[0]);
        });
    }

    private void emailCampusAdmin() {
        Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Constants.STRICT_ADMIN_EMAIL));
        mail.putExtra(Intent.EXTRA_SUBJECT, "CampusTrade report follow-up");
        mail.putExtra(Intent.EXTRA_TEXT, "Hello campus team,\n\nI'm following up on a CampusTrade report.\n\nStudent: "
                + host.currentStudentEmail + "\n\n");
        try {
            host.startActivity(Intent.createChooser(mail, "Email campus admin"));
        } catch (android.content.ActivityNotFoundException e) {
            host.ui.showSimpleMessage("Campus admin email", Constants.STRICT_ADMIN_EMAIL);
        }
    }

    private void showContentStanding() {
        int strikes = host.safety.contentStrikes(host.currentStudentEmail);
        String reason = host.safety.contentLastReason(host.currentStudentEmail);
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.titleText("Content warnings", host.text, 17));
        body.addView(host.ui.bodyText(strikes + " of 3 warnings used. "
                + (host.safety.postingBlocked(host.currentStudentEmail)
                ? "Posting is paused until a moderator reviews the account."
                : "Reach 3 warnings and posting is paused for moderator review."), host.muted, 12));
        if (!reason.isEmpty()) {
            TextView last = host.ui.bodyText("Latest issue: " + reason, host.text, 12);
            last.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
            last.setBackground(host.ui.strokedRound(host.surface(), 10, host.line));
            LinearLayout.LayoutParams lastParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lastParams.topMargin = host.ui.dp(10);
            body.addView(last, lastParams);
        }
        Button appeal = host.ui.primaryButton(strikes > 0 ? "Request a review" : "No warnings to appeal");
        appeal.setEnabled(strikes > 0);
        LinearLayout.LayoutParams appealParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        appealParams.topMargin = host.ui.dp(12);
        body.addView(appeal, appealParams);
        Dialog sheet = host.ui.showBottomSheet("Content warnings", body);
        appeal.setOnClickListener(v -> {
            sheet.dismiss();
            LinearLayout reviewForm = new LinearLayout(host);
            reviewForm.setOrientation(LinearLayout.VERTICAL);
            reviewForm.setPadding(0, host.ui.dp(4), 0, 0);
            reviewForm.addView(host.ui.bodyText("Explain why the warning or posting pause should be reviewed.",
                    host.muted, 11));
            EditText why = host.ui.input("Why should this be reviewed?", InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            why.setMinLines(3);
            why.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(700)});
            reviewForm.addView(why);
            Button send = host.ui.primaryButton("Send appeal");
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
            sendParams.topMargin = host.ui.dp(10);
            reviewForm.addView(send, sendParams);
            Dialog form = host.ui.showBottomSheet("Request a review", reviewForm);
            send.setOnClickListener(v2 -> {
                String text = host.ui.text(why).trim();
                if (text.length() < 20) {
                    why.setError("Add at least 20 characters");
                    return;
                }
                String uid = host.currentUid == null ? "" : host.currentUid;
                new AuthRepository().submitAppeal(uid, host.currentStudentEmail, host.currentStudentName,
                        "CONTENT WARNING APPEAL: " + text, new AuthRepository.SimpleCallback() {
                            @Override public void onOk() {
                                form.dismiss();
                                Toast.makeText(host, "Appeal sent to the campus team.", Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onBad(String error) {
                                why.setError(error);
                            }
                        });
            });
        });
    }

    /** A student-facing history with the admin reason and a satisfaction rating. */
    public void showMyReports() {
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("My reports", "Replies, outcomes and feedback", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        final ArrayList<MainActivity.ModerationReport> reports = new ArrayList<>();
        for (MainActivity.ModerationReport report : host.moderationReports) {
            if (host.currentStudentEmail.equalsIgnoreCase(report.reporterEmail)) reports.add(report);
        }
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            java.util.Collections.sort(reports, (a, b) -> Long.compare(b.createdAt, a.createdAt));
            if (reports.isEmpty()) {
                list.addView(host.ui.emptyState("flag", "No reports yet",
                        "Reports you submit from a listing, review or student profile appear here."));
                return;
            }
            for (MainActivity.ModerationReport report : reports) {
                LinearLayout card = host.ui.card(host.surface(), 14, host.ui.dp(14));
                card.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
                LinearLayout top = host.ui.horizontal();
                top.setGravity(Gravity.CENTER_VERTICAL);
                TextView reason = host.ui.titleText(report.reason, host.text, 14);
                reason.setSingleLine(true);
                reason.setEllipsize(TextUtils.TruncateAt.END);
                top.addView(reason, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                top.addView(host.ui.statusPill(report.state));
                card.addView(top);
                TextView target = host.ui.bodyText(report.entityLabel() + " - " + report.productTitle
                        + "\nSent " + host.notification.alertTime(report.createdAt), host.muted, 11);
                target.setPadding(0, host.ui.dp(6), 0, 0);
                card.addView(target);
                if (!report.resolutionNote.isEmpty()) {
                    TextView outcome = host.ui.bodyText("Admin reason: " + report.resolutionNote, host.text, 12);
                    outcome.setPadding(host.ui.dp(10), host.ui.dp(9), host.ui.dp(10), host.ui.dp(9));
                    outcome.setBackground(host.ui.strokedRound(
                            host.nightActive ? Color.rgb(42, 42, 46) : Color.rgb(247, 246, 242), 10, host.line));
                    LinearLayout.LayoutParams outcomeParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    outcomeParams.topMargin = host.ui.dp(9);
                    card.addView(outcome, outcomeParams);
                }
                LinearLayout actions = host.ui.horizontal();
                actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setPadding(0, host.ui.dp(8), 0, 0);
                if (report.isOpen()) {
                    TextView chat = host.ui.linkText("Live chat");
                    chat.setTextSize(11);
                    chat.setPadding(host.ui.dp(4), host.ui.dp(8), host.ui.dp(10), host.ui.dp(8));
                    chat.setOnClickListener(v -> {
                        page.dismiss();
                        host.inbox.openAdminChat();
                    });
                    actions.addView(chat);
                }
                TextView email = host.ui.linkText("Email admin");
                email.setTextSize(11);
                email.setPadding(host.ui.dp(4), host.ui.dp(8), host.ui.dp(10), host.ui.dp(8));
                email.setOnClickListener(v -> {
                    Intent mail = new Intent(Intent.ACTION_SENDTO,
                            Uri.parse("mailto:" + Constants.STRICT_ADMIN_EMAIL));
                    mail.putExtra(Intent.EXTRA_SUBJECT, "CampusTrade report: " + report.productTitle);
                    mail.putExtra(Intent.EXTRA_TEXT, "Report reason: " + report.reason
                            + "\nReport ID: " + report.id
                            + "\n\nHello campus team,\n");
                    try {
                        host.startActivity(Intent.createChooser(mail, "Email campus admin"));
                    } catch (android.content.ActivityNotFoundException e) {
                        host.ui.showSimpleMessage("Campus admin email", Constants.STRICT_ADMIN_EMAIL);
                    }
                });
                actions.addView(email);
                actions.addView(host.ui.space(1, 1), new LinearLayout.LayoutParams(0, 1, 1));
                if (report.satisfaction > 0) {
                    TextView thanks = host.ui.bodyText("You rated " + report.satisfaction + "/5", host.green, 11);
                    actions.addView(thanks);
                } else if (!report.isOpen()) {
                    TextView rate = host.ui.linkText("Rate outcome");
                    rate.setTextSize(11);
                    rate.setPadding(host.ui.dp(4), host.ui.dp(8), host.ui.dp(4), host.ui.dp(8));
                    rate.setOnClickListener(v -> promptSatisfaction(page, report, reports, render[0]));
                    actions.addView(rate);
                }
                card.addView(actions);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.bottomMargin = host.ui.dp(10);
                list.addView(card, cardParams);
            }
        };
        render[0].run();
        if (host.currentUid == null || host.currentUid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reports")
                .whereEqualTo("reporterId", host.currentUid).limit(50).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        MainActivity.ModerationReport cloud = reportFromDoc(doc);
                        boolean replaced = false;
                        for (int i = 0; i < reports.size(); i++) {
                            if (reports.get(i).id.equals(cloud.id)) {
                                reports.set(i, cloud);
                                replaced = true;
                                break;
                            }
                        }
                        if (!replaced) reports.add(cloud);
                        for (int i = 0; i < host.moderationReports.size(); i++) {
                            if (host.moderationReports.get(i).id.equals(cloud.id)) {
                                host.moderationReports.set(i, cloud);
                                break;
                            }
                        }
                    }
                    host.saveModerationReports();
                    render[0].run();
                });
    }

    private MainActivity.ModerationReport reportFromDoc(com.google.firebase.firestore.DocumentSnapshot doc) {
        String productId = doc.getString("productId");
        String productTitle = doc.getString("productTitle");
        String reporterEmail = doc.getString("reporterEmail");
        String reporterName = doc.getString("reporterName");
        String reason = doc.getString("reason");
        String detail = doc.getString("detail");
        String priority = doc.getString("priority");
        String state = doc.getString("state");
        long createdAt = 0;
        Object created = doc.get("createdAt");
        if (created instanceof com.google.firebase.Timestamp) {
            createdAt = ((com.google.firebase.Timestamp) created).toDate().getTime();
        } else if (created instanceof Number) {
            createdAt = ((Number) created).longValue();
        }
        MainActivity.ModerationReport report = new MainActivity.ModerationReport(doc.getId(),
                productId == null ? "" : productId,
                productTitle == null ? "Listing" : productTitle,
                reporterEmail == null ? host.currentStudentEmail : reporterEmail,
                reporterName == null ? host.currentStudentName : reporterName,
                reason == null ? "Report" : reason,
                detail == null ? "" : detail,
                priority == null ? "Medium" : priority,
                state == null ? "Open" : state,
                createdAt);
        String note = doc.getString("resolutionNote");
        if (note != null) report.resolutionNote = note;
        Object resolved = doc.get("resolvedAt");
        if (resolved instanceof com.google.firebase.Timestamp) {
            report.resolvedAt = ((com.google.firebase.Timestamp) resolved).toDate().getTime();
        } else if (resolved instanceof Number) {
            report.resolvedAt = ((Number) resolved).longValue();
        }
        Long satisfaction = doc.getLong("satisfaction");
        if (satisfaction != null) report.satisfaction = satisfaction.intValue();
        String comment = doc.getString("satisfactionComment");
        if (comment != null) report.satisfactionComment = comment;
        return report;
    }

    private void promptSatisfaction(Dialog page, MainActivity.ModerationReport report,
                                    ArrayList<MainActivity.ModerationReport> reports, Runnable render) {
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.titleText("How did we handle it?", host.text, 16));
        body.addView(host.ui.bodyText(report.productTitle + " - " + report.reason, host.muted, 11));
        final int[] score = {0};
        final TextView[] choices = new TextView[5];
        LinearLayout stars = host.ui.horizontal();
        stars.setPadding(0, host.ui.dp(12), 0, host.ui.dp(4));
        for (int i = 1; i <= 5; i++) {
            final int value = i;
            TextView choice = host.ui.chip(String.valueOf(i), host.nightActive
                            ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235), host.muted);
            choice.setTextSize(13);
            choice.setOnClickListener(v -> {
                score[0] = value;
                for (int j = 0; j < choices.length; j++) {
                    boolean selected = j + 1 == value;
                    choices[j].setTextColor(selected ? Color.WHITE : host.muted);
                    choices[j].setBackground(host.ui.round(selected ? host.blue
                            : host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235), 999));
                }
            });
            choices[i - 1] = choice;
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(0, host.ui.dp(44), 1);
            if (i > 1) choiceParams.leftMargin = host.ui.dp(6);
            stars.addView(choice, choiceParams);
        }
        body.addView(stars);
        body.addView(host.ui.bodyText("1 = very poor, 5 = excellent", host.muted, 10));
        EditText comment = host.ui.listingField(body, "Comment (optional)", "Anything else we should know?",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        comment.setSingleLine(false);
        comment.setMinLines(2);
        comment.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
        Button submit = host.ui.primaryButton("Send feedback");
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        submitParams.topMargin = host.ui.dp(10);
        body.addView(submit, submitParams);
        Dialog sheet = host.ui.showBottomSheet("Rate the outcome", body);
        submit.setOnClickListener(v -> {
            if (score[0] == 0) {
                Toast.makeText(host, "Choose a rating from 1 to 5.", Toast.LENGTH_SHORT).show();
                return;
            }
            report.satisfaction = score[0];
            report.satisfactionComment = host.ui.text(comment).trim();
            host.saveModerationReports();
            java.util.Map<String, Object> fields = new java.util.HashMap<>();
            fields.put("satisfaction", report.satisfaction);
            fields.put("satisfactionComment", report.satisfactionComment);
            fields.put("satisfactionAt", com.google.firebase.Timestamp.now());
            fields.put("satisfactionBy", host.currentStudentEmail);
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reports")
                    .document(report.id).update(fields)
                    .addOnFailureListener(e -> Toast.makeText(host,
                            "Rating saved on this phone; the cloud copy didn't update.",
                            Toast.LENGTH_SHORT).show());
            sheet.dismiss();
            render.run();
            Toast.makeText(host, "Thanks for the feedback.", Toast.LENGTH_SHORT).show();
        });
    }

    /** Direct feedback form, also reachable from Help & support. */
    public void showFeedbackForm() {
        Dialog page = new Dialog(host);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Send feedback", "Tell the campus team what to improve", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout intro = host.ui.card(host.surface(), 14, host.ui.dp(14));
        intro.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
        intro.addView(host.ui.titleText("Help us make CampusTrade better", host.text, 15));
        intro.addView(host.ui.bodyText("Your message goes to the campus moderation team. You can report a bug, suggest an improvement or ask for help.",
                host.muted, 12));
        content.addView(intro);

        Spinner type = host.ui.listingSelector(content, "Type", new String[] {"App issue", "Suggestion", "Account help", "Other"});
        EditText detail = host.ui.listingField(content, "Message", "What happened? Include the screen or order involved.",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        detail.setSingleLine(false);
        detail.setGravity(Gravity.TOP | Gravity.START);
        detail.setMinLines(5);
        detail.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        detail.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(150)));
        Button send = host.ui.primaryButton("Send feedback");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        sendParams.topMargin = host.ui.dp(14);
        content.addView(send, sendParams);
        send.setOnClickListener(v -> {
            String body = host.ui.text(detail).trim();
            if (body.length() < 10) {
                detail.setError("Add at least 10 characters");
                return;
            }
            String uid = CloudSync.uid(host);
            if (uid == null || uid.isEmpty()) {
                Toast.makeText(host, "Sign in again to send feedback.", Toast.LENGTH_SHORT).show();
                return;
            }
            send.setEnabled(false);
            send.setText("Sending...");
            new RatingRepository().report(uid, host.currentStudentEmail, host.currentStudentName,
                    type.getSelectedItem().toString(), body, new RatingRepository.DoneCallback() {
                        @Override public void onOk() {
                            if (host.isDestroyed() || host.isFinishing()) return;
                            page.dismiss();
                            Toast.makeText(host, "Feedback sent to the campus team.", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onBad(String message) {
                            if (host.isDestroyed() || host.isFinishing()) return;
                            send.setEnabled(true);
                            send.setText("Send feedback");
                            Toast.makeText(host, "Feedback didn't send: " + message, Toast.LENGTH_LONG).show();
                        }
                    });
        });
        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // Help is a full page: helper and common questions first, then feedback.
    public void showHelpSupport() {
        Dialog page = new Dialog(host);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Help & support", "Answers first, then the campus team", page::dismiss));

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout helper = host.ui.card(host.page, 0, host.ui.dp(16));
        helper.setBackground(host.ui.strokedRound(host.nightActive ? Color.rgb(48, 38, 36) : Color.rgb(255, 246, 242),
                14, host.nightActive ? host.blue : Color.rgb(198, 216, 242)));
        LinearLayout helperTop = host.ui.horizontal();
        helperTop.setGravity(Gravity.CENTER_VERTICAL);
        View bot = host.new CampusAssetView(host, "chat", host.blue);
        bot.setPadding(host.ui.dp(9), host.ui.dp(9), host.ui.dp(9), host.ui.dp(9));
        bot.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(226, 237, 255), 10));
        helperTop.addView(bot, new LinearLayout.LayoutParams(host.ui.dp(38), host.ui.dp(38)));
        LinearLayout helperCopy = new LinearLayout(host);
        helperCopy.setOrientation(LinearLayout.VERTICAL);
        helperCopy.setPadding(host.ui.dp(10), 0, 0, 0);
        helperCopy.addView(host.ui.titleText("Need help?", host.text, 15));
        helperCopy.addView(host.ui.bodyText("Ask the CampusTrade Helper for instant app answers.", host.muted, 11));
        helperTop.addView(helperCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        helper.addView(helperTop);
        Button askHelper = host.ui.primaryButton("Ask the helper");
        LinearLayout.LayoutParams helperAction = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44));
        helperAction.topMargin = host.ui.dp(12);
        askHelper.setOnClickListener(v -> { page.dismiss(); host.inbox.showConversation(HelperBot.PEER); });
        helper.addView(askHelper, helperAction);
        content.addView(helper);

        profileHeading(content, "Common questions");
        String[][] faqs = {
                {"How do I buy an item?",
                        "Open a listing, check the seller card and condition, then tap Buy now. Choose campus pickup and pay by cash or EFT on collection."},
                {"What does a reservation mean?",
                        "An accepted offer reserves the item for 24 hours. The buyer should complete the order before the countdown ends, otherwise it returns to the market."},
                {"How do I stay safe?",
                        "Meet in a busy campus spot, inspect the item before paying, and keep the conversation inside CampusTrade. Never share banking passwords or OTPs."},
                {"How do reviews work?",
                        "After an order is completed, both students can rate the handover. Reviews show on public seller profiles and cannot be edited once submitted."}
        };
        LinearLayout questions = host.ui.card(host.page, 16, 0);
        questions.setBackground(host.ui.strokedRound(host.surface(), 14, host.line));
        for (int i = 0; i < faqs.length; i++) {
            final String question = faqs[i][0];
            final String answer = faqs[i][1];
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
            row.setMinimumHeight(host.ui.dp(52));
            row.addView(host.ui.bodyText(question, host.text, 13), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(host.new CampusAssetView(host, "chevron", host.muted),
                    new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16)));
            row.setOnClickListener(v -> {
                LinearLayout body = new LinearLayout(host);
                body.setOrientation(LinearLayout.VERTICAL);
                body.setPadding(host.ui.dp(4), host.ui.dp(6), host.ui.dp(4), host.ui.dp(8));
                body.addView(host.ui.bodyText(answer, host.text, 13));
                host.ui.showBottomSheet(question, body);
            });
            host.ui.attachPressAnimation(row);
            questions.addView(row);
            if (i < faqs.length - 1) host.ui.addMenuDivider(questions);
        }
        content.addView(questions);

        profileHeading(content, "Send feedback to the campus team");
        Spinner type = host.ui.listingSelector(content, "Type", new String[] {"App issue", "Suggestion", "Account help", "Other"});
        EditText detail = host.ui.listingField(content, "Message", "What happened? Include the screen or order involved.",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        detail.setSingleLine(false);
        detail.setGravity(Gravity.TOP | Gravity.START);
        detail.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        detail.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(120)));
        Button send = host.ui.primaryButton("Send feedback");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        sendParams.topMargin = host.ui.dp(14);
        send.setOnClickListener(v -> {
            String body = host.ui.text(detail).trim();
            if (body.length() < 10) {
                detail.setError("Add at least 10 characters");
                return;
            }
            String uid = CloudSync.uid(host);
            if (uid == null || uid.isEmpty()) {
                Toast.makeText(host, "Sign in again to send feedback.", Toast.LENGTH_SHORT).show();
                return;
            }
            send.setEnabled(false);
            send.setText("Sending...");
            new RatingRepository().report(uid, host.currentStudentEmail, host.currentStudentName,
                    type.getSelectedItem().toString(), body,
                    new RatingRepository.DoneCallback() {
                        @Override public void onOk() {
                            if (host.isDestroyed() || host.isFinishing()) return;
                            page.dismiss();
                            Toast.makeText(host, "Feedback sent to the campus team.", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onBad(String message) {
                            if (host.isDestroyed() || host.isFinishing()) return;
                            send.setEnabled(true);
                            send.setText("Send feedback");
                            Toast.makeText(host, "Feedback didn't send: " + message, Toast.LENGTH_LONG).show();
                        }
                    });
        });
        content.addView(send, sendParams);

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // The verified badge comes from Firebase, not just the local session.
    public void buildProfile(LinearLayout content) {
        SharedPreferences profilePrefs = com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles");
        boolean verified = new AuthRepository().isCurrentEmailVerified();
        int ownedListings = 0;
        host.savedListings = 0;
        for (MainActivity.Product p : host.products) {
            if (host.market.ownsListing(p)) ownedListings++;
            if (p.savedBy.contains(host.currentStudentEmail)) host.savedListings++;
        }
        host.messageCount = host.inbox.unreadMessages();
        int unreadMessages = host.messageCount;
        LinearLayout profile = host.ui.card(Color.WHITE, 20, host.ui.dp(18));
        profile.setGravity(Gravity.CENTER_HORIZONTAL);
        View avatar = profileAvatar(host.ui.dp(84), 28, Color.WHITE, host.blue);
        FrameLayout identity = new FrameLayout(host);
        identity.addView(avatar, new FrameLayout.LayoutParams(host.ui.dp(84), host.ui.dp(84), Gravity.CENTER));
        if (verified) {
            View check = host.new CampusAssetView(host, "check", Color.WHITE);
            check.setPadding(host.ui.dp(4), host.ui.dp(4), host.ui.dp(4), host.ui.dp(4));
            check.setBackground(host.ui.strokedRound(host.green, 999, Color.WHITE));
            identity.addView(check, new FrameLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24), Gravity.BOTTOM | Gravity.END));
        }
        profile.addView(identity, new LinearLayout.LayoutParams(host.ui.dp(92), host.ui.dp(92)));
        TextView name = host.ui.titleText(host.currentStudentName, host.text, 18);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, host.ui.dp(12), 0, host.ui.dp(4));
        profile.addView(name);
        String course = profilePrefs.getString("course:" + host.currentStudentEmail, "");
        String storedYear = profilePrefs.getString("year:" + host.currentStudentEmail, "");
        String year = yearLabel(storedYear);
        TextView program = host.ui.bodyText(course.isEmpty() ? "Eduvos student" : course + (year.isEmpty() ? "" : " · " + year), host.muted, 12);
        program.setGravity(Gravity.CENTER);
        profile.addView(program);

        // Use one combined identity badge.
        LinearLayout labels = host.ui.horizontal();
        labels.setGravity(Gravity.CENTER);
        labels.setPadding(0, host.ui.dp(12), 0, 0);
        labels.addView(host.ui.chip(verified ? "Eduvos student · Verified" : "Eduvos student",
                verified ? (host.nightActive ? Color.rgb(24, 54, 42) : Color.rgb(231, 247, 240))
                        : (host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255)),
                verified ? host.readableInk(host.green) : host.readableInk(host.blue)));
        profile.addView(labels);

        // Put Edit beside the profile identity.
        TextView editProfile = host.ui.textButton("Edit profile");
        editProfile.setPadding(host.ui.dp(16), host.ui.dp(4), host.ui.dp(16), host.ui.dp(4));
        editProfile.setOnClickListener(v -> showEditProfile());
        profile.addView(editProfile);

        LinearLayout stats = host.ui.horizontal();
        stats.setPadding(0, host.ui.dp(10), 0, 0);
        TextView listingsStat = host.ui.metric(String.valueOf(ownedListings), "Listings");
        listingsStat.setOnClickListener(v -> { host.marketFilter = "Mine"; host.showAppScreen("Market"); });
        host.ui.attachPressAnimation(listingsStat);
        stats.addView(listingsStat, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View divider1 = new View(host);
        divider1.setBackgroundColor(host.line);
        stats.addView(divider1, new LinearLayout.LayoutParams(host.ui.dp(1), host.ui.dp(36)));
        TextView savedStat = host.ui.metric(String.valueOf(host.savedListings), "Saved");
        savedStat.setOnClickListener(v -> { host.marketFilter = "Saved"; host.showAppScreen("Market"); });
        host.ui.attachPressAnimation(savedStat);
        stats.addView(savedStat, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View divider2 = new View(host);
        divider2.setBackgroundColor(host.line);
        stats.addView(divider2, new LinearLayout.LayoutParams(host.ui.dp(1), host.ui.dp(36)));
        TextView messagesStat = host.ui.metric(String.valueOf(unreadMessages), "Unread");
        messagesStat.setOnClickListener(v -> host.showAppScreen("Inbox"));
        host.ui.attachPressAnimation(messagesStat);
        stats.addView(messagesStat, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        profile.addView(stats);
        content.addView(profile);

        content.addView(host.ui.sectionHeading("Campus activity", ""));
        LinearLayout menu = host.ui.card(Color.WHITE, 16, host.ui.dp(6));
        int savedResources = host.savedListings;
        for (MainActivity.AcademicItem item : host.academics.academicCalendarItems()) if (item.kind.equals("Note") && item.saved) savedResources++;
        menu.addView(host.ui.menuItem("bookmark", "Saved resources", String.valueOf(savedResources)));
        host.ui.addMenuDivider(menu);
        menu.addView(host.ui.menuItem("box", "My marketplace listings", String.valueOf(ownedListings)));
        host.ui.addMenuDivider(menu);
        int memberships = 0;
        for (CampusFeatures.Group g : host.featureStore.data.groups) if (g.members.contains(host.currentStudentEmail)) memberships++;
        menu.addView(host.ui.menuItem("team", "Groups and societies", String.valueOf(memberships)));
        host.ui.addMenuDivider(menu);
        menu.addView(host.ui.menuItem("shield", "Campus verification", verified ? "Verified" : "Pending"));
        content.addView(menu);
        LinearLayout payments = host.ui.card(Color.WHITE, 16, host.ui.dp(6));
        int myOrders = 0;
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (order.isBuyer(host.currentStudentEmail) || order.isSeller(host.currentStudentEmail)) myOrders++;
        }
        payments.addView(host.ui.menuItem("payment", "Orders & transactions", String.valueOf(myOrders)));
        content.addView(payments);
        TextView reviews = host.ui.linkText("See order reviews");
        reviews.setPadding(host.ui.dp(8), host.ui.dp(12), host.ui.dp(8), 0);
        reviews.setOnClickListener(v -> showStudentReviews(host.currentStudentEmail)); content.addView(reviews);
        String myUid = CloudSync.uid(host);
        if (!myUid.isEmpty()) {
            new RatingRepository().forUser(myUid, new RatingRepository.RatingListCallback() {
                @Override public void onOk(java.util.List<com.campustrade.auth.model.Rating> ratings) {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    double total = 0;
                    for (com.campustrade.auth.model.Rating rating : ratings) total += rating.getRating();
                    if (ratings.isEmpty()) reviews.setText("No order reviews yet");
                    else reviews.setText(String.format(Locale.ROOT, "Order rating: %s (%d)",
                            RatingStars.of(total / ratings.size()), ratings.size()));
                }
                @Override public void onBad(String message) { }
            });
        }
        content.addView(host.ui.coloredNotice("+", "Build your campus reputation", "Add products or resources to grow your profile.", host.blue, Color.WHITE, v -> host.showAppScreen("Market")));

        // Clear the session so the next launch starts at login.
        Button logout = host.ui.secondaryButton("Log out");
        logout.setOnClickListener(v -> {
            host.stopChatListeners();
            host.stopPresenceTracking();
            host.markOffline();
            new AuthRepository().logout();
            host.currentUid = "";
            host.adminAccount = false;
            com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS).edit().clear().apply();
            host.savedListings = 0;
            host.messageCount = 0;
            host.auth.showAuthScreen();
        });
        LinearLayout.LayoutParams outParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        outParams.topMargin = host.ui.dp(14);
        content.addView(logout, outParams);
    }

    public void pickProfilePhoto() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        host.startActivityForResult(picker, MainActivity.PROFILE_PHOTO_REQUEST);
    }

    private View profileAvatar(int size, int textSize, int textColor, int background) {
        String image = com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles")
                .getString("profile_image:" + host.currentStudentEmail, "");
        if (image != null && !image.isEmpty()) {
            ImageView photo = new ImageView(host);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setContentDescription(host.currentStudentName + " profile photo");
            Glide.with(host).load(image).circleCrop().into(photo);
            return photo;
        }
        TextView avatar = host.ui.titleText(host.ui.initials(host.currentStudentName), textColor, textSize);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(host.ui.round(background, 999));
        return avatar;
    }

    // Use the seller's avatar, not the signed-in student's.
    private View sellerAvatar(String email, String name, int size, int textSize, int textColor, int background) {
        String image = com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles")
                .getString("profile_image:" + email, "");
        if (image != null && !image.isEmpty()) {
            ImageView photo = new ImageView(host);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setContentDescription(name + " profile photo");
            Glide.with(host).load(image).circleCrop().into(photo);
            return photo;
        }
        TextView avatar = host.ui.titleText(host.ui.initials(name), textColor, textSize);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(host.ui.round(background, 999));
        return avatar;
    }

    private void changePassword() {
        LinearLayout form = host.ui.workflowForm();
        EditText oldPassword = host.ui.listingField(form, "Current password",
                "Enter the password you use now",
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText newPassword = host.ui.listingField(form, "New password",
                "10+ characters, with upper, lower, number and symbol",
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirmPassword = host.ui.listingField(form, "Confirm new password",
                "Type the new password again",
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        host.ui.workflowDialog("Change password", form, "Update password", () -> {
            String oldValue = host.ui.text(oldPassword);
            String newValue = host.ui.text(newPassword);
            String confirmValue = host.ui.text(confirmPassword);
            if (oldValue.isEmpty()) throw new RuntimeException("Enter your current password.");
            if (!host.ui.isStrongPassword(newValue)) {
                throw new RuntimeException("Use 10+ characters with uppercase, lowercase, a number and a symbol.");
            }
            if (!newValue.equals(confirmValue)) throw new RuntimeException("The new passwords don't match.");
            if (newValue.equals(oldValue)) throw new RuntimeException("Choose a password you haven't used here.");
            new AuthRepository().changePassword(oldValue, newValue, new AuthRepository.SimpleCallback() {
                @Override public void onOk() {
                    host.ui.showSimpleMessage("Password updated",
                            "Your CampusTrade password was changed on this account.");
                }
                @Override public void onBad(String message) {
                    host.ui.showSimpleMessage("Password didn't change", message);
                }
            });
        });
    }

    public void confirmLogout() {
        host.ui.dialog().setTitle("Log out?")
                .setMessage("You can sign back in with your student email at any time.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (d, which) -> signOutLocal()).show();
    }

    private void confirmDeactivate() {
        host.ui.dialog().setTitle("Deactivate account?")
                .setMessage("You will be signed out and can't sign in again until the campus team restores your account.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Deactivate", (d, which) ->
                        host.confirmSensitiveAction(() -> setAccountStatus("deactivated", false))).show();
    }

    private void confirmDelete() {
        host.ui.dialog().setTitle("Delete account?")
                .setMessage("Your public profile will be removed and you will be signed out. Your account remains unavailable unless the campus team restores it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, which) ->
                        host.confirmSensitiveAction(() -> setAccountStatus("deleted", true))).show();
    }

    private void setAccountStatus(String status, boolean deletePublicProfile) {
        new AuthRepository().setAccountStatus(host.currentUid, status, new AuthRepository.SimpleCallback() {
            @Override public void onOk() {
                if (deletePublicProfile) {
                    new AuthRepository().deleteOwnListings(host.currentUid, null);
                    new AuthRepository().deleteOwnFavourites(host.currentUid, null);
                    new AuthRepository().deletePublicProfile(host.currentUid, new AuthRepository.SimpleCallback() {
                        @Override public void onOk() { signOutLocal(); }
                        @Override public void onBad(String message) { signOutLocal(); }
                    });
                } else {
                    signOutLocal();
                }
            }
            @Override public void onBad(String message) {
                host.ui.showSimpleMessage("Account", message);
            }
        });
    }

    private void signOutLocal() {
        host.stopChatListeners();
        host.stopPresenceTracking();
        host.markOffline();
        new AuthRepository().logout();
        host.currentUid = "";
        host.currentStudentEmail = "";
        host.adminAccount = false;
        host.clearSensitiveVerification();
        com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS).edit().clear().apply();
        host.savedListings = 0;
        host.messageCount = 0;
        host.auth.showAuthScreen();
    }

    public void showSellerProfile(String email) {
        ArrayList<MainActivity.Product> active = new ArrayList<>();
        for (MainActivity.Product product : host.products) {
            if (product.sellerEmail.equalsIgnoreCase(email) && host.market.publicListing(product)) active.add(product);
        }
        int completed = 0;
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (order.isSeller(email) && order.status.equals(MainActivity.TransactionRecord.COMPLETED)) completed++;
        }
        final int completedCount = completed;

        Dialog page = new Dialog(host);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        LinearLayout header = host.ui.pageHeader("Seller profile", "Public campus profile", page::dismiss);
        View actions = host.new CampusAssetView(host, "flag", host.muted);
        actions.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        actions.setContentDescription("Seller actions");
        actions.setBackground(host.ui.strokedRound(host.page, 999, host.border));
        actions.setOnClickListener(v -> showStudentActions(email));
        header.addView(actions, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        root.addView(header);

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout hero = new LinearLayout(host);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(host.ui.dp(16), host.ui.dp(18), host.ui.dp(16), host.ui.dp(16));
        hero.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        String sellerDisplayName = host.studentName(email);
        View avatar = sellerAvatar(email, sellerDisplayName, host.ui.dp(84), 28, Color.WHITE, host.blue);
        hero.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(84), host.ui.dp(84)));
        TextView name = host.ui.titleText(sellerDisplayName, host.text, 20);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, host.ui.dp(12), 0, host.ui.dp(3));
        hero.addView(name);
        String cachedCourse = com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles")
                .getString("course:" + email, "");
        TextView program = host.ui.bodyText(cachedCourse.isEmpty() ? "Eduvos student" : cachedCourse, host.muted, 12);
        program.setGravity(Gravity.CENTER);
        hero.addView(program);

        LinearLayout badges = host.ui.horizontal();
        badges.setGravity(Gravity.CENTER);
        badges.setPadding(0, host.ui.dp(10), 0, 0);
        badges.addView(host.ui.chip("Eduvos student", host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255),
                host.readableInk(host.blue)));
        badges.addView(host.ui.space(host.ui.dp(6), 1));
        badges.addView(host.ui.chip(completedCount == 0 ? "New seller" : completedCount + " completed sales",
                host.nightActive ? Color.rgb(24, 54, 42) : Color.rgb(231, 247, 240), host.readableInk(host.green)));
        hero.addView(badges);

        TextView ratingSummary = host.ui.bodyText("No ratings yet", host.muted, 12);
        ratingSummary.setGravity(Gravity.CENTER);
        ratingSummary.setPadding(0, host.ui.dp(12), 0, 0);
        hero.addView(ratingSummary);
        Button chat = host.ui.primaryButton("Chat with seller");
        chat.setBackground(host.ui.round(host.primary, 8));
        LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        chatParams.topMargin = host.ui.dp(14);
        chat.setOnClickListener(v -> { page.dismiss(); host.inbox.showConversation(email); });
        hero.addView(chat, chatParams);
        content.addView(hero);

        profileHeading(content, "Active listings (" + active.size() + ")");
        if (active.isEmpty()) {
            content.addView(host.ui.emptyState("box", "Nothing listed right now",
                    "This seller has no active items. Check back after their next upload."));
        } else {
            LinearLayout gridRow = null;
            for (int i = 0; i < active.size(); i++) {
                MainActivity.Product product = active.get(i);
                if (i % 2 == 0) {
                    gridRow = host.ui.horizontal();
                    gridRow.setPadding(0, 0, 0, host.ui.dp(8));
                    content.addView(gridRow);
                }
                LinearLayout card = host.ui.card(host.page, 0, host.ui.dp(8));
                card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
                View thumb = host.market.listingThumbnail(product, false);
                card.addView(thumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(104)));
                TextView title = host.ui.titleText(product.title, host.text, 12);
                title.setMaxLines(1);
                title.setEllipsize(TextUtils.TruncateAt.END);
                title.setPadding(0, host.ui.dp(6), 0, 0);
                card.addView(title);
                LinearLayout priceRow = host.ui.horizontal();
                priceRow.setGravity(Gravity.CENTER_VERTICAL);
                priceRow.setPadding(0, host.ui.dp(3), 0, 0);
                TextView price = host.ui.bodyText(new java.math.BigDecimal(product.price).signum() == 0
                        ? "Free" : "R" + product.price, host.text, 12);
                price.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                priceRow.addView(price, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                priceRow.addView(host.ui.statusPill(product.status.equals("Available") ? "Available" : product.status));
                card.addView(priceRow);
                card.setOnClickListener(v -> { page.dismiss(); host.market.showProductDetails(product); });
                host.ui.attachPressAnimation(card);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (i % 2 == 1) cardParams.leftMargin = host.ui.dp(8);
                gridRow.addView(card, cardParams);
                if (i % 2 == 0) gridRow.addView(host.ui.space(host.ui.dp(8), 1));
            }
        }

        profileHeading(content, "Reviews");
        LinearLayout reviewBox = new LinearLayout(host);
        reviewBox.setOrientation(LinearLayout.VERTICAL);
        reviewBox.addView(host.ui.emptyState("check", "Loading reviews", "Getting this seller's order feedback."));
        content.addView(reviewBox);
        CloudSync.peerUid(host, email, new CloudSync.StrCallback() {
            @Override public void onOk(String uid) {
                new RatingRepository().forUser(uid, new RatingRepository.RatingListCallback() {
                    @Override public void onOk(java.util.List<com.campustrade.auth.model.Rating> ratings) {
                        if (host.isDestroyed() || host.isFinishing()) return;
                        reviewBox.removeAllViews();
                        double total = 0;
                        int count = 0;
                        for (com.campustrade.auth.model.Rating rating : ratings) {
                            total += rating.getRating();
                            count++;
                            String reviewer = CloudSync.displayName(host, rating.getReviewerID());
                            if (reviewer == null || reviewer.isEmpty()) reviewer = "Student";
                            reviewBox.addView(reviewCard(rating, reviewer, email));
                        }
                        if (count == 0) {
                            reviewBox.addView(host.ui.emptyState("check", "No reviews yet",
                                    "Reviews appear here after a completed order with this seller."));
                            ratingSummary.setText("No ratings yet");
                        } else {
                            ratingSummary.setText(String.format(Locale.ROOT, "%s from %d review%s",
                                    RatingStars.of(total / count), count, count == 1 ? "" : "s"));
                        }
                    }
                    @Override public void onBad(String message) {
                        reviewBox.removeAllViews();
                        reviewBox.addView(host.ui.emptyState("check", "Reviews unavailable",
                                "Check your connection and try again."));
                    }
                });
            }
            @Override public void onBad(String message) {
                reviewBox.removeAllViews();
                reviewBox.addView(host.ui.emptyState("check", "Profile unavailable",
                        "We couldn't load this seller's reviews right now."));
            }
        });

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    void profileHeading(LinearLayout content, String title) {
        settingsHeading(content, title);
    }

    int yearNumber(String stored) {
        if (stored == null) return 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(stored);
        if (!matcher.find()) return 0;
        try {
            int value = Integer.parseInt(matcher.group(1));
            return value >= 1 && value <= 7 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    String yearLabel(String stored) {
        if (stored == null || stored.trim().isEmpty()) return "";
        String value = stored.toLowerCase(Locale.ROOT);
        if (value.contains("honour")) return "Honours";
        if (value.contains("postgrad")) return "Postgraduate";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(stored);
        if (!matcher.find()) return "";
        try {
            int raw = Integer.parseInt(matcher.group(1));
            if (raw >= 1 && raw <= 7) return "Year " + raw;
            if (raw == 8) return "Honours";
            if (raw >= 9) return "Postgraduate";
        } catch (NumberFormatException ignored) { }
        return "";
    }

    public void showStudentActions(String email) {
        if (email == null || email.equalsIgnoreCase(host.currentStudentEmail)) return;
        boolean blocked = host.safety.blocks(host.currentStudentEmail, email);
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(settingsOption(blocked ? "check" : "shield",
                blocked ? "Unblock this student" : "Block this student",
                blocked ? "See their listings and messages again" : "Hide their listings and start no new chats",
                "", v -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    try {
                        host.safety.block(host.currentStudentEmail, email, !blocked, host.currentUid);
                        Toast.makeText(host, blocked ? "Student unblocked." : "Student blocked. You won't see them in Marketplace or Inbox.", Toast.LENGTH_SHORT).show();
                        if ("Market".equals(host.currentTab)) host.showAppScreen("Market");
                        if ("Inbox".equals(host.currentTab)) host.showAppScreen("Inbox");
                    } catch (RuntimeException e) {
                        host.ui.showSimpleMessage("Block", e.getMessage());
                    }
                }));
        host.ui.addMenuDivider(body);
        body.addView(settingsOption("flag", "Report this student", "Send a moderation report", "", v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            reportStudent(email);
        }));
        host.ui.addMenuDivider(body);
        body.addView(settingsOption("star", "View seller reviews", "Ratings from completed orders", "", v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            showStudentReviews(email);
        }));
        sheetRef[0] = host.ui.showBottomSheet("Seller actions", body);
    }

    private void reportStudent(String email) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Only report behaviour that breaks campus rules. The student is not told who reported them.",
                host.muted, 11));
        android.widget.Spinner reason = host.ui.listingSelector(form, "Reason", new String[] {
                "Suspicious behaviour", "Harassment or abuse", "Fake account", "Other"});
        EditText detail = host.ui.listingField(form, "Details", "Describe the issue", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        detail.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        Button submit = host.ui.primaryButton("Submit report");
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        submitParams.topMargin = host.ui.dp(10);
        form.addView(submit, submitParams);
        Dialog sheet = host.ui.showBottomSheet("Report student", form);
        submit.setOnClickListener(v -> {
            String text = host.ui.text(detail).trim();
            if (text.length() < 10) { detail.setError("Give us at least 10 characters so we can check it"); return; }
            String selected = reason.getSelectedItem().toString();
            submitReport(email, "user:" + email, "Seller: " + email, selected, text);
            sheet.dismiss();
        });
    }

    // Saved notes and listings share one page with tabs, not a chooser.
    public void showSavedResources() {
        Dialog page = new Dialog(host);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Saved resources", "Notes and listings you kept", page::dismiss));

        LinearLayout tabs = host.ui.horizontal();
        tabs.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(10));
        TextView notesTab = host.ui.filterPill("Study notes", true);
        TextView marketTab = host.ui.filterPill("Marketplace", false);
        notesTab.setTextSize(12);
        marketTab.setTextSize(12);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, host.ui.dp(42), 1);
        tabs.addView(notesTab, tabParams);
        LinearLayout.LayoutParams marketTabParams = new LinearLayout.LayoutParams(0, host.ui.dp(42), 1);
        marketTabParams.leftMargin = host.ui.dp(8);
        tabs.addView(marketTab, marketTabParams);
        root.addView(tabs);

        ScrollView scroll = new ScrollView(host);
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(host.ui.dp(20), 0, host.ui.dp(20), host.ui.dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        final int[] active = {0};
        Runnable render = () -> {
            list.removeAllViews();
            notesTab.setBackground(active[0] == 0 ? host.ui.round(host.blue, 999) : host.ui.strokedRound(host.surface(), 999, host.border));
            notesTab.setTextColor(active[0] == 0 ? Color.WHITE : host.text);
            marketTab.setBackground(active[0] == 1 ? host.ui.round(host.blue, 999) : host.ui.strokedRound(host.surface(), 999, host.border));
            marketTab.setTextColor(active[0] == 1 ? Color.WHITE : host.text);
            if (active[0] == 0) {
                int count = 0;
                for (MainActivity.AcademicItem item : host.academics.academicCalendarItems()) {
                    if (!item.kind.equals("Note") || !item.saved) continue;
                    count++;
                    LinearLayout card = host.ui.card(host.page, 0, host.ui.dp(12));
                    card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
                    card.addView(host.ui.titleText(item.title, host.text, 14));
                    card.addView(host.ui.bodyText((item.module.isEmpty() ? "Study note" : item.module)
                            + " · saved for later", host.muted, 11));
                    card.setOnClickListener(v -> { page.dismiss(); host.academics.showAcademicItem(item); });
                    host.ui.attachPressAnimation(card);
                    list.addView(card);
                }
                if (count == 0) {
                    list.addView(host.ui.emptyState("book", "No saved notes yet",
                            "Tap Save on a note in Study and it will wait for you here."));
                }
            } else {
                int count = 0;
                for (MainActivity.Product product : host.products) {
                    if (!product.savedBy.contains(host.currentStudentEmail)) continue;
                    count++;
                    LinearLayout card = host.ui.card(host.page, 0, host.ui.dp(10));
                    card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
                    LinearLayout row = host.ui.horizontal();
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    View thumb = host.market.listingThumbnail(product, false);
                    row.addView(thumb, new LinearLayout.LayoutParams(host.ui.dp(64), host.ui.dp(64)));
                    LinearLayout copy = new LinearLayout(host);
                    copy.setOrientation(LinearLayout.VERTICAL);
                    copy.setPadding(host.ui.dp(12), 0, 0, 0);
                    TextView title = host.ui.titleText(product.title, host.text, 13);
                    title.setMaxLines(1);
                    title.setEllipsize(TextUtils.TruncateAt.END);
                    copy.addView(title);
                    TextView price = host.ui.bodyText(new java.math.BigDecimal(product.price).signum() == 0
                            ? "Free" : "R" + product.price, host.text, 13);
                    price.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    copy.addView(price);
                    copy.addView(host.ui.statusPill(product.status));
                    row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    row.addView(host.new CampusAssetView(host, "chevron", host.muted),
                            new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16)));
                    card.addView(row);
                    card.setOnClickListener(v -> { page.dismiss(); host.market.showProductDetails(product); });
                    host.ui.attachPressAnimation(card);
                    list.addView(card);
                }
                if (count == 0) {
                    list.addView(host.ui.emptyState("heart", "No saved listings yet",
                            "Tap the heart on a marketplace card to keep it here."));
                }
            }
        };
        notesTab.setOnClickListener(v -> { active[0] = 0; render.run(); });
        marketTab.setOnClickListener(v -> { active[0] = 1; render.run(); });
        render.run();

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void showBlockedStudents() {
        java.util.Set<String> blocked = host.safety.blocked(host.currentStudentEmail);
        if (blocked.isEmpty()) {
            host.ui.showSimpleMessage("Blocked students", "You haven't blocked anyone yet.");
            return;
        }
        String[] people = blocked.toArray(new String[0]);
        host.ui.dialog().setTitle("Blocked students")
                .setItems(people, (d, which) -> {
                    host.safety.block(host.currentStudentEmail, people[which], false, host.currentUid);
                    Toast.makeText(host, "Student unblocked.", Toast.LENGTH_SHORT).show();
                    showBlockedStudents();
                }).setNegativeButton("Close", null).show();
    }

    public void showStudentReviews(String email) {
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(18), host.ui.dp(8), host.ui.dp(18), host.ui.dp(18));
        content.addView(host.ui.bodyText("Loading reviews...", host.muted, 12));
        ScrollView scroll = new ScrollView(host);
        scroll.addView(content);
        AlertDialog dialog = host.ui.dialog().setTitle("Reviews for " + host.studentName(email))
                .setView(scroll).setNegativeButton("Close", null).create();
        dialog.show();
        host.ui.addPageClose(dialog);
        CloudSync.peerUid(host, email, new CloudSync.StrCallback() {
            @Override public void onOk(String uid) {
                new RatingRepository().forUser(uid, new RatingRepository.RatingListCallback() {
                    @Override public void onOk(java.util.List<com.campustrade.auth.model.Rating> ratings) {
                        if (host.isDestroyed() || host.isFinishing()) return;
                        content.removeAllViews();
                        int shown = 0;
                        for (com.campustrade.auth.model.Rating rating : ratings) {
                            shown++;
                            String reviewer = CloudSync.displayName(host, rating.getReviewerID());
                            if (reviewer == null || reviewer.isEmpty()) reviewer = "Student";
                            content.addView(reviewCard(rating, reviewer, email));
                        }
                        if (shown == 0) {
                            content.addView(host.ui.emptyState("check", "No reviews yet",
                                    "Reviews appear after a completed order. This student has none so far."));
                        }
                    }
                    @Override public void onBad(String message) {
                        content.removeAllViews();
                        content.addView(host.ui.emptyState("check", "Reviews unavailable",
                                "Check your connection and try again."));
                    }
                });
            }
            @Override public void onBad(String message) {
                content.removeAllViews();
                content.addView(host.ui.emptyState("check", "Profile unavailable",
                        "We couldn't load this student's reviews right now."));
            }
        });
    }

    private LinearLayout reviewCard(com.campustrade.auth.model.Rating rating, String reviewer, String targetEmail) {
        LinearLayout card = host.ui.card(Color.WHITE, 12, host.ui.dp(12));
        card.addView(host.ui.bodyText(RatingStars.of(rating.getRating()), host.blue, 16));
        String comment = rating.getComment() == null ? "" : rating.getComment().trim();
        card.addView(host.ui.bodyText(reviewer + (comment.isEmpty() ? " left a rating." : ": " + comment), host.muted, 12));
        if (!rating.getReviewerID().equals(host.currentUid)) {
            TextView report = host.ui.linkText("Report this review");
            report.setTextSize(11);
            report.setPadding(0, host.ui.dp(8), 0, 0);
            report.setOnClickListener(v -> reportReview(rating, reviewer, targetEmail));
            card.addView(report);
        }
        return card;
    }

    private void reportReview(com.campustrade.auth.model.Rating rating, String reviewer, String targetEmail) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Report a review that breaks campus rules. The reviewer is not told who reported it.",
                host.muted, 11));
        Spinner reason = host.ui.listingSelector(form, "What is wrong?", new String[] {
                "Abusive or hateful review", "Spam or fake review", "Shares private information", "Other"});
        EditText detail = host.ui.listingField(form, "Details", "Tell us what happened", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        detail.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        Button submit = host.ui.primaryButton("Send report");
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        submitParams.topMargin = host.ui.dp(10);
        form.addView(submit, submitParams);
        Dialog sheet = host.ui.showBottomSheet("Report review", form);
        submit.setOnClickListener(v -> {
            String text = host.ui.text(detail).trim();
            if (text.length() < 10) { detail.setError("Add a little more detail first"); return; }
            submitReport(targetEmail, "review:" + rating.getDocId(),
                    "Review by " + reviewer + " (" + RatingStars.of(rating.getRating()) + ")",
                    reason.getSelectedItem().toString(), text);
            sheet.dismiss();
        });
    }

    private void submitReport(String targetEmail, String productId, String productTitle,
                              String reason, String detail) {
        String priority = reason.contains("Harassment") || reason.contains("Abuse") || reason.toLowerCase(Locale.ROOT).contains("hate")
                ? "High" : "Medium";
        String id = java.util.UUID.randomUUID().toString();
        MainActivity.ModerationReport report = new MainActivity.ModerationReport(id, productId,
                productTitle, host.currentStudentEmail, host.currentStudentName,
                reason, detail, priority, "Open", System.currentTimeMillis());
        host.moderationReports.add(report);
        if (!host.saveModerationReports()) {
            host.moderationReports.remove(report);
            Toast.makeText(host, "That report did not save. Give it another go.", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.Map<String, Object> doc = new java.util.HashMap<>();
        doc.put("reporterId", host.currentUid);
        doc.put("reporterEmail", report.reporterEmail);
        doc.put("reporterName", report.reporterName);
        doc.put("reportedEmail", targetEmail);
        doc.put("entityType", report.entityType());
        doc.put("reportedId", report.productId);
        doc.put("productId", report.productId);
        doc.put("productTitle", report.productTitle);
        doc.put("reason", report.reason);
        doc.put("detail", report.detail);
        doc.put("priority", report.priority);
        doc.put("state", report.state);
        doc.put("createdAt", com.google.firebase.Timestamp.now());
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("reports").document(id).set(doc)
                .addOnFailureListener(error -> Toast.makeText(host,
                        "Report saved on this phone. It will reach the campus team when you are back online.",
                        Toast.LENGTH_LONG).show());
        // Repeated or high-priority reports remove the listing from public view
        // until a moderator reviews it.
        if (!productId.startsWith("review:") && !productId.startsWith("user:")) {
            MainActivity.Product reportedProduct = null;
            for (MainActivity.Product product : host.products) {
                if (product.id.equals(productId)) { reportedProduct = product; break; }
            }
            if (reportedProduct != null) {
                int reportCount = 1;
                for (MainActivity.ModerationReport existing : host.moderationReports) {
                    if (existing.productId.equals(productId)) reportCount++;
                }
                if (report.priority.equals("High") || reportCount >= 2) {
                    reportedProduct.hidden = true;
                    if (host.market.saveProducts()) {
                        host.market.cloudMirrorListing(reportedProduct, false);
                    }
                    if (reportedProduct.sellerEmail != null && !reportedProduct.sellerEmail.isEmpty()) {
                        host.notification.addNotification(reportedProduct.sellerEmail, "Listing hidden",
                                reportedProduct.title + " was hidden pending a moderation review.", "marketplace", "");
                        host.notification.sendPush(reportedProduct.sellerEmail, "Listing hidden",
                                reportedProduct.title + " was hidden pending a moderation review.");
                    }
                }
            }
        }
        host.notification.showCampusNotification("Report sent", "The campus team has it and will take a look.", "marketplace");
    }

    // Save here first, then send to Firebase so a failure doesn't lose edits.
    public void showEditProfile() {
        final boolean returnToSettings = host.editingFromSettings;
        host.editingFromSettings = false;
        SharedPreferences prefs = com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_profiles");
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), 0, host.ui.dp(20), host.ui.dp(20));
        EditText name = host.ui.listingField(form, "Full name", "Your full name",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        name.setText(host.currentStudentName);
        EditText course = host.ui.listingField(form, "Programme (optional)", "Your programme", InputType.TYPE_CLASS_TEXT);
        course.setText(prefs.getString("course:" + host.currentStudentEmail, ""));
        String[] yearOptions = new String[host.YEARS.length + 1];
        yearOptions[0] = "Not specified";
        System.arraycopy(host.YEARS, 0, yearOptions, 1, host.YEARS.length);
        Spinner year = host.ui.listingSelector(form, "Year of study (optional)", yearOptions);
        String storedYear = prefs.getString("year:" + host.currentStudentEmail, "");
        int storedYearNumber = yearNumber(storedYear);
        int yearSelection = 0;
        if (storedYearNumber >= 1 && storedYearNumber <= 7) yearSelection = storedYearNumber;
        else if (storedYear.toLowerCase(Locale.ROOT).contains("honour")) yearSelection = 8;
        else if (storedYear.toLowerCase(Locale.ROOT).contains("postgrad")) yearSelection = 9;
        else if (storedYear.trim().matches("\\d+")) {
            int raw = Integer.parseInt(storedYear.trim());
            yearSelection = raw == 8 ? 8 : raw >= 9 ? 9 : 0;
        }
        year.setSelection(yearSelection);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
        course.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Edit profile", "Name, programme and year", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button saveButton = host.ui.primaryButton("Save changes");
        footer.addView(saveButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        root.addView(footer);
        saveButton.setOnClickListener(v -> {
            String fullName = host.ui.text(name).trim();
            String studyYear = year.getSelectedItemPosition() == 0 ? ""
                    : year.getSelectedItem().toString();
            if (fullName.length() < 2 || host.auth.isValidStudentNumber(fullName)) {
                name.setError("Use your full name, not your student number"); return;
            }
            host.currentStudentName = fullName;
            String updatedCourse = host.ui.text(course).trim();
            prefs.edit().putString("full_name:" + host.currentStudentEmail, fullName)
                    .putString("course:" + host.currentStudentEmail, updatedCourse)
                    .putString("year:" + host.currentStudentEmail, studyYear).apply();
            com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS).edit().putString("student_name", fullName).apply();
            new AuthRepository().updateProfile(CloudSync.uid(host), host.currentStudentEmail, fullName,
                    updatedCourse, studyYear, new AuthRepository.SimpleCallback() {
                        @Override public void onOk() { }
                        @Override public void onBad(String message) {
                            Toast.makeText(host,
                                    "Saved on this phone. The cloud copy did not go through - try again when you have signal.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
            page.dismiss();
            host.showAppScreen(returnToSettings ? "Settings" : "Profile");
        });

        page.setContentView(root);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }
}