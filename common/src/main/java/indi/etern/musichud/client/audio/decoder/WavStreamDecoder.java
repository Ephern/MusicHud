package indi.etern.musichud.client.audio.decoder;

import lombok.SneakyThrows;
import org.lwjgl.openal.AL10;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * WAV 音频解码器，支持 PCM 格式（8位/16位，单声道/立体声）。
 */
public class WavStreamDecoder implements AudioDecoder {
    private final InputStream inputStream;
    private final int sampleRate;
    private final int format;          // OpenAL 格式常量
    private final long dataSize;       // 音频数据总字节数
    private final int frameSize;
    private long bytesRead;            // 已读取的音频数据字节数

    /**
     * 从输入流构造 WAV 解码器，解析文件头并定位到数据块开始。
     *
     * @param inputStream 输入流（通常为 BufferedInputStream）
     */
    @SneakyThrows
    public WavStreamDecoder(InputStream inputStream) {
        this.inputStream = inputStream;

        // 1. 读取 RIFF 头
        byte[] riff = new byte[4];
        readFully(riff);
        if (!new String(riff).equals("RIFF")) {
            throw new IOException("Not a WAV file: missing RIFF header");
        }
        readIntLE(); // 文件总大小（跳过）
        byte[] wave = new byte[4];
        readFully(wave);
        if (!new String(wave).equals("WAVE")) {
            throw new IOException("Not a WAV file: missing WAVE identifier");
        }

        // 2. 查找 fmt 块
        int audioFormat;
        int channels;
        int sampleRateTmp;
        int bitsPerSample;
        while (true) {
            byte[] chunkId = new byte[4];
            readFully(chunkId);
            long chunkSize = readIntLE();
            if (new String(chunkId).equals("fmt ")) {
                audioFormat = readShortLE();   // 格式标签，1 表示 PCM
                channels = readShortLE();
                sampleRateTmp = readIntLE();
                readIntLE();      // 字节率（跳过）
                readShortLE();    // 块对齐（跳过）
                bitsPerSample = readShortLE();

                // 跳过 fmt 块中可能存在的额外数据
                long extra = chunkSize - 16;
                if (extra > 0) {
                    long skipped = inputStream.skip(extra);
                    if (skipped != extra) {
                        throw new IOException("Failed to skip fmt chunk extra data");
                    }
                }
                break;
            } else {
                // 忽略其他块
                long skipped = inputStream.skip(chunkSize);
                if (skipped != chunkSize) {
                    throw new IOException("Failed to skip chunk");
                }
            }
        }
        if (audioFormat != 1) {
            throw new IOException("Unsupported audio format: " + audioFormat + " (only PCM supported)");
        }

        // 3. 查找 data 块
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
        frameSize = sampleRate * channels * bitsPerSample / 8;
        bytesRead = 0;

        // 确定 OpenAL 格式常量
        if (channels == 1 && bitsPerSample == 8) {
            this.format = AL10.AL_FORMAT_MONO8;
        } else if (channels == 1 && bitsPerSample == 16) {
            this.format = AL10.AL_FORMAT_MONO16;
        } else if (channels == 2 && bitsPerSample == 8) {
            this.format = AL10.AL_FORMAT_STEREO8;
        } else if (channels == 2 && bitsPerSample == 16) {
            this.format = AL10.AL_FORMAT_STEREO16;
        } else {
            throw new IOException("Unsupported channel count (" + channels +
                    ") or bits per sample (" + bitsPerSample + ")");
        }
    }

    /**
     * 读取一段音频数据。
     *
     * @param maxSize 最大字节数（实际返回可能小于该值）
     * @return 音频数据字节数组，如果已到达数据末尾则返回 null
     */
    @Override
    public byte[] readChunk(long maxSize) {
        if (bytesRead >= dataSize) {
            return null;
        }
        long remaining = dataSize - bytesRead;
        long toRead = Math.min(maxSize, remaining);
        if (toRead > Integer.MAX_VALUE) {
            toRead = Integer.MAX_VALUE;
        }
        byte[] buffer = new byte[(int) toRead];
        try {
            int read = 0;
            while (read < toRead) {
                int r = inputStream.read(buffer, read, (int) toRead - read);
                if (r == -1) {
                    // 流提前结束，但理论上有 dataSize 保证，不会发生
                    break;
                }
                read += r;
            }
            bytesRead += read;
            if (read == 0) {
                return null;
            }
            // 如果读取不足，返回实际读取的部分
            if (read < buffer.length) {
                byte[] trimmed = new byte[read];
                System.arraycopy(buffer, 0, trimmed, 0, read);
                return trimmed;
            }
            return buffer;
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
    @SneakyThrows
    public void close() {
        inputStream.close();
    }

    // ---------- 辅助方法 ----------
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
}
