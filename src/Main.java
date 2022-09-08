import MainServer.MainServer;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        try {
            MainServer.setup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
