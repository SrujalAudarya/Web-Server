
import java.io.*;
import java.net.*;
import java.nio.file.*;

public class MainServer {

    private static final int PORT = 8080;

    // Developer/server UI files
    private static final String WWW_FOLDER = "www";

    // User-created website files
    private static final String HTDOCS_FOLDER = "htdocs";

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);

            System.out.println("Server started...");
            System.out.println("Open User Site: http://localhost:" + PORT);
            System.out.println("Open Server Panel: http://localhost:" + PORT + "/server");

            while (true) {
                Socket socket = serverSocket.accept();
                handleClient(socket);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            OutputStream output = socket.getOutputStream();

            String requestLine = reader.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            System.out.println("Request: " + requestLine);

            String[] requestParts = requestLine.split(" ");
            String path = requestParts[1];

            // API ROUTES
            if (path.equals("/api/status")) {
                sendJson(output, "{\"status\":\"running\", \"server\":\"MyJavaServer\"}");
                socket.close();
                return;
            }

            // Dynamic template page from htdocs
            if (path.equals("/user")) {
                String html = renderTemplate(
                        "user.html",
                        "User Page",
                        "Welcome to dynamic HTML rendering!"
                );

                sendHtml(output, html);
                socket.close();
                return;
            }

            File file;

            // Developer/server UI route
            if (path.startsWith("/server")) {
                String serverPath = path.replaceFirst("/server", "");

                if (serverPath.equals("") || serverPath.equals("/")) {
                    serverPath = "/index.html";
                }

                serverPath = resolvePath(serverPath);
                file = new File(WWW_FOLDER + serverPath);

            } else {
                // User website route
                path = resolvePath(path);
                file = new File(HTDOCS_FOLDER + path);
            }

            if (file.exists() && !file.isDirectory()) {
                sendFile(output, file);
            } else {
                send404(output);
            }

            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String resolvePath(String path) {
        if (path.equals("/")) {
            return "/index.html";
        }

        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }

        if (!path.contains(".") && !path.endsWith("/")) {
            return path + ".html";
        }

        return path;
    }

    private static String renderTemplate(
            String fileName,
            String title,
            String message
    ) throws IOException {

        String html = Files.readString(
                Paths.get(HTDOCS_FOLDER + "/" + fileName)
        );

        html = html.replace("{{title}}", title);
        html = html.replace("{{message}}", message);

        return html;
    }

    private static void sendFile(OutputStream output, File file) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        String contentType = getContentType(file.getName());

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + fileBytes.length + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(fileBytes);
        output.flush();
    }

    private static void sendHtml(OutputStream output, String html)
            throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + html.length() + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(html.getBytes());
        output.flush();
    }

    private static void sendJson(OutputStream output, String json) throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + json.length() + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(json.getBytes());
        output.flush();
    }

    private static void send404(OutputStream output) throws IOException {
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
                + "Content-Length: " + html.length() + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(html.getBytes());
        output.flush();
    }

    private static String getContentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html";
        }
        if (fileName.endsWith(".css")) {
            return "text/css";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "text/plain";
    }
}
