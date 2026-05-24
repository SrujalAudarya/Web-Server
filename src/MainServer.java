import java.net.ServerSocket;
import java.net.Socket;

import config.ConfigLoader;
import models.ServerConfig;
import server.ClientHandler;

public class MainServer {

    public static void main(String[] args) {

        try {

            ServerConfig config = ConfigLoader.load();

            ServerSocket serverSocket = new ServerSocket(config.getPort());

            System.out.println("Server started...");
            System.out.println("Open User Site: http://localhost:" + config.getPort());
            System.out.println("Open Server Panel: http://localhost:" + config.getPort() + "/server");

            while (true) {

                Socket socket = serverSocket.accept();

                new Thread(
                        new ClientHandler(socket, config)).start();
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}