package indi.etern.musichud.client.audio.decoder;

import lombok.SneakyThrows;
import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;
import org.lwjgl.openal.AL10;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FLACStreamDecoder implements AudioDecoder {
    private final FLACDecoder decoder;
    private final BufferedInputStream inputStream;
    private final int format;
    private final int sampleRate;
    private boolean convert24bitsTo16bits = false;

    public FLACStreamDecoder(BufferedInputStream inputStream) throws IOException {
        this.inputStream = inputStream;
        this.decoder = new FLACDecoder(inputStream);

        // 读取FLAC流信息
        try {
            StreamInfo streamInfo = decoder.readStreamInfo();
            this.sampleRate = streamInfo.getSampleRate();
            int channels = streamInfo.getChannels();
            int bitsPerSample = streamInfo.getBitsPerSample();

            // 根据声道数和位深度确定OpenAL格式
            if (channels == 1) {
                switch (bitsPerSample) {
                    case 8 -> this.format = AL10.AL_FORMAT_MONO8;
                    case 16 -> this.format = AL10.AL_FORMAT_MONO16;
                    case 24 -> {
                        convert24bitsTo16bits = true;
                        this.format = AL10.AL_FORMAT_STEREO16;
                    }
                    default -> throw new UnsupportedEncodingException("bits per sample is not 8/16/24");
                }
            } else if (channels == 2) {
                switch (bitsPerSample) {
                    case 8 -> this.format = AL10.AL_FORMAT_STEREO8;
                    case 16 -> this.format = AL10.AL_FORMAT_STEREO16;
                    case 24 -> {
                        convert24bitsTo16bits = true;
                        this.format = AL10.AL_FORMAT_STEREO16;
                    }
                    default -> throw new UnsupportedEncodingException("bits per sample is not 8/16/24");
                }
            } else {
                throw new UnsupportedEncodingException("More than 2 channels");
            }
        } catch (Exception e) {
            throw new IOException("Failed to initialize FLAC decoder", e);
        }
    }

    @Override
    @SneakyThrows
    public byte[] readChunk(long maxSize) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        while (output.size() < maxSize) {
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
        if (convert24bitsTo16bits) {
            result = convert24BitTo16Bit(result);
        }
        return result;
    }

    private byte[] convert24BitTo16Bit(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return null;

        // 24位音频每样本3字节，转换为16位每样本2字节
        int sampleCount = audioData.length / 3;
        short[] samples = new short[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            int offset = i * 3;
            // 读取24位小端序样本
            int sample24 = ((audioData[offset + 2] & 0xFF) << 16) |
                    ((audioData[offset + 1] & 0xFF) << 8) |
                    (audioData[offset] & 0xFF);

            // 转换为16位（取高16位或进行缩放）
            samples[i] = (short) (sample24 >> 8);
        }

        // 转换为字节数组
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        return buffer.array();
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
    public void close() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            // 忽略关闭错误
        }
    }
}
