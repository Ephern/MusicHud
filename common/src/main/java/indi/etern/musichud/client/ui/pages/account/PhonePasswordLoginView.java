package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;

public class PhonePasswordLoginView extends LinearLayout implements ILoginView{
    public PhonePasswordLoginView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
    }

    @Override
    public void reset() {

    }

    @Override
    public void errorText(String message) {

    }
}