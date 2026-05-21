package ru.gr0946x.net;

import ru.gr0946x.entity.Message;
import ru.gr0946x.entity.User;
import ru.gr0946x.service.MessageService;
import ru.gr0946x.service.UserService;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

public class ConnectedClient {
    private final Communicator communicator;
    private final static List<ConnectedClient> clients = new ArrayList<>();
    private final static Map<Long, ConnectedClient> userSessions = new HashMap<>();
    private User currentUser = null;
    private Long selectedChatUserId = null;
    private final UserService userService;
    private final MessageService messageService;
    private boolean authenticated = false;

    public ConnectedClient(Socket socket) throws IOException {
        communicator = new Communicator(socket);
        communicator.addDataListener(this::parseData);
        try {
            userService = new UserService();
            messageService = new MessageService();
        } catch (Exception e) {
            System.err.println("Ошибка инициализации сервисов: " + e.getMessage());
            e.printStackTrace();
            // Отправляем ошибку клиенту и закрываем соединение
            communicator.sendData("ERROR:Сервер недоступен. Ошибка БД.");
            communicator.stop();
            throw new IOException("Ошибка инициализации БД", e);
        }
        synchronized (clients) {
            clients.add(this);
        }
    }

    public void start(){
        communicator.start();
        sendData(MessageType.REQUEST
                + ProtocolConstants.COMMAND_SEPARATOR
                + "Выберите действие: 1 - Вход, 2 - Регистрация");
    }

    public void sendData(String data){
        communicator.sendData(data);
    }

    private void parseData(String data){
        if (!authenticated) {
            handleAuthentication(data);
        } else {
            handleMessage(data);
        }
    }

    private void handleAuthentication(String data) {
        String[] parts = data.split("\\:\\:", 2);
        if (parts.length < 2) {
            sendData(MessageType.ERROR
                    + ProtocolConstants.COMMAND_SEPARATOR
                    + "Неверный формат команды");
            sendData(MessageType.REQUEST
                    + ProtocolConstants.COMMAND_SEPARATOR
                    + "Выберите действие: 1 - Вход, 2 - Регистрация");
            return;
        }

        String action = parts[0];
        String[] credentials = parts[1].split("\\|\\|\\|", 2);

        if (credentials.length < 2) {
            sendData(MessageType.ERROR
                    + ProtocolConstants.COMMAND_SEPARATOR
                    + "Требуется логин и пароль");
            return;
        }

        String nick = credentials[0].trim();
        String password = credentials[1].trim();

        try {
            if ("1".equals(action)) {
                // Login
                Optional<User> user = userService.authenticate(nick, password);
                if (user.isPresent()) {
                    currentUser = user.get();
                    authenticated = true;
                    synchronized (userSessions) {
                        userSessions.put(currentUser.getId(), this);
                    }
                    sendData(MessageType.AUTH_SUCCESS
                            + ProtocolConstants.COMMAND_SEPARATOR
                            + "Успешный вход как " + currentUser.getNick());
                    broadcastOnlineUsers();
                    sendOnlineUsers();
                } else {
                    sendData(MessageType.AUTH_FAILURE
                            + ProtocolConstants.COMMAND_SEPARATOR
                            + "Неверные учетные данные");
                }
            } else if ("2".equals(action)) {
                // Register
                if (!Character.isLetter(nick.charAt(0))) {
                    throw new Exception("Имя должно начинаться с буквы");
                }
                User newUser = userService.register(nick, password);
                currentUser = newUser;
                authenticated = true;
                synchronized (userSessions) {
                    userSessions.put(currentUser.getId(), this);
                }
                sendData(MessageType.AUTH_SUCCESS
                        + ProtocolConstants.COMMAND_SEPARATOR
                        + "Успешная регистрация. Вы вошли как " + currentUser.getNick());
                broadcastOnlineUsers();
                sendOnlineUsers();
            } else {
                sendData(MessageType.ERROR
                        + ProtocolConstants.COMMAND_SEPARATOR
                        + "Неизвестное действие");
            }
        } catch (Exception e) {
            sendData(MessageType.ERROR
                    + ProtocolConstants.COMMAND_SEPARATOR
                    + e.getMessage());
        }
    }

