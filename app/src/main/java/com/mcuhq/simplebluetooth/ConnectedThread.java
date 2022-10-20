package com.mcuhq.simplebluetooth;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final Handler mHandler;

    public ConnectedThread(BluetoothSocket socket, Handler handler) {
        mmSocket = socket;
        mHandler = handler;
        InputStream inputStream = null;
        OutputStream outputStream = null;

        // Het op halen van input en output streams , kunnen null zijn en dus kunnen niet gelijk
        // final zijn
        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
        }

        mmInStream = inputStream;
        mmOutStream = outputStream;
    }

    @Override
    public void run() {
        // bufferopslag voor de stream
        byte[] buffer = new byte[1024];
        // bufferopslag voor de stream
        int bytes;
        // Blijf luisteren naar de InputStream totdat er een uitzondering optreedt
        while (true) {
            try {
                // Read from the InputStream
                bytes = mmInStream.available();
                if (bytes != 0) {
                    buffer = new byte[1024];
                    // pauze en wacht op de rest van de gegevens. Pas dit aan afhankelijk van uw verzendsnelheid.
                    SystemClock.sleep(100);
                    // hoeveel bytes zijn klaar om gelezen te worden?
                    bytes = mmInStream.available();
                    //  hoeveel bytes we daadwerkelijk lezen
                    bytes = mmInStream.read(buffer, 0, bytes);
                    // Stuur de verkregen bytes naar de UI-activiteit
                    mHandler.obtainMessage(MainActivity.MSG_READ, bytes, -1, buffer)
                            .sendToTarget();
                }
            } catch (IOException e) {
                e.printStackTrace();

                break;
            }
        }
    }

    //Sturen van data naar de geconecte blauwetand apparaat
    public void write(String input) {
        //String to bytes
        byte[] bytes = input.getBytes();
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) {
        }
    }

    //Het aflsuiten van de bluewTand connectie
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
        }
    }
}