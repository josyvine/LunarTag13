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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.lunartag.app.R;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Utility class for rendering consistent, resolution-independent watermarks
 * across front camera, back camera, and gallery imported photos.
 */
public class WatermarkUtils {

    private WatermarkUtils() {}

    public static Bitmap addWatermark(
            Context context,
            Bitmap originalBitmap,
            Bitmap mapBitmap,
            String[] lines,
            String lat,
            String lon,
            boolean showQr
    ) {
        if (originalBitmap == null || lines == null || lines.length == 0) {
            return originalBitmap;
        }

        // Filter out null/empty lines so centering is always accurate
        List<String> validLines = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                validLines.add(line);
            }
        }
        if (validLines.isEmpty()) {
            return originalBitmap;
        }

        // Ensure mutable working copy
        Bitmap workingBitmap = originalBitmap;
        if (!workingBitmap.isMutable()) {
            workingBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        }

        Canvas canvas = new Canvas(workingBitmap);
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Use the shorter side as reference so portrait AND landscape imports stay proportional
        float refSize = Math.min(width, height);

        // --- 1. Proportional Dimensions & Spacing (No hardcoded pixel offsets) ---
        float marginX = refSize * 0.025f;
        float paddingY = refSize * 0.020f;
        float gapBetweenLogoAndQr = refSize * 0.010f;
        float gapBetweenBrandAndLogo = refSize * 0.014f;

        float baseTextSize = refSize / 40.0f;
        float minTextSize = baseTextSize * 0.55f;
        float lineSpacing = baseTextSize * 0.28f;

        float shadowRadius = Math.max(1.5f, refSize * 0.0025f);
        float shadowOffset = Math.max(1.0f, refSize * 0.0015f);

        int logoSize = Math.max(36, Math.round(refSize * 0.075f));
        int qrSize = Math.max(72, Math.round(refSize * 0.120f));

        // --- 2. Configure Paints ---
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(baseTextSize);
        textPaint.setShadowLayer(shadowRadius, shadowOffset, shadowOffset, Color.BLACK);

        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(Color.YELLOW);
        brandPaint.setTextSize(refSize / 35.0f);
        brandPaint.setFakeBoldText(true);
        brandPaint.setShadowLayer(shadowRadius, shadowOffset, shadowOffset, Color.BLACK);

        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha(145);

        // --- 3. Load Branding & Measure Horizontal Boundaries ---
        Bitmap logo = null;
        if (context != null) {
            logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.lunartag);
        }

        boolean hasQr = showQr && lat != null && lon != null && !lat.trim().isEmpty() && !lon.trim().isEmpty();
        boolean hasLogo = (logo != null);

        String appName = "Lunar Tag";
        float brandTextWidth = brandPaint.measureText(appName);

        // Anchor the wider right-side element (QR code) to the right margin so nothing overflows
        float qrLeft = width - qrSize - marginX;
        float logoLeft = hasQr
                ? qrLeft + (qrSize - logoSize) / 2f
                : width - logoSize - marginX;
        float brandTextX = (hasLogo ? logoLeft : qrLeft) - brandTextWidth - gapBetweenBrandAndLogo;

        float rightContentLeftEdge = width - marginX;
        if (hasLogo || hasQr) {
            rightContentLeftEdge = Math.min(brandTextX, hasQr ? qrLeft : logoLeft);
        }

        // --- 4. Map Sizing & Left Text Area ---
        float mapGap = refSize * 0.020f;
        float textLeft = (mapBitmap != null) ? (marginX + mapBitmap.getWidth() + mapGap) : marginX;
        float maxAllowedTextWidth = Math.max(refSize * 0.30f, rightContentLeftEdge - textLeft - marginX);

        // --- 5. Measure Actual Text Block Height (accounting for shrunk long lines) ---
        float[] lineSizes = new float[validLines.size()];
        float[] lineHeights = new float[validLines.size()];
        float[] lineAscents = new float[validLines.size()];
        float totalTextHeight = 0f;

        for (int i = 0; i < validLines.size(); i++) {
            String line = validLines.get(i);
            float currentSize = baseTextSize;
            textPaint.setTextSize(currentSize);

            while (textPaint.measureText(line) > maxAllowedTextWidth && currentSize > minTextSize) {
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

        // --- 6. Calculate Overall Watermark Bar Height ---
        float brandingContentHeight = 0f;
        if (hasLogo && hasQr) {
            brandingContentHeight = logoSize + gapBetweenLogoAndQr + qrSize;
        } else if (hasLogo) {
            brandingContentHeight = logoSize;
        } else if (hasQr) {
            brandingContentHeight = qrSize;
        }

        float textStackHeight = totalTextHeight + (paddingY * 2f);
        float brandingStackHeight = brandingContentHeight + (paddingY * 2f);
        float mapStackHeight = (mapBitmap != null) ? (mapBitmap.getHeight() + (paddingY * 2f)) : 0f;

        float blockHeight = Math.max(brandingStackHeight, Math.max(textStackHeight, mapStackHeight));
        float watermarkTop = height - blockHeight;

        // --- 7. Draw Background Bar ---
        Rect backgroundRect = new Rect(0, Math.round(watermarkTop), width, height);
        canvas.drawRect(backgroundRect, backgroundPaint);

        // --- 8. Draw Map Preview (if provided) ---
        if (mapBitmap != null) {
            float mapTop = watermarkTop + (blockHeight - mapBitmap.getHeight()) / 2f;
            RectF mapDest = new RectF(marginX, mapTop, marginX + mapBitmap.getWidth(), mapTop + mapBitmap.getHeight());
            canvas.drawBitmap(mapBitmap, null, mapDest, null);
        }

        // --- 9. Draw Branding & QR (Vertically centered inside blockHeight) ---
        float brandingStartY = watermarkTop + (blockHeight - brandingContentHeight) / 2f;

        if (hasLogo) {
            RectF logoDest = new RectF(logoLeft, brandingStartY, logoLeft + logoSize, brandingStartY + logoSize);
            Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(logo, null, logoDest, bitmapPaint);

            float brandTextY = brandingStartY + (logoSize / 2f) - ((brandPaint.descent() + brandPaint.ascent()) / 2f);
            canvas.drawText(appName, brandTextX, brandTextY, brandPaint);
        }

        if (hasQr) {
            Bitmap qrBitmap = generateQrCodeBitmap(lat, lon, qrSize);
            if (qrBitmap != null) {
                float qrTop = hasLogo
                        ? (brandingStartY + logoSize + gapBetweenLogoAndQr)
                        : brandingStartY;
                RectF qrDest = new RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize);
                // Disable filtering on QR code so modules stay razor-sharp
                Paint qrPaint = new Paint();
                qrPaint.setFilterBitmap(false);
                canvas.drawBitmap(qrBitmap, null, qrDest, qrPaint);
            }
        }

        // --- 10. Draw Main Text Lines (Vertically centered inside blockHeight) ---
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
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}