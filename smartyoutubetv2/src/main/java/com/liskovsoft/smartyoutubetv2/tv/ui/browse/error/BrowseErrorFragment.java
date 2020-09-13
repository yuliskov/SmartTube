package com.liskovsoft.smartyoutubetv2.tv.ui.browse.error;

import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.BrowseSupportFragment.MainFragmentAdapter;
import androidx.leanback.app.ErrorSupportFragment;
import com.liskovsoft.smartyoutubetv2.common.app.models.auth.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class BrowseErrorFragment extends ErrorSupportFragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private static final boolean TRANSLUCENT = true;
    private static final int TIMER_DELAY = 1000;

    private final Handler mHandler = new Handler();
    private final ErrorFragmentData mErrorData;
    private final MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new MainFragmentAdapter<Fragment>(this) {
                @Override
                public void setEntranceTransitionState(boolean state) {
                    //setEntranceTransitionState(state);
                }
            };

    public BrowseErrorFragment(ErrorFragmentData errorData) {
        mErrorData = errorData;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //if (getFragmentManager() != null) {
        //    mSpinnerFragment = new SpinnerFragment();
        //    getFragmentManager().beginTransaction().add(R.id.main_frame, mSpinnerFragment).commit();
        //}
    }

    @Override
    public void onStart() {
        super.onStart();

        //mHandler.postDelayed(() -> {
        //    if (getFragmentManager() != null) {
        //        getFragmentManager().beginTransaction().remove(mSpinnerFragment).commit();
        //        setErrorContent();
        //    }
        //}, TIMER_DELAY);

        setErrorContent();
    }

    //@Override
    //public void onStop() {
    //    super.onStop();
    //    mHandler.removeCallbacksAndMessages(null);
    //    getFragmentManager().beginTransaction().remove(mSpinnerFragment).commit();
    //}

    private void setErrorContent() {
        setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.lb_ic_sad_cloud));

        setMessage(mErrorData.getMessage());
        setDefaultBackground(TRANSLUCENT);

        setButtonText(mErrorData.getActionText());
        setButtonClickListener(arg0 -> {
            //getFragmentManager().beginTransaction().remove(BrowseErrorFragment.this).commit();
            //getFragmentManager().popBackStack();

            mErrorData.onAction();
        });
    }

    @Override
    public MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }
}
