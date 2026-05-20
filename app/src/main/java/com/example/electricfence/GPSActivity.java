package com.example.electricfence;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
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

        tvLat = findViewById(R.id.tv_lat);
        tvLng = findViewById(R.id.tv_lng);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        findViewById(R.id.btn_open_gmaps).setOnClickListener(v -> {
            if (currentLat != 0 && currentLng != 0) {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + currentLat + "," + currentLng);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                }
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        FirebaseManager.getInstance().listenToGPS(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Double lat = snapshot.child("lat").getValue(Double.class);
                    Double lng = snapshot.child("lng").getValue(Double.class);

                    if (lat != null && lng != null) {
                        currentLat = lat;
                        currentLng = lng;
                        LatLng location = new LatLng(currentLat, currentLng);
                        tvLat.setText(String.format(Locale.getDefault(), "%.6f", currentLat));
                        tvLng.setText(String.format(Locale.getDefault(), "%.6f", currentLng));

                        if (deviceMarker == null) {
                            deviceMarker = mMap.addMarker(new MarkerOptions().position(location).title("Fence Device"));
                            if (deviceMarker != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
                            }
                        } else {
                            deviceMarker.setPosition(location);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}