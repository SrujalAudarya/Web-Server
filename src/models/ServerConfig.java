package models;

public class ServerConfig {

    private int port;
    private String htdocsFolder;

    private String mysqlUrl;
    private String mysqlUser;
    private String mysqlPassword;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHtdocsFolder() {
        return htdocsFolder;
    }

    public void setHtdocsFolder(String htdocsFolder) {
        this.htdocsFolder = htdocsFolder;
    }

    public String getMysqlUrl() {
        return mysqlUrl;
    }

    public void setMysqlUrl(String mysqlUrl) {
        this.mysqlUrl = mysqlUrl;
    }

    public String getMysqlUser() {
        return mysqlUser;
    }

    public void setMysqlUser(String mysqlUser) {
        this.mysqlUser = mysqlUser;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public void setMysqlPassword(String mysqlPassword) {
        this.mysqlPassword = mysqlPassword;
    }
}
