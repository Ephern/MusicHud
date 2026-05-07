package indi.etern.musichud.client.audio.decoder;

public interface AudioDecoder extends AutoCloseable {
    byte[] readChunk(long maxSize);
    int getFormat();
    int getSampleRate();

    int getFrameSize();

    void close();
}
