package com.nilhcem.blefun.things;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

public class AwesomenessCounter {

    private static final String PREFS_NAME = "awesomeness";
    private static final String PREFS_KEY_COUNTER = "counter";

    private final SharedPreferences mPrefs;

    public AwesomenessCounter(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getCounterValue() {
        return mPrefs.getInt(PREFS_KEY_COUNTER, 0);
    }

    @SuppressLint("ApplySharedPref")
    public int incrementCounterValue() {
        int newValue = getCounterValue() + 1;
        mPrefs.edit().putInt(PREFS_KEY_COUNTER, newValue).commit();
        return newValue;
    }
}
