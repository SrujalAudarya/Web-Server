package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import models.ServerConfig;

public class DatabaseManager {

    private static Connection connection;

    public static void connect(ServerConfig config) {

        try {

            Class.forName(
                    "com.mysql.cj.jdbc.Driver"
            );

            connection
                    = DriverManager.getConnection(
                            config.getMysqlUrl(),
                            config.getMysqlUser(),
                            config.getMysqlPassword()
                    );

            System.out.println(
                    "MySQL connected successfully"
            );

        } catch (Exception e) {

            System.out.println(
                    "MySQL connection failed"
            );

            e.printStackTrace();
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    public static boolean isConnected() {

        try {

            return connection != null
                    && !connection.isClosed();

        } catch (Exception e) {
            return false;
        }
    }

    // =========================
    // SELECT QUERY
    // =========================
    public static ResultSet executeQuery(
            String sql
    ) {

        try {

            Statement statement
                    = connection.createStatement();

            return statement.executeQuery(sql);

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }
    }

    // =========================
    // INSERT / UPDATE / DELETE
    // =========================
    public static int executeUpdate(
            String sql
    ) {

        try {

            Statement statement
                    = connection.createStatement();

            return statement.executeUpdate(sql);

        } catch (Exception e) {

            e.printStackTrace();

            return -1;
        }
    }

    // =========================
    // CLOSE CONNECTION
    // =========================
    public static void close() {

        try {

            if (connection != null) {
                connection.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
