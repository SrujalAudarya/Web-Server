package utils;

public class PathUtil {

    public static String resolvePath(String path) {

        if (path.equals("/")) {
            return "/index.html";
        }

        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }

        if (path.endsWith("/")) {
            return path + "index.html";
        }

        if (!path.contains(".")) {
            return path + ".html";
        }

        return path;
    }

    public static String getContentType(
            String fileName) {

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

        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }

        if (fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")) {
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
