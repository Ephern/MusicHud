package indi.etern.musichud.client.utils;

import indi.etern.musichud.MusicHud;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class HashUtils {
    private static final Logger logger = MusicHud.getLogger(HashUtils.class);

    private HashUtils() {
    }

    public static String computeMd5(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("path must refer to a file");
        }
        try (InputStream fileInputStream = Files.newInputStream(path)) {
            MessageDigest MD5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fileInputStream.read(buffer)) != -1) {
                MD5.update(buffer, 0, length);
            }
            return new String(Hex.encodeHex(MD5.digest()));
        } catch (Throwable t) {
            logger.error("failed to compute md5 of {}", path.toAbsolutePath().toString(), t);
            return null;
        }
    }
}
