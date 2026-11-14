package com.localstream.player;

import com.localstream.player.api.StreamApiClient;
import com.localstream.player.model.RemoteFile;
import com.localstream.player.ui.PlayerWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.List;

/**
 * Main Laptop Java Application for Local Streaming.
 * Displays the connection configuration and simple file list UI.
 * When a file is selected, it opens a separate PlayerWindow.
 */
public class LocalStreamApp extends Application {

    private final StreamApiClient apiClient = new StreamApiClient();
    private final ObservableList<RemoteFile> masterFileList = FXCollections.observableArrayList();
    private FilteredList<RemoteFile> filteredFileList;

    private TextField serverUrlField;
    private PasswordField pinField;
    private TextField pinPlainField;
    private CheckBox showPinCheck;
    private Button connectBtn;
    private Circle statusIndicator;
    private Label statusLabel;
    private TableView<RemoteFile> tableView;
    private TextField searchField;
    private Label countLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Local Stream Player - Client");
        primaryStage.setWidth(950);
        primaryStage.setHeight(650);
        primaryStage.setMinWidth(750);
        primaryStage.setMinHeight(500);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #121212;");

        // Top Connection & Search Panel
        VBox topContainer = new VBox(10);
        topContainer.setPadding(new Insets(16));
        topContainer.setStyle("-fx-background-color: #1E1E1E; -fx-border-color: #2E2E2E; -fx-border-width: 0 0 1 0;");

        HBox connectionBar = createConnectionBar();
        HBox filterBar = createFilterBar();
        topContainer.getChildren().addAll(connectionBar, filterBar);
        root.setTop(topContainer);

        // Center: File List Table
        tableView = createTableView();
        VBox centerBox = new VBox(tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        centerBox.setPadding(new Insets(16));
        root.setCenter(centerBox);

        // Bottom: Status Bar & Instructions
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initial focus
        Platform.runLater(() -> connectBtn.requestFocus());
    }

    private HBox createConnectionBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("📡 Local Stream");
        title.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label urlLbl = new Label("Server URL:");
        urlLbl.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 12px;");

        serverUrlField = new TextField("http://192.168.43.1:8080");
        serverUrlField.setPrefWidth(210);
        serverUrlField.setStyle("-fx-background-color: #2D2D2D; -fx-text-fill: white; -fx-prompt-text-fill: #777; -fx-background-radius: 4;");

        Label pinLbl = new Label("PIN Token:");
        pinLbl.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 12px;");

        pinField = new PasswordField();
        pinField.setPromptText("4-digit PIN");
        pinField.setPrefWidth(100);
        pinField.setStyle("-fx-background-color: #2D2D2D; -fx-text-fill: white; -fx-prompt-text-fill: #777; -fx-background-radius: 4;");

        pinPlainField = new TextField();
        pinPlainField.setPromptText("4-digit PIN");
        pinPlainField.setPrefWidth(100);
        pinPlainField.setStyle("-fx-background-color: #2D2D2D; -fx-text-fill: white; -fx-prompt-text-fill: #777; -fx-background-radius: 4;");
        pinPlainField.setVisible(false);
        pinPlainField.setManaged(false);

        // Synchronize password fields
        pinField.textProperty().bindBidirectional(pinPlainField.textProperty());

        showPinCheck = new CheckBox("Show");
        showPinCheck.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 11px;");
        showPinCheck.setOnAction(e -> {
            boolean show = showPinCheck.isSelected();
            pinField.setVisible(!show);
            pinField.setManaged(!show);
            pinPlainField.setVisible(show);
            pinPlainField.setManaged(show);
        });

        connectBtn = new Button("⚡ Connect");
        connectBtn.setStyle("-fx-background-color: #2979FF; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 16; -fx-background-radius: 5; -fx-cursor: hand;");
        connectBtn.setOnAction(e -> loadFilesFromServer());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusIndicator = new Circle(5, Color.web("#757575"));
        statusLabel = new Label("Disconnected");
        statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");

        HBox statusBox = new HBox(6, statusIndicator, statusLabel);
        statusBox.setAlignment(Pos.CENTER_RIGHT);

