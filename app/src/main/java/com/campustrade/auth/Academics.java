package com.campustrade.auth;

import android.app.TimePickerDialog;
import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import android.text.TextUtils;

// Study screen for notes, deadlines, events and the calendar.
public class Academics {

    private final MainActivity host;

    public Academics(MainActivity host) {
        this.host = host;
    }

    // Build the Study page and its coming-up list.
    public void buildAcademics(LinearLayout content) {
        LinearLayout searchRow = host.ui.horizontal();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(host.ui.dp(12), 0, host.ui.dp(4), 0);
        searchRow.setBackground(host.ui.bottomRule(host.text));
        searchRow.addView(host.new CampusAssetView(host, "search", host.muted), new LinearLayout.LayoutParams(host.ui.dp(20), host.ui.dp(20)));
        EditText search = host.ui.input("Search notes or courses", InputType.TYPE_CLASS_TEXT);
        search.setTextSize(13);
        search.setBackgroundColor(Color.TRANSPARENT);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        View filter = host.new CampusAssetView(host, "sliders", host.blue);
        filter.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        filter.setContentDescription("Filter academic items");
        filter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        filter.setOnClickListener(v -> {
            String[] modes = {"All", "Note", "Deadline", "Event", "Purchased"};
            host.ui.dialog().setTitle("Show academic items").setItems(modes, (d, which) -> {
                host.academicFilter = modes[which]; host.showAppScreen("Academics");
            }).show();
        });
        searchRow.addView(filter, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        content.addView(searchRow);
        ArrayList<MainActivity.AcademicItem> items = academicCalendarItems();
        LinearLayout actions = host.ui.horizontal();
        for (String type : new String[] {"Note", "Event", "Deadline"}) {
            TextView add = host.ui.actionChip("+ " + type);
            add.setOnClickListener(v -> showAcademicEditor(null, type, 0));
            actions.addView(add, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        }
        content.addView(actions);
        // Counts deadlines and the student's purchases for the summary card.
        int deadlines = 0, purchased = 0, notes = 0, events = 0;
        for (MainActivity.AcademicItem item : items) {
            if (item.scheduled()) deadlines++;
            if (item.kind.equals("Note")) notes++;
            if (item.kind.equals("Event")) events++;
        }
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (order.isBuyer(host.currentStudentEmail)
                    && order.status.equals(MainActivity.TransactionRecord.COMPLETED)) purchased++;
        }
        LinearLayout summary = host.ui.card(host.page, 0, host.ui.dp(18));
        LinearLayout metrics = host.ui.horizontal();
        String[][] stats = {{String.valueOf(notes), "Notes"}, {String.valueOf(deadlines), "Deadlines"},
                {String.valueOf(events), "Events"}, {String.valueOf(purchased), "Purchased"}};
        for (String[] stat : stats) {
            TextView value = host.ui.metric(stat[0], stat[1]);
            value.setTextColor(host.text);
            metrics.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        summary.addView(metrics);
        content.addView(summary);
        LinearLayout results = new LinearLayout(host);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        renderAcademics(results, items, "");
        search.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { renderAcademics(results, items, s.toString()); }
        });
    }

