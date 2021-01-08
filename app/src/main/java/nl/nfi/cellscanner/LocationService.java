package nl.nfi.cellscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationService extends Service implements LocationListener {
    private static final int NOTIFICATION_ERROR = 2;
    private static final int NOTIFICATION_STATUS = 3;

    private TelephonyManager mTelephonyManager;
    private Database db;
    private NotificationCompat.Builder mBuilder;

    private static boolean running = false;

    public static void start(Context ctx) {
        running = true;
        ctx.startService(new Intent(ctx, LocationService.class));
    }

    public static void stop(Context ctx) {
        running = false;
        ctx.stopService(new Intent(ctx, LocationService.class));
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
        PackageInfo p;
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
        Log.v(App.TITLE, getClass().getName() + ".onBind()");
        return null;
    }

    @Override
    public void onCreate() {
        running = true;
        ContextCompat.startForegroundService(this, new Intent(this, LocationService.class));

        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        mBuilder = new NotificationCompat.Builder(this, "default-channel")
                .setContentTitle(App.TITLE)
                .setSmallIcon(nl.nfi.cellscanner.R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN);

        startForeground(NOTIFICATION_STATUS, mBuilder.build());

        Log.v(App.TITLE, getClass().getName() + ".onCreate()");
        Log.v(App.TITLE, "using db: " + getDataPath());
        db = App.getDatabase();
        Toast.makeText(this, "using db: " + getDataPath(), Toast.LENGTH_SHORT).show();

        // initialize telephony manager (for cell updates)
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        // start request for location updates (for GPS updates)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5000, 100, this);
        }

        // schedule a periodic update
        final Handler handler = new Handler();
        Runnable timer = new Runnable() {
            @Override
            public void run() {
                if (running) {
                    periodicUpdate();
                    handler.postDelayed(this, App.UPDATE_DELAY_MILLIS);
                }
            }
        };
        handler.post(timer);
    }

    @Override
    public void onDestroy() {
        Log.v(App.TITLE, getClass().getName() + ".onDestroy()");

        db.close();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(NOTIFICATION_STATUS);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_MIN;
            NotificationChannel channel = new NotificationChannel("default-channel", App.TITLE, importance);
            channel.setDescription("notification channel");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null)
                notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateNotification(int notification_id, NotificationCompat.Builder mBuilder) {
        createNotificationChannel();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        mBuilder = mBuilder
                .setSmallIcon(nl.nfi.cellscanner.R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(notification_id, mBuilder.build());
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

    private String[] storeCellInfo(List<CellInfo> lst) {
        Date date = new Date();

        List<String> cells = new ArrayList<>();
        for (CellInfo info : lst) {
            try {
                CellStatus status = CellStatus.fromCellInfo(info);
                if (status.isValid()) {
                    db.updateCellStatus(date, status);
                    cells.add(status.toString());
                }
            } catch (CellStatus.UnsupportedTypeException e) {
                db.storeMessage(e.getMessage());
            }
        }

        return cells.toArray(new String[0]);
    }

    @Override
    public void onLocationChanged(Location location) {
        db.updateGpsStatus(new Date(), location);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {
    }

    @Override
    public void onProviderDisabled(String s) {
    }

    private void periodicUpdate() {
        db.updateRecordingStatus(new Date(), db.getCellRecordingStatus(), db.getGpsRecordingStatus());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        List<CellInfo> cellinfo = mTelephonyManager.getAllCellInfo();
        String[] cellstr;
        try {
            cellstr = storeCellInfo(cellinfo);
            if (cellstr.length == 0)
                cellstr = new String[]{"no data"};
        } catch(Throwable e) {
            Toast.makeText(this, "error: "+e, Toast.LENGTH_SHORT).show();
            sendErrorNotification(e);
            cellstr = new String[]{"error"};
        }

        Log.v(App.TITLE, "Update cell info: "+TextUtils.join(", ", cellstr));

        mBuilder
                .setContentTitle(String.format(Locale.ROOT, "%d cells registered (%d visible)", cellstr.length, cellinfo.size()))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(TextUtils.join("\n", cellstr)));

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null)
            mNotificationManager.notify(NOTIFICATION_STATUS, mBuilder.build());
    }
}
