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

        if (header[0] == WAV_HEADER[0] && header[1] == WAV_HEADER[1] && header[2] == WAV_HEADER[2] && header[3] == WAV_HEADER[3] &&
                header[8] == WAV_HEADER[4] && header[9] == WAV_HEADER[5] && header[10] == WAV_HEADER[6] && header[11] == WAV_HEADER[7]) {
            return FormatType.WAV;
        }

        if (header[0] == FLAC_HEADER[0] && header[1] == FLAC_HEADER[1] &&
                header[2] == FLAC_HEADER[2] && header[3] == FLAC_HEADER[3]) {
            return FormatType.FLAC;
        }

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
        // Frame sync: 0xFF with the top three bits of the next byte set.
        for (int i = 0; i < length - 3; i++) {
            if ((header[i] & 0xFF) == 0xFF) {
                int secondByte = header[i + 1] & 0xFF;
                if ((secondByte & 0xE0) == 0xE0) {
                    int thirdByte = header[i + 2] & 0xFF;

                    // Reserved bitrate (0xF) and sample-rate (0x3) indices are invalid.
                    if ((thirdByte & 0xF0) != 0xF0) {
                        if ((thirdByte & 0x0C) != 0x0C) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
