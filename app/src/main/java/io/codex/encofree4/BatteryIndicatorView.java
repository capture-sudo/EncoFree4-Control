package io.codex.encofree4;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Compact phone-style battery with the percentage number inside the body. */
@SuppressLint("ViewConstructor") // Created with a label by the panel, never inflated from XML.
final class BatteryIndicatorView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private final RectF interior = new RectF();
    private final String label;
    private int level = -1;
    private boolean charging;

    BatteryIndicatorView(Context context, String label) {
        super(context);
        this.label = label;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setBattery(-1, false);
    }

    void setBattery(int level, boolean charging) {
        this.level = level < 0 ? -1 : Math.min(level, 100);
        this.charging = this.level >= 0 && charging;
        String value = this.level < 0 ? getContext().getString(R.string.battery_unknown)
                : this.level + "%";
        setContentDescription(label + "，" + value + (this.charging
                ? "，" + getContext().getString(R.string.battery_charging) : ""));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float bodyHeight = batteryHeight();
        paint.setTextSize(sp(10));
        float labelHeight = paint.descent() - paint.ascent();
        setMeasuredDimension(resolveSize(Math.round(dp(78)), widthMeasureSpec),
                resolveSize(Math.round(dp(10) + bodyHeight + labelHeight), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Center the complete silhouette, including its terminal.
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(sp(12));
        float bodyWidth = Math.max(dp(44), paint.measureText("100") + dp(10));
        float left = (getWidth() - bodyWidth - dp(4)) / 2f;
        body.set(left, dp(2), left + bodyWidth, dp(2) + batteryHeight());
        interior.set(body.left + dp(2.5f), body.top + dp(2.5f),
                body.right - dp(2.5f), body.bottom - dp(2.5f));

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x18FFFFFF);
        canvas.drawRoundRect(body, dp(6), dp(6), paint);
        paint.setColor(0x80FFFFFF);
        canvas.drawRoundRect(body.right + dp(1.5f), body.centerY() - dp(4),
                body.right + dp(4), body.centerY() + dp(4), dp(1), dp(1), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(0x99FFFFFF);
        canvas.drawRoundRect(body, dp(6), dp(6), paint);
        paint.setStyle(Paint.Style.FILL);

        float fillRight = interior.left + interior.width() * Math.max(0, level) / 100f;
        int fillColor = charging ? 0xFF83E4AF : level <= 20 ? 0xFFFF7B7B : 0xFFE6E8EC;
        if (level > 0) {
            int saved = canvas.save();
            canvas.clipRect(interior.left, interior.top, fillRight, interior.bottom);
            paint.setColor(fillColor);
            canvas.drawRoundRect(interior, dp(3.5f), dp(3.5f), paint);
            canvas.restoreToCount(saved);
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(sp(12));
        String number = level < 0 ? "--" : Integer.toString(level);
        float baseline = body.centerY() - (paint.ascent() + paint.descent()) / 2f;
        paint.setColor(level < 0 ? 0x99FFFFFF : 0xFFF5F5F5);
        canvas.drawText(number, body.centerX(), baseline, paint);
        if (level > 0) {
            // Keep digits legible on both the light fill and the dark empty portion.
            int saved = canvas.save();
            canvas.clipRect(interior.left, interior.top, fillRight, interior.bottom);
            paint.setColor(0xFF202329);
            canvas.drawText(number, body.centerX(), baseline, paint);
            canvas.restoreToCount(saved);
        }

        paint.setTextSize(sp(10));
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(charging ? 0xFF83E4AF : 0xB3FFFFFF);
        canvas.drawText(label, getWidth() / 2f, body.bottom + dp(5) - paint.ascent(), paint);
    }

    private float batteryHeight() {
        paint.setTextSize(sp(12));
        return Math.max(dp(24), paint.descent() - paint.ascent() + dp(6));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
