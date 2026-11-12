package com.campustrade.auth;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Locale;

final class CampusSafety {
    private final SharedPreferences prefs;
    private final SharedPreferences disputes;
    CampusSafety(Context context) {
        prefs = context.getSharedPreferences("campustrade_safety", Context.MODE_PRIVATE);
        disputes = context.getSharedPreferences("campustrade_community_tools", Context.MODE_PRIVATE);
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