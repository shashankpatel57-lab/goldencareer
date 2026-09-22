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

    private TextView statusDot;
    private TextView statusText;
    private TextView pinText;
    private TextView desktopAddress;
    private TextView ftpAddress;
    private TextView statsText;
    private TextView permissionText;
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
        getWindow().setStatusBarColor(Color.rgb(9, 38, 62));
        getWindow().setNavigationBarColor(Color.rgb(7, 25, 40));
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
        root.setPadding(dp(18), dp(16), dp(18), dp(32));
        root.setBackgroundColor(Color.rgb(246, 248, 251));

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 20) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top;
                int bottom;

                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars =
                            insets.getInsets(WindowInsets.Type.systemBars());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }

                v.setPadding(dp(18), top + dp(14), dp(18), bottom + dp(28));
                return insets;
            });
        }

        addHero();
        addStatusCard();
        addActionCard();
        addDesktopCard();
        addPrivacyCard();
        addPermissionCard();
        addFooter();
    }

    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(22), dp(22), dp(22), dp(22));

        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(9, 52, 86),
                        Color.rgb(13, 109, 104),
                        Color.rgb(20, 128, 79)
                });
        heroBg.setCornerRadius(dp(26));

        hero.setBackground(heroBg);
        hero.setElevation(dp(5));
        root.addView(hero);

        TextView badge = text(
                "MADE IN INDIA  🇮🇳",
                12,
                Color.rgb(255, 235, 205),
                true);
        badge.setLetterSpacing(.13f);
        hero.addView(badge);

        TextView brand = text(
                "BharatDrop",
                34,
                Color.WHITE,
                true);
        brand.setPadding(0, dp(7), 0, 0);
        hero.addView(brand);

        TextView tagline = text(
                "Fast local phone-to-PC transfer. No cloud. No upload. Your files stay between your devices.",
                14,
                Color.rgb(231, 247, 244),
                false);
        tagline.setPadding(0, dp(8), 0, 0);
        hero.addView(tagline);

        TextView developer = text(
                "Developer: Shashank Patel",
                12,
                Color.rgb(255, 238, 213),
                true);
        developer.setPadding(0, dp(14), 0, 0);
        hero.addView(developer);
    }

    private void addStatusCard() {
        LinearLayout card = card(Color.WHITE, Color.rgb(224, 230, 238));
        addTopGap(root, card, 14);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);

        statusDot = text("●", 18, Color.rgb(143, 151, 164), true);
        top.addView(statusDot);

        statusText = text(
                "BharatDrop is stopped",
                17,
                Color.rgb(45, 58, 77),
                true);
        LinearLayout.LayoutParams statusLp =
                new LinearLayout.LayoutParams(0, -2, 1);
        statusLp.leftMargin = dp(8);
        top.addView(statusText, statusLp);

        TextView localChip = chip(
                "LOCAL ONLY",
                Color.rgb(235, 248, 243),
                Color.rgb(9, 111, 78));
        top.addView(localChip);

        TextView pinLabel = text(
                "Secure session PIN",
                12,
                Color.rgb(100, 109, 125),
                true);
        pinLabel.setPadding(0, dp(18), 0, 0);
        card.addView(pinLabel);

        pinText = text(
                "— — — — — —",
                30,
                Color.rgb(16, 66, 100),
                true);
        pinText.setLetterSpacing(.14f);
        pinText.setPadding(0, dp(3), 0, 0);
        card.addView(pinText);

        TextView pinHint = text(
                "Enter this PIN in BharatDrop Desktop on your Windows PC.",
                12,
                Color.rgb(103, 112, 128),
                false);
        pinHint.setPadding(0, dp(4), 0, 0);
        card.addView(pinHint);

        statsText = text(
                "0 MB sent  •  0 active streams",
                13,
                Color.rgb(91, 101, 117),
                false);
        statsText.setPadding(0, dp(14), 0, 0);
        card.addView(statsText);
    }

    private void addActionCard() {
        startStop = button(
                "START BHARATDROP",
                Color.rgb(18, 125, 85),
                Color.WHITE);
        startStop.setOnClickListener(v -> {
            if (FileServerService.isRunning()) stopServer();
            else startServer();
        });
        addTopGap(root, startStop, 14);
    }

    private void addDesktopCard() {
        TextView title = sectionTitle("Connect your Windows PC");
        addTopGap(root, title, 24);

        LinearLayout card = card(
                Color.rgb(250, 252, 255),
                Color.rgb(218, 226, 238));
        root.addView(card);

        card.addView(text(
                "BharatDrop Desktop",
                20,
                Color.rgb(20, 58, 92),
                true));

        TextView desc = text(
                "Uses the packed-folder engine for folders with thousands of files. Supports pause/resume, automatic reconnection, optional SHA-256 verification and transfer reports.",
                13,
                Color.rgb(73, 84, 102),
                false);
        desc.setPadding(0, dp(7), 0, 0);
        card.addView(desc);

        TextView dlLabel = text(
                "Desktop download address",
                11,
                Color.rgb(103, 112, 128),
                true);
        dlLabel.setPadding(0, dp(16), 0, 0);
        card.addView(dlLabel);

        desktopAddress = text(
                "http://—",
                16,
                Color.rgb(34, 78, 127),
                true);
        desktopAddress.setPadding(0, dp(4), 0, 0);
        desktopAddress.setTextIsSelectable(true);
        card.addView(desktopAddress);

        Button copyDesktop = button(
                "Copy Desktop Download Address",
                Color.rgb(235, 241, 250),
                Color.rgb(34, 78, 127));
        copyDesktop.setOnClickListener(v ->
                copy(FileServerService.getHttpUrl(), "Desktop address"));
        addTopGap(card, copyDesktop, 10);

        TextView ftpLabel = text(
                "Secure Explorer address",
                11,
                Color.rgb(103, 112, 128),
                true);
        ftpLabel.setPadding(0, dp(16), 0, 0);
        card.addView(ftpLabel);

        ftpAddress = text(
                "ftp://—",
                16,
                Color.rgb(12, 105, 82),
                true);
        ftpAddress.setPadding(0, dp(4), 0, 0);
        ftpAddress.setTextIsSelectable(true);
        card.addView(ftpAddress);

        Button copyFtp = button(
                "Copy Explorer Address",
                Color.rgb(233, 248, 243),
                Color.rgb(12, 105, 82));
        copyFtp.setOnClickListener(v ->
                copy(FileServerService.getFtpUrl(), "Explorer address"));
        addTopGap(card, copyFtp, 10);

        TextView steps = text(
                "1. Put the phone and PC on the same Wi-Fi / hotspot.\n" +
                "2. Start BharatDrop on the phone.\n" +
                "3. Open BharatDrop Desktop on Windows.\n" +
                "4. Auto Detect → enter the PIN shown above → select a phone folder → Transfer.",
                13,
                Color.rgb(58, 70, 88),
                false);
        steps.setPadding(0, dp(16), 0, 0);
        card.addView(steps);
    }

    private void addPrivacyCard() {
        TextView title = sectionTitle("Privacy by design");
        addTopGap(root, title, 24);

        LinearLayout card = card(
                Color.rgb(247, 252, 249),
                Color.rgb(205, 234, 220));
        root.addView(card);

        card.addView(text(
                "No Data Retention",
                19,
                Color.rgb(9, 106, 76),
                true));

        TextView body = text(
                "BharatDrop does not upload your files to our servers, does not create a cloud account, and does not retain transferred files or transfer history on any developer-controlled server. Transfer happens directly on your local network.",
                13,
                Color.rgb(55, 78, 68),
                false);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body);

        TextView secure = chip(
                "READ-ONLY PHONE SERVER  •  SESSION PIN  •  LOCAL NETWORK",
                Color.rgb(229, 247, 239),
                Color.rgb(8, 103, 72));
        addTopGap(card, secure, 12);
    }

    private void addPermissionCard() {
        TextView title = sectionTitle("Device access");
        addTopGap(root, title, 24);

        LinearLayout card = card(
                Color.WHITE,
                Color.rgb(224, 230, 238));
        root.addView(card);

        permissionText = text(
                "Checking storage access…",
                14,
                Color.rgb(90, 100, 119),
                true);
        card.addView(permissionText);

        TextView why = text(
                "BharatDrop needs broad file access because its core purpose is transferring user-selected folders from shared storage to your PC.",
                12,
                Color.rgb(103, 112, 128),
                false);
        why.setPadding(0, dp(6), 0, 0);
        card.addView(why);

        Button grant = button(
                "Grant File Access",
                Color.rgb(242, 245, 250),
                Color.rgb(38, 57, 82));
        grant.setOnClickListener(v -> requestStorageAccess());
        addTopGap(card, grant, 12);

        Button hotspot = button(
                "Open Wi-Fi / Hotspot Settings",
                Color.rgb(242, 245, 250),
                Color.rgb(38, 57, 82));
        hotspot.setOnClickListener(v -> openHotspotSettings());
        addTopGap(card, hotspot, 8);
    }

    private void addFooter() {
        TextView footer = text(
                "BHARATDROP  •  DEVELOPED IN INDIA  •  SHASHANK PATEL",
                10,
                Color.rgb(105, 114, 130),
                true);
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(.07f);
        footer.setPadding(0, dp(28), 0, 0);
        root.addView(footer);
    }

    private void startServer() {
        if (!hasStorageAccess()) {
            Toast.makeText(
                    this,
                    "Grant file access first",
                    Toast.LENGTH_LONG).show();
            requestStorageAccess();
            return;
        }

        Intent i = new Intent(this, FileServerService.class);

        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);

        Toast.makeText(
                this,
                "Starting BharatDrop…",
                Toast.LENGTH_SHORT).show();

        handler.postDelayed(this::refreshUi, 700);
    }

    private void stopServer() {
        Intent i = new Intent(this, FileServerService.class);
        i.setAction(FileServerService.ACTION_STOP);
        startService(i);
        handler.postDelayed(this::refreshUi, 350);
    }

    private void copy(String value, String label) {
        if (value == null) {
            Toast.makeText(
                    this,
                    "Start BharatDrop first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager cm =
                (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);

        cm.setPrimaryClip(
                ClipData.newPlainText(label, value));

        Toast.makeText(
                this,
                label + " copied",
                Toast.LENGTH_SHORT).show();
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
                Intent i = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    42);
        }
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30)
            return Environment.isExternalStorageManager();

        if (Build.VERSION.SDK_INT >= 23)
            return checkSelfPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;

        return true;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    43);
        }
    }

    private void refreshUi() {
        boolean running = FileServerService.isRunning();

        statusDot.setTextColor(
                running
                        ? Color.rgb(18, 145, 94)
                        : Color.rgb(143, 151, 164));

        statusText.setText(
                running
                        ? "BharatDrop is ready"
                        : "BharatDrop is stopped");

        pinText.setText(
                running
                        ? spacedPin(FileServerService.getPin())
                        : "— — — — — —");

        desktopAddress.setText(
                FileServerService.getHttpUrl() == null
                        ? "http://—"
                        : FileServerService.getHttpUrl());

        ftpAddress.setText(
                FileServerService.getFtpUrl() == null
                        ? "ftp://—"
                        : FileServerService.getFtpUrl());

        statsText.setText(
                formatBytes(FileServerService.getBytesServed()) +
                        " sent  •  " +
                        FileServerService.getActiveTransfers() +
                        " active streams");

        startStop.setText(
                running
                        ? "STOP BHARATDROP"
                        : "START BHARATDROP");

        startStop.setBackground(
                round(
                        running
                                ? Color.rgb(195, 57, 66)
                                : Color.rgb(18, 125, 85),
                        dp(15),
                        0));

        permissionText.setText(
                hasStorageAccess()
                        ? "✓ File access granted"
                        : "File access required");

        permissionText.setTextColor(
                hasStorageAccess()
                        ? Color.rgb(10, 132, 88)
                        : Color.rgb(183, 83, 31));
    }

    private String spacedPin(String pin) {
        if (pin == null || pin.length() != 6)
            return "— — — — — —";

        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) {
            if (i > 0) b.append(' ');
            b.append(pin.charAt(i));
        }
        return b.toString();
    }

    private TextView sectionTitle(String s) {
        return text(
                s,
                18,
                Color.rgb(23, 37, 56),
                true);
    }

    private TextView chip(String s, int bg, int fg) {
        TextView t = text(s, 10, fg, true);
        t.setPadding(dp(10), dp(7), dp(10), dp(7));

        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(13));
        t.setBackground(g);

        return t;
    }

    private LinearLayout card(int bg, int stroke) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(18), dp(18), dp(18));
        l.setBackground(round(bg, dp(20), stroke));
        l.setElevation(dp(2));
        return l;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(52));
        b.setTextColor(fg);
        b.setBackground(round(bg, dp(15), 0));
        return b;
    }

    private GradientDrawable round(int bg, float radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(radius);

        if (stroke != 0)
            g.setStroke(dp(1), stroke);

        return g;
    }

    private TextView text(
            String s,
            int sp,
            int color,
            boolean bold) {

        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.14f);

        if (bold)
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        return t;
    }

    private void addTopGap(
            LinearLayout parent,
            View v,
            int gap) {

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(gap);
        parent.addView(v, lp);
    }

    private String formatBytes(long b) {
        if (b < 1024) return b + " B";

        double v = b / 1024d;

        if (v < 1024)
            return String.format(
                    java.util.Locale.US,
                    "%.1f KB",
                    v);

        v /= 1024d;

        if (v < 1024)
            return String.format(
                    java.util.Locale.US,
                    "%.1f MB",
                    v);

        return String.format(
                java.util.Locale.US,
                "%.2f GB",
                v / 1024d);
    }

    private int dp(int v) {
        return Math.round(
                v * getResources().getDisplayMetrics().density);
    }
}
