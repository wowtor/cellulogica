package cellulogica.wowtor.github.com.cellulogica;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import java.util.List;

public class LocationService extends Service {
    protected static Logger logger = new Logger();
    private TelephonyManager mTelephonyManager;
    private Database db;
    private boolean running = false;

    public String getDataPath() {
        PackageManager m = getPackageManager();
        String s = getPackageName();
        PackageInfo p = null;
        try {
            p = m.getPackageInfo(s, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("cellulogica", "Package name not found", e);
            return "cellinfo.sqlite";
        }

        return p.applicationInfo.dataDir + "/cellinfo.sqlite3";
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.v("cellologica", getClass().getName()+".onBind()");
        return null;
    }

    @Override
    public void onCreate() {
        Log.v("cellologica", getClass().getName()+".onCreate()");
        Log.v("cellulogica", "using db: "+getDataPath());
        db = new Database(this);
        userMessage("using db: "+getDataPath());
        running = true;
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_CELL_INFO  | PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SERVICE_STATE);

        final Handler handler = new Handler();
        Runnable timer = new Runnable() {
            @Override
            public void run() {
                if (running) {
                    Log.v("cellulogica", "Update cell info");
                    updateCellInfo();
                    handler.postDelayed(this, 10000);
                }
            }
        };
        handler.post(timer);
    }

    @Override
    public void onDestroy() {
        running = false;
        Log.v("cellologica", getClass().getName()+".onDestroy()");
    }

    private void userMessage(String s) {
        logger.message(s);
    }

    @SuppressLint("MissingPermission")
    private void updateCellInfo() {
        List<CellInfo> cellinfo = mTelephonyManager.getAllCellInfo();
        db.storeCellInfo(cellinfo);
        userMessage(String.format("%d cells found", cellinfo.size()));
    }

    private class MyPhoneStateListener extends PhoneStateListener {
        /**
         * This does not seem to receive meaningful calls.
         *
         * @param cellInfo
         */
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            if (cellInfo == null)
                Log.v("cellulogica","Cell info: no data");
            else {
                Log.v("cellulogica", String.format("Cell info list: %d", cellInfo.size()));
                for (CellInfo info : cellInfo) {
                    Log.v("cellulogica", String.format("Cell info: %s", info.toString()));
                }
            }
        }

        /**
         * This method seems to receive updates for the first SIM only.
         *
         * @param location
         */
        public void onCellLocationChanged(CellLocation location) {
            if (location == null) {
                Log.v("cellulogica", "Cell location: null");
            } else if (location instanceof GsmCellLocation) {
                GsmCellLocation gsm = (GsmCellLocation) location;
                userMessage(String.format("Cell location: gsm psc=%d; lac=%d; cid=%d", gsm.getPsc(), gsm.getLac(), gsm.getCid()));
            } else if (location instanceof CdmaCellLocation) {
                CdmaCellLocation cdma = (CdmaCellLocation) location;
                userMessage(String.format("Cell location: cdma %d-%d-%d / geo: %d-%d", cdma.getSystemId(), cdma.getNetworkId(), cdma.getBaseStationId(), cdma.getBaseStationLatitude(), cdma.getBaseStationLongitude()));
            } else {
                userMessage(String.format("Cell location: %s", location.getClass().getName()));
            }
        }

        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState == null) {
                Log.v("cellulogica", "Service state: null");
            } else {
                Log.v("cellulogica", String.format("Service state: operator=%s", serviceState.getOperatorNumeric()));
            }
        }
    }
}
