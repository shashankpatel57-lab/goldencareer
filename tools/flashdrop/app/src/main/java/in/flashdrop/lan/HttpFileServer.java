package in.flashdrop.lan;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FlashDrop Direct v1.9 HTTP server.
 * OnePlus-friendly design: browsing and Turbo bulk streaming share the same
 * proven HTTP port. Bulk requests stream many files continuously over one
 * HTTP connection, eliminating per-file reconnect gaps.
 */
public class HttpFileServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_HEADER = 128 * 1024;
    private static final int COPY_BUFFER = 2 * 1024 * 1024;

    private final Context context;
    private final File root;
    private final String pin;
    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptThread;
    private ExecutorService pool;
    private int port;

    private final AtomicLong bytesServed = new AtomicLong();
    private final AtomicInteger activeTransfers = new AtomicInteger();

    public HttpFileServer(Context context, File root, String pin) throws IOException {
        this.context = context.getApplicationContext();
        this.root = root.getCanonicalFile();
        this.pin = pin;
    }

    public void start() throws IOException {
        IOException last = null;
        for (int p = DEFAULT_PORT; p <= DEFAULT_PORT + 9; p++) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress("0.0.0.0", p), 64);
                server = ss;
                port = p;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (server == null) throw last == null ? new IOException("No free HTTP port") : last;

        pool = new ThreadPoolExecutor(
                4, 20, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(128),
                r -> {
                    Thread t = new Thread(r, "FlashDropHTTP");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());

        running = true;
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket s = server.accept();
                    configure(s);
                    pool.execute(() -> handle(s));
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        }, "FlashDropHTTPAccept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        if (pool != null) pool.shutdownNow();
    }

    public int getPort() { return port; }
    public long getBytesServed() { return bytesServed.get(); }
    public int getActiveTransfers() { return activeTransfers.get(); }

    public String getBestIpAddress() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            List<String> fallback = new ArrayList<>();
            while (en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (ip.startsWith("192.168.") || ip.startsWith("172.") || ip.startsWith("10.")) {
                        if (ni.getName().toLowerCase(Locale.US).contains("wlan")
                                || ni.getName().toLowerCase(Locale.US).contains("ap")
                                || ni.getName().toLowerCase(Locale.US).contains("swlan")) return ip;
                        fallback.add(ip);
                    }
                }
            }
            if (!fallback.isEmpty()) return fallback.get(0);
        } catch (Exception ignored) {}

        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            String s = Formatter.formatIpAddress(ip);
            if (s != null && !"0.0.0.0".equals(s)) return s;
        } catch (Exception ignored) {}

        return "192.168.43.1";
    }

    private void configure(Socket s) throws SocketException {
        s.setTcpNoDelay(true);
        s.setKeepAlive(true);
        s.setReceiveBufferSize(2 * 1024 * 1024);
        s.setSendBufferSize(4 * 1024 * 1024);
        s.setSoTimeout(30000);
    }

    private void handle(Socket socket) {
        activeTransfers.incrementAndGet();
        try (Socket s = socket) {
            BufferedInputStream in = new BufferedInputStream(s.getInputStream(), 64 * 1024);
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), 1024 * 1024);

            Request req = readRequest(in);
            if (req == null) return;

            if ("OPTIONS".equals(req.method)) {
                writeHeaders(out, 204, "No Content", "text/plain", 0L,
                        "Connection: close\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET,POST,OPTIONS\r\n");
                out.flush();
                return;
            }

            if ("/".equals(req.path)) {
                serveHome(out, req);
                return;
            }

            if ("/windows".equals(req.path) || "/FlashDropTurbo.exe".equals(req.path)) {
                serveWindows(out);
                return;
            }

            if ("/api/list".equals(req.path)) {
                if (!authorized(req)) { sendJson(out, 403, "{\"error\":\"Invalid PIN\"}"); return; }
                serveList(out, query(req, "path"));
                return;
            }

            if ("/api/file".equals(req.path)) {
                if (!authorized(req)) { sendText(out, 403, "Invalid PIN"); return; }
                serveFile(out, req, query(req, "path"));
                return;
            }

            if ("/api/bench".equals(req.path)) {
                if (!authorized(req)) { sendText(out, 403, "Invalid PIN"); return; }
                serveBench(out, req);
                return;
            }

            if ("/api/bulk".equals(req.path) && "POST".equals(req.method)) {
                if (!authorized(req)) { sendText(out, 403, "Invalid PIN"); return; }
                serveBulk(out, req, in);
                return;
            }

            sendText(out, 404, "Not found");
        } catch (Exception ignored) {
        } finally {
            activeTransfers.decrementAndGet();
        }
    }

    private boolean authorized(Request r) {
        return pin.equals(query(r, "pin"));
    }

    private void serveHome(OutputStream out, Request req) throws IOException {
        String p = query(req, "pin");
        String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>FlashDrop Direct</title><style>"
                + "body{font-family:Segoe UI,Arial;background:#f4f7fb;color:#12203a;margin:0}.hero{background:linear-gradient(120deg,#3f4df6,#0da082);color:white;padding:32px}"
                + ".wrap{max-width:900px;margin:auto}.card{background:white;margin:22px auto;padding:22px;border-radius:18px;box-shadow:0 8px 30px #20305018}"
                + "a.btn{display:inline-block;background:#3f4df6;color:#fff;padding:13px 18px;border-radius:12px;text-decoration:none;font-weight:700}.muted{color:#667085}</style></head><body>"
                + "<div class='hero'><div class='wrap'><h1>FlashDrop Direct</h1><div>Local high-speed phone → PC transfer</div><div>Designed & built by Shashank Patel</div></div></div>"
                + "<div class='wrap'><div class='card'><h2>Windows Turbo</h2><p>Download the latest Windows companion directly from this phone.</p>"
                + "<a class='btn' href='/windows'>Download FlashDrop Turbo</a></div>"
                + "<div class='card'><h2>Connection</h2><p class='muted'>Keep this page open only on your own hotspot/private Wi-Fi. Internet is not required.</p></div></div>"
                + "</body></html>";
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        writeHeaders(out, 200, "OK", "text/html; charset=utf-8", (long)b.length, "Connection: close\r\nCache-Control: no-store\r\n");
        out.write(b);
        out.flush();
    }

    private void serveWindows(OutputStream out) throws IOException {
        InputStream asset = null;
        try {
            asset = context.getAssets().open("FlashDropTurbo.exe");
            long len = asset.available();
            writeHeaders(out, 200, "OK", "application/octet-stream", len,
                    "Connection: close\r\nContent-Disposition: attachment; filename=\"FlashDropTurbo.exe\"\r\nCache-Control: no-store\r\n");
            copy(asset, out, -1);
            out.flush();
        } catch (Exception e) {
            sendText(out, 404, "Windows helper not packaged");
        } finally {
            try { if (asset != null) asset.close(); } catch (Exception ignored) {}
        }
    }

    private void serveList(OutputStream out, String rawPath) throws IOException {
        File dir = resolve(rawPath);
        if (dir == null || !dir.isDirectory()) {
            sendJson(out, 404, "{\"error\":\"Folder unavailable\"}");
            return;
        }

        File[] items;
        try { items = dir.listFiles(); } catch (Exception e) { items = null; }
        if (items == null) items = new File[0];

        Arrays.sort(items, (a,b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        StringBuilder sb = new StringBuilder(Math.max(512, items.length * 110));
        sb.append("{\"path\":\"").append(js(relative(dir))).append("\",\"items\":[");

        boolean first = true;
        for (File f : items) {
            if (!f.canRead()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":\"").append(js(f.getName())).append("\",")
                    .append("\"path\":\"").append(js(relative(f))).append("\",")
                    .append("\"dir\":").append(f.isDirectory() ? "true" : "false").append(',')
                    .append("\"size\":").append(f.isFile() ? f.length() : 0).append(',')
                    .append("\"modified\":").append(f.lastModified()).append('}');
        }
        sb.append("],\"error\":null}");
        sendJson(out, 200, sb.toString());
    }

    private void serveFile(OutputStream out, Request req, String rawPath) throws IOException {
        File f = resolve(rawPath);
        if (f == null || !f.isFile() || !f.canRead()) { sendText(out,404,"File unavailable"); return; }

        long size = f.length();
        long start = 0;
        String range = header(req, "range");
        if (range != null && range.toLowerCase(Locale.US).startsWith("bytes=")) {
            try {
                String n = range.substring(6).split("-")[0].trim();
                start = Math.max(0, Math.min(size, Long.parseLong(n)));
            } catch (Exception ignored) {}
        }

        long remain = size - start;
        int status = start > 0 ? 206 : 200;
        String extra = "Connection: close\r\nAccept-Ranges: bytes\r\n"
                + (start > 0 ? "Content-Range: bytes " + start + "-" + Math.max(start,size-1) + "/" + size + "\r\n" : "")
                + "Content-Disposition: attachment; filename*=UTF-8''" + urlEncode(f.getName()) + "\r\n";

        writeHeaders(out, status, status==206?"Partial Content":"OK", "application/octet-stream", remain, extra);

        try (RandomAccessFile raf = new RandomAccessFile(f,"r")) {
            raf.seek(start);
            copy(raf, out, remain);
        }
        out.flush();
    }

    private void serveBench(OutputStream out, Request req) throws IOException {
        long bytes = 16L * 1024L * 1024L;
        try { bytes = Long.parseLong(query(req,"bytes")); } catch (Exception ignored) {}
        bytes = Math.max(1024*1024L, Math.min(128L*1024L*1024L, bytes));

        writeHeaders(out,200,"OK","application/octet-stream",bytes,"Connection: close\r\nCache-Control: no-store\r\n");
        byte[] zero = new byte[1024*1024];
        long left=bytes;
        while(left>0) {
            int n=(int)Math.min(zero.length,left);
            out.write(zero,0,n);
            bytesServed.addAndGet(n);
            left-=n;
        }
        out.flush();
    }

    private void serveBulk(OutputStream out, Request req, InputStream in) throws IOException {
        long contentLength = req.contentLength;
        if (contentLength < 0 || contentLength > 32L * 1024L * 1024L) {
            sendText(out,400,"Invalid bulk manifest");
            return;
        }

        byte[] body = readExactly(in, (int)contentLength);
        String manifest = new String(body, StandardCharsets.UTF_8);
        String[] lines = manifest.split("\n");

        writeHeaders(out,200,"OK","application/octet-stream",null,
                "Connection: close\r\nCache-Control: no-store\r\nX-FlashDrop-Bulk: 1\r\n");
        out.flush();

        for (String line : lines) {
            if (!running) break;
            line = line.trim();
            if (line.length()==0) continue;

            String[] p = line.split("\\|",3);
            if (p.length != 3) continue;

            int id;
            long offset;
            try {
                id = Integer.parseInt(p[0]);
                offset = Long.parseLong(p[1]);
            } catch (Exception e) { continue; }

            String remote;
            try {
                remote = new String(android.util.Base64.decode(p[2], android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
            } catch (Exception e) { remote = null; }

            File f = resolve(remote);
            if (f == null || !f.isFile() || !f.canRead()) {
                writeAscii(out, "ERR " + id + " FILE_UNAVAILABLE\n");
                out.flush();
                continue;
            }

            long size = f.length();
            offset = Math.max(0, Math.min(offset, size));
            long remain = size - offset;

            writeAscii(out, "FILE " + id + " " + remain + " " + size + "\n");
            out.flush();

            if (remain > 0) {
                try (RandomAccessFile raf = new RandomAccessFile(f,"r")) {
                    raf.seek(offset);
                    copy(raf, out, remain);
                }
            }
            out.flush();
        }

        writeAscii(out,"DONE\n");
        out.flush();
    }

    private Request readRequest(InputStream in) throws IOException {
        Request r = new Request();
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.length()==0) return null;
        String[] first = requestLine.split(" ");
        if (first.length < 2) return null;

        r.method = first[0].toUpperCase(Locale.US);
        String target = first[1];

        int q = target.indexOf('?');
        r.path = q >= 0 ? target.substring(0,q) : target;
        r.query = q >= 0 ? target.substring(q+1) : "";

        int bytes = requestLine.length()+2;
        String line;
        while ((line = readLine(in)) != null) {
            bytes += line.length()+2;
            if (bytes > MAX_HEADER) throw new IOException("Header too large");
            if (line.length()==0) break;
            int c=line.indexOf(':');
            if(c>0) r.headers.put(line.substring(0,c).trim().toLowerCase(Locale.US),line.substring(c+1).trim());
        }

        try { r.contentLength = Long.parseLong(header(r,"content-length")); } catch(Exception e){ r.contentLength = 0; }
        return r;
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        int c;
        while ((c=in.read()) != -1) {
            if(c=='\n') break;
            if(c!='\r') b.write(c);
            if(b.size()>MAX_HEADER) throw new IOException("Line too long");
        }
        if(c==-1 && b.size()==0) return null;
        return new String(b.toByteArray(),StandardCharsets.UTF_8);
    }

    private byte[] readExactly(InputStream in, int len) throws IOException {
        byte[] b=new byte[len];
        int off=0;
        while(off<len) {
            int n=in.read(b,off,len-off);
            if(n<0) throw new EOFException();
            off+=n;
        }
        return b;
    }

    private void copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buf=new byte[COPY_BUFFER];
        long left=limit;
        while(limit<0 || left>0) {
            int want=limit<0?buf.length:(int)Math.min(buf.length,left);
            int n=in.read(buf,0,want);
            if(n<0) break;
            if(n==0) continue;
            out.write(buf,0,n);
            bytesServed.addAndGet(n);
            if(limit>=0) left-=n;
        }
        if(limit>=0 && left!=0) throw new EOFException("Short read");
    }

    private void copy(RandomAccessFile raf, OutputStream out, long limit) throws IOException {
        byte[] buf=new byte[COPY_BUFFER];
        long left=limit;
        while(left>0) {
            int n=raf.read(buf,0,(int)Math.min(buf.length,left));
            if(n<0) break;
            if(n==0) continue;
            out.write(buf,0,n);
            bytesServed.addAndGet(n);
            left-=n;
        }
        if(left!=0) throw new EOFException("Short read");
    }

    private File resolve(String raw) {
        try {
            String p = raw == null ? "" : raw.replace('\\','/');
            while(p.startsWith("/")) p=p.substring(1);
            File f=p.length()==0?root:new File(root,p);
            File c=f.getCanonicalFile();
            String rp=root.getCanonicalPath(), fp=c.getCanonicalPath();
            if(fp.equals(rp) || fp.startsWith(rp+File.separator)) return c;
        } catch(Exception ignored) {}
        return null;
    }

    private String relative(File f) {
        try {
            String rp=root.getCanonicalPath(), fp=f.getCanonicalPath();
            if(fp.equals(rp)) return "";
            String s=fp.substring(rp.length()).replace(File.separatorChar,'/');
            while(s.startsWith("/")) s=s.substring(1);
            return s;
        } catch(Exception e) { return ""; }
    }

    private String query(Request r,String key) {
        if(r.query==null || r.query.length()==0) return "";
        String[] parts=r.query.split("&");
        for(String p:parts) {
            int i=p.indexOf('=');
            String k=i<0?p:p.substring(0,i);
            if(key.equals(urlDecode(k))) return i<0?"":urlDecode(p.substring(i+1));
        }
        return "";
    }

    private String header(Request r,String key) {
        return r.headers.get(key.toLowerCase(Locale.US));
    }

    private String urlDecode(String s) {
        try { return URLDecoder.decode(s,"UTF-8"); } catch(Exception e) { return s; }
    }

    private String urlEncode(String s) {
        try { return URLEncoder.encode(s,"UTF-8").replace("+","%20"); } catch(Exception e) { return s; }
    }

    private String js(String s) {
        if(s==null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t");
    }

    private void sendJson(OutputStream out,int code,String body) throws IOException {
        byte[] b=body.getBytes(StandardCharsets.UTF_8);
        writeHeaders(out,code,code==200?"OK":"Error","application/json; charset=utf-8",(long)b.length,"Connection: close\r\nCache-Control: no-store\r\n");
        out.write(b); out.flush();
    }

    private void sendText(OutputStream out,int code,String body) throws IOException {
        byte[] b=body.getBytes(StandardCharsets.UTF_8);
        writeHeaders(out,code,code==200?"OK":"Error","text/plain; charset=utf-8",(long)b.length,"Connection: close\r\n");
        out.write(b); out.flush();
    }

    private void writeHeaders(OutputStream out,int code,String reason,String type,Long length,String extra) throws IOException {
        StringBuilder h=new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        h.append("Server: FlashDrop/1.9\r\n");
        h.append("Content-Type: ").append(type).append("\r\n");
        if(length!=null) h.append("Content-Length: ").append(length).append("\r\n");
        if(extra!=null) h.append(extra);
        h.append("\r\n");
        writeAscii(out,h.toString());
    }

    private void writeAscii(OutputStream out,String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static class Request {
        String method;
        String path;
        String query;
        long contentLength;
        Map<String,String> headers=new HashMap<>();
    }
}
