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
    private TextView serverStatus, turboAddress, explorerAddress, pinText, permissionText, statsText;
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
        getWindow().setStatusBarColor(Color.rgb(35, 28, 100));
        getWindow().setNavigationBarColor(Color.rgb(12, 20, 38));
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
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        root.setBackgroundColor(Color.rgb(245, 247, 252));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 20) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top, bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    top = bars.top; bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(dp(16), top + dp(14), dp(16), bottom + dp(24));
                return insets;
            });
        }

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(79,70,229), Color.rgb(8,145,178), Color.rgb(5,150,105)});
        heroBg.setCornerRadius(dp(24));
        hero.setBackground(heroBg);
        hero.setElevation(dp(4));
        root.addView(hero);

        TextView brand = text("FLASHDROP DIRECT", 12, Color.rgb(229, 231, 255), true);
        brand.setLetterSpacing(.16f);
        hero.addView(brand);

        TextView title = text("Turbo 3.1 • Persistent Stream", 29, Color.WHITE, true);
        title.setPadding(0, dp(7), 0, 0);
        hero.addView(title);

        TextView sub = text("OnePlus-friendly high-speed transfer over the same FTP path already proven in File Explorer.", 14, Color.rgb(235, 252, 249), false);
        sub.setPadding(0, dp(7), 0, 0);
        hero.addView(sub);

        TextView by = text("Designed & built by Shashank Patel", 12, Color.rgb(225, 247, 241), true);
        by.setPadding(0, dp(13), 0, 0);
        hero.addView(by);

        LinearLayout status = card(Color.WHITE, Color.rgb(225,229,239));
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(-1,-2);
        sc.topMargin=dp(14); root.addView(status, sc);

        serverStatus = text("Server stopped", 16, Color.rgb(86,96,116), true);
        status.addView(serverStatus);

        TextView turboLabel = chip("⬇  WINDOWS TURBO 3 DOWNLOAD", Color.rgb(238,235,255), Color.rgb(79,70,229));
        turboLabel.setPadding(dp(10),dp(7),dp(10),dp(7));
        addTopGap(status,turboLabel,14);

        turboAddress = text("http://—", 20, Color.rgb(41,52,107), true);
        turboAddress.setPadding(0,dp(8),0,0);
        status.addView(turboAddress);

        pinText = text("PIN: —", 15, Color.rgb(91,71,202), true);
        pinText.setPadding(0,dp(5),0,0);
        status.addView(pinText);

        TextView explorerLabel = chip("⚡  FTP / TURBO 3 ENGINE", Color.rgb(231,249,245), Color.rgb(5,128,103));
        addTopGap(status,explorerLabel,15);
        explorerAddress = text("ftp://—", 17, Color.rgb(22,94,78), true);
        explorerAddress.setPadding(0,dp(7),0,0);
        status.addView(explorerAddress);

        statsText = text("0 MB sent • 0 active transfers", 13, Color.rgb(105,114,132), false);
        statsText.setPadding(0,dp(12),0,0);
        status.addView(statsText);

        startStop = button("START FLASHDROP", Color.rgb(79,70,229), Color.WHITE);
        startStop.setOnClickListener(v -> { if (FileServerService.isRunning()) stopServer(); else startServer(); });
        addTopGap(root,startStop,14);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1,-2); ap.topMargin=dp(8); root.addView(actions,ap);
        Button copyTurbo = button("Copy Turbo Address", Color.rgb(236,239,255), Color.rgb(68,61,188));
        copyTurbo.setOnClickListener(v -> copy(FileServerService.getHttpUrl(),"Turbo address"));
        Button copyFtp = button("Copy Explorer Address", Color.rgb(231,248,244), Color.rgb(5,128,103));
        copyFtp.setOnClickListener(v -> copy(FileServerService.getFtpUrl(),"Explorer address"));
        LinearLayout.LayoutParams half1=new LinearLayout.LayoutParams(0,-2,1); half1.rightMargin=dp(4);
        LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-2,1); half2.leftMargin=dp(4);
        actions.addView(copyTurbo,half1); actions.addView(copyFtp,half2);

        TextView recTitle = sectionTitle("Turbo 3 for large backups");
        addTopGap(root,recTitle,24);

        LinearLayout turboCard = card(Color.rgb(248,247,255), Color.rgb(218,214,255));
        root.addView(turboCard);
        turboCard.addView(text("⚡ FlashDrop Turbo 3.1 for Windows",20,Color.rgb(58,48,171),true));
        TextView turboDesc=text("Turbo 3.1 uses the same proven FTP port, then switches its workers into a persistent XFD1 streaming mode. File after file stays on the same socket, removing repeated passive-socket setup pauses while preserving resume support.",14,Color.rgb(70,78,103),false);
        turboDesc.setPadding(0,dp(8),0,0); turboCard.addView(turboDesc);
        TextView target=chip("TARGET: 100 GB/hour ≈ 28 MB/s sustained",Color.rgb(235,250,246),Color.rgb(4,128,99));
        addTopGap(turboCard,target,12);
        TextView steps=text("1. Connect PC to this phone's 5 GHz hotspot.\n2. Start FlashDrop.\n3. Open the HTTP address above only to download Turbo 3.\n4. Run Turbo 3.1 → Auto Detect. No Turbo PIN is required.\n5. Choose the phone folder + PC folder → START TURBO COPY.",14,Color.rgb(50,61,85),false);
        steps.setPadding(0,dp(12),0,0); turboCard.addView(steps);

        TextView expTitle=sectionTitle("Simple Explorer mode");
        addTopGap(root,expTitle,22);
        LinearLayout expCard=card(Color.rgb(246,252,250),Color.rgb(206,237,228)); root.addView(expCard);
        expCard.addView(text("📁 Copy folders directly in File Explorer",18,Color.rgb(10,105,85),true));
        TextView exp=text("Open the FTP address above in Windows File Explorer. This remains the easiest method for normal copying. Turbo 3.1 upgrades this same FTP service with persistent file streams for large backups.",14,Color.rgb(56,76,72),false);
        exp.setPadding(0,dp(8),0,0); expCard.addView(exp);

        TextView setupTitle=sectionTitle("Connection & storage");
        addTopGap(root,setupTitle,22);
        LinearLayout setup=card(Color.WHITE,Color.rgb(226,230,239)); root.addView(setup);
        permissionText=text("Checking storage access…",14,Color.rgb(90,100,119),false); setup.addView(permissionText);
        Button grant=button("Grant All Files Access",Color.rgb(244,246,251),Color.rgb(40,57,92));
        grant.setOnClickListener(v->requestStorageAccess()); addTopGap(setup,grant,12);
        Button hotspot=button("Open Hotspot Settings",Color.rgb(244,246,251),Color.rgb(40,57,92));
        hotspot.setOnClickListener(v->openHotspotSettings()); addTopGap(setup,hotspot,8);

        LinearLayout perf=card(Color.rgb(250,251,255),Color.rgb(229,232,244));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2); pp.topMargin=dp(14); root.addView(perf,pp);
        perf.addView(text("Performance profile",16,Color.rgb(34,47,75),true));
        TextView perfText=text("• Turbo 3.1 stays on the proven FTP control port\n• XFD1 persistent stream mode removes per-file data-socket reconnects\n• 1–8 persistent parallel workers\n• 1–8 MB sequential buffers\n• 4 MB Android file buffers + high-performance Wi-Fi lock\n• Automatic reconnect/resume on interruption\n• Standard read-only FTP remains available for Explorer",13,Color.rgb(74,84,103),false);
        perfText.setPadding(0,dp(8),0,0); perf.addView(perfText);

        TextView limit=text("Actual speed depends on the phone's Wi-Fi chipset, hotspot link rate, storage read speed, laptop Wi-Fi, interference and file sizes. 100 GB/hour needs roughly 28 MB/s continuously, so it is a performance target rather than a guaranteed minimum.",12,Color.rgb(118,126,143),false);
        limit.setPadding(dp(4),dp(16),dp(4),0); root.addView(limit);

        TextView footer=text("FLASHDROP DIRECT • BY SHASHANK PATEL",11,Color.rgb(111,118,136),true);
        footer.setGravity(Gravity.CENTER); footer.setLetterSpacing(.08f); footer.setPadding(0,dp(24),0,0); root.addView(footer);
    }

    private TextView sectionTitle(String s) { return text(s,18,Color.rgb(20,31,54),true); }

    private TextView chip(String s,int bg,int fg) {
        TextView t=text(s,11,fg,true); t.setPadding(dp(10),dp(7),dp(10),dp(7));
        GradientDrawable g=new GradientDrawable(); g.setColor(bg); g.setCornerRadius(dp(12)); t.setBackground(g);
        return t;
    }

    private void startServer() {
        if (!hasStorageAccess()) {
            Toast.makeText(this,"Grant All Files Access first",Toast.LENGTH_LONG).show();
            requestStorageAccess(); return;
        }
        Intent i=new Intent(this,FileServerService.class);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        Toast.makeText(this,"Starting FlashDrop…",Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::refreshUi,800);
    }

    private void stopServer() {
        Intent i=new Intent(this,FileServerService.class); i.setAction(FileServerService.ACTION_STOP); startService(i);
        handler.postDelayed(this::refreshUi,350);
    }

    private void copy(String value,String label) {
        if(value==null){Toast.makeText(this,"Start FlashDrop first",Toast.LENGTH_SHORT).show();return;}
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label,value));
        Toast.makeText(this,label+" copied",Toast.LENGTH_SHORT).show();
    }

    private void openHotspotSettings() {
        try { startActivity(new Intent("android.settings.TETHER_SETTINGS")); }
        catch(Exception e){ startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
    }

    private void requestStorageAccess() {
        if(Build.VERSION.SDK_INT>=30) {
            try {
                Intent i=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:"+getPackageName())); startActivity(i);
            } catch(Exception e){ startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        } else if(Build.VERSION.SDK_INT>=23) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},42);
        }
    }

    private boolean hasStorageAccess() {
        if(Build.VERSION.SDK_INT>=30) return Environment.isExternalStorageManager();
        if(Build.VERSION.SDK_INT>=23) return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private void requestNotificationPermissionIfNeeded() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},43);
    }

    private void refreshUi() {
        boolean running=FileServerService.isRunning();
        serverStatus.setText(running?"● FlashDrop online":"Server stopped");
        serverStatus.setTextColor(running?Color.rgb(5,150,105):Color.rgb(94,103,120));
        turboAddress.setText(FileServerService.getHttpUrl()==null?"http://—":FileServerService.getHttpUrl());
        explorerAddress.setText(FileServerService.getFtpUrl()==null?"ftp://—":FileServerService.getFtpUrl());
        pinText.setText("PIN: "+(running?FileServerService.getPin():"—"));
        statsText.setText(formatBytes(FileServerService.getBytesServed())+" sent • "+FileServerService.getActiveTransfers()+" active transfers");
        startStop.setText(running?"STOP FLASHDROP":"START FLASHDROP");
        startStop.setBackground(round(running?Color.rgb(220,58,67):Color.rgb(79,70,229),dp(15),0));
        permissionText.setText(hasStorageAccess()?"✓ Shared storage access granted":"Storage permission required");
        permissionText.setTextColor(hasStorageAccess()?Color.rgb(5,140,103):Color.rgb(185,83,32));
    }

    private LinearLayout card(int bg,int stroke) {
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18),dp(18),dp(18),dp(18));
        l.setBackground(round(bg,dp(19),stroke)); l.setElevation(dp(2)); return l;
    }

    private Button button(String s,int bg,int fg) {
        Button b=new Button(this); b.setText(s); b.setTextSize(13); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setMinHeight(dp(50)); b.setTextColor(fg); b.setBackground(round(bg,dp(14),0)); return b;
    }

    private GradientDrawable round(int bg,float radius,int stroke) {
        GradientDrawable g=new GradientDrawable(); g.setColor(bg); g.setCornerRadius(radius);
        if(stroke!=0) g.setStroke(dp(1),stroke); return g;
    }

    private TextView text(String s,int sp,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setLineSpacing(0,1.14f);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }

    private void addTopGap(LinearLayout parent,View v,int gap) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(gap); parent.addView(v,lp);
    }

    private String formatBytes(long b) {
        if(b<1024)return b+" B"; double v=b/1024d;
        if(v<1024)return String.format(java.util.Locale.US,"%.1f KB",v);
        v/=1024d; if(v<1024)return String.format(java.util.Locale.US,"%.1f MB",v);
        return String.format(java.util.Locale.US,"%.2f GB",v/1024d);
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
