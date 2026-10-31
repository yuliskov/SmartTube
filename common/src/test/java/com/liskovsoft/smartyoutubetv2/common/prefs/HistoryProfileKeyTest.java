package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HistoryProfileKeyTest {
    @Test
    public void sameDisplayNameDoesNotShareHistory() {
        Account first = account("Viewer", "youtube:email:first@example.test");
        Account second = account("Viewer", "youtube:email:second@example.test");

        assertNotEquals(HistoryProfileKey.forAccount(first), HistoryProfileKey.forAccount(second));
        assertFalse(HistoryProfileKey.canMigrateLegacy(first, Arrays.asList(first, second)));
        assertFalse(HistoryProfileKey.canMigrateLegacy(second, Arrays.asList(first, second)));
    }

    @Test
    public void normalisedLegacyNamesAreAlsoAmbiguous() {
        Account first = account("Living Room", "youtube:page:first");
        Account second = account("Living_Room", "youtube:page:second");

        assertEquals(HistoryProfileKey.legacyKey(first), HistoryProfileKey.legacyKey(second));
        assertNotEquals(HistoryProfileKey.forAccount(first), HistoryProfileKey.forAccount(second));
        assertFalse(HistoryProfileKey.canMigrateLegacy(first, Arrays.asList(first, second)));
    }

    @Test
    public void accountRenameKeepsHistoryKey() {
        assertEquals(HistoryProfileKey.forAccount(account("Old name", "youtube:page:stable")),
                HistoryProfileKey.forAccount(account("New name", "youtube:page:stable")));
    }

    @Test
    public void guestAndAccountNamedAnonymousRemainSeparate() {
        Account account = account("anonymous", "youtube:email:viewer@example.test");

        assertNotEquals(HistoryProfileKey.forAccount(null), HistoryProfileKey.forAccount(account));
        assertFalse(HistoryProfileKey.canMigrateLegacy(account, Collections.singletonList(account)));
        assertFalse(HistoryProfileKey.canMigrateLegacy(null, Collections.singletonList(account)));
    }

    @Test
    public void incompleteAccountCannotUseGuestOrDisplayNameStorage() {
        Account unnamed = account(null, null);
        Account named = account("Viewer", null);

        assertNull(HistoryProfileKey.forAccount(unnamed));
        assertNull(HistoryProfileKey.forAccount(named));
        assertFalse(HistoryProfileKey.canMigrateLegacy(named, Collections.singletonList(named)));
        assertFalse(HistoryProfileKey.canMigrateLegacy(null, Collections.singletonList(unnamed)));
        assertFalse(HistoryProfileKey.isPersistent("history_session_example"));
    }

    @Test
    public void stableKeysDoNotExposeIdentityOrFollowDisplayNamePaths() {
        Account account = account("../../outside", "youtube:email:synthetic@example.test");
        String key = HistoryProfileKey.forAccount(account);

        assertTrue(key.matches("history_v2_[0-9a-f]{64}"));
        assertNull(HistoryProfileKey.legacyKey(account));
        assertFalse(HistoryProfileKey.canMigrateLegacy(account, Collections.singletonList(account)));
        assertNull(HistoryProfileKey.legacyKey(account("..\\outside", "youtube:page:stable")));
    }

    @Test
    public void uniqueLegacyAccountCanMigrateWithoutChangingOtherProfiles() {
        Account first = account("Viewer One", "youtube:email:first@example.test");
        Account second = account("Viewer Two", "youtube:email:second@example.test");

        assertEquals("Viewer_One_state_updater_data", HistoryProfileKey.legacyKey(first));
        assertTrue(HistoryProfileKey.canMigrateLegacy(first, Arrays.asList(first, second)));
        assertTrue(HistoryProfileKey.canMigrateLegacy(second, Arrays.asList(first, second)));
        assertTrue(HistoryProfileKey.canMigrateLegacy(null, Arrays.asList(first, second)));
        assertFalse(HistoryProfileKey.canMigrateLegacy(first, Collections.singletonList(second)));
    }

    @Test
    public void accountWithMissingNameCannotClaimGuestLegacyData() {
        Account account = account(null, "youtube:email:viewer@example.test");

        assertNotEquals(HistoryProfileKey.forAccount(null), HistoryProfileKey.forAccount(account));
        assertFalse(HistoryProfileKey.canMigrateLegacy(account, Collections.singletonList(account)));
        assertFalse(HistoryProfileKey.canMigrateLegacy(null, Collections.singletonList(account)));
    }

    @Test
    public void migrationOwnershipIsSpecificToEachLegacyKey() {
        assertNotEquals(HistoryProfileKey.migrationOwnerKey("Viewer_One_state_updater_data"),
                HistoryProfileKey.migrationOwnerKey("Viewer_Two_state_updater_data"));
        assertTrue(HistoryProfileKey.isPersistent(HistoryProfileKey.forAccount(null)));
        assertTrue(HistoryProfileKey.isPersistent(HistoryProfileKey.forAccount(account("Viewer", "youtube:page:stable"))));
        assertFalse(HistoryProfileKey.isPersistent(null));
    }

    private static Account account(String name, String profileId) {
        return new Account() {
            @Override public int getId() { return 0; }
            @Override public String getProfileId() { return profileId; }
            @Override public String getName() { return name; }
            @Override public String getEmail() { return null; }
            @Override public String getAvatarImageUrl() { return null; }
            @Override public boolean isSelected() { return false; }
            @Override public boolean isEmpty() { return profileId == null; }
        };
    }
}
