package nl.nfi.cellscanner;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Database {
    protected static final int VERSION = 2;

    private static final String META_VERSION_CODE = "version_code";
    private static final String META_ANDROID_ID = "android_id";

    private SQLiteDatabase db;

    public static String getFileTitle() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US);
        return String.format("%s_cellinfo.sqlite3", fmt.format(new Date()));
    }

    public static File getDataFile(Context ctx) {
        return new File(ctx.getExternalFilesDir(null), "cellinfo.sqlite3");
    }

    public Database(SQLiteDatabase db) {
        this.db = db;
    }

    private Long getLongFromSQL(String query) {
        Cursor c = db.rawQuery(query, new String[]{});
        c.moveToNext();
        if (c.isNull(0)) {
            return null;
        } else {
            return c.getLong(0);
        }
    }

    private String[] getActiveCells(Date date) {
        List<String> cells = new ArrayList<String>();
        if (date != null) {
            Cursor c = db.rawQuery("SELECT radio, mcc, mnc, area, cid FROM cellinfo WHERE ? BETWEEN date_start AND date_end", new String[]{Long.toString(date.getTime())});
            while (c.moveToNext()) {
                String radio = c.getString(0);
                int mcc = c.getInt(1);
                int mnc = c.getInt(2);
                int lac = c.getInt(3);
                int cid = c.getInt(4);
                cells.add(String.format("%s: %d-%d-%d-%d", radio, mcc, mnc, lac, cid));
            }
        }

        return cells.toArray(new String[]{});
    }

    private Date getTimestampFromSQL(String query) {
        Long v = getLongFromSQL(query);
        return v == null ? null : new Date(v);
    }

    public String getUpdateStatus() {
        DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

        Date last_update_time = getTimestampFromSQL("SELECT MAX(date_end) FROM cellinfo");
        String[] current_cells = getActiveCells(last_update_time);

        StringBuffer s = new StringBuffer();
        s.append(String.format("updated: %s\n", last_update_time == null ? "never" : fmt.format(last_update_time)));
        for (String cell: current_cells) {
            s.append(String.format("current cell: %s\n", cell));
        }

        return s.toString();
    }

    /**
     * Update the current cellular connection status.
     *
     * @param date the current date
     * @param status the cellular connection status
     */
    public void updateCellStatus(Date date, CellStatus status) {
        Date previous_date = getTimestampFromSQL("SELECT MAX(date_end) FROM cellinfo");
        boolean contiguous = previous_date != null && date.getTime() < previous_date.getTime() + App.EVENT_VALIDITY_MILLIS;

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
                qargs.add(values.getAsString(key));
            }

            int nrows = db.update("cellinfo", update, TextUtils.join(" AND ", qwhere), qargs.toArray(new String[0]));
            if (nrows > 0)
                return;
        }

        // if there is no previous registration to be updated, create a new one
        ContentValues insert = new ContentValues(values);
        insert.put("date_start", date.getTime());
        insert.put("date_end", date.getTime());
        Log.v(App.TITLE, "new cell: "+insert.toString());
        db.insert("cellinfo", null, insert);
    }

    protected String getMetaEntry(String name) {
        Cursor c = db.query("meta", new String[]{"value"}, "entry = ?", new String[]{"versionCode"}, null, null, null);
        if (!c.moveToNext())
            return null;

        return c.getString(0);
    }

    protected void setMetaEntry(String name, String value) {
        ContentValues content = new ContentValues();
        content.put("entry", name);
        content.put("value", value);
        int n = db.update("meta", content, "entry = ?", new String[]{name});
        if (n == 0)
            db.insert("meta", null, content);
    }

    public void storeVersionCode(Context ctx)
    {
        PackageInfo pInfo = null;
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
        String versionCode = getMetaEntry(META_VERSION_CODE);
        if (versionCode == null)
            return -1;
        else
            return Integer.parseInt(versionCode);
    }

    public void storePhoneID(Context ctx) {
        String android_id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        setMetaEntry(META_ANDROID_ID, android_id);
    }

    public void storeMessage(String msg) {
        // message should not exceed maximum length
        if (msg.length() > 250)
            msg = msg.substring(0, 250);

        ContentValues content = new ContentValues();
        content.put("date", new Date().getTime());
        content.put("message", msg);
        db.insert("message", null, content);
    }

    protected static void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createTables(db);
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

        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfo ("+
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

        db.execSQL("CREATE INDEX IF NOT EXISTS cellinfo_date_end ON cellinfo(date_end)");
    }

    public void dropTables() {
        db.execSQL("DROP TABLE IF EXISTS meta");
        db.execSQL("DROP TABLE IF EXISTS cellinfo");
    }
}