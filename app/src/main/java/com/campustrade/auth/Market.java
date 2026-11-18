package com.campustrade.auth;

import android.app.Dialog;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.content.res.ColorStateList;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.io.InputStream;
import java.math.BigDecimal;
import org.json.JSONArray;
import org.json.JSONObject;
import com.campustrade.auth.data.CloudSync;
import com.campustrade.auth.data.HelperBot;
import com.campustrade.auth.data.ListingRepository;
import com.campustrade.auth.model.Chat;
import android.text.TextUtils;

// Marketplace for browsing, listing and offers.
public class Market {

    private final MainActivity host;
    private com.google.firebase.Timestamp marketCursor = null;
    private boolean marketPageBusy = false;
    private boolean marketPageEnded = false;
    private Button marketLoadMore;
    private boolean offersPulling = false;
    private long lastOffersPullAt = 0;
    private com.google.firebase.firestore.ListenerRegistration listingsListener;
    private com.google.firebase.firestore.ListenerRegistration deletedListingsListener;
    private com.google.firebase.firestore.ListenerRegistration offersBuyerListener;
    private com.google.firebase.firestore.ListenerRegistration offersSellerListener;
    private Dialog currentOfferPage;
    private String currentOfferId = "";

    public Market(MainActivity host) {
        this.host = host;
    }

