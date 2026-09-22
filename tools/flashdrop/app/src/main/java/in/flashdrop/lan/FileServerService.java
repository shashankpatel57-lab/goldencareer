package in.flashdrop.lan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;
import java.security.SecureRandom;
import java.util.Locale;

public class FileServerService extends Service {
    public static final String ACTION_STOP = "in.flashdrop.lan.STOP";
    private static volatile boolean running = false;
    private static volatile String url;
    private static volatile String pin = "------";
    private static volatile HttpFileServer server;
    private static PowerManager.WakeLock wakeLock;
    private static WifiManager.WifiLock wifiLock;

    @Override public void onCreate() {
        super.onCreate();
        startForeground(1001, buildNotification("Starting local file server…"));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) startServer();
        return START_STICKY;
    }

    private synchronized void startServer() {
        if (running) return;
        try {
            acquireLocks();
            pin = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1_000_000));
            File root = Environment.getExternalStorageDirectory();
            server = new HttpFileServer(root, pin);
            server.start();
            running = true;
            url = "http://" + server.getBestIpAddress() + ":" + server.getPort();
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(1001, buildNotification("Open " + url + " on your PC • PIN " + pin));
        } catch (Exception e) {
            running = false;
            url = null;
            stopServer();
            stopForeground(true);
            stopSelf();
        }
    }

    private synchronized void stopServer() {
        running = false;
        url = null;
        if (server != null) {
            try { server.stop(); } catch (Exception ignored) {}
            server = null;
        }
        releaseLocks();
    }

    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlashDrop:TransferWake");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignored) {}
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FlashDrop:HighPerfWifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        } catch (Exception ignored) {}
    }

    private void releaseLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
        wakeLock = null;
        wifiLock = null;
    }

    private Notification buildNotification(String content) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "flashdrop_transfer";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(channelId, "FlashDrop transfers", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps local phone-to-PC transfers active");
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channelId) : new Notification.Builder(this);
        return b.setContentTitle("FlashDrop LAN")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    public static boolean isRunning() { return running && server != null; }
    public static String getUrl() { return url; }
    public static String getPin() { return pin; }
    public static long getBytesServed() { return server == null ? 0L : server.getBytesServed(); }
    public static int getActiveTransfers() { return server == null ? 0 : server.getActiveTransfers(); }
}
