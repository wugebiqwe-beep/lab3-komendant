package org.example;

import org.springframework.web.bind.annotation.*;
import java.sql.*;

@RestController
public class BuildingController {

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @GetMapping(value = "/buildings", produces = "text/html;charset=UTF-8")
    public String list() {
        StringBuilder html = new StringBuilder("""
            <html><head><meta charset="UTF-8"><title>Корпуси</title></head>
            <body>
            <h1>Лабораторна №3 — Комендант</h1>
            <h2>Список корпусів</h2>
            <table border="1" cellpadding="8">
            <tr><th>ID</th><th>Назва</th><th>Адреса</th>
            <th>Поверхи</th><th>Дії</th></tr>
            """);

        try (Connection con = DatabaseConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM buildings ORDER BY id")) {

            while (rs.next()) {
                int id = rs.getInt("id");
                html.append("<tr><td>").append(id)
                        .append("</td><td>").append(escapeHtml(rs.getString("name")))
                        .append("</td><td>").append(escapeHtml(rs.getString("address")))
                        .append("</td><td>").append(rs.getInt("floors"))
                        .append("</td><td>")
                        .append("<a href='/buildings/edit?id=").append(id)
                        .append("'>Редагувати</a> | ")
                        .append("<form action='/buildings/delete' method='post' style='display:inline'>")
                        .append("<input type='hidden' name='id' value='").append(id).append("'>")
                        .append("<button type='submit' onclick='return confirm(\"Видалити корпус?\")'>Видалити</button>")
                        .append("</form>")
                        .append("</td></tr>");
            }
        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        html.append("""
            </table>
            <h2>Додати корпус</h2>
            <form action="/buildings/add" method="post">
                Назва: <input name="name" required><br><br>
                Адреса: <input name="address" required><br><br>
                Поверхів: <input name="floors" type="number" min="1" required>
                <br><br>
                <button type="submit">Додати</button>
            </form>
            </body></html>
            """);

        return html.toString();
    }

    @PostMapping("/buildings/add")
    public String add(@RequestParam String name,
                      @RequestParam String address,
                      @RequestParam int floors) {

        String sql = """
        INSERT INTO buildings (name, address, floors)
        VALUES (?, ?, ?)
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, address);
            ps.setInt(3, floors);

            ps.executeUpdate();

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        return "<meta http-equiv='refresh' content='0;url=/buildings'>";
    }

    @PostMapping("/buildings/delete")
    public String delete(@RequestParam int id) {

        String sql = "DELETE FROM buildings WHERE id = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        return "<meta http-equiv='refresh' content='0;url=/buildings'>";
    }

    @GetMapping(value = "/buildings/edit", produces = "text/html;charset=UTF-8")
    public String editForm(@RequestParam int id) {

        String sql = "SELECT * FROM buildings WHERE id = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                return "Корпус не знайдено";
            }

            return """
            <html>
            <head><meta charset="UTF-8"></head>
            <body>
            <h1>Редагування корпусу</h1>
            <form action="/buildings/update" method="post">
                <input type="hidden" name="id" value="%d">
                Назва: <input name="name" value="%s" required><br><br>
                Адреса: <input name="address" value="%s" required><br><br>
                Поверхів: <input name="floors" type="number"
                       min="1" value="%d" required><br><br>
                <button type="submit">Зберегти</button>
            </form>
            <a href="/buildings">Назад</a>
            </body>
            </html>
            """.formatted(
                    rs.getInt("id"),
                    escapeHtml(rs.getString("name")),
                    escapeHtml(rs.getString("address")),
                    rs.getInt("floors")
            ); }

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }
    }

    @PostMapping("/buildings/update")
    public String update(@RequestParam int id,
                         @RequestParam String name,
                         @RequestParam String address,
                         @RequestParam int floors) {

        String sql = """
        UPDATE buildings
        SET name = ?, address = ?, floors = ?
        WHERE id = ?
        """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, address);
            ps.setInt(3, floors);
            ps.setInt(4, id);

            ps.executeUpdate();

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        return "<meta http-equiv='refresh' content='0;url=/buildings'>";
    }

}