package server;

import java.sql.*;
import java.util.Properties;
import java.io.InputStream;

public class DBConfig {
    private static String url;
    private static String user;
    private static String password;

    static {
        Properties props = new Properties();
        try (InputStream is = DBConfig.class.getClassLoader()
                .getResourceAsStream("server-config.properties")) {

            if (is == null) {
                System.err.println("Config file not found in resources!");
                url = "jdbc:postgresql://localhost:5432/autoservice";
                user = "admin_user";
                password = "1234";
            } else {
                props.load(is);
                url = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/autoservice");
                user = props.getProperty("db.user", "postgres");
                password = props.getProperty("db.password", "");
            }
        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
            url = "jdbc:postgresql://localhost:5432/autoservice";
            user = "postgres";
            password = "12345";
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}