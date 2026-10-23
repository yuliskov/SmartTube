package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.sharedutils.helpers.FileHelpers;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Validates an imported ZIP before replacing the current local backup. */
final class BackupArchiveImporter {
    private BackupArchiveImporter() {}

    static void importZip(File zip, File mediaDir) throws IOException {
        if (!zip.isFile()) throw new IOException("Backup archive is missing");

        File stagingRoot = new File(mediaDir, "backup-import-" + UUID.randomUUID());
        if (!stagingRoot.mkdir()) throw new IOException("Cannot prepare backup import");

        try {
            ZipHelper2.unzip(zip, stagingRoot);
            File nestedData = new File(stagingRoot, "data");
            File stagedData = nestedData.isDirectory() ? nestedData : stagingRoot;
            if (!hasBackup(stagedData)) throw new IOException("Archive does not contain a backup");

            File dataDir = new File(mediaDir, "data");
            File previousData = new File(mediaDir, "data.previous-" + UUID.randomUUID());
            boolean hadPrevious = dataDir.exists();
            if (hadPrevious && !dataDir.renameTo(previousData)) {
                throw new IOException("Cannot move existing backup");
            }
            if (!stagedData.renameTo(dataDir)) {
                if (hadPrevious && !previousData.renameTo(dataDir)) {
                    throw new IOException("Previous backup is at " + previousData.getAbsolutePath());
                }
                throw new IOException("Cannot install imported backup");
            }
            if (hadPrevious) FileHelpers.delete(previousData);
            zip.delete();
        } finally {
            FileHelpers.delete(stagingRoot);
        }
    }

    private static boolean hasBackup(File dataDir) {
        File[] appDirs = dataDir.listFiles();
        if (appDirs == null) return false;
        for (File appDir : appDirs) {
            File prefsDir = new File(appDir, "Backup/shared_prefs");
            if (prefsDir.isDirectory() && !FileHelpers.isEmpty(prefsDir)) return true;
        }
        return false;
    }
}
