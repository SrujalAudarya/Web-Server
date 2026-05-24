package server;

import java.io.*;
import java.net.Socket;
import java.util.Map;

import models.ServerConfig;
import utils.HttpUtil;
import utils.LoggerUtil;
import utils.PathUtil;
import utils.ResponseUtil;
import handlers.ApiHandler;

public class ClientHandler implements Runnable {

    private Socket socket;
    private ServerConfig config;

    public ClientHandler(Socket socket, ServerConfig config) {
        this.socket = socket;
        this.config = config;
    }

    @Override
    public void run() {
        handleClient(socket);
    }

    private void handleClient(Socket socket) {

        try {

            InputStream input = socket.getInputStream();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input));

            OutputStream output = socket.getOutputStream();

            String requestLine = reader.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            System.out.println("Request: " + requestLine);

            LoggerUtil.log("Request: " + requestLine);

            String[] requestParts = requestLine.split(" ");

            String method = requestParts[0];
            String path = requestParts[1];

            Map<String, String> headers = HttpUtil.readHeaders(reader);

            if (path.startsWith("/api/")) {

                ApiHandler.handle(
                        method,
                        path,
                        reader,
                        input,
                        headers,
                        output);

                socket.close();
                return;
            }

            File file;

            if (path.startsWith("/server")) {

                String serverPath = path.replaceFirst("/server", "");

                if (serverPath.equals("")
                        || serverPath.equals("/")) {

                    serverPath = "/index.html";
                }

                serverPath = PathUtil.resolvePath(serverPath);

                file = new File(
                        config.getWwwFolder() + serverPath);

            } else {

                path = PathUtil.resolvePath(path);

                file = new File(
                        config.getHtdocsFolder() + path);
            }

            if (file.exists() && !file.isDirectory()) {

                ResponseUtil.sendFile(output, file);

            } else {

                ResponseUtil.send404(output);
            }

            socket.close();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}