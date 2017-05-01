package com.nilhcem.blefun.mobile.scan;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.nilhcem.blefun.mobile.R;
import com.nilhcem.blefun.mobile.interact.InteractActivity;

import java.util.ArrayList;
import java.util.List;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

import static com.nilhcem.blefun.common.AwesomenessProfile.SERVICE_UUID;

public class ScanActivity extends AppCompatActivity {

    private static final String TAG = ScanActivity.class.getSimpleName();
    private static final long SCAN_TIMEOUT_MS = 10_000;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_LOCATION = 1;

    private boolean mScanning;

    private final BluetoothLeScannerCompat mScanner = BluetoothLeScannerCompat.getScanner();
    private final Handler mStopScanHandler = new Handler();
    private final Runnable mStopScanRunnable = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(getApplicationContext(), "No devices found", Toast.LENGTH_SHORT).show();
            stopLeScan();
        }
    };

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            // We scan with report delay > 0. This will never be called.
            Log.i(TAG, "onScanResult: " + result.getDevice().getAddress());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i(TAG, "onBatchScanResults: " + results.toString());

            if (!results.isEmpty()) {
                ScanResult result = results.get(0);
                startInteractActivity(result.getDevice());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "Scan failed: " + errorCode);
            stopLeScan();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan_activity);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.scan_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                startLeScan();
                break;
            case R.id.menu_stop:
                stopLeScan();
                break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, "You must turn Bluetooth on, to use this app", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission accepted");
        } else {
            Toast.makeText(this, "You must grant the location permission.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        prepareForScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLeScan();
    }

    private void prepareForScan() {
        if (isBleSupported()) {
            // Ensures Bluetooth is enabled on the device
            BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter btAdapter = btManager.getAdapter();
            if (btAdapter.isEnabled()) {
                // Prompt for runtime permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    startLeScan();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
                }
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {
            Toast.makeText(this, "BLE is not supported", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private boolean isBleSupported() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private void startLeScan() {
        mScanning = true;

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(1000)
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build());
        mScanner.startScan(filters, settings, mScanCallback);

        // Stops scanning after a pre-defined scan period.
        mStopScanHandler.postDelayed(mStopScanRunnable, SCAN_TIMEOUT_MS);

        invalidateOptionsMenu();
    }

    private void stopLeScan() {
        if (mScanning) {
            mScanning = false;

            mScanner.stopScan(mScanCallback);
            mStopScanHandler.removeCallbacks(mStopScanRunnable);

            invalidateOptionsMenu();
        }
    }

    private void startInteractActivity(BluetoothDevice device) {
        Intent intent = new Intent(this, InteractActivity.class);
        intent.putExtra(InteractActivity.EXTRA_DEVICE_ADDRESS, device.getAddress());
        startActivity(intent);
        finish();
    }
}
