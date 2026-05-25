package handlers;

import java.io.IOException;
import java.io.OutputStream;
import utils.ResponseUtil;

public class MiddlewareHandler {

    public static boolean runMiddlewares(
            String method,
            String path,
            OutputStream output
    ) {

        try {

            System.out.println("[Middleware] " + method + " " + path);

            if (path.startsWith("/api/admin")) {
                sendUnauthorized(output);
                return false;
            }

            return true;

        } catch (IOException e) {
            e.printStackTrace();

            try {
                sendInternalServerError(output, e.getMessage());
            } catch (IOException ignored) {
            }

            return false;
        }
    }

    public static void sendInternalServerError(
            OutputStream output,
            String message
    ) throws IOException {

        ResponseUtil.sendJson(
                output,
                "{\"status\":500,\"error\":\"Internal Server Error\",\"message\":\""
                + safe(message) + "\"}"
        );
    }

    public static void sendApiNotFound(
            OutputStream output
    ) throws IOException {

        ResponseUtil.sendJson(
                output,
                "{\"status\":404,\"error\":\"API route not found\"}"
        );
    }

    public static void sendMethodNotAllowed(
            OutputStream output
    ) throws IOException {

        ResponseUtil.sendJson(
                output,
                "{\"status\":405,\"error\":\"Method Not Allowed\"}"
        );
    }

    public static void sendBadRequest(
            OutputStream output,
            String message
    ) throws IOException {

        ResponseUtil.sendJson(
                output,
                "{\"status\":400,\"error\":\"Bad Request\",\"message\":\""
                + safe(message) + "\"}"
        );
    }

    public static void sendUnauthorized(
            OutputStream output
    ) throws IOException {

        ResponseUtil.sendJson(
                output,
                "{\"status\":401,\"error\":\"Unauthorized access\"}"
        );
    }

    private static String safe(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
