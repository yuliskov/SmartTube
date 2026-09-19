package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

final class SignInQrCode {
    private static final int SIZE = 384;

    private SignInQrCode() {
    }

    /** Generates only in memory: activation links must never go to an image service or disk cache. */
    @Nullable
    static Bitmap create(@Nullable String activationUrl) {
        if (activationUrl == null || activationUrl.isEmpty()) {
            return null;
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 4); // White quiet zone required for reliable scanning.

        try {
            BitMatrix matrix = new QRCodeWriter().encode(activationUrl, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        } catch (WriterException | IllegalArgumentException e) {
            // Leave manual sign-in available. Never log the activation link or use a remote fallback.
            return null;
        }
    }
}
