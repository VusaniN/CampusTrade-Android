package com.campustrade.auth;

// Shared limits and names used by more than one file.
public final class Constants {

    private Constants() { }

    // ---- Listing limits ----
    public static final int MAX_LISTING_PHOTOS = 5;
    // 10 MB limit; larger files are slow and blocked.
    public static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024; // 10 MB
    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024; // 8 MB

    // ---- Validation patterns ----
    public static final String PRICE_PATTERN = "[0-9]{1,7}(\\.[0-9]{1,2})?";

    // ---- Staff / support identities ----
    // This campus account always opens the restricted admin shell.
    public static final String STRICT_ADMIN_EMAIL = "eduv4941658@vossie.net";
    // Reserved peer used by students when they start a live admin chat.
    public static final String ADMIN_CHAT_EMAIL = "admin@campustrade.local";

    // ---- Photo uploads ----
    public static final String CLOUD_NAME = "gel8nwzg";
    public static final String CLOUDINARY_PRESET = "campus_trade";
    public static final String[] ALLOWED_IMAGE_MIMES = {
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"
    };

    // ---- Notifications ----
    public static final String NOTIFICATION_CHANNEL_ID = "campustrade_updates_v3";
    public static final String ORDER_CHANNEL_ID = "campustrade_orders_v3";
    public static final String CHAT_CHANNEL_ID = "campustrade_chats_v3";

    // ---- Invoice email relay (Google Apps Script + Gmail) ----
    // The relay must verify the caller's Firebase ID token and the order.
    public static final String INVOICE_EMAIL_ENDPOINT =
            "https://script.google.com/macros/s/AKfycbyK25iK_D-mmdnRNXU8Xdl5tnrKmCHPCf3yg468w1z-EU-fPTx6IvUDoKYvF3JQvrRR/exec";
}