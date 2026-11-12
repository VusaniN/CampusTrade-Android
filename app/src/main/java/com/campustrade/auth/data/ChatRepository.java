package com.campustrade.auth.data;

import com.campustrade.auth.model.Chat;
import com.campustrade.auth.model.Message;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Load and save chats. Each chat links two students and a listing.
public class ChatRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface IdCallback {
        void onOk(String docId);
        void onBad(String message);
    }

    public interface ChatListCallback {
        void onOk(List<Chat> chats);
        void onBad(String message);
    }

    public interface MessageListCallback {
        void onOk(List<Message> messages);
        void onBad(String message);
    }

    public interface DoneCallback {
        void onOk();
        void onBad(String message);
    }

    public static String documentId(String buyerID, String sellerID, String listingID) {
        // One chat ID per buyer, seller and listing, so saving the same ID reuses it.
        return buyerID + "_" + sellerID + "_" + listingID;
    }

    /** Find or create one chat for this buyer, seller and listing. */
    public void create(String buyerID, String sellerID, String listingID, IdCallback cb) {
        final String chatId = documentId(buyerID, sellerID, listingID);
        // Saving the same ID only refreshes the header.
        Map<String, Object> doc = new HashMap<>();
        doc.put("buyerID", buyerID);
        doc.put("sellerID", sellerID);
        doc.put("listingID", listingID);
        doc.put("createdDate", Timestamp.now());
        db.collection("chats").document(chatId).set(doc)
                .addOnSuccessListener(v -> cb.onOk(chatId))
                .addOnFailureListener(e -> {
                    // Rules allow reading an existing chat but not creating a second one.
                    db.collection("chats").document(chatId).get()
                            .addOnSuccessListener(second -> {
                                if (second.exists()) cb.onOk(chatId);
                                else cb.onBad(msg(e));
                            })
                            .addOnFailureListener(second -> cb.onBad(msg(e)));
                });
    }

    /** Load chats where the student is buyer or seller. */
    public void inbox(String uid, ChatListCallback cb) {
        // There is no members list, so load buyer and seller chats and merge them.
        db.collection("chats").whereEqualTo("buyerID", uid).get()
                .addOnSuccessListener(buySnap -> db.collection("chats")
                        .whereEqualTo("sellerID", uid).get()
                        .addOnSuccessListener(sellSnap -> {
                            List<Chat> out = new ArrayList<>();
                            // Seen stops a chat appearing twice when both queries match.
                            java.util.Set<String> seen = new java.util.HashSet<>();
                            for (QueryDocumentSnapshot d : buySnap) {
                                Chat c = d.toObject(Chat.class);
                                c.setDocId(d.getId());
                                seen.add(d.getId());
                                out.add(c);
                            }
                            for (QueryDocumentSnapshot d : sellSnap) {
                                if (!seen.add(d.getId())) {
                                    continue; 
                                }
                                Chat c = d.toObject(Chat.class);
                                c.setDocId(d.getId());
                                out.add(c);
                            }
                            cb.onOk(out);
                        })
                        .addOnFailureListener(e -> cb.onBad(msg(e))))
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    /** Send a message in a chat. */
    public void send(String chatId, String senderID, String text, DoneCallback cb) {
        // Messages are separate, so opening a chat loads only that chat.
        Map<String, Object> doc = new HashMap<>();
        doc.put("senderID", senderID);
        doc.put("message", text);
        doc.put("timestamp", Timestamp.now());
        db.collection("chats").document(chatId).collection("messages").add(doc)
                .addOnSuccessListener(v -> cb.onOk())
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }
    public static final int MESSAGE_PAGE_SIZE = 100;
    /** Load recent messages, oldest first. */
    public void messages(String chatId, MessageListCallback cb) {
        messages(chatId, MESSAGE_PAGE_SIZE, cb);
    }

    public void messages(String chatId, int limit, MessageListCallback cb) {
        // Load at most 200 messages.
        final int capped = Math.max(1, Math.min(limit, 200));
        db.collection("chats").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(capped).get()
                .addOnSuccessListener(snap -> {
                    List<Message> out = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        Message message = d.toObject(Message.class);
                        message.setDocId(d.getId());
                        out.add(message);
                    }
                    // Firebase returns newest first; reverse them for the chat view.
                    Collections.reverse(out);
                    cb.onOk(out);
                })
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    /** Mark the other student's messages read when the chat opens. */
    public void markRead(String chatId, String peerUid, DoneCallback cb) {
        db.collection("chats").document(chatId).collection("messages")
                .whereEqualTo("senderID", peerUid).get()
                .addOnSuccessListener(snap -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    int writes = 0;
                    for (QueryDocumentSnapshot d : snap) {
                        if (d.getTimestamp("readAt") != null) continue;
                        batch.update(d.getReference(), "readAt", Timestamp.now());
                        writes++;
                    }
                    if (writes == 0) {
                        cb.onOk();
                        return;
                    }
                    batch.commit()
                            .addOnSuccessListener(v -> cb.onOk())
                            .addOnFailureListener(e -> cb.onBad(msg(e)));
                })
                .addOnFailureListener(e -> cb.onBad(msg(e)));
    }

    /** Show a short typing hint; ignore old hints. */
    public void typing(String chatId, String uid) {
        if (chatId == null || chatId.isEmpty() || uid == null || uid.isEmpty()) return;
        java.util.Map<String, Object> update = new HashMap<>();
        update.put("typingUid", uid);
        update.put("typingAt", Timestamp.now());
        db.collection("chats").document(chatId)
                .update(update)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "typing update: " + e.getMessage()));
    }

    private String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Request failed. Check connection.";
    }
}