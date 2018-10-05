package cellscanner.wowtor.github.com.cellscanner;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.SyncStateContract;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class LocationService extends Service {
    private static int NOTIFICATION_CELLINFO = 0;
    private static int NOTIFICATION_STATUS = 1;
    private static int NOTIFICATION_ERROR = 2;
    private static int NOTIFICATION_FOREGROUND = 3;

    private TelephonyManager mTelephonyManager;
    private Database db;

    private static boolean running = false;

    //private static final boolean PREFER_BACKGROUND_SERVICE = false;
    private static final boolean PREFER_FOREGROUND_SERVICE = true;

    private interface ServiceHelper {
        void startService(Context ctx);
        void stopService(Context ctx);
        void setup(LocationService service);
        void cleanup(LocationService service);
    }

    private static ServiceHelper getHelper() {
        if (PREFER_FOREGROUND_SERVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new ServiceHelper() {

                @Override
                public void startService(Context ctx) {
                    ContextCompat.startForegroundService(ctx, new Intent(ctx, LocationService.class));
                }

                @Override
                public void stopService(Context ctx) {
                    ctx.stopService(new Intent(ctx, LocationService.class));
                }

                @Override
                public void setup(LocationService service) {
                    service.createNotificationChannel();

                    Intent intent = new Intent(service, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, intent, 0);

                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(service, "default-channel")
                            .setContentTitle(App.TITLE)
                            .setContentText("running")
                            .setSmallIcon(cellscanner.wowtor.github.com.cellscanner.R.drawable.ic_launcher_foreground)
                            .setContentIntent(pendingIntent)
                            .setPriority(NotificationCompat.PRIORITY_MIN);

                    service.startForeground(NOTIFICATION_FOREGROUND, mBuilder.build());
                }

                @Override
                public void cleanup(LocationService service) {
                    service.stopForeground(true); //true will remove notification
                }
            };
        } else {
            return new ServiceHelper() {
                @Override
                public void startService(Context ctx) {
                    ctx.startService(new Intent(ctx, LocationService.class));
                }

                @Override
                public void stopService(Context ctx) {
                    ctx.stopService(new Intent(ctx, LocationService.class));
                }

                @Override
                public void setup(LocationService service) {
                }

                @Override
                public void cleanup(LocationService service) {
                }
            };
        }
    }

    public static void start(Context ctx) {
        running = true;
        getHelper().startService(ctx);
    }

    public static void stop(Context ctx) {
        running = false;
        getHelper().stopService(ctx);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    public String getDataPath() {
        PackageManager m = getPackageManager();
        String s = getPackageName();
        PackageInfo p = null;
        try {
            p = m.getPackageInfo(s, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(App.TITLE, "Package name not found", e);
            return "cellinfo.sqlite";
        }

        return p.applicationInfo.dataDir + "/cellinfo.sqlite3";
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.v(App.TITLE, getClass().getName()+".onBind()");
        return null;
    }

    private void scheduleRestart(int delay_millis) {
        Context context = getApplicationContext();
        Intent intent = new Intent(context, LocationService.class);

        if (delay_millis == 0) {
            startActivity(intent);
        } else {
            PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay_millis, pendingIntent);
        }
    }

    @Override
    public void onCreate() {
        running = true;

        getHelper().setup(this);

        Log.v(App.TITLE, getClass().getName()+".onCreate()");
        Log.v(App.TITLE, "using db: "+getDataPath());
        db = App.getDatabase();
        db.storePhoneID(getApplicationContext());
        db.storeVersionCode(getApplicationContext());
        Toast.makeText(this, "using db: "+getDataPath(), Toast.LENGTH_SHORT);

        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //mTelephonyManager.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_CELL_INFO  | PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SERVICE_STATE);

        // schedule a periodic update
        final Handler handler = new Handler();
        Runnable timer = new Runnable() {
            @Override
            public void run() {
                if (running) {
                    Log.v(App.TITLE, "Update cell info");
                    updateCellInfo();
                    handler.postDelayed(this, App.UPDATE_DELAY_MILLIS);
                }
            }
        };
        handler.post(timer);
    }

    @Override
    public void onDestroy() {
        getHelper().cleanup(this);

        Log.v(App.TITLE, getClass().getName()+".onDestroy()");

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(NOTIFICATION_CELLINFO);

        updateServiceStatus("service killed (restarting)");

        if (running)
            scheduleRestart(0);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_MIN;
            NotificationChannel channel = new NotificationChannel("default-channel", App.TITLE, importance);
            channel.setDescription("notification channel");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateNotification(int notification_id, NotificationCompat.Builder mBuilder) {
        createNotificationChannel();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        mBuilder = mBuilder
                .setSmallIcon(cellscanner.wowtor.github.com.cellscanner.R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(notification_id, mBuilder.build());
    }

    private void updateServiceStatus(String msg) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "default-channel")
                .setContentTitle("Network cell")
                .setContentText(msg);

        updateNotification(NOTIFICATION_STATUS, mBuilder);
    }

    private void sendErrorNotification(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "default-channel")
                .setContentTitle("Error")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(sw.toString()))
                .setContentText(e.toString());

        updateNotification(NOTIFICATION_ERROR, mBuilder);
    }

    @SuppressLint("MissingPermission")
    private void updateCellInfo() {
        List<CellInfo> cellinfo = mTelephonyManager.getAllCellInfo();
        String[] cellstr;
        try {
            cellstr = db.storeCellInfo(cellinfo);
            if (cellstr.length == 0)
                cellstr = new String[]{"no data"};
        } catch(Throwable e) {
            Toast.makeText(this, "error: "+e, Toast.LENGTH_SHORT);
            sendErrorNotification(e);
            cellstr = new String[]{"error"};
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "default-channel")
                .setContentTitle("Network cell")
                .setContentText(String.format("%d cells", cellinfo.size()))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(TextUtils.join("\n", cellstr)))
                .setTimeoutAfter(App.EVENT_VALIDITY_MILLIS);

        updateNotification(NOTIFICATION_CELLINFO, mBuilder);
    }

    private class MyPhoneStateListener extends PhoneStateListener {
        /**
         * This does not seem to receive meaningful calls.
         *
         * @param cellInfo
         */
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            if (cellInfo == null)
                Log.v(App.TITLE,"Cell info: no data");
            else {
                Log.v(App.TITLE, String.format("Cell info list: %d", cellInfo.size()));
                for (CellInfo info : cellInfo) {
                    Log.v(App.TITLE, String.format("Cell info: %s", info.toString()));
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
                Log.v(App.TITLE, "Cell location: null");
            } else if (location instanceof GsmCellLocation) {
                GsmCellLocation gsm = (GsmCellLocation) location;
            } else if (location instanceof CdmaCellLocation) {
                CdmaCellLocation cdma = (CdmaCellLocation) location;
            } else {
                // other
            }

            Log.v(App.TITLE, "cell location changed");
        }

        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState == null) {
                Log.v(App.TITLE, "Service state: null");
            } else {
                Log.v(App.TITLE, String.format("Service state: operator=%s", serviceState.getOperatorNumeric()));
            }
        }
    }
}
