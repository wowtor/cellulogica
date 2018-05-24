package cellulogica.wowtor.github.com.cellulogica;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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

public class MainActivity extends AppCompatActivity implements Logger.LogListener {

    private Button exportButton, clearButton;
    private Switch recorderSwitch;
    //private static final int CDMA_COORDINATE_DIVISOR = 3600 * 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userMessage("hello!");

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
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocationService.logger.listen(this);
        updateLogViewer(LocationService.logger);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocationService.logger.removeListener(this);
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
        userMessage("perm result");
        for (int i=0 ; i<permissions.length ; i++) {
            if (permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION && grantResults[i] == PackageManager.PERMISSION_GRANTED)
                startRecording();
            if (permissions[i] == Manifest.permission.READ_EXTERNAL_STORAGE && grantResults[i] == PackageManager.PERMISSION_GRANTED)
                exportData(null);
        }
    }

    public void userMessage(String s) {
        LocationService.logger.message(s);
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
            Log.v("cellulogica", "exists: " + Database.getDataPath(this).exists());
            sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(Intent.createChooser(sharingIntent, "Share via"));
        }
    }

    public void clearDatabase(View view) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        Database db = new Database(MainActivity.this);
                        db.dropTables();
                        userMessage("database reset");
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        userMessage("pfew");
                        break;
                }
            }
        };

        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setMessage("Drop tables. Sure?").setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }

    public void startRecording() {
        if (requestLocationPermission()) {
            startService(new Intent(this, LocationService.class));
            Log.v("cellulogica", "Location service started");
        } else {
            userMessage("no permission -- try again");
        }
    }

    public void stopRecording() {
        stopService(new Intent(this, LocationService.class));
        Log.v("cellulogica", "Location service stopped");
        exportButton.setEnabled(true);
        clearButton.setEnabled(true);
    }

    @Override
    public void logMessage(Logger.LogMessage msg, Logger logger) {
        updateLogViewer(logger);
    }

    private void updateLogViewer(Logger logger) {
        TextView userMessages = (TextView)findViewById(R.id.userMessages);
        userMessages.setText(logger.toString());
    }
}
