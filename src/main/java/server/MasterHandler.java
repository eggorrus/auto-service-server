package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MasterHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        try {
            switch (method) {
                case "GET":
                    if (query != null && query.startsWith("id=")) {
                        long id = Long.parseLong(query.split("=")[1]);
                        handleGetById(exchange, id);
                    } else {
                        handleGetAll(exchange);
                    }
                    break;
                case "POST":
                    handlePost(exchange);
                    break;
                case "PUT":
                    if (path.matches(".*/\\d+$")) {
                        long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                        handlePut(exchange, id);
                    }
                    break;
                case "DELETE":
                    if (path.matches(".*/\\d+$")) {
                        long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                        handleDelete(exchange, id);
                    }
                    break;
                default:
                    sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException, SQLException {
        List<JSONObject> masters = new ArrayList<>();
        String sql = "SELECT * FROM master ORDER BY id";

        try (Connection conn = DBConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                masters.add(mapResultSetToJson(rs));
            }
        }

        sendResponse(exchange, 200, new JSONArray(masters).toString());
    }

    private void handleGetById(HttpExchange exchange, long id) throws IOException, SQLException {
        String sql = "SELECT * FROM master WHERE id = ?";

        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                sendResponse(exchange, 200, mapResultSetToJson(rs).toString());
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Master not found\"}");
            }
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException, SQLException {
        String body = readRequestBody(exchange);
        JSONObject json = new JSONObject(body);

        String sql = "INSERT INTO master (first_name, last_name, is_active, specialization) " +
                "VALUES (?, ?, ?, ?) RETURNING id";

        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, json.getString("firstName"));
            ps.setString(2, json.getString("lastName"));
            ps.setBoolean(3, json.getBoolean("active"));
            ps.setString(4, json.getString("specialization"));

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                json.put("id", rs.getLong("id"));
                sendResponse(exchange, 201, json.toString());
            }
        }
    }

    private void handlePut(HttpExchange exchange, long id) throws IOException, SQLException {
        String body = readRequestBody(exchange);
        JSONObject json = new JSONObject(body);

        String sql = "UPDATE master SET first_name = ?, last_name = ?, " +
                "is_active = ?, specialization = ? WHERE id = ?";

        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, json.getString("firstName"));
            ps.setString(2, json.getString("lastName"));
            ps.setBoolean(3, json.getBoolean("active"));
            ps.setString(4, json.getString("specialization"));
            ps.setLong(5, id);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                sendResponse(exchange, 200, "{\"status\":\"updated\"}");
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Master not found\"}");
            }
        }
    }

    private void handleDelete(HttpExchange exchange, long id) throws IOException, SQLException {
        String sql = "DELETE FROM master WHERE id = ?";

        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                sendResponse(exchange, 200, "{\"status\":\"deleted\"}");
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Master not found\"}");
            }
        }
    }

    private JSONObject mapResultSetToJson(ResultSet rs) throws SQLException {
        JSONObject obj = new JSONObject();
        obj.put("id", rs.getLong("id"));
        obj.put("firstName", rs.getString("first_name"));
        obj.put("lastName", rs.getString("last_name"));
        obj.put("active", rs.getBoolean("is_active"));
        obj.put("specialization", rs.getString("specialization"));
        return obj;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}