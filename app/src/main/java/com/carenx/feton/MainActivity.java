package com.carenx.feton;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements
        BluetoothMeasureParameter.BluetoothConnectionStatus,
        BluetoothMeasureParameter.FetalDopplerReading {

    private static final int REQUEST_PERMISSIONS = 1;

    private BluetoothMeasureParameter measureParameter;

    private EditText editSensorName;
    private SwitchMaterial switchBle;
    private Button btnConnect, btnDisconnect, btnStartReading;
    private TextView textStatus, textDeviceName, textHeartRate, textToco, textAfm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editSensorName = findViewById(R.id.edit_sensor_name);
        switchBle = findViewById(R.id.switch_ble);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnStartReading = findViewById(R.id.btn_start_reading);
        textStatus = findViewById(R.id.text_status);
        textDeviceName = findViewById(R.id.text_device_name);
        textHeartRate = findViewById(R.id.text_heart_rate);
        textToco = findViewById(R.id.text_toco);
        textAfm = findViewById(R.id.text_afm);

        btnConnect.setOnClickListener(v -> onConnectClicked());
        btnDisconnect.setOnClickListener(v -> onDisconnectClicked());
        btnStartReading.setOnClickListener(v -> {
            if (measureParameter != null) {
                measureParameter.startReading();
            }
        });
    }

    private void onConnectClicked() {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        startConnection();
    }

    private void startConnection() {
        String sensorName = editSensorName.getText().toString().trim();
        if (sensorName.isEmpty()) {
            sensorName = "JPD";
        }
        boolean useBLEService = switchBle.isChecked();

        String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());

        textStatus.setText(R.string.Please_wait_while_it_connect_to_device);
        textDeviceName.setText("");
        btnConnect.setEnabled(false);

        measureParameter = new BluetoothMeasureParameter(this, true, date, sensorName, useBLEService);
        measureParameter.scanAndConnect();
    }

    private void onDisconnectClicked() {
        if (measureParameter != null) {
            measureParameter.disconnect();
        }
        btnConnect.setEnabled(true);
        btnDisconnect.setEnabled(false);
        btnStartReading.setEnabled(false);
        textStatus.setText(R.string.status_not_connected);
        textDeviceName.setText("");
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
            };
        } else {
            return new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }

    private boolean hasRequiredPermissions() {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestRequiredPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions(), REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasRequiredPermissions()) {
                startConnection();
            } else {
                Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (measureParameter != null) {
            measureParameter.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (measureParameter != null) {
            measureParameter.stop();
        }
    }

    // ----- BluetoothMeasureParameter.BluetoothConnectionStatus -----

    @Override
    public void onNotConnected(String message) {
        runOnUiThread(() -> {
            textStatus.setText(message);
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            btnStartReading.setEnabled(false);

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                Toast.makeText(this, R.string.bluetooth_is_not_available, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onConnecting() {
        runOnUiThread(() -> textStatus.setText(R.string.Please_wait_while_it_connect_to_device));
    }

    @Override
    public void onConnected(String deviceName) {
        runOnUiThread(() -> {
            textStatus.setText(getString(R.string.connected_to) + deviceName);
            textDeviceName.setText(deviceName);
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            btnStartReading.setEnabled(true);
            measureParameter.startReading();
        });
    }

    // ----- BluetoothMeasureParameter.FetalDopplerReading -----

    @Override
    public void fetalDopplerReadingReceived(int heartRate, int tocoWave, int afmWave, int status1, int status2, byte[] data, boolean b) {
        runOnUiThread(() -> {
            textHeartRate.setText(String.valueOf(heartRate));
            textToco.setText(String.valueOf(tocoWave));
            textAfm.setText(String.valueOf(afmWave));
        });
    }

    @Override
    public void fetalDopplerframeRecieved(byte[] data, boolean b) {
        // Raw sound frame data is already played through the AudioTrack
        // inside BluetoothMeasureParameter; nothing additional to do here.
    }
}
