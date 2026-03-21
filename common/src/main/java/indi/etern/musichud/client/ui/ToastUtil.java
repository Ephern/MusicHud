package indi.etern.musichud.client.ui;

import icyllis.modernui.widget.Toast;

public class ToastUtil {
//    static Toast lastToast = null;

    public static void show(Toast toast) {
        // waiting for ModernUI to fix
//        MuiModApi.postToUiThread(() -> {
//            if (lastToast != null) {
//                lastToast.cancel();
//            }
            toast.show();
//            lastToast = toast;
//        });
    }
}
