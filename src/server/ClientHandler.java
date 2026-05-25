package server;

import config.ServerConfig;

import handlers.ApiHandler;
import handlers.FileHandler;
import handlers.MiddlewareHandler;

import utils.HttpUtil;
import utils.LoggerUtil;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ServerConfig config;

    public ClientHandler(
            Socket socket,
            ServerConfig config
    ) {
        this.socket = socket;
        this.config = config;
    }

    @Override
    public void run() {

        try {

            InputStream input
                    = socket.getInputStream();

            BufferedReader reader
                    = new BufferedReader(
                            new InputStreamReader(input)
                    );

            OutputStream output
                    = socket.getOutputStream();

            String requestLine
                    = reader.readLine();

            if (requestLine == null
                    || requestLine.isEmpty()) {

                socket.close();
                return;
            }

            LoggerUtil.log(
                    "Request: " + requestLine
            );

            System.out.println(
                    "Request: " + requestLine
            );

            String[] requestParts
                    = requestLine.split(" ");

            String method = requestParts[0];
            String path = requestParts[1];

            Map<String, String> headers
                    = HttpUtil.readHeaders(reader);

            if (!MiddlewareHandler.runMiddlewares(
                    method,
                    path,
                    output
            )) {
                socket.close();
                return;
            }

            // =========================
            // API REQUESTS
            // =========================
            if (path.startsWith("/api/")) {

                boolean handled
                        = ApiHandler.handleApiRequest(
                                method,
                                path,
                                reader,
                                input,
                                headers,
                                output
                        );

                if (handled) {
                    socket.close();
                    return;
                }
            }

            // =========================
            // STATIC FILES
            // =========================
            FileHandler.handleFileRequest(
                    path,
                    config,
                    output
            );

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
