package ru.gr0946x.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Client {
    private Communicator communicator;
    private final List<BiConsumer<String, MessageType>> listeners =
            new ArrayList<>();
    private final List<Consumer<String>> rawDataListeners = new ArrayList<>();
    
    public Client(String host, int port) throws IOException {
        var socket = new Socket(host, port);
        communicator = new Communicator(socket);
        communicator.addDataListener(this::parseData);
    }

    public void addDataListener(BiConsumer<String, MessageType> listener){
        listeners.add(listener);
    }

    public void removeDataListener(BiConsumer<String, MessageType> listener){
        listeners.remove(listener);
    }

    public void addRawDataListener(Consumer<String> listener) {
        rawDataListeners.add(listener);
    }

    public void removeRawDataListener(Consumer<String> listener) {
        rawDataListeners.remove(listener);
    }

    public void start(){
        communicator.start();
    }

    private void parseData(String data){
        for (var listener : rawDataListeners) {
            listener.accept(data);
        }

        var fullInfo = data.split("\\:", 2);
        if (fullInfo.length == 2) {
            try {
                var type = MessageType.valueOf(fullInfo[0]);
                for (var listener : listeners) {
                    listener.accept(fullInfo[1], type);
                }
            } catch (IllegalArgumentException e) {
                for (var listener : listeners) {
                    listener.accept(fullInfo[0] + ProtocolConstants.COMMAND_SEPARATOR + fullInfo[1], MessageType.INFO);
                }
            }
        }
    }

    public void sendData(String data){
        communicator.sendData(data);
    }

    public void sendPrivateMessage(String recipientNick, String message) {
        String data = MessageType.PRIVATE_MESSAGE + ProtocolConstants.COMMAND_SEPARATOR
                + recipientNick + ProtocolConstants.FIELD_SEPARATOR + message;
        sendData(data);
    }

    public void sendBroadcastMessage(String message) {
        String data = MessageType.MESSAGE + ProtocolConstants.COMMAND_SEPARATOR + message;
        sendData(data);
    }

    public void setChatUser(String userNick) {
        String data = "SET_CHAT_USER" + ProtocolConstants.COMMAND_SEPARATOR + userNick;
        sendData(data);
    }

    public void markAsRead(String userNick) {
        String data = "MARK_AS_READ" + ProtocolConstants.COMMAND_SEPARATOR + userNick;
        sendData(data);
    }

    public void requestOnlineUsers() {
        String data = "GET_ONLINE_USERS" + ProtocolConstants.COMMAND_SEPARATOR;
        sendData(data);
    }

    public void searchMessages(String userNick, String searchText) {
        String data = MessageType.SEARCH_MESSAGES + ProtocolConstants.COMMAND_SEPARATOR
                + userNick + ProtocolConstants.FIELD_SEPARATOR + searchText;
        sendData(data);
    }

    public void getBroadcastHistory() {
        String data = "GET_BROADCAST_HISTORY" + ProtocolConstants.COMMAND_SEPARATOR;
        sendData(data);
    }

    public void markBroadcastAsRead() {
        String data = "MARK_BROADCAST_READ" + ProtocolConstants.COMMAND_SEPARATOR;
        sendData(data);
    }

    public void stop(){
        communicator.stop();
    }
}
