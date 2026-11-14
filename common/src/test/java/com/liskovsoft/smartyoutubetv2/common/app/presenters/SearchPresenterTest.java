package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.app.Activity;
import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SearchPresenterTest {
    private Application mApplication;
    private SearchPresenter mPresenter;
    private GeneralData mGeneralData;

    @Before
    public void setUp() throws Exception {
        resetSingleton(SearchPresenter.class);
        resetSingleton(ViewManager.class);
        mApplication = RuntimeEnvironment.getApplication();
        mGeneralData = GeneralData.instance(mApplication);
        mGeneralData.setChildModeEnabled(false);
        SearchData.instance(mApplication).setInstantVoiceSearchEnabled(false);
        AccountsData.instance(mApplication).setPasswordAccepted(true);
        ViewManager.instance(mApplication).register(SearchView.class, SearchActivity.class);
        mPresenter = SearchPresenter.instance(mApplication);
    }

    @After
    public void tearDown() throws Exception {
        mGeneralData.setChildModeEnabled(false);
        AccountsData.instance(mApplication).setPasswordAccepted(false);
        resetSingleton(SearchPresenter.class);
        resetSingleton(ViewManager.class);
    }

    @Test
    public void childModeBlocksAllLaunchVariants() {
        mGeneralData.setChildModeEnabled(true);

        mPresenter.startSearch(null);
        mPresenter.startSearch("query");
        mPresenter.startVoice();
        mPresenter.startPlay("query");

        assertNull(shadowOf(mApplication).getNextStartedActivity());
    }

    @Test
    public void childModeRejectsDirectlyInitializedOrRestoredView() {
        SearchActivity view = attachView();
        mGeneralData.setChildModeEnabled(true);

        mPresenter.onViewInitialized();

        assertTrue(view.finished);
        assertNull(view.tagsProvider);
        assertEquals(0, view.searchStarts);
        assertEquals(0, view.voiceStarts);
    }

    @Test
    public void childModeRejectsQueryCallbackWithExistingView() {
        SearchActivity view = attachView();
        mGeneralData.setChildModeEnabled(true);

        mPresenter.onSearch("query");

        assertTrue(view.finished);
        assertFalse(view.progressShown);
        assertEquals(0, view.searchStarts);
    }

    @Test
    public void childModeRejectsQueryCallbackWithoutView() {
        mGeneralData.setChildModeEnabled(true);

        mPresenter.onSearch("query");

        assertNull(shadowOf(mApplication).getNextStartedActivity());
    }

    @Test
    public void normalSearchLaunchesActivity() {
        mPresenter.startSearch(null);

        assertEquals(SearchActivity.class.getName(),
                shadowOf(mApplication).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void normalViewInitializesSearchAndTags() {
        SearchActivity view = attachView();

        mPresenter.onViewInitialized();

        assertFalse(view.finished);
        assertNotNull(view.tagsProvider);
        assertEquals(1, view.searchStarts);
        assertEquals(0, view.voiceStarts);
    }

    @Test
    public void blockedVoiceAndQueryDoNotLeakIntoLaterSearch() {
        mGeneralData.setChildModeEnabled(true);
        mPresenter.startPlay("blocked query");
        mPresenter.startVoice();
        mGeneralData.setChildModeEnabled(false);
        SearchActivity view = attachView();

        mPresenter.onViewInitialized();

        assertEquals(1, view.searchStarts);
        assertNull(view.searchText);
        assertEquals(0, view.voiceStarts);
    }

    private SearchActivity attachView() {
        SearchActivity view = Robolectric.buildActivity(SearchActivity.class).setup().get();
        mPresenter.setView(view);
        return view;
    }

    private static void resetSingleton(Class<?> type) throws Exception {
        Field field = type.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, null);
    }

    public static class SearchActivity extends Activity implements SearchView {
        boolean finished;
        boolean progressShown;
        int searchStarts;
        int voiceStarts;
        String searchText;
        MediaServiceSearchTagProvider tagsProvider;

        @Override public void updateSearch(VideoGroup group) {}
        @Override public void clearSearch() {}
        @Override public void clearSearchTags() {}
        @Override public void removeSearchTag(Tag tag) {}
        @Override public void setTagsProvider(MediaServiceSearchTagProvider provider) { tagsProvider = provider; }
        @Override public void showProgressBar(boolean show) { progressShown |= show; }
        @Override public void startSearch(String text) { searchText = text; searchStarts++; }
        @Override public String getSearchText() { return searchText; }
        @Override public void startVoiceRecognition() { voiceStarts++; }
        @Override public void finishReally() { finished = true; }
    }
}
