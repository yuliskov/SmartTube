package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;

public class SignInFragment extends Fragment implements SignInView {
    private static final String TAG = SignInFragment.class.getSimpleName();

    private final Handler mHandler = new Handler();
    private SignInPresenter mSignInPresenter;
    private UriBackgroundManager mBackgroundManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        
        //setupEventListeners();
    }

    //private void setupEventListeners() {
    //    setOnItemViewClickedListener(new ItemViewClickedListener());
    //    setOnItemViewSelectedListener(new ItemViewSelectedListener());
    //}

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mSignInPresenter = SignInPresenter.instance(context);
        mSignInPresenter.register(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();

        mSignInPresenter.onInitDone();
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacksAndMessages(null);

        // fix Service not registered: android.speech.SpeechRecognizer$Connection
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSignInPresenter.unregister(this);
    }

    @Override
    public void showCode(String userCode) {
        // TODO: not implemented
    }

    //@Override
    //public void onStop() {
    //    super.onStop();
    //    mBackgroundManager.onStop();
    //}
    //
    //@Override
    //public void onStart() {
    //    super.onStart();
    //    mBackgroundManager.onStart();
    //}
}
