package com.openai.inwardregister;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxExporter {
    private XlsxExporter() {}

    private static final String[] HEADERS = new String[]{
            "Receiving Date", "Inward No.", "Letter No.", "Letter Date", "Sender", "Subject", "Department", "Tracking ID", "Signature"
    };

    private static final String[] KEYS = new String[]{
            "Receiving_Date", "Inward_No", "Letter_No", "Letter_Date", "Sender", "Subject", "Department", "Tracking_ID", "Signature"
    };

    public static File export(File out, JSONArray entries) throws Exception {
        if (out.exists()) out.delete();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", sheet(entries));
        }
        return out;
    }

    private static void put(ZipOutputStream z, String name, String content) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>";
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>";
    }

    private static String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"Inward Register\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"2\">" +
                "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "</fonts>" +
                "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0B57D0\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
                "<borders count=\"2\"><border/><border><left style=\"thin\"><color rgb=\"FFD9DEE8\"/></left><right style=\"thin\"><color rgb=\"FFD9DEE8\"/></right><top style=\"thin\"><color rgb=\"FFD9DEE8\"/></top><bottom style=\"thin\"><color rgb=\"FFD9DEE8\"/></bottom></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"3\">" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>" +
                "</cellXfs></styleSheet>";
    }

    private static String sheet(JSONArray entries) {
        StringBuilder s = new StringBuilder();
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        s.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        s.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        s.append("<cols>");
        double[] widths = {15, 12, 20, 15, 34, 50, 24, 24, 18};
        for (int i=0; i<widths.length; i++) s.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        s.append("</cols><sheetData>");

        s.append("<row r=\"1\" ht=\"26\" customHeight=\"1\">");
        for (int c=0; c<HEADERS.length; c++) s.append(inlineCell(ref(c,1), HEADERS[c], 1));
        s.append("</row>");

        for (int i=0; i<entries.length(); i++) {
            int row = i + 2;
            JSONObject o = entries.optJSONObject(i);
            s.append("<row r=\"").append(row).append("\" ht=\"34\" customHeight=\"1\">");
            for (int c=0; c<KEYS.length; c++) {
                if (c == 1) {
                    int n = o == null ? 0 : o.optInt(KEYS[c], 0);
                    s.append(numberCell(ref(c,row), n, 2));
                } else {
                    String v = o == null ? "" : o.optString(KEYS[c], "");
                    s.append(inlineCell(ref(c,row), v, 2));
                }
            }
            s.append("</row>");
        }
        int last = Math.max(1, entries.length()+1);
        s.append("</sheetData>");
        s.append("<autoFilter ref=\"A1:I").append(last).append("\"/>");
        s.append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/>");
        s.append("<pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>");
        s.append("</worksheet>");
        return s.toString();
    }

    private static String inlineCell(String ref, String val, int style) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\" s=\"" + style + "\"><is><t xml:space=\"preserve\">" + esc(val) + "</t></is></c>";
    }

    private static String numberCell(String ref, int val, int style) {
        return "<c r=\"" + ref + "\" t=\"n\" s=\"" + style + "\"><v>" + val + "</v></c>";
    }

    private static String ref(int col, int row) {
        int n = col + 1;
        StringBuilder b = new StringBuilder();
        while (n > 0) { int r = (n-1)%26; b.insert(0, (char)('A'+r)); n = (n-1)/26; }
        return b.toString() + row;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i=0; i<s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default: if (ch >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') b.append(ch);
            }
        }
        return b.toString();
    }
}
