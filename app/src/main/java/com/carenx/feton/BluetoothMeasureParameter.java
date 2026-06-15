package com.carenx.feton;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.fetosense.audioplay.MyAudioTrack8Bit;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.fetosense.adpcm.Adpcm;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Set;

public class BluetoothMeasureParameter {

    private BluetoothAdapter mBluetoothAdapter = null;
    public Context context;
    private Activity act;
    private FDBluetoothService mService;
    private FDBLEService mBLEService;
    public static final int FREQ = 4000;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_FHR = 6;

    private static final int REQUEST_CONNECT_DEVICE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final String TAG = BluetoothMeasureParameter.class.getSimpleName();
    private String sensorName = "JPD";
    private BluetoothConnectionStatus connectionStatus;
    private FetalDopplerReading fetalReading;
    private MyAudioTrack8Bit mAudioTrack8Bit;
    private BeatThread beatThread;
    private String mConnectedDeviceName = null;
    private int[] handleBuffer = new int[200];
    private byte[] fhrData = new byte[6];
    private boolean useSensor = false;
    public final static String ACTION_DATA_AVAILABLE ="com.nscpl.libble.ACTION_DATA_AVAILABLE";
    private boolean readSound = true;
    public String service_error="",leads_missing="",leads_not_connected = "";
    private String FILENAME = "fetal_sound";
    private FileOutputStream fos=null;
    private Handler uiHandler = new Handler();
    private int FRAME_SIZE = 162;
    String name=Environment.getExternalStorageDirectory().toString();
    public static String date=null;
    File file = new File(Environment.getExternalStorageDirectory().toString()+ File.separator + "Remedi"+File.separator+"fetaldoppler_sample");
    String FILENAM = "ReMeDi";
    String FOLDERNAME = "Fetal_Sound";
    File subFolder;
    private boolean useBLEService;
    public BluetoothMeasureParameter(Context context,boolean useSensor,String date,String sensorName, boolean useBLEService){

        this.context = context;
        this.useBLEService = useBLEService;
        if (sensorName != null) {
            this.sensorName = sensorName;
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        act = (Activity) context;
        this.useSensor = useSensor;
        fetalReading =  (FetalDopplerReading) context;
        this.date=date;
        if(useSensor){
            if (mBluetoothAdapter == null) {
                Toast.makeText(context, context.getResources().getString(R.string.bluetooth_is_not_available),Toast.LENGTH_LONG).show();
                act.finish();
                return;
            }
            onStart();
            connectionStatus = (BluetoothConnectionStatus) context;
        }
        soundInitialization();
	/*	String folder =Environment.getExternalStorageDirectory().toString()+ File.separator + "Remedi"+ File.separator + FOLDERNAME;
		subFolder = new File(folder);
		Log.d("folder",""+subFolder);
		*//*if (!subFolder.exists()) {
			subFolder.mkdirs();
		}else {*//*
			File file = new File(String.valueOf(subFolder));
			try {
				if (file.exists()) {
					file.delete();
				}
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}*/
        //}
    }
    public void soundInitialization(){
        mAudioTrack8Bit = new MyAudioTrack8Bit();
        mAudioTrack8Bit.prepareAudioTrack();

        if(useSensor){
            beatThread = new BeatThread();
            beatThread.start();
        }
    }

    private void onStart(){
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            act.startActivityForResult(enableIntent, REQUEST_ENABLE_BT);

        } else if (useBLEService){
            mBLEService = new FDBLEService(context, mHandler,date);
        } else {
            mService = new FDBluetoothService(context, mHandler,date);

        }
    }


    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case FDBluetoothService.STATE_CONNECTED:
                            connectionStatus.onConnected(mConnectedDeviceName);
                            break;
                        case FDBluetoothService.STATE_CONNECTING:
                            connectionStatus.onConnecting();
                            break;
                        case FDBluetoothService.STATE_LISTEN:
                        case FDBluetoothService.STATE_NONE:
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    //String writeMessage = byteArrayToHexString(writeBuf);
                    break;
                case MESSAGE_READ:
                    byte readBuf[] = (byte[]) msg.obj;

