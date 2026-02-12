package arte.programar.materialfile.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;

import arte.programar.materialfile.R;
import arte.programar.materialfile.filter.FileFilter;
import arte.programar.materialfile.utils.FileUtils;
import arte.programar.materialfile.widget.EmptyRecyclerView;

public class DirectoryFragment extends Fragment {

    private static final String ARG_FILE = "arg_file_path";
    private static final String ARG_FILTER = "arg_filter";

    private View mEmptyView;
    private File mFile;

    private FileFilter mFilter;

    private EmptyRecyclerView mDirectoryRecyclerView;
    private DirectoryAdapter mDirectoryAdapter;
    private FileClickListener mFileClickListener;

    static DirectoryFragment getInstance(
            File file,
            FileFilter filter
    ) {
        final DirectoryFragment instance = new DirectoryFragment();

        final Bundle args = new Bundle();
        args.putSerializable(ARG_FILE, file);
        args.putSerializable(ARG_FILTER, filter);
        instance.setArguments(args);

        return instance;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mFileClickListener = (FileClickListener) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mFileClickListener = null;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_directory, container, false);

        mDirectoryRecyclerView = view.findViewById(R.id.directory_recycler_view);
        mEmptyView = view.findViewById(R.id.directory_empty_view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initArgs();
        initFilesList();
    }

    private void initFilesList() {
        mDirectoryAdapter = new DirectoryAdapter(FileUtils.getFileList(mFile, mFilter));

        mDirectoryAdapter.setOnItemClickListener(new ThrottleClickListener() {
            @Override
            void onItemClickThrottled(View view, int position) {
                if (mFileClickListener != null) {
                    mFileClickListener.onFileClicked(mDirectoryAdapter.getModel(position));
                }
            }
        });

        mDirectoryRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mDirectoryRecyclerView.setAdapter(mDirectoryAdapter);
        mDirectoryRecyclerView.setEmptyView(mEmptyView);
    }

    private void initArgs() {
        final Bundle arguments = requireArguments();

        if (arguments.containsKey(ARG_FILE)) {
            mFile = (File) getArguments().getSerializable(ARG_FILE);
        }

        mFilter = (FileFilter) getArguments().getSerializable(ARG_FILTER);
    }

    interface FileClickListener {
        void onFileClicked(File clickedFile);
    }
}