    private void handleMessage(String data) {
        String[] parts = data.split("\\:", 2);
        if (parts.length < 2) {
            return;
        }

        String command = parts[0];
        String content = parts[1];

        switch (command) {
            case "PRIVATE_MESSAGE":
                handlePrivateMessage(content);
                break;
            case "MESSAGE":
                handleBroadcastMessage(content);
                break;
            case "SET_CHAT_USER":
                handleSetChatUser(content);
                break;
            case "GET_HISTORY":
                handleGetHistory(content);
                break;
            case "SEARCH_MESSAGES":
                handleSearchMessages(content);
                break;
            case "GET_ONLINE_USERS":
                sendOnlineUsers();
                break;
            case "MARK_AS_READ":
                handleMarkAsRead(content);
                break;
            case "GET_BROADCAST_HISTORY":
                handleGetBroadcastHistory();
                break;
            case "MARK_BROADCAST_READ":
                handleMarkBroadcastAsRead();
                break;
            default:
                // Legacy support - treat as broadcast message
                handleBroadcastMessage(data);
                break;
        }
    }

    private void handlePrivateMessage(String content) {
        String[] parts = content.split("\\|\\|\\|", 2);
        if (parts.length < 2) {
            return;
        }

        String targetNick = parts[0].trim();
        String messageText = parts[1];

        Optional<User> targetUser = userService.findByNick(targetNick);
        if (targetUser.isEmpty()) {
            sendData(MessageType.ERROR
                    + ProtocolConstants.COMMAND_SEPARATOR
                    + "Пользователь " + targetNick + " не найден");
            return;
        }

        Message msg = new Message(currentUser.getId(), targetUser.get().getId(), messageText);
        messageService.save(msg);

        // Check if target user is online
        ConnectedClient targetClient = userSessions.get(targetUser.get().getId());
        if (targetClient != null) {
            // Target is online - update status to DELIVERED
            msg.setStatus(ru.gr0946x.entity.MessageStatus.DELIVERED);
            messageService.save(msg);
            
            // Send message to target with DELIVERED status
            targetClient.sendData(MessageType.PRIVATE_MESSAGE
                    + ProtocolConstants.COMMAND_SEPARATOR
                    + currentUser.getNick()
                    + ProtocolConstants.FIELD_SEPARATOR
                    + messageText
                    + ProtocolConstants.FIELD_SEPARATOR
                    + msg.getStatus());
        }
        
        // Send ACK to sender with final status
        sendData("PRIVATE_MESSAGE_ACK" + ProtocolConstants.COMMAND_SEPARATOR
                + targetNick
                + ProtocolConstants.FIELD_SEPARATOR
                + messageText
                + ProtocolConstants.FIELD_SEPARATOR
                + msg.getStatus());
    }

    private void handleBroadcastMessage(String messageText) {
        Message msg = new Message(currentUser.getId(), 0L, messageText);
        // Set to DELIVERED immediately since we're broadcasting to all online users
        msg.setStatus(ru.gr0946x.entity.MessageStatus.DELIVERED);
        messageService.save(msg);

        String broadcast = currentUser.getNick() + ProtocolConstants.AUTHOR_SEPARATOR + messageText;
        synchronized (clients) {
            clients.stream()
                    .filter(c -> c.authenticated && c.currentUser != null)
                    .forEach(client -> {
                        client.sendData(MessageType.MESSAGE
                                + ProtocolConstants.COMMAND_SEPARATOR
                                + broadcast);
                    });
        }
    }

    private void handleSetChatUser(String targetNick) {
        Optional<User> targetUser = userService.findByNick(targetNick.trim());
        if (targetUser.isPresent()) {
            selectedChatUserId = targetUser.get().getId();
            List<Message> history = messageService.findLastMessagesBetween(
                    currentUser.getId(),
                    selectedChatUserId,
                    10
            );
            sendData("HISTORY_START" + ProtocolConstants.COMMAND_SEPARATOR + targetNick);
            for (Message m : history) {
                String sender = m.getSenderId().equals(currentUser.getId()) ? "Вы" : targetNick;
                sendData("HISTORY_MESSAGE" + ProtocolConstants.COMMAND_SEPARATOR
                        + sender + ": " + m.getText() + " (" + m.getStatus() + ")");
            }
            sendData("HISTORY_END" + ProtocolConstants.COMMAND_SEPARATOR);
        }
    }

    private void handleGetHistory(String targetNick) {
        Optional<User> targetUser = userService.findByNick(targetNick.trim());
        if (targetUser.isPresent()) {
            List<Message> messages = messageService.findAllMessagesBetween(
                    currentUser.getId(),
                    targetUser.get().getId()
            );
            sendData("HISTORY_START" + ProtocolConstants.COMMAND_SEPARATOR + targetNick);
            for (Message m : messages) {
                String sender = m.getSenderId().equals(currentUser.getId()) ? "Вы" : targetNick;
                sendData("HISTORY_MESSAGE" + ProtocolConstants.COMMAND_SEPARATOR
                        + sender + ": " + m.getText());
            }
            sendData("HISTORY_END" + ProtocolConstants.COMMAND_SEPARATOR);
        }
    }

