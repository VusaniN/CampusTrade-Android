package com.campustrade.auth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;

// Rules for offers, pickups, reviews, groups, wanted posts and searches.
public final class CampusFeatures {
    public final ArrayList<Offer> offers = new ArrayList<>();
    public final ArrayList<Pickup> pickups = new ArrayList<>();
    public final ArrayList<Review> reviews = new ArrayList<>();
    public final ArrayList<Group> groups = new ArrayList<>();
    public final ArrayList<Wanted> wanted = new ArrayList<>();
    public final ArrayList<SearchAlert> searches = new ArrayList<>();

    public static String id() { return UUID.randomUUID().toString(); }
    public static String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    public static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
    public static String money(String value, boolean allowZero) {
        BigDecimal price = new BigDecimal(value.trim()).setScale(2, RoundingMode.UNNECESSARY);
        require(price.signum() >= (allowZero ? 0 : 1), "Enter a valid amount");
        require(price.compareTo(new BigDecimal("9999999.99")) <= 0, "Amount is too large");
        return price.toPlainString();
    }

    // New offer: no self-offers and no duplicate open offers.
    public Offer offer(String listing, String buyer, String seller, String amount, long now) {
        buyer = email(buyer); seller = email(seller);
        require(!buyer.equals(seller), "You can't make an offer on your own listing");
        for (Offer o : offers) require(!(o.listing.equals(listing) && o.buyer.equals(buyer) && o.pending()), "You already have an open offer");
        Offer o = new Offer(); o.id = id(); o.listing = listing; o.buyer = buyer; o.seller = seller;
        o.amount = money(amount, false); o.turn = seller; o.state = "Pending"; o.created = now;
        offers.add(o); return o;
    }
    public Offer findOffer(String id) {
        for (Offer o : offers) if (o.id.equals(id)) return o;
        throw new IllegalArgumentException("Offer no longer exists");
    }
    public Offer reservation(String listing, long now) {
        for (Offer o : offers) if (o.listing.equals(listing) && o.state.equals("Accepted") && o.expires > now) return o;
        return null;
    }
    // Accept, decline or counter. Accepted offers reserve the item for 24 hours.
    public void respond(String id, String actor, String action, String amount, long now) {
        Offer o = findOffer(id); actor = email(actor);
        require(o.pending() && o.turn.equals(actor), "This offer isn't awaiting your response");
        if (action.equals("Counter")) {
            o.amount = money(amount, false); o.turn = actor.equals(o.buyer) ? o.seller : o.buyer; o.state = "Countered";
        } else if (action.equals("Accept")) {
            require(reservation(o.listing, now) == null, "This item is already reserved");
            o.state = "Accepted"; o.expires = now + 24L * 60 * 60 * 1000;
        } else if (action.equals("Decline")) o.state = "Declined";
        else throw new IllegalArgumentException("Unknown offer action");
    }
    // Either side can cancel an offer while it is still open or reserved.
    public void cancelOffer(String id, String actor) {
        Offer o = findOffer(id); actor = email(actor);
        require(actor.equals(o.buyer) || actor.equals(o.seller), "This isn't your offer");
        require(o.pending() || o.state.equals("Accepted"), "This offer is already closed"); o.state = "Cancelled";
    }
    // Close offers when a listing is sold or removed.
    public void closeOffers(String listing) {
        for (Offer o : offers) if (o.listing.equals(listing) && (o.pending() || o.state.equals("Accepted"))) o.state = "Closed";
    }

    // Set a public pickup point; the other student still confirms.
    public Pickup schedule(String order, String buyer, String seller, String actor, String location, long time, long now) {
        buyer = email(buyer); seller = email(seller); actor = email(actor);
        require(actor.equals(buyer) || actor.equals(seller), "This isn't your order");
        require(location.trim().length() >= 3 && time > now, "Choose a future time and public pickup location");
        Pickup p = pickup(order);
        if (p == null) { p = new Pickup(); p.order = order; pickups.add(p); }
        p.buyer = buyer; p.seller = seller; p.location = location.trim(); p.time = time;
        p.confirmed.clear(); p.confirmed.add(actor); return p;
    }
    public Pickup pickup(String order) { for (Pickup p : pickups) if (p.order.equals(order)) return p; return null; }
    // Both students must confirm before the pickup is complete.
    public void confirmPickup(String order, String actor) {
        Pickup p = pickup(order); actor = email(actor);
        require(p != null && (actor.equals(p.buyer) || actor.equals(p.seller)), "This isn't your pickup");
        p.confirmed.add(actor);
    }
    // Only the buyer and seller can review, once per order.
    public Review review(String order, String buyer, String seller, boolean completed, String actor,
            int communication, int accuracy, int punctuality, String comment) {
        buyer = email(buyer); seller = email(seller); actor = email(actor);
        require(completed && (actor.equals(buyer) || actor.equals(seller)), "Only participants in completed orders can review");
        for (Review r : reviews) require(!(r.order.equals(order) && r.author.equals(actor)), "You already reviewed this order");
        require(communication >= 1 && communication <= 5 && accuracy >= 1 && accuracy <= 5 && punctuality >= 1 && punctuality <= 5, "Ratings must be between 1 and 5");
        Review r = new Review(); r.order = order; r.author = actor; r.target = actor.equals(buyer) ? seller : buyer;
        r.communication = communication; r.accuracy = accuracy; r.punctuality = punctuality; r.comment = comment.trim(); reviews.add(r); return r;
    }
    // Turn the three ratings into one average.
    public double rating(String target) {
        double total = 0; int count = 0;
        for (Review r : reviews) if (r.target.equals(email(target))) { total += (r.communication + r.accuracy + r.punctuality) / 3.0; count++; }
        return count == 0 ? 0 : total / count;
    }

