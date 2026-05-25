
import config.ConfigLoader;
import config.ServerConfig;

import server.ClientHandler;

import java.net.ServerSocket;
import java.net.Socket;

public class MainServer {

    public static void main(String[] args) {

        try {

            ServerConfig config
                    = ConfigLoader.load();

            ServerSocket serverSocket
                    = new ServerSocket(
                            config.getPort()
                    );

            System.out.println(
                    "================================="
            );

            System.out.println(
                    "Server started on port: "
                    + config.getPort()
            );

            System.out.println(
                    "Open User Site: http://localhost:"
                    + config.getPort()
            );

            System.out.println(
                    "Open Server Panel: http://localhost:"
                    + config.getPort()
                    + "/server"
            );

            System.out.println(
                    "================================="
            );

            while (true) {

                Socket socket
                        = serverSocket.accept();

                ClientHandler handler
                        = new ClientHandler(
                                socket,
                                config
                        );

                new Thread(handler).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
