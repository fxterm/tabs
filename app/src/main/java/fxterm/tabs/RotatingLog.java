package fxterm.tabs;

import module java.base;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;

/** The log file with a size cap: past `max` bytes it becomes `<name>.1` (the one older file kept) and a fresh file starts.
 *  It opens the file the NIO way, which lets Windows rename or delete it while Tabs runs. If the rename is refused the
 *  file is emptied instead, so it never grows without bound. A file already over the cap at start is rotated first. */
final class RotatingLog extends OutputStream {
    final Path file, old;
    final long max;
    OutputStream out;
    long size;

    RotatingLog(Path file, long max) throws IOException {
        this.file = file;
        this.max = max;
        old = file.resolveSibling(file.getFileName() + ".1");
        open();
    }

    synchronized void open() throws IOException {
        size = Files.exists(file) ? Files.size(file) : 0;
        if (size >= max) rotate();
        out = Files.newOutputStream(file, CREATE, APPEND);
    }

    void rotate() {
        try {
            Files.move(file, old, REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.write(file, new byte[0]); } catch (IOException ignored) { }
        }
        size = 0;
    }

    @Override public synchronized void write(byte[] b, int off, int len) throws IOException {
        if (size > 0 && size + len > max) {
            out.close();
            rotate();
            out = Files.newOutputStream(file, CREATE, APPEND);
        }
        out.write(b, off, len);
        out.flush();
        size += len;
    }

    @Override public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }
}
