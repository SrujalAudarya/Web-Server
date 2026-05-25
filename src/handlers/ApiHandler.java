package handlers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import utils.HttpUtil;
import utils.RequestUtil;
import utils.ResponseUtil;

public class ApiHandler {

    private static final Map<String, String> sessions
            = new HashMap<>();

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
