package com.openai.inwardregister;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private SessionStore store;
    private LinearLayout root;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store = new SessionStore(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null) render();
    }

    private void render() {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        sc.setBackgroundColor(Ui.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,20), Ui.dp(this,22), Ui.dp(this,20), Ui.dp(this,38));
        sc.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.badge(this, "INWARD REGISTER • PRIVATE ON-DEVICE AI"));
        root.addView(Ui.title(this, "MailDesk AI", 30));
        TextView sub = Ui.body(this, "A professional inward-register assistant for physical mail. English + Hindi OCR, handwriting-focused department detection, local subject generation and Excel export — without uploading letters.");
        sub.setPadding(0,0,0,Ui.dp(this,16));
        root.addView(sub);

        if (!store.isActive()) renderStart(); else renderActive();
        Ui.prepareScreen(this, sc);
        setContentView(sc);
    }

    private void renderStart() {
        LinearLayout privacy = Ui.successCard(this);
        privacy.addView(Ui.title(this, "100% local processing", 18));
        TextView p = Ui.body(this, "No Groq, Gemini or xAI key is required. Letter images remain on the phone while the local OCR and document-intelligence engine works.");
        p.setTextColor(Ui.GREEN);
        privacy.addView(p);
        root.addView(privacy);

        LinearLayout card = Ui.card(this);
        card.addView(Ui.title(this, "Start a new inward session", 20));
        card.addView(Ui.body(this, "Choose one receiving date and the first inward number. Every confirmed letter increments the inward number automatically."));
        Button start = Ui.primary(this, "Start New Session");
        start.setOnClickListener(v -> showStartDialog());
        card.addView(start);
        root.addView(card);

        Button settings = Ui.secondary(this, "Local AI & Department Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(settings);
    }

    private void renderActive() {
        JSONArray entries = store.entries();
        LinearLayout summary = Ui.card(this);
        summary.addView(Ui.title(this, "Active Session", 20));
        TextView d = Ui.body(this, "Receiving date      " + store.receivingDate());
        d.setTextSize(16); summary.addView(d);
        TextView n = Ui.body(this, "Next inward no.    " + store.nextInwardNo());
        n.setTextSize(16); summary.addView(n);
        TextView c = Ui.body(this, "Confirmed letters  " + entries.length());
        c.setTextSize(16); summary.addView(c);
        root.addView(summary);

        Button scan = Ui.primary(this, "＋ Scan Next Letter");
        scan.setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
        root.addView(scan);

        Button export = Ui.secondary(this, "Finish & Export Excel (.xlsx)");
        export.setEnabled(entries.length() > 0);
        export.setAlpha(entries.length() > 0 ? 1f : .45f);
        export.setOnClickListener(v -> exportAndShare());
        root.addView(export);

        Button settings = Ui.secondary(this, "Local AI & Department Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(settings);

        if (entries.length() > 0) {
            TextView h = Ui.title(this, "Today's Entries", 20);
            h.setPadding(0, Ui.dp(this,18), 0, Ui.dp(this,8));
            root.addView(h);
            for (int i=0; i<entries.length(); i++) addEntryCard(i, entries.optJSONObject(i));
        }

        Button close = Ui.danger(this, "Close Session / Start Fresh");
        close.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Close current session?")
                .setMessage("Export first if you need the Excel file. Closing starts a fresh register and removes this session from the app.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close Session", (dialog,which) -> { store.closeSession(); render(); })
                .show());
        root.addView(close);
    }

    private void addEntryCard(int index, JSONObject o) {
        if (o == null) return;
        LinearLayout card = Ui.card(this);
        String inward = String.valueOf(o.optInt("Inward_No", 0));
        String sender = o.optString("Sender", "").trim();
        String subject = o.optString("Subject", "").trim();
        String dept = o.optString("Department", "").trim();
        card.addView(Ui.title(this, "#" + inward + (sender.isEmpty() ? "" : "  •  " + sender), 16));
        TextView s = Ui.body(this, subject.isEmpty() ? "Subject not entered" : subject);
        s.setMaxLines(3); card.addView(s);
        if (!dept.isEmpty()) {
            TextView dv = Ui.body(this, "Department  •  " + dept);
            dv.setTextColor(Ui.BLUE_DARK); card.addView(dv);
        }
        Button edit = Ui.secondary(this, "Review / Edit Entry");
        final int idx = index;
        edit.setOnClickListener(v -> {
            Intent it = new Intent(this, ReviewActivity.class);
            it.putExtra("edit_index", idx);
            startActivity(it);
        });
        card.addView(edit);
        root.addView(card);
    }

    private void showStartDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this,22), Ui.dp(this,8), Ui.dp(this,22), 0);
        EditText date = Ui.input(this, "Receiving date");
        date.setText(store.defaultToday());
        date.setFocusable(false);
        date.setOnClickListener(v -> pickDate(date));
        box.addView(Ui.label(this, "RECEIVING DATE")); box.addView(date);
        EditText inward = Ui.input(this, "e.g. 1001");
        inward.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(Ui.label(this, "STARTING INWARD NO.")); box.addView(inward);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("New Inward Session")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ds = date.getText().toString().trim();
            String ns = inward.getText().toString().trim();
            if (ds.isEmpty() || ns.isEmpty()) { Toast.makeText(this, "Enter date and starting inward number.", Toast.LENGTH_SHORT).show(); return; }
            try {
                int n = Integer.parseInt(ns);
                if (n < 1) throw new Exception();
                store.start(ds, n);
                dlg.dismiss(); render();
            } catch (Exception e) { Toast.makeText(this, "Starting inward number must be a positive integer.", Toast.LENGTH_SHORT).show(); }
        }));
        dlg.show();
    }

    private void pickDate(EditText target) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog d = new DatePickerDialog(this, (v,y,m,day) -> {
            Calendar c = Calendar.getInstance(); c.set(y,m,day);
            target.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(c.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        d.show();
    }

    private void exportAndShare() {
        try {
            JSONArray arr = store.entries();
            if (arr.length() == 0) { Toast.makeText(this, "No entries to export.", Toast.LENGTH_SHORT).show(); return; }
            String date = store.receivingDate().replaceAll("[^0-9A-Za-z]+", "-");
            File out = new File(getCacheDir(), "Inward_Register_" + date + ".xlsx");
            XlsxExporter.export(out, arr);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", out);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, "Inward Register - " + store.receivingDate());
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share Inward Register Excel"));
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Export failed").setMessage(e.getMessage()).setPositiveButton("OK", null).show();
        }
    }
}