        bar.getChildren().addAll(title, urlLbl, serverUrlField, pinLbl, pinField, pinPlainField, showPinCheck, connectBtn, spacer, statusBox);
        return bar;
    }

    private HBox createFilterBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label searchLbl = new Label("🔍 Search:");
        searchLbl.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 12px;");

        searchField = new TextField();
        searchField.setPromptText("Filter files by name or type...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setStyle("-fx-background-color: #252525; -fx-text-fill: white; -fx-prompt-text-fill: #666; -fx-background-radius: 4;");

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (filteredFileList != null) {
                filteredFileList.setPredicate(file -> {
                    if (newVal == null || newVal.trim().isEmpty()) return true;
                    String filter = newVal.toLowerCase().trim();
                    return file.getName().toLowerCase().contains(filter) ||
                           file.getMimeType().toLowerCase().contains(filter);
                });
                updateCountLabel();
            }
        });

        countLabel = new Label("0 files");
        countLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");

        bar.getChildren().addAll(searchLbl, searchField, countLabel);
        return bar;
    }

    private TableView<RemoteFile> createTableView() {
        TableView<RemoteFile> table = new TableView<>();
        table.setStyle("-fx-background-color: #1E1E1E; -fx-border-color: #2E2E2E; -fx-table-cell-border-color: #2E2E2E;");
        table.setPlaceholder(new Label("No files loaded. Connect to your Android streaming server above."));

        // Column 1: Type Icon
        TableColumn<RemoteFile, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(70);
        typeCol.setCellValueFactory(new PropertyValueFactory<>("mimeType"));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    RemoteFile file = getTableRow().getItem();
                    if (file != null && file.isVideo()) {
                        setText("🎬 Video");
                        setStyle("-fx-text-fill: #64B5F6;");
                    } else if (file != null && file.isAudio()) {
                        setText("🎵 Audio");
                        setStyle("-fx-text-fill: #81C784;");
                    } else {
                        setText("📄 File");
                        setStyle("-fx-text-fill: #B0BEC5;");
                    }
                }
            }
        });

        // Column 2: File Name
        TableColumn<RemoteFile, String> nameCol = new TableColumn<>("File Name");
        nameCol.setPrefWidth(380);
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold;");
                }
            }
        });

        // Column 3: MIME Type
        TableColumn<RemoteFile, String> mimeCol = new TableColumn<>("MIME Type");
        mimeCol.setPrefWidth(160);
        mimeCol.setCellValueFactory(new PropertyValueFactory<>("mimeType"));
        mimeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #9E9E9E; -fx-font-family: monospace; -fx-font-size: 11px;");
                }
            }
        });

        // Column 4: Size
        TableColumn<RemoteFile, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setPrefWidth(110);
        sizeCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFormattedSize()));
        sizeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #E0E0E0;");
                }
            }
        });

        // Column 5: Action Button
        TableColumn<RemoteFile, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button playBtn = new Button("▶ Play");

            {
                playBtn.setStyle("-fx-background-color: #2979FF; -fx-text-fill: white; -fx-background-radius: 4; -fx-cursor: hand; -fx-font-size: 11px;");
                playBtn.setOnAction(e -> {
                    RemoteFile file = getTableRow().getItem();
                    if (file != null) {
                        openPlayer(file);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(playBtn);
                }
            }
        });

        table.getColumns().addAll(typeCol, nameCol, mimeCol, sizeCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Double-click row to play
        table.setRowFactory(tv -> {
            TableRow<RemoteFile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    RemoteFile rowData = row.getItem();
                    openPlayer(rowData);
                }
            });
            return row;
        });

        filteredFileList = new FilteredList<>(masterFileList, p -> true);
        table.setItems(filteredFileList);

        return table;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(16);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #181818; -fx-border-color: #2E2E2E; -fx-border-width: 1 0 0 0;");

        Label hintLabel = new Label("💡 Tip: Turn on Android Hotspot, connect your laptop to it, check the IP & PIN in the Android app, then click Connect.");
        hintLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");

        bar.getChildren().add(hintLabel);
        return bar;
    }

    private void loadFilesFromServer() {
        String serverUrl = serverUrlField.getText();
        String pin = pinField.getText();

        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            showErrorAlert("Missing Server URL", "Please enter the Android server URL (e.g. http://192.168.43.1:8080).");
            return;
        }

        connectBtn.setDisable(true);
        statusIndicator.setFill(Color.web("#FFB300")); // Orange
        statusLabel.setText("Connecting...");
        statusLabel.setStyle("-fx-text-fill: #FFB300;");

        new Thread(() -> {
            try {
                List<RemoteFile> files = apiClient.fetchFiles(serverUrl, pin);
                Platform.runLater(() -> {
                    masterFileList.clear();
                    masterFileList.addAll(files);
                    updateCountLabel();
                    statusIndicator.setFill(Color.web("#00E676")); // Green
                    statusLabel.setText("Connected (" + files.size() + " files)");
                    statusLabel.setStyle("-fx-text-fill: #00E676;");
                    connectBtn.setDisable(false);
                });
            } catch (StreamApiClient.PinAuthenticationException ex) {
                Platform.runLater(() -> {
                    statusIndicator.setFill(Color.web("#FF1744")); // Red
                    statusLabel.setText("PIN Authentication Failed");
                    statusLabel.setStyle("-fx-text-fill: #FF1744;");
                    connectBtn.setDisable(false);
                    showErrorAlert("Authentication Failed", ex.getMessage());
                });
            } catch (StreamApiClient.ServerConnectionException ex) {
                Platform.runLater(() -> {
                    statusIndicator.setFill(Color.web("#FF1744")); // Red
                    statusLabel.setText("Connection Failed");
                    statusLabel.setStyle("-fx-text-fill: #FF1744;");
                    connectBtn.setDisable(false);
                    showErrorAlert("Connection Error", ex.getMessage());
                });
            }
        }).start();
    }

    private void openPlayer(RemoteFile file) {
        String serverUrl = serverUrlField.getText();
        String pin = pinField.getText();
        String streamUrl = apiClient.buildStreamUrl(serverUrl, file.getId(), pin);

        PlayerWindow playerWindow = new PlayerWindow(file, streamUrl);
        playerWindow.show();
    }

    private void updateCountLabel() {
        if (filteredFileList != null) {
            countLabel.setText(filteredFileList.size() + " of " + masterFileList.size() + " files");
        }
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
