package config;

import java.io.FileInputStream;
import java.util.Properties;
import models.ServerConfig;

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

            config.setHtdocsFolder(
                    props.getProperty("server.htdocs")
            );

            config.setMysqlUrl(
                    props.getProperty("mysql.url")
            );

            config.setMysqlUser(
                    props.getProperty("mysql.user")
            );

            config.setMysqlPassword(
                    props.getProperty("mysql.password")
            );

        } catch (Exception e) {
            e.printStackTrace();
        }

        return config;
    }
}
