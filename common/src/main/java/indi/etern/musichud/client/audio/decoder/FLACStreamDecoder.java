package indi.etern.musichud.client.audio.decoder;

import indi.etern.musichud.beans.user.MultichannelMode;
import indi.etern.musichud.client.audio.OpenAlSource;
import lombok.SneakyThrows;
import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTFloat32;

import java.io.*;

public class FLACStreamDecoder implements AudioDecoder {
    private final FLACDecoder decoder;
    private final BufferedInputStream inputStream;
    private final int sampleRate;
    private final int inputBytesPerSample;
    private final int inputFrameBytes;
    private final int bitsPerSample;
    private final int sourceChannels;
    private final boolean useFloat32;
    private final MultichannelMode multichannelMode;

    private volatile int format;
    private volatile int frameSize;
    private volatile int outputFrameBytes;
    private volatile IResampler channelMixer;
    private volatile IResampler bitDepthResampler;
    private volatile boolean float32Output;
    private volatile boolean discreteAttempt;

    public FLACStreamDecoder(BufferedInputStream inputStream, boolean useFloat32) throws IOException {
        this(inputStream, useFloat32, MultichannelMode.PREFER_DISCRETE);
    }

    public FLACStreamDecoder(BufferedInputStream inputStream, boolean useFloat32, MultichannelMode mode) throws IOException {
        this.inputStream = inputStream;
        this.decoder = new FLACDecoder(inputStream);
        this.useFloat32 = useFloat32;
        this.multichannelMode = mode == null ? MultichannelMode.PREFER_DISCRETE : mode;

        // read FLAC stream info
        try {
            StreamInfo streamInfo = decoder.readStreamInfo();
            this.sampleRate = streamInfo.getSampleRate();
            int channels = streamInfo.getChannels();
            int bitsPerSample = streamInfo.getBitsPerSample();
            this.sourceChannels = channels;
            this.bitsPerSample = bitsPerSample;
            this.inputBytesPerSample = bitsPerSample / 8;
            this.inputFrameBytes = channels * inputBytesPerSample;

            if (channels <= 2) {
                buildLegacyStereo(channels, bitsPerSample, useFloat32);
            } else {
                buildMultichannel(channels, bitsPerSample);
            }
        } catch (Exception e) {
            throw new IOException("Failed to initialize FLAC decoder", e);
        }
    }

    private void buildLegacyStereo(int channels, int bitsPerSample, boolean useFloat32) throws UnsupportedEncodingException {
        int effectiveBitsPerSample = bitsPerSample;
        boolean floatOutput = useFloat32 && channels == 2 && (bitsPerSample == 24 || bitsPerSample == 32);

        // determine OpenAL format based on channels and bit depth
        if (channels == 1) {
            switch (bitsPerSample) {
                case 8 -> this.format = AL10.AL_FORMAT_MONO8;
                case 16 -> this.format = AL10.AL_FORMAT_MONO16;
                case 24, 32 -> {
                    this.format = AL10.AL_FORMAT_MONO16;
                    effectiveBitsPerSample = 16;
                }
                default -> throw new UnsupportedEncodingException("Unsupported bits per sample: " + bitsPerSample);
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
                default -> throw new UnsupportedEncodingException("Unsupported bits per sample: " + bitsPerSample);
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

    private void buildMultichannel(int channels, int bitsPerSample) throws UnsupportedEncodingException {
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32) {
            throw new UnsupportedEncodingException("Unsupported bits per sample: " + bitsPerSample);
        }
        boolean wantDiscrete = multichannelMode != MultichannelMode.FORCE_DOWNMIX;
        int discreteBits = (bitsPerSample == 8 || bitsPerSample == 16) ? bitsPerSample : 16;
        int discreteFormat = wantDiscrete ? MultichannelFormats.discreteFormat(channels, discreteBits) : -1;
        if (wantDiscrete && discreteFormat != -1 && OpenAlSource.isMultichannelSupported()) {
            buildDiscrete(channels, bitsPerSample, discreteFormat);
        } else if (multichannelMode == MultichannelMode.DISCRETE_ONLY) {
            if (discreteFormat == -1) {
                throw new UnsupportedEncodingException("No discrete multichannel format for " + channels + " channels");
            }
            throw new UnsupportedEncodingException("Discrete multichannel output not supported by the OpenAL device");
        } else {
            buildDownmix(channels, bitsPerSample);
        }
    }

    private void buildDiscrete(int channels, int bitsPerSample, int discreteFormat) {
        // MCFORMATS has no float32 variants: 24/32-bit sources go through the
        // (channel-agnostic, sample-wise) 16-bit resamplers, keeping N channels.
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
        this.channelMixer = new MultichannelToStereoMixer(channels, bitsPerSample / 8, false);
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

    @Override
    @SneakyThrows
    public synchronized byte[] readChunk(long maxSize) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Scale the raw accumulation target by the frame-size ratio so the
        // converted chunk stays near maxSize (downmix shrinks Nch->stereo,
        // 24/32-bit resampling shrinks, float32 output expands).
        long rawTarget = Math.max(1, maxSize * inputFrameBytes / Math.max(1, outputFrameBytes));

        while (output.size() < rawTarget) {
            Frame frame = decoder.readNextFrame();
            if (frame == null)
                break;

            ByteData byteData = decoder.decodeFrame(frame, null);
            if (byteData == null)
                break;

            byte[] frameData = byteData.getData();
            output.write(frameData, 0, byteData.getLen());
        }

        if (output.size() == 0) return null;
        byte[] result = output.toByteArray();
        if (channelMixer != null) {
            result = channelMixer.resample(result);
        }
        if (bitDepthResampler != null) {
            result = bitDepthResampler.resample(result);
        }
        return result;
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
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ignored) {}
    }
}
