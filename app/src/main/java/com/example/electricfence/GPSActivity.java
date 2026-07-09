package com.example.electricfence;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.Locale;

public class GPSActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Marker deviceMarker;
    private Marker homeMarker;

    // WebView fallback jika Maps API Key belum tersedia
    private WebView webViewMap;
    private boolean useWebViewFallback = false;

    // Koordinat device
    private TextView tvLat, tvLng;
    // Info Home
    private TextView tvHomeLat, tvHomeLng, tvHomeSet, tvDistance, tvSatellite;
    // Anti Theft
    private TextView tvTheftStatus, tvTheftDetail;
    private LinearLayout layoutTheftWarning;

    private double currentLat = 0, currentLng = 0;
    private boolean firstMapMove = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps);

        initViews();
        setupNavbar();

        // Cek apakah Google Maps API Key sudah diset
        String mapsApiKey = getMetaDataValue("com.google.android.geo.API_KEY");
        if ("YOUR_GOOGLE_MAPS_API_KEY_HERE".equals(mapsApiKey) || mapsApiKey == null || mapsApiKey.isEmpty()) {
            // Gunakan WebView sebagai fallback
            useWebViewFallback = true;
            setupWebViewFallback();
            observeGPS(); // Tetap listen Firebase untuk update koordinat ke WebView
        } else {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            }
        }
    }

    private String getMetaDataValue(String key) {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager()
                    .getApplicationInfo(getPackageName(), android.content.pm.PackageManager.GET_META_DATA);
            Object value = ai.metaData.get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Setup WebView sebagai fallback peta (tanpa Google Maps API Key)
     * Menggunakan OpenStreetMap (Leaflet.js) — gratis, tidak perlu API Key
     */
    private void setupWebViewFallback() {
        // Hide the Google Maps CardView to prevent UI overlaps
        View cardGmaps = findViewById(R.id.card_gmaps);
        if (cardGmaps != null) {
            cardGmaps.setVisibility(View.GONE);
        }

        // Cari WebView fallback di layout (gunakan WebView di tempat map fragment)
        webViewMap = findViewById(R.id.webview_map_fallback);
        if (webViewMap == null) return;

        webViewMap.setVisibility(View.VISIBLE);
        WebSettings settings = webViewMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        webViewMap.setWebViewClient(new WebViewClient());

        // Load peta default (Bandung) - akan di-update saat GPS data diterima
        loadOpenStreetMap(0, 0, false);
    }

    /**
     * Load peta OpenStreetMap dengan Leaflet.js menggunakan koordinat yang diberikan
     */
    private void loadOpenStreetMap(double lat, double lng, boolean hasCoords) {
        if (webViewMap == null) return;

        String centerLat = hasCoords ? String.valueOf(lat) : "-6.9175";
        String centerLng = hasCoords ? String.valueOf(lng) : "107.6191";
        String markerHtml = hasCoords ?
                "var marker = L.marker([" + lat + ", " + lng + "]).addTo(map).bindPopup('📍 Lokasi Pagar Listrik').openPopup();" : "";

        String html = "<!DOCTYPE html><html><head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<style>body{margin:0;padding:0}#map{width:100%;height:100vh}</style>" +
                "</head><body>" +
                "<div id='map'></div>" +
                "<script>" +
                "var map = L.map('map').setView([" + centerLat + "," + centerLng + "], 16);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {" +
                "attribution:'&copy; OpenStreetMap contributors'}).addTo(map);" +
                markerHtml +
                "</script></body></html>";

        webViewMap.loadDataWithBaseURL("https://unpkg.com", html, "text/html", "UTF-8", null);
    }

    private void initViews() {
        tvLat = findViewById(R.id.tv_lat);
        tvLng = findViewById(R.id.tv_lng);
        tvHomeLat = findViewById(R.id.tv_home_lat);
        tvHomeLng = findViewById(R.id.tv_home_lng);
        tvHomeSet = findViewById(R.id.tv_home_set);
        tvDistance = findViewById(R.id.tv_distance);
        tvSatellite = findViewById(R.id.tv_satellite);
        tvTheftStatus = findViewById(R.id.tv_theft_status);
        tvTheftDetail = findViewById(R.id.tv_theft_detail);
        layoutTheftWarning = findViewById(R.id.layout_theft_warning);

        // Copy coords
        View btnCopy = findViewById(R.id.btn_copy_coords);
        if (btnCopy != null) btnCopy.setOnClickListener(v -> copyToClipboard());

        // Google Maps / external maps button
        View btnGmaps = findViewById(R.id.btn_open_gmaps);
        if (btnGmaps != null) btnGmaps.setOnClickListener(v -> openInGoogleMaps());

        View btnMapOverlay = findViewById(R.id.btn_map_overlay);
        if (btnMapOverlay != null) btnMapOverlay.setOnClickListener(v -> openInGoogleMaps());

        // Set Lokasi Aman
        View btnSetHome = findViewById(R.id.btn_set_home);
        if (btnSetHome != null) {
            btnSetHome.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Set Lokasi Aman")
                        .setMessage("Simpan koordinat GPS saat ini sebagai lokasi aman perangkat?")
                        .setPositiveButton("Ya, Simpan", (dialog, which) -> {
                            FirebaseManager.getInstance().setSetHome(true);
                            Toast.makeText(this, "Perintah Set Lokasi Aman dikirim", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });
        }

        // Reset Lokasi Aman
        View btnResetHome = findViewById(R.id.btn_reset_home);
        if (btnResetHome != null) {
            btnResetHome.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Reset Lokasi Aman")
                        .setMessage("Hapus lokasi aman yang tersimpan?")
                        .setPositiveButton("Ya, Reset", (dialog, which) -> {
                            FirebaseManager.getInstance().setResetHome(true);
                            Toast.makeText(this, "Perintah Reset Lokasi dikirim", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });
        }

        // Back button
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
    }

    private void setupNavbar() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
        findViewById(R.id.nav_voltage).setOnClickListener(v -> {
            startActivity(new Intent(this, VoltageActivity.class));
            finish();
        });
        // GPS is current page
        findViewById(R.id.nav_gps).setOnClickListener(v -> {});
        findViewById(R.id.btn_notifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationActivity.class));
            finish();
        });
        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Start listening to GPS from Firebase
        observeGPS();
    }

    private void observeGPS() {
        FirebaseManager.getInstance().listenToGPS(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                try {
                    Object latObj = snapshot.child("Latitude").getValue();
                    Object lngObj = snapshot.child("Longitude").getValue();
                    Object homeLatObj = snapshot.child("HomeLatitude").getValue();
                    Object homeLngObj = snapshot.child("HomeLongitude").getValue();
                    Boolean homeSet = snapshot.child("HomeSet").getValue(Boolean.class);
                    Object satelliteObj = snapshot.child("Satellite").getValue();
                    Object distanceObj = snapshot.child("Distance").getValue();
                    Boolean theftDetected = snapshot.child("TheftDetected").getValue(Boolean.class);
                    String theftStatusStr = snapshot.child("TheftStatus").getValue(String.class);

                    // Update device coordinates
                    if (latObj != null && lngObj != null) {
                        currentLat = Double.parseDouble(latObj.toString());
                        currentLng = Double.parseDouble(lngObj.toString());

                        if (tvLat != null)
                            tvLat.setText(String.format(Locale.getDefault(), "%.6f", currentLat));
                        if (tvLng != null)
                            tvLng.setText(String.format(Locale.getDefault(), "%.6f", currentLng));

                        if (useWebViewFallback) {
                            // Update OpenStreetMap WebView
                            loadOpenStreetMap(currentLat, currentLng, currentLat != 0 || currentLng != 0);
                        } else {
                            updateMap(currentLat, currentLng);
                        }
                    }

                    // Home location
                    double homeLat = homeLatObj != null ? Double.parseDouble(homeLatObj.toString()) : 0;
                    double homeLng = homeLngObj != null ? Double.parseDouble(homeLngObj.toString()) : 0;
                    boolean isHomeSet = homeSet != null && homeSet;

                    if (tvHomeLat != null)
                        tvHomeLat.setText(isHomeSet ? String.format(Locale.getDefault(), "%.6f", homeLat) : "-");
                    if (tvHomeLng != null)
                        tvHomeLng.setText(isHomeSet ? String.format(Locale.getDefault(), "%.6f", homeLng) : "-");
                    if (tvHomeSet != null)
                        tvHomeSet.setText(isHomeSet ? "✓ Tersimpan" : "✗ Belum diset");

                    // Satellite
                    int satellite = satelliteObj != null ? Integer.parseInt(satelliteObj.toString()) : 0;
                    if (tvSatellite != null)
                        tvSatellite.setText(satellite + " satelit");

                    // Distance
                    double distance = distanceObj != null ? Double.parseDouble(distanceObj.toString()) : 0;
                    if (tvDistance != null)
                        tvDistance.setText(String.format(Locale.getDefault(), "%.1f m", distance));

                    // Home marker on Google Maps (bukan WebView)
                    if (!useWebViewFallback && mMap != null && isHomeSet && homeLat != 0 && homeLng != 0) {
                        LatLng homeLocation = new LatLng(homeLat, homeLng);
                        if (homeMarker == null) {
                            homeMarker = mMap.addMarker(new MarkerOptions()
                                    .position(homeLocation)
                                    .title("Lokasi Aman"));
                        } else {
                            homeMarker.setPosition(homeLocation);
                        }
                    }

                    // Anti Theft status
                    boolean theft = theftDetected != null && theftDetected;
                    updateTheftUI(theft, theftStatusStr);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateMap(double lat, double lng) {
        if (mMap == null) return;
        LatLng location = new LatLng(lat, lng);
        if (deviceMarker == null) {
            deviceMarker = mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Lokasi Pagar Listrik"));
            if (firstMapMove) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 17));
                firstMapMove = false;
            }
        } else {
            deviceMarker.setPosition(location);
        }
    }

    private void updateTheftUI(boolean isTheft, String theftStatusStr) {
        if (tvTheftStatus == null) return;
        if (isTheft) {
            tvTheftStatus.setText("⚠ DIDUGA DICURI");
            tvTheftStatus.setTextColor(getColor(R.color.neon_red));
            if (tvTheftDetail != null) {
                tvTheftDetail.setText("Perangkat berpindah dari lokasi awal. Kemungkinan dicuri.");
                tvTheftDetail.setVisibility(View.VISIBLE);
            }
            if (layoutTheftWarning != null)
                layoutTheftWarning.setBackgroundColor(0xFFFFECE8);
        } else {
            tvTheftStatus.setText("✓ Perangkat berada di lokasi aman");
            tvTheftStatus.setTextColor(getColor(R.color.status_green));
            if (tvTheftDetail != null)
                tvTheftDetail.setVisibility(View.GONE);
            if (layoutTheftWarning != null)
                layoutTheftWarning.setBackgroundColor(0xFFEDF7ED);
        }
    }

    private void copyToClipboard() {
        String coords = currentLat + ", " + currentLng;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Coordinates", coords);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Koordinat disalin ke clipboard", Toast.LENGTH_SHORT).show();
    }

    private void openInGoogleMaps() {
        if (currentLat != 0 && currentLng != 0) {
            String url = "https://www.google.com/maps?q=" + currentLat + "," + currentLng;
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(webIntent);
        } else {
            Toast.makeText(this, "Lokasi belum tersedia", Toast.LENGTH_SHORT).show();
        }
    }
}
