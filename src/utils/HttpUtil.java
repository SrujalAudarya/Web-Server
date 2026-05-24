package utils;

import java.io.*;
import java.util.*;

public class HttpUtil {

    public static Map<String, String> readHeaders(
            BufferedReader reader) throws IOException {

        Map<String, String> headers = new HashMap<>();

        String line;

        while ((line = reader.readLine()) != null
                && !line.isEmpty()) {

            int index = line.indexOf(":");

            if (index != -1) {

                String key = line.substring(
                        0,
                        index).trim()
                        .toLowerCase();

                String value = line.substring(
                        index + 1).trim();

                headers.put(
                        key,
                        value);
            }
        }

        return headers;
    }

    public static Map<String, String> parseCookies(
            Map<String, String> headers) {

        Map<String, String> cookies = new HashMap<>();

        String cookieHeader = headers.get("cookie");

        if (cookieHeader == null) {
            return cookies;
        }

        String[] cookiePairs = cookieHeader.split(";");

        for (String pair : cookiePairs) {

            String[] parts = pair.trim()
                    .split("=", 2);

            if (parts.length == 2) {

                cookies.put(
                        parts[0].trim(),
                        parts[1].trim());
            }
        }

        return cookies;
    }

    public static Map<String, String> parseQueryParams(
            String path) {

        Map<String, String> queryParams = new HashMap<>();

        if (!path.contains("?")) {
            return queryParams;
        }

        String queryString = path.substring(
                path.indexOf("?") + 1);

        String[] pairs = queryString.split("&");

        for (String pair : pairs) {

            String[] parts = pair.split("=", 2);

            if (parts.length == 2) {

                queryParams.put(
                        parts[0],
                        parts[1]);
            }
        }

        return queryParams;
    }
}