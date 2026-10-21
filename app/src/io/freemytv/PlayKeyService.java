package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Philips (TP Vision/TPV): the remote's Google Play button never reaches the apps or a key filter. The TV's own service
 * (TPVTvService) takes it and opens Google Play Movies, which Google shut down in 2023, and it logs every press. This
 * service follows that one log tag and opens the app picked in Settings > Remote Buttons. It filters no keys, so it adds
 * nothing to the remote's latency; being an accessibility service keeps it alive and lets it open an app from the
 * background. READ_LOGS comes from `pm grant` when Free My TV turns it on (HomeMode.remoteService).
 */
public class PlayKeyService extends AccessibilityService {
    private static final String PRESS = "LogKeyPressEvent: KeyCode=0,scan=127,";
    private volatile Process logcat;

    @Override protected void onServiceConnected() {
        Thread reader = new Thread(this::follow, "play-key");
        reader.setDaemon(true);
        reader.start();
    }

    private void follow() {
        // Only what is logged from now on: an old press in the buffer must not open anything.
        String since = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date());
        try {
            logcat = new ProcessBuilder("logcat", "-b", "main", "-v", "brief", "-T", since, "-s", "TPVTvService:D")
                    .redirectErrorStream(true).start();
            BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
            long last = 0;
            for (String line; (line = in.readLine()) != null; ) {
                if (!line.contains(PRESS)) continue;
                long now = SystemClock.elapsedRealtime();
                if (now - last < 800) continue;   // one press, one app
                last = now;
                RemoteKeys.open(this, Prefs.KEY_PHILIPS_PLAY);
            }
        } catch (IOException e) {
            AppLog.w("google play button: " + e.getMessage());
        }
    }

    @Override public void onDestroy() {
        if (logcat != null) logcat.destroy();
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}
}
