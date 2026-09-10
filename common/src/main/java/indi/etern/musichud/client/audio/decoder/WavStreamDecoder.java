package indi.etern.musichud.client.audio.decoder;

import indi.etern.musichud.beans.user.MultichannelMode;
import indi.etern.musichud.client.audio.OpenAlSource;
import lombok.SneakyThrows;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTFloat32;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * WAV audio decoder, supporting PCM (8/16/24/32-bit signed, mono/stereo).
 * Automatically resamples 24-bit and 32-bit to 16-bit.
 * <p>
 * When {@code useFloat32} is set and the source is stereo with 24/32-bit
 * samples, the decoded PCM is emitted losslessly as float32
 * ({@code AL_FORMAT_STEREO_FLOAT32}) instead of being dithered down to 16-bit.
 */
public class WavStreamDecoder implements AudioDecoder {
    private final InputStream inputStream;
    private final long dataSize;       // total audio data bytes in file
    private final int sampleRate;
    private final int inputFrameBytes;
    private final int bitsPerSample;
    private final int sourceChannels;
    private final boolean useFloat32;
    private final MultichannelMode multichannelMode;
    private volatile int format;          // OpenAL format constant
    private volatile int frameSize;
    private volatile int outputFrameBytes;
    private volatile IResampler channelMixer;
    private volatile IResampler bitDepthResampler;
    private volatile boolean float32Output;
    private volatile boolean discreteAttempt;
    private long bytesRead;      // raw bytes read from stream

    /**
     * Construct WAV decoder from an input stream, parse header and seek to data chunk.
     *
     * @param inputStream input stream (usually BufferedInputStream)
     * @param useFloat32  whether float32 buffer formats ({@code AL_EXT_FLOAT32})
     *                    are available; enables lossless 24/32-bit stereo output
     */
    public WavStreamDecoder(InputStream inputStream, boolean useFloat32) {
        this(inputStream, useFloat32, MultichannelMode.PREFER_DISCRETE);
    }

    /**
     * @param mode how to handle sources with more than 2 channels
     */
    @SneakyThrows
    public WavStreamDecoder(InputStream inputStream, boolean useFloat32, MultichannelMode mode) {
        this.inputStream = inputStream;
        this.useFloat32 = useFloat32;
        this.multichannelMode = mode == null ? MultichannelMode.PREFER_DISCRETE : mode;

        try {
            // 1. read RIFF header
            byte[] riff = new byte[4];
            readFully(riff);
            if (!new String(riff).equals("RIFF")) {
                throw new IOException("Not a WAV file: missing RIFF header");
            }
            readIntLE(); // total file size (skip)
            byte[] wave = new byte[4];
            readFully(wave);
            if (!new String(wave).equals("WAVE")) {
                throw new IOException("Not a WAV file: missing WAVE identifier");
            }

            // 2. locate fmt chunk
            int audioFormat;
            int channels;
            int sampleRateTmp;
            int bitsPerSample;
            while (true) {
                byte[] chunkId = new byte[4];
                readFully(chunkId);
                long chunkSize = readIntLE();
                if (new String(chunkId).equals("fmt ")) {
                    audioFormat = readShortLE();   // format tag, 1 = PCM
                    channels = readShortLE();
                    sampleRateTmp = readIntLE();
                    readIntLE();      // byte rate (skip)
                    readShortLE();    // block align (skip)
                    bitsPerSample = readShortLE();

                    // skip possible extra data in fmt chunk
                    long extra = chunkSize - 16;
                    if (extra > 0) {
                        long skipped = inputStream.skip(extra);
                        if (skipped != extra) {
                            throw new IOException("Failed to skip fmt chunk extra data");
                        }
                    }
                    break;
                } else {
                    // skip other chunks
                    long skipped = inputStream.skip(chunkSize);
                    if (skipped != chunkSize) {
                        throw new IOException("Failed to skip chunk");
                    }
                }
            }
            if (audioFormat != 1) {
                throw new IOException("Unsupported audio format: " + audioFormat + " (only PCM supported)");
            }

            // 3. locate data chunk
            long dataSizeTmp;
            while (true) {
                byte[] chunkId = new byte[4];
                readFully(chunkId);
                long chunkSize = readIntLE();
                if (new String(chunkId).equals("data")) {
                    dataSizeTmp = chunkSize;
                    break;
                } else {
                    long skipped = inputStream.skip(chunkSize);
                    if (skipped != chunkSize) {
                        throw new IOException("Failed to skip chunk");
                    }
                }
            }

            sampleRate = sampleRateTmp;
            dataSize = dataSizeTmp;
            bytesRead = 0;
            this.sourceChannels = channels;
            this.bitsPerSample = bitsPerSample;
            this.inputFrameBytes = channels * (bitsPerSample / 8);

            if (channels <= 2) {
                buildLegacyStereo(channels, bitsPerSample, useFloat32);
            } else {
                buildMultichannel(channels, bitsPerSample);
            }
        } catch (Exception e) {
            inputStream.close();
            throw e;
        }
    }

