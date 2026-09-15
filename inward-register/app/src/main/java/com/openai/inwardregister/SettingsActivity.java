package com.openai.inwardregister;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private AdminSettings settings;
    private EditText grokModel, geminiModel, grokKeys, geminiKeys;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        settings = new AdminSettings(this);
        if (settings.hasPin()) askPin(); else createPinDialog();
    }

    private void askPin() {
        EditText pin = pinInput("Admin PIN");
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Admin Login")
                .setMessage("Enter the PIN to manage AI providers and API keys.")
                .setView(pin)
                .setNegativeButton("Cancel", (x,w) -> finish())
                .setPositiveButton("Unlock", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (settings.verifyPin(pin.getText().toString())) { d.dismiss(); render(); }
            else { pin.setError("Incorrect PIN"); pin.requestFocus(); }
        }));
        d.setCanceledOnTouchOutside(false);
        d.show();
    }

    private void createPinDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this,22), 0, Ui.dp(this,22), 0);
        EditText p1 = pinInput("Create PIN (4–12 digits)");
        EditText p2 = pinInput("Confirm PIN");
        box.addView(p1); box.addView(p2);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Create Admin PIN")
                .setMessage("This PIN protects the AI settings screen on this phone.")
                .setView(box)
                .setNegativeButton("Cancel", (x,w) -> finish())
                .setPositiveButton("Create", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = p1.getText().toString(), b = p2.getText().toString();
            if (!a.matches("\\d{4,12}")) { p1.setError("Use 4–12 digits"); return; }
            if (!a.equals(b)) { p2.setError("PINs do not match"); return; }
            settings.setPin(a); d.dismiss(); render();
        }));
        d.setCanceledOnTouchOutside(false);
        d.show();
    }

    private EditText pinInput(String hint) {
        EditText e = Ui.input(this, hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    private void render() {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(Color.rgb(247,249,252));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,28));
        sc.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = Ui.body(this, "ADMIN • SECURED ON DEVICE");
        badge.setTextColor(Color.rgb(11,87,208));
        badge.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        root.addView(badge);
        root.addView(Ui.title(this, "AI Provider Settings", 27));
        root.addView(Ui.body(this, "Grok is attempted first. If all currently available Grok keys fail, the app falls back to Gemini. Keys are encrypted with Android Keystore. A key receiving HTTP 429 is paused using Retry-After (or a short cooldown) rather than repeatedly hammering the provider."));

        LinearLayout grok = Ui.card(this);
        grok.addView(Ui.title(this, "Primary • Grok Vision", 19));
        grok.addView(Ui.label(this, "MODEL"));
        grokModel = Ui.input(this, "grok-4.6");
        grokModel.setText(settings.grokModel()); grok.addView(grokModel);
        grok.addView(Ui.label(this, "API KEYS — ONE PER LINE"));
        grokKeys = Ui.multilineInput(this, "xAI API key 1\nxAI API key 2", 5);
        grokKeys.setText(settings.getGrokKeysRaw()); grok.addView(grokKeys);
        grok.addView(Ui.body(this, "Round-robin selection distributes normal requests across your configured authorized keys. Invalid or rate-limited keys are temporarily skipped."));
        root.addView(grok);

        LinearLayout gem = Ui.card(this);
        gem.addView(Ui.title(this, "Fallback • Google Gemini Vision", 19));
        gem.addView(Ui.label(this, "MODEL"));
        geminiModel = Ui.input(this, "gemini-3.8-flash");
        geminiModel.setText(settings.geminiModel()); gem.addView(geminiModel);
        gem.addView(Ui.label(this, "API KEYS — ONE PER LINE"));
        geminiKeys = Ui.multilineInput(this, "Gemini API key 1\nGemini API key 2", 5);
        geminiKeys.setText(settings.getGeminiKeysRaw()); gem.addView(geminiKeys);
        root.addView(gem);

        Button save = Ui.primary(this, "Save AI Settings");
        save.setOnClickListener(v -> {
            settings.setModels(grokModel.getText().toString(), geminiModel.getText().toString());
            settings.setGrokKeys(grokKeys.getText().toString());
            settings.setGeminiKeys(geminiKeys.getText().toString());
            Toast.makeText(this, "AI settings saved securely.", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        Button changePin = Ui.secondary(this, "Change Admin PIN");
        changePin.setOnClickListener(v -> changePinDialog());
        root.addView(changePin);

        Button done = Ui.secondary(this, "Done");
        done.setOnClickListener(v -> finish());
        root.addView(done);
        setContentView(sc);
    }

    private void changePinDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this,22), 0, Ui.dp(this,22), 0);
        EditText old = pinInput("Current PIN");
        EditText p1 = pinInput("New PIN");
        EditText p2 = pinInput("Confirm new PIN");
        box.addView(old); box.addView(p1); box.addView(p2);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Change Admin PIN")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Change", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!settings.verifyPin(old.getText().toString())) { old.setError("Incorrect current PIN"); return; }
            String a=p1.getText().toString(), b=p2.getText().toString();
            if (!a.matches("\\d{4,12}")) { p1.setError("Use 4–12 digits"); return; }
            if (!a.equals(b)) { p2.setError("PINs do not match"); return; }
            settings.setPin(a); d.dismiss(); Toast.makeText(this, "Admin PIN changed.", Toast.LENGTH_SHORT).show();
        }));
        d.show();
    }
}
