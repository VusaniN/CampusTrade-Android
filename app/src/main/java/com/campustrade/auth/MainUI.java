package com.campustrade.auth;

import android.app.TimePickerDialog;
import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.content.res.ColorStateList;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import com.campustrade.auth.data.AuthRepository;
import android.text.TextUtils;

// Shared views and styles used across the app.
public class MainUI {

    final MainActivity host;

    public MainUI(MainActivity host) {
        this.host = host;
    }

    /**
     * Spinner rows use the screen theme,
     * so set their colours for dark mode.
     */
    ArrayAdapter<String> themedSpinnerAdapter(String[] options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(host, android.R.layout.simple_spinner_item, options) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(host.text);
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(host.text);
                view.setBackgroundColor(host.surface());
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    /** Tint checkboxes and radios for dark mode. */
    void themedToggle(android.widget.CompoundButton toggle) {
        toggle.setTextColor(host.text);
        int[][] states = {new int[] {android.R.attr.state_checked}, new int[] {}};
        toggle.setButtonTintList(new ColorStateList(states, new int[] {host.blue, host.muted}));
    }

    /** Match dialogs to dark mode. */
    AlertDialog.Builder dialog() {
        return host.nightActive
                ? new AlertDialog.Builder(host, R.style.EditorialDialogNight)
                : new AlertDialog.Builder(host);
    }

    /**
     * Add a close button to page-style dialogs
     * after they open.
     */
    void addPageClose(AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        View content = window.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        int alertTitleId = content.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = alertTitleId == 0 ? null : window.findViewById(alertTitleId);
        if (title == null) title = window.findViewById(android.R.id.title);
        if (title != null) {
            title.setPadding(title.getPaddingLeft(), title.getPaddingTop(),
                    Math.max(title.getPaddingRight(), dp(56)), title.getPaddingBottom());
        }
        ImageButton close = new ImageButton(host);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setImageTintList(ColorStateList.valueOf(host.muted));
        close.setContentDescription("Close");
        close.setBackground(round(Color.TRANSPARENT, 999));
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setElevation(dp(6));
        close.setMinimumWidth(dp(48));
        close.setMinimumHeight(dp(48));
        close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        params.topMargin = dp(4);
        params.rightMargin = dp(4);
        ((FrameLayout) content).addView(close, params);
    }

    void selectSpinner(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) if (spinner.getItemAtPosition(i).toString().equals(value)) spinner.setSelection(i);
    }

