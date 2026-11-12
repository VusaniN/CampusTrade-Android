# Requirements coverage

This is a practical status map for the CampusTrade build. It records what is implemented, what is partially covered and what is intentionally outside the current scope.

| Area | Status | Notes |
|---|---|---|
| Registration, login, logout, email verification, password reset | Implemented | Firebase Auth, campus domain, duplicate email handling and session persistence |
| Account roles and status | Implemented | Firestore `isAdmin`; suspended/deactivated/deleted accounts blocked at sign-in |
| Profile details and photo | Implemented | Name, course, year and Cloudinary profile photo |
| Public seller profile | Implemented | Active listings, completed-sale count, rating and reviews |
| Account settings | Implemented | Profile photo, change password by reset link, deactivate and soft-delete |
| Create and edit listings | Implemented | Title, description, price, category, condition, location, delivery and negotiable flag |
| Listing media | Implemented | Up to five photos and an optional document, uploaded to Cloudinary |
| Listing status | Implemented | Available, Reserved, Ordered, Sold, Paused and Draft; sold/removed items are hidden |
| Listing lifetime | Implemented | Listings expire after 60 days; sellers can renew from Manage listing |
| Listing metrics | Implemented | View count and favourite count, mirrored to Firestore |
| Browse, search, filter and sort | Implemented | Marketplace is the default landing screen; search, category/price/saved/my-listing filters and three sort orders |
| Favourites | Implemented | Firestore `favourites` collection plus local saved state |
| Product details | Implemented | Full-page detail with large photo/placeholder, seller display name, campus verification, rating, completed-sale count, views and saves; email address is not shown |
| Buyer/seller messaging | Implemented | Firestore chats, unread indicators, Sent/Seen receipts, typing hint and push notifications |
| Transaction lifecycle | Implemented | Requested, Held, Completed and Cancelled with seller approval and cancellation approval |
| Offers and reservations | Implemented | Firestore `offers` sync, buyer/seller responses, 24-hour reservations and cross-device state |
| Pickup scheduling | Implemented | Firestore `pickups` sync for time, public meeting point and both-student confirmation |
| Ratings and reviews | Implemented | One review per student per completed order; profile reads Firestore `ratings`; reviews can be reported |
| Notifications | Implemented | In-app notification centre, notification preferences and FCM pushes |
| Listing/user/review reports | Implemented | Report reasons, details, local queue and Firestore reports; moderators can remove a reported review |
| Blocking | Implemented | Blocked students are removed from Marketplace and Inbox |
| Admin moderation | Implemented | Searchable report queue, hide/restore listings, remove listings, remove reported reviews, named feedback cards and remove students |
| Admin user management | Implemented | Searchable student list; suspend and restore with an `admin_actions` audit entry |
| Admin dashboard KPIs | Partially implemented | Quick on-device counts plus account/report tools; the full Firestore dashboard is in Admin Analytics |
| Admin analytics | Implemented | Firestore users, listings, orders and reports; active-user KPI, chronological charts and date filters |
| Seller dashboard | Implemented | Reads Firestore listings, orders and ratings; includes sales/listing charts and no estimated-profit section, with an offline phone fallback |
| Listing performance charts | Implemented | Custom Canvas bar and line charts |
| Image/file validation and upload | Implemented | File type/size checks, Cloudinary uploads, local previews and retry messages |
| Wanted posts | Implemented | Firestore `wanted` sync across devices, including fulfilled/reopened state |
| Study group posts | Implemented | Group metadata, membership, posts and read state sync through `studyGroups` subcollections |
| Firestore security rules | Implemented | Users, listings, orders, offers, pickups, chats, ratings, reports, groups, wanted, favourites, blocks and admin actions |
| Empty, error and offline states | Partially implemented | Local fallback, loading and student-tone error messages; not every screen has a skeleton loader |
| Read receipts and typing indicators | Implemented | Sent/Seen labels and a six-second typing hint; requires the updated `firestore.rules` |
| Automated payments, payouts and delivery | Not implemented | Cash on collection or EFT arranged directly between students; no card, checkout or payout processing |
| Expiring listings | Implemented | 60-day lifetime, expired listings hidden from the public Market and a Renew action |
| Review reporting | Implemented | Report from a seller's review list; moderators can remove the cloud rating |
| Full text search and pagination | Partially implemented | Local filtering plus 20-listing Firestore pages with Load more |

## Important security note

Marketplace-wide analytics and administrative actions require the moderator's Firestore `users/{uid}` document to have `isAdmin: true`. The debug passcode alone does not grant Firestore admin access.

Deploy `firestore.rules` before testing read receipts, typing hints, listing expiry, review removal, offers, pickups or the updated seller/admin analytics. The rules allow a recipient to set `readAt` on a message, a participant to write `typingUid`/`typingAt`, an owner to write `expiresAt`, an order participant to update a shared pickup, and a moderator to delete a reported rating. Until the rules are deployed, Firestore will reject those writes.
