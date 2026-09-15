package com.openai.inwardregister;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SessionStore {
    private static final String PREF = "inward_register_session_v1";
    private static final String K_ACTIVE = "active";
    private static final String K_DATE = "date";
    private static final String K_NEXT = "next";
    private static final String K_ENTRIES = "entries";
    private final SharedPreferences sp;

    public SessionStore(Context c) {
        sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public boolean isActive() { return sp.getBoolean(K_ACTIVE, false); }

    public String defaultToday() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date());
    }

    public void start(String receivingDate, int startingNo) {
        sp.edit()
                .putBoolean(K_ACTIVE, true)
                .putString(K_DATE, receivingDate)
                .putInt(K_NEXT, startingNo)
                .putString(K_ENTRIES, "[]")
                .apply();
    }

    public void closeSession() {
        sp.edit().putBoolean(K_ACTIVE, false).apply();
    }

    public String receivingDate() { return sp.getString(K_DATE, defaultToday()); }
    public int nextInwardNo() { return sp.getInt(K_NEXT, 1); }

    public JSONArray entries() {
        try { return new JSONArray(sp.getString(K_ENTRIES, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private void saveEntries(JSONArray arr) {
        sp.edit().putString(K_ENTRIES, arr.toString()).apply();
    }

    public void addEntry(JSONObject o) {
        JSONArray arr = entries();
        arr.put(o);
        saveEntries(arr);
        sp.edit().putInt(K_NEXT, nextInwardNo() + 1).apply();
    }

    public void updateEntry(int index, JSONObject o) {
        JSONArray arr = entries();
        if (index >= 0 && index < arr.length()) {
            try { arr.put(index, o); saveEntries(arr); } catch (JSONException ignored) {}
        }
    }

    public void deleteEntry(int index) {
        JSONArray arr = entries();
        if (index >= 0 && index < arr.length()) {
            arr.remove(index);
            saveEntries(arr);
        }
    }

    public JSONObject entry(int index) {
        return entries().optJSONObject(index);
    }

    public JSONObject newEntrySkeleton(String trackingId) {
        JSONObject o = new JSONObject();
        try {
            o.put("Receiving_Date", receivingDate());
            o.put("Inward_No", nextInwardNo());
            o.put("Letter_No", "");
            o.put("Letter_Date", "");
            o.put("Sender", "");
            o.put("Subject", "");
            o.put("Department", "");
            o.put("Tracking_ID", trackingId == null ? "" : trackingId);
            o.put("Signature", "");
            o.put("AI_Provider", "");
        } catch (JSONException ignored) {}
        return o;
    }
}
