package com.campustrade.auth;

// Shared limits and names used by more than one file.
public final class Constants {

    private Constants() { }

    // ---- Listing limits ----
    public static final int MAX_LISTING_PHOTOS = 5;
    // 10 MB limit; larger files are slow and blocked.
    public static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024; // 10 MB

    // ---- Validation patterns ----
    public static final String PRICE_PATTERN = "[0-9]{1,7}(\\.[0-9]{1,2})?";

    // ---- Photo uploads ----
    public static final String CLOUD_NAME = "gel8nwzg";
    public static final String CLOUDINARY_PRESET = "campus_trade";

    // ---- Notifications ----
    public static final String NOTIFICATION_CHANNEL_ID = "campustrade_updates_v2";
}