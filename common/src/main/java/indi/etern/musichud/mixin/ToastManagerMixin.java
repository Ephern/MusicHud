package indi.etern.musichud.mixin;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Core;
import icyllis.modernui.util.Log;
import icyllis.modernui.view.WindowManager;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.Toast;
import icyllis.modernui.widget.ToastManager;
import org.slf4j.Marker;
import org.spongepowered.asm.mixin.*;

import java.util.ArrayDeque;

@SuppressWarnings({"UnstableApiUsage", "rawtypes"})
@Mixin(value = ToastManager.class, remap = false)
public abstract class ToastManagerMixin {
    @Final
    @Shadow
    private ArrayDeque mToastQueue = new ArrayDeque();

    @Shadow
    private Toast mCurrentToastShown;

    @Final
    @Shadow
    private Runnable mDurationReached;

    @Final
    @Shadow
    private WindowManager mWindowManager;

    @Final
    @Shadow
    private TextView mTextView;

    @Final
    @Shadow
    static Marker MARKER;

    @Unique
    private Object music_hud$getToastLocked(@NonNull Toast token) {
        //noinspection SynchronizeOnNonFinalField
        synchronized (mToastQueue) {
            for (Object r : mToastQueue) {
                if (((ToastRecordAccessor) r).getToken() == token) {
                    return r;
                }
            }
        }
        return null;
    }

    @Shadow
    private void showNextToastLocked() {
        throw new AssertionError();
    }

    @SuppressWarnings("OverwriteAuthorRequired")
    @Overwrite(remap = false)
    private void onDurationReached() {
        //noinspection SynchronizeOnNonFinalField
        synchronized (mToastQueue) {
            if (mCurrentToastShown != null) {
                Object record = music_hud$getToastLocked(mCurrentToastShown);
                mCurrentToastShown = null;
                if (record != null) {
                    music_hud$cancelToastLocked(record, true);
                }
            }
        }
    }

    @Unique
    private void music_hud$cancelToastLocked(@NonNull Object record, boolean fromCallback) {
        boolean hide = fromCallback;

        if (((ToastRecordAccessor) record).getToken() == mCurrentToastShown) {
            assert !fromCallback;
            Core.getUiHandlerAsync().removeCallbacks(mDurationReached);
            mCurrentToastShown = null;
            hide = true;
        }

        if (hide) {
            mWindowManager.removeView(mTextView);
        }
        mToastQueue.remove(record);

        if (!mToastQueue.isEmpty()) {
            showNextToastLocked();
        }
    }

    @SuppressWarnings("OverwriteAuthorRequired")
    @Overwrite(remap = false)
    public void cancelToast(@NonNull Toast token) {
        //noinspection SynchronizeOnNonFinalField
        synchronized (mToastQueue) {
            Object r = music_hud$getToastLocked(token);
            if (r != null) {
                music_hud$cancelToastLocked(r, false);
            } else {
                Log.LOGGER.warn(MARKER, "Toast already cancelled. token={}", token);
            }
        }
    }
}
