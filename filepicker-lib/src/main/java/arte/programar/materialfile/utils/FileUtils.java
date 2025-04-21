package arte.programar.materialfile.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import arte.programar.materialfile.filter.FileFilter;

public class FileUtils {

    public static final String FILE_PATH_SEPARATOR = "/";

    public static List<File> getFileList(File directory, FileFilter filter) {
        File[] files = directory.listFiles(filter::accept);

        if (files == null) {
            return new ArrayList<>();
        }

        List<File> result = Arrays.asList(files);
        Collections.sort(result, new FileComparator());
        return result;
    }

    @Nullable
    public static File getParentOrNull(File file) {
        if (file.getParent() == null) {
            return null;
        }

        return file.getParentFile();
    }

    public static boolean isParent(File maybeChild, File possibleParent) {
        if (!possibleParent.exists() || !possibleParent.isDirectory()) {
            return false;
        }

        File child = maybeChild;
        while (child != null) {
            if (child.equals(possibleParent)) {
                return true;
            }
            child = child.getParentFile();
        }

        return false;
    }

    @Nullable
    public static File getFile(Context context, String path) {
        File filesDir = ContextCompat.getExternalFilesDirs(context, null)[0];

        if (filesDir == null) { // storage device is unavailable
            return null;
        }

        String npath = path == null ? "" : path;
        String absolutePath = filesDir.getPath();
        if (absolutePath.contains("/Android/data")) {
            int index = absolutePath.indexOf("/Android/data");
            String storage = absolutePath.substring(0, index).concat(npath.length() > 0 ? FILE_PATH_SEPARATOR : "");
            absolutePath = String.format(Locale.getDefault(), "%s%s", storage, npath);
        }

        Log.d("TAG", absolutePath);
        return new File(absolutePath);
    }
}
