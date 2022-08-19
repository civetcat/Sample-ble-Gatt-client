package com.example.sampleblegattclient;

import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static com.example.sampleblegattclient.SampleGattAttributes.CDR_SERVICE_ID;
import static com.example.sampleblegattclient.SampleGattAttributes.CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID;
import static com.example.sampleblegattclient.SampleGattAttributes.CDR_USER_WRITE_CHARACTERISTIC_ID;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DemoActivity extends AppCompatActivity {
    private final static String TAG = DemoActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    // filename code: 01
    private static final String FILENAME_INCIDENT = "PNDincident.zip";
    private static final String sendfile_end_string = "END:";
    public final int TX_STATUS_IDLE = 0;
    public final int TX_STATUS_TRANSFER = TX_STATUS_IDLE + 1;
    public final int TX_STATUS_END = TX_STATUS_TRANSFER + 1;
    public final int TX_DATA_MAX_LENGTH = 20;

    private TextView mConnectionState;
    private TextView mDataField;
    private TextView mDataStringField;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private boolean mNotify_states = false;

    private TextView mTextView_label_service1_uuid;
    private TextView mTextView_label_character1_uuid;
    private TextView mTextView_label_character2_uuid;
    private Button mButton_character1_write_nabi_fw_info;
    private ProgressDialog mWaitingDialog;

    private BluetoothGattService mGattService = null;
    private BluetoothGattCharacteristic mGattCharacteristicReadNotify = null;
    private BluetoothGattCharacteristic mGattCharacteristicWrite = null;

    private final byte[] bytes_mem = null;
    private byte[] mTmpBuf = null;
    private int mTxStatus;
    private final int mTxTotal = 0;
    private int mTxCurrentSize;
    private String start_time_string;

    private byte single_cmd_opcode;
    private byte single_cmd_command;
    private byte single_cmd_value;

    private final byte[] byte_MD5 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        // get parameters from intent
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        initView();

        // BLE Service
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //
        mTxStatus = TX_STATUS_IDLE;
        mTmpBuf = new byte[TX_DATA_MAX_LENGTH];
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.i(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private void initView() {
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = findViewById(R.id.connection_state);
        mDataField = findViewById(R.id.income_data_value);
        mDataStringField = findViewById(R.id.income_data_string);
        mTextView_label_service1_uuid = (findViewById(R.id.label_service1_uuid));
        mTextView_label_character1_uuid = (findViewById(R.id.label_character1_uuid));
        mTextView_label_character2_uuid = (findViewById(R.id.label_character2_uuid));
        findViewById(R.id.label_character3_uuid);
        Button mButton_character1_read = (findViewById(R.id.button_character1_read));
        Button mButton_character1_write = (findViewById(R.id.button_character1_write));
        Button mButton_character1_notify = (findViewById(R.id.button_character1_notify));
        mButton_character1_write_nabi_fw_info = (findViewById(R.id.button_character1_write_nabi_fw_info));
        mButton_character1_write_nabi_fw_info.setVisibility(View.GONE);
        Button mButton_character1_write1_single_cmd_string = (findViewById(R.id.button_character1_write_single_cmd_string));
        mButton_character1_write1_single_cmd_string.setVisibility(View.GONE);
        Button mButton_character2_read = (findViewById(R.id.button_character2_read));
        Button mButton_character2_write = (findViewById(R.id.button_character2_write));
        Button mButton_character2_notify = (findViewById(R.id.button_character2_notify));
        Button mButton_character3_write_sms = (findViewById(R.id.button_character3_write_sms));
        Button mButton_character3_write_phonecall = (findViewById(R.id.button_character3_write_phonecall));
        Button mButton_character3_write_whatsapp = (findViewById(R.id.button_character3_write_whatsapp));
        mButton_character1_read.setEnabled(false);
        mButton_character1_write.setEnabled(false);
        mButton_character1_notify.setEnabled(false);
        mButton_character1_write_nabi_fw_info.setEnabled(false);
        mButton_character1_write1_single_cmd_string.setEnabled(false);
        mButton_character2_read.setEnabled(false);
        mButton_character2_write.setEnabled(false);
        mButton_character2_notify.setEnabled(false);
        mButton_character3_write_sms.setEnabled(false);
        mButton_character3_write_phonecall.setEnabled(false);
        mButton_character3_write_whatsapp.setEnabled(false);
        mButton_character1_read.setOnClickListener(mButtonOnClickListener);
        mButton_character1_write.setOnClickListener(mButtonOnClickListener);
        mButton_character1_notify.setOnClickListener(mButtonOnClickListener);
        mButton_character1_write_nabi_fw_info.setOnClickListener(mButtonOnClickListener);
        mButton_character1_write1_single_cmd_string.setOnClickListener(mButtonOnClickListener);
        mButton_character2_read.setOnClickListener(mButtonOnClickListener);
        mButton_character2_write.setOnClickListener(mButtonOnClickListener);
        mButton_character2_notify.setOnClickListener(mButtonOnClickListener);
        mButton_character3_write_sms.setOnClickListener(mButtonOnClickListener);
        mButton_character3_write_phonecall.setOnClickListener(mButtonOnClickListener);
        mButton_character3_write_whatsapp.setOnClickListener(mButtonOnClickListener);

        Objects.requireNonNull(getSupportActionBar()).setTitle(mDeviceName);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }

        // Do not show connect/disconnect in current version
        menu.findItem(R.id.menu_connect).setVisible(false);
        menu.findItem(R.id.menu_disconnect).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Button OnClickListener
    View.OnClickListener mButtonOnClickListener = v -> {
        Button mButton = (Button) v;
        String buttonLabel = mButton.getText().toString();
        switch (v.getId()) {
            case R.id.button_character1_read:
                Log.i(TAG, "Click button " + buttonLabel + " for Character 1");
                setReadCharacteristic(mGattCharacteristicReadNotify);
                break;
            case R.id.button_character1_write:
                Log.i(TAG, "Click button " + buttonLabel + " for Character 1");
                showWriteDialog();
                break;
            case R.id.button_character1_notify:
                Log.i(TAG, "Click button " + buttonLabel + " for Character 1");
                setNotifications(mGattCharacteristicReadNotify, !mNotify_states);
                break;
            case R.id.button_character1_write_nabi_fw_info:
                Log.i(TAG, "Click button " + buttonLabel + " for Character 1");
                //byte[] write_value = new byte[]{-2, 97, 1};
                byte[] write_value = new byte[]{97, 0};
                setWriteByte(mGattCharacteristicReadNotify, write_value);
                break;
            case R.id.button_character1_write_single_cmd_string:
                Log.i(TAG, "Click button " + buttonLabel + " for Character 1");
                showWriteSingleCommandDialog();
                break;
            case R.id.button_character2_read:
            case R.id.button_character2_notify:
            case R.id.button_character2_write:
                Log.i(TAG, "Click button " + buttonLabel + " for Character 2");
                break;
            case R.id.button_character3_write_sms:
            case R.id.button_character3_write_phonecall:
                Log.i(TAG, "Click button " + buttonLabel + " for Character 3");
                break;
            case R.id.button_character3_write_whatsapp:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + v.getId());
        }
    };

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            Log.i(TAG, "onServiceConnected(): connect to addr = " + mDeviceAddress);
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.i(TAG, "onReceive: BluetoothLeService.ACTION_GATT_CONNECTED");
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i(TAG, "onReceive: BluetoothLeService.ACTION_GATT_DISCONNECTED");
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                Log.i(TAG, "onReceive: BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED");
                UUID check_connected_service_uuid = checkGattServices(mBluetoothLeService.getSupportedGattServices());
                if (check_connected_service_uuid != null) {
                    displayGattServices(check_connected_service_uuid);
                    if (check_connected_service_uuid.equals(CDR_SERVICE_ID)) {
                        setNotifications(mGattCharacteristicReadNotify, true);
                    }
                }
            } else if (BluetoothLeService.ACTION_CHECK_NOTIFY.equals(action)) {
                Log.i(TAG, "onReceive: BluetoothLeService.ACTION_CHECK_NOTIFY");
                mNotify_states = intent.getBooleanExtra(BluetoothLeService.EXTRA_DATA, false);
                updateNotifyButton(mNotify_states);
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.i(TAG, "onReceive: BluetoothLeService.ACTION_DATA_AVAILABLE");
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            } else if (BluetoothLeService.ACTION_DATA_WRITE_DONE.equals(action)) {
                Log.i(TAG, "onReceive: BluetoothLeService.ACTION_DATA_WRITE_DONE");
                if (mTxStatus == TX_STATUS_TRANSFER) {
                    if (mTxCurrentSize >= mTxTotal) {
                        mTxStatus = TX_STATUS_END;
                        byte[] mTmpBuf2 = new byte[TX_DATA_MAX_LENGTH];
                        System.arraycopy(sendfile_end_string.getBytes(), 0, mTmpBuf2, 0, sendfile_end_string.getBytes().length);
                        assert false;
                        System.arraycopy(byte_MD5, 0, mTmpBuf2, sendfile_end_string.getBytes().length, byte_MD5.length);
                        writeFile(mGattCharacteristicWrite, mTmpBuf2);
                    } else {
                        int next_data_length = mTxTotal - mTxCurrentSize;
                        if (next_data_length < TX_DATA_MAX_LENGTH) {
                            byte[] last_data = new byte[next_data_length];
                            assert false;
                            System.arraycopy(bytes_mem, mTxCurrentSize, last_data, 0, next_data_length);
                            writeFile(mGattCharacteristicWrite, last_data);
                            Log.i(TAG, "onReceive: Last writing length = " + next_data_length);
                        } else {
                            assert false;
                            System.arraycopy(bytes_mem, mTxCurrentSize, mTmpBuf, 0, TX_DATA_MAX_LENGTH);
                            writeFile(mGattCharacteristicWrite, mTmpBuf);
                        }
                    }
                }
            }
        }
    };

    private UUID checkGattServices(List<BluetoothGattService> gattServicesList) {
        Log.i(TAG, "checkGattServices()");

        // check input
        if (gattServicesList == null) {
            Log.e(TAG, "displayGattServices: null list");
            return null;
        }

        // check supported service
        for (int i = 0; i < gattServicesList.size(); i++) {
            if (gattServicesList.get(i).getUuid().equals(CDR_SERVICE_ID)) {
                mGattService = gattServicesList.get(i);
                Log.i(TAG, "Find supported service CDR_SERVICE_ID");
                break;
            }
        }
        if (mGattService == null) {
            Log.e(TAG, "No supported service existed.");
            return null;
        }

        // check supported Characteristic
        if (mGattService.getUuid().equals(CDR_SERVICE_ID)) {
            mGattCharacteristicReadNotify = mGattService.getCharacteristic(CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID);
            mGattCharacteristicWrite = mGattService.getCharacteristic(CDR_USER_WRITE_CHARACTERISTIC_ID);
            if (mGattService.getCharacteristic(CDR_USER_READ_NOTIFY_CHARACTERISTIC_ID) == null ||
                    mGattService.getCharacteristic(CDR_USER_WRITE_CHARACTERISTIC_ID) == null) {
                Log.e(TAG, "( CDR ) No supported characteristic existed.");
                return null;
            }
            return mGattService.getUuid();
        }

        Log.e(TAG, "Supported service existed but contain error CHARACTERISTIC ID");
        return null;
    }

    private void displayGattServices(UUID service_uuid) {
        Log.i(TAG, "displayGattServices(): " + service_uuid.toString());
        mTextView_label_service1_uuid.setText(service_uuid.toString());
        if (mGattCharacteristicReadNotify != null) {
            mTextView_label_character1_uuid.setText(mGattCharacteristicReadNotify.getUuid().toString());
        }
        if (mGattCharacteristicWrite != null) {
            mTextView_label_character2_uuid.setText(mGattCharacteristicWrite.getUuid().toString());
        }
        if (service_uuid.equals(CDR_SERVICE_ID)) {
            Log.d(TAG, "CDR_SERVICE_ID");
        }
    }

    private boolean hasProperty(BluetoothGattCharacteristic characteristic, int property) {
        return (characteristic.getProperties() & property) > 0;
    }

    private void setNotifications(BluetoothGattCharacteristic characteristic, boolean enabled) {
        mBluetoothLeService.enableNotifications(characteristic, enabled);
    }

    private void getNotifications(BluetoothGattCharacteristic characteristic) {
        mBluetoothLeService.checkNotifications(characteristic);
    }

    private void setWriteString(BluetoothGattCharacteristic characteristic, String write_value) {
        characteristic.setValue(write_value);
        mBluetoothLeService.writeCharacteristic(characteristic);
    }

    private void setWriteByte(BluetoothGattCharacteristic characteristic, byte[] write_value) {
        characteristic.setValue(write_value);
        mBluetoothLeService.writeCharacteristic(characteristic);
    }

    private void setReadCharacteristic(BluetoothGattCharacteristic characteristic) {
        mBluetoothLeService.readCharacteristic(characteristic);
    }

    private void showWriteDialog() {
        // get layout
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.dialog_write, null);

        // AlertDialog
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        //alertDialogBuilder.setTitle("Title");

        // set layout to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final EditText input = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);

        // Set up the buttons
        alertDialogBuilder.setPositiveButton("OK", (dialog, which) -> {
            String m_Text = input.getText().toString();
            Log.i(TAG, "User input: " + m_Text);
            if (mGattService.getUuid().equals(CDR_SERVICE_ID))
                setWriteString(mGattCharacteristicReadNotify, m_Text);
        });
        alertDialogBuilder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        alertDialogBuilder.show();
    }

    private void showWriteSingleCommandDialog() {
        // get layout
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.dialog_write_single_command, null);

        // AlertDialog
        AlertDialog.Builder alertDialogBuilder = new androidx.appcompat.app.AlertDialog.Builder(this);
        //alertDialogBuilder.setTitle("Title");

        // set layout to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final EditText et_opcode = (EditText) promptsView.findViewById(R.id.et_opcode);
        final EditText et_command = (EditText) promptsView.findViewById(R.id.et_command);
        final EditText et_value = (EditText) promptsView.findViewById(R.id.et_value);

        et_opcode.setText("99");
        et_opcode.clearFocus();
        et_command.clearFocus();
        et_value.clearFocus();

        // Set up the buttons
        alertDialogBuilder.setPositiveButton("OK", (dialog, which) -> {
            String mOPCode = et_opcode.getText().toString();
            String mCommand = et_command.getText().toString();
            String mValue = et_value.getText().toString();

            if (mOPCode.isEmpty()) {
                showValueErrorDialog();
                return;
            }
            if (mCommand.isEmpty()) {
                showValueErrorDialog();
                return;
            }
            if (mValue.isEmpty()) {
                showValueErrorDialog();
                return;
            }

            int value_opcode = Integer.parseInt(mOPCode);
            int value_command = Integer.parseInt(mCommand);
            int value_val = Integer.parseInt(mValue);

            if (value_opcode >= 0x0 && value_opcode <= 0xFF) {
                Log.i(TAG, "User input OPCode: " + value_opcode + " (0x" + Integer.toHexString(value_opcode) + ")");
            } else {
                showValueErrorDialog();
                return;
            }

            if (value_command >= 0x0 && value_command <= 0xFF) {
                Log.i(TAG, "User input Command: " + value_command + " (0x" + Integer.toHexString(value_command) + ")");
            } else {
                showValueErrorDialog();
                return;
            }

            if (value_val >= 0x0 && value_val <= 0xFF) {
                Log.i(TAG, "User input Value: " + value_val + " (0x" + Integer.toHexString(value_val) + ")");
            } else {
                showValueErrorDialog();
                return;
            }

            single_cmd_opcode = (byte) value_opcode;
            single_cmd_command = (byte) value_command;
            single_cmd_value = (byte) value_val;

            byte[] write_command = new byte[3];
            write_command[0] = single_cmd_opcode;
            write_command[1] = single_cmd_command;
            write_command[2] = single_cmd_value;

            setWriteByte(mGattCharacteristicReadNotify, write_command);
        });
        alertDialogBuilder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        alertDialogBuilder.show();
    }

    private void showWriteFailDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(DemoActivity.this);
        builder.setTitle(getString(R.string.label_title_error))
                .setMessage(getString(R.string.label_error_msg_write_single_cmd))
                .setPositiveButton(getString(R.string.label_ok), (dialogInterface, i) -> {
                    //
                })
                .show();
    }

    private void showValueErrorDialog() {
        Log.i(TAG, "showValueErrorDialog()");

        //
        String err_msg = "The input value is invalid !";

        AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(DemoActivity.this);
        builder.setTitle(getString(R.string.label_title_error))
                .setMessage(err_msg)
                .setPositiveButton(getString(R.string.label_ok), (dialogInterface, i) -> {
                    //
                })
                .show();
    }

    private void showFileAlertDialog() {
        //
        File folder_location = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
        String err_msg = "The " + FILENAME_INCIDENT + " is not located at " + folder_location;

        AlertDialog.Builder builder = new AlertDialog.Builder(DemoActivity.this);
        builder.setTitle(getString(R.string.label_title_error))
                .setMessage(err_msg)
                .setPositiveButton(getString(R.string.label_ok), (dialogInterface, i) -> {
                    //
                })
                .show();
    }

    private void showReadFileFailDialog() {
        //
        String err_msg = getString(R.string.label_read_file_fail);

        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(DemoActivity.this);
        builder.setTitle(getString(R.string.label_title_error))
                .setMessage(err_msg)
                .setPositiveButton(getString(R.string.label_ok), (dialogInterface, i) -> {
                    //
                })
                .show();
    }

    private void showWaitingDialog() {
        if (mWaitingDialog == null) {
            mWaitingDialog = ProgressDialog.show(DemoActivity.this,
                    "",
                    getString(R.string.label_loading),
                    true);
        }
        Log.i(TAG, "showWaitingDialog()");
    }

    private void dismissWaitingDialog() {
        if (mWaitingDialog != null) {
            mWaitingDialog.dismiss();
            mWaitingDialog = null;
        }

        Log.i(TAG, "dismissWaitingDialog()");
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE_DONE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE_FAIL);
        intentFilter.addAction(BluetoothLeService.ACTION_CHECK_NOTIFY);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_DEBUG);
        intentFilter.addAction(BluetoothLeService.ACTION_ANCS_DATA_WRITE_DONE);
        intentFilter.addAction(BluetoothLeService.ACTION_ANCS_DATA_NOTIFY);

        return intentFilter;
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(() -> mConnectionState.setText(resourceId));
    }

    private void displayData(String data) {
        if (data != null) {
            Log.i(TAG, "data = " + data);
            mDataStringField.setText(data);

            byte[] byte_data = data.getBytes();
            mDataField.setText(Arrays.toString(byte_data));
        } else {
            Log.v(TAG, "income data is null");
        }
    }

    private void updateNotifyButton(boolean notify_enabled) {
        if (notify_enabled) {
            updateConnectionState(R.string.connected_with_subscribe);
            mButton_character1_write_nabi_fw_info.setEnabled(true);
        } else {
            updateConnectionState(R.string.connected_with_unsubscribe);
            mButton_character1_write_nabi_fw_info.setEnabled(false);
        }
    }

    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    private boolean checkFile() {
        File folder_location = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
        File mFileLocation = new File(folder_location, FILENAME_INCIDENT);
        if (!mFileLocation.exists()) {
            Log.e(TAG, "The " + FILENAME_INCIDENT + " is not located at " + folder_location);
            Log.e(TAG, "It should be located at: " + mFileLocation.getPath());
            return false;
        }

        return true;
    }

    private void writeFile(BluetoothGattCharacteristic characteristic, byte[] write_value) {
        if (characteristic != null) {
            if (mTxStatus == TX_STATUS_IDLE) {
                characteristic.setValue(write_value);
                mBluetoothLeService.writeCharacteristic(characteristic);
                mTxCurrentSize = 0;
                mTxStatus = TX_STATUS_TRANSFER;
                start_time_string = getCurrentTimeString();
                Log.e(TAG, "Start File TX at " + start_time_string);
            } else if (mTxStatus == TX_STATUS_TRANSFER) {
                characteristic.setValue(write_value);
                mBluetoothLeService.writeCharacteristic(characteristic);
                mTxCurrentSize = mTxCurrentSize + write_value.length;
                Log.i(TAG, "mTxCurrentSize = " + mTxCurrentSize);
            } else if (mTxStatus == TX_STATUS_END) {
                characteristic.setValue(write_value);
                mBluetoothLeService.writeCharacteristic(characteristic);
                mTxCurrentSize = 0;
                mTxStatus = TX_STATUS_IDLE;
                String end_time_string = getCurrentTimeString();
                Log.e(TAG, "End File TX at " + end_time_string);
                Log.e(TAG, "File size is " + mTxTotal + " Byte");
                Log.e(TAG, "File TX cost " + getCalculateTimeResult(start_time_string, end_time_string) + " ms");

                dismissWaitingDialog();
                setScreenKeepOFF();
            }
        } else {
            Log.e(TAG, "writeFile() Error: null mGattCharacteristics");
        }
    }

    private String getCurrentTimeString() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss:SSS");
        return format.format(new Date());
    }

    private Long getCalculateTimeResult(String start_time, String end_time) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss:SSS");
        Date dt1;
        Date dt2;
        try {
            dt1 = format.parse(start_time);
            dt2 = format.parse(end_time);
        } catch (ParseException e) {
            e.printStackTrace();
            return 0L;
        }
        Long ut1 = dt1.getTime();
        Long ut2 = dt2.getTime();

        return ut2 - ut1;
    }

    public String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }

        Log.e(TAG, "bytesToHexString()-byte[]: " + Arrays.toString(src));

        for (byte b : src) {
            int v = b & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    private void setScreenKeepON() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void setScreenKeepOFF() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
