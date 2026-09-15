package com.openai.inwardregister;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanActivity extends Activity {
    private static final int REQ_TRACK = 401;
    private static final int REQ_PAGE = 402;
    private SessionStore store;
    private EditText trackingField;
    private TextView trackingStatus, pagesStatus;
    private LinearLayout trackingSection, pagesSection;
    private Button continueButton, doneButton;
    private File pendingFile;
    private Uri pendingUri;
    private final ArrayList<String> pages = new ArrayList<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store = new SessionStore(this);
        if (!store.isActive()) { finish(); return; }
        render();
    }

    private void render() {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(Color.rgb(247,249,252));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,28));
        sc.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView step = Ui.body(this, "NEW LETTER  •  INWARD #" + store.nextInwardNo());
        step.setTextColor(Color.rgb(11,87,208));
        step.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        root.addView(step);
        root.addView(Ui.title(this, "Scan incoming mail", 27));
        root.addView(Ui.body(this, "First capture the envelope/tracking label. Then photograph every page belonging to this one letter."));

        trackingSection = Ui.card(this);
        trackingSection.addView(Ui.title(this, "1  •  Tracking / Envelope", 19));
        trackingSection.addView(Ui.body(this, "Scanning is processed locally on the phone using barcode recognition and OCR, so this step does not consume an AI API call."));
        Button scanTracking = Ui.primary(this, "Scan Tracking Number");
        scanTracking.setOnClickListener(v -> openCamera(REQ_TRACK));
        trackingSection.addView(scanTracking);

        Button byHand = Ui.secondary(this, "No Envelope — Mark as “By Hand”");
        byHand.setOnClickListener(v -> {
            trackingField.setText("By Hand");
            trackingStatus.setText("Marked as hand-delivered. You can continue to letter pages.");
            updateContinue();
        });
        trackingSection.addView(byHand);

        trackingSection.addView(Ui.label(this, "TRACKING ID — REVIEW / CORRECT IF NEEDED"));
        trackingField = Ui.input(this, "Tracking ID");
        trackingField.setOnEditorActionListener((v,a,e) -> { updateContinue(); return false; });
        trackingField.setOnFocusChangeListener((v,has) -> { if (!has) updateContinue(); });
        trackingSection.addView(trackingField);
        trackingStatus = Ui.body(this, "Not scanned yet.");
        trackingStatus.setPadding(0,0,0,Ui.dp(this,5));
        trackingSection.addView(trackingStatus);
        continueButton = Ui.primary(this, "Continue to Letter Pages");
        continueButton.setEnabled(false); continueButton.setAlpha(.45f);
        continueButton.setOnClickListener(v -> showPagesStep());
        trackingSection.addView(continueButton);
        root.addView(trackingSection);

        pagesSection = Ui.card(this);
        pagesSection.setVisibility(View.GONE);
        pagesSection.addView(Ui.title(this, "2  •  Capture Letter Pages", 19));
        pagesSection.addView(Ui.body(this, "Capture page 1, page 2, page 3… in order. Use “Remove Last Page” if a photo is blurred or belongs to the wrong letter."));
        Button capturePage = Ui.primary(this, "Capture Next Page");
        capturePage.setOnClickListener(v -> openCamera(REQ_PAGE));
        pagesSection.addView(capturePage);
        Button remove = Ui.secondary(this, "Remove Last Page");
        remove.setOnClickListener(v -> removeLastPage());
        pagesSection.addView(remove);
        pagesStatus = Ui.body(this, "0 pages captured");
        pagesStatus.setTextSize(16);
        pagesSection.addView(pagesStatus);
        doneButton = Ui.primary(this, "Done Scanning This Letter → Process with AI");
        doneButton.setEnabled(false); doneButton.setAlpha(.45f);
        doneButton.setOnClickListener(v -> processLetter());
        pagesSection.addView(doneButton);
        root.addView(pagesSection);

        Button cancel = Ui.danger(this, "Cancel This Letter");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);
        setContentView(sc);
    }

    private void updateContinue() {
        boolean ok = trackingField != null && !trackingField.getText().toString().trim().isEmpty();
        continueButton.setEnabled(ok); continueButton.setAlpha(ok ? 1f : .45f);
    }

    private void showPagesStep() {
        updateContinue();
        if (!continueButton.isEnabled()) { Toast.makeText(this, "Scan, enter or mark the tracking field first.", Toast.LENGTH_SHORT).show(); return; }
        trackingSection.setVisibility(View.GONE);
        pagesSection.setVisibility(View.VISIBLE);
    }

    private void openCamera(int requestCode) {
        try {
            File dir = new File(getFilesDir(), "mail_scans");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create scan folder");
            pendingFile = new File(dir, (requestCode == REQ_TRACK ? "tracking_" : "page_") + System.currentTimeMillis() + "_" + UUID.randomUUID() + ".jpg");
            pendingUri = FileProvider.getUriForFile(this, getPackageName() + ".files", pendingFile);
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(MediaStore.EXTRA_OUTPUT, pendingUri);
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(cam, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No camera app was found on this phone.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Camera error").setMessage(e.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || pendingFile == null || !pendingFile.exists() || pendingFile.length() == 0) return;
        if (requestCode == REQ_TRACK) {
            trackingStatus.setText("Reading tracking information locally…");
            scanTrackingImage(pendingUri);
        } else if (requestCode == REQ_PAGE) {
            pages.add(pendingFile.getAbsolutePath());
            updatePages();
        }
        pendingFile = null; pendingUri = null;
    }

    private void scanTrackingImage(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            BarcodeScanner scanner = BarcodeScanning.getClient();
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        String best = "";
                        for (Barcode b : barcodes) {
                            String raw = b.getRawValue();
                            if (raw != null && raw.trim().length() >= 6) { best = raw.trim(); break; }
                        }
                        scanner.close();
                        if (!best.isEmpty()) applyTracking(best, "Barcode detected locally");
                        else runTextOcr(image);
                    })
                    .addOnFailureListener(e -> { scanner.close(); runTextOcr(image); });
        } catch (Exception e) {
            trackingStatus.setText("Could not read automatically. Enter Tracking ID manually.");
        }
    }

    private void runTextOcr(InputImage image) {
        TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        rec.process(image)
                .addOnSuccessListener(result -> {
                    String candidate = extractTrackingCandidate(result.getText());
                    rec.close();
                    if (!candidate.isEmpty()) applyTracking(candidate, "Tracking text detected locally");
                    else {
                        trackingStatus.setText("No confident tracking number found. Type it manually, or rescan the envelope.");
                        trackingField.requestFocus();
                    }
                })
                .addOnFailureListener(e -> {
                    rec.close();
                    trackingStatus.setText("OCR could not read this envelope. Type Tracking ID manually.");
                });
    }

    private void applyTracking(String value, String message) {
        trackingField.setText(value);
        trackingStatus.setText(message + ". Please verify it before continuing.");
        updateContinue();
    }

    private String extractTrackingCandidate(String text) {
        if (text == null) return "";
        String upper = text.toUpperCase(Locale.US).replaceAll("[^A-Z0-9:/\\-\\n ]", " ");
        Pattern india = Pattern.compile("\\b[A-Z]{2}\\d{9}IN\\b");
        Matcher m = india.matcher(upper.replace(" ", ""));
        if (m.find()) return m.group();

        Pattern labelled = Pattern.compile("(?i)(?:TRACK(?:ING)?|CONSIGNMENT|AWB|ARTICLE|DOCKET|SPEED\\s*POST|REFERENCE)\\s*(?:NO|NUMBER|ID|#|:|-)*\\s*([A-Z0-9][A-Z0-9\\-]{7,29})");
        m = labelled.matcher(text.replaceAll("\\s+", " "));
        if (m.find()) return m.group(1).trim();

        Pattern generic = Pattern.compile("\\b(?=[A-Z0-9-]{9,25}\\b)(?=[A-Z0-9-]*\\d)[A-Z0-9-]{9,25}\\b");
        m = generic.matcher(upper);
        String best = "";
        while (m.find()) {
            String c = m.group().replace("-", "");
            if (c.matches("\\d{10,14}")) continue; // usually phone / timestamp
            if (c.length() > best.length()) best = m.group();
        }
        return best;
    }

    private void removeLastPage() {
        if (pages.isEmpty()) { Toast.makeText(this, "No page to remove.", Toast.LENGTH_SHORT).show(); return; }
        String p = pages.remove(pages.size()-1);
        try { new File(p).delete(); } catch (Exception ignored) {}
        updatePages();
    }

    private void updatePages() {
        int n = pages.size();
        pagesStatus.setText(n + (n == 1 ? " page captured" : " pages captured"));
        doneButton.setEnabled(n > 0); doneButton.setAlpha(n > 0 ? 1f : .45f);
    }

    private void processLetter() {
        if (pages.isEmpty()) return;
        String tracking = trackingField.getText().toString().trim();
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Reading Letter");
        pd.setMessage("AI is reading all captured pages and checking handwritten routing/department notes…");
        pd.setIndeterminate(true); pd.setCancelable(false); pd.show();

        AiClient ai = new AiClient(this);
        ai.extract(new ArrayList<>(pages), new AiClient.Callback() {
            @Override public void onSuccess(JSONObject data, String provider) {
                if (!isFinishing()) pd.dismiss();
                openReview(tracking, data, provider, null);
            }
            @Override public void onFailure(String message) {
                if (!isFinishing()) pd.dismiss();
                openReview(tracking, new JSONObject(), "Manual Review", message);
            }
        });
    }

    private void openReview(String tracking, JSONObject extracted, String provider, String warning) {
        JSONObject entry = store.newEntrySkeleton(tracking);
        try {
            entry.put("Letter_No", extracted.optString("Letter_No", ""));
            entry.put("Letter_Date", extracted.optString("Letter_Date", ""));
            entry.put("Sender", extracted.optString("Sender", ""));
            entry.put("Subject", extracted.optString("Subject", ""));
            entry.put("Department", extracted.optString("Department", ""));
            entry.put("AI_Provider", provider);
        } catch (Exception ignored) {}
        Intent it = new Intent(this, ReviewActivity.class);
        it.putExtra("entry_json", entry.toString());
        if (warning != null) it.putExtra("warning", warning);
        startActivity(it);
        finish();
    }
}
