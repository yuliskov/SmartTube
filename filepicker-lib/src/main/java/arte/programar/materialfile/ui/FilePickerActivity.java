package arte.programar.materialfile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

//import com.google.android.material.color.DynamicColors;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import arte.programar.materialfile.R;
import arte.programar.materialfile.filter.FileFilter;
import arte.programar.materialfile.filter.PatternFilter;
import arte.programar.materialfile.utils.FileUtils;

public class FilePickerActivity extends AppCompatActivity implements DirectoryFragment.FileClickListener {
    public static final String ARG_START_FILE = "arg_start_path";
    public static final String ARG_CURRENT_FILE = "arg_current_path";

    public static final String ARG_FILTER = "arg_filter";
    public static final String ARG_CLOSEABLE = "arg_closeable";
    public static final String ARG_TITLE = "arg_title";

    public static final String STATE_START_FILE = "state_start_path";
    public static final String RESULT_FILE_PATH = "result_file_path";
    private static final String STATE_CURRENT_FILE = "state_current_path";
    private static final int HANDLE_CLICK_DELAY = 150;

    private Toolbar mToolbar;

    private File mStart = null;
    private File mCurrent = null;

    private CharSequence mTitle;

    private Boolean mCloseable = true;

    private FileFilter mFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // MOD:
        //DynamicColors.applyIfAvailable(this);
        setContentView(R.layout.activity_file_picker);

        if (mStart == null) {
            mStart = FileUtils.getFile(getApplicationContext(), "");
            mCurrent = mStart;
        }

        initArguments(savedInstanceState);
        initViews();
        initToolbar();

        if (savedInstanceState == null) {
            initBackStackState();
        }
    }

    private void initArguments(Bundle savedInstanceState) {
        if (getIntent().hasExtra(ARG_FILTER)) {
            Serializable filter = getIntent().getSerializableExtra(ARG_FILTER);

            if (filter instanceof Pattern) {
                mFilter = new PatternFilter((Pattern) filter, false);
            } else {
                mFilter = (FileFilter) filter;
            }
        }

        if (savedInstanceState != null) {
            mStart = (File) savedInstanceState.getSerializable(STATE_START_FILE);
            mCurrent = (File) savedInstanceState.getSerializable(STATE_CURRENT_FILE);
            updateTitle();
        } else {
            if (getIntent().hasExtra(ARG_START_FILE)) {
                mStart = (File) getIntent().getSerializableExtra(ARG_START_FILE);
                mCurrent = mStart;
            }

            if (getIntent().hasExtra(ARG_CURRENT_FILE)) {
                File currentFile = (File) getIntent().getSerializableExtra(ARG_CURRENT_FILE);

                if (FileUtils.isParent(currentFile, mStart)) {
                    mCurrent = currentFile;
                }
            }
        }

        if (getIntent().hasExtra(ARG_TITLE)) {
            mTitle = getIntent().getCharSequenceExtra(ARG_TITLE);
        }

        if (getIntent().hasExtra(ARG_CLOSEABLE)) {
            mCloseable = getIntent().getBooleanExtra(ARG_CLOSEABLE, true);
        }
    }

    private void initToolbar() {
        setSupportActionBar(mToolbar);

        // Truncate start of path
        try {
            Field f;
            if (TextUtils.isEmpty(mTitle)) {
                f = mToolbar.getClass().getDeclaredField("mTitleTextView");
            } else {
                f = mToolbar.getClass().getDeclaredField("mSubtitleTextView");
            }

            f.setAccessible(true);
            TextView textView = (TextView) f.get(mToolbar);
            textView.setEllipsize(TextUtils.TruncateAt.START);
        } catch (Exception ignored) {
        }

        if (!TextUtils.isEmpty(mTitle)) {
            setTitle(mTitle);
        }
        updateTitle();
    }

    private void initViews() {
        mToolbar = findViewById(R.id.toolbar);
    }

    private void initBackStackState() {
        final List<File> path = new ArrayList<>();

        File current = mCurrent;

        while (current != null) {
            path.add(current);

            if (current.equals(mStart)) {
                break;
            }

            current = FileUtils.getParentOrNull(current);
        }

        Collections.reverse(path);

        for (File file : path) {
            addFragmentToBackStack(file);
        }
    }

    private void updateTitle() {
        if (getSupportActionBar() != null) {
            //final String titlePath = mCurrent.getAbsolutePath();
            // Show only path name
            final String titlePath = mCurrent.getName();

            getSupportActionBar().setDisplayHomeAsUpEnabled(!mCurrent.getPath().equals(mStart.getPath()));

            if (TextUtils.isEmpty(mTitle)) {
                getSupportActionBar().setTitle(titlePath);
            } else {
                getSupportActionBar().setSubtitle(titlePath);
            }
        }
    }

    private void addFragmentToBackStack(File file) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, DirectoryFragment.getInstance(file, mFilter))
                .addToBackStack(null)
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // MOD: dialog onStop fix
        //getMenuInflater().inflate(R.menu.menu, menu);
        //menu.findItem(R.id.action_close).setVisible(mCloseable);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        // MOD: dialog onStop fix
        //if (menuItem.getItemId() == android.R.id.home) {
        //    onBackPressed();
        //} else if (menuItem.getItemId() == R.id.action_close) {
        //    finish();
        //}
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 1) {
            getSupportFragmentManager().popBackStack();
            mCurrent = FileUtils.getParentOrNull(mCurrent);
            updateTitle();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_CURRENT_FILE, mCurrent);
        outState.putSerializable(STATE_START_FILE, mStart);
    }

    @Override
    public void onFileClicked(final File clickedFile) {
        new Handler().postDelayed(() -> handleFileClicked(clickedFile), HANDLE_CLICK_DELAY);
    }

    private void handleFileClicked(final File clickedFile) {
        if (isFinishing()) {
            return;
        }

        if (clickedFile.isDirectory()) {
            mCurrent = clickedFile;
            // If the user wanna go to the emulated directory, he will be taken to the
            // corresponding user emulated folder.
            if (mCurrent.getAbsolutePath().equals("/storage/emulated")) {
                mCurrent = Environment.getExternalStorageDirectory();
            }
            addFragmentToBackStack(mCurrent);
            updateTitle();
        } else {
            setResultAndFinish(clickedFile);
        }
    }

    private void setResultAndFinish(File file) {
        Intent data = new Intent();

        data.putExtra(RESULT_FILE_PATH, file.getPath());
        setResult(RESULT_OK, data);

        finish();
    }
}
