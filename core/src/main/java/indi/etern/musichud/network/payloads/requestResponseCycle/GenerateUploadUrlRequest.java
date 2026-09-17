package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.beans.user.cloud.UploadTaskMeta;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.*;
import indi.etern.musichud.network.payloads.ApiRequestPayload;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GenerateUploadUrlRequest extends ApiRequestPayload {
    public static final ByteBufCodec<GenerateUploadUrlRequest> CODEC = RequestResponseCodecs.withCycleId(
            ByteBufCodec.composite(
                    Codecs.STRING_UTF8, GenerateUploadUrlRequest::getFileName,
                    Codecs.STRING_UTF8, GenerateUploadUrlRequest::getMd5,
                    Codecs.LONG, GenerateUploadUrlRequest::getFileBytes,
                    GenerateUploadUrlRequest::new
            )
    );

    private String fileName;
    private String md5;
    private long fileBytes;

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        public void register() {
            RequestHandlerRegistry.autoRegisterPayload(GenerateUploadUrlRequest.class, CODEC,
                    (request, player) ->
                    {
                        try {
                            UploadTaskMeta uploadTaskMeta = IMusicApiService.getInstance(ApiProvider.NCM)
                                    .requestUploadUrl(request.fileName, request.md5, request.fileBytes, player.getUUID());
                            return ResponseResult.of(new GenerateUploadUrlResponse(MessagedResult.success(uploadTaskMeta)));
                        } catch (Throwable e) {
                            UploadTaskMeta failed = UploadTaskMeta.ofFailure(request.fileName, request.md5, request.fileBytes);
                            return ResponseResult.of(new GenerateUploadUrlResponse(MessagedResult.fail(e.getClass().getName() + ": " + e.getMessage(), failed)));
                        }
                    });
        }
    }
}
