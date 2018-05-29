package cellulogica.wowtor.github.com.cellulogica;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public class LocationServiceRestarter extends BroadcastReceiver {
    private static final int TIMEOUT_IN_SECONDS = 3600;

    // this constructor is called by the alarm manager.
    public LocationServiceRestarter() {
    }

    // you can use this constructor to create the alarm.
    //  Just pass in the main activity as the context,
    //  any extras you'd like to get later when triggered
    //  and the timeout
    public LocationServiceRestarter(Context context){
        setAlarm(context);
    }

    private void setAlarm(Context context) {
        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, LocationServiceRestarter.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis());
        time.add(Calendar.SECOND, TIMEOUT_IN_SECONDS);
        alarmMgr.set(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LocationService.restartIfInactive(context);
    }
}