package com.openai.inwardregister;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public final class Ui {
    public static final int BG = Color.rgb(246, 248, 252);
    public static final int TEXT = Color.rgb(20, 28, 40);
    public static final int MUTED = Color.rgb(92, 101, 115);
    public static final int BLUE = Color.rgb(19, 89, 214);
    public static final int BLUE_DARK = Color.rgb(10, 57, 145);
    public static final int GREEN = Color.rgb(12, 130, 92);

    private Ui() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /**
     * Android 15+ enforces edge-to-edge for targetSdk 35. We deliberately handle
     * system-bar insets here so content never sits under the status/navigation bars.
     */
    public static void prepareScreen(Activity activity, View content) {
        Window w = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(w, w.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    public static TextView title(Context c, String text, int sp) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setLineSpacing(0, 1.03f);
        t.setPadding(0, dp(c, 3), 0, dp(c, 5));
        return t;
    }

    public static TextView body(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(MUTED);
        t.setLineSpacing(dp(c, 1), 1.12f);
        return t;
    }

    public static TextView badge(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(BLUE_DARK);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
        t.setBackground(roundRect(Color.rgb(232, 240, 255), Color.rgb(195, 214, 247), 50, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 8));
        t.setLayoutParams(lp);
        return t;
    }

    public static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(Color.rgb(73, 84, 102));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLetterSpacing(.04f);
        t.setPadding(0, dp(c, 10), 0, dp(c, 5));
        return t;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(152, 160, 173));
        e.setSingleLine(true);
        e.setPadding(dp(c, 15), dp(c, 13), dp(c, 15), dp(c, 13));
        e.setBackground(roundRect(Color.WHITE, Color.rgb(210, 217, 228), 14, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 7));
        e.setLayoutParams(lp);
        return e;
    }

    public static EditText multilineInput(Context c, String hint, int lines) {
        EditText e = input(c, hint);
        e.setSingleLine(false);
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    public static Button primary(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        b.setBackground(roundRect(BLUE, Color.TRANSPARENT, 15, c));
        b.setElevation(dp(c, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 54));
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        b.setLayoutParams(lp);
        return b;
    }

    public static Button secondary(Context c, String text) {
        Button b = primary(c, text);
        b.setTextColor(BLUE_DARK);
        b.setElevation(0);
        b.setBackground(roundRect(Color.rgb(237, 244, 255), Color.rgb(196, 214, 243), 15, c));
        return b;
    }

    public static Button danger(Context c, String text) {
        Button b = primary(c, text);
        b.setTextColor(Color.rgb(146, 39, 45));
        b.setElevation(0);
        b.setBackground(roundRect(Color.rgb(255, 241, 242), Color.rgb(244, 200, 202), 15, c));
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 17), dp(c, 18), dp(c, 17));
        l.setBackground(roundRect(Color.WHITE, Color.rgb(224, 229, 237), 19, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 13));
        l.setLayoutParams(lp);
        l.setElevation(dp(c, 1));
        return l;
    }

    public static LinearLayout successCard(Context c) {
        LinearLayout l = card(c);
        l.setBackground(roundRect(Color.rgb(240, 250, 246), Color.rgb(187, 229, 213), 19, c));
        return l;
    }

    public static TextView metric(Context c, String value, String caption) {
        TextView t = new TextView(c);
        t.setText(value + "\n" + caption);
        t.setTextSize(13);
        t.setTextColor(MUTED);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    public static GradientDrawable roundRect(int fill, int stroke, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.rgb(233, 236, 242));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        lp.setMargins(0, dp(c, 10), 0, dp(c, 10));
        v.setLayoutParams(lp);
        return v;
    }
}