    // Build Market with search, filters and listings.
    public void buildMarket(LinearLayout content) {
        LinearLayout searchRow = host.ui.horizontal();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(host.ui.dp(12), 0, host.ui.dp(4), 0);
        searchRow.setBackground(host.ui.strokedRound(host.surface(), 8, host.border));
        searchRow.setMinimumHeight(host.ui.dp(50));
        searchRow.addView(host.new CampusAssetView(host, "search", host.muted), new LinearLayout.LayoutParams(host.ui.dp(20), host.ui.dp(20)));
        EditText marketSearch = host.ui.input("Search books, tech, furniture", InputType.TYPE_CLASS_TEXT);
        marketSearch.setTextSize(13);
        marketSearch.setBackgroundColor(Color.TRANSPARENT);
        marketSearch.setText(host.marketQuery);
        // Search as the student types; the same search feeds filter counts.
        marketSearch.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) {
                host.marketQuery = s.toString();
                renderListings();
            }
        });
        searchRow.addView(marketSearch, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        boolean filtersActive = !host.marketFilter.equals("All") || !host.marketCategory.equals("All")
                || !host.marketMinPrice.isEmpty() || !host.marketMaxPrice.isEmpty();
        View filter = host.new CampusAssetView(host, "sliders", filtersActive ? host.blue : host.muted);
        filter.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        filter.setContentDescription("Filter listings");
        filter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        filter.setOnClickListener(v -> showMarketplaceFilters());
        searchRow.addView(filter, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(46)));
        content.addView(searchRow);
        content.addView(marketFilterRow());
        // Offers, wanted posts, alerts and orders now live in the navigation
        // drawer, so the marketplace itself stays focused on browsing.
        LinearLayout heading = host.ui.horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, host.ui.dp(12), 0, 0);
        heading.addView(host.ui.titleText("New listings", host.text, 16), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View layoutToggle = host.new CampusAssetView(host, host.marketGrid ? "grid" : "list", host.muted);
        layoutToggle.setPadding(host.ui.dp(9), host.ui.dp(9), host.ui.dp(9), host.ui.dp(9));
        layoutToggle.setContentDescription(host.marketGrid ? "Switch to list view" : "Switch to grid view");
        layoutToggle.setBackground(host.ui.strokedRound(host.page, 999, host.border));
        layoutToggle.setOnClickListener(v -> {
            host.marketGrid = !host.marketGrid;
            host.showAppScreen("Market");
        });
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(host.ui.dp(36), host.ui.dp(36));
        layoutParams.rightMargin = host.ui.dp(4);
        heading.addView(layoutToggle, layoutParams);
        TextView sort = host.ui.textButton(host.marketSort);
        sort.setPadding(host.ui.dp(10), host.ui.dp(8), 0, host.ui.dp(8));
        sort.setOnClickListener(v -> {
            String[] sorts = {"Newest", "Price: low to high", "Price: high to low"};
            int checked = 0;
            for (int i = 0; i < sorts.length; i++) if (sorts[i].equals(host.marketSort)) checked = i;
            host.ui.dialog().setTitle("Sort listings")
                    .setSingleChoiceItems(sorts, checked, (d, which) -> {
                        host.marketSort = sorts[which]; d.dismiss(); host.showAppScreen("Market");
                    }).show();
        });
        heading.addView(sort);
        content.addView(heading);
        host.listingsContainer = new LinearLayout(host);
        host.listingsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(host.listingsContainer);
        marketLoadMore = host.ui.secondaryButton(marketPageEnded ? "All caught up" : "Load more listings");
        marketLoadMore.setEnabled(!marketPageEnded && !marketPageBusy);
        marketLoadMore.setVisibility(marketPageEnded ? View.GONE : View.VISIBLE);
        marketLoadMore.setOnClickListener(v -> loadMoreMarket());
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        moreParams.topMargin = host.ui.dp(10);
        content.addView(marketLoadMore, moreParams);
        // Draw local listings first, then load shared ones.
        cloudMergeMarket();
        renderListings();
        LinearLayout safety = host.ui.horizontal();
        safety.setGravity(Gravity.CENTER_VERTICAL);
        safety.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        safety.setBackground(host.ui.strokedRound(host.nightActive ? Color.rgb(55, 47, 28) : Color.rgb(255, 244, 218), 12,
                host.nightActive ? Color.rgb(217, 139, 0) : Color.rgb(217, 139, 0)));
        safety.addView(host.new CampusAssetView(host, "shield", host.readableInk(Color.rgb(217, 139, 0))), new LinearLayout.LayoutParams(host.ui.dp(22), host.ui.dp(22)));
        TextView safetyText = host.ui.bodyText("Meet in a public campus pickup point and inspect before paying.",
                host.nightActive ? Color.rgb(255, 226, 168) : Color.rgb(92, 74, 30), 11);
        safetyText.setPadding(host.ui.dp(10), 0, 0, 0);
        safety.addView(safetyText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView openSafety = host.ui.linkText("Safety center");
        openSafety.setPadding(host.ui.dp(10), 0, 0, 0);
        openSafety.setOnClickListener(v -> SafetyCenter.show(host));
        safety.addView(openSafety);
        LinearLayout.LayoutParams safetyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        safetyParams.topMargin = host.ui.dp(14);
        content.addView(safety, safetyParams);
    }

    // Show live counts but only change filters on Apply.
    public void showMarketplaceFilters() {
        Dialog dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        String[] values = {"All", "Available", "Saved"};
        String[] labels = {"All listings", "Available only", "Saved listings"};
        String[] icons = {"bag", "check", "bookmark"};
        final String[] selected = {host.marketFilter.equals("Available") || host.marketFilter.equals("Saved") ? host.marketFilter : "All"};
        final String[] selectedCategory = {host.marketCategory == null ? "All" : host.marketCategory};
        LinearLayout sheet = new LinearLayout(host);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(20));
        sheet.setBackground(host.ui.topRound(host.surface(), 20));
        View handle = new View(host);
        handle.setBackground(host.ui.round(host.border, 999));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(host.ui.dp(32), host.ui.dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = host.ui.dp(12);
        sheet.addView(handle, handleParams);
        LinearLayout header = host.ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(host.ui.titleText("Filter listings", host.text, 20), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton close = host.iconButton(android.R.drawable.ic_menu_close_clear_cancel, "Close filters");
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        sheet.addView(header);

        ScrollView scroll = new ScrollView(host);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout options = new LinearLayout(host);
        options.setOrientation(LinearLayout.VERTICAL);
        LinearLayout[] rows = new LinearLayout[values.length];
        RadioButton[] buttons = new RadioButton[values.length];
        for (int i = 0; i < values.length; i++) {
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(host.ui.dp(12), host.ui.dp(10), host.ui.dp(4), host.ui.dp(10));
            View symbol = host.new CampusAssetView(host, icons[i], host.blue);
            symbol.setPadding(host.ui.dp(7), host.ui.dp(7), host.ui.dp(7), host.ui.dp(7));
            symbol.setBackground(host.ui.round(Color.rgb(234, 242, 255), 8));
            row.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(36), host.ui.dp(36)));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(12), 0, host.ui.dp(4), 0);
            TextView label = host.ui.titleText(labels[i], host.text, 14);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(label);
            int count = marketplaceFilterCount(values[i]);
            copy.addView(host.ui.bodyText(count + (count == 1 ? " listing" : " listings"), host.muted, 12));
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            RadioButton radio = new RadioButton(host);
            host.ui.themedToggle(radio);
            radio.setContentDescription(labels[i]);
            row.addView(radio, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = host.ui.dp(8);
            options.addView(row, params);
            rows[i] = row;
            buttons[i] = radio;
        }

        TextView categoryHeading = host.ui.label("Category");
        categoryHeading.setPadding(0, host.ui.dp(16), 0, host.ui.dp(8));
        options.addView(categoryHeading);
        TextView[] categoryChips = new TextView[host.CATEGORIES.length + 1];
        HorizontalScrollView categoryScroll = new HorizontalScrollView(host);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryRow = host.ui.horizontal();
        String[] categoryValues = new String[host.CATEGORIES.length + 1];
        categoryValues[0] = "All";
        for (int i = 0; i < host.CATEGORIES.length; i++) categoryValues[i + 1] = host.CATEGORIES[i];
        for (int i = 0; i < categoryValues.length; i++) {
            final String category = categoryValues[i];
            TextView chip = host.ui.filterPill(category.equals("All") ? "All categories" : category, false);
            chip.setTextSize(12);
            chip.setSingleLine(true);
            chip.setOnClickListener(v -> {
                selectedCategory[0] = category;
                for (int c = 0; c < categoryChips.length; c++) {
                    String value = categoryValues[c];
                    boolean active = value.equals(selectedCategory[0]);
                    categoryChips[c].setTextColor(active ? Color.WHITE : host.text);
                    categoryChips[c].setBackground(active ? host.ui.round(host.blue, 999) : host.ui.strokedRound(host.surface(), 999, host.border));
                }
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(40));
            if (i > 0) chipParams.leftMargin = host.ui.dp(8);
            categoryRow.addView(chip, chipParams);
            categoryChips[i] = chip;
        }
        for (int i = 0; i < categoryChips.length; i++) {
            boolean active = categoryValues[i].equals(selectedCategory[0]);
            categoryChips[i].setTextColor(active ? Color.WHITE : host.text);
            categoryChips[i].setBackground(active ? host.ui.round(host.blue, 999) : host.ui.strokedRound(host.surface(), 999, host.border));
        }
        categoryScroll.addView(categoryRow);
        options.addView(categoryScroll);

        TextView priceHeading = host.ui.label("Price range");
        priceHeading.setPadding(0, host.ui.dp(18), 0, host.ui.dp(8));
        options.addView(priceHeading);
        LinearLayout priceRow = host.ui.horizontal();
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText minPrice = host.ui.input("Min", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        minPrice.setText(host.marketMinPrice);
        EditText maxPrice = host.ui.input("Max", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        maxPrice.setText(host.marketMaxPrice);
        priceRow.addView(minPrice, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        TextView to = host.ui.bodyText("to", host.muted, 12);
        to.setGravity(Gravity.CENTER);
        priceRow.addView(to, new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(48)));
        priceRow.addView(maxPrice, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        options.addView(priceRow);

        scroll.addView(options);
        int optionHeight = Math.min(host.ui.dp(430), (int) (host.getResources().getDisplayMetrics().heightPixels * 0.56f));
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, optionHeight));
        TextView resultCount = host.ui.bodyText("", host.muted, 12);
        resultCount.setPadding(0, host.ui.dp(16), 0, host.ui.dp(12));
        sheet.addView(resultCount);
        Runnable refreshSelection = () -> {
            for (int i = 0; i < values.length; i++) {
                boolean active = selected[0].equals(values[i]);
                buttons[i].setChecked(active);
                rows[i].setSelected(active);
                rows[i].setBackground(host.ui.strokedRound(active
                                ? (host.nightActive ? Color.rgb(40, 52, 70) : Color.rgb(244, 248, 255))
                                : host.surface(), 8, active ? host.blue : host.line));
            }
            int count = marketplaceFilterCount(selected[0]);
            resultCount.setText(count + (count == 1 ? " matching listing" : " matching listings"));
        };
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            View.OnClickListener choose = v -> { selected[0] = value; refreshSelection.run(); };
            rows[i].setOnClickListener(choose);
            buttons[i].setOnClickListener(choose);
        }
        LinearLayout footer = host.ui.horizontal();
        Button reset = host.ui.secondaryButton("Reset");
        reset.setTextColor(host.blue);
        reset.setBackground(host.ui.strokedRound(host.surface(), 8, host.border));
        reset.setOnClickListener(v -> {
            selected[0] = "All";
            selectedCategory[0] = "All";
            minPrice.setText("");
            maxPrice.setText("");
            for (int c = 0; c < categoryChips.length; c++) {
                boolean active = categoryValues[c].equals("All");
                categoryChips[c].setTextColor(active ? Color.WHITE : host.text);
                categoryChips[c].setBackground(active ? host.ui.round(host.blue, 999) : host.ui.strokedRound(host.surface(), 999, host.border));
            }
            refreshSelection.run();
        });
        footer.addView(reset, new LinearLayout.LayoutParams(host.ui.dp(84), host.ui.dp(48)));
        footer.addView(host.ui.space(host.ui.dp(10), 1));
        Button apply = host.ui.primaryButton("Apply filters");
        apply.setBackground(host.ui.round(host.primary, 8));
        apply.setOnClickListener(v -> {
            String min = host.ui.text(minPrice).trim();
            String max = host.ui.text(maxPrice).trim();
            if (!min.isEmpty() && !min.matches(Constants.PRICE_PATTERN)) {
                minPrice.setError("Enter a valid amount");
                return;
            }
            if (!max.isEmpty() && !max.matches(Constants.PRICE_PATTERN)) {
                maxPrice.setError("Enter a valid amount");
                return;
            }
            host.marketFilter = selected[0];
            host.marketCategory = selectedCategory[0];
            host.marketMinPrice = min;
            host.marketMaxPrice = max;
            dialog.dismiss();
            host.showAppScreen("Market");
        });
        footer.addView(apply, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        sheet.addView(footer);
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        refreshSelection.run();
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(host.getWindow().getDecorView().getWindowToken(), 0);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.35f);
        }
    }

    // Count what each filter would show.
    public int marketplaceFilterCount(String filter) {
        int count = 0;
        for (MainActivity.Product product : host.products) {
            if (!publicListing(product) && !(filter.equals("Mine") && ownsListing(product))) continue;
            if (filter.equals("Available") && !product.status.equals("Available")) continue;
            if (filter.equals("Saved") && !product.savedBy.contains(host.currentStudentEmail)) continue;
            String searchable = product.title + " " + product.category + " " + product.description
                    + " " + product.documentName + " " + product.delivery + " " + product.specs;
            if (!searchable.toLowerCase(Locale.ROOT).contains(host.marketQuery.trim().toLowerCase(Locale.ROOT))) continue;
            count++;
        }
        return count;
    }

    public void showAddProductDialog() {
        if (!host.settingsPrefs().getBoolean("listing_terms_acknowledged", false)) {
            showListingTermsDialog();
        } else {
            showProductEditor(null);
        }
    }

    // T&Cs and publishing risks are shown before the listing editor opens. The
    // student can choose not to see this reminder again; the review step still
    // requires its own acknowledgement before publication.
    private void showListingTermsDialog() {
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        body.addView(host.ui.bodyText("Read these terms before you list anything. CampusTrade only connects students; you are responsible for the accuracy of the listing, meeting safely and settling cash or EFT directly.",
                host.muted, 12));
        body.addView(host.ui.bodyText("CampusTrade does not process payment or refunds and is not liable for theft, fraud, loss, injury, non-delivery or losses outside the app, except where the law does not allow that liability to be excluded.",
                host.muted, 11));
        TextView fullTerms = host.ui.linkText("Read the full Terms & Conditions");
        fullTerms.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
        fullTerms.setOnClickListener(v -> Terms.show(host));
        body.addView(fullTerms);
        CheckBox acknowledge = new CheckBox(host);
        acknowledge.setText("I have read the Terms & Conditions and understand the publishing risks");
        acknowledge.setTextSize(12);
        host.ui.themedToggle(acknowledge);
        body.addView(acknowledge);
        CheckBox dontRemind = new CheckBox(host);
        dontRemind.setText("Don't remind me again");
        dontRemind.setTextSize(12);
        host.ui.themedToggle(dontRemind);
        body.addView(dontRemind);
        Button continueButton = host.ui.primaryButton("Create listing");
        continueButton.setEnabled(false);
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        continueParams.topMargin = host.ui.dp(12);
        body.addView(continueButton, continueParams);
        Button cancel = host.ui.secondaryButton("Cancel");
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44));
        cancelParams.topMargin = host.ui.dp(6);
        body.addView(cancel, cancelParams);
        Dialog sheet = host.ui.showBottomSheet("Listing terms", body);
        acknowledge.setOnCheckedChangeListener((button, checked) -> continueButton.setEnabled(checked));
        continueButton.setOnClickListener(v -> {
            if (dontRemind.isChecked()) {
                host.settingsPrefs().edit().putBoolean("listing_terms_acknowledged", true).apply();
            }
            sheet.dismiss();
            showProductEditor(null);
        });
        cancel.setOnClickListener(v -> sheet.dismiss());
    }

    // One editor for new and existing listings; drafts stay here.
    public void showProductEditor(MainActivity.Product existing) {
        if (host.listingDialog != null && host.listingDialog.isShowing()) return;
        host.draftPhotos.clear();
        host.draftDocumentUri = null;
        host.draftDocumentUrl = "";
        host.draftDocumentName = "";
        host.draftDocumentMime = "";
        host.draftDocumentSize = 0;
        if (existing != null) {
            host.draftPhotos.addAll(existing.photos);
            host.draftDocumentUri = existing.documentUri;
            host.draftDocumentUrl = existing.documentUrl;
            host.draftDocumentName = existing.documentName;
            host.draftDocumentMime = existing.documentMime;
            host.draftDocumentSize = existing.documentSize;
        }

        final Dialog dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        host.listingDialog = dialog;
        LinearLayout editor = new LinearLayout(host);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setBackgroundColor(host.page);

        LinearLayout header = host.ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(host.ui.dp(20), host.ui.dp(14), host.ui.dp(16), host.ui.dp(10));
        LinearLayout heading = new LinearLayout(host);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(host.ui.titleText(existing == null ? "List an item" : "Edit listing", host.text, 22));
        heading.addView(host.ui.bodyText("Campus Marketplace · 3 quick steps", host.muted, 11));
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton close = new ImageButton(host);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setImageTintList(android.content.res.ColorStateList.valueOf(host.muted));
        close.setContentDescription("Close listing editor");
        close.setBackground(host.ui.round(host.page, 8));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        editor.addView(header);

        // Show progress at every step.
        LinearLayout progress = host.ui.horizontal();
        progress.setPadding(host.ui.dp(20), 0, host.ui.dp(20), host.ui.dp(10));
        String[] stepNames = {"1  Details", "2  Price", "3  Review"};
        TextView[] stepChips = new TextView[3];
        for (int i = 0; i < stepNames.length; i++) {
            TextView chip = host.ui.bodyText(stepNames[i], host.muted, 11);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            chip.setPadding(host.ui.dp(6), host.ui.dp(7), host.ui.dp(6), host.ui.dp(7));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, host.ui.dp(34), 1);
            if (i > 0) chipParams.leftMargin = host.ui.dp(6);
            progress.addView(chip, chipParams);
            stepChips[i] = chip;
        }
        editor.addView(progress);

        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        editor.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(form);

        final LinearLayout stepDetails = new LinearLayout(host);
        stepDetails.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout stepPrice = new LinearLayout(host);
        stepPrice.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout stepReview = new LinearLayout(host);
        stepReview.setOrientation(LinearLayout.VERTICAL);
        form.addView(stepDetails);
        form.addView(stepPrice);
        form.addView(stepReview);

        // Step 1: photos, document and item details.
        listingSection(stepDetails, "Photos");
        host.listingPhotoCount = host.ui.bodyText("0 / 5 photos", host.muted, 12);
        stepDetails.addView(host.listingPhotoCount);
        HorizontalScrollView photos = new HorizontalScrollView(host);
        photos.setHorizontalScrollBarEnabled(false);
        host.listingPhotoContainer = host.ui.horizontal();
        host.listingPhotoContainer.setPadding(0, host.ui.dp(12), 0, host.ui.dp(8));
        photos.addView(host.listingPhotoContainer);
        stepDetails.addView(photos);
        Button addPhotos = host.ui.secondaryButton("Add photos");
        addPhotos.setTextColor(host.blue);
        addPhotos.setBackground(host.ui.strokedRound(host.surface(), 8, host.border));
        addPhotos.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_camera, 0, 0, 0);
        addPhotos.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(host.blue));
        addPhotos.setOnClickListener(v -> pickListingPhotos());
        stepDetails.addView(addPhotos, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));

        listingSection(stepDetails, "Document (optional)");
        stepDetails.addView(host.ui.bodyText("One PDF, Word, PowerPoint, RTF or TXT file up to 10 MB.",
                host.muted, 12));
        host.listingDocumentContainer = new LinearLayout(host);
        host.listingDocumentContainer.setOrientation(LinearLayout.VERTICAL);
        host.listingDocumentContainer.setPadding(0, host.ui.dp(12), 0, 0);
        stepDetails.addView(host.listingDocumentContainer);

        listingSection(stepDetails, "Item details");
        EditText titleInput = host.ui.listingField(stepDetails, "Product title", "What are you selling?", InputType.TYPE_CLASS_TEXT);
        titleInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(80) });

        TextView categoryLabel = host.ui.label("Category");
        categoryLabel.setPadding(0, host.ui.dp(12), 0, host.ui.dp(8));
        stepDetails.addView(categoryLabel);
        final LinearLayout specsBox = new LinearLayout(host);
        specsBox.setOrientation(LinearLayout.VERTICAL);
        specsBox.setVisibility(View.GONE);
        final TextView categoryError = host.ui.bodyText("Choose a category before continuing.", host.nightActive
                ? Color.rgb(255, 125, 146) : Color.rgb(190, 18, 60), 11);
        categoryError.setVisibility(View.GONE);
        final String[] selectedCategory = {existing == null ? "" : existing.category};
        final TextView[] categoryChips = new TextView[host.CATEGORIES.length];
        String[] categoryValues = host.CATEGORIES.clone();
        HorizontalScrollView categoryScroll = new HorizontalScrollView(host);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryRow = host.ui.horizontal();
        for (int i = 0; i < categoryValues.length; i++) {
            final String value = categoryValues[i];
            TextView chip = host.ui.filterPill(value, false);
            chip.setTextSize(12);
            chip.setSingleLine(true);
            chip.setOnClickListener(v -> {
                selectedCategory[0] = value;
                refreshCategoryChips(categoryChips, categoryValues, selectedCategory[0]);
                categoryError.setVisibility(View.GONE);
                specsBox.setVisibility(value.equals("Technology") ? View.VISIBLE : View.GONE);
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(40));
            if (i > 0) chipParams.leftMargin = host.ui.dp(8);
            categoryRow.addView(chip, chipParams);
            categoryChips[i] = chip;
        }
        categoryScroll.addView(categoryRow);
        stepDetails.addView(categoryScroll);
        refreshCategoryChips(categoryChips, categoryValues, selectedCategory[0]);
        stepDetails.addView(categoryError);

        Spinner conditionInput = host.ui.listingSelector(stepDetails, "Condition", new String[] {
                "Choose a condition", "New", "Like new", "Good", "Fair" });
        final TextView conditionError = host.ui.bodyText("Choose the item's condition.", host.nightActive
                ? Color.rgb(255, 125, 146) : Color.rgb(190, 18, 60), 11);
        conditionError.setVisibility(View.GONE);
        stepDetails.addView(conditionError);

        EditText descriptionInput = host.ui.listingField(stepDetails, "Description", "Details about your item",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        descriptionInput.setSingleLine(false);
        descriptionInput.setMinLines(3);
        descriptionInput.setGravity(Gravity.TOP | Gravity.START);
        descriptionInput.setPadding(host.ui.dp(14), host.ui.dp(12), host.ui.dp(14), host.ui.dp(12));
        descriptionInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(120)));
        descriptionInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(1000) });
        TextView counter = host.ui.bodyText("0 / 1000", host.muted, 11);
        counter.setGravity(Gravity.END);
        counter.setPadding(0, host.ui.dp(4), 0, 0);
        stepDetails.addView(counter);
        descriptionInput.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { counter.setText(s.length() + " / 1000"); }
        });

        // Technology listings show a dedicated specs field.
        specsBox.setVisibility(existing != null && existing.category.equals("Technology") ? View.VISIBLE : View.GONE);
        specsBox.addView(host.ui.bodyText("Give buyers the full device specifications: model, processor, RAM, storage, screen, battery, ports and any faults.",
                host.muted, 11));
        EditText specsInput = host.ui.listingField(specsBox, "Tech specs", "e.g. 16 GB RAM, 512 GB SSD, Intel i7, 14-inch",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        specsInput.setSingleLine(false);
        specsInput.setMinLines(3);
        specsInput.setGravity(Gravity.TOP | Gravity.START);
        specsInput.setPadding(host.ui.dp(14), host.ui.dp(12), host.ui.dp(14), host.ui.dp(12));
        specsInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(110)));
        specsInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2000)});
        if (existing != null) specsInput.setText(existing.specs);
        stepDetails.addView(specsBox);

        // Step 2: price and collection.
        listingSection(stepPrice, "Price & collection");
        EditText priceInput = host.ui.listingField(stepPrice, "Price (ZAR)", "0.00",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText quantityInput = host.ui.listingField(stepPrice, "Quantity available",
                "How many can you sell? (0 = sold out)", InputType.TYPE_CLASS_NUMBER);
        quantityInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(3) });
        CheckBox negotiableInput = new CheckBox(host);
        negotiableInput.setText("Price is negotiable");
        negotiableInput.setTextSize(12);
        negotiableInput.setChecked(true);
        host.ui.themedToggle(negotiableInput);
        stepPrice.addView(negotiableInput);
        EditText locationInput = host.ui.listingField(stepPrice, "Pickup location", "Campus or public meeting point", InputType.TYPE_CLASS_TEXT);
        Spinner deliveryInput = host.ui.listingSelector(stepPrice, "Delivery", new String[] {
                "Campus pickup", "Delivery" });
        TextView safetyNote = host.ui.bodyText("Choose a busy campus spot for handovers and inspect the item before paying.",
                host.muted, 11);
        safetyNote.setPadding(0, host.ui.dp(14), 0, 0);
        stepPrice.addView(safetyNote);

        if (existing != null) {
            titleInput.setText(existing.title); priceInput.setText(existing.price); locationInput.setText(existing.location);
            quantityInput.setText(String.valueOf(Math.max(0, existing.quantity)));
            descriptionInput.setText(existing.description);
            negotiableInput.setChecked(existing.negotiable);
            host.ui.selectSpinner(conditionInput, existing.condition); host.ui.selectSpinner(deliveryInput,
                    existing.delivery != null && existing.delivery.toLowerCase(Locale.ROOT).contains("delivery")
                            ? "Delivery" : "Campus pickup");
        }
        final boolean[] pickupChosen = {deliveryInput.getSelectedItemPosition() == 0};
        final Runnable updatePickupField = () -> {
            pickupChosen[0] = deliveryInput.getSelectedItemPosition() == 0;
            locationInput.setEnabled(pickupChosen[0]);
            locationInput.setFocusable(pickupChosen[0]);
            locationInput.setAlpha(pickupChosen[0] ? 1f : 0.35f);
            locationInput.setHint(pickupChosen[0] ? "Campus or public meeting point" : "Not needed for delivery");
            if (!pickupChosen[0]) locationInput.setError(null);
        };
        deliveryInput.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
                                                 int position, long id) {
                updatePickupField.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        updatePickupField.run();

        // Step 3: final check before publishing.
        listingSection(stepReview, "Check your listing");
        final LinearLayout reviewBox = new LinearLayout(host);
        reviewBox.setOrientation(LinearLayout.VERTICAL);
        reviewBox.setPadding(host.ui.dp(14), host.ui.dp(6), host.ui.dp(14), host.ui.dp(6));
        reviewBox.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        stepReview.addView(reviewBox);
        TextView reviewNote = host.ui.bodyText("Use Back to change anything. Publish makes the listing visible to verified students immediately.",
                host.muted, 11);
        reviewNote.setPadding(0, host.ui.dp(12), 0, 0);
        stepReview.addView(reviewNote);

        // Publishing requires a fresh acknowledgement of the marketplace
        // risks and the Terms & Conditions, not just the login checkbox.
        LinearLayout riskCard = new LinearLayout(host);
        riskCard.setOrientation(LinearLayout.VERTICAL);
        riskCard.setPadding(host.ui.dp(14), host.ui.dp(12), host.ui.dp(14), host.ui.dp(12));
        int riskBackground = host.nightActive ? Color.rgb(54, 45, 30) : Color.rgb(255, 247, 229);
        int riskBorder = host.nightActive ? Color.rgb(105, 83, 44) : Color.rgb(236, 211, 160);
        riskCard.setBackground(host.ui.strokedRound(riskBackground, 12, riskBorder));
        LinearLayout.LayoutParams riskParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        riskParams.topMargin = host.ui.dp(14);
        stepReview.addView(riskCard, riskParams);
        riskCard.addView(host.ui.titleText("Publishing risks", host.text, 14));
        riskCard.addView(host.ui.bodyText("CampusTrade only connects students. You are responsible for the accuracy of this listing, meeting safely and settling cash or EFT directly. "
                + "CampusTrade does not process payment or refunds and is not liable for theft, fraud, loss, injury, non-delivery or losses outside the app, "
                + "except where the law does not allow that liability to be excluded.", host.muted, 11));
        TextView fullTerms = host.ui.linkText("Read the full Terms & Conditions");
        fullTerms.setPadding(0, host.ui.dp(8), 0, host.ui.dp(4));
        fullTerms.setOnClickListener(v -> Terms.show(host));
        riskCard.addView(fullTerms);
        CheckBox publishTerms = new CheckBox(host);
        publishTerms.setText("I have read the Terms & Conditions and understand these publishing risks");
        publishTerms.setTextSize(12);
        host.ui.themedToggle(publishTerms);
        riskCard.addView(publishTerms);

        LinearLayout footer = new LinearLayout(host);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(host.ui.dp(20), host.ui.dp(10), host.ui.dp(20), host.ui.dp(12));
        footer.setBackground(host.ui.edgeRule(host.text, true));
        LinearLayout footerButtons = host.ui.horizontal();
        Button backButton = host.ui.secondaryButton("Back");
        backButton.setVisibility(View.GONE);
        Button continueButton = host.ui.primaryButton("Continue");
        footerButtons.addView(backButton, new LinearLayout.LayoutParams(0, host.ui.dp(50), 1));
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(0, host.ui.dp(50), 1);
        continueParams.leftMargin = host.ui.dp(8);
        footerButtons.addView(continueButton, continueParams);
        footer.addView(footerButtons);
        TextView saveDraft = host.ui.textButton("Save as draft");
        saveDraft.setVisibility(existing == null ? View.VISIBLE : View.GONE);
        footer.addView(saveDraft, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(38)));
        editor.addView(footer);

        final int[] step = {0};
        final boolean[] savingDraft = {false};
        final Runnable fillReview = () -> {
            reviewBox.removeAllViews();
            reviewLine(reviewBox, "Title", host.ui.text(titleInput).trim());
            String priceText = host.ui.text(priceInput).trim().isEmpty() ? "0.00" : host.ui.text(priceInput).trim();
            reviewLine(reviewBox, "Price", new BigDecimal(priceText).signum() == 0 ? "Free" : "R" + priceText);
            reviewLine(reviewBox, "Quantity", host.ui.text(quantityInput).trim().isEmpty()
                    ? "1" : host.ui.text(quantityInput).trim());
            reviewLine(reviewBox, "Category", selectedCategory[0]);
            reviewLine(reviewBox, "Condition", conditionInput.getSelectedItemPosition() == 0
                    ? "Not set" : conditionInput.getSelectedItem().toString());
            if (selectedCategory[0].equals("Technology") && !host.ui.text(specsInput).trim().isEmpty()) {
                reviewLine(reviewBox, "Tech specs", host.ui.text(specsInput).trim());
            }
            reviewLine(reviewBox, "Pickup", pickupChosen[0]
                    ? host.ui.text(locationInput).trim() : "Not used for delivery");
            reviewLine(reviewBox, "Delivery", deliveryInput.getSelectedItem().toString());
            reviewLine(reviewBox, "Photos", host.draftPhotos.size() + " added");
            if (host.draftDocumentName != null && !host.draftDocumentName.isEmpty()) {
                reviewLine(reviewBox, "Document", host.draftDocumentName);
            }
        };
        final Runnable refreshGate = () -> {
            if (step[0] != 2) { continueButton.setEnabled(true); return; }
            String price = host.ui.text(priceInput).trim();
            boolean valid = host.ui.text(titleInput).trim().length() >= 3
                    && !selectedCategory[0].isEmpty()
                    && conditionInput.getSelectedItemPosition() > 0
                    && host.ui.text(descriptionInput).trim().length() >= 10
                    && price.matches(Constants.PRICE_PATTERN)
                    && host.ui.text(quantityInput).trim().matches("[0-9]{1,3}")
                    && (!pickupChosen[0] || !host.ui.text(locationInput).trim().isEmpty())
                    && publishTerms.isChecked();
            continueButton.setEnabled(valid);
            continueButton.setBackground(host.ui.round(valid ? host.primary
                    : host.nightActive ? Color.rgb(58, 58, 63) : Color.rgb(222, 220, 214), 4));
            continueButton.setTextColor(valid ? Color.WHITE : host.muted);
        };
        publishTerms.setOnCheckedChangeListener((button, checked) -> refreshGate.run());
        final Runnable renderStep = () -> {
            stepDetails.setVisibility(step[0] == 0 ? View.VISIBLE : View.GONE);
            stepPrice.setVisibility(step[0] == 1 ? View.VISIBLE : View.GONE);
            stepReview.setVisibility(step[0] == 2 ? View.VISIBLE : View.GONE);
            for (int i = 0; i < stepChips.length; i++) {
                boolean active = i == step[0];
                boolean complete = i < step[0];
                stepChips[i].setTextColor(active ? Color.WHITE : complete ? host.readableInk(host.green) : host.muted);
                stepChips[i].setBackground(active ? host.ui.round(host.blue, 999)
                        : host.ui.strokedRound(complete ? (host.nightActive ? Color.rgb(28, 55, 44) : Color.rgb(228, 246, 237))
                        : host.surface(), 999, complete ? host.green : host.line));
            }
            backButton.setVisibility(step[0] == 0 ? View.GONE : View.VISIBLE);
            continueButton.setText(step[0] == 2 ? (existing == null ? "Publish listing" : "Save changes") : "Continue");
            if (step[0] == 2) fillReview.run();
            refreshGate.run();
            scroll.scrollTo(0, 0);
        };
        final Runnable performSave = () -> {
            String title = host.ui.text(titleInput).trim();
            String price = host.ui.text(priceInput).trim();
            String location = host.ui.text(locationInput).trim();
            String description = host.ui.text(descriptionInput).trim();
            if (!savingDraft[0] && !publishTerms.isChecked()) {
                step[0] = 2; renderStep.run();
                host.ui.showSimpleMessage("Terms & Conditions",
                        "Tick the acknowledgement before publishing this listing.");
                return;
            }
            if (title.length() < 3) {
                step[0] = 0; renderStep.run();
                titleInput.setError("Add a clear product title");
                titleInput.requestFocus();
                return;
            }
            if (!savingDraft[0] && selectedCategory[0].isEmpty()) {
                step[0] = 0; renderStep.run();
                categoryError.setVisibility(View.VISIBLE);
                return;
            }
            if (!savingDraft[0] && conditionInput.getSelectedItemPosition() == 0) {
                step[0] = 0; renderStep.run();
                conditionError.setVisibility(View.VISIBLE);
                return;
            }
            if (savingDraft[0] && price.isEmpty()) price = "0";
            if (!price.matches(Constants.PRICE_PATTERN)) {
                step[0] = 1; renderStep.run();
                priceInput.setError("Enter a price with up to 2 decimal places; R0 means free");
                priceInput.requestFocus();
                return;
            }
            String quantityText = host.ui.text(quantityInput).trim();
            if (quantityText.isEmpty()) quantityText = "1";
            if (!quantityText.matches("[0-9]{1,3}") || (!savingDraft[0] && Integer.parseInt(quantityText) < 1)) {
                step[0] = 1; renderStep.run();
                quantityInput.setError("Enter a stock quantity from 1 to 999");
                quantityInput.requestFocus();
                return;
            }
            int quantity = Math.max(0, Math.min(999, Integer.parseInt(quantityText)));
            if (!savingDraft[0] && pickupChosen[0] && location.isEmpty()) {
                step[0] = 1; renderStep.run();
                locationInput.setError("Add a safe pickup location");
                locationInput.requestFocus();
                return;
            }
            if (!savingDraft[0] && description.length() < 10) {
                step[0] = 0; renderStep.run();
                descriptionInput.setError("Add a little more detail");
                descriptionInput.requestFocus();
                return;
            }
            if (!savingDraft[0] && host.draftPhotos.isEmpty()) {
                step[0] = 0; renderStep.run();
                host.ui.showSimpleMessage("Photo required",
                        "Add at least one product photo before publishing this listing.");
                return;
            }
            if (!savingDraft[0] && !host.requireProfilePhoto()) {
                return;
            }
            if (!savingDraft[0]) {
                if (host.safety.postingPausedDialog(host.currentStudentEmail)) return;
                String specs = host.ui.text(specsInput).trim();
                CampusSafety.ContentScan scan = host.safety.reviewContent(host.currentStudentEmail,
                        host.currentUid, "listing", title + "\n" + description + "\n" + specs);
                if (scan.flagged) {
                    step[0] = 2;
                    renderStep.run();
                    host.safety.warnIfFlagged(scan, null);
                    return;
                }
            }
            String category = selectedCategory[0].isEmpty() ? "Other" : selectedCategory[0];
            String condition = conditionInput.getSelectedItemPosition() == 0 ? "Good"
                    : conditionInput.getSelectedItem().toString();
            String delivery = deliveryInput.getSelectedItem().toString();
            if (location.isEmpty()) location = pickupChosen[0] ? "Campus pickup" : "Delivery";
            String createdAt = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date());
            String nextStatus = savingDraft[0] ? "Draft"
                    : quantity <= 0 ? "Sold"
                    : existing == null || existing.status.equals("Draft") || existing.status.equals("Paused")
                    || existing.status.equals("Sold") ? "Available" : existing.status;
            MainActivity.Product product = new MainActivity.Product(title, new BigDecimal(price).setScale(2).toPlainString(),
                    description, host.currentStudentEmail, category, condition, location, delivery, createdAt,
                    nextStatus, existing == null ? java.util.UUID.randomUUID().toString() : existing.id);
            product.sellerName = host.currentStudentName;
            product.quantity = quantity;
            product.specs = selectedCategory[0].equals("Technology") ? host.ui.text(specsInput).trim() : "";
            product.photos.addAll(host.draftPhotos);
            product.documentUri = host.draftDocumentUri;
            product.documentUrl = host.draftDocumentUrl;
            product.documentName = host.draftDocumentName;
            product.documentMime = host.draftDocumentMime;
            product.documentSize = host.draftDocumentSize;
            product.documentDirty = existing == null ? product.hasDocument() : documentChanged(existing, product);
            product.negotiable = negotiableInput.isChecked();
            product.updatedTime = System.currentTimeMillis();
            if (existing != null) {
                product.savedBy.addAll(existing.savedBy);
                product.hidden = existing.hidden;
                product.createdTime = existing.createdTime;
                product.expiresTime = existing.expiresTime;
                product.viewCount = existing.viewCount;
                product.favouriteCount = existing.favouriteCount;
                if (existing.status.equals("Draft") || existing.status.equals("Paused")) {
                    product.createdTime = System.currentTimeMillis();
                    product.expiresTime = product.createdTime + 60L * 24 * 60 * 60 * 1000;
                }
            } else {
                product.expiresTime = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000;
            }
            int position = existing == null ? host.products.size() : host.products.indexOf(existing);
            if (existing == null) host.products.add(product); else host.products.set(position, product);
            if (!saveProducts()) {
                if (existing == null) host.products.remove(product); else host.products.set(position, existing);
                Toast.makeText(host, "The listing didn't save. Give it another go.", Toast.LENGTH_LONG).show();
                return;
            }
            cloudPushListing(product, CloudSync.remoteId(host, product.id).isEmpty());
            host.marketFilter = savingDraft[0] ? "Mine" : "All";
            dialog.dismiss();
            if (!savingDraft[0]) {
                host.notification.showCampusNotification("Listing published",
                        title + " is now visible in the Campus Marketplace.", "marketplace");
                host.community.notifySearchMatches(product);
            }
            host.showAppScreen("Market");
        };

        backButton.setOnClickListener(v -> {
            if (step[0] > 0) { step[0]--; renderStep.run(); }
        });
        continueButton.setOnClickListener(v -> {
            if (step[0] == 0) {
                boolean valid = true;
                if (host.ui.text(titleInput).trim().length() < 3) {
                    titleInput.setError("Add a clear product title"); valid = false;
                }
                if (selectedCategory[0].isEmpty()) { categoryError.setVisibility(View.VISIBLE); valid = false; }
                if (conditionInput.getSelectedItemPosition() == 0) { conditionError.setVisibility(View.VISIBLE); valid = false; }
                if (host.ui.text(descriptionInput).trim().length() < 10) {
                    descriptionInput.setError("Add a little more detail"); valid = false;
                }
                if (!valid) return;
                step[0] = 1; renderStep.run(); return;
            }
            if (step[0] == 1) {
                boolean valid = true;
                if (!host.ui.text(priceInput).trim().matches(Constants.PRICE_PATTERN)) {
                    priceInput.setError("Enter a price with up to 2 decimal places; R0 means free"); valid = false;
                }
                if (pickupChosen[0] && host.ui.text(locationInput).trim().isEmpty()) {
                    locationInput.setError("Add a safe pickup location"); valid = false;
                }
                if (!valid) return;
                step[0] = 2; renderStep.run(); return;
            }
            savingDraft[0] = false;
            performSave.run();
        });
        saveDraft.setOnClickListener(v -> {
            savingDraft[0] = true;
            performSave.run();
            savingDraft[0] = false;
        });
        titleInput.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { refreshGate.run(); }
        });
        priceInput.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { refreshGate.run(); }
        });
        locationInput.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { refreshGate.run(); }
        });
        descriptionInput.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { refreshGate.run(); }
        });

        dialog.setContentView(editor);
        dialog.setOnDismissListener(d -> {
            host.listingDialog = null;
            host.listingPhotoContainer = null;
            host.listingDocumentContainer = null;
            host.listingPhotoCount = null;
            host.draftPhotos.clear();
            host.draftDocumentUri = null;
            host.draftDocumentUrl = "";
            host.draftDocumentName = "";
            host.draftDocumentMime = "";
            host.draftDocumentSize = 0;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        renderStep.run();
        renderDraftPhotos();
        renderDraftDocument();
    }

    void refreshCategoryChips(TextView[] chips, String[] values, String selected) {
        for (int i = 0; i < chips.length; i++) {
            boolean active = values[i].equals(selected) || (selected.isEmpty() && i == 0);
            chips[i].setTextColor(active ? Color.WHITE : host.text);
            chips[i].setBackground(active ? host.ui.round(host.blue, 999)
                    : host.ui.strokedRound(host.surface(), 999, host.border));
        }
    }

    void reviewLine(LinearLayout box, String label, String value) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.TOP);
        row.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
        TextView key = host.ui.bodyText(label, host.muted, 12);
        row.addView(key, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView val = host.ui.bodyText(value == null || value.isEmpty() ? "Not set" : value, host.text, 12);
        val.setGravity(Gravity.END);
        val.setMaxLines(2);
        val.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(val, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(row);
    }

    // Old single-page editor kept for reference.
    void showProductEditorLegacy(MainActivity.Product existing) {
        if (host.listingDialog != null && host.listingDialog.isShowing()) return;
        // Reset the draft, then copy the old listing values when editing.
        host.draftPhotos.clear();
        host.draftDocumentUri = null;
        host.draftDocumentUrl = "";
        host.draftDocumentName = "";
        host.draftDocumentMime = "";
        host.draftDocumentSize = 0;
        if (existing != null) {
            host.draftPhotos.addAll(existing.photos);
            host.draftDocumentUri = existing.documentUri;
            host.draftDocumentUrl = existing.documentUrl;
            host.draftDocumentName = existing.documentName;
            host.draftDocumentMime = existing.documentMime;
            host.draftDocumentSize = existing.documentSize;
        }
        Dialog dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        host.listingDialog = dialog;
        LinearLayout editor = new LinearLayout(host);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setBackgroundColor(host.page);

        LinearLayout header = host.ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(host.ui.dp(20), host.ui.dp(14), host.ui.dp(16), host.ui.dp(14));
        LinearLayout heading = new LinearLayout(host);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(host.ui.titleText(existing == null ? "New listing" : "Edit listing", host.text, 22));
        heading.addView(host.ui.bodyText("Campus Marketplace", host.muted, 12));
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton close = new ImageButton(host);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setImageTintList(android.content.res.ColorStateList.valueOf(host.muted));
        close.setContentDescription("Close listing editor");
        close.setBackground(host.ui.round(host.page, 8));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(host.ui.dp(48), host.ui.dp(48)));
        editor.addView(header);
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        editor.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(host.ui.dp(20), 0, host.ui.dp(20), host.ui.dp(24));
        scroll.addView(form);
        // Pick photos and documents here; upload at publish.
        listingSection(form, "Photos");
        host.listingPhotoCount = host.ui.bodyText("0 / 5 photos", host.muted, 12);
        form.addView(host.listingPhotoCount);
        HorizontalScrollView photos = new HorizontalScrollView(host);
        photos.setHorizontalScrollBarEnabled(false);
        host.listingPhotoContainer = host.ui.horizontal();
        host.listingPhotoContainer.setPadding(0, host.ui.dp(12), 0, host.ui.dp(8));
        photos.addView(host.listingPhotoContainer);
        form.addView(photos);
        Button addPhotos = host.ui.secondaryButton("Add photos");
        addPhotos.setTextColor(host.blue);
        addPhotos.setBackground(host.ui.strokedRound(Color.WHITE, 8, host.border));
        addPhotos.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_camera, 0, 0, 0);
        addPhotos.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(host.blue));
        addPhotos.setOnClickListener(v -> pickListingPhotos());
        form.addView(addPhotos, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));

        listingSection(form, "Document (optional)");
        form.addView(host.ui.bodyText("Attach one PDF, Word, PowerPoint, RTF or TXT file up to 10 MB.",
                host.muted, 12));
        host.listingDocumentContainer = new LinearLayout(host);
        host.listingDocumentContainer.setOrientation(LinearLayout.VERTICAL);
        host.listingDocumentContainer.setPadding(0, host.ui.dp(12), 0, 0);
        form.addView(host.listingDocumentContainer);

        listingSection(form, "Item details");
        EditText titleInput = host.ui.listingField(form, "Product title", "What are you selling?", InputType.TYPE_CLASS_TEXT);
        titleInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(80) });
        Spinner categoryInput = host.ui.listingSelector(form, "Category", new String[] {
                "Choose a category", "Books", "Technology", "Stationery", "Other" });
        Spinner conditionInput = host.ui.listingSelector(form, "Condition", new String[] {
                "Choose a condition", "New", "Like new", "Good", "Fair" });
        EditText descriptionInput = host.ui.listingField(form, "Description", "Details about your item",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        descriptionInput.setSingleLine(false);
        descriptionInput.setMinLines(3);
        descriptionInput.setGravity(Gravity.TOP | Gravity.START);
        descriptionInput.setPadding(host.ui.dp(14), host.ui.dp(12), host.ui.dp(14), host.ui.dp(12));
        descriptionInput.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(120)));
        descriptionInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(1000) });
        TextView counter = host.ui.bodyText("0 / 1000", host.muted, 11);
        counter.setGravity(Gravity.END);
        counter.setPadding(0, host.ui.dp(4), 0, 0);
        form.addView(counter);
        descriptionInput.addTextChangedListener(new MainActivity.SimpleWatcher() {
            @Override public void afterTextChanged(Editable s) { counter.setText(s.length() + " / 1000"); }
        });
        listingSection(form, "Price & collection");
        EditText priceInput = host.ui.listingField(form, "Price (ZAR)", "0.00",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox negotiableInput = new CheckBox(host);
        negotiableInput.setText("Price is negotiable");
        negotiableInput.setTextSize(12);
        negotiableInput.setChecked(true);
        host.ui.themedToggle(negotiableInput);
        form.addView(negotiableInput);
        EditText locationInput = host.ui.listingField(form, "Pickup location", "Campus or public meeting point", InputType.TYPE_CLASS_TEXT);
        Spinner deliveryInput = host.ui.listingSelector(form, "Delivery", new String[] {
                "Campus pickup", "Delivery" });
        if (existing != null) {
            titleInput.setText(existing.title); priceInput.setText(existing.price); locationInput.setText(existing.location);
            descriptionInput.setText(existing.description);
            negotiableInput.setChecked(existing.negotiable);
            host.ui.selectSpinner(categoryInput, existing.category); host.ui.selectSpinner(conditionInput, existing.condition); host.ui.selectSpinner(deliveryInput,
                    existing.delivery != null && existing.delivery.toLowerCase(Locale.ROOT).contains("delivery")
                            ? "Delivery" : "Campus pickup");
        }

        LinearLayout footer = new LinearLayout(host);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(16));
        footer.setBackground(host.ui.edgeRule(host.text, true));
        Button publish = host.ui.primaryButton("Publish listing");
        Button saveDraft = host.ui.secondaryButton("Save draft");
        final boolean[] savingDraft = {false};
        // Save draft uses the same checks as publish, with a flag.
        saveDraft.setOnClickListener(v -> { savingDraft[0] = true; publish.performClick(); savingDraft[0] = false; });
        LinearLayout footerButtons = host.ui.horizontal();
        footerButtons.addView(saveDraft, new LinearLayout.LayoutParams(0, host.ui.dp(52), 1));
        footerButtons.addView(host.ui.space(host.ui.dp(8), 1)); footerButtons.addView(publish, new LinearLayout.LayoutParams(0, host.ui.dp(52), 1));
        footer.addView(footerButtons);
        editor.addView(footer);
        dialog.setContentView(editor);
            // Publish checks the form, saves here, then tries Firebase.
            publish.setOnClickListener(v -> {
                    String title = host.ui.text(titleInput).trim();
                    String category = categoryInput.getSelectedItem().toString();
                    String condition = conditionInput.getSelectedItem().toString();
                    String price = host.ui.text(priceInput).trim();
                    String location = host.ui.text(locationInput).trim();
                    String delivery = deliveryInput.getSelectedItem().toString();
                    String description = host.ui.text(descriptionInput).trim();
                    if (title.length() < 3) {
                        titleInput.setError("Add a clear product title");
                        titleInput.requestFocus();
                        return;
                    }
                    if (!savingDraft[0] && categoryInput.getSelectedItemPosition() == 0) {
                        scroll.smoothScrollTo(0, categoryInput.getTop());
                        categoryInput.performClick();
                        return;
                    }
                    if (!savingDraft[0] && conditionInput.getSelectedItemPosition() == 0) {
                        scroll.smoothScrollTo(0, conditionInput.getTop());
                        conditionInput.performClick();
                        return;
                    }
                    if (savingDraft[0] && price.isEmpty()) price = "0";
                    if (!price.matches(Constants.PRICE_PATTERN)) {
                        priceInput.setError("Enter a price with up to 2 decimal places; R 0 means free");
                        priceInput.requestFocus();
                        return;
                    }
                    if (!savingDraft[0] && location.isEmpty()) {
                        locationInput.setError("Add a safe pickup location");
                        locationInput.requestFocus();
                        return;
                    }
                    if (!savingDraft[0] && description.length() < 10) {
                        descriptionInput.setError("Add a little more detail");
                        descriptionInput.requestFocus();
                        return;
                    }
                    String createdAt = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date());
                    if (categoryInput.getSelectedItemPosition() == 0) category = "Other";
                    if (conditionInput.getSelectedItemPosition() == 0) condition = "Good";
                    MainActivity.Product product = new MainActivity.Product(title, new BigDecimal(price).setScale(2).toPlainString(),
                            description, host.currentStudentEmail, category, condition, location, delivery, createdAt,
                            savingDraft[0] ? "Draft" : "Available", existing == null ? java.util.UUID.randomUUID().toString() : existing.id);
                    product.sellerName = host.currentStudentName;
                    product.photos.addAll(host.draftPhotos);
                    product.documentUri = host.draftDocumentUri;
                    product.documentUrl = host.draftDocumentUrl;
                    product.documentName = host.draftDocumentName;
                    product.documentMime = host.draftDocumentMime;
                    product.documentSize = host.draftDocumentSize;
                    product.documentDirty = existing == null ? product.hasDocument() : documentChanged(existing, product);
                    product.negotiable = negotiableInput.isChecked();
                    product.updatedTime = System.currentTimeMillis();
                    if (existing != null) {
                        product.savedBy.addAll(existing.savedBy);
                        product.hidden = existing.hidden;
                        product.createdTime = existing.createdTime;
                        product.expiresTime = existing.expiresTime;
                        product.viewCount = existing.viewCount;
                        product.favouriteCount = existing.favouriteCount;
                        // Publishing starts a fresh 60-day run.
                        if (existing.status.equals("Draft") || existing.status.equals("Paused")) {
                            product.createdTime = System.currentTimeMillis();
                            product.expiresTime = product.createdTime + 60L * 24 * 60 * 60 * 1000;
                        }
                    } else {
                        product.expiresTime = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000;
                    }
                    int position = existing == null ? host.products.size() : host.products.indexOf(existing);
                    if (existing == null) host.products.add(product); else host.products.set(position, product);
                    // If saving fails, put the old list back so the screen matches storage.
                    if (!saveProducts()) {
                        if (existing == null) host.products.remove(product); else host.products.set(position, existing);
                        Toast.makeText(host, "The listing didn't save. Give it another go.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    // Drafts stay on this phone; only finished listings go to Firebase.
                    cloudPushListing(product, CloudSync.remoteId(host, product.id).isEmpty());
                    host.marketFilter = savingDraft[0] ? "Mine" : "All";
                    dialog.dismiss();
                    if (!savingDraft[0]) { host.notification.showCampusNotification("Listing published", title + " is now visible in the Campus Marketplace.", "marketplace"); host.community.notifySearchMatches(product); }
                    host.showAppScreen("Market");
            });
        // Closing the editor clears the draft so it can't affect the next listing.
        dialog.setOnDismissListener(d -> {
            host.listingDialog = null;
            host.listingPhotoContainer = null;
            host.listingDocumentContainer = null;
            host.listingPhotoCount = null;
            host.draftPhotos.clear();
            host.draftDocumentUri = null;
            host.draftDocumentUrl = "";
            host.draftDocumentName = "";
            host.draftDocumentMime = "";
            host.draftDocumentSize = 0;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        renderDraftPhotos();
        renderDraftDocument();
    }

    // A listing is public when it is not hidden, draft, paused, sold or expired.
    // Sold-out listings disappear from the marketplace immediately.
    public boolean publicListing(MainActivity.Product p) {
        if (p.hidden || p.quantity <= 0 || !p.status.equals("Available")) return false;
        return !expired(p);
    }

    /** Listings last 60 days; renew puts them back on the market. */
    public long expiryTime(MainActivity.Product p) {
        if (p.expiresTime > 0) return p.expiresTime;
        return p.createdTime > 0 ? p.createdTime + 60L * 24 * 60 * 60 * 1000 : 0;
    }

    public boolean expired(MainActivity.Product p) {
        long expiry = expiryTime(p);
        return expiry > 0 && System.currentTimeMillis() > expiry;
    }

    /** True when this student owns the listing. */
    public boolean ownsListing(MainActivity.Product p) {
        if (p == null || p.sellerEmail == null) return false;
        String ref = p.sellerEmail.trim();
        if (ref.isEmpty()) return false;
        String uid = CloudSync.uid(host);
        if (!uid.isEmpty() && ref.equals(uid)) return true;
        return !host.currentStudentEmail.isEmpty() && ref.equalsIgnoreCase(host.currentStudentEmail);
    }

    public MainActivity.Product productById(String id) { for (MainActivity.Product p : host.products) if (p.id.equals(id)) return p; return null; }
    public String sellerLabel(MainActivity.Product product) {
        if (product != null && product.sellerName != null && !product.sellerName.trim().isEmpty()) {
            return product.sellerName;
        }
        return sellerLabel(product == null ? "" : product.sellerEmail);
    }
    public String sellerLabel(String sellerField) {
        if (sellerField == null || sellerField.trim().isEmpty()) {
            return "Student seller";
        }
        if (HelperBot.PEER.equals(sellerField)) {
            return HelperBot.NAME;
        }
        if (!sellerField.contains("@")) {
            String name = CloudSync.displayName(host, sellerField);
            return name == null || name.isEmpty() ? "Student seller" : name;
        }
        String name = host.studentName(sellerField);
        return name == null || name.trim().isEmpty() || name.equalsIgnoreCase(sellerField)
                ? "Student seller" : name;
    }

    // Manage opens only for my listing; options depend on status.
    public void manageListing(MainActivity.Product p) {
        if (!ownsListing(p)) return;
        for (MainActivity.TransactionRecord order : host.transactions) if (order.productId.equals(p.id) && !order.status.equals(MainActivity.TransactionRecord.CANCELLED)) { host.order.showTransactionHistory(); return; }

        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(8), 0, host.ui.dp(10));
        LinearLayout summary = host.ui.horizontal();
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(host.ui.dp(4), 0, host.ui.dp(4), host.ui.dp(10));
        summary.addView(host.ui.statusPill(p.status));
        summary.addView(host.ui.space(host.ui.dp(8), 1));
        TextView price = host.ui.titleText(new BigDecimal(p.price).signum() == 0 ? "Free" : "R" + p.price, host.text, 18);
        summary.addView(price, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView expiry = host.ui.bodyText(expired(p) ? "Expired" : "Expires " + host.messageTime(expiryTime(p)), host.muted, 11);
        summary.addView(expiry);
        body.addView(summary);

        final Dialog[] sheetRef = {null};
        boolean paused = p.status.equals("Paused") || p.status.equals("Draft");
        sheetAction(body, "compose", "Edit listing", "Change photos, price or details", host.text, () -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            if (host.featureStore.data.reservation(p.id, System.currentTimeMillis()) != null) {
                host.ui.showSimpleMessage("Item reserved", "Cancel the accepted offer before editing this listing.");
                return;
            }
            showProductEditor(p);
        });
        sheetAction(body, paused ? "check" : "box", paused ? "Publish listing" : "Pause listing",
                paused ? "Put this back on the market" : "Hide it from browsing without deleting",
                host.text, () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    updateListingState(p, paused ? 1 : -1);
                });
        sheetAction(body, "check", "Mark sold", "Close the listing and release offers", host.text, () -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            updateListingState(p, 2);
        });
        sheetAction(body, "calendar", "Renew listing", "Start a fresh 60-day run", host.text, () -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            updateListingState(p, 3);
        });
        sheetAction(body, "tag", "View offers", "See every offer made on this item", host.text, () -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            host.showAppScreen("Offers");
        });
        TextView danger = host.ui.tinyCaps("DANGER ZONE", host.nightActive ? Color.rgb(255, 125, 146) : Color.rgb(190, 18, 60));
        danger.setPadding(host.ui.dp(6), host.ui.dp(18), 0, host.ui.dp(4));
        body.addView(danger);
        sheetAction(body, "flag", "Delete listing", "Remove it permanently from CampusTrade",
                host.nightActive ? Color.rgb(255, 125, 146) : Color.rgb(190, 18, 60), () -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    host.ui.dialog().setTitle("Delete \"" + p.title + "\"?")
                            .setMessage("This removes the listing from the marketplace and cannot be undone.")
                            .setNegativeButton("Keep listing", null)
                            .setPositiveButton("Delete", (confirm, w) -> {
                                host.products.remove(p);
                                if (!saveProducts()) {
                                    Toast.makeText(host, "Couldn't delete it. Give it another go.", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                cloudMirrorListing(p, true);
                                Toast.makeText(host, "Listing deleted.", Toast.LENGTH_SHORT).show();
                                host.marketFilter = "Mine"; host.showAppScreen("Market");
                            }).show();
                });
        sheetRef[0] = host.ui.showBottomSheet("Manage \"" + p.title + "\"", body);
    }

    void updateListingState(MainActivity.Product p, int action) {
        if (((action == 1 && !p.status.equals("Available")) || action == 3)
                && (p.description.length() < 10 || p.location.trim().isEmpty())) {
            showProductEditor(p);
            return;
        }
        if (action == 1 && p.quantity <= 0) {
            host.ui.showSimpleMessage("Sold out", "Add stock in Edit listing before republishing this item.");
            return;
        }
        String old = p.status; long created = p.createdTime; long oldExpiry = p.expiresTime;
        int oldQuantity = p.quantity;
        p.status = action == 2 ? "Sold" : action == 1 && p.status.equals("Available") ? "Paused"
                : action == -1 ? "Paused" : "Available";
        if (action == 2) p.quantity = 0;
        if (action == 3) {
            p.createdTime = System.currentTimeMillis();
            p.expiresTime = p.createdTime + 60L * 24 * 60 * 60 * 1000;
        }
        if (!saveProducts()) {
            p.status = old; p.quantity = oldQuantity; p.createdTime = created; p.expiresTime = oldExpiry;
            host.ui.showSimpleMessage("Couldn't save", "Give it another go."); return;
        }
        cloudMirrorListing(p, false);
        try {
            if (!p.status.equals("Available")) {
                host.featureStore.update(f -> f.closeOffers(p.id));
                cloudCloseOffers(p.id);
            }
        } catch (RuntimeException e) { Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show(); }
        if (p.status.equals("Available")) host.community.notifySearchMatches(p);
        host.marketFilter = "Mine"; host.showAppScreen("Market");
    }

    void sheetAction(LinearLayout body, String icon, String title, String subtitle, int color, Runnable action) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.ui.dp(6), host.ui.dp(8), host.ui.dp(6), host.ui.dp(8));
        row.setMinimumHeight(host.ui.dp(54));
        View symbol = host.new CampusAssetView(host, icon, color);
        symbol.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
        symbol.setBackground(host.ui.round(host.nightActive ? Color.rgb(42, 42, 46) : Color.rgb(243, 241, 236), 10));
        row.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(36), host.ui.dp(36)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(12), 0, 0, 0);
        copy.addView(host.ui.titleText(title, color, 14));
        copy.addView(host.ui.bodyText(subtitle, host.muted, 11));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View chevron = host.new CampusAssetView(host, "chevron", host.muted);
        row.addView(chevron, new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16)));
        row.setOnClickListener(v -> action.run());
        host.ui.attachPressAnimation(row);
        body.addView(row);
    }

    public void makeOffer(MainActivity.Product p) {
        if (p.quantity <= 0) {
            host.ui.showSimpleMessage("Sold out", "There is no stock left to make an offer on.");
            return;
        }
        LinearLayout form = host.ui.workflowForm();
        form.addView(host.ui.bodyText("Asking price R" + p.price + " · " + p.title, host.muted, 12));
        EditText amount = host.ui.listingField(form, "Your offer (ZAR)", "Amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView note = host.ui.bodyText("The seller can accept, counter or decline. An accepted offer reserves the item for 24 hours.",
                host.muted, 11);
        note.setPadding(0, host.ui.dp(10), 0, host.ui.dp(14));
        form.addView(note);
        Button send = host.ui.primaryButton("Send offer");
        form.addView(send, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        Dialog sheet = host.ui.showBottomSheet("Make an offer", form);
        send.setOnClickListener(v -> {
            String value = host.ui.text(amount).trim();
            if (!value.matches(Constants.PRICE_PATTERN) || new BigDecimal(value).signum() <= 0) {
                amount.setError("Enter an amount above R0");
                return;
            }
            try {
                CampusFeatures.require(p.status.equals("Available") && p.quantity > 0 && !p.hidden && !expired(p),
                        p.quantity <= 0 ? "This listing is sold out" : "This listing is unavailable");
                final CampusFeatures.Offer[] created = {null};
                host.featureStore.update(f -> created[0] = f.offer(p.id, host.currentStudentEmail, p.sellerEmail, value, System.currentTimeMillis()));
                if (created[0] != null) cloudSaveOffer(created[0], p);
                host.notification.addNotification(p.sellerEmail, "New offer",
                        p.title + ": R" + CampusFeatures.money(value, false), "marketplace", "");
                host.notification.sendPush(p.sellerEmail, "New offer",
                        p.title + ": R" + CampusFeatures.money(value, false), "marketplace", "");
                sheet.dismiss();
                host.showAppScreen("Offers");
            } catch (RuntimeException e) {
                amount.setError(e.getMessage() == null ? "Couldn't send that offer" : e.getMessage());
            }
        });
    }

    boolean refreshOffers(Runnable after) {
        long now = System.currentTimeMillis();
        if (offersPulling || now - lastOffersPullAt <= 8000) return false;
        offersPulling = true;
        lastOffersPullAt = now;
        cloudPullOffers(() -> {
            offersPulling = false;
            if (after != null) after.run();
        });
        return true;
    }

    public void buildOffers(LinearLayout content) {
        refreshOffers(() -> {
            if (host.currentTab.equals("Offers")) host.showAppScreen("Offers");
        });
        int count = 0;
        for (int i = host.featureStore.data.offers.size() - 1; i >= 0; i--) {
            CampusFeatures.Offer o = host.featureStore.data.offers.get(i);
            if (!o.buyer.equals(host.currentStudentEmail) && !o.seller.equals(host.currentStudentEmail)) continue;
            MainActivity.Product p = productById(o.listing); count++;
            boolean expiredReservation = o.state.equals("Accepted") && o.expires <= System.currentTimeMillis();
            String offerState = expiredReservation ? "Expired" : o.state;
            LinearLayout card = host.ui.card(host.page, 0, host.ui.dp(10));
            card.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
            LinearLayout row = host.ui.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            View thumb = p != null ? listingThumbnail(p, false) : null;
            if (thumb != null) {
                row.addView(thumb, new LinearLayout.LayoutParams(host.ui.dp(64), host.ui.dp(64)));
            } else {
                LinearLayout missing = new LinearLayout(host);
                missing.setGravity(Gravity.CENTER);
                missing.setBackground(host.ui.round(host.nightActive ? Color.rgb(48, 48, 54) : Color.rgb(232, 230, 224), 8));
                missing.addView(host.new CampusAssetView(host, "box", host.muted), new LinearLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24)));
                row.addView(missing, new LinearLayout.LayoutParams(host.ui.dp(64), host.ui.dp(64)));
            }
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(12), 0, 0, 0);
            TextView title = host.ui.titleText(p == null ? "Listing unavailable" : p.title, host.text, 14);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);
            LinearLayout priceRow = host.ui.horizontal();
            priceRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView price = host.ui.titleText("R" + o.amount, host.text, 16);
            priceRow.addView(price);
            priceRow.addView(host.ui.space(host.ui.dp(8), 1));
            priceRow.addView(host.ui.statusPill(offerState));
            copy.addView(priceRow);
            String role = o.buyer.equals(host.currentStudentEmail) ? "You offered" : "Offer from " + host.studentName(o.buyer);
            TextView meta = host.ui.bodyText(role + " · " + host.messageTime(o.created), host.muted, 11);
            meta.setMaxLines(1);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(meta);
            if (o.state.equals("Accepted") && !expiredReservation) {
                TextView reserved = host.ui.chip("Reserved · " + countdown(o.expires),
                        host.ui.statusBackground("Reserved"), host.ui.statusInk("Reserved"));
                reserved.setTextSize(10);
                reserved.setPadding(host.ui.dp(8), host.ui.dp(4), host.ui.dp(8), host.ui.dp(4));
                copy.addView(reserved);
            } else if (o.pending() && o.turn.equals(host.currentStudentEmail)) {
                TextView yourTurn = host.ui.chip("Waiting for your reply",
                        host.ui.statusBackground("Awaiting"), host.ui.statusInk("Awaiting"));
                yourTurn.setTextSize(10);
                yourTurn.setPadding(host.ui.dp(8), host.ui.dp(4), host.ui.dp(8), host.ui.dp(4));
                copy.addView(yourTurn);
            } else if (o.pending()) {
                TextView waiting = host.ui.chip("Waiting for " + host.studentName(o.turn),
                        host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235), host.muted);
                waiting.setTextSize(10);
                waiting.setPadding(host.ui.dp(8), host.ui.dp(4), host.ui.dp(8), host.ui.dp(4));
                copy.addView(waiting);
            }
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(host.new CampusAssetView(host, "chevron", host.muted), new LinearLayout.LayoutParams(host.ui.dp(16), host.ui.dp(16)));
            card.addView(row);
            if (o.pending() && o.turn.equals(host.currentStudentEmail)) {
                Button approve = host.ui.primaryButton("Approve offer");
                LinearLayout.LayoutParams approveParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44));
                approveParams.topMargin = host.ui.dp(8);
                approve.setOnClickListener(v -> {
                    try {
                        respondToOffer(o.id, "Accept", "");
                    } catch (RuntimeException e) {
                        host.ui.showSimpleMessage("Offer", e.getMessage());
                    }
                });
                card.addView(approve, approveParams);
            }
            card.setOnClickListener(v -> reviewOffer(o.id));
            content.addView(card);
        }
        if (count == 0) {
            content.addView(host.ui.emptyState("tag", "No offers yet",
                    "When you make an offer or a buyer contacts you, it will appear here."));
        }
    }

    String countdown(long until) {
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) return "expired";
        long hours = remaining / (60L * 60 * 1000);
        long minutes = (remaining / (60L * 1000)) % 60;
        if (hours >= 1) return hours + "h " + minutes + "m left";
        return Math.max(1, minutes) + "m left";
    }

    /** Copy a new offer so the seller sees it. */
    private void cloudSaveOffer(CampusFeatures.Offer offer, MainActivity.Product product) {
        String uid = CloudSync.uid(host);
        String listingId = CloudSync.remoteId(host, product.id);
        if (uid.isEmpty() || listingId.isEmpty() || offer == null) return;
        CloudSync.peerUid(host, offer.seller, new CloudSync.StrCallback() {
            @Override public void onOk(String sellerUid) {
                java.util.Map<String, Object> doc = new java.util.HashMap<>();
                doc.put("buyerId", uid);
                doc.put("sellerId", sellerUid);
                doc.put("listingId", listingId);
                doc.put("buyerEmail", offer.buyer);
                doc.put("sellerEmail", offer.seller);
                try { doc.put("amount", Double.parseDouble(offer.amount)); }
                catch (NumberFormatException e) { doc.put("amount", 0.0); }
                doc.put("turn", offer.turn);
                doc.put("state", offer.state);
                doc.put("created", offer.created);
                doc.put("expiresAt", offer.expires);
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("offers")
                        .document(offer.id).set(doc)
                        .addOnFailureListener(e -> {
                            android.util.Log.w("CampusTrade", "offer save failed: " + e.getMessage());
                            Toast.makeText(host,
                                    "Offer saved on this phone, but the seller may not see it yet. Check the connection and try again.",
                                    Toast.LENGTH_LONG).show();
                        });
            }
            @Override public void onBad(String message) {
                android.util.Log.w("CampusTrade", "offer seller lookup failed: " + message);
                Toast.makeText(host, "Couldn't find the seller account for this offer.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void cloudUpdateOffer(CampusFeatures.Offer offer) {
        if (offer == null) return;
        java.util.Map<String, Object> update = new java.util.HashMap<>();
        try { update.put("amount", Double.parseDouble(offer.amount)); }
        catch (NumberFormatException e) { update.put("amount", 0.0); }
        update.put("turn", offer.turn);
        update.put("state", offer.state);
        update.put("expiresAt", offer.expires);
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("offers")
                .document(offer.id).update(update)
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "offer update failed: " + e.getMessage()));
    }

    private void cloudCloseOffers(String localListingId) {
        for (CampusFeatures.Offer offer : host.featureStore.data.offers) {
            if (offer.listing.equals(localListingId)) cloudUpdateOffer(offer);
        }
    }

    private void startOfferListeners(String uid) {
        if (uid.isEmpty() || offersBuyerListener != null) return;
        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();
        offersBuyerListener = db.collection("offers").whereEqualTo("buyerId", uid)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        android.util.Log.w("CampusTrade", "offer listener (buyer): " + error.getMessage());
                        return;
                    }
                    if (snap == null || host.isDestroyed() || host.isFinishing()) return;
                    mergeOfferDocs(snap.getDocuments());
                    refreshOfferSurfaces();
                });
        offersSellerListener = db.collection("offers").whereEqualTo("sellerId", uid)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        android.util.Log.w("CampusTrade", "offer listener (seller): " + error.getMessage());
                        return;
                    }
                    if (snap == null || host.isDestroyed() || host.isFinishing()) return;
                    mergeOfferDocs(snap.getDocuments());
                    refreshOfferSurfaces();
                });
    }

    private void refreshOfferSurfaces() {
        if (currentOfferPage != null && currentOfferPage.isShowing() && !currentOfferId.isEmpty()) {
            CampusFeatures.Offer offer = host.featureStore.data.findOffer(currentOfferId);
            if (offer == null) {
                currentOfferPage.dismiss();
            } else {
                String id = currentOfferId;
                currentOfferPage.dismiss();
                reviewOffer(id);
            }
        }
        if ("Offers".equals(host.currentTab) || "Home".equals(host.currentTab)) {
            host.showAppScreen(host.currentTab);
        }
    }

    /** Load offers where this student is buyer or seller. */
    private void cloudPullOffers(Runnable after) {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) { if (after != null) after.run(); return; }
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("offers").whereEqualTo("buyerId", uid).get()
                .addOnSuccessListener(buySnap -> db.collection("offers").whereEqualTo("sellerId", uid).get()
                        .addOnSuccessListener(sellSnap -> {
                            java.util.LinkedHashMap<String, com.google.firebase.firestore.DocumentSnapshot> docs =
                                    new java.util.LinkedHashMap<>();
                            for (com.google.firebase.firestore.DocumentSnapshot d : buySnap.getDocuments()) docs.put(d.getId(), d);
                            for (com.google.firebase.firestore.DocumentSnapshot d : sellSnap.getDocuments()) docs.put(d.getId(), d);
                            mergeOfferDocs(docs.values());
                            if (after != null) after.run();
                        })
                        .addOnFailureListener(e -> {
                            android.util.Log.w("CampusTrade", "offer seller pull failed: " + e.getMessage());
                            if (after != null) after.run();
                        }))
                .addOnFailureListener(e -> {
                    android.util.Log.w("CampusTrade", "offer buyer pull failed: " + e.getMessage());
                    if (after != null) after.run();
                });
    }

    /** Merge any set of offer documents into the local feature store. */
    private void mergeOfferDocs(java.util.Collection<com.google.firebase.firestore.DocumentSnapshot> docs) {
        try {
            host.featureStore.update(f -> {
                for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
                    CampusFeatures.Offer target = null;
                    for (CampusFeatures.Offer o : f.offers) if (o.id.equals(doc.getId())) { target = o; break; }
                    if (target == null) {
                        target = new CampusFeatures.Offer();
                        target.id = doc.getId();
                        f.offers.add(target);
                    }
                    String remoteListing = doc.getString("listingId");
                    String localListing = CloudSync.localId(host, remoteListing == null ? "" : remoteListing);
                    target.listing = localListing.isEmpty() ? (remoteListing == null ? "" : remoteListing) : localListing;
                    String buyer = doc.getString("buyerEmail");
                    if (buyer == null || buyer.isEmpty()) buyer = CloudSync.emailForUid(host, doc.getString("buyerId"));
                    String seller = doc.getString("sellerEmail");
                    if (seller == null || seller.isEmpty()) seller = CloudSync.emailForUid(host, doc.getString("sellerId"));
                    target.buyer = buyer == null ? "" : buyer;
                    target.seller = seller == null ? "" : seller;
                    Double amount = doc.getDouble("amount");
                    target.amount = amount == null ? "0.00" : java.math.BigDecimal.valueOf(amount)
                            .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                    String turn = doc.getString("turn");
                    if (turn != null && !turn.isEmpty() && !turn.contains("@")) turn = CloudSync.emailForUid(host, turn);
                    target.turn = turn == null || turn.isEmpty() ? target.seller : turn;
                    target.state = doc.getString("state") == null ? "Pending" : doc.getString("state");
                    Long created = doc.getLong("created");
                    target.created = created == null ? 0 : created;
                    Long expires = doc.getLong("expiresAt");
                    target.expires = expires == null ? 0 : expires;
                }
            });
        } catch (RuntimeException e) {
            android.util.Log.w("CampusTrade", "offer merge failed: " + e.getMessage());
        }
    }

    public void reviewOffer(String id) {
        final CampusFeatures.Offer o = host.featureStore.data.findOffer(id);
        if (o == null) return;
        final MainActivity.Product p = productById(o.listing);
        final boolean expiredReservation = o.state.equals("Accepted") && o.expires <= System.currentTimeMillis();
        final String state = expiredReservation ? "Expired" : o.state;
        final Dialog page = new Dialog(host);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        currentOfferPage = page;
        currentOfferId = id;
        page.setOnDismissListener(d -> {
            if (currentOfferPage == page) {
                currentOfferPage = null;
                currentOfferId = "";
            }
        });
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Offer", "R" + o.amount, page::dismiss));

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(6), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (p != null) {
            View thumb = listingThumbnail(p, true);
            content.addView(thumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(180)));
        }
        LinearLayout amountRow = host.ui.horizontal();
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        amountRow.setPadding(0, host.ui.dp(14), 0, 0);
        amountRow.addView(host.ui.titleText("R" + o.amount, host.text, 32),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        amountRow.addView(host.ui.statusPill(state));
        content.addView(amountRow);
        TextView title = host.ui.titleText(p == null ? "Listing unavailable" : p.title, host.text, 18);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(title);
        if (p != null) {
            content.addView(host.ui.bodyText("Asking R" + p.price + " · " + p.condition + " · " + p.location, host.muted, 12));
        }
        if (o.state.equals("Accepted") && !expiredReservation) {
            content.addView(host.ui.bodyText("Reserved · " + countdown(o.expires), host.readableInk(host.green), 12));
        }

        LinearLayout parties = new LinearLayout(host);
        parties.setOrientation(LinearLayout.VERTICAL);
        parties.setPadding(host.ui.dp(14), host.ui.dp(10), host.ui.dp(14), host.ui.dp(10));
        parties.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        LinearLayout.LayoutParams partyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        partyParams.topMargin = host.ui.dp(16);
        content.addView(parties, partyParams);
        parties.addView(offerPartyRow("Buyer", host.studentName(o.buyer), o.buyer.equals(host.currentStudentEmail)));
        View partyRule = new View(host);
        partyRule.setBackgroundColor(host.line);
        parties.addView(partyRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));
        parties.addView(offerPartyRow("Seller", host.studentName(o.seller), o.seller.equals(host.currentStudentEmail)));

        TextView next = host.ui.bodyText(offerNextStep(o), host.muted, 12);
        next.setPadding(0, host.ui.dp(14), 0, 0);
        content.addView(next);

        LinearLayout footer = new LinearLayout(host);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(host.ui.dp(20), host.ui.dp(10), host.ui.dp(20), host.ui.dp(14));
        footer.setBackground(host.ui.edgeRule(host.line, true));
        boolean myTurn = o.pending() && o.turn.equals(host.currentStudentEmail);
        if (myTurn) {
            Button accept = host.ui.primaryButton("Approve offer");
            accept.setOnClickListener(v -> {
                if (p == null || !p.status.equals("Available") || p.hidden || p.quantity <= 0) {
                    host.ui.showSimpleMessage("Unavailable", p != null && p.quantity <= 0
                            ? "This listing is sold out." : "This one's no longer available.");
                    return;
                }
                page.dismiss();
                try { respondToOffer(id, "Accept", ""); }
                catch (RuntimeException e) { host.ui.showSimpleMessage("Offer", e.getMessage()); }
            });
            footer.addView(accept, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
            Button counter = host.ui.secondaryButton("Counteroffer");
            LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
            counterParams.topMargin = host.ui.dp(8);
            counter.setOnClickListener(v -> { page.dismiss(); counterOffer(id); });
            footer.addView(counter, counterParams);
            TextView decline = host.ui.textButton("Decline offer");
            decline.setOnClickListener(v -> {
                page.dismiss();
                try { respondToOffer(id, "Decline", ""); }
                catch (RuntimeException e) { host.ui.showSimpleMessage("Offer", e.getMessage()); }
            });
            footer.addView(decline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42)));
        } else if (o.state.equals("Accepted") && o.buyer.equals(host.currentStudentEmail)
                && o.expires > System.currentTimeMillis() && p != null) {
            Button buy = host.ui.primaryButton("Buy now");
            buy.setOnClickListener(v -> { page.dismiss(); host.order.showPaymentDialog(p); });
            footer.addView(buy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
            TextView cancel = host.ui.textButton("Cancel reservation");
            cancel.setOnClickListener(v -> { page.dismiss(); cancelOffer(id); });
            footer.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42)));
        } else if (o.pending() || o.state.equals("Accepted")) {
            if (o.buyer.equals(host.currentStudentEmail)) {
                TextView cancel = host.ui.textButton(o.pending() ? "Cancel offer" : "Cancel reservation");
                cancel.setOnClickListener(v -> { page.dismiss(); cancelOffer(id); });
                footer.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44)));
            }
            if (p != null) {
                Button view = host.ui.secondaryButton("View listing");
                view.setOnClickListener(v -> { page.dismiss(); showProductDetails(p); });
                footer.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46)));
            }
        } else {
            if (p != null) {
                Button view = host.ui.secondaryButton("View listing");
                view.setOnClickListener(v -> { page.dismiss(); showProductDetails(p); });
                footer.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46)));
            }
            TextView close = host.ui.textButton("Close");
            close.setOnClickListener(v -> page.dismiss());
            footer.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42)));
        }
        root.addView(footer);

        page.setContentView(root);
        page.setCanceledOnTouchOutside(false);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
        }
    }

    LinearLayout offerPartyRow(String role, String name, boolean you) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
        TextView avatar = host.ui.titleText(host.ui.initials(name), host.blue, 12);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(host.ui.round(host.nightActive ? Color.rgb(40, 52, 70) : Color.rgb(234, 242, 255), 999));
        row.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(34), host.ui.dp(34)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(10), 0, 0, 0);
        copy.addView(host.ui.bodyText(role, host.muted, 10));
        copy.addView(host.ui.titleText(name + (you ? " (you)" : ""), host.text, 13));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    String offerNextStep(CampusFeatures.Offer o) {
        if (o.state.equals("Accepted") && o.expires > System.currentTimeMillis()) {
            return o.buyer.equals(host.currentStudentEmail)
                    ? "You have this item reserved. Complete the order before the reservation ends."
                    : "The buyer has 24 hours to complete the order. Offers are paused until then.";
        }
        if (o.pending() && o.turn.equals(host.currentStudentEmail)) {
            return "This offer is waiting for your reply. Accepting reserves the item for the buyer for 24 hours.";
        }
        if (o.pending()) return "Waiting for " + host.studentName(o.turn) + " to reply.";
        if (o.state.equals("Accepted")) return "This reservation has expired. The item can be offered again.";
        return "This offer is closed. You can still view the listing.";
    }

    void counterOffer(String id) {
        LinearLayout form = host.ui.workflowForm();
        EditText amount = host.ui.listingField(form, "Counteroffer (ZAR)", "Amount", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Button send = host.ui.primaryButton("Send counteroffer");
        form.addView(send, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        Dialog sheet = host.ui.showBottomSheet("Counteroffer", form);
        send.setOnClickListener(v -> {
            String value = host.ui.text(amount).trim();
            if (!value.matches(Constants.PRICE_PATTERN) || new BigDecimal(value).signum() <= 0) {
                amount.setError("Enter an amount above R0");
                return;
            }
            try {
                sheet.dismiss();
                respondToOffer(id, "Counter", value);
            } catch (RuntimeException e) {
                amount.setError(e.getMessage() == null ? "Couldn't send that counteroffer" : e.getMessage());
            }
        });
    }

    public void respondToOffer(String id, String action, String amount) {
        if ("Accept".equalsIgnoreCase(action)) {
            CampusFeatures.Offer before = host.featureStore.data.findOffer(id);
            MainActivity.Product stock = before == null ? null : productById(before.listing);
            if (stock != null && stock.quantity <= 0) {
                throw new RuntimeException("This listing is sold out. Restock it before accepting the offer.");
            }
        }
        host.featureStore.update(f -> f.respond(id, host.currentStudentEmail, action, amount, System.currentTimeMillis()));
        CampusFeatures.Offer o = host.featureStore.data.findOffer(id);
        cloudUpdateOffer(o);
        String other = host.currentStudentEmail.equals(o.buyer) ? o.seller : o.buyer;
        String title = "Offer " + o.state.toLowerCase(Locale.ROOT);
        String body = "R" + o.amount;
        host.notification.addNotification(other, title, body, "marketplace", "");
        host.notification.sendPush(other, title, body, "marketplace", "");
        host.showAppScreen("Offers");
    }

    public void cancelOffer(String id) {
        try {
            host.featureStore.update(f -> f.cancelOffer(id, host.currentStudentEmail));
            cloudUpdateOffer(host.featureStore.data.findOffer(id));
            host.showAppScreen("Offers");
        }
        catch (RuntimeException e) { host.ui.showSimpleMessage("Offer", e.getMessage()); }
    }

    void pickupActions(MainActivity.TransactionRecord order) {
        CampusFeatures.Pickup existing = host.featureStore.data.pickup(order.id);
        final Dialog[] sheetRef = {null};
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, host.ui.dp(4), 0, 0);
        if (existing == null) {
            body.addView(host.ui.titleText("No pickup planned yet", host.text, 16));
            body.addView(host.ui.bodyText("Agree a public campus spot and time. Both students confirm before the handover.",
                    host.muted, 12));
            Button propose = host.ui.primaryButton("Propose pickup");
            propose.setOnClickListener(v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                showPickupForm(order);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
            params.topMargin = host.ui.dp(16);
            body.addView(propose, params);
        } else {
            LinearLayout top = host.ui.horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(host.ui.titleText(existing.location, host.text, 16),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(host.ui.statusPill(existing.agreed() ? "Agreed" : "Awaiting"));
            body.addView(top);
            body.addView(host.ui.bodyText(host.messageTime(existing.time), host.muted, 12));
            body.addView(host.ui.bodyText("Arrangement v" + Math.max(1, existing.revision)
                    + (existing.updatedAt > 0 ? " · updated " + host.messageTime(existing.updatedAt) : ""),
                    host.muted, 11));
            body.addView(pickupTickRow("Buyer confirmed", existing.confirmed.contains(order.buyerEmail)));
            body.addView(pickupTickRow("Seller confirmed", existing.confirmed.contains(order.sellerEmail)));
            if (!existing.confirmed.contains(host.currentStudentEmail)) {
                Button agree = host.ui.primaryButton("Agree to pickup");
                agree.setOnClickListener(v -> {
                    try {
                        host.featureStore.update(f -> f.confirmPickup(order.id, host.currentStudentEmail));
                        CampusFeatures.Pickup updated = host.featureStore.data.pickup(order.id);
                        host.order.cloudSavePickup(updated);
                        String other = order.isBuyer(host.currentStudentEmail) ? order.sellerEmail : order.buyerEmail;
                        host.notification.addNotification(other, "Pickup confirmed",
                                order.itemTitle + " · " + updated.location + " · arrangement v" + Math.max(1, updated.revision),
                                "payment", "", order.id);
                        host.notification.sendPush(other, "Pickup confirmed",
                                order.itemTitle + " · " + updated.location, "payment", "", order.id);
                        host.notification.showCampusNotification(updated.agreed() ? "Pickup agreed" : "Pickup confirmed",
                                order.itemTitle + " · " + updated.location, "payment", "", order.id);
                        if (sheetRef[0] != null) sheetRef[0].dismiss();
                        Toast.makeText(host, updated.agreed() ? "Pickup agreed by both students." : "Pickup confirmed. Waiting for the other student.",
                                Toast.LENGTH_SHORT).show();
                        host.showAppScreen("Payments");
                    } catch (RuntimeException e) {
                        Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
                params.topMargin = host.ui.dp(16);
                body.addView(agree, params);
            }
            Button change = host.ui.secondaryButton("Propose new pickup");
            change.setOnClickListener(v -> {
                if (sheetRef[0] != null) sheetRef[0].dismiss();
                showPickupForm(order);
            });
            LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
            changeParams.topMargin = host.ui.dp(8);
            body.addView(change, changeParams);
        }
        sheetRef[0] = host.ui.showBottomSheet("Campus pickup", body);
    }

    private LinearLayout pickupTickRow(String label, boolean confirmed) {
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(6), 0, 0);
        TextView tick = host.ui.bodyText(confirmed ? "✓" : "○", confirmed ? host.readableInk(host.green) : host.muted, 13);
        tick.setGravity(Gravity.CENTER);
        row.addView(tick, new LinearLayout.LayoutParams(host.ui.dp(24), host.ui.dp(24)));
        row.addView(host.ui.bodyText(label, confirmed ? host.text : host.muted, 12),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(host.ui.statusPill(confirmed ? "Confirmed" : "Waiting"));
        return row;
    }

    private void showPickupForm(MainActivity.TransactionRecord order) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, host.ui.dp(4), 0, 0);
        EditText location = host.ui.listingField(form, "Public campus meeting point", "Location", InputType.TYPE_CLASS_TEXT);
        Button date = host.ui.secondaryButton("Choose date and time");
        final long[] time = {0};
        date.setOnClickListener(v -> host.ui.chooseFutureDateTime(date, time));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46));
        dateParams.topMargin = host.ui.dp(10);
        form.addView(date, dateParams);
        final Dialog[] formRef = {null};
        Button propose = host.ui.primaryButton("Send pickup proposal");
        propose.setOnClickListener(v -> {
            String meetingPoint = host.ui.text(location).trim();
            if (meetingPoint.length() < 2) {
                location.setError("Add a meeting point");
                return;
            }
            if (time[0] <= System.currentTimeMillis()) {
                Toast.makeText(host, "Choose a future date and time.", Toast.LENGTH_LONG).show();
                return;
            }
            final CampusFeatures.Pickup[] created = {null};
            try {
                host.featureStore.update(f -> created[0] = f.schedule(order.id, order.buyerEmail, order.sellerEmail,
                        host.currentStudentEmail, meetingPoint, time[0], System.currentTimeMillis()));
                host.order.cloudSavePickup(created[0]);
                String other = order.isBuyer(host.currentStudentEmail) ? order.sellerEmail : order.buyerEmail;
                String change = created[0].revision <= 1 ? "Pickup proposed" : "Pickup changed";
                host.notification.addNotification(other, change,
                        order.itemTitle + " · " + meetingPoint + " · " + host.messageTime(time[0])
                                + " · arrangement v" + created[0].revision, "payment", "", order.id);
                host.notification.sendPush(other, change,
                        order.itemTitle + " · " + meetingPoint, "payment", "", order.id);
                host.notification.showCampusNotification(change,
                        "Arrangement v" + created[0].revision + " · waiting for confirmation.",
                        "payment", "", order.id);
                if (formRef[0] != null) formRef[0].dismiss();
                host.showAppScreen("Payments");
            } catch (RuntimeException e) {
                Toast.makeText(host, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams proposeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
        proposeParams.topMargin = host.ui.dp(16);
        form.addView(propose, proposeParams);
        form.addView(host.ui.bodyText("The other student must confirm the same pickup for it to be agreed.", host.muted, 11));
        formRef[0] = host.ui.showBottomSheet("Schedule pickup", form);
    }

    public void listingSection(LinearLayout form, String title) {
        TextView heading = host.ui.titleText(title, host.text, 17);
        heading.setPadding(0, host.ui.dp(22), 0, host.ui.dp(12));
        form.addView(heading);
    }

    // Use the system file picker so access survives.
    public void pickListingPhotos() {
        if (host.draftPhotos.size() >= Constants.MAX_LISTING_PHOTOS) {
            Toast.makeText(host, "You can add up to 5 photos.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.setType("image/*");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            host.startActivityForResult(picker, host.LISTING_PHOTO_REQUEST);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(host, "No photo picker is available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    // Show allowed document types; check them again before upload.
    public void pickListingDocument() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, host.DOCUMENT_MIME_TYPES);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            host.startActivityForResult(picker, host.LISTING_DOCUMENT_REQUEST);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(host, "No document picker is available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    // Rebuild the photo strip; the first photo becomes the cover.
    public void renderDraftPhotos() {
        if (host.listingPhotoContainer == null) return;
        host.listingPhotoContainer.removeAllViews();
        host.listingPhotoCount.setText(host.draftPhotos.size() + " / 5 photos");
        for (int i = 0; i < host.draftPhotos.size(); i++) {
            Uri uri = host.draftPhotos.get(i);
            LinearLayout tile = new LinearLayout(host);
            tile.setOrientation(LinearLayout.VERTICAL);
            ImageView photo = listingImage(uri, "Listing photo " + (i + 1), 300);
            tile.addView(photo, new LinearLayout.LayoutParams(host.ui.dp(108), host.ui.dp(108)));
            TextView remove = host.ui.bodyText(i == 0 ? "Cover - Remove" : "Remove", host.blue, 12);
            remove.setGravity(Gravity.CENTER);
            remove.setOnClickListener(v -> { host.draftPhotos.remove(uri); renderDraftPhotos(); });
            tile.addView(remove, new LinearLayout.LayoutParams(host.ui.dp(108), host.ui.dp(48)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(host.ui.dp(108), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = host.ui.dp(10);
            host.listingPhotoContainer.addView(tile, params);
        }
    }

    // Show the document button or the chosen file.
    public void renderDraftDocument() {
        if (host.listingDocumentContainer == null) return;
        host.listingDocumentContainer.removeAllViews();
        if (host.draftDocumentUri == null || host.draftDocumentName.isEmpty()) {
            Button add = host.ui.secondaryButton("Add document");
            add.setTextColor(host.blue);
            add.setBackground(host.ui.strokedRound(Color.WHITE, 8, host.border));
            add.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_upload, 0, 0, 0);
            add.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(host.blue));
            add.setOnClickListener(v -> pickListingDocument());
            host.listingDocumentContainer.addView(add, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
            return;
        }
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        row.setBackground(host.ui.round(host.surface(), 8));
        View icon = host.new CampusAssetView(host, "note", host.blue);
        icon.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
        icon.setBackground(host.ui.round(host.nightActive ? Color.rgb(48, 48, 54) : Color.rgb(234, 242, 255), 8));
        row.addView(icon, new LinearLayout.LayoutParams(host.ui.dp(38), host.ui.dp(38)));
        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
        TextView name = host.ui.titleText(host.draftDocumentName, host.text, 13);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);
        String state = host.draftDocumentUrl.isEmpty() ? " / uploads when published" : " / uploaded";
        copy.addView(host.ui.bodyText(documentTypeLabel(host.draftDocumentMime, host.draftDocumentName)
                + " / " + formatDocumentSize(host.draftDocumentSize) + state, host.muted, 10));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.END);
        TextView replace = host.ui.linkText("Replace");
        replace.setTextSize(11);
        replace.setOnClickListener(v -> pickListingDocument());
        actions.addView(replace);
        TextView remove = host.ui.linkText("Remove");
        remove.setTextSize(11);
        remove.setTextColor(host.muted);
        remove.setOnClickListener(v -> {
            host.draftDocumentUri = null;
            host.draftDocumentUrl = "";
            host.draftDocumentName = "";
            host.draftDocumentMime = "";
            host.draftDocumentSize = 0;
            renderDraftDocument();
        });
        actions.addView(remove);
        row.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.listingDocumentContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public String documentDisplayName(Uri uri) {
        try (Cursor cursor = host.getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name.trim();
            }
        } catch (RuntimeException ignored) { }
        String last = uri.getLastPathSegment();
        return last == null || last.trim().isEmpty() ? "document" : last;
    }

    public long documentDisplaySize(Uri uri) {
        try (Cursor cursor = host.getContentResolver().query(uri,
                new String[] {OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (RuntimeException ignored) { }
        return 0;
    }

    public String documentExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1
                ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public String documentMimeForName(String name) {
        String ext = documentExtension(name);
        if (ext.equals("pdf")) return "application/pdf";
        if (ext.equals("doc")) return "application/msword";
        if (ext.equals("docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (ext.equals("ppt")) return "application/vnd.ms-powerpoint";
        if (ext.equals("pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (ext.equals("txt")) return "text/plain";
        if (ext.equals("rtf")) return "application/rtf";
        if (ext.equals("odt")) return "application/vnd.oasis.opendocument.text";
        return "application/octet-stream";
    }

    public boolean isAllowedDocument(String name, String mime) {
        String ext = documentExtension(name);
        if (ext.equals("pdf") || ext.equals("doc") || ext.equals("docx") || ext.equals("ppt")
                || ext.equals("pptx") || ext.equals("txt") || ext.equals("rtf") || ext.equals("odt")) {
            return true;
        }
        for (String type : host.DOCUMENT_MIME_TYPES) if (type.equals(mime)) return true;
        return false;
    }

    public String documentTypeLabel(String mime, String name) {
        String ext = documentExtension(name);
        if (!ext.isEmpty()) return ext.toUpperCase(Locale.ROOT);
        if (mime == null || mime.isEmpty()) return "Document";
        if (mime.contains("pdf")) return "PDF";
        if (mime.contains("word")) return "Word";
        if (mime.contains("presentation") || mime.contains("powerpoint")) return "PowerPoint";
        if (mime.startsWith("text/")) return "Text";
        return "Document";
    }

    public String formatDocumentSize(long bytes) {
        if (bytes <= 0) return "Size unavailable";
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        return bytes + " B";
    }

    public boolean documentChanged(MainActivity.Product before, MainActivity.Product after) {
        return !MainActivity.safe(before.documentUrl).equals(MainActivity.safe(after.documentUrl))
                || !MainActivity.safe(before.documentName).equals(MainActivity.safe(after.documentName))
                || !MainActivity.safe(before.documentMime).equals(MainActivity.safe(after.documentMime))
                || before.documentSize != after.documentSize
                || !MainActivity.safe(String.valueOf(before.documentUri)).equals(MainActivity.safe(String.valueOf(after.documentUri)));
    }

    // Load web photos with Glide; resize local photos in the background.
    /** Consistent product image or a neutral no-photo tile. */
    View listingThumbnail(MainActivity.Product product, boolean large) {
        if (!product.photos.isEmpty()) {
            return listingImage(product.photos.get(0), product.title, large ? 900 : 300);
        }
        LinearLayout placeholder = new LinearLayout(host);
        placeholder.setOrientation(LinearLayout.VERTICAL);
        placeholder.setGravity(Gravity.CENTER);
        int tile = host.nightActive ? Color.rgb(48, 48, 54)
                : product.category.equals("Technology") ? Color.rgb(220, 225, 214)
                : product.category.equals("Books") ? Color.rgb(234, 219, 208) : Color.rgb(226, 224, 215);
        placeholder.setBackground(host.ui.round(tile, 10));
        placeholder.setContentDescription(product.title + ", no photo added");
        String icon = product.category.equals("Books") ? "book"
                : product.category.equals("Technology") ? "laptop" : "box";
        View symbol = host.new CampusAssetView(host, icon, host.muted);
        placeholder.addView(symbol, new LinearLayout.LayoutParams(host.ui.dp(large ? 48 : 26), host.ui.dp(large ? 48 : 26)));
        if (large) {
            TextView none = host.ui.bodyText("No photo yet", host.muted, 11);
            none.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams noneParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            noneParams.gravity = Gravity.CENTER_HORIZONTAL;
            noneParams.topMargin = host.ui.dp(8);
            placeholder.addView(none, noneParams);
        }
        return placeholder;
    }

    /** Save toggle with a comfortable 44dp tap target. */
    View saveHeart(MainActivity.Product product, Runnable after) {
        boolean saved = product.savedBy.contains(host.currentStudentEmail);
        FrameLayout target = new FrameLayout(host);
        target.setContentDescription(saved ? "Unsave listing" : "Save listing");
        target.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        View heart = heartIcon(saved);
        target.addView(heart, new FrameLayout.LayoutParams(host.ui.dp(30), host.ui.dp(30), Gravity.CENTER));
        target.setOnClickListener(v -> {
            boolean wasSaved = product.savedBy.contains(host.currentStudentEmail);
            if (!product.savedBy.remove(host.currentStudentEmail)) product.savedBy.add(host.currentStudentEmail);
            boolean nowSaved = product.savedBy.contains(host.currentStudentEmail);
            int oldCount = product.favouriteCount;
            product.favouriteCount = Math.max(0, product.favouriteCount + (nowSaved ? 1 : -1));
            if (!saveProducts()) {
                if (wasSaved) product.savedBy.add(host.currentStudentEmail); else product.savedBy.remove(host.currentStudentEmail);
                product.favouriteCount = oldCount;
                Toast.makeText(host, "Couldn't save that change.", Toast.LENGTH_SHORT).show();
                return;
            }
            syncFavouriteCount(product, nowSaved);
            syncFavouriteDoc(product, nowSaved);
            host.savedListings = 0;
            for (MainActivity.Product item : host.products) if (item.savedBy.contains(host.currentStudentEmail)) host.savedListings++;
            target.setContentDescription(nowSaved ? "Unsave listing" : "Save listing");
            target.removeAllViews();
            target.addView(heartIcon(nowSaved), new FrameLayout.LayoutParams(host.ui.dp(30), host.ui.dp(30), Gravity.CENTER));
            if (after != null) after.run();
        });
        return target;
    }

    View heartIcon(boolean saved) {
        View heart = host.new CampusAssetView(host, "heart", saved ? host.blue : host.muted);
        heart.setPadding(host.ui.dp(7), host.ui.dp(7), host.ui.dp(7), host.ui.dp(7));
        return heart;
    }

    public ImageView listingImage(Uri uri, String description, int targetPixels) {
        ImageView image = new ImageView(host);
        image.setContentDescription(description);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(host.ui.round(host.page, 8));
        image.setClipToOutline(true);
        if (uri != null && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            com.bumptech.glide.Glide.with(host).load(uri.toString()).into(image);
            return image;
        }
        host.imageLoader.execute(() -> {
            Bitmap result = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream stream = host.getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(stream, null, options);
                }
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > targetPixels * 2) {
                    options.inSampleSize *= 2;
                }
                options.inJustDecodeBounds = false;
                try (InputStream stream = host.getContentResolver().openInputStream(uri)) {
                    result = BitmapFactory.decodeStream(stream, null, options);
                }
                // Phone cameras often save photos sideways, so rotate them.
                if (result != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    int orientation = android.media.ExifInterface.ORIENTATION_NORMAL;
                    try (InputStream stream = host.getContentResolver().openInputStream(uri)) {
                        orientation = new android.media.ExifInterface(stream).getAttributeInt(
                                android.media.ExifInterface.TAG_ORIENTATION, orientation);
                    } catch (Exception ignored) { }
                    Matrix transform = new Matrix();
                    switch (orientation) {
                        case 2: transform.setScale(-1, 1); break;
                        case 3: transform.setRotate(180); break;
                        case 4: transform.setScale(1, -1); break;
                        case 5: transform.setRotate(90); transform.postScale(-1, 1); break;
                        case 6: transform.setRotate(90); break;
                        case 7: transform.setRotate(-90); transform.postScale(-1, 1); break;
                        case 8: transform.setRotate(-90); break;
                        default: break;
                    }
                    if (!transform.isIdentity()) {
                        Bitmap oriented = Bitmap.createBitmap(result, 0, 0, result.getWidth(), result.getHeight(), transform, true);
                        if (oriented != result) result.recycle();
                        result = oriented;
                    }
                }
            } catch (Exception e) {
                // Show a neutral placeholder if photo access fails.
            }
            final Bitmap bitmap = result;
            image.post(() -> {
                if (host.isDestroyed()) return;
                if (bitmap != null) image.setImageBitmap(bitmap);
                else image.setImageResource(android.R.drawable.ic_menu_gallery);
            });
        });
        return image;
    }

    public void showListingPhotos(MainActivity.Product product) {
        LinearLayout gallery = new LinearLayout(host);
        gallery.setOrientation(LinearLayout.VERTICAL);
        gallery.setPadding(host.ui.dp(16), host.ui.dp(8), host.ui.dp(16), host.ui.dp(16));
        for (int i = 0; i < product.photos.size(); i++) {
            gallery.addView(listingImage(product.photos.get(i), product.title + " photo " + (i + 1), 1000),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(260)));
            gallery.addView(host.ui.space(1, host.ui.dp(12)));
        }
        ScrollView scroll = new ScrollView(host);
        scroll.addView(gallery);
        AlertDialog dialog = host.ui.dialog().setTitle(product.title).setView(scroll).setPositiveButton("Done", null).show();
        host.ui.addPageClose(dialog);
    }

    public void cloudPushListing(MainActivity.Product product, boolean isNew) {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty() || product.hidden) {
            return;
        }
        // Drafts stay here. New cloud listings start as AVAILABLE; edits update the same one.
        if (product.status == null || product.status.equalsIgnoreCase("Draft")) {
            return;
        }
        if (isNew && !product.status.equalsIgnoreCase("Available")) {
            return;
        }
        // Upload large files in the background.
        new Thread(() -> {
            String imageUrl = "";
            java.util.List<String> urls = new java.util.ArrayList<>();
            // Upload photos first; keep the ones that work and warn about failures.
            for (Uri uri : product.photos) {
                String uploaded = CloudSync.uploadPhoto(host, uri);
                if (uploaded != null && !uploaded.isEmpty()) {
                    urls.add(uploaded);
                }
            }
            final String finalImage = urls.isEmpty() ? "" : urls.get(0);
            final java.util.List<String> finalUrls = urls;
            final int failedPhotos = Math.max(0, product.photos.size() - urls.size());

            // Upload the document only when it is new or changed.
            String documentUrl = product.documentUrl == null ? "" : product.documentUrl;
            boolean documentUploadFailed = false;
            if (product.hasDocument() && (product.documentDirty || documentUrl.isEmpty())) {
                String uploaded = CloudSync.uploadDocument(host, product.documentUri,
                        product.documentName, product.documentMime);
                if (uploaded != null && !uploaded.isEmpty()) {
                    documentUrl = uploaded;
                } else {
                    documentUploadFailed = true;
                }
            }
            final String finalDocumentUrl = documentUrl;
            final boolean finalDocumentFailed = documentUploadFailed;
            final String finalDocumentName = finalDocumentUrl.isEmpty() ? "" : product.documentName;
            final String finalDocumentMime = finalDocumentUrl.isEmpty() ? "" : product.documentMime;
            final long finalDocumentSize = finalDocumentUrl.isEmpty() ? 0 : product.documentSize;

            host.runOnUiThread(() -> {
                if (host.isDestroyed() || host.isFinishing()) return;
                ListingRepository repo = new ListingRepository();
                if (finalDocumentFailed) {
                    Toast.makeText(host,
                            "The document couldn't be uploaded. The listing was saved without it.",
                            Toast.LENGTH_LONG).show();
                }
                if (failedPhotos > 0) {
                    Toast.makeText(host,
                            failedPhotos + " photo(s) couldn't be uploaded. The listing was saved without them.",
                            Toast.LENGTH_LONG).show();
                }
                // New listings get a new document; edits update the linked one.
                if (isNew) {
                    double priceValue = 0;
                    try {
                        priceValue = Double.parseDouble(product.price);
                    } catch (NumberFormatException ignored) { }
                    repo.createWithId(product.id, uid, host.currentStudentName, product.title,
                            product.description, priceValue, product.category, product.condition,
                            product.specs, product.location, product.delivery, "AVAILABLE", finalImage, finalUrls,
                            "", finalDocumentName, finalDocumentMime, finalDocumentSize,
                            product.quantity, new ListingRepository.IdCallback() {
                                @Override public void onOk(String docId) {
                                    CloudSync.putRemoteId(host, product.id, docId);
                                    CloudSync.directoryPut(host, uid, host.currentStudentEmail);
                                    rememberUploadedDocument(product, finalDocumentUrl);
                                    savePrivateListingDocument(docId, finalDocumentUrl,
                                            finalDocumentName, finalDocumentMime, finalDocumentSize);
                                    java.util.Map<String, Object> extra = new java.util.HashMap<>();
                                    extra.put("negotiable", product.negotiable);
                                    extra.put("quantity", Math.max(0, product.quantity));
                                    extra.put("viewCount", product.viewCount);
                                    extra.put("favouriteCount", product.favouriteCount);
                                    extra.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                                    new ListingRepository().update(docId, extra, new ListingRepository.DoneCallback() {
                                        @Override public void onOk() { }
                                        @Override public void onBad(String message) { }
                                    });
                                }
                                @Override public void onBad(String message) {
                                    host.ui.showCloudRetry("Listing saved on this device",
                                            "The marketplace copy failed: " + message,
                                            () -> cloudPushListing(product, true));
                                }
                            });
                } else {
                    String docId = CloudSync.remoteId(host, product.id);
                    if (docId.isEmpty()) {
                        host.ui.showCloudRetry("Listing saved on this device",
                                "The cloud copy is missing, so other students can't see it yet.",
                                () -> cloudPushListing(product, true));
                        return;
                    }
                    java.util.Map<String, Object> fields = new java.util.HashMap<>();
                    fields.put("listingN", product.title);
                    fields.put("description", product.description);
                    fields.put("specs", product.specs);
                    try {
                        fields.put("price", Double.parseDouble(product.price));
                    } catch (NumberFormatException ignored) { }
                    fields.put("category", product.category);
                    fields.put("condition", product.condition);
                    fields.put("status", product.status.toUpperCase());
                    if (!finalImage.isEmpty()) {
                        fields.put("imageURL", finalImage);
                    }
                    if (!finalUrls.isEmpty()) {
                        fields.put("photos", finalUrls);
                    }
                    fields.put("documentURL", "");
                    fields.put("documentName", finalDocumentName);
                    fields.put("documentMime", finalDocumentMime);
                    fields.put("documentSize", finalDocumentSize);
                    fields.put("negotiable", product.negotiable);
                    fields.put("quantity", Math.max(0, product.quantity));
                    fields.put("viewCount", product.viewCount);
                    fields.put("favouriteCount", product.favouriteCount);
                    if (expiryTime(product) > 0) fields.put("expiresAt",
                            new com.google.firebase.Timestamp(new Date(expiryTime(product))));
                    fields.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                    repo.update(docId, fields, new ListingRepository.DoneCallback() {
                        @Override public void onOk() {
                            rememberUploadedDocument(product, finalDocumentUrl);
                            savePrivateListingDocument(docId, finalDocumentUrl,
                                    finalDocumentName, finalDocumentMime, finalDocumentSize);
                        }
                        @Override public void onBad(String message) {
                            host.ui.showCloudRetry("Listing saved on this device",
                                    "The marketplace copy failed: " + message,
                                    () -> cloudPushListing(product, false));
                        }
                    });
                }
            });
        }).start();
    }

    public void rememberUploadedDocument(MainActivity.Product product, String url) {
        if (url != null && !url.isEmpty()) {
            product.documentUrl = url;
        }
        product.documentDirty = false;
        saveProducts();
    }

    private void savePrivateListingDocument(String listingId, String url, String name,
                                            String mime, long size) {
        if (listingId == null || listingId.isEmpty()) return;
        com.google.firebase.firestore.DocumentReference doc = com.google.firebase.firestore
                .FirebaseFirestore.getInstance().collection("listingDocuments").document(listingId);
        if (url == null || url.isEmpty()) {
            doc.delete().addOnFailureListener(e -> android.util.Log.w("CampusTrade",
                    "private document delete failed: " + e.getMessage()));
            return;
        }
        String safeName = name == null ? "" : name;
        if (safeName.length() > 255) safeName = safeName.substring(0, 255);
        String safeMime = mime == null ? "" : mime;
        if (safeMime.length() > 100) safeMime = safeMime.substring(0, 100);
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("sellerID", CloudSync.uid(host));
        fields.put("documentURL", url);
        fields.put("documentName", safeName);
        fields.put("documentMime", safeMime);
        fields.put("documentSize", Math.max(0, size));
        fields.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        doc.set(fields).addOnFailureListener(e -> android.util.Log.w("CampusTrade",
                "private document save failed: " + e.getMessage()));
    }

    /** Copy a status change or delete to Firebase. */
    void cloudMirrorListing(MainActivity.Product product, boolean deleted) {
        // Only the seller can change the cloud listing;
        // the buyer's device records the order instead.
        if (!ownsListing(product)) {
            return;
        }
        String docId = CloudSync.remoteId(host, product.id);
        if (docId.isEmpty()) {
            // A draft or deleted local item has no cloud copy.
            if (deleted || !"Available".equalsIgnoreCase(product.status)) {
                return;
            }
            host.ui.showCloudRetry("Listing saved on this device",
                    "The cloud copy is missing, so other students can't see this listing.",
                    () -> cloudPushListing(product, true));
            return;
        }
        ListingRepository repo = new ListingRepository();
        if (deleted) {
            repo.delete(docId, new ListingRepository.DoneCallback() {
                @Override public void onOk() { }
                @Override public void onBad(String message) {
                    host.ui.showCloudRetry("Listing removed on this device",
                            "The marketplace copy couldn't be removed: " + message,
                            () -> cloudMirrorListing(product, true));
                }
            });
            return;
        }
        java.util.Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("status", product.status.toUpperCase());
        fields.put("quantity", Math.max(0, product.quantity));
        if (expiryTime(product) > 0) fields.put("expiresAt",
                new com.google.firebase.Timestamp(new Date(expiryTime(product))));
        repo.update(docId, fields, new ListingRepository.DoneCallback() {
            @Override public void onOk() { }
            @Override public void onBad(String message) {
                host.ui.showCloudRetry("Listing saved on this device",
                        "The marketplace status couldn't be updated: " + message,
                        () -> cloudMirrorListing(product, false));
            }
        });
    }

    private void incrementView(MainActivity.Product product) {
        product.viewCount++;
        product.updatedTime = System.currentTimeMillis();
        saveProducts();
        String docId = remoteListingId(product);
        if (docId.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("listings").document(docId)
                .update("viewCount", com.google.firebase.firestore.FieldValue.increment(1),
                        "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "View count sync failed: " + e.getMessage()));
    }

    /** Load saved listings from Firebase so they survive a reinstall. */
    public void syncFavourites() {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("favourites")
                .whereEqualTo("userId", uid).get()
                .addOnSuccessListener(snapshot -> {
                    java.util.Set<String> remoteIds = new java.util.LinkedHashSet<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        String listingId = doc.getString("listingId");
                        if (listingId != null && !listingId.isEmpty()) remoteIds.add(listingId);
                    }
                    boolean changed = false;
                    for (MainActivity.Product product : host.products) {
                        String remoteId = remoteListingId(product);
                        if (remoteId.isEmpty()) continue;
                        boolean saved = remoteIds.contains(remoteId);
                        boolean had = product.savedBy.contains(host.currentStudentEmail);
                        if (saved && !had) { product.savedBy.add(host.currentStudentEmail); changed = true; }
                        if (!saved && had) { product.savedBy.remove(host.currentStudentEmail); changed = true; }
                    }
                    if (changed && saveProducts() && "Market".equals(host.currentTab)) renderListings();
                })
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "Favourites sync failed: " + e.getMessage()));
    }

    private String remoteListingId(MainActivity.Product product) {
        String remote = CloudSync.remoteId(host, product.id);
        if (!remote.isEmpty()) return remote;
        return product.id.startsWith("remote-") ? product.id.substring("remote-".length()) : "";
    }

    private void syncFavouriteDoc(MainActivity.Product product, boolean saved) {
        String uid = CloudSync.uid(host);
        String listingId = remoteListingId(product);
        if (uid.isEmpty() || listingId.isEmpty()) return;
        com.google.firebase.firestore.DocumentReference ref = com.google.firebase.firestore.FirebaseFirestore
                .getInstance().collection("favourites").document(uid + "_" + listingId);
        if (saved) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("userId", uid);
            data.put("listingId", listingId);
            data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            ref.set(data).addOnFailureListener(e -> android.util.Log.w("CampusTrade", "Favourite save failed: " + e.getMessage()));
        } else {
            ref.delete().addOnFailureListener(e -> android.util.Log.w("CampusTrade", "Favourite delete failed: " + e.getMessage()));
        }
    }

    private void syncFavouriteCount(MainActivity.Product product, boolean saved) {
        String docId = remoteListingId(product);
        if (docId.isEmpty()) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("listings").document(docId)
                .update("favouriteCount", com.google.firebase.firestore.FieldValue.increment(saved ? 1 : -1),
                        "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade", "Favourite count sync failed: " + e.getMessage()));
    }

    /** Start the live Firestore feeds used by the marketplace. */
    public void startLiveUpdates() {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty() || listingsListener != null) return;
        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();
        listingsListener = db.collection("listings")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        android.util.Log.w("CampusTrade", "listing listener: " + error.getMessage());
                        return;
                    }
                    if (snap == null || host.isDestroyed() || host.isFinishing()) return;
                    handleLiveListings(snap);
                });
        deletedListingsListener = db.collection("deletedListings").limit(200)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        android.util.Log.w("CampusTrade", "tombstone listener: " + error.getMessage());
                        return;
                    }
                    if (snap == null || host.isDestroyed() || host.isFinishing()) return;
                    applyDeletedListings(snap.getDocuments());
                });
        startOfferListeners(uid);
    }

    /** Remove the live feeds when the student signs out or the app closes. */
    public void stopLiveUpdates() {
        if (listingsListener != null) { listingsListener.remove(); listingsListener = null; }
        if (deletedListingsListener != null) { deletedListingsListener.remove(); deletedListingsListener = null; }
        if (offersBuyerListener != null) { offersBuyerListener.remove(); offersBuyerListener = null; }
        if (offersSellerListener != null) { offersSellerListener.remove(); offersSellerListener = null; }
        currentOfferPage = null;
        currentOfferId = "";
    }

    /** Apply a full listing snapshot without waiting for a screen reopen. */
    private void handleLiveListings(com.google.firebase.firestore.QuerySnapshot snapshot) {
        String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<com.campustrade.auth.model.Listing> items = new java.util.ArrayList<>();
        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
            com.campustrade.auth.model.Listing item =
                    doc.toObject(com.campustrade.auth.model.Listing.class);
            if (item == null) continue;
            item.setDocId(doc.getId());
            seen.add(doc.getId());
            items.add(item);
        }
        boolean changed = false;
        for (com.campustrade.auth.model.Listing item : items) {
            MainActivity.Product existing = productForRemoteListing(item.getDocId());
            if (existing == null) {
                changed |= mergeMarketItems(uid, java.util.Collections.singletonList(item));
                existing = productForRemoteListing(item.getDocId());
            }
            if (existing != null) changed |= applyLiveListing(existing, item);
        }
        // A listing removed from Firestore must disappear from the marketplace
        // on every phone, not only on the phone that deleted it.
        for (int i = host.products.size() - 1; i >= 0; i--) {
            MainActivity.Product product = host.products.get(i);
            String remote = remoteListingId(product);
            if (!remote.isEmpty() && !seen.contains(remote)) {
                host.products.remove(i);
                changed = true;
            }
        }
        if (changed) saveProducts();
        if (changed) refreshLiveSurfaces();
    }

    private MainActivity.Product productForRemoteListing(String docId) {
        if (docId == null || docId.isEmpty()) return null;
        for (MainActivity.Product product : host.products) {
            if (product.id.equals("remote-" + docId)) return product;
            if (remoteListingId(product).equals(docId)) return product;
        }
        return null;
    }

    /** Update one cached listing from its cloud copy, or just its state for the owner. */
    private boolean applyLiveListing(MainActivity.Product existing,
                                     com.campustrade.auth.model.Listing item) {
        String sellerEmail = item.getSellerID() != null && item.getSellerID().equals(CloudSync.uid(host))
                ? host.currentStudentEmail : CloudSync.emailForUid(host, item.getSellerID());
        if (sellerEmail == null || sellerEmail.isEmpty()) sellerEmail = item.getSellerID();
        boolean own = existing.sellerEmail.equalsIgnoreCase(host.currentStudentEmail);
        long serverUpdated = item.getUpdatedAt() == null ? 0 : item.getUpdatedAt().toDate().getTime();
        // Don't let an older cloud snapshot overwrite an edit that is still uploading.
        if (own && serverUpdated > 0 && serverUpdated < existing.updatedTime) {
            return syncOwnListingStatus(item);
        }
        MainActivity.Product replacement = productFromListing(item, sellerEmail, existing.id);
        replacement.savedBy.addAll(existing.savedBy);
        replacement.hidden = existing.hidden;
        if (sameListing(existing, replacement)) return false;
        int index = host.products.indexOf(existing);
        if (index >= 0) host.products.set(index, replacement);
        return index >= 0;
    }

    private boolean sameListing(MainActivity.Product a, MainActivity.Product b) {
        if (!a.title.equals(b.title) || !a.price.equals(b.price)
                || !a.description.equals(b.description) || !a.category.equals(b.category)
                || !a.condition.equals(b.condition) || !a.location.equals(b.location)
                || !a.delivery.equals(b.delivery) || !a.status.equals(b.status)
                || !a.specs.equals(b.specs) || a.negotiable != b.negotiable
                || a.viewCount != b.viewCount || a.favouriteCount != b.favouriteCount
                || !a.sellerName.equals(b.sellerName) || !a.createdAt.equals(b.createdAt)
                || !String.valueOf(a.documentUrl).equals(String.valueOf(b.documentUrl))) return false;
        if (a.photos.size() != b.photos.size()) return false;
        for (int i = 0; i < a.photos.size(); i++) {
            if (!a.photos.get(i).toString().equals(b.photos.get(i).toString())) return false;
        }
        return true;
    }

    /** Redraw whichever read-only screen is currently on top. */
    private void refreshLiveSurfaces() {
        if (host.isDestroyed() || host.isFinishing()) return;
        if ("Market".equals(host.currentTab)) {
            renderListings();
        } else if ("Offers".equals(host.currentTab) || "Home".equals(host.currentTab)
                || "SellerDashboard".equals(host.currentTab) || "AdminAnalytics".equals(host.currentTab)) {
            host.showAppScreen(host.currentTab);
        }
    }

    /** Load the first page of listings from Firebase. */
    public void cloudMergeMarket() {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty()) return;
        purgeDeletedListings();
        loadMarketPage(null, uid);
    }

    /** Remove local copies of listings deleted by a moderator. */
    private void purgeDeletedListings() {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("deletedListings").limit(200).get()
                .addOnSuccessListener(snapshot -> {
                    if (host.isDestroyed() || host.isFinishing()) return;
                    applyDeletedListings(snapshot.getDocuments());
                })
                .addOnFailureListener(e -> android.util.Log.w("CampusTrade",
                        "deleted listings sync failed: " + e.getMessage()));
    }

    private void applyDeletedListings(java.util.List<com.google.firebase.firestore.DocumentSnapshot> docs) {
        java.util.Set<String> deleted = new java.util.HashSet<>();
        java.util.Set<String> deletedKeys = new java.util.HashSet<>();
        for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
            com.google.firebase.Timestamp expires = doc.getTimestamp("expiresAt");
            if (expires != null && expires.toDate().getTime() < System.currentTimeMillis()) continue;
            deleted.add(doc.getId());
            String title = doc.getString("title");
            String seller = doc.getString("sellerEmail");
            if (title != null && !title.isEmpty() && seller != null && !seller.isEmpty()) {
                deletedKeys.add((title + "|" + seller).toLowerCase(Locale.ROOT));
            }
        }
        if (deleted.isEmpty() && deletedKeys.isEmpty()) return;
        boolean changed = false;
        for (int i = host.products.size() - 1; i >= 0; i--) {
            MainActivity.Product product = host.products.get(i);
            String remote = CloudSync.remoteId(host, product.id);
            String key = (product.title + "|" + product.sellerEmail).toLowerCase(Locale.ROOT);
            if ((!remote.isEmpty() && deleted.contains(remote)) || deletedKeys.contains(key)) {
                host.products.remove(i);
                changed = true;
            }
        }
        if (changed) {
            saveProducts();
            refreshLiveSurfaces();
        }
    }

    /** Load the next page after the oldest listing shown. */
    public void loadMoreMarket() {
        final String uid = CloudSync.uid(host);
        if (uid.isEmpty() || marketPageBusy || marketPageEnded) return;
        loadMarketPage(marketCursor, uid);
    }

    private void loadMarketPage(final com.google.firebase.Timestamp after, final String uid) {
        if (marketPageBusy) return;
        marketPageBusy = true;
        updateLoadMoreButton();
        new ListingRepository().listPage(after, 20, new ListingRepository.ListCallback() {
            @Override public void onOk(java.util.List<com.campustrade.auth.model.Listing> items) {
                marketPageBusy = false;
                if (host.isDestroyed() || host.isFinishing()) return;
                boolean changed = mergeMarketItems(uid, items);
                if (!items.isEmpty()) {
                    com.google.firebase.Timestamp oldest = items.get(items.size() - 1).getCreatedAt();
                    if (oldest != null && (marketCursor == null
                            || oldest.toDate().before(marketCursor.toDate()))) {
                        marketCursor = oldest;
                    }
                }
                marketPageEnded = items.size() < 20;
                updateLoadMoreButton();
                // Save once at the end instead of after every listing.
                if (changed && saveProducts()) renderListings();
                host.order.cloudPullOrders(null);
            }
            @Override public void onBad(String message) {
                marketPageBusy = false;
                updateLoadMoreButton();
                android.util.Log.w("CampusTrade", message);
            }
        });
    }

    private void updateLoadMoreButton() {
        if (marketLoadMore == null) return;
        marketLoadMore.setEnabled(!marketPageBusy && !marketPageEnded);
        marketLoadMore.setVisibility(marketPageEnded ? View.GONE : View.VISIBLE);
        marketLoadMore.setText(marketPageBusy ? "Loading..." : marketPageEnded ? "All caught up" : "Load more listings");
    }

    /** Add one page, skipping listings already saved. */
    private boolean mergeMarketItems(String uid, java.util.List<com.campustrade.auth.model.Listing> items) {
        boolean changed = false;
        for (com.campustrade.auth.model.Listing item : items) {
            if (uid.equals(item.getSellerID())) {
                if (syncOwnListingStatus(item)) changed = true;
                // On a new phone, add the seller's cloud listings without a local copy.
            }
            String mappedLocalId = CloudSync.localId(host, item.getDocId());
            if (!mappedLocalId.isEmpty()) {
                // A deleted local listing can leave an old mapping.
                // Skip it only if the phone listing still exists.
                boolean mappedStillExists = false;
                for (MainActivity.Product p : host.products) {
                    if (p.id.equals(mappedLocalId)) { mappedStillExists = true; break; }
                }
                if (mappedStillExists) continue;
            }
            String sellerEmail = uid.equals(item.getSellerID())
                    ? host.currentStudentEmail : CloudSync.emailForUid(host, item.getSellerID());
            if (sellerEmail == null || sellerEmail.isEmpty()) sellerEmail = item.getSellerID();
            String priceText = item.getPrice() == Math.floor(item.getPrice())
                    ? String.valueOf((long) item.getPrice())
                    : String.valueOf(item.getPrice());
            long remoteCreated = item.getCreatedAt() == null ? 0 : item.getCreatedAt().toDate().getTime();
            boolean duplicate = false;
            for (MainActivity.Product p : host.products) {
                boolean sameSeller = p.sellerEmail.equalsIgnoreCase(sellerEmail)
                        || p.sellerEmail.equals(item.getSellerID());
                if (!p.title.equals(item.getListingN()) || !sameSeller) continue;
                boolean samePrice = false;
                try {
                    samePrice = new BigDecimal(p.price).compareTo(new BigDecimal(priceText)) == 0;
                } catch (NumberFormatException ignored) { }
                // Listings can share a title, so match only when price and publish time also match.
                boolean closeTime = p.createdTime <= 0 || remoteCreated <= 0
                        || Math.abs(p.createdTime - remoteCreated) < 24L * 60 * 60 * 1000;
                if (!samePrice || !closeTime) continue;
                duplicate = true;
                // Link the buyer's local listing to the cloud copy so orders can be sent.
                if (CloudSync.remoteId(host, p.id).isEmpty()) {
                    CloudSync.putRemoteId(host, p.id, item.getDocId());
                    changed = true;
                }
                break;
            }
            if (duplicate) continue;
            MainActivity.Product product = productFromListing(item, sellerEmail,
                    "remote-" + item.getDocId());
            CloudSync.putRemoteId(host, product.id, item.getDocId());
            host.products.add(product);
            changed = true;
        }
        return changed;
    }

    /** Build a marketplace card from one cloud listing. */
    private MainActivity.Product productFromListing(com.campustrade.auth.model.Listing item,
                                                    String sellerEmail, String productId) {
        String priceText = item.getPrice() == Math.floor(item.getPrice())
                ? String.valueOf((long) item.getPrice())
                : String.valueOf(item.getPrice());
        String remoteStatus = "SOLD".equals(item.getStatus()) ? "Sold"
                : "PAUSED".equals(item.getStatus()) ? "Paused"
                : "RESERVED".equals(item.getStatus()) ? "Reserved"
                : "ORDERED".equals(item.getStatus()) ? "Ordered" : "Available";
        MainActivity.Product product = new MainActivity.Product(item.getListingN(), priceText,
                item.getDescription() != null ? item.getDescription() : "",
                sellerEmail,
                item.getCategory() != null ? item.getCategory() : "Other",
                item.getCondition() != null ? item.getCondition() : "Good",
                item.getLocation() != null && !item.getLocation().isEmpty()
                        ? item.getLocation() : "Campus pickup",
                item.getDelivery() != null && !item.getDelivery().isEmpty()
                        ? item.getDelivery() : "Campus pickup",
                item.getCreatedAt() != null
                        ? new java.text.SimpleDateFormat("dd MMM, HH:mm",
                                java.util.Locale.getDefault()).format(item.getCreatedAt().toDate())
                        : "",
                remoteStatus,
                productId);
        product.negotiable = item.getNegotiable();
        product.quantity = (int) Math.max(0, Math.min(999, item.getQuantity()));
        if (product.quantity <= 0) product.status = "Sold";
        product.specs = item.getSpecs() == null ? "" : item.getSpecs();
        product.viewCount = (int) item.getViewCount();
        product.favouriteCount = (int) item.getFavouriteCount();
        if (item.getCreatedAt() != null) product.createdTime = item.getCreatedAt().toDate().getTime();
        if (item.getUpdatedAt() != null) product.updatedTime = item.getUpdatedAt().toDate().getTime();
        if (item.getExpiresAt() != null) product.expiresTime = item.getExpiresAt().toDate().getTime();
        product.sellerName = item.getSellerName() == null || item.getSellerName().isEmpty()
                ? CloudSync.displayName(host, item.getSellerID()) : item.getSellerName();
        CloudSync.cacheProfile(host, item.getSellerID(), product.sellerName, sellerEmail);
        if (item.getPhotos() != null) {
            for (String url : item.getPhotos()) {
                if (url != null && !url.isEmpty() && product.photos.size() < Constants.MAX_LISTING_PHOTOS) {
                    product.photos.add(Uri.parse(url));
                }
            }
        }
        if (product.photos.isEmpty() && item.getImageURL() != null
                && !item.getImageURL().isEmpty()) {
            product.photos.add(Uri.parse(item.getImageURL()));
        }
        if ((item.getDocumentURL() != null && !item.getDocumentURL().isEmpty())
                || (item.getDocumentName() != null && !item.getDocumentName().isEmpty())) {
            if (item.getDocumentURL() != null && !item.getDocumentURL().isEmpty()) {
                product.documentUrl = item.getDocumentURL();
                product.documentUri = Uri.parse(item.getDocumentURL());
            }
            product.documentName = item.getDocumentName() == null ? "" : item.getDocumentName();
            product.documentMime = item.getDocumentMime() == null ? "" : item.getDocumentMime();
            product.documentSize = item.getDocumentSize();
        }
        return product;
    }

    /** Show buyer progress on the owner's copy. */
    public boolean syncOwnListingStatus(com.campustrade.auth.model.Listing item) {
        String localId = CloudSync.localId(host, item.getDocId());
        if (localId.isEmpty()) return false;
        String remote = "SOLD".equals(item.getStatus()) ? "Sold"
                : "ORDERED".equals(item.getStatus()) ? "Ordered"
                : "RESERVED".equals(item.getStatus()) ? "Reserved"
                : "PAUSED".equals(item.getStatus()) ? "Paused" : "Available";
        for (MainActivity.Product product : host.products) {
            if (!product.id.equals(localId)) continue;
            boolean changed = false;
            int remoteQuantity = (int) Math.max(0, Math.min(999, item.getQuantity()));
            if (product.quantity != remoteQuantity) {
                product.quantity = remoteQuantity;
                changed = true;
            }
            if (product.quantity <= 0) remote = "Sold";
            if (item.getExpiresAt() != null) {
                long expiry = item.getExpiresAt().toDate().getTime();
                if (product.expiresTime != expiry) {
                    product.expiresTime = expiry;
                    changed = true;
                }
            }
            if (!remote.equals(product.status)) {
                boolean upgraded = remote.equals("Sold") || remote.equals("Ordered") || remote.equals("Reserved");
                boolean released = remote.equals("Available")
                        && (product.status.equals("Ordered") || product.status.equals("Reserved"));
                if (upgraded || released) {
                    product.status = remote;
                    changed = true;
                }
            }
            return changed;
        }
        return false;
    }

    // Redraw the cards when search, filter, sort or saved state changes.
    public void renderListings() {
        if (host.listingsContainer == null) return;
        host.listingsContainer.removeAllViews();
        if (host.products.isEmpty()) {
            LinearLayout empty = host.ui.card(host.page, 0, 0);
            empty.setPadding(0, host.ui.dp(28), 0, host.ui.dp(28));
            TextView title = host.ui.titleText("No listings yet.", host.text, 24);
            title.setPadding(0, 0, 0, host.ui.dp(8));
            empty.addView(title);
            TextView copy = host.ui.bodyText("Student listings will appear here.", host.muted, 14);
            empty.addView(copy);
            host.listingsContainer.addView(empty);
            return;
        }
        int shown = 0;
        boolean grid = host.marketGrid;
        LinearLayout gridRow = null;
        ArrayList<MainActivity.Product> ordered = new ArrayList<>(host.products);
        java.util.Collections.sort(ordered, (a, b) -> Long.compare(b.createdTime, a.createdTime));
        if (!host.marketSort.equals("Newest")) java.util.Collections.sort(ordered, (a, b) -> host.marketSort.equals("Price: low to high")
                ? new BigDecimal(a.price).compareTo(new BigDecimal(b.price))
                : new BigDecimal(b.price).compareTo(new BigDecimal(a.price)));
        // Apply search and filters before building each card.
        for (MainActivity.Product product : ordered) {
            if (host.safety.blockedEither(host.currentStudentEmail, product.sellerEmail)
                    && !ownsListing(product)) continue;
            if ((!publicListing(product) && !(host.marketFilter.equals("Mine") && ownsListing(product)))) continue;
            String searchable = product.title + " " + product.category + " " + product.description
                    + " " + product.documentName + " " + product.delivery + " " + product.specs;
            if (!searchable.toLowerCase(Locale.ROOT).contains(host.marketQuery.trim().toLowerCase(Locale.ROOT))) continue;
            if (host.marketFilter.equals("Mine") && !ownsListing(product)) {
                continue;
            }
            if (host.marketFilter.equals("Available") && !product.status.equals("Available")) {
                continue;
            }
            if (host.marketFilter.equals("Saved") && !product.savedBy.contains(host.currentStudentEmail)) continue;
            if (host.marketCategory != null && !host.marketCategory.equals("All")
                    && !host.marketCategory.equals(product.category)) continue;
            BigDecimal priceValue = new BigDecimal(product.price);
            try {
                if (!host.marketMinPrice.isEmpty()
                        && priceValue.compareTo(new BigDecimal(host.marketMinPrice)) < 0) continue;
                if (!host.marketMaxPrice.isEmpty()
                        && priceValue.compareTo(new BigDecimal(host.marketMaxPrice)) > 0) continue;
            } catch (NumberFormatException ignored) { }
            shown++;
            String state = marketState(product);
            LinearLayout card = host.ui.card(host.page, 0, grid ? host.ui.dp(8) : 0);
            if (grid) card.setBackground(host.ui.strokedRound(host.surface(), 10, host.line));
            card.setPadding(0, host.ui.dp(grid ? 8 : 10), 0, host.ui.dp(grid ? 8 : 10));
            card.setMinimumHeight(host.ui.dp(grid ? 232 : 104));
            LinearLayout row = host.ui.horizontal();
            if (grid) row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(grid ? Gravity.TOP : Gravity.CENTER_VERTICAL);
            View image = listingThumbnail(product, false);
            row.addView(image, new LinearLayout.LayoutParams(
                    grid ? ViewGroup.LayoutParams.MATCH_PARENT : host.ui.dp(92),
                    grid ? host.ui.dp(112) : host.ui.dp(92)));
            LinearLayout details = new LinearLayout(host);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(grid ? 0 : host.ui.dp(12), grid ? host.ui.dp(6) : 0, host.ui.dp(4), 0);
            details.setMinimumHeight(host.ui.dp(grid ? 92 : 0));
            LinearLayout titleRow = host.ui.horizontal();
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = host.ui.titleText(product.title, host.text, grid ? 13 : 15);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            View heart = saveHeart(product, () -> renderListings());
            titleRow.addView(heart, new LinearLayout.LayoutParams(host.ui.dp(36), host.ui.dp(36)));
            details.addView(titleRow);
            LinearLayout priceRow = host.ui.horizontal();
            priceRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView price = host.ui.bodyText(priceValue.signum() == 0 ? "Free" : "R" + product.price, host.text, grid ? 14 : 15);
            price.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            price.setSingleLine(true);
            priceRow.addView(price);
            priceRow.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1));
            priceRow.addView(host.ui.statusPill(state));
            details.addView(priceRow);
            String sellerName = sellerLabel(product);
            String handover = fulfilmentLabel(product);
            TextView seller = host.ui.bodyText(sellerName
                    + (grid ? "" : " / " + (handover.equals("Delivery only") ? handover : product.location + " / " + handover)),
                    host.muted, 11);
            seller.setPadding(0, host.ui.dp(4), 0, 0);
            seller.setMaxLines(1);
            seller.setEllipsize(TextUtils.TruncateAt.END);
            details.addView(seller);
            TextView stock = host.ui.bodyText(product.quantity <= 0 ? "Sold out"
                            : product.quantity == 1 ? "1 in stock" : product.quantity + " in stock",
                    product.quantity <= 0 ? host.error : host.muted, 10);
            details.addView(stock);
            CampusFeatures.Offer cardReservation = host.featureStore.data.reservation(product.id, System.currentTimeMillis());
            if (cardReservation != null && (cardReservation.buyer.equals(host.currentStudentEmail)
                    || cardReservation.seller.equals(host.currentStudentEmail))) {
                details.addView(host.ui.bodyText("Agreed price: R" + cardReservation.amount, host.blue, 10));
            }
            if (!grid && product.hasDocument()) {
                boolean documentVisible = ownsListing(product) || buyerCanOpenDocument(product);
                TextView document = host.ui.bodyText(documentVisible && !product.documentName.isEmpty()
                        ? "Document: " + product.documentName : "Document included", host.muted, 11);
                document.setMaxLines(1);
                document.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                details.addView(document);
            }
            row.addView(details, grid
                    ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(row);
            card.setOnClickListener(v -> showProductDetails(product));
            if (grid) {
                if (shown % 2 == 1) {
                    gridRow = host.ui.horizontal();
                    host.listingsContainer.addView(gridRow);
                }
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                cell.topMargin = host.ui.dp(12);
                if (shown % 2 == 0) cell.leftMargin = host.ui.dp(8);
                gridRow.addView(card, cell);
            } else host.listingsContainer.addView(card);
        }
        if (grid && gridRow != null && shown % 2 == 1) gridRow.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1));
        if (shown == 0) host.listingsContainer.addView(host.ui.emptyCard("Nothing matches this filter yet - try another one."));
    }

    void shareListing(MainActivity.Product product) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        String price = new BigDecimal(product.price).signum() == 0 ? "Free" : "R" + product.price;
        share.putExtra(Intent.EXTRA_TEXT, "Check out \"" + product.title + "\" on CampusTrade - "
                + price + ", " + product.category + ", " + product.location
                + ". Listed by a verified Eduvos student.");
        try {
            host.startActivity(Intent.createChooser(share, "Share listing"));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(host, "No app can share this listing.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Full-page listing detail with a large image and sticky actions. */
    /**
     * Saved is private to the student who saved the listing. Once anyone has
     * saved it, the rest of the market sees Reserved.
     */
    /** Wording for the listing's handover method. */
    String fulfilmentLabel(MainActivity.Product product) {
        return product.delivery != null && product.delivery.toLowerCase(Locale.ROOT).contains("delivery")
                ? "Delivery only" : "Campus pickup";
    }

    String marketState(MainActivity.Product product) {        if (product.status == null) product.status = "Available";
        if (product.status.equals("Sold") || product.quantity <= 0) return "Sold out";
        if (expired(product)) return "Expired";
        if (!product.status.equals("Available")) return product.status;
        CampusFeatures.Offer reservation = host.featureStore.data.reservation(product.id, System.currentTimeMillis());
        if (reservation != null) return "Reserved";
        if (ownsListing(product)) return "Available";
        if (product.savedBy.contains(host.currentStudentEmail)) return "Saved";
        if (!product.savedBy.isEmpty() || product.favouriteCount > 0) return "Reserved";
        return "Available";
    }

    void showProductDetails(MainActivity.Product product) {
        if (!ownsListing(product)) incrementView(product);

        Dialog page = new Dialog(host);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);

        LinearLayout header = host.ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(host.ui.dp(20), host.ui.dp(12), host.ui.dp(20), host.ui.dp(8));
        TextView close = host.ui.linkText("Back");
        close.setTextSize(13);
        close.setOnClickListener(v -> page.dismiss());
        header.addView(close);
        TextView headerTitle = host.ui.titleText("Listing", host.text, 16);
        headerTitle.setGravity(Gravity.CENTER);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View share = host.new CampusAssetView(host, "share", host.text);
        share.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
        share.setContentDescription("Share listing");
        share.setBackground(host.ui.strokedRound(host.page, 999, host.border));
        share.setOnClickListener(v -> shareListing(product));
        header.addView(share, new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40)));
        if (!ownsListing(product)) {
            View report = host.new CampusAssetView(host, "flag", host.muted);
            report.setPadding(host.ui.dp(10), host.ui.dp(10), host.ui.dp(10), host.ui.dp(10));
            report.setContentDescription("Report listing");
            report.setBackground(host.ui.strokedRound(host.page, 999, host.border));
            report.setOnClickListener(v -> { page.dismiss(); host.admin.showReportListing(product); });
            LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(host.ui.dp(40), host.ui.dp(40));
            reportParams.leftMargin = host.ui.dp(6);
            header.addView(report, reportParams);
        }
        root.addView(header);
        View headerRule = new View(host);
        headerRule.setBackgroundColor(host.line);
        root.addView(headerRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(1)));

        ScrollView scroll = new ScrollView(host);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.ui.dp(20), host.ui.dp(14), host.ui.dp(20), host.ui.dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        View image = listingThumbnail(product, true);
        image.setOnClickListener(v -> { if (!product.photos.isEmpty()) showListingPhotos(product); });
        content.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(250)));
        if (!product.photos.isEmpty()) {
            TextView photos = host.ui.linkText("View " + product.photos.size() + " photo(s)");
            photos.setPadding(0, host.ui.dp(8), 0, 0);
            photos.setOnClickListener(v -> showListingPhotos(product));
            content.addView(photos);
        }

        LinearLayout titleRow = host.ui.horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, host.ui.dp(14), 0, 0);
        TextView title = host.ui.titleText(product.title, host.text, 22);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View save = saveHeart(product, () -> renderListings());
        titleRow.addView(save, new LinearLayout.LayoutParams(host.ui.dp(44), host.ui.dp(44)));
        content.addView(titleRow);

        TextView price = host.ui.titleText(new BigDecimal(product.price).signum() == 0 ? "Free" : "R " + product.price,
                host.blue, 26);
        price.setPadding(0, host.ui.dp(8), 0, 0);
        content.addView(price);

        LinearLayout chipArea = new LinearLayout(host);
        chipArea.setOrientation(LinearLayout.VERTICAL);
        chipArea.setPadding(0, host.ui.dp(8), 0, host.ui.dp(10));
        String state = marketState(product);
        CampusFeatures.Offer reservation = host.featureStore.data.reservation(product.id, System.currentTimeMillis());
        int detailChipBg = host.nightActive ? Color.rgb(48, 48, 52) : Color.rgb(242, 240, 235);
        LinearLayout chips = host.ui.horizontal();
        addDetailChip(chips, host.ui.statusPill(state));
        TextView conditionChip = host.ui.chip(product.condition, detailChipBg, host.text);
        conditionChip.setBackground(host.ui.strokedRound(detailChipBg, 999, host.line));
        addDetailChip(chips, conditionChip);
        TextView stockChip = host.ui.chip(product.quantity <= 0 ? "Sold out"
                        : product.quantity + " available", product.quantity <= 0
                        ? (host.nightActive ? Color.rgb(60, 36, 36) : Color.rgb(255, 236, 236)) : detailChipBg,
                product.quantity <= 0 ? (host.nightActive ? Color.rgb(255, 150, 150) : Color.rgb(180, 30, 30)) : host.text);
        stockChip.setBackground(host.ui.strokedRound(product.quantity <= 0
                ? (host.nightActive ? Color.rgb(60, 36, 36) : Color.rgb(255, 236, 236)) : detailChipBg, 999, host.line));
        addDetailChip(chips, stockChip);
        chipArea.addView(chips);

        LinearLayout moreChips = host.ui.horizontal();
        if (reservation != null && (reservation.buyer.equals(host.currentStudentEmail)
                || reservation.seller.equals(host.currentStudentEmail))) {
            TextView agreedChip = host.ui.chip("Agreed R" + reservation.amount, detailChipBg, host.blue);
            agreedChip.setBackground(host.ui.strokedRound(detailChipBg, 999, host.line));
            addDetailChip(moreChips, agreedChip);
        }
        if (product.negotiable) {
            TextView negotiableChip = host.ui.chip("Negotiable", detailChipBg, host.text);
            negotiableChip.setBackground(host.ui.strokedRound(detailChipBg, 999, host.line));
            addDetailChip(moreChips, negotiableChip);
        }
        if (fulfilmentLabel(product).equals("Delivery only")) {
            TextView deliveryChip = host.ui.chip("Delivery only", detailChipBg, host.text);
            deliveryChip.setBackground(host.ui.strokedRound(detailChipBg, 999, host.line));
            addDetailChip(moreChips, deliveryChip);
        }
        if (moreChips.getChildCount() > 0) chipArea.addView(moreChips);
        content.addView(chipArea);

        TextView descriptionTitle = host.ui.titleText("Description", host.text, 14);
        descriptionTitle.setPadding(0, host.ui.dp(14), 0, host.ui.dp(4));
        content.addView(descriptionTitle);
        String descriptionText = product.description == null || product.description.trim().isEmpty()
                ? "No description provided." : product.description;
        TextView description = host.ui.bodyText(descriptionText, host.text, 14);
        description.setLineSpacing(host.ui.dp(2), 1f);
        content.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (product.specs != null && !product.specs.trim().isEmpty()) {
            TextView specsTitle = host.ui.titleText("Tech specs", host.text, 14);
            specsTitle.setPadding(0, host.ui.dp(14), 0, host.ui.dp(4));
            content.addView(specsTitle);
            content.addView(host.ui.bodyText(product.specs, host.muted, 12));
        }
        String handover = fulfilmentLabel(product);
        content.addView(host.ui.bodyText(product.category + " / "
                + (handover.equals("Delivery only") ? handover : product.location + " / " + handover)
                + "\n" + (product.viewCount + " views / " + product.favouriteCount + " saves"), host.muted, 12));

        if (product.hasDocument()) {
            MainActivity.TransactionRecord documentOrder = buyerDocumentOrder(product);
            boolean canOpen = ownsListing(product) || documentOrder != null;
            LinearLayout document = host.ui.horizontal();
            document.setGravity(Gravity.CENTER_VERTICAL);
            document.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
            document.setBackground(host.ui.round(host.surface(), 10));
            LinearLayout.LayoutParams documentParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            documentParams.topMargin = host.ui.dp(12);
            View icon = host.new CampusAssetView(host, "note", host.blue);
            icon.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
            document.addView(icon, new LinearLayout.LayoutParams(host.ui.dp(34), host.ui.dp(34)));
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(host.ui.dp(10), 0, 0, 0);
            String documentTitle = canOpen
                    ? (product.documentName.isEmpty() ? "Attached document" : product.documentName)
                    : "Document included";
            TextView documentName = host.ui.titleText(documentTitle, host.text, 12);
            documentName.setMaxLines(2);
            copy.addView(documentName);
            copy.addView(host.ui.bodyText(canOpen
                    ? documentTypeLabel(product.documentMime, product.documentName)
                        + " / " + formatDocumentSize(product.documentSize)
                    : "Available after the seller confirms your payment", host.muted, 10));
            document.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (canOpen) {
                TextView open = host.ui.linkText("Open");
                open.setTextSize(12);
                open.setOnClickListener(v -> openProductDocument(product));
                document.addView(open);
                document.setOnClickListener(v -> openProductDocument(product));
            }
            content.addView(document, documentParams);
        }

        LinearLayout sellerCard = new LinearLayout(host);
        sellerCard.setOrientation(LinearLayout.VERTICAL);
        sellerCard.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
        sellerCard.setBackground(host.ui.strokedRound(host.surface(), 12, host.line));
        LinearLayout.LayoutParams sellerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sellerParams.topMargin = host.ui.dp(14);
        sellerCard.setLayoutParams(sellerParams);
        LinearLayout sellerTop = host.ui.horizontal();
        sellerTop.setGravity(Gravity.CENTER_VERTICAL);
        View avatar = StudentAvatar.create(host, product.sellerEmail, sellerLabel(product), 14,
                host.blue, host.nightActive ? Color.rgb(40, 52, 70) : Color.rgb(234, 242, 255));
        sellerTop.addView(avatar, new LinearLayout.LayoutParams(host.ui.dp(42), host.ui.dp(42)));
        LinearLayout sellerCopy = new LinearLayout(host);
        sellerCopy.setOrientation(LinearLayout.VERTICAL);
        sellerCopy.setPadding(host.ui.dp(10), 0, 0, 0);
        String sellerName = sellerLabel(product);
        TextView sellerNameLabel = host.ui.titleText(sellerName, host.text, 14);
        sellerCopy.addView(sellerNameLabel);
        boolean verifiedSeller = product.sellerEmail != null
                && product.sellerEmail.toLowerCase(Locale.ROOT).endsWith("@vossie.net");
        int completedSales = 0;
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (order.isSeller(product.sellerEmail) && order.status.equals(MainActivity.TransactionRecord.COMPLETED)) completedSales++;
        }
        double sellerRating = host.featureStore.data.rating(product.sellerEmail);
        String rep = (verifiedSeller ? "Verified campus student" : "Campus seller")
                + " / " + (sellerRating <= 0 ? "No ratings yet" : RatingStars.of(sellerRating))
                + " / " + (completedSales <= 0 ? "New seller" : completedSales + " completed sale(s)");
        sellerCopy.addView(host.ui.bodyText(rep, host.muted, 10));
        sellerTop.addView(sellerCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!ownsListing(product)) {
            View sellerChevron = host.new CampusAssetView(host, "chevron", host.muted);
            sellerTop.addView(sellerChevron, new LinearLayout.LayoutParams(host.ui.dp(20), host.ui.dp(20)));
        }
        sellerCard.addView(sellerTop);
        content.addView(sellerCard);
        if (!ownsListing(product)) {
            sellerCard.setOnClickListener(v -> { page.dismiss(); host.profile.showSellerProfile(product.sellerEmail); });
        }
        if (("Student seller".equals(sellerName) || "Seller".equals(sellerName) || sellerName.trim().isEmpty())
                && product.sellerEmail != null && product.sellerEmail.contains("@")) {
            CloudSync.peerUid(host, product.sellerEmail, new CloudSync.StrCallback() {
                @Override public void onOk(String sellerUid) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("publicProfiles").document(sellerUid).get()
                            .addOnSuccessListener(profile -> {
                                String name = profile.getString("stuName");
                                if (name == null || name.trim().isEmpty()) return;
                                if (page.isShowing()) {
                                    sellerNameLabel.setText(name);
                                }
                                product.sellerName = name;
                                CloudSync.cacheProfile(host, sellerUid, name, product.sellerEmail);
                                saveProducts();
                                renderListings();
                            });
                }
                @Override public void onBad(String message) { }
            });
        }
        TextView reviews = host.ui.linkText(sellerRating <= 0 ? "See seller reviews"
                : RatingStars.label("Seller rating:", sellerRating));
        reviews.setPadding(host.ui.dp(4), host.ui.dp(10), 0, 0);
        reviews.setOnClickListener(v -> { page.dismiss(); host.profile.showStudentReviews(product.sellerEmail); });
        content.addView(reviews);

        boolean canBuy = product.status.equals("Available") && product.quantity > 0 && !expired(product)
                && (reservation == null || reservation.buyer.equals(host.currentStudentEmail));
        if (!ownsListing(product) && canBuy && reservation == null
                && product.negotiable && new BigDecimal(product.price).signum() > 0) {
            TextView offer = host.ui.secondaryButton("Make an offer");
            LinearLayout.LayoutParams offerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48));
            offerParams.topMargin = host.ui.dp(12);
            offer.setOnClickListener(v -> { page.dismiss(); makeOffer(product); });
            content.addView(offer, offerParams);
        }
        if (!ownsListing(product) && reservation != null && reservation.buyer.equals(host.currentStudentEmail)) {
            TextView cancelReservation = host.ui.textButton("Cancel reservation");
            cancelReservation.setOnClickListener(v -> {
                CampusFeatures.Offer accepted = host.featureStore.data.reservation(product.id, System.currentTimeMillis());
                if (accepted != null) { page.dismiss(); cancelOffer(accepted.id); }
            });
            content.addView(cancelReservation, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(44)));
        }

        LinearLayout bottom = host.ui.horizontal();
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(host.ui.dp(16), host.ui.dp(10), host.ui.dp(16), host.ui.dp(12));
        bottom.setBackground(host.ui.edgeRule(host.line, true));
        if (ownsListing(product)) {
            TextView manage = host.ui.primaryButton("Manage listing");
            manage.setOnClickListener(v -> { page.dismiss(); manageListing(product); });
            bottom.addView(manage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(48)));
        } else {
            TextView chat = host.ui.secondaryButton("Chat");
            chat.setOnClickListener(v -> {
                page.dismiss();
                com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_messages").edit()
                        .putBoolean("market:" + host.inbox.marketplaceChatKey(product.sellerEmail), true).apply();
                host.inbox.showConversation(product.sellerEmail);
            });
            bottom.addView(chat, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
            bottom.addView(host.ui.space(host.ui.dp(8), 1));
            boolean reservedByOther = reservation != null && !reservation.buyer.equals(host.currentStudentEmail);
            String actionLabel = canBuy ? (new BigDecimal(product.price).signum() == 0 ? "Collect item" : "Buy now")
                    : product.quantity <= 0 ? "Sold out" : expired(product) ? "Expired" : reservedByOther ? "Reserved"
                    : state.equals("Sold out") || state.equals("Sold") || state.equals("Completed") ? "Sold" : "Unavailable";
            Button buy = host.ui.primaryButton(actionLabel);
            if (!canBuy) {
                buy.setBackground(host.ui.round(host.nightActive ? Color.rgb(52, 52, 57) : Color.rgb(235, 233, 228), 4));
                buy.setTextColor(host.muted);
            }
            buy.setEnabled(canBuy);
            buy.setOnClickListener(v -> {
                page.dismiss();
                if (canBuy) host.order.showPaymentDialog(product);
                else if (reservedByOther) Toast.makeText(host, "Another student has this reserved.", Toast.LENGTH_SHORT).show();
                else host.order.showTransactionHistory();
            });
            bottom.addView(buy, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
        }
        root.addView(bottom);

        page.setContentView(root);
        page.setCanceledOnTouchOutside(false);
        page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
        }
    }

    private void addDetailChip(LinearLayout row, TextView chip) {
        if (row.getChildCount() > 0) row.addView(host.ui.space(host.ui.dp(6), 1));
        row.addView(chip);
    }

    void showProductDetailsLegacy(MainActivity.Product product) {
            if (!ownsListing(product)) incrementView(product);
            LinearLayout card = new LinearLayout(host);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(host.ui.dp(18), host.ui.dp(12), host.ui.dp(18), host.ui.dp(18));
            if (!product.photos.isEmpty()) {
                ImageView cover = listingImage(product.photos.get(0), product.title, 800);
                cover.setOnClickListener(v -> showListingPhotos(product));
                card.addView(cover, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(200)));
                TextView photos = host.ui.linkText("View " + product.photos.size() + " photo(s)");
                photos.setPadding(0, host.ui.dp(8), 0, host.ui.dp(8));
                photos.setOnClickListener(v -> showListingPhotos(product));
                card.addView(photos);
            }
            if (product.hasDocument()) {
                MainActivity.TransactionRecord documentOrder = buyerDocumentOrder(product);
                boolean canOpen = ownsListing(product) || documentOrder != null;
                LinearLayout document = host.ui.horizontal();
                document.setGravity(Gravity.CENTER_VERTICAL);
                document.setPadding(host.ui.dp(12), host.ui.dp(12), host.ui.dp(12), host.ui.dp(12));
                document.setBackground(host.ui.round(host.surface(), 8));
                LinearLayout.LayoutParams documentParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                documentParams.topMargin = host.ui.dp(10);
                documentParams.bottomMargin = host.ui.dp(10);
                View icon = host.new CampusAssetView(host, "note", host.blue);
                icon.setPadding(host.ui.dp(8), host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
                icon.setBackground(host.ui.round(host.nightActive ? Color.rgb(48, 48, 54) : Color.rgb(234, 242, 255), 8));
                document.addView(icon, new LinearLayout.LayoutParams(host.ui.dp(38), host.ui.dp(38)));
                LinearLayout copy = new LinearLayout(host);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(host.ui.dp(10), 0, host.ui.dp(8), 0);
                TextView documentName = host.ui.titleText(canOpen
                        ? (product.documentName.isEmpty() ? "Attached document" : product.documentName)
                        : "Document included", host.text, 13);
                documentName.setMaxLines(2);
                documentName.setEllipsize(TextUtils.TruncateAt.END);
                copy.addView(documentName);
                copy.addView(host.ui.bodyText(canOpen
                        ? documentTypeLabel(product.documentMime, product.documentName)
                            + " / " + formatDocumentSize(product.documentSize)
                        : "Available after the seller confirms your payment", host.muted, 10));
                document.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                if (canOpen) {
                    TextView open = host.ui.actionChip("Open");
                    open.setOnClickListener(v -> openProductDocument(product));
                    document.addView(open, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(48)));
                    document.setOnClickListener(v -> openProductDocument(product));
                }
                card.addView(document, documentParams);
            }
            card.addView(host.ui.titleText("R " + product.price, host.blue, 22));
            card.addView(host.ui.bodyText(product.description, host.text, 14));
            card.addView(host.ui.bodyText(product.category + " - " + product.condition, host.muted, 12));
            card.addView(host.ui.bodyText(product.negotiable ? "Negotiable" : "Fixed price", host.muted, 12));
            card.addView(host.ui.bodyText(product.location + " - " + product.delivery, host.muted, 12));
            if (product.viewCount > 0 || product.favouriteCount > 0) {
                card.addView(host.ui.bodyText(product.viewCount + " views / " + product.favouriteCount + " saves", host.muted, 11));
            }
            TextView sellerLine = host.ui.bodyText("Seller: " + sellerLabel(product.sellerEmail), host.muted, 12);
            if (!ownsListing(product)) sellerLine.setOnClickListener(v -> host.profile.showSellerProfile(product.sellerEmail));
            card.addView(sellerLine);
            TextView reputation = host.ui.linkText(host.featureStore.data.rating(product.sellerEmail) == 0 ? "Seller reviews"
                    : RatingStars.label("Seller rating:", host.featureStore.data.rating(product.sellerEmail)));
            reputation.setOnClickListener(v -> host.profile.showStudentReviews(product.sellerEmail)); card.addView(reputation);
            ScrollView scroll = new ScrollView(host);
            scroll.addView(card);
            AlertDialog details = host.ui.dialog().setTitle(product.title).setView(scroll)
                    .setPositiveButton("Done", null)
                    .setNeutralButton(ownsListing(product) ? "Manage" : "Report", (d, which) -> {
                        if (ownsListing(product)) manageListing(product); else host.admin.showReportListing(product);
                    }).create();
            if (!ownsListing(product) && product.status.equals("Available") && !expired(product)
                    && new BigDecimal(product.price).signum() > 0) {
                TextView offer = host.ui.actionChip("Make an offer"); offer.setPadding(0, host.ui.dp(12), 0, host.ui.dp(12));
                offer.setOnClickListener(v -> { details.dismiss(); makeOffer(product); }); card.addView(offer);
            }
            LinearLayout actions = host.ui.horizontal();
            actions.setPadding(0, host.ui.dp(12), 0, 0);
            TextView message = host.ui.actionChip("Chat");
            android.graphics.drawable.Drawable chatIcon = host.getDrawable(android.R.drawable.ic_dialog_email);
            chatIcon.setBounds(0, 0, host.ui.dp(16), host.ui.dp(16));
            message.setCompoundDrawables(chatIcon, null, null, null);
            message.setCompoundDrawablePadding(host.ui.dp(4));
            message.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(host.blue));
            message.setSingleLine(true);
            message.setOnClickListener(v -> {
                if (ownsListing(product)) {
                    Toast.makeText(host, "This one is yours.", Toast.LENGTH_SHORT).show();
                } else {
                    details.dismiss();
                    com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_messages").edit()
                            .putBoolean("market:" + host.inbox.marketplaceChatKey(product.sellerEmail), true).apply();
                    host.inbox.showConversation(product.sellerEmail);
                }
            });
            boolean canBuy = product.status.equals("Available") && product.quantity > 0 && !expired(product);
            TextView pay = host.ui.actionChip(!canBuy ? (product.quantity <= 0 ? "Sold out" : expired(product) ? "Expired" : "Order")
                    : new BigDecimal(product.price).signum() == 0 ? "Collect item" : "Buy now");
            pay.setOnClickListener(v -> {
                if (!canBuy || !ownsListing(product)) details.dismiss();
                if (canBuy) {
                    host.order.showPaymentDialog(product);
                } else if (expired(product) && ownsListing(product)) {
                    manageListing(product);
                } else {
                    host.order.showTransactionHistory();
                }
            });
            actions.addView(message, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
            actions.addView(host.ui.space(host.ui.dp(8), 1));
            actions.addView(pay, new LinearLayout.LayoutParams(0, host.ui.dp(48), 1));
            card.addView(actions);
            details.show();
            host.ui.addPageClose(details);
    }

    public MainActivity.TransactionRecord buyerDocumentOrder(MainActivity.Product product) {
        if (product == null || product.id == null) return null;
        for (MainActivity.TransactionRecord order : host.transactions) {
            if (order == null || order.status == null || order.productId == null) continue;
            if (!order.productId.equals(product.id)) continue;
            if (!order.isBuyer(host.currentStudentEmail)) continue;
            if (!order.status.equals(MainActivity.TransactionRecord.HELD)
                    && !order.status.equals(MainActivity.TransactionRecord.COMPLETED)) continue;
            if (!order.hasDocument() || !order.paymentConfirmed()) continue;
            return order;
        }
        return null;
    }

    public boolean buyerCanOpenDocument(MainActivity.Product product) {
        return buyerDocumentOrder(product) != null;
    }

    public void openProductDocument(MainActivity.Product product) {
        if (ownsListing(product)) {
            if (product.documentUri != null) {
                openDocumentUri(product.documentUri, product.documentMime);
                return;
            }
            if (product.documentUrl != null && !product.documentUrl.isEmpty()) {
                openDocumentUri(Uri.parse(product.documentUrl), product.documentMime);
                return;
            }
            fetchPrivateListingDocument(product);
            return;
        }
        MainActivity.TransactionRecord order = buyerDocumentOrder(product);
        if (order != null) {
            openDocumentUri(Uri.parse(order.documentUrl), order.documentMime);
            return;
        }
        Toast.makeText(host, "The document is available after the seller confirms your payment.",
                Toast.LENGTH_LONG).show();
    }

    private void fetchPrivateListingDocument(MainActivity.Product product) {
        if (product == null || product.id == null || product.id.isEmpty()) return;
        String listingId = CloudSync.remoteId(host, product.id);
        if (listingId.isEmpty()) listingId = product.id;
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("listingDocuments")
                .document(listingId).get()
                .addOnSuccessListener(doc -> {
                    String url = doc.exists() ? doc.getString("documentURL") : "";
                    if (url == null || url.isEmpty()) {
                        Toast.makeText(host, "This listing has no attached document.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    product.documentUrl = url;
                    String name = doc.getString("documentName");
                    String mime = doc.getString("documentMime");
                    if (name != null) product.documentName = name;
                    if (mime != null) product.documentMime = mime;
                    openDocumentUri(Uri.parse(url), product.documentMime);
                })
                .addOnFailureListener(e -> Toast.makeText(host,
                        "Could not open the private document.", Toast.LENGTH_SHORT).show());
    }

    private void openDocumentUri(Uri uri, String mimeType) {
        if (uri == null) {
            Toast.makeText(host, "This listing has no attached document.", Toast.LENGTH_SHORT).show();
            return;
        }
        String mime = mimeType == null || mimeType.trim().isEmpty() ? "*/*" : mimeType;
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, mime);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.startActivity(view);
            return;
        } catch (android.content.ActivityNotFoundException ignored) {
            // Open the share sheet instead of failing silently.
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        if ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) {
            share.setType(mime);
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, uri.toString());
        }
        try {
            host.startActivity(Intent.createChooser(share, "Open document"));
        } catch (android.content.ActivityNotFoundException e) {
            host.ui.showSimpleMessage("Document", "No app on this device can open the file. Link: " + uri);
        }
    }

    // Listings are saved in phone storage.
    public void loadProducts() {
        try {
            JSONArray records = new JSONArray(com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_listings").getString("records", "[]"));
            boolean skipped = false;
            for (int i = 0; i < records.length(); i++) {
                try {
                    JSONObject record = records.getJSONObject(i);
                    MainActivity.Product product = new MainActivity.Product(record.getString("title"), record.getString("price"), record.getString("description"),
                            record.getString("seller"), record.getString("category"), record.getString("condition"),
                            record.getString("location"), record.getString("delivery"), record.getString("created"),
                            record.optString("status", "Available"), record.getString("id"));
                    product.sellerName = record.optString("sellerName", "");
                    product.specs = record.optString("specs", "");
                    product.quantity = Math.max(0, record.optInt("quantity", 1));
                    if (product.quantity <= 0) product.status = "Sold";
                    product.hidden = record.optBoolean("hidden", false);
                    product.createdTime = record.optLong("createdTime", 0);
                    product.expiresTime = record.optLong("expiresTime", 0);
                    product.negotiable = record.optBoolean("negotiable", true);
                    product.viewCount = record.optInt("viewCount", 0);
                    product.favouriteCount = record.optInt("favouriteCount", 0);
                    product.updatedTime = record.optLong("updatedTime", product.createdTime);
                    JSONArray photos = record.optJSONArray("photos");
                    if (photos != null) for (int p = 0; p < Math.min(photos.length(), Constants.MAX_LISTING_PHOTOS); p++) product.photos.add(Uri.parse(photos.getString(p)));
                    String documentUri = record.optString("documentUri", "");
                    if (!documentUri.isEmpty()) product.documentUri = Uri.parse(documentUri);
                    product.documentUrl = record.optString("documentUrl", "");
                    product.documentName = record.optString("documentName", "");
                    product.documentMime = record.optString("documentMime", "");
                    product.documentSize = record.optLong("documentSize", 0);
                    JSONArray saved = record.optJSONArray("savedBy");
                    if (saved != null) for (int s = 0; s < saved.length(); s++) product.savedBy.add(host.normalizeEmail(saved.getString(s)));
                    if (product.favouriteCount == 0 && !product.savedBy.isEmpty()) product.favouriteCount = product.savedBy.size();
                    if (host.order.hasOrdersFor(product.id)) host.order.applyListingState(product);
                    host.products.add(product);
                // Skip a broken record and load the rest.
                } catch (org.json.JSONException | IllegalArgumentException | ArithmeticException e) {
                    skipped = true;
                }
            }
            if (skipped) {
                Toast.makeText(host, "Some saved listings didn't load.", Toast.LENGTH_LONG).show();
            }
        } catch (org.json.JSONException e) {
            Toast.makeText(host, "Saved listings didn't load.", Toast.LENGTH_LONG).show();
        }
    }

    // Save immediately so we know whether it worked.
    boolean saveProducts() {
        JSONArray records = new JSONArray();
        try {
            for (MainActivity.Product product : host.products) {
                JSONArray photos = new JSONArray();
                for (Uri uri : product.photos) photos.put(uri.toString());
                records.put(new JSONObject().put("id", product.id).put("title", product.title).put("price", product.price)
                        .put("description", product.description).put("specs", product.specs).put("seller", product.sellerEmail)
                        .put("sellerName", product.sellerName == null ? "" : product.sellerName).put("category", product.category)
                        .put("condition", product.condition).put("location", product.location).put("delivery", product.delivery)
                        .put("created", product.createdAt).put("status", product.status).put("photos", photos)
                        .put("documentUri", product.documentUri == null ? "" : product.documentUri.toString())
                        .put("documentUrl", product.documentUrl).put("documentName", product.documentName)
                        .put("documentMime", product.documentMime).put("documentSize", product.documentSize)
                        .put("hidden", product.hidden).put("createdTime", product.createdTime)
                        .put("expiresTime", product.expiresTime)
                        .put("negotiable", product.negotiable)
                        .put("quantity", product.quantity)
                        .put("viewCount", product.viewCount).put("favouriteCount", product.favouriteCount)
                        .put("updatedTime", product.updatedTime)
                        .put("savedBy", new JSONArray(product.savedBy)));
            }
        } catch (org.json.JSONException e) { return false; }
        return com.campustrade.auth.data.SecurePrefs.get(host, host.PREFS + "_listings").edit().putString("records", records.toString()).commit();
    }

    public LinearLayout marketFilterRow() {
        LinearLayout container = new LinearLayout(host);
        HorizontalScrollView scroller = new HorizontalScrollView(host);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = host.ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, host.ui.dp(8), host.ui.dp(8), host.ui.dp(8));
        scroller.setClipToPadding(false);
        String[] filters = {"All", "Books", "Technology", "Stationery", "Free"};
        for (int i = 0; i < filters.length; i++) {
            final String filter = filters[i];
            boolean selected = filter.equals("All")
                    ? host.marketCategory.equals("All") && host.marketMaxPrice.isEmpty()
                    : filter.equals("Free") ? "0".equals(host.marketMaxPrice)
                    : host.marketCategory.equals(filter);
            TextView pill = host.ui.filterPill(filter, selected);
            pill.setTextSize(12);
            pill.setPadding(host.ui.dp(14), 0, host.ui.dp(14), 0);
            pill.setSingleLine(true);
            pill.setOnClickListener(v -> {
                if (filter.equals("All")) {
                    host.marketCategory = "All";
                    host.marketMaxPrice = "";
                } else if (filter.equals("Free")) {
                    host.marketCategory = "All";
                    host.marketMaxPrice = "0";
                } else {
                    host.marketCategory = filter;
                    host.marketMaxPrice = "";
                }
                host.showAppScreen("Market");
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, host.ui.dp(48));
            if (i > 0) params.leftMargin = host.ui.dp(8);
            row.addView(pill, params);
        }
        scroller.addView(row);
        container.addView(scroller);
        return container;
    }
}
