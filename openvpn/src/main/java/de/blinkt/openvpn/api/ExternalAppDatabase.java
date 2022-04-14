/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.util.HashSet;
import java.util.Set;

import de.blinkt.openvpn.core.Preferences;

public class ExternalAppDatabase {
    private final String PREFERENCES_KEY = "allowed_apps";
    Context mContext;
    public ExternalAppDatabase(Context c) {
        mContext = c;
    }
    boolean isAllowed(String packagename) {
        Set<String> allowedapps = getExtAppList();
        return allowedapps.contains(packagename);
    }
    public Set<String> getExtAppList() {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(mContext);
        return prefs.getStringSet(PREFERENCES_KEY, new HashSet<String>());
    }
    void addApp(String packagename) {
        Set<String> allowedapps = getExtAppList();
        allowedapps.add(packagename);
        saveExtAppList(allowedapps);
    }
    private void saveExtAppList(Set<String> allowedapps) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(mContext);
        Editor prefedit = prefs.edit();
        // Workaround for bug
        prefedit.putStringSet(PREFERENCES_KEY, allowedapps);
        int counter = prefs.getInt("counter", 0);
        prefedit.putInt("counter", counter + 1);
        prefedit.apply();
    }
    public void clearAllApiApps() {
        saveExtAppList(new HashSet<String>());
    }
    public void removeApp(String packagename) {
        Set<String> allowedapps = getExtAppList();
        allowedapps.remove(packagename);
        saveExtAppList(allowedapps);
    }
}
