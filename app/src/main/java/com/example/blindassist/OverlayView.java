package com.example.blindassist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private final Paint boxPaint;
    private final Paint textPaint;
    private final Paint bgPaint;
    private final Paint centerPaint;
    private final Paint scanPaint;
    private List<DetectionResult> results = new ArrayList<>();
    private int imageWidth = 1;
    private int imageHeight = 1;
    private float scanY = 0f;
    private boolean scanDown = true;

    private final int[] COLORS = {
            0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
            0xFFFFFF00, 0xFF00FFFF, 0xFFFF00FF,
            0xFFFF6600, 0xFF9900FF
    };

    public static class DetectionResult {
        public final String label;
        public final float confidence;
        public final RectF boundingBox;
        public final String distance;
        public final String direction;

        public DetectionResult(String label, float confidence,
                               RectF boundingBox, String distance, String direction) {
            this.label = label;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
            this.distance = distance;
            this.direction = direction;
        }
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setTextSize(32f);
        textPaint.setAntiAlias(true);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAntiAlias(true);

        centerPaint = new Paint();
        centerPaint.setStyle(Paint.Style.STROKE);
        centerPaint.setColor(0xAAFFFFFF);
        centerPaint.setStrokeWidth(2f);
        centerPaint.setAntiAlias(true);

        scanPaint = new Paint();
        scanPaint.setStyle(Paint.Style.FILL);
        scanPaint.setColor(0x2200FF00);
        scanPaint.setAntiAlias(true);
    }

    public void setResults(List<DetectionResult> results, int w, int h) {
        this.results = results != null ? results : new ArrayList<>();
        this.imageWidth = w > 0 ? w : 1;
        this.imageHeight = h > 0 ? h : 1;

        // Animasi scan line
        if (scanDown) {
            scanY += getHeight() * 0.02f;
            if (scanY >= getHeight()) scanDown = false;
        } else {
            scanY -= getHeight() * 0.02f;
            if (scanY <= 0) scanDown = true;
        }

        invalidate();
    }

    public void clearResults() {
        this.results = new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Gambar scan line
        canvas.drawRect(0, scanY, getWidth(), scanY + 4f, scanPaint);

        // Gambar crosshair
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float cs = 25f;
        canvas.drawLine(cx - cs, cy, cx + cs, cy, centerPaint);
        canvas.drawLine(cx, cy - cs, cx, cy + cs, centerPaint);
        canvas.drawCircle(cx, cy, 10f, centerPaint);

        if (results == null || results.isEmpty()) return;

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        for (int i = 0; i < results.size(); i++) {
            DetectionResult r = results.get(i);

            // Warna berdasarkan jarak
            int color;
            switch (r.distance) {
                case "SANGAT DEKAT": color = 0xFFFF0000; break;
                case "DEKAT":        color = 0xFFFF6600; break;
                case "SEDANG":       color = 0xFFFFFF00; break;
                default:             color = 0xFF00FF00; break;
            }

            boxPaint.setColor(color);
            bgPaint.setColor(color);

            RectF box = r.boundingBox;
            float left   = box.left   * scaleX;
            float top    = box.top    * scaleY;
            float right  = box.right  * scaleX;
            float bottom = box.bottom * scaleY;

            // Gambar kotak
            canvas.drawRect(left, top, right, bottom, boxPaint);

            // Gambar sudut
            float cl = 25f;
            boxPaint.setStrokeWidth(8f);
            canvas.drawLine(left, top, left + cl, top, boxPaint);
            canvas.drawLine(left, top, left, top + cl, boxPaint);
            canvas.drawLine(right - cl, top, right, top, boxPaint);
            canvas.drawLine(right, top, right, top + cl, boxPaint);
            canvas.drawLine(left, bottom - cl, left, bottom, boxPaint);
            canvas.drawLine(left, bottom, left + cl, bottom, boxPaint);
            canvas.drawLine(right, bottom - cl, right, bottom, boxPaint);
            canvas.drawLine(right - cl, bottom, right, bottom, boxPaint);
            boxPaint.setStrokeWidth(4f);

            // Label - confidence + jarak + arah
            String label = (int)(r.confidence * 100) + "% | "
                    + r.distance + " | " + r.direction;

            float pad = 6f;
            float textH = textPaint.getTextSize();
            float bgTop = top - textH - pad * 2;
            float bgBot = top;

            if (bgTop < 0) {
                bgTop = top;
                bgBot = top + textH + pad * 2;
            }

            bgPaint.setAlpha(180);
            canvas.drawRect(left, bgTop,
                    left + textPaint.measureText(label) + pad * 2,
                    bgBot, bgPaint);
            canvas.drawText(label, left + pad, bgBot - pad, textPaint);
        }
    }
}