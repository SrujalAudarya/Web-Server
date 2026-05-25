package handlers;

import config.ServerConfig;
import utils.PathUtil;
import utils.ResponseUtil;

import java.io.*;

public class FileHandler {

    public static void handleFileRequest(
            String path,
            ServerConfig config,
            OutputStream output
    ) throws IOException {

        File file;

        if (path.startsWith("/server")) {

            String serverPath = path.replaceFirst("/server", "");

            if (serverPath.equals("") || serverPath.equals("/")) {
                serverPath = "/index.html";
            }

            serverPath = PathUtil.resolvePath(serverPath);

            file = new File(config.getWwwFolder() + serverPath);

        } else {

            path = PathUtil.resolvePath(path);

            file = new File(config.getHtdocsFolder() + path);
        }

        if (file.exists() && !file.isDirectory()) {
            ResponseUtil.sendFile(output, file);
        } else {
            ResponseUtil.send404(output);
        }
    }
}
