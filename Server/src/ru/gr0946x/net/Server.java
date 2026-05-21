package ru.gr0946x.net;

import ru.gr0946x.db.DatabaseConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

public class Server {

    private boolean isActive;
    private final int port;

    public Server(int port){
        this.port = port;
        // Initialize database
        try {
            DatabaseConfig.getInstance();
            System.out.println("База данных инициализирована");
        } catch (Exception e) {
            System.err.println("Ошибка инициализации БД: " + e.getMessage());
            e.printStackTrace();
        }
        
        isActive = true;
        new Thread(()->{
            try (var serverSocket = new ServerSocket(port)) {
                System.out.println("Сервер запущен на порту " + port);
                while (isActive) {
                    try{
                        var socket = serverSocket.accept();
                        System.out.println("Клиент подключен");
                        var connClient = new ConnectedClient(socket);
                        connClient.start();
                    } catch (Exception e) {
                        if (isActive) {
                            System.out.println("Ошибка подключения клиентов...");
                            System.out.println(e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Ошибка включения сервера");
            }
        }).start();
        
        startConsole();
    }

    private void startConsole() {
        new Thread(() -> {
            try (BufferedReader console = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String command;
                while (isActive) {
                    command = console.readLine();
                    if (command != null && command.equalsIgnoreCase("exit")) {
                        System.out.println("Завершение сервера...");
                        isActive = false;
                        DatabaseConfig.getInstance().shutdown();
                        System.exit(0);
                    }
                }
            } catch (IOException e) {
                System.err.println("Ошибка консоли: " + e.getMessage());
            }
        }).start();
    }
}
