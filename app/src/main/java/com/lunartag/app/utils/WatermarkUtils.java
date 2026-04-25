package com.lunartag.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;

import com.lunartag.app.R;

// ZXing Imports for QR Generation
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.EncodeHintType;

import java.util.Hashtable;

/**
 * A utility class with static methods for rendering the watermark onto a photo.
 * UPDATED: Added QR Code generation and rendering logic.
 */
public class WatermarkUtils {

    // Private constructor to prevent instantiation
    private WatermarkUtils() {}

    /**
     * Renders the complete watermark block onto the provided Bitmap.
     * @param context The Android Context (needed to load the logo resource).
     * @param originalBitmap The original, mutable photo bitmap.
     * @param mapBitmap The small, pre-rendered bitmap of the map preview.
     * @param lines An array of strings, with each string representing one line of the watermark text.
     * @param lat Raw latitude string for QR link.
     * @param lon Raw longitude string for QR link.
     * @param showQr Boolean toggle to enable/disable QR printing.
     */
    public static void addWatermark(Context context, Bitmap originalBitmap, Bitmap mapBitmap, String[] lines, String lat, String lon, boolean showQr) {
        if (originalBitmap == null || lines == null || lines.length == 0) {
            return;
        }

        Canvas canvas = new Canvas(originalBitmap);
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // --- 1. Configure Main Text Paint ---
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(width / 40.0f); // Slightly smaller text for address to fit better
        textPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK);

        // --- 2. Configure Branding Paint (App Name) ---
        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(Color.YELLOW); // Yellow color looks professional on black
        brandPaint.setTextSize(width / 35.0f); // Slightly larger/bolder than body text
        brandPaint.setFakeBoldText(true);
        brandPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK);

        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha(140); // Semi-transparent black

        // --- 3. Calculate Dimensions ---
        float textHeight = textPaint.descent() - textPaint.ascent();
        
        // Height calculation:
        // We ensure the block grows based on the number of lines provided (important for manual mode).
        // Added extra padding (60 instead of 40) to ensure the header branding and long addresses 
        // have enough breathing room and do not truncate at the bottom.
        float blockHeight = (textHeight * lines.length) + (lines.length * 12) + 60;

        // NEW: Adjust block height if QR code is enabled to prevent clipping
        if (showQr) {
            blockHeight += 120; // Add space for the QR code bitmap
        }

        // Ensure block is tall enough for the map if map exists
        if (mapBitmap != null && mapBitmap.getHeight() + 20 > blockHeight) {
            blockHeight = mapBitmap.getHeight() + 40;
        }

        float watermarkTop = height - blockHeight;

        // --- 4. Draw Background ---
        Rect backgroundRect = new Rect(0, (int) watermarkTop, width, height);
        canvas.drawRect(backgroundRect, backgroundPaint);

        // --- 5. Draw Map Bitmap (if provided) ---
        float mapLeft = 20;
        float mapTop = watermarkTop + 20;
        if (mapBitmap != null) {
            canvas.drawBitmap(mapBitmap, mapLeft, mapTop, null);
        }

        // --- 6. Draw Branding (Top-Right Corner) ---
        Bitmap logo = null;
        if (context != null) {
            logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.lunartag);
        }

        float logoX = 0;
        float logoY = 0;
        int targetLogoSize = 0;

        if (logo != null) {
            targetLogoSize = (int) (width * 0.08); 
            if (targetLogoSize < 50) targetLogoSize = 50; 

            Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, targetLogoSize, targetLogoSize, true);

            logoX = width - targetLogoSize - 30; 
            logoY = watermarkTop + 20; 

            canvas.drawBitmap(scaledLogo, logoX, logoY, null);

            String appName = "Lunar Tag";
            float textWidth = brandPaint.measureText(appName);
            float brandTextX = logoX - textWidth - 20;
            float brandTextY = logoY + (targetLogoSize / 2f) - ((brandPaint.descent() + brandPaint.ascent()) / 2f);

            canvas.drawText(appName, brandTextX, brandTextY, brandPaint);

            // --- NEW: Draw QR Code (Beneath Logo) ---
            if (showQr && lat != null && lon != null) {
                Bitmap qrBitmap = generateQrCodeBitmap(lat, lon);
                if (qrBitmap != null) {
                    // Position QR directly below the logo
                    float qrX = logoX + (targetLogoSize / 2f) - (qrBitmap.getWidth() / 2f);
                    float qrY = logoY + targetLogoSize + 15; // 15px gap below logo
                    canvas.drawBitmap(qrBitmap, qrX, qrY, null);
                }
            }
        }

        // --- 7. Draw Main Text Lines ---
        float textLeft = (mapBitmap != null) ? mapBitmap.getWidth() + 50 : 40;
        
        float maxAllowedWidth = (width - 60);
        if (logo != null) {
            maxAllowedWidth = (width - (width * 0.25f)); 
        }

        float currentY = watermarkTop + textHeight + 40; 

        for (String line : lines) {
            if (line != null) {
                float lineWidth = textPaint.measureText(line);
                if (lineWidth > maxAllowedWidth) {
                    float originalTextSize = textPaint.getTextSize();
                    float scaleFactor = maxAllowedWidth / lineWidth;
                    textPaint.setTextSize(originalTextSize * scaleFactor);
                    canvas.drawText(line, textLeft, currentY, textPaint);
                    textPaint.setTextSize(originalTextSize); 
                } else {
                    canvas.drawText(line, textLeft, currentY, textPaint);
                }
                
                currentY += (textHeight + 10); 
            }
        }
    }

    /**
     * NEW Helper: Generates a QR Code bitmap targeting a Google Maps URL.
     */
    private static Bitmap generateQrCodeBitmap(String lat, String lon) {
        try {
            // URL format that opens directly in Google Maps app or browser
            String uri = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
            
            int size = 120; // Tiny size for the watermark block
            Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1); // Minimum white border

            BitMatrix bitMatrix = new MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, size, size, hints);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    // Use White for QR modules and Transparent/Dark for background
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