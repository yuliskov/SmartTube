package arte.programar.materialfile.filter;

import java.io.File;
import java.util.regex.Pattern;

public class PatternFilter implements FileFilter {

    private final Pattern mPattern;
    private final boolean mDirectoriesFilter;

    public PatternFilter(Pattern pattern, boolean directoriesFilter) {
        mPattern = pattern;
        mDirectoriesFilter = directoriesFilter;
    }

    @Override
    public boolean accept(File f) {
        return f.isDirectory() && !mDirectoriesFilter || mPattern.matcher(f.getName()).matches();
    }
}
