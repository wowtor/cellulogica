package cellulogica.wowtor.github.com.cellulogica;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class ServiceStartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        String message = "ServiceStartReceiver onReceive, action is " + action;

        Toast.makeText(context, message, Toast.LENGTH_LONG).show();

        Log.d(App.TITLE, action);

        LocationService.start(context);
    }
}