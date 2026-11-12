package com.campustrade.auth.data;

import com.campustrade.auth.model.Rating;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Ratings and report forms.
public class RatingRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface DoneCallback {
        // The rating screen only needs success or failure.
        void onOk();
        void onBad(String message);
    }

    public interface RatingListCallback {
        void onOk(List<Rating> ratings);
        void onBad(String message);
    }

    /** Create a review after a completed order. The ID keeps one review per student per order. */
    public void rate(String reviewerID, String reviewedUserID,
                     double stars, String comment, String orderId, DoneCallback cb) {
        if (orderId == null || orderId.isEmpty()) {
            cb.onBad("A rating needs an order.");
            return;
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("reviewerID", reviewerID);
        doc.put("reviewedUserID", reviewedUserID);
        // Keep stars between 1 and 5.
        doc.put("rating", Math.max(1.0, Math.min(5.0, stars)));
        doc.put("comment", comment != null ? comment : "");
        doc.put("orderId", orderId);
        doc.put("createdAt", Timestamp.now());
        db.collection("ratings").document(orderId + "_" + reviewerID).set(doc)
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    /** Load ratings received by one student. */
    public void forUser(String reviewedUserID, RatingListCallback cb) {
        db.collection("ratings").whereEqualTo("reviewedUserID", reviewedUserID).get()
                .addOnSuccessListener(snap -> {
                    List<Rating> out = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Rating rating = d.toObject(Rating.class);
                        rating.setDocId(d.getId());
                        out.add(rating);
                    }
                    cb.onOk(out);
                })
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    /** Send an issue report or suggestion. */
    public void report(String userID, String type, String description, DoneCallback cb) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("userID", userID);
        doc.put("type", type);
        doc.put("description", description);
        doc.put("statusF", "OPEN");
        doc.put("createdAt", Timestamp.now());
        db.collection("feedback").add(doc)
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    private String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Request failed. Check connection.";
    }
}