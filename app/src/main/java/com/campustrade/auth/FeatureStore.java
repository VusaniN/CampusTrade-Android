package com.campustrade.auth;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Collection;

// Save community data as JSON when Firebase is offline.
public final class FeatureStore {
    public interface Action { void apply(CampusFeatures features); }
    private final SharedPreferences prefs;
    private boolean readable = true;
    public CampusFeatures data;

    // Load saved JSON; a broken file becomes empty.
    public FeatureStore(Context context) {
        prefs = context.getSharedPreferences("campustrade_community", Context.MODE_PRIVATE);
        try { data = decode(new JSONObject(prefs.getString("records", "{}"))); }
        catch (JSONException e) { readable = false; data = new CampusFeatures(); }
    }

    // Change a copy, save it, then replace the old data only if the save works.
    public void update(Action action) {
        if (!readable) throw new IllegalStateException("Saved community data couldn't be loaded");
        try {
            CampusFeatures candidate = decode(encode(data));
            action.apply(candidate);
            if (!prefs.edit().putString("records", encode(candidate).toString()).commit())
                throw new IllegalStateException("Couldn't save this change. Give it another go");
            data = candidate;
        } catch (JSONException e) { throw new IllegalStateException("Couldn't save community data", e); }
    }

    private static JSONArray array(JSONObject root, String key) { JSONArray a = root.optJSONArray(key); return a == null ? new JSONArray() : a; }
    private static void strings(JSONArray array, Collection<String> target) throws JSONException {
        for (int i = 0; i < array.length(); i++) target.add(array.getString(i));
    }

    // Turn the saved lists into JSON for phone storage.
    static JSONObject encode(CampusFeatures f) throws JSONException {
        JSONObject root = new JSONObject().put("version", 1);
        JSONArray offers = new JSONArray(), pickups = new JSONArray(), reviews = new JSONArray(), groups = new JSONArray(), wanted = new JSONArray(), searches = new JSONArray();
        for (CampusFeatures.Offer o : f.offers) offers.put(new JSONObject().put("id", o.id).put("listing", o.listing).put("buyer", o.buyer)
                .put("seller", o.seller).put("amount", o.amount).put("turn", o.turn).put("state", o.state).put("created", o.created).put("expires", o.expires));
        for (CampusFeatures.Pickup p : f.pickups) pickups.put(new JSONObject().put("order", p.order).put("buyer", p.buyer).put("seller", p.seller)
                .put("location", p.location).put("time", p.time).put("confirmed", new JSONArray(p.confirmed))
                .put("revision", p.revision).put("updatedBy", p.updatedBy == null ? "" : p.updatedBy)
                .put("updatedAt", p.updatedAt));
        for (CampusFeatures.Review r : f.reviews) reviews.put(new JSONObject().put("order", r.order).put("author", r.author).put("target", r.target)
                .put("comment", r.comment).put("communication", r.communication).put("accuracy", r.accuracy).put("punctuality", r.punctuality));
        for (CampusFeatures.Group g : f.groups) {
            JSONArray posts = new JSONArray();
            for (CampusFeatures.GroupPost p : g.posts) posts.put(new JSONObject().put("id", p.id).put("author", p.author)
                    .put("authorName", p.authorName == null ? "" : p.authorName).put("kind", p.kind)
                    .put("body", p.body).put("time", p.time).put("due", p.due).put("readBy", new JSONArray(p.readBy))
                    .put("title", p.title).put("end", p.end).put("location", p.location));
            groups.put(new JSONObject().put("id", g.id).put("owner", g.owner).put("name", g.name).put("module", g.module).put("description", g.description)
                    .put("private", g.privateGroup).put("members", new JSONArray(g.members)).put("requests", new JSONArray(g.requests)).put("posts", posts));
        }
        for (CampusFeatures.Wanted w : f.wanted) wanted.put(new JSONObject().put("id", w.id).put("owner", w.owner)
                .put("ownerName", w.ownerName == null ? "" : w.ownerName).put("title", w.title)
                .put("category", w.category).put("detail", w.detail).put("budget", w.budget).put("created", w.created).put("active", w.active));
        for (CampusFeatures.SearchAlert s : f.searches) searches.put(new JSONObject().put("id", s.id).put("owner", s.owner).put("query", s.query)
                .put("category", s.category).put("budget", s.budget).put("active", s.active).put("seen", new JSONArray(s.seen)));
        return root.put("offers", offers).put("pickups", pickups).put("reviews", reviews).put("groups", groups).put("wanted", wanted).put("searches", searches);
    }

