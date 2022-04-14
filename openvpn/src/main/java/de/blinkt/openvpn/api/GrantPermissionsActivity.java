/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.api;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;

public class GrantPermissionsActivity extends Activity {
    private static final int VPN_PREPARE = 0;

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = VpnService.prepare(this);
        if (i == null)
            onActivityResult(VPN_PREPARE, RESULT_OK, null);
        else
            startActivityForResult(i, VPN_PREPARE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        setResult(resultCode);
        finish();
    }
}
