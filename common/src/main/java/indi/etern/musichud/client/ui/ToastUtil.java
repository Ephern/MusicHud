package indi.etern.musichud.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;

public class ToastUtil {
    static Toast lastToast = null;

    public static void show(Toast toast) {
        MuiModApi.postToUiThread(() -> {
            if (lastToast != null) {
                lastToast.cancel();
            }
            toast.show();
            lastToast = toast;
        });
    }

    public static void show(CharSequence message) {
        MuiModApi.postToUiThread(() -> {
            //noinspection UnstableApiUsage
            Context context = UIManager.getInstance().getDecorView().getContext();
            if (lastToast != null) {
                lastToast.cancel();
            }
            Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
            toast.show();
            lastToast = toast;
        });
    }
}