    private void buildLegacyStereo(int channels, int bitsPerSample, boolean useFloat32) throws IOException {
        int effectiveBitsPerSample = bitsPerSample;
        boolean floatOutput = useFloat32 && channels == 2 && (bitsPerSample == 24 || bitsPerSample == 32);

        // determine OpenAL format and resampler
        if (channels == 1) {
            switch (bitsPerSample) {
                case 8 -> this.format = AL10.AL_FORMAT_MONO8;
                case 16 -> this.format = AL10.AL_FORMAT_MONO16;
                case 24, 32 -> {
                    this.format = AL10.AL_FORMAT_MONO16;
                    effectiveBitsPerSample = 16;
                }
                default -> throw new IOException("Unsupported bits per sample: " + bitsPerSample);
            }
        } else {
            switch (bitsPerSample) {
                case 8 -> this.format = AL10.AL_FORMAT_STEREO8;
                case 16 -> this.format = AL10.AL_FORMAT_STEREO16;
                case 24, 32 -> {
                    if (floatOutput) {
                        this.format = EXTFloat32.AL_FORMAT_STEREO_FLOAT32;
                        effectiveBitsPerSample = 32;
                    } else {
                        this.format = AL10.AL_FORMAT_STEREO16;
                        effectiveBitsPerSample = 16;
                    }
                }
                default -> throw new IOException("Unsupported bits per sample: " + bitsPerSample);
            }
        }

        this.bitDepthResampler = switch (bitsPerSample) {
            case 24 -> floatOutput ? new Bit24ToFloat32Converter() : new Bit24To16Resampler();
            case 32 -> floatOutput ? new Bit32ToFloat32Converter() : new Bit32To16Resampler();
            default -> null;
        };

        this.channelMixer = null;
        this.float32Output = floatOutput;
        this.discreteAttempt = false;
        this.outputFrameBytes = channels * (floatOutput ? 4 : effectiveBitsPerSample / 8);
        frameSize = effectiveBitsPerSample * channels * sampleRate / 8;
    }

