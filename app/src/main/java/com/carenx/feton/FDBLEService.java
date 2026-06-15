package com.carenx.feton;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * BLE Service for handling chunked data reception from BLE Type 2 fetal doppler devices.
 * This service maintains the same interface as FDBluetoothService but uses BLE GATT
 * instead of classic Bluetooth RFCOMM.
 */
public class FDBLEService {
    // Debugging
    private static final String TAG = "FDBLEService";
    private static final boolean D = true;

    // BLE Service and Characteristic UUIDs - Update these based on your device
    private static final UUID FETAL_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
//    private static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
//    private static final UUID CHARACTERISTIC_UUID_RX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
//    private static final UUID CHARACTERISTIC_UUID_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final UUID FETAL_CHAR_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID CONTROL_CHAR_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");

    // Connection states
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    // Disconnect command constant (from memory about graceful disconnect)
    private static final byte DISCONNECT_COMMAND = (byte) 0xFF;

    // Member fields
    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mTxCharacteristic;
    private BluetoothGattCharacteristic mRxCharacteristic;
    private int mConnectionState = STATE_NONE;
    private Context mContext;
    private String mDeviceAddress;
    private String mId;

    // Frame buffer for chunked data reassembly
    private byte[] frameBuffer = new byte[162];
    private int bufferPosition = 0;
    private boolean isReceivingFrame = false;
    private long lastChunkTime = 0;
    private static final long CHUNK_TIMEOUT_MS = 1000; // 1 second timeout for incomplete frames

    // File writing
    private PrintWriter out_e = null;
    private File file_e = null;
    private String fileName;

    /**
     * Constructor
     */
    public FDBLEService(Context context, Handler handler, String id) {
        mContext = context;
        mHandler = handler;
        mId = id;

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        fileName = createFileNameByTimeStamp();

        if (D) Logger.d(TAG, "FDBLEService created");
    }

