package com.campustrade.auth.data;

import java.util.Locale;

// Keyword-based helper; answers come from this file.
public final class HelperBot {

    // Uses a dedicated helper address so the normal chat screens can open it.
    public static final String PEER = "helper@campustrade.app";
    public static final String NAME = "CampusTrade Helper";

    private HelperBot() { }

    public static String welcome() {
        // Shown as the first bubble when the helper chat is opened.
        return "Hi! I'm the CampusTrade Helper. Ask me about selling, buying, payments, offers, chats, "
                + "ratings, the Study tab, groups, settings or account access. If you're not sure where "
                + "to start, just say \"help\".";
    }

    
    public static String reply(String question) {
        // Everything is matched in lowercase so "Sell" and "sell" behave the same.
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT).trim();

        // Specific topics come first so "sign in" doesn't hit the general help branch.
        if (hasAny(q, "log out", "logout", "sign out", "signout")) {
            return "Open Profile and scroll to Log out. Your saved listings, chats and study items stay on your account. "
                    + "Use \"Keep me logged in\" only on a device you trust.";
        }
        if (hasAny(q, "sign up", "signup", "signing up", "register", "create an account",
                "create account", "new account", "make an account")) {
            return "On the sign-in screen choose Sign up. Add your full name, @vossie.net email, student number, "
                    + "course, year and a strong password, then check your inbox for the verification link.";
        }
        if (hasAny(q, "password", "forgot", "reset password", "reset my password", "reset link",
                "login", "logging in", "log in", "sign in", "can't sign", "cant sign", "can't sign",
                "can't log", "cant log", "can't log", "unable to sign")) {
            return "Enter your email on the sign-in screen, tap \"Forgot password?\" and we'll email you a reset link - "
                    + "it expires after an hour. Check spam too, and make sure you're using your student email.";
        }
        if (hasAny(q, "verify", "verification")) {
            return "New accounts get a verification email - if it's not in your inbox, check the spam folder. "
                    + "You can still use the app while it is pending.";
        }
        if (hasAny(q, "contact support", "customer support", "support team", "support ticket",
                "from support", "feedback", "complain", "suggestion", "contact you", "contact the team",
                "real person", "human", "agent", "representative")) {
            return "Open Settings -> Help & support to send a message to the campus team. "
                    + "For a listing safety issue, open the listing and tap Report.";
        }
        if (hasAny(q, "chatbot", "helper bot", "campustrade helper", "app helper")) {
            return "I'm the CampusTrade Helper - an automated guide to the app. Ask me how to buy, sell, pay, "
                    + "make an offer, use chats or manage your account.";
        }
        if (hasAny(q, "receipt")) {
            return "Open Orders & transactions, open the order and choose More -> Transaction confirmation. You can share it as text "
                    + "with the other student or keep it for your records.";
        }
        if (hasAny(q, "payout", "sales balance", "seller balance", "get paid", "withdraw funds",
                "withdraw my money", "my sales")) {
            return "Completed sales appear in your Sales balance on the Selling tab. "
                    + "Cash or EFT is arranged directly with the other student. CampusTrade does not hold or pay out money.";
        }
        if (hasAny(q, "offer", "negotiate", "bargain", "lower the price", "counter")) {
            return "On any listing tap Make an offer. The seller sees it in the Offers tab and can accept, counter or decline - "
                    + "an accepted price is used automatically at checkout for 24 hours. You can cancel an open offer from the Offers tab.";
        }
        if (hasAny(q, "cancel", "cancellation", "refund", "return item", "withdraw request", "withdraw my request")) {
            return "Open Orders & transactions, open the order and choose More -> Cancellation choices. Either student can request cancellation "
                    + "of an uncollected order; the other must approve, and completed orders can't be cancelled. "
                    + "Any payment already made is settled directly between students. A buyer can also tap Cancel request while it is still requested.";
        }
        if (hasAny(q, "fee", "fees", "service charge", "estimate")) {
            return "CampusTrade does not charge a platform fee. "
                    + "The order total is the item price; delivery and payment are arranged directly with the other student.";
        }
        if (hasAny(q, "payment", "pay", "card", "eft", "bank", "money")) {
            // Payment answer comes before buy/checkout because both topics overlap.
            return "CampusTrade does not process payments. "
                    + "Choose Cash on collection or EFT between students, then settle directly with the seller. "
                    + "No card or bank details are collected by the app.";
        }
        if (hasAny(q, "buy", "purchase", "checkout", "order", "collect", "collection",
                "request item", "request an item")) {
            return "Open a listing and tap Buy now, choose a payment method and send your request. "
                    + "The seller approves it in Orders & transactions - you'll be notified when they confirm. "
                    + "Free items just need \"Request item\", and you tap Confirm collection at pickup.";
        }
        if (hasAny(q, "delete", "take down", "remove listing", "remove my listing")) {
            return "To remove a listing: open it -> Manage -> Delete listing (this removes it for everyone). "
                    + "For account removal, open Settings -> Help & support and choose Account help.";
        }
        if (hasWord(q, "sell", "list", "selling", "sold")
                || hasAny(q, "listing", "photo", "picture", "image", "upload", "document", "pdf", "docx",
                        "draft", "pause", "renew", "edit listing")) {
            // Match whole words, so "sell" doesn't match "counsellor".
            return "Market tab -> Sell: add up to 5 photos and an optional PDF, Word, PowerPoint or TXT document "
                    + "(10 MB max), plus a title, price and safe pickup spot. Publish it or save a draft. "
                    + "Manage any listing later to edit, pause, renew or mark it sold.";
        }
        if (hasAny(q, "chat", "message", "conversation", "contact", "talk to")) {
            return "Use Chat on a listing or the Inbox tab. Messages sync between devices and unread chats show a blue badge. "
                    + "You're chatting with me - the bot - for quick app questions.";
        }
        if (hasAny(q, "rate", "rating", "review", "star", "reputation")) {
            return "After a completed pickup, open the order in Orders & transactions and leave a star rating. "
                    + "Reviews build the other student's campus reputation.";
        }
        if (hasAny(q, "group", "study partner", "partner")) {
            return "Groups live with the group chats in your Inbox: create one or request to join, and the owner approves you. "
                    + "Group notes and activities appear in your Study calendar once you're a member.";
        }
        if (hasAny(q, "study", "note", "deadline", "calendar", "event", "module")) {
            return "The Study tab holds your notes, deadlines and events with a calendar view. "
                    + "Bookmark notes to find them fast, and share notes inside your groups.";
        }
        if (hasAny(q, "wanted", "alert", "notify me", "looking for")) {
            return "Post what you need in the Wanted tab, and set search alerts so the Market flags new items that match.";
        }
        if (hasAny(q, "notif", "quiet", "push")) {
            return "Manage notification categories and quiet hours under Settings. "
                    + "Payment alerts always come through; chat and study alerts follow your quiet hours.";
        }
        if (hasAny(q, "save", "bookmark", "favourite", "favorite")) {
            return "Tap the bookmark on a listing to save it - saved items are filterable in the Market and in your Profile.";
        }
        if (hasAny(q, "dark", "theme", "appearance", "night", "light mode")) {
            return "Settings -> Dark mode: choose Auto (dark from 18:00), Dark or Light. "
                    + "Your choice is saved per account and applied immediately.";
        }
        if (hasAny(q, "security", "privacy", "screenshot", "app lock", "screen lock") || hasWord(q, "lock")) {
            return "Settings -> Security: App lock uses your device screen lock, and Screen privacy hides previews and blocks screenshots.";
        }
        if (hasAny(q, "report", "scam", "unsafe", "block", "safe", "meet")) {
            return "Use Report on a listing if something looks wrong - campus moderators review it. "
                    + "For a student profile, abusive review or account concern, send a message through Settings -> Help & support. "
                    + "Meet in busy campus spots, check items before paying, and keep chats inside CampusTrade.";
        }
        if (hasAny(q, "account", "profile", "student number", "details")) {
            return "Your Profile shows your name, student number, course and year. Use Edit profile to update your details. "
                    + "For account removal or a login problem, open Settings -> Help & support and choose Account help.";
        }
        if (hasAny(q, "admin", "moderator")) {
            return "Admin tools are for campus moderators. If you spot a problem, report the listing so it reaches the moderation queue.";
        }
        if (hasAny(q, "who are you", "your name", "what are you", "are you a bot")) {
            return "I'm the CampusTrade Helper - a quick guide to the app. I answer from a fixed set of campus FAQs. "
                    + "For a person, open Settings -> Help & support.";
        }
        if (hasAny(q, "not working", "isn't working", "isn't working", "doesn't work", "doesn't work",
                "won't open", "wont open", "can't open", "cant open", "can't open",
                "won't load", "wont load", "keeps closing", "freezes", "not responding",
                "stuck", "bug", "broken", "crash", "error", "something went wrong", "something is wrong")) {
            // No script answer for technical problems, so give restart advice and support.
            return "Try closing and reopening the app, then check your connection and retry. "
                    + "If it keeps happening, open Settings -> Help & support and describe what you were doing.";
        }
        if (hasAny(q, "thank", "appreciate")) {
            return "Anytime! Anything else I can help with?";
        }
        if (hasWord(q, "help", "assist", "assistance", "support")
                || hasAny(q, "what can you do", "how can you help", "how does this work", "how do i use",
                "how to use", "getting started", "get started", "new here", "faq", "guide",
                "i have a question", "confused", "not sure", "don't understand", "dont understand",
                "how do i", "how can i", "where do i")) {
            return "I can help with buying, selling, payments, offers, chats, reviews, Study tools, groups, "
                    + "notifications, dark mode and account access. Tell me what you're trying to do - for example "
                    + "\"How do I sell something?\" or \"How do I cancel an order?\" For a person, open Settings -> Help & support.";
        }
        if (hasWord(q, "hi", "hey", "hello", "yo", "howzit")
                || hasAny(q, "good morning", "good afternoon", "good evening")) {
            return "Hey! What can I help you with? Try \"How do I sell something?\" or \"How do payments work?\"";
        }
        if (hasAny(q, "bye", "goodbye", "see you")) {
            return "Goodbye - happy trading on campus!";
        }
        // Nothing matched: list topics and point to Settings > Help & support.
        return "Sorry, I didn't catch that. I can help with buying, selling, payments, offers, chats, reviews, "
                + "Study tools, groups, notifications, dark mode and account access. Try rephrasing, or open "
                + "Settings -> Help & support to message the campus team.";
    }

    private static boolean hasAny(String q, String... needles) {
        // Simple text check for phrases; good for "log out", bad for "hi".
        for (String needle : needles) {
            if (q.contains(needle)) return true;
        }
        return false;
    }

    private static boolean hasWord(String q, String... words) {
        // Split on non-letters/numbers so short words like "hi" match safely.
        String[] tokens = q.split("[^a-z0-9]+");
        for (String token : tokens) {
            for (String word : words) {
                if (token.equals(word)) return true;
            }
        }
        return false;
    }
}