package arte.programar.materialfile;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;

//import androidx.activity.result.ActivityResultLauncher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import arte.programar.materialfile.filter.CompositeFilter;
import arte.programar.materialfile.filter.FileFilter;
import arte.programar.materialfile.filter.HiddenFilter;
import arte.programar.materialfile.filter.PatternFilter;
import arte.programar.materialfile.ui.FilePickerActivity;

public class MaterialFilePicker {
    private Activity mActivity;

    private Fragment mFragment;
    private androidx.fragment.app.Fragment mSupportFragment;
    //private ActivityResultLauncher<Intent> mStartForResultFiles;

    private Class<? extends FilePickerActivity> mFilePickerClass = FilePickerActivity.class;

    private Pattern mFileFilter;
    private Boolean mDirectoriesFilter = false;
    private String mRootPath;
    private String mCurrentPath;
    private Boolean mShowHidden = false;
    private Boolean mCloseable = true;
    private CharSequence mTitle;

    public MaterialFilePicker() {
    }

    /**
     * Specifies activity, which will be used to
     * start file picker
     */
    public MaterialFilePicker withActivity(Activity activity) {
        if (mSupportFragment != null || mFragment != null) {
            throw new RuntimeException("You must pass either Activity, Fragment or SupportFragment");
        }

        mActivity = activity;
        return this;
    }

    /**
     * Specifies fragment, which will be used to
     * start file picker
     */
    public MaterialFilePicker withFragment(Fragment fragment) {
        if (mSupportFragment != null || mActivity != null) {
            throw new RuntimeException("You must pass either Activity, Fragment or SupportFragment");
        }

        mFragment = fragment;
        return this;
    }

    /**
     * Specifies support fragment which will be used to
     * start file picker
     */
    public MaterialFilePicker withSupportFragment(androidx.fragment.app.Fragment fragment) {
        if (mActivity != null || mFragment != null) {
            throw new RuntimeException("You must pass either Activity, Fragment or SupportFragment");
        }

        mSupportFragment = fragment;
        return this;
    }

    //public MaterialFilePicker withActivityResultApi(ActivityResultLauncher<Intent> startForResultFiles) {
    //    mStartForResultFiles = startForResultFiles;
    //    return this;
    //}

    /**
     * Hides files that matched by specified regular expression.
     * Use {@link MaterialFilePicker#withFilterDirectories withFilterDirectories} method
     * to enable directories filtering
     */
    public MaterialFilePicker withFilter(Pattern pattern) {
        mFileFilter = pattern;
        return this;
    }

    /**
     * If directoriesFilter is true directories will also be affected by filter,
     * the default value of directories filter is false
     *
     * @see MaterialFilePicker#withFilter
     */
    public MaterialFilePicker withFilterDirectories(boolean directoriesFilter) {
        mDirectoriesFilter = directoriesFilter;
        return this;
    }

    /**
     * Specifies root directory for picker,
     * user can't go upper that specified path
     */
    public MaterialFilePicker withRootPath(String rootPath) {
        mRootPath = rootPath;
        return this;
    }

    /**
     * Specifies start directory for picker,
     * which will be shown to user at the beginning
     */
    public MaterialFilePicker withPath(String path) {
        mCurrentPath = path;
        return this;
    }

    /**
     * Show or hide hidden files in picker
     */
    public MaterialFilePicker withHiddenFiles(boolean show) {
        mShowHidden = show;
        return this;
    }

    /**
     * Show or hide close menu in picker
     */
    public MaterialFilePicker withCloseMenu(boolean closeable) {
        mCloseable = closeable;
        return this;
    }

    /**
     * Set title of picker
     */
    public MaterialFilePicker withTitle(CharSequence title) {
        mTitle = title;
        return this;
    }

    public MaterialFilePicker withCustomActivity(Class<? extends FilePickerActivity> customActivityClass) {
        mFilePickerClass = customActivityClass;
        return this;
    }

    /**
     * Open Material File Picker activity.
     * You should set Activity or Fragment before calling this method
     *
     * @see MaterialFilePicker#withActivity(Activity)
     * @see MaterialFilePicker#withFragment(Fragment)
     * @see MaterialFilePicker#withSupportFragment(androidx.fragment.app.Fragment)
     */
    public void start(int requestCode) {
        //mStartForResultFiles.launch(getIntent());
        mActivity.startActivityForResult(getIntent(), requestCode);
    }

    // Public because of https://github.com/nbsp-team/MaterialFilePicker/issues/113
    public Intent getIntent() {
        CompositeFilter filter = getFilter();

        Intent intent = new Intent(getActivity(), mFilePickerClass);
        intent.putExtra(FilePickerActivity.ARG_FILTER, filter);
        intent.putExtra(FilePickerActivity.ARG_CLOSEABLE, mCloseable);

        if (mRootPath != null) {
            intent.putExtra(FilePickerActivity.ARG_START_FILE, new File(mRootPath));
        }

        if (mCurrentPath != null) {
            intent.putExtra(FilePickerActivity.ARG_CURRENT_FILE, new File(mCurrentPath));
        }

        if (mTitle != null) {
            intent.putExtra(FilePickerActivity.ARG_TITLE, mTitle);
        }

        return intent;
    }

    private Activity getActivity() {
        Activity activity = null;

        //if (mActivity == null && mFragment == null && mSupportFragment == null && mStartForResultFiles == null) {
        if (mActivity == null && mFragment == null && mSupportFragment == null) {
            throw new RuntimeException(
                    "You must pass Activity/Fragment by calling " +
                            "withActivity/withFragment/withSupportFragment and " +
                            "withActivityResultApi method"
            );
        }

        if (mActivity != null) {
            activity = mActivity;
        } else if (mFragment != null) {
            activity = mFragment.getActivity();
        } else if (mSupportFragment != null) {
            activity = mSupportFragment.getActivity();
        }

        return activity;
    }

    private CompositeFilter getFilter() {
        final List<FileFilter> filters = new ArrayList<>();

        if (!mShowHidden) {
            filters.add(new HiddenFilter());
        }

        if (mFileFilter != null) {
            filters.add(new PatternFilter(mFileFilter, mDirectoriesFilter));
        }

        return new CompositeFilter(filters);
    }
}
