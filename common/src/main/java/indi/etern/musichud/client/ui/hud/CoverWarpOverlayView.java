package indi.etern.musichud.client.ui.hud;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.render.CoverWarpSettings;
import org.jetbrains.annotations.NotNull;

public class CoverWarpOverlayView extends View {
    private static final int HIT_DP = 24;
    private static final int POINT_RADIUS_DP = 5;

    private final CoverWarpSettings settings = CoverWarpSettings.getInstance();
    private final Paint gridPaint = new Paint();
    private final Paint pointPaint = new Paint();

    private int dragX = -1;
    private int dragY = -1;
    private float dragStartPointerX;
    private float dragStartPointerY;
    private float dragStartOffsetX;
    private float dragStartOffsetY;

    public CoverWarpOverlayView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(false);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor(Theme.HUD_EDIT_BORDER_COLOR);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(Theme.PRIMARY_COLOR);
    }

    public void refresh() {
        invalidate();
    }

    @Override
    protected void onDraw(@NotNull Canvas canvas) {
        super.onDraw(canvas);
        int n = settings.getGridSize();
        float width = getWidth();
        float height = getHeight();
        if (n < 2 || width <= 0 || height <= 0) {
            return;
        }

        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (x + 1 < n) {
                    canvas.drawLine(pointX(x, y), pointY(x, y),
                            pointX(x + 1, y), pointY(x + 1, y), gridPaint);
                }
                if (y + 1 < n) {
                    canvas.drawLine(pointX(x, y), pointY(x, y),
                            pointX(x, y + 1), pointY(x, y + 1), gridPaint);
                }
            }
        }

        float radius = dp(POINT_RADIUS_DP);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                pointPaint.setColor(x == dragX && y == dragY
                        ? 0xFFFFFFFF
                        : Theme.PRIMARY_COLOR);
                canvas.drawCircle(pointX(x, y), pointY(x, y), radius, pointPaint);
            }
        }
    }

    private float pointX(int x, int y) {
        int n = settings.getGridSize();
        float source = x / (float) (n - 1);
        return Math.clamp(source + settings.getOffsetX(x, y), 0f, 1f) * getWidth();
    }

    private float pointY(int x, int y) {
        int n = settings.getGridSize();
        float source = y / (float) (n - 1);
        return Math.clamp(source + settings.getOffsetY(x, y), 0f, 1f) * getHeight();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                int[] hit = hitTest(ev.getX(), ev.getY());
                if (hit == null) {
                    return false;
                }
                dragX = hit[0];
                dragY = hit[1];
                dragStartPointerX = ev.getX();
                dragStartPointerY = ev.getY();
                dragStartOffsetX = settings.getOffsetX(dragX, dragY);
                dragStartOffsetY = settings.getOffsetY(dragX, dragY);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (dragX < 0) {
                    return false;
                }
                int n = settings.getGridSize();
                float width = Math.max(1f, getWidth());
                float height = Math.max(1f, getHeight());
                float sourceX = dragX / (float) (n - 1);
                float sourceY = dragY / (float) (n - 1);
                float deltaX = (ev.getX() - dragStartPointerX) / width;
                float deltaY = (ev.getY() - dragStartPointerY) / height;
                float offsetX = Math.clamp(sourceX + dragStartOffsetX + deltaX, 0f, 1f) - sourceX;
                float offsetY = Math.clamp(sourceY + dragStartOffsetY + deltaY, 0f, 1f) - sourceY;
                settings.setOffset(dragX, dragY, offsetX, offsetY);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragX >= 0) {
                    dragX = -1;
                    dragY = -1;
                    invalidate();
                    return true;
                }
                return false;
            }
            default -> {
                return super.onTouchEvent(ev);
            }
        }
    }

    private int[] hitTest(float x, float y) {
        int n = settings.getGridSize();
        float threshold = dp(HIT_DP);
        float bestDistance = threshold * threshold;
        int[] best = null;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float dx = x - pointX(i, j);
                float dy = y - pointY(i, j);
                float distance = dx * dx + dy * dy;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = new int[]{i, j};
                }
            }
        }
        return best;
    }
}
