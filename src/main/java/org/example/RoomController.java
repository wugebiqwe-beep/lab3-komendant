
package org.example;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.sql.*;

@RestController
public class RoomController {

    // Перевірка ролі ADMIN
    private boolean isAdmin() {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // Захист тексту для HTML
    private String escapeHtml(String s) {
        if (s == null) return "";

        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Приховане поле CSRF-токена
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
        return "<meta http-equiv='refresh' content='0;url=/rooms'>";
    }

    // СПИСОК АУДИТОРІЙ
    @GetMapping(value = "/rooms", produces = "text/html;charset=UTF-8")
    public String list(HttpServletRequest request) {

        boolean admin = isAdmin();
        String csrf = admin ? csrfInput(request) : "";

        StringBuilder html = new StringBuilder("""
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Аудиторії</title>
            </head>
            <body>
                <h1>РГР — Комендант</h1>
                <h2>Список аудиторій</h2>

                <table border="1" cellpadding="8">
                    <tr>
                        <th>ID</th>
                        <th>Корпус</th>
                        <th>Аудиторія</th>
                        <th>Поверх</th>
                        <th>Місткість</th>
                        <th>Дії</th>
                    </tr>
            """);

        String sql = """
            SELECT r.id, r.room_number, r.floor, r.capacity,
                   b.name AS building_name
            FROM rooms r
            JOIN buildings b ON r.building_id = b.id
            ORDER BY r.id
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
                        .append(rs.getInt("floor"))
                        .append("</td><td>")
                        .append(rs.getInt("capacity"))
                        .append("</td><td>");

                if (admin) {
                    html.append("<a href='/rooms/edit?id=")
                            .append(id)
                            .append("'>Редагувати</a> | ")
                            .append("<form action='/rooms/delete' method='post' style='display:inline'>")
                            .append("<input type='hidden' name='id' value='")
                            .append(id)
                            .append("'>")
                            .append(csrf)
                            .append("<button type='submit' onclick='return confirm(\"Видалити аудиторію?\")'>")
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
                <h2>Додати аудиторію</h2>

                <form action="/rooms/add" method="post">
                    ID корпусу:
                    <input name="buildingId" type="number"
                           min="1" required><br><br>

                    Номер аудиторії:
                    <input name="roomNumber" required><br><br>

                    Поверх:
                    <input name="floor" type="number"
                           min="1" required><br><br>

                    Місткість:
                    <input name="capacity" type="number"
                           min="0" required><br><br>
                """);

            html.append(csrf);

            html.append("""
                    <button type="submit">Додати</button>
                </form>
                """);
        }

        html.append("""
            <p><a href="/buildings">Повернутися до корпусів</a></p>
            </body>
            </html>
            """);

        return html.toString();
    }

    // ДОДАВАННЯ АУДИТОРІЇ
    @PostMapping("/rooms/add")
    public String add(@RequestParam int buildingId,
                      @RequestParam String roomNumber,
                      @RequestParam int floor,
                      @RequestParam int capacity) {

        String sql = """
            INSERT INTO rooms
            (building_id, room_number, floor, capacity)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, buildingId);
            ps.setString(2, roomNumber);
            ps.setInt(3, floor);
            ps.setInt(4, capacity);

            ps.executeUpdate();

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        return redirect();
    }

    // ФОРМА РЕДАГУВАННЯ
    @GetMapping(value = "/rooms/edit",
            produces = "text/html;charset=UTF-8")
    public String edit(@RequestParam int id,
                       HttpServletRequest request) {

        String sql = "SELECT * FROM rooms WHERE id = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return "Аудиторію не знайдено";
                }

                return """
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Редагування аудиторії</title>
                    </head>
                    <body>
                        <h1>Редагування аудиторії</h1>

                        <form action="/rooms/update" method="post">
                            <input type="hidden" name="id" value="%d">

                            ID корпусу:
                            <input name="buildingId" type="number"
                                   min="1" value="%d" required><br><br>

                            Номер аудиторії:
                            <input name="roomNumber" value="%s"
                                   required><br><br>

                            Поверх:
                            <input name="floor" type="number"
                                   min="1" value="%d" required><br><br>

                            Місткість:
                            <input name="capacity" type="number"
                                   min="0" value="%d" required><br><br>

                            %s

                            <button type="submit">Зберегти</button>
                        </form>

                        <p><a href="/rooms">Назад</a></p>
                    </body>
                    </html>
                    """.formatted(
                        rs.getInt("id"),
                        rs.getInt("building_id"),
                        escapeHtml(rs.getString("room_number")),
                        rs.getInt("floor"),
                        rs.getInt("capacity"),
                        csrfInput(request)
                );
            }

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }
    }

    // ОНОВЛЕННЯ АУДИТОРІЇ
    @PostMapping("/rooms/update")
    public String update(@RequestParam int id,
                         @RequestParam int buildingId,
                         @RequestParam String roomNumber,
                         @RequestParam int floor,
                         @RequestParam int capacity) {

        String sql = """
            UPDATE rooms
            SET building_id = ?,
                room_number = ?,
                floor = ?,
                capacity = ?
            WHERE id = ?
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, buildingId);
            ps.setString(2, roomNumber);
            ps.setInt(3, floor);
            ps.setInt(4, capacity);
            ps.setInt(5, id);

            ps.executeUpdate();

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }

        return redirect();
    }

    // ВИДАЛЕННЯ АУДИТОРІЇ
    @PostMapping("/rooms/delete")
    public String delete(@RequestParam int id) {

        String sql = "DELETE FROM rooms WHERE id = ?";

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
