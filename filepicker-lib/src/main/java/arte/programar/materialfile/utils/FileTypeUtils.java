package arte.programar.materialfile.utils;

import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import arte.programar.materialfile.R;

public class FileTypeUtils {

    private static final Map<String, FileType> fileTypeExtensions = new HashMap<>();

    static {
        for (FileType fileType : FileType.values()) {
            for (String extension : fileType.getExtensions()) {
                fileTypeExtensions.put(extension, fileType);
            }
        }
    }

    public static FileType getFileType(File file) {
        if (file.isDirectory()) {
            return FileType.DIRECTORY;
        }

        FileType fileType = fileTypeExtensions.get(getExtension(file.getName()));
        if (fileType != null) {
            return fileType;
        }

        return FileType.FILE;
    }

    private static String getExtension(String fileName) {
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            encoded = fileName;
        }
        return MimeTypeMap.getFileExtensionFromUrl(encoded).toLowerCase();
    }

    public enum FileType {
        DIRECTORY(R.drawable.ic_app_directory, R.string.type_directory),
        FILE(R.drawable.ic_app_file, R.string.type_document),

        APK(R.drawable.ic_app_apk, R.string.type_apk, "apk"),
        CERTIFICATE(R.drawable.ic_app_certificate, R.string.type_certificate, "cer", "der", "pfx", "p12", "arm", "pem"),
        COMPRESS(R.drawable.ic_app_compress, R.string.type_archive, "cab", "7z", "alz", "arj", "bzip2", "bz2", "dmg", "gzip", "gz", "jar", "lz", "lzip", "lzma", "zip", "rar", "tar", "tgz"),
        WORD(R.drawable.ic_app_document, R.string.type_word, "doc", "docm", "docx", "dot", "mcw", "rtf", "pages", "odt", "ott"),
        DRAWING(R.drawable.ic_app_drawing, R.string.type_drawing, "ai", "cdr", "dfx", "eps", "svg", "stl", "wmf", "emf", "art", "xar"),
        JSON(R.drawable.ic_app_json, R.string.type_json, "json"),
        IMAGE(R.drawable.ic_app_image, R.string.type_image, "bmp", "gif", "ico", "jpeg", "jpg", "pcx", "png", "psd", "tga", "tiff", "tif", "xcf"),
        MUSIC(R.drawable.ic_app_music, R.string.type_music, "aiff", "aif", "wav", "flac", "m4a", "wma", "amr", "mp2", "mp3", "wma", "aac", "mid", "m3u"),
        PDF(R.drawable.ic_app_pdf, R.string.type_pdf, "pdf"),
        PRESENTATION(R.drawable.ic_app_presentation, R.string.type_power_point, "pptx", "keynote", "ppt", "pps", "pot", "odp", "otp"),
        SPREADSHEET(R.drawable.ic_app_spreadsheet, R.string.type_excel, "xls", "xlk", "xlsb", "xlsm", "xlsx", "xlr", "xltm", "xlw", "numbers", "ods", "ots"),
        VIDEO(R.drawable.ic_app_video, R.string.type_video, "avi", "mov", "wmv", "mkv", "3gp", "f4v", "flv", "mp4", "mpeg", "webm");

        private final int icon;
        private final int description;
        private final String[] extensions;

        FileType(int icon, int description, String... extensions) {
            this.icon = icon;
            this.description = description;
            this.extensions = extensions;
        }

        public String[] getExtensions() {
            return extensions;
        }

        public int getIcon() {
            return icon;
        }

        public int getDescription() {
            return description;
        }
    }
}
