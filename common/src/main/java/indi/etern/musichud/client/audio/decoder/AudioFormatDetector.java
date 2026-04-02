package indi.etern.musichud.client.audio.decoder;

import indi.etern.musichud.beans.music.FormatType;

import java.io.IOException;
import java.io.InputStream;

public class AudioFormatDetector {
    private static final byte[] ID3_HEADER = {0x49, 0x44, 0x33};
    private static final byte[] FLAC_HEADER = {0x66, 0x4C, 0x61, 0x43};
    private static final byte[] WAV_HEADER = {'R', 'I', 'F', 'F', 'W', 'A', 'V', 'E'};

    public static FormatType detectFormat(InputStream inputStream) throws IOException {
        if (!inputStream.markSupported()) {
            throw new IllegalArgumentException("InputStream must support mark/reset");
        }

        inputStream.mark(1024);
        byte[] header = new byte[1024];
        int bytesRead = inputStream.read(header);
        inputStream.reset();

        if (bytesRead < 12) {
            throw new IOException("Insufficient data to determine format");
        }

        // 检测 WAV 格式
        if (header[0] == WAV_HEADER[0] && header[1] == WAV_HEADER[1] && header[2] == WAV_HEADER[2] && header[3] == WAV_HEADER[3] &&
                header[8] == WAV_HEADER[4] && header[9] == WAV_HEADER[5] && header[10] == WAV_HEADER[6] && header[11] == WAV_HEADER[7]) {
            return FormatType.WAV;
        }

        // 检测 FLAC 格式（原逻辑）
        if (header[0] == FLAC_HEADER[0] && header[1] == FLAC_HEADER[1] &&
                header[2] == FLAC_HEADER[2] && header[3] == FLAC_HEADER[3]) {
            return FormatType.FLAC;
        }

        // 检测 MP3 格式（原逻辑，注意 ID3 头或帧头检测）
        if (header[0] == ID3_HEADER[0] && header[1] == ID3_HEADER[1] &&
                header[2] == ID3_HEADER[2]) {
            return FormatType.MP3;
        }

        if (detectMP3FrameHeader(header, bytesRead)) {
            return FormatType.MP3;
        }

        throw new IOException("Unsupported audio format");
    }

    private static boolean detectMP3FrameHeader(byte[] header, int length) {
        // MP3帧头检测：查找11个连续的1位（0xFF + 第二字节的高3位为111）
        for (int i = 0; i < length - 3; i++) {
            if ((header[i] & 0xFF) == 0xFF) {
                int secondByte = header[i + 1] & 0xFF;
                // 检查第二字节的高3位是否为111（MPEG版本和层信息）
                if ((secondByte & 0xE0) == 0xE0) {
                    // 进一步验证这是一个有效的MP3帧头
                    int thirdByte = header[i + 2] & 0xFF;

                    // 检查位率索引（不能是1111，表示无效）
                    if ((thirdByte & 0xF0) != 0xF0) {
                        // 检查采样率索引（不能是11，表示无效）
                        if ((thirdByte & 0x0C) != 0x0C) {
                            // 检查保护位、填充位等
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
