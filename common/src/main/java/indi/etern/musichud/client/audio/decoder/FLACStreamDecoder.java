package indi.etern.musichud.client.audio.decoder;

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
    private final int format;
    private final int sampleRate;
    private final int frameSize;
    private final IResampler resampler;
    private final boolean float32Output;
    private final int inputBytesPerSample;

    public FLACStreamDecoder(BufferedInputStream inputStream, boolean useFloat32) throws IOException {
        this.inputStream = inputStream;
        this.decoder = new FLACDecoder(inputStream);

        // read FLAC stream info
        try {
            StreamInfo streamInfo = decoder.readStreamInfo();
            this.sampleRate = streamInfo.getSampleRate();
            int channels = streamInfo.getChannels();
            int bitsPerSample = streamInfo.getBitsPerSample();
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
            } else if (channels == 2) {
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
            } else {
                throw new UnsupportedEncodingException("More than 2 channels");
            }

            this.resampler = switch (bitsPerSample) {
                case 24 -> floatOutput ? new Bit24ToFloat32Converter() : new Bit24To16Resampler();
                case 32 -> floatOutput ? new Bit32ToFloat32Converter() : new Bit32To16Resampler();
                default -> null;
            };

            this.float32Output = floatOutput;
            this.inputBytesPerSample = bitsPerSample / 8;
            frameSize = effectiveBitsPerSample * channels * sampleRate / 8;
        } catch (Exception e) {
            throw new IOException("Failed to initialize FLAC decoder", e);
        }
    }

    @Override
    @SneakyThrows
    public byte[] readChunk(long maxSize) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // For float32 output, stop accumulating raw PCM earlier so the converted
        // chunk stays near maxSize (24-bit raw -> 4/3 expansion, 32-bit -> 1:1).
        long rawTarget = float32Output
                ? Math.max(1, (long) (maxSize * inputBytesPerSample / 4.0))
                : maxSize;

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
        if (resampler != null) {
            result = resampler.resample(result);
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
