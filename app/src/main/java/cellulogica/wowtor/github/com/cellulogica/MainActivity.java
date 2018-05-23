package cellulogica.wowtor.github.com.cellulogica;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
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

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static class LogMessage {
        public Date date;
        public String msg;

        public LogMessage(String msg) {
            date = new Date();
            this.msg = msg;
        }
    }

    private LinkedList<LogMessage> lines = new LinkedList<LogMessage>();
    private Button exportButton, clearButton;
    private Switch recorderSwitch;
    private TelephonyManager mTelephonyManager;
    private boolean listening = false;
    private static SimpleDateFormat datefmt = new SimpleDateFormat("HH:mm:ss");
    //private static final int CDMA_COORDINATE_DIVISOR = 3600 * 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userMessage("hello!");

        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        requestPermission();

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

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            initializeListener();
        else
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            initializeListener();
    }

    private void initializeListener() {
            mTelephonyManager.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_CELL_INFO  | PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_SERVICE_STATE);
            listening = true;
    }

    private class MyPhoneStateListener extends PhoneStateListener {
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            if (cellInfo == null)
                userMessage("Cell info: no data");
            else {
                userMessage(String.format("Cell info list: %d", cellInfo.size()));
                for (CellInfo info : cellInfo) {
                    userMessage(String.format("Cell info: %s", info.toString()));
                }
            }
        }

        public void onCellLocationChanged(CellLocation location) {
            if (location == null) {
                userMessage("Cell location: null");
            }
            else if (location instanceof GsmCellLocation) {
                GsmCellLocation gsm = (GsmCellLocation)location;
                userMessage(String.format("Cell location: gsm psc=%d; lac=%d; cid=%d", gsm.getPsc(), gsm.getLac(), gsm.getCid()));
            }
            else if (location instanceof CdmaCellLocation) {
                CdmaCellLocation cdma = (CdmaCellLocation)location;
                userMessage(String.format("Cell location: cdma %d-%d-%d / geo: %d-%d", cdma.getSystemId(), cdma.getNetworkId(), cdma.getBaseStationId(), cdma.getBaseStationLatitude(), cdma.getBaseStationLongitude()));
            }
            else {
                userMessage(String.format("Cell location: %s", location.getClass().getName()));
            }
        }

        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState == null) {
                userMessage("Service state: null");
            } else {
                userMessage(String.format("Service state: operator=%s", serviceState.getOperatorNumeric()));
            }
        }
    }

    public void userMessage(String s) {
        lines.add(0, new LogMessage(s));
        while (lines.size() > 100)
            lines.pop();

        StringBuffer buf = new StringBuffer();
        for (LogMessage line : lines) {
            buf.append(String.format("%s %s", datefmt.format(line.date), line.msg));
            buf.append('\n');
        }

        TextView userMessages = (TextView)findViewById(R.id.userMessages);
        userMessages.setText(buf.toString());
    }

    public void exportData(View view) {
        userMessage("TODO: export");
    }

    public void clearDatabase(View view) {
        userMessage("TODO: clear");
    }

    public void startRecording() {
        if (!listening) {
            userMessage("no permission -- try again");
            requestPermission();
            return;
        }
        userMessage("TODO: Recording");
    }

    public void stopRecording() {
        userMessage("TODO: Stopped");
        exportButton.setEnabled(true);
        clearButton.setEnabled(true);
    }
}
