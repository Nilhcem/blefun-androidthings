package com.nilhcem.blefun.things;

import android.util.Log;

import com.google.android.things.contrib.driver.pwmservo.Servo;
import com.nilhcem.ledcontrol.LedControl;

import java.io.IOException;

public class LuckyCat {

    private static final String TAG = LuckyCat.class.getSimpleName();
    private static final String SERVO_PWM = "PWM0";
    private static final String DIGITS_SPI = "SPI0.0";

    private Servo mServo;
    private LedControl mLedControl;

    public void onCreate() {
        try {
            mServo = new Servo(SERVO_PWM);
            mServo.setPulseDurationRange(0.6, 2.4);
            mServo.setAngleRange(-90, 90);
            mServo.setEnabled(true);

            mLedControl = new LedControl(DIGITS_SPI);
            mLedControl.setIntensity(4);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing lucky cat", e);
        }
    }

    public void movePaw() {
        try {
            mServo.setAngle(mServo.getMaximumAngle());
            Thread.sleep(1000);
            mServo.setAngle(mServo.getMinimumAngle());
        } catch (IOException e) {
            Log.e(TAG, "Error moving paw", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep error", e);
        }
    }

    public void updateCounter(int counter) {
        int curValue = counter;
        for (int i = 0; i < 8; i++) {
            byte value = (byte) ((i != 0 && curValue == 0) ? 16 : (curValue % 10));
            try {
                mLedControl.setDigit(i, value, false);
            } catch (IOException e) {
                Log.e(TAG, "Error setting counter", e);
            }
            curValue /= 10;
        }
    }

    public void onDestroy() {
        try {
            if (mServo != null) {
                mServo.close();
            }
            if (mLedControl != null) {
                mLedControl.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing lucky cat resources", e);
        } finally {
            mServo = null;
            mLedControl = null;
        }
    }
}
