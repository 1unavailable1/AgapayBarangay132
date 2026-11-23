package com.agapay.brgy132;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "agapay_brgy132.db";
    private static final int DATABASE_VERSION = 7;
    public static final int MAX_STOCK_LIMIT = 1000;
    public static final String ACTION_DATA_CHANGED = "com.agapay.brgy132.ACTION_DATA_CHANGED";

    // 1. OFFICIAL TABLE
    public static final String TABLE_OFFICIAL = "official";
    public static final String COL_OFFICIAL_ID = "official_id";
    public static final String COL_OFF_NAME = "full_name";
    public static final String COL_OFF_EMAIL = "email";
    public static final String COL_OFF_PASSWORD = "password";
    public static final String COL_OFF_ROLE = "role";

    // 2. EVACUATION_ROOM TABLE
    public static final String TABLE_EVAC_ROOM = "evacuation_room";
    public static final String COL_ROOM_ID = "room_id";
    public static final String COL_CENTER_NAME = "center_name";
    public static final String COL_ROOM_NAME = "room_name";
    public static final String COL_ROOM_CAPACITY = "room_capacity";
    public static final String COL_ROOM_LIMIT = "room_limit";
    public static final String COL_ROOM_STATUS = "room_status";
    public static final String COL_LAT = "latitude";
    public static final String COL_LNG = "longitude";

    // 3. RESIDENT TABLE
    public static final String TABLE_RESIDENT = "resident";
    public static final String COL_RESIDENT_ID = "resident_id";
    public static final String COL_RES_ROOM_ID = "room_id";
    public static final String COL_FAMILY_HEAD = "family_head_name";
    public static final String COL_RES_FULL_NAME = "full_name";
    public static final String COL_STREET = "street";
    public static final String COL_CONTACT_NO = "contact_number";
    public static final String COL_EMAIL = "email";
    public static final String COL_PASSWORD = "password";
    public static final String COL_FAMILY_PHOTO = "family_photo";
    public static final String COL_AGE_GROUP = "age_group";
    public static final String COL_CATEGORY = "category";
    public static final String COL_TOTAL_MEMBERS = "total_members";
    public static final String COL_SENIORS_COUNT = "seniors_count";
    public static final String COL_PWD_COUNT = "pwd_count";
    public static final String COL_INFANTS_COUNT = "infants_count";
    public static final String COL_CHILDREN_COUNT = "children_count";
    public static final String COL_PARENTS_COUNT = "parents_count";

    // 4. INVENTORY TABLE
    public static final String TABLE_INVENTORY = "inventory";
    public static final String COL_ITEM_ID = "item_id";
    public static final String COL_PACKAGE_NAME = "package_name";
    public static final String COL_QTY_REMAINING = "quantity_remaining";
    public static final String COL_QTY_DISTRIBUTED = "quantity_distributed";
    public static final String COL_ASSIGNED_OFFICIAL_ID = "assigned_official_id";

    // 5. RELIEF_DISTRIBUTION TABLE
    public static final String TABLE_RELIEF = "relief_distribution";
    public static final String COL_DIST_ID = "distribution_id";
    public static final String COL_DIST_RESIDENT_ID = "resident_id";
    public static final String COL_DIST_OFFICIAL_ID = "official_id";
    public static final String COL_DIST_ITEM_ID = "item_id";
    public static final String COL_DATE_CLAIMED = "date_claimed";
    public static final String COL_CLAIM_STATUS = "claim_status";

    public static final List<String> VALID_OFFICIAL_EMAILS = Arrays.asList(
            "andrewsalas@brgy132.gov.ph",
            "drewabarro@brgy132.gov.ph",
            "christinealcantara@brgy132.gov.ph"
    );

    private final Context appContext;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.appContext = context.getApplicationContext();
    }

    public void notifyDataChanged() {
        Intent intent = new Intent(ACTION_DATA_CHANGED);
        intent.setPackage(appContext.getPackageName());
        appContext.sendBroadcast(intent);
    }

    public static boolean isAuthorizedOfficialEmail(String email) {
        return email != null && VALID_OFFICIAL_EMAILS.contains(email.trim().toLowerCase());
    }

    public static boolean isValidGmail(String email) {
        if (email == null) return false;
        String clean = email.trim().toLowerCase();
        return clean.matches("^[a-z0-9._%+-]+@gmail\\.com$");
    }

    public static boolean isValidPhPhoneNumber(String contact) {
        if (contact == null) return false;
        String digitsOnly = contact.replaceAll("[\\s\\-()+]", "");
        return digitsOnly.matches("^(09\\d{9}|639\\d{9})$");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_OFFICIAL + " ("
                + COL_OFFICIAL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_OFF_NAME + " TEXT NOT NULL, "
                + COL_OFF_EMAIL + " TEXT UNIQUE NOT NULL, "
                + COL_OFF_PASSWORD + " TEXT NOT NULL, "
                + COL_OFF_ROLE + " TEXT NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_EVAC_ROOM + " ("
                + COL_ROOM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_CENTER_NAME + " TEXT NOT NULL, "
                + COL_ROOM_NAME + " TEXT NOT NULL, "
                + COL_ROOM_CAPACITY + " INTEGER NOT NULL, "
                + COL_ROOM_LIMIT + " INTEGER NOT NULL, "
                + COL_ROOM_STATUS + " TEXT NOT NULL, "
                + COL_LAT + " REAL NOT NULL, "
                + COL_LNG + " REAL NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_RESIDENT + " ("
                + COL_RESIDENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_RES_ROOM_ID + " INTEGER, "
                + COL_FAMILY_HEAD + " TEXT NOT NULL, "
                + COL_RES_FULL_NAME + " TEXT NOT NULL, "
                + COL_STREET + " TEXT DEFAULT 'General Concepcion', "
                + COL_CONTACT_NO + " TEXT NOT NULL, "
                + COL_EMAIL + " TEXT UNIQUE NOT NULL, "
                + COL_PASSWORD + " TEXT NOT NULL, "
                + COL_FAMILY_PHOTO + " TEXT DEFAULT '', "
                + COL_AGE_GROUP + " TEXT DEFAULT 'Adult', "
                + COL_CATEGORY + " TEXT DEFAULT 'Regular', "
                + COL_TOTAL_MEMBERS + " INTEGER DEFAULT 1, "
                + COL_SENIORS_COUNT + " INTEGER DEFAULT 0, "
                + COL_PWD_COUNT + " INTEGER DEFAULT 0, "
                + COL_INFANTS_COUNT + " INTEGER DEFAULT 0, "
                + COL_CHILDREN_COUNT + " INTEGER DEFAULT 0, "
                + COL_PARENTS_COUNT + " INTEGER DEFAULT 1)");

        db.execSQL("CREATE TABLE " + TABLE_INVENTORY + " ("
                + COL_ITEM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_PACKAGE_NAME + " TEXT NOT NULL, "
                + COL_QTY_REMAINING + " INTEGER NOT NULL, "
                + COL_QTY_DISTRIBUTED + " INTEGER DEFAULT 0, "
                + COL_ASSIGNED_OFFICIAL_ID + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_RELIEF + " ("
                + COL_DIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_DIST_RESIDENT_ID + " INTEGER NOT NULL, "
                + COL_DIST_OFFICIAL_ID + " INTEGER NOT NULL, "
                + COL_DIST_ITEM_ID + " INTEGER NOT NULL, "
                + COL_DATE_CLAIMED + " TEXT NOT NULL, "
                + COL_CLAIM_STATUS + " TEXT NOT NULL)");

        seedOfficial(db, "Andrew Jae Salas", "andrewsalas@brgy132.gov.ph", "admin123", "Inventory & Relief Head");
        seedOfficial(db, "Denisse Andrew E. Abarro", "drewabarro@brgy132.gov.ph", "admin123", "Barangay Official Admin");
        seedOfficial(db, "Christine R. Alcantara", "christinealcantara@brgy132.gov.ph", "admin123", "Shelter & Profiling Officer");

        seedRoom(db, "Brgy 132 Covered Court Hub", "Zone A - Main Court", 28, 40, "AVAILABLE", 14.6615, 120.9922);
        seedRoom(db, "Brgy 132 Covered Court Hub", "Zone B - Senior & PWD Wing", 18, 25, "AVAILABLE", 14.6616, 120.9923);
        seedRoom(db, "Bagong Barrio Elem. School", "Bldg 1 - Room 101", 32, 35, "NEAR FULL", 14.6628, 120.9935);
        seedRoom(db, "Bagong Barrio National High School", "Gymnasium Hall", 45, 80, "AVAILABLE", 14.6602, 120.9948);

        seedResident(db, 1, "Juan Dela Cruz", "Juan Dela Cruz", "General Concepcion",
                "+63 912 345 6789", "juan@gmail.com", "resident123", "Adult", "PWD",
                5, 1, 1, 1, 3, 2);

        seedInventory(db, "Family Food Package A (5kg Rice + Canned Goods)", 500, 120, 1);
        seedInventory(db, "Hygiene & Sanitary Package B", 350, 85, 2);
        seedInventory(db, "Infant & Senior Care Package C", 200, 40, 3);
    }

    private void seedOfficial(SQLiteDatabase db, String name, String email, String pass, String role) {
        ContentValues cv = new ContentValues();
        cv.put(COL_OFF_NAME, name);
        cv.put(COL_OFF_EMAIL, email.toLowerCase());
        cv.put(COL_OFF_PASSWORD, pass);
        cv.put(COL_OFF_ROLE, role);
        db.insert(TABLE_OFFICIAL, null, cv);
    }

    private void seedRoom(SQLiteDatabase db, String center, String room, int cap, int limit,
                          String status, double lat, double lng) {
        ContentValues cv = new ContentValues();
        cv.put(COL_CENTER_NAME, center);
        cv.put(COL_ROOM_NAME, room);
        cv.put(COL_ROOM_CAPACITY, cap);
        cv.put(COL_ROOM_LIMIT, limit);
        cv.put(COL_ROOM_STATUS, status);
        cv.put(COL_LAT, lat);
        cv.put(COL_LNG, lng);
        db.insert(TABLE_EVAC_ROOM, null, cv);
    }

    private void seedResident(SQLiteDatabase db, Integer roomId, String head, String fullName,
                              String street, String contact, String email, String pass,
                              String ageGroup, String category, int totalMembers, int seniors,
                              int pwd, int infants, int children, int parents) {
        ContentValues cv = new ContentValues();
        if (roomId != null) cv.put(COL_RES_ROOM_ID, roomId);
        cv.put(COL_FAMILY_HEAD, head);
        cv.put(COL_RES_FULL_NAME, fullName);
        cv.put(COL_STREET, street);
        cv.put(COL_CONTACT_NO, contact);
        cv.put(COL_EMAIL, email.toLowerCase());
        cv.put(COL_PASSWORD, pass);
        cv.put(COL_AGE_GROUP, ageGroup);
        cv.put(COL_CATEGORY, category);
        cv.put(COL_TOTAL_MEMBERS, totalMembers);
        cv.put(COL_SENIORS_COUNT, seniors);
        cv.put(COL_PWD_COUNT, pwd);
        cv.put(COL_INFANTS_COUNT, infants);
        cv.put(COL_CHILDREN_COUNT, children);
        cv.put(COL_PARENTS_COUNT, parents);
        db.insert(TABLE_RESIDENT, null, cv);
    }

    private void seedInventory(SQLiteDatabase db, String pkgName, int remaining, int distributed, int officialId) {
        ContentValues cv = new ContentValues();
        cv.put(COL_PACKAGE_NAME, pkgName);
        cv.put(COL_QTY_REMAINING, remaining);
        cv.put(COL_QTY_DISTRIBUTED, distributed);
        cv.put(COL_ASSIGNED_OFFICIAL_ID, officialId);
        db.insert(TABLE_INVENTORY, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_RESIDENT + " ADD COLUMN " + COL_FAMILY_PHOTO + " TEXT DEFAULT ''");
            } catch (Exception ignored) {
            }
            ContentValues fixPass = new ContentValues();
            fixPass.put(COL_PASSWORD, "resident123");
            db.update(TABLE_RESIDENT, fixPass,
                    COL_PASSWORD + " IS NULL OR TRIM(" + COL_PASSWORD + ") = ''", null);
            return;
        }
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_OFFICIAL);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVAC_ROOM);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RESIDENT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVENTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RELIEF);
        db.execSQL("DROP TABLE IF EXISTS users");
        db.execSQL("DROP TABLE IF EXISTS evacuation_centers");
        onCreate(db);
    }

    public String authenticateUser(String email, String password) {
        if (email == null || password == null) return null;
        String cleanEmail = email.trim().toLowerCase();
        SQLiteDatabase db = this.getReadableDatabase();

        if (isAuthorizedOfficialEmail(cleanEmail)) {
            Cursor cOff = db.query(TABLE_OFFICIAL, new String[]{COL_OFFICIAL_ID},
                    COL_OFF_EMAIL + "=? AND " + COL_OFF_PASSWORD + "=?",
                    new String[]{cleanEmail, password}, null, null, null);
            boolean ok = cOff.moveToFirst();
            cOff.close();
            if (ok) return "OFFICIAL";
        }

        if (!isValidGmail(cleanEmail)) {
            return null;
        }

        Cursor cRes = db.query(TABLE_RESIDENT, new String[]{COL_RESIDENT_ID},
                COL_EMAIL + "=? AND " + COL_PASSWORD + "=?",
                new String[]{cleanEmail, password}, null, null, null);
        boolean resOk = cRes.moveToFirst();
        cRes.close();
        return resOk ? "RESIDENT" : null;
    }

    public boolean isEmailRegistered(String email) {
        String cleanEmail = email.trim().toLowerCase();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_RESIDENT, new String[]{COL_RESIDENT_ID},
                COL_EMAIL + "=?", new String[]{cleanEmail}, null, null, null);
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    public boolean registerResident(String firstName, String lastName, String contactNumber,
                                    String email, String password) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        if (!isValidGmail(cleanEmail)) return false;
        if (!isValidPhPhoneNumber(contactNumber)) return false;
        if (password == null || password.trim().length() < 4) return false;
        if (isEmailRegistered(cleanEmail)) return false;

        String fullName = (firstName + " " + lastName).trim();
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_FAMILY_HEAD, fullName);
        cv.put(COL_RES_FULL_NAME, fullName);
        cv.put(COL_STREET, "General Concepcion");
        cv.put(COL_CONTACT_NO, contactNumber.trim());
        cv.put(COL_EMAIL, cleanEmail);
        cv.put(COL_PASSWORD, password);
        cv.put(COL_AGE_GROUP, "Adult");
        cv.put(COL_CATEGORY, "Regular");
        cv.put(COL_TOTAL_MEMBERS, 1);
        cv.put(COL_SENIORS_COUNT, 0);
        cv.put(COL_PWD_COUNT, 0);
        cv.put(COL_INFANTS_COUNT, 0);
        cv.put(COL_CHILDREN_COUNT, 0);
        cv.put(COL_PARENTS_COUNT, 1);
        long id = db.insert(TABLE_RESIDENT, null, cv);
        if (id != -1) {
            CloudSyncManager.pushResident(appContext, (int) id, null, fullName, "General Concepcion", contactNumber.trim(), cleanEmail, 1, 0, 0, 0);
            notifyDataChanged();
            return true;
        }
        return false;
    }

    public Cursor getResidentByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_RESIDENT, null, COL_EMAIL + "=?",
                new String[]{email.trim().toLowerCase()}, null, null, null);
    }

    public boolean updateResidentProfile(String email, String fullName, String street,
                                         int totalMembers, int seniors, int pwd, int infants,
                                         String photoUri) {
        return updateResidentProfile(email, fullName, street, totalMembers, seniors, pwd, infants, photoUri, null);
    }

    public boolean updateResidentProfile(String email, String fullName, String street,
                                         int totalMembers, int seniors, int pwd, int infants,
                                         String photoUri, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_RES_FULL_NAME, fullName);
        cv.put(COL_FAMILY_HEAD, fullName);
        cv.put(COL_STREET, street);
        cv.put(COL_TOTAL_MEMBERS, totalMembers);
        cv.put(COL_SENIORS_COUNT, seniors);
        cv.put(COL_PWD_COUNT, pwd);
        cv.put(COL_INFANTS_COUNT, infants);
        if (photoUri != null && !photoUri.isEmpty()) {
            cv.put(COL_FAMILY_PHOTO, photoUri);
        }
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            cv.put(COL_PASSWORD, newPassword.trim());
        }
        boolean ok = db.update(TABLE_RESIDENT, cv, COL_EMAIL + "=?",
                new String[]{email.trim().toLowerCase()}) > 0;
        if (ok) {
            Cursor c = getResidentByEmail(email);
            if (c != null && c.moveToFirst()) {
                int id = c.getInt(c.getColumnIndexOrThrow(COL_RESIDENT_ID));
                Integer roomId = c.isNull(c.getColumnIndexOrThrow(COL_RES_ROOM_ID)) ? null : c.getInt(c.getColumnIndexOrThrow(COL_RES_ROOM_ID));
                String contact = c.getString(c.getColumnIndexOrThrow(COL_CONTACT_NO));
                CloudSyncManager.pushResident(appContext, id, roomId, fullName, street, contact, email.trim().toLowerCase(), totalMembers, seniors, pwd, infants);
                c.close();
            }
            notifyDataChanged();
        }
        return ok;
    }

    public Cursor getAllEvacuationRooms() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_EVAC_ROOM, null, null, null, null, null, COL_ROOM_ID + " ASC");
    }

    public Cursor getAvailableEvacuationCenters() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_EVAC_ROOM, null, COL_ROOM_CAPACITY + " < " + COL_ROOM_LIMIT, null, null, null, null);
    }

    public Cursor getOfficialByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_OFFICIAL, null, COL_OFF_EMAIL + "=?",
                new String[]{email.trim().toLowerCase()}, null, null, null);
    }

    public Cursor getAllResidents() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_RESIDENT, null, null, null, null, null, COL_RESIDENT_ID + " DESC");
    }

    public String addResidentByOfficialValidated(Integer roomId, String familyHead, String fullName,
                                                 String street, String contact, String email,
                                                 String ageGroup, String category, int children, int parents) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "INVALID: Resident full name is required.";
        }
        if (!isValidPhPhoneNumber(contact)) {
            return "INVALID NUMBER: Enter a valid Philippine mobile number (e.g., 09123456789 or +63 912 345 6789).";
        }
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        if (!isValidGmail(cleanEmail)) {
            return "INVALID GMAIL: Enter a valid @gmail.com address (e.g., resident@gmail.com).";
        }
        if (isEmailRegistered(cleanEmail)) {
            return "DUPLICATE: This Gmail address is already registered.";
        }

        if (roomId != null && roomId > 0) {
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor roomCursor = db.query(TABLE_EVAC_ROOM,
                    new String[]{COL_ROOM_CAPACITY, COL_ROOM_LIMIT, COL_ROOM_STATUS, COL_CENTER_NAME, COL_ROOM_NAME},
                    COL_ROOM_ID + "=?", new String[]{String.valueOf(roomId)}, null, null, null);
            if (!roomCursor.moveToFirst()) {
                roomCursor.close();
                return "INVALID ROOM: Room #" + roomId + " does not exist.";
            }
            int cap = roomCursor.getInt(0);
            int limit = roomCursor.getInt(1);
            String status = roomCursor.getString(2);
            String center = roomCursor.getString(3);
            String room = roomCursor.getString(4);
            roomCursor.close();

            if (cap >= limit || "FULL".equalsIgnoreCase(status)) {
                return "DECLINED: " + center + " (" + room + ") is already FULL (" + cap + "/" + limit + "). Resident registration declined.";
            }
        }

        boolean ok = addResidentByOfficial(roomId, familyHead, fullName, street, contact.trim(), cleanEmail, ageGroup, category, children, parents);
        return ok ? "SUCCESS: Resident Profile Registered!" : "DECLINED: Selected evacuation room is already FULL.";
    }

    public boolean addResidentByOfficial(Integer roomId, String familyHead, String fullName,
                                         String street, String contact, String email,
                                         String ageGroup, String category, int children, int parents) {
        String cleanEmail = email.trim().toLowerCase();
        if (!isValidGmail(cleanEmail) || !isValidPhPhoneNumber(contact) || isEmailRegistered(cleanEmail)) {
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();

        if (roomId != null && roomId > 0) {
            Cursor roomCursor = db.query(TABLE_EVAC_ROOM,
                    new String[]{COL_ROOM_CAPACITY, COL_ROOM_LIMIT, COL_ROOM_STATUS},
                    COL_ROOM_ID + "=?", new String[]{String.valueOf(roomId)}, null, null, null);
            if (!roomCursor.moveToFirst()) {
                roomCursor.close();
                return false;
            }
            int cap = roomCursor.getInt(0);
            int limit = roomCursor.getInt(1);
            String status = roomCursor.getString(2);
            roomCursor.close();

            if (cap >= limit || "FULL".equalsIgnoreCase(status)) {
                return false;
            }
        }

        int seniors = "Senior".equalsIgnoreCase(category) ? 1 : 0;
        int pwd = "PWD".equalsIgnoreCase(category) ? 1 : 0;
        int infants = "Young".equalsIgnoreCase(category) ? 1 : 0;
        int total = Math.max(1, children + parents);

        ContentValues cv = new ContentValues();
        if (roomId != null && roomId > 0) cv.put(COL_RES_ROOM_ID, roomId);
        cv.put(COL_FAMILY_HEAD, familyHead.isEmpty() ? fullName : familyHead);
        cv.put(COL_RES_FULL_NAME, fullName);
        cv.put(COL_STREET, street.isEmpty() ? "General Concepcion" : street);
        cv.put(COL_CONTACT_NO, contact);
        cv.put(COL_EMAIL, cleanEmail);
        cv.put(COL_PASSWORD, "resident123");
        cv.put(COL_AGE_GROUP, ageGroup);
        cv.put(COL_CATEGORY, category);
        cv.put(COL_TOTAL_MEMBERS, total);
        cv.put(COL_SENIORS_COUNT, seniors);
        cv.put(COL_PWD_COUNT, pwd);
        cv.put(COL_INFANTS_COUNT, infants);
        cv.put(COL_CHILDREN_COUNT, children);
        cv.put(COL_PARENTS_COUNT, parents);

        long inserted = db.insert(TABLE_RESIDENT, null, cv);
        if (inserted != -1) {
            if (roomId != null && roomId > 0) {
                boolean added = updateRoomOccupancyDelta(roomId, 1);
                if (!added) {
                    db.delete(TABLE_RESIDENT, COL_RESIDENT_ID + "=?", new String[]{String.valueOf(inserted)});
                    return false;
                }
            }
            CloudSyncManager.pushResident(appContext, (int) inserted, roomId, fullName, street, contact, cleanEmail, total, seniors, pwd, infants);
            notifyDataChanged();
            return true;
        }
        return false;
    }

    public boolean updateRoomOccupancyDelta(int roomId, int delta) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = db.query(TABLE_EVAC_ROOM, new String[]{COL_CENTER_NAME, COL_ROOM_NAME, COL_ROOM_CAPACITY, COL_ROOM_LIMIT, COL_LAT, COL_LNG},
                COL_ROOM_ID + "=?", new String[]{String.valueOf(roomId)}, null, null, null);
        if (c.moveToFirst()) {
            String center = c.getString(0);
            String room = c.getString(1);
            int cap = c.getInt(2);
            int limit = c.getInt(3);
            double lat = c.getDouble(4);
            double lng = c.getDouble(5);

            if (delta > 0 && cap >= limit) {
                c.close();
                return false;
            }

            int newCap = Math.max(0, Math.min(limit, cap + delta));
            String newStatus = newCap >= limit ? "FULL" : (newCap >= (limit * 0.8) ? "NEAR FULL" : "AVAILABLE");

            ContentValues cv = new ContentValues();
            cv.put(COL_ROOM_CAPACITY, newCap);
            cv.put(COL_ROOM_STATUS, newStatus);
            db.update(TABLE_EVAC_ROOM, cv, COL_ROOM_ID + "=?", new String[]{String.valueOf(roomId)});

            CloudSyncManager.pushEvacuationRoom(appContext, roomId, center, room, newCap, limit, newStatus, lat, lng);
            notifyDataChanged();
            c.close();
            return true;
        }
        c.close();
        return false;
    }

    public String updateResidentRoomAssignment(int residentId, Integer newRoomId) {
        SQLiteDatabase db = this.getWritableDatabase();

        Integer oldRoomId = null;
        Cursor c = db.query(TABLE_RESIDENT, new String[]{COL_RES_ROOM_ID},
                COL_RESIDENT_ID + "=?", new String[]{String.valueOf(residentId)}, null, null, null);
        if (c.moveToFirst() && !c.isNull(0)) {
            int val = c.getInt(0);
            if (val > 0) oldRoomId = val;
        }
        c.close();

        if (newRoomId != null && newRoomId > 0 && !newRoomId.equals(oldRoomId)) {
            Cursor roomCursor = db.query(TABLE_EVAC_ROOM, new String[]{COL_ROOM_CAPACITY, COL_ROOM_LIMIT, COL_CENTER_NAME, COL_ROOM_NAME},
                    COL_ROOM_ID + "=?", new String[]{String.valueOf(newRoomId)}, null, null, null);
            if (roomCursor.moveToFirst()) {
                int cap = roomCursor.getInt(0);
                int limit = roomCursor.getInt(1);
                String center = roomCursor.getString(2);
                String room = roomCursor.getString(3);
                roomCursor.close();

                if (cap >= limit) {
                    return "DECLINED: " + center + " (" + room + ") is FULL (" + cap + "/" + limit + "). Assignment cancelled.";
                }
            } else {
                roomCursor.close();
            }
        }

        ContentValues cv = new ContentValues();
        if (newRoomId != null && newRoomId > 0) {
            cv.put(COL_RES_ROOM_ID, newRoomId);
        } else {
            cv.putNull(COL_RES_ROOM_ID);
        }
        db.update(TABLE_RESIDENT, cv, COL_RESIDENT_ID + "=?", new String[]{String.valueOf(residentId)});

        if (oldRoomId != null && !oldRoomId.equals(newRoomId)) {
            updateRoomOccupancyDelta(oldRoomId, -1);
        }
        if (newRoomId != null && newRoomId > 0 && !newRoomId.equals(oldRoomId)) {
            boolean success = updateRoomOccupancyDelta(newRoomId, 1);
            if (!success) {
                return "DECLINED: Room is at full capacity.";
            }
        }
        notifyDataChanged();
        return "SUCCESS: Resident shelter assignment updated.";
    }

    public Cursor getAllInventoryWithOfficial() {
        SQLiteDatabase db = this.getReadableDatabase();
        String sql = "SELECT i." + COL_ITEM_ID + ", i." + COL_PACKAGE_NAME + ", i." + COL_QTY_REMAINING
                + ", i." + COL_QTY_DISTRIBUTED + ", i." + COL_ASSIGNED_OFFICIAL_ID
                + ", o." + COL_OFF_NAME
                + " FROM " + TABLE_INVENTORY + " i"
                + " LEFT JOIN " + TABLE_OFFICIAL + " o ON i." + COL_ASSIGNED_OFFICIAL_ID + " = o." + COL_OFFICIAL_ID
                + " ORDER BY i." + COL_ITEM_ID + " ASC";
        return db.rawQuery(sql, null);
    }

    public String adjustInventoryStock(int itemId, int officialId, int delta) {
        SQLiteDatabase db = this.getWritableDatabase();
        String sql = "SELECT i." + COL_PACKAGE_NAME + ", i." + COL_QTY_REMAINING + ", i." + COL_QTY_DISTRIBUTED
                + ", i." + COL_ASSIGNED_OFFICIAL_ID + ", o." + COL_OFF_NAME + ", o." + COL_OFF_ROLE
                + " FROM " + TABLE_INVENTORY + " i"
                + " LEFT JOIN " + TABLE_OFFICIAL + " o ON i." + COL_ASSIGNED_OFFICIAL_ID + " = o." + COL_OFFICIAL_ID
                + " WHERE i." + COL_ITEM_ID + "=?";
        Cursor c = db.rawQuery(sql, new String[]{String.valueOf(itemId)});
        if (!c.moveToFirst()) {
            c.close();
            return "Package not found.";
        }
        String pkgName = c.getString(0);
        int remaining = c.getInt(1);
        int distributed = c.getInt(2);
        int assignedId = c.getInt(3);
        String assignedName = c.getString(4);
        String assignedRole = c.getString(5);
        c.close();

        if (assignedId != officialId) {
            return "RESTRICTED: Only the assigned official custodian can modify this stock.\n\nOfficial to find: "
                    + assignedName + " (" + assignedRole + " • ID #" + assignedId + ")";
        }
        if (delta < 0) {
            int deductAmt = Math.abs(delta);
            if (remaining <= 0) {
                return "OUT OF STOCK: 0 packs remaining.";
            }
            if (remaining - deductAmt < 0) {
                return "INSUFFICIENT STOCK: Cannot deduct " + deductAmt + " packs (only " + remaining + " remaining).";
            }
        }
        if (delta > 0 && remaining >= MAX_STOCK_LIMIT) {
            return "LIMIT REACHED: Maximum warehouse capacity is " + MAX_STOCK_LIMIT + " packs.";
        }

        int actualDelta = delta;
        if (delta > 0 && remaining + delta > MAX_STOCK_LIMIT) {
            actualDelta = MAX_STOCK_LIMIT - remaining;
        }

        int newRemaining = remaining + actualDelta;
        int newDistributed = actualDelta < 0 ? distributed + Math.abs(actualDelta) : distributed;

        ContentValues cv = new ContentValues();
        cv.put(COL_QTY_REMAINING, newRemaining);
        cv.put(COL_QTY_DISTRIBUTED, newDistributed);
        db.update(TABLE_INVENTORY, cv, COL_ITEM_ID + "=?", new String[]{String.valueOf(itemId)});

        CloudSyncManager.pushInventory(appContext, itemId, pkgName, newRemaining, newDistributed, assignedId);
        notifyDataChanged();

        if (actualDelta < 0) {
            return "SUCCESS: Deducted " + Math.abs(actualDelta) + " pack(s) (Remaining: " + newRemaining + ")";
        } else if (actualDelta < delta) {
            return "CAPPED AT LIMIT (" + MAX_STOCK_LIMIT + "): Added +" + actualDelta + " packs (Remaining: " + newRemaining + ")";
        } else {
            return "SUCCESS: Restocked +" + actualDelta + " packs (Remaining: " + newRemaining + " / " + MAX_STOCK_LIMIT + ")";
        }
    }

    public Cursor getAllReliefLogs() {
        SQLiteDatabase db = this.getReadableDatabase();
        String sql = "SELECT d." + COL_DIST_ID + ", r." + COL_RES_FULL_NAME + ", r." + COL_RESIDENT_ID
                + ", i." + COL_PACKAGE_NAME + ", o." + COL_OFF_NAME + ", d." + COL_DATE_CLAIMED + ", d." + COL_CLAIM_STATUS
                + " FROM " + TABLE_RELIEF + " d"
                + " LEFT JOIN " + TABLE_RESIDENT + " r ON d." + COL_DIST_RESIDENT_ID + " = r." + COL_RESIDENT_ID
                + " LEFT JOIN " + TABLE_INVENTORY + " i ON d." + COL_DIST_ITEM_ID + " = i." + COL_ITEM_ID
                + " LEFT JOIN " + TABLE_OFFICIAL + " o ON d." + COL_DIST_OFFICIAL_ID + " = o." + COL_OFFICIAL_ID
                + " ORDER BY d." + COL_DIST_ID + " DESC";
        return db.rawQuery(sql, null);
    }

    public Cursor getReliefStatusForResident(int residentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String sql = "SELECT i." + COL_ITEM_ID + ", i." + COL_PACKAGE_NAME + ", i." + COL_QTY_REMAINING
                + ", o." + COL_OFF_NAME + ", d." + COL_DATE_CLAIMED + ", d." + COL_CLAIM_STATUS
                + " FROM " + TABLE_INVENTORY + " i"
                + " LEFT JOIN " + TABLE_OFFICIAL + " o ON i." + COL_ASSIGNED_OFFICIAL_ID + " = o." + COL_OFFICIAL_ID
                + " LEFT JOIN " + TABLE_RELIEF + " d ON i." + COL_ITEM_ID + " = d." + COL_DIST_ITEM_ID
                + " AND d." + COL_DIST_RESIDENT_ID + " = ?"
                + " ORDER BY i." + COL_ITEM_ID + " ASC";
        return db.rawQuery(sql, new String[]{String.valueOf(residentId)});
    }

    public String distributeReliefPackage(int residentId, int officialId, int itemId) {
        SQLiteDatabase db = this.getWritableDatabase();

        Cursor resCheck = db.query(TABLE_RESIDENT, new String[]{COL_RES_FULL_NAME},
                COL_RESIDENT_ID + "=?", new String[]{String.valueOf(residentId)}, null, null, null);
        if (!resCheck.moveToFirst()) {
            resCheck.close();
            return "INVALID RESIDENT ID: Resident #" + residentId + " not found.";
        }
        String resName = resCheck.getString(0);
        resCheck.close();

        String dupSql = "SELECT d." + COL_DATE_CLAIMED + ", o." + COL_OFF_NAME + ", o." + COL_OFF_ROLE
                + " FROM " + TABLE_RELIEF + " d"
                + " LEFT JOIN " + TABLE_OFFICIAL + " o ON d." + COL_DIST_OFFICIAL_ID + " = o." + COL_OFFICIAL_ID
                + " WHERE d." + COL_DIST_RESIDENT_ID + "=? AND d." + COL_DIST_ITEM_ID + "=? AND d." + COL_CLAIM_STATUS + "='CLAIMED'";
        Cursor dupCheck = db.rawQuery(dupSql, new String[]{String.valueOf(residentId), String.valueOf(itemId)});
        if (dupCheck.moveToFirst()) {
            String claimedAt = dupCheck.getString(0);
            String releasedBy = dupCheck.getString(1);
            String releasedRole = dupCheck.getString(2);
            dupCheck.close();
            return "BLOCKED (ANTI-DOUBLE CLAIM): " + resName + " already claimed this relief package on " + claimedAt
                    + ".\n\nReleased by Official: " + releasedBy + " (" + releasedRole + ")";
        }
        dupCheck.close();

        String invSql = "SELECT i." + COL_PACKAGE_NAME + ", i." + COL_QTY_REMAINING + ", i." + COL_QTY_DISTRIBUTED
                + ", i." + COL_ASSIGNED_OFFICIAL_ID + ", o." + COL_OFF_NAME + ", o." + COL_OFF_ROLE
                + " FROM " + TABLE_INVENTORY + " i"
                + " LEFT JOIN " + TABLE_OFFICIAL + " o ON i." + COL_ASSIGNED_OFFICIAL_ID + " = o." + COL_OFFICIAL_ID
                + " WHERE i." + COL_ITEM_ID + "=?";
        Cursor invCheck = db.rawQuery(invSql, new String[]{String.valueOf(itemId)});
        if (!invCheck.moveToFirst()) {
            invCheck.close();
            return "ERROR: Package not found.";
        }

        String pkgName = invCheck.getString(0);
        int remaining = invCheck.getInt(1);
        int distributed = invCheck.getInt(2);
        int assignedOfficial = invCheck.getInt(3);
        String assignedName = invCheck.getString(4);
        String assignedRole = invCheck.getString(5);
        invCheck.close();

        if (assignedOfficial != officialId) {
            return "RESTRICTED: Only the assigned official distributor can release this package.\n\nOfficial to find: "
                    + assignedName + " (" + assignedRole + " • Official ID #" + assignedOfficial + ")";
        }
        if (remaining <= 0) {
            return "OUT OF STOCK: No packages remaining.";
        }

        ContentValues invUpdate = new ContentValues();
        invUpdate.put(COL_QTY_REMAINING, remaining - 1);
        invUpdate.put(COL_QTY_DISTRIBUTED, distributed + 1);
        db.update(TABLE_INVENTORY, invUpdate, COL_ITEM_ID + "=?", new String[]{String.valueOf(itemId)});

        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        ContentValues distCv = new ContentValues();
        distCv.put(COL_DIST_RESIDENT_ID, residentId);
        distCv.put(COL_DIST_OFFICIAL_ID, officialId);
        distCv.put(COL_DIST_ITEM_ID, itemId);
        distCv.put(COL_DATE_CLAIMED, now);
        distCv.put(COL_CLAIM_STATUS, "CLAIMED");
        long distId = db.insert(TABLE_RELIEF, null, distCv);

        CloudSyncManager.pushInventory(appContext, itemId, pkgName, remaining - 1, distributed + 1, assignedOfficial);
        CloudSyncManager.pushReliefLog(appContext, (int) distId, residentId, officialId, itemId, now, "CLAIMED");
        notifyDataChanged();

        return "SUCCESS: Relief package released to " + resName + " and logged.";
    }
}