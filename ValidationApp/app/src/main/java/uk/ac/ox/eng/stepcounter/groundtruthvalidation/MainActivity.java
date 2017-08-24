package uk.ac.ox.eng.stepcounter.groundtruthvalidation;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.TypedArrayUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private Button mConnectButton;
    private Button mResetButton;

    private TextView mStepsTV;
    private TextView mLeftFootTV;
    private TextView mRightFootTV;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic;

    private final String serviceUUID = "00002220-0000-1000-8000-00805f9b34fb";
    private final String receiveUUID = "00002221-0000-1000-8000-00805f9b34fb";

    private boolean mConnected;
    private Handler mHandler;
    private Handler mReadHandler;
    private Runnable mReadState;
    private final int SCAN_PERIOD = 40000;

    private static final String TAG = "com.jamie.ground_truth";
    private final int REQUEST_PERMISSION_CALLBACK = 1;

    private int mSteps = 0;
    private int mRight_state = 0;
    private int mLeft_state = 0;
    private int mRight_p_state = 1;
    private int mLeft_p_state = 1;

    public MainActivity() {
        super();
        mConnected = false;
        mHandler = new Handler();
        mReadHandler = new Handler();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                String [] permissions = new String[1];
                permissions[0] = Manifest.permission.ACCESS_COARSE_LOCATION;
                requestPermissions(permissions, REQUEST_PERMISSION_CALLBACK);
            }
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mLeftFootTV = (TextView) findViewById(R.id.tv_left_foot);
        mRightFootTV = (TextView) findViewById(R.id.tv_right_foot);
        mStepsTV = (TextView) findViewById(R.id.tv_steps);

        mConnectButton = (Button) findViewById(R.id.button_connect);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mConnected) {
                    // Attempt to connect
                    mConnectButton.setEnabled(false);
                    mBluetoothAdapter.startLeScan(handleScan);

                    // Handler to stop scan after a number of seconds
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!mConnected) {
                                Log.d(TAG, "Scanning timeout, stopping it.");
                                mBluetoothAdapter.stopLeScan(handleScan);

                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mConnectButton.setEnabled(true);
                                        mConnected = false;
                                        mConnectButton.setText(R.string.connect);
                                        mRightFootTV.setText(R.string.not_connected);
                                        mLeftFootTV.setText(R.string.not_connected);
                                    }
                                });
                            }
                        }

                    }, SCAN_PERIOD);
                } else {
                    // Disconnect.
                    mConnected = false;
                    mConnectButton.setText(R.string.connect);
                    mRightFootTV.setText(R.string.not_connected);
                    mLeftFootTV.setText(R.string.not_connected);

                    if(mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                    }

                }
            }
        });
        mResetButton = (Button) findViewById(R.id.button_reset);
        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSteps = 0;
                mStepsTV.setText(Integer.toString(mSteps));
            }
        });

        mReadState = new Runnable() {
            @Override
            public void run() {
                mReadHandler.postDelayed(this, 100);
                if (mConnected) {
                    if(mBluetoothGatt != null && mCharacteristic != null) {
                        mBluetoothGatt.readCharacteristic(mCharacteristic);
                    }
                }
            }
        };

        mReadHandler.post(mReadState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        //When the application stops we disconnect
        mBluetoothAdapter.stopLeScan(handleScan);
        if(mBluetoothGatt != null)
            mBluetoothGatt.disconnect();
    }

    BluetoothAdapter.LeScanCallback handleScan = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int i, byte[] bytes) {
            if (device.getName() == null){
                return;
            }
            Log.d(TAG, "Found BTLE device: " + device.getName());
            if (device.getName().equalsIgnoreCase("RFduino")) {
                mBluetoothAdapter.stopLeScan(handleScan);

                Log.d(TAG, "Trying to connect to RFduino");
                mBluetoothGatt = device.connectGatt(MainActivity.this, true, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        super.onConnectionStateChange(gatt, status, newState);
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d(TAG, "Connected to RFduino, attempting to start service discovery:" + mBluetoothGatt.discoverServices());
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d(TAG, "Disconnected from RFduino.");
                            mConnected = false;
                            // Move to onUI
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnectButton.setEnabled(true);
                                    mConnectButton.setText(R.string.connect);
                                }
                            });

                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        super.onServicesDiscovered(gatt, status);

                        if (status == BluetoothGatt.GATT_SUCCESS) {

                            BluetoothGattService serv = mBluetoothGatt.getService(UUID.fromString(serviceUUID));
                            mCharacteristic = serv.getCharacteristic(UUID.fromString(receiveUUID));
                            mConnected = true;

                            //Now we assume that the device is fully connected
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //On the UI we shall enable the bars and the disconnect button
                                    mConnectButton.setEnabled(true);
                                    mConnectButton.setText(R.string.disconnect);
                                    mRightFootTV.setText("-");
                                    mLeftFootTV.setText("-");
                                }
                            });
                        }
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

                        byte[] value = characteristic.getValue();
                        // Process bit array.
                        value = reverseArray(value);
                        ByteBuffer wrapped = ByteBuffer.wrap(value);
                        int val = wrapped.getInt();
                        Log.d(TAG, Integer.toString(val));
                        decodeState(val);



                    }
                });
            }
        }
    };

    private void decodeState(int value) {

        switch(value) {

            case 0:
                mRight_state = 0;
                mLeft_state = 0;
                break;
            case 1:
                mRight_state = 1;
                mLeft_state = 0;
                break;
            case 2:
                mRight_state = 0;
                mLeft_state = 1;
                break;
            case 3:
                mRight_state = 1;
                mLeft_state = 1;
                break;
        }

        if (mRight_state == 1 && mRight_p_state == 0) {
            //Step
            mSteps += 1;
        }
        if (mLeft_state == 1 && mLeft_p_state == 0) {
            mSteps += 1;
        }

        // Update UI components
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRightFootTV.setText(mRight_state == 0 ? R.string.up : R.string.down);
                mLeftFootTV.setText(mLeft_state == 0 ? R.string.up : R.string.down);
                mStepsTV.setText(Integer.toString(mSteps));
                mRight_p_state = mRight_state;
                mLeft_p_state = mLeft_state;
            }
        });



    }


    private byte[] reverseArray(byte[] arr) {

        byte[] out = new byte[arr.length];

        for (int i = arr.length - 1; i > -1; i--) {
            out[arr.length - 1 - i] = arr[i];
        }
        return out;
    }

}
