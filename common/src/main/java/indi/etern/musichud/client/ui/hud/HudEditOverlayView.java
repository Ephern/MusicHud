package indi.etern.musichud.client.ui.hud;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.utils.ui.HudLayoutMath;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

public class HudEditOverlayView extends View {
    private static final int OFFSET_LIMIT = 1920;
    private static final int MIN_HEIGHT = 16;
    private static final int MAX_HEIGHT = 256;
    private static final int MAX_WIDTH = 800;
    private static final int HIT_DP = 8;
    private static final int HANDLE_VISUAL_DP = 8;
    private static final int EDGE_MARKER_LENGTH_DP = 24;
    private static final int EDGE_MARKER_THICKNESS_DP = 3;
    private static final int BORDER_THICKNESS_DP = 1;

    private static final int[] PRESSED_STATE = {R.attr.state_pressed};
    private static final int[] HOVERED_STATE = {R.attr.state_hovered};
    private static final int[] EMPTY_STATE = {};

    private final ClientConfig clientConfig = ClientConfig.getInstance();
    private final HudRendererManager hudRendererManager = HudRendererManager.getInstance();
    private final Paint borderPaint = new Paint();
    private final Paint handlePaint = new Paint();

    private Handle hoveredHandle = Handle.NONE;
    private Handle activeHandle = Handle.NONE;
    private float dragStartPointerX;
    private float dragStartPointerY;
    private RectF dragStartRect;
    @Setter
    private Runnable onConfigChanged;

