package indi.etern.musichud.client.audio;

import java.io.ByteArrayOutputStream;

/**
 * High-quality software audio resampler using Catmull-Rom cubic interpolation.
 * <p>
 * Resamples 16-bit interleaved PCM from source rate to target rate per-channel.
 * This bypasses OpenAL's potentially low-quality internal resampler,
 * ensuring consistent audio quality across different bundled OpenAL Soft versions.
 */
public class AudioResampler {
    private final int inputRate;
    private final int outputRate;
    private final int channels;
    private final short[] history;
    private int historyFrames;
    private long nextInputFrameGlobal;
    private long outputFramePos;

    /**
     * @param inputRate  source sample rate (e.g. 44100)
     * @param outputRate target sample rate (e.g. 48000)
     * @param channels   number of channels (1 = mono, 2 = stereo)
     */
    public AudioResampler(int inputRate, int outputRate, int channels) {
        this.inputRate = inputRate;
        this.outputRate = outputRate;
        this.channels = channels;
        this.history = new short[4 * channels];
        this.historyFrames = 0;
        this.nextInputFrameGlobal = 0;
        this.outputFramePos = 0;
    }

    /**
     * Process a chunk of 16-bit interleaved PCM and return resampled output.
     *
     * @param inputPcm little-endian 16-bit interleaved PCM bytes
     * @return little-endian 16-bit interleaved PCM bytes at the target sample rate
     */
    public byte[] process(byte[] inputPcm) {
        int inputFrames = inputPcm.length / (2 * channels);
        if (inputFrames == 0) return new byte[0];

        short[] inputSamples = bytesToShorts(inputPcm);

        // Keep at most 3 history frames for Catmull-Rom (need n-1)
        int keepHist = Math.min(historyFrames, 3);
        int totalFrames = keepHist + inputFrames;
        short[] combined = new short[totalFrames * channels];
        int histSrc = historyFrames - keepHist;
        System.arraycopy(history, histSrc * channels, combined, 0, keepHist * channels);
        System.arraycopy(inputSamples, 0, combined, keepHist * channels, inputFrames * channels);

        // combined[i] is at global input position: firstInputGlobal + i
        long firstInputGlobal = nextInputFrameGlobal - keepHist;
        long lastInputGlobal = nextInputFrameGlobal + inputFrames - 1;

        // Advance outputFramePos past frames that need samples before firstInputGlobal.
        // For Catmull-Rom we need samples at n-1, n, n+1, n+2, so n-1 >= firstInputGlobal.
        long k = outputFramePos;
        while (true) {
            long n = k * inputRate / outputRate;
            if (n - 1 >= firstInputGlobal) break;
            if (n + 2 > lastInputGlobal) {
                // Can't produce any valid output yet — save history and return
                updateHistory(inputSamples, inputFrames);
                nextInputFrameGlobal += inputFrames;
                outputFramePos = k;
                return new byte[0];
            }
            k++;
        }

        // Produce output frames while n+2 is within the combined buffer
        ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
        while (true) {
            long n = k * inputRate / outputRate;
            if (n + 2 > lastInputGlobal) break;

            int baseIdx = (int) (n - firstInputGlobal);
            double t = (double) (k * inputRate % outputRate) / outputRate;

            for (int ch = 0; ch < channels; ch++) {
                double y0 = sampleAt(combined, baseIdx - 1, ch);
                double y1 = sampleAt(combined, baseIdx, ch);
                double y2 = sampleAt(combined, baseIdx + 1, ch);
                double y3 = sampleAt(combined, baseIdx + 2, ch);

                double val = catmullRom(y0, y1, y2, y3, t);
                int clamped = (int) Math.round(val);
                if (clamped < Short.MIN_VALUE) clamped = Short.MIN_VALUE;
                if (clamped > Short.MAX_VALUE) clamped = Short.MAX_VALUE;
                bos.write(clamped & 0xFF);
                bos.write((clamped >> 8) & 0xFF);
            }
            k++;
        }

        updateHistory(inputSamples, inputFrames);
        nextInputFrameGlobal += inputFrames;
        outputFramePos = k;

        return bos.toByteArray();
    }

    /**
     * Flush remaining output frames (zero-padded tail), called when input is exhausted.
     */
    public byte[] flush() {
        short[] tail = new short[4 * channels];
        byte[] tailBytes = shortsToBytes(tail);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int iterations = 0;
        // Feed zero frames; the resampler will eventually stop producing output
        // when the tail is consumed.
        while (iterations++ < 8) {
            byte[] result = process(tailBytes);
            if (result.length == 0) break;
            bos.writeBytes(result);
        }
        return bos.toByteArray();
    }

    // ---- internal helpers ----

    private void updateHistory(short[] samples, int frameCount) {
        int keep = Math.min(frameCount, 4);
        int srcStart = (frameCount - keep) * channels;
        // Ensure history doesn't overflow its fixed size
        if (historyFrames + keep > history.length / channels) {
            int shift = keep;
            int move = historyFrames - shift;
            if (move > 0) {
                System.arraycopy(history, shift * channels, history, 0, move * channels);
            }
            historyFrames = move;
        }
        System.arraycopy(samples, srcStart, history, historyFrames * channels, keep * channels);
        historyFrames += keep;
    }

    private double sampleAt(short[] buffer, int frameIdx, int channel) {
        int totalFrames = buffer.length / channels;
        if (frameIdx < 0 || frameIdx >= totalFrames) return 0.0;
        return buffer[frameIdx * channels + channel];
    }

    // ---- Catmull-Rom cubic interpolation ----

    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (
                (2.0 * p1) +
                (-p0 + p2) * t +
                (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 +
                (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
        );
    }

    // ---- byte conversion ----

    private static short[] bytesToShorts(byte[] bytes) {
        int len = bytes.length / 2;
        short[] result = new short[len];
        for (int i = 0; i < len; i++) {
            int lo = bytes[i * 2] & 0xFF;
            int hi = bytes[i * 2 + 1] & 0xFF;
            result[i] = (short) (lo | (hi << 8));
        }
        return result;
    }

    private static byte[] shortsToBytes(short[] shorts) {
        byte[] result = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            int s = shorts[i];
            result[i * 2] = (byte) (s & 0xFF);
            result[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return result;
    }

    @Override
    public String toString() {
        return "AudioResampler{ratio=" + inputRate + "->" + outputRate + ", channels=" + channels + "}";
    }
}
