package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.TimeZone;

public class DatabaseConnection {

    private static final String URL =
            "jdbc:postgresql://localhost:5432/lab3_komendant";

    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";

    public static Connection getConnection() throws SQLException {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"));

        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}