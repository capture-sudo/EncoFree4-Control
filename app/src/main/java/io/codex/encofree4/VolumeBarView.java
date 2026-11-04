package io.codex.encofree4;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

final class VolumeBarView extends View {
    interface OnVolumeChangeListener {
        void onVolumeChanged(float fraction);

        void onVolumeChangeFinished();
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackBounds = new RectF();
    private final Path trackClip = new Path();
    private final float trackHeight;
    private final float cornerRadius;

    private OnVolumeChangeListener changeListener;
    private float fraction;
    private boolean dragging;

    VolumeBarView(Context context) {
        super(context);
        trackHeight = dp(32);
        cornerRadius = dp(12);

        trackPaint.setColor(Color.argb(38, 255, 255, 255));
        trackPaint.setStyle(Paint.Style.FILL);

        progressPaint.setColor(Color.WHITE);
        progressPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.argb(38, 255, 255, 255));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1));

        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.volume));
    }

    void setOnVolumeChangeListener(OnVolumeChangeListener listener) {
        changeListener = listener;
    }

    void setSystemVolume(int volume, int maximum) {
        if (dragging || maximum <= 0) {
            return;
        }
        setFractionSilently(volume / (float) maximum);
    }

    boolean isDragging() {
        return dragging;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = Math.round(dp(48));
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateTrackBounds();
        if (trackBounds.isEmpty()) {
            return;
        }
        canvas.drawRoundRect(trackBounds, cornerRadius, cornerRadius, trackPaint);
        if (fraction > 0f) {
            // Clip the fill to the rounded outside edge; the internal level edge stays straight.
            int saved = canvas.save();
            canvas.clipPath(trackClip);
            canvas.drawRect(trackBounds.left, trackBounds.top,
                    trackBounds.left + trackBounds.width() * fraction,
                    trackBounds.bottom, progressPaint);
            canvas.restoreToCount(saved);
        }
        canvas.drawRoundRect(trackBounds, cornerRadius, cornerRadius, borderPaint);
    }

    private void updateTrackBounds() {
        float height = Math.min(trackHeight,
                Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom() - dp(2)));
        float centerY = getPaddingTop()
                + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
        trackBounds.set(getPaddingLeft() + dp(1), centerY - height / 2f,
                getWidth() - getPaddingRight() - dp(1), centerY + height / 2f);
        trackClip.reset();
        trackClip.addRoundRect(trackBounds, cornerRadius, cornerRadius, Path.Direction.CW);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                setFractionFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    setFractionFromTouch(event.getX());
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    setFractionFromTouch(event.getX());
                    finishDrag();
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    finishDrag();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return SeekBar.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT,
                0f,
                1f,
                fraction
        ));
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            changeVolumeByAccessibility(fraction + 0.05f);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            changeVolumeByAccessibility(fraction - 0.05f);
            return true;
        }
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()
                && arguments != null) {
            float value = arguments.getFloat(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
                    fraction
            );
            changeVolumeByAccessibility(value);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    private void changeVolumeByAccessibility(float requestedFraction) {
        setFractionSilently(requestedFraction);
        if (changeListener != null) {
            changeListener.onVolumeChanged(fraction);
            changeListener.onVolumeChangeFinished();
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    private void setFractionFromTouch(float touchX) {
        float left = getPaddingLeft() + dp(1);
        float right = getWidth() - getPaddingRight() - dp(1);
        if (right <= left) {
            return;
        }
        setFractionSilently((touchX - left) / (right - left));
        if (changeListener != null) {
            changeListener.onVolumeChanged(fraction);
        }
    }

    private void finishDrag() {
        dragging = false;
        if (changeListener != null) {
            changeListener.onVolumeChangeFinished();
        }
    }

    private void setFractionSilently(float requestedFraction) {
        float clamped = Math.max(0f, Math.min(1f, requestedFraction));
        if (Math.abs(clamped - fraction) < 0.0001f) {
            return;
        }
        fraction = clamped;
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
