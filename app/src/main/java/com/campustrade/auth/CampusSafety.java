package com.campustrade.auth;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Locale;

final class CampusSafety {
    private static final int CONTENT_WARNINGS = 3;
    private final SharedPreferences prefs;
    private final SharedPreferences disputes;
    private final Context context;
    CampusSafety(Context context) {
        this.context = context;
        prefs = com.campustrade.auth.data.SecurePrefs.get(context, "campustrade_safety");
        disputes = com.campustrade.auth.data.SecurePrefs.get(context, "campustrade_community_tools");
    }

    static final class ContentScan {
        final boolean flagged;
        final String reasons;
        final int strike;
        final boolean postingBlocked;
        ContentScan(boolean flagged, String reasons, int strike, boolean postingBlocked) {
            this.flagged = flagged;
            this.reasons = reasons;
            this.strike = strike;
            this.postingBlocked = postingBlocked;
        }
    }

    /** Common scam, illicit-goods and misleading-claim patterns. */
    private String contentReasons(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        StringBuilder reasons = new StringBuilder();
        String[][] checks = {
                {"counterfeit|fake (id|passport|degree|certificate|invoice|proof|licen[cs]e)|replica (designer|brand)|knock[- ]?off",
                        "possible counterfeit goods or fake documents"},
                {"stolen (phone|laptop|bike|car|goods)|no questions asked|goods fell off a truck",
                        "possible stolen goods"},
                {"cocaine|heroin|methamphetamine|mandrax|ecstasy|illegal drugs|weed for sale|dagga for sale",
                        "possible illegal drugs"},
                {"unlicensed firearm|gun for sale|ammunition for sale|weapon for sale",
                        "possible illegal weapons"},
                {"leaked exam|exam paper for sale|exam memo|test bank|assignment answers for sale|write my exam|do my assignment for money",
                        "possible academic dishonesty"},
                {"sell cvv|buy cvv|fullz|bank logs|hacked account|phishing kit|carding",
                        "possible fraud or account takeover"},
                {"money doubling|double your money|guaranteed returns|get rich quick|miracle cure|cures? cancer|guaranteed pass",
                        "possible misleading claim"},
                {"pay outside campustrade|pay me directly first|send deposit before viewing|western union only|bitcoin only",
                        "possible payment scam"}
        };
        for (String[] check : checks) {
            if (!java.util.regex.Pattern.compile("(?:" + check[0] + ")").matcher(value).find()) continue;
            if (reasons.length() > 0) reasons.append("; ");
            reasons.append(check[1]);
        }
        return reasons.toString();
    }

    int contentStrikes(String actor) {
        return prefs.getInt("content_strikes:" + email(actor), 0);
    }

    boolean postingBlocked(String actor) {
        return contentStrikes(actor) >= CONTENT_WARNINGS;
    }

    /**
     * Warnings 1 and 2 stop the specific post and explain the rule.
     * Warning 3 pauses posting and files an admin review.
     */
    ContentScan reviewContent(String actor, String uid, String surface, String text) {
        String reasons = contentReasons(text);
        int previous = contentStrikes(actor);
        if (reasons.isEmpty()) {
            return new ContentScan(false, "", previous, postingBlocked(actor));
        }
        if (previous >= CONTENT_WARNINGS) {
            return new ContentScan(true, reasons, CONTENT_WARNINGS, true);
        }
        int strike = previous + 1;
        prefs.edit().putInt("content_strikes:" + email(actor), strike)
                .putString("content_last_reason:" + email(actor), reasons)
                .putLong("content_last_at:" + email(actor), System.currentTimeMillis()).commit();
        if (uid != null && !uid.isEmpty()) {
            java.util.Map<String, Object> flag = new java.util.HashMap<>();
            flag.put("userId", uid);
            flag.put("userEmail", email(actor));
            flag.put("surface", surface == null ? "app" : surface);
            flag.put("content", text == null ? "" : text);
            flag.put("reasons", reasons);
            flag.put("strike", strike);
            flag.put("status", strike >= CONTENT_WARNINGS ? "Escalated" : "Warning");
            flag.put("createdAt", com.google.firebase.Timestamp.now());
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("contentFlags").add(flag);
        }
        return new ContentScan(true, reasons, strike, strike >= CONTENT_WARNINGS);
    }