                    try {
                        if (beatThread!=null && (beatThread.mHandler != null) && (useSensor)) {
                            Message message = beatThread.mHandler.obtainMessage(MESSAGE_FHR);
                            message.obj = readBuf;
                            message.what = MESSAGE_FHR;
                            beatThread.mHandler.sendMessage(message);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);

                    break;
                case MESSAGE_TOAST:
                    connectionStatus.onNotConnected(msg.getData().getString(TOAST));
                    break;
                case MESSAGE_FHR:

                    break;
            }
        }
    };


    private String byteArrayToString(byte[] b){
        int len = b.length;
        String data = new String();
        for (int i = 0; i < len; i++){
            data+= b[i]+" ";
        }

        return data;
    }

    private String byteArrayToHexString(byte[] b) {
        int len = b.length;
        String data = new String();

        for (int i = 0; i < len; i++){
            data += Integer.toHexString((b[i] >> 4) & 0xf);
            data += Integer.toHexString(b[i] & 0xf)+" ";
        }
        return data;
    }

    public synchronized void startReading(){
        if (useBLEService) {
            if (mBLEService != null) {
                // Only if the state is STATE_NONE, do we know that we haven't
                // started already
                if (mBLEService.getState() == FDBluetoothService.STATE_NONE) {
                    // Start the Bluetooth chat services
                    mBLEService.start();
                }
            }
        } else
        if (mService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't
            // started already
            if (mService.getState() == FDBluetoothService.STATE_NONE) {
                // Start the Bluetooth chat services
                mService.start();
            }
        }
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    if (useBLEService){
                        mBLEService = new FDBLEService(context, mHandler,date);
                    } else {
                        mService = new FDBluetoothService(context, mHandler, date);
                    }
                    scanAndConnect();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Logger.d(TAG, "BT not enabled");

                }
        }
    }

    private void connectDevice(String address) {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (useBLEService) {
            mBLEService.connect(device);
        } else {
            mService.connect(device);
        }
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }

        context.unregisterReceiver(mReceiver);
    }

    public void disconnect() {
        if (useBLEService) {
            if (mBLEService != null){
                mHandler.removeCallbacksAndMessages(null);
                mBLEService.stop();
                if(beatThread!=null){
                    beatThread.stopSelf();
                }
            }
        } else
        if (mService != null){
            mHandler.removeCallbacksAndMessages(null);
            mService.stop();
            if(beatThread!=null){
                beatThread.stopSelf();
            }
        }
        if (mAudioTrack8Bit != null) {
            mAudioTrack8Bit.mAudioTrack.stop();
        }
    }

    public void stop(){
        if (mAudioTrack8Bit != null) {
            mAudioTrack8Bit.mAudioTrack.stop();
        }
        try {

            if (useBLEService) {
                if (mBLEService != null) {
                    mHandler.removeCallbacksAndMessages(null);
                    mBLEService.stop();
                    readSound = false;
                    if (beatThread != null) {
                        beatThread.stopSelf();
                        beatThread = null;
                    }
                }
            } else
            if (mService != null) {
                mHandler.removeCallbacksAndMessages(null);
                mService.stop();
                readSound = false;
                if (beatThread != null) {
                    beatThread.stopSelf();
                    beatThread = null;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }



    public void scanAndConnect(){
        if(useSensor){
            Log.d(TAG, "scanAndConnect: sensor info"+useSensor);
            doDiscovery();
        }
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    private void doDiscovery() {

        Log.d(TAG, "doDiscovery: list "+BluetoothDevice.ACTION_FOUND);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(mReceiver, filter);

        Log.d(TAG, "doDiscovery: recevier "+context.registerReceiver(mReceiver,filter));

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        Log.d(TAG, "doDiscovery: permission"+mBluetoothAdapter.getBondedDevices());
        Log.d(TAG, "doDiscovery: paired devices"+pairedDevices);

//		if (pairedDevices.size() > 0) {
//			boolean deviceFound = false;
//			for (BluetoothDevice device : pairedDevices) {
//				if(device !=null){
//					Log.d(TAG, "Device name paired "+device.getBondState());
//					if(device.getName().contains(sensor_name)){
//						deviceFound = true;
//						connectDevice(device.getAddress());
//					}
//				}
//				//iFM10B2016010852
//			}
//			if(!deviceFound){
//				scanFetalDoppler();
//			}
//		} else {
        Message msg = mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_TOAST);
        Log.d(TAG, "doDiscovery: do discovery 5 "+msg);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothMeasureParameter.TOAST, context.getResources().getString(R.string.Please_wait_while_it_connect_to_device));
        msg.setData(bundle);
        Log.d(TAG, "doDiscovery: do discovery 6 "+bundle);
        mHandler.sendMessage(msg);
        Log.d(TAG, "doDiscovery: do discovery  7 "+mHandler.toString());
        scanFetalDoppler();

//		}
    }

    private void scanFetalDoppler(){
        Log.d(TAG, "scanFetalDoppler: sacn and connect working");
        Log.d(TAG, "scanFetalDoppler: bluwtooth"+mBluetoothAdapter.isDiscovering());
        if (mBluetoothAdapter.isDiscovering()) {
            Log.d(TAG, "scanFetalDoppler: discovery"+mBluetoothAdapter.isDiscovering());
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
    }








    private void ensureDiscoverable() {

        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(
                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            act.startActivity(discoverableIntent);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed
                // already
                if(device != null){
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {

                        if(device.getName() !=null)
                            if(device.getName().contains(sensorName) && (device.getType()==3 || device.getType()==2)){
                                Log.d(TAG, "onReceive: name"+device.getName());
                                connectDevice(device.getAddress());

                            }
                        Logger.d(TAG, "device name "+device.getName()+" "+device.getAddress());

                    }else{

                        if(device.getName() != null)
                            if(device.getName().contains(sensorName)){
                                if (useBLEService) {
                                    mBLEService.stop();
                                    mBLEService.start();
                                } else {
                                    mService.stop();
                                    mService.start();
                                }
                                connectDevice(device.getAddress());
                            }
                        Logger.d(TAG, "device name else "+device.getName()+" "+device.getAddress());
                    }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                    .equals(action)) {
                connectionStatus.onNotConnected(context.getString(R.string.no_devices_found));
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {

                connectionStatus.onNotConnected(context.getString(R.string.disconnected));

            }


        }
    };

    int cnt=0;
    int arr[]=new int[2000];

    int arr1[] = new int[4000];
    static int cnt1 = 0;


//    private void playSound(byte[] data){
//
//        Log.d(TAG, "playSound: "+Arrays.toString(data));
//        fhrData[0] = data[110]; //110  3
//        fhrData[1] = data[112]; //112  5
//        fhrData[2] = data[113]; //113  6
//        fhrData[3] = data[114]; // 114 7
//        fhrData[4] = data[115]; //115 8
//        fhrData[5] = (byte) (data[116] + 1);
//
//        final int heartRate = fhrData[0] & 255;
//        final int tocoWave = fhrData[1] & 255;
//        final int afmWave = fhrData[2] & 255;
//        final int status1 = fhrData[3] & 255;
//        final int status2 = fhrData[4] & 255;
//        int signal = status1 & 3;
//        int beatBd = (status1 & 4) >> 2;
//        int probeBattery = status2 & 7;
//
//        if(beatThread != null) {
//            beatThread.rate = heartRate;
//        }
//
//
//        int[] soundArr = ADPCM.decodeAdpcm(handleBuffer, 0, data, 0, 100, 107);
//        Log.d(TAG, "playSound: Sound Arr" + Arrays.toString(soundArr));
//        /*********converting 200 samples to 400 byte**********************************/
//        final byte[] finalArray=new byte[400];
//
//        int count=0;
//
//        for(int i=0;i<soundArr.length;i++)
//        {
//            arr[cnt++]=soundArr[i];
//            arr1[cnt1++]=soundArr[i];
//            byte msb=(byte) ((soundArr[i] & 0xff)>>8);
//            finalArray[count++]=msb;
//            byte lsb=(byte) (soundArr[i] & 0xff);
//            finalArray[count++]=lsb;
//
//            if (cnt==2000){
//                byte[] b=null;
//                try {
//                    b=Arrays.toString(arr).getBytes("UTF-8");
//                    Log.d(TAG, "playSound: "+b);
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                }
//                fetalReading.fetalDopplerframeRecieved(b,true);
//                arr=new int[2000];
//                cnt=0;
//            }
//
//            if(cnt1==4000){
//                byte[] b=null;
//                try {
//                    b=Arrays.toString(arr1).getBytes("UTF-8");
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                }
//                writeT(arr1);
//                arr1 = new int[4000];
//                cnt1=0;
//            }
//
//        }
//
//        audioTrack.write(finalArray, 0, finalArray.length);
//
////        if (heartRate > 0) {
////
////            Logger.d("found", "heart rate " + heartRate);
////            Logger.d("found", "heart data  " + data);
////            writeT(finalArray);
////
////
////        }
//        ((Activity)context).runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                fetalReading.fetalDopplerReadingReceived(heartRate, tocoWave, afmWave, status1, status2,finalArray,false);
//            }
//        });
//
//
//
//    }

    private void playSoundFetoSense(byte[] data){

        Log.d(TAG, "playSound: "+Arrays.toString(data) + " length: " + data.length);
        if (data.length >= 117) {
            fhrData[0] = data[110]; //110  3
            fhrData[1] = data[112]; //112  5
            fhrData[2] = data[113]; //113  6
            fhrData[3] = data[114]; // 114 7
            fhrData[4] = data[115]; //115 8
            fhrData[5] = (byte) (data[116] + 1);
        }

        final int heartRate = fhrData[0] & 255;
        final int tocoWave = fhrData[1] & 255;
        final int afmWave = fhrData[2] & 255;
        final int status1 = fhrData[3] & 255;
        final int status2 = fhrData[4] & 255;
        int signal = status1 & 3;
        int beatBd = (status1 & 4) >> 2;
        int probeBattery = status2 & 7;

        if (beatThread != null) {
            beatThread.rate = heartRate;
        }

        Log.d(TAG, "playSound: heartRate " + heartRate);

        byte[] nativeOut = new byte[200];
        int numSamples = Adpcm.decode(data, nativeOut, 0, 1.0f);

        for(int i=0;i<numSamples;i++)
        {
            int sampleVal = nativeOut[i] & 0xFF;
            arr[cnt++] = sampleVal;
            arr1[cnt1++] = sampleVal;

            Log.d(TAG, "playSoundFetoSense: amplifyFetalSignal " + sampleVal);

            if (cnt==2000){
                byte[] b=null;
                try {
                    b=Arrays.toString(arr).getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                fetalReading.fetalDopplerframeRecieved(b,true);
                arr=new int[2000];
                cnt=0;
            }

            if(cnt1==4000){
                byte[] b=null;
                try {
                    b=Arrays.toString(arr1).getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                writeT(arr1);
                arr1 = new int[4000];
                cnt1=0;
            }
        }

        if (mAudioTrack8Bit != null) {
            mAudioTrack8Bit.writeAudioTrack(nativeOut, 0, numSamples);
        }

        final byte[] finalArray1 = nativeOut;
        ((Activity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fetalReading.fetalDopplerReadingReceived(heartRate, tocoWave, afmWave, status1, status2, finalArray1,false);
            }
        });
    }

//    private void writeT(int[] LoggerMsg) {
//        File externalDir = context.getExternalFilesDir(null);
//        Logger.d(TAG, "filePathDebug, (discard): externalDir >> " + externalDir.getPath());
//        File fil = new File(externalDir, "Remedi/Fetal_Sound/");
//        File file = new File(fil, "Fetal_reading_" + date + ".txt");
//        Logger.d(TAG, "filePathDebug, (discard): file >> " + file);
//
//        Log.d(TAG, "writeT: "+file);
//        if (!file.exists()) {
//            try {
//                fil.mkdirs();
//                file.createNewFile();
//                out_e = new PrintWriter(new BufferedWriter(new FileWriter(file, false)));
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        } else {
//            try {
//                out_e = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        }
//        out_e.println(Arrays.toString(LoggerMsg));
//        out_e.flush();
//
//    }
    public static byte getLSB(int x)
    {
        return (byte)(x & 0xFF);
    }

    public static byte getMSB(int x)
    {
        return (byte)(x >> 8 & 0xFF);
    }

    private void saveFetalSound(byte[] soundArr){
        try {

            if(fos == null){
                fos = context.openFileOutput(FILENAME, Context.MODE_APPEND);
                Logger.d("file",""+name);
            }
            fos.write(soundArr);
            fos.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeT(int[] data) {

        try {

            PrintWriter out_e = null;
//				File fil = new File(Environment.getExternalStorageDirectory().toString() + File.separator + "Remedi" + File.separator + "Fetal_Sound" + File.separator);
//				File file = new File(fil, "fetal_reading_" +date+ ".txt");

            //-------------------------------
            File externalDir = context.getExternalFilesDir(null);
            Logger.d(TAG, "filePathDebug, (discard): externalDir >> " + externalDir.getPath());
            File fil = new File(externalDir, "Remedi/Fetal_Sound/");
            File file = new File(fil, "Fetal_reading_" + date + ".txt");
            Logger.d(TAG, "filePathDebug, (discard): file >> " + file);

            //---------------------------


            if (!file.exists()) {
                try {
                    fil.mkdirs();
                    file.createNewFile();
                    out_e = new PrintWriter(new BufferedWriter(new FileWriter(file, false)));

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    out_e = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            out_e.println(Arrays.toString(data)+"\n");
            out_e.flush();

        }catch (Exception e){
            e.printStackTrace();
        }

    }


    public  String  readFileContent() {
        try {

//			File fil = new File(Environment.getExternalStorageDirectory().toString() + File.separator + "Remedi" + File.separator + "Fetal_Sound" + File.separator);
//			File file = new File(fil, "fetal_reading_" + date + ".txt");

            //-------------------------------
            File externalDir = context.getExternalFilesDir(null);
            Log.d(TAG, "readFileContent: "+externalDir);
            Logger.d(TAG, "filePathDebug, (discard): externalDir >> " + externalDir.getPath());
            File fil = new File(externalDir, "Remedi/Fetal_Sound/");
            File file = new File(fil, "Fetal_reading_" + date + ".txt");
            Logger.d(TAG, "filePathDebug, (discard): file >> " + file);

            //---------------------------

            String data = readFileContent(file.toString());
            Logger.d("Read data======", "" + data);
            playaudio(data, 1000);


        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    int count=0;

    public void playaudio(final String data,final int max) {

		/*new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				((Activity)context).runOnUiThread(new Runnable() {
					public void run() {*/

						/*final Dialog dialog = new Dialog(context);
						dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
						dialog.setContentView(R.layout.audio_play);
						// ImageView play= (ImageView) dialog.findViewById(R.id.play);
						ImageView pause = (ImageView) dialog.findViewById(R.id.pause);
						final SeekBar seekBar = (SeekBar) dialog.findViewById(R.id.media_seekbar);
						TextView time = (TextView) dialog.findViewById(R.id.playback_time);
						final TextView text = (TextView) dialog.findViewById(R.id.text_audio);
						text.setText("Playing audio.....");
						Window window = dialog.getWindow();
						window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
						dialog.show();
						seekBar.setProgress(0);
						seekBar.setMax(max);*/
        mAudioTrack8Bit.mAudioTrack.play();

        try {
            mAudioTrack8Bit.mAudioTrack.play();
            String pairData = data.replaceAll("\\]", "],");
            String data1 = "[" + pairData + "]";
            JSONArray array = new JSONArray(data1);
            for (int i = 0; i < array.length() - 1; i++) {
                byte[] bb = new byte[array.getJSONArray(i).length()];
                int k = 0;
                for (int j = 0; j < array.getJSONArray(i).length(); j++) {
                    //Log.d("data got", "" + array.getJSONArray(i).getInt(j));
                    bb[k++] = (byte) array.getJSONArray(i).getInt(j);
                }
                mAudioTrack8Bit.mAudioTrack.write(bb, 0, bb.length);
                //	seekBar.setProgress(count++);
            }
            Logger.d("end of the file","end");
            //	dialog.dismiss();

        } catch (Exception e) {
            e.printStackTrace();
        }
				/*	}
				});
			}
		});
*/

    }
    public static String readFileContent(String myfile) {

        /*File myfile = new File("/storage/emulated/0/Remedi/Fetal_Sound/fetal_reading_2017-10-08_16:18:19.txt");*/
        Logger.d("Reading..", "" + myfile);
        StringBuilder rawdata = new StringBuilder();
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(myfile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
            String line;
            while ((line = reader.readLine()) != null) {
                rawdata.append(line);
            }
            return rawdata.toString();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] hexStringToShortArray(String s) {

        Logger.d("s==="," "+s);
        String[] ss = s.split("\\s+");
        int len = ss.length;

        if(s.contains("[")||s.contains("]"))
            s= s.replaceAll("\\[","").replaceAll("\\]","");

        String sss[]=s.split(":");

        byte[] data = new byte[ss.length];
        int j = 0;
        for (int k = 0; k <ss.length ; k++) {
            if(ss[k] != null && !ss[k].isEmpty()) {
                // Log.d("value of " + (ss[k].trim()), " " + Integer.parseInt((ss[k].trim()), 16));
                data[j] =(byte) Integer.parseInt((ss[k].trim()), 16);
                j++;
            }
        }
        return data;
    }

    private  PrintWriter out_e = null;
    private  PrintWriter printWriter = null;
    private  File file_e = null;
    public static final String folderNameFetal = Environment.getExternalStorageDirectory().toString()+ File.separator+"Remedi"+File.separator+"3rdparty"+File.separator;


    private void writeToFile(String LoggerMsg) {

        if (out_e == null) {

            file_e = new File(folderNameFetal);

            File file = new File(file_e, FILENAME);
            if (!file.exists()) {
                try {
                    file_e.mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                file.delete();
                try {
                    file_e.mkdirs();
                    file.createNewFile();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                out_e = new PrintWriter(new BufferedWriter(new FileWriter(file, false)));
                //out = new PrintWriter(new FileOutputStream(file, true));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }catch (IOException e) {
                e.printStackTrace();
            }

        }

        out_e.println(LoggerMsg);
        out_e.flush();
    }

    public void save(byte[] soundArr){

        try {

            FileOutputStream outputStream = new FileOutputStream(new File(subFolder, FILENAME));
            outputStream.write(soundArr);
            outputStream.close();

        } catch (FileNotFoundException e) {
            Log.e("ERROR", e.toString());
        } catch (IOException e) {
            Log.e("ERROR", e.toString());
        }


    }

    private void writeToFil(String LoggerMsg) {

        PrintWriter out_e=null;
        String folderNameECG = Environment.getExternalStorageDirectory().toString()+ File.separator+"Remedi_Stetho"+File.separator+"setho_raw_data"+File.separator;
        File filer=new File(folderNameECG);
        File file = new File(filer, date+".txt");
        Logger.d("fileName",""+file.toString());
        if (!file.exists()) {
            try {
                filer.mkdirs();
                file.createNewFile();
                out_e = new PrintWriter(new BufferedWriter(new FileWriter(file, false)));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            try {
                out_e = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        out_e.println(LoggerMsg+" ");
        out_e.flush();

    }





/*	public byte[] readSoundFromFile(){
		//audioTrack.play();
		readSound = true;
		FileInputStream fin=null;
		byte[] buffer = new byte[FRAME_SIZE];
		Logger.d(TAG, "buffer length "+buffer.length);
		try {
			if(useSensor){
				fin = context.openFileInput(FILENAME);
				Log.d(TAG, "available "+fin.available());
			}else {
				String name = Environment.getExternalStorageDirectory().toString() + "/new1.txt";
				Log.d("FileName", "" + name);
				File file = new File(name);
				//file.mkdir();
				if (!file.exists()) {
					Logger.d(TAG, "FILE not Found11......................");
					return null;
				} else {
					Logger.d(TAG, "FILE Found");
					fin = new FileInputStream(file);
				}
				//}
				if (fin != null) {
					while (readSound) {
						int len = fin.read(buffer, 0, FRAME_SIZE);
						for (int i = 0; i < buffer.length; i++) {
							Log.d("play data===", "" + buffer[i]);
						}
						Logger.d(TAG, "len 1 " + buffer);
						if (len > 0) {
							Logger.d(TAG, "len " + len);
							int[] soundArr = ADPCM.decodeAdpcm(handleBuffer, 0, buffer, 0, 100, 107);
							*//*********converting 200 samples to 400 byte**********************************//*
							byte[] finalArray = new byte[400];
							int count = 0;
							for (int i = 0; i < soundArr.length; i++) {
								byte msb = (byte) ((soundArr[i] & 0xff) >> 8);
								finalArray[count++] = msb;
								byte lsb = (byte) (soundArr[i] & 0xff);
								finalArray[count++] = lsb;
							}
							audioTrack.write(finalArray, 0, finalArray.length);
						} else {
							Logger.d(TAG, "len else " + len);
							break;
						}
					}
					fin.close();
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return buffer;
	}*/


//    public byte[] readSoundFromFile(){
//        readSound = true;
//        FileInputStream fin=null;
//        byte[] buffer = new byte[FRAME_SIZE];
//        Logger.d(TAG, "buffer length "+buffer.length);
//        try {
//            if(useSensor){
//                fin = context.openFileInput(FILENAME);
//                Logger.d(TAG, "available "+fin.available());
//
//            }else{
//                File file = new File(Environment.getExternalStorageDirectory().toString()+ File.separator + "Remedi"+File.separator+"fetaldoppler_sample");
////				File file = new File(context.getExternalFilesDir(null), "Remedi/"+"fetaldoppler_sample");
//                if(!file.exists()){
//                    return null;
//                }else{
//                    Logger.d(TAG, "FILE Found");
//                    fin = new FileInputStream(file);
//                }
//            }
//            if(fin != null){
//                while (readSound) {
//                    int len = fin.read(buffer, 0, FRAME_SIZE);
//                    Logger.d(TAG, "len 1 "+len);
//                    if(len > 0){
//                        Logger.d(TAG, "len "+len);
////                        int[] soundArr = ADPCM.decodeAdpcm(handleBuffer, 0, buffer, 0, 100, 107);
//                        byte[] finalArray = new byte[400];
//                        int count = 0;
//                        for (int i = 0; i < soundArr.length; i++) {
//                            byte msb = (byte) ((soundArr[i] & 0xff) >> 8);
//                            finalArray[count++] = msb;
//                            byte lsb = (byte) (soundArr[i] & 0xff);
//                            finalArray[count++] = lsb;
//                        }
//                        audioTrack.write(finalArray, 0, finalArray.length);
//                    }else{
//                        Logger.d(TAG, "len else "+len);
//                        break;
//                    }
//                }
//                fin.close();
//            }
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return buffer;
//    }

//    public void readFile(){
//
//        try {
//            readSound = true;
//            FileInputStream fin=null;
//            byte[] buffer = new byte[FRAME_SIZE];
//            Logger.d(TAG, "buffer length "+buffer.length);
//            FileInputStream outputStream = new FileInputStream(new File(subFolder, FILENAME));
//            while (readSound) {
//                int len = outputStream.read(buffer, 0, FRAME_SIZE);
//                Logger.d(TAG, "len 1 "+len);
//                if(len > 0){
//                    Logger.d(TAG, "len "+len);
//                    int[] soundArr = ADPCM.decodeAdpcm(handleBuffer, 0, buffer, 0, 100, 107);
//                    byte[] finalArray = new byte[400];
//                    int count = 0;
//                    for (int i = 0; i < soundArr.length; i++) {
//                        byte msb = (byte) ((soundArr[i] & 0xff) >> 8);
//                        finalArray[count++] = msb;
//                        byte lsb = (byte) (soundArr[i] & 0xff);
//                        finalArray[count++] = lsb;
//                    }
//                    audioTrack.write(finalArray, 0, finalArray.length);
//                }else{
//                    Logger.d(TAG, "len else "+len);
//                    break;
//                }
//            }
//            outputStream.close();
//
//        } catch (FileNotFoundException e) {
//            Log.e("ERROR", e.toString());
//        } catch (IOException e) {
//            Log.e(TAG, e.toString());
//        }
//
//
//
//
//    }


    private class BeatThread extends Thread {
        private boolean isRun;


        private int rate;
        public Handler mHandler;
        public BeatThread() {
            isRun = true; // Initialize the thread to run
//            rat e = 0;     // Initialize the rate field to a default value
        }

        public void run() {
            Looper.prepare();
            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    if(msg.what == MESSAGE_FHR) {
                        byte readBuf[] = (byte[]) msg.obj;

                        if(useSensor){
                            //saveFetalSound(readBuf);

                            if (useBLEService) {
                                Log.d(TAG, "handleMessage: reding buffer "+ Arrays.toString(readBuf));

                                playSoundFetoSense(readBuf);
                            } else {
                                Log.d(TAG, "handleMessage: reding buffer "+ Arrays.toString(readBuf));
//                                playSound(readBuf);
                            }
                            //writeToFil(byteArrayToHexString(readBuf));
                            //saveFetalSound(readBuf);
                            //save(readBuf);
                            //'writeT(readBuf);+

                        }
                    }
                }
            };
            Looper.loop();

        	/*while (isRun) {

                if (this.rate > 0) {
                    try {
                        Thread.sleep((long) (30000 / rate));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mHandler.sendEmptyMessage(MESSAGE_FHR);
                } else {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e2) {
                        e2.printStackTrace();
                    }
                }
            }*/
        }

        public void stopSelf() {
            this.isRun = false;
            //mHandler.removeMessages(MESSAGE_FHR);
            mHandler.getLooper().quit();
        }
    }

    public interface BluetoothConnectionStatus{
        public void onNotConnected(String message);
        public void onConnecting();
        public void onConnected(String deviceName);
    }

    public interface FetalDopplerReading{
        public void fetalDopplerReadingReceived(int heartRate,int tocoWave,int afmWave,int status1,int status2,byte[] data,boolean b);
        public void fetalDopplerframeRecieved(byte[] data,boolean b);

    }

}