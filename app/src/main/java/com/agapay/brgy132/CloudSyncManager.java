package com.agapay.brgy132;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class CloudSyncManager {

    private static boolean listenersAttached = false;

    final DatabaseReference rootRef;
    private final DatabaseHelper dbHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SyncCallback {
        void onDataSynced();
    }

    public CloudSyncManager(Context context) {
        this.dbHelper = new DatabaseHelper(context.getApplicationContext());
        this.rootRef = FirebaseDatabase.getInstance().getReference("agapay_brgy132");
    }

    public void bindLiveSync(Activity activity, Runnable refreshAction) {
        if (activity == null) return;
        startLiveSync(() -> {
            if (refreshAction != null && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.runOnUiThread(refreshAction);
            }
        });
    }

    // 1. Push a Resident record to Firebase (call on Sign Up and Profile Update)
    public void syncResidentToCloud(String email, String fullName, String familyHead,
                                    String street, String contact, String password,
                                    String ageGroup, String category, int totalMembers,
                                    int seniors, int pwd, int infants, int roomId) {
        if (email == null || email.trim().isEmpty()) return;
        String safeKey = email.trim().toLowerCase().replace(".", "_");

        Map<String, Object> residentMap = new HashMap<>();
        residentMap.put("email", email.trim().toLowerCase());
        residentMap.put("full_name", fullName);
        residentMap.put("family_head", familyHead);
        residentMap.put("street", street);
        residentMap.put("contact", contact);
        residentMap.put("password", password);
        residentMap.put("age_group", ageGroup);
        residentMap.put("category", category);
        residentMap.put("total_members", totalMembers);
        residentMap.put("seniors_count", seniors);
        residentMap.put("pwd_count", pwd);
        residentMap.put("infants_count", infants);
        residentMap.put("room_id", roomId);

        rootRef.child("residents").child(safeKey).setValue(residentMap);
    }

    // 2. Push an Official record to Firebase (call when Official adds another Official)
    public void syncOfficialToCloud(String email, String fullName, String role, String password) {
        if (email == null || email.trim().isEmpty()) return;
        String safeKey = email.trim().toLowerCase().replace(".", "_");

        Map<String, Object> officialMap = new HashMap<>();
        officialMap.put("email", email.trim().toLowerCase());
        officialMap.put("full_name", fullName);
        officialMap.put("role", role);
        officialMap.put("password", password);

        rootRef.child("officials").child(safeKey).setValue(officialMap);
    }

    // 3. Push Inventory / Relief Distribution updates to Firebase
    public void syncInventoryItem(int itemId, String packageName, int remaining, int distributed) {
        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("item_id", itemId);
        itemMap.put("package_name", packageName);
        itemMap.put("quantity_remaining", remaining);
        itemMap.put("quantity_distributed", distributed);

        rootRef.child("inventory").child(String.valueOf(itemId)).setValue(itemMap);
    }

    // 4. Live Listener: Pulls all cloud changes into local SQLite automatically
    public void startLiveSync(SyncCallback callback) {
        if (listenersAttached) {
            notifyCallback(callback);
            return;
        }
        listenersAttached = true;

        rootRef.child("residents").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String email = child.child("email").getValue(String.class);
                    if (email == null || email.trim().isEmpty()) continue;

                    String normalizedEmail = email.trim().toLowerCase();
                    ContentValues values = new ContentValues();
                    values.put(DatabaseHelper.COL_EMAIL, normalizedEmail);
                    values.put(DatabaseHelper.COL_RES_FULL_NAME, child.child("full_name").getValue(String.class));
                    values.put(DatabaseHelper.COL_STREET, child.child("street").getValue(String.class));
                    values.put(DatabaseHelper.COL_PASSWORD, child.child("password").getValue(String.class));

                    Long total = child.child("total_members").getValue(Long.class);
                    Long seniors = child.child("seniors_count").getValue(Long.class);
                    Long pwd = child.child("pwd_count").getValue(Long.class);
                    Long infants = child.child("infants_count").getValue(Long.class);
                    Long roomId = child.child("room_id").getValue(Long.class);

                    values.put(DatabaseHelper.COL_TOTAL_MEMBERS, total != null ? total.intValue() : 1);
                    values.put(DatabaseHelper.COL_SENIORS_COUNT, seniors != null ? seniors.intValue() : 0);
                    values.put(DatabaseHelper.COL_PWD_COUNT, pwd != null ? pwd.intValue() : 0);
                    values.put(DatabaseHelper.COL_INFANTS_COUNT, infants != null ? infants.intValue() : 0);
                    values.put(DatabaseHelper.COL_RES_ROOM_ID, roomId != null ? roomId.intValue() : 0);

                    int updated = db.update(DatabaseHelper.TABLE_RESIDENT, values,
                            DatabaseHelper.COL_EMAIL + "=?", new String[]{normalizedEmail});
                    if (updated == 0) {
                        db.insert(DatabaseHelper.TABLE_RESIDENT, null, values);
                    }
                }
                notifyCallback(callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        rootRef.child("officials").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String email = child.child("email").getValue(String.class);
                    if (email == null || email.trim().isEmpty()) continue;

                    String normalizedEmail = email.trim().toLowerCase();
                    ContentValues values = new ContentValues();
                    values.put("email", normalizedEmail);
                    values.put("full_name", child.child("full_name").getValue(String.class));
                    values.put("password", child.child("password").getValue(String.class));

                    int updated = db.update("official", values, "email=?", new String[]{normalizedEmail});
                    if (updated == 0) {
                        db.insert("official", null, values);
                    }
                }
                notifyCallback(callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // Static compatibility helpers for DatabaseHelper calls
    public static void pushResident(Context context, int id, Integer roomId, String fullName,
                                    String street, String contact, String email,
                                    int totalMembers, int seniors, int pwd, int infants) {
        if (context == null) return;
        new CloudSyncManager(context).syncResidentToCloud(
                email != null ? email : ("resident_" + id + "@brgy132.local"),
                fullName != null ? fullName : "",
                fullName != null ? fullName : "",
                street != null ? street : "",
                contact != null ? contact : "",
                "",
                "",
                "",
                totalMembers,
                seniors,
                pwd,
                infants,
                roomId != null ? roomId : 0
        );
    }

    public static void pushEvacuationRoom(Context context, int roomId, String centerName,
                                          String roomName, int capacity, int limit,
                                          String status, double lat, double lng) {
        if (context == null) return;
        CloudSyncManager mgr = new CloudSyncManager(context);
        Map<String, Object> roomMap = new HashMap<>();
        roomMap.put("room_id", roomId);
        roomMap.put("center_name", centerName);
        roomMap.put("room_name", roomName);
        roomMap.put("capacity", capacity);
        roomMap.put("room_limit", limit);
        roomMap.put("status", status);
        roomMap.put("lat", lat);
        roomMap.put("lng", lng);
        mgr.rootRef.child("evacuation_rooms").child(String.valueOf(roomId)).setValue(roomMap);
    }

    public static void pushInventory(Context context, int itemId, String packageName,
                                     int remaining, int distributed, int assignedOfficialId) {
        if (context == null) return;
        CloudSyncManager mgr = new CloudSyncManager(context);
        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("item_id", itemId);
        itemMap.put("package_name", packageName);
        itemMap.put("quantity_remaining", remaining);
        itemMap.put("quantity_distributed", distributed);
        itemMap.put("assigned_official_id", assignedOfficialId);
        mgr.rootRef.child("inventory").child(String.valueOf(itemId)).setValue(itemMap);
    }

    public static void pushReliefLog(Context context, int logId, int residentId, int officialId,
                                     int itemId, String dateClaimed, String status) {
        if (context == null) return;
        CloudSyncManager mgr = new CloudSyncManager(context);
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("log_id", logId);
        logMap.put("resident_id", residentId);
        logMap.put("official_id", officialId);
        logMap.put("item_id", itemId);
        logMap.put("date_claimed", dateClaimed);
        logMap.put("status", status);
        mgr.rootRef.child("relief_logs").child(String.valueOf(logId)).setValue(logMap);
    }

    private void notifyCallback(SyncCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onDataSynced);
        }
    }
}