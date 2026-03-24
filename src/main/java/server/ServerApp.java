package server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class ServerApp {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/clients", new ClientHandler());
        server.createContext("/api/masters", new MasterHandler());
        server.createContext("/api/prices", new PriceHandler());
        server.createContext("/api/work-orders", new WorkOrderHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Сервер запущен");
    }
}