package config;

import java.io.FileInputStream;
import java.util.Properties;

public class ConfigLoader {

    public static ServerConfig load() {

        ServerConfig config = new ServerConfig();

        try {

            Properties props = new Properties();

            FileInputStream fis
                    = new FileInputStream(
                            "config/server.properties"
                    );

            props.load(fis);

            config.setPort(
                    Integer.parseInt(
                            props.getProperty("server.port")
                    )
            );

            config.setWwwFolder(
                    props.getProperty("server.www")
            );

            config.setHtdocsFolder(
                    props.getProperty("server.htdocs")
            );

        } catch (Exception e) {
            e.printStackTrace();
        }

        return config;
    }
}
