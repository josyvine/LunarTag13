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
 * UPDATED: Optimized for Smart Workplace text lengths and automated landmark strings.
 * FIXED: Balanced QR size and vertical alignment to match the "working" rendering.
 * FIXED: Resolved truncation by calculating block height from branding stack height.
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
        textPaint.setTextSize(width / 40.0f); 
        textPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK);

        // --- 2. Configure Branding Paint (App Name) ---
        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(Color.YELLOW); 
        brandPaint.setTextSize(width / 35.0f); 
        brandPaint.setFakeBoldText(true);
        brandPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK);

        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha(140); // Semi-transparent black

        // --- 3. Calculate Dimensions ---
        float textHeight = textPaint.descent() - textPaint.ascent();
        float lineSpacing = 10f;
        
        // Precise calculation of text block height
        float totalTextHeight = (textHeight * lines.length) + (lineSpacing * (lines.length - 1));
        
        // Define Branding sizes (Logo and QR)
        // FIXED: Reduced QR size to 220 to match the "working" rendering look
        int qrSize = 220; 
        int logoSize = (int) (width * 0.08);
        if (logoSize < 50) logoSize = 50;
        
        // Vertical Spacing
        float topPadding = 40f;
        float bottomPadding = 40f;
        float gapBetweenLogoAndQr = 15f;

        // Calculate heights for both sides
        float brandingStackHeight = topPadding + logoSize + gapBetweenLogoAndQr + (showQr ? qrSize : 0) + bottomPadding;
        float textStackHeight = totalTextHeight + (topPadding * 2);
        
        // Determine the final height of the black bar
        float blockHeight = Math.max(brandingStackHeight, textStackHeight);

        // Map minimum height check
        if (mapBitmap != null && mapBitmap.getHeight() + 80 > blockHeight) {
            blockHeight = mapBitmap.getHeight() + 80;
        }

        float watermarkTop = height - blockHeight;

        // --- 4. Draw Background ---
        Rect backgroundRect = new Rect(0, (int) watermarkTop, width, height);
        canvas.drawRect(backgroundRect, backgroundPaint);

        // --- 5. Draw Map Bitmap (if provided) ---
        float mapLeft = 20;
        float mapTop = watermarkTop + (blockHeight - (mapBitmap != null ? mapBitmap.getHeight() : 0)) / 2f;
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

        if (logo != null) {
            Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true);

            logoX = width - logoSize - 40; 
            // FIXED: Logo is anchored to the top of the bar for consistent spacing
            logoY = watermarkTop + topPadding; 

            canvas.drawBitmap(scaledLogo, logoX, logoY, null);

            String appName = "Lunar Tag";
            float textWidth = brandPaint.measureText(appName);
            float brandTextX = logoX - textWidth - 20;
            // Center the "Lunar Tag" text vertically with the logo
            float brandTextY = logoY + (logoSize / 2f) - ((brandPaint.descent() + brandPaint.ascent()) / 2f);

            canvas.drawText(appName, brandTextX, brandTextY, brandPaint);

            // --- 7. Draw QR Code (Positioned strictly under Logo) ---
            if (showQr && lat != null && lon != null) {
                Bitmap qrBitmap = generateQrCodeBitmap(lat, lon, qrSize);
                if (qrBitmap != null) {
                    // Center QR code under the Logo icon
                    float qrX = logoX + (logoSize / 2f) - (qrBitmap.getWidth() / 2f);
                    // FIXED: QR Y position is calculated relative to logo to ensure no overlap or truncation
                    float qrY = logoY + logoSize + gapBetweenLogoAndQr;
                    canvas.drawBitmap(qrBitmap, qrX, qrY, null);
                }
            }
        }

        // --- 8. Draw Main Text Lines ---
        float textLeft = (mapBitmap != null) ? mapBitmap.getWidth() + 50 : 40;
        
        // SAFETY: QR area takes up space on right, give text approx 65% of width
        float maxAllowedWidth = (width - (width * 0.35f));

        // Center the entire text stack vertically within the generated blockHeight
        float currentY = watermarkTop + ((blockHeight - totalTextHeight) / 2f) - textPaint.ascent();

        for (String line : lines) {
            if (line != null) {
                float lineWidth = textPaint.measureText(line);
                if (lineWidth > maxAllowedWidth) {
                    // Smart Scaling: Shrink font if the address/landmark is too long for the resolution
                    float originalTextSize = textPaint.getTextSize();
                    float scaleFactor = maxAllowedWidth / lineWidth;
                    textPaint.setTextSize(originalTextSize * scaleFactor);
                    canvas.drawText(line, textLeft, currentY, textPaint);
                    textPaint.setTextSize(originalTextSize); 
                } else {
                    canvas.drawText(line, textLeft, currentY, textPaint);
                }
                
                currentY += (textHeight + lineSpacing); 
            }
        }
    }

    /**
     * Helper: Generates a QR Code bitmap targeting a Google Maps URL.
     */
    private static Bitmap generateQrCodeBitmap(String lat, String lon, int size) {
        try {
            // URL format that opens directly in Google Maps app or browser
            String uri = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
            
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
                    // Use White for QR modules and Transparent for background
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