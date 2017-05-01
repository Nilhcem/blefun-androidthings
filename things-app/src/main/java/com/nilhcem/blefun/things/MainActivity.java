package com.nilhcem.blefun.things;

import android.app.Activity;
import android.os.Bundle;

import com.nilhcem.blefun.common.Ints;

public class MainActivity extends Activity {

    private AwesomenessCounter mAwesomenessCounter;
    private final LuckyCat mLuckyCat = new LuckyCat();
    private final GattServer mGattServer = new GattServer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAwesomenessCounter = new AwesomenessCounter(this);

        mLuckyCat.onCreate();
        mLuckyCat.updateCounter(mAwesomenessCounter.getCounterValue());

        mGattServer.onCreate(this, new GattServer.GattServerListener() {
            @Override
            public byte[] onCounterRead() {
                return Ints.toByteArray(mAwesomenessCounter.getCounterValue());
            }

            @Override
            public void onInteractorWritten() {
                int count = mAwesomenessCounter.incrementCounterValue();
                mLuckyCat.movePaw();
                mLuckyCat.updateCounter(count);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGattServer.onDestroy();
        mLuckyCat.onDestroy();
    }
}
