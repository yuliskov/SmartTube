package com.liskovsoft.smartyoutubetv2.common.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupArchiveImporterTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void invalidZipLeavesExistingBackupAlone() throws Exception {
        File mediaDir = temporaryFolder.newFolder("media");
        File existing = new File(mediaDir, "data/org.smarttube.beta/Backup/shared_prefs/settings.xml");
        existing.getParentFile().mkdirs();
        Files.write(existing.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        File zip = new File(mediaDir, "bad.zip");
        Files.write(zip.toPath(), "not a zip".getBytes(StandardCharsets.UTF_8));

        try {
            BackupArchiveImporter.importZip(zip, mediaDir);
            fail("Expected invalid ZIP to fail");
        } catch (IOException expected) {
            assertEquals("old", new String(Files.readAllBytes(existing.toPath()), StandardCharsets.UTF_8));
            assertTrue(zip.exists());
        }
        assertEquals(2, mediaDir.listFiles().length); // data and the original ZIP
    }

    @Test
    public void validZipReplacesBackup() throws Exception {
        File mediaDir = temporaryFolder.newFolder("media");
        File existing = new File(mediaDir, "data/org.smarttube.beta/Backup/shared_prefs/settings.xml");
        existing.getParentFile().mkdirs();
        Files.write(existing.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        File zip = new File(mediaDir, "good.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("data/org.smarttube.beta/Backup/shared_prefs/settings.xml"));
            out.write("new".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        BackupArchiveImporter.importZip(zip, mediaDir);

        assertEquals("new", new String(Files.readAllBytes(existing.toPath()), StandardCharsets.UTF_8));
        assertFalse(zip.exists());
        assertEquals(1, mediaDir.listFiles().length);
    }

    @Test
    public void zipSlipIsRejected() throws Exception {
        File mediaDir = temporaryFolder.newFolder("media");
        File zip = new File(mediaDir, "bad-path.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../outside.txt"));
            out.write("bad".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        try {
            BackupArchiveImporter.importZip(zip, mediaDir);
            fail("Expected path traversal to fail");
        } catch (IOException expected) {
            assertFalse(new File(mediaDir, "outside.txt").exists());
        }
    }
}
