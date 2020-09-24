package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.BrowseSupportFragment.MainFragmentAdapter;
import androidx.leanback.app.ErrorSupportFragment;
import com.liskovsoft.smartyoutubetv2.common.app.models.signin.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class BrowseDialogFragment extends ErrorSupportFragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private static final boolean TRANSLUCENT = true;
    private static final int TIMER_DELAY = 1000;

    private final Handler mHandler = new Handler();
    private final ErrorFragmentData mDialogData;
    private final MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new MainFragmentAdapter<Fragment>(this) {
                @Override
                public void setEntranceTransitionState(boolean state) {
                    //setEntranceTransitionState(state);
                }
            };

    public BrowseDialogFragment(ErrorFragmentData dialogData) {
        mDialogData = dialogData;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();

        setDialogContent();
    }

    private void setDialogContent() {
        setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.lb_ic_sad_cloud));

        setMessage(mDialogData.getMessage());
        //setDefaultBackground(TRANSLUCENT);

        setButtonText(mDialogData.getActionText());
        setButtonClickListener(arg0 -> {
            //BrowseErrorFragment errorFragment = new BrowseErrorFragment();
            //getFragmentManager().beginTransaction().replace(R.id.main_frame, errorFragment)
            //        .addToBackStack(null).commit();

            mDialogData.onAction();
        });
    }

    @Override
    public MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }
}
