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
    private TextView addressText;
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

        TextView brand = text("FLASHDROP", 13, Color.rgb(44, 94, 246), true);
        brand.setLetterSpacing(0.18f);
        root.addView(brand);

        TextView title = text("Phone → PC at LAN speed", 28, Color.rgb(14, 24, 42), true);
        title.setPadding(0, dp(6), 0, 0);
        root.addView(title);

        TextView sub = text("No internet. No cloud. No ZIP. Open the address on your Windows PC and pull files directly over your phone hotspot.", 15, Color.rgb(85, 96, 116), false);
        sub.setPadding(0, dp(8), 0, dp(18));
        root.addView(sub);

        LinearLayout card = card();
        root.addView(card);
        serverStatus = text("Server stopped", 16, Color.rgb(40, 52, 70), true);
        card.addView(serverStatus);
        addressText = text("http://—", 22, Color.rgb(22, 43, 92), true);
        addressText.setPadding(0, dp(10), 0, 0);
        card.addView(addressText);
        pinText = text("PIN: —", 18, Color.rgb(44, 94, 246), true);
        pinText.setPadding(0, dp(8), 0, 0);
        card.addView(pinText);
        statsText = text("0 MB sent • 0 active transfers", 13, Color.rgb(104, 113, 129), false);
        statsText.setPadding(0, dp(8), 0, 0);
        card.addView(statsText);

        startStop = button("Start file server", true);
        startStop.setOnClickListener(v -> {
            if (FileServerService.isRunning()) stopServer(); else startServer();
        });
        addTopGap(root, startStop, 14);

        Button copy = button("Copy PC address", false);
        copy.setOnClickListener(v -> copyAddress());
        addTopGap(root, copy, 8);

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

        TextView setupTitle = text("Fastest setup", 18, Color.rgb(14, 24, 42), true);
        setupTitle.setPadding(0, dp(24), 0, dp(8));
        root.addView(setupTitle);
        LinearLayout setup = card();
        root.addView(setup);
        setup.addView(text("1  Turn on phone hotspot and choose 5 GHz / 5 GHz preferred.\n\n2  Connect the Windows PC directly to that hotspot. Internet is not required.\n\n3  Start FlashDrop, then open the shown http:// address on the PC.\n\n4  Enter the PIN shown here. Browse, search and download.\n\n5  For a complete folder with its subfolders, use “Windows Folder Pull” in the web page. It copies files directly and does not create a ZIP.", 14, Color.rgb(58, 69, 87), false));
        Button hotspot = button("Open hotspot settings", false);
        hotspot.setOnClickListener(v -> openHotspotSettings());
        addTopGap(setup, hotspot, 14);

        TextView perf = text("Optimized for large transfers", 18, Color.rgb(14, 24, 42), true);
        perf.setPadding(0, dp(24), 0, dp(8));
        root.addView(perf);
        LinearLayout perfCard = card();
        root.addView(perfCard);
        perfCard.addView(text("• Raw file streaming — no recompression\n• HTTP byte-range / resume support\n• Parallel transfer handling\n• Wi‑Fi high-performance lock while server is active\n• Wake lock to reduce sleep interruptions\n• Works entirely on the local hotspot", 14, Color.rgb(58, 69, 87), false));

        TextView note = text("Android security note: “All files access” covers shared storage, but Android still blocks a normal app from reading other apps’ private /data directories and some protected Android/data content.", 12, Color.rgb(116, 124, 139), false);
        note.setPadding(0, dp(18), 0, 0);
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
        Toast.makeText(this, "Starting local file server…", Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::refreshUi, 700);
    }

    private void stopServer() {
        Intent i = new Intent(this, FileServerService.class);
        i.setAction(FileServerService.ACTION_STOP);
        startService(i);
        handler.postDelayed(this::refreshUi, 300);
    }

    private void copyAddress() {
        String url = FileServerService.getUrl();
        if (url == null) {
            Toast.makeText(this, "Start the server first", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("FlashDrop address", url));
        Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
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
        String url = FileServerService.getUrl();
        serverStatus.setText(running ? "● Server online" : "Server stopped");
        serverStatus.setTextColor(running ? Color.rgb(22, 143, 94) : Color.rgb(92, 102, 119));
        addressText.setText(url == null ? "http://—" : url);
        pinText.setText("PIN: " + (running ? FileServerService.getPin() : "—"));
        long bytes = FileServerService.getBytesServed();
        statsText.setText(formatBytes(bytes) + " sent • " + FileServerService.getActiveTransfers() + " active transfers");
        startStop.setText(running ? "Stop file server" : "Start file server");
        permissionText.setText(hasStorageAccess() ? "✓ All-files access granted for shared storage" : "Permission required before the server can browse shared storage");
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
