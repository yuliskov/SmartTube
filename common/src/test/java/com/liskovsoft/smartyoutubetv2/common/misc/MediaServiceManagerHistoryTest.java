package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.android.plugins.RxAndroidPlugins;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MediaServiceManagerHistoryTest {
    private TestScheduler scheduler;
    private MediaServiceManager manager;
    private final List<String> contentCalls = new ArrayList<>();
    private final List<PublishSubject<Void>> historyRequests = new ArrayList<>();

    @Before
    public void setUp() {
        scheduler = new TestScheduler();
        RxJavaPlugins.setIoSchedulerHandler(ignored -> scheduler);
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(ignored -> scheduler);
        RxAndroidPlugins.setMainThreadSchedulerHandler(ignored -> scheduler);

        ContentService content = proxy(ContentService.class, (p, method, args) -> {
            contentCalls.add(method.getName());
            return null;
        });
        MediaItemService items = proxy(MediaItemService.class, (p, method, args) -> {
            PublishSubject<Void> request = PublishSubject.create();
            historyRequests.add(request);
            return request;
        });
        SignInService signIn = proxy(SignInService.class, (p, method, args) -> null);
        ServiceManager services = proxy(ServiceManager.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getContentService": return content;
                case "getMediaItemService": return items;
                case "getSignInService": return signIn;
                default: return null;
            }
        });
        manager = new MediaServiceManager(services);
    }

    @After
    public void tearDown() {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
    }

    @Test
    public void switchingAccountsCancelsPendingHistoryBeforeNotifyingListeners() {
        manager.updateHistory(video(), 10_000);
        PublishSubject<Void> oldRequest = historyRequests.get(0);
        assertTrue(oldRequest.hasObservers());
        manager.addAccountListener(account -> assertFalse(oldRequest.hasObservers()));

        manager.onAccountChanged(null);

        assertFalse(oldRequest.hasObservers());
        manager.updateHistory(video(), 20_000);
        assertEquals(2, historyRequests.size());
        assertTrue(historyRequests.get(1).hasObservers());
    }

    @Test
    public void queuedHistoryChangesAreDiscardedAfterAccountSwitch() {
        manager.enableHistory(true);
        manager.clearSearchHistory();
        manager.removeSearchTag("synthetic search");
        manager.onAccountChanged(null);

        scheduler.triggerActions();

        assertTrue(contentCalls.isEmpty());
    }

    @Test
    public void returningToSameAccountDoesNotReviveQueuedChanges() {
        manager.clearSearchHistory();
        manager.onAccountChanged(null);
        manager.onAccountChanged(null);
        scheduler.triggerActions();

        assertTrue(contentCalls.isEmpty());
    }

    @Test
    public void currentAccountHistoryChangesStillRun() {
        manager.onAccountChanged(null);
        manager.enableHistory(true);
        manager.clearSearchHistory();
        manager.removeSearchTag("synthetic search");
        scheduler.triggerActions();

        assertEquals(3, contentCalls.size());
        assertEquals("enableHistory", contentCalls.get(0));
        assertEquals("clearSearchHistory", contentCalls.get(1));
        assertEquals("removeSearchTag", contentCalls.get(2));
    }

    private static Video video() {
        Video video = new Video();
        video.videoId = "synthetic-video";
        return video;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
