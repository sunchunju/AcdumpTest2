package com.test.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.se.omapi.Channel;
import android.se.omapi.Reader;
import android.se.omapi.SEService;
import android.se.omapi.SEService.OnConnectedListener;
import android.se.omapi.Session;
import androidx.test.InstrumentationRegistry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Button startBtn;
    private SEService seService;
    private Object serviceMutex = new Object();
    private boolean connected = false;
    private final long SERVICE_CONNECTION_TIME_OUT = 20000;
    private ServiceConnectionTimerTask mTimerTask = new ServiceConnectionTimerTask();
    private Timer connectionTimer;
    private boolean mFlagServiceMutex = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.start_btn);
        startBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        // STRING to store the dump content
        String strResponce = "" ;
        // byte for APDU  transit response
        byte[] responce;
        //get AC dump CMD
        byte[] readAcLogCmd = new byte[]{(byte)0x80,(byte)0xCA,(byte)0x00,(byte)0xFE,(byte)0x06,(byte)0xDF,(byte)0x26,(byte)0x00,(byte)0xFE,(byte)0x00,(byte)0x00,(byte)0x00};
        //TODO: openBasicChannel with AID null,  APDU 00 A4 04 00 00 , responce 90 00.
        bindToSEService();

        try {
            waitForConnection();
            if (connected){
                Reader[] readers = seService.getReaders();

                Log.i("suncj","readers.length() = "+readers.length);

                for (Reader reader : readers) {
                    Session session = null;
                    Channel channel = null;
                    try {
                        session = reader.openSession();
                        Log.i("Could not open session", session.toString());
                        //TODO: openBasicChannel with AID null,  APDU 00 A4 04 00 00 , responce 90 00.
                        channel = session.openBasicChannel(null);

                        strResponce = strResponce + "--------BEGIN:AC LOG------------" + "\n";
                        strResponce = strResponce + "AC Log Entries :" + "\n";
                        //loop send CMD，if response 6310 , 9000 finish break
                        while(true){
                            responce = channel.transmit(readAcLogCmd);
                            //TODO: HexByte to HexString. result NEED REMOVE last 2 BYTE like 9000 or 6310
                            strResponce =   strResponce +  bytesToHexString(responce) + "\n";
                            if (responce != null &&
                                    responce[responce.length-2] == (byte)0x90 &&
                                    responce[responce.length-1] == (byte)0x00){
                                strResponce = strResponce + "Aclog: All Dumpped" + "\n";
                                break;
                            }
                        }
                        strResponce = strResponce + "---------END:AC LOG-------------" + "\n";

                        writeToFile(strResponce);
                    } finally {
                        if (channel != null) channel.close();
                        if (session != null) session.close();
                    }
                }
            }

        } catch (TimeoutException | IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void bindToSEService() {

        BindToSEService bindService = new BindToSEService();
        bindService.start();
    }

    private void writeToFile(String strResponce) {
        String sdPath = Environment.getExternalStorageDirectory().getPath();

        File file = new File(sdPath, "test.txt");
        //2 创建一个文件输出流
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(strResponce.getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private final OnConnectedListener mListener = new OnConnectedListener() {
        @Override
        public void onConnected() {
            synchronized (serviceMutex) {
                Log.i("suncj","onConnected");
                connected = true;
                serviceMutex.notify();
            }
        }
    };

    class SynchronousExecutor implements Executor {
        public void execute(Runnable r) {
            r.run();
        }
    }

    private void waitForConnection() throws TimeoutException {
        synchronized (serviceMutex) {
            if (!connected) {
                try {
                    serviceMutex.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (!connected) {
                throw new TimeoutException(
                        "Service could not be connected after " + SERVICE_CONNECTION_TIME_OUT
                                + " ms");
            }
            if (connectionTimer != null) {
                connectionTimer.cancel();
            }
        }
    }

    class ServiceConnectionTimerTask extends TimerTask {
        @Override
        public void run() {
            synchronized (serviceMutex) {
                Log.i("suncj","ServiceConnectionTimerTask run");
                serviceMutex.notifyAll();
            }
        }
    }

    private String bytesToHexString(byte[] src) {
        StringBuilder builder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        String hv;
        for (int i = 0; i < src.length; i++) {
            // 以十六进制（基数 16）无符号整数形式返回一个整数参数的字符串表示形式，并转换为大写
            hv = Integer.toHexString(src[i] & 0xFF).toUpperCase();
            if (hv.length() < 2) {
                builder.append(0);
            }
            builder.append(hv);
        }

        return builder.toString();
    }

    private class BindToSEService extends Thread{
        @Override
        public void run() {
            Log.i("suncj","BindToSEService run");
            seService = new SEService(MainActivity.this, new SynchronousExecutor(), mListener);
            connectionTimer = new Timer();
            connectionTimer.schedule(mTimerTask, SERVICE_CONNECTION_TIME_OUT);
        }
    }
}