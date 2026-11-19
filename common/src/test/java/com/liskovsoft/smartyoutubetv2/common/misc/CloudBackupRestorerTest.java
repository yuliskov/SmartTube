package com.liskovsoft.smartyoutubetv2.common.misc;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CloudBackupRestorerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void malformedZipKeepsCurrentData() throws Exception {
        File root = temp.newFolder("app");
        write(new File(root, "shared_prefs/current.xml"), "current");
        write(new File(root, "files/history.txt"), "history");

        try {
            CloudBackupRestorer.restoreZip(new ByteArrayInputStream("broken".getBytes(StandardCharsets.UTF_8)), root, prefs -> {});
            fail("Expected a bad archive to fail");
        } catch (IOException expected) {
            assertEquals("current", read(new File(root, "shared_prefs/current.xml")));
            assertEquals("history", read(new File(root, "files/history.txt")));
        }
    }

    @Test
    public void validZipReplacesBothDirectories() throws Exception {
        File root = temp.newFolder("app");
        write(new File(root, "shared_prefs/current.xml"), "current");
        write(new File(root, "files/history.txt"), "history");

        CloudBackupRestorer.restoreZip(zip("shared_prefs/new.xml", "new", "files/new.txt", "file"), root, prefs -> {});

        assertEquals("new", read(new File(root, "shared_prefs/new.xml")));
        assertEquals("file", read(new File(root, "files/new.txt")));
        assertFalse(new File(root, "shared_prefs/current.xml").exists());
        assertFalse(new File(root, "files/history.txt").exists());
    }

    @Test
    public void failedLegacyDownloadKeepsCurrentData() throws Exception {
        File root = temp.newFolder("app");
        write(new File(root, "shared_prefs/current.xml"), "current");

        try {
            CloudBackupRestorer.restoreLegacy(Arrays.asList("one.xml", "two.xml"), name -> {
                if (name.equals("two.xml")) throw new IOException("download failed");
                return new ByteArrayInputStream("one".getBytes(StandardCharsets.UTF_8));
            }, root, prefs -> {});
            fail("Expected download failure");
        } catch (IOException expected) {
            assertEquals("current", read(new File(root, "shared_prefs/current.xml")));
            assertFalse(new File(root, "shared_prefs/one.xml").exists());
        }
    }

    @Test
    public void oldZipWithXmlAtRootStillRestores() throws Exception {
        File root = temp.newFolder("app");
        write(new File(root, "shared_prefs/current.xml"), "current");

        CloudBackupRestorer.restoreZip(zip("old.xml", "old", "another.xml", "other"), root, prefs -> {});

        assertEquals("old", read(new File(root, "shared_prefs/old.xml")));
        assertFalse(new File(root, "shared_prefs/current.xml").exists());
    }

    private static ByteArrayInputStream zip(String firstName, String firstContent, String secondName, String secondContent) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, firstName, firstContent);
            add(zip, secondName, secondContent);
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static void add(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void write(File file, String content) throws IOException {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
