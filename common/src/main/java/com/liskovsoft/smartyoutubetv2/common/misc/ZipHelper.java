package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipHelper {
    public static boolean zipFolder(File sourceFolder, File zipFile, String[] backupPatterns) {
        try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zipFolderRecursive(sourceFolder, sourceFolder, zipOut, backupPatterns);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void zipFolderRecursive(File rootFolder, File currentFile, ZipOutputStream zipOut, String[] backupPatterns) throws IOException {
        String entryName = rootFolder.toURI().relativize(currentFile.toURI()).getPath();

        if (currentFile.isDirectory()) {
            if (!entryName.isEmpty()) {
                zipOut.putNextEntry(new ZipEntry(entryName + "/"));
                zipOut.closeEntry();
            }
            File[] children = currentFile.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (Helpers.endsWithAny(child.getName(), backupPatterns))
                        zipFolderRecursive(rootFolder, child, zipOut, backupPatterns);
                }
            }
        } else {
            zipOut.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream input = new FileInputStream(currentFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    zipOut.write(buffer, 0, length);
                }
            }
            zipOut.closeEntry();
        }
    }

    public static boolean unzipToFolder(File zipFile, File outputFolder) {
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        try (ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                File filePath = new File(outputFolder, entry.getName());

                if (entry.isDirectory()) {
                    filePath.mkdirs();
                } else {
                    if (filePath.getParentFile() != null) {
                        filePath.getParentFile().mkdirs();
                    }
                    try (FileOutputStream output = new FileOutputStream(filePath)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipIn.read(buffer)) > 0) {
                            output.write(buffer, 0, length);
                        }
                    }
                }
                zipIn.closeEntry();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