    // Read JSON back into the lists. Missing lists become empty.
    static CampusFeatures decode(JSONObject root) throws JSONException {
        CampusFeatures f = new CampusFeatures();
        JSONArray a = array(root, "offers");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i); CampusFeatures.Offer o = new CampusFeatures.Offer();
            o.id = r.getString("id"); o.listing = r.getString("listing"); o.buyer = r.getString("buyer"); o.seller = r.getString("seller");
            o.amount = r.getString("amount"); o.turn = r.getString("turn"); o.state = r.getString("state"); o.created = r.getLong("created"); o.expires = r.optLong("expires"); f.offers.add(o);
        }
        a = array(root, "pickups");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i); CampusFeatures.Pickup p = new CampusFeatures.Pickup();
            p.order = r.getString("order"); p.buyer = r.getString("buyer"); p.seller = r.getString("seller"); p.location = r.getString("location");
            p.time = r.getLong("time"); strings(array(r, "confirmed"), p.confirmed);
            p.revision = r.optInt("revision", 1);
            p.updatedBy = r.optString("updatedBy", "");
            p.updatedAt = r.optLong("updatedAt", 0);
            f.pickups.add(p);
        }
        a = array(root, "reviews");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i); CampusFeatures.Review review = new CampusFeatures.Review();
            review.order = r.getString("order"); review.author = r.getString("author"); review.target = r.getString("target"); review.comment = r.getString("comment");
            review.communication = r.getInt("communication"); review.accuracy = r.getInt("accuracy"); review.punctuality = r.getInt("punctuality"); f.reviews.add(review);
        }
        a = array(root, "groups");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i); CampusFeatures.Group g = new CampusFeatures.Group();
            g.id = r.getString("id"); g.owner = r.getString("owner"); g.name = r.getString("name"); g.module = r.getString("module");
            g.description = r.getString("description"); g.privateGroup = r.getBoolean("private"); strings(array(r, "members"), g.members); strings(array(r, "requests"), g.requests);
            JSONArray posts = array(r, "posts");
            for (int j = 0; j < posts.length(); j++) {
                JSONObject v = posts.getJSONObject(j); CampusFeatures.GroupPost p = new CampusFeatures.GroupPost();
                p.id = v.getString("id"); p.author = v.getString("author"); p.authorName = v.optString("authorName", "");
                p.kind = v.getString("kind"); p.body = v.getString("body");
                p.time = v.getLong("time"); p.due = v.optLong("due"); strings(array(v, "readBy"), p.readBy); g.posts.add(p);
                p.title = v.optString("title", ""); p.end = v.optLong("end", 0); p.location = v.optString("location", "");
            }
            f.groups.add(g);
        }
        a = array(root, "wanted");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i); CampusFeatures.Wanted w = new CampusFeatures.Wanted();
            w.id = r.getString("id"); w.owner = r.getString("owner"); w.ownerName = r.optString("ownerName", "");
            w.title = r.getString("title"); w.category = r.getString("category");
            w.detail = r.getString("detail"); w.budget = r.getString("budget"); w.created = r.getLong("created"); w.active = r.getBoolean("active"); f.wanted.add(w);
        }
        a = array(root, "searches");
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i); CampusFeatures.SearchAlert s = new CampusFeatures.SearchAlert();
            s.id = r.getString("id"); s.owner = r.getString("owner"); s.query = r.getString("query"); s.category = r.getString("category");
            s.budget = r.getString("budget"); s.active = r.getBoolean("active"); strings(array(r, "seen"), s.seen); f.searches.add(s);
        }
        return f;
    }
}