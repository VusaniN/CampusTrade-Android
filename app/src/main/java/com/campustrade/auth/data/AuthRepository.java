package com.campustrade.auth.data;

import com.campustrade.auth.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


// Bridge between the screens and Firebase.
public class AuthRepository {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Screens get an ID or a message; onVerify handles "check your email".
    public interface AuthCallback {
        void onOk(String uid);
        void onBad(String message);

        /** The account exists but must verify its email first. */
        default void onVerify(String email, String message) {
            onBad(message);
        }
    }

    public interface SimpleCallback {
        void onOk();
        void onBad(String message);
    }

    /** Sign-up creates the login, private profile and public profile. The email must be verified before Firebase can be used. */
    public void register(String stuName, String stuEmail, String studentNumber,
                         String password, String course, String yearOfStudy, AuthCallback cb) {
        final String email = stuEmail.trim().toLowerCase(Locale.ROOT);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    final FirebaseUser user = result.getUser();
                    if (user == null) {
                        cb.onBad("The account couldn't be created. Give it another go.");
                        return;
                    }
                    user.sendEmailVerification();
                    final String uid = user.getUid();

                    Map<String, Object> privateDoc = new HashMap<>();
                    // The private profile keeps the student number hidden.
                    privateDoc.put("stuName", stuName);
                    privateDoc.put("stuEmail", email);
                    privateDoc.put("studentNumber", studentNumber);
                    privateDoc.put("course", course != null ? course : "");
                    privateDoc.put("year", yearOfStudy != null ? yearOfStudy : "");
                    privateDoc.put("rating", 0.0);
                    privateDoc.put("createdAt", Timestamp.now());
                    privateDoc.put("uid", uid);
                    privateDoc.put("profileImage", "");
                    privateDoc.put("accountStatus", "active");
                    privateDoc.put("lastActive", System.currentTimeMillis());

                    // Public profile fields.
                    // The public profile is searchable by email and never shows the student number.
                    Map<String, Object> publicDoc = new HashMap<>();
                    publicDoc.put("uid", uid);
                    publicDoc.put("stuName", stuName);
                    publicDoc.put("stuEmail", email);
                    publicDoc.put("course", course != null ? course : "");
                    publicDoc.put("year", yearOfStudy != null ? yearOfStudy : "");
                    publicDoc.put("rating", 0.0);
                    publicDoc.put("createdAt", Timestamp.now());
                    publicDoc.put("profileImage", "");

                    // Write both profiles together so a dropped connection can't half-create them.
                    WriteBatch batch = db.batch();
                    batch.set(db.collection("users").document(uid), privateDoc);
                    batch.set(db.collection("publicProfiles").document(uid), publicDoc);
                    batch.commit()
                            .addOnSuccessListener(v -> {
                                // Keep the unverified Firebase session so the
                                // countdown screen can resend without asking
                                // for the password again. The app session is
                                // still locked until verification succeeds.
                                cb.onVerify(email, "Account created. We sent a verification link to "
                                        + email + ". Verify it, then log in.");
                            })
                            .addOnFailureListener(e -> {
                                // Delete the login too, otherwise the student can't
                                // retry with the same email.
                                user.delete();
                                auth.signOut();
                                cb.onBad(msg(e));
                            });
                })
                .addOnFailureListener(e -> cb.onBad(friendly(e)));
    }

    /** Login returns the student ID only after email verification. */
    public void login(String stuEmail, String password, AuthCallback cb) {
        auth.signInWithEmailAndPassword(stuEmail, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        cb.onBad("Login failed. Give it another go.");
                        return;
                    }
                    if (!user.isEmailVerified()) {

                        // Resend while keeping the unverified session so the
                        // verification screen can resend without a password.
                        user.sendEmailVerification();
                        String email = user.getEmail() != null ? user.getEmail() : stuEmail;
                        cb.onVerify(email, "Verify your campus email before signing in. "
                                + "We sent a new link to " + email + ".");
                        return;
                    }
                    cb.onOk(user.getUid());
                })
                .addOnFailureListener(e -> cb.onBad(friendly(e)));
    }

    /** Clear the Firebase session. */
    public void logout() {
        auth.signOut();
    }

    /** Send the Firebase reset email. */
    public void resetPassword(String stuEmail, SimpleCallback cb) {
        auth.sendPasswordResetEmail(stuEmail)
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> cb.onBad(resetFriendly(e)));
    }

    /** Resend the verification link using the kept unverified session. */
    public void resendVerification(SimpleCallback cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onBad("Log in again with your password, then resend the link.");
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> cb.onBad(resetFriendly(e)));
    }

    /** Refresh the Firebase user after the student taps the email link. */
    public void checkEmailVerified(SimpleCallback cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            cb.onBad("Log in again to finish verifying your email.");
            return;
        }
        user.reload().addOnSuccessListener(v -> {
            FirebaseUser fresh = auth.getCurrentUser();
            if (fresh != null && fresh.isEmailVerified()) cb.onOk();
            else cb.onBad("That link isn't verified yet. Open the newest email and try again.");
        }).addOnFailureListener(e -> cb.onBad("Couldn't check the link. Check your connection."));
    }

    /** In-app password change, after proving the current password. */
    public void changePassword(String oldPassword, String newPassword, SimpleCallback cb) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            cb.onBad("You aren't signed in. Log in again.");
            return;
        }
        if (newPassword == null || newPassword.length() < 10) {
            cb.onBad("Use a new password with at least 10 characters.");
            return;
        }
        com.google.firebase.auth.AuthCredential credential =
                EmailAuthProvider.getCredential(user.getEmail(), oldPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(v -> user.updatePassword(newPassword)
                        .addOnSuccessListener(done -> cb.onOk())
                        .addOnFailureListener(e -> cb.onBad(passwordFriendly(e))))
                .addOnFailureListener(e -> cb.onBad("The current password is incorrect."));
    }

    private String passwordFriendly(Exception e) {
        String code = "";
        if (e instanceof com.google.firebase.auth.FirebaseAuthException) {
            code = ((com.google.firebase.auth.FirebaseAuthException) e).getErrorCode();
        }
        if ("ERROR_WEAK_PASSWORD".equals(code)) return "Use a stronger password (10+ characters).";
        if ("ERROR_REQUIRES_RECENT_LOGIN".equals(code)) return "Log in again before changing the password.";
        if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) return "No connection. Check the internet and try again.";
        return "The password couldn't be changed. Try again.";
    }

    /** File an appeal from a suspended login before it is signed out. */
    public void submitAppeal(String uid, String email, String name, String reason, SimpleCallback cb) {
        if (uid == null || uid.isEmpty()) {
            cb.onBad("Log in again to send an appeal.");
            return;
        }
        Map<String, Object> appeal = new HashMap<>();
        appeal.put("uid", uid);
        appeal.put("email", email == null ? "" : email);
        appeal.put("name", name == null ? "" : name);
        appeal.put("reason", reason == null ? "" : reason.trim());
        appeal.put("status", "Pending");
        appeal.put("createdAt", Timestamp.now());
        // A new document per appeal keeps old reviews and lets a student ask
        // again after a rejection without overwriting the moderation record.
        db.collection("appeals").document(uid + "_" + System.currentTimeMillis()).set(appeal)
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    private String resetFriendly(Exception e) {
        // Turn Firebase errors into messages a student can act on.
        String code = "";
        if (e instanceof com.google.firebase.auth.FirebaseAuthException) {
            code = ((com.google.firebase.auth.FirebaseAuthException) e).getErrorCode();
        }
        if ("ERROR_INVALID_EMAIL".equals(code)) {
            return "Enter a valid student email address.";
        }
        if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
            return "No connection. Check the internet and try again.";
        }
        if ("ERROR_TOO_MANY_REQUESTS".equals(code)) {
            return "Too many attempts. Try again in a few minutes.";
        }
        return "Couldn't send the reset email. Try again.";
    }

    // Used on startup to decide whether to skip login.
    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isEmailVerified() ? user.getUid() : null;
    }

    /** The profile shows the real login state. */
    public boolean isCurrentEmailVerified() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }

    /** Load one private student profile. */
    public void loadUser(String uid, UserCallback cb) {
        // Firebase needs the empty User constructor to build a profile.
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snap -> cb.onOk(snap.toObject(User.class)))
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    public void syncPublicProfile(String uid, User user, SimpleCallback cb) {
        // Keep the public profile in step; merging keeps fields like rating.
        if (uid == null || uid.isEmpty() || user == null) {
            if (cb != null) cb.onBad("Profile is missing.");
            return;
        }
        String email = user.getStuEmail() == null ? "" : user.getStuEmail().toLowerCase(Locale.ROOT);
        Map<String, Object> doc = new HashMap<>();
        doc.put("uid", uid);
        doc.put("stuEmail", email);
        doc.put("stuName", user.getStuName() != null ? user.getStuName() : "");
        doc.put("course", user.getCourse() != null ? user.getCourse() : "");
        doc.put("year", user.getYear() != null ? user.getYear() : "");
        doc.put("profileImage", user.getProfileImage() != null ? user.getProfileImage() : "");
        doc.put("updatedAt", Timestamp.now());
        db.collection("publicProfiles").document(uid).set(doc, SetOptions.merge())
                .addOnSuccessListener(v -> { if (cb != null) cb.onOk(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onBad(msg(e)); });
    }

    /** Update the private and public profiles together. */
    public void updateProfile(String uid, String email, String stuName, String course,
                              String year, SimpleCallback cb) {
        if (uid == null || uid.isEmpty()) {
            cb.onBad("You aren't signed in.");
            return;
        }
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> privateFields = new HashMap<>();
        privateFields.put("stuName", stuName);
        privateFields.put("course", course != null ? course : "");
        privateFields.put("year", year != null ? year : "");

        Map<String, Object> publicFields = new HashMap<>();
        publicFields.put("uid", uid);
        publicFields.put("stuEmail", normalized);
        publicFields.put("stuName", stuName);
        publicFields.put("course", course != null ? course : "");
        publicFields.put("year", year != null ? year : "");
        publicFields.put("updatedAt", Timestamp.now());

        WriteBatch batch = db.batch();
        // Update the private profile; create or update the public one.
        batch.update(db.collection("users").document(uid), privateFields);
        batch.set(db.collection("publicProfiles").document(uid), publicFields, SetOptions.merge());
        batch.commit()
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    /** Save the last login time for admin activity. */
    public void touchLastActive(String uid) {
        if (uid == null || uid.isEmpty()) return;
        db.collection("users").document(uid)
                .update("lastActive", System.currentTimeMillis(), "updatedAt", Timestamp.now());
    }

    /** Update the live presence shown on the moderator dashboard. */
    public void setPresence(String uid, boolean online) {
        if (uid == null || uid.isEmpty()) return;
        java.util.Map<String, Object> fields = new HashMap<>();
        fields.put("online", online);
        fields.put("lastActive", System.currentTimeMillis());
        fields.put("updatedAt", Timestamp.now());
        db.collection("users").document(uid).update(fields)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade",
                        "presence update failed: " + e.getMessage()));
    }

    /** Used by settings and admin account tools. */
    public void setAccountStatus(String uid, String status, SimpleCallback cb) {
        if (uid == null || uid.isEmpty()) {
            if (cb != null) cb.onBad("You aren't signed in.");
            return;
        }
        db.collection("users").document(uid)
                .update("accountStatus", status, "updatedAt", Timestamp.now())
                .addOnSuccessListener(v -> { if (cb != null) cb.onOk(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onBad(msg(e)); });
    }

    public void updateProfileImage(String uid, String url, SimpleCallback cb) {
        if (uid == null || uid.isEmpty()) {
            if (cb != null) cb.onBad("You aren't signed in.");
            return;
        }
        String image = url == null ? "" : url;
        db.collection("users").document(uid).update("profileImage", image, "updatedAt", Timestamp.now());
        db.collection("publicProfiles").document(uid)
                .set(java.util.Collections.singletonMap("profileImage", image), SetOptions.merge())
                .addOnSuccessListener(v -> { if (cb != null) cb.onOk(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onBad(msg(e)); });
    }

    public void deletePublicProfile(String uid, SimpleCallback cb) {
        if (uid == null || uid.isEmpty()) {
            if (cb != null) cb.onBad("You aren't signed in.");
            return;
        }
        db.collection("publicProfiles").document(uid).delete()
                .addOnSuccessListener(v -> { if (cb != null) cb.onOk(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onBad(msg(e)); });
    }

    /** Remove the student's public listings when deleting. */
    public void deleteOwnListings(String uid, SimpleCallback cb) {
        if (uid == null || uid.isEmpty()) { if (cb != null) cb.onBad("You aren't signed in."); return; }
        db.collection("listings").whereEqualTo("sellerID", uid).get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) batch.delete(doc.getReference());
                    batch.commit()
                            .addOnSuccessListener(v -> { if (cb != null) cb.onOk(); })
                            .addOnFailureListener(e -> { if (cb != null) cb.onBad(msg(e)); });
                })
                .addOnFailureListener(e -> { if (cb != null) cb.onBad(msg(e)); });
    }

    /** Remove the student's saved listings when deleting. */
    public void deleteOwnFavourites(String uid, SimpleCallback cb) {
        if (uid == null || uid.isEmpty()) { if (cb != null) cb.onBad("You aren't signed in."); return; }
        db.collection("favourites").whereEqualTo("userId", uid).get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) batch.delete(doc.getReference());
                    batch.commit()
                            .addOnSuccessListener(v -> { if (cb != null) cb.onOk(); })
                            .addOnFailureListener(e -> { if (cb != null) cb.onBad(msg(e)); });
                })
                .addOnFailureListener(e -> { if (cb != null) cb.onBad(msg(e)); });
    }

    public interface UserCallback {
        void onOk(User user);
        void onBad(String message);
    }

    private String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Request failed. Check connection.";
    }

    private String friendly(Exception e) {
        // Group login and sign-up errors that show the same message.
        String code = "";
        if (e instanceof com.google.firebase.auth.FirebaseAuthException) {
            code = ((com.google.firebase.auth.FirebaseAuthException) e).getErrorCode();
        }
        if ("ERROR_INVALID_EMAIL".equals(code) || "ERROR_WRONG_PASSWORD".equals(code)
                || "ERROR_USER_NOT_FOUND".equals(code)
                || "ERROR_INVALID_CREDENTIAL".equals(code)) {
            return "Email or password is incorrect.";
        }
        if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
            return "Couldn't create the account. Try logging in instead.";
        }
        if ("ERROR_WEAK_PASSWORD".equals(code)) {
            return "Use a stronger password (10+ characters).";
        }
        if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
            return "No connection. Check the internet and try again.";
        }
        return msg(e);
    }
}