package com.liskovsoft.smartyoutubetv2.tv.ui.modern.dialogs;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;


import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernEdgeFade;

/**
 * Dialogs of the modern interface.<br/>
 * Opened from the settings page they are rendered as full pages, otherwise as the side panel
 * (same fragment and presenters, modern theme).
 */
public class ModernDialogActivity extends AppDialogActivity {
    private static final long PAGE_REQUEST_TTL_MS = 3_000;
    private static long sPageRequestedAt;
    private ModernDialogPageView mPage;

    /**
     * The next dialog (shown within a short time) is rendered as a page.
     */
    public static void requestPage() {
        sPageRequestedAt = SystemClock.uptimeMillis();
    }

    private static boolean consumePageRequest() {
        boolean requested = sPageRequestedAt != 0 && SystemClock.uptimeMillis() - sPageRequestedAt < PAGE_REQUEST_TTL_MS;
        sPageRequestedAt = 0;
        return requested;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lists of the panels (added by fragments at any time) fade at the edges instead of cutting rows
        View decor = getWindow().getDecorView();
        int color = ModernColorScheme.background(this);
        int length = getResources().getDimensionPixelSize(R.dimen.modern_panel_fading_edge);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> ModernEdgeFade.attachAll(decor, color, length));
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_Modern_Preferences);
        ModernColorScheme.apply(this);
    }

    @Override
    protected AppDialogView onCreateDialogView() {
        if (!consumePageRequest()) {
            return super.onCreateDialogView();
        }

        overridePendingTransition(android.R.anim.fade_in, 0);
        mPage = new ModernDialogPageView(this);
        setContentView(mPage);

        AppDialogPresenter presenter = AppDialogPresenter.instance(this);
        presenter.setView(mPage);
        presenter.onViewInitialized();

        return mPage;
    }

    @Override
    protected boolean handleNavigation(KeyEvent event) {
        if (mPage == null) {
            return super.handleNavigation(event);
        }

        // Pages are navigated with all the keys, back first leaves the details
        return event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && mPage.onBackKey(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPage != null) {
            mPage.setPaused(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPage != null) {
            mPage.setPaused(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPage != null) {
            AppDialogPresenter presenter = AppDialogPresenter.instance(this);
            if (presenter.getView() == mPage) {
                presenter.onViewDestroyed();
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        if (mPage != null) {
            overridePendingTransition(0, android.R.anim.fade_out);
        }
    }
}
