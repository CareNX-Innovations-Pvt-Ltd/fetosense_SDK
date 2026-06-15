package com.carenx.feton;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for incoming
 * connections, a thread for connecting with a device, and a thread for
 * performing data transmissions when connected.
 */
public class FDBluetoothService {
	// Debugging
	private static final String TAG = "BluetoothService";
	private static final boolean D = true;

	// Name for the SDP record when creating server socket
	private static final String NAME = "Bluetooth";

	// Unique UUID for this application
	private static final UUID MY_UUID = UUID.fromString("0001101-0000-1000-8000-00805F9B34FB");
	public List<List<Byte>> dataFromKit = new ArrayList<List<Byte>>();
	private String service_error="",leads_missing="",leads_not_connected = "";

	/*************  The Class object for Process data library  **************/
	private String ecg_data="";
	// INSECURE "8ce255c0-200a-11e0-ac64-0800200c9a66"
	// SECURE "fa87c0d0-afac-11de-8a39-0800200c9a66"
	// SPP "0001101-0000-1000-8000-00805F9B34FB"

	// Member fields
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private AcceptThread mAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0; // we're doing nothing
	public static final int STATE_LISTEN = 1; // now listening for incoming
	// connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing
	// connection
	public static final int STATE_CONNECTED = 3; // now connected to a remote
	// device

	private Context context;
	private String fileName;
	public static String id=null;
	/**
	 * Constructor. Prepares a new BluetoothChat session.
	 *
	 * @param context
	 *            The UI Activity Context
	 * @param handler
	 *            A Handler to send messages back to the UI Activity
	 */
	public FDBluetoothService(Context context, Handler handler,String id) {
        this.context = context;
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = handler;
		this.id=id;
	}

