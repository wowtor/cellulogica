package cellscanner.wowtor.github.com.cellscanner;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class App extends Application {
    public static final String TITLE = "cellscanner";
    public static int UPDATE_DELAY_MILLIS = 4000;
    public static int EVENT_VALIDITY_MILLIS = UPDATE_DELAY_MILLIS+20000;

    private static SQLiteOpenHelper dbhelper;

    private static final int DATABASE_VERSION = 1;

    public static Database getDatabase() {
        return new Database(dbhelper.getWritableDatabase());
    }

    private static class OpenHelper extends SQLiteOpenHelper {
        public OpenHelper(Context context) {
            super(context, Database.getDataPath(context).toString(), null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            Database.createTables(sqLiteDatabase);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        dbhelper = new OpenHelper(getApplicationContext());
    }
}
