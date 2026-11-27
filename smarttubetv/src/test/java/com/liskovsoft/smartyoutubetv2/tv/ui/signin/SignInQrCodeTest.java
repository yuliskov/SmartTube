package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {17, 28}, manifest = Config.NONE, application = Application.class)
public class SignInQrCodeTest {
    @Test
    public void youtubeActivationCodeSurvivesScanning() throws Exception {
        assertScansAs("https://youtube.com/qr/activate/ABCD-EFGH");
    }

    @Test
    public void googleManualActivationPageSurvivesScanning() throws Exception {
        assertScansAs("https://www.google.com/device");
    }

    @Test
    public void queryParametersArePreservedExactly() throws Exception {
        assertScansAs("https://example.org/activate?code=ABCD%20EFGH&next=%2Ftv");
    }

    @Test
    public void missingOrUnencodableUrlLeavesManualSignInAvailable() {
        assertNull(SignInQrCode.create(null));
        assertNull(SignInQrCode.create(""));
        char[] oversized = new char[5000];
        Arrays.fill(oversized, 'a');
        assertNull(SignInQrCode.create(new String(oversized)));
    }

    private void assertScansAs(String url) throws Exception {
        Bitmap bitmap = SignInQrCode.create(url);
        assertNotNull(bitmap);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < width; i++) {
            assertEquals(Color.WHITE, bitmap.getPixel(i, 0));
            assertEquals(Color.WHITE, bitmap.getPixel(i, height - 1));
        }
        for (int i = 0; i < height; i++) {
            assertEquals(Color.WHITE, bitmap.getPixel(0, i));
            assertEquals(Color.WHITE, bitmap.getPixel(width - 1, i));
        }
        BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        assertEquals(url, new QRCodeReader().decode(image).getText());
    }
}
