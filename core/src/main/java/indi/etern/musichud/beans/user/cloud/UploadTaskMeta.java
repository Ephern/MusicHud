package indi.etern.musichud.beans.user.cloud;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;

public record UploadTaskMeta(
        boolean available,
        String uploadUrl,
        String uploadToken,
        boolean uploadable,
        String songId,
        String resourceId,
        long fileBytes,
        String md5,
        String fileName
) {
    public static UploadTaskMeta ofFailure(String fileName, String md5, long fileBytes) {
        return new UploadTaskMeta(
                false,
                "",
                "",
                false,
                "",
                "",
                fileBytes,
                md5,
                fileName
        );
    }

    public static final ByteBufCodec<UploadTaskMeta> CODEC = ByteBufCodec.composite(
            Codecs.BOOL, UploadTaskMeta::available,
            Codecs.STRING_UTF8, UploadTaskMeta::uploadUrl,
            Codecs.STRING_UTF8, UploadTaskMeta::uploadToken,
            Codecs.BOOL, UploadTaskMeta::uploadable,
            Codecs.STRING_UTF8, UploadTaskMeta::songId,
            Codecs.STRING_UTF8, UploadTaskMeta::resourceId,
            Codecs.LONG, UploadTaskMeta::fileBytes,
            Codecs.STRING_UTF8, UploadTaskMeta::md5,
            Codecs.STRING_UTF8, UploadTaskMeta::fileName,
            UploadTaskMeta::new
    );
}
