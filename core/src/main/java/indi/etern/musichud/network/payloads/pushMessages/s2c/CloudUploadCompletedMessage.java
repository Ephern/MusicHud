package indi.etern.musichud.network.payloads.pushMessages.s2c;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.CommonRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.network.INetworkRegister;
import indi.etern.musichud.network.NetworkReceiver;
import indi.etern.musichud.network.payloads.S2CPayload;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.CloudUploadUpdateNotifier;

/**
 * Pushed when the server finished publishing an uploaded file to the cloud drive (which can
 * lag far behind the direct upload). {@code songId} is the upload-task id from the token
 * request and correlates the message with the client's pending task.
 */
public record CloudUploadCompletedMessage(String songId, String resolvedTrackId,
                                          boolean success, String message) implements S2CPayload {
    public static final ByteBufCodec<CloudUploadCompletedMessage> CODEC = ByteBufCodec.composite(
            Codecs.STRING_UTF8, CloudUploadCompletedMessage::songId,
            Codecs.STRING_UTF8, CloudUploadCompletedMessage::resolvedTrackId,
            Codecs.BOOL, CloudUploadCompletedMessage::success,
            Codecs.STRING_UTF8, CloudUploadCompletedMessage::message,
            CloudUploadCompletedMessage::new
    );

    @RegisterMark
    public static class RegisterImpl implements CommonRegister {
        @Override
        public void register() {
            NetworkReceiver<CloudUploadCompletedMessage> receiver = NetworkReceiver.noop();
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                receiver = (message, context) -> MusicHud.EXECUTOR.execute(() ->
                        CloudUploadUpdateNotifier.notifyCompleted(new CloudUploadUpdateNotifier.Completion(
                                message.songId(), message.resolvedTrackId(), message.success(), message.message())));
            }
            INetworkRegister.getInstance().autoRegisterPayload(CloudUploadCompletedMessage.class, CODEC, receiver);
        }
    }
}
