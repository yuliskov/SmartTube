package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.List;

public class SignInFragment extends GuidedStepSupportFragment implements SignInView {
    private static final int CONTINUE = 2;
    private static final String SIGN_IN_URL_SHORT = "https://yt.be/activate"; // doesn't support query params
    private static final String SIGN_IN_URL_FULL = "https://youtube.com/tv/activate"; // support query params
    private SignInPresenter mSignInPresenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSignInPresenter = SignInPresenter.instance(getContext());
        mSignInPresenter.setView(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSignInPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSignInPresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode) {
        setTitle(userCode);
    }

    private void setTitle(String userCode) {
        if (TextUtils.isEmpty(userCode)) {
            return;
        }

        getGuidanceStylist().getTitleView().setText(userCode);

        Glide.with(getContext())
                .load(Utils.toQrCodeLink(SIGN_IN_URL_FULL + "?user_code=" + userCode.replace(" ", "-")))
                .apply(ViewUtil.glideOptions())
                .into(getGuidanceStylist().getIconView());
    }

    @Override
    public void close() {
        getActivity().finish();
    }

    @Override
    @NonNull
    public GuidanceStylist.Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        String title = getString(R.string.signin_view_title);
        String description = getString(R.string.signin_view_description, SIGN_IN_URL_SHORT);
        return new GuidanceStylist.Guidance(title, description, "", ContextCompat.getDrawable(getContext(), R.drawable.activate_account_qrcode));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        GuidedAction login = new GuidedAction.Builder()
                .id(CONTINUE)
                .title(getString(R.string.signin_view_action_text))
                .build();
        actions.add(login);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == CONTINUE) {
            mSignInPresenter.onActionClicked();
        }
    }
}
