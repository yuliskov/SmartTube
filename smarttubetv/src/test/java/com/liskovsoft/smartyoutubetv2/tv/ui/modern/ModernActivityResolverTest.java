package com.liskovsoft.smartyoutubetv2.tv.ui.modern;

import android.app.Activity;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModernActivityResolverTest {
    interface FakeView {}
    public static class ClassicActivity extends Activity {}
    public static class ModernActivity extends Activity {}

    @After
    public void tearDown() {
        ModernActivityResolver.MODERN.remove(FakeView.class);
        ModernActivityResolver.ROOT.remove(ClassicActivity.class);
    }

    @Test
    public void disabled_returnsClassic() {
        ModernActivityResolver.MODERN.put(FakeView.class, ModernActivity.class);
        assertEquals(ClassicActivity.class, new ModernActivityResolver(false).resolve(FakeView.class, ClassicActivity.class));
    }

    @Test
    public void enabled_withoutModernScreen_fallsBackToClassic() {
        assertEquals(ClassicActivity.class, new ModernActivityResolver(true).resolve(FakeView.class, ClassicActivity.class));
    }

    @Test
    public void enabled_withModernScreen_returnsModern() {
        ModernActivityResolver.MODERN.put(FakeView.class, ModernActivity.class);
        assertEquals(ModernActivity.class, new ModernActivityResolver(true).resolve(FakeView.class, ClassicActivity.class));
    }

    @Test
    public void root_followsSameRules() {
        assertEquals(ClassicActivity.class, new ModernActivityResolver(true).resolveRoot(ClassicActivity.class));
        ModernActivityResolver.ROOT.put(ClassicActivity.class, ModernActivity.class);
        assertEquals(ModernActivity.class, new ModernActivityResolver(true).resolveRoot(ClassicActivity.class));
        assertEquals(ClassicActivity.class, new ModernActivityResolver(false).resolveRoot(ClassicActivity.class));
    }
}
