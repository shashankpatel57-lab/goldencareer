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
    private static volatile String httpUrl;
    private static volatile String ftpUrl;
    private static volatile String pin = "------";
    private static volatile HttpFileServer httpServer;
    private static volatile FtpFileServer ftpServer;
    private static PowerManager.WakeLock wakeLock;
    private static WifiManager.WifiLock wifiLock;

    @Override public void onCreate() {
        super.onCreate();
        startForeground(1001, buildNotification("Starting direct transfer server…"));
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

            httpServer = new HttpFileServer(root, pin);
            httpServer.start();

            ftpServer = new FtpFileServer(root);
            ftpServer.start();

            String ip = httpServer.getBestIpAddress();
            httpUrl = "http://" + ip + ":" + httpServer.getPort();
            ftpUrl = "ftp://" + ip + ":" + ftpServer.getPort() + "/";
            running = true;

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(1001, buildNotification("Windows Explorer: " + ftpUrl));
        } catch (Exception e) {
            running = false;
            httpUrl = null;
            ftpUrl = null;
            stopServer();
            stopForeground(true);
            stopSelf();
        }
    }

    private synchronized void stopServer() {
        running = false;
        httpUrl = null;
        ftpUrl = null;
        if (httpServer != null) {
            try { httpServer.stop(); } catch (Exception ignored) {}
            httpServer = null;
        }
        if (ftpServer != null) {
            try { ftpServer.stop(); } catch (Exception ignored) {}
            ftpServer = null;
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
        return b.setContentTitle("FlashDrop Direct")
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

    public static boolean isRunning() { return running && httpServer != null && ftpServer != null; }
    public static String getHttpUrl() { return httpUrl; }
    public static String getFtpUrl() { return ftpUrl; }
    public static String getPin() { return pin; }
    public static long getBytesServed() {
        long a = httpServer == null ? 0L : httpServer.getBytesServed();
        long b = ftpServer == null ? 0L : ftpServer.getBytesServed();
        return a + b;
    }
    public static int getActiveTransfers() {
        int a = httpServer == null ? 0 : httpServer.getActiveTransfers();
        int b = ftpServer == null ? 0 : ftpServer.getActiveTransfers();
        return a + b;
    }
}
