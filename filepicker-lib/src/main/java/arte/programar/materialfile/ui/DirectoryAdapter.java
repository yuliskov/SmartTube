package arte.programar.materialfile.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

import arte.programar.materialfile.R;
import arte.programar.materialfile.utils.FileTypeUtils;

class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.DirectoryViewHolder> {

    private final List<File> mFiles;
    private OnItemClickListener mOnItemClickListener;

    DirectoryAdapter(List<File> files) {
        mFiles = files;
    }

    void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    @NonNull
    @Override
    public DirectoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);

        return new DirectoryViewHolder(view, mOnItemClickListener);
    }

    @Override
    public void onBindViewHolder(DirectoryViewHolder holder, int position) {
        File currentFile = mFiles.get(position);

        FileTypeUtils.FileType fileType = FileTypeUtils.getFileType(currentFile);
        holder.mFileImage.setImageResource(fileType.getIcon());
        holder.mFileSubtitle.setText(fileType.getDescription());
        holder.mFileTitle.setText(currentFile.getName());
    }

    @Override
    public int getItemCount() {
        return mFiles.size();
    }

    File getModel(int index) {
        return mFiles.get(index);
    }

    static class DirectoryViewHolder extends RecyclerView.ViewHolder {

        private final ImageView mFileImage;
        private final TextView mFileTitle;
        private final TextView mFileSubtitle;

        DirectoryViewHolder(View itemView, final OnItemClickListener clickListener) {
            super(itemView);

            itemView.setOnClickListener(v -> {
                // MOD:
                //clickListener.onItemClick(v, getBindingAdapterPosition());
                clickListener.onItemClick(v, getAdapterPosition());
            });

            mFileImage = itemView.findViewById(R.id.item_file_image);
            mFileTitle = itemView.findViewById(R.id.item_file_title);
            mFileSubtitle = itemView.findViewById(R.id.item_file_subtitle);
        }
    }
}