    LinearLayout workflowForm() {
        LinearLayout form = new LinearLayout(host); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(18)); return form;
    }

    void workflowDialog(String title, LinearLayout form, String action, Runnable save) {
        workflowDialog(title, form, action, save, false);
    }

    // Small form; attach Save after showing so errors keep it open.
    void workflowDialog(String title, LinearLayout form, String action, Runnable save, boolean required) {
        ScrollView scroll = new ScrollView(host); scroll.addView(form);
        AlertDialog.Builder builder = dialog().setTitle(title).setView(scroll)
                .setPositiveButton(action, null).setCancelable(!required);
        if (!required) builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { save.run(); dialog.dismiss(); }
            catch (RuntimeException e) { Toast.makeText(host, e.getMessage() == null ? "Couldn't save that change" : e.getMessage(), Toast.LENGTH_LONG).show(); }
        })); dialog.show();
        addPageClose(dialog);
    }

    // Card expiry only uses the month, so ignore the day.
    void showCardExpiryPicker(EditText target) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(host, (view, year, month, day) -> {
            java.util.Calendar picked = java.util.Calendar.getInstance();
            picked.set(year, month, 1);
            target.setText(new SimpleDateFormat("MM/yy", Locale.ROOT).format(picked.getTime()));
        }, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH), now.get(java.util.Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        java.util.Calendar max = java.util.Calendar.getInstance();
        max.add(java.util.Calendar.YEAR, 15);
        picker.getDatePicker().setMaxDate(max.getTimeInMillis());
        picker.setTitle("Card expiry month");
        picker.show();
    }

    // Pick a date, then a time; show the result on the button.
    void chooseFutureDateTime(Button button, long[] time) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(host, (view, year, month, day) -> new TimePickerDialog(host, (clock, hour, minute) -> {
            java.util.Calendar value = java.util.Calendar.getInstance(); value.set(year, month, day, hour, minute, 0); value.set(java.util.Calendar.MILLISECOND, 0);
            time[0] = value.getTimeInMillis(); button.setText(host.messageTime(time[0]));
        }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true).show(), now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH), now.get(java.util.Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMinDate(System.currentTimeMillis()); picker.show();
    }

    EditText listingField(LinearLayout form, String title, String hint, int type) {
        TextView caption = label(title);
        caption.setPadding(0, dp(12), 0, dp(8));
        form.addView(caption);
        EditText field = input(hint, type);
        field.setBackground(strokedRound(host.surface(), 8, host.border));
        form.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return field;
    }

    Spinner listingSelector(LinearLayout form, String title, String[] options) {
        TextView caption = label(title);
        caption.setPadding(0, dp(12), 0, dp(8));
        form.addView(caption);
        Spinner selector = new Spinner(host);
        selector.setAdapter(themedSpinnerAdapter(options));
        selector.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.surface()));
        selector.setContentDescription(title);
        selector.setBackground(strokedRound(host.surface(), 8, host.border));
        selector.setPadding(dp(12), 0, dp(12), 0);
        form.addView(selector, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return selector;
    }

    LinearLayout paymentPanelForm() {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(20));
        return form;
    }

    ScrollView paymentPanelScroll(LinearLayout form) {
        ScrollView scroll = new ScrollView(host); scroll.addView(form); return scroll;
    }

    // Compact tappable row for activity and orders.
    LinearLayout pulseRow(String icon, String title, String subtitle, String badge,
            int background, int accent, View.OnClickListener listener) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        View symbol = host.new CampusAssetView(host, icon, accent);
        symbol.setPadding(dp(10), dp(10), dp(10), dp(10));
        symbol.setBackground(round(background, 12));
        row.addView(symbol, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(6), 0);
        TextView heading = titleText(title, host.text, 12);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heading);
        TextView detail = bodyText(subtitle, host.muted, 11);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(chip(badge, background, accent));
        row.setOnClickListener(listener);
        attachPressAnimation(row);
        return row;
    }

    // Section title with an optional action.
    LinearLayout sectionHeading(String left, String right) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, dp(10));
        row.addView(titleText(left, host.text, 16), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (right == null || right.isEmpty()) return row;
        TextView action = linkText(right);
        if (right.equals("Edit profile")) {
            action.setGravity(Gravity.CENTER);
            action.setPadding(dp(12), dp(8), dp(12), dp(8));
            action.setBackground(strokedRound(Color.rgb(234, 242, 255), 999, host.blue));
        }
        action.setOnClickListener(v -> host.handleSectionAction(left, right));
        row.addView(action);
        return row;
    }

    void showSimpleMessage(String title, String message) {
        dialog()
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Save locally first. If the cloud copy fails,
     * tell the student and offer a retry.
     */
    void showCloudRetry(String title, String message, Runnable retry) {
        if (host.isDestroyed() || host.isFinishing()) return;
        host.runOnUiThread(() -> {
            if (host.isDestroyed() || host.isFinishing() || host.cloudRetryVisible) return;
            host.cloudRetryVisible = true;
            AlertDialog alert = dialog()
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Retry", (d, which) -> retry.run())
                    .setNegativeButton("Later", null)
                    .create();
            alert.setOnDismissListener(d -> host.cloudRetryVisible = false);
            alert.show();
        });
    }

    LinearLayout emptyCard(String message) {
        LinearLayout box = card(Color.WHITE, 16, dp(18));
        TextView view = bodyText(message, host.muted, 14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(24), dp(10), dp(24));
        box.addView(view);
        return box;
    }

    // Colour callout for tips and status.
    LinearLayout coloredNotice(String marker, String title, String subtitle, int bg, int accent, View.OnClickListener listener) {
        LinearLayout box = horizontal();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        box.setBackground(bottomRule(host.line));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        box.setLayoutParams(params);
        View avatar = host.new CampusAssetView(host, marker.equals("team") ? "team" : marker.equals("pin") ? "pin" : marker.equals("+") ? "plus" : marker.equals("sparkle") ? "sparkle" : "tag", accent == Color.WHITE ? host.blue : accent);
        avatar.setPadding(dp(6), dp(6), dp(6), dp(6));
        avatar.setBackground(round(accent == Color.WHITE ? Color.rgb(220, 233, 255) : bg, 999));
        box.addView(avatar, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(titleText(title, host.text, 12));
        copy.addView(bodyText(subtitle, host.muted, 11));
        box.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(host.new CampusAssetView(host, "chevron", host.muted), new LinearLayout.LayoutParams(dp(20), dp(20)));
        box.setOnClickListener(listener);
        attachPressAnimation(box);
        return box;
    }

    void addMenuDivider(LinearLayout menu) {
        View divider = new View(host);
        divider.setBackgroundColor(host.line);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(8);
        params.rightMargin = dp(8);
        menu.addView(divider, params);
    }

    // One settings row; most open another screen.
    LinearLayout menuItem(String icon, String label, String value) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(10), 0);
        View symbol = host.new CampusAssetView(host, icon, icon.equals("payment") ? host.green : host.blue);
        symbol.setPadding(dp(8), dp(8), dp(8), dp(8));
        symbol.setBackground(round(icon.equals("payment") ? Color.rgb(229, 247, 239) : Color.rgb(234, 242, 255), 10));
        row.addView(symbol, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView labelView = bodyText(label, host.text, 12);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labelView.setPadding(dp(11), 0, 0, 0);
        row.addView(labelView, new LinearLayout.LayoutParams(0, dp(56), 1));
        row.addView(bodyText(value, value.equals("Verified") ? host.readableInk(host.green) : host.muted, 12));
        View chevron = host.new CampusAssetView(host, "chevron", host.muted);
        chevron.setPadding(dp(4), 0, 0, 0);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(18)));
        row.setOnClickListener(v -> {
            if (label.equals("My marketplace listings")) {
                host.marketFilter = "Mine";
                host.showAppScreen("Market");
            } else if (label.equals("Saved resources")) {
                host.profile.showSavedResources();
            } else if (label.equals("Transactions") || label.equals("Payments & orders") || label.equals("Orders & transactions")) {
                host.order.showTransactionHistory();
            } else if (label.equals("Groups and societies")) {
                host.showAppScreen("Groups");
            } else if (label.equals("Campus verification")) {
                boolean verified = new AuthRepository().isCurrentEmailVerified();
                showSimpleMessage("Campus verification", verified ? "Your campus account is verified."
                        : "Your Eduvos email: " + host.currentStudentEmail + "\nVerification is pending confirmation by the campus.");
            } else if (label.equals("Notifications")) {
                host.notification.showNotificationHistory();
            } else {
                Toast.makeText(host, label + " isn't available right now.", Toast.LENGTH_SHORT).show();
            }
        });
        attachPressAnimation(row);
        return row;
    }

    TextView metric(String value, String label) {
        TextView metric = new TextView(host);
        metric.setText(value + "\n" + label);
        metric.setTextColor(host.text);
        metric.setGravity(Gravity.CENTER);
        metric.setTextSize(12);
        metric.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return metric;
    }

    TextView chip(String label, int bg, int fg) {
        TextView chip = bodyText(label, fg, 12);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setBackground(round(bg, 999));
        return chip;
    }

    // Status colours: green available, amber reserved,
    // grey sold. Red is not a status.
    int statusInk(String status) {
        String value = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (value.contains("completed") || value.contains("available") || value.equals("active")
                || value.contains("resolved") || value.contains("verified") || value.contains("accepted")
                || value.contains("confirmed") || value.contains("agreed") || value.contains("reviewed")
                || value.contains("joined")) {
            return host.readableInk(host.green);
        }
        if (value.contains("reserved") || value.contains("request") || value.contains("ordered")
                || value.contains("pending") || value.equals("new") || value.equals("review")
                || value.contains("under review") || value.contains("await") || value.contains("high")
                || value.contains("pay at collection") || value.contains("waiting")
                || value.equals("open") || value.contains("suspend")) {
            return host.readableInk(Color.rgb(166, 101, 0));
        }
        return host.muted;
    }

    int statusBackground(String status) {
        String value = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (value.contains("completed") || value.contains("available") || value.equals("active")
                || value.contains("resolved") || value.contains("verified") || value.contains("accepted")
                || value.contains("confirmed") || value.contains("agreed") || value.contains("reviewed")
                || value.contains("joined")) {
            return host.nightActive ? Color.rgb(28, 55, 44) : Color.rgb(228, 246, 237);
        }
        if (value.contains("reserved") || value.contains("request") || value.contains("ordered")
                || value.contains("pending") || value.equals("new") || value.equals("review")
                || value.contains("under review") || value.contains("await") || value.contains("high")
                || value.contains("pay at collection") || value.contains("waiting")
                || value.equals("open") || value.contains("suspend")) {
            return host.nightActive ? Color.rgb(58, 48, 26) : Color.rgb(255, 244, 218);
        }
        return host.nightActive ? Color.rgb(45, 45, 50) : Color.rgb(238, 237, 233);
    }

    TextView statusPill(String status) {
        String value = status == null || status.trim().isEmpty() ? "Unknown" : status;
        TextView pill = chip(value, statusBackground(value), statusInk(value));
        pill.setTextSize(10);
        pill.setSingleLine(true);
        pill.setPadding(dp(9), dp(4), dp(9), dp(4));
        return pill;
    }

    // Three button levels: filled for the main action,
    // outlined for secondary, text for quiet links.
    TextView textButton(String label) {
        TextView button = bodyText(label, host.muted, 12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        attachPressAnimation(button);
        return button;
    }

    android.app.Dialog bottomSheet() {
        android.app.Dialog dialog = new android.app.Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    /** Titled bottom sheet with a close button and scrolling body. */
    android.app.Dialog showBottomSheet(String title, LinearLayout body) {
        android.app.Dialog dialog = bottomSheet();
        LinearLayout sheet = new LinearLayout(host);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(20));
        sheet.setBackground(topRound(host.surface(), 20));
        View handle = new View(host);
        handle.setBackground(round(host.border, 999));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(34), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(12);
        sheet.addView(handle, handleParams);
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleText(title, host.text, 20), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = textButton("Close");
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close);
        sheet.addView(header);
        ScrollView scroll = new ScrollView(host);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)
                host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(host.getWindow().getDecorView().getWindowToken(), 0);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int) (host.getResources().getDisplayMetrics().heightPixels * 0.82f));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.35f);
        }
        return dialog;
    }

    /** Full-screen page for long details and forms. */
    android.app.Dialog fullScreenForm() {
        android.app.Dialog dialog = new android.app.Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    LinearLayout pageHeader(String title, String subtitle, Runnable back) {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(10));
        TextView close = textButton("Back");
        close.setOnClickListener(v -> back.run());
        header.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(titleText(title, host.text, 20));
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(bodyText(subtitle, host.muted, 11));
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return header;
    }

    LinearLayout emptyState(String icon, String title, String message) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(20), dp(36), dp(20), dp(36));
        View symbol = host.new CampusAssetView(host, icon, host.muted);
        symbol.setPadding(dp(16), dp(16), dp(16), dp(16));
        symbol.setBackground(round(host.nightActive ? Color.rgb(45, 45, 50) : Color.rgb(238, 237, 233), 999));
        box.addView(symbol, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView heading = titleText(title, host.text, 16);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(14), 0, dp(4));
        box.addView(heading);
        TextView copy = bodyText(message, host.muted, 12);
        copy.setGravity(Gravity.CENTER);
        box.addView(copy);
        return box;
    }

    TextView actionChip(String label) {
        TextView action = bodyText(label, host.text, 12);
        action.setSingleLine(true);
        action.setEllipsize(TextUtils.TruncateAt.END);
        action.setPadding(dp(12), dp(4), dp(12), dp(4));
        action.setGravity(Gravity.CENTER);
        action.setMinimumWidth(dp(48));
        action.setMinimumHeight(dp(40));
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setBackground(strokedRound(host.surface(), 999, host.border));
        attachPressAnimation(action);
        return action;
    }

    // Basic card style. Filled cards use the accent colour.
    LinearLayout card(int color, int radius, int padding) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(color == host.blue ? round(host.blue, 4) : bottomRule(host.line));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        card.setLayoutParams(params);
        return card;
    }

    LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    View space(int width, int height) {
        View view = new View(host);
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return view;
    }

    TextView tab(String label) {
        TextView tab = new TextView(host);
        tab.setText(label);
        tab.setGravity(Gravity.CENTER);
        tab.setTextSize(14);
        return tab;
    }

    TextView label(String label) {
        TextView view = bodyText(label, host.text, 13);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        params.bottomMargin = dp(7);
        view.setLayoutParams(params);
        return view;
    }

    TextView titleText(String value, int color, int size) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(size >= 16 ? Typeface.create("serif", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.BOLD));
        view.setIncludeFontPadding(false);
        return view;
    }

    TextView bodyText(String value, int color, int size) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        return view;
    }

    TextView tinyCaps(String value, int color) {
        TextView view = bodyText(value, color, 10);
        view.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        view.setLetterSpacing(0);
        return view;
    }

    TextView smallText(String value) {
        return bodyText(value, host.muted, 13);
    }

    TextView linkText(String value) {
        TextView view = bodyText(value, host.blue, 12);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinimumWidth(dp(48));
        view.setMinimumHeight(dp(48));
        attachPressAnimation(view);
        return view;
    }

    Button primaryButton(String value) {
        Button button = new Button(host);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setMinimumWidth(dp(48));
        button.setMinimumHeight(dp(48));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(host.primary, 4));
        attachPressAnimation(button);
        return button;
    }

    Button secondaryButton(String value) {
        Button button = new Button(host);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(host.text);
        button.setTextSize(14);
        button.setBackground(strokedRound(host.page, 4, host.text));
        attachPressAnimation(button);
        return button;
    }

    EditText input(String placeholder, int inputType) {
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setHint(placeholder);
        input.setInputType(inputType);
        input.setTextColor(host.text);
        input.setHintTextColor(host.muted);
        input.setTextSize(15);
        input.setMinimumHeight(dp(48));
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(strokedRound(host.surface(), 8, host.border));
        input.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return input;
    }

    EditText dialogInput(String placeholder, int inputType) {
        EditText input = input(placeholder, inputType);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        input.setLayoutParams(params);
        return input;
    }

    // Build the password field with Show/Hide.
    LinearLayout passwordField(String placeholder) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(8), 0);
        row.setBackground(strokedRound(host.surface(), 8, host.border));
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setHint(placeholder);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTypeface(Typeface.DEFAULT);
        input.setTextColor(host.text);
        input.setHintTextColor(host.muted);
        input.setTextSize(15);
        input.setBackgroundColor(Color.TRANSPARENT);
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView toggle = bodyText("Show", host.muted, 12);
        toggle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toggle.setGravity(Gravity.CENTER);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return row;
    }

    void styleTab(TextView tab, boolean active) {
        tab.setBackground(bottomRule(active ? host.blue : host.line));
        tab.setTextColor(active ? host.text : host.muted);
        tab.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    // Switch password visibility without losing text or cursor.
    void togglePassword(EditText input, boolean primary) {
        if (primary) {
            host.passwordVisible = !host.passwordVisible;
            setPasswordVisibility(input, host.passwordVisible);
            host.passwordToggle.setText(host.passwordVisible ? "Hide" : "Show");
        } else {
            host.confirmPasswordVisible = !host.confirmPasswordVisible;
            setPasswordVisibility(input, host.confirmPasswordVisible);
            host.confirmPasswordToggle.setText(host.confirmPasswordVisible ? "Hide" : "Show");
        }
    }

    void setPasswordVisibility(EditText input, boolean visible) {
        int cursor = input.getSelectionStart();
        input.setInputType(visible
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTypeface(Typeface.DEFAULT);
        input.setSelection(Math.max(cursor, 0));
    }

    // Signup and strength bar use the same rules.
    boolean isStrongPassword(String password) {
        return password.length() >= 10
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    void updatePasswordStrength() {
        if (host.passwordStrengthText == null) return;
        String password = text(host.passwordInput);
        if (!host.signupMode) {
            host.passwordStrengthText.setText("Password required to log in.");
            host.passwordStrengthText.setTextColor(host.muted);
            return;
        }
        int score = passwordScore(password);
        host.passwordStrengthText.setText(passwordComplexityMessage(password));
        if (score >= 5) {
            host.passwordStrengthText.setTextColor(host.readableInk(host.green));
        } else if (score >= 3) {
            host.passwordStrengthText.setTextColor(host.nightActive ? Color.rgb(244, 184, 74) : Color.rgb(180, 109, 0));
        } else {
            host.passwordStrengthText.setTextColor(host.nightActive ? Color.rgb(255, 125, 146) : Color.rgb(190, 18, 60));
        }
    }

    int passwordScore(String password) {
        int score = 0;
        if (password.length() >= 10) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;
        return score;
    }

    String passwordComplexityMessage(String password) {
        ArrayList<String> missing = new ArrayList<>();
        if (password.length() < 10) missing.add("10+ characters");
        if (!password.matches(".*[A-Z].*")) missing.add("uppercase letter");
        if (!password.matches(".*[a-z].*")) missing.add("lowercase letter");
        if (!password.matches(".*\\d.*")) missing.add("number");
        if (!password.matches(".*[^A-Za-z0-9].*")) missing.add("symbol");
        if (missing.isEmpty()) return "Strong password. Your account is better protected.";
        return "Add: " + joinList(missing);
    }

    String joinList(ArrayList<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    // 999 makes a pill shape; other corners stop at 12dp.
    GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(editorialSurface(color));
        drawable.setCornerRadius(dp(radiusDp >= 999 ? radiusDp : Math.min(radiusDp, 12)));
        return drawable;
    }

    GradientDrawable strokedRound(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = round(color, radiusDp);
        drawable.setStroke(dp(1), editorialSurface(strokeColor));
        return drawable;
    }

    GradientDrawable topRound(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(editorialSurface(color));
        float radius = dp(Math.min(radiusDp, 8));
        drawable.setCornerRadii(new float[] { radius, radius, radius, radius, 0, 0, 0, 0 });
        return drawable;
    }

    int editorialSurface(int color) {
        if (color == host.page || color == host.line || color == host.border) return color;
        if (Color.alpha(color) == 255 && Color.red(color) >= 210 && Color.green(color) >= 210 && Color.blue(color) >= 210) return host.page;
        return color;
    }

    android.graphics.drawable.Drawable bottomRule(int color) {
        return edgeRule(color, false);
    }

    // Paint the page colour with a single 1dp rule.
    android.graphics.drawable.Drawable edgeRule(int color, boolean top) {
        return new android.graphics.drawable.Drawable() {
            private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                ink.setColor(host.page);
                canvas.drawRect(bounds, ink);
                ink.setColor(color);
                float y = top ? bounds.top : bounds.bottom - dp(1);
                canvas.drawRect(bounds.left, y, bounds.right, y + dp(1), ink);
            }
            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
        };
    }

    View editorialDivider(int color) {
        View divider = new View(host);
        divider.setBackgroundColor(color);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return divider;
    }

    TextView wordmark() {
        TextView brand = bodyText("CampusTrade", host.text, 22);
        brand.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        brand.setContentDescription("CampusTrade");
        return brand;
    }

    String text(EditText input) {
        return input.getText().toString();
    }

    String initials(String name) {
        String clean = name.trim();
        if (clean.isEmpty()) return "ES";
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    int dp(int value) {
        return (int) (value * host.getResources().getDisplayMetrics().density + 0.5f);
    }

    // Shrink on touch, grow on hover, then run the click listener.
    void attachPressAnimation(View view) {
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start();
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });
    }

    LinearLayout group(LinearLayout parent) {
        LinearLayout group = new LinearLayout(host);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group);
        return group;
    }

    TextView filterPill(String label, boolean active) {
        TextView pill = bodyText(label, active ? Color.WHITE : host.text, 12);
        pill.setGravity(Gravity.CENTER);
        pill.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setPadding(dp(12), dp(4), dp(12), dp(4));
        pill.setMinimumWidth(dp(48));
        pill.setMinimumHeight(dp(40));
        pill.setBackground(active ? round(host.blue, 999) : strokedRound(host.surface(), 999, host.border));
        attachPressAnimation(pill);
        return pill;
    }
}