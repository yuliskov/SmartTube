package com.liskovsoft.smartyoutubetv2.common.prefs.common;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DataSaverBase extends DataChangeBase {
    private final AppPrefs mAppPrefs;
    private final String mDataKey;
    private final List<String> mValues;

    public DataSaverBase(Context context) {
        mAppPrefs = AppPrefs.instance(context.getApplicationContext());
        mDataKey = this.getClass().getSimpleName();
        mValues = new ArrayList<>();
        restoreState();
    }

    protected void setBoolean(int index, boolean value) {
        checkCapacity(index);
        mValues.set(index, Helpers.toString(value));
        persistState();
    }

    protected boolean getBoolean(int index, boolean defaultValue) {
        if (index >= mValues.size() || mValues.get(index) == null) {
            return defaultValue;
        }

        return Helpers.parseBoolean(mValues.get(index));
    }

    protected void setInt(int index, int value) {
        checkCapacity(index);
        mValues.set(index, Helpers.toString((Integer) value));
        persistState();
    }

    protected int getInt(int index, int defaultValue) {
        if (index >= mValues.size() || mValues.get(index) == null) {
            return defaultValue;
        }

        return Helpers.parseInt(mValues.get(index));
    }

    private void checkCapacity(int index) {
        int size = mValues.size();
        if (size <= index) { // fill with nulls
            for (int i = size; i <= index; i++) {
                mValues.add(null);
            }
        }
    }

    private void restoreState() {
        String data = mAppPrefs.getData(mDataKey);

        String[] split = Helpers.splitPrefs(data);

        if (split != null) {
            mValues.addAll(Arrays.asList(split));
        }
    }

    @Override
    protected void persistState() {
        super.persistState();
        mAppPrefs.setData(mDataKey, Helpers.mergePrefs(
                mValues.toArray()
        ));
    }
}
