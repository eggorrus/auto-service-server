package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WorkOrderHandler implements HttpHandler {
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
        List<JSONObject> orders = new ArrayList<>();
        String sql = """
            SELECT wo.*, 
                   c.first_name as client_first, c.last_name as client_last,
                   m.first_name as master_first, m.last_name as master_last,
                   p.name_of_service
            FROM work_order wo
            LEFT JOIN client c ON wo.client_id = c.id
            LEFT JOIN master m ON wo.master_id = m.id
            LEFT JOIN price p ON wo.price_id = p.id
            ORDER BY wo.created_at DESC
            """;

        try (Connection conn = DBConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                orders.add(mapResultSetToJson(rs));
            }
        }

        sendResponse(exchange, 200, new JSONArray(orders).toString());
    }

    private void handleGetById(HttpExchange exchange, long id) throws IOException, SQLException {
        String sql = """
            SELECT wo.*, 
                   c.first_name as client_first, c.last_name as client_last,
                   m.first_name as master_first, m.last_name as master_last,
                   p.name_of_service
            FROM work_order wo
            LEFT JOIN client c ON wo.client_id = c.id
            LEFT JOIN master m ON wo.master_id = m.id
            LEFT JOIN price p ON wo.price_id = p.id
            WHERE wo.id = ?
            """;

        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                sendResponse(exchange, 200, mapResultSetToJson(rs).toString());
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Work order not found\"}");
            }
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException, SQLException {
        String body = readRequestBody(exchange);
        JSONObject json = new JSONObject(body);

        String sql = """
            INSERT INTO work_order 
            (client_id, master_id, price_id, final_price, breakdown, current_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::breakdowns_enum, ?::status_enum, now(), now())
            RETURNING id
            """;

        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, json.getLong("clientId"));
            ps.setLong(2, json.getLong("masterId"));
            ps.setLong(3, json.getLong("priceId"));
            ps.setFloat(4, json.getFloat("finalPrice"));
            ps.setString(5, json.getString("breakdown"));
            ps.setString(6, json.getString("status"));

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

        String sql = """
            UPDATE work_order 
            SET client_id = ?, master_id = ?, price_id = ?, 
                final_price = ?, breakdown = ?::breakdowns_enum, 
                current_status = ?::status_enum, updated_at = now()
            WHERE id = ?
            """;

        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, json.getLong("clientId"));
            ps.setLong(2, json.getLong("masterId"));
            ps.setLong(3, json.getLong("priceId"));
            ps.setFloat(4, json.getFloat("finalPrice"));
            ps.setString(5, json.getString("breakdown"));
            ps.setString(6, json.getString("status"));
            ps.setLong(7, id);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                sendResponse(exchange, 200, "{\"status\":\"updated\"}");
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Work order not found\"}");
            }
        }
    }

    private void handleDelete(HttpExchange exchange, long id) throws IOException, SQLException {
        String sql = "DELETE FROM work_order WHERE id = ?";

        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                sendResponse(exchange, 200, "{\"status\":\"deleted\"}");
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Work order not found\"}");
            }
        }
    }

    private JSONObject mapResultSetToJson(ResultSet rs) throws SQLException {
        JSONObject obj = new JSONObject();
        obj.put("id", rs.getLong("id"));

        if (rs.getLong("client_id") > 0) {
            JSONObject client = new JSONObject();
            client.put("id", rs.getLong("client_id"));
            client.put("firstName", rs.getString("client_first"));
            client.put("lastName", rs.getString("client_last"));
            obj.put("client", client);
        }

        if (rs.getLong("master_id") > 0) {
            JSONObject master = new JSONObject();
            master.put("id", rs.getLong("master_id"));
            master.put("firstName", rs.getString("master_first"));
            master.put("lastName", rs.getString("master_last"));
            obj.put("master", master);
        }

        if (rs.getLong("price_id") > 0) {
            JSONObject price = new JSONObject();
            price.put("id", rs.getLong("price_id"));
            price.put("nameOfService", rs.getString("name_of_service"));
            obj.put("price", price);
        }

        obj.put("finalPrice", rs.getFloat("final_price"));
        obj.put("breakdown", rs.getString("breakdown"));
        obj.put("status", rs.getString("current_status"));
        obj.put("createdAt", rs.getTimestamp("created_at").toString());
        obj.put("updatedAt", rs.getTimestamp("updated_at").toString());

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