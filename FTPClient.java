import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

public class FTPClient {

	public enum Command {
		DIR, GET, PUT
	}

	public static void main(String[] args) throws UnknownHostException,
	IOException {
		FTPClient client = new FTPClient();
		if (args.length == 3
				&& client.commandMapping.get(args[2].toUpperCase()) == Command.DIR) {
			client.controlIP = args[0];
			client.controlPort = Integer.parseInt(args[1]);
			client.command = Command.DIR;
		} else if (args.length == 4
				&& client.commandMapping.get(args[2].toUpperCase()) == Command.GET) {
			client.controlIP = args[0];
			client.controlPort = Integer.parseInt(args[1]);
			client.command = Command.GET;
			client.pathOfFileToGet = args[3];
		} else if (args.length >= 4
				&& client.commandMapping.get(args[2].toUpperCase()) == Command.PUT) {
			client.controlIP = args[0];
			client.controlPort = Integer.parseInt(args[1]);
			client.command = Command.PUT;
			client.pathOfFileToGet = args[3];
			if (args.length == 5) {
				client.pathToPutInServer = args[4];
			} else {
				client.pathToPutInServer = null;
			}
		}
		client.executeCommand();
	}

	private static final String PUT = "PUT";
	private static final String FILE_NOT_FOUND = "FILE NOT FOUND";
	private static final int SIZE_OF_BYTE_ARRAY = 1000000;
	private static final String OK_NUMBER = "200";
	private static final String PASV_COMMAND = "PASV";
	private static final String DIR_COMMAND = "DIR";
	private static final String OK_DIR_RESPONSE = "200 DIR COMMAND OK";
	private static final String UNKNOWN_RESPONSE = "500 COMMAND INVALID";
	private static final String INVALID_ARGS_RESPONSE = "501 INVALID ARGUMENTS";
	private static final String GET_COMMAND = "GET";
	private static final String OK_GET_RESPONSE = "200 GET COMMAND OK";
	private static final Object OK_PUT_RESPONSE = "200 PUT COMMAND OK";
	private static final String FILE_NOT_FOUND_RESPONSE = "401 FILE NOT FOUND";
	private String controlIP;
	private int controlPort;
	private String serverIP;
	private int serverPort;
	private Command command;
	private String pathOfFileToGet;
	private HashMap<String, Command> commandMapping = new HashMap<String, Command>();
	private String pathToPutInServer;
	private Socket controlSocket;
	private Socket dataSocket;
	private String currentPath;
	private DataOutputStream outToControl;
	private BufferedReader inFromControl;
	private String replyFromServer;

	private File log;

	FileOutputStream outToLog;

	public FTPClient() throws SecurityException, IOException {
		commandMapping.put("DIR", Command.DIR);
		commandMapping.put("GET", Command.GET);
		commandMapping.put(PUT, Command.PUT);
		currentPath = System.getProperty("user.dir");
		log = new File(currentPath + "/log");
		outToLog = new FileOutputStream(log, true);
	}

	private String createMessage(String message) {
		return (message + "\r\n");
	}

	private void dir() throws IOException, UnknownHostException,
	FileNotFoundException {
		setUpControl();
		outToControl.writeBytes(createMessage(DIR_COMMAND));
		replyFromServer = inFromControl.readLine();
		if (replyFromServer.equals(UNKNOWN_RESPONSE)) {
			writeMessageToLog(UNKNOWN_RESPONSE);
		} else if (replyFromServer.equals(INVALID_ARGS_RESPONSE)) {
			writeMessageToLog(INVALID_ARGS_RESPONSE);
		} else if (replyFromServer.equals(OK_DIR_RESPONSE)) {
			dataSocket = new Socket(InetAddress.getByName(serverIP), serverPort);
			File file = new File(currentPath
					+ "/client-directory/directory_listing");
			if (!file.exists()) {
				file.createNewFile();
			}
			FileOutputStream out = new FileOutputStream(file);
			InputStream input = dataSocket.getInputStream();
			writeToFile(out, input, false);
			out.close();
			input.close();
		}
	}

	private void executeCommand() throws UnknownHostException, IOException {
		switch (command) {
		case DIR:
			dir();
			break;
		case GET:
			get();
			break;
		case PUT:
			put();
			break;
		default:
			break;

		}
	}

