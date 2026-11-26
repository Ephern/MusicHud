package indi.etern.musichud.client.audio.decoder;

import indi.etern.musichud.client.audio.info.AudioChannelLayout;
import indi.etern.musichud.client.audio.info.AudioContainer;
import indi.etern.musichud.client.audio.info.AudioOutputInfo;
import indi.etern.musichud.client.audio.info.DecodedStreamInfo;
import javazoom.jl.decoder.*;
import lombok.SneakyThrows;
import org.lwjgl.openal.AL10;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;

public class MP3StreamDecoder implements AudioDecoder {
    private final Bitstream bitstream;
    private final Decoder decoder;
    private volatile int format;
    private volatile int sampleRate;
    private volatile int channels = 2;
    private volatile int frameSize;
    private volatile boolean initialized = false;
    private final Object bitrateLock = new Object();
    private long bitrateSum;
    private long frameCount;
    private int minBitrate = Integer.MAX_VALUE;
    private int maxBitrate;

    public MP3StreamDecoder(BufferedInputStream inputStream) {
        this.bitstream = new Bitstream(inputStream);
        this.decoder = new Decoder();
    }

    @Override
    @SneakyThrows
    public byte[] readChunk(long maxSize) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (output.size() < maxSize) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                // bitrate() is bps; bitrateInstant() exposes frame values for VBR detection.
                int bitrate = header.bitrate();
                int instantBitrate = header.bitrateInstant();
                synchronized (bitrateLock) {
                    if (bitrate > 0) {
                        bitrateSum += bitrate;
                        frameCount++;
                    }
                    if (instantBitrate > 0) {
                        minBitrate = Math.min(minBitrate, instantBitrate);
                        maxBitrate = Math.max(maxBitrate, instantBitrate);
                    }
                }
                if (!initialized) {
                    this.sampleRate = header.frequency();
                    this.channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                    this.format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
                    frameSize = sampleRate * channels * 16 / 8/*bitsPerSample*/;
                    initialized = true;
                }

                OBuffer obuffer = decoder.decodeFrame(header, bitstream);
                if (obuffer instanceof SampleBuffer buffer) {
                    short[] shortData = buffer.getBuffer();

                    // 16-bit little-endian PCM.
                    for (int i = 0; i < buffer.getBufferLength(); i++) {
                        short sample = shortData[i];
                        output.write(sample & 0xFF);
                        output.write((sample >> 8) & 0xFF);
                    }
                }
                bitstream.closeFrame();
            }

            if (output.size() == 0) return null;
            return output.toByteArray();
        } catch (BitstreamException e) {
            if (e.getErrorCode() == BitstreamErrors.STREAM_ERROR) {
                return null;
            } else {
                throw e;
            }
        }
    }

    @Override
    public int getFormat() {
        return format != 0 ? format : AL10.AL_FORMAT_STEREO16;
    }

    @Override
    public int getSampleRate() {
        return sampleRate != 0 ? sampleRate : 44100;
    }

    @Override
    public int getFrameSize() {
        return frameSize;
    }

    @Override
    public DecodedStreamInfo getStreamInfo() {
        if (!initialized) {
            return null;
        }
        long frames;
        long sum;
        int min;
        int max;
        synchronized (bitrateLock) {
            frames = frameCount;
            sum = bitrateSum;
            min = minBitrate;
            max = maxBitrate;
        }
        int averageBitrate = frames == 0 ? 0 : (int) (sum / frames);
        boolean variableBitrate = frames > 1 && min != max;
        return new DecodedStreamInfo(
                AudioContainer.MP3,
                AudioChannelLayout.fromChannels(channels),
                sampleRate,
                channels,
                0,
                averageBitrate,
                -1,
                variableBitrate
        );
    }

    @Override
    public AudioOutputInfo getOutputInfo() {
        if (!initialized) {
            return null;
        }
        return AudioOutputInfo.describe(format, sampleRate, channels, false);
    }

    @Override
    public void close() {
        try {
            bitstream.close();
        } catch (Exception ignored) {}
    }
}
