package com.example.electricfence;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
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
    private TextView tvLat, tvLng;
    private Marker deviceMarker;
    private double currentLat = 0, currentLng = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps);

        initViews();
        setupNavbar();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initViews() {
        tvLat = findViewById(R.id.tv_lat);
        tvLng = findViewById(R.id.tv_lng);

        findViewById(R.id.btn_copy_coords).setOnClickListener(v -> copyToClipboard());
        
        findViewById(R.id.btn_open_gmaps).setOnClickListener(v -> openInGoogleMaps());
        findViewById(R.id.btn_map_overlay).setOnClickListener(v -> openInGoogleMaps());
        
        findViewById(R.id.btn_back).setOnClickListener(v -> {
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
        findViewById(R.id.btn_notifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationActivity.class));
            finish();
        });
        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
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
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + currentLat + "," + currentLng);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback to browser
                Intent webIntent = new Intent(Intent.ACTION_VIEW, 
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=" + currentLat + "," + currentLng));
                startActivity(webIntent);
            }
        } else {
            Toast.makeText(this, "Lokasi belum tersedia", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        FirebaseManager.getInstance().listenToGPS(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        Object latObj = snapshot.child("lat").getValue();
                        Object lngObj = snapshot.child("lng").getValue();
                        
                        if (latObj != null && lngObj != null) {
                            currentLat = Double.parseDouble(latObj.toString());
                            currentLng = Double.parseDouble(lngObj.toString());
                            
                            LatLng location = new LatLng(currentLat, currentLng);
                            tvLat.setText(String.format(Locale.getDefault(), "%.6f", currentLat));
                            tvLng.setText(String.format(Locale.getDefault(), "%.6f", currentLng));

                            if (deviceMarker == null) {
                                deviceMarker = mMap.addMarker(new MarkerOptions()
                                    .position(location)
                                    .title("Lokasi Pagar Listrik"));
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 17));
                            } else {
                                deviceMarker.setPosition(location);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}