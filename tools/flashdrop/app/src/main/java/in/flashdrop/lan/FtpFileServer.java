package in.flashdrop.lan;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final String pin;
    private volatile boolean running;
    private ServerSocket controlServer;
    private Thread acceptThread;
    private ExecutorService pool;
    private int port;
    private final AtomicLong bytesServed = new AtomicLong();
    private final AtomicInteger activeTransfers = new AtomicInteger();

    public FtpFileServer(File root, String pin) throws IOException {
        this.root = root.getCanonicalFile();
        this.pin = pin == null ? "" : pin.trim();
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

        pool = new ThreadPoolExecutor(4, 32, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(128), r -> {
            Thread t = new Thread(r, "FileSetuFTP");
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
        }, "FileSetuFTPAccept");
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

            reply(out, 220, "FileSetu Secure FTP ready");
            String line;
            while (running && (line = in.readLine()) != null) {
                String cmd;
                String arg;
                int sp = line.indexOf(' ');
                if (sp < 0) { cmd = line.trim().toUpperCase(Locale.US); arg = ""; }
                else { cmd = line.substring(0, sp).trim().toUpperCase(Locale.US); arg = line.substring(sp + 1).trim(); }

                if ("USER".equals(cmd)) {
                    reply(out, 331, "FileSetu PIN required");
                    continue;
                }
                if ("PASS".equals(cmd)) {
                    if (pin.equals(arg)) {
                        loggedIn = true;
                        reply(out, 230, "Logged in - secure read only");
                    } else {
                        loggedIn = false;
                        reply(out, 530, "Incorrect FileSetu PIN");
                    }
                    continue;
                }
                if ("QUIT".equals(cmd)) {
                    reply(out, 221, "Goodbye");
                    break;
                }
                if (!loggedIn) {
                    reply(out, 530, "Please login using the FileSetu PIN");
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
                        writeRaw(out, " XFD1\r\n");
                        writeRaw(out, " XFD2\r\n");
                        writeRaw(out, " XHASHB SHA-256\r\n");
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
                    case "XFD2":
                        reply(out, 200, "XFD2 READY");
                        handlePackedStream(s, in, out);
                        return;
                    case "XFD1":
                        reply(out, 200, "XFD1 READY");
                        handleTurboStream(s, in, out);
                        return;
                    case "XHASHB":
                        handleHashBatch(in, out, arg);
                        break;
                    case "STAT":
                        reply(out, 211, "FileSetu ready; secure read-only shared storage");
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

    private static final class PackEntry {
        int index;
        boolean directory;
        File file;
        String relative;
        long size;
        long modified;
    }

    private static final class PackWant {
        int index;
        long offset;
    }

    private void handleLegacyPackedFolder(
            Socket socket,
            BufferedReader in,
            BufferedWriter controlOut,
            String encodedRoot) throws IOException {

        String remote;
        try {
            remote = new String(
                    android.util.Base64.decode(encodedRoot, android.util.Base64.DEFAULT),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            reply(controlOut, 501, "Bad packed-folder path");
            return;
        }

        File selected = resolve(root, remote);
        if (selected == null || !selected.exists() || !selected.canRead()) {
            reply(controlOut, 550, "Packed-folder path unavailable");
            return;
        }

        List<PackEntry> manifest = new ArrayList<>();
        long[] total = new long[]{0L};
        buildPackManifest(selected, selected, manifest, total);

        reply(controlOut, 200, "XFD2 READY");
        writeRaw(controlOut,
                "MANIFEST " + manifest.size() + " " + total[0] + "\r\n");

        for (PackEntry e : manifest) {
            String rel64 = android.util.Base64.encodeToString(
                    e.relative.getBytes(StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP);

            if (e.directory) {
                writeRaw(controlOut,
                        "D " + e.index + " " + e.modified + " " + rel64 + "\r\n");
            } else {
                writeRaw(controlOut,
                        "F " + e.index + " " + e.size + " " + e.modified + " " + rel64 + "\r\n");
            }
        }

        writeRaw(controlOut, "ENDMANIFEST\r\n");

        String wantHeader = in.readLine();
        if (wantHeader == null || !wantHeader.startsWith("WANT ")) {
            throw new EOFException("XFD2 WANT missing");
        }

        int wantCount;
        try {
            wantCount = Integer.parseInt(wantHeader.substring(5).trim());
        } catch (Exception e) {
            reply(controlOut, 501, "Bad WANT count");
            return;
        }

        List<PackWant> wants = new ArrayList<>(Math.max(0, wantCount));

        for (int i = 0; i < wantCount; i++) {
            String line = in.readLine();
            if (line == null) throw new EOFException("XFD2 WANT truncated");

            String[] p = line.trim().split(" ");
            if (p.length != 2) throw new IOException("Bad WANT item");

            PackWant w = new PackWant();
            try {
                w.index = Integer.parseInt(p[0]);
                w.offset = Math.max(0L, Long.parseLong(p[1]));
            } catch (Exception e) {
                throw new IOException("Bad WANT item");
            }
            wants.add(w);
        }

        String endWant = in.readLine();
        if (!"ENDWANT".equals(endWant))
            throw new IOException("XFD2 ENDWANT missing");

        OutputStream raw = socket.getOutputStream();
        byte[] buf = new byte[4 * 1024 * 1024];

        for (PackWant w : wants) {
            if (!running) break;

            if (w.index < 0 || w.index >= manifest.size()) {
                writeRaw(controlOut, "ERR " + w.index + " BAD_INDEX\r\n");
                continue;
            }

            PackEntry e = manifest.get(w.index);
            if (e.directory || e.file == null || !e.file.isFile() || !e.file.canRead()) {
                writeRaw(controlOut, "ERR " + w.index + " FILE_UNAVAILABLE\r\n");
                continue;
            }

            long currentSize = e.file.length();
            long offset = Math.min(Math.max(0L, w.offset), currentSize);
            long remaining = currentSize - offset;

            writeRaw(controlOut,
                    "DATA " + e.index + " " + remaining + " " + currentSize + "\r\n");

            activeTransfers.incrementAndGet();
            long sent = 0L;

            try (RandomAccessFile raf = new RandomAccessFile(e.file, "r")) {
                raf.seek(offset);

                while (sent < remaining && running) {
                    int n = raf.read(
                            buf,
                            0,
                            (int)Math.min((long)buf.length, remaining - sent));

                    if (n < 0) break;
                    if (n == 0) continue;

                    raw.write(buf, 0, n);
                    sent += n;
                    bytesServed.addAndGet(n);
                }

                raw.flush();
            } finally {
                activeTransfers.decrementAndGet();
            }

            if (sent != remaining)
                throw new EOFException("XFD2 short send");
        }

        writeRaw(controlOut, "DONE\r\n");
    }

    private void buildPackManifest(
            File base,
            File current,
            List<PackEntry> out,
            long[] total) {

        if (!running || current == null || !current.exists() || !current.canRead())
            return;

        if (!current.equals(base)) {
            PackEntry e = new PackEntry();
            e.index = out.size();
            e.directory = current.isDirectory();
            e.file = current;
            e.relative = packRelative(base, current);
            e.size = current.isFile() ? current.length() : 0L;
            e.modified = current.lastModified();
            out.add(e);

            if (!e.directory)
                total[0] += e.size;
        }

        if (!current.isDirectory())
            return;

        File[] children = safeList(current);
        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory())
                return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File child : children)
            buildPackManifest(base, child, out, total);
    }

    private String packRelative(File base, File file) {
        try {
            String bp = base.getCanonicalPath();
            String fp = file.getCanonicalPath();

            if (fp.equals(bp)) return "";

            String rel = fp.substring(bp.length());
            while (rel.startsWith(File.separator))
                rel = rel.substring(1);

            return rel.replace(File.separatorChar, '/');
        } catch (Exception e) {
            return file.getName();
        }
    }

    private void handleHashBatch(
            BufferedReader in,
            BufferedWriter out,
            String countArg) throws IOException {

        int count;
        try {
            count = Integer.parseInt(countArg.trim());
        } catch (Exception e) {
            reply(out, 501, "Bad hash count");
            return;
        }

        if (count < 1 || count > 2000) {
            reply(out, 501, "Hash count out of range");
            return;
        }

        reply(out, 150, "HASH " + count);

        for (int i = 0; i < count; i++) {
            String encoded = in.readLine();
            if (encoded == null) throw new EOFException("Hash request ended early");

            String remote;
            try {
                remote = new String(
                        android.util.Base64.decode(encoded.trim(), android.util.Base64.DEFAULT),
                        StandardCharsets.UTF_8);
            } catch (Exception e) {
                writeRaw(out, "550 " + i + " 0 BAD_PATH\r\n");
                continue;
            }

            File f = resolve(root, remote);
            if (f == null || !f.isFile() || !f.canRead()) {
                writeRaw(out, "550 " + i + " 0 FILE_UNAVAILABLE\r\n");
                continue;
            }

            try {
                String sha = sha256(f);
                writeRaw(out,
                        "213 " + i + " " + f.length() + " " + sha + "\r\n");
            } catch (Exception e) {
                writeRaw(out, "550 " + i + " " + f.length() + " HASH_FAILED\r\n");
            }
        }

        writeRaw(out, "226 HASH DONE\r\n");
    }

    private String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[1024 * 1024];

        try (InputStream in = new BufferedInputStream(new FileInputStream(file), buf.length)) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) md.update(buf, 0, n);
            }
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest)
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private void handlePackedStream(Socket socket, BufferedReader in, BufferedWriter controlOut) throws IOException {
        OutputStream raw = socket.getOutputStream();
        byte[] buf = new byte[4 * 1024 * 1024];

        while (running) {
            String line = in.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            if ("QUIT".equalsIgnoreCase(line)) {
                reply(controlOut, 221, "Goodbye");
                break;
            }

            if ("PING".equalsIgnoreCase(line)) {
                reply(controlOut, 200, "PONG");
                continue;
            }

            if (!line.startsWith("BATCH ")) {
                reply(controlOut, 500, "XFD2 expected BATCH");
                continue;
            }

            int count;
            try {
                count = Integer.parseInt(line.substring(6).trim());
            } catch (Exception e) {
                reply(controlOut, 501, "Bad batch count");
                continue;
            }

            if (count < 1 || count > 2000) {
                reply(controlOut, 501, "Batch count out of range");
                continue;
            }

            String[] paths = new String[count];
            long[] offsets = new long[count];

            boolean bad = false;

            for (int i = 0; i < count; i++) {
                String item = in.readLine();
                if (item == null) throw new EOFException("Batch request ended early");

                String[] p = item.split(" ", 2);
                if (p.length != 2) {
                    bad = true;
                    break;
                }

                try {
                    offsets[i] = Math.max(0L, Long.parseLong(p[0]));
                    paths[i] = new String(
                            android.util.Base64.decode(p[1], android.util.Base64.DEFAULT),
                            StandardCharsets.UTF_8);
                } catch (Exception e) {
                    bad = true;
                    break;
                }
            }

            if (bad) {
                reply(controlOut, 501, "Bad batch item");
                continue;
            }

            reply(controlOut, 150, "BATCH " + count);

            for (int i = 0; i < count && running; i++) {
                File f = resolve(root, paths[i]);

                if (f == null || !f.isFile() || !f.canRead()) {
                    writeRaw(controlOut, "550 " + i + " 0 0\r\n");
                    continue;
                }

                long total = f.length();
                long offset = Math.min(offsets[i], total);
                long length = total - offset;

                writeRaw(controlOut, "151 " + i + " " + length + " " + total + "\r\n");

                activeTransfers.incrementAndGet();
                long sent = 0L;

                try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                    raf.seek(offset);

                    while (sent < length && running) {
                        int n = raf.read(buf, 0, (int)Math.min((long)buf.length, length - sent));
                        if (n < 0) break;
                        if (n == 0) continue;

                        raw.write(buf, 0, n);
                        sent += n;
                        bytesServed.addAndGet(n);
                    }

                    raw.flush();
                } finally {
                    activeTransfers.decrementAndGet();
                }

                if (sent != length) {
                    try { writeRaw(controlOut, "426 " + i + " " + sent + "\r\n"); } catch (Exception ignored) {}
                    throw new EOFException("Packed stream short send");
                }

                writeRaw(controlOut, "152 " + i + " " + sent + "\r\n");
            }

            writeRaw(controlOut, "226 BATCH DONE\r\n");
        }
    }

    private void handleTurboStream(Socket socket, BufferedReader in, BufferedWriter controlOut) throws IOException {
        OutputStream raw = socket.getOutputStream();
        byte[] buf = new byte[4 * 1024 * 1024];

        while (running) {
            String line = in.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            if ("QUIT".equalsIgnoreCase(line)) {
                reply(controlOut, 221, "Goodbye");
                break;
            }

            if ("PING".equalsIgnoreCase(line)) {
                reply(controlOut, 200, "PONG");
                continue;
            }

            if (!line.startsWith("GET ")) {
                reply(controlOut, 500, "XFD1 expected GET");
                continue;
            }

            String[] p = line.split(" ", 4);
            if (p.length != 4) {
                reply(controlOut, 501, "Bad GET request");
                continue;
            }

            long offset;
            long requested;
            try {
                offset = Math.max(0L, Long.parseLong(p[1]));
                requested = Math.max(0L, Long.parseLong(p[2]));
            } catch (Exception e) {
                reply(controlOut, 501, "Bad range");
                continue;
            }

            String remote;
            try {
                remote = new String(android.util.Base64.decode(p[3], android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
            } catch (Exception e) {
                reply(controlOut, 501, "Bad path");
                continue;
            }

            File f = resolve(root, remote);
            if (f == null || !f.isFile() || !f.canRead()) {
                reply(controlOut, 550, "File unavailable");
                continue;
            }

            long total = f.length();
            offset = Math.min(offset, total);
            long remaining = total - offset;
            long length = requested <= 0 ? remaining : Math.min(requested, remaining);

            writeRaw(controlOut, "150 " + length + " " + total + "\r\n");

            activeTransfers.incrementAndGet();
            long sent = 0L;

            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                raf.seek(offset);

                while (sent < length && running) {
                    int n = raf.read(buf, 0, (int)Math.min((long)buf.length, length - sent));
                    if (n < 0) break;
                    if (n == 0) continue;

                    raw.write(buf, 0, n);
                    sent += n;
                    bytesServed.addAndGet(n);
                }

                raw.flush();
            } finally {
                activeTransfers.decrementAndGet();
            }

            if (sent != length) {
                try { writeRaw(controlOut, "426 " + sent + "\r\n"); } catch (Exception ignored) {}
                throw new EOFException("Turbo stream short send");
            }

            writeRaw(controlOut, "226 " + sent + "\r\n");
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
            data.setSendBufferSize(8 * 1024 * 1024);
            data.setKeepAlive(true);
            raf.seek(offset);
            OutputStream out = new BufferedOutputStream(data.getOutputStream(), 4 * 1024 * 1024);
            byte[] buf = new byte[4 * 1024 * 1024];
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
