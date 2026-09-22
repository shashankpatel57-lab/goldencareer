package in.flashdrop.lan;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView serverStatus;
    private TextView ftpAddressText;
    private TextView httpAddressText;
    private TextView pinText;
    private TextView permissionText;
    private TextView statsText;
    private Button startStop;
    private LinearLayout root;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(9, 18, 34));
        getWindow().setNavigationBarColor(Color.rgb(9, 18, 34));
        buildUi();
        requestNotificationPermissionIfNeeded();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshUi();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(245, 247, 251));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 20) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(dp(18), top + dp(18), dp(18), bottom + dp(24));
                return insets;
            });
        }

        TextView brand = text("FLASHDROP DIRECT", 13, Color.rgb(44, 94, 246), true);
        brand.setLetterSpacing(0.16f);
        root.addView(brand);

        TextView title = text("Copy phone folders like a drive", 27, Color.rgb(14, 24, 42), true);
        title.setPadding(0, dp(6), 0, 0);
        root.addView(title);

        TextView sub = text("No scripts. No ZIP. No internet. Connect the PC to your phone's 5 GHz hotspot, start the server, then open the FTP address directly in Windows File Explorer.", 15, Color.rgb(85, 96, 116), false);
        sub.setPadding(0, dp(8), 0, dp(18));
        root.addView(sub);

        LinearLayout card = card();
        root.addView(card);
        serverStatus = text("Server stopped", 16, Color.rgb(40, 52, 70), true);
        card.addView(serverStatus);

        TextView directLabel = text("WINDOWS FILE EXPLORER", 11, Color.rgb(44, 94, 246), true);
        directLabel.setLetterSpacing(0.10f);
        directLabel.setPadding(0, dp(14), 0, dp(2));
        card.addView(directLabel);

        ftpAddressText = text("ftp://—", 21, Color.rgb(22, 43, 92), true);
        card.addView(ftpAddressText);

        TextView guest = text("Login: any username + any password • Read-only", 12, Color.rgb(100, 108, 123), false);
        guest.setPadding(0, dp(5), 0, 0);
        card.addView(guest);

        TextView webLabel = text("BROWSER MODE", 11, Color.rgb(104, 113, 129), true);
        webLabel.setLetterSpacing(0.10f);
        webLabel.setPadding(0, dp(14), 0, dp(2));
        card.addView(webLabel);
        httpAddressText = text("http://—", 15, Color.rgb(72, 83, 103), true);
        card.addView(httpAddressText);

        pinText = text("Browser PIN: —", 13, Color.rgb(104, 113, 129), false);
        pinText.setPadding(0, dp(4), 0, 0);
        card.addView(pinText);

        statsText = text("0 MB sent • 0 active transfers", 13, Color.rgb(104, 113, 129), false);
        statsText.setPadding(0, dp(10), 0, 0);
        card.addView(statsText);

        startStop = button("Start direct transfer", true);
        startStop.setOnClickListener(v -> {
            if (FileServerService.isRunning()) stopServer(); else startServer();
        });
        addTopGap(root, startStop, 14);

        Button copy = button("Copy Windows Explorer address", false);
        copy.setOnClickListener(v -> copyFtpAddress());
        addTopGap(root, copy, 8);

        TextView howTitle = text("On your Windows PC", 18, Color.rgb(14, 24, 42), true);
        howTitle.setPadding(0, dp(24), 0, dp(8));
        root.addView(howTitle);
        LinearLayout how = card();
        root.addView(how);
        how.addView(text("1  Connect PC to the phone's 5 GHz hotspot.\n\n2  Open File Explorer — not Chrome/Edge.\n\n3  Click the address bar and type the FTP address shown above.\n\n4  If Windows asks for login, enter any username and any password.\n\n5  Your phone storage opens like a folder. Select DCIM, Downloads, Movies or any available folder → Copy → paste directly to E: / D: / any PC folder.\n\nWhole folders and subfolders transfer directly. No PowerShell script and no ZIP creation.", 14, Color.rgb(58, 69, 87), false));

        Button hotspot = button("Open hotspot settings", false);
        hotspot.setOnClickListener(v -> openHotspotSettings());
        addTopGap(how, hotspot, 14);

        TextView accessTitle = text("Storage access", 18, Color.rgb(14, 24, 42), true);
        accessTitle.setPadding(0, dp(24), 0, dp(8));
        root.addView(accessTitle);
        LinearLayout accessCard = card();
        root.addView(accessCard);
        permissionText = text("Checking…", 14, Color.rgb(83, 94, 111), false);
        accessCard.addView(permissionText);
        Button grant = button("Grant all-files access", false);
        grant.setOnClickListener(v -> requestStorageAccess());
        addTopGap(accessCard, grant, 12);

        TextView perf = text("High-speed local transfer", 18, Color.rgb(14, 24, 42), true);
        perf.setPadding(0, dp(24), 0, dp(8));
        root.addView(perf);
        LinearLayout perfCard = card();
        root.addView(perfCard);
        perfCard.addView(text("• Direct FTP file streaming — no compression/repacking\n• Resume support for interrupted files\n• Large transfer buffers\n• Multiple transfer sessions\n• Wi-Fi high-performance lock\n• Wake lock during server operation\n• Internet is not required", 14, Color.rgb(58, 69, 87), false));

        TextView security = text("Use the FTP mode only on your own hotspot/private Wi-Fi and stop the server when finished. The FTP view is deliberately read-only, so the PC cannot delete or overwrite phone files.", 12, Color.rgb(116, 124, 139), false);
        security.setPadding(0, dp(18), 0, 0);
        root.addView(security);

        TextView note = text("Android still blocks other apps' private /data directories and some protected Android/data content even with All files access.", 12, Color.rgb(116, 124, 139), false);
        note.setPadding(0, dp(9), 0, 0);
        root.addView(note);
    }

    private void startServer() {
        if (!hasStorageAccess()) {
            Toast.makeText(this, "Grant all-files access first", Toast.LENGTH_LONG).show();
            requestStorageAccess();
            return;
        }
        Intent i = new Intent(this, FileServerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "Starting direct transfer…", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::refreshUi, 700);
    }

    private void stopServer() {
        Intent i = new Intent(this, FileServerService.class);
        i.setAction(FileServerService.ACTION_STOP);
        startService(i);
        handler.postDelayed(this::refreshUi, 300);
    }

    private void copyFtpAddress() {
        String url = FileServerService.getFtpUrl();
        if (url == null) {
            Toast.makeText(this, "Start the server first", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("FlashDrop Windows address", url));
        Toast.makeText(this, "Windows Explorer address copied", Toast.LENGTH_SHORT).show();
    }

    private void openHotspotSettings() {
        try {
            startActivity(new Intent("android.settings.TETHER_SETTINGS"));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 42);
        }
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23) return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
        }
    }

    private void refreshUi() {
        boolean running = FileServerService.isRunning();
        String ftp = FileServerService.getFtpUrl();
        String http = FileServerService.getHttpUrl();
        serverStatus.setText(running ? "● Direct transfer online" : "Server stopped");
        serverStatus.setTextColor(running ? Color.rgb(22, 143, 94) : Color.rgb(92, 102, 119));
        ftpAddressText.setText(ftp == null ? "ftp://—" : ftp);
        httpAddressText.setText(http == null ? "http://—" : http);
        pinText.setText("Browser PIN: " + (running ? FileServerService.getPin() : "—"));
        statsText.setText(formatBytes(FileServerService.getBytesServed()) + " sent • " + FileServerService.getActiveTransfers() + " active transfers");
        startStop.setText(running ? "Stop direct transfer" : "Start direct transfer");
        permissionText.setText(hasStorageAccess() ? "✓ All-files access granted for shared storage" : "Permission required before shared storage can be exposed");
        permissionText.setTextColor(hasStorageAccess() ? Color.rgb(22, 143, 94) : Color.rgb(185, 83, 32));
    }

    private String formatBytes(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        if (v < 1024) return String.format(java.util.Locale.US, "%.1f KB", v);
        v /= 1024.0;
        if (v < 1024) return String.format(java.util.Locale.US, "%.1f MB", v);
        return String.format(java.util.Locale.US, "%.2f GB", v / 1024.0);
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(229, 233, 240));
        l.setBackground(bg);
        l.setElevation(dp(2));
        return l;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(50));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (primary) {
            bg.setColor(Color.rgb(44, 94, 246));
            b.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.rgb(239, 243, 251));
            bg.setStroke(dp(1), Color.rgb(218, 225, 237));
            b.setTextColor(Color.rgb(30, 56, 112));
        }
        b.setBackground(bg);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void addTopGap(LinearLayout parent, View v, int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(dp);
        parent.addView(v, lp);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
