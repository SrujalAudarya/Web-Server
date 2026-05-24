package config;

import java.util.Properties;
import java.io.*;

import models.ServerConfig;

public class ConfigLoader {

        public static ServerConfig load()
                        throws Exception {

                Properties props = new Properties();

                props.load(
                                new FileInputStream("config/server.properties"));

                return new ServerConfig(
                                Integer.parseInt(
                                                props.getProperty("server.port")),
                                props.getProperty("server.www"),
                                props.getProperty("server.htdocs"));
        }
}