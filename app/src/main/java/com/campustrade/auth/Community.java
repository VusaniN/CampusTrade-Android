package com.campustrade.auth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashSet;
import com.campustrade.auth.data.CloudSync;
import com.campustrade.auth.model.Message;
import android.text.TextUtils;

// Study groups, wanted posts and search alerts.
public class Community {

    private final MainActivity host;
    private boolean groupsPulling = false;
    private long lastGroupsPullAt = 0;
    private boolean groupSearchRefreshing = false;
    private String lastGroupSearchPullTerm = "";
    private long lastGroupSearchPullAt = 0L;
    private final java.util.Set<String> groupPostsPulling = new java.util.HashSet<>();
    private final java.util.Map<String, Long> groupPostsPullAt = new java.util.HashMap<>();
    private boolean wantedPulling = false;
    private long lastWantedPullAt = 0;
    private Dialog openGroupPage;

    public Community(MainActivity host) {
        this.host = host;
    }

    // Build Groups and refresh from Firebase at most every few seconds.
    public void buildGroups(LinearLayout content) {
        cloudPullGroups(false, () -> {
            if (host.currentTab.equals("Groups")) host.showAppScreen("Groups");
        });
        Button create = host.ui.primaryButton("Create study group"); create.setOnClickListener(v -> createStudyGroup()); content.addView(create);
        EditText search = host.ui.input("Search groups or modules", InputType.TYPE_CLASS_TEXT); content.addView(search);
        LinearLayout rows = new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL); content.addView(rows);
        LinearLayout suggestionsSection = new LinearLayout(host); suggestionsSection.setOrientation(LinearLayout.VERTICAL);
        suggestionsSection.addView(host.ui.sectionHeading("Suggested study partners", "Same course & year"));
        HorizontalScrollView suggScroll = new HorizontalScrollView(host);
        suggScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout suggRow = host.ui.horizontal();
        suggScroll.addView(suggRow);
        suggestionsSection.addView(suggScroll);
        content.addView(suggestionsSection);
        // Redraws the list on each keystroke and hides suggestions while searching.
        final Runnable[] renderRef = new Runnable[1];
        Runnable render = () -> {
            rows.removeAllViews();
            int shownGroups = 0;
            String term = host.ui.text(search).trim().toLowerCase(Locale.ROOT);
            for (CampusFeatures.Group g : host.featureStore.data.groups) {
                if (!(g.name + " " + g.module).toLowerCase(Locale.ROOT).contains(term)) continue;
                shownGroups++;
                LinearLayout row = host.ui.card(host.surface(), 12, host.ui.dp(14));
                row.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
                LinearLayout top = host.ui.horizontal();
                top.setGravity(Gravity.CENTER_VERTICAL);
                top.addView(host.ui.titleText(g.name, host.text, 15),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                top.addView(host.ui.chip(g.privateGroup ? "Private" : "Public",
                        host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235), host.muted));
                row.addView(top);
                row.addView(host.ui.bodyText(g.module + " · " + memberLabel(g.members.size()), host.muted, 12));
                if (!g.description.isEmpty()) {
                    TextView description = host.ui.bodyText(g.description, host.muted, 11);
                    description.setMaxLines(2);
                    description.setEllipsize(TextUtils.TruncateAt.END);
                    row.addView(description);
                }
                LinearLayout chips = host.ui.horizontal();
                chips.setGravity(Gravity.CENTER_VERTICAL);
                chips.setPadding(0, host.ui.dp(8), 0, 0);
                if (g.members.contains(host.currentStudentEmail)) {
                    chips.addView(host.ui.statusPill("Joined"));
                    TextView invite = host.ui.actionChip("Invite");
                    invite.setOnClickListener(v -> shareGroup(g));
                    LinearLayout.LayoutParams inviteParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(40));
                    inviteParams.leftMargin = host.ui.dp(8);
                    chips.addView(invite, inviteParams);
                } else {
                    chips.addView(host.ui.chip(g.privateGroup ? "Approval needed" : "Open to join",
                            host.ui.statusBackground(g.privateGroup ? "Awaiting" : "Available"),
                            host.ui.statusInk(g.privateGroup ? "Awaiting" : "Available")));
                }
                row.addView(chips);
                row.setOnClickListener(v -> showGroup(g.id));
                host.ui.attachPressAnimation(row);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = host.ui.dp(8);
                rows.addView(row, rowParams);
            }
            if (shownGroups == 0) {
                rows.addView(host.ui.emptyState("team", term.isEmpty() ? "No study groups yet" : "No groups match",
                        term.isEmpty() ? "Create a group for your module and invite classmates."
                                : "Try another module or group name, or refresh from your campus."));
                if (term.length() >= 2 && !groupSearchRefreshing) {
                    long now = System.currentTimeMillis();
                    if (!term.equals(lastGroupSearchPullTerm) || now - lastGroupSearchPullAt > 6000) {
                        groupSearchRefreshing = true;
                        lastGroupSearchPullTerm = term;
                        lastGroupSearchPullAt = now;
                        boolean started = cloudPullGroups(true, () -> {
                            groupSearchRefreshing = false;
                            if (host.currentTab.equals("Groups") && renderRef[0] != null) {
                                renderRef[0].run();
                            }
                        });
                        if (!started) groupSearchRefreshing = false;
                    }
                }
            }
            suggestionsSection.setVisibility(term.isEmpty() ? View.VISIBLE : View.GONE);
        };
        renderRef[0] = render;
        search.addTextChangedListener(new MainActivity.SimpleWatcher() { @Override public void afterTextChanged(Editable s) { render.run(); } }); render.run();
        loadSuggestions(suggRow);
    }

    String memberLabel(int count) {
        return count == 1 ? "1 member" : count + " members";
    }

    void shareGroup(CampusFeatures.Group group) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Join my study group \"" + group.name + "\" (" + group.module
                + ") on CampusTrade. Open Groups and search for the group name.");
        try {
            host.startActivity(Intent.createChooser(share, "Invite to " + group.name));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(host, "No app is available to share this invite.", Toast.LENGTH_SHORT).show();
        }
    }

    // Invite sends a chat message and lets the student choose a group.
    private void inviteClassmate(String peerEmail) {
        ArrayList<CampusFeatures.Group> joined = new ArrayList<>();
        for (CampusFeatures.Group group : host.featureStore.data.groups) {
            if (group.members.contains(host.currentStudentEmail)) joined.add(group);
        }
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.bodyText("Invite " + host.studentName(peerEmail) + " to a group you've joined.",
                host.muted, 12));
        if (joined.isEmpty()) {
            body.addView(host.ui.emptyState("team", "Join a group first",
                    "Once you're a member, you can invite classmates from here."));
        } else {
            for (CampusFeatures.Group group : joined) {
                LinearLayout row = host.ui.horizontal();
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, host.ui.dp(12), 0, host.ui.dp(12));
                View symbol = host.new CampusAssetView(host, "team", host.blue);
                symbol.setPadding(host.ui.dp(9), host.ui.dp(9), host.ui.dp(9), host.ui.dp(9));
                symbol.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), 10));
                row.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
                LinearLayout copy = new LinearLayout(host);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(host.ui.dp(12), 0, host.ui.dp(8), 0);
                copy.addView(host.ui.titleText(group.name, host.text, 13));
                copy.addView(host.ui.bodyText(group.module + " · " + memberLabel(group.members.size()), host.muted, 11));
                row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                row.addView(host.new CampusAssetView(host, "chevron", host.muted),
                        new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16)));
                row.setOnClickListener(v -> {
                    host.inbox.localMessage(peerEmail, "You're invited to join \"" + group.name + "\" (" + group.module
                            + ") on CampusTrade. Open Groups and search for it.");
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    Toast.makeText(host, "Invite sent in chat.", Toast.LENGTH_SHORT).show();
                });
                host.ui.attachPressAnimation(row);
                body.addView(row);
            }
        }
        sheetRef[0] = host.ui.showBottomSheet("Invite to a group", body);
    }

    /** Suggest classmates, same course and year first. */
    public void loadSuggestions(LinearLayout row) {
        SharedPreferences profilePrefs = host.getSharedPreferences(host.PREFS + "_profiles", Context.MODE_PRIVATE);
        final String myCourse = profilePrefs.getString("course:" + host.currentStudentEmail, "");
        final String myYear = profilePrefs.getString("year:" + host.currentStudentEmail, "");
        final String uid = CloudSync.uid(host);
        if (myCourse.isEmpty() || uid.isEmpty()) {
            row.addView(host.ui.bodyText("Set your course in Edit profile to get study partner suggestions.", host.muted, 12));
            return;
        }
        row.addView(host.ui.bodyText("Finding classmates...", host.muted, 12));
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("publicProfiles")
                .whereEqualTo("course", myCourse).limit(15).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    row.removeAllViews();
                    java.util.ArrayList<com.google.firebase.firestore.QueryDocumentSnapshot> docs =
                            new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot d : snap) {
                        if (!d.getId().equals(uid)) {
                            docs.add(d);
                        }
                    }
                    final boolean hasYear = !myYear.isEmpty();
                    java.util.Collections.sort(docs, (a, b) -> Boolean.compare(
                            hasYear && myYear.equals(b.getString("year")),
                            hasYear && myYear.equals(a.getString("year"))));
                    int shown = 0;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot d : docs) {
                        if (shown >= 10) break;
                        String email = d.getString("stuEmail");
                        if (email == null || email.isEmpty()) continue;
                        String name = d.getString("stuName");
                        if (name == null || name.isEmpty()) name = email;
                        String year = d.getString("year");
                        CloudSync.directoryPut(host, d.getId(), email);
                        LinearLayout card = new LinearLayout(host);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setGravity(Gravity.CENTER);
                        card.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
                        TextView avatar = new TextView(host);
                        avatar.setText(host.ui.initials(name));
                        avatar.setTextColor(Color.WHITE);
                        avatar.setTextSize(20);
                        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                        avatar.setGravity(Gravity.CENTER);
                        avatar.setBackground(host.ui.round(host.blue, 999));
                        card.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(56), host.ui.dp(56)));
                        TextView who = host.ui.bodyText(name, host.text, 12);
                        who.setGravity(Gravity.CENTER);
                        who.setSingleLine(true);
                        who.setEllipsize(TextUtils.TruncateAt.END);
                        who.setPadding(0, host.ui.dp(6), 0, 0);
                        card.addView(who, new LinearLayout.LayoutParams(host.ui.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
                        TextView sub = host.ui.bodyText(year != null && !year.isEmpty() ? year : myCourse, host.muted, 11);
                        sub.setGravity(Gravity.CENTER);
                        sub.setSingleLine(true);
                        sub.setEllipsize(TextUtils.TruncateAt.END);
                        card.addView(sub, new LinearLayout.LayoutParams(host.ui.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
                        TextView invite = host.ui.actionChip("Invite");
                        final String peerEmail = email;
                        invite.setOnClickListener(v -> inviteClassmate(peerEmail));
                        LinearLayout.LayoutParams inviteParams = new LinearLayout.LayoutParams(
                                host.ui.dp(96), host.ui.dp(36));
                        inviteParams.topMargin = host.ui.dp(6);
                        invite.setMinimumWidth(host.ui.dp(48));
                        invite.setMinimumHeight(host.ui.dp(48));
                        card.addView(invite, inviteParams);
                        host.ui.attachPressAnimation(card);
                        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        cardParams.rightMargin = host.ui.dp(8);
                        row.addView(card, cardParams);
                        shown++;
                    }
                    if (shown == 0) {
                        row.addView(host.ui.bodyText("No classmates on CampusTrade yet. Invite them!", host.muted, 12));
                    }
                })
                .addOnFailureListener(e -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    row.removeAllViews();
                    row.addView(host.ui.bodyText("Suggestions unavailable offline.", host.muted, 12));
                });
    }

    // Create the group on this phone, then copy it to Firebase.
    public void createStudyGroup() {
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Create study group", "Set how classmates can join", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        EditText name = host.ui.listingField(form, "Group name", "Name", InputType.TYPE_CLASS_TEXT);
        EditText module = host.ui.listingField(form, "Module", "Module or course", InputType.TYPE_CLASS_TEXT);
        EditText detail = host.ui.listingField(form, "Description", "About this group", InputType.TYPE_CLASS_TEXT);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(80)});
        module.setFilters(new InputFilter[] {new InputFilter.LengthFilter(80)});
        detail.setFilters(new InputFilter[] {new InputFilter.LengthFilter(500)});
        form.addView(host.ui.label("Membership"));
        final boolean[] privateGroup = {false};
        LinearLayout options = new LinearLayout(host);
        options.setOrientation(LinearLayout.VERTICAL);
        LinearLayout publicCard = groupPrivacyCard("Public", "Anyone can join and read the feed.");
        LinearLayout privateCard = groupPrivacyCard("Private", "Classmates request to join and the owner approves.");
        options.addView(publicCard);
        LinearLayout.LayoutParams privateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privateParams.topMargin = host.ui.dp(8);
        options.addView(privateCard, privateParams);
        Runnable style = () -> {
            styleGroupPrivacy(publicCard, !privateGroup[0]);
            styleGroupPrivacy(privateCard, privateGroup[0]);
        };
        publicCard.setOnClickListener(v -> { privateGroup[0] = false; style.run(); });
        privateCard.setOnClickListener(v -> { privateGroup[0] = true; style.run(); });
        style.run();
        form.addView(options);
        form.addView(host.ui.bodyText("You become the first member and can invite classmates once the group is created.",
                host.muted, 11));

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button create = host.ui.primaryButton("Create group");
        footer.addView(create, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);
        create.setOnClickListener(v -> {
            String groupName = host.ui.text(name).trim();
            String groupModule = host.ui.text(module).trim();
            if (groupName.length() < 3) { name.setError("Use at least 3 characters"); return; }
            if (groupModule.length() < 2) { module.setError("Add the module or course"); return; }
            try {
                final CampusFeatures.Group[] created = {null};
                host.featureStore.update(f -> created[0] = f.createGroup(host.currentStudentEmail,
                        groupName, groupModule, host.ui.text(detail), privateGroup[0]));
                if (created[0] != null) {
                    cloudCreateGroup(created[0]);
                    lastGroupsPullAt = 0;
                }
                page.dismiss();
                host.showAppScreen("Groups");
            } catch (RuntimeException e) {
                Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private LinearLayout groupPrivacyCard(String title, String subtitle) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(host.ui.dp(14), host.ui.dp(12), host.ui.dp(14), host.ui.dp(12));
        card.addView(host.ui.titleText(title, host.text, 14));
        card.addView(host.ui.bodyText(subtitle, host.muted, 11));
        return card;
    }

    private void styleGroupPrivacy(LinearLayout card, boolean selected) {
        int background = selected
                ? (host.nightActive ? Color.rgb(54, 36, 33) : Color.rgb(255, 242, 237))
                : host.surface();
        card.setBackground(host.ui.strokedRound(background, 12, selected ? host.blue : host.line));
    }

    private static final class CloudGroup {
        String id, name, module, description, owner;
        boolean privateGroup;
        final java.util.LinkedHashSet<String> members = new java.util.LinkedHashSet<>();
        final java.util.LinkedHashSet<String> requests = new java.util.LinkedHashSet<>();
    }

    private String groupMemberEmail(String uid) {
        if (uid == null || uid.isEmpty()) return "";
        if (uid.equals(host.currentUid)) return host.currentStudentEmail;
        String email = CloudSync.emailForUid(host, uid);
        return email == null || email.isEmpty() ? uid : email;
    }

    // The creator joins first so the group is never empty.
    private void cloudCreateGroup(CampusFeatures.Group g) {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        java.util.Map<String, Object> doc = new java.util.HashMap<>();
        doc.put("name", g.name);
        doc.put("module", g.module);
        doc.put("description", g.description);
        doc.put("private", g.privateGroup);
        doc.put("creatorID", uid);
        doc.put("creatorEmail", g.owner);
        doc.put("creatorName", host.studentName(g.owner));
        java.util.List<String> members = new java.util.ArrayList<>();
        members.add(uid);
        doc.put("members", members);
        doc.put("requests", new java.util.ArrayList<String>());
        doc.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("studyGroups").document(g.id).set(doc)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "group create failed: " + e.getMessage()));
    }

    // Public groups join now; private groups add the student to requests.
    private void cloudJoinGroup(CampusFeatures.Group g) {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        String field = g.privateGroup ? "requests" : "members";
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("studyGroups").document(g.id)
                .update(field, com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "group join failed: " + e.getMessage()));
    }

    private void cloudLeaveGroup(CampusFeatures.Group g) {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("studyGroups").document(g.id)
                .update("members", com.google.firebase.firestore.FieldValue.arrayRemove(uid))
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "group leave failed: " + e.getMessage()));
    }

    // Firebase uses student IDs, so look up the ID before approving.
    private void cloudApproveGroup(CampusFeatures.Group g, String memberEmail) {
        CloudSync.peerUid(host, memberEmail, new CloudSync.StrCallback() {
            @Override public void onOk(String memberUid) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("studyGroups").document(g.id)
                        .update("members", com.google.firebase.firestore.FieldValue.arrayUnion(memberUid),
                                "requests", com.google.firebase.firestore.FieldValue.arrayRemove(memberUid))
                        .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "group approve failed: " + e.getMessage()));
            }
            @Override public void onBad(String message) { }
        });
    }

    /** Copy a group post so every member sees it. */
    void cloudSaveGroupPost(String groupId, CampusFeatures.GroupPost post) {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty() || post == null) return;
        java.util.Map<String, Object> doc = new java.util.HashMap<>();
        doc.put("authorID", uid);
        doc.put("authorEmail", post.author);
        doc.put("authorName", post.authorName == null ? "" : post.authorName);
        doc.put("kind", post.kind);
        doc.put("body", post.body);
        doc.put("time", post.time);
        doc.put("due", post.due);
        doc.put("title", post.title == null ? "" : post.title);
        doc.put("location", post.location == null ? "" : post.location);
        doc.put("end", post.end);
        doc.put("readBy", new java.util.ArrayList<>(post.readBy));
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("studyGroups").document(groupId).collection("posts").document(post.id)
                .set(doc, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "group post save failed: " + e.getMessage()));
    }

    /** Load one group's posts and save them. */
    private void cloudPullGroupPosts(String groupId, Runnable after) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("studyGroups").document(groupId).collection("posts").get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) { if (after != null) after.run(); return; }
                    try {
                        host.featureStore.update(f -> {
                            CampusFeatures.Group group = f.group(groupId);
                            for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                                CampusFeatures.GroupPost target = null;
                                for (CampusFeatures.GroupPost p : group.posts) {
                                    if (p.id.equals(doc.getId())) { target = p; break; }
                                }
                                if (target == null) {
                                    target = new CampusFeatures.GroupPost();
                                    target.id = doc.getId();
                                    group.posts.add(target);
                                }
                                String authorId = doc.getString("authorID");
                                String author = doc.getString("authorEmail");
                                if (author == null || author.isEmpty()) author = CloudSync.emailForUid(host, authorId);
                                target.author = author == null || author.isEmpty() ? "Student" : author;
                                target.authorName = doc.getString("authorName") == null ? "" : doc.getString("authorName");
                                target.kind = doc.getString("kind") == null ? "Message" : doc.getString("kind");
                                target.body = doc.getString("body") == null ? "" : doc.getString("body");
                                Long stamp = doc.getLong("time");
                                target.time = stamp == null ? 0 : stamp;
                                Long due = doc.getLong("due");
                                target.due = due == null ? 0 : due;
                                target.title = doc.getString("title") == null ? "" : doc.getString("title");
                                target.location = doc.getString("location") == null ? "" : doc.getString("location");
                                Long end = doc.getLong("end");
                                target.end = end == null ? 0 : end;
                                java.util.List<String> readBy = (java.util.List<String>) doc.get("readBy");
                                if (readBy != null) target.readBy.addAll(readBy);
                            }
                            group.posts.sort((a, b) -> Long.compare(a.time, b.time));
                        });
                    } catch (RuntimeException e) {
                        android.util.Log.w("CampusTrade", "group post merge failed: " + e.getMessage());
                    }
                    if (after != null) after.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("CampusTrade", "group post pull failed: " + e.getMessage());
                    if (after != null) after.run();
                });
    }

    /** Share the current student's read state with the group. */
    private void cloudMarkGroupPostsRead(CampusFeatures.Group group) {
        if (group == null) return;
        String email = host.currentStudentEmail;
        for (CampusFeatures.GroupPost post : group.posts) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("studyGroups").document(group.id).collection("posts").document(post.id)
                    .update("readBy", com.google.firebase.firestore.FieldValue.arrayUnion(email))
                    .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "group read sync failed: " + e.getMessage()));
        }
    }

    // Load campus groups and update the saved list. A searched term can force
    // an immediate refresh so a group created on another device appears.
    private boolean cloudPullGroups(boolean force, Runnable after) {
        long now = System.currentTimeMillis();
        if (groupsPulling || (!force && now - lastGroupsPullAt <= 8000)) return false;
        lastGroupsPullAt = now;
        groupsPulling = true;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("studyGroups").limit(100).get()
                .addOnSuccessListener(snap -> {
                    groupsPulling = false;
                    if (host.isDestroyed() || host.isFinishing()) return;
                    java.util.List<CloudGroup> incoming = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        CloudGroup c = new CloudGroup();
                        c.id = d.getId();
                        c.name = d.getString("name") == null ? "" : d.getString("name");
                        c.module = d.getString("module") == null ? "" : d.getString("module");
                        c.description = d.getString("description") == null ? "" : d.getString("description");
                        c.owner = d.getString("creatorEmail") == null ? "" : d.getString("creatorEmail");
                        c.privateGroup = Boolean.TRUE.equals(d.getBoolean("private"));
                        java.util.List<String> uids = (java.util.List<String>) d.get("members");
                        if (uids != null) {
                            for (String uid : uids) {
                                if (uid == null || uid.isEmpty()) continue;
                                c.members.add(groupMemberEmail(uid));
                            }
                        }
                        java.util.List<String> requested = (java.util.List<String>) d.get("requests");
                        if (requested != null) {
                            for (String uid : requested) {
                                if (uid == null || uid.isEmpty()) continue;
                                c.requests.add(groupMemberEmail(uid));
                            }
                        }
                        incoming.add(c);
                    }
                    try {
                        host.featureStore.update(f -> {
                            for (CloudGroup c : incoming) {
                                CampusFeatures.Group target = null;
                                for (CampusFeatures.Group g : f.groups) {
                                    if (g.id.equals(c.id)) { target = g; break; }
                                }
                                if (target == null) {
                                    target = new CampusFeatures.Group();
                                    target.id = c.id;
                                    f.groups.add(target);
                                }
                                if (!java.util.Objects.equals(target.name, c.name)
                                        || !java.util.Objects.equals(target.module, c.module)
                                        || !java.util.Objects.equals(target.description, c.description)
                                        || !java.util.Objects.equals(target.owner, c.owner)
                                        || target.privateGroup != c.privateGroup
                                        || !target.members.equals(c.members)
                                        || !target.requests.equals(c.requests)) {
                                    target.name = c.name;
                                    target.module = c.module;
                                    target.description = c.description;
                                    target.owner = c.owner;
                                    target.privateGroup = c.privateGroup;
                                    target.members.clear();
                                    target.members.addAll(c.members);
                                    target.requests.clear();
                                    target.requests.addAll(c.requests);
                                }
                            }
                        });
                    } catch (RuntimeException e) {
                        android.util.Log.w("CampusTrade", "group merge failed: " + e.getMessage());
                    }
                    // Always let the caller redraw; a search refresh needs the
                    // empty state to clear even when no new group was found.
                    if (after != null) after.run();
                })
                .addOnFailureListener(e -> {
                    groupsPulling = false;
                    android.util.Log.w("CampusTrade", "group pull failed: " + e.getMessage());
                    if (after != null) after.run();
                });
        return true;
    }

    // Add joined groups to Inbox; unread means posts not seen yet.
    void addGroupConversationRows(LinearLayout container, String query) {
        for (CampusFeatures.Group g : host.featureStore.data.groups) {
            if (!g.members.contains(host.currentStudentEmail) || !(g.name + " " + g.module).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            int unread = 0; for (CampusFeatures.GroupPost p : g.posts) if (!p.readBy.contains(host.currentStudentEmail)) unread++;
            if (host.inboxUnreadOnly && unread == 0) continue;
            LinearLayout row = host.ui.card(host.surface(), 12, host.ui.dp(14));
            row.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
            LinearLayout top = host.ui.horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(host.ui.titleText(g.name, host.text, 14),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (unread > 0) {
                top.addView(host.ui.chip(unread + " new", host.ui.statusBackground("New"), host.ui.statusInk("New")));
            }
            row.addView(top);
            String preview = g.posts.isEmpty() ? g.module : g.posts.get(g.posts.size() - 1).body;
            TextView subtitle = host.ui.bodyText(preview, host.muted, 12); subtitle.setSingleLine(true); subtitle.setEllipsize(TextUtils.TruncateAt.END); row.addView(subtitle);
            row.addView(host.ui.bodyText(memberLabel(g.members.size()), host.muted, 11));
            row.setOnClickListener(v -> showGroup(g.id));
            host.ui.attachPressAnimation(row);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = host.ui.dp(8);
            container.addView(row, rowParams);
        }
    }

    // Non-members can join; members open the feed.
    void showGroup(String id) {
        CampusFeatures.Group g;
        try { g = host.featureStore.data.group(id); }
        catch (RuntimeException e) { Toast.makeText(host, e.getMessage(), Toast.LENGTH_SHORT).show(); return; }
        if (openGroupPage != null && openGroupPage.isShowing()) openGroupPage.dismiss();
        long now = System.currentTimeMillis();
        long lastPostsPull = groupPostsPullAt.getOrDefault(id, 0L);
        if (g.members.contains(host.currentStudentEmail)
                && !groupPostsPulling.contains(id) && now - lastPostsPull > 5000) {
            groupPostsPulling.add(id);
            groupPostsPullAt.put(id, now);
            // Keep showing saved posts, then refresh after the download.
            cloudPullGroupPosts(id, () -> {
                groupPostsPulling.remove(id);
                if (openGroupPage != null && openGroupPage.isShowing()) {
                    openGroupPage.dismiss();
                    showGroup(id);
                }
            });
        }

        if (g.members.contains(host.currentStudentEmail)) {
            try {
                host.featureStore.update(f -> { for (CampusFeatures.GroupPost p : f.group(id).posts) p.readBy.add(host.currentStudentEmail); });
                cloudMarkGroupPostsRead(host.featureStore.data.group(id));
            } catch (RuntimeException e) {
                Toast.makeText(host, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        Dialog page = host.ui.fullScreenForm();
        openGroupPage = page;
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        LinearLayout header = host.ui.pageHeader(g.members.contains(host.currentStudentEmail) ? "Group feed" : "Study group",
                g.name, page::dismiss);
        if (g.members.contains(host.currentStudentEmail)) {
            View share = host.new CampusAssetView(host, "share", host.text);
            share.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
            share.setContentDescription("Invite to group");
            share.setBackground(host.ui.strokedRound(host.page, 999, host.border));
            share.setOnClickListener(v -> shareGroup(g));
            header.addView(share, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        }
        root.addView(header);
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
        heroTop.addView(host.ui.bodyText(g.module + " · " + memberLabel(g.members.size()), host.muted, 12),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        heroTop.addView(host.ui.chip(g.privateGroup ? "Private" : "Public",
                host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235), host.muted));
        hero.addView(heroTop);
        if (!g.description.isEmpty()) {
            TextView description = host.ui.bodyText(g.description, host.text, 13);
            description.setPadding(0, host.ui.dp(8), 0, 0);
            hero.addView(description);
        }
        if (g.members.contains(host.currentStudentEmail)) {
            LinearLayout actions = host.ui.horizontal();
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(0, host.ui.dp(10), 0, 0);
            actions.addView(host.ui.statusPill("Joined"));
            TextView members = host.ui.textButton("Members");
            members.setPadding(host.ui.dp(12), host.ui.dp(4), host.ui.dp(4), host.ui.dp(4));
            members.setOnClickListener(v -> manageGroupMembers(id));
            actions.addView(members);
            TextView invite = host.ui.textButton("Invite");
            invite.setPadding(host.ui.dp(12), host.ui.dp(4), host.ui.dp(4), host.ui.dp(4));
            invite.setOnClickListener(v -> shareGroup(g));
            actions.addView(invite);
            hero.addView(actions);
        } else {
            hero.addView(host.ui.bodyText(g.privateGroup
                    ? "This group is private. Request to join and the owner will approve you."
                    : "This group is public. Join to read the feed and post.", host.muted, 12));
        }
        content.addView(hero);

        if (g.members.contains(host.currentStudentEmail)) {
            TextView feedTitle = host.ui.titleText("Posts", host.text, 15);
            feedTitle.setPadding(0, host.ui.dp(22), 0, host.ui.dp(10));
            content.addView(feedTitle);
            if (g.posts.isEmpty()) {
                content.addView(host.ui.emptyState("chat", "No posts yet",
                        "Start with a question, a shared note or a deadline."));
            } else {
                for (CampusFeatures.GroupPost p : g.posts) {
                    content.addView(groupPostCard(g, p));
                }
            }
        }

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button action = host.ui.primaryButton(g.members.contains(host.currentStudentEmail) ? "New post"
                : g.privateGroup ? "Request to join" : "Join group");
        footer.addView(action, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);
        action.setOnClickListener(v -> {
            if (g.members.contains(host.currentStudentEmail)) {
                page.dismiss();
                openGroupPage = null;
                postToGroup(id);
                return;
            }
            try {
                host.featureStore.update(f -> f.join(id, host.currentStudentEmail));
                cloudJoinGroup(g);
                if (g.privateGroup) {
                    host.notification.addNotification(g.owner, "Group membership request",
                            host.studentName(host.currentStudentEmail) + " requested to join " + g.name, "groups", "");
                } else {
                    host.inbox.localMessage(g.owner, host.studentName(host.currentStudentEmail)
                            + " joined your study group \"" + g.name + "\".");
                }
                page.dismiss();
                host.showAppScreen("Groups");
            } catch (RuntimeException e) {
                Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        page.setContentView(root);
        page.setOnDismissListener(d -> { if (openGroupPage == page) openGroupPage = null; });
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private LinearLayout groupPostCard(CampusFeatures.Group g, CampusFeatures.GroupPost post) {
        LinearLayout card = host.ui.card(host.surface(), 12, host.ui.dp(14));
        card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        String author = post.authorName == null || post.authorName.isEmpty()
                ? host.studentName(post.author) : post.authorName;
        LinearLayout top = host.ui.horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = host.ui.titleText(host.ui.initials(author),
                (post.author.hashCode() & 1) == 0 ? host.blue : host.green, 10);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(host.ui.round((post.author.hashCode() & 1) == 0 ? Color.rgb(225, 236, 255) : Color.rgb(229, 247, 239), 999));
        top.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(32), host.ui.dp(32)));
        LinearLayout authorCopy = new LinearLayout(host);
        authorCopy.setOrientation(LinearLayout.VERTICAL);
        authorCopy.setPadding(host.ui.dp(9), 0, host.ui.dp(8), 0);
        authorCopy.addView(host.ui.titleText(author, host.text, 12));
        authorCopy.addView(host.ui.bodyText(post.kind, host.muted, 10));
        top.addView(authorCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(host.ui.chip(post.kind,
                host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235), host.muted));
        card.addView(top);
        TextView body = host.ui.bodyText(post.body, host.text, 13);
        body.setPadding(0, host.ui.dp(10), 0, 0);
        card.addView(body);
        String stamp = post.kind.equals("Deadline") || post.kind.equals("Event") ? host.academicDate(post.due) : host.messageTime(post.time);
        if (!post.location.isEmpty()) stamp += " · " + post.location;
        TextView when = host.ui.bodyText(stamp, host.muted, 11);
        when.setPadding(0, host.ui.dp(6), 0, 0);
        card.addView(when);
        if (post.kind.equals("Note")) {
            String key = "group:" + g.id + ":" + post.id;
            boolean saved = host.settingsPrefs().getStringSet("saved_note_ids", new LinkedHashSet<>()).contains(key);
            TextView bookmark = host.ui.textButton(saved ? "Unsave note" : "Save note");
            bookmark.setPadding(0, host.ui.dp(8), 0, 0);
            bookmark.setOnClickListener(v -> {
                host.academics.toggleSavedAcademicNote(new MainActivity.AcademicItem(key, "Note", g.module,
                        post.title, post.body, 0, 0, 0, "", saved));
                bookmark.setText(host.settingsPrefs().getStringSet("saved_note_ids", new LinkedHashSet<>()).contains(key)
                        ? "Unsave note" : "Save note");
            });
            card.addView(bookmark);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = host.ui.dp(8);
        card.setLayoutParams(params);
        return card;
    }

    // Owners approve requests; members can leave.
    public void manageGroupMembers(String id) {
        CampusFeatures.Group g;
        try { g = host.featureStore.data.group(id); }
        catch (RuntimeException e) { Toast.makeText(host, e.getMessage(), Toast.LENGTH_SHORT).show(); return; }
        if (!g.members.contains(host.currentStudentEmail)) return;
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.bodyText(memberLabel(g.members.size()) + " in " + g.name, host.muted, 12));
        for (String member : g.members) {
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, host.ui.dp(9), 0, host.ui.dp(9));
            TextView avatar = host.ui.titleText(host.ui.initials(host.studentName(member)), host.blue, 10);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(host.ui.round(host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(225, 236, 255), 999));
            row.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(34), host.ui.dp(34)));
            row.addView(host.ui.bodyText(host.studentName(member), host.text, 13),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (member.equals(g.owner)) row.addView(host.ui.chip("Owner",
                    host.nightActive ? Color.rgb(58, 48, 26) : Color.rgb(255, 244, 218),
                    host.readableInk(Color.rgb(166, 101, 0))));
            body.addView(row);
        }
        if (!g.requests.isEmpty()) {
            TextView requestsTitle = host.ui.titleText("Join requests", host.text, 14);
            requestsTitle.setPadding(0, host.ui.dp(16), 0, host.ui.dp(6));
            body.addView(requestsTitle);
            for (String requester : new ArrayList<>(g.requests)) {
                LinearLayout row = host.ui.horizontal();
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
                row.addView(host.ui.bodyText(host.studentName(requester), host.text, 13),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView approve = host.ui.actionChip("Approve");
                approve.setOnClickListener(v -> {
                    try {
                        host.featureStore.update(f -> f.approve(id, host.currentStudentEmail, requester));
                        cloudApproveGroup(g, requester);
                        host.notification.addNotification(requester, "Group request approved", g.name, "groups", "");
                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                        manageGroupMembers(id);
                    } catch (RuntimeException e) {
                        Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
                row.addView(approve, new LinearLayout.LayoutParams(host.ui.dp(92), host.ui.dp(40)));
                body.addView(row);
            }
        }
        if (!g.owner.equals(host.currentStudentEmail)) {
            TextView leave = host.ui.textButton("Leave group");
            leave.setPadding(0, host.ui.dp(18), 0, host.ui.dp(8));
            leave.setOnClickListener(v -> host.ui.dialog()
                    .setTitle("Leave " + g.name + "?")
                    .setMessage("You'll stop seeing this group's feed and posts.")
                    .setNegativeButton("Stay", null)
                    .setPositiveButton("Leave group", (d, which) -> {
                        try {
                            host.featureStore.update(f -> f.leave(id, host.currentStudentEmail));
                            cloudLeaveGroup(g);
                            if (sheetRef[0] != null) sheetRef[0].dismiss();
                            host.showAppScreen("Groups");
                        } catch (RuntimeException e) {
                            Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }).show());
            body.addView(leave);
        }
        sheetRef[0] = host.ui.showBottomSheet("Group members", body);
    }

    // Full-screen composer with type chips and an optional deadline.
    public void postToGroup(String id) {
        CampusFeatures.Group g;
        try { g = host.featureStore.data.group(id); }
        catch (RuntimeException e) { Toast.makeText(host, e.getMessage(), Toast.LENGTH_SHORT).show(); return; }
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("New post", g.name, page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        form.addView(host.ui.label("Post type"));
        final String[] kind = {"Message"};
        LinearLayout types = host.ui.horizontal();
        ArrayList<TextView> typeChips = new ArrayList<>();
        for (String option : new String[] {"Message", "Note", "Deadline"}) {
            TextView chip = host.ui.chip(option, kind[0].equals(option) ? host.blue : host.surface(),
                    kind[0].equals(option) ? Color.WHITE : host.text);
            chip.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
            if (!kind[0].equals(option)) {
                chip.setBackground(host.ui.strokedRound(host.surface(), 999, host.border));
            }
            chip.setOnClickListener(v -> {
                kind[0] = option;
                for (TextView item : typeChips) {
                    boolean active = item.getText().toString().equals(option);
                    item.setBackground(active ? host.ui.round(host.blue, 999)
                            : host.ui.strokedRound(host.surface(), 999, host.border));
                    item.setTextColor(active ? Color.WHITE : host.text);
                }
            });
            typeChips.add(chip);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, host.ui.dp(44), 1);
            if (typeChips.size() > 1) chipParams.leftMargin = host.ui.dp(8);
            types.addView(chip, chipParams);
        }
        form.addView(types);

        EditText body = host.ui.listingField(form, "Content", "Message or shared note",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.setSingleLine(false); body.setMinLines(4);
        body.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4000)});
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setPadding(host.ui.dp(14), host.ui.dp(12), host.ui.dp(14), host.ui.dp(12));
        body.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(160)));
        Button date = host.ui.secondaryButton("Choose deadline");
        final long[] due = {0};
        date.setVisibility(View.GONE);
        date.setOnClickListener(v -> host.ui.chooseFutureDateTime(date, due));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        dateParams.topMargin = host.ui.dp(8);
        form.addView(date, dateParams);
        form.addView(host.ui.bodyText("Everyone in the group is notified. Keep module content clear and respectful.", host.muted, 11));

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button post = host.ui.primaryButton("Post to group");
        footer.addView(post, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);
        post.setOnClickListener(v -> {
            String content = host.ui.text(body).trim();
            if (content.isEmpty()) { body.setError("Write something to post"); return; }
            if (kind[0].equals("Deadline") && due[0] <= System.currentTimeMillis()) {
                Toast.makeText(host, "Choose a future deadline.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                final CampusFeatures.GroupPost[] created = {null};
                host.featureStore.update(f -> {
                    created[0] = f.post(id, host.currentStudentEmail, kind[0], content, due[0], System.currentTimeMillis());
                    if (created[0] != null) created[0].authorName = host.currentStudentName;
                });
                if (created[0] != null) cloudSaveGroupPost(id, created[0]);
                for (String member : g.members) if (!member.equals(host.currentStudentEmail)) {
                    host.notification.addNotification(member, g.name + " · " + kind[0], content, "groups", "");
                }
                page.dismiss();
                host.showAppScreen("Groups");
            } catch (RuntimeException e) {
                Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        // Show the deadline button only for Deadline.
        for (TextView chip : typeChips) {
            chip.setOnClickListener(v -> {
                String option = chip.getText().toString();
                kind[0] = option;
                for (TextView item : typeChips) {
                    boolean active = item == chip;
                    item.setBackground(active ? host.ui.round(host.blue, 999)
                            : host.ui.strokedRound(host.surface(), 999, host.border));
                    item.setTextColor(active ? Color.WHITE : host.text);
                }
                date.setVisibility(option.equals("Deadline") ? View.VISIBLE : View.GONE);
            });
        }

        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // Closed wanted posts stay visible to the owner only.
    public void buildWanted(LinearLayout content) {
        long now = System.currentTimeMillis();
        if (!wantedPulling && now - lastWantedPullAt > 8000) {
            wantedPulling = true;
            lastWantedPullAt = now;
            cloudPullWanted(() -> {
                wantedPulling = false;
                if (host.currentTab.equals("Wanted")) host.showAppScreen("Wanted");
            });
        }
        Button create = host.ui.primaryButton("Post an item you need");
        create.setOnClickListener(v -> createWanted());
        content.addView(create);
        int count = 0;
        for (CampusFeatures.Wanted w : host.featureStore.data.wanted) {
            if (!w.active && !w.owner.equals(host.currentStudentEmail)) continue;
            count++;
            LinearLayout row = host.ui.card(host.surface(), 12, host.ui.dp(14));
            row.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
            LinearLayout top = host.ui.horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(host.ui.titleText(w.title, host.text, 15),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(host.ui.statusPill(w.active ? "Active" : "Closed"));
            row.addView(top);
            row.addView(host.ui.bodyText(w.category, host.muted, 11));
            LinearLayout budgetRow = host.ui.horizontal();
            budgetRow.setGravity(Gravity.CENTER_VERTICAL);
            budgetRow.setPadding(0, host.ui.dp(6), 0, 0);
            budgetRow.addView(host.ui.chip("Budget · R" + w.budget,
                    host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), host.readableInk(host.blue)));
            row.addView(budgetRow);
            if (!w.detail.isEmpty()) {
                TextView detail = host.ui.bodyText(w.detail, host.muted, 12);
                detail.setMaxLines(2);
                detail.setEllipsize(TextUtils.TruncateAt.END);
                detail.setPadding(0, host.ui.dp(6), 0, 0);
                row.addView(detail);
            }
            String ownerName = w.ownerName == null || w.ownerName.isEmpty() ? host.studentName(w.owner) : w.ownerName;
            row.addView(host.ui.bodyText((w.owner.equals(host.currentStudentEmail) ? "Your post · " : ownerName + " · ")
                    + host.messageTime(w.created), host.muted, 11));
            row.setOnClickListener(v -> showWantedActions(w));
            host.ui.attachPressAnimation(row);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = host.ui.dp(8);
            content.addView(row, rowParams);
        }
        if (count == 0) {
            content.addView(host.ui.emptyState("tag", "No wanted posts yet",
                    "Post the item you're looking for and classmates can message you when they have it."));
        }
    }

    private void showWantedActions(CampusFeatures.Wanted w) {
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        LinearLayout top = host.ui.horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(host.ui.titleText(w.title, host.text, 17),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(host.ui.statusPill(w.active ? "Active" : "Closed"));
        body.addView(top);
        body.addView(host.ui.chip("Budget · R" + w.budget,
                host.nightActive ? Color.rgb(38, 50, 66) : Color.rgb(234, 242, 255), host.readableInk(host.blue)));
        body.addView(host.ui.bodyText(w.category + " · " + host.messageTime(w.created), host.muted, 11));
        if (!w.detail.isEmpty()) body.addView(host.ui.bodyText(w.detail, host.text, 13));
        String ownerName = w.ownerName == null || w.ownerName.isEmpty() ? host.studentName(w.owner) : w.ownerName;
        body.addView(host.ui.bodyText("Posted by " + ownerName, host.muted, 11));
        if (w.owner.equals(host.currentStudentEmail)) {
            Button state = host.ui.primaryButton(w.active ? "Mark as fulfilled" : "Reopen post");
            state.setOnClickListener(v -> {
                try {
                    final boolean next = !w.active;
                    host.featureStore.update(f -> {
                        for (CampusFeatures.Wanted item : f.wanted) {
                            if (item.id.equals(w.id) && item.owner.equals(host.currentStudentEmail)) item.active = next;
                        }
                    });
                    cloudUpdateWanted(w.id, next);
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    host.showAppScreen("Wanted");
                } catch (RuntimeException e) {
                    Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
            stateParams.topMargin = host.ui.dp(14);
            body.addView(state, stateParams);
        } else {
            Button message = host.ui.primaryButton("Message " + ownerName);
            message.setOnClickListener(v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                host.inbox.showConversation(w.owner);
            });
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
            messageParams.topMargin = host.ui.dp(14);
            body.addView(message, messageParams);
        }
        sheetRef[0] = host.ui.showBottomSheet("Wanted post", body);
    }

    // Full-screen wanted post with category chips and a budget.
    public void createWanted() {
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Wanted post", "Tell classmates what you need", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        EditText title = host.ui.listingField(form, "Item needed", "What are you looking for?", InputType.TYPE_CLASS_TEXT);
        title.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
        form.addView(host.ui.label("Category"));
        final String[] category = {"Books"};
        LinearLayout categoryRow = host.ui.horizontal();
        HorizontalScrollView categoryScroll = new HorizontalScrollView(host);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        ArrayList<TextView> categoryChips = new ArrayList<>();
        for (String option : new String[] {"Books", "Technology", "Stationery", "Other"}) {
            TextView chip = host.ui.chip(option, host.surface(), host.text);
            chip.setPadding(host.ui.dp(10), host.ui.dp(8), host.ui.dp(10), host.ui.dp(8));
            styleChoiceChip(chip, option.equals(category[0]));
            chip.setOnClickListener(v -> {
                category[0] = option;
                for (TextView item : categoryChips) styleChoiceChip(item, item.getText().toString().equals(option));
            });
            categoryChips.add(chip);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(42));
            params.rightMargin = host.ui.dp(8);
            categoryRow.addView(chip, params);
        }
        categoryScroll.addView(categoryRow);
        form.addView(categoryScroll);
        EditText detail = host.ui.listingField(form, "Details", "Condition, module, size or anything useful", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        detail.setSingleLine(false);
        detail.setMinLines(3);
        detail.setGravity(Gravity.TOP | Gravity.START);
        detail.setFilters(new InputFilter[] {new InputFilter.LengthFilter(500)});

        form.addView(host.ui.label("Maximum budget"));
        final String[] budget = {"100"};
        LinearLayout budgetRow = host.ui.horizontal();
        HorizontalScrollView budgetScroll = new HorizontalScrollView(host);
        budgetScroll.setHorizontalScrollBarEnabled(false);
        ArrayList<TextView> budgetChips = new ArrayList<>();
        final EditText[] customBudget = {null};
        final View[] customLabel = {null};
        for (String amount : new String[] {"50", "100", "250", "500", "1000", "Custom"}) {
            TextView chip = host.ui.chip(amount.equals("Custom") ? "Custom" : "R" + amount, host.surface(), host.text);
            chip.setPadding(host.ui.dp(10), host.ui.dp(8), host.ui.dp(10), host.ui.dp(8));
            styleChoiceChip(chip, amount.equals(budget[0]));
            chip.setOnClickListener(v -> {
                budget[0] = amount;
                for (TextView item : budgetChips) {
                    String label = item.getText().toString();
                    styleChoiceChip(item, label.equals(amount.equals("Custom") ? "Custom" : "R" + amount));
                }
                if (customBudget[0] != null) customBudget[0].setVisibility(amount.equals("Custom") ? View.VISIBLE : View.GONE);
                if (customLabel[0] != null) customLabel[0].setVisibility(amount.equals("Custom") ? View.VISIBLE : View.GONE);
            });
            budgetChips.add(chip);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(42));
            params.rightMargin = host.ui.dp(8);
            budgetRow.addView(chip, params);
        }
        budgetScroll.addView(budgetRow);
        form.addView(budgetScroll);
        form.addView(host.ui.bodyText("A budget helps classmates know what range works for you.", host.muted, 11));
        customBudget[0] = host.ui.listingField(form, "Custom budget (ZAR)", "0.00",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        customLabel[0] = form.getChildAt(form.getChildCount() - 2);
        customBudget[0].setVisibility(View.GONE);
        customLabel[0].setVisibility(View.GONE);

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button publish = host.ui.primaryButton("Publish wanted post");
        footer.addView(publish, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);
        publish.setOnClickListener(v -> {
            String item = host.ui.text(title).trim();
            String details = host.ui.text(detail).trim();
            String amount = budget[0].equals("Custom") && customBudget[0] != null
                    ? host.ui.text(customBudget[0]).trim() : budget[0];
            if (item.length() < 3) { title.setError("Enter the item you need"); return; }
            if (amount.isEmpty()) {
                if (customBudget[0] != null) { customBudget[0].setError("Enter a budget"); customBudget[0].requestFocus(); }
                return;
            }
            if (!amount.matches("\\d+(\\.\\d{1,2})?")) {
                if (budget[0].equals("Custom") && customBudget[0] != null) customBudget[0].setError("Enter a valid amount");
                else Toast.makeText(host, "Choose a valid budget.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                final CampusFeatures.Wanted[] created = {null};
                host.featureStore.update(f -> {
                    created[0] = f.want(host.currentStudentEmail, item, category[0], details, amount,
                            System.currentTimeMillis());
                    if (created[0] != null) created[0].ownerName = host.currentStudentName;
                });
                if (created[0] != null) cloudSaveWanted(created[0]);
                page.dismiss();
                host.showAppScreen("Wanted");
            } catch (RuntimeException e) {
                Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void styleChoiceChip(TextView chip, boolean active) {
        chip.setBackground(active ? host.ui.round(host.blue, 999)
                : host.ui.strokedRound(host.surface(), 999, host.border));
        chip.setTextColor(active ? Color.WHITE : host.text);
        chip.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void cloudSaveWanted(CampusFeatures.Wanted w) {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty() || w == null) return;
        java.util.Map<String, Object> doc = new java.util.HashMap<>();
        doc.put("ownerId", uid);
        doc.put("ownerEmail", w.owner);
        doc.put("ownerName", w.ownerName == null ? "" : w.ownerName);
        doc.put("title", w.title);
        doc.put("category", w.category);
        doc.put("detail", w.detail);
        doc.put("budget", w.budget);
        doc.put("created", w.created);
        doc.put("active", w.active);
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("wanted")
                .document(w.id).set(doc)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "wanted save failed: " + e.getMessage()));
    }

    private void cloudUpdateWanted(String id, boolean active) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("wanted")
                .document(id).update("active", active)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "wanted update failed: " + e.getMessage()));
    }

    private void cloudPullWanted(Runnable after) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("wanted").limit(100).get()
                .addOnSuccessListener(snap -> {
                    if (host.isDestroyed() || host.isFinishing()) { if (after != null) after.run(); return; }
                    try {
                        host.featureStore.update(f -> {
                            for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                                CampusFeatures.Wanted target = null;
                                for (CampusFeatures.Wanted w : f.wanted) if (w.id.equals(doc.getId())) { target = w; break; }
                                if (target == null) {
                                    target = new CampusFeatures.Wanted();
                                    target.id = doc.getId();
                                    f.wanted.add(target);
                                }
                                String owner = doc.getString("ownerEmail");
                                if (owner == null || owner.isEmpty()) owner = CloudSync.emailForUid(host, doc.getString("ownerId"));
                                target.owner = owner == null || owner.isEmpty() ? "Student" : owner;
                                target.ownerName = doc.getString("ownerName") == null ? "" : doc.getString("ownerName");
                                target.title = doc.getString("title") == null ? "" : doc.getString("title");
                                target.category = doc.getString("category") == null ? "Other" : doc.getString("category");
                                target.detail = doc.getString("detail") == null ? "" : doc.getString("detail");
                                target.budget = doc.getString("budget") == null ? "0.00" : doc.getString("budget");
                                Long stamp = doc.getLong("created");
                                target.created = stamp == null ? 0 : stamp;
                                Boolean active = doc.getBoolean("active");
                                target.active = active == null || active;
                            }
                        });
                    } catch (RuntimeException e) {
                        android.util.Log.w("CampusTrade", "wanted merge failed: " + e.getMessage());
                    }
                    if (after != null) after.run();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("CampusTrade", "wanted pull failed: " + e.getMessage());
                    if (after != null) after.run();
                });
    }

    // Saved searches are private. Pausing stops new match alerts.
    public void buildSearchAlerts(LinearLayout content) {
        Button create = host.ui.primaryButton("Save a search");
        create.setOnClickListener(v -> createSearchAlert());
        content.addView(create);
        int count = 0;
        for (CampusFeatures.SearchAlert s : host.featureStore.data.searches) {
            if (!s.owner.equals(host.currentStudentEmail)) continue;
            count++;
            LinearLayout row = host.ui.card(host.surface(), 12, host.ui.dp(14));
            row.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
            LinearLayout top = host.ui.horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(host.ui.titleText(s.query.isEmpty() ? s.category : s.query, host.text, 15),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(host.ui.statusPill(s.active ? "Active" : "Paused"));
            row.addView(top);
            row.addView(host.ui.bodyText(s.category + (s.budget.isEmpty() ? " · Any price" : " · Up to R" + s.budget),
                    host.muted, 12));
            row.addView(host.ui.bodyText(s.active ? "We'll tell you when a listing matches."
                    : "Alerts are paused for this search.", host.muted, 11));
            row.setOnClickListener(v -> showSearchAlertSheet(s));
            host.ui.attachPressAnimation(row);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = host.ui.dp(8);
            content.addView(row, rowParams);
        }
        if (count == 0) {
            content.addView(host.ui.emptyState("search", "No saved searches yet",
                    "Save a search and we'll tell you when a matching listing appears. Try \"Calculus textbook\" under R300."));
            Button example = host.ui.secondaryButton("Try the example search");
            example.setOnClickListener(v -> createSearchAlert("Calculus textbook", "Books", "300"));
            LinearLayout.LayoutParams exampleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
            exampleParams.topMargin = host.ui.dp(8);
            content.addView(example, exampleParams);
        }
    }

    private void showSearchAlertSheet(CampusFeatures.SearchAlert alert) {
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        LinearLayout top = host.ui.horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(host.ui.titleText(alert.query.isEmpty() ? alert.category : alert.query, host.text, 17),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(host.ui.statusPill(alert.active ? "Active" : "Paused"));
        body.addView(top);
        body.addView(host.ui.bodyText(alert.category + (alert.budget.isEmpty() ? " · Any price" : " · Up to R" + alert.budget),
                host.muted, 12));
        Button matches = host.ui.primaryButton("View matches");
        matches.setOnClickListener(v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            showSearchResults(alert);
        });
        LinearLayout.LayoutParams matchesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        matchesParams.topMargin = host.ui.dp(14);
        body.addView(matches, matchesParams);
        Button toggle = host.ui.secondaryButton(alert.active ? "Pause alerts" : "Resume alerts");
        toggle.setOnClickListener(v -> {
            try {
                host.featureStore.update(f -> {
                    for (CampusFeatures.SearchAlert item : f.searches) {
                        if (item.id.equals(alert.id) && item.owner.equals(host.currentStudentEmail)) item.active = !item.active;
                    }
                });
                cloudUpdateSearchAlert(alert);
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                host.showAppScreen("SearchAlerts");
            } catch (RuntimeException e) {
                Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        toggleParams.topMargin = host.ui.dp(8);
        body.addView(toggle, toggleParams);
        TextView delete = host.ui.textButton("Delete search");
        delete.setPadding(0, host.ui.dp(16), 0, host.ui.dp(8));
        delete.setOnClickListener(v -> host.ui.dialog()
                .setTitle("Delete saved search?")
                .setMessage("Delete \"" + (alert.query.isEmpty() ? alert.category : alert.query)
                        + "\"? You'll stop getting match alerts.")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Delete", (d, which) -> {
                    try {
                        host.featureStore.update(f -> {
                            for (java.util.Iterator<CampusFeatures.SearchAlert> iterator = f.searches.iterator(); iterator.hasNext();) {
                                CampusFeatures.SearchAlert item = iterator.next();
                                if (item.id.equals(alert.id) && item.owner.equals(host.currentStudentEmail)) iterator.remove();
                            }
                        });
                        cloudDeleteSearchAlert(alert);
                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                        host.showAppScreen("SearchAlerts");
                    } catch (RuntimeException e) {
                        Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show());
        body.addView(delete);
        sheetRef[0] = host.ui.showBottomSheet("Saved search", body);
    }

    public void createSearchAlert() {
        createSearchAlert(host.marketQuery, "All", "");
    }

    // Full-screen saved search with category chips and a budget.
    public void createSearchAlert(String queryDefault, String categoryDefault, String budgetDefault) {
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Save a search", "Get alerts for new listings", page::dismiss));
        ScrollView scroll = new ScrollView(host);
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        EditText query = host.ui.listingField(form, "Keyword", "e.g. Calculus textbook", InputType.TYPE_CLASS_TEXT);
        query.setText(queryDefault);
        query.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
        form.addView(host.ui.label("Category"));
        final String[] category = {categoryDefault};
        LinearLayout categoryRow = host.ui.horizontal();
        HorizontalScrollView categoryScroll = new HorizontalScrollView(host);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        ArrayList<TextView> categoryChips = new ArrayList<>();
        for (String option : new String[] {"All", "Books", "Technology", "Stationery", "Other"}) {
            TextView chip = host.ui.chip(option, host.surface(), host.text);
            chip.setPadding(host.ui.dp(10), host.ui.dp(8), host.ui.dp(10), host.ui.dp(8));
            styleChoiceChip(chip, option.equals(category[0]));
            chip.setOnClickListener(v -> {
                category[0] = option;
                for (TextView item : categoryChips) styleChoiceChip(item, item.getText().toString().equals(option));
            });
            categoryChips.add(chip);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(42));
            params.rightMargin = host.ui.dp(8);
            categoryRow.addView(chip, params);
        }
        categoryScroll.addView(categoryRow);
        form.addView(categoryScroll);
        EditText budget = host.ui.listingField(form, "Maximum price (optional)", "Any price",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        budget.setText(budgetDefault);
        form.addView(host.ui.bodyText("Example: Calculus textbook, Books, up to R300. You'll only be notified for new listings that match.",
                host.muted, 11));

        View footerRule = new View(host);
        footerRule.setBackgroundColor(host.line);
        root.addView(footerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        LinearLayout footer = new LinearLayout(host);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackgroundColor(host.surface());
        Button save = host.ui.primaryButton("Save search");
        footer.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(50)));
        root.addView(footer);
        save.setOnClickListener(v -> {
            String keyword = host.ui.text(query).trim();
            String max = host.ui.text(budget).trim();
            if (keyword.isEmpty() && category[0].equals("All")) {
                query.setError("Add a keyword or choose a category");
                return;
            }
            if (!max.isEmpty() && !max.matches("\\d+(\\.\\d{1,2})?")) {
                budget.setError("Enter a valid amount");
                return;
            }
            try {
                final CampusFeatures.SearchAlert[] created = {null};
                host.featureStore.update(f -> created[0] = f.watch(host.currentStudentEmail, keyword, category[0], max));
                if (created[0] != null) cloudSaveSearchAlert(created[0]);
                page.dismiss();
                host.showAppScreen("SearchAlerts");
            } catch (RuntimeException e) {
                Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        page.setContentView(root);
        page.show();
        android.view.Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // Make an active copy of a search so the same match check can be reused.
    public void showSearchResults(CampusFeatures.SearchAlert s) {
        ArrayList<MainActivity.Product> matches = new ArrayList<>();
        for (MainActivity.Product p : host.products) {
            CampusFeatures.SearchAlert active = new CampusFeatures.SearchAlert();
            active.active = true; active.query = s.query; active.category = s.category; active.budget = s.budget;
            if (host.market.publicListing(p) && p.status.equals("Available")
                    && CampusFeatures.matches(active, p.title, p.description, p.category, p.price)) matches.add(p);
        }
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.bodyText(s.query.isEmpty() ? s.category : s.query,
                host.text, 15));
        body.addView(host.ui.bodyText(matches.size() + (matches.size() == 1 ? " matching listing" : " matching listings"),
                host.muted, 12));
        if (matches.isEmpty()) {
            body.addView(host.ui.emptyState("search", "No matches yet",
                    "New listings appear as students post them. We'll notify you when something matches."));
        } else {
            for (MainActivity.Product p : matches) {
                LinearLayout card = host.ui.horizontal();
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
                View thumb = host.market.listingThumbnail(p, false);
                card.addView(thumb, new LinearLayout.LayoutParams(host.ui.dp(52), host.ui.dp(52)));
                LinearLayout copy = new LinearLayout(host);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
                LinearLayout titleRow = host.ui.horizontal();
                titleRow.setGravity(Gravity.CENTER_VERTICAL);
                titleRow.addView(host.ui.titleText(p.title, host.text, 13),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                titleRow.addView(host.ui.titleText("R" + p.price, host.text, 13));
                copy.addView(titleRow);
                copy.addView(host.ui.bodyText(p.category + " · " + host.studentName(p.sellerEmail), host.muted, 11));
                card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                card.addView(host.new CampusAssetView(host, "chevron", host.muted),
                        new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16)));
                card.setOnClickListener(v -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    host.market.showProductDetails(p);
                });
                host.ui.attachPressAnimation(card);
                body.addView(card);
            }
        }
        sheetRef[0] = host.ui.showBottomSheet("Search matches", body);
    }

    // Use the listing ID and price to stop duplicate alerts.
    void notifySearchMatches(MainActivity.Product p) {
        if (!host.market.publicListing(p) || !p.status.equals("Available")) return;
        ArrayList<String> owners = new ArrayList<>(); String key = p.id + ":" + p.price;
        try {
            host.featureStore.update(f -> {
                for (CampusFeatures.SearchAlert s : f.searches) if (!s.owner.equals(p.sellerEmail) && !s.seen.contains(key)
                        && CampusFeatures.matches(s, p.title, p.description, p.category, p.price)) { s.seen.add(key); owners.add(s.owner); }
            });
            for (String owner : new LinkedHashSet<>(owners)) host.notification.addNotification(owner, "Saved-search match", p.title + " - R" + p.price, "marketplace", "");
            cloudNotifySearchMatches(p);
        } catch (RuntimeException e) { Toast.makeText(host, "Listing saved; search alerts couldn't be updated.", Toast.LENGTH_SHORT).show(); }
    }

    private void cloudSaveSearchAlert(CampusFeatures.SearchAlert s) {
        if (s == null) return;
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("ownerId", uid);
        data.put("ownerEmail", s.owner);
        data.put("query", s.query);
        data.put("category", s.category);
        data.put("budget", s.budget);
        data.put("active", s.active);
        data.put("seen", new java.util.ArrayList<>(s.seen));
        data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("searchAlerts").document(uid + "_" + s.id)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "search alert cloud save failed: " + e.getMessage()));
    }

    private void cloudUpdateSearchAlert(CampusFeatures.SearchAlert s) {
        if (s == null) return;
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("searchAlerts").document(uid + "_" + s.id)
                .update("active", s.active, "query", s.query, "category", s.category, "budget", s.budget)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "search alert cloud update failed: " + e.getMessage()));
    }

    private void cloudDeleteSearchAlert(CampusFeatures.SearchAlert s) {
        if (s == null) return;
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("searchAlerts").document(uid + "_" + s.id)
                .delete()
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "search alert cloud delete failed: " + e.getMessage()));
    }

    // Push uses a fresh Firebase token in the background.
    private void cloudNotifySearchMatches(MainActivity.Product p) {
        if (p == null || !host.market.publicListing(p) || !p.status.equals("Available")) return;
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(result -> {
            String idToken = result == null ? null : result.getToken();
            if (idToken == null || idToken.isEmpty()) return;
            new Thread(() -> {
                java.net.HttpURLConnection connection = null;
                try {
                    java.net.URL url = new java.net.URL("https://campustrade-fcm.vercel.app/api/search-match");
                    connection = (java.net.HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Authorization", "Bearer " + idToken);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    org.json.JSONObject payload = new org.json.JSONObject();
                    payload.put("listingId", p.id);
                    payload.put("title", p.title);
                    payload.put("description", p.description);
                    payload.put("category", p.category);
                    payload.put("price", p.price);
                    payload.put("sellerEmail", p.sellerEmail);
                    payload.put("sellerUid", CloudSync.uid(host));
                    byte[] bytes = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) android.util.Log.w("CampusTrade", "search match push returned HTTP " + code);
                } catch (Exception e) {
                    android.util.Log.w("CampusTrade", "search match push failed: " + e.getMessage());
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }).start();
        });
    }

    public void syncCloudSearchAlerts() {
        for (CampusFeatures.SearchAlert s : host.featureStore.data.searches) {
            if (s.owner.equals(host.currentStudentEmail)) cloudSaveSearchAlert(s);
        }
    }

    String communityHeaderIcon(String tab) {
        if (tab.equals("Home")) return "bell";
        if (tab.equals("Inbox")) return "compose";
        if (tab.equals("Profile")) return "settings";
        if (tab.equals("Academics")) return "calendar";
        if (tab.equals("Admin")) return "exit";
        if (tab.equals("Groups") || tab.equals("Wanted") || tab.equals("SearchAlerts")) return "plus";
        if (tab.equals("Notifications") || tab.equals("Payments") || tab.equals("AdminReports") || tab.equals("Offers")) return "back";
        return "cart";
    }

    public String communityHeaderLabel(String tab) {
        if (tab.equals("Home")) return "Open notifications";
        if (tab.equals("Inbox")) return "New conversation";
        if (tab.equals("Profile")) return "Profile settings";
        if (tab.equals("Academics")) return "Academic calendar";
        if (tab.equals("Admin")) return "Exit admin";
        if (tab.equals("Groups")) return "Create study group";
        if (tab.equals("Wanted")) return "Create wanted post";
        if (tab.equals("SearchAlerts")) return "Save search";
        return communityHeaderIcon(tab).equals("back") ? "Go back" : "Orders and transactions";
    }
}