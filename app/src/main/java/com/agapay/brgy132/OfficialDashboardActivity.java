package com.agapay.brgy132;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class OfficialDashboardActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private CloudSyncManager cloudSyncManager;
    private MapView officialMap;
    private int currentOfficialId = 1;
    private String currentOfficialEmail = "andrewsalas@brgy132.gov.ph";
    private String currentOfficialName = "Andrew Jae Salas";
    private String activeFilter = "ALL";
    private String searchQuery = "";
    private int selectedPackagePos = 0;

    private ScrollView tabOverview, tabProfiling, tabRelief, tabInventory, tabShelters;
    private ImageView ivNavOverview, ivNavProfiling, ivNavRelief, ivNavInventory, ivNavShelters;
    private TextView tvNavOverview, tvNavProfiling, tvNavRelief, tvNavInventory, tvNavShelters;
    private Spinner spinnerPackages;
    private final List<Integer> packageIds = new ArrayList<>();

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scannedTicket = result.getContents().trim().toUpperCase();
                    EditText etTicketInput = findViewById(R.id.etClaimResidentId);
                    if (etTicketInput != null) {
                        etTicketInput.setText(scannedTicket);
                    }
                    Toast.makeText(this, "Scanned Resident Ticket: " + scannedTicket, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "QR scan cancelled.", Toast.LENGTH_SHORT).show();
                }
            });

    private final Handler liveSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveSyncRunnable = new Runnable() {
        @Override
        public void run() {
            refreshLiveViewsWithoutDisruptingInput();
            liveSyncHandler.postDelayed(this, 1500);
        }
    };

    private final BroadcastReceiver instantDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshAllTabs();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_official_dashboard);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainOfficial), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dbHelper = new DatabaseHelper(this);
        cloudSyncManager = new CloudSyncManager(this);

        String passedEmail = getIntent().getStringExtra("USER_EMAIL");
        if (passedEmail != null && !passedEmail.isEmpty()) {
            currentOfficialEmail = passedEmail;
        }

        bindViews();
        setupNetworkMonitor();
        loadOfficialHeader();
        setupOfficialMap();
        refreshAllTabs();
        setupListeners();

        cloudSyncManager.startLiveSync(() -> runOnUiThread(this::refreshLiveViewsWithoutDisruptingInput));
    }

    private void bindViews() {
        tabOverview = findViewById(R.id.tabOverview);
        tabProfiling = findViewById(R.id.tabProfiling);
        tabRelief = findViewById(R.id.tabRelief);
        tabInventory = findViewById(R.id.tabInventory);
        tabShelters = findViewById(R.id.tabShelters);

        ivNavOverview = findViewById(R.id.ivNavOffOverview);
        ivNavProfiling = findViewById(R.id.ivNavOffProfiling);
        ivNavRelief = findViewById(R.id.ivNavOffRelief);
        ivNavInventory = findViewById(R.id.ivNavOffInventory);
        ivNavShelters = findViewById(R.id.ivNavOffShelters);

        tvNavOverview = findViewById(R.id.tvNavOffOverview);
        tvNavProfiling = findViewById(R.id.tvNavOffProfiling);
        tvNavRelief = findViewById(R.id.tvNavOffRelief);
        tvNavInventory = findViewById(R.id.tvNavOffInventory);
        tvNavShelters = findViewById(R.id.tvNavOffShelters);

        spinnerPackages = findViewById(R.id.spinnerPackages);
        officialMap = findViewById(R.id.officialOsmMap);
    }

    private void setupNetworkMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        updateOnlineBadge(isDeviceOnline());

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> updateOnlineBadge(true));
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> updateOnlineBadge(isDeviceOnline()));
            }
        };
    }

    private boolean isDeviceOnline() {
        if (connectivityManager == null) return false;
        Network active = connectivityManager.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(active);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void updateOnlineBadge(boolean online) {
        TextView pill = findViewById(R.id.btnOnlineStatusPill);
        if (pill == null) return;
        if (online) {
            pill.setText("● Online • Live");
            pill.setTextColor(Color.WHITE);
        } else {
            pill.setText("● Offline • Live");
            pill.setTextColor(Color.parseColor("#FEF08A"));
        }
    }

    private void loadOfficialHeader() {
        Cursor c = dbHelper.getOfficialByEmail(currentOfficialEmail);
        if (c != null && c.moveToFirst()) {
            currentOfficialId = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_OFFICIAL_ID));
            currentOfficialName = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_OFF_NAME));
            String role = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_OFF_ROLE));

            ((TextView) findViewById(R.id.tvActiveCustodianBanner))
                    .setText("Custodian: " + currentOfficialName + " (" + role + " • ID #" + currentOfficialId + ")");
            c.close();
        }
    }

    private void setupOfficialMap() {
        officialMap.setTileSource(TileSourceFactory.MAPNIK);
        officialMap.setMultiTouchControls(true);
        officialMap.getController().setZoom(16.0);
        officialMap.getController().setCenter(new GeoPoint(14.6615, 120.9922));
    }

    private void refreshAllTabs() {
        if (spinnerPackages != null && spinnerPackages.getSelectedItemPosition() >= 0) {
            selectedPackagePos = spinnerPackages.getSelectedItemPosition();
        }
        loadOverviewStats();
        loadResidentsList();
        loadReliefTab();
        loadInventoryTab();
        loadRoomsAndMap();
    }

    private void refreshLiveViewsWithoutDisruptingInput() {
        if (spinnerPackages != null && spinnerPackages.getSelectedItemPosition() >= 0) {
            selectedPackagePos = spinnerPackages.getSelectedItemPosition();
        }
        loadOverviewStats();
        loadResidentsList();
        loadReliefLogsOnly();
        loadInventoryTab();
        loadRoomsAndMap();
    }

    private void pushInventoryItemToCloud(int itemId) {
        Cursor ci = dbHelper.getAllInventoryWithOfficial();
        if (ci != null && ci.moveToFirst()) {
            do {
                if (ci.getInt(0) == itemId) {
                    String pkgName = ci.getString(1);
                    int newRemaining = ci.getInt(2);
                    int newDistributed = ci.getInt(3);
                    cloudSyncManager.syncInventoryItem(itemId, pkgName, newRemaining, newDistributed);
                    break;
                }
            } while (ci.moveToNext());
            ci.close();
        }
    }

    private void loadOverviewStats() {
        int householdCount = 0, totalPeople = 0, seniorTotal = 0, pwdTotal = 0, youngTotal = 0;
        Cursor cr = dbHelper.getAllResidents();
        if (cr != null && cr.moveToFirst()) {
            do {
                householdCount++;
                totalPeople += cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_MEMBERS));
                seniorTotal += cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_SENIORS_COUNT));
                pwdTotal += cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_PWD_COUNT));
                youngTotal += cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_INFANTS_COUNT));
            } while (cr.moveToNext());
            cr.close();
        }

        int sheltered = 0;
        LinearLayout homeRooms = findViewById(R.id.containerHomeRoomsPreview);
        homeRooms.removeAllViews();

        Cursor ce = dbHelper.getAllEvacuationRooms();
        if (ce != null && ce.moveToFirst()) {
            do {
                int roomId = ce.getInt(ce.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_ID));
                String center = ce.getString(ce.getColumnIndexOrThrow(DatabaseHelper.COL_CENTER_NAME));
                String room = ce.getString(ce.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_NAME));
                int cap = ce.getInt(ce.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_CAPACITY));
                int limit = ce.getInt(ce.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_LIMIT));
                String status = ce.getString(ce.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_STATUS));
                sheltered += cap;

                CardView card = makeWhiteCard();
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(32, 26, 32, 26);

                LinearLayout left = new LinearLayout(this);
                left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                left.setOrientation(LinearLayout.VERTICAL);

                TextView t1 = new TextView(this);
                t1.setText(center + " — " + room);
                t1.setTextColor(Color.parseColor("#111827"));
                t1.setTextSize(13f);
                t1.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView t2 = new TextView(this);
                t2.setText("Room #" + roomId + " • " + cap + " / " + limit + " capacity • " + Math.max(0, limit - cap) + " slots free");
                t2.setTextColor(Color.parseColor("#6B7280"));
                t2.setTextSize(11f);

                left.addView(t1);
                left.addView(t2);

                TextView badge = new TextView(this);
                badge.setText("FULL".equals(status) ? "FULL" : ("NEAR FULL".equals(status) ? "NEAR FULL" : "AVAILABLE"));
                badge.setTextSize(10f);
                badge.setTypeface(null, android.graphics.Typeface.BOLD);
                badge.setPadding(20, 8, 20, 8);
                badge.setBackgroundResource(R.drawable.bg_badge_green);
                badge.setTextColor("FULL".equals(status) ? Color.parseColor("#FECACA")
                        : ("NEAR FULL".equals(status) ? Color.parseColor("#FEF08A") : Color.WHITE));

                row.addView(left);
                row.addView(badge);
                card.addView(row);
                card.setOnClickListener(v -> showTab(4));
                homeRooms.addView(card);
            } while (ce.moveToNext());
            ce.close();
        }

        int lowAlerts = 0, claimedLogs = 0;
        Cursor ci = dbHelper.getAllInventoryWithOfficial();
        if (ci != null && ci.moveToFirst()) {
            do {
                if (ci.getInt(2) < 100) lowAlerts++;
            } while (ci.moveToNext());
            ci.close();
        }

        Cursor cl = dbHelper.getAllReliefLogs();
        if (cl != null) {
            claimedLogs = cl.getCount();
            cl.close();
        }

        ((TextView) findViewById(R.id.tvKpiHouseholds)).setText(String.valueOf(householdCount));
        ((TextView) findViewById(R.id.tvKpiResidents)).setText(String.valueOf(totalPeople));
        ((TextView) findViewById(R.id.tvKpiSheltered)).setText(String.valueOf(sheltered));
        ((TextView) findViewById(R.id.tvKpiAlerts)).setText(String.valueOf(lowAlerts));
        ((TextView) findViewById(R.id.tvKpiAlertSub)).setText(lowAlerts == 0 ? "All supplies normal" : lowAlerts + " batch low");

        int pct = householdCount > 0 ? Math.min(100, (claimedLogs * 100) / householdCount) : 0;
        ((TextView) findViewById(R.id.tvReliefClaimRatio)).setText(claimedLogs + " / " + householdCount + " (" + pct + "%)");
        ((ProgressBar) findViewById(R.id.barFairnessGreen)).setProgress(pct);

        ((TextView) findViewById(R.id.filterAll)).setText("All (" + householdCount + ")");
        ((TextView) findViewById(R.id.filterSenior)).setText("Senior (" + seniorTotal + ")");
        ((TextView) findViewById(R.id.filterPwd)).setText("PWD (" + pwdTotal + ")");
        ((TextView) findViewById(R.id.filterYoung)).setText("Young (" + youngTotal + ")");
    }

    private void loadResidentsList() {
        LinearLayout list = findViewById(R.id.containerResidentsList);
        list.removeAllViews();

        Cursor c = dbHelper.getAllResidents();
        if (c != null && c.moveToFirst()) {
            do {
                int id = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RESIDENT_ID));
                String resCode = String.format("B132-2026-%04d", id);
                String name = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RES_FULL_NAME));
                String head = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_FAMILY_HEAD));
                String street = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_STREET));
                String contact = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CONTACT_NO));
                int members = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_MEMBERS));
                int seniors = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_SENIORS_COUNT));
                int pwd = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_PWD_COUNT));
                int infants = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_INFANTS_COUNT));
                int roomId = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RES_ROOM_ID));

                if ("SENIOR".equals(activeFilter) && seniors <= 0) continue;
                if ("PWD".equals(activeFilter) && pwd <= 0) continue;
                if ("YOUNG".equals(activeFilter) && infants <= 0) continue;
                if (!searchQuery.isEmpty()
                        && !name.toLowerCase().contains(searchQuery)
                        && !resCode.toLowerCase().contains(searchQuery)) {
                    continue;
                }

                CardView card = makeWhiteCard();
                LinearLayout box = makeCardInner();

                LinearLayout topRow = new LinearLayout(this);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvId = new TextView(this);
                tvId.setText(resCode);
                tvId.setTextSize(10f);
                tvId.setTypeface(null, android.graphics.Typeface.BOLD);
                tvId.setTextColor(Color.WHITE);
                tvId.setBackgroundResource(R.drawable.bg_badge_green);
                tvId.setPadding(18, 6, 18, 6);

                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

                TextView tvVerified = new TextView(this);
                tvVerified.setText(roomId > 0 ? "Evacuated • Room #" + roomId : "At Home");
                tvVerified.setTextSize(10f);
                tvVerified.setTypeface(null, android.graphics.Typeface.BOLD);
                tvVerified.setTextColor(Color.WHITE);
                tvVerified.setBackgroundResource(R.drawable.bg_badge_green);
                tvVerified.setPadding(18, 6, 18, 6);

                topRow.addView(tvId);
                topRow.addView(spacer);
                topRow.addView(tvVerified);

                TextView tvName = new TextView(this);
                tvName.setText(name);
                tvName.setTextColor(Color.parseColor("#111827"));
                tvName.setTextSize(15f);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                tvName.setPadding(0, 12, 0, 4);

                TextView tvSub = new TextView(this);
                tvSub.setText("📍 " + street + "  •  👥 " + members + " Residents  •  "
                        + (roomId > 0 ? "Room #" + roomId : "At Home"));
                tvSub.setTextColor(Color.parseColor("#6B7280"));
                tvSub.setTextSize(11f);

                LinearLayout bottomRow = new LinearLayout(this);
                bottomRow.setOrientation(LinearLayout.HORIZONTAL);
                bottomRow.setGravity(Gravity.CENTER_VERTICAL);
                bottomRow.setPadding(0, 12, 0, 0);

                TextView tvTags = new TextView(this);
                tvTags.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                tvTags.setText("Senior (" + seniors + ") • PWD (" + pwd + ") • Young (" + infants + ")");
                tvTags.setTextColor(Color.parseColor("#D97706"));
                tvTags.setTextSize(11f);
                tvTags.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvDetails = new TextView(this);
                tvDetails.setText("Update Shelter / Home →");
                tvDetails.setTextColor(Color.parseColor("#0E5A36"));
                tvDetails.setTextSize(11f);
                tvDetails.setTypeface(null, android.graphics.Typeface.BOLD);

                bottomRow.addView(tvTags);
                bottomRow.addView(tvDetails);

                box.addView(topRow);
                box.addView(tvName);
                box.addView(tvSub);
                box.addView(bottomRow);
                card.addView(box);

                card.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle(name + " (" + resCode + ")")
                        .setMessage("Family Head: " + head + "\nContact: " + contact
                                + "\nStreet: " + street + "\nTotal Members: " + members
                                + "\nCurrent Location: " + (roomId > 0 ? "Evacuation Room #" + roomId : "At Home (" + street + ")"))
                        .setPositiveButton("Use in Relief Scan", (d, w) -> {
                            ((EditText) findViewById(R.id.etClaimResidentId)).setText(resCode);
                            showTab(2);
                        })
                        .setNeutralButton("Update Shelter / Home", (d, w) -> showAssignRoomDialog(id, name))
                        .setNegativeButton("Close", null)
                        .show());

                list.addView(card);
            } while (c.moveToNext());
            c.close();
        }
    }

    private void loadReliefTab() {
        packageIds.clear();
        List<String> labels = new ArrayList<>();

        Cursor ci = dbHelper.getAllInventoryWithOfficial();
        if (ci != null && ci.moveToFirst()) {
            do {
                int itemId = ci.getInt(0);
                String pkg = ci.getString(1);
                int rem = ci.getInt(2);
                int custodianId = ci.getInt(4);
                String custodianName = ci.getString(5);

                packageIds.add(itemId);
                String lockTag = (custodianId == currentOfficialId)
                        ? " [YOUR CUSTODY]" : " [ASSIGNED TO: " + custodianName + "]";
                labels.add(pkg + " (" + rem + " left)" + lockTag);
            } while (ci.moveToNext());
            ci.close();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPackages.setAdapter(adapter);
        if (selectedPackagePos >= 0 && selectedPackagePos < labels.size()) {
            spinnerPackages.setSelection(selectedPackagePos);
        }

        loadReliefLogsOnly();
    }

    private void loadReliefLogsOnly() {
        LinearLayout logsBox = findViewById(R.id.containerReliefLogs);
        logsBox.removeAllViews();

        Cursor cl = dbHelper.getAllReliefLogs();
        if (cl != null && cl.moveToFirst()) {
            do {
                String resName = cl.getString(1);
                int resId = cl.getInt(2);
                String pkg = cl.getString(3);
                String offName = cl.getString(4);
                String date = cl.getString(5);
                String status = cl.getString(6);

                CardView card = makeWhiteCard();
                LinearLayout box = makeCardInner();

                TextView tv = new TextView(this);
                tv.setText(resName + " (ID #" + resId + ") — " + status + " (LOCKED)");
                tv.setTextColor(Color.parseColor("#138A4C"));
                tv.setTextSize(12f);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView sub = new TextView(this);
                sub.setText("Package: " + pkg + "\nCustodian: " + offName + "  •  " + date);
                sub.setTextColor(Color.parseColor("#4B5563"));
                sub.setTextSize(11f);
                sub.setPadding(0, 4, 0, 0);

                box.addView(tv);
                box.addView(sub);
                card.addView(box);
                logsBox.addView(card);
            } while (cl.moveToNext());
            cl.close();
        }
    }

    private void loadInventoryTab() {
        LinearLayout invBox = findViewById(R.id.containerInventoryList);
        invBox.removeAllViews();

        int[] barColors = {Color.parseColor("#10B981"), Color.parseColor("#3B82F6"), Color.parseColor("#F59E0B")};
        int idx = 0;

        Cursor ci = dbHelper.getAllInventoryWithOfficial();
        if (ci != null && ci.moveToFirst()) {
            do {
                int itemId = ci.getInt(0);
                String pkg = ci.getString(1);
                int rem = ci.getInt(2);
                int dist = ci.getInt(3);
                int custodianId = ci.getInt(4);
                String custodianName = ci.getString(5);
                boolean isMine = (custodianId == currentOfficialId);
                boolean isAtLimit = rem >= DatabaseHelper.MAX_STOCK_LIMIT;

                CardView card = makeWhiteCard();
                LinearLayout box = makeCardInner();

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvCategory = new TextView(this);
                tvCategory.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                tvCategory.setText(isMine ? "YOUR ASSIGNED BATCH" : "LOCKED • FIND " + custodianName.toUpperCase());
                tvCategory.setTextColor(isMine ? Color.parseColor("#138A4C") : Color.parseColor("#B45309"));
                tvCategory.setTextSize(11f);
                tvCategory.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvCount = new TextView(this);
                tvCount.setText(rem + " / " + DatabaseHelper.MAX_STOCK_LIMIT + " packs");
                tvCount.setTextColor(Color.parseColor("#111827"));
                tvCount.setTextSize(14f);
                tvCount.setTypeface(null, android.graphics.Typeface.BOLD);

                top.addView(tvCategory);
                top.addView(tvCount);

                TextView tvTitle = new TextView(this);
                tvTitle.setText(pkg);
                tvTitle.setTextColor(Color.parseColor("#111827"));
                tvTitle.setTextSize(14f);
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                tvTitle.setPadding(0, 6, 0, 8);

                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(DatabaseHelper.MAX_STOCK_LIMIT);
                bar.setProgress(Math.min(DatabaseHelper.MAX_STOCK_LIMIT, rem));
                bar.setProgressTintList(ColorStateList.valueOf(barColors[idx % barColors.length]));
                idx++;

                TextView tvFooter = new TextView(this);
                tvFooter.setText("✓ Custodian: " + custodianName + "  •  Distributed: " + dist
                        + (isAtLimit ? "  •  MAX LIMIT REACHED" : ""));
                tvFooter.setTextColor(isMine ? Color.parseColor("#138A4C") : Color.parseColor("#6B7280"));
                tvFooter.setTextSize(11f);
                tvFooter.setPadding(0, 8, 0, 12);

                LinearLayout actionRow = new LinearLayout(this);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);
                actionRow.setWeightSum(2);

                Button btnMinusStock = new Button(this);
                LinearLayout.LayoutParams lpMinus = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lpMinus.setMargins(0, 0, 12, 0);
                btnMinusStock.setLayoutParams(lpMinus);
                btnMinusStock.setText(isMine ? "- Stock (Deduct)" : "🔒 Locked");
                btnMinusStock.setAllCaps(false);
                btnMinusStock.setTextColor(Color.WHITE);
                btnMinusStock.setBackgroundTintList(ColorStateList.valueOf(
                        isMine ? Color.parseColor("#D97706") : Color.parseColor("#9CA3AF")));
                btnMinusStock.setOnClickListener(v -> {
                    if (!isMine) {
                        new AlertDialog.Builder(this)
                                .setTitle("Custodian Restriction")
                                .setMessage("Only the assigned official can deduct stock from this batch.\n\nOfficial to find: " + custodianName)
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    if (rem <= 0) {
                        Toast.makeText(this, "OUT OF STOCK: 0 packs remaining.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    EditText etDeductQty = new EditText(this);
                    etDeductQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    etDeductQty.setHint("Max deductible: " + rem);
                    etDeductQty.setText("1");
                    new AlertDialog.Builder(this)
                            .setTitle("Deduct Stock: " + pkg)
                            .setMessage("Current stock: " + rem + " packs\nEnter how many packs to deduct:")
                            .setView(etDeductQty)
                            .setPositiveButton("Deduct Stock", (d, w) -> {
                                int subAmt = parseIntSafe(etDeductQty.getText().toString(), 0);
                                if (subAmt <= 0) {
                                    Toast.makeText(this, "Enter a valid quantity above 0.", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                String msg = dbHelper.adjustInventoryStock(itemId, currentOfficialId, -subAmt);
                                pushInventoryItemToCloud(itemId);
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

                Button btnPlusStock = new Button(this);
                LinearLayout.LayoutParams lpPlus = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lpPlus.setMargins(12, 0, 0, 0);
                btnPlusStock.setLayoutParams(lpPlus);
                btnPlusStock.setText(!isMine ? "🔒 Locked" : (isAtLimit ? "Max Limit (1000)" : "+ Stock (Restock)"));
                btnPlusStock.setAllCaps(false);
                btnPlusStock.setTextColor(Color.WHITE);
                btnPlusStock.setBackgroundTintList(ColorStateList.valueOf(
                        (!isMine || isAtLimit) ? Color.parseColor("#9CA3AF") : Color.parseColor("#0E5A36")));
                btnPlusStock.setOnClickListener(v -> {
                    if (!isMine) {
                        new AlertDialog.Builder(this)
                                .setTitle("Custodian Restriction")
                                .setMessage("Only the assigned official can restock this batch.\n\nOfficial to find: " + custodianName)
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    if (isAtLimit) {
                        Toast.makeText(this, "LIMIT REACHED: Already at maximum " + DatabaseHelper.MAX_STOCK_LIMIT + " packs.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int availableRoom = DatabaseHelper.MAX_STOCK_LIMIT - rem;
                    EditText etQty = new EditText(this);
                    etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    etQty.setHint("Max addable: " + availableRoom);
                    etQty.setText(String.valueOf(Math.min(50, availableRoom)));
                    new AlertDialog.Builder(this)
                            .setTitle("Restock " + pkg + " (Max Limit: " + DatabaseHelper.MAX_STOCK_LIMIT + ")")
                            .setMessage("Current stock: " + rem + " / " + DatabaseHelper.MAX_STOCK_LIMIT + "\nRemaining storage capacity: " + availableRoom + " packs")
                            .setView(etQty)
                            .setPositiveButton("Add Stock", (d, w) -> {
                                int addAmt = parseIntSafe(etQty.getText().toString(), 0);
                                if (addAmt <= 0) {
                                    Toast.makeText(this, "Enter a valid quantity above 0.", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                String msg = dbHelper.adjustInventoryStock(itemId, currentOfficialId, addAmt);
                                pushInventoryItemToCloud(itemId);
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

                actionRow.addView(btnMinusStock);
                actionRow.addView(btnPlusStock);

                box.addView(top);
                box.addView(tvTitle);
                box.addView(bar);
                box.addView(tvFooter);
                box.addView(actionRow);
                card.addView(box);
                invBox.addView(card);
            } while (ci.moveToNext());
            ci.close();
        }
    }

    private void loadRoomsAndMap() {
        LinearLayout roomList = findViewById(R.id.containerOfficialRooms);
        roomList.removeAllViews();
        officialMap.getOverlays().clear();

        Cursor cr = dbHelper.getAllEvacuationRooms();
        if (cr != null && cr.moveToFirst()) {
            do {
                int roomId = cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_ID));
                String center = cr.getString(cr.getColumnIndexOrThrow(DatabaseHelper.COL_CENTER_NAME));
                String room = cr.getString(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_NAME));
                int cap = cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_CAPACITY));
                int limit = cr.getInt(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_LIMIT));
                String status = cr.getString(cr.getColumnIndexOrThrow(DatabaseHelper.COL_ROOM_STATUS));
                double lat = cr.getDouble(cr.getColumnIndexOrThrow(DatabaseHelper.COL_LAT));
                double lng = cr.getDouble(cr.getColumnIndexOrThrow(DatabaseHelper.COL_LNG));

                GeoPoint pt = new GeoPoint(lat, lng);
                Marker m = new Marker(officialMap);
                m.setPosition(pt);
                m.setTitle(center + " — " + room);
                m.setSnippet("Room #" + roomId + ": " + cap + "/" + limit + " (" + status + ")");
                officialMap.getOverlays().add(m);

                CardView card = makeWhiteCard();
                LinearLayout box = makeCardInner();

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvName = new TextView(this);
                tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                tvName.setText(center);
                tvName.setTextColor(Color.parseColor("#111827"));
                tvName.setTextSize(14f);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView tvPill = new TextView(this);
                tvPill.setText(cap + " / " + limit);
                tvPill.setTextSize(11f);
                tvPill.setTypeface(null, android.graphics.Typeface.BOLD);
                tvPill.setPadding(20, 6, 20, 6);
                tvPill.setBackgroundResource(R.drawable.bg_badge_green);
                tvPill.setTextColor("FULL".equals(status) ? Color.parseColor("#FECACA")
                        : ("NEAR FULL".equals(status) ? Color.parseColor("#FEF08A") : Color.WHITE));

                top.addView(tvName);
                top.addView(tvPill);

                TextView tvRoomSub = new TextView(this);
                tvRoomSub.setText("📍 Room #" + roomId + ": " + room + "  •  Status: " + status
                        + " (" + Math.max(0, limit - cap) + " free)");
                tvRoomSub.setTextColor(Color.parseColor("#6B7280"));
                tvRoomSub.setTextSize(11f);
                tvRoomSub.setPadding(0, 6, 0, 10);

                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);

                Button btnMinus = new Button(this);
                btnMinus.setText("-1 Evacuee");
                btnMinus.setAllCaps(false);
                btnMinus.setOnClickListener(v -> dbHelper.updateRoomOccupancyDelta(roomId, -1));

                Button btnPlus = new Button(this);
                btnPlus.setText("+1 Evacuee");
                btnPlus.setAllCaps(false);
                btnPlus.setOnClickListener(v -> {
                    boolean success = dbHelper.updateRoomOccupancyDelta(roomId, 1);
                    if (!success) {
                        Toast.makeText(this, "DECLINED: " + center + " (" + room + ") is already at FULL CAPACITY!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Evacuee added successfully.", Toast.LENGTH_SHORT).show();
                    }
                });

                Button btnLocate = new Button(this);
                btnLocate.setText("Pin on Map");
                btnLocate.setAllCaps(false);
                btnLocate.setOnClickListener(v -> {
                    officialMap.getController().animateTo(pt);
                    officialMap.getController().setZoom(18.0);
                    m.showInfoWindow();
                });

                btnRow.addView(btnMinus);
                btnRow.addView(btnPlus);
                btnRow.addView(btnLocate);

                box.addView(top);
                box.addView(tvRoomSub);
                box.addView(btnRow);
                card.addView(box);
                roomList.addView(card);
            } while (cr.moveToNext());
            cr.close();
        }
        officialMap.invalidate();
    }

    private void setupListeners() {
        findViewById(R.id.btnOfficialMenu).setOnClickListener(v -> {
            String[] options = {"Export Complete CSV Audit Report", "Log Out (" + currentOfficialName + ")"};
            new AlertDialog.Builder(this)
                    .setTitle("Official Command Options")
                    .setItems(options, (d, which) -> {
                        if (which == 0) exportBarangayCsv();
                        else {
                            startActivity(new Intent(OfficialDashboardActivity.this, MainActivity.class));
                            finish();
                        }
                    })
                    .show();
        });

        findViewById(R.id.btnHomeLocatorLink).setOnClickListener(v -> showTab(4));

        findViewById(R.id.navOffOverview).setOnClickListener(v -> showTab(0));
        findViewById(R.id.navOffProfiling).setOnClickListener(v -> showTab(1));
        findViewById(R.id.navOffRelief).setOnClickListener(v -> showTab(2));
        findViewById(R.id.navOffInventory).setOnClickListener(v -> showTab(3));
        findViewById(R.id.navOffShelters).setOnClickListener(v -> showTab(4));

        findViewById(R.id.btnAddResidentProfile).setOnClickListener(v -> showAddResidentDialog());

        ((EditText) findViewById(R.id.etSearchResidents)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase();
                loadResidentsList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.filterAll).setOnClickListener(v -> setFilter("ALL"));
        findViewById(R.id.filterSenior).setOnClickListener(v -> setFilter("SENIOR"));
        findViewById(R.id.filterPwd).setOnClickListener(v -> setFilter("PWD"));
        findViewById(R.id.filterYoung).setOnClickListener(v -> setFilter("YOUNG"));

        findViewById(R.id.btnLaunchQrScanner).setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan Resident's Barangay 132 QR Pass (e.g. B132-2026-0002)");
            options.setBeepEnabled(true);
            options.setBarcodeImageEnabled(false);
            options.setOrientationLocked(false);
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            qrScanLauncher.launch(options);
        });

        findViewById(R.id.btnVerifyAndRelease).setOnClickListener(v -> {
            String rawInput = ((EditText) findViewById(R.id.etClaimResidentId)).getText().toString().trim();
            if (rawInput.isEmpty() || packageIds.isEmpty()) {
                Toast.makeText(this, "Enter or scan a Resident ID.", Toast.LENGTH_SHORT).show();
                return;
            }
            int resId = parseResidentId(rawInput);
            int selectedItemId = packageIds.get(spinnerPackages.getSelectedItemPosition());

            String result = dbHelper.distributeReliefPackage(resId, currentOfficialId, selectedItemId);
            pushInventoryItemToCloud(selectedItemId);
            new AlertDialog.Builder(this)
                    .setTitle("Anti-Double Claim & Custodian Verification")
                    .setMessage(result)
                    .setPositiveButton("OK", null)
                    .show();
        });

        findViewById(R.id.btnRestockMyBatch).setOnClickListener(v -> {
            String result = dbHelper.adjustInventoryStock(currentOfficialId, currentOfficialId, 50);
            pushInventoryItemToCloud(currentOfficialId);
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        });
    }

    private void setFilter(String filter) {
        activeFilter = filter;
        TextView fAll = findViewById(R.id.filterAll);
        TextView fSen = findViewById(R.id.filterSenior);
        TextView fPwd = findViewById(R.id.filterPwd);
        TextView fYng = findViewById(R.id.filterYoung);

        TextView[] all = {fAll, fSen, fPwd, fYng};
        String[] keys = {"ALL", "SENIOR", "PWD", "YOUNG"};
        for (int i = 0; i < all.length; i++) {
            boolean active = keys[i].equals(filter);
            all[i].setBackgroundColor(active ? Color.parseColor("#0E5A36") : Color.parseColor("#E5E7EB"));
            all[i].setTextColor(active ? Color.WHITE : Color.parseColor("#374151"));
        }
        loadResidentsList();
    }

    private void showAddResidentDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(44, 24, 44, 16);

        EditText etHead = makeInput(form, "Family Head Name");
        EditText etName = makeInput(form, "Resident Full Name");
        EditText etStreet = makeInput(form, "Street (e.g. General Concepcion)");
        EditText etContact = makeInput(form, "Contact Number (09XXXXXXXXX or +639XXXXXXXXX)");
        EditText etEmail = makeInput(form, "Gmail Address (example@gmail.com)");
        EditText etAgeGroup = makeInput(form, "Age Group (Young / Adult / Senior)");
        EditText etCategory = makeInput(form, "Category (PWD / Senior / Young / Regular)");
        EditText etChildren = makeInput(form, "Children Count (e.g. 2)");
        EditText etParents = makeInput(form, "Parents Count (e.g. 2)");
        EditText etRoomId = makeInput(form, "Assign Room ID (1-4, or 0 if at home)");

        new AlertDialog.Builder(this)
                .setTitle("+ Register New Resident Profile")
                .setView(form)
                .setPositiveButton("Save Profile", (d, w) -> {
                    int roomId = parseIntSafe(etRoomId.getText().toString(), 0);
                    int children = parseIntSafe(etChildren.getText().toString(), 0);
                    int parents = parseIntSafe(etParents.getText().toString(), 1);
                    String head = etHead.getText().toString().trim();
                    String name = etName.getText().toString().trim();
                    String street = etStreet.getText().toString().trim();
                    String contact = etContact.getText().toString().trim();
                    String email = etEmail.getText().toString().trim().toLowerCase();
                    String ageGroup = etAgeGroup.getText().toString().trim().isEmpty()
                            ? "Adult" : etAgeGroup.getText().toString().trim();
                    String category = etCategory.getText().toString().trim().isEmpty()
                            ? "Regular" : etCategory.getText().toString().trim();

                    String statusMsg = dbHelper.addResidentByOfficialValidated(
                            roomId > 0 ? roomId : null,
                            head,
                            name,
                            street,
                            contact,
                            email,
                            ageGroup,
                            category,
                            children,
                            parents
                    );

                    boolean isSuccess = statusMsg != null && statusMsg.startsWith("SUCCESS");
                    if (isSuccess) {
                        int totalMembers = Math.max(1, children + parents);
                        int seniors = "Senior".equalsIgnoreCase(ageGroup) || "Senior".equalsIgnoreCase(category) ? 1 : 0;
                        int pwd = "PWD".equalsIgnoreCase(category) ? 1 : 0;
                        int infants = "Young".equalsIgnoreCase(ageGroup) ? children : 0;
                        cloudSyncManager.syncResidentToCloud(
                                email,
                                name,
                                head,
                                street,
                                contact,
                                "resident123",
                                ageGroup,
                                category,
                                totalMembers,
                                seniors,
                                pwd,
                                infants,
                                roomId
                        );
                        refreshAllTabs();
                    }

                    new AlertDialog.Builder(this)
                            .setTitle(isSuccess ? "Resident Registered" : "Registration Declined")
                            .setMessage(statusMsg)
                            .setPositiveButton("OK", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddOfficialDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(44, 24, 44, 16);

        EditText etName = makeInput(form, "Official Full Name");
        EditText etEmail = makeInput(form, "Official Email (e.g. name@brgy132.gov.ph)");
        EditText etPassword = makeInput(form, "Password");

        new AlertDialog.Builder(this)
                .setTitle("+ Add Barangay Official")
                .setView(form)
                .setPositiveButton("Save Official", (d, w) -> {
                    String newName = etName.getText().toString().trim();
                    String newEmail = etEmail.getText().toString().trim().toLowerCase();
                    String newPassword = etPassword.getText().toString().trim();
                    if (newName.isEmpty() || newEmail.isEmpty() || newPassword.isEmpty()) {
                        Toast.makeText(this, "Complete all official fields.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new CloudSyncManager(this).syncOfficialToCloud(newEmail, newName, "Official", newPassword);
                    Toast.makeText(this, "Official synced to cloud.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAssignRoomDialog(int residentId, String residentName) {
        String[] choices = {
                "At Home (Not in Evacuation Center)",
                "Room #1 — Brgy 132 Covered Court Hub (Zone A - Main Court)",
                "Room #2 — Brgy 132 Covered Court Hub (Zone B - Senior & PWD Wing)",
                "Room #3 — Bagong Barrio Elem. School (Bldg 1 - Room 101)",
                "Room #4 — Bagong Barrio National High School (Gymnasium Hall)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Assign Location: " + residentName)
                .setItems(choices, (dialog, which) -> {
                    Integer selectedRoomId = (which == 0) ? null : which;
                    String result = dbHelper.updateResidentRoomAssignment(residentId, selectedRoomId);
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void exportBarangayCsv() {
        try {
            File file = new File(getExternalFilesDir(null), "Agapay_Brgy132_Audit_Report.csv");
            FileWriter writer = new FileWriter(file);

            // Section 1: Residents Masterlist
            writer.append("=== RESIDENTS MASTERLIST ===\n");
            writer.append("RESIDENT_ID,FULL_NAME,FAMILY_HEAD,STREET,CONTACT,AGE_GROUP,CATEGORY,TOTAL_MEMBERS,SENIORS,PWD,INFANTS,ROOM_ID\n");

            Cursor c = dbHelper.getAllResidents();
            if (c != null && c.moveToFirst()) {
                do {
                    writer.append(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RESIDENT_ID)))).append(",")
                            .append(csvEscape(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_RES_FULL_NAME)))).append(",")
                            .append(csvEscape(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_FAMILY_HEAD)))).append(",")
                            .append(csvEscape(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_STREET)))).append(",")
                            .append(csvEscape(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CONTACT_NO)))).append(",")
                            .append(csvEscape(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_AGE_GROUP)))).append(",")
                            .append(csvEscape(c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_CATEGORY)))).append(",")
                            .append(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_MEMBERS)))).append(",")
                            .append(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_SENIORS_COUNT)))).append(",")
                            .append(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_PWD_COUNT)))).append(",")
                            .append(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_INFANTS_COUNT)))).append(",")
                            .append(String.valueOf(c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_RES_ROOM_ID)))).append("\n");
                } while (c.moveToNext());
                c.close();
            }

            // Section 2: Relief Goods Stockpile & Custodians
            writer.append("\n=== RELIEF GOODS INVENTORY & CUSTODIANS ===\n");
            writer.append("ITEM_ID,PACKAGE_NAME,QTY_REMAINING,QTY_DISTRIBUTED,MAX_STOCK_LIMIT,ASSIGNED_OFFICIAL_ID,CUSTODIAN_NAME\n");

            Cursor ci = dbHelper.getAllInventoryWithOfficial();
            if (ci != null && ci.moveToFirst()) {
                do {
                    writer.append(String.valueOf(ci.getInt(0))).append(",")
                            .append(csvEscape(ci.getString(1))).append(",")
                            .append(String.valueOf(ci.getInt(2))).append(",")
                            .append(String.valueOf(ci.getInt(3))).append(",")
                            .append(String.valueOf(DatabaseHelper.MAX_STOCK_LIMIT)).append(",")
                            .append(String.valueOf(ci.getInt(4))).append(",")
                            .append(csvEscape(ci.getString(5))).append("\n");
                } while (ci.moveToNext());
                ci.close();
            }

            // Section 3: Relief Distribution & Claim Logs
            writer.append("\n=== RELIEF DISTRIBUTION LOGS ===\n");
            writer.append("DISTRIBUTION_ID,RESIDENT_NAME,RESIDENT_ID,PACKAGE_NAME,DISTRIBUTED_BY_OFFICIAL,DATE_CLAIMED,CLAIM_STATUS\n");

            Cursor cl = dbHelper.getAllReliefLogs();
            if (cl != null && cl.moveToFirst()) {
                do {
                    writer.append(String.valueOf(cl.getInt(0))).append(",")
                            .append(csvEscape(cl.getString(1))).append(",")
                            .append(String.valueOf(cl.getInt(2))).append(",")
                            .append(csvEscape(cl.getString(3))).append(",")
                            .append(csvEscape(cl.getString(4))).append(",")
                            .append(csvEscape(cl.getString(5))).append(",")
                            .append(csvEscape(cl.getString(6))).append("\n");
                } while (cl.moveToNext());
                cl.close();
            }

            writer.flush();
            writer.close();
            Toast.makeText(this, "Full CSV Exported: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void showTab(int index) {
        tabOverview.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabProfiling.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabRelief.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        tabInventory.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        tabShelters.setVisibility(index == 4 ? View.VISIBLE : View.GONE);

        ImageView[] icons = {ivNavOverview, ivNavProfiling, ivNavRelief, ivNavInventory, ivNavShelters};
        TextView[] labels = {tvNavOverview, tvNavProfiling, tvNavRelief, tvNavInventory, tvNavShelters};
        int activeColor = Color.parseColor("#0E5A36");
        int inactiveColor = Color.parseColor("#9CA3AF");

        for (int i = 0; i < icons.length; i++) {
            icons[i].setColorFilter(i == index ? activeColor : inactiveColor);
            labels[i].setTextColor(i == index ? activeColor : inactiveColor);
        }
        refreshAllTabs();
    }

    private CardView makeWhiteCard() {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 22);
        card.setLayoutParams(lp);
        card.setRadius(32f);
        card.setCardElevation(3f);
        card.setCardBackgroundColor(Color.WHITE);
        return card;
    }

    private LinearLayout makeCardInner() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(34, 28, 34, 28);
        return box;
    }

    private EditText makeInput(LinearLayout parent, String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(13f);
        parent.addView(et);
        return et;
    }

    private int parseResidentId(String raw) {
        String digits = raw.replaceAll(".*-", "").replaceAll("[^0-9]", "");
        return parseIntSafe(digits, -1);
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
        if (officialMap != null) officialMap.onResume();
        ContextCompat.registerReceiver(
                this,
                instantDataReceiver,
                new IntentFilter(DatabaseHelper.ACTION_DATA_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            updateOnlineBadge(isDeviceOnline());
        }
        liveSyncHandler.removeCallbacks(liveSyncRunnable);
        liveSyncHandler.post(liveSyncRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (officialMap != null) officialMap.onPause();
        liveSyncHandler.removeCallbacks(liveSyncRunnable);
        try { unregisterReceiver(instantDataReceiver); } catch (Exception ignored) {}
        try {
            if (connectivityManager != null && networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception ignored) {}
    }
}