package in.flashdrop.lan;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Base64;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FlashDrop Unified HTTP Server v2.0
 * One proven hotspot port for UI, browsing, benchmarks and continuous bundle streaming.
 */
public class HttpFileServer {
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
        for (int p=8080;p<=8090;p++) {
            try {
                ServerSocket s = new ServerSocket();
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress("0.0.0.0",p),64);
                server=s;
                port=p;
                break;
            } catch(IOException e) { last=e; }
        }
        if(server==null) throw last==null?new IOException("No free HTTP port"):last;

        pool=new ThreadPoolExecutor(
                4,24,60L,TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(128),
                r -> {
                    Thread t=new Thread(r,"FlashDropHTTP");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY+1);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());

        running=true;
        acceptThread=new Thread(() -> {
            while(running) {
                try {
                    Socket s=server.accept();
                    configure(s);
                    pool.execute(() -> handle(s));
                } catch(IOException e) {
                    if(!running) break;
                }
            }
        },"FlashDropHTTPAccept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running=false;
        try { if(server!=null) server.close(); } catch(Exception ignored) {}
        if(pool!=null) pool.shutdownNow();
    }

    public int getPort(){ return port; }
    public long getBytesServed(){ return bytesServed.get(); }
    public int getActiveTransfers(){ return activeTransfers.get(); }

    public String getBestIpAddress() {
        String fallback="127.0.0.1";
        try {
            Enumeration<NetworkInterface> ifs=NetworkInterface.getNetworkInterfaces();
            while(ifs.hasMoreElements()) {
                NetworkInterface ni=ifs.nextElement();
                if(!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs=ni.getInetAddresses();
                while(addrs.hasMoreElements()) {
                    InetAddress a=addrs.nextElement();
                    if(a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String ip=a.getHostAddress();
                        if(ip.startsWith("192.168.") || ip.startsWith("172.") || ip.startsWith("10."))
                            return ip;
                        fallback=ip;
                    }
                }
            }
        } catch(Exception ignored) {}
        return fallback;
    }

    private void configure(Socket s) throws SocketException {
        s.setTcpNoDelay(true);
        s.setKeepAlive(true);
        try{s.setSendBufferSize(4*1024*1024);}catch(Exception ignored){}
        try{s.setReceiveBufferSize(2*1024*1024);}catch(Exception ignored){}
        s.setSoTimeout(30000);
    }

    private void handle(Socket socket) {
        try(Socket s=socket) {
            InputStream in=new BufferedInputStream(s.getInputStream(),64*1024);
            OutputStream out=new BufferedOutputStream(s.getOutputStream(),1024*1024);

            String requestLine=readLine(in);
            if(requestLine==null || requestLine.length()==0) return;

            String[] first=requestLine.split(" ");
            if(first.length<2) return;

            String method=first[0].toUpperCase(Locale.US);
            String rawTarget=first[1];

            Map<String,String> headers=new LinkedHashMap<>();
            String line;
            while((line=readLine(in))!=null && line.length()>0) {
                int c=line.indexOf(':');
                if(c>0) headers.put(line.substring(0,c).trim().toLowerCase(Locale.US),line.substring(c+1).trim());
            }

            String path=rawTarget;
            String query="";
            int q=rawTarget.indexOf('?');
            if(q>=0){ path=rawTarget.substring(0,q); query=rawTarget.substring(q+1); }
            Map<String,String> params=parseQuery(query);

            if("/".equals(path)) {
                serveHome(out);
                return;
            }

            if("/client.exe".equals(path)) {
                serveAsset(out,"FlashDropTurbo.exe","application/vnd.microsoft.portable-executable");
                return;
            }

            if("/api/list".equals(path)) {
                if(!authorized(params)) { jsonError(out,403,"Bad PIN"); return; }
                serveList(out,params.get("path"));
                return;
            }

            if("/api/file".equals(path)) {
                if(!authorized(params)) { textError(out,403,"Bad PIN"); return; }
                serveFile(out,params.get("path"),headers.get("range"));
                return;
            }

            if("/api/bench".equals(path)) {
                if(!authorized(params)) { textError(out,403,"Bad PIN"); return; }
                long n=8L*1024L*1024L;
                try{n=Long.parseLong(params.get("bytes"));}catch(Exception ignored){}
                n=Math.max(1024*1024L,Math.min(256L*1024L*1024L,n));
                serveBenchmark(out,n);
                return;
            }

            if("/api/bundle".equals(path) && "POST".equals(method)) {
                if(!authorized(params)) { textError(out,403,"Bad PIN"); return; }
                int len=0;
                try{len=Integer.parseInt(headers.get("content-length"));}catch(Exception ignored){}
                if(len<=0 || len>16*1024*1024) { textError(out,400,"Bad bundle request"); return; }
                byte[] body=readExact(in,len);
                serveBundle(out,body);
                return;
            }

            textError(out,404,"Not found");
        } catch(Exception ignored) {}
    }

    private boolean authorized(Map<String,String> p) {
        return pin.equals(p.get("pin"));
    }

    private void serveHome(OutputStream out) throws IOException {
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+
                "<title>FlashDrop Direct</title><style>"+
                "body{font-family:Segoe UI,Arial;background:#f4f7fb;color:#10213f;margin:0}"+
                ".hero{background:linear-gradient(135deg,#3157d5,#17a77b);color:#fff;padding:34px}"+
                ".wrap{max-width:900px;margin:30px auto;padding:0 18px}.card{background:#fff;border-radius:18px;padding:24px;box-shadow:0 6px 28px #0001}"+
                "a.btn{display:inline-block;background:#3157d5;color:#fff;text-decoration:none;padding:14px 20px;border-radius:12px;font-weight:700}"+
                ".muted{color:#68758b}</style></head><body>"+
                "<div class='hero'><h1>FlashDrop Direct</h1><div>High-speed local transfer • by Shashank Patel</div></div>"+
                "<div class='wrap'><div class='card'><h2>Windows Turbo Client</h2>"+
                "<p>Connect this PC to the phone hotspot, download the Windows client, enter the 6-digit PIN shown in the Android app, then choose what to copy.</p>"+
                "<p><a class='btn' href='/client.exe'>Download FlashDrop Turbo for Windows</a></p>"+
                "<p class='muted'>No internet required. Transfers remain inside your local hotspot.</p></div></div></body></html>";
        byte[] b=html.getBytes(StandardCharsets.UTF_8);
        writeHeaders(out,200,"text/html; charset=utf-8",b.length,"close",null);
        out.write(b); out.flush();
    }

    private void serveAsset(OutputStream out,String asset,String type) throws IOException {
        AssetManager am=context.getAssets();
        try(InputStream ai=am.open(asset,AssetManager.ACCESS_STREAMING)) {
            long len=am.openFd(asset).getLength();
            writeHeaders(out,200,type,len,"close","Content-Disposition: attachment; filename=\"FlashDropTurbo.exe\"\r\n");
            copy(ai,out);
        } catch(FileNotFoundException e) {
            textError(out,404,"Windows client not embedded");
        }
    }

    private void serveList(OutputStream out,String rawPath) throws IOException {
        String p=rawPath==null?"":rawPath;
        File dir=resolve(p);
        if(dir==null || !dir.isDirectory() || !dir.canRead()) { jsonError(out,404,"Folder unavailable"); return; }

        File[] arr=dir.listFiles();
        if(arr==null) arr=new File[0];

        Arrays.sort(arr,(a,b)->{
            if(a.isDirectory()!=b.isDirectory()) return a.isDirectory()?-1:1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        StringBuilder sb=new StringBuilder(Math.max(256,arr.length*120));
        sb.append("{\"path\":\"").append(json(relPath(dir))).append("\",\"items\":[");

        boolean first=true;
        for(File f:arr) {
            if(!first) sb.append(',');
            first=false;
            sb.append("{\"name\":\"").append(json(f.getName())).append("\",")
              .append("\"path\":\"").append(json(relPath(f))).append("\",")
              .append("\"dir\":").append(f.isDirectory()?"true":"false").append(',')
              .append("\"size\":").append(f.isFile()?f.length():0).append(',')
              .append("\"modified\":").append(f.lastModified()).append('}');
        }
        sb.append("]}");

        byte[] b=sb.toString().getBytes(StandardCharsets.UTF_8);
        writeHeaders(out,200,"application/json; charset=utf-8",b.length,"close","Cache-Control: no-store\r\n");
        out.write(b); out.flush();
    }

    private void serveFile(OutputStream out,String rawPath,String range) throws IOException {
        File f=resolve(rawPath);
        if(f==null || !f.isFile() || !f.canRead()) { textError(out,404,"File unavailable"); return; }

        long size=f.length(), start=0, end=size-1;
        boolean partial=false;

        if(range!=null && range.startsWith("bytes=")) {
            try {
                String r=range.substring(6).trim();
                int dash=r.indexOf('-');
                if(dash>=0) {
                    String a=r.substring(0,dash).trim();
                    String b=r.substring(dash+1).trim();
                    if(a.length()>0) start=Long.parseLong(a);
                    if(b.length()>0) end=Long.parseLong(b);
                    start=Math.max(0,Math.min(start,size));
                    end=Math.max(start,Math.min(end,size-1));
                    partial=true;
                }
            } catch(Exception ignored) {}
        }

        long len=size==0?0:(end-start+1);
        String extra="Accept-Ranges: bytes\r\n";
        if(partial) extra+="Content-Range: bytes "+start+"-"+end+"/"+size+"\r\n";

        writeHeaders(out,partial?206:200,"application/octet-stream",len,"close",extra);
        if(len<=0){out.flush();return;}

        activeTransfers.incrementAndGet();
        try(RandomAccessFile raf=new RandomAccessFile(f,"r")) {
            raf.seek(start);
            byte[] buf=new byte[2*1024*1024];
            long left=len;
            while(left>0 && running) {
                int n=raf.read(buf,0,(int)Math.min(buf.length,left));
                if(n<0) break;
                out.write(buf,0,n);
                left-=n;
                bytesServed.addAndGet(n);
            }
            out.flush();
        } finally {
            activeTransfers.decrementAndGet();
        }
    }

    private void serveBenchmark(OutputStream out,long length) throws IOException {
        writeHeaders(out,200,"application/octet-stream",length,"close","Cache-Control: no-store\r\n");
        activeTransfers.incrementAndGet();
        try {
            byte[] zero=new byte[1024*1024];
            long left=length;
            while(left>0 && running) {
                int n=(int)Math.min(zero.length,left);
                out.write(zero,0,n);
                left-=n;
                bytesServed.addAndGet(n);
            }
            out.flush();
        } finally { activeTransfers.decrementAndGet(); }
    }

    private void serveBundle(OutputStream out,byte[] body) throws IOException {
        String text=new String(body,StandardCharsets.UTF_8);
        String[] lines=text.split("\\r?\\n");
        List<BundleEntry> entries=new ArrayList<>();

        for(String line:lines) {
            if(line.trim().length()==0) continue;
            String[] p=line.split("\\t",3);
            if(p.length!=3) continue;
            try {
                BundleEntry e=new BundleEntry();
                e.index=Integer.parseInt(p[0]);
                e.offset=Math.max(0,Long.parseLong(p[1]));
                e.path=new String(Base64.decode(p[2],Base64.DEFAULT),StandardCharsets.UTF_8);
                entries.add(e);
            } catch(Exception ignored) {}
        }

        String h="HTTP/1.1 200 OK\r\n"+
                "Content-Type: application/octet-stream\r\n"+
                "Connection: close\r\n"+
                "Cache-Control: no-store\r\n"+
                "X-FlashDrop-Protocol: bundle-v2\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        activeTransfers.incrementAndGet();
        byte[] buf=new byte[2*1024*1024];

        try {
            for(BundleEntry e:entries) {
                if(!running) break;
                File f=resolve(e.path);

                if(f==null || !f.isFile() || !f.canRead()) {
                    writeAscii(out,"ERR\t"+e.index+"\tFILE_UNAVAILABLE\n");
                    continue;
                }

                long size=f.length();
                long offset=Math.max(0,Math.min(e.offset,size));
                long remaining=size-offset;

                writeAscii(out,"FILE\t"+e.index+"\t"+remaining+"\t"+size+"\n");
                out.flush();

                if(remaining==0) continue;

                try(RandomAccessFile raf=new RandomAccessFile(f,"r")) {
                    raf.seek(offset);
                    long left=remaining;

                    while(left>0 && running) {
                        int n=raf.read(buf,0,(int)Math.min(buf.length,left));
                        if(n<0) throw new EOFException("Unexpected EOF");
                        out.write(buf,0,n);
                        left-=n;
                        bytesServed.addAndGet(n);
                    }
                }
            }

            writeAscii(out,"DONE\n");
            out.flush();
        } finally {
            activeTransfers.decrementAndGet();
        }
    }

    private void writeHeaders(OutputStream out,int code,String type,long len,String connection,String extra) throws IOException {
        String status=code==200?"OK":code==206?"Partial Content":code==403?"Forbidden":code==404?"Not Found":"Error";
        StringBuilder h=new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n")
         .append("Content-Type: ").append(type).append("\r\n")
         .append("Content-Length: ").append(len).append("\r\n")
         .append("Connection: ").append(connection).append("\r\n");
        if(extra!=null) h.append(extra);
        h.append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private void jsonError(OutputStream out,int code,String msg) throws IOException {
        byte[] b=("{\"error\":\""+json(msg)+"\"}").getBytes(StandardCharsets.UTF_8);
        writeHeaders(out,code,"application/json; charset=utf-8",b.length,"close",null);
        out.write(b);out.flush();
    }

    private void textError(OutputStream out,int code,String msg) throws IOException {
        byte[] b=msg.getBytes(StandardCharsets.UTF_8);
        writeHeaders(out,code,"text/plain; charset=utf-8",b.length,"close",null);
        out.write(b);out.flush();
    }

    private Map<String,String> parseQuery(String q) {
        Map<String,String> m=new LinkedHashMap<>();
        if(q==null || q.length()==0) return m;
        for(String part:q.split("&")) {
            int e=part.indexOf('=');
            String k=e<0?part:part.substring(0,e);
            String v=e<0?"":part.substring(e+1);
            try{k=URLDecoder.decode(k,"UTF-8");v=URLDecoder.decode(v,"UTF-8");}catch(Exception ignored){}
            m.put(k,v);
        }
        return m;
    }

    private File resolve(String raw) {
        try {
            String p=raw==null?"":raw.replace('\\','/');
            while(p.startsWith("/"))p=p.substring(1);
            File f=p.length()==0?root:new File(root,p);
            File c=f.getCanonicalFile();
            String rp=root.getCanonicalPath(), fp=c.getCanonicalPath();
            if(fp.equals(rp) || fp.startsWith(rp+File.separator)) return c;
        } catch(Exception ignored) {}
        return null;
    }

    private String relPath(File f) {
        try {
            String rp=root.getCanonicalPath(), fp=f.getCanonicalPath();
            if(fp.equals(rp)) return "";
            String r=fp.substring(rp.length()).replace(File.separatorChar,'/');
            while(r.startsWith("/"))r=r.substring(1);
            return r;
        } catch(Exception e) { return ""; }
    }

    private String json(String s) {
        if(s==null)return "";
        StringBuilder b=new StringBuilder(s.length()+16);
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch(c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if(c<32) b.append(String.format(Locale.US,"\\u%04x",(int)c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(128);
        int c;
        while((c=in.read())!=-1) {
            if(c=='\n') break;
            if(c!='\r') b.write(c);
            if(b.size()>64*1024) throw new IOException("Line too long");
        }
        if(c==-1 && b.size()==0) return null;
        return new String(b.toByteArray(),StandardCharsets.UTF_8);
    }

    private byte[] readExact(InputStream in,int n) throws IOException {
        byte[] b=new byte[n];
        int o=0;
        while(o<n) {
            int r=in.read(b,o,n-o);
            if(r<0) throw new EOFException("Short request");
            o+=r;
        }
        return b;
    }

    private void writeAscii(OutputStream out,String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.US_ASCII));
    }

    private void copy(InputStream in,OutputStream out) throws IOException {
        byte[] b=new byte[1024*1024];
        int n;
        while((n=in.read(b))!=-1) out.write(b,0,n);
        out.flush();
    }

    private static class BundleEntry {
        int index;
        long offset;
        String path;
    }
}
