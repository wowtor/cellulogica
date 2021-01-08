package cellscanner.wowtor.github.com.cellscanner;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class service extends Service {

    private static final String TAG_BOOT_EXECUTE_SERVICE = "BOOT_BROADCAST_SERVICE";

    public service() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG_BOOT_EXECUTE_SERVICE, "TESTAPP RunAfterBootService onCreate() method.");
        String message = "TESTAPP onCreate";

        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String message = "TESTAPP RunAfterBootService onStartCommand() method.";

        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();

        Log.d(TAG_BOOT_EXECUTE_SERVICE, "TESTAPP RunAfterBootService onStartCommand() method.");

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}