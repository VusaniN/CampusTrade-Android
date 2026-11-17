package com.campustrade.auth;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Keyless delivery maps. OpenStreetMap tiles are rendered in a WebView and
 * live positions come from Android's LocationManager, so no Maps API key or
 * background location service is needed.
 */
public final class DeliveryMap {

    private static final double DEFAULT_LAT = -26.2041;
    private static final double DEFAULT_LNG = 28.0473;

    private static LocationManager locationManager;
    private static LocationListener locationListener;
    private static WebView activeMap;
    private static TextView activeStatus;
    private static Dialog activePage;
    private static MainActivity activeHost;
    private static String activeOrderId = "";
    private static boolean sharing;
    private static final android.os.Handler expiryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable expiryRunnable;
    private static String pendingOrderId = "";
    private static String pendingAddress = "";
    private static String pendingLabel = "";
    private static double destinationLat;
    private static double destinationLng;
    private static String destinationAddress = "";

    private interface GeoCallback {
        void onResult(double lat, double lng);
    }

    private DeliveryMap() { }

    public static void showAddressPreview(MainActivity host, String address, String fallbackLabel) {
        if (host == null) return;
        Location current = lastLocation(host);
        String label = address == null || address.trim().isEmpty()
                ? (fallbackLabel == null ? "Campus area" : fallbackLabel)
                : address.trim();
        Dialog page = host.ui.fullScreenForm();
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Delivery map", label, page::dismiss));
        TextView note = host.ui.bodyText("Preview only. Live positions are shared after the order is confirmed. OpenStreetMap needs an internet connection.",
                host.muted, 11);
        note.setPadding(host.ui.dp(20), host.ui.dp(6), host.ui.dp(20), host.ui.dp(8));
        root.addView(note);
        WebView map = mapView(host);
        activeMap = map;
        activeHost = host;
        activeOrderId = "";
        map.setMinimumHeight(host.ui.dp(320));
        final double[] destination = {0, 0};
        double lat = current == null ? DEFAULT_LAT : current.getLatitude();
        double lng = current == null ? DEFAULT_LNG : current.getLongitude();
        map.loadDataWithBaseURL("https://www.openstreetmap.org/",
                html(lat, lng, 0, 0, 0, 0, 0, 0, label, "Your current area", ""),
                "text/html", "UTF-8", null);
        if (address != null && !address.trim().isEmpty()) {
            geocodeAddress(host, address.trim(), (gLat, gLng) -> {
                destination[0] = gLat;
                destination[1] = gLng;
                if (activeMap == map) {
                    map.loadDataWithBaseURL("https://www.openstreetmap.org/",
                            html(gLat, gLng, gLat, gLng, 0, 0, 0, 0, label, "", ""),
                            "text/html", "UTF-8", null);
                }
            });
        }
        root.addView(map, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout footer = new LinearLayout(host);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(host.ui.dp(20), host.ui.dp(10), host.ui.dp(20), host.ui.dp(14));
        footer.setBackgroundColor(host.surface());
        Button useLocation = host.ui.secondaryButton("Use my current location");
        footer.addView(useLocation, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46)));
        TextView copyright = host.ui.bodyText("© OpenStreetMap contributors · no Google Maps key required",
                host.muted, 10);
        copyright.setGravity(Gravity.CENTER);
        copyright.setPadding(0, host.ui.dp(8), 0, 0);
        footer.addView(copyright);
        root.addView(footer);
        page.setContentView(root);
        page.setOnDismissListener(d -> {
            if (activeMap == map) activeMap = null;
            activeHost = null;
        });
        useLocation.setOnClickListener(v -> {
            if (!hasLocationPermission(host)) {
                pendingOrderId = "";
                pendingAddress = address == null ? "" : address;
                pendingLabel = fallbackLabel == null ? "" : fallbackLabel;
                host.requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, MainActivity.LOCATION_PERMISSION_REQUEST);
                return;
            }
            Location fix = lastLocation(host);
            if (fix == null) {
                Toast.makeText(host, "No location fix yet. Move near a window and try again.", Toast.LENGTH_LONG).show();
                return;
            }
            map.loadDataWithBaseURL("https://www.openstreetmap.org/",
                    html(fix.getLatitude(), fix.getLongitude(), destination[0], destination[1], 0, 0, 0, 0,
                            label, "Your current area", ""), "text/html", "UTF-8", null);
        });
        page.show();
        applyFullScreenWindow(host, page);
    }

    public static void showOrder(MainActivity host, MainActivity.TransactionRecord order) {
        if (host == null || order == null || !order.deliveryOrder()) return;
        if (!order.isBuyer(host.currentStudentEmail) && !order.isSeller(host.currentStudentEmail)) return;
        if (!order.canShareLiveLocation()) {
            host.ui.showSimpleMessage(order.status.equals(MainActivity.TransactionRecord.COMPLETED)
                            ? "Delivery confirmed" : "Live map closed",
                    closedLocationMessage(order));
            return;
        }
        Dialog page = host.ui.fullScreenForm();
        activePage = page;
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(host.page);
        root.addView(host.ui.pageHeader("Delivery map", order.itemTitle, page::dismiss));
        LinearLayout details = new LinearLayout(host);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(host.ui.dp(20), host.ui.dp(4), host.ui.dp(20), host.ui.dp(8));
        details.addView(host.ui.bodyText(order.deliveryAddress.isEmpty()
                ? "No delivery address yet." : order.deliveryAddress, host.text, 12));
        TextView status = host.ui.bodyText(locationStatus(order), host.muted, 11);
        status.setPadding(0, host.ui.dp(4), 0, 0);
        details.addView(status);
        activeStatus = status;
        root.addView(details);
        WebView map = mapView(host);
        root.addView(map, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        activeMap = map;
        activeHost = host;
        activeOrderId = order.id;
        destinationLat = 0;
        destinationLng = 0;
        destinationAddress = order.deliveryAddress == null ? "" : order.deliveryAddress.trim();
        render(host, order, map);
        if (!destinationAddress.isEmpty()) {
            geocodeAddress(host, destinationAddress, (gLat, gLng) -> {
                if (activeMap == map && activeOrderId.equals(order.id)) {
                    destinationLat = gLat;
                    destinationLng = gLng;
                    render(host, order, map);
                }
            });
        }
        LinearLayout footer = new LinearLayout(host);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(host.ui.dp(20), host.ui.dp(10), host.ui.dp(20), host.ui.dp(14));
        footer.setBackgroundColor(host.surface());
        Button share = host.ui.primaryButton(sharing ? "Stop sharing my location" : "Share my live location");
        footer.addView(share, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(46)));
        Button refresh = host.ui.secondaryButton("Refresh positions");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.ui.dp(42));
        refreshParams.topMargin = host.ui.dp(6);
        footer.addView(refresh, refreshParams);
        TextView note = host.ui.bodyText("Positions update only while this page is open. Sharing stops when you close it. Never share a home address you do not want the other student to keep.",
                host.muted, 10);
        note.setPadding(0, host.ui.dp(8), 0, 0);
        footer.addView(note);
        root.addView(footer);
        page.setContentView(root);
        page.setOnDismissListener(d -> {
            stopUpdates();
            if (activeMap == map) activeMap = null;
            if (activeHost == host) activeHost = null;
            if (activePage == page) activePage = null;
            activeStatus = null;
            activeOrderId = "";
        });
        share.setOnClickListener(v -> {
            if (sharing) {
                stopUpdates();
                sharing = false;
                share.setText("Share my live location");
                Toast.makeText(host, "Live location sharing stopped.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasLocationPermission(host)) {
                pendingOrderId = order.id;
                pendingAddress = "";
                pendingLabel = "";
                host.requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, MainActivity.LOCATION_PERMISSION_REQUEST);
                return;
            }
            sharing = true;
            share.setText("Stop sharing my location");
            startUpdates(host, order.id);
            Toast.makeText(host, "Sharing your live location with the other student.", Toast.LENGTH_SHORT).show();
        });
        refresh.setOnClickListener(v -> host.order.cloudPullOrders(() -> {
            MainActivity.TransactionRecord fresh = host.order.orderById(order.id);
            if (fresh != null && page.isShowing()) {
                if (!fresh.canShareLiveLocation()) {
                    onOrderUpdated(host, fresh);
                    return;
                }
                status.setText(locationStatus(fresh));
                render(host, fresh, map);
                Toast.makeText(host, "Delivery positions refreshed.", Toast.LENGTH_SHORT).show();
            }
        }));
        page.show();
        applyFullScreenWindow(host, page);
    }

    public static void requestLiveShare(MainActivity host, MainActivity.TransactionRecord order) {
        if (host == null || order == null) return;
        if (!order.canShareLiveLocation()) {
            host.ui.showSimpleMessage("Live map closed", closedLocationMessage(order));
            return;
        }
        if (!hasLocationPermission(host)) {
            pendingOrderId = order.id;
            pendingAddress = "";
            pendingLabel = "";
            host.requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, MainActivity.LOCATION_PERMISSION_REQUEST);
            return;
        }
        sharing = true;
        showOrder(host, order);
        startUpdates(host, order.id);
    }

    public static void onPermissionResult(MainActivity host, boolean granted) {
        String orderId = pendingOrderId;
        String address = pendingAddress;
        String label = pendingLabel;
        pendingOrderId = "";
        pendingAddress = "";
        pendingLabel = "";
        if (!granted) {
            Toast.makeText(host, "Location permission is needed to show your live position.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (orderId != null && !orderId.isEmpty()) {
            MainActivity.TransactionRecord order = host.order.orderById(orderId);
            if (order != null && order.canShareLiveLocation()) {
                sharing = true;
                showOrder(host, order);
                startUpdates(host, order.id);
            } else if (order != null) {
                host.ui.showSimpleMessage("Live map closed", closedLocationMessage(order));
            }
            return;
        }
        showAddressPreview(host, address, label);
    }

    /** True while the live map for this order is the top page. */
    public static boolean isShowingOrder(String orderId) {
        return orderId != null && !orderId.isEmpty() && orderId.equals(activeOrderId)
                && activePage != null && activePage.isShowing();
    }

    /** Close an open live map as soon as the delivery is confirmed or cancelled. */
    public static void onOrderUpdated(MainActivity host, MainActivity.TransactionRecord order) {
        if (host == null || order == null) return;
        if (!activeOrderId.equals(order.id)) return;
        if (order.canShareLiveLocation()) {
            if (activeMap != null) render(host, order, activeMap);
            if (activeStatus != null) activeStatus.setText(locationStatus(order));
            return;
        }
        stopUpdates();
        boolean wasShowing = activePage != null && activePage.isShowing();
        if (wasShowing) {
            activePage.dismiss();
            Toast.makeText(host, "Delivery finished. Live map and location sharing are now closed.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String closedLocationMessage(MainActivity.TransactionRecord order) {
        if (order.status.equals(MainActivity.TransactionRecord.COMPLETED)) {
            return "Delivery has been confirmed. The live map and both students' locations are no longer available.";
        }
        if (order.status.equals(MainActivity.TransactionRecord.CANCELLED)) {
            return "This order is closed. The live map and location sharing are no longer available.";
        }
        return "Live tracking unlocks only while the seller has confirmed and the delivery is still active.";
    }

    private static void geocodeAddress(MainActivity host, String address, GeoCallback callback) {
        if (address == null || address.trim().isEmpty() || callback == null) return;
        final String query = address.trim();
        new Thread(() -> {
            double lat = 0;
            double lng = 0;
            try {
                Geocoder geocoder = new Geocoder(host, Locale.getDefault());
                if (Geocoder.isPresent()) {
                    List<Address> results = geocoder.getFromLocationName(query, 1);
                    if (results != null && !results.isEmpty()) {
                        Address found = results.get(0);
                        if (found.hasLatitude() && found.hasLongitude()) {
                            lat = found.getLatitude();
                            lng = found.getLongitude();
                        }
                    }
                }
            } catch (Exception ignored) { }
            if ((lat == 0 && lng == 0) || Math.abs(lat) < 0.0001) {
                HttpURLConnection connection = null;
                try {
                    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                    URL url = new URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + encoded);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("User-Agent", "CampusTrade/1.5.3 (student marketplace)");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                        StringBuilder body = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) body.append(line);
                        }
                        JSONArray array = new JSONArray(body.toString());
                        if (array.length() > 0) {
                            JSONObject first = array.getJSONObject(0);
                            lat = first.optDouble("lat", 0);
                            lng = first.optDouble("lon", 0);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            // If the full street address is not mapped, centre on the last
            // suburb/city name instead of dropping back to a random area.
            if (lat == 0 && lng == 0) {
                String[] parts = query.split("[ ,]+");
                String city = parts.length > 1 ? parts[parts.length - 1] : "";
                if (city.length() >= 3 && !city.equalsIgnoreCase(query)) {
                    try {
                        Geocoder geocoder = new Geocoder(host, Locale.getDefault());
                        List<Address> results = geocoder.getFromLocationName(city, 1);
                        if (results != null && !results.isEmpty()) {
                            Address found = results.get(0);
                            if (found.hasLatitude() && found.hasLongitude()) {
                                lat = found.getLatitude();
                                lng = found.getLongitude();
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }
            if (lat == 0 && lng == 0) return;
            final double resultLat = lat;
            final double resultLng = lng;
            host.runOnUiThread(() -> {
                if (!host.isDestroyed() && !host.isFinishing()) callback.onResult(resultLat, resultLng);
            });
        }).start();
    }

    private static void applyFullScreenWindow(MainActivity host, Dialog page) {
        android.view.Window window = page.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(host.page));
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setGravity(Gravity.BOTTOM);
    }

    private static WebView mapView(MainActivity host) {
        WebView map = new WebView(host);
        WebSettings settings = map.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        map.setWebViewClient(new WebViewClient());
        map.setBackgroundColor(Color.rgb(238, 242, 238));
        return map;
    }

    private static void render(MainActivity host, MainActivity.TransactionRecord order, WebView map) {
        if (!order.canShareLiveLocation()) return;
        double buyerLat = order.buyerLat;
        double buyerLng = order.buyerLng;
        double sellerLat = order.sellerLat;
        double sellerLng = order.sellerLng;
        boolean hasBuyer = buyerLat != 0 && buyerLng != 0;
        boolean hasSeller = sellerLat != 0 && sellerLng != 0;
        boolean hasDestination = destinationLat != 0 && destinationLng != 0;
        double centerLat = hasDestination ? destinationLat : hasBuyer ? buyerLat : hasSeller ? sellerLat : DEFAULT_LAT;
        double centerLng = hasDestination ? destinationLng : hasBuyer ? buyerLng : hasSeller ? sellerLng : DEFAULT_LNG;
        String buyer = hasBuyer ? "Buyer · updated " + host.messageTime(order.buyerLocationAt) : "";
        String seller = hasSeller ? "Seller · updated " + host.messageTime(order.sellerLocationAt) : "";
        map.loadDataWithBaseURL("https://www.openstreetmap.org/",
                html(centerLat, centerLng, hasDestination ? destinationLat : 0,
                        hasDestination ? destinationLng : 0, buyerLat, buyerLng, sellerLat, sellerLng,
                        destinationAddress.isEmpty() ? order.deliveryAddress : destinationAddress, buyer, seller),
                "text/html", "UTF-8", null);
    }

    private static String locationStatus(MainActivity.TransactionRecord order) {
        String buyer = order.buyerLocationAt <= 0 ? "Buyer not sharing"
                : "Buyer updated " + hostTime(order.buyerLocationAt);
        String seller = order.sellerLocationAt <= 0 ? "Seller not sharing"
                : "Seller updated " + hostTime(order.sellerLocationAt);
        return buyer + " · " + seller;
    }

    private static String hostTime(long time) {
        long minutes = Math.max(0, System.currentTimeMillis() - time) / 60000;
        if (minutes == 0) return "just now";
        if (minutes < 60) return minutes + " min ago";
        return (minutes / 60) + " hr ago";
    }

    private static String html(double centerLat, double centerLng, double destinationLat, double destinationLng,
                               double buyerLat, double buyerLng, double sellerLat, double sellerLng,
                               String address, String buyer, String seller) {
        String addressHtml = escapeHtml(address == null || address.isEmpty() ? "Delivery area" : address);
        String buyerLabel = JSONObject.quote(buyer == null ? "" : buyer);
        String sellerLabel = JSONObject.quote(seller == null ? "" : seller);
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
                + "<style>html,body,#map{height:100%;margin:0;overflow:hidden;background:#e8eee9;font-family:sans-serif}"
                + "#tiles{position:absolute;inset:0;overflow:hidden;background:#e8eee9}"
                + ".tile{position:absolute;width:256px;height:256px;user-select:none;-webkit-user-drag:none}"
                + ".marker{position:absolute;z-index:30;width:28px;height:28px;margin-left:-14px;margin-top:-14px;border-radius:50%;"
                + "border:4px solid #fff;box-shadow:0 0 0 10px rgba(47,166,125,.18),0 4px 12px rgba(0,0,0,.25)}"
                + ".marker.buyer{background:#2fa67d}.marker.seller{background:#3d8bd8;box-shadow:0 0 0 10px rgba(61,139,216,.18),0 4px 12px rgba(0,0,0,.25)}"
                + ".marker.destination{background:#e23a1d;box-shadow:0 0 0 10px rgba(226,58,29,.20),0 4px 12px rgba(0,0,0,.25)}"
                + ".label{position:absolute;z-index:600;left:12px;right:12px;top:12px;background:rgba(255,255,255,.96);"
                + "border:1px solid #d7dfd9;border-radius:12px;padding:10px 12px;font-size:12px;color:#1b2c25;"
                + "box-shadow:0 4px 16px rgba(0,0,0,.12)}.muted{color:#65756d;font-size:10px;margin-top:3px}"
                + ".key{display:flex;gap:12px;margin-top:6px;font-size:10px;color:#65756d}"
                + ".dot{display:inline-block;width:8px;height:8px;border-radius:99px;margin-right:4px}"
                + ".attribution{position:absolute;z-index:600;right:8px;bottom:8px;background:rgba(255,255,255,.9);"
                + "padding:3px 6px;border-radius:5px;font-size:9px;color:#45544c}</style></head>"
                + "<body><div id=\"map\"><div id=\"tiles\"></div>"
                + "<div class=\"label\">" + addressHtml
                + "<div class=\"muted\">Live positions stop when the map closes.</div>"
                + "<div class=\"key\"><span><i class=\"dot\" style=\"background:#e23a1d\"></i>Delivery</span>"
                + "<span><i class=\"dot\" style=\"background:#2fa67d\"></i>Buyer</span>"
                + "<span><i class=\"dot\" style=\"background:#3d8bd8\"></i>Seller</span></div></div>"
                + "<div class=\"attribution\">© OpenStreetMap contributors</div>"
                + "<script>"
                + "var centerLat=" + coord(centerLat) + ",centerLng=" + coord(centerLng) + ",zoom=14;"
                + "var map=document.getElementById('map'),tiles=document.getElementById('tiles');"
                + "var W=map.clientWidth||window.innerWidth,H=map.clientHeight||window.innerHeight;"
                + "function tx(lon,z){return (lon+180)/360*Math.pow(2,z);}"
                + "function ty(lat,z){var r=lat*Math.PI/180;return (1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2*Math.pow(2,z);}"
                + "var cx=tx(centerLng,zoom)*256,cy=ty(centerLat,zoom)*256;"
                + "var x0=Math.floor((cx-W/2)/256),x1=Math.ceil((cx+W/2)/256);"
                + "var y0=Math.floor((cy-H/2)/256),y1=Math.ceil((cy+H/2)/256),n=Math.pow(2,zoom);"
                + "for(var x=x0;x<=x1;x++){for(var y=y0;y<=y1;y++){if(y<0||y>=n)continue;"
                + "var img=document.createElement('img');img.className='tile';"
                + "img.src='https://tile.openstreetmap.org/'+zoom+'/'+((x%n+n)%n)+'/'+y+'.png';"
                + "img.style.left=(x*256-cx+W/2)+'px';img.style.top=(y*256-cy+H/2)+'px';"
                + "img.onerror=function(){this.style.opacity='0.15'};tiles.appendChild(img);}}"
                + "function marker(lat,lng,label,cls){if(lat===0&&lng===0)return;"
                + "var mx=tx(lng,zoom)*256-cx+W/2,my=ty(lat,zoom)*256-cy+H/2;"
                + "var m=document.createElement('div');m.className='marker '+cls;m.title=label;"
                + "m.style.left=mx+'px';m.style.top=my+'px';map.appendChild(m);}"
                + "marker(" + coord(destinationLat) + "," + coord(destinationLng) + ",'Delivery address','destination');"
                + "marker(" + coord(buyerLat) + "," + coord(buyerLng) + "," + buyerLabel + ",'buyer');"
                + "marker(" + coord(sellerLat) + "," + coord(sellerLng) + "," + sellerLabel + ",'seller');"
                + "</script></div></body></html>";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String coord(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static boolean hasLocationPermission(MainActivity host) {
        return host.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || host.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static Location lastLocation(MainActivity host) {
        if (!hasLocationPermission(host)) return null;
        try {
            LocationManager manager = (LocationManager) host.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) return null;
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                Location fix = manager.getLastKnownLocation(provider);
                if (fix != null && (best == null || fix.getTime() > best.getTime())) best = fix;
            }
            return best;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static void startUpdates(MainActivity host, String orderId) {
        stopUpdates();
        if (!hasLocationPermission(host)) return;
        MainActivity.TransactionRecord trackingOrder = host.order.orderById(orderId);
        if (trackingOrder == null || !trackingOrder.canShareLiveLocation()) return;
        try {
            locationManager = (LocationManager) host.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return;
            activeHost = host;
            activeOrderId = orderId;
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (location == null) return;
                    MainActivity.TransactionRecord order = host.order.orderById(activeOrderId);
                    if (order == null) return;
                    boolean buyer = order.isBuyer(host.currentStudentEmail);
                    boolean seller = order.isSeller(host.currentStudentEmail);
                    if (!buyer && !seller) return;
                    if (!order.canShareLiveLocation()) {
                        stopUpdates();
                        if (activePage != null && activePage.isShowing()) activePage.dismiss();
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (buyer) {
                        order.buyerLat = location.getLatitude();
                        order.buyerLng = location.getLongitude();
                        order.buyerLocationAt = now;
                    } else {
                        order.sellerLat = location.getLatitude();
                        order.sellerLng = location.getLongitude();
                        order.sellerLocationAt = now;
                    }
                    host.order.saveTransactions();
                    host.order.cloudUpdateOrder(order);
                    if (activeMap != null && activeOrderId.equals(order.id)) render(host, order, activeMap);
                    if (activeStatus != null) activeStatus.setText(locationStatus(order));
                }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { }
                @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) { }
            };
            boolean requested = false;
            for (String provider : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 12000L, 8f, locationListener, Looper.getMainLooper());
                    requested = true;
                }
            }
            Location current = lastLocation(host);
            if (current != null) locationListener.onLocationChanged(current);
            if (!requested) {
                Toast.makeText(host, "Location services are off. Turn on GPS to share a live position.",
                        Toast.LENGTH_LONG).show();
                sharing = false;
            } else {
                sharing = true;
            }
            if (expiryRunnable != null) expiryHandler.removeCallbacks(expiryRunnable);
            expiryRunnable = () -> {
                stopUpdates();
                Toast.makeText(host, "Live location sharing stopped after 30 minutes for safety.", Toast.LENGTH_LONG).show();
            };
            expiryHandler.postDelayed(expiryRunnable, 30L * 60 * 1000);
        } catch (SecurityException | IllegalArgumentException e) {
            android.util.Log.w("CampusTrade", "location updates failed: " + e.getMessage());
        }
    }

    private static void stopUpdates() {
        sharing = false;
        if (expiryRunnable != null) expiryHandler.removeCallbacks(expiryRunnable);
        expiryRunnable = null;
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) { }
        }
        locationManager = null;
        locationListener = null;
    }
}
