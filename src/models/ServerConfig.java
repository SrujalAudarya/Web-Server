package models;

public class ServerConfig {

    private int port;
    private String wwwFolder;
    private String htdocsFolder;

    public ServerConfig(
            int port,
            String wwwFolder,
            String htdocsFolder
    ){

        this.port=port;
        this.wwwFolder=wwwFolder;
        this.htdocsFolder=htdocsFolder;
    }

    public int getPort(){
        return port;
    }

    public String getWwwFolder(){
        return wwwFolder;
    }

    public String getHtdocsFolder(){
        return htdocsFolder;
    }
}