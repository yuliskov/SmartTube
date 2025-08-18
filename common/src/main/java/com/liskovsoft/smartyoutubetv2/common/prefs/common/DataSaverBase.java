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
    private final List<Object> mValues;

    private interface Converter {
        Object convert(String input);
    }

    public DataSaverBase(Context context) {
        mAppPrefs = AppPrefs.instance(context.getApplicationContext());
        mDataKey = this.getClass().getSimpleName();
        mValues = new ArrayList<>();
        restoreState();
    }

    protected boolean getBoolean(int index) {
        return getBoolean(index, false);
    }

    protected boolean getBoolean(int index, boolean defaultValue) {
        return (Boolean) getValue(index, defaultValue, Helpers::parseBoolean);
    }

    protected void setBoolean(int index, boolean value) {
        setValue(index, value);
    }

    protected int getInt(int index) {
        return getInt(index, -1);
    }

    protected int getInt(int index, int defaultValue) {
        return (Integer) getValue(index, defaultValue, Helpers::parseInt);
    }

    protected void setInt(int index, int value) {
        setValue(index, value);
    }

    private Object getValue(int index, Object defaultValue, Converter converter) {
        if (index >= mValues.size() || mValues.get(index) == null) {
            return defaultValue;
        }

        Object rawValue = mValues.get(index);
        if (rawValue instanceof String) {
            Object value = converter.convert((String) rawValue);
            mValues.set(index, value);
            return value;
        } else {
            return rawValue;
        }
    }

    private void setValue(int index, Object value) {
        checkCapacity(index);
        mValues.set(index, value);
        persistState();
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

        String[] split = Helpers.splitData(data);

        if (split != null) {
            mValues.addAll(Arrays.asList(split));
        }
    }

    private void persistState() {
        mAppPrefs.setData(mDataKey, Helpers.mergeData(
                mValues.toArray()
        ));
        onDataChange();
    }
}
