
package org.example;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.sql.*;

@RestController
public class BuildingController {

    private boolean isAdmin() {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String escapeHtml(String s) {
        if (s == null) return "";

        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Створення прихованого поля CSRF для HTML-форм
    private String csrfInput(HttpServletRequest request) {
        CsrfToken token = (CsrfToken)
                request.getAttribute(CsrfToken.class.getName());

        if (token == null) {
            throw new IllegalStateException("CSRF-токен недоступний");
        }

        return "<input type='hidden' name='" +
                escapeHtml(token.getParameterName()) +
                "' value='" +
                escapeHtml(token.getToken()) +
                "'>";
    }

    private String redirect() {
        return "<meta http-equiv='refresh' content='0;url=/buildings'>";
    }

    // СПИСОК КОРПУСІВ
    @GetMapping(value = "/buildings", produces = "text/html;charset=UTF-8")
    public String list(HttpServletRequest request) {

        boolean admin = isAdmin();
        String csrf = admin ? csrfInput(request) : "";

        StringBuilder html = new StringBuilder("""
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Корпуси</title>
            </head>
            <body>
                <h1>РГР — Комендант</h1>
                <h2>Список корпусів</h2>

                <table border="1" cellpadding="8">
                    <tr>
                        <th>ID</th>
                        <th>Назва</th>
                        <th>Адреса</th>
                        <th>Поверхи</th>
                        <th>Дії</th>
                    </tr>
            """);

        String sql = "SELECT * FROM buildings ORDER BY id";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");

                html.append("<tr><td>").append(id)
                        .append("</td><td>")
                        .append(escapeHtml(rs.getString("name")))
                        .append("</td><td>")
                        .append(escapeHtml(rs.getString("address")))
                        .append("</td><td>")
                        .append(rs.getInt("floors"))
                        .append("</td><td>");

                if (admin) {
                    html.append("<a href='/buildings/edit?id=")
                            .append(id)
                            .append("'>Редагувати</a> | ")
                            .append("<form action='/buildings/delete' method='post' style='display:inline'>")
                            .append("<input type='hidden' name='id' value='")
                            .append(id)
                            .append("'>")
                            .append(csrf)
                            .append("<button type='submit' onclick='return confirm(\"Видалити корпус?\")'>")
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

        if (admin) {
            html.append("""
                <h2>Додати корпус</h2>
                <form action="/buildings/add" method="post">
                    Назва: <input name="name" required><br><br>
                    Адреса: <input name="address" required><br><br>
                    Поверхів:
                    <input name="floors" type="number"
                           min="1" required><br><br>
                """);

            html.append(csrf);

            html.append("""
                    <button type="submit">Додати</button>
                </form>
                """);
        }

        html.append("""
            <p><a href="/rooms">Аудиторії</a> |
            <a href="/furniture">Меблі</a></p>
            </body>
            </html>
            """);

        return html.toString();
    }

    // ДОДАВАННЯ КОРПУСУ
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

        return redirect();
    }

    // ВИДАЛЕННЯ КОРПУСУ
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

        return redirect();
    }

    // ФОРМА РЕДАГУВАННЯ
    @GetMapping(value = "/buildings/edit",
            produces = "text/html;charset=UTF-8")
    public String editForm(@RequestParam int id,
                           HttpServletRequest request) {

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
                    <head>
                        <meta charset="UTF-8">
                        <title>Редагування корпусу</title>
                    </head>
                    <body>
                        <h1>Редагування корпусу</h1>

                        <form action="/buildings/update" method="post">
                            <input type="hidden" name="id" value="%d">
                            Назва:
                            <input name="name" value="%s" required><br><br>
                            Адреса:
                            <input name="address" value="%s" required><br><br>
                            Поверхів:
                            <input name="floors" type="number"
                                   min="1" value="%d" required><br><br>
                            %s
                            <button type="submit">Зберегти</button>
                        </form>

                        <p><a href="/buildings">Назад</a></p>
                    </body>
                    </html>
                    """.formatted(
                        rs.getInt("id"),
                        escapeHtml(rs.getString("name")),
                        escapeHtml(rs.getString("address")),
                        rs.getInt("floors"),
                        csrfInput(request)
                );
            }

        } catch (Exception e) {
            return "Помилка: " + escapeHtml(e.getMessage());
        }
    }

    // ОНОВЛЕННЯ КОРПУСУ
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

        return redirect();
    }
}
