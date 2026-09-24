package com.liskovsoft.smartyoutubetv2.common.vot;

import android.content.Context;
import android.util.AtomicFile;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Keeps the user-provided credential outside SmartTube's settings backups. */
final class VotTokenStore {
    private final AtomicFile mFile;

    VotTokenStore(Context context) {
        File dir = ContextCompat.getNoBackupFilesDir(context);
        mFile = dir == null ? null : new AtomicFile(new File(dir, "vot_yandex_token"));
    }

    String get() {
        if (mFile == null) return null;
        try (FileInputStream stream = mFile.openRead()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[513];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (out.size() + count > 512) return null;
                out.write(buffer, 0, count);
            }
            return VotSettings.oauthToken(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return null;
        }
    }

    boolean set(String token) {
        if (mFile == null) return false;
        if (token == null) {
            mFile.delete();
            return get() == null;
        }
        FileOutputStream stream = null;
        try {
            stream = mFile.startWrite();
            stream.write(token.getBytes(StandardCharsets.UTF_8));
            mFile.finishWrite(stream);
            return true;
        } catch (IOException ignored) {
            if (stream != null) mFile.failWrite(stream);
            return false;
        }
    }
}
