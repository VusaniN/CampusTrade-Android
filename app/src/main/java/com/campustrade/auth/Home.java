package com.campustrade.auth;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import android.text.TextUtils;

// Home screen with reminders, quick actions and purchases.
public class Home {

    private final MainActivity host;

    public Home(MainActivity host) {
        this.host = host;
    }

    public void buildHome(LinearLayout content) {
        TextView date = host.ui.tinyCaps(new SimpleDateFormat("EEE dd MMM", Locale.getDefault())
                .format(new Date()).toUpperCase(Locale.ROOT) + "  /  EDUVOS", host.muted);
        date.setPadding(0, host.ui.dp(8), 0, host.ui.dp(10));
        content.addView(date);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        content.addView(host.ui.titleText(hour < 12 ? "Good morning," : hour < 18 ? "Good afternoon," : "Good evening,", host.text, 42));
        TextView student = host.ui.titleText(host.currentStudentName + ".", host.blue, 42);
        student.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        student.setPadding(0, 0, 0, host.ui.dp(24));
        content.addView(student);

        ArrayList<MainActivity.AcademicItem> due = new ArrayList<>();
        for (MainActivity.AcademicItem item : host.academics.academicCalendarItems()) {
            if (item.kind.equals("Deadline") && item.due >= System.currentTimeMillis()) due.add(item);
        }
        java.util.Collections.sort(due, (a, b) -> Long.compare(a.due, b.due));
        content.addView(host.ui.editorialDivider(host.text));
        TextView nextLabel = host.ui.tinyCaps("NEXT UP", host.muted);
        nextLabel.setPadding(0, host.ui.dp(16), 0, host.ui.dp(6));
        content.addView(nextLabel);

        String reminderTitle = host.settingsPrefs().getString("next_up_title", "");
        long reminderTime = host.settingsPrefs().getLong("next_up_time", 0);
        boolean hasReminder = !reminderTitle.trim().isEmpty();
        LinearLayout next = host.ui.horizontal();
        next.setGravity(Gravity.CENTER_VERTICAL);
        next.setPadding(0, 0, 0, host.ui.dp(20));
        String nextTime = hasReminder
                ? reminderTime > 0 ? new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(reminderTime)) : "•"
                : "+";
        TextView time = host.ui.titleText(nextTime, host.text, hasReminder ? 44 : 36);
        next.addView(time, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout nextCopy = new LinearLayout(host);
        nextCopy.setOrientation(LinearLayout.VERTICAL);
        nextCopy.setPadding(host.ui.dp(12), 0, 0, 0);
        TextView nextTitle = host.ui.bodyText(hasReminder ? reminderTitle : "Set a reminder", host.text, 14);
        nextTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nextTitle.setGravity(Gravity.END);
        nextTitle.setMaxLines(1);
        nextTitle.setEllipsize(TextUtils.TruncateAt.END);
        nextCopy.addView(nextTitle);
        String nextDetailText = hasReminder && reminderTime > 0
                ? "Reminder · " + new SimpleDateFormat("EEE dd MMM, HH:mm", Locale.getDefault()).format(new Date(reminderTime))
                : hasReminder ? "Reminder · tap to add a time"
                : "Tap to add your reminder";
        TextView nextDetail = host.ui.bodyText(nextDetailText, host.muted, 12);
        nextDetail.setGravity(Gravity.END);
        nextDetail.setMaxLines(1);
        nextDetail.setEllipsize(TextUtils.TruncateAt.END);
        nextCopy.addView(nextDetail);
        next.addView(nextCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        next.setOnClickListener(v -> editNextUpReminder());
        host.ui.attachPressAnimation(next);
        content.addView(next);
        content.addView(host.ui.editorialDivider(host.text));
        LinearLayout dueHeading = host.ui.horizontal();
        dueHeading.setPadding(0, host.ui.dp(16), 0, host.ui.dp(6));
        dueHeading.addView(host.ui.tinyCaps("DUE SOON", host.muted), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        dueHeading.addView(host.ui.tinyCaps(due.size() + " UPCOMING", host.muted));
        content.addView(dueHeading);
        if (due.isEmpty()) {
            TextView empty = host.ui.bodyText("No upcoming deadlines.", host.muted, 14);
            empty.setPadding(0, host.ui.dp(16), 0, host.ui.dp(16));
            content.addView(empty);
        }
        for (int i = 0; i < Math.min(3, due.size()); i++) {
            MainActivity.AcademicItem item = due.get(i);
            LinearLayout deadline = host.ui.horizontal();
            deadline.setGravity(Gravity.CENTER_VERTICAL);
            deadline.setPadding(0, host.ui.dp(14), 0, host.ui.dp(14));
            deadline.setBackground(host.ui.bottomRule(host.line));
            TextView day = host.ui.bodyText(new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(item.due)), host.text, 12);
            day.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            deadline.addView(day, new LinearLayout.LayoutParams(host.ui.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name = host.ui.bodyText(item.title, host.text, 14);
            name.setPadding(host.ui.dp(4), 0, host.ui.dp(8), 0);
            deadline.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            View open = host.new CampusAssetView(host, "chevron", host.text);
            open.setBackground(host.ui.strokedRound(host.page, 999, host.text));
            open.setPadding(host.ui.dp(4), host.ui.dp(4), host.ui.dp(4), host.ui.dp(4));
            deadline.addView(open, new LinearLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24)));
            deadline.setOnClickListener(v -> host.academics.showAcademicItem(item));
            host.ui.attachPressAnimation(deadline);
            content.addView(deadline);
        }

        LinearLayout quick = host.ui.horizontal();
        quick.setPadding(0, host.ui.dp(14), 0, 0);
        quick.addView(quickAction("note", "Share note", v -> host.academics.showAcademicEditor(null, "Note", 0)), new LinearLayout.LayoutParams(0, host.ui.dp(84), 1));
        quick.addView(host.ui.space(host.ui.dp(10), 1));
        quick.addView(quickAction("tag", "Sell item", v -> {
            host.showAppScreen("Market");
            host.market.showAddProductDialog();
        }), new LinearLayout.LayoutParams(0, host.ui.dp(84), 1));
        quick.addView(host.ui.space(host.ui.dp(10), 1));
        quick.addView(quickAction("team", "Find team", v -> host.showAppScreen("Groups")), new LinearLayout.LayoutParams(0, host.ui.dp(84), 1));
        content.addView(quick);

        int unread = host.inbox.unreadMessages();
        int available = 0;
        for (MainActivity.Product product : host.products) if (host.market.publicListing(product)) available++;
        int offersToAnswer = 0;
        for (CampusFeatures.Offer offer : host.featureStore.data.offers) {
            if (offer.seller.equals(host.currentStudentEmail) && offer.pending()
                    && offer.turn.equals(host.currentStudentEmail)) offersToAnswer++;
        }
        LinearLayout counts = host.ui.horizontal();
        counts.setPadding(0, host.ui.dp(14), 0, 0);
        counts.addView(countTile(String.valueOf(available), "Live listings", v -> host.showAppScreen("Market")),
                new LinearLayout.LayoutParams(0, host.ui.dp(78), 1));
        counts.addView(host.ui.space(host.ui.dp(8), 1));
        counts.addView(countTile(String.valueOf(unread), "Unread", v -> host.showAppScreen("Inbox")),
                new LinearLayout.LayoutParams(0, host.ui.dp(78), 1));
        counts.addView(host.ui.space(host.ui.dp(8), 1));
        counts.addView(countTile(String.valueOf(offersToAnswer), "Offers", v -> host.showAppScreen("Offers")),
                new LinearLayout.LayoutParams(0, host.ui.dp(78), 1));
        content.addView(counts);

        content.addView(host.ui.sectionHeading("Campus pulse", ""));
        LinearLayout pulse = host.ui.card(Color.WHITE, 16, host.ui.dp(12));
        pulse.addView(host.ui.pulseRow("note", "Notes & resources", "Your academic workspace", "Notes",
                Color.rgb(234, 242, 255), host.blue, v -> host.showAppScreen("Academics")));
        pulse.addView(host.ui.pulseRow("laptop", "Student listings", host.products.isEmpty()
                ? "No items listed yet" : host.products.size() + " item(s) on campus", "Market",
                Color.rgb(229, 247, 239), Color.rgb(5, 150, 105), v -> host.showAppScreen("Market")));
        pulse.addView(host.ui.pulseRow("team", "Study connections", host.inbox.unreadMessages() > 0
                ? host.inbox.unreadMessages() + " unread message(s)" : "Connect with other students", "Inbox",
                Color.rgb(255, 244, 218), Color.rgb(217, 139, 0), v -> host.showAppScreen("Inbox")));
        pulse.addView(host.ui.pulseRow("tag", "Offers to review", offersToAnswer > 0
                ? offersToAnswer + " waiting for your reply" : "No offers waiting", "Offers",
                Color.rgb(255, 244, 218), Color.rgb(166, 101, 0), v -> host.showAppScreen("Offers")));
        content.addView(pulse);
        content.addView(classCard("Your academics", "Modules, notes and deadlines", v -> host.showAppScreen("Academics")));
    }

    /** Buyer's purchased books and notes. */
    void renderPurchased(LinearLayout content) {
        content.addView(host.ui.sectionHeading("Books/Notes purchased", ""));
        int bought = 0;
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (!order.isBuyer(host.currentStudentEmail)) continue;
            // Only completed purchases appear.
            if (!order.status.equals(MainActivity.TransactionRecord.COMPLETED)) continue;
            bought++;
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            TextView boughtTitle = host.ui.titleText(order.itemTitle, host.text, 12);
            boughtTitle.setSingleLine(true);
            boughtTitle.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(boughtTitle);
            copy.addView(host.ui.bodyText("R" + order.amount + " / " + order.statusLabel()
                    + " / " + host.market.sellerLabel(order.sellerEmail), host.muted, 11));
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final MainActivity.TransactionRecord boughtOrder = order;
            row.setOnClickListener(v -> host.order.showOrderDetails(boughtOrder));
            host.ui.attachPressAnimation(row);
            content.addView(row);
        }
        if (bought == 0) {
            content.addView(host.ui.emptyCard("Nothing purchased yet. Buy a listing to see it here."));
        }
    }

