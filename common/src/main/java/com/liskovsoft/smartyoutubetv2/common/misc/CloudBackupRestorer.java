package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.sharedutils.helpers.FileHelpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/** Prepares a Drive backup before touching the app's current data. */
final class CloudBackupRestorer {
    private static final long MAX_DOWNLOAD_BYTES = 256L * 1024 * 1024;

    interface FileOpener {
        InputStream open(String name) throws Exception;
    }

    interface PrefsFixer {
        void fix(File prefsDir) throws IOException;
    }

    private CloudBackupRestorer() {}

    static void restoreZip(InputStream download, File dataRoot, PrefsFixer fixer) throws IOException {
        File staging = createStaging(dataRoot);
        try {
            File archive = new File(staging, "backup.zip");
            try (InputStream source = download) {
                copyLimited(source, archive, MAX_DOWNLOAD_BYTES);
            }
            File extracted = new File(staging, "extracted");
            if (!extracted.mkdir()) throw new IOException("Cannot prepare backup extraction");
            ZipHelper2.unzip(archive, extracted);

            File prefs = new File(extracted, "shared_prefs");
            File files = new File(extracted, "files");
            if (!prefs.isDirectory()) {
                // Older archives contain just the XML files at the ZIP root.
                prefs = extracted;
                files = null;
            }
            validatePrefs(prefs);
            fixer.fix(prefs);
            install(prefs, files != null && files.isDirectory() ? files : null, dataRoot);
        } finally {
            FileHelpers.delete(staging);
        }
    }

    static void restoreLegacy(List<String> names, FileOpener opener, File dataRoot, PrefsFixer fixer) throws Exception {
        File staging = createStaging(dataRoot);
        try {
            File prefs = new File(staging, "shared_prefs");
            if (!prefs.mkdir()) throw new IOException("Cannot prepare backup restore");
            long remaining = MAX_DOWNLOAD_BYTES;
            if (names != null) {
                for (String name : names) {
                    if (name == null || !name.endsWith(".xml") || name.contains("/") || name.contains("\\")) {
                        continue;
                    }
                    File destination = new File(prefs, name);
                    try (InputStream source = opener.open(name)) {
                        remaining -= copyLimited(source, destination, remaining);
                    }
                }
            }
            validatePrefs(prefs);
            fixer.fix(prefs);
            install(prefs, null, dataRoot);
        } finally {
            FileHelpers.delete(staging);
        }
    }

    private static File createStaging(File dataRoot) throws IOException {
        File staging = new File(dataRoot, "drive-restore-" + UUID.randomUUID());
        if (!staging.mkdir()) throw new IOException("Cannot prepare backup restore");
        return staging;
    }

    private static long copyLimited(InputStream source, File destination, long limit) throws IOException {
        if (source == null) throw new IOException("Backup file is missing");
        long total = 0;
        try (FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IOException("Backup download is too large");
                output.write(buffer, 0, count);
            }
        }
        return total;
    }

    private static void validatePrefs(File prefs) throws IOException {
        File[] entries = prefs.listFiles();
        if (entries == null || entries.length == 0) throw new IOException("Backup has no preferences");
        boolean hasXml = false;
        for (File entry : entries) {
            if (entry.isDirectory()) throw new IOException("Unexpected directory in backup preferences");
            if (entry.getName().endsWith(".xml")) hasXml = true;
        }
        if (!hasXml) throw new IOException("Backup has no preferences");
    }

    private static void move(File from, File to) throws IOException {
        if (!from.renameTo(to)) throw new IOException("Cannot move backup data: " + from.getName());
    }

    private static void install(File prefs, File files, File dataRoot) throws IOException {
        File oldPrefs = new File(dataRoot, "shared_prefs");
        File oldFiles = new File(dataRoot, "files");
        File previous = new File(dataRoot, "drive-previous-" + UUID.randomUUID());
        if (!previous.mkdir()) throw new IOException("Cannot prepare backup rollback");
        File previousPrefs = new File(previous, "shared_prefs");
        File previousFiles = new File(previous, "files");
        boolean movedPrefs = false;
        boolean movedFiles = false;
        boolean installedPrefs = false;
        boolean installedFiles = false;
        boolean keepPrevious = false;
        try {
            if (oldPrefs.exists()) { move(oldPrefs, previousPrefs); movedPrefs = true; }
            if (oldFiles.exists()) { move(oldFiles, previousFiles); movedFiles = true; }
            move(prefs, oldPrefs);
            installedPrefs = true;
            if (files != null) { move(files, oldFiles); installedFiles = true; }
        } catch (IOException error) {
            if (installedFiles) FileHelpers.delete(oldFiles);
            if (installedPrefs) FileHelpers.delete(oldPrefs);
            try {
                if (movedPrefs) move(previousPrefs, oldPrefs);
                if (movedFiles) move(previousFiles, oldFiles);
            } catch (IOException rollbackError) {
                error.addSuppressed(rollbackError);
                keepPrevious = true;
                throw new IOException("Restore failed; previous data is at " + previous, error);
            }
            throw error;
        } finally {
            if (!keepPrevious) FileHelpers.delete(previous);
        }
    }
}
