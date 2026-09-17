package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.network.payloads.pushMessages.s2c.CloudUploadCompletedMessage;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sent by the client after it finished a client-direct upload. The server only needs to
 * forward this to the NCM API to import the uploaded resource into the user's cloud drive.
 * {@code songId}/{@code resourceId} come from the upload-task credentials (not the Netease
 * track id, which does not exist yet at upload time).
 */
@Getter
@AllArgsConstructor
public class CompleteCloudUploadRequest extends ApiRequestPayload {
    public static final ByteBufCodec<CompleteCloudUploadRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8, CompleteCloudUploadRequest::getSongId,
                    Codecs.STRING_UTF8, CompleteCloudUploadRequest::getResourceId,
                    Codecs.STRING_UTF8, CompleteCloudUploadRequest::getMd5,
                    Codecs.STRING_UTF8, CompleteCloudUploadRequest::getFileName,
                    Codecs.STRING_UTF8, CompleteCloudUploadRequest::getSong,
                    Codecs.STRING_UTF8, CompleteCloudUploadRequest::getArtist,
                    Codecs.STRING_UTF8, CompleteCloudUploadRequest::getAlbum,
                    CompleteCloudUploadRequest::new
            )
    );

    private final String songId;
    private final String resourceId;
    private final String md5;
    private final String fileName;
    private final String song;
    private final String artist;
    private final String album;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            // Publish can lag far behind the direct upload and even outlive the client request,
            // so ack immediately and push the result over the S2C channel when it finally settles.
            RequestHandlerRegistry.autoRegisterPayload(CompleteCloudUploadRequest.class, CODEC, (request, player) -> {
                MusicHud.EXECUTOR.execute(() -> {
                    String resolvedTrackId = "";
                    String message = "";
                    boolean success;
                    try {
                        String resolved = IMusicApiService.getInstance(ApiProvider.NCM).completeCloudUpload(
                                request.songId, request.resourceId, request.md5, request.fileName,
                                request.song, request.artist, request.album, player.getUUID());
                        resolvedTrackId = resolved == null ? "" : resolved;
                        success = true;
                    } catch (Throwable e) {
                        success = false;
                        message = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                    try {
                        IServerNetworkService.getInstance().sendToPlayer(player,
                                new CloudUploadCompletedMessage(request.songId, resolvedTrackId, success, message));
                    } catch (Throwable ignored) {
                    }
                });
                return ResponseResult.of(new CompleteCloudUploadResponse(MessagedResult.success("")));
            });
        }
    }
}
