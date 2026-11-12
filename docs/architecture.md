# CampusTrade architecture overview

CampusTrade keeps the single-activity Android architecture from CampusTrade. `MainActivity` is the composition root, lifecycle shell and shared-state owner. Feature classes receive a `MainActivity host` reference and build their screens.

## Main components

- `MainActivity` - navigation, session state, shared lists and screen switching
- `MainUI` - shared widgets, dialogs and styling
- `Auth` / `AuthRepository` - Firebase Authentication, profiles, account status and roles
- `Market` / `ListingRepository` - listings, images, documents, favourites, views and offers
- `Order` - purchase requests, order lifecycle, cancelations, receipts and reviews
- `Inbox` / `ChatRepository` - conversations, message persistence and notifications
- `Notification` / `CampusTradeMessagingService` - in-app alerts and FCM
- `Community` - study groups, wanted posts and search alerts
- `Profile` - public profile, settings, blocked students, account controls and seller profile
- `Admin` - reports, moderation, user account management and admin tools
- `SellerDashboard` - seller KPIs, sales/listing charts and listing performance
- `AdminAnalytics` - marketplace-wide Firestore analytics and charts
- `AnalyticsChart` - custom Canvas bar/line chart view
- `CampusSafety` - local blocking and pickup-code safety support
- `CloudSync` - Cloudinary uploads, cached names and local id mapping
- `FeatureStore` - local JSON persistence for community tools

## Data flow

1. The user registers or signs in with a verified `@vossie.net` account.
2. `MainActivity` restores the session only when the Firebase user still exists.
3. Marketplace listings are stored locally and mirrored to Firestore.
4. Favourites are stored in the `favourites` collection and merged into local listings on sign-in.
5. Listing views and favourite counts are incremented locally and mirrored to Firestore. Listings carry a 60-day `expiresAt`; renewing extends it.
6. Orders and chats use Firestore listeners while the app is open, with local copies for offline use. Opening a chat writes `readAt` on the other student's messages and typing writes a short `typingUid`/`typingAt` hint.
7. Seller analytics query the seller's Firestore listings, orders and `ratings`, with the saved phone copy as an offline fallback. Cost entry and profit estimates are not part of the app.
8. Offers and pickups sync through the `offers` and `pickups` collections so both students see the same negotiation and meeting plan.
9. Wanted posts sync through `wanted`; study group metadata, membership and posts sync through `studyGroups` and its `posts` subcollection.
10. Admin analytics query Firestore users, listings, orders and reports and require `users/{uid}.isAdmin == true`.
11. Reviews are stored in `ratings`; a reported review appears in `reports` and a moderator can delete the rating.
12. Firestore rules enforce ownership, participant access, admin role and status changes.

## Security model

- Firebase Authentication verifies the campus email.
- Firestore rules restrict private profiles, chats, orders and favourites.
- Admin analytics and admin actions require the Firestore `isAdmin` role.
- Suspended, deactivated and deleted accounts are rejected at sign-in.
- The debug admin passcode is only an extra local gate for development builds.

## Build and verification

- Admin access uses `campustradeAdminPasscode` from Gradle properties when the debug passcode path is used.
- Builds without an admin passcode keep the debug moderator tools locked.
- GitHub Actions runs the Gradle checks and assembles the debug APK.
