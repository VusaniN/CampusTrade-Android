package com.campustrade.auth.data;

import com.campustrade.auth.Constants;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

// Helpers for sessions, cached names, chats and photo uploads.
public final class CloudSync {

    private static final String PREFS = "campustrade_prefs";

    private CloudSync() {
    }

    public interface StrCallback {
        void onOk(String value);
        void onBad(String message);
    }

    // ---- session ----

    // Save the "keep me logged in" details used on startup.
    public static String uid(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("uid", "");
    }

    public static void saveSession(Context ctx, String uid, String email, String name) {
        // Save the ID and signed-in flag together.
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("uid", uid)
                .putString("student_email", email)
                .putString("student_name", name)
                .putBoolean("signed_in", true)
                .apply();
    }

    // ---- Email and ID lookup ----

    // Two-way ID/email map so chats don't check Firebase every time.
    private static SharedPreferences dir(Context ctx) {
        return ctx.getSharedPreferences(PREFS + "_directory", Context.MODE_PRIVATE);
    }

    public static void directoryPut(Context ctx, String uid, String email) {
        if (uid == null || uid.isEmpty() || email == null || email.isEmpty()) {
            return;
        }
        dir(ctx).edit().putString("u:" + uid, email).putString("e:" + email.toLowerCase(), uid).apply();
    }

