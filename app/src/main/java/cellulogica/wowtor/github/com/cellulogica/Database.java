package cellulogica.wowtor.github.com.cellulogica;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Database {
    private SQLiteDatabase db;
    private Date previous_date = null;
    private long _update_tolerance_millis;

    public static File getDataPath(Context ctx) {
        return new File(ctx.getExternalFilesDir(null), "cellinfo.sqlite3");
    }

    public Database(int update_tolerance_millis) {
        _update_tolerance_millis = update_tolerance_millis;
        db = App.getDatabase();
    }

    public String getUpdateStatus() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");

        StringBuffer s = new StringBuffer();
        for (String tab : new String[]{"cellinfogsm", "cellinfocdma", "cellinfowcdma", "cellinfolte"}) {
            Cursor c = db.rawQuery("SELECT MAX(date_end) FROM cellinfogsm", new String[]{});
            if (!c.isNull(0)) {
                Date d = new Date(c.getInt(0));
                s.append(String.format("%s\n", fmt.format(d)));
            }
        }

        return s.toString();
    }

    public String[] storeCellInfo(List<CellInfo> lst) {
        Date date = new Date();

        List<String> cells = new ArrayList<String>();
        for (CellInfo info : lst) {
            if (info.isRegistered()) // unregistered cell info objects tend to contain no useful data
                cells.add(storeCellInfo(date, info));
        }

        previous_date = date;
        return cells.toArray(new String[0]);
    }

    public String storeCellInfo(Date date, CellInfo info) {
        if (info instanceof CellInfoGsm) {
            return storeCellInfoGsm(date, (CellInfoGsm)info);
        } else if (info instanceof CellInfoCdma) {
            return storeCellInfoCdma(date, (CellInfoCdma)info);
        } else if (info instanceof CellInfoWcdma) {
            return storeCellInfoWcdma(date, (CellInfoWcdma)info);
        } else if (info instanceof CellInfoLte) {
            return storeCellInfoLte(date, (CellInfoLte) info);
        } else {
            Log.w("cellulogica", "Unrecognized cell info object");
            return "unrecognized";
        }
    }

    private void updateCellInfo(String table, Date date, ContentValues values) {
        if (previous_date != null && date.getTime() < previous_date.getTime() + _update_tolerance_millis) {
            ContentValues update = new ContentValues();
            update.put("date_end", date.getTime());

            ArrayList<String> qwhere = new ArrayList<String>();
            ArrayList<String> qargs = new ArrayList<String>();
            qwhere.add("date_end = ?");
            qargs.add(Long.toString(previous_date.getTime()));
            for (String key : values.keySet()) {
                qwhere.add(String.format("%s = ?", key));
                qargs.add(values.getAsString(key));
            }

            int nrows = db.update(table, update, TextUtils.join(" AND ", qwhere), qargs.toArray(new String[0]));
            if (nrows > 0)
                return;
        }

        ContentValues insert = new ContentValues(values);
        insert.put("date_start", date.getTime());
        insert.put("date_end", date.getTime());
        Log.v("cellulogica", "new cell: "+insert);
        db.insert(table, null, insert);
    }

    private static String toString(CellInfoGsm info) {
        return String.format("%s/gsm:%d-%d-%d-%d", info.isRegistered() ? "reg" : "unreg", info.getCellIdentity().getMcc(), info.getCellIdentity().getMnc(), info.getCellIdentity().getLac(), info.getCellIdentity().getCid());
    }

    private static String toString(CellInfoCdma info) {
        return String.format("%s/cdma:%d-%d", info.isRegistered() ? "reg" : "unreg", info.getCellIdentity().getBasestationId(), info.getCellIdentity().getNetworkId());
    }

    private static String toString(CellInfoWcdma info) {
        return String.format("%s/wcdma:%d-%d-%d-%d", info.isRegistered() ? "reg" : "unreg", info.getCellIdentity().getMcc(), info.getCellIdentity().getMnc(), info.getCellIdentity().getLac(), info.getCellIdentity().getCid());
    }

    private static String toString(CellInfoLte info) {
        return String.format("%s/lte:%d-%d-%d-%d", info.isRegistered() ? "reg" : "unreg", info.getCellIdentity().getMcc(), info.getCellIdentity().getMnc(), info.getCellIdentity().getTac(), info.getCellIdentity().getCi());
    }

    private String storeCellInfoGsm(Date date, CellInfoGsm info) {
        ContentValues content = new ContentValues();
        content.put("registered", info.isRegistered() ? 1 : 0);
        content.put("mcc", info.getCellIdentity().getMcc());
        content.put("mnc", info.getCellIdentity().getMnc());
        content.put("lac", info.getCellIdentity().getLac());
        content.put("cid", info.getCellIdentity().getCid());
        content.put("bsic", info.getCellIdentity().getBsic());
        content.put("arfcn", info.getCellIdentity().getArfcn());
        updateCellInfo("cellinfogsm", date, content);

        return toString(info);
    }

    private String storeCellInfoCdma(Date date, CellInfoCdma info) {
        ContentValues content = new ContentValues();
        content.put("registered", info.isRegistered() ? 1 : 0);
        content.put("basestationid", info.getCellIdentity().getBasestationId());
        content.put("latitude", info.getCellIdentity().getLatitude());
        content.put("longitude", info.getCellIdentity().getLongitude());
        content.put("networkid", info.getCellIdentity().getNetworkId());
        content.put("systemid", info.getCellIdentity().getSystemId());
        updateCellInfo("cellinfocdma", date, content);

        return toString(info);
    }

    private String storeCellInfoWcdma(Date date, CellInfoWcdma info) {
        ContentValues content = new ContentValues();
        content.put("registered", info.isRegistered() ? 1 : 0);
        content.put("mcc", info.getCellIdentity().getMcc());
        content.put("mnc", info.getCellIdentity().getMnc());
        content.put("lac", info.getCellIdentity().getLac());
        content.put("cid", info.getCellIdentity().getCid());
        content.put("psc", info.getCellIdentity().getPsc());
        content.put("uarfcn", info.getCellIdentity().getUarfcn());
        updateCellInfo("cellinfowcdma", date, content);

        return toString(info);
    }

    private String storeCellInfoLte(Date date, CellInfoLte info) {
        ContentValues content = new ContentValues();
        content.put("registered", info.isRegistered() ? 1 : 0);
        content.put("mcc", info.getCellIdentity().getMcc());
        content.put("mnc", info.getCellIdentity().getMnc());
        content.put("tac", info.getCellIdentity().getTac());
        content.put("ci", info.getCellIdentity().getCi());
        content.put("pci", info.getCellIdentity().getPci());
        updateCellInfo("cellinfolte", date, content);

        return toString(info);
    }

    public void dropTables() {
        db.execSQL("DROP TABLE IF EXISTS cellinfogsm");
        db.execSQL("DROP TABLE IF EXISTS cellinfocdma");
        db.execSQL("DROP TABLE IF EXISTS cellinfowcdma");
        db.execSQL("DROP TABLE IF EXISTS cellinfolte");
    }

    protected static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfogsm ("+
                "  date_start INT NOT NULL,"+
                "  date_end INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  mcc INT NOT NULL,"+
                "  mnc INT NOT NULL,"+
                "  lac INT NOT NULL,"+ // Location Area Code
                "  cid INT NOT NULL,"+ // Cell Identity
                "  bsic INT NOT NULL,"+ // Base Station Identity Code
                "  arfcn INT NOT NULL"+ // Absolute RF Channel Number
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfocdma ("+
                "  date_start INT NOT NULL,"+
                "  date_end INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  basestationid INT NOT NULL,"+
                "  latitude INT NOT NULL,"+
                "  longitude INT NOT NULL,"+
                "  networkid INT NOT NULL,"+
                "  systemid INT NOT NULL"+
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfowcdma ("+
                "  date_start INT NOT NULL,"+
                "  date_end INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  mcc INT NOT NULL,"+
                "  mnc INT NOT NULL,"+
                "  lac INT NOT NULL,"+
                "  cid INT NOT NULL,"+
                "  psc INT NOT NULL,"+ // 9-bit UMTS Primary Scrambling Code described in TS 25.331
                "  uarfcn INT NOT NULL"+ // 16-bit UMTS Absolute RF Channel Number
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfolte ("+
                "  date_start INT NOT NULL,"+
                "  date_end INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  mcc INT NOT NULL,"+
                "  mnc INT NOT NULL,"+
                "  tac INT NOT NULL,"+
                "  ci INT NOT NULL,"+ // 28-bit Cell Identity
                "  pci INT NOT NULL"+ // Physical Cell Id 0..503, Integer.MAX_VALUE if unknown
                ")");
    }
}
