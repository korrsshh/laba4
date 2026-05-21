package ru.gr0946x.ui;

import ru.gr0946x.net.Client;
import ru.gr0946x.net.MessageType;
import ru.gr0946x.net.ProtocolConstants;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

public class ConsoleUi implements Ui{

    private final List<Consumer<String>> listeners = new ArrayList<>();
    private Client client;
    private volatile boolean authenticated = false;

    public ConsoleUi(Client client) {
        this.client = client;
    }

    public void start(){
        var scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        new Thread(()-> {
            String currentChat = null;
            
            while (true) {
                if (!authenticated) {
                    System.out.print("1 - Вход, 2 - Регистрация: ");
                }
                String userData = scanner.nextLine();
                
                if (!authenticated) {
                    // Authentication phase
                    if (userData.startsWith("1") || userData.startsWith("2")) {
                        System.out.print("Логин: ");
                        String login = scanner.nextLine().trim();
                        System.out.print("Пароль: ");
                        String password = scanner.nextLine().trim();
                        
                        String authCmd = userData.startsWith("1") ? "1" : "2";
                        String authData = authCmd + ProtocolConstants.PARAM_SEPARATOR
                                + login + ProtocolConstants.FIELD_SEPARATOR + password;
                        
                        for (var listener : listeners) {
                            listener.accept(authData);
                        }
                        System.out.println("Ожидание ответа сервера...");
                    }
                } else {
                    if (userData.startsWith("/search ")) {
                        String searchText = userData.substring(8);
                        if (currentChat != null) {
                            client.searchMessages(currentChat, searchText);
                        } else {
                            System.out.println("Сначала выберите собеседника");
                        }
                    } else if (userData.startsWith("/chat ")) {
                        currentChat = userData.substring(6).trim();
                        client.setChatUser(currentChat);
                        client.markAsRead(currentChat);
                    } else if (userData.startsWith("/users")) {
                        client.requestOnlineUsers();
                    } else if (userData.startsWith("/broadcast")) {
                        client.getBroadcastHistory();
                        client.markBroadcastAsRead();
                    } else if (userData.startsWith("/private ")) {
                        String[] parts = userData.substring(9).split(" ", 2);
                        if (parts.length == 2) {
                            client.sendPrivateMessage(parts[0], parts[1]);
                        } else {
                            System.out.println("Использование: /private <ник> <сообщение>");
                        }
                    } else if (!userData.isEmpty()) {
                        if (currentChat != null) {
                            client.sendPrivateMessage(currentChat, userData);
                        } else {
                            client.sendBroadcastMessage(userData);
                        }
                    }
                }
            }
        }).start();
    }

    @Override
    public void showInfo(String data, MessageType type) {
        switch (type){
            case MESSAGE -> {
                var message = data.split("\\|\\|", 2);
                if (message.length == 2) {
                    System.out.println(message[0] + " написал: ");
                    System.out.println(message[1]);
                }
            }
            case PRIVATE_MESSAGE -> {
                var message = data.split("\\|\\|\\|", 3);
                if (message.length >= 2) {
                    String status = message.length > 2 ? " (" + message[2] + ")" : "";
                    System.out.println(message[0] + " (личное): " + message[1] + status);
                }
            }
            case PRIVATE_MESSAGE_ACK -> {
                var message = data.split("\\|\\|\\|", 3);
                if (message.length >= 2) {
                    String status = message.length > 2 ? " (" + message[2] + ")" : "";
                    System.out.println("Вы (личное) к " + message[0] + ": " + message[1] + status);
                }
            }
            case ERROR -> {
                System.err.println("✗ ОШИБКА: " + data);
            }
            case AUTH_SUCCESS -> {
                System.out.println("✓ " + data);
                authenticated = true;
            }
            case AUTH_FAILURE -> {
                System.err.println("✗ " + data);
                authenticated = false;
            }
            case REQUEST -> {
                System.out.println(">>> " + data);
            }
            default -> {
                if (data.startsWith("ONLINE_USERS")) {
                    String[] parts = data.split("\\:", 2);
                    if (parts.length == 2) {
                        System.out.println("Онлайн пользователи: " + parts[1]);
                    }
                } else if (data.startsWith("HISTORY_START")) {
                    System.out.println("\n--- История сообщений ---");
                } else if (data.startsWith("HISTORY_MESSAGE")) {
                    String[] parts = data.split("\\:", 2);
                    if (parts.length == 2) {
                        System.out.println(parts[1]);
                    }
                } else if (data.startsWith("HISTORY_END")) {
                    System.out.println("--- Конец истории ---\n");
                } else if (data.startsWith("BROADCAST_START")) {
                    System.out.println("\n=== ОБЩИЙ ЧАТ ===");
                } else if (data.startsWith("BROADCAST_MESSAGE")) {
                    String[] parts = data.split("\\:", 2);
                    if (parts.length == 2) {
                        System.out.println(parts[1]);
                    }
                } else if (data.startsWith("BROADCAST_END")) {
                    System.out.println("=== Конец общего чата ===\n");
                } else if (data.startsWith("SEARCH_START")) {
                    System.out.println("\n=== Результаты поиска ===");
                } else if (data.startsWith("SEARCH_RESULT")) {
                    String[] parts = data.split("\\:", 2);
                    if (parts.length == 2) {
                        System.out.println(parts[1]);
                    }
                } else if (data.startsWith("SEARCH_END")) {
                    System.out.println("=== Конец поиска ===\n");
                } else {
                    System.out.println(data);
                }
            }
        }
    }

    @Override
    public void addUserDataListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeUserDataListener(Consumer<String> listener) {
        listeners.remove(listener);
    }
}
