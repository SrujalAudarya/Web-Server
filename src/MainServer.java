
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MainServer {

    private static final Properties props = new Properties();

    private static int PORT;
    private static String WWW_FOLDER;
    private static String HTDOCS_FOLDER;

    public static void main(String[] args) {
        try {
            loadConfig();

            ServerSocket serverSocket = new ServerSocket(PORT);

            System.out.println("Server started...");
            System.out.println("Open User Site: http://localhost:" + PORT);
            System.out.println("Open Server Panel: http://localhost:" + PORT + "/server");

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() {
        try {
            FileInputStream fis = new FileInputStream("config/server.properties");

            props.load(fis);

            PORT = Integer.parseInt(props.getProperty("server.port"));
            WWW_FOLDER = props.getProperty("server.www");
            HTDOCS_FOLDER = props.getProperty("server.htdocs");

        } catch (Exception e) {
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
            writeLog("Request: " + requestLine);

            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];
            String path = requestParts[1];

            Map<String, String> headers = readHeaders(reader);

            if (path.startsWith("/api/")) {
                handleApiRequest(method, path, reader, headers, output);
                socket.close();
                return;
            }

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

            if (path.startsWith("/server")) {
                String serverPath = path.replaceFirst("/server", "");

                if (serverPath.equals("") || serverPath.equals("/")) {
                    serverPath = "/index.html";
                }

                serverPath = resolvePath(serverPath);
                file = new File(WWW_FOLDER + serverPath);

            } else {
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

    private static Map<String, String> readHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int index = line.indexOf(":");

            if (index != -1) {
                String key = line.substring(0, index).trim().toLowerCase();
                String value = line.substring(index + 1).trim();

                headers.put(key, value);
            }
        }

        return headers;
    }

    private static void handleApiRequest(
            String method,
            String path,
            BufferedReader reader,
            Map<String, String> headers,
            OutputStream output
    ) throws IOException {

        if (method.equals("GET") && path.equals("/api/status")) {
            sendJson(output, "{\"status\":\"running\",\"server\":\"MyJavaServer\"}");
            return;
        }

        if (handleDynamicApiRoute(method, path, reader, headers, output)) {
            return;
        }

        sendJson(output, "{\"error\":\"API route not found\"}");
    }

    private static boolean handleDynamicApiRoute(
            String method,
            String path,
            BufferedReader reader,
            Map<String, String> headers,
            OutputStream output
    ) throws IOException {

        File routesFile = new File("routes/api.routes");

        if (!routesFile.exists()) {
            return false;
        }

        BufferedReader routeReader = new BufferedReader(new FileReader(routesFile));

        String line;

        while ((line = routeReader.readLine()) != null) {

            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("=");

            if (parts.length != 2) {
                continue;
            }

            String routePart = parts[0].trim();
            String dataFileName = parts[1].trim();

            String[] routeInfo = routePart.split(" ");

            if (routeInfo.length != 2) {
                continue;
            }

            String routeMethod = routeInfo[0].trim();
            String routePath = routeInfo[1].trim();

            if (method.equals(routeMethod) && path.equals(routePath)) {

                File dataFile = new File("data/" + dataFileName);

                if (!dataFile.exists()) {
                    sendJson(output, "{\"error\":\"Data file not found\"}");
                    routeReader.close();
                    return true;
                }

                if (method.equals("GET")) {
                    String json = Files.readString(dataFile.toPath());
                    sendJson(output, json);

                    routeReader.close();
                    return true;
                }

                if (method.equals("POST")) {
                    String body = readRequestBody(reader, headers);

                    Map<String, String> data = parseBody(body, headers);

                    writeLog("POST " + path + " -> " + data.toString());

                    sendJson(output,
                            "{\"message\":\"POST request successful\",\"data\":\""
                            + escapeJson(data.toString()) + "\"}"
                    );

                    routeReader.close();
                    return true;
                }

                if (method.equals("PUT")) {
                    String body = readRequestBody(reader, headers);

                    Map<String, String> data = parseBody(body, headers);

                    writeLog("PUT " + path + " -> " + data.toString());

                    sendJson(output,
                            "{\"message\":\"PUT request successful\",\"data\":\""
                            + escapeJson(data.toString()) + "\"}"
                    );

                    routeReader.close();
                    return true;
                }

                if (method.equals("DELETE")) {
                    String body = readRequestBody(reader, headers);

                    Map<String, String> data = parseBody(body, headers);

                    writeLog("DELETE " + path + " -> " + data.toString());

                    sendJson(output,
                            "{\"message\":\"DELETE request successful\",\"data\":\""
                            + escapeJson(data.toString()) + "\"}"
                    );

                    routeReader.close();
                    return true;
                }
            }
        }

        routeReader.close();
        return false;
    }

    private static String readRequestBody(
            BufferedReader reader,
            Map<String, String> headers
    ) throws IOException {

        int contentLength = 0;

        if (headers.containsKey("content-length")) {
            contentLength = Integer.parseInt(headers.get("content-length"));
        }

        char[] bodyChars = new char[contentLength];

        int totalRead = 0;

        while (totalRead < contentLength) {
            int read = reader.read(bodyChars, totalRead, contentLength - totalRead);

            if (read == -1) {
                break;
            }

            totalRead += read;
        }

        return new String(bodyChars, 0, totalRead);
    }

    private static Map<String, String> parseBody(
            String body,
            Map<String, String> headers
    ) throws UnsupportedEncodingException {

        String contentType = headers.getOrDefault("content-type", "");

        if (contentType.contains("application/json")) {
            return parseJsonBody(body);
        }

        if (contentType.contains("application/x-www-form-urlencoded")) {
            return parseFormData(body);
        }

        return parseFormData(body);
    }

    private static Map<String, String> parseFormData(String body)
            throws UnsupportedEncodingException {

        Map<String, String> data = new HashMap<>();

        String[] pairs = body.split("&");

        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);

            if (parts.length == 2) {
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);

                data.put(key, value);
            }
        }

        return data;
    }

    private static Map<String, String> parseJsonBody(String body) {
        Map<String, String> data = new HashMap<>();

        body = body.trim();

        if (body.startsWith("{")) {
            body = body.substring(1);
        }

        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }

        String[] pairs = body.split(",");

        for (String pair : pairs) {
            String[] parts = pair.split(":", 2);

            if (parts.length == 2) {
                String key = parts[0].trim().replace("\"", "");
                String value = parts[1].trim().replace("\"", "");

                data.put(key, value);
            }
        }

        return data;
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
                + "Content-Length: " + html.getBytes().length + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(html.getBytes());
        output.flush();
    }

    private static void sendJson(OutputStream output, String json) throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + json.getBytes().length + "\r\n"
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
                + "Content-Length: " + html.getBytes().length + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(html.getBytes());
        output.flush();
    }

    private static void writeLog(String message) {
        try {
            File logsFolder = new File("logs");

            if (!logsFolder.exists()) {
                logsFolder.mkdirs();
            }

            FileWriter writer = new FileWriter("logs/access.log", true);
            writer.write(message + "\n");
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
        if (fileName.endsWith(".json")) {
            return "application/json";
        }
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }

        return "text/plain";
    }
}
