# CampusTrade

CampusTrade is a student marketplace and campus companion built with Java and Firebase. This repository contains the final v1.5.3 Android app, including buying, selling, messaging, orders, study tools, returns and moderation.

## Status

- Version: 1.5.3
- Platform: Android
- Language: Java
- Backend: Firebase Authentication and Cloud Firestore
- Media: Cloudinary for listing photos, documents and profile photos
- Status: release candidate

## Main capabilities

### Accounts and profiles

- Campus email registration, verification, login, logout and password reset
- Persistent sessions and protected screens
- Editable name, course and year
- Profile photo upload with Cloudinary
- A profile photo is required before publishing a listing or checking out; drafts are still allowed
- First-run app tutorial, with the same tour available from Settings → Support → App tutorial
- Profile picture and member information on the profile screen
- Public seller profile with active listings, completed sales, rating and reviews
- Change password by email reset
- Deactivate account and soft-delete account flows
- Suspended or deactivated accounts are blocked at sign-in

### Marketplace

- Marketplace opens as the default landing screen
- Dedicated full-page product details with larger imagery and a neutral no-photo placeholder
- Seller display name, campus verification, rating and completed-sale count shown instead of email addresses
- Create, edit, pause, renew and delete listings
- 60-day listing lifetime with a Renew action
- Multiple listing photos and optional document attachments
- Category, condition, negotiable price, pickup location and delivery option
- Private original-cost record for the seller; not shown in analytics
- Listing statuses: Available, Reserved, Ordered, Sold, Paused, Draft
- Search, category filters, price filters, saved-only and my-listings views
- Sort by newest, lowest price and highest price
- Listing view counts and favourite counts
- Firestore-synced favourites per student
- Sold, removed and paused listings are not shown as available

### Messaging and transactions

- Buyer/seller chat with Firestore persistence and local fallback
- Unread indicators, Sent/Seen receipts, typing hints, conversation filtering and push notifications
- Purchase requests, seller approval, reservations and cancellation requests
- Cross-device negotiated offers with accept, counteroffer, decline and 24-hour reservations
- Cross-device pickup scheduling with a public meeting point and both-student confirmation
- Payment preference limited to cash on collection or EFT arranged directly between students
- No online card, instant-EFT, checkout or payout processing
- Completed-order reviews with one review per student per order
- Completed-order return requests: reason, seller approval or decline, buyer withdrawal and return status
- Return settlements are arranged directly between students; CampusTrade does not process refunds
- Report an abusive or fake review from a seller profile
- Real-time listing, order, offer and pickup updates when screens are open
- Keyless OpenStreetMap delivery tracking, with live positions shared only while the map is open
- Delivery maps and coordinates close permanently after delivery confirmation or cancellation
- Collection proof can be captured by camera or chosen from the gallery; the seller must review it before the order completes

### Study groups

- Searchable public and private study groups, with name/module discovery and Firestore refresh on search
- Private group join requests synced to Firestore for owner approval
- Group notes shared from Home or Study hub, with the group feed opening after sharing
- Members-only group feeds, deadlines and notifications

### Safety and moderation

- Terms & Conditions acceptance required before login, with the full text available in Settings
- Clear liability and safe-exchange terms covering trades and meetings outside the application
- Listing, user and review reports with reasons and details
- Firestore-synced wanted posts and study-group posts
- Block and unblock students; blocked students are hidden from Marketplace and Inbox
- Searchable admin moderation queue and report actions
- Student feedback shown as named cards with category, date and message instead of backend IDs
- Searchable student account list with suspend and restore
- Remove a reported review from the cloud ratings collection
- Admin action audit trail in Firestore

### Seller dashboard

- Listing, active, reserved and sold counts
- Completed sales and recorded sales value
- Pending enquiries and average rating
- Sales value over time
- Listing status chart
- Views and saves chart
- Completed sales by category
- Top listings by views and favourites
- Date filters: all time, 7 days, 30 days and 90 days

### Administrator analytics

- Firestore-based marketplace KPIs, using the same source as the seller dashboard
- Registered and active users
- Sellers and buyers
- Listings, active listings, reserved listings and sold listings
- Completed, pending and cancelled orders
- Recorded sale value, clearly labelled as not platform revenue
- Reports and outstanding reports
- User growth, listings by category, listing status, transaction value and report status charts
- Recent users, listings and orders
- Date filters: all time, 7 days, 30 days and 90 days

## Firestore collections

- `users` - private account data, role, status, profile image and last active time
- `publicProfiles` - public name, course, year, rating and profile image
- `listings` - marketplace records, status, views, favourites and listing details
- `favourites` - one document per student and listing
- `orders` - purchase requests and transaction lifecycle
- `offers` - negotiated offers and 24-hour reservations
- `pickups` - shared pickup time, place and both-student confirmation
- `chats` - conversation headers and message subcollections with read receipts/typing
- `ratings` - completed-order reviews; moderators can remove reported ones
- `reports` - listing, user and review reports
- `admin_actions` - moderator audit log
- `studyGroups` - group metadata, membership and post subcollections
- `wanted`, `searchAlerts` - community features
- `blockedUsers` - per-student block list
- `feedback` - help and support messages

## Administrator access

Administrator analytics and moderation use a trusted Firestore role:

1. In Firestore, open `users/{uid}` for the moderator account.
2. Add `isAdmin: true`.
3. Sign in again.

The debug passcode remains available as an extra local gate for demo builds, but Firestore rules still require `isAdmin` for marketplace-wide analytics and admin actions.

## Build

1. Copy `local.properties.example` to `local.properties` and set your Android SDK path.
2. Optionally add `CAMPUSTRADE_HELPER_KEY`, `CAMPUSTRADE_HELPER_URL` and `CAMPUSTRADE_HELPER_MODEL` for live helper answers.
3. Build the debug APK:

```bat
gradlew.bat assembleDebug -PcampustradeAdminPasscode=YOUR_ADMIN_PASSCODE_HERE
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

4. Build the release APK:

```bat
gradlew.bat assembleRelease
```

The unsigned release APK is generated at `app/build/outputs/apk/release/app-release-unsigned.apk`; sign it before distribution. Use JDK 17-21 for Gradle builds. Android lint cannot parse JDK 25 and fails with a `25.0.3` version error.

`app/google-services.json` is included so the demo Firebase project can be used directly. Replace it with your own Firebase Android config for a separate deployment.

## Firestore rules

Deploy the rules in `firestore.rules` before testing favourites, account status changes, admin actions, admin analytics, listing expiry, review removal, read receipts, offers, pickups or typing hints:

```bat
firebase deploy --only firestore:rules
```

## Scope notes

- No card gateway, automated payout or delivery integration is included.
- Recorded sale values come from order records and are not verified financial statements. Cost entry and profit estimates have been removed from the app.
- Charts are drawn with a small custom Canvas view, so no chart dependency was added.
