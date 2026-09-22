package in.flashdrop.lan;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FlashDrop Turbo v1.4 raw read-only LAN server.
 * One request per TCP connection; optimized for sequential file streaming.
 */
public class TurboFileServer {
    private static final int DEFAULT_PORT = 9091;
    private static final int MAX_HEADER = 64 * 1024;
    private static final long MAX_BENCH = 512L * 1024L * 1024L;

    private final File root;
    private final String pin;
    private volatile boolean running;
    private ServerSocketChannel server;
    private Thread acceptThread;
    private ExecutorService pool;
    private int port;

    private final AtomicLong bytesServed = new AtomicLong();
    private final AtomicInteger activeTransfers = new AtomicInteger();

    public TurboFileServer(File root, String pin) throws IOException {
        this.root = root.getCanonicalFile();
        this.pin = pin;
    }

    public void start() throws IOException {
        ServerSocketChannel sc = ServerSocketChannel.open();
        sc.configureBlocking(true);
        sc.socket().setReuseAddress(true);
        sc.socket().bind(new InetSocketAddress("0.0.0.0", DEFAULT_PORT), 64);
        server = sc;
        port = DEFAULT_PORT;

        pool = new ThreadPoolExecutor(
                2, 16, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(64),
                r -> {
                    Thread t = new Thread(r, "FlashDropTurbo");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY + 1);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        running = true;
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    SocketChannel ch = server.accept();
                    configure(ch);
                    pool.execute(() -> handle(ch));
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        }, "FlashDropTurboAccept");
        acceptThread.setDaemon(true);
        acceptThread.setPriority(Thread.NORM_PRIORITY + 1);
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

    private void configure(SocketChannel ch) throws SocketException {
        Socket s = ch.socket();
        s.setTcpNoDelay(true);
        s.setKeepAlive(true);
        try { s.setSendBufferSize(4 * 1024 * 1024); } catch (Exception ignored) {}
        try { s.setReceiveBufferSize(1024 * 1024); } catch (Exception ignored) {}
        try { s.setTrafficClass(0x08); } catch (Exception ignored) {}
    }

    private void handle(SocketChannel ch) {
        activeTransfers.incrementAndGet();
        try (SocketChannel channel = ch) {
            Request req = readRequest(channel.socket().getInputStream());
            if (req == null || !"FLASHDROP/1".equals(req.magic)) {
                sendError(channel, "BAD_PROTOCOL");
                return;
            }
            if (!pin.equals(req.pin)) {
                sendError(channel, "BAD_PIN");
                return;
            }

            if ("BENCH".equals(req.mode)) {
                long size = Math.max(8L * 1024L * 1024L, Math.min(MAX_BENCH, req.benchBytes));
                sendHeader(channel, "OK " + size + "\n");
                sendBenchmark(channel, size);
                return;
            }

            if (!"GET".equals(req.mode)) {
                sendError(channel, "BAD_MODE");
                return;
            }

            File f = resolve(req.path);
            if (f == null || !f.isFile() || !f.canRead()) {
                sendError(channel, "FILE_UNAVAILABLE");
                return;
            }

            long size = f.length();
            long offset = Math.max(0L, Math.min(req.offset, size));
            long remaining = size - offset;
            sendHeader(channel, "OK " + remaining + " " + size + "\n");
            if (remaining > 0) sendFile(channel, f, offset, remaining);
        } catch (Exception ignored) {
        } finally {
            activeTransfers.decrementAndGet();
        }
    }

    private void sendFile(SocketChannel channel, File file, long offset, long length) throws IOException {
        // Explicit sequential buffered I/O is more stable than sendfile/transferTo
        // on several Android hotspot/kernel combinations.
        final int bufferSize = 2 * 1024 * 1024;
        ByteBuffer buf = ByteBuffer.allocateDirect(bufferSize);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel fc = raf.getChannel()) {
            fc.position(offset);
            long left = length;

            while (left > 0 && running) {
                buf.clear();
                if (left < bufferSize) buf.limit((int)left);

                int read = fc.read(buf);
                if (read < 0) break;
                if (read == 0) {
                    Thread.yield();
                    continue;
                }

                buf.flip();
                int wrote = 0;
                while (buf.hasRemaining() && running) {
                    int n = channel.write(buf);
                    if (n < 0) throw new EOFException("Socket closed");
                    if (n == 0) {
                        Thread.yield();
                        continue;
                    }
                    wrote += n;
                }

                left -= wrote;
                bytesServed.addAndGet(wrote);
            }

            if (left != 0) throw new EOFException("Short send");
        }
    }

    private void sendBenchmark(SocketChannel channel, long length) throws IOException {
        ByteBuffer zero = ByteBuffer.allocateDirect(1024 * 1024);
        long left = length;
        while (left > 0 && running) {
            zero.clear();
            int lim = (int)Math.min(zero.capacity(), left);
            zero.limit(lim);
            int wrote = 0;
            while (zero.hasRemaining()) wrote += channel.write(zero);
            left -= wrote;
            bytesServed.addAndGet(wrote);
        }
    }

    private void sendHeader(SocketChannel ch, String s) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
        while (b.hasRemaining()) ch.write(b);
    }

    private void sendError(SocketChannel ch, String message) {
        try { sendHeader(ch, "ERR " + message + "\n"); } catch (Exception ignored) {}
    }

    private Request readRequest(InputStream input) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8), 4096);
        Request q = new Request();
        int consumed = 0;
        String line;
        while ((line = r.readLine()) != null) {
            consumed += line.length() + 1;
            if (consumed > MAX_HEADER) throw new IOException("Header too large");
            if (q.magic == null) {
                q.magic = line.trim();
                continue;
            }
            if (line.length() == 0) break;
            int sp = line.indexOf(' ');
            String k = sp < 0 ? line.trim().toUpperCase(Locale.US) : line.substring(0, sp).trim().toUpperCase(Locale.US);
            String v = sp < 0 ? "" : line.substring(sp + 1).trim();
            switch (k) {
                case "PIN": q.pin = v; break;
                case "MODE": q.mode = v.toUpperCase(Locale.US); break;
                case "PATH":
                    try { q.path = new String(android.util.Base64.decode(v, android.util.Base64.DEFAULT), StandardCharsets.UTF_8); }
                    catch (Exception e) { q.path = null; }
                    break;
                case "OFFSET":
                    try { q.offset = Long.parseLong(v); } catch (Exception ignored) {}
                    break;
                case "BYTES":
                    try { q.benchBytes = Long.parseLong(v); } catch (Exception ignored) {}
                    break;
            }
        }
        return q;
    }

    private File resolve(String raw) {
        if (raw == null) return null;
        try {
            String p = raw.replace('\\', '/');
            while (p.startsWith("/")) p = p.substring(1);
            File f = p.isEmpty() ? root : new File(root, p);
            File c = f.getCanonicalFile();
            String rp = root.getCanonicalPath();
            String fp = c.getCanonicalPath();
            if (fp.equals(rp) || fp.startsWith(rp + File.separator)) return c;
        } catch (Exception ignored) {}
        return null;
    }

    private static class Request {
        String magic;
        String pin;
        String mode;
        String path;
        long offset;
        long benchBytes = 128L * 1024L * 1024L;
    }
}
