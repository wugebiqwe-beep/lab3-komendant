package org.example;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class Main {

    public static void main(String[] args) {

        String sql = "SELECT id, name, address, floors FROM buildings ORDER BY id";

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {

            System.out.println("=== СПИСОК КОРПУСІВ ===");

            while (result.next()) {
                int id = result.getInt("id");
                String name = result.getString("name");
                String address = result.getString("address");
                int floors = result.getInt("floors");

                System.out.println(
                        "ID: " + id +
                                " | Назва: " + name +
                                " | Адреса: " + address +
                                " | Поверхів: " + floors
                );
            }

        } catch (Exception e) {
            System.out.println("Помилка читання даних!");
            e.printStackTrace();
        }
    }
}