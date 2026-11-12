package com.campustrade.auth.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

// One chat message, ordered by time.
public class Message {

    private String senderID = "";
    private String message = "";
    private Timestamp timestamp = null;
    private Timestamp readAt = null;
    private String docId = "";

    public Message() {
    }

    public Message(String senderID, String message, Timestamp timestamp) {
        this.senderID = senderID;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getSenderID() {
        return senderID;
    }

    public void setSenderID(String senderID) {
        this.senderID = senderID;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public Timestamp getReadAt() {
        return readAt;
    }

    public void setReadAt(Timestamp readAt) {
        this.readAt = readAt;
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