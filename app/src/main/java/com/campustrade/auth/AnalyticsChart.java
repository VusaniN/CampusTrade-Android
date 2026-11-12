package com.campustrade.auth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small chart drawn by the app, so no chart library is needed. */
public final class AnalyticsChart extends View {

    public enum Mode { BAR, LINE, HORIZONTAL_BAR }

    public static final class Entry {
        final String label;
        final float value;
        public Entry(String label, float value) {
            this.label = label;
            this.value = value;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private Mode mode = Mode.BAR;
    private int accent = Color.rgb(226, 58, 29);
    private int textColor = Color.rgb(25, 25, 24);
    private int mutedColor = Color.rgb(114, 114, 109);
    private int gridColor = Color.rgb(220, 217, 208);

    public AnalyticsChart(Context context) {
        super(context);
        setMinimumHeight(dp(190));
    }

    public void setData(List<Entry> data, Mode mode, int accent, int textColor, int mutedColor) {
        entries.clear();
        if (data != null) entries.addAll(data);
        this.mode = mode;
        this.accent = accent;
        this.textColor = textColor;
        this.mutedColor = mutedColor;
        this.gridColor = mutedColor;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(dp(10));

        float max = 0;
        for (Entry entry : entries) max = Math.max(max, entry.value);
        if (entries.isEmpty() || max <= 0) {
            paint.setColor(mutedColor);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No data for this period", width / 2f, height / 2f, paint);
            return;
        }

        if (mode == Mode.HORIZONTAL_BAR) {
            drawHorizontalBars(canvas, paint, max);
            return;
        }

        float left = dp(44);
        float right = width - dp(10);
        float top = dp(12);
        float bottom = height - dp(32);
        float plotWidth = Math.max(1, right - left);
        float plotHeight = Math.max(1, bottom - top);

        max *= 1.15f;

        // Grid lines and values.
        paint.setStrokeWidth(dp(1));
        paint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float y = bottom - (plotHeight * i / 4f);
            paint.setColor(gridColor);
            paint.setAlpha(i == 0 ? 120 : 55);
            canvas.drawLine(left, y, right, y, paint);
            paint.setAlpha(255);
            paint.setColor(mutedColor);
            canvas.drawText(shortValue(max * i / 4f), left - dp(5), y + dp(4), paint);
        }

        if (mode == Mode.BAR || entries.size() == 1) drawBars(canvas, left, right, top, bottom, max, paint);
        else drawLine(canvas, left, right, top, bottom, max, paint);

        // Draw only labels that fit, and shorten long ones.
        paint.setColor(mutedColor);
        paint.setTextAlign(Paint.Align.CENTER);
        float baseGap = mode == Mode.BAR ? dp(44) : dp(54);
        int maxLabels = Math.max(2, (int) (plotWidth / baseGap));
        int step = Math.max(1, (int) Math.ceil(entries.size() / (float) maxLabels));
        for (int i = 0; i < entries.size(); i += step) {
            float x = entries.size() == 1 ? (left + right) / 2f
                    : mode == Mode.BAR
                    ? left + (plotWidth * (i + 0.5f) / entries.size())
                    : left + plotWidth * i / (entries.size() - 1f);
            float gap = mode == Mode.BAR
                    ? (plotWidth / entries.size()) * step
                    : (plotWidth / Math.max(1, entries.size() - 1)) * step;
            String label = ellipsize(paint, entries.get(i).label, Math.max(dp(28), gap - dp(6)));
            x = Math.max(left + dp(14), Math.min(right - dp(14), x));
            canvas.drawText(label, x, height - dp(8), paint);
        }
    }

    // Horizontal bars keep long category names readable.
    // Used by the admin category breakdown.
    private void drawHorizontalBars(Canvas canvas, Paint paint, float max) {
        paint.setTextSize(dp(10));
        float labelWidth = Math.min(dp(108), getWidth() * 0.38f);
        float barLeft = labelWidth + dp(8);
        float barRight = getWidth() - dp(44);
        float available = Math.max(dp(10), barRight - barLeft);
        float rowHeight = (getHeight() - dp(8)) / (float) entries.size();
        float barHeight = Math.min(dp(16), rowHeight * 0.52f);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float centerY = dp(4) + rowHeight * (i + 0.5f);
            paint.setColor(mutedColor);
            paint.setTextAlign(Paint.Align.LEFT);
            String label = ellipsize(paint, entry.label, labelWidth - dp(6));
            canvas.drawText(label, 0, centerY + dp(4), paint);

            RectF track = new RectF(barLeft, centerY - barHeight / 2f,
                    barRight, centerY + barHeight / 2f);
            paint.setColor(gridColor);
            paint.setAlpha(50);
            canvas.drawRoundRect(track, dp(4), dp(4), paint);
            paint.setAlpha(255);

            float valueWidth = available * Math.max(0, entry.value) / max;
            if (valueWidth > 0) {
                paint.setColor(accent);
                RectF bar = new RectF(barLeft, centerY - barHeight / 2f,
                        barLeft + Math.max(dp(3), valueWidth), centerY + barHeight / 2f);
                canvas.drawRoundRect(bar, dp(4), dp(4), paint);
            }
            paint.setColor(mutedColor);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(shortValue(entry.value), getWidth() - dp(4), centerY + dp(4), paint);
        }
    }

    // Shorten a label and add ... if it is too long.
    private String ellipsize(Paint paint, String text, float maxWidth) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 1 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) end--;
        return text.substring(0, end) + ellipsis;
    }

    private void drawBars(Canvas canvas, float left, float right, float top, float bottom,
                          float max, Paint paint) {
        float slot = Math.max(1, (right - left) / entries.size());
        float barWidth = Math.max(dp(6), slot * 0.56f);
        paint.setColor(accent);
        for (int i = 0; i < entries.size(); i++) {
            float value = entries.get(i).value;
            float barHeight = (bottom - top) * (value / max);
            float x = left + slot * i + (slot - barWidth) / 2f;
            RectF rect = new RectF(x, bottom - barHeight, x + barWidth, bottom);
            canvas.drawRoundRect(rect, dp(3), dp(3), paint);
        }
    }

    private void drawLine(Canvas canvas, float left, float right, float top, float bottom,
                          float max, Paint paint) {
        float plotWidth = right - left;
        float plotHeight = bottom - top;
        Path path = new Path();
        for (int i = 0; i < entries.size(); i++) {
            float x = entries.size() == 1 ? (left + right) / 2f
                    : left + plotWidth * i / (entries.size() - 1f);
            float y = bottom - plotHeight * (entries.get(i).value / max);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        paint.setColor(accent);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.5f));
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < entries.size(); i++) {
            float x = entries.size() == 1 ? (left + right) / 2f
                    : left + plotWidth * i / (entries.size() - 1f);
            float y = bottom - plotHeight * (entries.get(i).value / max);
            canvas.drawCircle(x, y, dp(3.5f), paint);
        }
    }

    private String shortValue(float value) {
        if (value >= 1000000) return String.format(Locale.ROOT, "%.1fM", value / 1000000f);
        if (value >= 1000) return String.format(Locale.ROOT, "%.0fk", value / 1000f);
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}