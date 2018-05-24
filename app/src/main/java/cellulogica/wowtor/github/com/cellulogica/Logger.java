package cellulogica.wowtor.github.com.cellulogica;

import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Logger {
    public interface LogListener {
        public void logMessage(LogMessage msg, Logger logger);
    }

    private LinkedList<LogMessage> lines = new LinkedList<LogMessage>();
    private int maxlines = 100;
    private static SimpleDateFormat datefmt = new SimpleDateFormat("HH:mm:ss");
    private List<LogListener> listeners = new ArrayList<LogListener>();

    public static class LogMessage {
        public Date date;
        public String msg;

        public LogMessage(String msg) {
            date = new Date();
            this.msg = msg;
        }
    }

    public void listen(LogListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer();
        for (LogMessage line : lines) {
            buf.append(String.format("%s %s", datefmt.format(line.date), line.msg));
            buf.append('\n');
        }

        return buf.toString();
    }

    public void message(String s) {
        LogMessage msg = new LogMessage(s);
        lines.add(0, msg);
        while (lines.size() > maxlines)
            lines.pop();

        for (LogListener listener : listeners)
            listener.logMessage(msg, this);
    }

}
