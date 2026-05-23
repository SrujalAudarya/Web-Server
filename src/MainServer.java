
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Type;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

public class MainServer {

    private static final Properties props = new Properties();
    private static final Map<String, String> sessions = new HashMap<>();

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

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
            InputStream input = socket.getInputStream();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input)
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

            if (!runMiddlewares(method, path, output)) {
                socket.close();
                return;
            }

            if (path.startsWith("/api/")) {
                handleApiRequest(method, path, reader, input, headers, output);
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
            InputStream input,
            Map<String, String> headers,
            OutputStream output
    ) throws IOException {

        if (method.equals("POST") && path.equals("/api/upload")) {
            byte[] fileBytes = readRequestBodyBytes(input, headers);

            File uploadFolder = new File("uploads");

            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            String fileName = "uploaded-file.bin";

            Files.write(Paths.get("uploads/" + fileName), fileBytes);

            sendJson(output, "{\"message\":\"File uploaded successfully\"}");
            return;
        }

        if (method.equals("POST") && path.equals("/api/upload-file")) {
            String contentType = headers.getOrDefault("content-type", "");

            if (!contentType.contains("multipart/form-data")) {
                sendJson(output, "{\"error\":\"Only multipart/form-data supported\"}");
                return;
            }

            String boundary = "--" + contentType.substring(contentType.indexOf("boundary=") + 9);

            byte[] bodyBytes = readRequestBodyBytes(input, headers);

            String bodyText = new String(bodyBytes, StandardCharsets.ISO_8859_1);

            String fileName = "uploaded-file.bin";

            int fileNameIndex = bodyText.indexOf("filename=\"");

            if (fileNameIndex != -1) {
                int start = fileNameIndex + 10;
                int end = bodyText.indexOf("\"", start);
                fileName = bodyText.substring(start, end);
            }

            int headerEnd = bodyText.indexOf("\r\n\r\n");

            if (headerEnd == -1) {
                sendJson(output, "{\"error\":\"Invalid multipart header\"}");
                return;
            }

            int fileStart = headerEnd + 4;

            byte[] boundaryBytes = ("\r\n" + boundary).getBytes(StandardCharsets.ISO_8859_1);

            int fileEnd = indexOf(bodyBytes, boundaryBytes, fileStart);

            if (fileEnd == -1) {
                sendJson(output, "{\"error\":\"Invalid multipart ending\"}");
                return;
            }

            byte[] fileBytes = new byte[fileEnd - fileStart];

            System.arraycopy(bodyBytes, fileStart, fileBytes, 0, fileBytes.length);

            File uploadFolder = new File("uploads");

            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            Files.write(Paths.get("uploads/" + fileName), fileBytes);

            sendJson(output,
                    "{\"message\":\"File uploaded successfully\",\"file\":\""
                    + fileName + "\"}"
            );

            return;
        }

        if (method.equals("GET") && path.equals("/api/status")) {
            sendJson(output, "{\"status\":\"running\",\"server\":\"MyJavaServer\"}");
            return;
        }

        if (method.equals("POST") && path.equals("/api/login")) {
            String sessionId = "SID" + System.currentTimeMillis();

            sessions.put(sessionId, "admin");

            sendJsonWithCookie(
                    output,
                    "{\"message\":\"Login successful\"}",
                    "SESSION_ID=" + sessionId + "; Path=/; HttpOnly"
            );

            return;
        }

        if (method.equals("GET") && path.equals("/api/profile")) {
            Map<String, String> cookies = parseCookies(headers);

            String sessionId = cookies.get("SESSION_ID");
            String username = sessions.get(sessionId);

            if (username == null) {
                sendJson(output, "{\"error\":\"Not logged in\"}");
            } else {
                sendJson(output, "{\"username\":\"" + username + "\"}");
            }

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

            String cleanPath = path;

            if (cleanPath.contains("?")) {
                cleanPath = cleanPath.substring(0, cleanPath.indexOf("?"));
            }

            boolean exactMatch = method.equals(routeMethod) && cleanPath.equals(routePath);
            boolean idMatch = method.equals(routeMethod) && cleanPath.startsWith(routePath + "/");

            if (exactMatch || idMatch) {
                File dataFile = new File("data/" + dataFileName);

                if (!dataFile.exists()) {
                    File dataFolder = new File("data");

                    if (!dataFolder.exists()) {
                        dataFolder.mkdirs();
                    }

                    Files.writeString(dataFile.toPath(), "[]");
                }

                if (method.equals("GET")) {
                    List<Map<String, Object>> list = readJsonArray(dataFile);

                    Map<String, String> queryParams = parseQueryParams(path);
                    String queryId = queryParams.get("id");

                    if (cleanPath.startsWith(routePath + "/") || queryId != null) {
                        String id;

                        if (queryId != null) {
                            id = queryId;
                        } else {
                            id = cleanPath.substring((routePath + "/").length());
                        }

                        for (Map<String, Object> item : list) {
                            String objectId = String.valueOf(item.get("id"));

                            if (objectId.endsWith(".0")) {
                                objectId = objectId.substring(0, objectId.length() - 2);
                            }

                            if (objectId.equals(id)) {
                                sendJson(output, gson.toJson(item));
                                routeReader.close();
                                return true;
                            }
                        }

                        sendJson(output, "{\"error\":\"Record not found\"}");
                        routeReader.close();
                        return true;
                    }

                    sendJson(output, gson.toJson(list));
                    routeReader.close();
                    return true;
                }

                if (method.equals("POST")) {
                    String postBody = readRequestBody(reader, headers).trim();

                    Map<String, Object> newObject = parseJsonBodyGson(postBody);

                    List<Map<String, Object>> list = readJsonArray(dataFile);
                    list.add(newObject);

                    writeJsonArray(dataFile, list);

                    writeLog("POST " + path + " -> " + postBody);

                    sendJson(output, "{\"message\":\"Data added successfully\"}");

                    routeReader.close();
                    return true;
                }

                if (method.equals("PUT")) {
                    String putBody = readRequestBody(reader, headers).trim();

                    Map<String, Object> newData = parseJsonBodyGson(putBody);

                    String updateId = String.valueOf(newData.get("id"));

                    if (updateId.endsWith(".0")) {
                        updateId = updateId.substring(0, updateId.length() - 2);
                    }

                    List<Map<String, Object>> list = readJsonArray(dataFile);

                    boolean updated = false;

                    for (int i = 0; i < list.size(); i++) {
                        Map<String, Object> oldData = list.get(i);

                        String oldId = String.valueOf(oldData.get("id"));

                        if (oldId.endsWith(".0")) {
                            oldId = oldId.substring(0, oldId.length() - 2);
                        }

                        if (oldId.equals(updateId)) {
                            list.set(i, newData);
                            updated = true;
                            break;
                        }
                    }

                    if (updated) {
                        writeJsonArray(dataFile, list);

                        sendJson(output,
                                "{\"message\":\"Data updated successfully\",\"id\":\""
                                + updateId + "\"}"
                        );
                    } else {
                        sendJson(output,
                                "{\"error\":\"Record not found\",\"id\":\""
                                + updateId + "\"}"
                        );
                    }

                    writeLog("PUT " + path + " -> " + putBody);

                    routeReader.close();
                    return true;
                }

                if (method.equals("DELETE")) {
                    String deleteBody = readRequestBody(reader, headers).trim();

                    Map<String, Object> deleteData = parseJsonBodyGson(deleteBody);

                    String deleteId = String.valueOf(deleteData.get("id"));

                    if (deleteId.endsWith(".0")) {
                        deleteId = deleteId.substring(0, deleteId.length() - 2);
                    }

                    List<Map<String, Object>> list = readJsonArray(dataFile);

                    boolean deleted = false;

                    for (int i = 0; i < list.size(); i++) {
                        Map<String, Object> oldData = list.get(i);

                        String oldId = String.valueOf(oldData.get("id"));

                        if (oldId.endsWith(".0")) {
                            oldId = oldId.substring(0, oldId.length() - 2);
                        }

                        if (oldId.equals(deleteId)) {
                            list.remove(i);
                            deleted = true;
                            break;
                        }
                    }

                    if (deleted) {
                        writeJsonArray(dataFile, list);

                        sendJson(output,
                                "{\"message\":\"Data deleted successfully\",\"id\":\""
                                + deleteId + "\"}"
                        );
                    } else {
                        sendJson(output,
                                "{\"error\":\"Record not found\",\"id\":\""
                                + deleteId + "\"}"
                        );
                    }

                    writeLog("DELETE " + path + " -> " + deleteBody);

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

    private static byte[] readRequestBodyBytes(
            InputStream input,
            Map<String, String> headers
    ) throws IOException {

        int contentLength = 0;

        if (headers.containsKey("content-length")) {
            contentLength = Integer.parseInt(headers.get("content-length"));
        }

        byte[] body = new byte[contentLength];

        int totalRead = 0;

        while (totalRead < contentLength) {
            int read = input.read(body, totalRead, contentLength - totalRead);

            if (read == -1) {
                break;
            }

            totalRead += read;
        }

        return body;
    }

    private static int indexOf(byte[] source, byte[] target, int fromIndex) {
        for (int i = fromIndex; i <= source.length - target.length; i++) {
            boolean found = true;

            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    found = false;
                    break;
                }
            }

            if (found) {
                return i;
            }
        }

        return -1;
    }

    private static Map<String, Object> parseJsonBodyGson(String body) {
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        return gson.fromJson(body, type);
    }

    private static List<Map<String, Object>> readJsonArray(File file) throws IOException {
        String json = Files.readString(file.toPath());

        if (json.trim().isEmpty()) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<List<Map<String, Object>>>() {
        }.getType();

        List<Map<String, Object>> list = gson.fromJson(json, type);

        if (list == null) {
            return new ArrayList<>();
        }

        return list;
    }

    private static void writeJsonArray(
            File file,
            List<Map<String, Object>> list
    ) throws IOException {

        Files.writeString(file.toPath(), gson.toJson(list));
    }

    private static Map<String, String> parseQueryParams(String path) {
        Map<String, String> queryParams = new HashMap<>();

        if (!path.contains("?")) {
            return queryParams;
        }

        String queryString = path.substring(path.indexOf("?") + 1);

        String[] pairs = queryString.split("&");

        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);

            if (parts.length == 2) {
                queryParams.put(parts[0], parts[1]);
            }
        }

        return queryParams;
    }

    private static Map<String, String> parseCookies(
            Map<String, String> headers
    ) {
        Map<String, String> cookies = new HashMap<>();

        String cookieHeader = headers.get("cookie");

        if (cookieHeader == null) {
            return cookies;
        }

        String[] cookiePairs = cookieHeader.split(";");

        for (String pair : cookiePairs) {
            String[] parts = pair.trim().split("=", 2);

            if (parts.length == 2) {
                cookies.put(parts[0].trim(), parts[1].trim());
            }
        }

        return cookies;
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

    private static void sendFile(
            OutputStream output,
            File file
    ) throws IOException {

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

    private static void sendHtml(
            OutputStream output,
            String html
    ) throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + html.getBytes().length + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(html.getBytes());
        output.flush();
    }

    private static void sendJson(
            OutputStream output,
            String json
    ) throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + json.getBytes().length + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(json.getBytes());
        output.flush();
    }

    private static void sendJsonWithCookie(
            OutputStream output,
            String json,
            String cookie
    ) throws IOException {

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Set-Cookie: " + cookie + "\r\n"
                + "Content-Length: " + json.getBytes().length + "\r\n"
                + "\r\n";

        output.write(header.getBytes());
        output.write(json.getBytes());
        output.flush();
    }

    private static void send404(
            OutputStream output
    ) throws IOException {

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

    private static boolean runMiddlewares(
            String method,
            String path,
            OutputStream output
    ) throws IOException {

        System.out.println("[Middleware] " + method + " " + path);

        if (path.startsWith("/api/admin")) {
            sendJson(output, "{\"error\":\"Unauthorized access\"}");
            return false;
        }

        return true;
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
