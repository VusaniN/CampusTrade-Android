package com.campustrade.auth;

import android.app.Activity;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.content.res.ColorStateList;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;

import com.campustrade.auth.data.AuthRepository;
import com.campustrade.auth.data.CloudSync;
import com.campustrade.auth.data.HelperBot;

// App shell: shared state, theme, tabs and screens.
public class MainActivity extends Activity {
    static final String STUDENT_EMAIL_DOMAIN = "vossie.net";
    static final String PREFS = "campustrade_prefs";
    static final int NOTIFICATION_PERMISSION_REQUEST = 42;

    // Brand red is used throughout the app.
    int primary = Color.rgb(226, 58, 29);
    int blue = Color.rgb(226, 58, 29);
    int error = Color.rgb(180, 40, 40);
    int page = Color.rgb(247, 245, 240);
    int text = Color.rgb(25, 25, 24);
    int muted = Color.rgb(114, 114, 109);
    int border = Color.rgb(192, 190, 182);
    int line = Color.rgb(220, 217, 208);
    int green = Color.rgb(42, 111, 83);
    boolean nightActive = false;

    // Set the colours before building the next screen.
    /** Apply the dark-mode colours. */
    void applyTheme() {
        nightActive = shouldNight();
        if (nightActive) {
            page = Color.rgb(18, 18, 20);
            text = Color.rgb(236, 236, 236);
            muted = Color.rgb(168, 168, 168);
            border = Color.rgb(58, 58, 62);
            line = Color.rgb(44, 44, 48);
            primary = Color.rgb(226, 58, 29);
            blue = Color.rgb(226, 58, 29);
            error = Color.rgb(255, 125, 146);
        } else {
            page = Color.rgb(247, 245, 240);
            text = Color.rgb(25, 25, 24);
            muted = Color.rgb(114, 114, 109);
            border = Color.rgb(192, 190, 182);
            line = Color.rgb(220, 217, 208);
            primary = Color.rgb(226, 58, 29);
            blue = Color.rgb(226, 58, 29);
            error = Color.rgb(180, 40, 40);
        }
    }

    /** Dark mode follows the student's choice or the clock. */
    private boolean shouldNight() {
        String mode = settingsPrefs().getString("theme_mode", "Auto");
        if ("Dark".equals(mode)) {
            return true;
        }
        if ("Light".equals(mode)) {
            return false;
        }
        // Auto mode uses 18:00 to 06:00 as night.
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return hour >= 18 || hour < 6;
    }

    String themeModeLabel() {
        return settingsPrefs().getString("theme_mode", "Auto");
    }

