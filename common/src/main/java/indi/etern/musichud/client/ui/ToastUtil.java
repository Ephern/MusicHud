package indi.etern.musichud.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;

public class ToastUtil {
    static Toast lastToast = null;
    static long lastToastTime = 0;
    static int lastToastDuration = 0;
    //same with ToastManager
    static final int LONG_DELAY = 3500;
    static final int SHORT_DELAY = 2000;

    public static void show(Toast toast) {
        MuiModApi.postToUiThread(() -> {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastToastTime > lastToastDuration && lastToast != null) {
                lastToast.cancel();
            }
            lastToastTime = currentTimeMillis;
            lastToastDuration = switch (toast.getDuration()) {
                case Toast.LENGTH_LONG -> LONG_DELAY;
                case Toast.LENGTH_SHORT -> SHORT_DELAY;
                default -> 0;
            };
            toast.show();
            lastToast = toast;
        });
    }

    public static void show(CharSequence message) {
        MuiModApi.postToUiThread(() -> {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastToastTime > lastToastDuration && lastToast != null) {
                lastToast.cancel();
            }
            lastToastTime = currentTimeMillis;
            lastToastDuration = SHORT_DELAY;
            //noinspection UnstableApiUsage
            Context context = UIManager.getInstance().getDecorView().getContext();
            Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
            toast.show();
            lastToast = toast;
        });
    }
}