    LinearLayout quickAction(String icon, String label, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        View iconView = host.new CampusAssetView(host, icon, host.blue);
        iconView.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        iconView.setBackground(host.ui.round(host.nightActive ? Color.rgb(40, 52, 70) : Color.rgb(234, 242, 255), 12));
        TextView labelView = host.ui.bodyText(label, host.text, 12);
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        labelView.setPadding(0, host.ui.dp(6), 0, 0);
        box.addView(iconView, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        box.addView(labelView);
        if (listener != null) {
            box.setOnClickListener(listener);
        } else {
            box.setOnClickListener(v -> Toast.makeText(host, label + " isn't available right now.", Toast.LENGTH_SHORT).show());
        }
        host.ui.attachPressAnimation(box);
        return box;
    }

    LinearLayout countTile(String value, String label, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        TextView number = host.ui.titleText(value, host.text, 22);
        number.setGravity(Gravity.CENTER);
        box.addView(number);
        TextView caption = host.ui.bodyText(label, host.muted, 10);
        caption.setGravity(Gravity.CENTER);
        box.addView(caption);
        box.setOnClickListener(listener);
        host.ui.attachPressAnimation(box);
        return box;
    }

    LinearLayout classCard(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout box = host.ui.horizontal();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(host.ui.dp(14), host.ui.dp(11), host.ui.dp(14), host.ui.dp(11));
        box.setBackground(host.ui.bottomRule(host.line));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = host.ui.dp(14);
        box.setLayoutParams(params);
        View calendarIcon = host.new CampusAssetView(host, "calendar", host.blue);
        calendarIcon.setPadding(host.ui.dp(9), host.ui.dp(9), host.ui.dp(9), host.ui.dp(9));
        calendarIcon.setBackground(host.ui.round(Color.rgb(234, 242, 255), 12));
        box.addView(calendarIcon, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(12), 0, 0, 0);
        copy.addView(host.ui.titleText(title, host.text, 12));
        copy.addView(host.ui.bodyText(subtitle, host.muted, 11));
        box.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(host.new CampusAssetView(host, "pin", host.blue), new LinearLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24)));
        box.setOnClickListener(listener);
        host.ui.attachPressAnimation(box);
        return box;
    }

    // Next up is a reminder the student sets,
    // not a listing or a textbook.
    private void editNextUpReminder() {
        String existingTitle = host.settingsPrefs().getString("next_up_title", "");
        long existingTime = host.settingsPrefs().getLong("next_up_time", 0);
        final long[] time = {existingTime};
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Set what's next for you. It shows on Home until you change or clear it.",
                host.muted, 11));
        EditText title = host.ui.listingField(form, "Reminder", "Assignment, meeting, revision...", InputType.TYPE_CLASS_TEXT);
        title.setText(existingTitle);
        Button when = host.ui.secondaryButton(existingTime > 0 ? host.messageTime(existingTime) : "Choose date & time");
        when.setOnClickListener(v -> host.ui.chooseFutureDateTime(when, time));
        LinearLayout.LayoutParams whenParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        whenParams.topMargin = host.ui.dp(6);
        form.addView(when, whenParams);
        Button save = host.ui.primaryButton("Save reminder");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        saveParams.topMargin = host.ui.dp(10);
        form.addView(save, saveParams);
        Dialog sheet = host.ui.showBottomSheet("Next up reminder", form);
        save.setOnClickListener(v -> {
            String value = host.ui.text(title).trim();
            if (value.length() < 2) {
                title.setError("Write a short reminder");
                return;
            }
            if (time[0] <= System.currentTimeMillis()) {
                Toast.makeText(host, "Choose a future date and time.", Toast.LENGTH_SHORT).show();
                return;
            }
            host.settingsPrefs().edit()
                    .putString("next_up_title", value)
                    .putLong("next_up_time", time[0])
                    .apply();
            sheet.dismiss();
            host.showAppScreen("Home");
        });
        if (!existingTitle.isEmpty() || existingTime > 0) {
            TextView clear = host.ui.textButton("Clear reminder");
            clear.setOnClickListener(v -> {
                host.settingsPrefs().edit().remove("next_up_title").remove("next_up_time").apply();
                sheet.dismiss();
                host.showAppScreen("Home");
            });
            form.addView(clear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42)));
        }
    }
}