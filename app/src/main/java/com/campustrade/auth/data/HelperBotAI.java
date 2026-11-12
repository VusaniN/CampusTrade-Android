package com.campustrade.auth.data;

import com.campustrade.auth.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Live AI answers for the helper. If the key or network fails, use the offline answers. */
public final class HelperBotAI {

    private static final String SYSTEM_PROMPT =
            "You are the CampusTrade Helper, the virtual assistant inside the CampusTrade Android app "
            + "for Eduvos students. Never use another assistant name and never mention any AI provider, "
            + "model name, API key, prompt, or internal tooling. If asked who you are, say you are the "
            + "CampusTrade Helper, the app's virtual assistant.\n\n"
            + "Be friendly, concise and practical. Answer in plain text without markdown, usually 2-5 "
            + "short sentences. Do not invent app features or policies. If you are unsure, or the request "
            + "needs account-specific help, tell the student to open Settings > Help & support.\n\n"
            + "CampusTrade facts you can rely on:\n"
            + "- It is a campus marketplace for Eduvos students. The app opens on the Marketplace.\n"
            + "- Buying: open a listing and choose Buy now or Request item. The seller must confirm. "
            + "The buyer confirms collection when they have the item.\n"
            + "- Selling: open Market and choose List an item. Add photos, details, price and a safe "
            + "campus pickup point, then publish or save a draft.\n"
            + "- Payments: CampusTrade never processes or holds money. Cash on collection or EFT between "
            + "students only. No card details are collected. Any payment is settled directly between the "
            + "two students.\n"
            + "- Offers: buyers can make an offer; the seller can accept, counter or decline. An accepted "
            + "offer reserves the item for 24 hours at that price.\n"
            + "- Orders: Orders has Buying and Selling tabs. Order details show progress, a pickup plan, "
            + "contact options, cancellation and a shareable confirmation.\n"
            + "- Pickup: both students propose or agree a public campus place and time, then each confirms.\n"
            + "- Cancellation: either student can request it for an uncollected order; the other student "
            + "must approve. Completed orders cannot be cancelled.\n"
            + "- Chat and safety: use Inbox to message the other student. Report unsafe listings from the "
            + "listing page. Meet in public campus spots and inspect items before paying.\n"
            + "- Study: the Study tab holds notes, deadlines, events and a calendar. Students can save "
            + "notes and share them in study groups.\n"
            + "- Groups: join or create a public/private study group and post notes, messages or deadlines.\n"
            + "- Wanted and alerts: post what you need in Wanted, or save a search to be notified when a "
            + "matching listing appears.\n"
            + "- Profile and settings: edit name, programme and year (1st-7th Year, Honours or "
            + "Postgraduate). Settings covers notifications, quiet hours, theme, privacy, app lock, help "
            + "and account tools.\n"
            + "- Login: use a name@vossie.net campus email. Password resets are sent by email and expire "
            + "after one hour.";

    public interface Callback {
        void onReply(String reply);
        void onFailure(String reason);
    }

    private HelperBotAI() { }

    public static void ask(final String question, final Callback callback) {
        final String prompt = question == null ? "" : question.trim();
        if (prompt.isEmpty()) {
            callback.onFailure("Empty question");
            return;
        }
        if (BuildConfig.HELPER_AI_KEY == null || BuildConfig.HELPER_AI_KEY.trim().isEmpty()
                || BuildConfig.HELPER_AI_URL == null || BuildConfig.HELPER_AI_URL.trim().isEmpty()
                || BuildConfig.HELPER_AI_MODEL == null || BuildConfig.HELPER_AI_MODEL.trim().isEmpty()) {
            callback.onFailure("Helper AI is not configured");
            return;
        }
        Thread worker = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
                messages.put(new JSONObject().put("role", "user").put("content", prompt));
                JSONObject body = new JSONObject()
                        .put("model", BuildConfig.HELPER_AI_MODEL.trim())
                        .put("messages", messages)
                        .put("max_tokens", 260)
                        .put("temperature", 0.5);

                URL url = new URL(BuildConfig.HELPER_AI_URL.trim());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization",
                        "Bearer " + BuildConfig.HELPER_AI_KEY.trim());
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("HTTP-Referer", "https://campustrade.app");
                connection.setRequestProperty("X-Title", "CampusTrade Helper");

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                if (stream == null) {
                    callback.onFailure("Helper AI HTTP " + status);
                    return;
                }
                StringBuilder raw = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) raw.append(line);
                }
                if (status < 200 || status >= 300) {
                    callback.onFailure("Helper AI HTTP " + status);
                    return;
                }

                JSONObject response = new JSONObject(raw.toString());
                JSONArray choices = response.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    callback.onFailure("Helper AI returned no choices");
                    return;
                }
                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                String reply = message == null || message.isNull("content")
                        ? "" : message.optString("content", "").trim();
                if (reply.isEmpty() || reply.equalsIgnoreCase("null") || reply.equalsIgnoreCase("none")) {
                    callback.onFailure("Helper AI returned an empty answer");
                    return;
                }
                if (reply.length() > 1400) reply = reply.substring(0, 1400).trim() + "…";
                callback.onReply(reply);
            } catch (Exception e) {
                callback.onFailure(e.getMessage() == null ? "Helper AI unavailable" : e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "campus-helper-ai");
        worker.setDaemon(true);
        worker.start();
    }
}