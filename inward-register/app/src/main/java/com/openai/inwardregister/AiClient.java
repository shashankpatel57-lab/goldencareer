package com.openai.inwardregister;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiClient {
    public interface Callback {
        void onSuccess(JSONObject data, String provider);
        void onFailure(String message);
    }

    private final Context context;
    private final AdminSettings settings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AiClient(Context context) {
        this.context = context.getApplicationContext();
        this.settings = new AdminSettings(context);
    }

    public void extract(List<String> imagePaths, Callback cb) {
        executor.execute(() -> {
            try {
                if (imagePaths == null || imagePaths.isEmpty()) {
                    fail(cb, "No letter pages were captured.");
                    return;
                }
                List<String> images = new ArrayList<>();
                for (String path : imagePaths) images.add(encodeImage(new File(path)));

                StringBuilder errors = new StringBuilder();
                List<String> grokKeys = settings.orderedKeys("grok");
                for (String key : grokKeys) {
                    try {
                        JSONObject out = callGrok(key, images);
                        success(cb, normalize(out), "Grok / " + settings.grokModel());
                        return;
                    } catch (ApiException e) {
                        handleFailure("grok", key, e);
                        errors.append("Grok: ").append(e.getMessage()).append("; ");
                    } catch (Exception e) {
                        errors.append("Grok: ").append(e.getMessage()).append("; ");
                    }
                }

                List<String> geminiKeys = settings.orderedKeys("gemini");
                for (String key : geminiKeys) {
                    try {
                        JSONObject out = callGemini(key, images);
                        success(cb, normalize(out), "Gemini / " + settings.geminiModel());
                        return;
                    } catch (ApiException e) {
                        handleFailure("gemini", key, e);
                        errors.append("Gemini: ").append(e.getMessage()).append("; ");
                    } catch (Exception e) {
                        errors.append("Gemini: ").append(e.getMessage()).append("; ");
                    }
                }

                if (grokKeys.isEmpty() && geminiKeys.isEmpty()) {
                    fail(cb, "No AI API key is configured. You can still enter the fields manually on the review screen.");
                } else {
                    fail(cb, errors.length() == 0 ? "AI extraction failed. Please review the fields manually." : errors.toString());
                }
            } catch (Exception e) {
                fail(cb, "AI processing error: " + e.getMessage());
            }
        });
    }

    private void handleFailure(String provider, String key, ApiException e) {
        long now = System.currentTimeMillis();
        if (e.code == 429) {
            long sec = e.retryAfterSeconds > 0 ? e.retryAfterSeconds : 60;
            settings.setCooldown(provider, key, now + Math.min(sec, 3600) * 1000L);
        } else if (e.code == 401 || e.code == 403) {
            settings.setCooldown(provider, key, now + 6L * 60 * 60 * 1000);
        }
    }

    private JSONObject callGrok(String key, List<String> images) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", settings.grokModel());
        body.put("temperature", 0.1);
        JSONArray messages = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        JSONArray content = new JSONArray();
        for (String b64 : images) {
            JSONObject part = new JSONObject();
            part.put("type", "image_url");
            JSONObject iu = new JSONObject();
            iu.put("url", "data:image/jpeg;base64," + b64);
            iu.put("detail", "high");
            part.put("image_url", iu);
            content.put(part);
        }
        JSONObject text = new JSONObject();
        text.put("type", "text");
        text.put("text", prompt());
        content.put(text);
        msg.put("content", content);
        messages.put(msg);
        body.put("messages", messages);

        HttpResponse r = postJson("https://api.x.ai/v1/chat/completions", body.toString(), "Bearer " + key, null);
        if (r.code < 200 || r.code >= 300) throw new ApiException(r.code, r.retryAfter, compactError(r.body));
        JSONObject root = new JSONObject(r.body);
        String txt = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
        return parseJsonObject(txt);
    }

    private JSONObject callGemini(String key, List<String> images) throws Exception {
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        for (String b64 : images) {
            JSONObject p = new JSONObject();
            JSONObject inline = new JSONObject();
            inline.put("mime_type", "image/jpeg");
            inline.put("data", b64);
            p.put("inline_data", inline);
            parts.put(p);
        }
        JSONObject pt = new JSONObject();
        pt.put("text", prompt());
        parts.put(pt);
        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);
        JSONObject cfg = new JSONObject();
        cfg.put("temperature", 0.1);
        cfg.put("responseMimeType", "application/json");
        body.put("generationConfig", cfg);

        String model = settings.geminiModel();
        URL u = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key);
        HttpResponse r = postJson(u.toString(), body.toString(), null, null);
        if (r.code < 200 || r.code >= 300) throw new ApiException(r.code, r.retryAfter, compactError(r.body));
        JSONObject root = new JSONObject(r.body);
        String txt = root.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text", "");
        return parseJsonObject(txt);
    }

    private String prompt() {
        return "You are extracting fields from scanned physical incoming letters. Read ALL supplied pages as one letter. " +
                "Return ONLY one JSON object with exactly these string keys: " +
                "{\"Letter_No\":\"\",\"Letter_Date\":\"\",\"Sender\":\"\",\"Subject\":\"\",\"Department\":\"\"}. " +
                "Rules: (1) Letter_No is the sender's/reference letter number, not a phone number or tracking ID. " +
                "(2) Letter_Date is the date printed on the letter; preserve a clear human date format. " +
                "(3) Sender should be concise but sufficiently identify the organization/person and office if visible. " +
                "(4) Find the explicit Subject/विषय line. If absent, infer a brief one-line subject from the letter content, without inventing facts. " +
                "(5) Department is especially important: inspect margins, stamps, routing marks and HANDWRITTEN PEN NOTES for a department/section name. " +
                "If no department is visible, return an empty string. Do not include commentary, confidence scores or extra keys.";
    }

    private JSONObject normalize(JSONObject in) throws Exception {
        JSONObject out = new JSONObject();
        out.put("Letter_No", in.optString("Letter_No", "").trim());
        out.put("Letter_Date", in.optString("Letter_Date", "").trim());
        out.put("Sender", in.optString("Sender", "").trim());
        out.put("Subject", in.optString("Subject", "").trim());
        out.put("Department", in.optString("Department", "").trim());
        return out;
    }

    private JSONObject parseJsonObject(String raw) throws Exception {
        if (raw == null) throw new Exception("Empty model response");
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "");
            s = s.replaceFirst("\\s*```$", "");
        }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b <= a) throw new Exception("Model returned non-JSON content");
        return new JSONObject(s.substring(a, b + 1));
    }

    private String encodeImage(File f) throws Exception {
        Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (src == null) throw new Exception("Could not read " + f.getName());
        int w = src.getWidth(), h = src.getHeight();
        int max = 1800;
        Bitmap use = src;
        if (Math.max(w, h) > max) {
            float scale = max / (float)Math.max(w, h);
            use = Bitmap.createScaledBitmap(src, Math.round(w * scale), Math.round(h * scale), true);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        use.compress(Bitmap.CompressFormat.JPEG, 82, bos);
        if (use != src) use.recycle();
        src.recycle();
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    private HttpResponse postJson(String url, String json, String auth, String apiKeyHeader) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(30000);
        c.setReadTimeout(90000);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        if (auth != null) c.setRequestProperty("Authorization", auth);
        if (apiKeyHeader != null) c.setRequestProperty("x-goog-api-key", apiKeyHeader);
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        long retry = parseRetry(c.getHeaderField("Retry-After"));
        InputStream is = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String body = readAll(is);
        c.disconnect();
        return new HttpResponse(code, body, retry);
    }

    private long parseRetry(String s) {
        try { return s == null ? 0 : Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    private String compactError(String body) {
        if (body == null) return "HTTP error";
        String s = body.replaceAll("\\s+", " ").trim();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }

    private void success(Callback cb, JSONObject data, String provider) {
        android.os.Handler h = new android.os.Handler(context.getMainLooper());
        h.post(() -> cb.onSuccess(data, provider));
    }

    private void fail(Callback cb, String msg) {
        android.os.Handler h = new android.os.Handler(context.getMainLooper());
        h.post(() -> cb.onFailure(msg));
    }

    private static class HttpResponse {
        final int code; final String body; final long retryAfter;
        HttpResponse(int c, String b, long r) { code = c; body = b; retryAfter = r; }
    }
    private static class ApiException extends Exception {
        final int code; final long retryAfterSeconds;
        ApiException(int code, long retry, String message) { super("HTTP " + code + ": " + message); this.code = code; this.retryAfterSeconds = retry; }
    }
}
