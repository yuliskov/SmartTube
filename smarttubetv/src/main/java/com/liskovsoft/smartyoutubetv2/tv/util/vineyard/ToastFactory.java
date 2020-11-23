package com.liskovsoft.smartyoutubetv2.tv.util.vineyard;

import android.content.Context;
import android.widget.Toast;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ToastFactory {
    public static Toast createWifiErrorToast(Context context) {
        return Toast.makeText(
                context,
                context.getString(R.string.error_message_network_needed),
                Toast.LENGTH_SHORT);
    }

}
