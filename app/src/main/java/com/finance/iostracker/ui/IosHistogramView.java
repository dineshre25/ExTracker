package com.finance.iostracker.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class IosHistogramView extends View {

    public static class BarItem {
        public final String label;
        public final float value;

        public BarItem(String label, float value) {
            this.label = label;
            this.value = value;
        }
    }

    private final List<BarItem> bars = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    private float maxVal = 1f;

    public IosHistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        barPaint.setColor(Color.parseColor("#007AFF"));
        labelPaint.setColor(Color.parseColor("#8E8E93"));
        labelPaint.setTextSize(28f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setBars(List<BarItem> items) {
        bars.clear();
        bars.addAll(items);
        maxVal = 1f;
        for (BarItem b : items) {
            if (b.value > maxVal) maxVal = b.value;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bars.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        float bottomPadding = 50f;
        float topPadding = 30f;
        float availableHeight = h - bottomPadding - topPadding;
        float slotWidth = (float) w / bars.size();
        float barWidth = Math.min(slotWidth * 0.45f, 64f);

        for (int i = 0; i < bars.size(); i++) {
            BarItem item = bars.get(i);
            float centerX = i * slotWidth + slotWidth / 2f;
            float barHeight = (item.value / maxVal) * availableHeight;
            float top = h - bottomPadding - Math.max(barHeight, 12f);

            barRect.set(centerX - barWidth / 2f, top, centerX + barWidth / 2f, h - bottomPadding);
            canvas.drawRoundRect(barRect, 18f, 18f, barPaint);
            canvas.drawText(item.label, centerX, h - 12f, labelPaint);
        }
    }
}