    private void buildMultichannel(int channels, int bitsPerSample) throws IOException {
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32) {
            throw new IOException("Unsupported bits per sample: " + bitsPerSample);
        }
        boolean wantDiscrete = multichannelMode != MultichannelMode.FORCE_DOWNMIX;
        int discreteBits = (bitsPerSample == 8 || bitsPerSample == 16) ? bitsPerSample : 16;
        int discreteFormat = wantDiscrete ? MultichannelFormats.discreteFormat(channels, discreteBits) : -1;
        if (wantDiscrete && discreteFormat != -1 && OpenAlSource.isMultichannelSupported()) {
            buildDiscrete(channels, bitsPerSample, discreteFormat);
        } else if (multichannelMode == MultichannelMode.DISCRETE_ONLY) {
            if (discreteFormat == -1) {
                throw new IOException("No discrete multichannel format for " + channels + " channels");
            }
            throw new IOException("Discrete multichannel output not supported by the OpenAL device");
        } else {
            buildDownmix(channels, bitsPerSample);
        }
    }

    private void buildDiscrete(int channels, int bitsPerSample, int discreteFormat) {
        this.bitDepthResampler = switch (bitsPerSample) {
            case 24 -> new Bit24To16Resampler();
            case 32 -> new Bit32To16Resampler();
            default -> null;
        };
        int effectiveBits = (bitsPerSample == 8) ? 8 : 16;
        this.channelMixer = null;
        this.format = discreteFormat;
        this.float32Output = false;
        this.discreteAttempt = true;
        this.outputFrameBytes = channels * (effectiveBits / 8);
        frameSize = effectiveBits * channels * sampleRate / 8;
    }

    private void buildDownmix(int channels, int bitsPerSample) {
        boolean floatOutput = useFloat32 && (bitsPerSample == 24 || bitsPerSample == 32);
        this.channelMixer = new MultichannelToStereoMixer(channels, bitsPerSample / 8, true);
        this.bitDepthResampler = switch (bitsPerSample) {
            case 24 -> floatOutput ? new Bit24ToFloat32Converter() : new Bit24To16Resampler();
            case 32 -> floatOutput ? new Bit32ToFloat32Converter() : new Bit32To16Resampler();
            default -> null;
        };
        int effectiveBits = bitsPerSample <= 16 ? bitsPerSample : (floatOutput ? 32 : 16);
        if (bitsPerSample == 8) {
            this.format = AL10.AL_FORMAT_STEREO8;
        } else if (floatOutput) {
            this.format = EXTFloat32.AL_FORMAT_STEREO_FLOAT32;
        } else {
            this.format = AL10.AL_FORMAT_STEREO16;
        }
        this.float32Output = floatOutput;
        this.discreteAttempt = false;
        this.outputFrameBytes = 2 * (effectiveBits / 8);
        frameSize = effectiveBits * 2 * sampleRate / 8;
    }

    @Override
    public synchronized boolean isDiscreteAttempt() {
        return discreteAttempt;
    }

    @Override
    public synchronized void fallbackToDownmix() {
        if (!discreteAttempt) return;
        buildDownmix(sourceChannels, bitsPerSample);
    }

    /**
     * Read a chunk of audio data.
     *
     * @param maxSize max bytes to return (after resampling if applicable)
     * @return audio data byte array, or null if end of data reached
     */
    @Override
    public synchronized byte[] readChunk(long maxSize) {
        if (bytesRead >= dataSize) {
            return null;
        }
        long remaining = dataSize - bytesRead;
        // Scale the raw read by the frame-size ratio so the converted chunk
        // stays near maxSize (downmix shrinks Nch->stereo, 24/32-bit
        // resampling shrinks, float32 output expands).
        long inputTarget = Math.max(1, maxSize * inputFrameBytes / Math.max(1, outputFrameBytes));
        long toRead = Math.min(inputTarget, remaining);
        if (toRead > Integer.MAX_VALUE) {
            toRead = Integer.MAX_VALUE;
        }
        byte[] buffer = new byte[(int) toRead];
        try {
            int read = 0;
            while (read < toRead) {
                int r = inputStream.read(buffer, read, (int) toRead - read);
                if (r == -1) {
                    break;
                }
                read += r;
            }
            bytesRead += read;
            if (read == 0) {
                return null;
            }
            byte[] result = read < buffer.length ? trim(buffer, read) : buffer;
            if (channelMixer != null) {
                result = channelMixer.resample(result);
            }
            if (bitDepthResampler != null) {
                result = bitDepthResampler.resample(result);
            }
            return result.length == 0 ? null : result;
        } catch (IOException e) {
            throw new RuntimeException("Error reading audio data", e);
        }
    }

    @Override
    public int getFormat() {
        return format;
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    @Override
    public int getFrameSize() {
        return frameSize;
    }


    @Override
    public void close() {
        try {
            inputStream.close();
        } catch (Exception ignored) {}
    }

    // ---------- helper methods ----------
    private void readFully(byte[] b) throws IOException {
        int read = 0;
        while (read < b.length) {
            int r = inputStream.read(b, read, b.length - read);
            if (r == -1) {
                throw new IOException("Unexpected end of stream");
            }
            read += r;
        }
    }

    private int readIntLE() throws IOException {
        byte[] b = new byte[4];
        readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private short readShortLE() throws IOException {
        byte[] b = new byte[2];
        readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    private static byte[] trim(byte[] src, int len) {
        byte[] dst = new byte[len];
        System.arraycopy(src, 0, dst, 0, len);
        return dst;
    }
}