    void showThemePicker() {
        String[] modes = {"Auto", "Dark", "Light"};
        String current = settingsPrefs().getString("theme_mode", "Auto");
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(current)) checked = i;
        }
        ui.dialog().setTitle("Dark mode")
                .setSingleChoiceItems(modes, checked, (d, which) -> {
                    settingsPrefs().edit().putString("theme_mode", modes[which]).apply();
                    d.dismiss();
                    applyTheme();
                    applyWindowTheme();
                    showAppScreen("Settings");
                }).show();
    }

    // Keep status and navigation bar icons readable.
    void applyWindowTheme() {
        getWindow().setStatusBarColor(page);
        getWindow().setNavigationBarColor(page);
        int flags = 0;
        if (!nightActive) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /** Shared card style. */
    int surface() {
        return nightActive ? Color.rgb(30, 30, 34) : Color.WHITE;
    }

    // Lighten accents in dark mode.
    int readableInk(int color) {
        if (!nightActive) return color;
        if (color == green) return Color.rgb(105, 215, 167);
        if (color == Color.rgb(217, 139, 0)) return Color.rgb(244, 184, 74);
        return color;
    }

    boolean signupMode = false;
    boolean passwordVisible = false;
    boolean confirmPasswordVisible = false;
    String currentStudentEmail = "";
    String currentStudentName = "Eduvos Student";
    String currentUid = "";
    String currentTab = "Home";
    String selectedCourse = "";
    String selectedYear = "";
    
    static final String[] COURSES = {
            "BSc Information Technology",
            "Diploma in Business Management",
            "BCom Accounting",
            "Diploma in Human Resource Management",
            "Diploma in Marketing Management",
            "BSc Computer Science",
            "Higher Certificate in IT Support",
            "Other"
    };
    static final String[] YEARS = {
            "1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year", "6th Year", "7th Year",
            "Honours", "Postgraduate"
    };
    static final String[] CATEGORIES = {
            "Books", "Technology", "Stationery", "Other"
    };
    String marketFilter = "All";
    String marketCategory = "All";
    String marketMinPrice = "";
    String marketMaxPrice = "";
    String marketQuery = "";
    String marketSort = "Newest";
    boolean marketGrid = true;
    String inboxFilter = "All";
    boolean inboxUnreadOnly = false;
    String inboxQuery = "";
    private static final int UNLOCK_REQUEST = 72;
    private static final int SENSITIVE_ACTION_REQUEST = 78;
    private static final long SENSITIVE_ACTION_VALID_MS = 15L * 60 * 1000;
    private boolean needsUnlock = false;
    private boolean unlocking = false;
    private Runnable pendingSensitiveAction;
    private long sensitiveVerifiedAt = 0;
    boolean editingFromSettings = false;
    String academicFilter = "All";
    long academicCalendarDate = System.currentTimeMillis();
    Dialog academicCalendarDialog;
    String notificationsReturnTab = "Home";
    String paymentsReturnTab = "Market";
    String groupsReturnTab = "Inbox";
    boolean buyingOrders = true;
    MainUI ui;
    Home home;
    Community community;
    Auth auth;
    Market market;
    Profile profile;
    Notification notification;
    Inbox inbox;
    Academics academics;
    Order order;
    Admin admin;
    SellerDashboard sellerDashboard;
    AdminAnalytics adminAnalytics;
    boolean adminAccount = false;
    String paymentStatusFilter = "All";
    String paymentQuery = "";
    String adminReportQuery = "";
    boolean pullingOrders = false;
    boolean ordersPullQueued = false;
    boolean ordersPulledChanged = false;
    boolean adminAccess = false;
    boolean adminOnlySession = false;
    static final String ADMIN_PASSCODE = BuildConfig.ADMIN_PASSCODE;
    // Live chat listeners; push notifications come later.
    private com.google.firebase.firestore.ListenerRegistration chatsBuyerListener;
    private com.google.firebase.firestore.ListenerRegistration chatsSellerListener;
    private com.google.firebase.firestore.ListenerRegistration ordersBuyerListener;
    private com.google.firebase.firestore.ListenerRegistration ordersSellerListener;
    final java.util.List<com.google.firebase.firestore.ListenerRegistration> messageListeners = new java.util.ArrayList<>();
    final java.util.Set<String> listenedChatIds = new java.util.HashSet<>();
    String openChatPeer = "";
    LinearLayout openChatContainer;
    ScrollView openChatScroll;
    Dialog openChatDialog;
    boolean adminLast24Hours = true;
    boolean showClosedReports = false;
    final ArrayList<ModerationReport> moderationReports = new ArrayList<>();

    TextView heroTitle;
    TextView heroSubtitle;
    TextView loginTab;
    TextView signupTab;
    TextView adminTab;
    LinearLayout adminGroup;
    EditText adminPasscodeInput;
    LinearLayout authEmailGroup;
    LinearLayout authPasswordGroup;
    TextView authNotice;
    EditText fullNameInput;
    EditText emailInput;
    EditText studentNumberInput;
    EditText passwordInput;
    EditText confirmPasswordInput;
    TextView passwordToggle;
    TextView confirmPasswordToggle;
    TextView passwordStrengthText;
    CheckBox keepLoggedInCheck;
    CheckBox termsCheck;
    Button submitButton;
    LinearLayout signupFields;
    LinearLayout studentNumberGroup;
    LinearLayout confirmPasswordGroup;
    LinearLayout loginOptions;
    LinearLayout signupTerms;
    LinearLayout authFooter;
    TextView switchPrompt;

    LinearLayout listingsContainer;
    final ArrayList<Product> products = new ArrayList<>();
    final ArrayList<TransactionRecord> transactions = new ArrayList<>();
    int savedListings = 0;
    int messageCount = 0;
    static final int LISTING_PHOTO_REQUEST = 71;
    static final int LISTING_DOCUMENT_REQUEST = 73;
    static final int PROFILE_PHOTO_REQUEST = 74;
    static final int COLLECTION_CAMERA_REQUEST = 76;
    static final int LOCATION_PERMISSION_REQUEST = 83;
    String pendingCollectionOrderId = "";
    String notificationFilter = "All";
    String adminReportFilter = "All";
    static final String[] DOCUMENT_MIME_TYPES = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/rtf",
            "application/vnd.oasis.opendocument.text"
    };
    final ArrayList<Uri> draftPhotos = new ArrayList<>();
    Uri draftDocumentUri;
    String draftDocumentUrl = "";
    String draftDocumentName = "";
    String draftDocumentMime = "";
    long draftDocumentSize = 0;
    final ExecutorService imageLoader = Executors.newSingleThreadExecutor();
    LinearLayout listingPhotoContainer;
    LinearLayout listingDocumentContainer;
    TextView listingPhotoCount;
    Dialog listingDialog;
    final ArrayList<ChatMessage> chatMessages = new ArrayList<>();
    FeatureStore featureStore;
    CampusSafety safety;
    boolean authSubmitting = false;
    boolean cloudRetryVisible = false;

    // Build screens, restore the session, then open the right tab.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new MainUI(this);
        applyTheme();
        applyWindowTheme();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        featureStore = new FeatureStore(this);
        safety = new CampusSafety(this);
        home = new Home(this);
        community = new Community(this);
        auth = new Auth(this);
        market = new Market(this);
        profile = new Profile(this);
        notification = new Notification(this);
        inbox = new Inbox(this);
        academics = new Academics(this);
        order = new Order(this);
        inbox.loadMessages();
        order.loadTransactions();
        market.loadProducts();
        order.reconcileListingStates();
        loadModerationReports();
        admin = new Admin(this);
        sellerDashboard = new SellerDashboard(this);
        adminAnalytics = new AdminAnalytics(this);
        notification.createNotificationChannel();
        notification.requestNotificationPermission();
        SharedPreferences session = com.campustrade.auth.data.SecurePrefs.get(this, PREFS);
        String savedEmail = session.getString("student_email", "");
        String savedUid = session.getString("uid", "");
        String savedTab = savedInstanceState != null
                ? savedInstanceState.getString("tab", "Market") : "Market";
        if (savedTab.equals("Admin") || savedTab.equals("AdminReports") || savedTab.equals("AdminAnalytics")) savedTab = "Profile";
        boolean savedSession = session.getBoolean("signed_in", false) && !savedUid.isEmpty();
        // A saved session opens the app, but the lock still asks for the PIN.
        if (savedSession) {
            currentStudentEmail = savedEmail;
            currentUid = savedUid;
            currentStudentName = com.campustrade.auth.data.SecurePrefs.get(this, PREFS + "_profiles").getString("full_name:" + savedEmail, "Student");
            CloudSync.directoryPut(this, currentUid, currentStudentEmail);
            refreshAdminRole();
            new AuthRepository().touchLastActive(currentUid);
            market.syncFavourites();
            market.cloudMergeMarket();
            safety.sync(currentStudentEmail, currentUid);
            needsUnlock = settingsPrefs().getBoolean("app_lock", false); showAppScreen(savedTab);
            startChatListeners();
            community.syncCloudSearchAlerts();
            order.retryPendingOrderPushes();
            verifyFirebaseSessionAsync();
            handleNotificationIntent(getIntent());
        } else {
            if (new AuthRepository().currentUid() == null) session.edit().putBoolean("signed_in", false).apply();
            auth.showAuthScreen();
        }
    }

    // Handle the notification permission and update the push toggle.
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, granted ? "CampusTrade notifications enabled." : "Notifications are off for now.", Toast.LENGTH_SHORT).show();
            if (currentTab.equals("Settings") || currentTab.equals("Notifications")) {
                settingsPrefs().edit().putBoolean("push", granted).apply();
                showAppScreen(currentTab);
            }
        }
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            DeliveryMap.onPermissionResult(this, granted);
        }
    }

    private boolean isAdminTab(String tab) {
        return tab.equals("Admin") || tab.equals("AdminReports") || tab.equals("AdminAnalytics");
    }

    // Rebuild the current tab so no old views remain.
    void showAppScreen(String tab) {
        boolean enteringAdmin = isAdminTab(tab) && !isAdminTab(currentTab);
        if (enteringAdmin && admin.canUseAdmin()
                && System.currentTimeMillis() - sensitiveVerifiedAt > SENSITIVE_ACTION_VALID_MS) {
            confirmSensitiveAction(() -> showAppScreen(tab));
            return;
        }
        if (adminOnlySession) {
            admin.showAdminOnlyScreen(tab.equals("AdminReports") ? "AdminReports"
                    : tab.equals("AdminAnalytics") ? "AdminAnalytics" : "Admin");
            return;
        }
        if ((tab.equals("Admin") || tab.equals("AdminReports") || tab.equals("AdminAnalytics"))
                && !admin.canUseAdmin()) {
            Toast.makeText(this, "Admin access is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tab.equals("Groups") && !currentTab.equals("Groups")) groupsReturnTab = currentTab;
        currentTab = tab;
        boolean secure = settingsPrefs().getBoolean("screen_privacy", false)
                || isAdminTab(tab) || tab.equals("Payments");
        if (secure) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(page);
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(page);
        topBar.addView(appHeader(headerTitle(tab), headerSubtitle(tab), headerAction(tab)),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View headerRule = new View(this);
        headerRule.setBackgroundColor(line);
        topBar.addView(headerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)));
        screen.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(28), ui.dp(12), ui.dp(28), ui.dp(28));
        scroll.addView(content);
        screen.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Let the screen fill the content.
        if (tab.equals("Home")) home.buildHome(content);
        else if (tab.equals("Academics")) academics.buildAcademics(content);
        else if (tab.equals("Market")) market.buildMarket(content);
        else if (tab.equals("Inbox")) inbox.buildInbox(content);
        else if (tab.equals("Settings")) profile.buildSettings(content);
        else if (tab.equals("Notifications")) notification.buildNotifications(content);
        else if (tab.equals("Payments")) order.buildPayments(content);
        else if (tab.equals("Admin")) admin.buildAdmin(content);
        else if (tab.equals("AdminReports")) admin.buildAdminReports(content);
        else if (tab.equals("AdminAnalytics")) adminAnalytics.build(content);
        else if (tab.equals("SellerDashboard")) sellerDashboard.build(content);
        else if (tab.equals("Groups")) community.buildGroups(content);
        else if (tab.equals("Wanted")) community.buildWanted(content);
        else if (tab.equals("Offers")) market.buildOffers(content);
        else if (tab.equals("SearchAlerts")) community.buildSearchAlerts(content);
        else profile.buildProfile(content);

        if (tab.equals("Market")) {
            LinearLayout footer = new LinearLayout(this);
            footer.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
            Button list = ui.primaryButton("+  List an item for sale");
            list.setOnClickListener(v -> market.showAddProductDialog());
            footer.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)));
            screen.addView(footer);
        }
        screen.addView(bottomNavigation());
        topBar.setElevation(ui.dp(6));
        setContentView(screen);
    }

    /** Require a recent screen-lock check or password before a destructive action. */
    void confirmSensitiveAction(Runnable action) {
        if (action == null) return;
        if (System.currentTimeMillis() - sensitiveVerifiedAt <= SENSITIVE_ACTION_VALID_MS) {
            action.run();
            return;
        }
        KeyguardManager guard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Intent confirm = guard == null || !guard.isDeviceSecure() ? null
                : guard.createConfirmDeviceCredentialIntent("Confirm CampusTrade action",
                        "Use your phone's screen lock to continue");
        if (confirm != null) {
            pendingSensitiveAction = () -> {
                sensitiveVerifiedAt = System.currentTimeMillis();
                action.run();
            };
            try {
                startActivityForResult(confirm, SENSITIVE_ACTION_REQUEST);
                return;
            } catch (Exception ignored) {
                pendingSensitiveAction = null;
            }
        }
        promptPasswordForSensitiveAction(action);
    }

    private void promptPasswordForSensitiveAction(Runnable action) {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            Toast.makeText(this, "Set a phone screen lock or sign in again to confirm this action.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        android.widget.EditText password = new android.widget.EditText(this);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint("Your CampusTrade password");
        int pad = ui.dp(20);
        password.setPadding(pad, ui.dp(10), pad, ui.dp(10));
        new android.app.AlertDialog.Builder(this)
                .setTitle("Confirm it's you")
                .setMessage("Re-enter your password before this sensitive action.")
                .setView(password)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String value = password.getText().toString();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    user.reauthenticate(com.google.firebase.auth.EmailAuthProvider
                                    .getCredential(user.getEmail(), value))
                            .addOnSuccessListener(v -> {
                                sensitiveVerifiedAt = System.currentTimeMillis();
                                action.run();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this,
                                    "Couldn't confirm your password. Try again.",
                                    Toast.LENGTH_LONG).show());
                })
                .show();
    }

    void clearSensitiveVerification() {
        sensitiveVerifiedAt = 0;
    }

    // Settings are saved per email.
    SharedPreferences settingsPrefs() {
        return com.campustrade.auth.data.SecurePrefs.get(this, PREFS + "_settings_" + currentStudentEmail);
    }

    boolean isDebugBuild() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    boolean isSignedIn() {
        return !currentStudentEmail.isEmpty()
                && com.campustrade.auth.data.SecurePrefs.get(this, PREFS).getBoolean("signed_in", false);
    }

    boolean hasProfilePhoto() {
        if (currentStudentEmail.isEmpty()) return false;
        String image = com.campustrade.auth.data.SecurePrefs.get(this, PREFS + "_profiles")
                .getString("profile_image:" + currentStudentEmail, "");
        return image != null && !image.trim().isEmpty();
    }

    /** Returns false and opens the photo prompt when the profile is missing a picture. */
    boolean requireProfilePhoto() {
        if (hasProfilePhoto()) return true;
        ui.dialog().setTitle("Profile photo required")
                .setMessage("Add a clear photo of yourself before publishing a listing or purchasing an item. "
                        + "This helps other students know who they are meeting.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Add photo", (dialog, which) -> profile.pickProfilePhoto())
                .show();
        return false;
    }

    // Admin is a Firebase role; the debug passcode is an extra local check.
    void refreshAdminRole() {
        if (currentUid == null || currentUid.isEmpty()) { adminAccount = false; return; }
        new AuthRepository().loadUser(currentUid, new AuthRepository.UserCallback() {
            @Override public void onOk(com.campustrade.auth.model.User user) {
                if (isDestroyed() || isFinishing()) return;
                if (user != null && ("suspended".equalsIgnoreCase(user.getAccountStatus())
                        || "deactivated".equalsIgnoreCase(user.getAccountStatus())
                        || "deleted".equalsIgnoreCase(user.getAccountStatus()))) {
                    new AuthRepository().logout();
                    currentUid = "";
                    currentStudentEmail = "";
                    com.campustrade.auth.data.SecurePrefs.get(MainActivity.this, PREFS).edit().clear().apply();
                    auth.showAuthScreen();
                    return;
                }
                adminAccount = user != null && user.getIsAdmin();
            }
            @Override public void onBad(String message) {
                if (isDestroyed() || isFinishing()) return;
                adminAccount = false;
            }
        });
    }

    // Reports are saved on this phone.
    void loadModerationReports() {
        try {
            JSONArray records = new JSONArray(com.campustrade.auth.data.SecurePrefs.get(this, PREFS + "_reports").getString("records", "[]"));
            for (int i = 0; i < records.length(); i++) {
                JSONObject r = records.getJSONObject(i);
                moderationReports.add(new ModerationReport(r.getString("id"), r.getString("product"), r.getString("title"), r.getString("reporter"),
                        r.getString("name"), r.getString("reason"), r.getString("detail"), r.getString("priority"), r.getString("state"), r.getLong("time")));
            }
        } catch (org.json.JSONException e) { Toast.makeText(this, "Some reports couldn't be loaded.", Toast.LENGTH_SHORT).show(); }
    }

    // Return false so the caller can warn that reports weren't saved.
    boolean saveModerationReports() {
        JSONArray records = new JSONArray();
        try {
            for (ModerationReport r : moderationReports) records.put(new JSONObject().put("id", r.id).put("product", r.productId).put("title", r.productTitle)
                    .put("reporter", r.reporterEmail).put("name", r.reporterName).put("reason", r.reason).put("detail", r.detail)
                    .put("priority", r.priority).put("state", r.state).put("time", r.createdAt));
        } catch (org.json.JSONException e) { return false; }
        return com.campustrade.auth.data.SecurePrefs.get(this, PREFS + "_reports").edit().putString("records", records.toString()).commit();
    }

    String studentName(String email) {
        if (HelperBot.PEER.equals(email)) {
            return HelperBot.NAME;
        }
        if (email != null && !email.contains("@")) {
            String name = CloudSync.displayName(this, email);
            return name == null || name.isEmpty() ? "Student seller" : name;
        }
        String saved = com.campustrade.auth.data.SecurePrefs.get(this, PREFS + "_profiles").getString("full_name:" + email, "");
        if (saved != null && !saved.trim().isEmpty()) return saved;
        String cached = CloudSync.nameForEmail(this, email);
        return cached == null || cached.trim().isEmpty() ? "Student seller" : cached;
    }

    // Stop listeners being added twice after a restore.
    /** Live listeners show messages while the app is open. */
    void startChatListeners() {
        final String uid = CloudSync.uid(this);
        if (uid.isEmpty() || chatsBuyerListener != null) return;
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        chatsBuyerListener = db.collection("chats").whereEqualTo("buyerID", uid).limit(20)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) { android.util.Log.w("CampusTrade", "chat listener (buyer): " + error.getMessage()); return; }
                    if (snap == null) return;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        inbox.listenToChat(doc.getId(), doc.getString("sellerID"));
                    }
                });
        chatsSellerListener = db.collection("chats").whereEqualTo("sellerID", uid).limit(20)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) { android.util.Log.w("CampusTrade", "chat listener (seller): " + error.getMessage()); return; }
                    if (snap == null) return;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        inbox.listenToChat(doc.getId(), doc.getString("buyerID"));
                    }
                });
        startOrderListeners();
        market.startLiveUpdates();
        order.startLivePickups();
        saveFcmToken();
    }

    // Save the push token on the student profile.
    void saveFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
            .addOnSuccessListener(token -> {
                String uid = CloudSync.uid(this);
                if (uid.isEmpty() || token == null || token.isEmpty()) return;
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .update("fcmToken", token)
                    .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "FCM token save failed: " + e.getMessage()));
            });
    }

    // Use two order listeners: one for purchases, one for sales.
    /** Live order updates while the app is open. */
    private void startOrderListeners() {
        final String uid = CloudSync.uid(this);
        if (uid.isEmpty() || ordersSellerListener != null) return;
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> onChange =
                (snap, error) -> {
                    if (error != null) {
                        android.util.Log.w("CampusTrade", "order listener: " + error.getMessage());
                        return;
                    }
                    order.cloudPullOrders(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        String tab = currentTab;
                        if (tab.equals("Payments") || tab.equals("Offers") || tab.equals("Home")
                                || tab.equals("SellerDashboard") || tab.equals("AdminAnalytics")) {
                            showAppScreen(tab);
                        }
                        order.refreshOpenOrder();
                    });
                };
        ordersSellerListener = db.collection("orders").whereEqualTo("sellerId", uid).limit(30)
                .addSnapshotListener(onChange);
        ordersBuyerListener = db.collection("orders").whereEqualTo("buyerId", uid).limit(30)
                .addSnapshotListener(onChange);
    }

    private void stopOrderListeners() {
        if (ordersSellerListener != null) { ordersSellerListener.remove(); ordersSellerListener = null; }
        if (ordersBuyerListener != null) { ordersBuyerListener.remove(); ordersBuyerListener = null; }
    }

    // Remove listeners so they stop when the screen closes.
    void stopChatListeners() {
        stopOrderListeners();
        market.stopLiveUpdates();
        order.stopLivePickups();
        if (chatsBuyerListener != null) { chatsBuyerListener.remove(); chatsBuyerListener = null; }
        if (chatsSellerListener != null) { chatsSellerListener.remove(); chatsSellerListener = null; }
        for (com.google.firebase.firestore.ListenerRegistration listener : messageListeners) listener.remove();
        messageListeners.clear();
        listenedChatIds.clear();
        openChatPeer = "";
        openChatContainer = null;
        openChatScroll = null;
        openChatDialog = null;
    }

    // Handle file pickers and the app lock.
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SENSITIVE_ACTION_REQUEST) {
            Runnable action = pendingSensitiveAction;
            pendingSensitiveAction = null;
            if (resultCode == RESULT_OK && action != null) {
                sensitiveVerifiedAt = System.currentTimeMillis();
                action.run();
            } else {
                Toast.makeText(this, "Action cancelled.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == UNLOCK_REQUEST) {
            unlocking = false;
            needsUnlock = false;
            getWindow().getDecorView().setVisibility(View.VISIBLE);
            if (resultCode != RESULT_OK) {
                com.campustrade.auth.data.SecurePrefs.get(this, PREFS).edit().putBoolean("signed_in", false).apply();
                finish();
            }
            return;
        }
        if (requestCode == PROFILE_PHOTO_REQUEST) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            Toast.makeText(this, "Uploading profile photo...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String url = CloudSync.uploadPhoto(this, uri);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    if (url == null || url.isEmpty()) {
                        Toast.makeText(this, "The profile photo couldn't be uploaded.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    com.campustrade.auth.data.SecurePrefs.get(this, PREFS + "_profiles").edit()
                            .putString("profile_image:" + currentStudentEmail, url).apply();
                    new AuthRepository().updateProfileImage(currentUid, url,
                            new AuthRepository.SimpleCallback() {
                                @Override public void onOk() {
                                    Toast.makeText(MainActivity.this, "Profile photo updated.", Toast.LENGTH_SHORT).show();
                                    showAppScreen(currentTab.equals("Settings") ? "Settings" : "Profile");
                                }
                                @Override public void onBad(String message) {
                                    Toast.makeText(MainActivity.this, "Photo saved on this device, but the cloud copy failed: " + message,
                                            Toast.LENGTH_LONG).show();
                                    showAppScreen(currentTab.equals("Settings") ? "Settings" : "Profile");
                                }
                            });
                });
            }).start();
            return;
        }
        if (requestCode == COLLECTION_CAMERA_REQUEST) {
            String orderId = pendingCollectionOrderId;
            pendingCollectionOrderId = "";
            if (resultCode != RESULT_OK || orderId == null || orderId.isEmpty()) {
                order.collectionPhotoCancelled();
                return;
            }
            order.receiveCollectionPhoto(orderId, order.pendingCollectionPhoto);
            return;
        }
        if (requestCode == LISTING_DOCUMENT_REQUEST) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null
                    || listingDialog == null || !listingDialog.isShowing()) return;
            Uri uri = data.getData();
            String name = market.documentDisplayName(uri);
            long size = market.documentDisplaySize(uri);
            String mime = getContentResolver().getType(uri);
            if (mime == null || mime.trim().isEmpty() || "application/octet-stream".equals(mime)) {
                mime = market.documentMimeForName(name);
            }
            if (!market.isAllowedDocument(name, mime)) {
                Toast.makeText(this, "Choose a PDF, Word, PowerPoint, RTF, ODT or TXT file.", Toast.LENGTH_LONG).show();
                return;
            }
            if (size > Constants.MAX_DOCUMENT_BYTES) {
                Toast.makeText(this, "Choose a document smaller than 10 MB.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                
            }
            draftDocumentUri = uri;
            draftDocumentUrl = "";
            draftDocumentName = name;
            draftDocumentMime = mime;
            draftDocumentSize = Math.max(0, size);
            market.renderDraftDocument();
            return;
        }
        if (requestCode != LISTING_PHOTO_REQUEST || resultCode != RESULT_OK || data == null
                || listingDialog == null || !listingDialog.isShowing()) return;
        int count = data.getClipData() == null ? 1 : data.getClipData().getItemCount();
        for (int i = 0; i < count; i++) {
            Uri uri = data.getClipData() == null ? data.getData() : data.getClipData().getItemAt(i).getUri();
            if (uri == null || draftPhotos.contains(uri)) continue;
            if (draftPhotos.size() >= Constants.MAX_LISTING_PHOTOS) {
                Toast.makeText(this, "Only the first 5 photos were added.", Toast.LENGTH_SHORT).show();
                break;
            }
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                
            }
            draftPhotos.add(uri);
        }
        market.renderDraftPhotos();
    }

    static String safe(String value) { return value == null ? "" : value; }

    @Override protected void onDestroy() {
        stopChatListeners();
        imageLoader.shutdownNow();
        super.onDestroy();
    }

    /** Copy this listing to Firebase. */

    /** Load orders from both sides so devices match. */

    /** Review delivery and payment before creating the order. */

    static final String[] SA_BANKS = {"FNB", "ABSA", "Standard Bank", "Nedbank", "Capitec"};

    // Build the top bar; its button changes per tab.
    LinearLayout appHeader(String title, String subtitle, String action) {
        LinearLayout header = ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ui.dp(28), ui.dp(16), ui.dp(28), ui.dp(10));
        header.setBackgroundColor(page);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(0, 0, ui.dp(12), 0);
        if (currentTab.equals("Home")) {
            copy.addView(ui.wordmark());
        } else {
            TextView heading = ui.titleText(title, text, 28);
            if (Build.VERSION.SDK_INT >= 26) {
                heading.setAutoSizeTextTypeUniformWithConfiguration(20, 28, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            }
            heading.setMaxLines(1);
            heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(heading);
            TextView detail = ui.bodyText(subtitle, muted, 12);
            detail.setPadding(0, ui.dp(4), 0, 0);
            copy.addView(detail);
        }
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View button;
        if (!currentTab.equals("Settings")) {
            button = new CampusAssetView(this, community.communityHeaderIcon(currentTab), text);
            button.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
            button.setContentDescription(community.communityHeaderLabel(currentTab));
            button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) button.setTooltipText(button.getContentDescription());
        } else {
            TextView command = new TextView(this);
            command.setText(action);
            command.setTextSize(action.length() > 1 ? 11 : 20);
            command.setGravity(Gravity.CENTER);
            command.setTextColor(text);
            button = command;
        }
        button.setMinimumWidth(ui.dp(48));
        button.setMinimumHeight(ui.dp(48));
        button.setBackground(ui.strokedRound(page, 999, text));
        button.setOnClickListener(v -> {
            if (currentTab.equals("Market")) {
                order.showTransactionHistory();
            } else if (currentTab.equals("Profile")) {
                showAppScreen("Settings");
            } else if (currentTab.equals("Settings")) {
                showAppScreen("Profile");
            } else if (currentTab.equals("Notifications")) {
                showAppScreen(notificationsReturnTab);
            } else if (currentTab.equals("Payments")) {
                showAppScreen(paymentsReturnTab);
            } else if (currentTab.equals("Admin")) {
                admin.exitAdminMode();
            } else if (currentTab.equals("AdminReports")) {
                showAppScreen("Admin");
            } else if (currentTab.equals("Groups")) community.createStudyGroup();
            else if (currentTab.equals("Wanted")) community.createWanted();
            else if (currentTab.equals("SearchAlerts")) community.createSearchAlert();
            else if (currentTab.equals("Offers")) showAppScreen("Market");
            else if (currentTab.equals("Academics")) {
                academics.showAcademicCalendar();
            } else if (currentTab.equals("Inbox")) {
                inbox.showMessageDialog();
            } else {
                notification.showNotificationHistory();
            }
        });
        ui.attachPressAnimation(button);
        header.addView(button, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));

        View menuButton = new CampusAssetView(this, "menu", text);
        menuButton.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
        menuButton.setContentDescription("Open navigation menu");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) menuButton.setTooltipText("Menu");
        ui.attachPressAnimation(menuButton);
        menuButton.setOnClickListener(v -> showAppMenu());
        header.addView(menuButton, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        return header;
    }

    // Five tabs; Market sits in a raised circle.
    LinearLayout bottomNavigation() {
        LinearLayout nav = ui.horizontal();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(ui.dp(6), ui.dp(14), ui.dp(6), ui.dp(12));
        nav.setBackground(ui.edgeRule(text, true));
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        String lastTab = currentTab.startsWith("Admin") ? "Admin" : currentTab.equals("Settings") ? "Settings" : "Profile";
        String lastIcon = lastTab.equals("Admin") ? "shield" : lastTab.equals("Settings") ? "settings" : "profile";
        String[] tabs = {"Home", "Academics", "Market", "Inbox", lastTab};
        String[] icons = {"home", "book", "cart", "chat", lastIcon};
        String[] names = {"Home", "Study", "Market", "Inbox", lastTab.equals("Profile") ? "Me" : lastTab};
        for (int i = 0; i < tabs.length; i++) {
            final String tab = tabs[i];
            boolean active = tab.equals(currentTab) || (tab.equals("Admin") && currentTab.equals("AdminReports"))
                    || (tab.equals("Inbox") && currentTab.equals("Groups"))
                    || (tab.equals("Market") && (currentTab.equals("Wanted") || currentTab.equals("Offers") || currentTab.equals("SearchAlerts")));
            int navColor = active ? blue : muted;
            if (tab.equals("Market")) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                item.setClipChildren(false);
                item.setContentDescription("Marketplace");
                View circle = new CampusAssetView(this, "cart", Color.WHITE);
                circle.setBackground(ui.strokedRound(Color.rgb(200, 50, 26), 999, Color.WHITE));
                circle.setTranslationY(-ui.dp(8));
                circle.setPadding(ui.dp(15), ui.dp(15), ui.dp(15), ui.dp(15));
                int diameter = ui.dp(62);
                item.addView(circle, new LinearLayout.LayoutParams(diameter, diameter));
                TextView marketLabel = ui.bodyText("Market", navColor, 12);
                marketLabel.setSingleLine(true);
                marketLabel.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
                marketLabel.setGravity(Gravity.CENTER);
                item.addView(marketLabel);
                item.setOnClickListener(v -> showAppScreen(tab));
                nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                continue;
            }
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, ui.dp(4), 0, ui.dp(3));
            item.setContentDescription(names[i]);
            View icon = new CampusAssetView(this, icons[i], navColor);
            icon.setContentDescription(null);
            item.addView(icon, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
            TextView label = ui.bodyText(names[i], navColor, 12);
            label.setSingleLine(true);
            label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            label.setGravity(Gravity.CENTER);
            item.addView(label);
            View dot = new View(this);
            dot.setBackground(ui.round(active ? blue : Color.TRANSPARENT, 999));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(ui.dp(6), ui.dp(6));
            dotParams.topMargin = ui.dp(6);
            item.addView(dot, dotParams);
            item.setOnClickListener(v -> showAppScreen(tab));
            ui.attachPressAnimation(item);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ui.dp(64), 1);
            params.leftMargin = ui.dp(2);
            params.rightMargin = ui.dp(2);
            nav.addView(item, params);
        }
        return nav;
    }

    String headerTitle(String tab) {
        if (tab.equals("Home")) {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            String when = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
            return when + ", " + currentStudentName;
        }
        if (tab.equals("Academics")) return "Study";
        if (tab.equals("Market")) return "Market";
        if (tab.equals("Inbox")) return "Inbox";
        if (tab.equals("Settings")) return "Settings";
        if (tab.equals("Notifications")) return "Notifications";
        if (tab.equals("Payments")) return "Orders";
        if (tab.equals("Admin")) return "Admin";
        if (tab.equals("AdminReports")) return "Moderation";
        if (tab.equals("AdminAnalytics")) return "Analytics";
        if (tab.equals("SellerDashboard")) return "Sales";
        if (tab.equals("Groups")) return "Groups";
        if (tab.equals("Wanted")) return "Wanted";
        if (tab.equals("Offers")) return "Offers";
        if (tab.equals("SearchAlerts")) return "Alerts";
        return "Profile";
    }

    String headerSubtitle(String tab) {
        if (tab.equals("Home")) return new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date()) + " - Eduvos campus";
        if (tab.equals("Academics")) return "Courses, notes and deadlines";
        if (tab.equals("Market")) return "Buy and sell within campus";
        if (tab.equals("Inbox")) return "Groups, buyers and campus friends";
        if (tab.equals("Settings")) return "Account, alerts, privacy and preferences";
        if (tab.equals("Notifications")) return notification.unreadNotifications() + " unread";
        if (tab.equals("Payments")) return "Your purchases and sales";
        if (tab.equals("Admin")) return "Campus operations overview";
        if (tab.equals("AdminReports")) return "Local campus reports";
        if (tab.equals("AdminAnalytics")) return "Users, listings and transactions";
        if (tab.equals("SellerDashboard")) return "Your listings, sales and performance";
        if (tab.equals("Groups")) return "Course-based campus connections";
        if (tab.equals("Wanted")) return "What students need";
        if (tab.equals("Offers")) return "Offers and accepted prices";
        if (tab.equals("SearchAlerts")) return "Matching listings and price alerts";
        return "Your campus identity";
    }

    String headerAction(String tab) {
        if (tab.equals("Market")) return "+";
        if (tab.equals("Profile")) return "SET";
        if (tab.equals("Inbox")) return "MSG";
        if (tab.equals("Academics")) return "ADD";
        if (tab.equals("Settings")) return "<";
        return "CT";
    }

    void handleSectionAction(String section, String action) {
        if (section.equals("Marketplace")) {
            market.showAddProductDialog();
        } else if (section.equals("Campus activity")) {
            profile.showEditProfile();
        } else if (section.equals("My courses")) {
            academics.showAcademicDialog();
        } else {
            Toast.makeText(this, action + " isn't available right now.", Toast.LENGTH_SHORT).show();
        }
    }

    String academicDate(long time) { return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(time)); }

    ImageButton iconButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(blue));
        button.setContentDescription(description);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) button.setTooltipText(description);
        button.setMinimumWidth(ui.dp(48));
        button.setMinimumHeight(ui.dp(48));
        button.setBackground(ui.strokedRound(Color.WHITE, 8, border));
        ui.attachPressAnimation(button);
        return button;
    }

    String messageTime(long time) {
        return new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date(time));
    }

    String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }


    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    // Check the real Firebase session, not just the saved flag.
    private void verifyFirebaseSessionAsync() {
        new Thread(() -> {
            String uid = new AuthRepository().currentUid();
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                if (uid == null && com.campustrade.auth.data.SecurePrefs.get(this, PREFS).getBoolean("signed_in", false)) {
                    com.campustrade.auth.data.SecurePrefs.get(this, PREFS).edit().putBoolean("signed_in", false).apply();
                    auth.showAuthScreen();
                } else if (uid != null && (currentUid == null || currentUid.isEmpty())) {
                    currentUid = uid;
                    refreshAdminRole();
                }
            });
        }).start();
    }

    // Drawer with student details and grouped links.
    // Admin shows only for moderators.
    void showAppMenu() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.argb(90, 0, 0, 0));
        root.setOnClickListener(v -> dialog.dismiss());

        int panelWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(surface());
        panel.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(12));
        panel.setElevation(ui.dp(12));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        panel.setLayoutParams(panelParams);
        panel.setOnClickListener(v -> { });

        // Start with the student's identity.
        LinearLayout identity = ui.horizontal();
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(16));
        String initials = "";
        String[] nameParts = currentStudentName.trim().split("\\s+");
        for (int i = 0; i < Math.min(2, nameParts.length); i++) {
            if (!nameParts[i].isEmpty()) initials += nameParts[i].substring(0, 1).toUpperCase(Locale.ROOT);
        }
        TextView avatar = ui.bodyText(initials.isEmpty() ? "S" : initials, Color.WHITE, 20);
        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(ui.round(blue, 999));
        identity.addView(avatar, new LinearLayout.LayoutParams(ui.dp(50), ui.dp(50)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(ui.dp(12), 0, 0, 0);
        TextView name = ui.titleText(currentStudentName, text, 16);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(name);
        TextView email = ui.bodyText(currentStudentEmail, muted, 11);
        email.setSingleLine(true);
        email.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(email);
        identity.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = ui.textButton("Close");
        close.setOnClickListener(v -> dialog.dismiss());
        identity.addView(close);
        identity.setOnClickListener(v -> { dialog.dismiss(); showAppScreen("Profile"); });
        panel.addView(identity);
        View identityRule = new View(this);
        identityRule.setBackgroundColor(line);
        panel.addView(identityRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, ui.dp(6), 0, ui.dp(6));

        drawerSection(list, "Browse");
        drawerRow(list, dialog, "home", "Home", "", "Home");
        drawerRow(list, dialog, "cart", "Market", "", "Market");
        drawerRow(list, dialog, "book", "Study", "", "Academics");
        drawerRow(list, dialog, "chat", "Inbox", inbox.unreadMessages() > 0 ? String.valueOf(inbox.unreadMessages()) : "", "Inbox");

        drawerSection(list, "Selling");
        drawerRow(list, dialog, "payment", "Orders", "", "Payments");
        drawerRow(list, dialog, "tag", "Offers", "", "Offers");
        drawerRow(list, dialog, "tag", "Wanted", "", "Wanted");
        drawerRow(list, dialog, "bell", "Search alerts", "", "SearchAlerts");
        drawerRow(list, dialog, "profile", "Seller dashboard", "", "SellerDashboard");

        drawerSection(list, "Community");
        drawerRow(list, dialog, "team", "Groups", "", "Groups");
        drawerRow(list, dialog, "bell", "Notifications", notification.unreadNotifications() > 0
                ? String.valueOf(notification.unreadNotifications()) : "", "Notifications");

        drawerSection(list, "Account");
        drawerRow(list, dialog, "profile", "Profile", "", "Profile");
        drawerRow(list, dialog, "settings", "Settings", "", "Settings");
        drawerRow(list, dialog, "pin", "Help & support", "", "Help");

        if (admin.canUseAdmin()) {
            drawerSection(list, "Moderation");
            drawerRow(list, dialog, "shield", "Admin", "", "Admin");
            drawerRow(list, dialog, "grid", "Analytics", "", "AdminAnalytics");
        }

        TextView signOut = ui.textButton("Log out");
        signOut.setGravity(Gravity.START);
        signOut.setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(12));
        signOut.setOnClickListener(v -> { dialog.dismiss(); profile.confirmLogout(); });
        list.addView(signOut);

        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(panel);
        dialog.setContentView(root);
        panel.setTranslationX(panelWidth);
        dialog.setOnShowListener(d -> panel.animate().translationX(0f).setDuration(180).start());
        dialog.show();
    }

    void drawerSection(LinearLayout list, String label) {
        TextView heading = ui.tinyCaps(label.toUpperCase(Locale.ROOT), muted);
        heading.setPadding(ui.dp(10), ui.dp(14), ui.dp(10), ui.dp(4));
        list.addView(heading);
    }

    void drawerRow(LinearLayout list, Dialog dialog, String icon, String label, String badge, String target) {
        LinearLayout row = ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(8), ui.dp(2), ui.dp(8), ui.dp(2));
        row.setMinimumHeight(ui.dp(46));
        View symbol = new CampusAssetView(this, icon, muted);
        symbol.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        symbol.setBackground(ui.round(nightActive ? Color.rgb(42, 42, 46) : Color.rgb(243, 241, 236), 10));
        row.addView(symbol, new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)));
        TextView name = ui.bodyText(label, text, 14);
        name.setPadding(ui.dp(12), 0, ui.dp(6), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (badge != null && !badge.isEmpty()) {
            TextView count = ui.chip(badge, blue, Color.WHITE);
            count.setTextSize(10);
            count.setPadding(ui.dp(7), ui.dp(2), ui.dp(7), ui.dp(2));
            row.addView(count);
        }
        View chevron = new CampusAssetView(this, "chevron", border);
        row.addView(chevron, new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (target.equals("Help")) profile.showHelpSupport();
            else showAppScreen(target);
        });
        ui.attachPressAnimation(row);
        list.addView(row);
    }

    void handleNotificationIntent(Intent intent) {
        if (intent == null || currentStudentEmail.isEmpty()
                || !com.campustrade.auth.data.SecurePrefs.get(this, PREFS).getBoolean("signed_in", false)) return;
        boolean openOrders = intent.getBooleanExtra("open_orders", false);
        boolean openNotifications = intent.getBooleanExtra("open_notifications", false);
        boolean openInbox = intent.getBooleanExtra("open_inbox", false);
        String openPeer = intent.getStringExtra("open_peer");
        String openOrder = intent.getStringExtra("open_order");
        String filter = intent.getStringExtra("open_notification_filter");
        if (!openOrders && !openNotifications && !openInbox && (openPeer == null || openPeer.isEmpty())) return;

        if (openInbox || (openPeer != null && !openPeer.isEmpty())) {
            inbox.reloadMessages();
            showAppScreen("Inbox");
            if (openPeer != null && !openPeer.isEmpty()) inbox.showConversation(openPeer);
        } else if (openOrders || (openOrder != null && !openOrder.isEmpty())) {
            notificationFilter = "Orders";
            TransactionRecord target = order.orderById(openOrder);
            if (target != null) buyingOrders = target.isBuyer(currentStudentEmail);
            showAppScreen("Payments");
            if (target != null) order.showOrderDetails(target);
        } else {
            if (filter != null && !filter.isEmpty()) notificationFilter = filter;
            showAppScreen("Notifications");
        }
        intent.removeExtra("open_inbox");
        intent.removeExtra("open_notifications");
        intent.removeExtra("open_orders");
        intent.removeExtra("open_order");
        intent.removeExtra("open_peer");
        intent.removeExtra("open_notification_filter");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("tab", currentTab);
    }

    @Override protected void onStop() {
        super.onStop();
        if (!unlocking && !currentStudentEmail.isEmpty()
                && com.campustrade.auth.data.SecurePrefs.get(this, PREFS).getBoolean("signed_in", false)) needsUnlock = true;
    }

    // No real back stack, so handle common back cases by hand.
    @Override public void onBackPressed() {
        if (adminOnlySession) {
            if (currentTab.equals("AdminReports") || currentTab.equals("AdminAnalytics")) { admin.showAdminOnlyScreen("Admin"); return; }
            admin.exitAdminMode();
            return;
        }
        if (com.campustrade.auth.data.SecurePrefs.get(this, PREFS).getBoolean("signed_in", false)) {
            if (admin.canUseAdmin() && currentTab.equals("AdminReports")) { showAppScreen("Admin"); return; }
            if (admin.canUseAdmin() && currentTab.equals("AdminAnalytics")) { showAppScreen("Admin"); return; }
            if (admin.canUseAdmin() && currentTab.equals("Admin")) { admin.exitAdminMode(); return; }
            if (currentTab.equals("SellerDashboard")) { showAppScreen("Profile"); return; }
            if (currentTab.equals("Payments")) { showAppScreen(paymentsReturnTab); return; }
            if (currentTab.equals("Groups")) { showAppScreen(groupsReturnTab == null || groupsReturnTab.isEmpty() ? "Inbox" : groupsReturnTab); return; }
            if (currentTab.equals("Wanted") || currentTab.equals("Offers") || currentTab.equals("SearchAlerts")) { showAppScreen("Market"); return; }
            if (!currentTab.equals("Market")) { showAppScreen("Market"); return; }
        }
        super.onBackPressed();
    }

    // On return, check the lock and refresh settings.
    @Override protected void onResume() {
        super.onResume();
        boolean signedIn = com.campustrade.auth.data.SecurePrefs.get(this, PREFS).getBoolean("signed_in", false);
        if (needsUnlock && !unlocking && settingsPrefs().getBoolean("app_lock", false)) {
            KeyguardManager guard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            Intent unlock = guard == null ? null : guard.createConfirmDeviceCredentialIntent("Unlock CampusTrade", "Confirm your phone's screen lock");
            if (unlock != null) {
                unlocking = true;
                getWindow().getDecorView().setVisibility(View.INVISIBLE);
                startActivityForResult(unlock, UNLOCK_REQUEST);
            } else {
                com.campustrade.auth.data.SecurePrefs.get(this, PREFS).edit().putBoolean("signed_in", false).apply();
                finish();
            }
        } else if (signedIn && shouldNight() != nightActive) {
            applyTheme();
            applyWindowTheme();
            showAppScreen(currentTab);
        } else if ((currentTab.equals("Settings") || currentTab.equals("Notifications")) && !unlocking) showAppScreen(currentTab);
        if (!unlocking) needsUnlock = false;
        if (signedIn && !unlocking) refreshAdminRole();
    }

    private class LogoView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        LogoView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float corner = Math.min(w, h) * 0.28f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(78, 132, 246));
            canvas.drawRoundRect(new RectF(0, 0, w, h), corner, corner, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ui.dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Color.WHITE);

            path.reset();
            path.moveTo(w * 0.20f, h * 0.36f);
            path.lineTo(w * 0.50f, h * 0.22f);
            path.lineTo(w * 0.80f, h * 0.36f);
            path.lineTo(w * 0.50f, h * 0.50f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawLine(w * 0.34f, h * 0.48f, w * 0.34f, h * 0.62f, paint);
            canvas.drawLine(w * 0.34f, h * 0.62f, w * 0.66f, h * 0.62f, paint);
            canvas.drawLine(w * 0.66f, h * 0.48f, w * 0.66f, h * 0.62f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(green);
            canvas.drawCircle(w * 0.72f, h * 0.72f, Math.min(w, h) * 0.16f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(ui.dp(2));
            canvas.drawLine(w * 0.64f, h * 0.73f, w * 0.70f, h * 0.79f, paint);
            canvas.drawLine(w * 0.70f, h * 0.79f, w * 0.82f, h * 0.65f, paint);
        }
    }

    // Draw the small line icons used across the app.
    class CampusAssetView extends View {
        private final String type;
        private final int color;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();

        CampusAssetView(Context context, String type, int color) {
            super(context);
            this.type = type;
            this.color = color;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private void line(Canvas canvas, float x1, float y1, float x2, float y2) {
            canvas.drawLine(x1, y1, x2, y2, stroke);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                    getHeight() - getPaddingTop() - getPaddingBottom());
            if (size <= 0) return;
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(size / 24f, size / 24f);
            stroke.setColor(color);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.6f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            shape.reset();
            switch (type) {
                case "exit":
                    shape.moveTo(10, 3); shape.lineTo(3, 3); shape.lineTo(3, 21); shape.lineTo(10, 21);
                    canvas.drawPath(shape, stroke); line(canvas, 10, 12, 22, 12);
                    line(canvas, 17, 7, 22, 12); line(canvas, 22, 12, 17, 17);
                    break;
                case "flag":
                    line(canvas, 4, 3, 4, 22);
                    shape.moveTo(4, 3); shape.cubicTo(9, 0, 15, 7, 21, 3);
                    shape.lineTo(21, 14); shape.cubicTo(15, 18, 9, 11, 4, 14);
                    canvas.drawPath(shape, stroke);
                    break;
                case "sparkle":
                    shape.moveTo(12, 3); shape.lineTo(15, 9); shape.lineTo(21, 12);
                    shape.lineTo(15, 15); shape.lineTo(12, 21); shape.lineTo(9, 15);
                    shape.lineTo(3, 12); shape.lineTo(9, 9); shape.close();
                    canvas.drawPath(shape, stroke); line(canvas, 20, 1, 20, 5); line(canvas, 18, 3, 22, 3);
                    break;
                case "back":
                    line(canvas, 15, 6, 9, 12); line(canvas, 9, 12, 15, 18);
                    break;
                case "home":
                    shape.moveTo(3, 12); shape.lineTo(12, 4); shape.lineTo(21, 12);
                    canvas.drawPath(shape, stroke);
                    shape.reset(); shape.moveTo(6, 11); shape.lineTo(6, 20);
                    shape.lineTo(18, 20); shape.lineTo(18, 11);
                    canvas.drawPath(shape, stroke);
                    break;
                case "chat":
                    shape.moveTo(6, 18); shape.lineTo(3, 22); shape.lineTo(4, 15);
                    shape.cubicTo(0, 5, 7, 1, 13, 2); shape.cubicTo(23, 3, 25, 13, 18, 17);
                    shape.quadTo(13, 20, 6, 18); canvas.drawPath(shape, stroke);
                    break;
                case "bag":
                    canvas.drawRoundRect(new RectF(4, 5, 20, 22), 3, 3, stroke);
                    canvas.drawArc(new RectF(8, 7, 16, 15), 0, 180, false, stroke);
                    line(canvas, 7, 2, 17, 2);
                    break;
                case "course":
                    canvas.drawRoundRect(new RectF(5, 2, 20, 22), 1.5f, 1.5f, stroke);
                    line(canvas, 8, 2, 8, 22); line(canvas, 11, 18, 17, 18);
                    shape.moveTo(13, 2); shape.lineTo(13, 10); shape.lineTo(15, 8);
                    shape.lineTo(17, 10); shape.lineTo(17, 2); canvas.drawPath(shape, stroke);
                    break;
                case "grid":
                    canvas.drawRoundRect(new RectF(3, 3, 10, 10), 1, 1, stroke);
                    canvas.drawRoundRect(new RectF(14, 3, 21, 10), 1, 1, stroke);
                    canvas.drawRoundRect(new RectF(3, 14, 10, 21), 1, 1, stroke);
                    canvas.drawRoundRect(new RectF(14, 14, 21, 21), 1, 1, stroke);
                    break;
                case "list":
                    line(canvas, 3, 6, 21, 6); line(canvas, 3, 12, 21, 12); line(canvas, 3, 18, 21, 18);
                    break;
                case "moon":
                    shape.moveTo(15, 3); shape.cubicTo(6, 5, 7, 18, 18, 17);
                    shape.cubicTo(11, 27, -1, 16, 5, 8); shape.quadTo(9, 2, 15, 3);
                    canvas.drawPath(shape, stroke);
                    break;
                case "profile":
                    canvas.drawCircle(12, 7, 4, stroke);
                    canvas.drawArc(new RectF(4, 14, 20, 30), 180, 180, false, stroke);
                    break;
                case "bookmark":
                    shape.moveTo(6, 3); shape.lineTo(18, 3); shape.lineTo(18, 22);
                    shape.lineTo(12, 17); shape.lineTo(6, 22); shape.close();
                    canvas.drawPath(shape, stroke);
                    break;
                case "plus":
                    line(canvas, 6, 12, 18, 12); line(canvas, 12, 6, 12, 18);
                    break;
                case "check":
                    line(canvas, 5, 12, 10, 17); line(canvas, 10, 17, 19, 7);
                    break;
                case "checklist":
                    canvas.drawRoundRect(new RectF(4, 2.5f, 20, 21.5f), 2, 2, stroke);
                    line(canvas, 7, 7, 8.5f, 8.5f); line(canvas, 8.5f, 8.5f, 11, 5.5f);
                    line(canvas, 14, 7, 17, 7);
                    line(canvas, 7, 12, 8.5f, 13.5f); line(canvas, 8.5f, 13.5f, 11, 10.5f);
                    line(canvas, 14, 12, 17, 12);
                    line(canvas, 7, 17, 8.5f, 18.5f); line(canvas, 8.5f, 18.5f, 11, 15.5f);
                    line(canvas, 14, 17, 17, 17);
                    break;
                case "settings":
                    for (int i = 0; i < 24; i++) {
                        double angle = Math.PI * 2 * i / 24;
                        float radius = i % 3 == 1 ? 10 : 8;
                        float x = 12 + (float) Math.cos(angle) * radius;
                        float y = 12 + (float) Math.sin(angle) * radius;
                        if (i == 0) shape.moveTo(x, y); else shape.lineTo(x, y);
                    }
                    shape.close(); canvas.drawPath(shape, stroke);
                    canvas.drawCircle(12, 12, 3.5f, stroke);
                    break;
                case "compose":
                    shape.moveTo(11, 4); shape.lineTo(4, 4); shape.lineTo(4, 21);
                    shape.lineTo(20, 21); shape.lineTo(20, 13); canvas.drawPath(shape, stroke);
                    shape.reset(); shape.moveTo(9, 16); shape.lineTo(10, 11);
                    shape.lineTo(19, 2); shape.lineTo(23, 6); shape.lineTo(14, 15); shape.close();
                    canvas.drawPath(shape, stroke); line(canvas, 17, 4, 21, 8);
                    if (inbox.unreadMessages() > 0) {
                        stroke.setStyle(Paint.Style.FILL); stroke.setColor(Color.rgb(239, 68, 68));
                        canvas.drawCircle(21, 2, 2, stroke);
                    }
                    break;
                case "book":
                    shape.moveTo(12, 5); shape.cubicTo(9, 3, 5, 3, 2, 4);
                    shape.lineTo(2, 20); shape.cubicTo(5, 19, 9, 19, 12, 21);
                    shape.cubicTo(15, 19, 19, 19, 22, 20); shape.lineTo(22, 4);
                    shape.cubicTo(19, 3, 15, 3, 12, 5); shape.lineTo(12, 21);
                    canvas.drawPath(shape, stroke);
                    break;
                case "box":
                    shape.moveTo(12, 2); shape.lineTo(22, 7); shape.lineTo(22, 17);
                    shape.lineTo(12, 22); shape.lineTo(2, 17); shape.lineTo(2, 7); shape.close();
                    canvas.drawPath(shape, stroke);
                    line(canvas, 2, 7, 12, 12); line(canvas, 22, 7, 12, 12);
                    line(canvas, 12, 12, 12, 22); line(canvas, 7, 4.5f, 17, 9.5f);
                    break;
                case "heart":
                case "heartFilled":
                    shape.moveTo(12, 21); shape.cubicTo(8, 17, 2, 13, 2, 7);
                    shape.cubicTo(2, 1, 9, 1, 12, 6); shape.cubicTo(15, 1, 22, 1, 22, 7);
                    shape.cubicTo(22, 13, 16, 17, 12, 21); shape.close();
                    if (type.equals("heartFilled")) { stroke.setStyle(Paint.Style.FILL); stroke.setColor(blue); }
                    canvas.drawPath(shape, stroke);
                    break;
                case "cart":
                    shape.moveTo(2, 3); shape.lineTo(5, 3); shape.lineTo(8, 16);
                    shape.lineTo(19, 16); canvas.drawPath(shape, stroke);
                    shape.reset(); shape.moveTo(6, 6); shape.lineTo(22, 6);
                    shape.lineTo(20, 13); shape.lineTo(8, 13); canvas.drawPath(shape, stroke);
                    canvas.drawCircle(9, 21, 1, stroke); canvas.drawCircle(18, 21, 1, stroke);
                    break;
                case "search":
                    canvas.drawCircle(10, 10, 6, stroke); line(canvas, 15, 15, 21, 21);
                    break;
                case "sliders":
                    line(canvas, 3, 6, 21, 6); line(canvas, 3, 12, 21, 12); line(canvas, 3, 18, 21, 18);
                    line(canvas, 8, 3, 8, 9); line(canvas, 16, 9, 16, 15); line(canvas, 10, 15, 10, 21);
                    break;
                case "payment":
                    canvas.drawRoundRect(new RectF(2, 4, 22, 20), 2, 2, stroke);
                    line(canvas, 2, 9, 22, 9); line(canvas, 6, 15, 11, 15);
                    break;
                case "shield":
                    shape.moveTo(12, 2); shape.lineTo(21, 6); shape.lineTo(20, 15);
                    shape.quadTo(19, 19, 12, 22); shape.quadTo(5, 19, 4, 15);
                    shape.lineTo(3, 6); shape.close(); canvas.drawPath(shape, stroke);
                    line(canvas, 8, 12, 11, 15); line(canvas, 11, 15, 17, 9);
                    break;
                case "note":
                    shape.moveTo(14, 3); shape.lineTo(7, 3); shape.lineTo(7, 11);
                    shape.moveTo(7, 19); shape.lineTo(7, 21); shape.lineTo(20, 21);
                    shape.lineTo(20, 9); shape.lineTo(14, 3); shape.lineTo(14, 9); shape.lineTo(20, 9);
                    canvas.drawPath(shape, stroke);
                    line(canvas, 3, 15, 11, 15); line(canvas, 7, 11, 7, 19);
                    break;
                case "tag":
                    shape.moveTo(3, 4); shape.lineTo(11, 4); shape.lineTo(21, 14);
                    shape.lineTo(14, 21); shape.lineTo(3, 10); shape.close();
                    canvas.drawPath(shape, stroke); canvas.drawCircle(7, 8, 1, stroke);
                    break;
                case "laptop":
                    canvas.drawRoundRect(new RectF(5, 4, 19, 16), 1.5f, 1.5f, stroke);
                    shape.moveTo(5, 16); shape.lineTo(2, 20); shape.lineTo(22, 20);
                    shape.lineTo(19, 16); shape.close(); canvas.drawPath(shape, stroke);
                    break;
                case "team":
                    canvas.drawCircle(10, 8, 3.5f, stroke);
                    canvas.drawArc(new RectF(3, 13, 17, 27), 180, 180, false, stroke);
                    canvas.drawArc(new RectF(12, 4.5f, 19, 11.5f), -80, 150, false, stroke);
                    canvas.drawArc(new RectF(11, 13, 23, 27), -85, 85, false, stroke);
                    break;
                case "bell":
                    shape.moveTo(5, 17); shape.lineTo(7, 13); shape.lineTo(7, 9);
                    shape.cubicTo(7, 2, 17, 2, 17, 9); shape.lineTo(17, 13);
                    shape.lineTo(19, 17); shape.close(); canvas.drawPath(shape, stroke);
                    canvas.drawArc(new RectF(10, 17, 14, 21), 0, 180, false, stroke);
                    if (notification.unreadNotifications() > 0) {
                        stroke.setStyle(Paint.Style.FILL); stroke.setColor(Color.rgb(239, 68, 68));
                        canvas.drawCircle(18, 5, 2.5f, stroke);
                    }
                    break;
                case "calendar":
                    canvas.drawRoundRect(new RectF(3, 5, 21, 21), 2, 2, stroke);
                    line(canvas, 3, 10, 21, 10); line(canvas, 8, 3, 8, 7); line(canvas, 16, 3, 16, 7);
                    line(canvas, 7, 14, 10, 14); line(canvas, 14, 14, 17, 14);
                    line(canvas, 7, 17, 10, 17);
                    break;
                case "pin":
                    shape.moveTo(12, 22); shape.cubicTo(10, 19, 5, 13, 5, 9);
                    shape.cubicTo(5, 0, 19, 0, 19, 9); shape.cubicTo(19, 13, 14, 19, 12, 22);
                    canvas.drawPath(shape, stroke); canvas.drawCircle(12, 9, 2.5f, stroke);
                    break;
                case "chevron":
                    line(canvas, 9, 6, 15, 12); line(canvas, 15, 12, 9, 18);
                    break;
                case "share":
                    canvas.drawCircle(6, 12, 2.4f, stroke);
                    canvas.drawCircle(18, 6, 2.4f, stroke);
                    canvas.drawCircle(18, 18, 2.4f, stroke);
                    line(canvas, 8, 11, 16, 7); line(canvas, 8, 13, 16, 17);
                    break;
                case "star":
                    for (int i = 0; i < 10; i++) {
                        double angle = -Math.PI / 2 + Math.PI * i / 5;
                        float radius = i % 2 == 0 ? 9 : 4;
                        float x = 12 + (float) Math.cos(angle) * radius;
                        float y = 12 + (float) Math.sin(angle) * radius;
                        if (i == 0) shape.moveTo(x, y); else shape.lineTo(x, y);
                    }
                    shape.close(); canvas.drawPath(shape, stroke);
                    break;
                case "close":
                    line(canvas, 6, 6, 18, 18); line(canvas, 18, 6, 6, 18);
                    break;
                case "menu":
                    line(canvas, 4, 6, 20, 6);
                    line(canvas, 4, 12, 20, 12);
                    line(canvas, 4, 18, 20, 18);
                    break;
                default: break;
            }
            canvas.restore();
        }
    }

    private class NavIconView extends View {
        private final String type;
        private final int iconColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        NavIconView(Context context, String type, int iconColor) {
            super(context);
            this.type = type;
            this.iconColor = iconColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            paint.setColor(iconColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ui.dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            if (type.equals("home")) {
                path.reset();
                path.moveTo(w * 0.16f, h * 0.50f);
                path.lineTo(w * 0.50f, h * 0.18f);
                path.lineTo(w * 0.84f, h * 0.50f);
                canvas.drawPath(path, paint);
                canvas.drawRoundRect(new RectF(w * 0.28f, h * 0.48f, w * 0.72f, h * 0.86f), ui.dp(2), ui.dp(2), paint);
            } else if (type.equals("academics")) {
                canvas.drawRoundRect(new RectF(w * 0.12f, h * 0.22f, w * 0.48f, h * 0.82f), ui.dp(2), ui.dp(2), paint);
                canvas.drawRoundRect(new RectF(w * 0.52f, h * 0.22f, w * 0.88f, h * 0.82f), ui.dp(2), ui.dp(2), paint);
                canvas.drawLine(w * 0.50f, h * 0.24f, w * 0.50f, h * 0.84f, paint);
            } else if (type.equals("market")) {
                canvas.drawRoundRect(new RectF(w * 0.22f, h * 0.34f, w * 0.78f, h * 0.84f), ui.dp(3), ui.dp(3), paint);
                canvas.drawLine(w * 0.36f, h * 0.34f, w * 0.36f, h * 0.24f, paint);
                canvas.drawLine(w * 0.64f, h * 0.34f, w * 0.64f, h * 0.24f, paint);
                canvas.drawLine(w * 0.36f, h * 0.24f, w * 0.64f, h * 0.24f, paint);
            } else if (type.equals("cart")) {
                canvas.drawRoundRect(new RectF(w * 0.14f, h * 0.36f, w * 0.86f, h * 0.78f), ui.dp(3), ui.dp(3), paint);
                path.reset();
                path.moveTo(w * 0.30f, h * 0.36f);
                path.lineTo(w * 0.38f, h * 0.16f);
                path.lineTo(w * 0.62f, h * 0.16f);
                path.lineTo(w * 0.70f, h * 0.36f);
                canvas.drawPath(path, paint);
                canvas.drawCircle(w * 0.32f, h * 0.88f, ui.dp(2), paint);
                canvas.drawCircle(w * 0.68f, h * 0.88f, ui.dp(2), paint);
            } else if (type.equals("inbox")) {
                canvas.drawRoundRect(new RectF(w * 0.16f, h * 0.20f, w * 0.84f, h * 0.70f), ui.dp(8), ui.dp(8), paint);
                path.reset();
                path.moveTo(w * 0.38f, h * 0.70f);
                path.lineTo(w * 0.30f, h * 0.84f);
                path.lineTo(w * 0.50f, h * 0.70f);
                canvas.drawPath(path, paint);
            } else {
                canvas.drawCircle(w * 0.50f, h * 0.32f, Math.min(w, h) * 0.16f, paint);
                canvas.drawRoundRect(new RectF(w * 0.28f, h * 0.56f, w * 0.72f, h * 0.88f), ui.dp(8), ui.dp(8), paint);
            }
        }
    }

    abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    static final class ModerationReport {
        final String id;
        final String productId;
        final String productTitle;
        final String reporterEmail;
        final String reporterName;
        final String reason;
        final String detail;
        final String priority;
        final long createdAt;
        String state;

        ModerationReport(String id, String productId, String productTitle, String reporterEmail, String reporterName,
                String reason, String detail, String priority, String state, long createdAt) {
            this.id = id;
            this.productId = productId;
            this.productTitle = productTitle;
            this.reporterEmail = reporterEmail;
            this.reporterName = reporterName;
            this.reason = reason;
            this.detail = detail;
            this.priority = priority;
            this.state = state;
            this.createdAt = createdAt;
        }

        boolean isOpen() { return state.equals("Open"); }
        String entityType() {
            if (productId.startsWith("review:")) return "review";
            if (productId.startsWith("user:")) return "user";
            if (productId.startsWith("listing:")) return "listing";
            return productId.isEmpty() ? "general" : "listing";
        }
        String entityLabel() {
            if (entityType().equals("review")) return "Review";
            if (entityType().equals("user")) return "Student";
            return "Listing";
        }
        String reportedEmail() {
            return entityType().equals("user") && productId.length() > 5 ? productId.substring(5) : "";
        }
        boolean close(boolean authorized, boolean dismiss) {
            if (!authorized || !isOpen()) return false;
            state = dismiss ? "Dismissed" : "Resolved";
            return true;
        }
        static int priorityRank(String priority) { return priority.equals("High") ? 3 : priority.equals("Medium") ? 2 : 1; }
    }

    static class AcademicItem {
        final String id;
        final String kind;
        final String module;
        final String title;
        final String detail;
        final int progress;
        final long due;
        final long end;
        final String location;
        final boolean saved;

        AcademicItem(String id, String kind, String module, String title, String detail, int progress, long due) {
            this(id, kind, module, title, detail, progress, due, 0, "", false);
        }
        AcademicItem(String id, String kind, String module, String title, String detail, int progress, long due, long end, String location, boolean saved) {
            this.id = id;
            this.kind = kind;
            this.module = module;
            this.title = title;
            this.detail = detail;
            this.progress = progress;
            this.due = due;
            this.end = end; this.location = location; this.saved = saved;
        }
        boolean scheduled() { return kind.equals("Deadline") || kind.equals("Event"); }
        boolean onDay(long day) {
            if (!scheduled()) return false;
            java.util.Calendar start = java.util.Calendar.getInstance(); start.setTimeInMillis(day);
            start.set(java.util.Calendar.HOUR_OF_DAY, 0); start.set(java.util.Calendar.MINUTE, 0);
            start.set(java.util.Calendar.SECOND, 0); start.set(java.util.Calendar.MILLISECOND, 0);
            java.util.Calendar next = (java.util.Calendar) start.clone(); next.add(java.util.Calendar.DAY_OF_MONTH, 1);
            return due < next.getTimeInMillis() && (kind.equals("Event") && end > due ? end > start.getTimeInMillis() : due >= start.getTimeInMillis());
        }
        static String scheduleError(String kind, long start, long end, long now) {
            if (!kind.equals("Event") && !kind.equals("Deadline")) return "";
            if (start <= now) return "Choose a future date and time";
            if (kind.equals("Event") && end != 0 && end <= start) return "Event end must be after its start";
            return "";
        }
    }

    static class ChatMessage {
        final String sender;
        final String recipient;
        final String senderName;
        final String body;
        final long time;
        boolean read;
        String remoteId = "";
        long readAt = 0;

        ChatMessage(String sender, String recipient, String senderName, String body, long time, boolean read) {
            this.sender = sender;
            this.recipient = recipient;
            this.senderName = senderName;
            this.body = body;
            this.time = time;
            this.read = read;
        }

        ChatMessage(String sender, String recipient, String senderName, String body, long time, boolean read,
                    String remoteId, long readAt) {
            this(sender, recipient, senderName, body, time, read);
            this.remoteId = remoteId == null ? "" : remoteId;
            this.readAt = readAt;
        }
    }

    static class Product {
        final String id;
        final String title;
        final String price;
        final String description;
        final String sellerEmail;
        String sellerName = "";
        final String category;
        final String condition;
        final String location;
        final String delivery;
        final String createdAt;
        String specs = "";
        final ArrayList<Uri> photos = new ArrayList<>();
        final LinkedHashSet<String> savedBy = new LinkedHashSet<>();
        Uri documentUri = null;
        String documentUrl = "";
        String documentName = "";
        String documentMime = "";
        long documentSize = 0;
        boolean documentDirty = false;
        String status;
        boolean hidden = false;
        boolean negotiable = true;
        int viewCount = 0;
        int favouriteCount = 0;
        long updatedTime = System.currentTimeMillis();
        long createdTime = System.currentTimeMillis();
        long expiresTime = 0;

        Product(String title, String price, String description, String sellerEmail, String category, String condition, String location, String delivery, String createdAt, String status) {
            this(title, price, description, sellerEmail, category, condition, location, delivery, createdAt, status, java.util.UUID.randomUUID().toString());
        }

        Product(String title, String price, String description, String sellerEmail, String category, String condition, String location, String delivery, String createdAt, String status, String id) {
            this.id = id;
            this.title = title;
            this.price = price;
            this.description = description;
            this.sellerEmail = sellerEmail;
            this.category = category;
            this.condition = condition;
            this.location = location;
            this.delivery = delivery;
            this.createdAt = createdAt;
            this.status = status;
        }

        boolean hasDocument() {
            return documentUri != null || (documentUrl != null && !documentUrl.isEmpty());
        }
    }

    enum PaymentMethod {
        CARD("Debit card", "Legacy card option. Not offered in new orders."),
        CREDIT("Credit card", "Legacy card option. Not offered in new orders."),
        EFT("EFT between students", "Arrange the bank transfer directly with the seller after they confirm."),
        INSTANT_EFT("Instant EFT", "Legacy instant-EFT option. Not offered in new orders."),
        CASH("Cash on collection", "Pay the seller in cash when you collect the item.");

        final String label;
        final String description;
        PaymentMethod(String label, String description) { this.label = label; this.description = description; }
        boolean usesCard() { return this == CARD || this == CREDIT; }
        boolean offered() { return this == EFT || this == CASH; }
        static PaymentMethod fromLabel(String label) {
            for (PaymentMethod method : values()) if (label != null && method.label.equalsIgnoreCase(label.trim())) return method;
            return CASH;
        }
    }

    static final class CardValidator {
        static String validationError(String holder, String number, String expiry, String code, long now) {
            if (holder == null || holder.trim().length() < 2 || holder.trim().length() > 100) return "Enter the cardholder name (2-100 characters).";
            if (number == null || !number.replaceAll("\\s", "").matches("\\d{16}")) return "Enter a valid 16-digit card number.";
            if (code == null || !code.matches("\\d{3}")) return "Use a 3-digit security code.";
            if (expiry == null || !expiry.matches("(0[1-9]|1[0-2])/[0-9]{2}")) return "Enter expiry as MM/YY.";
            java.util.Calendar calendar = java.util.Calendar.getInstance(); calendar.setTimeInMillis(now);
            int year = 2000 + Integer.parseInt(expiry.substring(3));
            int month = Integer.parseInt(expiry.substring(0, 2));
            if (year < calendar.get(java.util.Calendar.YEAR)
                    || (year == calendar.get(java.util.Calendar.YEAR) && month < calendar.get(java.util.Calendar.MONTH) + 1)) return "Choose a current or future expiry month.";
            return null;
        }
    }

    static final class OrderSummary {
        BigDecimal held = BigDecimal.ZERO.setScale(2);
        BigDecimal completed = BigDecimal.ZERO.setScale(2);

        static OrderSummary forUser(java.util.List<TransactionRecord> orders, String email, boolean buying) {
            OrderSummary summary = new OrderSummary();
            for (TransactionRecord order : orders) {
                if (!(buying ? order.isBuyer(email) : order.isSeller(email))) continue;
                if (order.status.equals(TransactionRecord.HELD)) summary.held = summary.held.add(order.value());
                else if (order.status.equals(TransactionRecord.COMPLETED)) summary.completed = summary.completed.add(order.value());
            }
            return summary;
        }
    }

    // One order per buyer and seller, from requested to completed.
    static final class TransactionRecord {
        static final String REQUESTED = "requested";
        static final String HELD = "held";
        static final String COMPLETED = "completed";
        static final String CANCELLED = "cancelled";
        final String id;
        final String itemTitle;
        final String amount;
        final String method;
        final String buyerName;
        final String buyerPhone;
        final String reference;
        final String paidAt;
        final String sellerEmail;
        final String buyerEmail;
        String sellerName = "";
        final String category;
        String status;
        final String productId;
        String cancellationRequester = "";
        String cancellationReason = "";
        String cancellationOutcome = "";
        long cancellationTime = 0;
        String returnRequester = "";
        String returnReason = "";
        String returnOutcome = "";
        long returnTime = 0;
        long payoutRequestedAt = 0;
        String payoutMethod = "Bank transfer", payoutSchedule = "On request";
        long createdTime = 0;
        // Delivery, collection proof and invoice details.
        String deliveryMethod = "Campus pickup";
        String deliveryAddress = "";
        double buyerLat = 0, buyerLng = 0, sellerLat = 0, sellerLng = 0;
        long buyerLocationAt = 0, sellerLocationAt = 0;
        String collectionPhotoUrl = "";
        String collectionPhotoBy = "";
        long collectionPhotoTime = 0;
        String sellerVerifiedBy = "";
        long sellerVerifiedTime = 0;
        String invoiceNumber = "";
        long invoiceFinalizedAt = 0;

        TransactionRecord(String id, String itemTitle, String amount, String method, String buyerName, String buyerPhone,
                String reference, String paidAt, String sellerEmail, String buyerEmail, String category, String status) {
            this(id, itemTitle, amount, method, buyerName, buyerPhone, reference, paidAt, sellerEmail, buyerEmail, category, status, "");
        }

        TransactionRecord(String id, String itemTitle, String amount, String method, String buyerName, String buyerPhone,
                String reference, String paidAt, String sellerEmail, String buyerEmail, String category, String status, String productId) {
            BigDecimal price = new BigDecimal(amount).setScale(2, java.math.RoundingMode.UNNECESSARY);
            if (price.signum() < 0 || (price.signum() == 0 && !method.equals("Free collection")) || sellerEmail.trim().isEmpty() || buyerEmail.trim().isEmpty()
                    || sellerEmail.trim().equalsIgnoreCase(buyerEmail.trim())) throw new IllegalArgumentException("Invalid order");
            if (!status.equals(REQUESTED) && !status.equals(HELD) && !status.equals(COMPLETED) && !status.equals(CANCELLED)) throw new IllegalArgumentException("Unknown order status");
            this.id = id;
            this.itemTitle = itemTitle;
            this.amount = price.toPlainString();
            this.method = method;
            this.buyerName = buyerName;
            this.buyerPhone = buyerPhone;
            this.reference = reference;
            this.paidAt = paidAt;
            this.sellerEmail = sellerEmail.trim().toLowerCase(Locale.ROOT);
            this.buyerEmail = buyerEmail.trim().toLowerCase(Locale.ROOT);
            this.category = category;
            this.status = status;
            this.productId = productId;
        }

        BigDecimal value() { return new BigDecimal(amount); }
        long createdMillis() {
            if (createdTime > 0) return createdTime;
            try {
                return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).parse(paidAt).getTime();
            } catch (java.text.ParseException e) {
                return 0;
            }
        }
        boolean isBuyer(String email) { return buyerEmail.equalsIgnoreCase(email.trim()); }
        boolean isSeller(String email) { return sellerEmail.equalsIgnoreCase(email.trim()); }
        String statusLabel() {
            if (status.equals(CANCELLED)) return "Cancelled";
            if (status.equals(REQUESTED)) return "Request pending";
            if (cancellationPending()) return "Cancellation requested";
            if (returnPending()) return "Return requested";
            if (status.equals(COMPLETED) && "Approved".equals(returnOutcome)) return "Return approved";
            if (status.equals(COMPLETED) && "Declined".equals(returnOutcome)) return "Return declined";
            if (status.equals(COMPLETED)) return "Completed";
            if (hasCollectionProof() && sellerVerifiedTime <= 0) return "Collection review";
            return deliveryOrder() ? "Out for delivery" : method.equals("Cash on collection") ? "Pay at collection" : "Awaiting collection";
        }
        String listingState() {
            if (status.equals(CANCELLED)) return "Available";
            if (status.equals(REQUESTED)) return "Reserved";
            return status.equals(COMPLETED) ? "Sold" : method.equals("Cash on collection") || method.equals("Free collection") ? "Reserved" : "Ordered";
        }

        boolean deliveryOrder() { return deliveryMethod != null && deliveryMethod.equals("Local delivery"); }
        boolean hasCollectionProof() { return collectionPhotoUrl != null && !collectionPhotoUrl.trim().isEmpty(); }
        boolean collectionProofPending() { return status.equals(HELD) && hasCollectionProof() && sellerVerifiedTime <= 0; }

        /** Live maps and location sharing are only for an active delivery. */
        boolean canShareLiveLocation() {
            return deliveryOrder() && status.equals(HELD) && !cancellationPending();
        }

        /** Remove any shared positions once the handover is finished or closed. */
        void clearSharedLocations() {
            buyerLat = 0; buyerLng = 0; buyerLocationAt = 0;
            sellerLat = 0; sellerLng = 0; sellerLocationAt = 0;
        }

        boolean approveBySeller(String email) {
            if (!isSeller(email) || !status.equals(REQUESTED)) return false;
            status = HELD;
            return true;
        }
        boolean declineBySeller(String email, long now) {
            if (!isSeller(email) || !status.equals(REQUESTED)) return false;
            status = CANCELLED; cancellationOutcome = "Declined by seller"; cancellationTime = now;
            return true;
        }
        boolean cancelRequestByBuyer(String email, long now) {
            if (!isBuyer(email) || !status.equals(REQUESTED)) return false;
            status = CANCELLED; cancellationOutcome = "Withdrawn by buyer"; cancellationTime = now;
            return true;
        }

        // The buyer photographs the handover. Only the seller can complete the
        // order, after reviewing that photo.
        boolean submitCollectionProof(String email, String photoUrl, long now) {
            if (!isBuyer(email) || !status.equals(HELD) || cancellationPending()
                    || photoUrl == null || photoUrl.trim().isEmpty()) return false;
            collectionPhotoUrl = photoUrl.trim();
            collectionPhotoBy = buyerEmail;
            collectionPhotoTime = now;
            sellerVerifiedBy = "";
            sellerVerifiedTime = 0;
            return true;
        }
        boolean verifyCollectionBySeller(String email, long now, String invoice) {
            if (!isSeller(email) || !status.equals(HELD) || cancellationPending()
                    || !hasCollectionProof() || !collectionPhotoBy.equalsIgnoreCase(buyerEmail)) return false;
            status = COMPLETED;
            sellerVerifiedBy = sellerEmail;
            sellerVerifiedTime = now;
            clearSharedLocations();
            if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
                invoiceNumber = invoice == null ? "" : invoice.trim();
            }
            if (invoiceFinalizedAt <= 0) invoiceFinalizedAt = now;
            return true;
        }

        boolean cancellationPending() { return status.equals(HELD) && !cancellationRequester.isEmpty(); }
        boolean requestCancellation(String email, String reason, long now) {
            if (!(isBuyer(email) || isSeller(email)) || !status.equals(HELD) || cancellationPending() || reason.trim().isEmpty()) return false;
            cancellationRequester = email.trim().toLowerCase(Locale.ROOT);
            cancellationReason = reason.trim(); cancellationOutcome = "Requested"; cancellationTime = now;
            return true;
        }
        boolean resolveCancellation(String email, boolean approve, long now) {
            if (!(isBuyer(email) || isSeller(email)) || !cancellationPending() || cancellationRequester.equalsIgnoreCase(email.trim())) return false;
            cancellationRequester = ""; cancellationOutcome = approve ? "Approved" : "Declined"; cancellationTime = now;
            if (approve) { status = CANCELLED; clearSharedLocations(); }
            return true;
        }
        boolean withdrawCancellation(String email, long now) {
            if (!cancellationPending() || !cancellationRequester.equalsIgnoreCase(email.trim())) return false;
            cancellationRequester = ""; cancellationOutcome = "Withdrawn"; cancellationTime = now; return true;
        }
        // A buyer can ask to return a completed order; the seller decides.
        boolean returnPending() {
            return status.equals(COMPLETED) && "Requested".equals(returnOutcome) && !returnRequester.isEmpty();
        }
        boolean requestReturn(String email, String reason, long now) {
            if (!isBuyer(email) || !status.equals(COMPLETED) || returnPending()
                    || !returnOutcome.isEmpty() || reason.trim().isEmpty()) return false;
            returnRequester = email.trim().toLowerCase(Locale.ROOT);
            returnReason = reason.trim();
            returnOutcome = "Requested";
            returnTime = now;
            return true;
        }
        boolean resolveReturn(String email, boolean approve, long now) {
            if (!isSeller(email) || !returnPending()) return false;
            returnRequester = "";
            returnOutcome = approve ? "Approved" : "Declined";
            returnTime = now;
            return true;
        }
        boolean withdrawReturn(String email, long now) {
            if (!returnPending() || !returnRequester.equalsIgnoreCase(email.trim())) return false;
            returnRequester = "";
            returnOutcome = "Withdrawn";
            returnTime = now;
            return true;
        }
        boolean requestPayout(String email, long now) {
            return requestPayout(email, now, "Bank transfer", "On request");
        }
        boolean requestPayout(String email, long now, String method, String schedule) {
            if (!isSeller(email) || !status.equals(COMPLETED) || value().signum() == 0
                    || this.method.equals("Cash on collection") || payoutRequestedAt != 0 || now <= 0) return false;
            if (!(method.equals("Bank transfer") || method.equals("Payment-provider balance"))
                    || !(schedule.equals("After collection") || schedule.equals("Weekly") || schedule.equals("On request"))) return false;
            payoutMethod = method; payoutSchedule = schedule; payoutRequestedAt = now; return true;
        }
    }

    // Work out the fee estimate, buyer total and seller total.
    static final class PaymentEstimate {
        final BigDecimal fee, buyerTotal, sellerProceeds;
        PaymentEstimate(String price, String discount, String delivery, String percent, boolean buyerPaysFee) {
            BigDecimal item = estimateAmount(price), reduction = estimateAmount(discount), shipping = estimateAmount(delivery);
            BigDecimal rate = estimateAmount(percent);
            if (reduction.compareTo(item) > 0 || rate.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("Check discount and fee percentage");
            BigDecimal net = item.subtract(reduction);
            fee = net.multiply(rate).divide(new BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP);
            buyerTotal = net.add(shipping).add(buyerPaysFee ? fee : BigDecimal.ZERO).setScale(2);
            sellerProceeds = net.subtract(buyerPaysFee ? BigDecimal.ZERO : fee).setScale(2);
        }
        private static BigDecimal estimateAmount(String value) {
            if (!value.matches(Constants.PRICE_PATTERN)) throw new IllegalArgumentException("Enter non-negative amounts with up to 2 decimal places");
            return new BigDecimal(value).setScale(2);
        }
    }

}