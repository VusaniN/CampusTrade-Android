package com.campustrade.auth;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.campustrade.auth.data.CloudSync;
import com.campustrade.auth.data.HelperBot;
import com.campustrade.auth.data.SecurePrefs;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

/** A student avatar that falls back to initials and refreshes from the public profile. */
final class StudentAvatar {

    private StudentAvatar() { }

    static View create(MainActivity host, String emailOrUid, String name, int textSize,
                       int textColor, int background) {
        FrameLayout holder = new FrameLayout(host);
        String display = name == null || name.trim().isEmpty() ? "Student" : name.trim();
        holder.setContentDescription(display + " profile photo");
        String local = cachedImage(host, emailOrUid);
        render(host, holder, local, display, textSize, textColor, background);
        if (emailOrUid == null || emailOrUid.trim().isEmpty()) return holder;
        if (HelperBot.PEER.equalsIgnoreCase(emailOrUid)
                || Constants.ADMIN_CHAT_EMAIL.equalsIgnoreCase(emailOrUid)) return holder;

        if (emailOrUid.contains("@")) {
            CloudSync.peerUid(host, emailOrUid, new CloudSync.StrCallback() {
                @Override public void onOk(String uid) {
                    loadProfile(host, holder, uid, emailOrUid, display,
                            textSize, textColor, background);
                }

                @Override public void onBad(String message) { }
            });
        } else {
            loadProfile(host, holder, emailOrUid, emailOrUid, display,
                    textSize, textColor, background);
        }
        return holder;
    }

    private static void loadProfile(MainActivity host, FrameLayout holder, String uid,
                                    String cacheId, String fallbackName,
                                    int textSize, int textColor, int background) {
        FirebaseFirestore.getInstance().collection("publicProfiles").document(uid).get()
                .addOnSuccessListener(profile -> {
                    String image = profile.getString("profileImage");
                    if (image == null || image.trim().isEmpty()) return;
                    cacheImage(host, cacheId, image);
                    String profileEmail = profile.getString("stuEmail");
                    if (profileEmail != null && profileEmail.contains("@")) {
                        cacheImage(host, profileEmail, image);
                    }
                    String freshName = profile.getString("stuName");
                    String shownName = freshName == null || freshName.trim().isEmpty()
                            ? fallbackName : freshName.trim();
                    host.runOnUiThread(() -> {
                        if (host.isDestroyed() || host.isFinishing()) return;
                        render(host, holder, image, shownName,
                                textSize, textColor, background);
                    });
                });
    }

    private static void render(MainActivity host, FrameLayout holder, String image, String name,
                               int textSize, int textColor, int background) {
        holder.removeAllViews();
        holder.setContentDescription(name + " profile photo");
        if (image != null && !image.trim().isEmpty()) {
            ImageView photo = new ImageView(host);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setContentDescription(name + " profile photo");
            holder.addView(photo, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Glide.with(host).load(image).circleCrop().into(photo);
            return;
        }
        TextView initials = host.ui.titleText(host.ui.initials(name), textColor, textSize);
        initials.setGravity(Gravity.CENTER);
        initials.setBackground(host.ui.round(background, 999));
        holder.addView(initials, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static String cachedImage(MainActivity host, String email) {
        if (email == null || email.isEmpty()) return "";
        String image = SecurePrefs.get(host, host.PREFS + "_profiles")
                .getString("profile_image:" + email, "");
        if (image == null || image.isEmpty()) {
            image = SecurePrefs.get(host, host.PREFS + "_profiles")
                    .getString("profile_image:" + email.toLowerCase(Locale.ROOT), "");
        }
        return image == null ? "" : image;
    }

    private static void cacheImage(MainActivity host, String email, String image) {
        SecurePrefs.get(host, host.PREFS + "_profiles").edit()
                .putString("profile_image:" + email, image)
                .putString("profile_image:" + email.toLowerCase(Locale.ROOT), image)
                .apply();
    }
}
