package utils;

import java.util.HashMap;
import java.util.Map;

public class CookieUtil {

    public static Map<String, String> parseCookies(Map<String, String> headers) {
        Map<String, String> cookies = new HashMap<>();

        String cookieHeader = headers.get("cookie");

        if (cookieHeader == null) {
            return cookies;
        }

        String[] pairs = cookieHeader.split(";");

        for (String pair : pairs) {
            String[] parts = pair.trim().split("=", 2);

            if (parts.length == 2) {
                cookies.put(parts[0], parts[1]);
            }
        }

        return cookies;
    }
}