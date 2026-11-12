package com.campustrade.auth.model;

import com.google.firebase.firestore.Exclude;

// One review for a completed order.
public class Rating {

    private String reviewerID = "";
    private String reviewedUserID = "";
    private double rating = 0.0;
    private String comment = "";
    private String docId = "";

    public Rating() {
    }

    public String getReviewerID() {
        return reviewerID;
    }

    public void setReviewerID(String reviewerID) {
        this.reviewerID = reviewerID;
    }

    public String getReviewedUserID() {
        return reviewedUserID;
    }

    public void setReviewedUserID(String reviewedUserID) {
        this.reviewedUserID = reviewedUserID;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Exclude
    public String getDocId() {
        return docId;
    }

    @Exclude
    public void setDocId(String docId) {
        this.docId = docId;
    }
}