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

import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
import static com.example.sampleblegattclient.SampleGattAttributes.CDR_SERVICE_ID;
import static com.example.sampleblegattclient.SampleGattAttributes.CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID;
import static com.example.sampleblegattclient.SampleGattAttributes.CDR_USER_WRITE_CHARACTERISTIC_ID;
import static com.example.sampleblegattclient.SampleGattAttributes.IVT_CLIENT_CHARACTERISTIC_CONFIG;
import static com.example.sampleblegattclient.SampleGattAttributes.PND_CLIENT_CHARACTERISTIC_CONFIG;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITE_DONE =
            "com.example.bluetooth.le.ACTION_DATA_WRITE_DONE";
    public final static String ACTION_DATA_WRITE_FAIL =
            "com.example.bluetooth.le.ACTION_DATA_WRITE_FAIL";
    public final static String ACTION_CHECK_NOTIFY =
            "com.example.bluetooth.le.ACTION_CHECK_NOTIFY";
    public final static String ACTION_DATA_DEBUG =
            "com.example.bluetooth.le.ACTION_DATA_DEBUG";
    public final static String ACTION_ANCS_DATA_WRITE_DONE =
            "com.example.bluetooth.le.ACTION_ANCS_DATA_WRITE_DONE";
    public final static String ACTION_ANCS_DATA_NOTIFY =
            "com.example.bluetooth.le.ACTION_ANCS_DATA_NOTIFY";

    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdateAction(intentAction);
                Log.i(TAG, "onConnectionStateChange(): newState = BluetoothProfile.STATE_CONNECTED");
                Log.i(TAG, "Connected to GATT server.");

                //
                Log.i(TAG, "Attempting to requestConnectionPriority:" +
                        //mBluetoothGatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH));
                        mBluetoothGatt.requestConnectionPriority(CONNECTION_PRIORITY_BALANCED));

                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "onConnectionStateChange(): newState = BluetoothProfile.STATE_DISCONNECTED");
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdateAction(intentAction);
            } else {
                Log.e(TAG, "onConnectionStateChange(): received: [status = "
                        + status
                        + ", newState = "
                        + newState
                        + "]"
                );
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onServicesDiscovered()");

                // check supported service
                BluetoothGattService mGattService = gatt.getService(CDR_SERVICE_ID);
                if (mGattService != null) {
                    Log.i(TAG, "( CDR ) onServicesDiscovered(): send ACTION_GATT_SERVICES_DISCOVERED");
                    broadcastUpdateAction(ACTION_GATT_SERVICES_DISCOVERED);
                    return;
                }

                // No supported Service exist.
                {
                    disconnect();
                    Log.e(TAG, "onServicesDiscovered disconnect() with no supported service");
                    broadcastUpdateAction(ACTION_GATT_DISCONNECTED);
                }

            } else {
                Log.e(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onCharacteristicRead(): send onCharacteristicRead");
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } else {
                Log.e(TAG, "onCharacteristicRead received: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onCharacteristicWrite(): send onCharacteristicWrite");

                if (CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID.equals(characteristic.getUuid())) {
                    byte[] value = characteristic.getValue();
                    Log.i(TAG, "onCharacteristicWrite CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID response");
                }
                if (CDR_USER_WRITE_CHARACTERISTIC_ID.equals(characteristic.getUuid())) {
                    byte[] value = characteristic.getValue();
                    Log.i(TAG, "onCharacteristicWrite CDR_USER_WRITE_CHARACTERISTIC_ID response");
                }
                broadcastUpdateAction(ACTION_DATA_WRITE_DONE);
            } else {
                Log.e(TAG, "onCharacteristicWrite received: " + status);
                if (CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID.equals(characteristic.getUuid())) {
                    byte[] value = characteristic.getValue();
                    Log.e(TAG, "onCharacteristicWrite CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID response");
                }
                if (CDR_USER_WRITE_CHARACTERISTIC_ID.equals(characteristic.getUuid())) {
                    byte[] value = characteristic.getValue();
                    Log.e(TAG, "onCharacteristicWrite CDR_USER_WRITE_CHARACTERISTIC_ID response");
                }
                broadcastUpdateAction(ACTION_DATA_WRITE_FAIL);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.e(TAG, "onCharacteristicChanged(): come from " + characteristic.getUuid().toString());

            if (CDR_USER_WRITE_CHARACTERISTIC_ID.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                Log.i(TAG, "onCharacteristicChanged CDR_USER_WRITE_CHARACTERISTIC_ID response: " + PrintBytesToHexString(value));
                broadcastUpdate(ACTION_ANCS_DATA_NOTIFY, characteristic);
                return;
            }

            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //Log.i(TAG, "onDescriptorRead(): OK");
                broadcastUpdateNotify(descriptor);
            } else {
                Log.e(TAG, "onDescriptorRead() received: " + status);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdateNotify(descriptor);
            } else {
                Log.e(TAG, "onDescriptorWrite() received: " + status);
            }
        }
    };

    private void broadcastUpdateAction(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdateNotify(BluetoothGattDescriptor descriptor) {
        final Intent intent = new Intent(BluetoothLeService.ACTION_CHECK_NOTIFY);

        if (Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
            intent.putExtra(EXTRA_DATA, true);
        else
            intent.putExtra(EXTRA_DATA, Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE));

        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.i(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.i(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.i(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else if (CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                Log.i(TAG, "broadcastUpdate() : " + Arrays.toString(data));
                intent.putExtra(EXTRA_DATA, new String(data));
                Log.i(TAG, "broadcastUpdate() putExtra: " + intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        } else if (CDR_USER_WRITE_CHARACTERISTIC_ID.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                Log.i(TAG, "broadcastUpdate() : " + Arrays.toString(data));
                intent.putExtra(EXTRA_DATA, new String(data));
                Log.i(TAG, "broadcastUpdate() putExtra: " + intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return true;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return true;
        } else {
            Log.i(TAG, "mBluetoothAdapter.getAddress() = " + mBluetoothAdapter.getAddress());
        }

        return false;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.e(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.i(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                return true;
            } else {
                return false;
            }
        }

        Log.w(TAG, "connect() addr = " + address);
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        Log.w(TAG, "mBluetoothAdapter.getRemoteDevice() addr = " + device.getAddress());
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.i(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "BluetoothAdapter not initialized");
            return;
        }

        byte[] set_value = characteristic.getValue();
        Log.i(TAG, "writeCharacteristic() : " + Arrays.toString(set_value));
        Log.i(TAG, "writeCharacteristic() : " + PrintBytesToHexString(set_value));
        // characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    // for DeviceControlActivity
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        boolean isEnableNotification = mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        Log.w(TAG, "setCharacteristicNotification isEnableNotification = " + ((isEnableNotification) ? "true" : "false)"));

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
            Log.w(TAG, "writeDescriptor: " + characteristic.getUuid().toString());
        }

        if ("00002a2b-0000-1000-8000-00805f9b34fb".equals(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
            Log.w(TAG, "writeDescriptor: " + characteristic.getUuid().toString());
        }

        if ("0A0A1012-4E41-4249-5F49-445F42415345".equalsIgnoreCase(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
            Log.w(TAG, "writeDescriptor: " + characteristic.getUuid().toString());
        }
    }

    // for DemoActivity
    public final void enableNotifications(final BluetoothGattCharacteristic characteristic, boolean enabled) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null) {
            Log.e(TAG, "BluetoothAdapter not initialized");
            return;
        }

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            Log.e(TAG, "The characteristic property do not support PROPERTY_NOTIFY");
            return;
        }

        gatt.setCharacteristicNotification(characteristic, enabled);
        BluetoothGattDescriptor descriptor;
        descriptor = characteristic.getDescriptor(PND_CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor != null) {
            if (enabled)
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            else
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            return;
        }

        descriptor = characteristic.getDescriptor(IVT_CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor != null) {
            if (enabled)
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            else
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            return;
        }

        Log.e(TAG, "Enable notifications fail.");
    }

    public final void checkNotifications(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null) {
            Log.e(TAG, "BluetoothAdapter not initialized");
            return;
        }

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            Log.e(TAG, "The characteristic property do not support PROPERTY_NOTIFY");
            return;
        }

        BluetoothGattDescriptor descriptor;
        descriptor = characteristic.getDescriptor(PND_CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor != null) {
            gatt.readDescriptor(descriptor);
            return;
        }

        descriptor = characteristic.getDescriptor(IVT_CLIENT_CHARACTERISTIC_CONFIG);
        if (descriptor != null) {
            gatt.readDescriptor(descriptor);
            return;
        }

        Log.e(TAG, "Check descriptor fail.");
    }

    public List<String> getPairedBTDevice() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        List<String> result_list = new ArrayList<>();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.i(TAG, "found: " + device.getAddress() + "(" + device.getName() + ")");
                result_list.add(device.getAddress());
            }
        }

        return result_list;
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }

    public String PrintBytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        for (byte b : src) {
            int v = b & 0xFF;
            String hv = " 0x" + Integer.toHexString(v).toUpperCase();
            stringBuilder.append(hv);
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }
}
