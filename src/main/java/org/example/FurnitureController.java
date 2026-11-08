
package org.example;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.sql.*;

@RestController
public class FurnitureController {

    // Перевірка ролі ADMIN
    private boolean isAdmin() {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // Захист тексту під час відображення в HTML
    private String escapeHtml(String s) {
        if (s == null) return "";

        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Створення прихованого поля CSRF-токена
    private String csrfInput(HttpServletRequest request) {
        CsrfToken token = (CsrfToken)
                request.getAttribute(CsrfToken.class.getName());

        if (token == null) {
            throw new IllegalStateException(
                    "CSRF-токен недоступний"
            );
        }

        return "<input type='hidden' name='" +
                escapeHtml(token.getParameterName()) +
                "' value='" +
                escapeHtml(token.getToken()) +
                "'>";
    }

    private String redirect() {
        return "<meta http-equiv='refresh' content='0;url=/furniture'>";
    }

    // СПИСОК МЕБЛІВ
    @GetMapping(value = "/furniture", produces = "text/html;charset=UTF-8")
    public String list(HttpServletRequest request) {

        boolean admin = isAdmin();
        String csrf = admin ? csrfInput(request) : "";

        StringBuilder html = new StringBuilder("""
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Меблі</title>
            </head>
            <body>
                <h1>РГР — Комендант</h1>
                <h2>Список меблів</h2>

                <table border="1" cellpadding="8">
                    <tr>
                        <th>ID</th>
                        <th>Корпус</th>
                        <th>Аудиторія</th>
                        <th>Назва</th>
                        <th>Кількість</th>
                        <th>Стан</th>
                        <th>Дії</th>
                    </tr>
            """);

        String sql = """
            SELECT f.id, f.name, f.quantity, f.condition,
                   r.room_number, b.name AS building_name
            FROM furniture f
            JOIN rooms r ON f.room_id = r.id
            JOIN buildings b ON r.building_id = b.id
            ORDER BY f.id
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");

                html.append("<tr><td>").append(id)
                        .append("</td><td>")
                        .append(escapeHtml(rs.getString("building_name")))
                        .append("</td><td>")
                        .append(escapeHtml(rs.getString("room_number")))
                        .append("</td><td>")
                        .append(escapeHtml(rs.getString("name")))
                        .append("</td><td>")
                        .append(rs.getInt("quantity"))
                        .append("</td><td>")
                        .append(escapeHtml(rs.getString("condition")))
                        .append("</td><td>");

                if (admin) {
                    html.append("<a href='/furniture/edit?id=")
                            .append(id)
                            .append("'>Редагувати</a> | ")
                            .append("<form action='/furniture/delete' method='post' style='display:inline'>")
                            .append("<input type='hidden' name='id' value='")
                            .append(id)
                            .append("'>")
                            .append(csrf)
                            .append("<button type='submit' onclick='return confirm(\"Видалити меблі?\")'>")
                            .append("Видалити</button>")
                            .append("</form>");
                } else {
                    html.append("Лише перегляд");
                }

                html.append("</td></tr>");
            }

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        html.append("</table>");

        // Форма додавання тільки для ADMIN
        if (admin) {
            html.append("""
                <h2>Додати меблі</h2>

                <form action="/furniture/add" method="post">
                    ID аудиторії:
                    <input name="roomId" type="number"
                           min="1" required><br><br>

                    Назва:
                    <input name="name" required><br><br>

                    Кількість:
                    <input name="quantity" type="number"
                           min="1" required><br><br>

                    Стан:
                    <input name="condition" required><br><br>
                """);

            html.append(csrf);

            html.append("""
                    <button type="submit">Додати</button>
                </form>
                """);
        }

        html.append("""
            <p><a href="/rooms">Повернутися до аудиторій</a></p>
            <p><a href="/buildings">Повернутися до корпусів</a></p>
            </body>
            </html>
            """);

        return html.toString();
    }

    // ДОДАВАННЯ МЕБЛІВ
    @PostMapping("/furniture/add")
    public String add(@RequestParam int roomId,
                      @RequestParam String name,
                      @RequestParam int quantity,
                      @RequestParam String condition) {

        String sql = """
            INSERT INTO furniture (room_id, name, quantity, condition)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, roomId);
            ps.setString(2, name);
            ps.setInt(3, quantity);
            ps.setString(4, condition);

            ps.executeUpdate();

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        return redirect();
    }

    // ФОРМА РЕДАГУВАННЯ
    @GetMapping(value = "/furniture/edit",
            produces = "text/html;charset=UTF-8")
    public String edit(@RequestParam int id,
                       HttpServletRequest request) {

        String sql = "SELECT * FROM furniture WHERE id = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return "Меблі не знайдено";
                }

                return """
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Редагування меблів</title>
                    </head>
                    <body>
                        <h1>Редагування меблів</h1>

                        <form action="/furniture/update" method="post">
                            <input type="hidden" name="id" value="%d">

                            ID аудиторії:
                            <input name="roomId" type="number"
                                   min="1" value="%d" required><br><br>

                            Назва:
                            <input name="name" value="%s"
                                   required><br><br>

                            Кількість:
                            <input name="quantity" type="number"
                                   min="1" value="%d" required><br><br>

                            Стан:
                            <input name="condition" value="%s"
                                   required><br><br>

                            %s

                            <button type="submit">Зберегти</button>
                        </form>

                        <p><a href="/furniture">Назад</a></p>
                    </body>
                    </html>
                    """.formatted(
                        rs.getInt("id"),
                        rs.getInt("room_id"),
                        escapeHtml(rs.getString("name")),
                        rs.getInt("quantity"),
                        escapeHtml(rs.getString("condition")),
                        csrfInput(request)
                );
            }

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }
    }

    // ОНОВЛЕННЯ МЕБЛІВ
    @PostMapping("/furniture/update")
    public String update(@RequestParam int id,
                         @RequestParam int roomId,
                         @RequestParam String name,
                         @RequestParam int quantity,
                         @RequestParam String condition) {

        String sql = """
            UPDATE furniture
            SET room_id = ?,
                name = ?,
                quantity = ?,
                condition = ?
            WHERE id = ?
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, roomId);
            ps.setString(2, name);
            ps.setInt(3, quantity);
            ps.setString(4, condition);
            ps.setInt(5, id);

            ps.executeUpdate();

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        return redirect();
    }

    // ВИДАЛЕННЯ МЕБЛІВ
    @PostMapping("/furniture/delete")
    public String delete(@RequestParam int id) {

        String sql = "DELETE FROM furniture WHERE id = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        return redirect();
    }
}
