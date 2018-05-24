package cellulogica.wowtor.github.com.cellulogica;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import java.io.File;
import java.util.Date;
import java.util.List;

public class Database {
    private SQLiteDatabase db;

    public static File getDataPath(Context ctx) {
        return new File(ctx.getExternalFilesDir(null), "cellinfo.sqlite3");
    }

    public Database(Context ctx) {
        db = SQLiteDatabase.openOrCreateDatabase(getDataPath(ctx), null);
        createTables();
    }

    public void storeCellInfo(List<CellInfo> lst) {
        Date date = new Date();

        for (CellInfo info : lst) {
            if (info.isRegistered())
                storeCellInfo(date, info);
        }
    }

    public void storeCellInfo(Date date, CellInfo info) {
        if (info instanceof CellInfoGsm) {
            storeCellInfoGsm(date, (CellInfoGsm)info);
        } else if (info instanceof CellInfoCdma) {
            storeCellInfoCdma(date, (CellInfoCdma)info);
        } else if (info instanceof CellInfoWcdma) {
            storeCellInfoWcdma(date, (CellInfoWcdma)info);
        } else if (info instanceof CellInfoLte) {
            storeCellInfoLte(date, (CellInfoLte) info);
        } else {
            Log.w("cellulogica", "Unrecognized cell info object");
        }
    }

    private void storeCellInfoGsm(Date date, CellInfoGsm info) {
        db.execSQL(String.format("INSERT INTO cellinfogsm VALUES(%d,%d,%d,%d,%d,%d,%d,%d)",
                date.getTime(),
                info.isRegistered() ? 1 : 0,
                info.getCellIdentity().getMcc(),
                info.getCellIdentity().getMnc(),
                info.getCellIdentity().getLac(),
                info.getCellIdentity().getCid(),
                info.getCellIdentity().getBsic(),
                info.getCellIdentity().getArfcn()
        ));
    }

    private void storeCellInfoCdma(Date date, CellInfoCdma info) {
        db.execSQL(String.format("INSERT INTO cellinfocdma VALUES(%d,%d,%d,%d,%d,%d,%d)",
                date.getTime(),
                info.isRegistered() ? 1 : 0,
                info.getCellIdentity().getBasestationId(),
                info.getCellIdentity().getLatitude(),
                info.getCellIdentity().getLongitude(),
                info.getCellIdentity().getNetworkId(),
                info.getCellIdentity().getSystemId()
        ));
    }

    private void storeCellInfoWcdma(Date date, CellInfoWcdma info) {
        db.execSQL(String.format("INSERT INTO cellinfowcdma VALUES(%d,%d,%d,%d,%d,%d,%d,%d)",
                date.getTime(),
                info.isRegistered() ? 1 : 0,
                info.getCellIdentity().getMcc(),
                info.getCellIdentity().getMnc(),
                info.getCellIdentity().getLac(),
                info.getCellIdentity().getCid(),
                info.getCellIdentity().getPsc(),
                info.getCellIdentity().getUarfcn()
        ));
    }

    private void storeCellInfoLte(Date date, CellInfoLte info) {
        db.execSQL(String.format("INSERT INTO cellinfolte VALUES(%d,%d,%d,%d,%d,%d,%d)",
                date.getTime(),
                info.isRegistered() ? 1 : 0,
                info.getCellIdentity().getMcc(),
                info.getCellIdentity().getMnc(),
                info.getCellIdentity().getTac(),
                info.getCellIdentity().getCi(),
                info.getCellIdentity().getPci()
        ));
    }

    public void dropTables() {
        db.execSQL("DROP TABLE IF EXISTS cellinfogsm");
        db.execSQL("DROP TABLE IF EXISTS cellinfocdma");
        db.execSQL("DROP TABLE IF EXISTS cellinfowcdma");
        db.execSQL("DROP TABLE IF EXISTS cellinfolte");
    }

    private void createTables() {
        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfogsm ("+
                "  date INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  mcc INT NOT NULL,"+
                "  mnc INT NOT NULL,"+
                "  lac INT NOT NULL,"+ // Location Area Code
                "  cid INT NOT NULL,"+ // Cell Identity
                "  bsid INT NOT NULL,"+ // Base Station Identity
                "  arfcn INT NOT NULL"+ // Absolute RF Channel Number
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfocdma ("+
                "  date INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  basestationid INT NOT NULL,"+
                "  latitude INT NOT NULL,"+
                "  longitude INT NOT NULL,"+
                "  networkid INT NOT NULL,"+
                "  systemid INT NOT NULL"+
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfowcdma ("+
                "  date INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  mcc INT NOT NULL,"+
                "  mcn INT NOT NULL,"+
                "  lac INT NOT NULL,"+
                "  cid INT NOT NULL,"+
                "  psc INT NOT NULL,"+ // 9-bit UMTS Primary Scrambling Code described in TS 25.331
                "  uarfcn INT NOT NULL"+ // 16-bit UMTS Absolute RF Channel Number
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS cellinfolte ("+
                "  date INT NOT NULL,"+
                "  registered INT NOT NULL,"+
                "  mcc INT NOT NULL,"+
                "  mcn INT NOT NULL,"+
                "  tac INT NOT NULL,"+
                "  ci INT NOT NULL,"+ // 28-bit Cell Identity
                "  pci INT NOT NULL"+ // Physical Cell Id 0..503, Integer.MAX_VALUE if unknown
                ")");
    }
}
