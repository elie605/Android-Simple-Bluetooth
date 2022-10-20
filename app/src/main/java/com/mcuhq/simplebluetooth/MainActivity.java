package com.mcuhq.simplebluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final String TAG = MainActivity.class.getSimpleName();

    private static final UUID idString = UUID.fromString("01101101-0110-1000-8888" +
            "-00805FBB34FF"); // unieke id

    //voor het identificeren van gedeelde typen tussen aanroepende functies
    // gebruikt om het toevoegen van bluetooth-namen te identificeren
    private final static int MSG_REQUEST = 1;
    // gebruikt in bluetooth-handler om berichtupdate te identificeren
    public final static int MSG_READ = 2;
    // gebruikt in bluetooth-handler om de berichtstatus te identificeren
    private final static int MSG_STATUS = 3;

    private TextView txvBlueStatus;
    private TextView txvReadBuffer;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> arrayAdapter;

    // Onze hoofdafhandelaar die terugbelmeldingen ontvangt
    private Handler handler;
    // Bluetooth-achtergrondwerkthread om gegevens te verzenden en ontvangen
    private ConnectedThread connThread;
    // bidirectioneel klant-naar-client gegevenspad
    private BluetoothSocket blueSocket = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txvBlueStatus = (TextView) findViewById(R.id.bluetooth_status);
        txvReadBuffer = (TextView) findViewById(R.id.read_buffer);
        Button btnShowPairedDevices = (Button) findViewById(R.id.paired_btn);
        Button btnShowEpicLightsOnPairedDevice = (Button) findViewById(R.id.checkbox_led_1);

        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        ListView lvDevice = (ListView) findViewById(R.id.devices_list_view);
        lvDevice.setAdapter(arrayAdapter);
        lvDevice.setOnItemClickListener(mDeviceClickListener);

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_READ) {
                    String msgR = null;
                    msgR = new String((byte[]) msg.obj, StandardCharsets.UTF_8);
                    txvReadBuffer.setText(msgR);
                }

                if (msg.what == MSG_STATUS) {
                    char[] msgS;
                    if (msg.arg1 == 1)
                        txvBlueStatus.setText(getString(R.string.BTConnected) + msg.obj);
                    else
                        txvBlueStatus.setText(getString(R.string.BTconnFail));
                }
            }
        };

        if (arrayAdapter != null) {
            btnShowEpicLightsOnPairedDevice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (connThread != null) //Thread moet wel bestaan voordat we verder kunnen
                        connThread.write("1w");
                }
            });


            btnShowPairedDevices.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listPairedDevices();
                }
            });

        }
    }

    private void listPairedDevices() {
        arrayAdapter.clear();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (bluetoothAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : pairedDevices)
                arrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), getString(R.string.show_paired_devices), Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(getApplicationContext(), getString(R.string.BTnotOn), Toast.LENGTH_SHORT).show();
    }

    private final AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            if (!bluetoothAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), getString(R.string.BTnotOn), Toast.LENGTH_SHORT).show();
                return;
            }

            txvBlueStatus.setText(getString(R.string.cConnet));
            // Haal het MAC-adres van het apparaat op, dit zijn de laatste 17 tekens in de weergave
            String info = ((TextView) view).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0, info.length() - 17);

            // Spawn een nieuwe thread om te voorkomen dat de GUI wordt geblokkeerd
            new Thread() {
                @Override
                public void run() {
                    boolean fail = false;

                    BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

                    try {
                        blueSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), getString(R.string.ErrSockCrea), Toast.LENGTH_SHORT).show();
                    }
                    // Breng de Bluetooth-aansluiting tot stand.
                    try {
                        blueSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            blueSocket.close();
                            handler.obtainMessage(MSG_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e2) {
                            // voeg code in om hiermee om te gaan
                            Toast.makeText(getBaseContext(), getString(R.string.ErrSockCrea), Toast.LENGTH_SHORT).show();
                        }
                    }
                    if (!fail) {
                        connThread = new ConnectedThread(blueSocket, handler);
                        connThread.start();

                        handler.obtainMessage(MSG_STATUS, 1, -1, name)
                                .sendToTarget();
                    }
                }
            }.start();
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, idString);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e);
        }
        return device.createRfcommSocketToServiceRecord(idString);
    }
}
