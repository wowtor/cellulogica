package cellulogica.wowtor.github.com.cellulogica;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static android.support.v4.content.PermissionChecker.PERMISSION_GRANTED;

public class MainActivity extends AppCompatActivity {

    private Button exportButton, clearButton;
    private Switch recorderSwitch;
    //private static final int CDMA_COORDINATE_DIVISOR = 3600 * 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        exportButton = (Button)findViewById(R.id.exportButton);
        clearButton = (Button)findViewById(R.id.clearButton);
        recorderSwitch = (Switch)findViewById(R.id.recorderSwitch);

        recorderSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                exportButton.setEnabled(!isChecked);
                clearButton.setEnabled(!isChecked);
                if (isChecked)
                    startRecording();
                else
                    stopRecording();
            }
        });

        final Handler handler = new Handler();
        Runnable timer = new Runnable() {
            @Override
            public void run() {
                Log.v(App.TITLE, "Update cell info");
                updateLogViewer();;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timer);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private boolean requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            return true;
        else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
            return false;
        }
    }

    private boolean requestFilePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            return true;
        else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        for (int i=0 ; i<permissions.length ; i++) {
            if (permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION && grantResults[i] == PackageManager.PERMISSION_GRANTED)
                startRecording();
            else if (permissions[i] == Manifest.permission.READ_EXTERNAL_STORAGE && grantResults[i] == PackageManager.PERMISSION_GRANTED)
                exportData(null);
            else
                Toast.makeText(getApplicationContext(), "unknown permission granted: "+permissions[i], Toast.LENGTH_SHORT);
        }
    }

    private static String getFileTitle() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        return String.format("%s_cellinfo.sqlite3", fmt.format(new Date()));
    }

    public void exportData(View view) {
        if (requestFilePermission()) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("*/*");
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getFileTitle());
            Uri uri = Uri.fromFile(Database.getDataPath(this));
            Log.v(App.TITLE, "exists: " + Database.getDataPath(this).exists());
            sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(Intent.createChooser(sharingIntent, "Share via"));
        }
    }

    public void clearDatabase(View view) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Context ctx = getApplicationContext();
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        File path = Database.getDataPath(MainActivity.this);
                        path.delete();
                        Toast.makeText(ctx, "database deleted", Toast.LENGTH_SHORT);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //Toast.makeText(ctx, "pfew", Toast.LENGTH_SHORT);
                        break;
                }
            }
        };

        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setMessage("Drop tables. Sure?").setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }

    public void startRecording() {
        Context ctx = getApplicationContext();
        if (requestLocationPermission()) {
            LocationService.start(this);
            Toast.makeText(ctx, "Location service started", Toast.LENGTH_SHORT);
            Log.v(App.TITLE, "Location service started");
        } else {
            Toast.makeText(ctx, "no permission -- try again", Toast.LENGTH_SHORT);
        }
    }

    public void stopRecording() {
        LocationService.stop(this);
        Context ctx = getApplicationContext();
        Toast.makeText(ctx, "Location service stopped", Toast.LENGTH_SHORT);
        Log.v(App.TITLE, "Location service stopped");
        exportButton.setEnabled(true);
        clearButton.setEnabled(true);
    }

    private void updateLogViewer() {
        TextView userMessages = (TextView)findViewById(R.id.userMessages);
        Database db = new Database(-1);
        userMessages.setText(db.getUpdateStatus());
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i (App.TITLE, "service is running");
                return true;
            }
        }
        Log.i (App.TITLE, false+"");
        return false;
    }
}