    public HudEditOverlayView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(false);
    }

    public void refresh() {
        invalidate();
    }

    public RectF getHudGuiRect() {
        return currentGuiRect();
    }

    private RectF currentGuiRect() {
        return HudLayoutMath.computeGuiRect(scaledWidth(), scaledHeight(),
                HorizontalAlign.valueOf(clientConfig.getHudHorizontalPosition()),
                VerticalAlign.valueOf(clientConfig.getHudVerticalPosition()),
                clientConfig.getHudOffsetX(), clientConfig.getHudOffsetY(),
                clientConfig.getHudWidth(), clientConfig.getHudHeight());
    }

    private RectF hudRectPhysical() {
        RectF gui = currentGuiRect();
        float scale = guiScale();
        return new RectF(gui.left * scale, gui.top * scale, gui.right * scale, gui.bottom * scale);
    }

    private static int scaledWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int scaledHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static float guiScale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dragStartPointerX = ev.getX();
                dragStartPointerY = ev.getY();
                dragStartRect = new RectF(currentGuiRect());
                activeHandle = hitTest(ev.getX(), ev.getY());
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (activeHandle != Handle.NONE && dragStartRect != null) {
                    float scale = guiScale();
                    float dx = (ev.getX() - dragStartPointerX) / scale;
                    float dy = (ev.getY() - dragStartPointerY) / scale;
                    applyDrag(dragStartRect, activeHandle, dx, dy);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle != Handle.NONE) {
                    activeHandle = Handle.NONE;
                    dragStartRect = null;
                    clientConfig.save();
                    invalidate();
                }
                return true;
            }
            default -> {
                return super.onTouchEvent(ev);
            }
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                Handle h = activeHandle != Handle.NONE ? activeHandle : hitTest(ev.getX(), ev.getY());
                if (h != hoveredHandle) {
                    hoveredHandle = h;
                    invalidate();
                }
            }
            case MotionEvent.ACTION_HOVER_EXIT -> {
                if (hoveredHandle != Handle.NONE) {
                    hoveredHandle = Handle.NONE;
                    invalidate();
                }
            }
            default -> {
            }
        }
        return super.onHoverEvent(ev);
    }

    private Handle hitTest(float x, float y) {
        RectF r = hudRectPhysical();
        if (r.width() <= 0 || r.height() <= 0) {
            return Handle.NONE;
        }
        int hit = dp(HIT_DP);
        float left = r.left;
        float right = r.right;
        float top = r.top;
        float bottom = r.bottom;
        boolean xNearLeft = Math.abs(x - left) <= hit;
        boolean xNearRight = Math.abs(x - right) <= hit;
        boolean yNearTop = Math.abs(y - top) <= hit;
        boolean yNearBottom = Math.abs(y - bottom) <= hit;
        if (xNearLeft && yNearTop) return Handle.TOP_LEFT;
        if (xNearRight && yNearTop) return Handle.TOP_RIGHT;
        if (xNearLeft && yNearBottom) return Handle.BOTTOM_LEFT;
        if (xNearRight && yNearBottom) return Handle.BOTTOM_RIGHT;
        if (yNearTop && x >= left - hit && x <= right + hit) return Handle.TOP;
        if (yNearBottom && x >= left - hit && x <= right + hit) return Handle.BOTTOM;
        if (xNearLeft && y >= top - hit && y <= bottom + hit) return Handle.LEFT;
        if (xNearRight && y >= top - hit && y <= bottom + hit) return Handle.RIGHT;
        if (x >= left && x <= right && y >= top && y <= bottom) return Handle.MOVE;
        return Handle.NONE;
    }

    private void applyDrag(RectF startRect, Handle handle, float dx, float dy) {
        RectF rect = new RectF(startRect);
        switch (handle) {
            case MOVE -> rect.offset(dx, dy);
            case LEFT -> rect.left = startRect.left + dx;
            case RIGHT -> rect.right = startRect.right + dx;
            case TOP -> rect.top = startRect.top + dy;
            case BOTTOM -> rect.bottom = startRect.bottom + dy;
            case TOP_LEFT -> {
                rect.left = startRect.left + dx;
                rect.top = startRect.top + dy;
            }
            case TOP_RIGHT -> {
                rect.right = startRect.right + dx;
                rect.top = startRect.top + dy;
            }
            case BOTTOM_LEFT -> {
                rect.left = startRect.left + dx;
                rect.bottom = startRect.bottom + dy;
            }
            case BOTTOM_RIGHT -> {
                rect.right = startRect.right + dx;
                rect.bottom = startRect.bottom + dy;
            }
            default -> {
                return;
            }
        }

        int newHeight;
        int newWidth;
        if (handle == Handle.MOVE) {
            newHeight = clientConfig.getHudHeight();
            newWidth = clientConfig.getHudWidth();
        } else {
            newHeight = clampStep(rect.height(), 2, MIN_HEIGHT, MAX_HEIGHT);
            newWidth = clampStep(rect.width(), 4, newHeight, MAX_WIDTH);
            // rebuild the rect from the anchored (fixed) edges so the drag feels stable
            switch (handle) {
                case LEFT, TOP_LEFT, BOTTOM_LEFT -> rect.left = rect.right - newWidth;
                case RIGHT, TOP_RIGHT, BOTTOM_RIGHT -> rect.right = rect.left + newWidth;
                default -> {
                }
            }
            switch (handle) {
                case TOP, TOP_LEFT, TOP_RIGHT -> rect.top = rect.bottom - newHeight;
                case BOTTOM, BOTTOM_LEFT, BOTTOM_RIGHT -> rect.bottom = rect.top + newHeight;
                default -> {
                }
            }
        }

        HorizontalAlign ha = HorizontalAlign.valueOf(clientConfig.getHudHorizontalPosition());
        VerticalAlign va = VerticalAlign.valueOf(clientConfig.getHudVerticalPosition());
        HudLayoutMath.Values values = HudLayoutMath.reverseGuiRect(scaledWidth(), scaledHeight(), ha, va,
                rect.left, rect.top, rect.right, rect.bottom);
        int newOffsetX = Math.clamp(values.offsetX(), -OFFSET_LIMIT, OFFSET_LIMIT);
        int newOffsetY = Math.clamp(values.offsetY(), -OFFSET_LIMIT, OFFSET_LIMIT);
        int newRadius = Math.min(clientConfig.getHudCornerRadius(), newHeight / 2);

        boolean changed = false;
        if (newOffsetX != clientConfig.getHudOffsetX()) {
            clientConfig.setHudOffsetX(newOffsetX);
            changed = true;
        }
        if (newOffsetY != clientConfig.getHudOffsetY()) {
            clientConfig.setHudOffsetY(newOffsetY);
            changed = true;
        }
        if (newWidth != clientConfig.getHudWidth()) {
            clientConfig.setHudWidth(newWidth);
            changed = true;
        }
        if (newHeight != clientConfig.getHudHeight()) {
            clientConfig.setHudHeight(newHeight);
            changed = true;
        }
        if (newRadius != clientConfig.getHudCornerRadius()) {
            clientConfig.setHudCornerRadius(newRadius);
            changed = true;
        }
        if (!changed) {
            return;
        }

        hudRendererManager.setBaseLayout(new Layout(newOffsetX, newOffsetY, newWidth, newHeight, newRadius, ha, va));
        hudRendererManager.refreshStyle();
        if (onConfigChanged != null) {
            onConfigChanged.run();
        }
    }

    private static int clampStep(float value, int step, int min, int max) {
        int stepped = Math.round(value / step) * step;
        return Math.clamp(stepped, min, max);
    }

    @Override
    protected void onDraw(@NotNull Canvas canvas) {
        super.onDraw(canvas);
        RectF r = hudRectPhysical();
        if (r.width() <= 0 || r.height() <= 0) {
            return;
        }
        float radius = clientConfig.getHudCornerRadius() * guiScale();

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(BORDER_THICKNESS_DP));
        borderPaint.setColor(Theme.HUD_EDIT_BORDER_COLOR);
        canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, radius, borderPaint);

        drawHandleMarker(canvas, Handle.TOP_LEFT, r.left, r.top);
        drawHandleMarker(canvas, Handle.TOP_RIGHT, r.right, r.top);
        drawHandleMarker(canvas, Handle.BOTTOM_LEFT, r.left, r.bottom);
        drawHandleMarker(canvas, Handle.BOTTOM_RIGHT, r.right, r.bottom);
        drawHandleMarker(canvas, Handle.TOP, (r.left + r.right) / 2, r.top);
        drawHandleMarker(canvas, Handle.BOTTOM, (r.left + r.right) / 2, r.bottom);
        drawHandleMarker(canvas, Handle.LEFT, r.left, (r.top + r.bottom) / 2);
        drawHandleMarker(canvas, Handle.RIGHT, r.right, (r.top + r.bottom) / 2);
    }

    private void drawHandleMarker(Canvas canvas, Handle handle, float cx, float cy) {
        int color = Theme.HUD_HANDLE_STATES.getColorForState(handleState(handle),
                Theme.HUD_HANDLE_STATES.getDefaultColor());
        handlePaint.setColor(color);
        switch (handle) {
            case TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT -> {
                float half = dp(HANDLE_VISUAL_DP) / 2f;
                handlePaint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(cx - half, cy - half, cx + half, cy + half, half, handlePaint);
            }
            case TOP, BOTTOM -> {
                float length = dp(EDGE_MARKER_LENGTH_DP);
                handlePaint.setStyle(Paint.Style.STROKE);
                handlePaint.setStrokeWidth(dp(EDGE_MARKER_THICKNESS_DP));
                handlePaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx - length / 2, cy, cx + length / 2, cy, handlePaint);
            }
            case LEFT, RIGHT -> {
                float length = dp(EDGE_MARKER_LENGTH_DP);
                handlePaint.setStyle(Paint.Style.STROKE);
                handlePaint.setStrokeWidth(dp(EDGE_MARKER_THICKNESS_DP));
                handlePaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx, cy - length / 2, cx, cy + length / 2, handlePaint);
            }
            default -> {
            }
        }
    }

    private int[] handleState(Handle handle) {
        if (activeHandle == handle) {
            return PRESSED_STATE;
        }
        if (hoveredHandle == handle) {
            return HOVERED_STATE;
        }
        return EMPTY_STATE;
    }

    private enum Handle {
        NONE, MOVE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }
}
