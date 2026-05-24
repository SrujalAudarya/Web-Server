package utils;

import java.io.*;

public class LoggerUtil {

    public static void log(String message) {

        try {

            File logsFolder = new File("logs");

            if (!logsFolder.exists()) {
                logsFolder.mkdirs();
            }

            FileWriter writer = new FileWriter(
                    "logs/access.log",
                    true);

            writer.write(message + "\n");
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}