	private void get() throws IOException, UnknownHostException,
	FileNotFoundException {
		setUpControl();
		outToControl.writeBytes(createMessage(GET_COMMAND + " "
				+ pathOfFileToGet));
		replyFromServer = inFromControl.readLine();
		if (replyFromServer.equals(UNKNOWN_RESPONSE)) {
			writeMessageToLog(UNKNOWN_RESPONSE);
		} else if (replyFromServer.equals(INVALID_ARGS_RESPONSE)) {
			writeMessageToLog(INVALID_ARGS_RESPONSE);
		} else if (replyFromServer.equals(FILE_NOT_FOUND_RESPONSE)) {
			writeMessageToLog(FILE_NOT_FOUND_RESPONSE);
		} else if (replyFromServer.equals(OK_GET_RESPONSE)) {
			dataSocket = new Socket(InetAddress.getByName(serverIP), serverPort);
			String fileName = getFileName(pathOfFileToGet);
			File file = new File(currentPath + "/client-directory/" + fileName);
			if (!file.exists()) {
				file.createNewFile();
			}
			FileOutputStream out = new FileOutputStream(file);
			InputStream input = dataSocket.getInputStream();
			writeToFile(out, input, false);
			out.close();
			input.close();
		}
	}

	private String getFileName(String pathOfFileToGet) {
		return pathOfFileToGet.substring(pathOfFileToGet.lastIndexOf("/") + 1);
	}

	private void put() throws IOException, UnknownHostException,
	FileNotFoundException {
		String filePathString = currentPath + "/client-directory/"
				+ pathOfFileToGet;
		File f = new File(filePathString);
		if (f.exists()) {
			setUpControl();
			if (pathToPutInServer == null) {
				outToControl.writeBytes(createMessage(PUT + " "
						+ pathOfFileToGet));
			} else {
				outToControl.writeBytes(createMessage(PUT + " "
						+ pathOfFileToGet + " " + pathToPutInServer));
			}
			replyFromServer = inFromControl.readLine();
			if (replyFromServer.equals(UNKNOWN_RESPONSE)) {
				writeMessageToLog(UNKNOWN_RESPONSE);
			} else if (replyFromServer.equals(INVALID_ARGS_RESPONSE)) {
				writeMessageToLog(INVALID_ARGS_RESPONSE);
			} else if (replyFromServer.equals(OK_PUT_RESPONSE)) {
				dataSocket = new Socket(InetAddress.getByName(serverIP),
						serverPort);
				DataOutputStream outToData = new DataOutputStream(
						dataSocket.getOutputStream());
				FileInputStream inputFromFile = new FileInputStream(f);
				byte[] bytes = new byte[SIZE_OF_BYTE_ARRAY];
				int readNum = inputFromFile.read(bytes);
				while (readNum != -1) {
					outToData.write(bytes, 0, readNum);
					readNum = inputFromFile.read(bytes);
				}
				outToData.close();
				replyFromServer = inFromControl.readLine();
				writeMessageToLog(replyFromServer);
				inputFromFile.close();
				inFromControl.close();
			}
		} else {
			writeMessageToLog(FILE_NOT_FOUND);
		}
	}

	private void setUpControl() throws IOException, UnknownHostException {
		controlSocket = new Socket(InetAddress.getByName(controlIP),
				controlPort);
		outToControl = new DataOutputStream(controlSocket.getOutputStream());
		inFromControl = new BufferedReader(new InputStreamReader(
				controlSocket.getInputStream()));
		outToControl.writeBytes(createMessage(PASV_COMMAND));
		replyFromServer = inFromControl.readLine();
		// 200 PORT 172.25.64.87 12991
		String[] replyArray = replyFromServer.split(" ");
		if (replyArray[0].equals(OK_NUMBER)) {
			serverIP = replyArray[2];
			serverPort = Integer.parseInt(replyArray[3]);
		}
	}

	private void writeMessageToLog(String message) throws IOException {
		String actualMessage;
		if (log.length() == 0) {
			actualMessage = message;
		} else {
			actualMessage = "\n" + message;
		}
		FileOutputStream toLog = new FileOutputStream(log, true);
		byte[] bytes = actualMessage.getBytes();
		toLog.write(bytes);
		toLog.close();
	}

	private void writeToFile(FileOutputStream out, InputStream input,
			Boolean addNewLine) throws IOException {
		byte[] bytes = new byte[SIZE_OF_BYTE_ARRAY];
		int readNum = input.read(bytes);
		while (readNum != -1) {
			out.write(bytes, 0, readNum);
			readNum = input.read(bytes);
		}
		if (addNewLine) {
			out.write("\r\n".getBytes());
		}
	}
}
