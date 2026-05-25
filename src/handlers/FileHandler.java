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

        path = PathUtil.resolvePath(path);

        File file = new File(
                config.getHtdocsFolder() + path
        );

        if (file.exists() && !file.isDirectory()) {
            ResponseUtil.sendFile(output, file);
        } else {
            ResponseUtil.send404(output);
        }
    }
}
