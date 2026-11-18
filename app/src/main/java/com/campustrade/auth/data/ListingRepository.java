package com.campustrade.auth.data;

import com.campustrade.auth.model.Listing;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Load and save listings here instead of in the Market screen.
public class ListingRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface IdCallback {
        void onOk(String docId);
        void onBad(String message);
    }

    public interface ListCallback {
        void onOk(List<Listing> items);
        void onBad(String message);
    }

    public interface DoneCallback {
        void onOk();
        void onBad(String message);
    }

    // Short version for older screens.
    public void create(String sellerID, String sellerName, String listingN, String description,
                       double price, String category, String condition, String location,
                       String delivery, String status, String imageURL,
                       java.util.List<String> photos, IdCallback cb) {
        create(sellerID, sellerName, listingN, description, price, category, condition, location,
                delivery, status, imageURL, photos, "", "", "", 0, cb);
    }

    /** Use the local ID so retries update instead of duplicating. */
    public void createWithId(String docId, String sellerID, String sellerName, String listingN,
                             String description, double price, String category, String condition,
                             String specs, String location, String delivery, String status, String imageURL,
                             java.util.List<String> photos, String documentURL, String documentName,
                             String documentMime, long documentSize, int quantity, IdCallback cb) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("sellerID", sellerID);
        doc.put("sellerName", sellerName != null ? sellerName : "");
        doc.put("listingN", listingN);
        doc.put("description", description);
        doc.put("specs", specs != null ? specs : "");
        doc.put("price", price);
        doc.put("category", category);
        doc.put("condition", condition);
        doc.put("location", location != null ? location : "");
        doc.put("delivery", delivery != null ? delivery : "");
        doc.put("status", status != null ? status : "AVAILABLE");
        doc.put("imageURL", imageURL != null ? imageURL : "");
        doc.put("photos", photos != null ? photos : new ArrayList<>());
        doc.put("documentURL", documentURL != null ? documentURL : "");
        doc.put("documentName", documentName != null ? documentName : "");
        doc.put("documentMime", documentMime != null ? documentMime : "");
        doc.put("documentSize", Math.max(0, documentSize));
        doc.put("negotiable", true);
        doc.put("quantity", Math.max(1, Math.min(999, quantity)));
        doc.put("viewCount", 0);
        doc.put("favouriteCount", 0);
        doc.put("createdAt", Timestamp.now());
        doc.put("updatedAt", Timestamp.now());
        doc.put("expiresAt", new Timestamp(new java.util.Date(
                System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000)));
        // No ID lets Firebase create one; a local ID makes retries update the same listing.
        if (docId == null || docId.isEmpty()) {
            db.collection("listings").add(doc)
                    .addOnSuccessListener(ref -> cb.onOk(ref.getId()))
                    .addOnFailureListener(e -> {
                        // Older rules do not know the expiry field, so keep publishing and sync it later.
                        Map<String, Object> legacy = withoutExpiry(doc);
                        if (legacy == null) { cb.onBad(msg(e)); return; }
                        db.collection("listings").add(legacy)
                                .addOnSuccessListener(ref -> cb.onOk(ref.getId()))
                                .addOnFailureListener(second -> cb.onBad(msg(second)));
                    });
        } else {
            com.google.firebase.firestore.DocumentReference ref =
                    db.collection("listings").document(docId);
            ref.get()
                    .addOnSuccessListener(snap -> {
                        if (snap.exists()) {

                            // Edits keep the original publish date.
                            doc.remove("createdAt");
                            ref.set(doc, SetOptions.merge())
                                    .addOnSuccessListener(v -> cb.onOk(docId))
                                    .addOnFailureListener(e -> {
                                        Map<String, Object> legacy = withoutExpiry(doc);
                                        if (legacy == null) { cb.onBad(msg(e)); return; }
                                        ref.set(legacy, SetOptions.merge())
                                                .addOnSuccessListener(v -> cb.onOk(docId))
                                                .addOnFailureListener(second -> cb.onBad(msg(second)));
                                    });
                        } else {
                            ref.set(doc)
                                    .addOnSuccessListener(v -> cb.onOk(docId))
                                    .addOnFailureListener(e -> {
                                        Map<String, Object> legacy = withoutExpiry(doc);
                                        if (legacy == null) { cb.onBad(msg(e)); return; }
                                        ref.set(legacy)
                                                .addOnSuccessListener(v -> cb.onOk(docId))
                                                .addOnFailureListener(second -> cb.onBad(msg(second)));
                                    });
                        }
                    })
                    .addOnFailureListener(e -> cb.onBad(msg(e)));
        }
    }

    /** Return a copy without the expiry field, or null if there is nothing to retry. */
    private Map<String, Object> withoutExpiry(Map<String, Object> fields) {
        if (!fields.containsKey("expiresAt")) return null;
        Map<String, Object> copy = new HashMap<>(fields);
        copy.remove("expiresAt");
        return copy;
    }

    /** Create with an optional document. */
    public void create(String sellerID, String sellerName, String listingN, String description,
                       double price, String category, String condition, String location,
                       String delivery, String status, String imageURL,
                       java.util.List<String> photos, String documentURL, String documentName,
                       String documentMime, long documentSize, IdCallback cb) {
        createWithId(null, sellerID, sellerName, listingN, description, price, category, condition,
                "", location, delivery, status, imageURL, photos, documentURL, documentName,
                documentMime, documentSize, 1, cb);
    }

    /** Load the newest 20 listings. */
    public void list(ListCallback cb) {
        listPage(null, 20, cb);
    }

    /** Load one page, newest first. Use the oldest createdAt as the next cursor. */
    public void listPage(Timestamp after, int limit, ListCallback cb) {
        final int capped = Math.max(1, Math.min(limit, 50));
        com.google.firebase.firestore.Query query = db.collection("listings")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(capped);
        if (after != null) query = query.startAfter(after);
        query.get()
                .addOnSuccessListener(snap -> {
                    List<Listing> out = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Listing item = d.toObject(Listing.class);
                        // Copy the cloud ID onto the listing for edits and deletes.
                        item.setDocId(d.getId());
                        out.add(item);
                    }
                    cb.onOk(out);
                })
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    /** Update fields or mark the listing sold. */
    public void update(String docId, Map<String, Object> fields, DoneCallback cb) {
        // Update changes only the given fields and fails if the listing is missing.
        db.collection("listings").document(docId).update(fields)
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> {
                    // Retry without the new expiry field
                    // if the rules are older.
                    Map<String, Object> legacy = withoutExpiry(fields);
                    if (legacy == null) { cb.onBad(msg(e)); return; }
                    db.collection("listings").document(docId).update(legacy)
                            .addOnSuccessListener(v -> cb.onOk())
                            .addOnFailureListener(second -> cb.onBad(msg(second)));
                });
    }

    /** Delete: owner only. */
    public void delete(String docId, DoneCallback cb) {
        // Firebase rules stop another student deleting the listing.
        db.collection("listings").document(docId).delete()
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    private String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Request failed. Check connection.";
    }
}