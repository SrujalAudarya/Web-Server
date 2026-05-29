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

        // =========================
        // FILE UPLOAD
        // =========================
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

        // =========================
        // FETCH DATABASES
        // =========================
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

        // =========================
        // FETCH TABLES IN DATABASE
        // =========================
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

        // =========================
        // FETCH TABLE RECORDS
        // =========================
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
        // SQL QUERY EXECUTION
        // =========================
        if (method.equals("POST")
                && path.equals("/api/javamyadmin/query")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String sql
                        = String.valueOf(data.get("sql"));

                ResultSet rs
                        = DatabaseManager.executeQuery(sql);

                ResultSetMetaData meta
                        = rs.getMetaData();

                int columns
                        = meta.getColumnCount();

                StringBuilder json
                        = new StringBuilder("[");

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

                ResponseUtil.sendJson(
                        output,
                        json.toString()
                );

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }
        // =========================
        // INSERT ROW
        // =========================
        if (method.equals("POST") && path.equals("/api/javamyadmin/insert-row")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data = gson.fromJson(body, Map.class);

                String db = String.valueOf(data.get("db"));
                String table = String.valueOf(data.get("table"));

                Object rowObject = data.get("row");

                if (db.equals("null") || table.equals("null") || rowObject == null) {
                    MiddlewareHandler.sendBadRequest(
                            output,
                            "Database, table and row required"
                    );
                    return true;
                }

                Map<String, Object> row
                        = (Map<String, Object>) rowObject;

                StringBuilder columns = new StringBuilder();
                StringBuilder values = new StringBuilder();

                int count = 0;

                for (String key : row.keySet()) {

                    if (count > 0) {
                        columns.append(",");
                        values.append(",");
                    }

                    columns.append("`").append(key).append("`");

                    values.append("'")
                            .append(
                                    String.valueOf(row.get(key))
                                            .replace("'", "\\'")
                            )
                            .append("'");

                    count++;
                }

                String sql
                        = "INSERT INTO `"
                        + db
                        + "`.`"
                        + table
                        + "` ("
                        + columns
                        + ") VALUES ("
                        + values
                        + ")";

                int rows = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Row inserted successfully\",\"affectedRows\":"
                        + rows
                        + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

        // =========================
        // UPDATE ROW
        // =========================
        if (method.equals("PUT") && path.equals("/api/javamyadmin/update-row")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);
                Map<String, Object> data = gson.fromJson(body, Map.class);

                String db = String.valueOf(data.get("db"));
                String table = String.valueOf(data.get("table"));
                String idColumn = String.valueOf(data.get("idColumn"));
                String idValue = String.valueOf(data.get("idValue"));
                Map<String, Object> row = (Map<String, Object>) data.get("row");

                if (db == null || table == null || idColumn == null || idValue == null || row == null) {
                    MiddlewareHandler.sendBadRequest(output, "Database, table, idColumn, idValue and row required");
                    return true;
                }

                StringBuilder setValues = new StringBuilder();

                int count = 0;

                for (String key : row.keySet()) {
                    if (count > 0) {
                        setValues.append(",");
                    }

                    setValues.append("`")
                            .append(key)
                            .append("`='")
                            .append(String.valueOf(row.get(key)).replace("'", "\\'"))
                            .append("'");

                    count++;
                }

                String sql = "UPDATE `" + db + "`.`" + table + "` SET "
                        + setValues
                        + " WHERE `" + idColumn + "`='"
                        + idValue.replace("'", "\\'")
                        + "'";

                int rows = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Row updated successfully\",\"affectedRows\":" + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

        // =========================
        // DELETE ROW
        // =========================
        if (method.equals("DELETE") && path.equals("/api/javamyadmin/delete-row")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);
                Map<String, Object> data = gson.fromJson(body, Map.class);

                String db = String.valueOf(data.get("db"));
                String table = String.valueOf(data.get("table"));
                String idColumn = String.valueOf(data.get("idColumn"));
                String idValue = String.valueOf(data.get("idValue"));

                if (db == null || table == null || idColumn == null || idValue == null) {
                    MiddlewareHandler.sendBadRequest(output, "Database, table, idColumn and idValue required");
                    return true;
                }

                String sql = "DELETE FROM `" + db + "`.`" + table + "` WHERE `"
                        + idColumn + "`='"
                        + idValue.replace("'", "\\'")
                        + "'";

                int rows = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Row deleted successfully\",\"affectedRows\":" + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

        // =========================
        // CREATE DATABASE
        // =========================
        if (method.equals("POST") && path.equals("/api/javamyadmin/create-database")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);
                Map<String, Object> data = gson.fromJson(body, Map.class);

                String db = String.valueOf(data.get("db"));

                if (db == null || db.equals("null") || db.trim().isEmpty()) {
                    MiddlewareHandler.sendBadRequest(output, "Database name required");
                    return true;
                }

                String sql = "CREATE DATABASE `" + db + "`";

                int rows = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Database created successfully\",\"affectedRows\":" + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

        // =========================
        // DROP DATABASE
        // =========================
        if (method.equals("DELETE") && path.equals("/api/javamyadmin/drop-database")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);
                Map<String, Object> data = gson.fromJson(body, Map.class);

                String db = String.valueOf(data.get("db"));

                if (db == null || db.equals("null") || db.trim().isEmpty()) {
                    MiddlewareHandler.sendBadRequest(output, "Database name required");
                    return true;
                }

                String sql = "DROP DATABASE `" + db + "`";

                int rows = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Database deleted successfully\",\"affectedRows\":" + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

        // =========================
        // CREATE TABLE
        // =========================
        if (method.equals("POST") && path.equals("/api/javamyadmin/create-table")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);
                Map<String, Object> data = gson.fromJson(body, Map.class);

                String db = String.valueOf(data.get("db"));
                String table = String.valueOf(data.get("table"));
                String columns = String.valueOf(data.get("columns"));

                if (db == null || table == null || columns == null
                        || db.equals("null") || table.equals("null") || columns.equals("null")) {

                    MiddlewareHandler.sendBadRequest(output, "Database, table and columns required");
                    return true;
                }

                String sql = "CREATE TABLE `" + db + "`.`" + table + "` (" + columns + ")";

                int rows = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Table created successfully\",\"affectedRows\":" + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

        // =========================
        // DROP TABLE
        // =========================
        if (method.equals("DELETE") && path.equals("/api/javamyadmin/drop-table")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);
                Map<String, Object> data = gson.fromJson(body, Map.class);

                String db = String.valueOf(data.get("db"));
                String table = String.valueOf(data.get("table"));

                if (db == null || table == null || db.equals("null") || table.equals("null")) {
                    MiddlewareHandler.sendBadRequest(output, "Database and table required");
                    return true;
                }

                String sql = "DROP TABLE `" + db + "`.`" + table + "`";

                int rows = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Table deleted successfully\",\"affectedRows\":" + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

        // =========================
        // DESCRIBE TABLE
        // =========================
        if (method.equals("GET") && path.startsWith("/api/javamyadmin/describe-table")) {

            try {
                Map<String, String> params = HttpUtil.parseQueryParams(path);

                String db = params.get("db");
                String table = params.get("table");

                if (db == null || table == null) {
                    MiddlewareHandler.sendBadRequest(output, "Database and table required");
                    return true;
                }

                ResultSet rs = DatabaseManager.executeQuery(
                        "DESCRIBE `" + db + "`.`" + table + "`"
                );

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

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

        // =====================================================
// EXPORT DATABASE (JSON)
// =====================================================
        if (method.equals("GET")
                && path.startsWith("/api/javamyadmin/export-database-json")) {

            try {

                Map<String, String> params
                        = HttpUtil.parseQueryParams(path);

                String db = params.get("db");

                if (db == null) {
                    MiddlewareHandler.sendBadRequest(
                            output,
                            "Database required"
                    );
                    return true;
                }

                ResultSet tablesRs
                        = DatabaseManager.executeQuery(
                                "SHOW TABLES FROM `" + db + "`"
                        );

                StringBuilder json = new StringBuilder("{");

                boolean firstTable = true;

                while (tablesRs.next()) {

                    String table = tablesRs.getString(1);

                    if (!firstTable) {
                        json.append(",");
                    }

                    json.append("\"").append(table).append("\":[");

                    ResultSet rs
                            = DatabaseManager.executeQuery(
                                    "SELECT * FROM `" + db + "`.`" + table + "`"
                            );

                    ResultSetMetaData meta = rs.getMetaData();
                    int columns = meta.getColumnCount();

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

                    firstTable = false;
                }

                json.append("}");

                ResponseUtil.sendJson(output, json.toString());

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// RENAME TABLE
// =====================================================
        if (method.equals("PUT")
                && path.equals("/api/javamyadmin/rename-table")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String db
                        = String.valueOf(data.get("db"));

                String oldTable
                        = String.valueOf(data.get("oldTable"));

                String newTable
                        = String.valueOf(data.get("newTable"));

                String sql
                        = "RENAME TABLE `" + db + "`.`"
                        + oldTable
                        + "` TO `"
                        + db
                        + "`.`"
                        + newTable
                        + "`";

                int rows
                        = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Table renamed successfully\",\"affectedRows\":"
                        + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// TRUNCATE TABLE
// =====================================================
        if (method.equals("DELETE")
                && path.equals("/api/javamyadmin/truncate-table")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String db
                        = String.valueOf(data.get("db"));

                String table
                        = String.valueOf(data.get("table"));

                String sql
                        = "TRUNCATE TABLE `" + db + "`.`" + table + "`";

                int rows
                        = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Table truncated successfully\",\"affectedRows\":"
                        + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// ADD COLUMN
// =====================================================
        if (method.equals("PUT")
                && path.equals("/api/javamyadmin/add-column")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String db
                        = String.valueOf(data.get("db"));

                String table
                        = String.valueOf(data.get("table"));

                String column
                        = String.valueOf(data.get("column"));

                String type
                        = String.valueOf(data.get("type"));

                String sql
                        = "ALTER TABLE `" + db + "`.`" + table
                        + "` ADD COLUMN `"
                        + column
                        + "` "
                        + type;

                int rows
                        = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Column added successfully\",\"affectedRows\":"
                        + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// DROP COLUMN
// =====================================================
        if (method.equals("DELETE")
                && path.equals("/api/javamyadmin/drop-column")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String db
                        = String.valueOf(data.get("db"));

                String table
                        = String.valueOf(data.get("table"));

                String column
                        = String.valueOf(data.get("column"));

                String sql
                        = "ALTER TABLE `" + db + "`.`" + table
                        + "` DROP COLUMN `"
                        + column
                        + "`";

                int rows
                        = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Column dropped successfully\",\"affectedRows\":"
                        + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// MODIFY COLUMN
// =====================================================
        if (method.equals("PUT")
                && path.equals("/api/javamyadmin/modify-column")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String db
                        = String.valueOf(data.get("db"));

                String table
                        = String.valueOf(data.get("table"));

                String column
                        = String.valueOf(data.get("column"));

                String type
                        = String.valueOf(data.get("type"));

                String sql
                        = "ALTER TABLE `" + db + "`.`" + table
                        + "` MODIFY COLUMN `"
                        + column
                        + "` "
                        + type;

                int rows
                        = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Column modified successfully\",\"affectedRows\":"
                        + rows + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// SEARCH ROWS
// =====================================================
        if (method.equals("GET")
                && path.startsWith("/api/javamyadmin/search")) {

            try {

                Map<String, String> params
                        = HttpUtil.parseQueryParams(path);

                String db = params.get("db");
                String table = params.get("table");
                String column = params.get("column");
                String keyword = params.get("keyword");

                String sql
                        = "SELECT * FROM `" + db + "`.`" + table
                        + "` WHERE `" + column
                        + "` LIKE '%" + keyword + "%'";

                ResultSet rs
                        = DatabaseManager.executeQuery(sql);

                ResultSetMetaData meta
                        = rs.getMetaData();

                int columns
                        = meta.getColumnCount();

                StringBuilder json
                        = new StringBuilder("[");

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

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// PAGINATION + SORTING + FILTERS
// =====================================================
        if (method.equals("GET")
                && path.startsWith("/api/javamyadmin/list-records")) {

            try {

                Map<String, String> params
                        = HttpUtil.parseQueryParams(path);

                String db = params.get("db");
                String table = params.get("table");

                String page
                        = params.getOrDefault("page", "1");

                String limit
                        = params.getOrDefault("limit", "10");

                String sort
                        = params.getOrDefault("sort", "id");

                String order
                        = params.getOrDefault("order", "ASC");

                String filterColumn
                        = params.get("filterColumn");

                String filterValue
                        = params.get("filterValue");

                int offset
                        = (Integer.parseInt(page) - 1)
                        * Integer.parseInt(limit);

                String sql
                        = "SELECT * FROM `" + db + "`.`" + table + "`";

                if (filterColumn != null
                        && filterValue != null) {

                    sql += " WHERE `" + filterColumn
                            + "` LIKE '%" + filterValue + "%'";
                }

                sql += " ORDER BY `" + sort + "` " + order;

                sql += " LIMIT " + limit
                        + " OFFSET " + offset;

                ResultSet rs
                        = DatabaseManager.executeQuery(sql);

                ResultSetMetaData meta
                        = rs.getMetaData();

                int columns
                        = meta.getColumnCount();

                StringBuilder json
                        = new StringBuilder("[");

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

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// EXPLAIN QUERY
// =====================================================
        if (method.equals("POST")
                && path.equals("/api/javamyadmin/explain-query")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String sql
                        = String.valueOf(data.get("sql"));

                ResultSet rs
                        = DatabaseManager.executeQuery(
                                "EXPLAIN " + sql
                        );

                ResultSetMetaData meta
                        = rs.getMetaData();

                int columns
                        = meta.getColumnCount();

                StringBuilder json
                        = new StringBuilder("[");

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

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

        // =====================================================
// IMPORT DATABASE (RUN SQL FILE CONTENT)
// =====================================================
        if (method.equals("POST")
                && path.equals("/api/javamyadmin/import-database")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String sql
                        = String.valueOf(data.get("sql"));

                if (sql == null
                        || sql.equals("null")
                        || sql.trim().isEmpty()) {

                    MiddlewareHandler.sendBadRequest(
                            output,
                            "SQL content required"
                    );

                    return true;
                }

                String[] queries
                        = sql.split(";");

                int executed = 0;

                for (String query : queries) {

                    query = query.trim();

                    if (!query.isEmpty()) {

                        DatabaseManager.executeUpdate(query);

                        executed++;
                    }
                }

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Database imported successfully\",\"executedQueries\":"
                        + executed + "}"
                );

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// ALTER TABLE
// =====================================================
        if (method.equals("PUT")
                && path.equals("/api/javamyadmin/alter-table")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String db
                        = String.valueOf(data.get("db"));

                String table
                        = String.valueOf(data.get("table"));

                String operation
                        = String.valueOf(data.get("operation"));

                if (db == null
                        || table == null
                        || operation == null) {

                    MiddlewareHandler.sendBadRequest(
                            output,
                            "db, table and operation required"
                    );

                    return true;
                }

                String sql
                        = "ALTER TABLE `" + db + "`.`"
                        + table + "` "
                        + operation;

                int rows
                        = DatabaseManager.executeUpdate(sql);

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Table altered successfully\",\"affectedRows\":"
                        + rows + "}"
                );

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// SAVE QUERY
// =====================================================
        if (method.equals("POST")
                && path.equals("/api/javamyadmin/save-query")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String name
                        = String.valueOf(data.get("name"));

                String sql
                        = String.valueOf(data.get("sql"));

                if (name == null
                        || sql == null
                        || name.equals("null")
                        || sql.equals("null")) {

                    MiddlewareHandler.sendBadRequest(
                            output,
                            "name and sql required"
                    );

                    return true;
                }

                File folder
                        = new File("saved-queries");

                if (!folder.exists()) {
                    folder.mkdirs();
                }

                Files.writeString(
                        Paths.get(
                                "saved-queries/" + name + ".sql"
                        ),
                        sql
                );

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"Query saved successfully\"}"
                );

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// GET SAVED QUERIES
// =====================================================
        if (method.equals("GET")
                && path.equals("/api/javamyadmin/saved-queries")) {

            try {

                File folder
                        = new File("saved-queries");

                if (!folder.exists()) {
                    folder.mkdirs();
                }

                File[] files
                        = folder.listFiles();

                StringBuilder json
                        = new StringBuilder("[");

                boolean first = true;

                if (files != null) {

                    for (File file : files) {

                        if (!first) {
                            json.append(",");
                        }

                        json.append("{");

                        json.append("\"name\":\"")
                                .append(file.getName())
                                .append("\"");

                        json.append("}");

                        first = false;
                    }
                }

                json.append("]");

                ResponseUtil.sendJson(
                        output,
                        json.toString()
                );

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// RUN SAVED QUERY
// =====================================================
        if (method.equals("GET")
                && path.startsWith("/api/javamyadmin/run-saved-query")) {

            try {

                Map<String, String> params
                        = HttpUtil.parseQueryParams(path);

                String name
                        = params.get("name");

                if (name == null) {

                    MiddlewareHandler.sendBadRequest(
                            output,
                            "Query name required"
                    );

                    return true;
                }

                File file
                        = new File(
                                "saved-queries/" + name + ".sql"
                        );

                if (!file.exists()) {

                    MiddlewareHandler.sendBadRequest(
                            output,
                            "Saved query not found"
                    );

                    return true;
                }

                String sql
                        = Files.readString(file.toPath());

                ResultSet rs
                        = DatabaseManager.executeQuery(sql);

                ResultSetMetaData meta
                        = rs.getMetaData();

                int columns
                        = meta.getColumnCount();

                StringBuilder json
                        = new StringBuilder("[");

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

                ResponseUtil.sendJson(
                        output,
                        json.toString()
                );

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

// =====================================================
// JOIN OPERATION
// =====================================================
        if (method.equals("POST")
                && path.equals("/api/javamyadmin/join")) {

            try {

                String body
                        = RequestUtil.readRequestBody(reader, headers);

                Map<String, Object> data
                        = gson.fromJson(body, Map.class);

                String db
                        = String.valueOf(data.get("db"));

                String table1
                        = String.valueOf(data.get("table1"));

                String table2
                        = String.valueOf(data.get("table2"));

                String joinType
                        = String.valueOf(data.get("joinType"));

                String on
                        = String.valueOf(data.get("on"));

                String columns
                        = String.valueOf(data.getOrDefault(
                                "columns",
                                "*"
                        ));

                String sql
                        = "SELECT " + columns
                        + " FROM `" + db + "`.`"
                        + table1 + "` "
                        + joinType + " JOIN `"
                        + db + "`.`"
                        + table2 + "` ON "
                        + on;

                ResultSet rs
                        = DatabaseManager.executeQuery(sql);

                ResultSetMetaData meta
                        = rs.getMetaData();

                int columnCount
                        = meta.getColumnCount();

                StringBuilder json
                        = new StringBuilder("[");

                boolean firstRow = true;

                while (rs.next()) {

                    if (!firstRow) {
                        json.append(",");
                    }

                    json.append("{");

                    for (int i = 1; i <= columnCount; i++) {

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

                ResponseUtil.sendJson(
                        output,
                        json.toString()
                );

            } catch (Exception e) {

                MiddlewareHandler.sendInternalServerError(
                        output,
                        e.getMessage()
                );
            }

            return true;
        }

        // =====================================================
// GET QUERY HISTORY
// =====================================================
        if (method.equals("GET")
                && path.equals("/api/javamyadmin/query-history")) {

            try {
                File file = new File("query-history/history.log");

                if (!file.exists()) {
                    ResponseUtil.sendJson(output, "[]");
                    return true;
                }

                List<String> lines = Files.readAllLines(file.toPath());

                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                for (String line : lines) {
                    if (!first) {
                        json.append(",");
                    }

                    json.append("\"")
                            .append(line.replace("\"", "\\\""))
                            .append("\"");

                    first = false;
                }

                json.append("]");

                ResponseUtil.sendJson(output, json.toString());

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

        // =====================================================
// EXPORT JSON - SINGLE TABLE
// =====================================================
        if (method.equals("GET")
                && path.startsWith("/api/javamyadmin/export-json")) {

            try {
                Map<String, String> params = HttpUtil.parseQueryParams(path);

                String db = params.get("db");
                String table = params.get("table");

                if (db == null || table == null) {
                    MiddlewareHandler.sendBadRequest(output, "Database and table required");
                    return true;
                }

                ResultSet rs = DatabaseManager.executeQuery(
                        "SELECT * FROM `" + db + "`.`" + table + "`"
                );

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

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

// =====================================================
// IMPORT SQL FILE CONTENT
// Body: { "sql": "CREATE TABLE ...; INSERT INTO ...;" }
// =====================================================
        if (method.equals("POST")
                && path.equals("/api/javamyadmin/import-sql")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);
                Map<String, Object> data = gson.fromJson(body, Map.class);

                String sqlContent = String.valueOf(data.get("sql"));

                if (sqlContent == null
                        || sqlContent.equals("null")
                        || sqlContent.trim().isEmpty()) {

                    MiddlewareHandler.sendBadRequest(output, "SQL content required");
                    return true;
                }

                String[] queries = sqlContent.split(";");

                int executed = 0;

                for (String query : queries) {
                    query = query.trim();

                    if (!query.isEmpty()) {
                        DatabaseManager.executeUpdate(query);
                        saveQueryHistory(query);
                        executed++;
                    }
                }

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"SQL imported successfully\",\"executedQueries\":"
                        + executed + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

// =====================================================
// IMPORT CSV
// Body:
// {
//   "db":"testdb",
//   "table":"users",
//   "columns":["name","email"],
//   "rows":[["Srujal","srujal@gmail.com"],["Admin","admin@gmail.com"]]
// }
// =====================================================
        if (method.equals("POST")
                && path.equals("/api/javamyadmin/import-csv")) {

            try {
                String body = RequestUtil.readRequestBody(reader, headers);
                Map<String, Object> data = gson.fromJson(body, Map.class);

                String db = String.valueOf(data.get("db"));
                String table = String.valueOf(data.get("table"));

                List<String> columns = (List<String>) data.get("columns");
                List<List<String>> rows = (List<List<String>>) data.get("rows");

                if (db == null || table == null || columns == null || rows == null) {
                    MiddlewareHandler.sendBadRequest(output, "db, table, columns and rows required");
                    return true;
                }

                int inserted = 0;

                for (List<String> row : rows) {
                    StringBuilder columnSql = new StringBuilder();
                    StringBuilder valueSql = new StringBuilder();

                    for (int i = 0; i < columns.size(); i++) {
                        if (i > 0) {
                            columnSql.append(",");
                            valueSql.append(",");
                        }

                        columnSql.append("`").append(columns.get(i)).append("`");

                        String value = "";

                        if (i < row.size()) {
                            value = String.valueOf(row.get(i));
                        }

                        valueSql.append("'")
                                .append(value.replace("'", "\\'"))
                                .append("'");
                    }

                    String sql = "INSERT INTO `" + db + "`.`" + table + "` ("
                            + columnSql
                            + ") VALUES ("
                            + valueSql
                            + ")";

                    int result = DatabaseManager.executeUpdate(sql);

                    if (result > 0) {
                        inserted += result;
                    }

                    saveQueryHistory(sql);
                }

                ResponseUtil.sendJson(
                        output,
                        "{\"message\":\"CSV imported successfully\",\"insertedRows\":"
                        + inserted + "}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
            }

            return true;
        }

// =====================================================
// EXPORT CSV
// =====================================================
        if (method.equals("GET")
                && path.startsWith("/api/javamyadmin/export-csv")) {

            try {
                Map<String, String> params = HttpUtil.parseQueryParams(path);

                String db = params.get("db");
                String table = params.get("table");

                if (db == null || table == null) {
                    MiddlewareHandler.sendBadRequest(output, "Database and table required");
                    return true;
                }

                ResultSet rs = DatabaseManager.executeQuery(
                        "SELECT * FROM `" + db + "`.`" + table + "`"
                );

                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();

                StringBuilder csv = new StringBuilder();

                for (int i = 1; i <= columns; i++) {
                    if (i > 1) {
                        csv.append(",");
                    }

                    csv.append(meta.getColumnName(i));
                }

                csv.append("\n");

                while (rs.next()) {
                    for (int i = 1; i <= columns; i++) {
                        if (i > 1) {
                            csv.append(",");
                        }

                        String value = rs.getString(i);

                        if (value == null) {
                            value = "";
                        }

                        csv.append("\"")
                                .append(value.replace("\"", "\"\""))
                                .append("\"");
                    }

                    csv.append("\n");
                }

                ResponseUtil.sendJson(
                        output,
                        "{\"csv\":\""
                        + csv.toString()
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "")
                        + "\"}"
                );

            } catch (Exception e) {
                MiddlewareHandler.sendInternalServerError(output, e.getMessage());
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

    // =====================================================
// QUERY HISTORY - SAVE HISTORY
// Call this after running any SQL query
// =====================================================
    private static void saveQueryHistory(String sql) {

        try {
            File folder = new File("query-history");

            if (!folder.exists()) {
                folder.mkdirs();
            }

            FileWriter writer = new FileWriter(
                    "query-history/history.log",
                    true
            );

            writer.write(
                    System.currentTimeMillis()
                    + " | "
                    + sql.replace("\n", " ")
                    + "\n"
            );

            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
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
