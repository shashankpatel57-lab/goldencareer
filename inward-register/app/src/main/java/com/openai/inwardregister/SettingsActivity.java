package com.openai.inwardregister;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private EditText customDepartments;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("local_ai", Context.MODE_PRIVATE);
        render();
    }

    private void render() {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        sc.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,20), Ui.dp(this,22), Ui.dp(this,20), Ui.dp(this,38));
        sc.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.badge(this, "LOCAL AI ENGINE • ZERO API KEYS"));
        root.addView(Ui.title(this, "Document Intelligence", 29));
        TextView intro = Ui.body(this, "The engine runs entirely on this phone. It uses separate English and Devanagari OCR models, a second high-contrast handwriting pass, fuzzy department matching and local subject synthesis.");
        intro.setPadding(0,0,0,Ui.dp(this,14));
        root.addView(intro);

        LinearLayout status = Ui.successCard(this);
        status.addView(Ui.title(this, "Engine ready", 19));
        TextView ready = Ui.body(this, "✓ English OCR bundled\n✓ Hindi / Devanagari OCR bundled\n✓ Handwriting-focused contrast pass\n✓ Local department classifier\n✓ Local subject generator\n✓ No Internet permission in this APK");
        ready.setTextColor(Ui.GREEN);
        ready.setLineSpacing(Ui.dp(this,2),1.18f);
        status.addView(ready);
        root.addView(status);

        LinearLayout tips = Ui.card(this);
        tips.addView(Ui.title(this, "For best handwriting recognition", 18));
        tips.addView(Ui.body(this, "• Keep the page flat and parallel to the camera.\n• Avoid shadows across blue/black pen notes.\n• Fill most of the frame with the page.\n• If a routing note is very small, move closer and capture a sharper page photo.\n• Always verify Department on the review screen."));
        root.addView(tips);

        LinearLayout custom = Ui.card(this);
        custom.addView(Ui.title(this, "Custom Department Dictionary", 18));
        custom.addView(Ui.body(this, "The app already knows common banking departments. Add your bank-specific departments or handwritten aliases below. One department per line. Optional aliases can follow '=' separated by commas."));
        custom.addView(Ui.label(this, "FORMAT EXAMPLES"));
        TextView ex = Ui.body(this, "HRM = HR, Personnel, कार्मिक\nChairman's Secretariat = CS, Chairman Office, अध्यक्ष सचिवालय");
        ex.setTextColor(Ui.BLUE_DARK);
        custom.addView(ex);
        custom.addView(Ui.label(this, "YOUR CUSTOM ENTRIES"));
        customDepartments = Ui.multilineInput(this, "Department = alias 1, alias 2, हिंदी नाम", 8);
        customDepartments.setText(prefs.getString("custom_departments", ""));
        custom.addView(customDepartments);
        Button save = Ui.primary(this, "Save Department Dictionary");
        save.setOnClickListener(v -> {
            prefs.edit().putString("custom_departments", customDepartments.getText().toString().trim()).apply();
            Toast.makeText(this, "Local AI department dictionary updated.", Toast.LENGTH_SHORT).show();
        });
        custom.addView(save);
        root.addView(custom);

        LinearLayout privacy = Ui.card(this);
        privacy.addView(Ui.title(this, "Privacy", 18));
        privacy.addView(Ui.body(this, "This build does not request Android Internet permission. Letter photos, OCR text and register entries stay inside the app's local storage unless you explicitly share the exported Excel file."));
        root.addView(privacy);

        Button done = Ui.secondary(this, "Done");
        done.setOnClickListener(v -> finish());
        root.addView(done);

        Ui.prepareScreen(this, sc);
        setContentView(sc);
    }
}
