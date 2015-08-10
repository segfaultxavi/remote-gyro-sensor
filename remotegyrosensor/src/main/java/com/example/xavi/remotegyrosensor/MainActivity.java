package com.example.xavi.remotegyrosensor;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // Intent request codes
    private static final int REQUEST_ENABLE_BT = 1;

    private BluetoothCommService mCommService = null;
    private BluetoothAdapter mBluetoothAdapter = null;

    private String mConnectedDeviceName;

    private SensorManager mSensorManager;
    private final float[] mGravity = new float[3];
    private final float[] mGeomagnetic = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mCommService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCommService != null) {
            mCommService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mCommService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mCommService.getState() == BluetoothCommService.STATE_NONE) {
                // Start the Bluetooth chat services
                mCommService.start();
            }
        }
    }

    private void setupChat() {
        // Initialize the BluetoothChatService to perform bluetooth connections
        mCommService = new BluetoothCommService(mHandler);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] mat = new float[16];
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_MAGNETIC_FIELD:
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                    System.arraycopy(event.values, 0, mGravity, 0, 3);
                else
                    System.arraycopy(event.values, 0, mGeomagnetic, 0, 3);
                SensorManager.getRotationMatrix(mat, null, mGravity, mGeomagnetic);
                break;
        }
        ByteBuffer send = ByteBuffer.allocate(1 + 16 * 4).order(ByteOrder.LITTLE_ENDIAN);
        send.put((byte)'M');
        for (float f:mat) {
            send.putFloat(f);
        }
        mCommService.write(send.array());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothCommService.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothCommService.STATE_CONNECTED:
                            ((TextView)findViewById(R.id.status)).setText ("Connected to " + mConnectedDeviceName);
                            Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                            if (mSensor == null) {
                                Toast.makeText(MainActivity.this, "No sensors available", Toast.LENGTH_LONG).show();
                                finish();
                            }
                            mSensorManager.registerListener(MainActivity.this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);

                            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                            if (mSensor == null) {
                                Toast.makeText(MainActivity.this, "No sensors available", Toast.LENGTH_LONG).show();
                                finish();
                            }
                            mSensorManager.registerListener(MainActivity.this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
                            break;
                        case BluetoothCommService.STATE_LISTEN:
                        case BluetoothCommService.STATE_NONE:
                            mSensorManager.unregisterListener(MainActivity.this);
                            ((TextView)findViewById(R.id.status)).setText ("Not connected");
                            break;
                    }
                    break;
                case BluetoothCommService.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    break;
                case BluetoothCommService.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(BluetoothCommService.DEVICE_NAME);
                    Toast.makeText(MainActivity.this, "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothCommService.MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, msg.getData().getString(BluetoothCommService.TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}
