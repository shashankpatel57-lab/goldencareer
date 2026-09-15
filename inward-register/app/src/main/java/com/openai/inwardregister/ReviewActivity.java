package com.openai.inwardregister;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class ReviewActivity extends Activity {
    private SessionStore store;
    private JSONObject entry;
    private int editIndex = -1;
    private EditText letterNo, letterDate, sender, subject, department, tracking;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store = new SessionStore(this);
        editIndex = getIntent().getIntExtra("edit_index", -1);
        try {
            if (editIndex >= 0) entry = store.entry(editIndex);
            else entry = new JSONObject(getIntent().getStringExtra("entry_json"));
        } catch (Exception e) {
            entry = store.newEntrySkeleton("");
        }
        if (entry == null) entry = store.newEntrySkeleton("");
        render();
    }

    private void render() {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        sc.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,20), Ui.dp(this,22), Ui.dp(this,20), Ui.dp(this,36));
        sc.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.badge(this, editIndex >= 0 ? "EDIT REGISTER ENTRY" : "LOCAL AI EXTRACTION COMPLETE"));
        root.addView(Ui.title(this, "Review before saving", 29));
        TextView intro = Ui.body(this, "The app has already performed local English/Hindi OCR and handwriting-focused analysis. Verify the result once; every field stays editable.");
        intro.setPadding(0,0,0,Ui.dp(this,14));
        root.addView(intro);

        String warning = getIntent().getStringExtra("warning");
        if (warning != null && !warning.trim().isEmpty()) {
            LinearLayout warn = Ui.card(this);
            TextView wt = Ui.title(this, "Needs a quick manual check", 16);
            wt.setTextColor(Color.rgb(158,91,0));
            warn.addView(wt);
            warn.addView(Ui.body(this, warning));
            root.addView(warn);
        }

        LinearLayout meta = Ui.successCard(this);
        meta.addView(Ui.title(this, "Register Metadata", 18));
        meta.addView(Ui.body(this, "Receiving Date   " + entry.optString("Receiving_Date", store.receivingDate())));
        meta.addView(Ui.body(this, "Inward No.        " + entry.optInt("Inward_No", store.nextInwardNo())));
        String provider = entry.optString("AI_Provider", "");
        if (!provider.isEmpty()) {
            TextView engine = Ui.body(this, "Engine               " + provider);
            engine.setTextColor(Ui.GREEN);
            meta.addView(engine);
        }
        root.addView(meta);

        LinearLayout form = Ui.card(this);
        form.addView(Ui.title(this, "Letter Details", 18));
        form.addView(Ui.body(this, "Fields generated from OCR are suggestions, not locked values. Correct anything unclear before confirming."));
        letterNo = addField(form, "LETTER NO.", "Reference / letter number", entry.optString("Letter_No", ""), false);
        letterDate = addField(form, "LETTER DATE", "Date printed on letter", entry.optString("Letter_Date", ""), false);
        sender = addField(form, "SENDER", "Organization / person / office", entry.optString("Sender", ""), false);
        subject = addField(form, "SUBJECT", "Detected subject or locally generated one-line summary", entry.optString("Subject", ""), true);
        department = addField(form, "DEPARTMENT", "Printed / handwritten routing department", entry.optString("Department", ""), false);
        tracking = addField(form, "TRACKING ID", "Tracking number or By Hand", entry.optString("Tracking_ID", ""), false);
        TextView note = Ui.body(this, "Signature stays blank in Excel so the physical inward register can be signed after printing.");
        note.setPadding(0,Ui.dp(this,7),0,0);
        form.addView(note);
        root.addView(form);

        Button save = Ui.primary(this, editIndex >= 0 ? "Save Changes" : "✓ Confirm & Add to Register");
        save.setOnClickListener(v -> save());
        root.addView(save);

        if (editIndex >= 0) {
            Button del = Ui.danger(this, "Delete This Entry");
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Delete entry #" + entry.optInt("Inward_No", 0) + "?")
                    .setMessage("This removes the row from the current session. The next inward number is not rolled back.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d,w) -> { store.deleteEntry(editIndex); finish(); })
                    .show());
            root.addView(del);
        }

        Button cancel = Ui.secondary(this, "Back Without Saving");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);

        Ui.prepareScreen(this, sc);
        setContentView(sc);
    }

    private EditText addField(LinearLayout parent, String label, String hint, String value, boolean multiline) {
        parent.addView(Ui.label(this, label));
        EditText e = multiline ? Ui.multilineInput(this, hint, 3) : Ui.input(this, hint);
        e.setText(value);
        parent.addView(e);
        return e;
    }

    private void save() {
        try {
            entry.put("Letter_No", letterNo.getText().toString().trim());
            entry.put("Letter_Date", letterDate.getText().toString().trim());
            entry.put("Sender", sender.getText().toString().trim());
            entry.put("Subject", subject.getText().toString().trim());
            entry.put("Department", department.getText().toString().trim());
            entry.put("Tracking_ID", tracking.getText().toString().trim());
            entry.put("Signature", "");

            if (entry.optString("Tracking_ID", "").isEmpty()) {
                Toast.makeText(this, "Tracking ID cannot be blank. Use “By Hand” when there is no envelope.", Toast.LENGTH_LONG).show();
                return;
            }
            if (editIndex >= 0) store.updateEntry(editIndex, entry); else store.addEntry(entry);
            Toast.makeText(this, editIndex >= 0 ? "Entry updated." : "Letter added. Next inward number is ready.", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Could not save").setMessage(e.getMessage()).setPositiveButton("OK", null).show();
        }
    }
}
