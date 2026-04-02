package indi.etern.musichud.beans.music;

import indi.etern.musichud.client.audio.decoder.*;
import lombok.SneakyThrows;

import java.io.BufferedInputStream;

public enum FormatType {
    FLAC {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new FLACStreamDecoder(inputStream);
        }
    },
    MP3 {
        @Override
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new MP3StreamDecoder(inputStream);
        }
    },
    WAV {
        @Override
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return new WavStreamDecoder(inputStream);
        }
    },
    AUTO {
        @Override
        @SneakyThrows
        public AudioDecoder newDecoder(BufferedInputStream inputStream) {
            return AudioFormatDetector.detectFormat(inputStream).newDecoder(inputStream);
        }
    };

    public abstract AudioDecoder newDecoder(BufferedInputStream inputStream);
}
