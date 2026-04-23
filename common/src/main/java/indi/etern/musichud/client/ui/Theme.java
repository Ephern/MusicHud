package indi.etern.musichud.client.ui;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class Theme {
    public static final int BASE_BACKGROUND_COLOR = 0xB0000000;

    public static final int PRIMARY_COLOR = 0xFFE0BFB7;

    public static final int EMPHASIZE_TEXT_COLOR = 0xFFFFFFFF;
    public static final int NORMAL_TEXT_COLOR = 0xFFE0E0E0;
    public static final int SECONDARY_TEXT_COLOR = 0xFFA0A0A0;
    public static final int FADE_TEXT_COLOR = 0xFF606060;

    public static final int ERROR_TEXT_COLOR = 0xFFFF4F4F;

    public static final int GHOST_BUTTON_BACKGROUND = 0x00FFFFFF;
    public static final int GHOST_BUTTON_BACKGROUND_PRESSED = 0x05FFFFFF;
    public static final int GHOST_BUTTON_BACKGROUND_HOVERED = 0x07FFFFFF;
    public static final int GHOST_BUTTON_BACKGROUND_CHECKED = 0x08FFFFFF;

    public static final ColorStateList GHOST_BUTTON_STATES = new ColorStateList(
            new int[][]{
                    new int[]{R.attr.state_pressed},
                    new int[]{R.attr.state_checked},
                    new int[]{R.attr.state_hovered},
                    StateSet.WILD_CARD
            },
            new int[]{
                    Theme.GHOST_BUTTON_BACKGROUND_PRESSED,
                    Theme.GHOST_BUTTON_BACKGROUND_CHECKED,
                    Theme.GHOST_BUTTON_BACKGROUND_HOVERED,
                    Theme.GHOST_BUTTON_BACKGROUND
            }
    );

    public static final ColorStateList ITEM_RIPPLE_COLOR_STATES = new ColorStateList(
            new int[][]{
                    new int[]{R.attr.state_pressed},
                    new int[]{R.attr.state_focused},
                    new int[]{R.attr.state_hovered},
                    StateSet.WILD_CARD
            },
            new int[]{
                    0x0A000000,
                    0x0A000000,
                    0x00000000,
                    0x08000000,
            }
    );

    public static final int TEXT_SIZE_SMALL = 10;
    public static final int TEXT_SIZE_NORMAL = 12;
    public static final int TEXT_SIZE_LARGE = 15;
    public static final int TEXT_SIZE_LARGER = 18;
    public static final int MAIN_LYRIC_SIZE = 24;
    public static final int SUB_LYRIC_SIZE = 15;
    public static final float EMPHASIZE_LYRIC_ALPHA = 0.9f;
    public static final float FADE_LYRIC_ALPHA = 0.3f;
    public static final int EMPHASIZE_LYRIC_COLOR = 0xD6FFFFFF;
    public static final int GLOW_LYRIC_COLOR = 0xFFFFFFFF;
    public static final int FADE_LYRIC_COLOR = 0x4BFFFFFF;
    public static final int HUD_EMPHASIZE_COLOR = 0xD0FFFFFF;
    public static final int HUD_FADE_COLOR = 0x70FFFFFF;
    public static final int HUD_PROGRESS_LEFT = 0x00000000;
    public static final int HUD_PROGRESS_CURRENT = 0x50FFFFFF;
    public static final int HUD_PROGRESS_BACKGROUND = 0x32FFFFFF;

    public static TextView getNotificationTextView(Context context, boolean enabled) {
        TextView textView = new TextView(context);
        textView.setTextSize(TEXT_SIZE_NORMAL);
        textView.setTextColor(EMPHASIZE_TEXT_COLOR);
        if (enabled) {
            if (MusicHud.getStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                textView.setText(I18n.get(MusicHud.MOD_ID + ".text.notConnected"));
            } else if (MusicHud.getStatus() == MusicHud.ConnectStatus.INCAPABLE) {
                textView.setText(I18n.get(MusicHud.MOD_ID + ".text.incapableWithServer"));
            }
        } else {
            textView.setText(I18n.get(MusicHud.MOD_ID + ".text.disabled"));
        }
        textView.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        return textView;
    }
}
