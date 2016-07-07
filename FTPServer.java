import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;

public class FTPServer {

	public static void main(String[] args) throws UnknownHostException,
			IOException {
		FTPServer server = new FTPServer();
		if (args.length == 1) {
			server.controlPort = Integer.parseInt(args[0]);
			server.dataPort = server.controlPort + 1;
		} else if (args.length == 2) {
			server.controlPort = Integer.parseInt(args[0]);
			server.dataPort = Integer.parseInt(args[1]);
		}
		server.welcomeSocket = new ServerSocket(server.controlPort);
		server.run();
	}

	private static final int SIZE_OF_BYTE_ARRAY = 1000000;
	private static final String PASV_COMMAND = "PASV";
	private static final String DIR_COMMAND = "DIR";
	private static final String GET_COMMAND = "GET";
	private static final String PUT_COMMAND = "PUT";
	private static final String OK_DIR_RESPONSE = "200 DIR COMMAND OK";
	private static final String UNKNOWN_RESPONSE = "500 UNKNOWN COMMAND";
	private static final String INVALID_ARGS_RESPONSE = "501 INVALID ARGUMENTS";
	private static final String FILE_NOT_FOUND_RESPONSE = "401 FILE NOT FOUND";
	private static final String OK_RESPONSE = "200 OK";
	private static final String OK_GET_RESPONSE = "200 GET COMMAND OK";
	private static final String OK_PUT_RESPONSE = "200 PUT COMMAND OK";
	private int controlPort;
	private String currentPath;
	private DataOutputStream outFromControl;
	private BufferedReader inFromControl;
	FileOutputStream outToLog;
	private int dataPort;
	private ServerSocket welcomeSocket;

	private ArrayList<String> fileNames;

	public FTPServer() throws SecurityException, IOException {
		currentPath = System.getProperty("user.dir");
	}

	private String createMessage(String message) {
		return (message + "\r\n");
	}

	private String getFileName(String pathOfFileToGet) {
		return pathOfFileToGet.substring(pathOfFileToGet.lastIndexOf("/") + 1);
	}

