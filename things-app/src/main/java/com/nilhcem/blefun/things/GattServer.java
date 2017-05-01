package com.nilhcem.blefun.things;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.util.Log;

import com.nilhcem.blefun.common.AwesomenessProfile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static android.content.Context.BLUETOOTH_SERVICE;
import static com.nilhcem.blefun.common.AwesomenessProfile.CHARACTERISTIC_COUNTER_UUID;
import static com.nilhcem.blefun.common.AwesomenessProfile.CHARACTERISTIC_INTERACTOR_UUID;
import static com.nilhcem.blefun.common.AwesomenessProfile.DESCRIPTOR_CONFIG;
import static com.nilhcem.blefun.common.AwesomenessProfile.DESCRIPTOR_USER_DESC;
import static com.nilhcem.blefun.common.AwesomenessProfile.SERVICE_UUID;

public class GattServer {

    private static final String TAG = GattServer.class.getSimpleName();

    public interface GattServerListener {
        void onInteractorWritten();

        byte[] onCounterRead();
    }

    private Context mContext;
    private GattServerListener mListener;

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    /* Collection of notification subscribers */
    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startAdvertising();
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    stopAdvertising();
                    break;
                default:
                    // Do nothing
                    break;
            }
        }
    };

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: " + errorCode);
        }
    };

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
                // Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_COUNTER_UUID.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read counter");
                byte[] value = mListener.onCounterRead();
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (CHARACTERISTIC_INTERACTOR_UUID.equals(characteristic.getUuid())) {
                Log.i(TAG, "Write interactor");

                if (mListener != null) {
                    mListener.onInteractorWritten();
                }
                notifyRegisteredDevices();
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Write: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            if (DESCRIPTOR_CONFIG.equals(descriptor.getUuid())) {
                Log.d(TAG, "Config descriptor read request");
                byte[] returnValue;
                if (mRegisteredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue);
            } else if (DESCRIPTOR_USER_DESC.equals(descriptor.getUuid())) {
                Log.d(TAG, "User description descriptor read request");
                byte[] returnValue = AwesomenessProfile.getUserDescription(descriptor.getCharacteristic().getUuid());
                returnValue = Arrays.copyOfRange(returnValue, offset, returnValue.length);
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue);
            } else {
                Log.w(TAG, "Unknown descriptor read request");
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (DESCRIPTOR_CONFIG.equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: " + device);
                    mRegisteredDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from notifications: " + device);
                    mRegisteredDevices.remove(device);
                }

                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request");
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                }
            }
        }
    };

    public void onCreate(Context context, GattServerListener listener) throws RuntimeException {
        mContext = context;
        mListener = listener;

        mBluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            throw new RuntimeException("GATT server requires Bluetooth support");
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled... enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled... starting services");
            startAdvertising();
            startServer();
        }
    }

    public void onDestroy() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            stopServer();
            stopAdvertising();
        }

        mContext.unregisterReceiver(mBluetoothReceiver);
        mListener = null;
    }

    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    private void startAdvertising() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
    }

    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) {
            return;
        }
        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    private void startServer() {
        mBluetoothGattServer = mBluetoothManager.openGattServer(mContext, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        mBluetoothGattServer.addService(createAwesomenessService());
    }

    private void stopServer() {
        if (mBluetoothGattServer == null) {
            return;
        }
        mBluetoothGattServer.close();
    }

    private BluetoothGattService createAwesomenessService() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Counter characteristic (read-only, supports notifications)
        BluetoothGattCharacteristic counter = new BluetoothGattCharacteristic(CHARACTERISTIC_COUNTER_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor counterConfig = new BluetoothGattDescriptor(DESCRIPTOR_CONFIG, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        counter.addDescriptor(counterConfig);
        BluetoothGattDescriptor counterDescription = new BluetoothGattDescriptor(DESCRIPTOR_USER_DESC, BluetoothGattDescriptor.PERMISSION_READ);
        counter.addDescriptor(counterDescription);

        // Interactor characteristic
        BluetoothGattCharacteristic interactor = new BluetoothGattCharacteristic(CHARACTERISTIC_INTERACTOR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor interactorDescription = new BluetoothGattDescriptor(DESCRIPTOR_USER_DESC, BluetoothGattDescriptor.PERMISSION_READ);
        interactor.addDescriptor(interactorDescription);

        service.addCharacteristic(counter);
        service.addCharacteristic(interactor);

        return service;
    }

    private void notifyRegisteredDevices() {
        if (mRegisteredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered");
            return;
        }

        Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattCharacteristic counterCharacteristic = mBluetoothGattServer
                    .getService(SERVICE_UUID)
                    .getCharacteristic(CHARACTERISTIC_COUNTER_UUID);
            byte[] value = mListener.onCounterRead();
            counterCharacteristic.setValue(value);
            mBluetoothGattServer.notifyCharacteristicChanged(device, counterCharacteristic, false);
        }
    }
}
