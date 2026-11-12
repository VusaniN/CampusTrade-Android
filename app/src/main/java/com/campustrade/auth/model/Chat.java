package com.campustrade.auth.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;


// One chat: buyer, seller and listing; messages sit underneath.
public class Chat {

    private String buyerID = "";
    private String sellerID = "";
    private String listingID = "";
    private Timestamp createdDate = null;
    private String docId = "";

    public Chat() {
    }

    public String getBuyerID() {
        return buyerID;
    }

    public void setBuyerID(String buyerID) {
        this.buyerID = buyerID;
    }

    public String getSellerID() {
        return sellerID;
    }

    public void setSellerID(String sellerID) {
        this.sellerID = sellerID;
    }

    public String getListingID() {
        return listingID;
    }

    public void setListingID(String listingID) {
        this.listingID = listingID;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Timestamp createdDate) {
        this.createdDate = createdDate;
    }

    // Local ID; the cloud should not save it.
    @Exclude
    public String getDocId() {
        return docId;
    }

    @Exclude
    public void setDocId(String docId) {
        this.docId = docId;
    }
}