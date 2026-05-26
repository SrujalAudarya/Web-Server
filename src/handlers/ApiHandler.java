package handlers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import utils.HttpUtil;
import utils.RequestUtil;
import utils.ResponseUtil;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import database.DatabaseManager;
import java.sql.*;

public class ApiHandler {

    private static final Map<String, String> sessions = new HashMap<>();

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static boolean handleApiRequest(
            String method,
            String path,
            BufferedReader reader,
            InputStream input,
            Map<String, String> headers,
            OutputStream output
    ) throws IOException {

        if (method.equals("GET") && path.equals("/api/status")) {
            ResponseUtil.sendJson(
                    output,
                    "{\"status\":\"running\",\"server\":\"MyJavaServer\"}"
            );
            return true;
        }

        if (method.equals("POST") && path.equals("/api/login")) {
            String sessionId = "SID" + System.currentTimeMillis();

            sessions.put(sessionId, "admin");

            ResponseUtil.sendJsonWithCookie(
                    output,
                    "{\"message\":\"Login successful\"}",
                    "SESSION_ID=" + sessionId + "; Path=/; HttpOnly"
            );

            return true;
        }

        if (method.equals("GET") && path.equals("/api/profile")) {
            Map<String, String> cookies
                    = HttpUtil.parseCookies(headers);

            String sessionId = cookies.get("SESSION_ID");
            String username = sessions.get(sessionId);

            if (username == null) {
                ResponseUtil.sendJson(
                        output,
                        "{\"error\":\"Not logged in\"}"
                );
            } else {
                ResponseUtil.sendJson(
                        output,
                        "{\"username\":\"" + username + "\"}"
                );
            }

            return true;
        }

        if (method.equals("POST") && path.equals("/api/upload-file")) {
            String contentType
                    = headers.getOrDefault("content-type", "");

            if (!contentType.contains("multipart/form-data")) {
                ResponseUtil.sendJson(
                        output,
                        "{\"error\":\"Only multipart/form-data supported\"}"
                );
                return true;
            }

            String boundary
                    = "--" + contentType.substring(
                            contentType.indexOf("boundary=") + 9
                    );

            byte[] bodyBytes
                    = RequestUtil.readRequestBodyBytes(input, headers);

            String bodyText
                    = new String(bodyBytes, StandardCharsets.ISO_8859_1);

            String fileName = "uploaded-file.bin";

            int fileNameIndex = bodyText.indexOf("filename=\"");

            if (fileNameIndex != -1) {
                int start = fileNameIndex + 10;
                int end = bodyText.indexOf("\"", start);
                fileName = bodyText.substring(start, end);
            }

            int headerEnd = bodyText.indexOf("\r\n\r\n");

            if (headerEnd == -1) {
                ResponseUtil.sendJson(
                        output,
                        "{\"error\":\"Invalid multipart header\"}"
                );
                return true;
            }

            int fileStart = headerEnd + 4;

            byte[] boundaryBytes
                    = ("\r\n" + boundary)
                            .getBytes(StandardCharsets.ISO_8859_1);

            int fileEnd
                    = indexOf(bodyBytes, boundaryBytes, fileStart);

            if (fileEnd == -1) {
                ResponseUtil.sendJson(
                        output,
                        "{\"error\":\"Invalid multipart ending\"}"
                );
                return true;
            }

            byte[] fileBytes = new byte[fileEnd - fileStart];

            System.arraycopy(
                    bodyBytes,
                    fileStart,
                    fileBytes,
                    0,
                    fileBytes.length
            );

            File uploadFolder = new File("uploads");

            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            Files.write(
                    Paths.get("uploads/" + fileName),
                    fileBytes
            );

            ResponseUtil.sendJson(
                    output,
                    "{\"message\":\"File uploaded successfully\",\"file\":\""
                    + fileName + "\"}"
            );

            return true;
        }

        if (method.equals("GET") && path.equals("/api/javamyadmin/databases")) {

            try {
                ResultSet rs = DatabaseManager.executeQuery("SHOW DATABASES");

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs != null && rs.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    json.append("\"")
                            .append(rs.getString(1))
                            .append("\"");

                    first = false;
                }

                json.append("]");

                ResponseUtil.sendJson(output, json.toString());

            } catch (SQLException e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

        if (method.equals("GET") && path.startsWith("/api/javamyadmin/tables")) {

            try {
                Map<String, String> params = HttpUtil.parseQueryParams(path);
                String db = params.get("db");

                if (db == null) {
                    MiddlewareHandler.sendBadRequest(output, "Database name required");
                    return true;
                }

                ResultSet rs = DatabaseManager.executeQuery(
                        "SHOW TABLES FROM " + db
                );

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (rs != null && rs.next()) {
                    if (!first) {
                        json.append(",");
                    }

                    json.append("\"")
                            .append(rs.getString(1))
                            .append("\"");

                    first = false;
                }

                json.append("]");

                ResponseUtil.sendJson(output, json.toString());

            } catch (SQLException e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

        if (method.equals("GET") && path.startsWith("/api/javamyadmin/records")) {

            try {
                Map<String, String> params = HttpUtil.parseQueryParams(path);

                String db = params.get("db");
                String table = params.get("table");

                if (db == null || table == null) {
                    MiddlewareHandler.sendBadRequest(
                            output,
                            "Database and table required"
                    );
                    return true;
                }

                ResultSet rs = DatabaseManager.executeQuery(
                        "SELECT * FROM " + db + "." + table + " LIMIT 100"
                );

                if (rs == null) {
                    MiddlewareHandler.sendBadRequest(output, "Query failed");
                    return true;
                }

                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();

                StringBuilder json = new StringBuilder("[");
                boolean firstRow = true;

                while (rs.next()) {
                    if (!firstRow) {
                        json.append(",");
                    }

                    json.append("{");

                    for (int i = 1; i <= columns; i++) {
                        if (i > 1) {
                            json.append(",");
                        }

                        json.append("\"")
                                .append(meta.getColumnName(i))
                                .append("\":\"")
                                .append(rs.getString(i))
                                .append("\"");
                    }

                    json.append("}");
                    firstRow = false;
                }

                json.append("]");

                ResponseUtil.sendJson(output, json.toString());

            } catch (SQLException e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

        // =========================
        // DYNAMIC CRUD ROUTES
        // =========================
        if (handleDynamicCrud(
                method,
                path,
                reader,
                headers,
                output
        )) {
            return true;
        }

        // =========================
        // API NOT FOUND
        // =========================
        MiddlewareHandler.sendApiNotFound(output);

        return true;
    }

    private static boolean handleDynamicCrud(
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

        BufferedReader routeReader
                = new BufferedReader(
                        new FileReader(routesFile)
                );

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

            String routeMethod = routeInfo[0];
            String routePath = routeInfo[1];

            if (!method.equals(routeMethod)
                    || !path.equals(routePath)) {
                continue;
            }

            File dataFile
                    = new File("data/" + dataFileName);

            if (!dataFile.exists()) {

                File dataFolder = new File("data");

                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }

                Files.writeString(
                        dataFile.toPath(),
                        "[]"
                );
            }

            Type type
                    = new TypeToken<
                        List<Map<String, Object>>>() {
                    }.getType();

            List<Map<String, Object>> dataList;

            String json
                    = Files.readString(dataFile.toPath());

            if (json.trim().isEmpty()) {
                json = "[]";
            }

            dataList = gson.fromJson(json, type);

            if (dataList == null) {
                dataList = new ArrayList<>();
            }

            // =====================
            // GET
            // =====================
            if (method.equals("GET")) {

                ResponseUtil.sendJson(
                        output,
                        gson.toJson(dataList)
                );

                routeReader.close();
                return true;
            }

            // =====================
            // POST
            // =====================
            if (method.equals("POST")) {

                String body
                        = RequestUtil.readRequestBody(
                                reader,
                                headers
                        );

                Map<String, Object> newData
                        = gson.fromJson(body, Map.class);

                dataList.add(newData);

                Files.writeString(
                        dataFile.toPath(),
                        gson.toJson(dataList)
                );

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Data added successfully\"}"
                );

                routeReader.close();
                return true;
            }

            // =====================
            // PUT
            // =====================
            if (method.equals("PUT")) {

                String body
                        = RequestUtil.readRequestBody(
                                reader,
                                headers
                        );

                Map<String, Object> newData
                        = gson.fromJson(body, Map.class);

                String updateId
                        = String.valueOf(newData.get("id"));

                boolean updated = false;

                for (int i = 0; i < dataList.size(); i++) {

                    Map<String, Object> oldData
                            = dataList.get(i);

                    String oldId
                            = String.valueOf(oldData.get("id"));

                    if (oldId.equals(updateId)
                            || oldId.equals(updateId + ".0")) {

                        dataList.set(i, newData);

                        updated = true;
                        break;
                    }
                }

                Files.writeString(
                        dataFile.toPath(),
                        gson.toJson(dataList)
                );

                if (updated) {

                    ResponseUtil.sendJson(
                            output,
                            "{\"message\":\"Updated successfully\"}"
                    );

                } else {

                    MiddlewareHandler.sendBadRequest(
                            output,
                            "Record not found"
                    );
                }

                routeReader.close();
                return true;
            }

            // =====================
            // DELETE
            // =====================
            if (method.equals("DELETE")) {

                String body
                        = RequestUtil.readRequestBody(
                                reader,
                                headers
                        );

                Map<String, Object> deleteData
                        = gson.fromJson(body, Map.class);

                String deleteId
                        = String.valueOf(deleteData.get("id"));

                boolean deleted = false;

                Iterator<Map<String, Object>> iterator
                        = dataList.iterator();

                while (iterator.hasNext()) {

                    Map<String, Object> item
                            = iterator.next();

                    String oldId
                            = String.valueOf(item.get("id"));

                    if (oldId.equals(deleteId)
                            || oldId.equals(deleteId + ".0")) {

                        iterator.remove();

                        deleted = true;
                        break;
                    }
                }

                Files.writeString(
                        dataFile.toPath(),
                        gson.toJson(dataList)
                );

                if (deleted) {

                    ResponseUtil.sendJson(
                            output,
                            "{\"message\":\"Deleted successfully\"}"
                    );

                } else {

                    MiddlewareHandler.sendBadRequest(
                            output,
                            "Record not found"
                    );
                }

                routeReader.close();
                return true;
            }
        }

        routeReader.close();

        return false;
    }

    private static int indexOf(
            byte[] source,
            byte[] target,
            int fromIndex
    ) {
        for (int i = fromIndex;
                i <= source.length - target.length;
                i++) {

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
}
