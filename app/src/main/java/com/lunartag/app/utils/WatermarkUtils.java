package com.lunartag.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;

import com.lunartag.app.R;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.EncodeHintType;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class WatermarkUtils {

    private static final int MIN_RENDER_SIDE = 1440;

    private WatermarkUtils() {}

    public static Bitmap addWatermark(Context context, Bitmap originalBitmap, Bitmap mapBitmap, String[] lines, String lat, String lon, boolean showQr) {
        if (originalBitmap == null || lines == null || lines.length == 0) {
            return originalBitmap;
        }

        List<String> validLines = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                validLines.add(line);
            }
        }
        if (validLines.isEmpty()) {
            return originalBitmap;
        }

        Bitmap workingBitmap = originalBitmap;

        // Normalize low-res imported images so text, logo, and QR render crisp like camera photos
        int origMinSide = Math.min(workingBitmap.getWidth(), workingBitmap.getHeight());
        float upscaleFactor = 1.0f;
        if (origMinSide > 0 && origMinSide < MIN_RENDER_SIDE) {
            upscaleFactor = (float) MIN_RENDER_SIDE / origMinSide;
            int newW = Math.round(workingBitmap.getWidth() * upscaleFactor);
            int newH = Math.round(workingBitmap.getHeight() * upscaleFactor);
            workingBitmap = Bitmap.createScaledBitmap(workingBitmap, newW, newH, true);
        }

        if (!workingBitmap.isMutable()) {
            workingBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        workingBitmap.setDensity(Bitmap.DENSITY_NONE);

        Canvas canvas = new Canvas(workingBitmap);
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        float refSize = Math.min(width, height);

        // --- 1. Configure Main Text Paint ---
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        float baseTextSize = refSize / 40.0f;
        textPaint.setTextSize(baseTextSize);
        float shadowR = Math.max(1.5f, refSize * 0.0015f);
        float shadowD = Math.max(1.0f, refSize * 0.0010f);
        textPaint.setShadowLayer(shadowR, shadowD, shadowD, Color.BLACK);

        // --- 2. Configure Branding Paint (App Name) ---
        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(Color.YELLOW);
        brandPaint.setTextSize(refSize / 35.0f);
        brandPaint.setFakeBoldText(true);
        brandPaint.setShadowLayer(shadowR, shadowD, shadowD, Color.BLACK);

        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha(140);

        // --- 3. Tight Proportional Spacing (Matches Front/Back ~17% bar height) ---
        float marginX = refSize * 0.010f;
        float topPadding = refSize * 0.010f;
        float bottomPadding = refSize * 0.010f;
        float lineSpacing = baseTextSize * 0.20f;
        float gapBetweenLogoAndQr = refSize * 0.007f;
        float brandGap = refSize * 0.010f;

        int qrSize = Math.max(64, Math.round(refSize * 0.125f));
        int logoSize = Math.max(36, Math.round(refSize * 0.080f));

        // --- 4. Branding & QR Horizontal Layout ---
        Bitmap logo = null;
        if (context != null) {
            logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.lunartag);
            if (logo != null) {
                logo.setDensity(Bitmap.DENSITY_NONE);
            }
        }

        boolean hasLogo = (logo != null);
        boolean hasQr = showQr && lat != null && lon != null && !lat.trim().isEmpty() && !lon.trim().isEmpty();

        String appName = "Lunar Tag";
        float brandTextWidth = brandPaint.measureText(appName);

        float qrX = width - qrSize - marginX;
        float logoX = hasQr ? (qrX + (qrSize - logoSize) / 2f) : (width - logoSize - marginX);
        float brandTextX = (hasLogo ? logoX : qrX) - brandTextWidth - brandGap;

        float rightBoundary = width - marginX;
        if (hasLogo || hasQr) {
            rightBoundary = Math.min(brandTextX, hasQr ? qrX : logoX);
        }

        // --- 5. Map Dimensions (if provided) ---
        float mapW = 0f;
        float mapH = 0f;
        if (mapBitmap != null) {
            mapBitmap.setDensity(Bitmap.DENSITY_NONE);
            mapW = mapBitmap.getWidth() * upscaleFactor;
            mapH = mapBitmap.getHeight() * upscaleFactor;
        }

        float textLeft = (mapBitmap != null) ? (marginX + mapW + (refSize * 0.015f)) : marginX;
        float maxAllowedWidth = Math.max(refSize * 0.30f, rightBoundary - textLeft - marginX);
        float minTextSize = baseTextSize * 0.50f;

        // --- 6. Measure Actual Text Block ---
        float[] lineSizes = new float[validLines.size()];
        float[] lineHeights = new float[validLines.size()];
        float[] lineAscents = new float[validLines.size()];
        float totalTextHeight = 0f;

        for (int i = 0; i < validLines.size(); i++) {
            String line = validLines.get(i);
            float currentSize = baseTextSize;
            textPaint.setTextSize(currentSize);

            while (textPaint.measureText(line) > maxAllowedWidth && currentSize > minTextSize) {
                currentSize -= Math.max(0.5f, baseTextSize * 0.03f);
                textPaint.setTextSize(currentSize);
            }

            Paint.FontMetrics fm = textPaint.getFontMetrics();
            lineSizes[i] = currentSize;
            lineAscents[i] = fm.ascent;
            lineHeights[i] = fm.descent - fm.ascent;
            totalTextHeight += lineHeights[i];
            if (i < validLines.size() - 1) {
                totalTextHeight += lineSpacing;
            }
        }

        // --- 7. Calculate Block Height ---
        float brandingContentHeight = 0f;
        if (hasLogo && hasQr) {
            brandingContentHeight = logoSize + gapBetweenLogoAndQr + qrSize;
        } else if (hasLogo) {
            brandingContentHeight = logoSize;
        } else if (hasQr) {
            brandingContentHeight = qrSize;
        }

        float brandingStackHeight = topPadding + brandingContentHeight + bottomPadding;
        float textStackHeight = totalTextHeight + topPadding + bottomPadding;
        float mapStackHeight = (mapBitmap != null) ? (mapH + topPadding + bottomPadding) : 0f;

        float blockHeight = Math.max(brandingStackHeight, Math.max(textStackHeight, mapStackHeight));
        float watermarkTop = height - blockHeight;

        // --- 8. Draw Background ---
        Rect backgroundRect = new Rect(0, Math.round(watermarkTop), width, height);
        canvas.drawRect(backgroundRect, backgroundPaint);

        // --- 9. Draw Map Bitmap ---
        if (mapBitmap != null) {
            float mapTop = watermarkTop + (blockHeight - mapH) / 2f;
            RectF mapDest = new RectF(marginX, mapTop, marginX + mapW, mapTop + mapH);
            canvas.drawBitmap(mapBitmap, null, mapDest, null);
        }

        // --- 10. Draw Branding & QR ---
        float brandingStartY = watermarkTop + (blockHeight - brandingContentHeight) / 2f;

        if (hasLogo) {
            RectF logoDest = new RectF(logoX, brandingStartY, logoX + logoSize, brandingStartY + logoSize);
            Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(logo, null, logoDest, logoPaint);

            float brandTextY = brandingStartY + (logoSize / 2f) - ((brandPaint.descent() + brandPaint.ascent()) / 2f);
            canvas.drawText(appName, brandTextX, brandTextY, brandPaint);
        }

        if (hasQr) {
            Bitmap qrBitmap = generateQrCodeBitmap(lat, lon, qrSize);
            if (qrBitmap != null) {
                qrBitmap.setDensity(Bitmap.DENSITY_NONE);
                float qrY = hasLogo ? (brandingStartY + logoSize + gapBetweenLogoAndQr) : brandingStartY;
                RectF qrDest = new RectF(qrX, qrY, qrX + qrSize, qrY + qrSize);
                Paint qrPaint = new Paint();
                qrPaint.setFilterBitmap(false);
                canvas.drawBitmap(qrBitmap, null, qrDest, qrPaint);
            }
        }

        // --- 11. Draw Main Text Lines ---
        float currentTopY = watermarkTop + (blockHeight - totalTextHeight) / 2f;

        for (int i = 0; i < validLines.size(); i++) {
            textPaint.setTextSize(lineSizes[i]);
            float baselineY = currentTopY - lineAscents[i];
            canvas.drawText(validLines.get(i), textLeft, baselineY, textPaint);
            currentTopY += lineHeights[i] + lineSpacing;
        }

        return workingBitmap;
    }

    private static Bitmap generateQrCodeBitmap(String lat, String lon, int size) {
        try {
            String uri = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;

            Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, size, size, hints);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? Color.WHITE : Color.TRANSPARENT;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}