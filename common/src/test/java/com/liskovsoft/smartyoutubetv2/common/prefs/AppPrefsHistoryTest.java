package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppPrefsHistoryTest {
    private final List<Account> mAccounts = new ArrayList<>();
    private Account mSelected;
    private AppPrefs mPrefs;
    private Object mOriginalSignInService;
    private Field mSignInField;

    @Before
    public void setUp() throws Exception {
        resetPrefsSingleton();
        MediaServiceManager manager = MediaServiceManager.instance();
        mSignInField = MediaServiceManager.class.getDeclaredField("mSignInService");
        mSignInField.setAccessible(true);
        mOriginalSignInService = mSignInField.get(manager);
        SignInService service = (SignInService) Proxy.newProxyInstance(SignInService.class.getClassLoader(),
                new Class<?>[]{SignInService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSelectedAccount": return mSelected;
                        case "getAccounts": return mAccounts;
                        case "isSigned": return mSelected != null;
                        default: return null;
                    }
                });
        mSignInField.set(manager, service);
        mPrefs = AppPrefs.instance(RuntimeEnvironment.getApplication());
        mPrefs.clear();
    }

    @After
    public void tearDown() throws Exception {
        MediaServiceManager.instance().removeAccountListener(mPrefs);
        mSignInField.set(MediaServiceManager.instance(), mOriginalSignInService);
        resetPrefsSingleton();
    }

    @Test
    public void historyInitialisationDoesNotOverwriteSavedSettingsProfile() throws Exception {
        mPrefs.putString("last_profile_name", "Saved_Settings");
        mSelected = addAccount("Current Account", "first");
        MediaServiceManager.instance().removeAccountListener(mPrefs);
        resetPrefsSingleton();
        mPrefs = AppPrefs.instance(RuntimeEnvironment.getApplication());

        assertEquals(HistoryProfileKey.forAccount(mSelected), mPrefs.getStateUpdaterKey());
        assertEquals("Saved_Settings", mPrefs.getString("last_profile_name", null));
    }

    @Test
    public void sameNameAccountsKeepSeparateHistoryWithSettingsProfilesDisabled() {
        Account first = addAccount("Viewer", "first");
        Account second = addAccount("Viewer", "second");
        mPrefs.enableMultiProfiles(false);

        select(first);
        String firstKey = mPrefs.getStateUpdaterKey();
        mPrefs.setStateUpdaterData("first-history");
        select(second);
        assertNotEquals(firstKey, mPrefs.getStateUpdaterKey());
        assertNull(mPrefs.getStateUpdaterData());
        mPrefs.setStateUpdaterData("second-history");
        select(first);

        assertEquals("first-history", mPrefs.getStateUpdaterData());
        select(second);
        assertEquals("second-history", mPrefs.getStateUpdaterData());
    }

    @Test
    public void uniqueLegacyHistoryMigratesOnceAndOriginalRemainsRecoverable() {
        Account first = addAccount("Viewer", "first");
        mPrefs.setData("Viewer_state_updater_data", "legacy-history");
        select(first);

        assertEquals("legacy-history", mPrefs.getStateUpdaterData());
        assertEquals("legacy-history", mPrefs.getData("Viewer_state_updater_data"));
        mPrefs.setStateUpdaterData("");

        assertEquals("", mPrefs.getStateUpdaterData());
        assertEquals("legacy-history", mPrefs.getData("Viewer_state_updater_data"));
    }

    @Test
    public void ambiguousLegacyHistoryIsNotAssignedAfterOtherAccountIsRemoved() {
        Account first = addAccount("Living Room", "first");
        Account second = addAccount("Living_Room", "second");
        mPrefs.setData("Living_Room_state_updater_data", "mixed-legacy-history");
        select(first);
        assertNull(mPrefs.getStateUpdaterData());

        mAccounts.remove(first);
        select(second);

        assertNull(mPrefs.getStateUpdaterData());
        assertEquals("mixed-legacy-history", mPrefs.getData("Living_Room_state_updater_data"));
    }

    @Test
    public void guestLegacyDataIsNotClaimedByAccountNamedAnonymous() {
        Account account = addAccount("anonymous", "first");
        mPrefs.setData("anonymous_state_updater_data", "ambiguous-legacy-history");
        select(account);
        assertNull(mPrefs.getStateUpdaterData());
        mPrefs.setStateUpdaterData("account-history");
        select(null);

        assertNull(mPrefs.getStateUpdaterData());
        mPrefs.setStateUpdaterData("guest-history");
        select(account);
        assertEquals("account-history", mPrefs.getStateUpdaterData());
    }

    @Test
    public void guestHistoryMigratesWhenNoAccountCanOwnItsLegacyName() {
        addAccount("Viewer", "first");
        mPrefs.setData("anonymous_state_updater_data", "guest-legacy-history");
        select(null);

        assertEquals("guest-legacy-history", mPrefs.getStateUpdaterData());
    }

    @Test
    public void renamedAccountKeepsItsHistory() {
        Account original = addAccount("Old Name", "first");
        select(original);
        mPrefs.setStateUpdaterData("account-history");
        mAccounts.clear();
        Account renamed = addAccount("New Name", "first");
        select(renamed);

        assertEquals("account-history", mPrefs.getStateUpdaterData());
    }

    @Test
    public void incompleteAccountsCannotPersistIntoGuestOrEachOther() {
        Account first = addAccount(null, null);
        Account second = addAccount(null, null);
        select(first);
        String firstKey = mPrefs.getStateUpdaterKey();
        mPrefs.setStateUpdaterData("unidentified-history");
        assertNull(mPrefs.getStateUpdaterData());
        select(second);
        assertNotEquals(firstKey, mPrefs.getStateUpdaterKey());
        assertNull(mPrefs.getStateUpdaterData());
        select(null);
        assertNotEquals(firstKey, mPrefs.getStateUpdaterKey());
        assertNull(mPrefs.getStateUpdaterData());
    }

    @Test
    public void capturedKeyWritesRemainBoundToOriginalAccount() {
        Account first = addAccount("First", "first");
        Account second = addAccount("Second", "second");
        select(first);
        String firstKey = mPrefs.getStateUpdaterKey();
        select(second);
        mPrefs.setStateUpdaterData(firstKey, "delayed-first-history");

        assertNull(mPrefs.getStateUpdaterData());
        assertEquals("delayed-first-history", mPrefs.getStateUpdaterData(firstKey));
    }

    private void select(Account account) {
        mSelected = account;
        mPrefs.onAccountChanged(account);
    }

    private Account addAccount(String name, String id) {
        Account account = new Account() {
            @Override public int getId() { return 0; }
            @Override public String getProfileId() { return id == null ? null : "youtube:page:" + id; }
            @Override public String getName() { return name; }
            @Override public String getEmail() { return id == null ? null : id + "@example.test"; }
            @Override public String getAvatarImageUrl() { return null; }
            @Override public boolean isSelected() { return mSelected == this; }
            @Override public boolean isEmpty() { return id == null; }
        };
        mAccounts.add(account);
        return account;
    }

    private static void resetPrefsSingleton() throws Exception {
        Field field = AppPrefs.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
