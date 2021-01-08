package nl.nfi.cellscanner;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;

public class App extends Application {
    public static final String TITLE = "cellscanner";
    public static final double GPS_TOLERANCE_X = .001;
    public static final double GPS_TOLERANCE_Y = .001;
    public static final double GPS_TOLERANCE_Z = .0005;
    public static int UPDATE_DELAY_MILLIS = 1000;
    public static int EVENT_VALIDITY_MILLIS = UPDATE_DELAY_MILLIS+20000;

    private static SQLiteOpenHelper dbhelper;

    private static final int DATABASE_VERSION = Database.VERSION;

    public static Database getDatabase() {
        return new Database(dbhelper.getWritableDatabase());
    }

    public static void resetDatabase(Context appcontext) {
        dbhelper.close();

        File path = Database.getDataPath(appcontext);
        path.delete();

        dbhelper = new OpenHelper(appcontext);
    }

    private static class OpenHelper extends SQLiteOpenHelper {
        private final Context ctx;

        public OpenHelper(Context context) {
            super(context, Database.getDataPath(context).toString(), null, DATABASE_VERSION);
            this.ctx = context;
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            Database.createTables(sqLiteDatabase);

            Database db = new Database(sqLiteDatabase);
            db.storePhoneID(ctx);
            db.storeVersionCode(ctx);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            Database db = new Database(sqLiteDatabase);
            db.upgrade(oldVersion, newVersion, ctx);
        }
    }

    @Override
    public void onCreate() {
        Log.v(App.TITLE, "STARTING APP");
        super.onCreate();
        dbhelper = new OpenHelper(getApplicationContext());
    }
}
