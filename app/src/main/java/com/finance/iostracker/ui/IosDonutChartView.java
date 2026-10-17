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
import java.util.Locale;

public class IosDonutChartView extends View {

    public static class Slice {
        public final String label;
        public final float amount;
        public final int color;

        public Slice(String label, float amount, String hexColor) {
            this.label = label;
            this.amount = amount;
            this.color = Color.parseColor(hexColor);
        }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private float totalAmount = 0f;

    public IosDonutChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(56f);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setSlices(List<Slice> newSlices) {
        slices.clear();
        totalAmount = 0f;
        for (Slice s : newSlices) {
            if (s.amount > 0) {
                slices.add(s);
                totalAmount += s.amount;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float size = Math.min(w, h) - 80f;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        rectF.set(left, top, left + size, top + size);

        if (totalAmount <= 0) {
            arcPaint.setColor(Color.parseColor("#E5E5EA"));
            canvas.drawArc(rectF, 0, 360, false, arcPaint);
            return;
        }

        float startAngle = -90f;
        for (Slice slice : slices) {
            float sweepAngle = (slice.amount / totalAmount) * 360f;
            arcPaint.setColor(slice.color);
            canvas.drawArc(rectF, startAngle, sweepAngle - 2f, false, arcPaint);
            startAngle += sweepAngle;
        }

        textPaint.setColor(Color.GRAY);
        textPaint.setTextSize(32f);
        canvas.drawText("Total Outflow", w / 2f, h / 2f - 16f, textPaint);

        textPaint.setColor(Color.parseColor("#007AFF"));
        textPaint.setTextSize(46f);
        canvas.drawText(String.format(Locale.FRANCE, "%.0f €", totalAmount), w / 2f, h / 2f + 36f, textPaint);
    }
}