package indi.etern.musichud.client.audio.decoder;

import javazoom.jl.decoder.*;
import lombok.SneakyThrows;
import org.lwjgl.openal.AL10;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;

public class MP3StreamDecoder implements AudioDecoder {
    private final Bitstream bitstream;
    private final Decoder decoder;
    private int format;
    private int sampleRate;
    private boolean initialized = false;

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
                if (!initialized) {
                    this.sampleRate = header.frequency();
                    int channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                    this.format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
                    initialized = true;
                }

                Obuffer obuffer = decoder.decodeFrame(header, bitstream);
                if (obuffer instanceof SampleBuffer buffer) {
                    short[] shortData = buffer.getBuffer();

                    // 将short[]转换为byte[] (16位PCM, 小端序)
                    for (short sample : shortData) {
                        output.write(sample & 0xFF);        // 低字节
                        output.write((sample >> 8) & 0xFF); // 高字节
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
    public void close() {
        try {
            bitstream.close();
        } catch (Exception e) {
            // 忽略关闭错误
        }
    }
}
