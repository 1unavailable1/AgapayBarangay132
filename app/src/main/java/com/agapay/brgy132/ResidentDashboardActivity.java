package com.agapay.brgy132;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ResidentDashboardActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private CloudSyncManager cloudSyncManager;
    private MapView residentMap;
    private String currentEmail = "juan@gmail.com";
    private int currentResidentId = 1;
    private String currentResidentName = "Juan Dela Cruz";
    private String currentResidentStreet = "General Concepcion";
    private String currentContact = "";
    private String currentPassword = "";
    private int currentRoomId = 0;
    private String selectedPhotoUri = "";
    private boolean isPasswordVisible = false;

    private ScrollView scrollHomeTab, scrollProfileTab;
    private ImageView ivNavHome, ivNavProfile, ivHomeAvatar, ivProfilePreview;
    private TextView tvNavHome, tvNavProfile, btnProfileDone;

    private final Handler liveSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveSyncRunnable = new Runnable() {
        @Override
        public void run() {
            refreshAllDashboardData();
            liveSyncHandler.postDelayed(this, 1500);
        }
    };

    private final BroadcastReceiver instantDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshAllDashboardData();
        }
    };

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String savedLocalUri = copyPhotoToInternalStorage(uri);
                    if (savedLocalUri != null) {
                        selectedPhotoUri = savedLocalUri;
                        applyAvatarImage(ivProfilePreview, selectedPhotoUri);
                        applyAvatarImage(ivHomeAvatar, selectedPhotoUri);
                        TextView tvFileName = findViewById(R.id.tvPhotoFileName);
                        if (tvFileName != null) {
                            tvFileName.setText("Photo ready");
                            tvFileName.setTextColor(Color.parseColor("#138A4C"));
                        }
                        Toast.makeText(this, "Photo selected! Tap Save Profile Updates.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Could not load selected image.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_resident_dashboard);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainResident), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dbHelper = new DatabaseHelper(this);
        cloudSyncManager = new CloudSyncManager(this);

        String passedEmail = getIntent().getStringExtra("USER_EMAIL");
        if (passedEmail != null && !passedEmail.isEmpty()) {
            currentEmail = passedEmail;
        }

        bindViews();
        fixTextContrast();
        setupMap();
        refreshAllDashboardData();
        setupListeners();

        cloudSyncManager.startLiveSync(() -> runOnUiThread(this::refreshAllDashboardData));
    }

    private void fixTextContrast() {
        View centerMap = findViewById(R.id.btnCenterMap);
        if (centerMap instanceof TextView) {
            ((TextView) centerMap).setTextColor(Color.WHITE);
        }
        View openQr = findViewById(R.id.btnOpenQrModal);
        if (openQr instanceof TextView) {
            ((TextView) openQr).setTextColor(Color.WHITE);
        }
        View choosePhoto = findViewById(R.id.btnChoosePhoto);
        if (choosePhoto instanceof TextView) {
            ((TextView) choosePhoto).setTextColor(Color.WHITE);
        }
        View saveProfile = findViewById(R.id.btnSaveProfile);
        if (saveProfile instanceof TextView) {
            ((TextView) saveProfile).setTextColor(Color.WHITE);
        }
        TextView btnTogglePass = findViewById(R.id.btnToggleResPassword);
        if (btnTogglePass != null) {
            btnTogglePass.setTextColor(Color.WHITE);
        }
        TextView tvFileName = findViewById(R.id.tvPhotoFileName);
        if (tvFileName != null) {
            tvFileName.setTextColor(Color.parseColor("#374151"));
        }
    }

    private String copyPhotoToInternalStorage(Uri sourceUri) {
        try (InputStream in = getContentResolver().openInputStream(sourceUri)) {
            if (in == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) return null;

            File outFile = new File(getFilesDir(), "resident_avatar_" + currentResidentId + ".jpg");
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
                out.flush();
            }
            return Uri.fromFile(outFile).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void applyAvatarImage(ImageView imageView, String uriString) {
        if (imageView == null) return;
        if (uriString != null && !uriString.isEmpty() && !uriString.contains("content://me")) {
            try {
                Uri parsed = Uri.parse(uriString);
                if ("file".equals(parsed.getScheme()) && parsed.getPath() != null) {
                    File f = new File(parsed.getPath());
                    if (f.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                        if (bmp != null) {
                            imageView.setImageTintList(null);
                            imageView.setColorFilter(null);
                            imageView.clearColorFilter();
                            imageView.setBackgroundColor(Color.WHITE);
                            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            imageView.setPadding(0, 0, 0, 0);
                            imageView.setImageBitmap(bmp);
                            return;
                        }
                    }
                }
                imageView.setImageTintList(null);
                imageView.clearColorFilter();
                imageView.setBackgroundColor(Color.WHITE);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setPadding(0, 0, 0, 0);
                imageView.setImageURI(parsed);
                return;
            } catch (Exception ignored) {
            }
        }
        imageView.setBackgroundColor(Color.parseColor("#138A4C"));
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setPadding(24, 24, 24, 24);
        imageView.setImageResource(R.drawable.ic_home);
        imageView.setImageTintList(ColorStateList.valueOf(Color.WHITE));
    }

    private void refreshAllDashboardData() {
        loadResidentData();
        loadEvacuationRoomsAndMap();
        loadResidentTransparencyCards();
    }

    private void bindViews() {
        scrollHomeTab = findViewById(R.id.scrollHomeTab);
        scrollProfileTab = findViewById(R.id.scrollProfileTab);

        ivNavHome = findViewById(R.id.ivNavHome);
        ivNavProfile = findViewById(R.id.ivNavProfile);
        tvNavHome = findViewById(R.id.tvNavHome);
        tvNavProfile = findViewById(R.id.tvNavProfile);
        btnProfileDone = findViewById(R.id.btnProfileDone);

        ivHomeAvatar = findViewById(R.id.ivHomeAvatar);
        ivProfilePreview = findViewById(R.id.ivProfilePreview);
        residentMap = findViewById(R.id.osmMapView);
    }

    private void setupMap() {
        if (residentMap != null) {
            residentMap.setTileSource(TileSourceFactory.MAPNIK);
            residentMap.setMultiTouchControls(true);
            residentMap.getController().setZoom(17.5);
            GeoPoint savedPin = getSavedCustomPin();
            residentMap.getController().setCenter(savedPin != null ? savedPin : resolveResidentStreetPoint());
        }
    }

    private SharedPreferences getPinPrefs() {
        return getSharedPreferences("resident_pins_prefs", MODE_PRIVATE);
    }

    private void saveCustomPin(double lat, double lng, String label) {
        String key = currentEmail.trim().toLowerCase();
        getPinPrefs().edit()
                .putString(key + "_lat", String.valueOf(lat))
                .putString(key + "_lng", String.valueOf(lng))
                .putString(key + "_label", label)
                .apply();
    }

    private GeoPoint getSavedCustomPin() {
        String key = currentEmail.trim().toLowerCase();
        String latStr = getPinPrefs().getString(key + "_lat", null);
        String lngStr = getPinPrefs().getString(key + "_lng", null);
        if (latStr != null && lngStr != null) {
            try {
                return new GeoPoint(Double.parseDouble(latStr), Double.parseDouble(lngStr));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String getSavedCustomPinLabel() {
        String key = currentEmail.trim().toLowerCase();
        return getPinPrefs().getString(key + "_label", currentResidentStreet);
    }

    private GeoPoint resolveResidentStreetPoint() {
        List<MarkerData> streetMarkers = Arrays.asList(
                new MarkerData("Adelfa", 14.6634, 120.9913),
                new MarkerData("General Tinio", 14.6612, 120.9901),
                new MarkerData("General Concepcion", 14.6612, 120.9908),
                new MarkerData("Mariano Ponce", 14.6609, 120.9915),
                new MarkerData("Reparo Road", 14.6650, 120.9913),
                new MarkerData("Zapote", 14.6622, 120.9938)
        );

        String cleanResidentStreet = currentResidentStreet.toLowerCase().trim();
        for (MarkerData md : streetMarkers) {
            String target = md.name.toLowerCase();
            if (cleanResidentStreet.contains(target) || target.contains(cleanResidentStreet)) {
                return new GeoPoint(md.lat, md.lng);
            }
        }
        return new GeoPoint(14.6612, 120.9908);
    }

    private String resolveResidentStreetLabel() {
        List<String> streets = Arrays.asList(
                "Adelfa",
                "General Tinio",
                "General Concepcion",
                "Mariano Ponce",
                "Reparo Road",
                "Zapote"
        );
        String cleanResidentStreet = currentResidentStreet.toLowerCase().trim();
        for (String s : streets) {
            String target = s.toLowerCase();
            if (cleanResidentStreet.contains(target) || target.contains(cleanResidentStreet)) {
                return s;
            }
        }
        return currentResidentStreet;
    }

    private void pinMyCurrentLocation() {
        GeoPoint myPoint = resolveResidentStreetPoint();
        String myLabel = resolveResidentStreetLabel();
        saveCustomPin(myPoint.getLatitude(), myPoint.getLongitude(), myLabel);
        refreshAllDashboardData();

        if (residentMap != null) {
            residentMap.getController().animateTo(myPoint);
            residentMap.getController().setZoom(18.0);
        }
        if (scrollHomeTab != null) {
            scrollHomeTab.smoothScrollTo(0, 0);
        }
        Toast.makeText(this, "Pinned your location: " + myLabel, Toast.LENGTH_SHORT).show();
    }

    private void loadResidentData() {
        Cursor c = dbHelper.getResidentByEmail(currentEmail);
        if (c != null && c.moveToFirst()) {
            try {
                currentResidentId = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RESIDENT_ID));
                currentResidentName = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RES_FULL_NAME));
                String street = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_STREET));
                if (street != null && !street.trim().isEmpty()) {
                    currentResidentStreet = street.trim();
                }
                currentContact = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CONTACT_NO));
                currentPassword = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_PASSWORD));
                int total = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_MEMBERS));
                int seniors = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_SENIORS_COUNT));
                int pwd = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_PWD_COUNT));
                int infants = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_INFANTS_COUNT));
                currentRoomId = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RES_ROOM_ID));
                String photoUri = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_FAMILY_PHOTO));

                String resCode = String.format("B132-2026-%04d", currentResidentId);
                GeoPoint customPin = getSavedCustomPin();
                String locationDisplay = (customPin != null) ? getSavedCustomPinLabel() : currentResidentStreet;

                TextView tvName = findViewById(R.id.tvResidentName);
                if (tvName != null) tvName.setText(currentResidentName);

                TextView tvSub = findViewById(R.id.tvResidentSubInfo);
                if (tvSub != null) {
                    tvSub.setText(locationDisplay + " • ID: " + resCode
                            + (currentRoomId > 0 ? " • Room #" + currentRoomId : " • At Home"));
                }

                TextView tvRoute = findViewById(R.id.tvMapRouteStatus);
                if (tvRoute != null) {
                    tvRoute.setText(locationDisplay + " Route Clear");
                }

                TextView chipM = findViewById(R.id.chipMembers);
                if (chipM != null) chipM.setText("Members: " + total);

                TextView chipS = findViewById(R.id.chipSeniors);
                if (chipS != null) chipS.setText("Senior: " + seniors);

                TextView chipP = findViewById(R.id.chipPwd);
                if (chipP != null) chipP.setText("PWD: " + pwd);

                TextView chipI = findViewById(R.id.chipInfants);
                if (chipI != null) chipI.setText("Infant: " + infants);

                if (photoUri != null && !photoUri.isEmpty() && !photoUri.contains("content://me")) {
                    selectedPhotoUri = photoUri;
                }
                applyAvatarImage(ivHomeAvatar, selectedPhotoUri);
                applyAvatarImage(ivProfilePreview, selectedPhotoUri);
            } catch (Exception ignored) {
            } finally {
                c.close();
            }
        }
    }

    private void loadEvacuationRoomsAndMap() {
        LinearLayout roomsContainer = findViewById(R.id.containerShelters);
        if (roomsContainer != null) {
            roomsContainer.removeAllViews();

            Button btnPinLocation = new Button(this);
            LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pinLp.setMargins(0, 0, 0, 20);
            btnPinLocation.setLayoutParams(pinLp);
            btnPinLocation.setText("Pin My Location");
            btnPinLocation.setAllCaps(false);
            btnPinLocation.setTextSize(13f);
            btnPinLocation.setTypeface(null, android.graphics.Typeface.BOLD);
            btnPinLocation.setTextColor(Color.WHITE);
            btnPinLocation.setPadding(24, 22, 24, 22);

            GradientDrawable pinBg = new GradientDrawable();
            pinBg.setColor(Color.parseColor("#138A4C"));
            pinBg.setCornerRadius(28f);
            btnPinLocation.setBackground(pinBg);

            btnPinLocation.setOnClickListener(v -> pinMyCurrentLocation());
            roomsContainer.addView(btnPinLocation);
        }

        if (residentMap != null) {
            residentMap.getOverlays().clear();
        }

        GeoPoint customPin = getSavedCustomPin();
        GeoPoint residentGeoPoint = (customPin != null) ? customPin : resolveResidentStreetPoint();
        String matchedStreetName = (customPin != null) ? getSavedCustomPinLabel() : resolveResidentStreetLabel();
        double residentLat = residentGeoPoint.getLatitude();
        double residentLng = residentGeoPoint.getLongitude();

        if (residentMap != null) {
            Marker residentMarker = new Marker(residentMap);
            residentMarker.setPosition(residentGeoPoint);
            residentMarker.setTitle("My Location (" + matchedStreetName + ")");
            residentMarker.setSnippet("Resident Family Pin");
            residentMap.getOverlays().add(residentMarker);
        }

        Cursor cr = dbHelper.getAllEvacuationRooms();
        GeoPoint closestShelterPoint = null;
        double minDistance = Double.MAX_VALUE;

        if (cr != null && cr.moveToFirst()) {
            do {
                try {
                    int roomId = cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_ID));
                    String center = cr.getString(cr.getColumnIndexOrThrow(DatabaseHelper.COL_CENTER_NAME));
                    String room = cr.getString(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_NAME));
                    int cap = cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_CAPACITY));
                    int limit = cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_LIMIT));
                    String status = cr.getString(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_STATUS));
                    double lat = cr.getDouble(cr.getColumnIndexOrThrow(DatabaseHelper.COL_LAT));
                    double lng = cr.getDouble(cr.getColumnIndexOrThrow(DatabaseHelper.COL_LNG));

                    GeoPoint pt = new GeoPoint(lat, lng);
                    if (residentMap != null) {
                        Marker m = new Marker(residentMap);
                        m.setPosition(pt);
                        m.setTitle(center + " — " + room);
                        m.setSnippet(cap + " / " + limit + " occupied • " + Math.max(0, limit - cap) + " slots free (" + status + ")");
                        residentMap.getOverlays().add(m);
                    }

                    if (!"FULL".equalsIgnoreCase(status)) {
                        double dist = calculateDistance(residentLat, residentLng, lat, lng);
                        if (dist < minDistance) {
                            minDistance = dist;
                            closestShelterPoint = pt;
                        }
                    }

                    if (roomsContainer != null) {
                        CardView card = new CardView(this);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.setMargins(0, 0, 0, 18);
                        card.setLayoutParams(lp);
                        card.setRadius(28f);
                        card.setCardElevation(2f);
                        card.setCardBackgroundColor(Color.WHITE);

                        LinearLayout box = new LinearLayout(this);
                        box.setOrientation(LinearLayout.VERTICAL);
                        box.setPadding(30, 24, 30, 24);

                        LinearLayout top = new LinearLayout(this);
                        top.setOrientation(LinearLayout.HORIZONTAL);
                        top.setGravity(Gravity.CENTER_VERTICAL);

                        TextView tvCenter = new TextView(this);
                        tvCenter.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                        tvCenter.setText(center + " — " + room);
                        tvCenter.setTextColor(Color.parseColor("#111827"));
                        tvCenter.setTextSize(13f);
                        tvCenter.setTypeface(null, android.graphics.Typeface.BOLD);

                        TextView tvStatus = new TextView(this);
                        tvStatus.setText("FULL".equals(status) ? "FULL" : ("NEAR FULL".equals(status) ? "NEAR FULL" : "AVAILABLE"));
                        tvStatus.setTextSize(10f);
                        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
                        tvStatus.setPadding(20, 8, 20, 8);
                        tvStatus.setTextColor(Color.WHITE);

                        GradientDrawable badgeBg = new GradientDrawable();
                        badgeBg.setCornerRadius(999f);
                        badgeBg.setColor("FULL".equals(status) ? Color.parseColor("#DC2626")
                                : ("NEAR FULL".equals(status) ? Color.parseColor("#D97706") : Color.parseColor("#138A4C")));
                        tvStatus.setBackground(badgeBg);

                        top.addView(tvCenter);
                        top.addView(tvStatus);

                        TextView tvSub = new TextView(this);
                        tvSub.setText("Room #" + roomId + "  •  Occupancy: " + cap + " / " + limit
                                + "  •  " + Math.max(0, limit - cap) + " slots free");
                        tvSub.setTextColor(Color.parseColor("#4B5563"));
                        tvSub.setTextSize(11f);
                        tvSub.setPadding(0, 6, 0, 0);

                        box.addView(top);
                        box.addView(tvSub);
                        card.addView(box);
                        card.setOnClickListener(v -> {
                            if (residentMap != null) {
                                residentMap.getController().animateTo(pt);
                                residentMap.getController().setZoom(18.0);
                            }
                        });
                        roomsContainer.addView(card);
                    }
                } catch (Exception ignored) {
                }
            } while (cr.moveToNext());
            cr.close();
        }

        if (closestShelterPoint != null && residentMap != null) {
            Polyline routeLine = new Polyline();
            List<GeoPoint> routePoints = new ArrayList<>();
            routePoints.add(residentGeoPoint);
            routePoints.add(closestShelterPoint);
            routeLine.setPoints(routePoints);
            routeLine.setColor(Color.parseColor("#10B981"));
            routeLine.setWidth(10f);
            residentMap.getOverlays().add(routeLine);
        }

        if (residentMap != null) {
            residentMap.invalidate();
        }
    }

    private static class MarkerData {
        String name;
        double lat;
        double lng;

        MarkerData(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private double calculateDistance(double startLat, double startLng, double endLat, double endLng) {
        double earthRadius = 6371;
        double dLat = Math.toRadians(endLat - startLat);
        double dLng = Math.toRadians(endLng - startLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadius * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private void loadResidentTransparencyCards() {
        LinearLayout container = findViewById(R.id.containerResTransparency);
        if (container == null) return;
        container.removeAllViews();

        String resCode = String.format("B132-2026-%04d", currentResidentId);

        CardView recordsCard = new CardView(this);
        LinearLayout.LayoutParams recordsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        recordsLp.setMargins(0, 8, 0, 24);
        recordsCard.setLayoutParams(recordsLp);
        recordsCard.setRadius(32f);
        recordsCard.setCardElevation(3f);
        recordsCard.setCardBackgroundColor(Color.WHITE);

        LinearLayout recordsBox = new LinearLayout(this);
        recordsBox.setOrientation(LinearLayout.VERTICAL);
        recordsBox.setPadding(34, 30, 34, 30);

        LinearLayout recHeaderRow = new LinearLayout(this);
        recHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        recHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        recHeaderRow.setPadding(0, 0, 0, 18);

        TextView tvRecHeader = new TextView(this);
        tvRecHeader.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvRecHeader.setText("My Family Relief Records");
        tvRecHeader.setTextColor(Color.parseColor("#111827"));
        tvRecHeader.setTextSize(15f);
        tvRecHeader.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvPassTag = new TextView(this);
        tvPassTag.setText(resCode);
        tvPassTag.setTextColor(Color.parseColor("#138A4C"));
        tvPassTag.setTextSize(11f);
        tvPassTag.setTypeface(null, android.graphics.Typeface.BOLD);

        recHeaderRow.addView(tvRecHeader);
        recHeaderRow.addView(tvPassTag);
        recordsBox.addView(recHeaderRow);

        Cursor cr = dbHelper.getReliefStatusForResident(currentResidentId);
        if (cr != null && cr.moveToFirst()) {
            do {
                try {
                    int itemId = cr.getInt(0);
                    String pkgName = cr.getString(1);
                    String custodian = cr.getString(3);
                    String dateClaimed = cr.getString(4);
                    String claimStatus = cr.getString(5);
                    boolean isClaimed = "CLAIMED".equalsIgnoreCase(claimStatus);

                    LinearLayout itemBox = new LinearLayout(this);
                    itemBox.setOrientation(LinearLayout.VERTICAL);
                    itemBox.setPadding(28, 22, 28, 22);
                    LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    itemLp.setMargins(0, 0, 0, 14);
                    itemBox.setLayoutParams(itemLp);
                    itemBox.setBackground(makeOutlinedBoxDrawable(Color.parseColor("#F9FAFB"), Color.parseColor("#E5E7EB"), 24f));

                    LinearLayout titleRow = new LinearLayout(this);
                    titleRow.setOrientation(LinearLayout.HORIZONTAL);
                    titleRow.setGravity(Gravity.CENTER_VERTICAL);

                    TextView tvPkgTitle = new TextView(this);
                    tvPkgTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    tvPkgTitle.setText("Typhoon Assistance Pack #" + itemId);
                    tvPkgTitle.setTextColor(Color.parseColor("#111827"));
                    tvPkgTitle.setTextSize(13.5f);
                    tvPkgTitle.setTypeface(null, android.graphics.Typeface.BOLD);

                    TextView tvStatusText = new TextView(this);
                    tvStatusText.setText(isClaimed ? "CLAIMED" : "ELIGIBLE");
                    tvStatusText.setTextColor(isClaimed ? Color.parseColor("#10B981") : Color.parseColor("#D97706"));
                    tvStatusText.setTextSize(11f);
                    tvStatusText.setTypeface(null, android.graphics.Typeface.BOLD);

                    titleRow.addView(tvPkgTitle);
                    titleRow.addView(tvStatusText);

                    TextView tvContents = new TextView(this);
                    tvContents.setText(pkgName);
                    tvContents.setTextColor(Color.parseColor("#4B5563"));
                    tvContents.setTextSize(12f);
                    tvContents.setPadding(0, 6, 0, 6);

                    TextView tvTimestamp = new TextView(this);
                    tvTimestamp.setText(isClaimed
                            ? "Claimed on " + dateClaimed + " • Verified by " + custodian
                            : "Ready for pickup at Barangay 132 Hall (Custodian: " + custodian + ")");
                    tvTimestamp.setTextColor(Color.parseColor("#6B7280"));
                    tvTimestamp.setTextSize(11f);

                    itemBox.addView(titleRow);
                    itemBox.addView(tvContents);
                    itemBox.addView(tvTimestamp);
                    recordsBox.addView(itemBox);
                } catch (Exception ignored) {
                }
            } while (cr.moveToNext());
            cr.close();
        }

        recordsCard.addView(recordsBox);
        container.addView(recordsCard);

        CardView stockCard = new CardView(this);
        LinearLayout.LayoutParams stockLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stockLp.setMargins(0, 0, 0, 24);
        stockCard.setLayoutParams(stockLp);
        stockCard.setRadius(32f);
        stockCard.setCardElevation(3f);
        stockCard.setCardBackgroundColor(Color.WHITE);

        LinearLayout stockBox = new LinearLayout(this);
        stockBox.setOrientation(LinearLayout.VERTICAL);
        stockBox.setPadding(34, 30, 34, 30);

        TextView tvTransHeader = new TextView(this);
        tvTransHeader.setText("Barangay Stockpile Transparency");
        tvTransHeader.setTextColor(Color.parseColor("#111827"));
        tvTransHeader.setTextSize(15f);
        tvTransHeader.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvTransSub = new TextView(this);
        tvTransSub.setText("Remaining emergency goods in Barangay 132 stockpile.");
        tvTransSub.setTextColor(Color.parseColor("#6B7280"));
        tvTransSub.setTextSize(12f);
        tvTransSub.setPadding(0, 4, 0, 20);

        stockBox.addView(tvTransHeader);
        stockBox.addView(tvTransSub);

        int[] barColors = {
                Color.parseColor("#10B981"),
                Color.parseColor("#3B82F6"),
                Color.parseColor("#F59E0B")
        };
        String[] shortLabels = {
                "Family Food Packs",
                "Hygiene & Sanitary Kits",
                "Infant & Senior Care"
        };

        Cursor ci = dbHelper.getAllInventoryWithOfficial();
        if (ci != null && ci.moveToFirst()) {
            LinearLayout currentRow = null;
            int idx = 0;
            do {
                try {
                    int rem = ci.getInt(2);
                    String label = idx < shortLabels.length ? shortLabels[idx] : ci.getString(1);
                    int barColor = barColors[idx % barColors.length];

                    if (idx % 2 == 0) {
                        currentRow = new LinearLayout(this);
                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                        currentRow.setWeightSum(2f);
                        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        rowLp.setMargins(0, 0, 0, 16);
                        currentRow.setLayoutParams(rowLp);
                        stockBox.addView(currentRow);
                    }

                    LinearLayout tile = new LinearLayout(this);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setPadding(26, 22, 26, 22);
                    LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    if (idx % 2 == 0) tileLp.setMargins(0, 0, 8, 0);
                    else tileLp.setMargins(8, 0, 0, 0);
                    tile.setLayoutParams(tileLp);
                    tile.setBackground(makeOutlinedBoxDrawable(Color.parseColor("#FAFAFA"), Color.parseColor("#E5E7EB"), 24f));

                    TextView tvTileLabel = new TextView(this);
                    tvTileLabel.setText(label);
                    tvTileLabel.setTextColor(Color.parseColor("#6B7280"));
                    tvTileLabel.setTextSize(11f);

                    LinearLayout numRow = new LinearLayout(this);
                    numRow.setOrientation(LinearLayout.HORIZONTAL);
                    numRow.setGravity(Gravity.BOTTOM);
                    numRow.setPadding(0, 8, 0, 12);

                    TextView tvBigNum = new TextView(this);
                    tvBigNum.setText(String.format("%,d", rem));
                    tvBigNum.setTextColor(Color.parseColor("#111827"));
                    tvBigNum.setTextSize(21f);
                    tvBigNum.setTypeface(null, android.graphics.Typeface.BOLD);

                    TextView tvLeftUnit = new TextView(this);
                    tvLeftUnit.setText("  left");
                    tvLeftUnit.setTextColor(Color.parseColor("#4B5563"));
                    tvLeftUnit.setTextSize(12f);
                    tvLeftUnit.setTypeface(null, android.graphics.Typeface.BOLD);

                    numRow.addView(tvBigNum);
                    numRow.addView(tvLeftUnit);

                    ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                    bar.setMax(DatabaseHelper.MAX_STOCK_LIMIT);
                    bar.setProgress(Math.min(DatabaseHelper.MAX_STOCK_LIMIT, rem));
                    bar.setProgressTintList(ColorStateList.valueOf(barColor));

                    tile.addView(tvTileLabel);
                    tile.addView(numRow);
                    tile.addView(bar);

                    if (currentRow != null) {
                        currentRow.addView(tile);
                    }
                    idx++;
                } catch (Exception ignored) {
                }
            } while (ci.moveToNext());
            ci.close();
        }

        stockCard.addView(stockBox);
        container.addView(stockCard);
    }

    private GradientDrawable makeOutlinedBoxDrawable(int fillColor, int strokeColor, float radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setStroke(2, strokeColor);
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    private void showQrPassDialog() {
        String ticketId = String.format("B132-2026-%04d", currentResidentId);
        Bitmap qrBitmap = RealQrEncoder.encodeVersion1ToBitmap(ticketId, 520);

        LinearLayout modalBox = new LinearLayout(this);
        modalBox.setOrientation(LinearLayout.VERTICAL);
        modalBox.setGravity(Gravity.CENTER_HORIZONTAL);
        modalBox.setPadding(48, 36, 48, 12);
        modalBox.setBackgroundColor(Color.WHITE);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Family Evacuation & Relief Pass");
        tvTitle.setTextColor(Color.parseColor("#111827"));
        tvTitle.setTextSize(19f);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 18);

        TextView tvName = new TextView(this);
        tvName.setText(currentResidentName);
        tvName.setTextColor(Color.parseColor("#138A4C"));
        tvName.setTextSize(15f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setGravity(Gravity.CENTER);

        TextView tvTicket = new TextView(this);
        tvTicket.setText("ID: " + ticketId);
        tvTicket.setTextColor(Color.parseColor("#138A4C"));
        tvTicket.setTextSize(15f);
        tvTicket.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTicket.setGravity(Gravity.CENTER);
        tvTicket.setPadding(0, 2, 0, 28);

        ImageView ivQr = new ImageView(this);
        ivQr.setLayoutParams(new LinearLayout.LayoutParams(480, 480));
        ivQr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivQr.setImageBitmap(qrBitmap);

        TextView tvHint = new TextView(this);
        tvHint.setText("Present this QR ticket at Barangay 132 shelters or ayuda distribution booths.");
        tvHint.setTextColor(Color.parseColor("#6B7280"));
        tvHint.setTextSize(13f);
        tvHint.setGravity(Gravity.CENTER);
        tvHint.setPadding(16, 28, 16, 12);

        modalBox.addView(tvTitle);
        modalBox.addView(tvName);
        modalBox.addView(tvTicket);
        modalBox.addView(ivQr);
        modalBox.addView(tvHint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(modalBox)
                .setPositiveButton("CLOSE", null)
                .create();
        dialog.show();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#3B5998"));
        }
    }

    private static class RealQrEncoder {
        private static final int SIZE = 21;
        private static final int DATA_CODEWORDS = 19;
        private static final int ECC_CODEWORDS = 7;

        static Bitmap encodeVersion1ToBitmap(String text, int targetPx) {
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            if (utf8.length > 17) {
                byte[] trimmed = new byte[17];
                System.arraycopy(utf8, 0, trimmed, 0, 17);
                utf8 = trimmed;
            }

            int[] data = new int[DATA_CODEWORDS];
            int bitPos = 0;
            bitPos = appendBits(data, bitPos, 0b0100, 4);
            bitPos = appendBits(data, bitPos, utf8.length, 8);
            for (byte b : utf8) {
                bitPos = appendBits(data, bitPos, b & 0xFF, 8);
            }
            bitPos = appendBits(data, bitPos, 0, Math.min(4, DATA_CODEWORDS * 8 - bitPos));
            while (bitPos % 8 != 0) {
                bitPos = appendBits(data, bitPos, 0, 1);
            }
            int byteIdx = bitPos / 8;
            int[] padBytes = {0xEC, 0x11};
            int padIdx = 0;
            while (byteIdx < DATA_CODEWORDS) {
                data[byteIdx++] = padBytes[padIdx % 2];
                padIdx++;
            }

            int[] ecc = computeReedSolomon(data, ECC_CODEWORDS);
            int[] allCodewords = new int[DATA_CODEWORDS + ECC_CODEWORDS];
            System.arraycopy(data, 0, allCodewords, 0, DATA_CODEWORDS);
            System.arraycopy(ecc, 0, allCodewords, DATA_CODEWORDS, ECC_CODEWORDS);

            boolean[][] dark = new boolean[SIZE][SIZE];
            boolean[][] isFunc = new boolean[SIZE][SIZE];

            drawFinder(dark, isFunc, 0, 0);
            drawFinder(dark, isFunc, SIZE - 7, 0);
            drawFinder(dark, isFunc, 0, SIZE - 7);

            for (int i = 8; i < SIZE - 8; i++) {
                boolean bit = (i % 2 == 0);
                dark[6][i] = bit;
                isFunc[6][i] = true;
                dark[i][6] = bit;
                isFunc[i][6] = true;
            }

            dark[13][8] = true;
            isFunc[13][8] = true;

            for (int i = 0; i <= 8; i++) {
                if (i != 6) {
                    isFunc[8][i] = true;
                    isFunc[i][8] = true;
                }
            }
            for (int i = 0; i < 8; i++) {
                isFunc[8][SIZE - 1 - i] = true;
                if (i < 7) isFunc[SIZE - 1 - i][8] = true;
            }

            int totalBits = allCodewords.length * 8;
            int bitCursor = 0;
            boolean upward = true;
            for (int right = SIZE - 1; right >= 1; right -= 2) {
                if (right == 6) right = 5;
                for (int vert = 0; vert < SIZE; vert++) {
                    int r = upward ? (SIZE - 1 - vert) : vert;
                    for (int dx = 0; dx < 2; dx++) {
                        int c = right - dx;
                        if (!isFunc[r][c]) {
                            boolean bit = false;
                            if (bitCursor < totalBits) {
                                int cw = allCodewords[bitCursor / 8];
                                bit = ((cw >>> (7 - (bitCursor % 8))) & 1) == 1;
                                bitCursor++;
                            }
                            if ((r + c) % 2 == 0) {
                                bit = !bit;
                            }
                            dark[r][c] = bit;
                        }
                    }
                }
                upward = !upward;
            }

            int fmt = 0x77C4;
            for (int i = 0; i <= 5; i++) dark[8][i] = ((fmt >>> (14 - i)) & 1) == 1;
            dark[8][7] = ((fmt >>> 8) & 1) == 1;
            dark[8][8] = ((fmt >>> 7) & 1) == 1;
            dark[7][8] = ((fmt >>> 6) & 1) == 1;
            for (int i = 9; i < 15; i++) dark[14 - i][8] = ((fmt >>> (14 - i)) & 1) == 1;

            for (int i = 0; i < 8; i++) dark[8][SIZE - 1 - i] = ((fmt >>> i) & 1) == 1;
            for (int i = 8; i < 15; i++) dark[SIZE - 15 + i][8] = ((fmt >>> i) & 1) == 1;

            int quiet = 2;
            int totalModules = SIZE + quiet * 2;
            int scale = Math.max(8, targetPx / totalModules);
            int px = scale * totalModules;

            Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            int[] blackRow = new int[scale];
            for (int i = 0; i < scale; i++) blackRow[i] = Color.parseColor("#111827");

            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    if (dark[r][c]) {
                        int x0 = (c + quiet) * scale;
                        int y0 = (r + quiet) * scale;
                        for (int py = 0; py < scale; py++) {
                            bmp.setPixels(blackRow, 0, scale, x0, y0 + py, scale, 1);
                        }
                    }
                }
            }
            return bmp;
        }

        private static int appendBits(int[] buf, int bitPos, int val, int count) {
            for (int i = count - 1; i >= 0; i--) {
                int bit = (val >>> i) & 1;
                if (bit == 1) {
                    buf[bitPos / 8] |= (1 << (7 - (bitPos % 8)));
                }
                bitPos++;
            }
            return bitPos;
        }

        private static void drawFinder(boolean[][] dark, boolean[][] isFunc, int r0, int c0) {
            for (int r = r0 - 1; r <= r0 + 7; r++) {
                for (int c = c0 - 1; c <= c0 + 7; c++) {
                    if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) continue;
                    int dr = r - r0;
                    int dc = c - c0;
                    boolean inBox = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
                    dark[r][c] = inBox && (dr == 0 || dr == 6 || dc == 0 || dc == 6
                            || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4));
                    isFunc[r][c] = true;
                }
            }
        }

        private static int[] computeReedSolomon(int[] data, int eccLen) {
            int[] gen = new int[eccLen + 1];
            gen[0] = 1;
            int root = 1;
            for (int i = 0; i < eccLen; i++) {
                for (int j = eccLen; j >= 1; j--) {
                    gen[j] = gfMul(gen[j], root) ^ gen[j - 1];
                }
                gen[0] = gfMul(gen[0], root);
                root = gfMul(root, 0x02);
            }

            int[] rem = new int[eccLen];
            int bVal;
            for (int b : data) {
                bVal = b ^ rem[0];
                System.arraycopy(rem, 1, rem, 0, eccLen - 1);
                rem[eccLen - 1] = 0;
                for (int j = 0; j < eccLen; j++) {
                    rem[j] ^= gfMul(gen[eccLen - 1 - j], bVal);
                }
            }
            return rem;
        }

        private static int gfMul(int x, int y) {
            int z = 0;
            for (int i = 7; i >= 0; i--) {
                z = (z << 1) ^ ((z >>> 7) * 0x11D);
                z ^= ((y >>> i) & 1) * x;
            }
            return z;
        }
    }

    private void setupListeners() {
        View navH = findViewById(R.id.navHome);
        if (navH != null) navH.setOnClickListener(v -> showTab(0));

        View navQ = findViewById(R.id.navQr);
        if (navQ != null) navQ.setOnClickListener(v -> showQrPassDialog());

        View navP = findViewById(R.id.navProfile);
        if (navP != null) navP.setOnClickListener(v -> showTab(1));

        if (btnProfileDone != null) {
            btnProfileDone.setOnClickListener(v -> showTab(0));
        }

        View openQr = findViewById(R.id.btnOpenQrModal);
        if (openQr != null) openQr.setOnClickListener(v -> showQrPassDialog());

        View centerMap = findViewById(R.id.btnCenterMap);
        if (centerMap != null) {
            centerMap.setOnClickListener(v -> {
                if (residentMap != null) {
                    GeoPoint savedPin = getSavedCustomPin();
                    residentMap.getController().animateTo(savedPin != null ? savedPin : resolveResidentStreetPoint());
                    residentMap.getController().setZoom(17.5);
                }
            });
        }

        View hotline = findViewById(R.id.btnHotline);
        if (hotline != null) {
            hotline.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Barangay 132 Emergency Hotlines")
                    .setMessage("• Barangay 132 Action Center: (02) 8364-1320\n• Caloocan DRRMO Rescue: 911 / (02) 8288-8811\n• BFP Caloocan Fire Station: (02) 8310-6527")
                    .setPositiveButton("OK", null)
                    .show());
        }

        View choosePhoto = findViewById(R.id.btnChoosePhoto);
        if (choosePhoto != null) {
            choosePhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        }

        TextView btnTogglePass = findViewById(R.id.btnToggleResPassword);
        EditText etPass = findViewById(R.id.etEditPassword);
        if (btnTogglePass != null && etPass != null) {
            btnTogglePass.setOnClickListener(v -> {
                isPasswordVisible = !isPasswordVisible;
                if (isPasswordVisible) {
                    etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnTogglePass.setText("Hide");
                } else {
                    etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnTogglePass.setText("Show");
                }
                etPass.setSelection(etPass.getText().length());
            });
        }

        View saveProfile = findViewById(R.id.btnSaveProfile);
        if (saveProfile != null) {
            saveProfile.setOnClickListener(v -> {
                EditText etName = findViewById(R.id.etEditFullName);
                EditText etPwd = findViewById(R.id.etEditPassword);
                EditText etSt = findViewById(R.id.etEditStreet);
                EditText etMem = findViewById(R.id.etEditMembers);
                EditText etSen = findViewById(R.id.etEditSeniors);
                EditText etPwdF = findViewById(R.id.etEditPwd);
                EditText etInf = findViewById(R.id.etEditInfants);

                String fullName = etName != null ? etName.getText().toString().trim() : "";
                if (fullName.isEmpty()) fullName = currentResidentName;

                String enteredPwd = etPwd != null ? etPwd.getText().toString().trim() : "";
                String passwordToSave = enteredPwd.isEmpty() ? null : enteredPwd;

                String street = etSt != null ? etSt.getText().toString().trim() : "";
                String cleanStreet = street.isEmpty() ? currentResidentStreet : street;
                int total = etMem != null ? parseIntSafe(etMem.getText().toString(), 1) : 1;
                int seniors = etSen != null ? parseIntSafe(etSen.getText().toString(), 0) : 0;
                int pwd = etPwdF != null ? parseIntSafe(etPwdF.getText().toString(), 0) : 0;
                int infants = etInf != null ? parseIntSafe(etInf.getText().toString(), 0) : 0;

                boolean updated = dbHelper.updateResidentProfile(
                        currentEmail, fullName, cleanStreet,
                        total, seniors, pwd, infants, selectedPhotoUri, passwordToSave);
                if (updated) {
                    currentResidentName = fullName;
                    currentResidentStreet = cleanStreet;
                    if (passwordToSave != null) {
                        currentPassword = passwordToSave;
                    }
                    GeoPoint updatedPoint = resolveResidentStreetPoint();
                    String updatedLabel = resolveResidentStreetLabel();
                    saveCustomPin(updatedPoint.getLatitude(), updatedPoint.getLongitude(), updatedLabel);
                    cloudSyncManager.syncResidentToCloud(
                            currentEmail,
                            fullName,
                            fullName,
                            cleanStreet,
                            currentContact,
                            currentPassword,
                            "",
                            "",
                            total,
                            seniors,
                            pwd,
                            infants,
                            currentRoomId
                    );
                }
                Toast.makeText(this, updated ? "Profile & photo saved!" : "Could not save profile.", Toast.LENGTH_SHORT).show();
                refreshAllDashboardData();
                showTab(0);
            });
        }

        View logout = findViewById(R.id.btnResidentLogout);
        if (logout != null) {
            logout.setOnClickListener(v -> {
                startActivity(new Intent(ResidentDashboardActivity.this, MainActivity.class));
                finish();
            });
        }
    }

    private void showTab(int index) {
        boolean isProfile = (index == 1);
        if (scrollHomeTab != null) scrollHomeTab.setVisibility(isProfile ? View.GONE : View.VISIBLE);
        if (scrollProfileTab != null) scrollProfileTab.setVisibility(isProfile ? View.VISIBLE : View.GONE);
        if (btnProfileDone != null) btnProfileDone.setVisibility(isProfile ? View.VISIBLE : View.GONE);

        View logoutBtn = findViewById(R.id.btnResidentLogout);
        if (logoutBtn != null) logoutBtn.setVisibility(isProfile ? View.GONE : View.VISIBLE);

        int active = Color.parseColor("#138A4C");
        int inactive = Color.parseColor("#9CA3AF");
        if (ivNavHome != null) ivNavHome.setColorFilter(isProfile ? inactive : active);
        if (tvNavHome != null) tvNavHome.setTextColor(isProfile ? inactive : active);
        if (ivNavProfile != null) ivNavProfile.setColorFilter(isProfile ? active : inactive);
        if (tvNavProfile != null) tvNavProfile.setTextColor(isProfile ? active : inactive);

        if (isProfile) {
            applyAvatarImage(ivProfilePreview, selectedPhotoUri);
            Cursor c = dbHelper.getResidentByEmail(currentEmail);
            if (c != null && c.moveToFirst()) {
                try {
                    EditText etName = findViewById(R.id.etEditFullName);
                    EditText etPwd = findViewById(R.id.etEditPassword);
                    EditText etSt = findViewById(R.id.etEditStreet);
                    EditText etMem = findViewById(R.id.etEditMembers);
                    EditText etSen = findViewById(R.id.etEditSeniors);
                    EditText etPwdF = findViewById(R.id.etEditPwd);
                    EditText etInf = findViewById(R.id.etEditInfants);

                    if (etName != null) etName.setText(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RES_FULL_NAME)));
                    if (etPwd != null) etPwd.setText(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_PASSWORD)));
                    if (etSt != null) etSt.setText(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_STREET)));
                    if (etMem != null) etMem.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_MEMBERS))));
                    if (etSen != null) etSen.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_SENIORS_COUNT))));
                    if (etPwdF != null) etPwdF.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_PWD_COUNT))));
                    if (etInf != null) etInf.setText(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_INFANTS_COUNT))));
                } catch (Exception ignored) {
                } finally {
                    c.close();
                }
            }
        }
    }

    private int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (residentMap != null) residentMap.onResume();
        try {
            ContextCompat.registerReceiver(
                    this,
                    instantDataReceiver,
                    new IntentFilter(DatabaseHelper.ACTION_DATA_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
        } catch (Exception ignored) {
        }
        liveSyncHandler.removeCallbacks(liveSyncRunnable);
        liveSyncHandler.post(liveSyncRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (residentMap != null) residentMap.onPause();
        liveSyncHandler.removeCallbacks(liveSyncRunnable);
        try {
            unregisterReceiver(instantDataReceiver);
        } catch (Exception ignored) {
        }
    }
}