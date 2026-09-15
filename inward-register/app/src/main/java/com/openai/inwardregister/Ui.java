package com.openai.inwardregister;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView title(Context c, String text, int sp) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(25, 31, 40));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(c, 4), 0, dp(c, 4));
        return t;
    }

    public static TextView body(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Color.rgb(84, 92, 104));
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    public static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(Color.rgb(80, 89, 103));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(c, 8), 0, dp(c, 4));
        return t;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(Color.rgb(28, 33, 42));
        e.setHintTextColor(Color.rgb(145, 151, 162));
        e.setSingleLine(true);
        e.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));
        e.setBackground(roundRect(Color.WHITE, Color.rgb(215, 220, 229), 12, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 8));
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
        b.setBackground(roundRect(Color.rgb(11, 87, 208), Color.TRANSPARENT, 14, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52));
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        b.setLayoutParams(lp);
        return b;
    }

    public static Button secondary(Context c, String text) {
        Button b = primary(c, text);
        b.setTextColor(Color.rgb(25, 74, 145));
        b.setBackground(roundRect(Color.rgb(236, 243, 255), Color.rgb(198, 214, 239), 14, c));
        return b;
    }

    public static Button danger(Context c, String text) {
        Button b = primary(c, text);
        b.setTextColor(Color.rgb(150, 35, 35));
        b.setBackground(roundRect(Color.rgb(255, 239, 239), Color.rgb(244, 195, 195), 14, c));
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setBackground(roundRect(Color.WHITE, Color.rgb(225, 229, 236), 16, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 12));
        l.setLayoutParams(lp);
        l.setElevation(dp(c, 1));
        return l;
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
        v.setBackgroundColor(Color.rgb(232, 235, 240));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        lp.setMargins(0, dp(c, 8), 0, dp(c, 8));
        v.setLayoutParams(lp);
        return v;
    }
}
