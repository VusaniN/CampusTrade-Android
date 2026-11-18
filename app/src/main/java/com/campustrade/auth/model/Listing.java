package com.campustrade.auth.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

// One marketplace listing.
public class Listing {

    private String sellerID = "";
    private String sellerName = "";
    private String listingN = "";
    private String description = "";
    private String specs = "";
    private double price = 0.0;
    private String category = "";
    private String condition = "";
    private String location = "";
    private String delivery = "";
    private String status = "AVAILABLE";
    private String imageURL = "";
    // First photo is the cover; listings can have more than one.
    private java.util.List<String> photos = new java.util.ArrayList<>();
    // Document fields, blank when nothing is attached.
    private String documentURL = "";
    private String documentName = "";
    private String documentMime = "";
    private long documentSize = 0;
    private boolean negotiable = true;
    private long quantity = 1;
    private long viewCount = 0;
    private long favouriteCount = 0;
    private Timestamp updatedAt = null;
    private Timestamp createdAt = null;
    private Timestamp expiresAt = null;
    private String docId = "";

    public Listing() {
    }

    public String getSellerID() {
        return sellerID;
    }

    public void setSellerID(String sellerID) {
        this.sellerID = sellerID;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getListingN() {
        return listingN;
    }

    public void setListingN(String listingN) {
        this.listingN = listingN;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpecs() {
        return specs;
    }

    public void setSpecs(String specs) {
        this.specs = specs;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDelivery() {
        return delivery;
    }

    public void setDelivery(String delivery) {
        this.delivery = delivery;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImageURL() {
        return imageURL;
    }

    public void setImageURL(String imageURL) {
        this.imageURL = imageURL;
    }

    public java.util.List<String> getPhotos() {
        return photos;
    }

    public void setPhotos(java.util.List<String> photos) {
        this.photos = photos;
    }

    public String getDocumentURL() {
        return documentURL;
    }

    public void setDocumentURL(String documentURL) {
        this.documentURL = documentURL;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getDocumentMime() {
        return documentMime;
    }

    public void setDocumentMime(String documentMime) {
        this.documentMime = documentMime;
    }

    public long getDocumentSize() {
        return documentSize;
    }

    public void setDocumentSize(long documentSize) {
        this.documentSize = documentSize;
    }

    public boolean getNegotiable() {
        return negotiable;
    }

    public void setNegotiable(boolean negotiable) {
        this.negotiable = negotiable;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public long getFavouriteCount() {
        return favouriteCount;
    }

    public void setFavouriteCount(long favouriteCount) {
        this.favouriteCount = favouriteCount;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    // Local ID so the app can find the cloud copy.
    @Exclude
    public String getDocId() {
        return docId;
    }

    @Exclude
    public void setDocId(String docId) {
        this.docId = docId;
    }
}