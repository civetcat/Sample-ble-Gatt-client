/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.sampleblegattclient;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuItemCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends AppCompatActivity /*ListActivity*/ {
    private final static String TAG = DeviceScanActivity.class.getSimpleName();

    // for parse ScanRecord(iBeacon)
    public final boolean bTestOneDevParseScanRecord = false;

    private Context mContext;

    private ScanCallback mScanCallback;
    private BluetoothLeScanner mBluetoothLeScanner;
    private DeviceListAdapter mDeviceListAdapter;
    private boolean mScanning = false;
    private boolean mConnecting = false;
    private Handler mHandler;
    private String sel_dev_name;
    private String sel_dev_addr;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private final String[] NEED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");
        mContext = DeviceScanActivity.this;
        setContentView(R.layout.activity_scanlist);
        Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.title_devices);

        init();

        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu()");
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            MenuItemCompat.setActionView(menu.findItem(R.id.menu_refresh), null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return (super.onCreateOptionsMenu(menu));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                handleLocationResult(DeviceScanActivity.this);
                break;
            case R.id.menu_stop:
                stopScanning();
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "onResume()");

        initBLE();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause()");

        stopScanning();
        mDeviceListAdapter.clear();
    }

    private void init() {

        // Initializes scan list view and its adapter.
        mDeviceListAdapter = new DeviceListAdapter();
        ListView mListView = findViewById(R.id.lv_scan_list);
        mListView.setAdapter(mDeviceListAdapter);
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            TextView tv_dev_name = (TextView) view.findViewById(R.id.device_name);
            TextView tv_dev_addr = (TextView) view.findViewById(R.id.device_address);
            sel_dev_name = tv_dev_name.getText().toString();
            sel_dev_addr = tv_dev_addr.getText().toString();
            Log.i(TAG, "onItemClick(" + position + "): " + sel_dev_name + ", " + sel_dev_addr);

            // Make sure stop background scanning
            if (mScanning)
                stopScanning();

            mConnecting = true;
            Intent myIntent = new Intent(DeviceScanActivity.this, DemoActivity.class);
            myIntent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, sel_dev_name);
            myIntent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, sel_dev_addr);
            startActivity(myIntent);
        });
    }

    private void initBLE() {
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // BLE is not supported.
            String ToastText = "BLE is not supported on this device.";
            Log.e(TAG, ToastText);
            Toast.makeText(mContext, ToastText, Toast.LENGTH_SHORT).show();
            return;
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            // BLE is not supported.
            String ToastText = "Bluetooth is not supported on the device.";
            Log.e(TAG, ToastText);
            Toast.makeText(mContext, ToastText, Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        // setup BluetoothLeScanner
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User choose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            String ToastText = "User choose not to enable Bluetooth";
            Log.e(TAG, ToastText);
            Toast.makeText(mContext, ToastText, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //do something
                Log.i(TAG, "onRequestPermissionsResult: PERMISSION_GRANTED");
                startScanning();
            } else {
                //do something
                Log.e(TAG, "onRequestPermissionsResult: NOT PERMISSION_GRANTED");
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void handleLocationResult(Activity activity) {
        if (!hasPermissions(this, NEED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(activity, NEED_PERMISSIONS, 1);
        } else {
            Log.i(TAG, "handleLocationResult: PERMISSION_GRANTED");
            startScanning();
        }

    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    // Start Device scan (lollipop+).
    public void startScanning() {
        if (mScanning || mConnecting) {
            String ToastText = "Under scanning or connecting.";
            Log.e(TAG, ToastText);
            Toast.makeText(mContext, ToastText, Toast.LENGTH_SHORT);
        }

        if (mScanCallback == null) {
            Log.i(TAG, "Starting Lollipop+ Scanning");

            if (mHandler == null)
                mHandler = new Handler();

            // Will stop the scanning after a set time.
            mHandler.postDelayed(() -> {
                if (!mScanning) {
                    Log.e(TAG, "Activity already finish()");
                    return;
                }

                stopScanning();

                if (mDeviceListAdapter.getCount() > 0)
                    Log.i(TAG, "Found " + mDeviceListAdapter.getCount() + " devices.");
                else
                    Log.e(TAG, "No devices found.");
            }, SCAN_PERIOD);

            // Kick off a new scan.
            mScanCallback = new SampleScanCallback();
            mScanning = true;
            sel_dev_addr = "";
            sel_dev_name = "";
            mDeviceListAdapter.clear();

            // General scanning
            //mBluetoothLeScanner.startScan(mScanCallback);
            // Specify Scanning with buildScanFilters();
            mBluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), mScanCallback);

            String toastText = getString(R.string.scan_start_toast) + " "
                    + TimeUnit.SECONDS.convert(SCAN_PERIOD, TimeUnit.MILLISECONDS) + " "
                    + getString(R.string.seconds);
            Toast.makeText(mContext, toastText, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(mContext, R.string.already_scanning, Toast.LENGTH_SHORT);
        }
    }

    // Stop Device scan(lollipop+).
    public void stopScanning() {
        Log.i(TAG, "Stopping Lollipop+ Scanning");

        // Stop the scan, wipe the callback.
        if (mScanCallback != null)
            mBluetoothLeScanner.stopScan(mScanCallback);
        mScanCallback = null;
        mScanning = false;

        // Even if no new results, update 'last seen' times.
        //mDeviceListAdapter.notifyDataSetChanged();
    }

    // Adapter for holding devices found through scanning.
    private class DeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> mDevices;
        private final LayoutInflater mInflator;

        public DeviceListAdapter() {
            super();
            mDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            // Filter out unknown name
            if (device.getName() == null || device.getName().length() <= 0 || device.getName().equals("null")) {
                return;
            }
            Log.i(TAG, "onScanResult(): found: " + device.getAddress() + " " + device.getName());
            if (!mDevices.contains(device)) {
                mDevices.add(device);
                notifyDataSetChanged();
                Log.i(TAG, "Find new Device: addr = " + device.getAddress() + "; (" + device.getName() + ")");

                mDevices.size();// display scan result dialog
//showScanResultDialog();
            }
        }

        public void removeDevice(int position) {
            if (getDevice(position) != null)
                mDevices.remove(position);
        }

        public BluetoothDevice getDevice(int position) {
            return mDevices.get(position);
        }

        public void clear() {
            mDevices.clear();
        }

        @Override
        public int getCount() {
            return mDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mDevices.get(i);
            final String deviceName = device.getName();
            final String deviceNULLName = "Unknown device";
            if (device.getName() == null || device.getName().length() <= 0 || device.getName().equalsIgnoreCase("null"))
                viewHolder.deviceName.setText(deviceNULLName);
            else
                viewHolder.deviceName.setText(deviceName);
            //else
            //    viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    // Device scan callback.
    private final BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(() -> {
                        mDeviceListAdapter.addDevice(device);
                        mDeviceListAdapter.notifyDataSetChanged();
                    });
                }
            };

    // Device scan callback(lollipop+).
    private class SampleScanCallback extends ScanCallback {

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.i(TAG, "onBatchScanResults");
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice bluetoothDevice = result.getDevice();


            if (bTestOneDevParseScanRecord) {
                String dev_addr = bluetoothDevice.getAddress();

                String dev_uuid = null;
                if (bluetoothDevice.getUuids() != null)
                    dev_uuid = Arrays.toString(bluetoothDevice.getUuids());
                if (dev_uuid == null)
                    Log.i(TAG, "onScanResult: " + dev_addr + " (" + result.getRssi() + ")");
                else
                    Log.i(TAG, "onScanResult: " + dev_addr + " (" + result.getRssi() + ")" + ", UUID: " + dev_uuid);

                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord != null) {
                    Log.e(TAG, "Get RAW manufacturer data...");
                    ParseBYTEData(scanRecord.getBytes());

                    byte[] manufacturer_data = scanRecord.getManufacturerSpecificData(0x4C);
                    if (manufacturer_data == null || manufacturer_data.length == 0) {
                        Log.e(TAG, "Get RAW manufacturer with manufacturer-ID(Apple:0x4C) fail !");
                    } else {
                        Log.e(TAG, "Get RAW manufacturer with manufacturer-ID(Apple:0x4C)");
                        ParseBYTEData(manufacturer_data);
                    }
                } else {
                    Log.e(TAG, "Null ScanRecord");
                }
            } else {
                //Log.i(TAG, "onScanResult(): found: " + bluetoothDevice.getAddress() + " " + bluetoothDevice.getName());
                mDeviceListAdapter.addDevice(bluetoothDevice);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);

            Log.i(TAG, "onScanFailed!!");
            Toast.makeText(mContext, "Scan failed with error: " + errorCode, Toast.LENGTH_LONG)
                    .show();
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }


    /**
     * Return a List of {@link ScanFilter} objects to filter by Service UUID.
     */
    private List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();

        ScanFilter.Builder builder = new ScanFilter.Builder();
        scanFilters.add(builder.build());

        return scanFilters;
    }

    /**
     * Return a {@link ScanSettings} object set to use low power (to preserve battery life).
     */
    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        //builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        return builder.build();
    }

    /*
    check below link, there are no method to parse Manufacturer data from google API.
    https://stackoverflow.com/questions/56890042/no-callback-when-using-scanfilter-in-bluetooth-le-connection

    You need parse by your self.
     */
    private void ParseBYTEData(byte[] data) {
        int length = data.length;
        Log.i(TAG, "ParseBYTEData length = " + length);
        //Log.i(TAG,"ParseManufacturerData byte = " + Arrays.toString(scan_record));
        Log.i(TAG, "ParseBYTEData byte = " + bytesToHex(data));
    }

    public String bytesToHex(byte[] in) {
        StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02X ", b));
        }
        return builder.toString();
    }

    public byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
