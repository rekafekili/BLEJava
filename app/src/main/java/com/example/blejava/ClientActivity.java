package com.example.blejava;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

// Searching : https://nerdranchighq.wpengine.com/blog/bluetooth-low-energy-on-android-part-2/
public class ClientActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_ENABLE_BT = 3333;
    private static final String TAG = "BLEBLE";
    private static final int REQUEST_FINE_LOCATION = 5005;
    private static final long SCAN_PERIOD = 3000;
    private static final UUID SERVICE_UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214");

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning = false;
    private HashMap<String, BluetoothDevice> mScanResults;
    private BtleScanCallback mScanCallback;
    private BluetoothLeScanner mBluetoothScanner;
    private Handler mHandler;
    private BluetoothGatt mGatt;
    private boolean mConnected = false;
    private boolean mInitialized = false;

    /* View */
    private Button mStartScanButton;
    private Button mStopScanButton;
    private Button mConnectButton;
    private EditText mSendingEditText;
    private Button mSendingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        viewBining();

        // 기본적인 BLE 동작을 할 수 있도록 도와주는 BluetoothAdapter를 생성한다.
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    private void viewBining() {
        mStartScanButton = findViewById(R.id.client_start_scan_button);
        mStopScanButton = findViewById(R.id.client_stop_scan_button);
        mConnectButton = findViewById(R.id.client_connect_button);
        mSendingEditText = findViewById(R.id.client_sending_edittext);
        mSendingButton = findViewById(R.id.client_sending_button);

        mStartScanButton.setOnClickListener(this);
        mStopScanButton.setOnClickListener(this);
        mConnectButton.setOnClickListener(this);
        mSendingButton.setOnClickListener(this);
    }

    // Scanning
    // 현재 안드로이드 기기가 BLE 지원이 되는지 확인한다.
    @Override
    protected void onResume() {
        super.onResume();
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();
        }
    }

    private void stopScan() {
        if(mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothScanner != null) {
            mBluetoothScanner.stopScan(mScanCallback);
            scanComplete();
        }

        mScanCallback = null;
        mScanning = false;
        mHandler = null;
    }

    private void scanComplete() {
        if(mScanResults.isEmpty()) {
            return;
        }

        for(String deviceAddress : mScanResults.keySet()) {
            Log.d(TAG, "Found Device : " + deviceAddress);
        }
    }

    private void startScan() {
        if(!hasPermission() || mScanning) {
            return;
        }

        List<ScanFilter> filters = new ArrayList<>();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        mScanResults = new HashMap<>();
        mScanCallback = new BtleScanCallback();
        mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothScanner.startScan(filters, settings, mScanCallback);
        mScanning = true;
        mHandler = new Handler();
        mHandler.postDelayed(this::stopScan, SCAN_PERIOD);
    }

    private boolean hasPermission() {
        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            requestBluetoothEnable();
            return false;
        } else if(!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        return true;
    }

    private boolean hasLocationPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        Log.d(TAG, "Requested user enables Bluetooth. Try starting the scan again.");
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.client_start_scan_button:
                startScan();
                break;
            case R.id.client_stop_scan_button:
                stopScan();
                break;
            case R.id.client_connect_button:
                clickConnectButton();
                break;
            case R.id.client_sending_button:
                sendingMessage();
                break;
        }
    }

    private void sendingMessage() {
        if(!mConnected || !mInitialized) {
            return;
        }
        BluetoothGattService service = mGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        String message = mSendingEditText.getText().toString();

        // In order to send the data we must first convert out String to byte[]
        byte[] messageBytes = new byte[0];
        messageBytes = message.getBytes(StandardCharsets.UTF_8);

        characteristic.setValue(messageBytes);
        boolean success = mGatt.writeCharacteristic(characteristic);
        Log.d(TAG, "Write Result : " + success);
    }

    private void clickConnectButton() {
        if(mScanResults.containsKey("C0:50:A0:C6:3E:16")) {
            BluetoothDevice bluetoothDevice = mScanResults.get("C0:50:A0:C6:3E:16");
            connectDevice(bluetoothDevice);
        }
    }

    private void connectDevice(BluetoothDevice device) {
        Log.d(TAG, "Device Name : " + device.getName());
        Log.d(TAG, "Device UUID : " + Arrays.toString(device.getUuids()));

        GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, false, gattClientCallback);
    }

    private class BtleScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for(ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan Failed with code : " + errorCode);
        }

        private void addScanResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            mScanResults.put(deviceAddress, device);
        }
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "BluetoothGatt : " + gatt);
            Log.d(TAG, "Status : " + status);
            Log.d(TAG, "New Status : " + newState);

            if(status == BluetoothGatt.GATT_FAILURE) {
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer();
                return;
            }

            // After receiving GATT_SUCCESS and STATE_CONNECTED
            // We Must DISCOVER the services of the GATT Server
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                mConnected = true;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
            }

            Log.d(TAG, "Connected with GattServer");
        }

        // We Must implement this Callback Method
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if(status != BluetoothGatt.GATT_SUCCESS) {
                return;
            }
            Log.d(TAG, "Discovered Service : " + gatt.getServices());

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            Log.d(TAG, "Bluetooth GATT Service : " + service.toString());
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            Log.d(TAG, "Bluetooth GATT Char : " + characteristic.toString());

            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            mInitialized = gatt.setCharacteristicNotification(characteristic, true);
            Log.d(TAG, "Initialized : " + mInitialized);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            Log.d(TAG, "Success Status : " + status);
            Log.d(TAG, "Success Write : " + characteristic.getStringValue(0));
        }

        // When Server send a message, we can received data from it
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            byte[] messageBytes = characteristic.getValue();
            String messageString;
            messageString = new String(messageBytes, StandardCharsets.UTF_8);
            Log.d(TAG, "Received Message : " + messageString);
        }

        private void disconnectGattServer() {
            mConnected = false;
            mInitialized = false;
            if(mGatt != null) {
                mGatt.disconnect();
                mGatt.close();
            }
        }
    }
}