package config;

public class ServerConfig {

    private int port;

    private String wwwFolder;

    private String htdocsFolder;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getWwwFolder() {
        return wwwFolder;
    }

    public void setWwwFolder(String wwwFolder) {
        this.wwwFolder = wwwFolder;
    }

    public String getHtdocsFolder() {
        return htdocsFolder;
    }

    public void setHtdocsFolder(String htdocsFolder) {
        this.htdocsFolder = htdocsFolder;
    }
}
