package com.openai.inwardregister;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fully local document-intelligence engine. No network/API is used.
 * It combines Latin + Devanagari OCR, an enhanced handwriting pass,
 * fuzzy department classification, structured field extraction and
 * extractive subject synthesis.
 */
public class AiClient {
    public interface Callback {
        void onSuccess(JSONObject data, String provider);
        void onFailure(String message);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static class OcrPage {
        String mainText = "";
        String handwritingText = "";
    }

    public AiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public void extract(List<String> imagePaths, Callback cb) {
        executor.execute(() -> {
            try {
                if (imagePaths == null || imagePaths.isEmpty()) {
                    fail(cb, "No letter pages were captured.");
                    return;
                }

                StringBuilder document = new StringBuilder();
                StringBuilder handwriting = new StringBuilder();
                int readable = 0;
                for (String path : imagePaths) {
                    OcrPage p = readPage(new File(path));
                    if (!p.mainText.trim().isEmpty()) readable++;
                    document.append("\n").append(p.mainText);
                    handwriting.append("\n").append(p.handwritingText);
                }

                if (readable == 0) {
                    fail(cb, "Local OCR could not read the captured pages. Retake the photos in brighter light and keep the page flat.");
                    return;
                }

                String text = cleanupDocument(document.toString());
                String hand = cleanupDocument(handwriting.toString());
                JSONObject out = new JSONObject();
                out.put("Letter_No", extractLetterNo(text));
                out.put("Letter_Date", extractLetterDate(text));
                out.put("Sender", extractSender(text));
                out.put("Department", extractDepartment(text + "\n" + hand));
                String subject = extractExplicitSubject(text);
                if (subject.isEmpty()) subject = synthesizeSubject(text);
                out.put("Subject", subject);
                out.put("Local_OCR_Text", trimTo(text, 9000));

                success(cb, out, "Local Inward AI • English + Hindi • Offline");
            } catch (Exception e) {
                fail(cb, "Local AI processing error: " + e.getMessage());
            }
        });
    }

    private OcrPage readPage(File f) throws Exception {
        Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (src == null) throw new Exception("Could not read " + f.getName());
        Bitmap base = scale(src, 2200);
        if (base != src) src.recycle();
        Bitmap enhanced = enhanceForHandwriting(base);

        String latin = recognize(base, false);
        String dev = recognize(base, true);
        String latinEnhanced = recognize(enhanced, false);
        String devEnhanced = recognize(enhanced, true);

        OcrPage p = new OcrPage();
        p.mainText = mergeLines(latin, dev);
        p.handwritingText = mergeLines(latinEnhanced, devEnhanced);
        if (enhanced != base) enhanced.recycle();
        base.recycle();
        return p;
    }

