package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Storage keys for watch history; display names are not account identities. */
final class HistoryProfileKey {
    private static final String PREFIX = "history_v2_";
    private static final String GUEST_NAME = "anonymous";
    private static final String LEGACY_SUFFIX = "_state_updater_data";

    private HistoryProfileKey() {}

    static String forAccount(Account account) {
        if (account == null) {
            return PREFIX + "guest";
        }

        String id = account.getProfileId();
        return id == null || id.isEmpty() ? null : PREFIX + digest(id);
    }

    static boolean isPersistent(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    static String legacyKey(Account account) {
        String name = account != null ? account.getName() : null;
        name = name != null ? name.replace(" ", "_") : GUEST_NAME;
        // Legacy names were used as paths. Never follow a name outside the profile directory.
        return name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 ? null : name + LEGACY_SUFFIX;
    }

    static boolean canMigrateLegacy(Account selected, List<Account> accounts) {
        String key = legacyKey(selected);
        if (key == null || (selected != null &&
                (forAccount(selected) == null || key.equals(GUEST_NAME + LEGACY_SUFFIX)))) {
            return false;
        }

        boolean selectedFound = selected == null;
        if (accounts != null) {
            for (Account account : accounts) {
                if (account == null || !key.equals(legacyKey(account))) {
                    continue;
                }
                if (selected == null || !forAccount(selected).equals(forAccount(account))) {
                    return false;
                }
                selectedFound = true;
            }
        }

        return selectedFound;
    }

    static String migrationOwnerKey(String legacyKey) {
        return "history_legacy_owner_" + digest(legacyKey);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(Charset.forName("UTF-8")));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                result.append(Character.forDigit((b & 0xff) >>> 4, 16));
                result.append(Character.forDigit(b & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for history profile keys", e);
        }
    }
}
