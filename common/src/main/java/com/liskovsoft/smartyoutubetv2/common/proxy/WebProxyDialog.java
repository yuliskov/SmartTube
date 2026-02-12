package com.liskovsoft.smartyoutubetv2.common.proxy;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.R;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;

public class WebProxyDialog {
    public static final String TAG = WebProxyDialog.class.getSimpleName();
    private final Context mContext;
    private AlertDialog mProxyConfigDialog;
    private final ProxyManager mProxyManager;
    private final Handler mProxyTestHandler;
    private final ArrayList<Call> mUrlTests;
    private int mNumTests;

    public WebProxyDialog(Context context) {
        mContext = context;
        mProxyManager = new ProxyManager(mContext);
        mProxyTestHandler = new Handler(Looper.myLooper());
        mUrlTests = new ArrayList<>();
    }

    public boolean isSupported() {
        return mProxyManager.isProxySupported();
    }

    public boolean isEnabled() {
        return mProxyManager.isProxyEnabled();
    }

    public void enable(boolean checked) {
        if (isSupported()) {
            mProxyManager.saveProxyInfoToPrefs(null, checked);
            if (checked) {
                // FIXME: If user hit cancel in dialog, proxy remains enabled.
                showProxyConfigDialog();
            } else {
                mProxyManager.configureSystemProxy();
            }
        }
    }

    protected void appendStatusMessage(String msgFormat, Object ...args) {
        TextView statusView = mProxyConfigDialog.findViewById(R.id.proxy_config_message);
        String message = String.format(msgFormat, args);
        if (statusView.getText().toString().isEmpty())
            statusView.append(message);
        else
            statusView.append("\n"+message);
    }

    protected void appendStatusMessage(int resId, Object ...args) {
        appendStatusMessage(mContext.getString(resId), args);
    }

    protected Proxy validateProxyConfigFields() {
        boolean isConfigValid = true;
        int proxyTypeId = ((RadioGroup) mProxyConfigDialog.findViewById(R.id.proxy_type)).getCheckedRadioButtonId();
        if (proxyTypeId == -1) {
            isConfigValid = false;
            appendStatusMessage(R.string.proxy_type_invalid);
            mProxyConfigDialog.findViewById(R.id.proxy_type_http).requestFocus();
        }
        String proxyHost = ((EditText) mProxyConfigDialog.findViewById(R.id.proxy_host)).getText().toString();
        if (proxyHost.isEmpty()) {
            isConfigValid = false;
            appendStatusMessage(R.string.proxy_host_invalid);
        }
        String proxyPortString = ((EditText) mProxyConfigDialog.findViewById(R.id.proxy_port)).getText().toString();
        int proxyPort = proxyPortString.isEmpty() ? 0 : Helpers.parseInt(proxyPortString);
        if (proxyPort <= 0) {
            isConfigValid = false;
            appendStatusMessage(R.string.proxy_port_invalid);
        }
        String proxyUser = ((EditText) mProxyConfigDialog.findViewById(R.id.proxy_username)).getText().toString();
        String proxyPassword = ((EditText) mProxyConfigDialog.findViewById(R.id.proxy_password)).getText().toString();
        if (proxyUser.isEmpty() != proxyPassword.isEmpty()) {
            isConfigValid = false;
            appendStatusMessage(R.string.proxy_credentials_invalid);
        }
        if (!isConfigValid) {
            return null;
        }
        Proxy.Type proxyType = proxyTypeId == R.id.proxy_type_http ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
        return new Proxy(proxyType, PasswdInetSocketAddress.createUnresolved(proxyHost, proxyPort, proxyUser, proxyPassword));
    }

