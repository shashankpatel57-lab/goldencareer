package in.flashdrop.lan;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small read-only passive-mode FTP server for Windows File Explorer.
 * It exposes Android shared storage directly so folders can be copied without ZIP/scripts.
 */
public class FtpFileServer {
    private final File root;
    private volatile boolean running;
    private ServerSocket controlServer;
    private Thread acceptThread;
    private ExecutorService pool;
    private int port;
    private final AtomicLong bytesServed = new AtomicLong();
    private final AtomicInteger activeTransfers = new AtomicInteger();

    public FtpFileServer(File root) throws IOException {
        this.root = root.getCanonicalFile();
    }

    public void start() throws IOException {
        IOException last = null;
        for (int p = 2121; p <= 2130; p++) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress("0.0.0.0", p), 50);
                controlServer = ss;
                port = p;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (controlServer == null) throw last == null ? new IOException("No free FTP port") : last;

        pool = new ThreadPoolExecutor(2, 12, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(64), r -> {
            Thread t = new Thread(r, "FlashDropFTP");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());

        running = true;
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket s = controlServer.accept();
                    s.setTcpNoDelay(true);
                    s.setKeepAlive(true);
                    pool.execute(() -> handleSession(s));
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        }, "FlashDropFTPAccept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        try { if (controlServer != null) controlServer.close(); } catch (Exception ignored) {}
        if (pool != null) pool.shutdownNow();
    }

    public int getPort() { return port; }
    public long getBytesServed() { return bytesServed.get(); }
    public int getActiveTransfers() { return activeTransfers.get(); }

    private void handleSession(Socket socket) {
        ServerSocket passive = null;
        File cwd = root;
        long restOffset = 0L;
        boolean loggedIn = false;

        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {

            reply(out, 220, "FlashDrop Direct FTP ready");
            String line;
            while (running && (line = in.readLine()) != null) {
                String cmd;
                String arg;
                int sp = line.indexOf(' ');
                if (sp < 0) { cmd = line.trim().toUpperCase(Locale.US); arg = ""; }
                else { cmd = line.substring(0, sp).trim().toUpperCase(Locale.US); arg = line.substring(sp + 1).trim(); }

                if ("USER".equals(cmd)) {
                    reply(out, 331, "Guest login okay, send password");
                    continue;
                }
                if ("PASS".equals(cmd)) {
                    loggedIn = true;
                    reply(out, 230, "Logged in - read only");
                    continue;
                }
                if ("QUIT".equals(cmd)) {
                    reply(out, 221, "Goodbye");
                    break;
                }
                if (!loggedIn) {
                    reply(out, 530, "Please login with any username/password");
                    continue;
                }

                switch (cmd) {
                    case "SYST":
                        reply(out, 215, "UNIX Type: L8");
                        break;
                    case "FEAT":
                        writeRaw(out, "211-Features\r\n");
                        writeRaw(out, " UTF8\r\n");
                        writeRaw(out, " SIZE\r\n");
                        writeRaw(out, " MDTM\r\n");
                        writeRaw(out, " REST STREAM\r\n");
                        writeRaw(out, " MLST type*;size*;modify*;\r\n");
                        writeRaw(out, "211 End\r\n");
                        break;
                    case "OPTS":
                        reply(out, 200, "UTF8 enabled");
                        break;
                    case "TYPE":
                    case "MODE":
                    case "STRU":
                    case "ALLO":
                        reply(out, 200, "OK");
                        break;
                    case "NOOP":
                        reply(out, 200, "OK");
                        break;
                    case "PWD":
                    case "XPWD":
                        reply(out, 257, "\"" + ftpPath(cwd).replace("\"", "") + "\" is current directory");
                        break;
                    case "CWD":
                    case "XCWD": {
                        File f = resolve(cwd, arg);
                        if (f != null && f.isDirectory() && f.canRead()) {
                            cwd = f;
                            reply(out, 250, "Directory changed to " + ftpPath(cwd));
                        } else reply(out, 550, "Directory unavailable");
                        break;
                    }
                    case "CDUP":
                    case "XCUP": {
                        File parent = cwd.getParentFile();
                        if (parent != null && insideRoot(parent)) cwd = parent;
                        else cwd = root;
                        reply(out, 250, "Directory changed to " + ftpPath(cwd));
                        break;
                    }
                    case "PASV": {
                        closeQuietly(passive);
                        passive = openPassive();
                        InetAddress a = s.getLocalAddress();
                        String ip = a.getHostAddress();
                        if (!(a instanceof Inet4Address) || ip == null || ip.contains(":")) ip = "127.0.0.1";
                        String[] oct = ip.split("\\.");
                        int p = passive.getLocalPort();
                        reply(out, 227, "Entering Passive Mode (" + oct[0] + "," + oct[1] + "," + oct[2] + "," + oct[3] + "," + (p / 256) + "," + (p % 256) + ")");
                        break;
                    }
                    case "EPSV": {
                        closeQuietly(passive);
                        passive = openPassive();
                        reply(out, 229, "Entering Extended Passive Mode (|||" + passive.getLocalPort() + "|)");
                        break;
                    }
                    case "PORT":
                    case "EPRT":
                        reply(out, 502, "Active mode disabled; use passive mode");
                        break;
                    case "LIST": {
                        if (passive == null) { reply(out, 425, "Use PASV first"); break; }
                        File f = arg.isEmpty() ? cwd : resolve(cwd, stripListOptions(arg));
                        if (f == null || !f.exists()) { reply(out, 550, "Path unavailable"); closeQuietly(passive); passive = null; break; }
                        reply(out, 150, "Opening data connection for directory list");
                        ServerSocket ps = passive; passive = null;
                        sendList(ps, f, false);
                        reply(out, 226, "Directory send OK");
                        break;
                    }
                    case "MLSD": {
                        if (passive == null) { reply(out, 425, "Use PASV first"); break; }
                        File f = arg.isEmpty() ? cwd : resolve(cwd, arg);
                        if (f == null || !f.exists()) { reply(out, 550, "Path unavailable"); closeQuietly(passive); passive = null; break; }
                        reply(out, 150, "Opening data connection for MLSD");
                        ServerSocket ps = passive; passive = null;
                        sendList(ps, f, true);
                        reply(out, 226, "Directory send OK");
                        break;
                    }
                    case "NLST": {
                        if (passive == null) { reply(out, 425, "Use PASV first"); break; }
                        File f = arg.isEmpty() ? cwd : resolve(cwd, arg);
                        if (f == null || !f.exists()) { reply(out, 550, "Path unavailable"); closeQuietly(passive); passive = null; break; }
                        reply(out, 150, "Opening data connection for names");
                        ServerSocket ps = passive; passive = null;
                        sendNameList(ps, f);
                        reply(out, 226, "Directory send OK");
                        break;
                    }
                    case "SIZE": {
                        File f = resolve(cwd, arg);
                        if (f != null && f.isFile() && f.canRead()) reply(out, 213, Long.toString(f.length()));
                        else reply(out, 550, "File unavailable");
                        break;
                    }
                    case "MDTM": {
                        File f = resolve(cwd, arg);
                        if (f != null && f.exists()) reply(out, 213, ftpTimestamp(f.lastModified()));
                        else reply(out, 550, "File unavailable");
                        break;
                    }
                    case "REST":
                        try {
                            restOffset = Math.max(0L, Long.parseLong(arg));
                            reply(out, 350, "Restarting at " + restOffset);
                        } catch (Exception e) {
                            restOffset = 0L;
                            reply(out, 501, "Bad offset");
                        }
                        break;
                    case "RETR": {
                        if (passive == null) { reply(out, 425, "Use PASV first"); break; }
                        File f = resolve(cwd, arg);
                        if (f == null || !f.isFile() || !f.canRead()) { reply(out, 550, "File unavailable"); closeQuietly(passive); passive = null; break; }
                        long offset = Math.min(restOffset, f.length());
                        restOffset = 0L;
                        reply(out, 150, "Opening binary data connection for " + safeName(f.getName()) + " (" + f.length() + " bytes)");
                        ServerSocket ps = passive; passive = null;
                        boolean ok = sendFile(ps, f, offset);
                        if (ok) reply(out, 226, "Transfer complete");
                        else reply(out, 426, "Connection closed; transfer aborted");
                        break;
                    }
                    case "STAT":
                        reply(out, 211, "FlashDrop Direct ready; read-only shared storage");
                        break;
                    case "STOR": case "APPE": case "DELE": case "RMD": case "XRMD":
                    case "MKD": case "XMKD": case "RNFR": case "RNTO": case "SITE":
                        reply(out, 550, "Read-only server");
                        break;
                    default:
                        reply(out, 502, "Command not implemented");
                        break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(passive);
        }
    }

    private ServerSocket openPassive() throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress("0.0.0.0", 0), 1);
        ss.setSoTimeout(30000);
        return ss;
    }

    private boolean sendFile(ServerSocket passive, File file, long offset) {
        activeTransfers.incrementAndGet();
        try (ServerSocket ps = passive;
             Socket data = ps.accept();
             RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            data.setTcpNoDelay(true);
            data.setSendBufferSize(2 * 1024 * 1024);
            raf.seek(offset);
            OutputStream out = new BufferedOutputStream(data.getOutputStream(), 1024 * 1024);
            byte[] buf = new byte[512 * 1024];
            int n;
            while ((n = raf.read(buf)) >= 0) {
                if (n == 0) continue;
                out.write(buf, 0, n);
                bytesServed.addAndGet(n);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            activeTransfers.decrementAndGet();
        }
    }

    private void sendList(ServerSocket passive, File path, boolean machine) throws IOException {
        try (ServerSocket ps = passive; Socket data = ps.accept()) {
            data.setTcpNoDelay(true);
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(data.getOutputStream(), StandardCharsets.UTF_8), 64 * 1024);
            File[] items = path.isDirectory() ? safeList(path) : new File[]{path};
            Arrays.sort(items, (a,b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : items) {
                if (machine) {
                    String type = f.isDirectory() ? "dir" : "file";
                    w.write("type=" + type + ";size=" + (f.isFile() ? f.length() : 0) + ";modify=" + ftpTimestamp(f.lastModified()) + "; " + safeName(f.getName()) + "\r\n");
                } else {
                    String perms = f.isDirectory() ? "dr-xr-xr-x" : "-r--r--r--";
                    String date = listTimestamp(f.lastModified());
                    w.write(String.format(Locale.US, "%s 1 owner group %12d %s %s\r\n", perms, f.isFile() ? f.length() : 0, date, safeName(f.getName())));
                }
            }
            w.flush();
        }
    }

    private void sendNameList(ServerSocket passive, File path) throws IOException {
        try (ServerSocket ps = passive; Socket data = ps.accept()) {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(data.getOutputStream(), StandardCharsets.UTF_8), 64 * 1024);
            File[] items = path.isDirectory() ? safeList(path) : new File[]{path};
            for (File f : items) w.write(safeName(f.getName()) + "\r\n");
            w.flush();
        }
    }

    private File[] safeList(File dir) {
        try {
            File[] f = dir.listFiles();
            return f == null ? new File[0] : f;
        } catch (SecurityException e) {
            return new File[0];
        }
    }

    private File resolve(File cwd, String raw) {
        try {
            if (raw == null || raw.isEmpty() || ".".equals(raw)) return cwd;
            String p = raw.replace('\\', '/');
            File f;
            if (p.startsWith("/")) {
                while (p.startsWith("/")) p = p.substring(1);
                f = p.isEmpty() ? root : new File(root, p);
            } else {
                f = new File(cwd, p);
            }
            File c = f.getCanonicalFile();
            return insideRoot(c) ? c : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean insideRoot(File f) {
        try {
            String rp = root.getCanonicalPath();
            String fp = f.getCanonicalPath();
            return fp.equals(rp) || fp.startsWith(rp + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    private String ftpPath(File f) {
        try {
            String rp = root.getCanonicalPath();
            String fp = f.getCanonicalPath();
            if (fp.equals(rp)) return "/";
            String rel = fp.substring(rp.length()).replace(File.separatorChar, '/');
            if (!rel.startsWith("/")) rel = "/" + rel;
            return rel;
        } catch (Exception e) {
            return "/";
        }
    }

    private String stripListOptions(String arg) {
        String a = arg.trim();
        if (!a.startsWith("-")) return a;
        int sp = a.indexOf(' ');
        return sp < 0 ? "" : a.substring(sp + 1).trim();
    }

    private String safeName(String s) {
        return s == null ? "" : s.replace('\r', '_').replace('\n', '_');
    }

    private String ftpTimestamp(long when) {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date(Math.max(0L, when)));
    }

    private String listTimestamp(long when) {
        SimpleDateFormat df = new SimpleDateFormat("MMM dd HH:mm", Locale.US);
        return df.format(new Date(Math.max(0L, when)));
    }

    private void reply(BufferedWriter out, int code, String msg) throws IOException {
        writeRaw(out, code + " " + msg + "\r\n");
    }

    private void writeRaw(BufferedWriter out, String s) throws IOException {
        out.write(s);
        out.flush();
    }

    private void closeQuietly(ServerSocket s) {
        try { if (s != null) s.close(); } catch (Exception ignored) {}
    }
}