	private void listFilesForFolder(File folder, String currentDirectory) {
		for (File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory()) {
				listFilesForFolder(fileEntry,
						currentDirectory + fileEntry.getName() + "/");
			} else {
				fileNames.add(currentDirectory + fileEntry.getName());
			}
		}
	}

	private void run() throws UnknownHostException, IOException {
		while (true) {
			fileNames = new ArrayList<String>();
			Socket controlSocket = welcomeSocket.accept();
			inFromControl = new BufferedReader(new InputStreamReader(
					controlSocket.getInputStream()));
			outFromControl = new DataOutputStream(
					controlSocket.getOutputStream());
			if (inFromControl.readLine().equals(PASV_COMMAND)) {
				String response = "200 PORT "
						+ InetAddress.getLocalHost().getHostAddress() + " "
						+ dataPort;
				byte[] responseBytes = createMessage(response).getBytes();
				outFromControl.write(responseBytes);
			} else {
				continue;
			}
			String[] commandArray = inFromControl.readLine().split(" ");
			String command = commandArray[0];
			if (command.equals(DIR_COMMAND)) {
				if (commandArray.length >= 2) {
					sendInvalidArgsResponse(controlSocket);
					continue;
				} else {
					ServerSocket dataSocket = new ServerSocket(dataPort);
					byte[] responseBytes = createMessage(OK_DIR_RESPONSE)
							.getBytes();
					outFromControl.write(responseBytes);
					Socket clientSocket = dataSocket.accept();
					DataOutputStream outToData = new DataOutputStream(
							clientSocket.getOutputStream());
					File folder = new File(currentPath + "/server-directory");
					listFilesForFolder(folder, "");
					Collections.sort(fileNames);
					for (int i = 0; i < fileNames.size(); i++) {
						String fileName = fileNames.get(i);
						if (i != fileNames.size() - 1) {
							outToData.write((fileName + "\n").getBytes());
						} else {
							outToData.write(fileName.getBytes());
						}
					}
					dataSocket.close();
					outToData.close();
				}
			} else if (command.equals(GET_COMMAND)) {
				if (commandArray.length != 2) {
					sendInvalidArgsResponse(controlSocket);
					continue;
				} else {
					String pathOfFileToSend = commandArray[1];
					File fileToSend = new File(currentPath
							+ "/server-directory/" + pathOfFileToSend);
					if (!fileToSend.exists()) {
						sendFileNotFoundResponse(controlSocket);
						continue;
					} else {
						ServerSocket dataSocket = new ServerSocket(dataPort);
						byte[] responseBytes = createMessage(OK_GET_RESPONSE)
								.getBytes();
						outFromControl.write(responseBytes);
						Socket clientSocket = dataSocket.accept();
						DataOutputStream outToData = new DataOutputStream(
								clientSocket.getOutputStream());
						FileInputStream inputFromFile = new FileInputStream(
								fileToSend);
						byte[] bytes = new byte[SIZE_OF_BYTE_ARRAY];
						int readNum = inputFromFile.read(bytes);
						while (readNum != -1) {
							outToData.write(bytes, 0, readNum);
							readNum = inputFromFile.read(bytes);
						}
						dataSocket.close();
						inputFromFile.close();
						outToData.close();
					}
				}
			} else if (command.equals(PUT_COMMAND)) {
				if (commandArray.length != 2 && commandArray.length != 3) {
					// To check whether file directory is valid to put in
					sendInvalidArgsResponse(controlSocket);
					continue;
				} else {
					String directoryToPut;
					if (commandArray.length == 3) {
						directoryToPut = currentPath + "/server-directory/"
								+ commandArray[2] + "/";
						File directoryOfFileToPut = new File(directoryToPut);
						if (!directoryOfFileToPut.exists()) {
							directoryOfFileToPut.mkdir();
						}
					} else { // commandArray.length = 2
						directoryToPut = currentPath + "/server-directory/";
					}
					String fileName = getFileName(commandArray[1]);
					File file = new File(directoryToPut + fileName);
					if (!file.exists()) {
						file.createNewFile();
					}
					ServerSocket dataSocket = new ServerSocket(dataPort);
					byte[] responseBytes = createMessage(OK_PUT_RESPONSE)
							.getBytes();
					outFromControl.write(responseBytes);
					Socket clientSocket = dataSocket.accept();
					FileOutputStream outToFile = new FileOutputStream(file);
					InputStream inFromClient = clientSocket.getInputStream();
					writeToFile(outToFile, inFromClient);
					dataSocket.close();
					outToFile.close();
					inFromClient.close();
				}
			} else {
				sendUnknownFileResponse(controlSocket);
				continue;
			}
			byte[] responseBytes = createMessage(OK_RESPONSE).getBytes();
			outFromControl.write(responseBytes);
			controlSocket.close();
		}
	}

	private void sendResponse(String message, Socket controlSocket)
			throws IOException {
		byte[] responseBytes = createMessage(message).getBytes();
		outFromControl.write(responseBytes);
		controlSocket.close();
	}

	private void sendFileNotFoundResponse(Socket controlSocket)
			throws IOException {
		sendResponse(FILE_NOT_FOUND_RESPONSE, controlSocket);
	}

	private void sendUnknownFileResponse(Socket controlSocket)
			throws IOException {
		sendResponse(UNKNOWN_RESPONSE, controlSocket);
	}

	private void sendInvalidArgsResponse(Socket controlSocket)
			throws IOException {
		sendResponse(INVALID_ARGS_RESPONSE, controlSocket);
	}

	private void writeToFile(FileOutputStream out, InputStream input)
			throws IOException {
		byte[] bytes = new byte[SIZE_OF_BYTE_ARRAY];
		int readNum = input.read(bytes);
		while (readNum != -1) {
			out.write(bytes, 0, readNum);
			readNum = input.read(bytes);
		}
	}
}
