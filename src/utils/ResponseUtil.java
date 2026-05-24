package utils;

import java.io.*;
import java.nio.file.*;

public class ResponseUtil {

    public static void sendFile(
            OutputStream output,
            File file) throws IOException {

        byte[] fileBytes = Files.readAllBytes(file.toPath());

        String contentType = PathUtil.getContentType(
                file.getName());

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + fileBytes.length
                + "\r\n\r\n";

        output.write(header.getBytes());
        output.write(fileBytes);
        output.flush();
    }

    public static void sendHtml(
            OutputStream output,
            String html) throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: "
                + html.getBytes().length
                + "\r\n\r\n";

        output.write(header.getBytes());
        output.write(html.getBytes());
        output.flush();
    }

    public static void sendJson(
            OutputStream output,
            String json) throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: "
                + json.getBytes().length
                + "\r\n\r\n";

        output.write(header.getBytes());
        output.write(json.getBytes());
        output.flush();
    }

    public static void sendJsonWithCookie(
            OutputStream output,
            String json,
            String cookie) throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Set-Cookie: " + cookie + "\r\n"
                + "Content-Length: "
                + json.getBytes().length
                + "\r\n\r\n";

        output.write(header.getBytes());
        output.write(json.getBytes());
        output.flush();
    }

    public static void send404(
            OutputStream output) throws IOException {

        String html = """
                <html>
                <body>
                    <h1>404 - Page Not Found</h1>
                    <p>The requested file was not found.</p>
                </body>
                </html>
                """;

        String header = "HTTP/1.1 404 Not Found\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: "
                + html.getBytes().length
                + "\r\n\r\n";

        output.write(header.getBytes());
        output.write(html.getBytes());
        output.flush();
    }
}