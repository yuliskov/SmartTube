package com.liskovsoft.smartyoutubetv2.common.misc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipHelper2 {
    static final long MAX_ZIP_BYTES = 256L * 1024 * 1024;
    private static final long MAX_UNZIPPED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    public static void zipDirectory(File sourceDir, File zipFile) {
        try {
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
            zipFileRecursive(zos, sourceDir, sourceDir.getName() + "/");
            zos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void zipFileRecursive(ZipOutputStream zos, File file, String base) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    zipFileRecursive(zos, child, base + child.getName() + "/");
                }
            }
        } else {
            FileInputStream fis = new FileInputStream(file);
            zos.putNextEntry(new ZipEntry(base.substring(0, base.length() -1))); // strip "/" at the end to mark as file
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) zos.write(buf, 0, len);
            fis.close();
            zos.closeEntry();
        }
    }

    public static void unzip(File zipFile, File targetRoot) throws IOException {
        if (zipFile.length() > MAX_ZIP_BYTES) throw new IOException("Backup archive is too large");

        try (ZipFile archive = new ZipFile(zipFile)) {
            String canonicalRoot = targetRoot.getCanonicalPath() + File.separator;
            Enumeration<? extends ZipEntry> entries = archive.entries();
            byte[] buffer = new byte[8192];
            long bytesWritten = 0;
            int entryCount = 0;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++entryCount > MAX_ENTRIES) throw new IOException("Too many backup entries");
                if (entry.getName().split("/").length > 8) throw new IOException("Backup path is too deep");

                File output = new File(targetRoot, entry.getName());
                if (!output.getCanonicalPath().startsWith(canonicalRoot)) {
                    throw new IOException("Blocked Zip Slip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) {
                        throw new IOException("Cannot create backup directory");
                    }
                    continue;
                }

                File parent = output.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Cannot create backup directory");
                }
                try (InputStream in = archive.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(output)) {
                    int length;
                    while ((length = in.read(buffer)) != -1) {
                        bytesWritten += length;
                        if (bytesWritten > MAX_UNZIPPED_BYTES) {
                            throw new IOException("Backup archive expands beyond the size limit");
                        }
                        out.write(buffer, 0, length);
                    }
                }
            }

            if (entryCount == 0) throw new IOException("Backup archive is empty");
        }
    }

}
