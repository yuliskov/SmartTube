/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by arne on 08.01.17.
 */
// Until I find a good solution
public class Preferences {
    static SharedPreferences getSharedPreferencesMulti(String name, Context c) {
        return c.getSharedPreferences(name, Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
    }
    public static SharedPreferences getDefaultSharedPreferences(Context c) {
        return c.getSharedPreferences(c.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
    }
}
