"""Host regression tests compiling the actual BlockedChannelData class."""

import subprocess
import tempfile
import unittest
from pathlib import Path


STUBS = {
    'android/annotation/SuppressLint.java': '''package android.annotation;
public @interface SuppressLint { String[] value(); }''',
    'android/content/Context.java': '''package android.content;
public class Context {
    public Context getApplicationContext() { return this; }
}''',
    'android/util/Pair.java': '''package android.util;
public class Pair<F, S> {
    public final F first;
    public final S second;
    public Pair(F first, S second) { this.first = first; this.second = second; }
}''',
    'androidx/annotation/NonNull.java': '''package androidx.annotation;
public @interface NonNull {}''',
    'androidx/annotation/Nullable.java': '''package androidx.annotation;
public @interface Nullable {}''',
    'com/liskovsoft/smartyoutubetv2/common/prefs/AppPrefs.java': '''
package com.liskovsoft.smartyoutubetv2.common.prefs;
public class AppPrefs {
    public interface ProfileChangeListener { void onProfileChanged(); }
    public static AppPrefs instance(android.content.Context context) { return new AppPrefs(); }
    public void addListener(ProfileChangeListener listener) {}
    public String getProfileData(String key) { return null; }
    public void setProfileData(String key, String value) {}
}''',
    'com/liskovsoft/smartyoutubetv2/common/utils/Utils.java': '''
package com.liskovsoft.smartyoutubetv2.common.utils;
public class Utils {
    public static void post(Runnable task) { task.run(); }
    public static void postDelayed(Runnable task, long delay) {}
}''',
    'com/liskovsoft/sharedutils/helpers/Helpers.java': '''
package com.liskovsoft.sharedutils.helpers;
import java.util.*;
import java.util.function.Function;
public class Helpers {
    public static int compareToIgnoreCase(String a, String b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareToIgnoreCase(b);
    }
    public static boolean equals(Object a, Object b) { return Objects.equals(a, b); }
    public static boolean allNulls(Object... values) {
        for (Object value : values) if (value != null) return false;
        return true;
    }
    public static String parseStr(String value) { return value; }
    public static String[] splitObj(String value) { return null; }
    public static String mergeObj(Object... values) { return ""; }
    public static String[] splitData(String value) { return null; }
    public static String mergeData(Object... values) { return ""; }
    public static <T> List<T> parseList(String[] data, int index, Function<String, T> parse) {
        return new ArrayList<>();
    }
    public static <K, V> Map<K, V> parseMap(String[] data, int index,
            Function<String, K> key, Function<String, V> value) {
        return new HashMap<>();
    }
}''',
}

HARNESS = '''
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;

public class BlockedChannelRegression {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        BlockedChannelData data = BlockedChannelData.instance(new Context());
        switch (args[0]) {
            case "distinct_same_name_ids":
                data.addChannel("UCaaa", "News");
                check(!data.containsChannel("UCbbb", "News"), "Different known ID must not match");
                data.addChannel("UCbbb", "News");
                check(data.getChannelCount() == 2, "Distinct same-name IDs must retain two entries");
                break;
            case "stable_id_after_rename":
                data.addChannel("UCaaa", "News");
                check(data.containsChannel("UCaaa", "Renamed"), "Known ID must survive rename");
                break;
            case "independent_removal":
                data.addChannel("UCaaa", "News");
                data.removeChannel("UCbbb", "News");
                check(data.containsChannel("UCaaa", "News"), "Removing another ID must preserve entry");
                data.addChannel("UCbbb", "News");
                data.removeChannel("UCaaa", "Renamed");
                check(!data.containsChannel("UCaaa", "News"), "Remove must find renamed ID");
                check(data.containsChannel("UCbbb", "News"), "Removal must preserve other same-name ID");
                check(data.getChannelCount() == 1, "Only one channel must remain");
                break;
            case "id_only_channels":
                data.addChannel("UCaaa", null);
                data.addChannel("UCbbb", null);
                check(data.getChannelCount() == 2, "ID-only channels must remain independent");
                check(data.containsChannel("UCaaa", "News"), "ID-only entry must match named query");
                data.removeChannel("UCaaa", null);
                check(!data.containsChannel("UCaaa", null), "ID-only removal must work");
                check(data.containsChannel("UCbbb", null), "ID-only removal must preserve other ID");
                break;
            case "case_insensitive_fallback":
                data.addChannel("UCaaa", "News");
                check(data.containsChannel(null, "nEwS"), "Missing query ID must fall back to name");
                data.removeChannel(null, "NEWS");
                check(data.isEmpty(), "Name-only removal must ignore case");
                data.addChannel(null, "News");
                check(data.containsChannel("UCaaa", "news"), "Missing stored ID must fall back to name");
                check(!data.containsChannel("UCaaa", null), "Missing names must not match unrelated entry");
                data.addChannel("UCaaa", "NEWS");
                check(data.getChannelCount() == 1, "Known ID must update matching name-only entry");
                check("UCaaa".equals(data.getChannelIdsWithNames().get(0).first), "Update must retain ID");
                break;
            case "duplicate_adds_and_updates":
                data.addChannel("UCaaa", "News");
                data.addChannel("UCaaa", "News");
                check(data.getChannelCount() == 1, "Duplicate adds must not increase count");
                data.addChannel("UCaaa", "Renamed");
                check(data.getChannelCount() == 1, "Same ID must update existing entry");
                check("Renamed".equals(data.getChannelIdsWithNames().get(0).second), "Update must retain latest name");
                data.addChannel("UCaaa", null);
                check(data.getChannelCount() == 1, "ID-only update must not duplicate entry");
                break;
            default:
                throw new AssertionError("Unknown test: " + args[0]);
        }
    }
}
'''


class BlockedChannelDataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        root = Path(cls.temp.name)
        sources = []
        for name, source in {**STUBS, 'BlockedChannelRegression.java': HARNESS}.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            sources.append(str(path))
        production = Path(__file__).resolve().parents[2] / (
            'src/main/java/com/liskovsoft/smartyoutubetv2/common/prefs/BlockedChannelData.java'
        )
        result = subprocess.run(
            ['javac', '-d', str(root), *sources, str(production)],
            capture_output=True, text=True,
        )
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        cls.classes = str(root)

    def run_case(self, name):
        result = subprocess.run(
            ['java', '-cp', self.classes, 'BlockedChannelRegression', name],
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_distinct_same_name_ids(self):
        self.run_case('distinct_same_name_ids')

    def test_stable_id_after_rename(self):
        self.run_case('stable_id_after_rename')

    def test_independent_removal(self):
        self.run_case('independent_removal')

    def test_id_only_channels(self):
        self.run_case('id_only_channels')

    def test_case_insensitive_fallback(self):
        self.run_case('case_insensitive_fallback')

    def test_duplicate_adds_and_updates(self):
        self.run_case('duplicate_adds_and_updates')


if __name__ == '__main__':
    unittest.main()