    /** Pick up a moderator reset or a strike count issued on another device. */
    void syncContentSanctions(String actor, String uid) {
        if (uid == null || uid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("contentSanctions").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String who = email(actor);
                    Long remote = doc.getLong("strikes");
                    com.google.firebase.Timestamp clearedStamp = doc.getTimestamp("clearedAt");
                    long cleared = clearedStamp == null ? 0 : clearedStamp.toDate().getTime();
                    long localCleared = prefs.getLong("content_cleared_at:" + who, 0);
                    if (cleared > localCleared) {
                        prefs.edit().putInt("content_strikes:" + who, 0)
                                .remove("content_last_reason:" + who)
                                .putLong("content_cleared_at:" + who, cleared).apply();
                    } else if (remote != null && remote > contentStrikes(actor)) {
                        prefs.edit().putInt("content_strikes:" + who, remote.intValue()).apply();
                    }
                });
    }

    String contentLastReason(String actor) {
        return prefs.getString("content_last_reason:" + email(actor), "");
    }

    void clearContentStrikes(String actor) {
        prefs.edit().remove("content_strikes:" + email(actor))
                .remove("content_last_reason:" + email(actor)).apply();
    }

    /** Block every new post once the third warning is active. */
    boolean postingPausedDialog(String actor) {
        if (!postingBlocked(actor)) return false;
        new android.app.AlertDialog.Builder(context)
                .setTitle("Posting paused")
                .setMessage("This account reached 3 content warnings. Posting stays paused until a moderator "
                        + "reviews the account.\n\nUse Settings > Content warnings to request a review.")
                .setPositiveButton("OK", null)
                .show();
        return true;
    }

    /** Show a consistent three-warning message and return true when the post was stopped. */
    boolean warnIfFlagged(ContentScan scan, Runnable onBlocked) {
        if (scan == null || !scan.flagged) return false;
        if (scan.postingBlocked) {
            new android.app.AlertDialog.Builder(context)
                    .setTitle("Posting paused")
                    .setMessage("You have reached 3 content warnings. Posting is paused and a campus moderator "
                            + "has been asked to review the account.\n\nLatest issue: " + scan.reasons
                            + "\n\nIf you believe this is a mistake, use Settings > Content warnings to appeal.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            new android.app.AlertDialog.Builder(context)
                    .setTitle("Warning " + scan.strike + " of 3")
                    .setMessage("This content was not posted because it may break CampusTrade rules: "
                            + scan.reasons + ".\n\n"
                            + (3 - scan.strike == 1 ? "One more warning" : (3 - scan.strike) + " more warnings")
                            + " would pause posting and send your account to a moderator for review.")
                    .setPositiveButton("I understand", null)
                    .show();
        }
        if (onBlocked != null) onBlocked.run();
        return true;
    }
    private static String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    boolean blocks(String actor, String peer) { return blocked(actor).contains(email(peer)); }
    boolean blockedEither(String actor, String peer) { return blocks(actor, peer) || blocks(peer, actor); }
    java.util.Set<String> blocked(String actor) { return new HashSet<>(prefs.getStringSet("blocked:" + email(actor), new HashSet<>())); }
    void block(String actor, String peer, boolean enabled) {
        if (email(actor).equals(email(peer))) throw new IllegalArgumentException("You can't block yourself");
        java.util.Set<String> people = blocked(actor);
        if (enabled) people.add(email(peer)); else people.remove(email(peer));
        if (!prefs.edit().putStringSet("blocked:" + email(actor), people).commit()) throw new IllegalStateException("Couldn't save block preferences");
    }

    void block(String actor, String peer, boolean enabled, String uid) {
        block(actor, peer, enabled);
        if (uid == null || uid.isEmpty()) return;
        String target = email(peer);
        com.google.firebase.firestore.DocumentReference ref = com.google.firebase.firestore.FirebaseFirestore
                .getInstance().collection("blockedUsers").document(uid + "_" + target);
        if (!enabled) { ref.delete(); return; }
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("ownerId", uid);
        data.put("blockedUserId", target);
        data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        ref.set(data);
    }

    void sync(String actor, String uid) {
        if (uid == null || uid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("blockedUsers")
                .whereEqualTo("ownerId", uid).get()
                .addOnSuccessListener(snapshot -> {
                    java.util.Set<String> people = new HashSet<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        String target = doc.getString("blockedUserId");
                        if (target != null && !target.isEmpty()) people.add(email(target));
                    }
                    prefs.edit().putStringSet("blocked:" + email(actor), people).apply();
                });
    }
    String pickupCode(String order) {
        String code = prefs.getString("code:" + order, "");
        if (code.isEmpty()) {
            code = String.format(Locale.ROOT, "%06d", new java.security.SecureRandom().nextInt(1_000_000));
            if (!prefs.edit().putString("code:" + order, code).commit()) throw new IllegalStateException("Couldn't save pickup code");
        }
        return code;
    }
    boolean pickupVerified(String order) { return prefs.getBoolean("collected:" + order, false); }
    void confirmCode(String order, String code, long now) {
        if (pickupVerified(order)) throw new IllegalArgumentException("Pickup code already confirmed");
        if (now < prefs.getLong("locked:" + order, 0)) throw new IllegalArgumentException("Too many attempts. Try again in five minutes.");
        if (!pickupCode(order).equals(code.trim())) {
            int attempts = prefs.getInt("attempts:" + order, 0) + 1;
            prefs.edit().putInt("attempts:" + order, attempts >= 5 ? 0 : attempts)
                    .putLong("locked:" + order, attempts >= 5 ? now + 300_000 : 0).commit();
            throw new IllegalArgumentException("Pickup code doesn't match");
        }
        if (!prefs.edit().putBoolean("collected:" + order, true).commit()) throw new IllegalStateException("Couldn't save pickup confirmation");
    }
    boolean disputed(String order) { return disputes.getBoolean("disputed:" + order, false); }
    void dispute(String order, boolean active) {
        if (!disputes.edit().putBoolean("disputed:" + order, active).commit()) throw new IllegalStateException("Couldn't save dispute status");
    }
}