    // Filter by type/search and show coming-up items above notes.
    public void renderAcademics(LinearLayout content, ArrayList<MainActivity.AcademicItem> all, String query) {
        content.removeAllViews();
        ArrayList<MainActivity.AcademicItem> items = new ArrayList<>();
        for (MainActivity.AcademicItem item : all) if ((host.academicFilter.equals("All") || host.academicFilter.equals(item.kind))
                && (item.title + " " + item.module + " " + item.detail).toLowerCase(Locale.ROOT)
                .contains(query.trim().toLowerCase(Locale.ROOT))) items.add(item);
        // Saved notes get their own list.
        if (host.academicFilter.equals("Saved")) {
            ArrayList<MainActivity.AcademicItem> savedNotes = new ArrayList<>();
            for (MainActivity.AcademicItem item : all) if (item.kind.equals("Note") && item.saved
                    && (item.title + " " + item.module + " " + item.detail).toLowerCase(Locale.ROOT)
                    .contains(query.trim().toLowerCase(Locale.ROOT))) savedNotes.add(item);
            renderAcademicNotes(content, savedNotes);
            return;
        }
        if (host.academicFilter.equals("Note")) {
            renderAcademicNotes(content, items); return;
        }
        if (query.trim().isEmpty()) host.home.renderPurchased(content);
        if (host.academicFilter.equals("Purchased")) return;
        LinearLayout heading = host.ui.horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, host.ui.dp(18), 0, host.ui.dp(8));
        heading.addView(host.ui.titleText("Coming up", host.text, 16), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView calendar = host.ui.linkText("View calendar");
        calendar.setOnClickListener(v -> showAcademicCalendar());
        heading.addView(calendar);
        content.addView(heading);
        LinearLayout upcoming = host.ui.card(Color.WHITE, 16, host.ui.dp(12));
        java.util.Collections.sort(items, (a, b) -> Long.compare(a.due, b.due));
        int deadlines = 0;
        for (MainActivity.AcademicItem item : items) {
            if (!item.scheduled() || item.due < System.currentTimeMillis()) continue;
            deadlines++;
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
            TextView badge = host.ui.bodyText(new SimpleDateFormat("dd\nMMM", Locale.getDefault()).format(new Date(item.due)), host.blue, 12);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(host.ui.round(Color.rgb(234, 242, 255), 12));
            row.addView(badge, new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(48)));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(12), 0, 0, 0);
            TextView title = host.ui.titleText(item.title, host.text, 12);
            title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);
            TextView detail = host.ui.bodyText(item.kind + " / " + (item.module.isEmpty() ? "" : item.module + " / ")
                    + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(item.due)), host.muted, 11);
            detail.setSingleLine(true); detail.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(detail);
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(host.new CampusAssetView(host, "chevron", host.muted), new LinearLayout.LayoutParams(host.ui.dp(20), host.ui.dp(20)));
            row.setOnClickListener(v -> showAcademicItem(item));
            upcoming.addView(row);
        }
        if (deadlines == 0) upcoming.addView(host.ui.bodyText("No upcoming events or deadlines in this view.", host.muted, 12));
        content.addView(upcoming);
        renderAcademicNotes(content, items);
    }

    // Draw note cards and mark group notes as Shared.
    public void renderAcademicNotes(LinearLayout content, ArrayList<MainActivity.AcademicItem> items) {
        content.addView(host.ui.sectionHeading(host.academicFilter.equals("Saved") ? "Saved notes" : "Notes & resources", ""));
        int noteCount = 0;
        for (MainActivity.AcademicItem item : items) if (item.kind.equals("Note")) {
            noteCount++;
            content.addView(host.ui.pulseRow("note", item.title, item.module + (item.id.startsWith("group:") ? " / Group note" : " / Personal note"),
                    item.saved ? "Saved" : item.id.startsWith("group:") ? "Shared" : "Note", Color.rgb(234, 242, 255), host.blue, v -> showAcademicItem(item)));
        }
        if (noteCount == 0) content.addView(host.ui.emptyCard(host.academicFilter.equals("Saved") ? "No saved notes yet." : "No notes in this view yet."));
        content.addView(host.ui.coloredNotice("team", "Study connections", "Find students to study with", Color.rgb(229, 247, 239), host.green,
                v -> host.showAppScreen("Groups")));
    }

    public void showAcademicDialog() {
        showAcademicEditor(null, "Course", 0);
    }

    // One full-screen editor for each type. Save is the main action;
    // Share saves first, then shares.
    public void showAcademicEditor(MainActivity.AcademicItem existing, String initialKind, long calendarDay) {
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        String mode = existing == null ? "New " + initialKind.toLowerCase(Locale.ROOT)
                : "Edit " + existing.kind.toLowerCase(Locale.ROOT);
        root.addView(host.ui.pageHeader(mode, "Study hub", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Spinner kind = host.ui.listingSelector(form, "Type", new String[] {"Course", "Note", "Deadline", "Event"});
        host.ui.selectSpinner(kind, existing == null ? initialKind : existing.kind);
        TextView typeHint = host.ui.bodyText("", host.muted, 11);
        form.addView(typeHint);
        EditText moduleInput = host.ui.listingField(form, "Module or course code", "Module", InputType.TYPE_CLASS_TEXT);
        EditText titleInput = host.ui.listingField(form, "Title", "Title", InputType.TYPE_CLASS_TEXT);
        EditText noteInput = host.ui.listingField(form, "Details (optional)", "Details", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        titleInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
        noteInput.setSingleLine(false); noteInput.setMinLines(3); noteInput.setGravity(Gravity.TOP | Gravity.START);
        noteInput.setPadding(host.ui.dp(14), host.ui.dp(10), host.ui.dp(14), host.ui.dp(10));
        noteInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2000)});
        noteInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(112)));
        EditText progress = host.ui.listingField(form, "Course progress (%)", "0", InputType.TYPE_CLASS_NUMBER);
        View progressLabel = form.getChildAt(form.getChildCount() - 2);
        progress.setFilters(new InputFilter[] {new InputFilter.LengthFilter(3)});
        final long[] due = {existing == null ? 0 : existing.due};
        final long[] end = {existing == null ? 0 : existing.end};
        Button date = host.ui.secondaryButton(due[0] == 0 ? "Choose date & time" : host.academicDate(due[0]));
        date.setOnClickListener(v -> chooseAcademicDateTime(date, due, calendarDay, false));
        form.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        Button finish = host.ui.secondaryButton(end[0] == 0 ? "Choose event end (optional)" : host.academicDate(end[0]));
        finish.setOnClickListener(v -> chooseAcademicDateTime(finish, end, due[0] > 0 ? due[0] : calendarDay, true));
        form.addView(finish, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        TextView clearEnd = host.ui.linkText("Clear event end");
        clearEnd.setPadding(0, host.ui.dp(6), 0, host.ui.dp(6));
        clearEnd.setOnClickListener(v -> { end[0] = 0; finish.setText("Choose event end (optional)"); });
        form.addView(clearEnd);
        EditText location = host.ui.listingField(form, "Event location (optional)", "Location", InputType.TYPE_CLASS_TEXT);
        location.setFilters(new InputFilter[] {new InputFilter.LengthFilter(160)});
        View locationLabel = form.getChildAt(form.getChildCount() - 2);
        if (existing != null) {
            moduleInput.setText(existing.module); titleInput.setText(existing.title); noteInput.setText(existing.detail);
            progress.setText(String.valueOf(existing.progress)); location.setText(existing.location);
        }

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button share = host.ui.secondaryButton("Share");
        Button save = host.ui.primaryButton("Save");
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(host.ui.dp(112), host.ui.dp(48));
        footer.addView(share, shareParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, host.ui.dp(48), 1);
        saveParams.leftMargin = host.ui.dp(8);
        footer.addView(save, saveParams);
        root.addView(footer);

        final Button[] shareFooter = {share};
        final boolean[] shareAfterSave = {false};
        Runnable updateVisibility = () -> {
            int position = kind.getSelectedItemPosition();
            progress.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            progressLabel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            date.setVisibility(position >= 2 ? View.VISIBLE : View.GONE);
            finish.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
            clearEnd.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
            location.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
            locationLabel.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
            moduleInput.setHint(position >= 2 ? "Module (optional)" : "Module");
            typeHint.setText(position == 0 ? "Track a course and its progress."
                    : position == 1 ? "Notes are saved to Study hub. Share saves the note first."
                    : position == 2 ? "The deadline appears on your calendar."
                    : "Events can have a start, end and location.");
            if (shareFooter[0] != null) {
                Boolean noteLike = position != 0;
                shareFooter[0].setVisibility(noteLike ? View.VISIBLE : View.GONE);
            }
        };
        kind.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateVisibility.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        updateVisibility.run();

        Runnable saveAction = () -> {
            String type = kind.getSelectedItem().toString();
            if ((type.equals("Course") || type.equals("Note")) && host.ui.text(moduleInput).trim().isEmpty()) {
                moduleInput.setError("Add the module");
                scroll.smoothScrollTo(0, moduleInput.getTop());
                return;
            }
            if (host.ui.text(titleInput).trim().isEmpty()) { titleInput.setError("Enter a title"); return; }
            String percentage = host.ui.text(progress).trim();
            if (type.equals("Course") && !percentage.isEmpty() && !percentage.matches("([0-9]|[1-9][0-9]|100)")) {
                progress.setError("Enter 0 to 100");
                return;
            }
            String error = MainActivity.AcademicItem.scheduleError(type, due[0], end[0],
                    existing != null && due[0] == existing.due ? 0 : System.currentTimeMillis());
            if (!error.isEmpty()) { host.ui.showSimpleMessage("Date & time", error); return; }
            MainActivity.AcademicItem item = new MainActivity.AcademicItem(
                    existing == null ? java.util.UUID.randomUUID().toString() : existing.id, type,
                    host.ui.text(moduleInput).trim(), host.ui.text(titleInput).trim(),
                    host.ui.text(noteInput).trim(), type.equals("Course") && !percentage.isEmpty() ? Integer.parseInt(percentage) : 0,
                    type.equals("Deadline") || type.equals("Event") ? due[0] : 0, type.equals("Event") ? end[0] : 0,
                    type.equals("Event") ? host.ui.text(location).trim() : "",
                    existing != null && existing.saved);
            ArrayList<MainActivity.AcademicItem> items = academicItems();
            if (existing != null) for (java.util.Iterator<MainActivity.AcademicItem> iterator = items.iterator(); iterator.hasNext();)
                if (iterator.next().id.equals(existing.id)) iterator.remove();
            items.add(item);
            if (!saveAcademicItems(items)) {
                host.ui.showSimpleMessage("Couldn't save", "Your item wasn't saved. Give it another go.");
                return;
            }
            page.dismiss();
            host.showAppScreen("Academics");
            if (shareAfterSave[0]) shareAcademicItem(item);
            else if (calendarDay > 0) { host.academicCalendarDate = due[0] > 0 ? due[0] : calendarDay; showAcademicCalendar(); }
        };
        save.setOnClickListener(v -> { shareAfterSave[0] = false; saveAction.run(); });
        share.setOnClickListener(v -> { shareAfterSave[0] = true; saveAction.run(); });

        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // Start the picker at a useful time, never in the past.
    public void chooseAcademicDateTime(Button button, long[] time, long seed, boolean finish) {
        java.util.Calendar initial = java.util.Calendar.getInstance();
        if (time[0] > 0) initial.setTimeInMillis(time[0]);
        else {
            if (seed > 0) initial.setTimeInMillis(seed);
            if (finish || seed == 0) initial.add(java.util.Calendar.HOUR_OF_DAY, 1);
            else { initial.set(java.util.Calendar.HOUR_OF_DAY, 10); initial.set(java.util.Calendar.MINUTE, 0); }
            if (initial.getTimeInMillis() <= System.currentTimeMillis()) { initial.setTimeInMillis(System.currentTimeMillis()); initial.add(java.util.Calendar.HOUR_OF_DAY, 1); }
        }
        DatePickerDialog picker = new DatePickerDialog(host, (view, year, month, day) -> new TimePickerDialog(host, (clock, hour, minute) -> {
            java.util.Calendar chosen = java.util.Calendar.getInstance(); chosen.set(year, month, day, hour, minute, 0); chosen.set(java.util.Calendar.MILLISECOND, 0);
            time[0] = chosen.getTimeInMillis(); button.setText(host.academicDate(time[0]));
        }, initial.get(java.util.Calendar.HOUR_OF_DAY), initial.get(java.util.Calendar.MINUTE), true).show(),
                initial.get(java.util.Calendar.YEAR), initial.get(java.util.Calendar.MONTH), initial.get(java.util.Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMinDate(System.currentTimeMillis()); picker.show();
    }

    // Load saved items; one broken entry only shows a toast.
    public ArrayList<MainActivity.AcademicItem> academicItems() {
        ArrayList<MainActivity.AcademicItem> items = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(host.getSharedPreferences(host.PREFS + "_academics", Context.MODE_PRIVATE).getString(host.currentStudentEmail, "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                items.add(new MainActivity.AcademicItem(item.getString("id"), item.getString("kind"), item.getString("module"),
                        item.getString("title"), item.optString("detail"), item.optInt("progress"), item.optLong("due"),
                        item.optLong("end", 0), item.optString("location", ""), item.optBoolean("saved", false)));
            }
        } catch (org.json.JSONException e) { Toast.makeText(host, "Some academic items couldn't be loaded.", Toast.LENGTH_SHORT).show(); }
        return items;
    }

    // Add group posts; shared items get a group: ID.
    public ArrayList<MainActivity.AcademicItem> academicCalendarItems() {
        ArrayList<MainActivity.AcademicItem> items = academicItems();
        for (CampusFeatures.Group g : host.featureStore.data.groups) if (g.members.contains(host.currentStudentEmail)) {
            for (CampusFeatures.GroupPost p : g.posts) if (p.kind.equals("Deadline") || p.kind.equals("Event") || p.kind.equals("Note")) {
                String key = "group:" + g.id + ":" + p.id;
                boolean saved = host.settingsPrefs().getStringSet("saved_note_ids", new LinkedHashSet<>()).contains(key);
                items.add(new MainActivity.AcademicItem(key, p.kind, g.module, p.title.isEmpty() ? p.body.split("\n", 2)[0] : p.title,
                        p.body + "\nShared in " + g.name, 0, p.due, p.end, p.location, saved));
            }
        }
        return items;
    }

    public boolean saveAcademicItems(ArrayList<MainActivity.AcademicItem> items) {
        JSONArray data = new JSONArray();
        for (MainActivity.AcademicItem item : items) {
            try {
                data.put(new JSONObject().put("id", item.id).put("kind", item.kind).put("module", item.module)
                        .put("title", item.title).put("detail", item.detail).put("progress", item.progress).put("due", item.due)
                        .put("end", item.end).put("location", item.location).put("saved", item.saved));
            } catch (org.json.JSONException e) { return false; }
        }
        return host.getSharedPreferences(host.PREFS + "_academics", Context.MODE_PRIVATE).edit().putString(host.currentStudentEmail, data.toString()).commit();
    }

    // Show one item full page; group items need membership first.
    public void showAcademicItem(MainActivity.AcademicItem item) {
        boolean shared = item.id.startsWith("group:");
        String groupId = shared ? item.id.substring(6).split(":", 2)[0] : "";
        if (shared && !host.featureStore.data.group(groupId).members.contains(host.currentStudentEmail)) {
            host.ui.showSimpleMessage("Group content", "Join this group to access its notes and activities.");
            return;
        }
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader(item.kind, item.module, page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout hero = host.ui.card(host.surface(), 16, host.ui.dp(16));
        hero.setBackground(host.ui.strokedRound(host.surface(), 16, host.line));
        LinearLayout heroTop = host.ui.horizontal();
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        heroTop.addView(host.ui.titleText(item.title, host.text, 20),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        heroTop.addView(host.ui.chip(shared ? "Shared" : item.saved ? "Saved" : "Study hub",
                host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235), host.muted));
        hero.addView(heroTop);
        StringBuilder meta = new StringBuilder(item.module);
        if (item.scheduled()) meta.append(" · ").append(host.academicDate(item.due));
        if (item.end > 0) meta.append(" · until ").append(host.academicDate(item.end));
        if (item.kind.equals("Course") && item.progress > 0) meta.append(" · ").append(item.progress).append("% complete");
        hero.addView(host.ui.bodyText(meta.toString(), host.muted, 12));
        if (!item.location.isEmpty()) hero.addView(host.ui.bodyText(item.location, host.muted, 12));
        content.addView(hero);

        TextView detailTitle = host.ui.titleText("Details", host.text, 15);
        detailTitle.setPadding(0, host.ui.dp(20), 0, host.ui.dp(8));
        content.addView(detailTitle);
        TextView details = host.ui.bodyText(item.detail.isEmpty() ? "No additional details." : item.detail,
                item.detail.isEmpty() ? host.muted : host.text, 14);
        details.setLineSpacing(host.ui.dp(2), 1f);
        content.addView(details);

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(12));
        footer.setBackgroundColor(host.surface());
        Button primary = host.ui.primaryButton(shared ? "Open group" : "Edit");
        footer.addView(primary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        if (shared) {
            primary.setOnClickListener(v -> { page.dismiss(); host.community.showGroup(groupId); });
            if (item.kind.equals("Note")) {
                Button save = host.ui.secondaryButton(item.saved ? "Unsave note" : "Save note");
                save.setOnClickListener(v -> { page.dismiss(); toggleSavedAcademicNote(item); });
                LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
                saveParams.topMargin = host.ui.dp(8);
                footer.addView(save, saveParams);
            }
        } else {
            primary.setOnClickListener(v -> { page.dismiss(); showAcademicEditor(item, item.kind, 0); });
            if (item.kind.equals("Note")) {
                Button save = host.ui.secondaryButton(item.saved ? "Unsave note" : "Save note");
                save.setOnClickListener(v -> { page.dismiss(); toggleSavedAcademicNote(item); });
                LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
                saveParams.topMargin = host.ui.dp(8);
                footer.addView(save, saveParams);
            }
            if (!item.kind.equals("Course")) {
                TextView share = host.ui.textButton("Share with group");
                share.setOnClickListener(v -> { page.dismiss(); shareAcademicItem(item); });
                footer.addView(share, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42)));
            }
            TextView delete = host.ui.textButton("Delete");
            delete.setTextColor(host.error);
            delete.setOnClickListener(v -> { page.dismiss(); deleteAcademicItem(item); });
            footer.addView(delete, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42)));
        }
        root.addView(footer);

        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    public void deleteAcademicItem(MainActivity.AcademicItem item) {
        if (item.id.startsWith("group:")) return;
        host.ui.dialog().setTitle("Delete academic item?")
                        .setMessage(item.title).setNegativeButton("Cancel", null).setPositiveButton("Delete", (confirm, action) -> {
                            ArrayList<MainActivity.AcademicItem> items = academicItems();
                            for (java.util.Iterator<MainActivity.AcademicItem> iterator = items.iterator(); iterator.hasNext();) if (iterator.next().id.equals(item.id)) iterator.remove();
                            if (saveAcademicItems(items)) host.showAppScreen("Academics"); else host.ui.showSimpleMessage("Couldn't delete", "Give it another go.");
                        }).show();
    }

    // Personal and group notes are saved separately.
    public void toggleSavedAcademicNote(MainActivity.AcademicItem item) {
        if (!item.kind.equals("Note")) return;
        boolean success;
        if (item.id.startsWith("group:")) {
            String group = item.id.substring(6).split(":", 2)[0];
            if (!host.featureStore.data.group(group).members.contains(host.currentStudentEmail)) return;
            LinkedHashSet<String> saved = new LinkedHashSet<>(host.settingsPrefs().getStringSet("saved_note_ids", new LinkedHashSet<>()));
            if (!saved.remove(item.id)) saved.add(item.id);
            success = host.settingsPrefs().edit().putStringSet("saved_note_ids", saved).commit();
        } else {
            ArrayList<MainActivity.AcademicItem> items = academicItems();
            for (int i = 0; i < items.size(); i++) {
                MainActivity.AcademicItem old = items.get(i);
                if (old.id.equals(item.id)) items.set(i, new MainActivity.AcademicItem(old.id, old.kind, old.module, old.title, old.detail, old.progress, old.due, old.end, old.location, !old.saved));
            }
            success = saveAcademicItems(items);
        }
        if (success) host.showAppScreen("Academics"); else host.ui.showSimpleMessage("Couldn't save", "The note preference wasn't saved.");
    }

    // Share an item to a group and notify members.
    public void shareAcademicItem(MainActivity.AcademicItem item) {
        if (item.id.startsWith("group:") || item.kind.equals("Course")) return;
        ArrayList<CampusFeatures.Group> groups = new ArrayList<>();
        for (CampusFeatures.Group group : host.featureStore.data.groups) if (group.members.contains(host.currentStudentEmail)) groups.add(group);
        if (groups.isEmpty()) {
            host.ui.dialog().setTitle("Share with a study group").setMessage("Join or create a group first. Your personal item remains saved.")
                    .setNegativeButton("Close", null).setPositiveButton("Find a group", (d, which) -> host.showAppScreen("Groups")).show(); return;
        }
        String[] names = new String[groups.size()];
        for (int i = 0; i < names.length; i++) names[i] = groups.get(i).name;
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(host.ui.bodyText("Share with members of the selected group. Private group notes stay inside the group.", host.muted, 12));
        Spinner group = host.ui.listingSelector(form, "Study group", names);
        Button share = host.ui.primaryButton("Share with group");
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        shareParams.topMargin = host.ui.dp(10);
        form.addView(share, shareParams);
        Dialog sheet = host.ui.showBottomSheet("Share " + item.kind.toLowerCase(Locale.ROOT), form);
        share.setOnClickListener(v -> {
            String id = groups.get(group.getSelectedItemPosition()).id;
            try {
                host.featureStore.update(f -> f.postActivity(id, host.currentStudentEmail, item.kind, item.title,
                        item.detail, item.due, item.end, item.location, System.currentTimeMillis()));
                sheet.dismiss();
                CampusFeatures.Group shared = host.featureStore.data.group(id);
                for (String member : shared.members) if (!member.equals(host.currentStudentEmail)) {
                    host.notification.addNotification(member, shared.name + " · shared " + item.kind.toLowerCase(Locale.ROOT),
                            item.title, "groups", "");
                }
                host.showAppScreen("Academics");
            } catch (RuntimeException e) {
                host.ui.showSimpleMessage("Couldn't share", e.getMessage());
            }
        });
    }

    // Full-screen calendar with a month grid and agenda.
    public void showAcademicCalendar() {
        if (host.academicCalendarDialog != null) host.academicCalendarDialog.dismiss();
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Calendar", "Deadlines, events and notes", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout monthBox = new LinearLayout(host);
        monthBox.setOrientation(LinearLayout.VERTICAL);
        form.addView(monthBox);
        LinearLayout agenda = new LinearLayout(host);
        agenda.setOrientation(LinearLayout.VERTICAL);
        form.addView(agenda);
        renderAcademicMonth(monthBox, agenda);
        renderCalendarDay(agenda, host.academicCalendarDate);

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button event = host.ui.secondaryButton("+ Event");
        Button deadline = host.ui.secondaryButton("+ Deadline");
        footer.addView(event, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        LinearLayout.LayoutParams deadlineParams = new LinearLayout.LayoutParams(0, host.ui.dp(48), 1);
        deadlineParams.leftMargin = host.ui.dp(8);
        footer.addView(deadline, deadlineParams);
        root.addView(footer);
        event.setOnClickListener(v -> { page.dismiss(); showAcademicEditor(null, "Event", host.academicCalendarDate); });
        deadline.setOnClickListener(v -> { page.dismiss(); showAcademicEditor(null, "Deadline", host.academicCalendarDate); });

        host.academicCalendarDialog = page;
        page.setOnDismissListener(d -> { if (host.academicCalendarDialog == page) host.academicCalendarDialog = null; });
        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    // Month grid with large dates and event dots.
    void renderAcademicMonth(LinearLayout container, LinearLayout agenda) {
        container.removeAllViews();
        final java.util.Calendar month = java.util.Calendar.getInstance();
        month.setTimeInMillis(host.academicCalendarDate);
        month.set(java.util.Calendar.DAY_OF_MONTH, 1);
        month.set(java.util.Calendar.HOUR_OF_DAY, 0);
        month.set(java.util.Calendar.MINUTE, 0);
        month.set(java.util.Calendar.SECOND, 0);
        month.set(java.util.Calendar.MILLISECOND, 0);

        LinearLayout header = host.ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView previous = host.ui.textButton("<");
        previous.setContentDescription("Previous month");
        previous.setOnClickListener(v -> {
            java.util.Calendar target = (java.util.Calendar) month.clone();
            target.add(java.util.Calendar.MONTH, -1);
            host.academicCalendarDate = target.getTimeInMillis();
            renderAcademicMonth(container, agenda);
            renderCalendarDay(agenda, host.academicCalendarDate);
        });
        header.addView(previous, new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(44)));
        TextView monthTitle = host.ui.titleText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(month.getTime()), host.text, 18);
        monthTitle.setGravity(Gravity.CENTER);
        header.addView(monthTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView following = host.ui.textButton(">");
        following.setContentDescription("Next month");
        following.setOnClickListener(v -> {
            java.util.Calendar target = (java.util.Calendar) month.clone();
            target.add(java.util.Calendar.MONTH, 1);
            host.academicCalendarDate = target.getTimeInMillis();
            renderAcademicMonth(container, agenda);
            renderCalendarDay(agenda, host.academicCalendarDate);
        });
        header.addView(following, new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(44)));
        container.addView(header);

        LinearLayout weekdays = host.ui.horizontal();
        String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String name : names) {
            TextView label = host.ui.tinyCaps(name.toUpperCase(Locale.ROOT), host.muted);
            label.setGravity(Gravity.CENTER);
            weekdays.addView(label, new LinearLayout.LayoutParams(0, host.ui.dp(28), 1));
        }
        container.addView(weekdays);

        ArrayList<MainActivity.AcademicItem> items = academicCalendarItems();
        int offset = (month.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY + 7) % 7;
        int days = month.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        int day = 1;
        for (int week = 0; week < 6 && day <= days; week++) {
            LinearLayout row = host.ui.horizontal();
            for (int cell = 0; cell < 7; cell++) {
                if ((week == 0 && cell < offset) || day > days) {
                    row.addView(host.ui.space(1, 1), new LinearLayout.LayoutParams(0, host.ui.dp(44), 1));
                    continue;
                }
                final java.util.Calendar date = (java.util.Calendar) month.clone();
                date.set(java.util.Calendar.DAY_OF_MONTH, day);
                final long dayMillis = date.getTimeInMillis();
                boolean hasEvent = false;
                for (MainActivity.AcademicItem item : items) if (item.onDay(dayMillis)) { hasEvent = true; break; }
                boolean selected = sameAcademicDay(dayMillis, host.academicCalendarDate);
                boolean today = sameAcademicDay(dayMillis, System.currentTimeMillis());
                LinearLayout cellBox = new LinearLayout(host);
                cellBox.setOrientation(LinearLayout.VERTICAL);
                cellBox.setGravity(Gravity.CENTER);
                if (selected) cellBox.setBackground(host.ui.round(host.blue, 10));
                else if (today) cellBox.setBackground(host.ui.strokedRound(host.surface(), 10, host.blue));
                TextView number = host.ui.titleText(String.valueOf(day), selected ? Color.WHITE : host.text, 16);
                number.setGravity(Gravity.CENTER);
                cellBox.addView(number);
                View dot = new View(host);
                dot.setBackground(host.ui.round(separatorDotColor(hasEvent, selected), 999));
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(host.ui.dp(5), host.ui.dp(5));
                dotParams.topMargin = host.ui.dp(2);
                cellBox.addView(dot, dotParams);
                final int chosen = day;
                cellBox.setOnClickListener(v -> {
                    java.util.Calendar picked = (java.util.Calendar) month.clone();
                    picked.set(java.util.Calendar.DAY_OF_MONTH, chosen);
                    host.academicCalendarDate = picked.getTimeInMillis();
                    renderAcademicMonth(container, agenda);
                    renderCalendarDay(agenda, host.academicCalendarDate);
                });
                host.ui.attachPressAnimation(cellBox);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, host.ui.dp(44), 1);
                cellParams.leftMargin = host.ui.dp(1);
                cellParams.rightMargin = host.ui.dp(1);
                cellParams.topMargin = host.ui.dp(2);
                row.addView(cellBox, cellParams);
                day++;
            }
            container.addView(row);
        }
    }

    boolean sameAcademicDay(long first, long second) {
        java.util.Calendar a = java.util.Calendar.getInstance();
        java.util.Calendar b = java.util.Calendar.getInstance();
        a.setTimeInMillis(first); b.setTimeInMillis(second);
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }

    int separatorDotColor(boolean hasEvent, boolean selected) {
        if (!hasEvent) return Color.TRANSPARENT;
        return selected ? Color.WHITE : host.blue;
    }

    public void renderCalendarDay(LinearLayout agenda, long day) {
        agenda.removeAllViews();
        TextView heading = host.ui.titleText(new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(new Date(day)), host.text, 20);
        heading.setPadding(0, host.ui.dp(16), 0, host.ui.dp(10)); agenda.addView(heading);
        ArrayList<MainActivity.AcademicItem> items = academicCalendarItems();
        java.util.Collections.sort(items, (a, b) -> Long.compare(a.due, b.due));
        int count = 0;
        for (MainActivity.AcademicItem item : items) if (item.onDay(day)) {
            count++;
            LinearLayout row = new LinearLayout(host); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, host.ui.dp(12), 0, host.ui.dp(12)); row.setBackground(host.ui.bottomRule(host.line));
            row.addView(host.ui.titleText(item.title, host.text, 17));
            row.addView(host.ui.bodyText(item.kind + " / " + host.academicDate(item.due) + (item.id.startsWith("group:") ? " / Shared" : ""), host.muted, 12));
            if (!item.location.isEmpty()) row.addView(host.ui.bodyText(item.location, host.muted, 12));
            row.setOnClickListener(v -> { if (host.academicCalendarDialog != null) host.academicCalendarDialog.dismiss(); showAcademicItem(item); });
            host.ui.attachPressAnimation(row); agenda.addView(row);
        }
        if (count == 0) agenda.addView(host.ui.bodyText("No events or deadlines on this date.", host.muted, 13));
    }
}