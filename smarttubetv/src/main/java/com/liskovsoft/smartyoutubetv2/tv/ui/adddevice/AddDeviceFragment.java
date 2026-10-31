package com.liskovsoft.smartyoutubetv2.tv.ui.adddevice;

import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

public class AddDeviceFragment extends GuidedStepSupportFragment implements AddDeviceView {
    private static final int CONTINUE = 2;
    private AddDevicePresenter mAddDevicePresenter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAddDevicePresenter = AddDevicePresenter.instance(getContext());
        mAddDevicePresenter.setView(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAddDevicePresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAddDevicePresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode) {
        setTitle(userCode);
    }

    private void setTitle(String userCode) {
        getGuidanceStylist().getTitleView().setText(userCode);
    }

    @Override
    public void close() {
        getActivity().finish();
    }

    @Override
    @NonNull
    public GuidanceStylist.Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        String title = getString(R.string.signin_view_title);
        String description = getString(R.string.add_device_view_description);
        return new GuidanceStylist.Guidance(title, description, "", null);
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
            mAddDevicePresenter.onActionClicked();
        }
    }
}
