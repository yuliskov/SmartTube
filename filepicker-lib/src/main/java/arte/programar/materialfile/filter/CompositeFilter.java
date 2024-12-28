package arte.programar.materialfile.filter;

import java.io.File;
import java.util.List;

public class CompositeFilter implements FileFilter {

    private final List<FileFilter> mFilters;

    public CompositeFilter(List<FileFilter> filters) {
        mFilters = filters;
    }

    @Override
    public boolean accept(File f) {
        for (FileFilter filter : mFilters) {
            if (!filter.accept(f)) {
                return false;
            }
        }

        return true;
    }
}