    private void handleSearchMessages(String searchData) {
        String[] parts = searchData.split("\\|\\|\\|", 2);
        if (parts.length < 2) {
            return;
        }

        String targetNick = parts[0].trim();
        String searchText = parts[1];

        Optional<User> targetUser = userService.findByNick(targetNick);
        if (targetUser.isEmpty()) {
            sendData(MessageType.ERROR
                    + ProtocolConstants.COMMAND_SEPARATOR
                    + "Пользователь не найден");
            return;
        }

        List<Message> results = messageService.searchMessagesBetween(
                currentUser.getId(),
                targetUser.get().getId(),
                searchText
        );

        sendData("SEARCH_START" + ProtocolConstants.COMMAND_SEPARATOR + searchText);
        for (Message m : results) {
            String sender = m.getSenderId().equals(currentUser.getId()) ? "Вы" : targetNick;
            sendData("SEARCH_RESULT" + ProtocolConstants.COMMAND_SEPARATOR
                    + sender + ": " + m.getText() + " (" + m.getStatus() + ")");
        }
        sendData("SEARCH_END" + ProtocolConstants.COMMAND_SEPARATOR);
    }

    private void sendOnlineUsers() {
        synchronized (userSessions) {
            List<String> onlineNicks = new ArrayList<>();
            for (User u : userService.findAll()) {
                if (userSessions.containsKey(u.getId()) && !u.getId().equals(currentUser.getId())) {
                    onlineNicks.add(u.getNick());
                }
            }
            sendData("ONLINE_USERS" + ProtocolConstants.COMMAND_SEPARATOR
                    + String.join("|", onlineNicks));
        }
    }

    private void broadcastOnlineUsers() {
        synchronized (clients) {
            clients.stream()
                    .filter(c -> c.authenticated && c.currentUser != null)
                    .forEach(ConnectedClient::sendOnlineUsers);
        }
    }

    private void handleMarkAsRead(String targetNick) {
        Optional<User> targetUser = userService.findByNick(targetNick.trim());
        if (targetUser.isEmpty()) {
            return;
        }

        // Find all DELIVERED messages from this target to current user and mark as READ
        List<Message> messages = messageService.findAllMessagesBetween(
                targetUser.get().getId(),
                currentUser.getId()
        );

        for (Message msg : messages) {
            if (msg.getStatusEnum() == ru.gr0946x.entity.MessageStatus.DELIVERED) {
                msg.setStatus(ru.gr0946x.entity.MessageStatus.READ);
                messageService.save(msg);
            }
        }

        // Notify sender if online
        ConnectedClient senderClient = userSessions.get(targetUser.get().getId());
        if (senderClient != null) {
            senderClient.sendData("MESSAGES_READ" + ProtocolConstants.COMMAND_SEPARATOR
                    + currentUser.getNick());
        }
    }

    private void handleGetBroadcastHistory() {
        List<Message> broadcasts = messageService.findAllBroadcasts();
        sendData("BROADCAST_START" + ProtocolConstants.COMMAND_SEPARATOR);
        for (Message m : broadcasts) {
            Optional<User> sender = userService.findById(m.getSenderId());
            String senderNick = sender.isPresent() ? sender.get().getNick() : "Неизвестный";
            sendData("BROADCAST_MESSAGE" + ProtocolConstants.COMMAND_SEPARATOR
                    + senderNick + ": " + m.getText() + " (" + m.getStatus() + ")");
        }
        sendData("BROADCAST_END" + ProtocolConstants.COMMAND_SEPARATOR);
    }

    private void handleMarkBroadcastAsRead() {
        // Find all DELIVERED broadcast messages and mark as READ
        List<Message> broadcasts = messageService.findAllBroadcasts();
        for (Message msg : broadcasts) {
            // Mark as READ only if:
            // 1. Status is DELIVERED
            // 2. Current user is not the sender
            if (msg.getStatusEnum() == ru.gr0946x.entity.MessageStatus.DELIVERED && 
                !msg.getSenderId().equals(currentUser.getId())) {
                msg.setStatus(ru.gr0946x.entity.MessageStatus.READ);
                messageService.save(msg);
            }
        }
    }

    public void stop(){
        if (currentUser != null) {
            synchronized (userSessions) {
                userSessions.remove(currentUser.getId());
            }
            broadcastOnlineUsers();
            sendData(MessageType.INFO
                    + ProtocolConstants.COMMAND_SEPARATOR
                    + currentUser.getNick() + " вышел из чата");
        }
        communicator.stop();
        synchronized (clients) {
            clients.remove(this);
        }
    }

    public static void main(String[] args) {
        // For testing
    }
}