    private String recognize(Bitmap bitmap, boolean devanagari) throws Exception {
        TextRecognizer rec = devanagari
                ? TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build())
                : TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            Text result = Tasks.await(rec.process(InputImage.fromBitmap(bitmap, 0)));
            return result == null ? "" : result.getText();
        } finally {
            rec.close();
        }
    }

    private Bitmap scale(Bitmap src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        if (Math.max(w, h) <= maxSide) return src;
        float s = maxSide / (float)Math.max(w, h);
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w*s)), Math.max(1, Math.round(h*s)), true);
    }

    /** Strong local contrast pass that often reveals blue/black pen strokes and faint routing notes. */
    private Bitmap enhanceForHandwriting(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] px = new int[w*h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int i=0; i<px.length; i++) {
            int c = px[i];
            int r=Color.red(c), g=Color.green(c), b=Color.blue(c);
            int gray = (r*30 + g*59 + b*11)/100;
            int v;
            if (gray > 210) v = 255;
            else if (gray < 95) v = 0;
            else {
                v = (int)((gray - 128) * 1.65f + 128);
                v = Math.max(0, Math.min(255, v));
            }
            px[i] = Color.rgb(v,v,v);
        }
        out.setPixels(px,0,w,0,0,w,h);
        return out;
    }

    private String mergeLines(String a, String b) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String src : new String[]{a,b}) {
            if (src == null) continue;
            for (String line : src.split("\\r?\\n")) {
                String t = line.trim().replaceAll("\\s+", " ");
                if (t.isEmpty()) continue;
                String key = normalize(t);
                if (key.length() < 2 || seen.contains(key)) continue;
                seen.add(key); out.add(t);
            }
        }
        return String.join("\n", out);
    }

    private String cleanupDocument(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : s.split("\\r?\\n")) {
            String t = line.trim().replaceAll("[ \\t]+", " ");
            if (t.isEmpty()) continue;
            String key = normalize(t);
            if (key.length() < 2 || seen.contains(key)) continue;
            seen.add(key); out.append(t).append('\n');
        }
        return out.toString().trim();
    }

    private String extractLetterNo(String text) {
        String one = text.replace('\n',' ');
        String[] patterns = {
                "(?i)(?:letter|ref(?:erence)?|memo|office\\s*order|dispatch)\\s*(?:no\\.?|number|#|:)\\s*[:\\-]?\\s*([A-Z0-9./()_-]{3,45})",
                "(?:पत्रांक|पत्र\\s*संख्या|संदर्भ\\s*संख्या|क्रमांक|ज्ञापांक)\\s*[:\\-]?\\s*([A-Za-z0-9०-९./()_-]{3,45})"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p).matcher(one);
            if (m.find()) return cleanValue(m.group(1));
        }
        return "";
    }

    private String extractLetterDate(String text) {
        for (String line : lines(text)) {
            if (containsAny(line.toLowerCase(Locale.ROOT), "date", "dated", "दिनांक", "दिनाँक")) {
                String d = firstDate(line);
                if (!d.isEmpty()) return d;
            }
        }
        for (String line : lines(text)) {
            String d = firstDate(line);
            if (!d.isEmpty()) return d;
        }
        return "";
    }

    private String firstDate(String s) {
        String month = "Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?";
        String[] ps = {
                "\\b(\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4})\\b",
                "\\b(\\d{1,2}\\s+(?:"+month+")\\s+\\d{2,4})\\b",
                "\\b((?:"+month+")\\s+\\d{1,2},?\\s+\\d{2,4})\\b"
        };
        for (String p: ps) { Matcher m=Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(s); if(m.find()) return m.group(1).trim(); }
        return "";
    }

    private String extractSender(String text) {
        List<String> ls = lines(text);
        Pattern labelled = Pattern.compile("(?i)^(?:from|sender|प्रेषक|सेवा में)\\s*[:\\-]?\\s*(.+)$");
        for (String l: ls) {
            Matcher m=labelled.matcher(l.trim());
            if(m.find() && m.group(1).trim().length()>3) return trimTo(m.group(1).trim(), 120);
        }
        int limit = Math.min(ls.size(), 18);
        String best=""; int bestScore=-999;
        for(int i=0;i<limit;i++) {
            String l=ls.get(i).trim();
            if(l.length()<4 || l.length()>150) continue;
            String low=l.toLowerCase(Locale.ROOT);
            if(isMetadataLine(low) || isSalutation(low)) continue;
            int score = 20-i;
            if(containsAny(low,"bank","office","department","division","ministry","authority","corporation","limited","ltd","branch","university","government","govt","nagar","bhawan","बैंक","कार्यालय","विभाग","शाखा","मंत्रालय","निगम","प्राधिकरण")) score+=30;
            if(l.matches(".*[A-Za-zअ-ह].*")) score+=8;
            if(score>bestScore){bestScore=score;best=l;}
        }
        return trimTo(best,120);
    }

    private String extractExplicitSubject(String text) {
        List<String> ls = lines(text);
        Pattern p = Pattern.compile("(?i)^(?:subject|sub\\.?|regarding|re|विषय|विषयक)\\s*[:\\-–]?\\s*(.*)$");
        for(int i=0;i<ls.size();i++) {
            Matcher m=p.matcher(ls.get(i).trim());
            if(m.find()) {
                String s=m.group(1).trim();
                if(s.length()<4 && i+1<ls.size()) s=ls.get(i+1).trim();
                s=cleanSubject(s);
                if(s.length()>=4) return trimTo(s,180);
            }
        }
        return "";
    }

    private String synthesizeSubject(String text) {
        List<String> ls=lines(text);
        String best=""; int bestScore=-999;
        for(int i=0;i<ls.size();i++) {
            String l=ls.get(i).trim();
            if(l.length()<18 || l.length()>260) continue;
            String low=l.toLowerCase(Locale.ROOT);
            if(isMetadataLine(low) || isSalutation(low) || isClosing(low)) continue;
            int score=0;
            if(i>1 && i<Math.max(8,ls.size()*2/3)) score+=6;
            if(containsAny(low,"request","approval","sanction","complaint","grievance","payment","claim","loan","account","branch","audit","inspection","recovery","appointment","transfer","promotion","pension","insurance","regarding","information","submission","proposal","renewal","permission","अनुरोध","स्वीकृति","अनुमोदन","शिकायत","भुगतान","ऋण","खाता","शाखा","लेखा","निरीक्षण","वसूली","स्थानांतरण","पदोन्नति","पेंशन","बीमा","सूचना","प्रस्ताव","नवीनीकरण","अनुमति")) score+=25;
            if(l.matches(".*[.!?।]$")) score+=4;
            score += Math.min(12, l.split("\\s+").length/2);
            if(low.contains("http") || low.contains("www.") || low.contains("@")) score-=30;
            if(score>bestScore){bestScore=score;best=l;}
        }
        if(best.isEmpty()) {
            for(String l:ls) if(l.length()>=12 && !isMetadataLine(l.toLowerCase(Locale.ROOT))) {best=l;break;}
        }
        best=cleanSubject(best);
        if(best.isEmpty()) return "";
        String low=best.toLowerCase(Locale.ROOT);
        boolean hindi = devanagariRatio(best) > .20;
        if(hindi) {
            best=best.replaceFirst("^(महोदय|महोदया)[,,:\\- ]*","")
                    .replaceFirst("^(निवेदन है कि|अवगत कराना है कि|कृपया)[,,:\\- ]*","");
            if(best.length()>0 && !containsAny(best,"संबंध","विषय","अनुरोध","सूचना","प्रस्ताव","शिकायत")) best = "संबंधित पत्राचार: " + best;
        } else {
            best=best.replaceFirst("(?i)^(dear sir/?madam|sir|madam)[,,:\\- ]*","")
                    .replaceFirst("(?i)^(this is to inform you that|we wish to inform you that|it is submitted that|please note that)\\s*","");
            if(!containsAny(low,"regarding","request","application","proposal","complaint","approval","submission","information")) best="Regarding " + best;
        }
        return trimTo(best.replaceAll("\\s+"," ").trim(), 180);
    }

    private String extractDepartment(String corpus) {
        Map<String,List<String>> map = departmentCatalog();
        List<String> ls=lines(corpus);
        String best=""; double bestScore=0;
        for(Map.Entry<String,List<String>> e:map.entrySet()) {
            for(String alias:e.getValue()) {
                String a=normalize(alias);
                if(a.length()<2) continue;
                for(String line:ls) {
                    String n=normalize(line);
                    if(n.isEmpty()) continue;
                    double score=0;
                    if(n.contains(a)) score=1.0;
                    else if(line.length()<=55 || a.length()<=12) {
                        String compact=n.replace(" ","");
                        String acomp=a.replace(" ","");
                        if(compact.length()>=3 && acomp.length()>=3) score=similarity(compact,acomp);
                    }
                    if(score>bestScore && score>=0.72) { bestScore=score; best=e.getKey(); }
                }
            }
        }
        return best;
    }

    private Map<String,List<String>> departmentCatalog() {
        Map<String,List<String>> m=new LinkedHashMap<>();
        add(m,"HRM", "hrm","human resource","human resources","personnel","staff","कार्मिक","मानव संसाधन","स्थापना");
        add(m,"Credit", "credit","loans","loan department","advance","advances","ऋण","अग्रिम","क्रेडिट");
        add(m,"Recovery", "recovery","recovery department","npa recovery","वसूली","ऋण वसूली");
        add(m,"Complaint / Grievance", "complaint","grievance","customer grievance","शिकायत","शिकायत निवारण","जन शिकायत");
        add(m,"IT", "it","information technology","computer","technology","सूचना प्रौद्योगिकी","आईटी");
        add(m,"Audit", "audit","internal audit","concurrent audit","लेखा परीक्षा","अंकेक्षण","ऑडिट");
        add(m,"Inspection", "inspection","inspection department","निरीक्षण");
        add(m,"Risk", "risk","risk management","जोखिम","जोखिम प्रबंधन");
        add(m,"Planning & Development", "planning","development","planning and development","योजना","विकास","योजना एवं विकास");
        add(m,"Financial Inclusion", "financial inclusion","fi department","financial literacy","वित्तीय समावेशन");
        add(m,"General Administration", "general administration","gad","administration","प्रशासन","सामान्य प्रशासन");
        add(m,"Premises", "premises","estate","building","परिसर","भवन");
        add(m,"Law / Legal", "law","legal","legal department","विधि","कानूनी","विधिक");
        add(m,"Vigilance", "vigilance","सतर्कता");
        add(m,"Accounts", "accounts","accounting","finance and accounts","लेखा","लेखा विभाग");
        add(m,"Treasury / Investment", "treasury","investment","fund management","कोष","निवेश");
        add(m,"Agriculture", "agriculture","agri","कृषि");
        add(m,"MSME", "msme","micro small medium","सूक्ष्म लघु मध्यम");
        add(m,"Retail Banking", "retail banking","retail","खुदरा बैंकिंग");
        add(m,"Marketing", "marketing","business development","विपणन","व्यवसाय विकास");
        add(m,"Operations", "operations","banking operations","परिचालन","संचालन");
        add(m,"Security", "security","security department","सुरक्षा");
        add(m,"Pension", "pension","पेंशन");
        add(m,"Insurance", "insurance","बीमा");
        add(m,"Training", "training","learning and development","प्रशिक्षण");
        add(m,"Chairman's Secretariat", "chairman secretariat","chairman's secretariat","cmd secretariat","chairman office","अध्यक्ष सचिवालय","अध्यक्ष कार्यालय");

        SharedPreferences sp=context.getSharedPreferences("local_ai",Context.MODE_PRIVATE);
        String custom=sp.getString("custom_departments","");
        for(String row:custom.split("\\r?\\n")) {
            String r=row.trim(); if(r.isEmpty()) continue;
            String[] sides=r.split("=",2);
            String canonical=sides[0].trim(); if(canonical.isEmpty()) continue;
            List<String> aliases=new ArrayList<>(); aliases.add(canonical);
            if(sides.length>1) for(String a:sides[1].split(",")) if(!a.trim().isEmpty()) aliases.add(a.trim());
            m.put(canonical,aliases);
        }
        return m;
    }

    private void add(Map<String,List<String>> m,String canonical,String...aliases){m.put(canonical,new ArrayList<>(Arrays.asList(aliases)));}

    private List<String> lines(String text){
        List<String> l=new ArrayList<>(); if(text==null)return l;
        for(String s:text.split("\\r?\\n")){String t=s.trim().replaceAll("\\s+"," ");if(!t.isEmpty())l.add(t);}return l;
    }
    private boolean isMetadataLine(String low){return containsAny(low,"letter no","ref no","reference no","date:","dated:","phone","mobile","email","website","www.","पत्रांक","दिनांक","दूरभाष","ईमेल");}
    private boolean isSalutation(String low){return low.matches(".*\\b(dear|sir|madam|respected|महोदय|महोदया|सेवा में)\\b.*") && low.length()<70;}
    private boolean isClosing(String low){return containsAny(low,"yours faithfully","yours sincerely","regards","thank you","भवदीय","सधन्यवाद");}
    private boolean containsAny(String s,String...keys){for(String k:keys)if(s.contains(k))return true;return false;}
    private String cleanValue(String s){return s==null?"":s.replaceAll("^[\\s:;,-]+|[\\s:;,-]+$","").trim();}
    private String cleanSubject(String s){if(s==null)return"";return s.replaceAll("^[\\s:;,-]+|[\\s:;,-]+$","").replaceAll("\\s+"," ").trim();}
    private String normalize(String s){if(s==null)return"";return s.toLowerCase(Locale.ROOT).replace('।',' ').replaceAll("[^a-z0-9\\u0900-\\u097F]+"," ").replaceAll("\\s+"," ").trim();}
    private String trimTo(String s,int n){if(s==null)return"";s=s.trim();return s.length()<=n?s:s.substring(0,n).trim()+"…";}
    private double devanagariRatio(String s){int d=0,l=0;for(char c:s.toCharArray()){if(Character.isLetter(c)){l++;if(c>=0x0900&&c<=0x097F)d++;}}return l==0?0:(double)d/l;}
    private double similarity(String a,String b){int max=Math.max(a.length(),b.length());if(max==0)return 1;return 1.0-(double)lev(a,b)/max;}
    private int lev(String a,String b){int[] prev=new int[b.length()+1],cur=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;for(int i=1;i<=a.length();i++){cur[0]=i;for(int j=1;j<=b.length();j++){int c=a.charAt(i-1)==b.charAt(j-1)?0:1;cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+c);}int[]t=prev;prev=cur;cur=t;}return prev[b.length()];}

    private void success(Callback cb, JSONObject data, String provider) {
        new android.os.Handler(context.getMainLooper()).post(() -> cb.onSuccess(data, provider));
    }
    private void fail(Callback cb, String msg) {
        new android.os.Handler(context.getMainLooper()).post(() -> cb.onFailure(msg));
    }
}
