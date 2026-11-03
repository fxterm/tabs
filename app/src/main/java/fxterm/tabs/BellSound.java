package fxterm.tabs;

import module java.base;
import java.lang.foreign.*;

/** The one soft note a tab asking for the user rings: a small bell made here, not a file from Windows, so its loudness and
 *  length are ours. Four partials with the slow, uneven decay of a real bell, peaking at about 22% of full scale (well under
 *  a system sound) and gone in 1.3 seconds. Played through Java Sound, on every platform; Windows' PlaySound is the fallback. */
public final class BellSound {

    static final int RATE = 44100;
    static final double SECONDS = 1.3, PEAK = 0.22, BASE_HZ = 784;
    /** Each partial: its multiple of the base note, its strength, and how fast it dies away (per second). */
    static final double[][] PARTIALS = {{1, 1, 3.6}, {2, 0.42, 5.2}, {2.76, 0.22, 7.5}, {5.4, 0.08, 12}};

    static MemorySegment image;

    /** The note as a 16-bit mono WAV file image. */
    public static byte[] wav() {
        var n = (int) (RATE * SECONDS);
        var samples = new double[n];
        var top = 0.0;
        for (var i = 0; i < n; i++) {
            var t = i / (double) RATE;
            var v = 0.0;
            for (var p : PARTIALS) v += p[1] * Math.exp(-p[2] * t) * Math.sin(2 * Math.PI * BASE_HZ * p[0] * t);
            // a 6 ms rise so it begins without a click, and a 60 ms fade so it ends without one
            v *= Math.min(1, t / 0.006) * Math.min(1, (SECONDS - t) / 0.06);
            samples[i] = v;
            top = Math.max(top, Math.abs(v));
        }
        var out = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(n * 2);
        for (var s : samples) out.putShort((short) Math.round(s / top * PEAK * Short.MAX_VALUE));
        return out.array();
    }

    /** Once, without blocking the caller. Plain Java Sound everywhere; if that cannot play (no audio line, a trimmed
     *  runtime), Windows' own PlaySound takes over, and what went wrong is logged either way. */
    public static void play() {
        Thread.ofPlatform().daemon().start(() -> {
            try {
                javaSound();
            } catch (Throwable e) {
                System.err.println("fxterm-tabs: Java Sound could not play the bell: " + e);
                if (!Os.WIN) return;
                try { winmm(); } catch (Throwable x) { System.err.println("fxterm-tabs: the bell did not sound: " + x); }
            }
        });
    }

    static synchronized void winmm() throws Throwable {
        var lib = SymbolLookup.libraryLookup("winmm", Arena.global());
        var playSound = Linker.nativeLinker().downcallHandle(lib.find("PlaySoundW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        if (image == null) {
            var bytes = wav();
            image = Arena.global().allocate(bytes.length);
            MemorySegment.copy(bytes, 0, image, ValueLayout.JAVA_BYTE, 0, bytes.length);
        }
        // SND_MEMORY | SND_ASYNC | SND_NODEFAULT: from memory, return at once, and no system "default beep" if it fails
        if ((int) playSound.invoke(image, MemorySegment.NULL, 0x4 | 0x1 | 0x2) == 0) throw new IOException("PlaySound refused the bell");
    }

    /** The note through a Clip, closed when it stops (waits for that, at most a few seconds). */
    public static void javaSound() throws Exception {
        try (var in = javax.sound.sampled.AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav()))) {
            var clip = javax.sound.sampled.AudioSystem.getClip();
            var done = new CountDownLatch(1);
            clip.addLineListener(e -> { if (e.getType() == javax.sound.sampled.LineEvent.Type.STOP) done.countDown(); });
            clip.open(in);
            clip.start();
            done.await(3, TimeUnit.SECONDS);
            clip.close();
        }
    }

    private BellSound() { }
}