    @RequiresApi(19)
    protected void testProxyConnections() {
        Proxy proxy;
        try {
            proxy = validateProxyConfigFields();
        } catch (IllegalArgumentException e) {
            appendStatusMessage(e.getMessage());
            return;
        }
        if (proxy == null) {
            appendStatusMessage(R.string.proxy_test_aborted);
            return;
        }

        mProxyManager.saveProxyInfoToPrefs(proxy, true);
        mProxyManager.configureSystemProxy();

        String[] testUrls = mContext.getString(R.string.proxy_test_urls).split("\n");
        OkHttpClient okHttpClient = OkHttpManager.instance().getClient();

        for (String urlString: testUrls) {
            int serialNo = ++ mNumTests;
            Request request = new Request.Builder().url(urlString).build();
            appendStatusMessage(R.string.proxy_test_start, serialNo, urlString);
            Call call = okHttpClient.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call.isCanceled())
                        mProxyTestHandler.post(() -> appendStatusMessage(R.string.proxy_test_cancelled, serialNo));
                    else
                        mProxyTestHandler.post(() -> appendStatusMessage(R.string.proxy_test_error, serialNo, e));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String protocol = response.protocol().toString().toUpperCase();
                    int code = response.code();
                    String status = response.message();
                    mProxyTestHandler.post(() -> appendStatusMessage(R.string.proxy_test_status,
                            serialNo, protocol, code, status.isEmpty() ? "OK" : status));
                }
            });
            mUrlTests.add(call);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    protected void showProxyConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.AppDialog);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View contentView = inflater.inflate(R.layout.web_proxy_dialog, null);

        KeyHelpers.fixShowKeyboard(
                contentView.findViewById(R.id.proxy_host),
                contentView.findViewById(R.id.proxy_port),
                contentView.findViewById(R.id.proxy_username),
                contentView.findViewById(R.id.proxy_password)
        );

        if (mProxyManager.getProxyType() == Proxy.Type.DIRECT) {
            ((EditText) contentView.findViewById(R.id.proxy_host)).setText("");
            ((EditText) contentView.findViewById(R.id.proxy_port)).setText("");
            ((RadioGroup) contentView.findViewById(R.id.proxy_type)).clearCheck();
        } else {
            ((EditText) contentView.findViewById(R.id.proxy_host)).setText(mProxyManager.getProxyHost());
            ((EditText) contentView.findViewById(R.id.proxy_port)).setText(String.valueOf(mProxyManager.getProxyPort()));
            ((EditText) contentView.findViewById(R.id.proxy_username)).setText(mProxyManager.getProxyUsername());
            ((EditText) contentView.findViewById(R.id.proxy_password)).setText(mProxyManager.getProxyPassword());
            int proxyTypeId = mProxyManager.getProxyType() == Proxy.Type.HTTP ? R.id.proxy_type_http : R.id.proxy_type_socks;
            ((RadioGroup) contentView.findViewById(R.id.proxy_type)).check(proxyTypeId);
        }

        // keep empty, will override below.
        // https://stackoverflow.com/a/15619098/5379584
        mProxyConfigDialog = builder
                .setTitle(R.string.proxy_settings_title)
                .setView(contentView)
                .setNeutralButton(R.string.proxy_test_btn, (dialog, which) -> { })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> { })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> { })
                .create();

        mNumTests = 0;
        mProxyConfigDialog.show();

        mProxyConfigDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((view) -> {
            ((TextView) mProxyConfigDialog.findViewById(R.id.proxy_config_message)).setText("");
            Proxy proxy = null;
            try {
                proxy = validateProxyConfigFields();
            } catch (IllegalArgumentException e) { // port out of range
                e.printStackTrace();
            }
            if (proxy == null) {
                appendStatusMessage(R.string.proxy_application_aborted);
            } else {
                Log.d(TAG, "Saving proxy info: " + proxy);
                mProxyManager.saveProxyInfoToPrefs(proxy, true);
                // Proxy applied on dismiss
                //mProxyManager.configureSystemProxy();
                for (Call call: mUrlTests) call.cancel();
                mProxyConfigDialog.dismiss();
            }
        });

        mProxyConfigDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener((view) -> {
            for (Call call: mUrlTests) call.cancel();
            mUrlTests.clear();
            ((TextView) mProxyConfigDialog.findViewById(R.id.proxy_config_message)).setText("");
            testProxyConnections();
        });

        mProxyConfigDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener((view) -> {
            for (Call call: mUrlTests) call.cancel();
            mProxyConfigDialog.dismiss();
        });

        mProxyConfigDialog.setOnDismissListener(dialog -> {
            Proxy proxy = null;
            try {
                proxy = validateProxyConfigFields();
            } catch (IllegalArgumentException e) { // port out of range
                e.printStackTrace();
            }
            if (proxy != null) {
                Log.d(TAG, "Saving proxy info: " + proxy);
                // Proxy saved on OK button press
                //mProxyManager.saveProxyInfoToPrefs(proxy, true);
                mProxyManager.configureSystemProxy();
                for (Call call: mUrlTests) call.cancel();
            }
        });
    }
}
