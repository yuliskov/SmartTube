package com.liskovsoft.smartyoutubetv2.tv.adapter.vineyard;

import android.content.Context;
import android.os.Handler;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Option;
import com.liskovsoft.smartyoutubetv2.tv.presenter.vineyard.IconItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.vineyard.LoadingPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.vineyard.LoadingCardView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PaginationAdapter extends ArrayObjectAdapter {
    public static final String KEY_TAG = "tag";
    public static final String KEY_ANCHOR = "anchor";
    public static final String KEY_NEXT_PAGE = "next_page";

    private Context mContext;
    private Integer mNextPage;
    private LoadingPresenter mLoadingPresenter;
    private IconItemPresenter mIconItemPresenter;
    private Presenter mPresenter;

    private String mRowTag;
    private String mAnchor;
    private int mLoadingIndicatorPosition;


    public PaginationAdapter(Context context, Presenter presenter, String tag) {
        mContext = context;
        mPresenter = presenter;
        mLoadingPresenter = new LoadingPresenter();
        mIconItemPresenter = new IconItemPresenter();
        mLoadingIndicatorPosition = -1;
        mNextPage = 1;
        mRowTag = tag;
        setPresenterSelector();
    }

    public void setTag(String tag) {
        mRowTag = tag;
    }

    public void setNextPage(int page) {
        mNextPage = page;
    }

    public void setPresenterSelector() {
        setPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object item) {
                if (item instanceof LoadingCardView) {
                    return mLoadingPresenter;
                } else if (item instanceof Option) {
                    return mIconItemPresenter;
                }
                return mPresenter;
            }
        });
    }

    public List<Object> getItems() {
        return unmodifiableList();
    }

    public boolean shouldShowLoadingIndicator() {
        return mLoadingIndicatorPosition == -1;
    }

    public void showLoadingIndicator() {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                mLoadingIndicatorPosition = size();
                add(mLoadingIndicatorPosition, new LoadingCardView(mContext));
                notifyItemRangeInserted(mLoadingIndicatorPosition, 1);
            }
        });
    }

    public void removeLoadingIndicator() {
        removeItems(mLoadingIndicatorPosition, 1);
        notifyItemRangeRemoved(mLoadingIndicatorPosition, 1);
        mLoadingIndicatorPosition = -1;
    }

    public void setAnchor(String anchor) {
        mAnchor = anchor;
    }

    public void addPosts(List<?> posts) {
        if (posts == null) {
            return;
        }

        if (posts.size() > 0) {
            addAll(size(), posts);
        } else {
            mNextPage = 0;
        }
    }

    public boolean shouldLoadNextPage() {
        return shouldShowLoadingIndicator() && mNextPage != 0;
    }

    public Map<String, String> getAdapterOptions() {
        Map<String, String> map = new HashMap<>();
        map.put(KEY_TAG, mRowTag);
        map.put(KEY_ANCHOR, mAnchor);
        map.put(KEY_NEXT_PAGE, mNextPage.toString());
        return map;
    }

    public void showReloadCard() {
        Option option = new Option(
                mContext.getString(R.string.title_no_videos),
                mContext.getString(R.string.message_check_again),
                R.drawable.ic_refresh_white);
        add(option);
    }

    public void showTryAgainCard() {
        Option option = new Option(
                mContext.getString(R.string.title_oops),
                mContext.getString(R.string.message_try_again),
                R.drawable.ic_refresh_white);
        add(option);
    }

    public void removeReloadCard() {
        if (isRefreshCardDisplayed()) {
            removeItems(0, 1);
            notifyItemRangeRemoved(size(), 1);
        }
    }

    public boolean isRefreshCardDisplayed() {
        Object item = get(size() - 1);
        if (item instanceof Option) {
            Option option = (Option) item;
            String noVideosTitle = mContext.getString(R.string.title_no_videos);
            String oopsTitle = mContext.getString(R.string.title_oops);
            return (option.title.equals(noVideosTitle) ||
                            option.title.equals(oopsTitle));
        }
        return false;
    }

    public abstract void addAllItems(List<?> items);

    public abstract List<?> getAllItems();


}
