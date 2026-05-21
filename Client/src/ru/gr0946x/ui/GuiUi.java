package ru.gr0946x.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import ru.gr0946x.net.Client;
import ru.gr0946x.net.MessageType;
import ru.gr0946x.net.ProtocolConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiUi extends Application implements Ui {
    private Client client;
    private final List<Consumer<String>> listeners = new ArrayList<>();
    

    private TextArea chatArea;
    private TextArea inputArea;
    private ListView<String> usersList;
    private Label currentUserLabel;
    private Label selectedUserLabel;
    private TextField searchField;
    private Button sendButton;
    private Button broadcastButton;
    private Button searchButton;
    
    private String currentUser = null;
    private String selectedChatUser = null;
    private boolean authenticated = false;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize client
        try {
            client = new Client("localhost", 9460);
            client.addRawDataListener(this::handleRawData);
            // Connect UI as listener for authentication commands
            addUserDataListener(client::sendData);
        } catch (Exception e) {
            showError("Ошибка подключения: " + e.getMessage());
            return;
        }

        primaryStage.setTitle("Мессенджер");
        primaryStage.setWidth(900);
        primaryStage.setHeight(700);


        BorderPane root = new BorderPane();
        
        // Top: User info
        HBox topBar = createTopBar();
        root.setTop(topBar);
        
        // Left: Users list
        VBox leftPanel = createLeftPanel();
        root.setLeft(leftPanel);
        
        // Center: Chat area
        VBox centerPanel = createCenterPanel();
        root.setCenter(centerPanel);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (client != null) {
                client.stop();
            }
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();

        // Start client connection
        client.start();
        
        // Request authentication
        showAuthDialog();
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        
        currentUserLabel = new Label("Не авторизован");
        currentUserLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        
        selectedUserLabel = new Label("Выберите собеседника");
        selectedUserLabel.setStyle("-fx-font-size: 11;");
        
        topBar.getChildren().addAll(currentUserLabel, new Separator(javafx.geometry.Orientation.VERTICAL), selectedUserLabel);
        
        return topBar;
    }

    private VBox createLeftPanel() {
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 0 1 0 0;");
        leftPanel.setPrefWidth(200);
        
        // Broadcast chat button
        Button broadcastChatButton = new Button("ОБЩИЙ ЧАТ");
        broadcastChatButton.setStyle("-fx-font-weight: bold; -fx-padding: 10;");
        broadcastChatButton.setMaxWidth(Double.MAX_VALUE);
        broadcastChatButton.setOnAction(e -> selectBroadcastChat());
        
        Label usersLabel = new Label("Онлайн пользователи:");
        usersLabel.setStyle("-fx-font-weight: bold;");
        
        usersList = new ListView<>();
        usersList.setOnMouseClicked(e -> {
            String selected = usersList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectUser(selected);
            }
        });
        
        VBox.setVgrow(usersList, Priority.ALWAYS);
        
        Button refreshButton = new Button("Обновить");
        refreshButton.setOnAction(e -> client.requestOnlineUsers());
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        
        leftPanel.getChildren().addAll(broadcastChatButton, usersLabel, usersList, refreshButton);
        return leftPanel;
    }

    private VBox createCenterPanel() {
        VBox centerPanel = new VBox(10);
        centerPanel.setPadding(new Insets(10));
        
        // Chat display area
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-font-size: 11; -fx-font-family: 'Consolas', 'Courier New';");
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        
        // Search panel
        HBox searchPanel = new HBox(5);
        searchField = new TextField();
        searchField.setPromptText("Искать сообщение...");
        searchButton = new Button("Поиск");
        searchButton.setOnAction(e -> performSearch());
        searchButton.setDisable(true);
        
        searchPanel.getChildren().addAll(new Label("Поиск:"), searchField, searchButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        // Input and buttons panel
        HBox inputPanel = new HBox(5);
        inputPanel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        inputPanel.setPadding(new Insets(5));
        
        inputArea = new TextArea();
        inputArea.setWrapText(true);
        inputArea.setStyle("-fx-font-size: 11;");
        inputArea.setPrefHeight(80);
        inputArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode().toString().equals("ENTER")) {
                sendMessage();
                e.consume();
            }
        });
        
        VBox buttonPanel = new VBox(5);
        sendButton = new Button("Отправить\n(Ctrl+Enter)");
        sendButton.setStyle("-fx-text-alignment: center;");
        sendButton.setOnAction(e -> sendMessage());
        sendButton.setDisable(true);
        
        broadcastButton = new Button("Отправить\nвсем");
        broadcastButton.setStyle("-fx-text-alignment: center;");
        broadcastButton.setOnAction(e -> sendBroadcast());
        broadcastButton.setDisable(true);
        
        buttonPanel.getChildren().addAll(sendButton, broadcastButton);
        
        inputPanel.getChildren().addAll(inputArea, buttonPanel);
        HBox.setHgrow(inputArea, Priority.ALWAYS);
        
        centerPanel.getChildren().addAll(chatArea, searchPanel, inputPanel);
        return centerPanel;
    }

    private void showAuthDialog() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Авторизация");
        dialog.setHeaderText("Вход или регистрация");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        ToggleGroup group = new ToggleGroup();
        RadioButton loginButton = new RadioButton("Вход");
        RadioButton registerButton = new RadioButton("Регистрация");
        loginButton.setToggleGroup(group);
        registerButton.setToggleGroup(group);
        loginButton.setSelected(true);

        TextField nickField = new TextField();
        nickField.setPromptText("Логин");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");

        grid.add(new Label("Действие:"), 0, 0);
        grid.add(loginButton, 1, 0);
        grid.add(registerButton, 1, 1);
        grid.add(new Label("Логин:"), 0, 2);
        grid.add(nickField, 1, 2);
        grid.add(new Label("Пароль:"), 0, 3);
        grid.add(passwordField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                String action = loginButton.isSelected() ? "1" : "2";
                return new String[]{action, nickField.getText(), passwordField.getText()};
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent()) {
            String[] credentials = result.get();
            String authCmd = credentials[0] + ProtocolConstants.PARAM_SEPARATOR
                    + credentials[1] + ProtocolConstants.FIELD_SEPARATOR + credentials[2];
            for (var listener : listeners) {
                listener.accept(authCmd);
            }
        } else {
            Platform.exit();
        }
    }

    private void selectUser(String nick) {
        selectedChatUser = nick;
        selectedUserLabel.setText("Чат с: " + nick);
        chatArea.clear();
        client.setChatUser(nick);
        client.markAsRead(nick);
        searchButton.setDisable(false);
        sendButton.setDisable(false);
    }

    private void selectBroadcastChat() {
        selectedChatUser = null;
        selectedUserLabel.setText("ОБЩИЙ ЧАТ");
        chatArea.clear();
        client.getBroadcastHistory();
        client.markBroadcastAsRead();
        searchButton.setDisable(true);
        sendButton.setDisable(false);
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        if (selectedChatUser == null) {
            // Broadcasting to general chat
            client.sendBroadcastMessage(text);
            appendToChat("Вы (личное) к [ВСЕМ]: " + text);
        } else {
            // Private message to selected user
            client.sendPrivateMessage(selectedChatUser, text);
            appendToChat("Вы (личное) к " + selectedChatUser + ": " + text);
        }
        inputArea.clear();
    }

    private void sendBroadcast() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;
        
        client.sendBroadcastMessage(text);
        appendToChat("[ВСЕМ] Вы: " + text);
        inputArea.clear();
    }

    private void performSearch() {
        if (selectedChatUser == null) {
            showError("Выберите собеседника");
            return;
        }
        
        String searchText = searchField.getText().trim();
        if (searchText.isEmpty()) return;
        
        client.searchMessages(selectedChatUser, searchText);
    }

    private void handleRawData(String data) {
        Platform.runLater(() -> {
            String[] parts = data.split("\\:", 2);
            if (parts.length < 2) {
                return;
            }

            String command = parts[0];
            String content = parts[1];

            switch (command) {
                case "AUTH_SUCCESS":
                    authenticated = true;
                    currentUser = extractNickFromAuthSuccess(content);
                    currentUserLabel.setText("Авторизован: " + currentUser);
                    broadcastButton.setDisable(false);
                    appendToChat("[СИСТЕМА] " + content);
                    client.requestOnlineUsers();
                    break;
                case "AUTH_FAILURE":
                    authenticated = false;
                    showError(content);
                    Platform.runLater(this::showAuthDialog);
                    break;
                case "PRIVATE_MESSAGE":
                    String[] msgParts = content.split("\\|\\|\\|", 3);
                    if (msgParts.length >= 2) {
                        String status = msgParts.length > 2 ? " (" + msgParts[2] + ")" : "";
                        appendToChat(msgParts[0] + ": " + msgParts[1] + status);
                    }
                    break;
                case "PRIVATE_MESSAGE_ACK":
                    String[] ackParts = content.split("\\|\\|\\|", 3);
                    if (ackParts.length >= 2) {
                        String status = ackParts.length > 2 ? " (" + ackParts[2] + ")" : "";
                        appendToChat("Вы (личное) к " + ackParts[0] + ": " + ackParts[1] + status);
                    }
                    break;
                case "MESSAGE":
                    String[] authParts = content.split("\\|\\|", 2);
                    if (authParts.length == 2) {
                        appendToChat("[ВСЕМ] " + authParts[0] + ": " + authParts[1]);
                    }
                    break;
                case "ONLINE_USERS":
                    updateOnlineUsers(content);
                    break;
                case "ERROR":
                    showError(content);
                    break;
                case "HISTORY_START":
                    chatArea.clear();
                    appendToChat("--- История сообщений с " + content + " ---");
                    break;
                case "HISTORY_MESSAGE":
                    appendToChat(content);
                    break;
                case "HISTORY_END":
                    appendToChat("--- Конец истории ---");
                    break;
                case "SEARCH_START":
                    chatArea.clear();
                    appendToChat("=== Результаты поиска: " + content + " ===");
                    break;
                case "SEARCH_RESULT":
                    appendToChat(content);
                    break;
                case "SEARCH_END":
                    appendToChat("=== Конец поиска ===");
                    break;
                case "BROADCAST_START":
                    chatArea.clear();
                    appendToChat("=== ОБЩИЙ ЧАТ ===");
                    break;
                case "BROADCAST_MESSAGE":
                    appendToChat(content);
                    break;
                case "BROADCAST_END":
                    appendToChat("=== Конец общего чата ===");
                    break;
                default:
                    if (content.startsWith("REQUEST")) {
                        showInfo(content.substring(8));
                    }
                    break;
            }
        });
    }

    private String extractNickFromAuthSuccess(String content) {
        // Extract nick from messages like:
        // "Успешный вход как test" or "Успешная регистрация. Вы вошли как test"
        if (content.contains(" как ")) {
            String[] parts = content.split(" как ");
            if (parts.length > 1) {
                String nick = parts[1].trim();
                // Remove trailing punctuation
                nick = nick.replaceAll("[^a-zA-Zа-яА-Я0-9_]", "");
                return nick.isEmpty() ? "Пользователь" : nick;
            }
        }
        return "Пользователь";
    }

    private void updateOnlineUsers(String content) {
        Platform.runLater(() -> {
            usersList.getItems().clear();
            if (!content.isEmpty()) {
                String[] users = content.split("\\|");
                for (String user : users) {
                    if (!user.isEmpty()) {
                        usersList.getItems().add(user);
                    }
                }
            }
        });
    }

    private void appendToChat(String message) {
        Platform.runLater(() -> {
            chatArea.appendText(message + "\n");
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Информация");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void showInfo(String data, MessageType type) {
        handleRawData(type + ProtocolConstants.COMMAND_SEPARATOR + data);
    }

    @Override
    public void addUserDataListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeUserDataListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
