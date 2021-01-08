package nl.nfi.cellscanner;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Database {
    protected static final int VERSION = 2;

    private static final String META_VERSION_CODE = "version_code";
    private static final String META_GLOBAL_USER_ID = "global_user_id";
    private static final String META_CELL_RECORDING_STATUS = "cell_recording_status";
    private static final String META_GPS_RECORDING_STATUS = "gps_recording_status";

    private final SQLiteDatabase db;

    public static File getDataPath(Context ctx) {
        return new File(ctx.getExternalFilesDir(null), "cellinfo.sqlite3");
    }

    public Database(SQLiteDatabase db) {
        this.db = db;
    }

    public void close() {
        db.close();
    }

    public void setCellRecordingStatus(boolean value) {
        setMetaEntry(META_CELL_RECORDING_STATUS, Boolean.toString(value));
    }

    public boolean getCellRecordingStatus() {
        return Boolean.valueOf(getMetaEntry(META_CELL_RECORDING_STATUS, Boolean.toString(false)));
    }

    public void setGpsRecordingStatus(boolean value) {
        setMetaEntry(META_GPS_RECORDING_STATUS, Boolean.toString(value));
    }

    public boolean getGpsRecordingStatus() {
        return Boolean.valueOf(getMetaEntry(META_GPS_RECORDING_STATUS, Boolean.toString(false)));
    }

    private Long getLongFromSQL(String query) {
        try (Cursor c = db.rawQuery(query, new String[]{})) {
            c.moveToNext();
            if (c.isNull(0)) {
                return null;
            } else {
                return c.getLong(0);
            }
        }
    }

    private String[] getActiveCells(Date date) {
        List<String> cells = new ArrayList<>();
        if (date != null) {
            try (Cursor c = db.rawQuery("SELECT radio, mcc, mnc, area, cid FROM cell_status WHERE ? BETWEEN date_start AND date_end", new String[]{Long.toString(date.getTime())})) {
                while (c.moveToNext()) {
                    String radio = c.getString(0);
                    int mcc = c.getInt(1);
                    int mnc = c.getInt(2);
                    int lac = c.getInt(3);
                    int cid = c.getInt(4);
                    cells.add(String.format(Locale.ROOT, "%s: %d-%d-%d-%d", radio, mcc, mnc, lac, cid));
                }
            }
        }

        return cells.toArray(new String[]{});
    }

    private Date getTimestampFromSQL(String query) {
        Long v = getLongFromSQL(query);
        return v == null ? null : new Date(v);
    }

    private Date getLastUpdateTime(String table) {
        return getTimestampFromSQL("SELECT MAX(date_end) FROM "+table);
    }

    private boolean isContiguous(Date first, Date second) {
        if (first == null || second == null)
            return false;

        return second.getTime() < first.getTime() + App.EVENT_VALIDITY_MILLIS;
    }

    public String getUpdateStatus() {
        DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

        Date last_update_time = getLastUpdateTime("cell_status");
        String[] current_cells = getActiveCells(last_update_time);

        StringBuilder s = new StringBuilder();
        s.append(String.format("updated: %s\n", last_update_time == null ? "never" : fmt.format(last_update_time)));
        for (String cell: current_cells) {
            s.append(String.format("current cell: %s\n", cell));
        }

        return s.toString();
    }

    /**
     * Update the current recording status.
     *
     * @param date current date
     * @param record_cell current cell recording status
     * @param record_gps current GPS recording status
     */
    public void updateRecordingStatus(Date date, boolean record_cell, boolean record_gps) {
        Date previous_date = getLastUpdateTime("recording_status");
        boolean contiguous = isContiguous(previous_date, date);

        // if the previous registration has the same values, update the end time only
        if (contiguous) {
            ContentValues update = new ContentValues();
            update.put("date_end", date.getTime());

            String qwhere = "date_end = ? AND record_cell = ? AND record_gps = ?";
            String[] qargs = new String[]{Long.toString(previous_date.getTime()), record_cell ? "1" : "0", record_gps ? "1" : "0"};

            int nrows = db.update("recording_status", update, qwhere, qargs);
            if (nrows > 0)
                return;
        }

        // if there is no previous registration to be updated, create a new one
        ContentValues insert = new ContentValues();
        insert.put("date_start", date.getTime());
        insert.put("date_end", date.getTime());
        insert.put("record_cell", record_cell ? 1 : 0);
        insert.put("record_gps", record_gps ? 1 : 0);
        Log.v(App.TITLE, "new recording status: "+insert);
        db.insert("recording_status", null, insert);
    }

    /**
     * Update the current cellular connection status.
     *
     * @param date the current date
     * @param status the cellular connection status
     */
    public void updateCellStatus(Date date, CellStatus status) {
        Date previous_date = getLastUpdateTime("cell_status");
        boolean contiguous = isContiguous(previous_date, date);

        ContentValues values = status.getContentValues();

        // if the previous registration has the same values, update the end time only
        if (contiguous) {
            ContentValues update = new ContentValues();
            update.put("date_end", date.getTime());

            ArrayList<String> qwhere = new ArrayList<>();
            ArrayList<String> qargs = new ArrayList<>();
            qwhere.add("date_end = ?");
            qargs.add(Long.toString(previous_date.getTime()));
            for (String key : values.keySet()) {
                qwhere.add(String.format("%s = ?", key));
                if (key.equals("registered"))
                    qargs.add("1");
                else
                    qargs.add(values.getAsString(key));
            }

            int nrows = db.update("cell_status", update, TextUtils.join(" AND ", qwhere), qargs.toArray(new String[0]));
            if (nrows > 0)
                return;
        }

        // if there is no previous registration to be updated, create a new one
        ContentValues insert = new ContentValues(values);
        insert.put("date_start", date.getTime());
        insert.put("date_end", date.getTime());
        Log.v(App.TITLE, "new cell: "+insert.toString());
        db.insert("cell_status", null, insert);
    }

    /**
     * Update the current GPS status.
     *
     * @param date the current date
     * @param location the current location
     */
    public void updateGpsStatus(Date date, Location location) {
        Date previous_date = getLastUpdateTime("gps_status");
        boolean contiguous = isContiguous(previous_date, date);

        // if the previous registration has the same values, update the end time only
        if (contiguous) {
            ContentValues update = new ContentValues();
            update.put("date_end", date.getTime());

            ArrayList<String> qwhere = new ArrayList<>();
            ArrayList<String> qargs = new ArrayList<>();
            qwhere.add("date_end = ? AND (latitude BETWEEN ? AND ?) AND (longitude BETWEEN ? AND ?) AND (altitude BETWEEN ? AND ?)");
            qargs.add(Long.toString(previous_date.getTime()));
            qargs.add(Double.toString(location.getLongitude() - App.GPS_TOLERANCE_X));
            qargs.add(Double.toString(location.getLongitude() + App.GPS_TOLERANCE_X));
            qargs.add(Double.toString(location.getLatitude() - App.GPS_TOLERANCE_Y));
            qargs.add(Double.toString(location.getLatitude() + App.GPS_TOLERANCE_Y));
            qargs.add(Double.toString(location.getAltitude() - App.GPS_TOLERANCE_Z));
            qargs.add(Double.toString(location.getAltitude() + App.GPS_TOLERANCE_Z));

            int nrows = db.update("gps_status", update, TextUtils.join(" AND ", qwhere), qargs.toArray(new String[0]));
            if (nrows > 0)
                return;
        }

        // if there is no previous registration to be updated, create a new one
        ContentValues insert = new ContentValues();
        insert.put("date_start", date.getTime());
        insert.put("date_end", date.getTime());
        insert.put("longitude", location.getLongitude());
        insert.put("latitude", location.getLatitude());
        insert.put("altitude", location.getAltitude());
        insert.put("accuracy", location.getAccuracy());

        if (Build.VERSION.SDK_INT >= 26)
            insert.put("vertical_accuracy", location.getVerticalAccuracyMeters());

        Log.v(App.TITLE, "new GPS location: "+insert);
        db.insert("gps_status", null, insert);
    }

    protected String getMetaEntry(String name) {
        return getMetaEntry(name, null);
    }

    protected String getMetaEntry(String name, String default_value) {
        try (Cursor c = db.query("meta", new String[]{"value"}, "entry = ?", new String[]{name}, null, null, null)) {
            if (!c.moveToNext())
                return default_value;

            return c.getString(0);
        }
    }

    protected void setMetaEntry(String name, String value) {
        ContentValues content = new ContentValues();
        content.put("entry", name);
        content.put("value", value);
        int n = db.update("meta", content, "entry = ?", new String[]{name});
        if (n == 0)
            db.insert("meta", null, content);
    }

    protected void storeVersionCode(Context ctx)
    {
        PackageInfo pInfo;
        try {
            pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("cellscanner", "error", e);
            return;
        }

        int versionCode = pInfo.versionCode;

        setMetaEntry(META_VERSION_CODE, Integer.toString(versionCode));
    }

    protected int getVersionCode() {
        String versionCode = getMetaEntry("versionCode");
        if (versionCode == null)
            return -1;
        else
            return Integer.parseInt(versionCode);
    }

    protected void storePhoneID(Context ctx) {
        // TODO: replace by some random number
        String android_id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        setMetaEntry(META_GLOBAL_USER_ID, android_id);
    }

    public void storeMessage(String msg) {
        // reduce length if necessary to prevent database errors
        if (msg.length() > 250)
            msg = msg.substring(0, 250);

        ContentValues content = new ContentValues();
        content.put("date", new Date().getTime());
        content.put("message", msg);
        db.insert("message", null, content);
    }

    protected void upgrade(int oldVersion, int newVersion, Context ctx) {
        createTables(db);
        storeVersionCode(ctx);
    }

    protected static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meta ("+
                "  entry VARCHAR(200) NOT NULL PRIMARY KEY,"+
                "  value TEXT NOT NULL"+
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS message ("+
                "  date INT NOT NULL,"+
                "  message VARCHAR(250) NOT NULL"+
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS recording_status ("+
                "  date_start INT NOT NULL,"+
                "  date_end INT NOT NULL,"+
                "  record_cell BOOL NOT NULL,"+
                "  record_gps BOOL NOT NULL"+
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS gps_status ("+
                "  date_start INT NOT NULL,"+
                "  date_end INT NOT NULL,"+
                "  longitude FLOAT NOT NULL,"+
                "  latitude FLOAT NOT NULL,"+
                "  accuracy FLOAT NOT NULL,"+
                "  altitude FLOAT NOT NULL,"+
                "  vertical_accuracy FLOAT"+
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS cell_status ("+
                "  date_start INT NOT NULL,"+
                "  date_end INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  radio VARCHAR(10) NOT NULL,"+ // radio technology (GSM, UMTS, LTE)
                "  mcc INT NOT NULL,"+
                "  mnc INT NOT NULL,"+
                "  area INT NOT NULL,"+ // Location Area Code (GSM, UMTS) or TAC (LTE)
                "  cid INT NOT NULL,"+ // Cell Identity (GSM: 16 bit; LTE: 28 bit)
                "  bsic INT,"+ // Base Station Identity Code (GSM only)
                "  arfcn INT,"+ // Absolute RF Channel Number (GSM only)
                "  psc INT,"+ // 9-bit UMTS Primary Scrambling Code described in TS 25.331 (UMTS only/)
                "  uarfcn INT,"+ // 16-bit UMTS Absolute RF Channel Number (UMTS only)
                "  pci INT"+ // Physical Cell Id 0..503, Integer.MAX_VALUE if unknown (LTE only)
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS cdma_status ("+
                "  date_start INT NOT NULL,"+
                "  date_end INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  basestationid INT NOT NULL,"+
                "  latitude INT NOT NULL,"+
                "  longitude INT NOT NULL,"+
                "  networkid INT NOT NULL,"+
                "  systemid INT NOT NULL"+
                ")");
    }
}
