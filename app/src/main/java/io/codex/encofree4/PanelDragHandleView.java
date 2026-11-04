package io.codex.encofree4;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

/** Owns only touches that start on the handle strip, never the volume control. */
@SuppressLint("ViewConstructor") // Created programmatically with its Activity callbacks, never from XML.
final class PanelDragHandleView extends View {
    interface Listener {
        float dismissDistance();
        void onDragStarted();
        void onDrag(float distance);
        void onDragCancelled();
        void onDismiss();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PanelSwipeGesture gesture;
    private final Listener listener;
    private VelocityTracker velocity;
    private int pointer = -1;

    PanelDragHandleView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        gesture = new PanelSwipeGesture(ViewConfiguration.get(context).getScaledTouchSlop(),
                dp(24), dp(900));
        paint.setColor(0x38FFFFFF);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(context.getString(R.string.panel_drag_description));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = (getWidth() - dp(36)) / 2f;
        canvas.drawRoundRect(left, dp(2), left + dp(36), dp(6), dp(2), dp(2), paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            pointer = event.getPointerId(0);
            gesture.begin(event.getRawX(), event.getRawY(), listener.dismissDistance());
            velocity = VelocityTracker.obtain();
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (pointer < 0) {
            return true;
        }
        // Use screen coordinates: translating this view must not change measured finger speed.
        MotionEvent raw = MotionEvent.obtain(event);
        raw.offsetLocation(event.getRawX() - event.getX(), event.getRawY() - event.getY());
        velocity.addMovement(raw);
        raw.recycle();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                boolean wasDragging = gesture.isDragging();
                float distance = gesture.move(event.getRawX(), event.getRawY());
                if (gesture.isDragging()) {
                    if (!wasDragging) {
                        listener.onDragStarted();
                    }
                    listener.onDrag(distance);
                }
                break;
            case MotionEvent.ACTION_UP:
                boolean dragged = gesture.isDragging();
                velocity.computeCurrentVelocity(1000);
                boolean dismiss = gesture.end(event.getRawX(), event.getRawY(),
                        velocity.getYVelocity(pointer));
                releaseTracker();
                getParent().requestDisallowInterceptTouchEvent(false);
                if (dismiss) {
                    listener.onDismiss();
                } else if (dragged) {
                    listener.onDragCancelled();
                } else {
                    performClick();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                cancelGesture();
                break;
            default:
                break;
        }
        return true;
    }

    void cancelGesture() {
        boolean dragged = gesture.isDragging();
        gesture.cancel();
        releaseTracker();
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        if (dragged) {
            listener.onDragCancelled();
        }
    }

    private void releaseTracker() {
        pointer = -1;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS);
        info.setDismissable(true);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_DISMISS) {
            listener.onDismiss();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelGesture();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
