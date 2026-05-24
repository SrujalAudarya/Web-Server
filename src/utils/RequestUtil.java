package utils;

import java.io.*;
import java.util.*;

public class RequestUtil {

    public static String readRequestBody(
            BufferedReader reader,
            Map<String, String> headers)
            throws IOException {

        int contentLength = 0;

        if (headers.containsKey("content-length")) {
            contentLength = Integer.parseInt(
                    headers.get("content-length"));
        }

        char[] bodyChars = new char[contentLength];

        int totalRead = 0;

        while (totalRead < contentLength) {

            int read = reader.read(
                    bodyChars,
                    totalRead,
                    contentLength - totalRead);

            if (read == -1) {
                break;
            }

            totalRead += read;
        }

        return new String(
                bodyChars,
                0,
                totalRead);
    }

    public static byte[] readRequestBodyBytes(
            InputStream input,
            Map<String, String> headers)
            throws IOException {

        int contentLength = 0;

        if (headers.containsKey("content-length")) {
            contentLength = Integer.parseInt(
                    headers.get("content-length"));
        }

        byte[] body = new byte[contentLength];

        int totalRead = 0;

        while (totalRead < contentLength) {

            int read = input.read(
                    body,
                    totalRead,
                    contentLength - totalRead);

            if (read == -1) {
                break;
            }

            totalRead += read;
        }

        return body;
    }
}
