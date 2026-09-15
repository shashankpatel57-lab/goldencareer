package com.openai.inwardregister;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AdminSettings {
    private static final String PREF = "inward_admin_v1";
    private static final String ALIAS = "inward_api_keys_aes_v1";
    private final SharedPreferences sp;

    public AdminSettings(Context c) { sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public boolean hasPin() { return sp.contains("pin_hash"); }

    public void setPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        sp.edit()
                .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("pin_hash", hashPin(pin, salt))
                .apply();
    }

    public boolean verifyPin(String pin) {
        try {
            byte[] salt = Base64.decode(sp.getString("pin_salt", ""), Base64.NO_WRAP);
            return constantTimeEquals(sp.getString("pin_hash", ""), hashPin(pin, salt));
        } catch (Exception e) { return false; }
    }

    private String hashPin(String pin, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(md.digest(), Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i=0; i<a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    public String grokModel() { return sp.getString("grok_model", "grok-4.6"); }
    public String geminiModel() { return sp.getString("gemini_model", "gemini-3.8-flash"); }
    public void setModels(String grok, String gemini) {
        sp.edit().putString("grok_model", clean(grok, "grok-4.6"))
                .putString("gemini_model", clean(gemini, "gemini-3.8-flash")).apply();
    }

    public void setGrokKeys(String raw) { putEncrypted("grok_keys", normalizeKeys(raw)); }
    public void setGeminiKeys(String raw) { putEncrypted("gemini_keys", normalizeKeys(raw)); }
    public String getGrokKeysRaw() { return getDecrypted("grok_keys"); }
    public String getGeminiKeysRaw() { return getDecrypted("gemini_keys"); }

    public List<String> orderedKeys(String provider) {
        List<String> keys = parseKeys(provider.equals("grok") ? getGrokKeysRaw() : getGeminiKeysRaw());
        if (keys.isEmpty()) return keys;
        int idx = sp.getInt("rr_" + provider, 0);
        idx = Math.floorMod(idx, keys.size());
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i=0; i<keys.size(); i++) {
            String k = keys.get((idx + i) % keys.size());
            if (cooldownUntil(provider, k) <= now) out.add(k);
        }
        sp.edit().putInt("rr_" + provider, (idx + 1) % keys.size()).apply();
        return out;
    }

    public void setCooldown(String provider, String key, long untilMs) {
        sp.edit().putLong("cool_" + provider + "_" + shortHash(key), untilMs).apply();
    }

    public long cooldownUntil(String provider, String key) {
        return sp.getLong("cool_" + provider + "_" + shortHash(key), 0L);
    }

    private String shortHash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(d, Base64.NO_WRAP | Base64.URL_SAFE).substring(0, 12);
        } catch (Exception e) { return String.valueOf(s.hashCode()); }
    }

    private List<String> parseKeys(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String line : raw.split("[\\r\\n,;]+")) {
            String v = line.trim();
            if (!v.isEmpty() && !out.contains(v)) out.add(v);
        }
        return out;
    }

    private String normalizeKeys(String raw) {
        return String.join("\n", parseKeys(raw));
    }

    private String clean(String s, String def) { return s == null || s.trim().isEmpty() ? def : s.trim(); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }

    private void putEncrypted(String name, String value) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] enc = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            sp.edit().putString(name + "_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString(name + "_ct", Base64.encodeToString(enc, Base64.NO_WRAP)).apply();
        } catch (Exception e) {
            // Keystore errors should not silently expose keys in plaintext.
        }
    }

    private String getDecrypted(String name) {
        try {
            String ivs = sp.getString(name + "_iv", "");
            String cts = sp.getString(name + "_ct", "");
            if (ivs.isEmpty() || cts.isEmpty()) return "";
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(ivs, Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(cts, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
}
