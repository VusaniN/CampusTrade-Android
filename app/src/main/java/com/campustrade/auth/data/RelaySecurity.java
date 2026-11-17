package com.campustrade.auth.data;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.util.concurrent.TimeUnit;

/** Tokens used by the small external relays. Never embed relay secrets in the APK. */
public final class RelaySecurity {

    private RelaySecurity() { }

    /** Firebase ID token for the signed-in student, or an empty string. */
    public static String firebaseIdToken() {
        try {
            FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return "";
            GetTokenResult result = Tasks.await(user.getIdToken(false), 15, TimeUnit.SECONDS);
            String token = result == null ? "" : result.getToken();
            return token == null ? "" : token.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** App Check token when App Check is configured and enforced. */
    public static String appCheckToken() {
        try {
            AppCheckToken token = Tasks.await(
                    FirebaseAppCheck.getInstance().getAppCheckToken(false), 10, TimeUnit.SECONDS);
            String value = token == null ? "" : token.getToken();
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