	/**
	 * Set the current state of the chat connection
	 *
	 * @param state
	 *            An integer defining the current connection state
	 */
	private synchronized void setState(int state) {
		if (D)
			Logger.d(TAG, "setState() " + mState + " -> " + state);
		int change=mState;
		mState = state;
		Logger.d("connected service",""+state);
		if (change==3 && state==1){
			connectionLost();
		}

		// Give the new state to the Handler so the UI Activity can update
		mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_STATE_CHANGE, state, -1)
				.sendToTarget();
	}

	/**
	 * Return the current connection state.
	 */
	public synchronized int getState() {
		return mState;
	}

	/**
	 * Start the chat service. Specifically start AcceptThread to begin a
	 * session in listening (server) mode. Called by the Activity onResume()
	 */
	public synchronized void start() {
		if (D)
			Logger.d(TAG, "start");

		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		setState(STATE_LISTEN);

		// Start the thread to listen on a BluetoothServerSocket
		if (mAcceptThread == null) {
			mAcceptThread = new AcceptThread();
			mAcceptThread.start();
		}
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 *
	 * @param device
	 *            The BluetoothDevice to connect
	 */
	public synchronized void connect(BluetoothDevice device) {
		if (D)
			Logger.d(TAG, "connect to: " + device);

		// Cancel any thread attempting to make a connection
		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(STATE_CONNECTING);
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 *
	 * @param socket
	 *            The BluetoothSocket on which the connection was made
	 * @param device
	 *            The BluetoothDevice that has been connected
	 */
	public synchronized void connected(BluetoothSocket socket,
									   BluetoothDevice device, final String socketType) {
		Log.d(TAG, "connected: socket type"+socketType);
		if (D)
			Logger.d(TAG, "connected, Socket Type:" + socketType);

		// Cancel the thread that completed the connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Cancel the accept thread because we only want to connect to one
		// device
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket, socketType);
		mConnectedThread.start();

		// Send the name of the connected device back to the UI Activity
		Message msg = mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothMeasureParameter.DEVICE_NAME, device.getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		setState(STATE_CONNECTED);
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop() {
		try {
			if (D)
				Logger.d(TAG, "stop");

			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}

			if (mConnectedThread != null) {
				mConnectedThread.cancel();
				mConnectedThread = null;
			}

			if (mAcceptThread != null) {
				mAcceptThread.cancel();
				mAcceptThread = null;
			}
			setState(STATE_NONE);
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 *
	 * @param out
	 *            The bytes to write
	 * @see ConnectedThread#write(byte[])
	 */
	public void write(byte[] out) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
	}

	/**
	 * Indicate that the connection attempt failed and notify the UI Activity.
	 */
	private void connectionFailed() {
        if (context != null) {
            // Send a failure message back to the Activity
            Message msg = mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_TOAST);
            Bundle bundle = new Bundle();
            bundle.putString(BluetoothMeasureParameter.TOAST, context.getResources().getString(R.string.unable_to_connect_device));
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
		// Start the service over to restart listening mode
		FDBluetoothService.this.start();
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost() {
        if (context != null) {
            // Send a failure message back to the Activity
            Message msg = mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_TOAST);
            Bundle bundle = new Bundle();
            bundle.putString(BluetoothMeasureParameter.TOAST, context.getResources().getString(R.string.device_connection_lost));
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }

		// Start the service over to restart listening mode
		FDBluetoothService.this.start();
	}

	/**
	 * This thread runs while listening for incoming connections. It behaves
	 * like a server-side client. It runs until a connection is accepted (or
	 * until cancelled).
	 */
	private class AcceptThread extends Thread {
		// The local server socket
		private final BluetoothServerSocket mmServerSocket;
		private String mSocketType;

		public AcceptThread() {
			BluetoothServerSocket tmp = null;

			// Create a new listening server socket
			try {

				tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, MY_UUID);

			} catch (IOException e) {
				Logger.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
			}
			mmServerSocket = tmp;
		}

		public void run() {
			if (D)
				Logger.d(TAG, "Socket Type: " + mSocketType
						+ "BEGIN mAcceptThread" + this);
			setName("AcceptThread" + mSocketType);

			BluetoothSocket socket = null;

			// Listen to the server socket if we're not connected
			while (mState != STATE_CONNECTED) {
				try {
					// This is a blocking call and will only return on a
					// successful connection or an exception
					socket = mmServerSocket.accept();
				} catch (Exception e) {
					//connectionLost();
					Logger.e(TAG, "Socket Type: " + mSocketType
							+ "accept() failed", e);
					break;
				}

				// If a connection was accepted
				if (socket != null) {
					synchronized (FDBluetoothService.this) {
						switch (mState) {
							case STATE_LISTEN:
							case STATE_CONNECTING:
								// Situation normal. Start the connected thread.
								connected(socket, socket.getRemoteDevice(),
										mSocketType);
								break;
							case STATE_NONE:
							case STATE_CONNECTED:
								// Either not ready or already connected. Terminate
								// new socket.
								try {
									socket.close();
								} catch (IOException e) {
									Logger.e(TAG, "Could not close unwanted socket", e);
								}
								break;
						}
					}
				}
			}
			if (D)
				Logger.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

		}

		public void cancel() {
			if (D)
				Logger.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
			try {
				mmServerSocket.close();

			} catch (Exception e) {
				//connectionLost();

				Logger.e(TAG, "Socket Type" + mSocketType
						+ "close() of server failed", e);
			}
		}
	}

	/**
	 * This thread runs while attempting to make an outgoing connection with a
	 * device. It runs straight through; the connection either succeeds or
	 * fails.
	 */
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		private String mSocketType;

		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;
			BluetoothSocket tmp = null;

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);

			} catch (IOException e) {
				Logger.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
			}
			mmSocket = tmp;
		}

		public void run() {
			Logger.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
			setName("ConnectThread" + mSocketType);

			// Always cancel discovery because it will slow down a connection
			mAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception
				mmSocket.connect();
			} catch (IOException e) {
				// Close the socket
				try {
					mmSocket.close();
				} catch (IOException e2) {
					Logger.e(TAG, "unable to close() " + mSocketType
							+ " socket during connection failure", e2);
				}
				connectionFailed();
				return;
			}

			// Reset the ConnectThread because we're done
			synchronized (FDBluetoothService.this) {
				mConnectThread = null;
			}

			// Start the connected thread
			connected(mmSocket, mmDevice, mSocketType);
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Logger.e(TAG, "close() of connect " + mSocketType
						+ " socket failed", e);
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket, String socketType) {
			Logger.d(TAG, "create ConnectedThread: " + socketType);
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Logger.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			Logger.i(TAG, "BEGIN mConnectedThread");
			byte[] buffer = new byte[162];
			int bytes;

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read(buffer);

					/*for(int i=0;i<buffer.length;i++)
					{
						Logger.d("bytes====",""+buffer[i]);
					}*/

					//Logger.d("bytes====",""+bytes);
					Log.d(TAG, "run: info"+bytes);

					if(bytes > 0){
						// Send the obtained bytes to the UI Activity
						mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_READ, bytes,
								-1, buffer).sendToTarget();

						//writeToFile(byteArrayToHexString(buffer));
						//	writeT(byteArrayToHexString(buffer));

					}
				} catch (IOException e) {
					Logger.e(TAG, "disconnected", e);
					//connectionLost();
					// Start the service over to restart listening mode
					FDBluetoothService.this.start();
					break;
				}
			}
		}


		private  PrintWriter out_e = null;
		private  PrintWriter printWriter = null;
		private  File file_e = null;
		public  final String folderNameFetal = Environment.getExternalStorageDirectory().toString()+ File.separator+"Remedi"+File.separator+"Third_Party"+File.separator;


		private void writeToFile(String LoggerMsg) {

			if (out_e == null) {

				file_e = new File(folderNameFetal);

				File file = new File(file_e, "Fetal_Sample.txt");
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



		/**
		 * Write to the connected OutStream.
		 *
		 * @param buffer
		 *            The bytes to write
		 */
		public void write(byte[] buffer) {
			try {
				mmOutStream.write(buffer);

				// Share the sent message back to the UI Activity
				mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_WRITE, -1, -1,
						buffer).sendToTarget();
			} catch (IOException e) {
				Logger.e(TAG, "Exception during write", e);
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Logger.e(TAG, "close() of connect socket failed", e);
			}
		}
	}


	/*
	 * This thread is used to receive(read from socket) frames from input stream
	 * of socket
	 */
	private class ConnectedThread1 extends Thread {

		private InputStream mmInStream;
		private OutputStream mmOutStream;
		Context cont;

		BluetoothSocket mmSocket;

		static final int FRAME_HEADER = 100;
		static final int FRAME_BODY = 200;
		static final int FRAME_FOOTER = 300;

		static final int FRAME_HEADER_START_VALUE = 83;// hexadecimal value of 'S'
		static final int FRAME_HEADER_END_VALUE = 72;  // hexadecimal value of 'H'
		static final int FRAME_BODY_DATA_TYPE_VALUE=-38;

		static final int FRAME_BODY_PACKETLENGTH_STARTBYTE_POS = 3;
		static final int FRAME_BODY_PACKETLENGTH_ENDBYTE_POS = 4;

		static final int FRAME_FOOTER_START_VALUE = 69; //hexadecimal value of 'S'
		static final int FRAME_FOOTER_END_VALUE = 70;   //hexadecimal value of 'S'
		int unitValue = 10;
		int singleByteValue = 0;
		int frameBodyCount = 1;
		int framePosition = FRAME_HEADER;
		int payLoadLength = 0;
		int counter = 0;
		int totalFrameSize = 162;
		int totalCount = 625;

		//		public ReadDataThread(BluetoothSocket socket) {
		public ConnectedThread1(BluetoothSocket socket, String socketType) {		// rajeev:: removed parameter from the function call
			Logger.d(TAG, "create ConnectedThread: " + socketType);
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Logger.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
			fileName = createFileNameByTimeStamp();

		}

		public void run() {

			int singleByteValue = 0;
			int frameBodyCount = 1;
			int framePosition = FRAME_HEADER;
			int payLoadLength = 0;

			int readBytes; // bytes returned from read()
			int frameType = 0;
			int bytePostn = 0;

			int[] frameReceived = new int[totalFrameSize];
			List<Byte> packet = new ArrayList<Byte>();

			byte[] buffer = new byte[totalFrameSize];
			while(true){

				try {

					try {
						readBytes = mmInStream.read(buffer);
					} catch (Exception e) {
						Logger.d("readBytes**Exception","readBytes**Exception");
						e.printStackTrace();
						break;
					}

					for (bytePostn = 0; bytePostn < readBytes; bytePostn++) {
						singleByteValue = (int)buffer[bytePostn] & 0x000000FF;

						switch (framePosition) {

							case FRAME_HEADER:
								if ((singleByteValue==FRAME_HEADER_START_VALUE)  && ((0x000000FF & (int)buffer[bytePostn+1])==FRAME_HEADER_END_VALUE))
								{
									packet.add((byte) singleByteValue);        // adding 'S' value into  packet list
									frameReceived[0] = singleByteValue; // storing 'S' value into  frameReceived array

									singleByteValue = 0x000000FF & (int)buffer[++bytePostn];
									packet.add((byte) singleByteValue);     // adding 'H' value into  packet list
									frameReceived[1] = singleByteValue;     // storing 'H' value into  frameReceived array


									framePosition = FRAME_BODY;
									frameBodyCount = 2;
								}
								break;
							case FRAME_BODY:
								switch (frameBodyCount) {
									case FRAME_BODY_PACKETLENGTH_STARTBYTE_POS:
										packet.add((byte) singleByteValue);
										frameReceived[frameBodyCount] = singleByteValue;
										payLoadLength = singleByteValue;
										break;

									case FRAME_BODY_PACKETLENGTH_ENDBYTE_POS:
										packet.add((byte) singleByteValue);
										frameReceived[frameBodyCount] = singleByteValue;
										if (frameType != 218) {
											payLoadLength = 2;
										}
										else {

											payLoadLength = 153;


										}

										break;

									default:
										packet.add((byte) singleByteValue);            //hear we are adding the data until
										frameReceived[frameBodyCount] = singleByteValue;

										if (frameBodyCount == 2){
											frameType = singleByteValue;
										}
										if (frameBodyCount > FRAME_BODY_PACKETLENGTH_ENDBYTE_POS && frameBodyCount == payLoadLength + 6) {
											framePosition = FRAME_FOOTER;
										}
										break;
								}
								++frameBodyCount;
								break;

							case FRAME_FOOTER:
							{
								if ((singleByteValue==FRAME_FOOTER_START_VALUE)  && ((0x000000FF & (int)buffer[bytePostn+1])==FRAME_FOOTER_END_VALUE))
								{
									packet.add((byte) singleByteValue);
									frameReceived[frameBodyCount] = singleByteValue;
									++frameBodyCount;
									singleByteValue = 0x000000FF & (int)buffer[++bytePostn];
									packet.add((byte) singleByteValue);
									frameReceived[frameBodyCount++] = singleByteValue;

									framePosition = FRAME_HEADER;
									frameBodyCount = 0;
									singleByteValue = 0;
									frameType = 0;

									synchronized (this) {
										if (dataFromKit != null && ((0x000000FF & (int)packet.get(2)) == 218))	// Data Packet is ready
											dataFromKit.add(packet);								// This will trigger call to ProcessDataLibrary

										packet = new ArrayList<Byte>();

									}
								}					// switch (singleByteValue)

								break;				// case FRAME_FOOTER
							}
						}							// switch (framePosition)
						singleByteValue = 0;
					}							// for loop
					if(dataFromKit.size() == totalCount)
						break;
				} catch (Exception e) {
					e.printStackTrace();
					Logger.d(TAG,"Exception at reading..."+ e.getMessage());
					break;
				}
			}
			stopStreamCommand();
			Logger.d(TAG, "FRAMES RECEIVED");

		}

		/**
		 * Write to the connected OutStream.
		 *
		 * @param buffer
		 *            The bytes to write
		 */
		public void write(byte[] buffer) {
			try {
				mmOutStream.write(buffer);

				// Share the sent message back to the UI Activity
				mHandler.obtainMessage(BluetoothMeasureParameter.MESSAGE_WRITE, -1, -1,
						buffer).sendToTarget();
			} catch (IOException e) {
				Logger.e(TAG, "Exception during write", e);
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Logger.e(TAG, "close() of connect socket failed", e);
			}
		}
	}


	public void stopStreamCommand(){
		mHandler.postDelayed(new Runnable() {

			@Override
			public void run() {
				write(convertingTobyteArray("0x5f 0x5e"));
			}
		}, 0);
	}



	private  PrintWriter out_e = null;
	private  File file_e = null;
	public static final String folderNameECG = Environment.getExternalStorageDirectory().toString()+ File.separator+"Remedi"+File.separator+"ecg"+File.separator;

	private void writeToFile(String LoggerMsg) {
		if (out_e == null) {

			file_e = new File(folderNameECG);

			File file = new File(file_e, fileName);
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

	public static byte[] convertingTobyteArray(String result) {
		String[] splited = result.split("\\s+");
		byte[] valueByte = new byte[splited.length];
		for (int i = 0; i < splited.length; i++) {
			if (splited[i].length() > 2) {
				String trimmedByte = splited[i].split("x")[1];
				valueByte[i] = (byte) convertstringtobyte(trimmedByte);
			}
		}
		return valueByte;
	}


	private void writeT(String LoggerMsg) {
		PrintWriter out_e=null;
		File fil = new File(Environment.getExternalStorageDirectory().toString()+ File.separator + "Remedi"+File.separator+"Fetal_Sound"+File.separator);
		File file = new File(fil, "Fetal_reading_"+id+".txt");
		if (!file.exists()) {
			try {
				fil.mkdirs();
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

	private static int convertstringtobyte(String string) {
		return Integer.parseInt(string, 16);
	}

	private String createFileNameByTimeStamp(){
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
		Date date = new Date();
		String timeStamp = dateFormat.format(date) + ".txt";
		return timeStamp;

	}

	private String byteArrayToHexString(byte[] b) {
		int len = b.length;
		String data = new String();

		for (int i = 0; i < len; i++) {
			data += Integer.toHexString((b[i] >> 4) & 0xf);
			data += Integer.toHexString(b[i] & 0xf) + " ";
		}

		return data;
	}


}