    /**
     * Set the current state of the connection
     */
    private synchronized void setState(int state) {
        if (D) Logger.d(TAG, "setState() " + mConnectionState + " -> " + state);

        int previousState = mConnectionState;
        mConnectionState = state;

        // Handle connection lost scenario
        if (previousState == STATE_CONNECTED && state == STATE_LISTEN) {
            connectionLost();
        }

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state
     */
    public synchronized int getState() {
        return mConnectionState;
    }

    /**
     * Start the service in listening mode
     */
    public synchronized void start() {
        if (D) Logger.d(TAG, "start");

        // Reset buffer
        resetFrameBuffer();
        setState(STATE_LISTEN);
    }

    /**
     * Connect to a BLE device
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Logger.d(TAG, "connect to: " + device);

        mDeviceAddress = device.getAddress();

        // Cancel any existing connection
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        setState(STATE_CONNECTING);

        // Connect to the GATT server
        mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
    }

    /**
     * Stop all connections and clean up
     */
    public synchronized void stop() {
        if (D) Logger.d(TAG, "stop");

        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        resetFrameBuffer();
        setState(STATE_NONE);
    }

    /**
     * Graceful disconnect - sends disconnect command before closing connection
     * (Based on memory about proper device disconnection)
     */
    public synchronized void gracefulDisconnect() {
        if (D) Logger.d(TAG, "gracefulDisconnect");

        if (mConnectionState == STATE_CONNECTED && mRxCharacteristic != null) {
            sendDisconnectCommand();

            // Wait a bit for device to process disconnect command
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stop();
                }
            }, 500);
        } else {
            stop();
        }
    }

    /**
     * Send disconnect command to device
     */
    private void sendDisconnectCommand() {
        if (mRxCharacteristic != null && mBluetoothGatt != null) {
            byte[] disconnectCmd = {DISCONNECT_COMMAND};
            mRxCharacteristic.setValue(disconnectCmd);
            boolean result = mBluetoothGatt.writeCharacteristic(mRxCharacteristic);
            if (D) Logger.d(TAG, "Disconnect command sent: " + result);
        }
    }

    /**
     * Write data to the connected device
     */
    public void write(byte[] data) {
        if (mConnectionState != STATE_CONNECTED || mRxCharacteristic == null) {
            Logger.w(TAG, "write: not connected or characteristic not available");
            return;
        }

        mRxCharacteristic.setValue(data);
        boolean result = mBluetoothGatt.writeCharacteristic(mRxCharacteristic);

        if (result) {
            // Send write confirmation to handler
            mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_WRITE, -1, -1, data).sendToTarget();
        }
    }

    /**
     * BLE GATT Callback
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (D) Logger.d(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (D) Logger.d(TAG, "onConnectionStateChange: device=" + gatt.getDevice().getName());
                // Send device name to handler
                Message msg = mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_DEVICE_NAME);
                Bundle bundle = new Bundle();
                bundle.putString(BluetoothMeasureParameter.DEVICE_NAME, gatt.getDevice().getName());
                msg.setData(bundle);
                mHandler.sendMessage(msg);

                setState(STATE_CONNECTED);

                // Discover services
                mBluetoothGatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setState(STATE_NONE);
                resetFrameBuffer();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (D) Logger.d(TAG, "onServicesDiscovered: status=" + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Find the service and characteristics
                BluetoothGattService service = gatt.getService(FETAL_SERVICE_UUID);
                if (service != null) {
                    mTxCharacteristic = service.getCharacteristic(FETAL_CHAR_UUID);
                    mRxCharacteristic = service.getCharacteristic(CONTROL_CHAR_UUID);

                    if (mTxCharacteristic != null) {
                        // Enable notifications for TX characteristic (data from device)
                        gatt.setCharacteristicNotification(mTxCharacteristic, true);

                        BluetoothGattDescriptor descriptor = mTxCharacteristic.getDescriptor(DESCRIPTOR_UUID);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                } else {
                    Logger.e(TAG, "Service not found: " + FETAL_SERVICE_UUID);
                    connectionFailed();
                }
            } else {
                Logger.e(TAG, "onServicesDiscovered failed: " + status);
                connectionFailed();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(FETAL_CHAR_UUID)) {
                byte[] data = characteristic.getValue();
                Log.e("onCharacteristicChanged", "HeartRate processData: " + Arrays.toString(data) + " length: " + data.length);

                if (data != null && data.length > 0) {
                    processDataChunk(data);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (D) Logger.d(TAG, "onCharacteristicWrite: status=" + status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (D) Logger.d(TAG, "onDescriptorWrite: status=" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Logger.d(TAG, "Notifications enabled successfully");
            }
        }
    };

    private List<Byte> byteArrayList = new ArrayList<>();

    public void addChunk(byte[] data) {
        try {
            // Check for start sequence
            if (data.length >= 3 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xA0 && (data[2] & 0xFF) == 0x05) {
                byteArrayList.clear();
                for (byte b : data) {
                    byteArrayList.add(b);
                }
            } else {
                for (byte b : data) {
                    byteArrayList.add(b);
                }
            }

            int count = byteArrayList.size();
            if (count == 107 || count == 117) {
                byte[] fullMessage = new byte[count];
                for (int i = 0; i < count; i++) {
                    fullMessage[i] = byteArrayList.get(i);
                }

                // Assuming GetSensorData is like sending a message handler in your environment
                // ✅ Send message via handler
                Message msg = mHandler.obtainMessage(
                        BluetoothMeasureParameter.MESSAGE_READ,
                        fullMessage.length,
                        -1,
                        fullMessage
                );
                msg.sendToTarget();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Process incoming data chunks and reassemble into complete frames
     */
    private void processDataChunk(byte[] chunk) {
        addChunk(chunk);
    }

    /**
     * Reset the frame buffer
     */
    private void resetFrameBuffer() {
        frameBuffer = new byte[162];
        bufferPosition = 0;
        isReceivingFrame = false;
    }

    /**
     * Handle connection failure
     */
    private void connectionFailed() {
        Message msg = mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothMeasureParameter.TOAST,
                mContext.getResources().getString(R.string.unable_to_connect_device));
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_NONE);
        start(); // Restart listening
    }

    /**
     * Handle connection lost
     */
    private void connectionLost() {
        Message msg = mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothMeasureParameter.TOAST,
                mContext.getResources().getString(R.string.device_connection_lost));
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        resetFrameBuffer();
        start(); // Restart listening
    }

    /**
     * Convert byte array to hex string for debugging
     */
    private String byteArrayToHexString(byte[] b) {
        StringBuilder data = new StringBuilder();
        for (byte value : b) {
            data.append(String.format("%02x ", value & 0xFF));
        }
        return data.toString();
    }

    /**
     * Write data to file for debugging
     */
    private void writeToFile(String loggerMsg) {
        if (out_e == null) {
            String folderName = mContext.getExternalFilesDir(null) + File.separator + "Remedi" + File.separator + "BLE_Debug" + File.separator;
            file_e = new File(folderName);

            File file = new File(file_e, fileName);
            if (!file.exists()) {
                try {
                    file_e.mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                out_e = new PrintWriter(new BufferedWriter(new FileWriter(file, false)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (out_e != null) {
            out_e.println(loggerMsg);
            out_e.flush();
        }
    }

    /**
     * Create filename with timestamp
     */
    private String createFileNameByTimeStamp() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        Date date = new Date();
        return "BLE_Data_" + dateFormat.format(date) + ".txt";
    }

    /**
     * Get the connected device address
     */
    public String getConnectedDeviceAddress() {
        return mDeviceAddress;
    }

    /**
     * Check if device is connected
     */
    public boolean isConnected() {
        return mConnectionState == STATE_CONNECTED;
    }
}