package Utils;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;

public class StreamHandler {
    private final Scanner scanner;
    private final PrintStream printStream;
    private final Socket socket;
    private final Object outLock = new Object(), inLock = new Object();
    public StreamHandler(Socket socket) throws IOException {
        scanner = new Scanner(socket.getInputStream());
        printStream = new PrintStream(socket.getOutputStream());
        this.socket = socket;
    }

    public void close() {
        scanner.close();
        printStream.close();
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        synchronized (outLock) {
            printStream.println(message);
        }
    }

    public String getResponse() {
        synchronized (inLock) {
            return scanner.nextLine();
        }
    }

}
