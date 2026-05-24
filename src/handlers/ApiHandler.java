package handlers;

import java.io.*;
import java.util.Map;

public class ApiHandler {

    public static void handle(
            String method,
            String path,
            BufferedReader reader,
            InputStream input,
            Map<String, String> headers,
            OutputStream output) throws IOException {

        if (method.equals("GET")
                && path.equals("/api/status")) {

            String json = "{\"status\":\"running\",\"server\":\"MyJavaServer\"}";

            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length:" + json.getBytes().length +
                    "\r\n\r\n";

            output.write(header.getBytes());
            output.write(json.getBytes());
            output.flush();

            return;
        }
    }
}