    // Creates a group and makes the creator the first member.
    public Group createGroup(String actor, String name, String module, String description, boolean privateGroup) {
        require(name.trim().length() >= 3 && module.trim().length() >= 2, "Enter a group name and module");
        Group g = new Group(); g.id = id(); g.owner = email(actor); g.name = name.trim(); g.module = module.trim();
        g.description = description.trim(); g.privateGroup = privateGroup; g.members.add(g.owner); groups.add(g); return g;
    }
    public Group group(String id) { for (Group g : groups) if (g.id.equals(id)) return g; throw new IllegalArgumentException("Group no longer exists"); }
    // Public groups join straight away; private groups need owner approval.
    public void join(String id, String actor) {
        Group g = group(id); actor = email(actor);
        if (g.privateGroup && !g.members.contains(actor)) g.requests.add(actor); else g.members.add(actor);
    }
    // Only the owner can approve a request to join a private group.
    public void approve(String id, String actor, String member) {
        Group g = group(id); member = email(member);
        require(g.owner.equals(email(actor)) && g.requests.contains(member), "Only the owner can approve a requested membership");
        g.requests.remove(member); g.members.add(member);
    }
    // A normal member can leave, but the owner can't leave their own group.
    public void leave(String id, String actor) {
        Group g = group(id); actor = email(actor); require(!g.owner.equals(actor), "The group owner can't leave");
        g.members.remove(actor); g.requests.remove(actor);
    }
    // Only members can post. Deadlines and events also need a future date.
    public GroupPost post(String id, String actor, String kind, String body, long due, long now) {
        Group g = group(id); actor = email(actor);
        require(g.members.contains(actor), "Join this group to access its content");
        require(body.trim().length() > 0 && body.length() <= 4000, "Enter content of up to 4000 characters");
        require(kind.equals("Message") || kind.equals("Note") || kind.equals("Deadline") || kind.equals("Event"), "Unknown group content");
        if (kind.equals("Deadline") || kind.equals("Event")) require(due > now, "Choose a future date and time");
        GroupPost p = new GroupPost(); p.id = id(); p.author = actor; p.kind = kind; p.body = body.trim(); p.time = now; p.due = due;
        p.readBy.add(actor); g.posts.add(p); return p;
    }
    // Add the extra fields for events and deadlines.
    public GroupPost postActivity(String id, String actor, String kind, String title, String body, long start, long end, String location, long now) {
        require(kind.equals("Note") || kind.equals("Deadline") || kind.equals("Event"), "Choose a note, event or deadline");
        require(!title.trim().isEmpty(), "Enter a title");
        require(!kind.equals("Event") || end == 0 || end > start, "Event end must be after its start");
        GroupPost post = post(id, actor, kind, title.trim() + (body.trim().isEmpty() ? "" : "\n" + body.trim()), start, now);
        post.title = title.trim(); post.end = kind.equals("Event") ? end : 0; post.location = kind.equals("Event") ? location.trim() : "";
        return post;
    }

    // A wanted post is a request for an item the student is looking for.
    public Wanted want(String actor, String title, String category, String detail, String budget, long now) {
        require(title.trim().length() >= 3, "Enter an item name");
        Wanted w = new Wanted(); w.id = id(); w.owner = email(actor); w.title = title.trim(); w.category = category;
        w.detail = detail.trim(); w.budget = money(budget, true); w.created = now; w.active = true; wanted.add(w); return w;
    }
    // Save a search and alert the student when a match appears.
    public SearchAlert watch(String actor, String query, String category, String budget) {
        require(!query.trim().isEmpty() || !category.equals("All"), "Choose a keyword or category");
        SearchAlert s = new SearchAlert(); s.id = id(); s.owner = email(actor); s.query = query.trim(); s.category = category;
        s.budget = budget.trim().isEmpty() ? "" : money(budget, true); s.active = true; searches.add(s); return s;
    }
    // Check a listing against a saved search. Budget is optional.
    public static boolean matches(SearchAlert alert, String title, String description, String category, String price) {
        return alert.active && (alert.category.equals("All") || alert.category.equals(category))
                && (title + " " + description).toLowerCase(Locale.ROOT).contains(alert.query.toLowerCase(Locale.ROOT))
                && (alert.budget.isEmpty() || new BigDecimal(price).compareTo(new BigDecimal(alert.budget)) <= 0);
    }

    // One offer on a listing. The turn value shows who responds next.
    public static final class Offer {
        public String id, listing, buyer, seller, amount, turn, state;
        public long created, expires;
        public boolean pending() { return state.equals("Pending") || state.equals("Countered"); }
    }
    // A pickup is agreed only when both students confirm.
    public static final class Pickup {
        public String order, buyer, seller, location; public long time;
        public final LinkedHashSet<String> confirmed = new LinkedHashSet<>();
        public boolean agreed() { return confirmed.contains(buyer) && confirmed.contains(seller); }
    }
    public static final class Review { public String order, author, target, comment; public int communication, accuracy, punctuality; }
    public static final class Group {
        public String id, owner, name, module, description; public boolean privateGroup;
        public final LinkedHashSet<String> members = new LinkedHashSet<>(), requests = new LinkedHashSet<>();
        public final ArrayList<GroupPost> posts = new ArrayList<>();
    }
    public static final class GroupPost {
        public String id, author, authorName = "", kind, body; public long time, due;
        public String title = "", location = ""; public long end;
        public final LinkedHashSet<String> readBy = new LinkedHashSet<>();
    }
    public static final class Wanted { public String id, owner, ownerName = "", title, category, detail, budget; public long created; public boolean active; }
    public static final class SearchAlert {
        public String id, owner, query, category, budget; public boolean active;
        public final LinkedHashSet<String> seen = new LinkedHashSet<>();
    }
}