    /** Cache names so chats and listings show a real student name. */
    public static void cacheProfile(Context ctx, String uid, String name, String email) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        if (email != null && !email.isEmpty()) {
            directoryPut(ctx, uid, email);
        }
        // Names use their own key because names can change.
        if (name != null && !name.isEmpty()) {
            android.content.SharedPreferences.Editor edit = ctx.getSharedPreferences(PREFS + "_directory", Context.MODE_PRIVATE).edit();
            edit.putString("n:" + uid, name);
            if (email != null && !email.isEmpty()) edit.putString("em:" + email.toLowerCase(java.util.Locale.ROOT), name);
            edit.apply();
        }
    }

    /** Cached name for an email, or empty. */
    public static String nameForEmail(Context ctx, String email) {
        if (email == null || email.isEmpty()) return "";
        return ctx.getSharedPreferences(PREFS + "_directory", Context.MODE_PRIVATE)
                .getString("em:" + email.toLowerCase(java.util.Locale.ROOT), "");
    }

    /** Turn a student email into a Firebase ID. */
    public static void emailToUid(Context ctx, String email, StrCallback cb) {
        if (email == null || email.isEmpty()) {
            cb.onBad("Unknown student");
            return;
        }
        String cached = dir(ctx).getString("e:" + email.toLowerCase(), "");
        // Check the local cache before Firebase.
        if (!cached.isEmpty()) {
            cb.onOk(cached);
            return;
        }
        final String typed = email.trim();
        FirebaseFirestore.getInstance().collection("publicProfiles")
                .whereEqualTo("stuEmail", typed).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        String uid = snap.getDocuments().get(0).getId();
                        directoryPut(ctx, uid, typed);
                        cb.onOk(uid);
                        return;
                    }
                    String lower = typed.toLowerCase(java.util.Locale.ROOT);
                    // Try the lowercase email once for older profiles saved with capitals.
                    if (lower.equals(typed)) {
                        cb.onBad("Student not on CampusTrade yet");
                        return;
                    }
                    FirebaseFirestore.getInstance().collection("publicProfiles")
                            .whereEqualTo("stuEmail", lower).limit(1).get()
                            .addOnSuccessListener(snap2 -> {
                                if (snap2.isEmpty()) {
                                    cb.onBad("Student not on CampusTrade yet");
                                    return;
                                }
                                String uid = snap2.getDocuments().get(0).getId();
                                directoryPut(ctx, uid, lower);
                                cb.onOk(uid);
                            })
                            .addOnFailureListener(e -> cb.onBad(e.getMessage() != null ? e.getMessage() : "Lookup failed"));
                })
                .addOnFailureListener(e -> cb.onBad(e.getMessage() != null ? e.getMessage() : "Lookup failed"));
    }

    /** Turn an email or Firebase ID into a Firebase ID. */
    public static void peerUid(Context ctx, String peer, StrCallback cb) {
        if (peer == null || peer.isEmpty()) {
            cb.onBad("Unknown student");
            return;
        }
        if (!peer.contains("@")) {
            // No @ means it is already an ID; still refresh the cached name.
            FirebaseFirestore.getInstance().collection("publicProfiles").document(peer).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            cb.onBad("Unknown student");
                            return;
                        }
                        String email = doc.getString("stuEmail");
                        if (email != null && !email.isEmpty()) {
                            directoryPut(ctx, peer, email);
                        }
                        String name = doc.getString("stuName");
                        if (name != null && !name.isEmpty()) {
                            ctx.getSharedPreferences(PREFS + "_directory", Context.MODE_PRIVATE)
                                    .edit().putString("n:" + peer, name).apply();
                        }
                        cb.onOk(peer);
                    })
                    .addOnFailureListener(e -> cb.onBad(msg(e)));
            return;
        }
        emailToUid(ctx, peer, cb);
    }

    /** Cached email for an ID. */
    public static String emailForUid(Context ctx, String uid) {
        if (uid == null || uid.isEmpty()) {
            return "";
        }
        return dir(ctx).getString("u:" + uid, "");
    }

    /** Display name for the ID. */
    public static String displayName(Context ctx, String uid) {
        String name = ctx.getSharedPreferences(PREFS + "_directory", Context.MODE_PRIVATE)
                .getString("n:" + uid, "");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String email = emailForUid(ctx, uid);
        if (email != null && !email.isEmpty() && email.contains("@")) {
            // Use the part before @ instead of a raw ID.
            return email.substring(0, email.indexOf('@'));
        }
        return "Seller";
    }

    private static SharedPreferences idmap(Context ctx) {
        // Link a local listing ID to its cloud copy so retries update the same item.
        return ctx.getSharedPreferences(PREFS + "_remoteids", Context.MODE_PRIVATE);
    }

    public static void putRemoteId(Context ctx, String localId, String docId) {
        idmap(ctx).edit().putString("l:" + localId, docId).putString("r:" + docId, localId).apply();
    }

    public static String remoteId(Context ctx, String localId) {
        String mapped = idmap(ctx).getString("l:" + localId, "");
        if (!mapped.isEmpty()) return mapped;
        // Listings loaded directly from Firestore use "remote-<docId>".
        return localId != null && localId.startsWith("remote-") ? localId.substring(7) : "";
    }

    public static String localId(Context ctx, String docId) {
        return idmap(ctx).getString("r:" + docId, "");
    }

    // ---- Direct chats ----

    /** Find or create the direct chat for two students. */
    public static void directChat(String myUid, String peerUid, ChatRepository.IdCallback cb) {
        // Direct chats use "direct" as the listing marker and check both sides.
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("chats").whereEqualTo("buyerID", myUid).get()
                .addOnSuccessListener(buySnap -> {
                    for (QueryDocumentSnapshot d : buySnap) {
                        if (peerUid.equals(d.getString("sellerID"))
                                && "direct".equals(d.getString("listingID"))) {
                            cb.onOk(d.getId());
                            return;
                        }
                    }
                    db.collection("chats").whereEqualTo("sellerID", myUid).get()
                            .addOnSuccessListener(sellSnap -> {
                                for (QueryDocumentSnapshot d : sellSnap) {
                                    if (peerUid.equals(d.getString("buyerID"))
                                            && "direct".equals(d.getString("listingID"))) {
                                        cb.onOk(d.getId());
                                        return;
                                    }
                                }
                                new ChatRepository().create(myUid, peerUid, "direct", cb);
                            })
                            .addOnFailureListener(e -> cb.onBad(msg(e)));
                })
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    // ---- Photo uploads ----

    /** Upload a gallery image and return its URL, or null. */
    public static String uploadPhoto(Context ctx, Uri uri) {
        return uploadFile(ctx, uri, "image/upload", "photo.jpg", "image/jpeg");
    }

    /** Upload a document and return its URL. */
    public static String uploadDocument(Context ctx, Uri uri, String fileName, String mimeType) {
        String name = fileName == null || fileName.trim().isEmpty() ? "document" : fileName.trim();
        name = name.replace("\"", "").replace("\r", "").replace("\n", "");
        String type = mimeType == null || mimeType.trim().isEmpty()
                ? "application/octet-stream" : mimeType.trim();
        type = type.replace("\r", "").replace("\n", "");
        return uploadFile(ctx, uri, "raw/upload", name, type);
    }

    private static String uploadFile(Context ctx, Uri uri, String resourcePath,
                                     String fileName, String contentType) {
        if (uri == null) {
            return null;
        }
        // A photo that is already a URL is not uploaded again.
        if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
            return uri.toString();
        }
        String boundary = "----CampusTrade" + System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            URL endpoint = new URL("https://api.cloudinary.com/v1_1/" + Constants.CLOUD_NAME + "/" + resourcePath);
            conn = (HttpURLConnection) endpoint.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            // Stream the upload instead of loading the whole file into memory.
            conn.setChunkedStreamingMode(16 * 1024);
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return null;
                }
                try (OutputStream out = conn.getOutputStream()) {
                    writeField(out, boundary, "upload_preset", Constants.CLOUDINARY_PRESET);
                    out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                    out.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                            + fileName + "\"\r\n").getBytes("UTF-8"));
                    out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes("UTF-8"));
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.write("\r\n".getBytes("UTF-8"));
                    out.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
                    out.flush();
                }
            }
            int code = conn.getResponseCode();
            InputStream res = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            if (res == null) {
                return null;
            }
            Scanner scanner = new Scanner(res, "UTF-8").useDelimiter("\\A");
            String body = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            if (code != 200) {
                return null;
            }
            // Return the URL, or null if the upload fails.
            return new org.json.JSONObject(body).optString("secure_url", null);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void writeField(OutputStream out, String boundary,
                                   String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
        out.write((value + "\r\n").getBytes("UTF-8"));
    }

    private static String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Request failed";
    }
}