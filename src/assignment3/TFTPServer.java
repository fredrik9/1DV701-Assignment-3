package assignment3;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class TFTPServer {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "C:\\temp\\"; // custom address at your
														// PC
	public static final String WRITEDIR = "C:\\temp\\"; // custom address at
														// your PC
	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		// Starting the server
		try {
			TFTPServer server = new TFTPServer();
			server.start();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private void start() throws SocketException {
		byte[] buf = new byte[BUFSIZE];

		// Create socket
		DatagramSocket socket = new DatagramSocket(null);

		// Create local bind point
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests
		while (true) {

			final InetSocketAddress clientAddress = receiveFrom(socket, buf);

			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null)
				continue;

			final StringBuffer requestedFile = new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);

			new Thread() {
				public void run() {
					try {
						DatagramSocket sendSocket = new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);
						
						 System.out.printf("%s request for %s from %s using port %d\n",
						 (reqtype == OP_RRQ)?"Read":"Write",clientAddress.getAddress(),
						 clientAddress.getHostName(),
						 clientAddress.getPort());
						
						// Read request
						if (reqtype == OP_RRQ) {
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
						}
						// Write request
						else {
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_WRQ);
						}

						sendSocket.close();
					} catch (SocketException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}
	}

	/**
	 * Reads the first block of data, i.e., the request for an action (read or
	 * write).
	 * 
	 * @param socket
	 *            (socket to read from)
	 * @param buf
	 *            (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
		// Creating receive packet.
		DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);

		// Receiving packet from socket.
		try {
			socket.receive(receivePacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Getting address from packet.
		InetSocketAddress socketAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());

		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and
	 * requestedFile
	 * 
	 * @param buf
	 *            (received request)
	 * @param requestedFile
	 *            (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) {

		ByteBuffer wrap = ByteBuffer.wrap(buf);

		// First 2 bytes
		short opcode = wrap.getShort();

		// Reads the fileName and excludes the 0 byte
		while (true) {

			// Converts from Octet to ASCII
			char c = (char) wrap.get();

			if (c == 0 || !wrap.hasRemaining())
				break;
			requestedFile.append(c);
		}

		String mode = "";
		// Reads the mode and excludes the 0 byte
		while (true) {

			// Converts from Octet to ASCII
			char c = (char) wrap.get();

			if (c == 0 || !wrap.hasRemaining())
				break;

			mode += c;
		}
		if (!mode.equals("octet")) {
			return OP_ERR;
		}

		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests
	 * 
	 * @param sendSocket
	 *            (socket used to send/receive packets)
	 * @param requestedFile
	 *            (name of file to read/write)
	 * @param opcode
	 *            (RRQ or WRQ)
	 */

	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {

		try {

			
			short blockNumber = 1;
			if (opcode == OP_RRQ) {
				// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
				
				// Read file
				ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(Paths.get(requestedFile.toString())));

				boolean result = send_DATA_receive_ACK(sendSocket, requestedFile, blockNumber, data);
			}
			
			else if (opcode == OP_WRQ) {

				boolean result = receive_DATA_send_ACK(params);

			} else {

				System.err.println("Invalid request. Sending an error packet.");

				send_ERR(params);

				return;
			}

			
		} catch (IOException e) {

			e.printStackTrace();
		}
	}

	private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, String requestedFile, short blockNumber, ByteBuffer data) {

		byte[] buf = new byte[BUFSIZE];

		try {

			// Opcode
			buf[0] = 0;
			buf[1] = 3;
			// Split the block number
			buf[2] = (byte) ((blockNumber >> 8) & 0xff);
			buf[3] = (byte) (blockNumber & 0xff);
			// Include file data
			while (data.position() < 512 && data.hasRemaining()) {
				buf[4 + data.position()] = data.get();
			}

			// Send the bytes
			sendSocket.send(new DatagramPacket(buf, buf.length, sendSocket.getRemoteSocketAddress()));

			// Empty buf to read ACK
			Arrays.fill(buf, (byte) 0);

			// Receive ACK
			sendSocket.receive(new DatagramPacket(buf, buf.length));

			// Returns false if it needs to retransmit the packet
			// Combines 2 bytes and checks the opcode
			if ((buf[0] << 8) + (buf[1] & 0xff) != 4)
				return false;
			
			// Combines 2 bytes and checks the block number
			else if ((buf[2] << 8) + (buf[3] & 0xff) != blockNumber)
				return false;
			
			else
				System.out.printf("Block# %s was succesfully Sent and Received.\r\n", (int)blockNumber);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
	}

	
	private boolean receive_DATA_send_ACK(params) {
		 return true;
		 }

	private void send_ERR(params) {
		  
	  }
	
}
