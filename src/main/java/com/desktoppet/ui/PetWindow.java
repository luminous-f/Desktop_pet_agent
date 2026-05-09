package com.desktoppet.ui;

import com.desktoppet.controller.dto.ApiModels.ChatResponse;
import com.desktoppet.controller.dto.ApiModels.StartupResponse;
import com.desktoppet.client.BackendApiClient;
import com.desktoppet.files.FileOrganizer;
import com.desktoppet.schedule.ScheduleItem;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

public final class PetWindow {
    private static final String SPRITE_BASE = "/assets/character/Castorice/sprites/";
    private static final String UI_FONT = "Noto Sans CJK SC";
    private static final String CHARACTER_NAME = "Castorice";

    private final Stage stage;
    private final BackendApiClient apiClient;
    private final Random random = new Random();

    private final StackPane root = new StackPane();
    private final ImageView petSprite = new ImageView();
    private final Image defaultSprite = loadSprite("默认.png", "speaking.png");
    private final Image happySprite = loadSprite("happy.png");
    private final Image speakingSprite = loadSprite("speaking.png");
    private final Image thinkingSprite = loadSprite("thinking.png");
    private final Image errorSprite = loadSprite("error.png");
    private final TextArea chatLog = new TextArea();
    private final TextArea chatInput = new TextArea();
    private final VBox chatPanel = new VBox(8);
    private final VBox schedulePanel = new VBox(8);
    private final VBox scheduleList = new VBox(4);
    private final VBox newsPanel = new VBox(8);
    private final VBox profilePanel = new VBox(8);
    private final TextArea profileEditor = new TextArea();
    private final TextArea newsText = new TextArea("点击刷新新闻摘要");
    private double dragOffsetX;
    private double dragOffsetY;

    public PetWindow(Stage stage, BackendApiClient apiClient) {
        this.stage = stage;
        this.apiClient = apiClient;
        build();
    }

    public Parent view() {
        return root;
    }

    public void startProactiveScheduler() {
        runAsync(() -> {
            StartupResponse startup = apiClient.startup();
            Platform.runLater(() -> {
                if (startup.startupNotice() != null && !startup.startupNotice().isBlank()) {
                    appendAssistant(startup.startupNotice());
                }
                if (startup.weather() != null && !startup.weather().isBlank()) {
                    appendAssistant(startup.weather());
                }
                profileEditor.setText(startup.profileText() == null ? "" : startup.profileText());
                renderSchedule(startup.todaySchedule().stream()
                        .map(item -> new ScheduleItem(item.id(), java.time.LocalDate.parse(item.date()), item.title(), item.done()))
                        .toList());
            });
            scheduleNextPrompt();
        });
    }

    private void build() {
        root.getStyleClass().add("pet-root");
        root.setPadding(new Insets(10));
        root.setStyle("-fx-font-family: '" + UI_FONT + "';");
        Button close = buildCloseButton();
        root.getChildren().addAll(buildMain(), close);
        StackPane.setAlignment(close, Pos.TOP_RIGHT);
        StackPane.setMargin(close, new Insets(0, 60, 0, 0));
    }

    private HBox buildMain() {
        petSprite.getStyleClass().add("pet-sprite");
        petSprite.setImage(defaultSprite);
        petSprite.setFitWidth(220);
        petSprite.setFitHeight(220);
        petSprite.setPreserveRatio(true);
        petSprite.setSmooth(true);
        petSprite.setOnMouseClicked(event -> {
            setSprite(happySprite);
            PauseTransition pause = new PauseTransition(Duration.seconds(1.2));
            pause.setOnFinished(done -> setSprite(defaultSprite));
            pause.play();
        });
        StackPane characterPane = new StackPane(petSprite);
        characterPane.getStyleClass().add("character-pane");
        enableDrag(characterPane);

        chatLog.getStyleClass().add("chat-log");
        chatLog.setFont(Font.font(UI_FONT, 13));
        chatLog.setEditable(false);
        chatLog.setWrapText(true);

        chatInput.getStyleClass().add("chat-input");
        chatInput.setFont(Font.font(UI_FONT, 13));
        chatInput.setPromptText("和桌宠说点什么");
        chatInput.setWrapText(true);
        chatInput.setPrefRowCount(2);
        chatInput.setMinHeight(56);

        Button send = new Button("发送");
        send.setFont(Font.font(UI_FONT, 13));
        send.getStyleClass().add("art-button");
        send.setOnAction(event -> sendCurrentMessage());
        HBox inputRow = new HBox(8, chatInput, buttonWithCaption(send, "发送", "send-button-box"));
        inputRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(chatInput, Priority.ALWAYS);

        chatPanel.getStyleClass().addAll("panel", "chat-panel");
        bindVisibilityToLayout(chatPanel);
        chatPanel.getChildren().addAll(chatLog, inputRow);

        Button chatToggle = toolButton(chatPanel);
        Button scheduleToggle = toolButton(schedulePanel);
        scheduleToggle.setOnAction(event -> {
            schedulePanel.setVisible(!schedulePanel.isVisible());
            if (schedulePanel.isVisible()) {
                refreshScheduleFromBackend();
            }
        });
        Button newsToggle = toolButton(newsPanel);
        Button profile = toolButton(profilePanel);
        Button organize = new Button("整理");
        organize.setFont(Font.font(UI_FONT, 13));
        organize.getStyleClass().addAll("tool-button", "art-button");
        organize.setOnAction(event -> filePanelVisible());
        HBox tools = new HBox(
                4,
                buttonWithCaption(chatToggle, "对话"),
                buttonWithCaption(scheduleToggle, "日程"),
                buttonWithCaption(newsToggle, "新闻"),
                buttonWithCaption(profile, "画像"),
                buttonWithCaption(organize, "整理")
        );
        tools.setAlignment(Pos.CENTER);

        VBox petColumn = new VBox(0, characterPane, tools);
        petColumn.getStyleClass().add("pet-column");
        petColumn.setAlignment(Pos.CENTER);

        HBox panels = new HBox(4, chatPanel, buildSidePanels());
        panels.getStyleClass().add("panels-column");
        panels.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(panels, Priority.ALWAYS);

        HBox main = new HBox(4, petColumn, panels);
        main.getStyleClass().add("main-layout");
        main.setAlignment(Pos.CENTER_LEFT);
        tools.toFront();
        return main;
    }

    private Button buildCloseButton() {
        Button close = new Button("x");
        close.setFont(Font.font("DejaVu Sans", 13));
        close.getStyleClass().addAll("art-button", "close-art-button");
        close.setOnAction(event -> Platform.exit());
        return close;
    }

    private VBox buildSidePanels() {
        schedulePanel.getStyleClass().addAll("panel", "schedule-panel");
        newsPanel.getStyleClass().addAll("panel", "news-panel");
        profilePanel.getStyleClass().addAll("panel", "profile-panel");
        schedulePanel.setVisible(false);
        newsPanel.setVisible(false);
        profilePanel.setVisible(false);
        bindVisibilityToLayout(schedulePanel);
        bindVisibilityToLayout(newsPanel);
        bindVisibilityToLayout(profilePanel);
        buildSchedulePanel();
        buildNewsPanel();
        buildProfilePanel();
        return new VBox(4, schedulePanel, newsPanel, profilePanel);
    }

    private Button toolButton(Parent panel) {
        Button button = new Button();
        button.setFont(Font.font(UI_FONT, 13));
        button.getStyleClass().addAll("tool-button", "art-button");
        button.setOnAction(event -> panel.setVisible(!panel.isVisible()));
        return button;
    }

    private void buildSchedulePanel() {
        schedulePanel.getChildren().clear();
        Label title = panelTitle("今日日程");
        TextField input = new TextField();
        input.getStyleClass().add("ui-input");
        input.setFont(Font.font(UI_FONT, 13));
        input.setPromptText("添加事项");
        Button add = new Button("添加");
        add.setFont(Font.font(UI_FONT, 13));
        add.getStyleClass().addAll("art-button", "panel-action-button");
        add.setOnAction(event -> addSchedule(input));
        input.setOnAction(event -> addSchedule(input));
        HBox row = new HBox(8, input, buttonWithCaption(add, "添加"));
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);
        ScrollPane scheduleScroll = new ScrollPane(scheduleList);
        scheduleScroll.getStyleClass().add("schedule-list-scroll");
        scheduleScroll.setFitToWidth(true);
        scheduleScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scheduleScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        schedulePanel.getChildren().addAll(title, row, scheduleScroll);
        refreshScheduleFromBackend();
    }

    private void refreshScheduleFromBackend() {
        runAsync(() -> {
            List<ScheduleItem> items = apiClient.todaySchedule();
            Platform.runLater(() -> renderSchedule(items));
        });
    }

    private void renderSchedule(List<ScheduleItem> items) {
        scheduleList.getChildren().clear();
        List<ScheduleItem> todayItems = items.stream()
                .filter(item -> LocalDate.now().equals(item.date()))
                .toList();
        if (todayItems.isEmpty()) {
            Label empty = new Label("今天还没有日程");
            empty.setFont(Font.font(UI_FONT, 13));
            scheduleList.getChildren().add(empty);
            return;
        }
        for (ScheduleItem item : todayItems) {
            Label itemLabel = new Label((item.done() ? "[x] " : "[ ] ") + item.title());
            itemLabel.setFont(Font.font(UI_FONT, 13));
            scheduleList.getChildren().add(itemLabel);
        }
    }

    private void buildNewsPanel() {
        newsPanel.getChildren().clear();
        Label title = panelTitle("新闻摘要");
        newsText.getStyleClass().add("transparent-text");
        newsText.setFont(Font.font(UI_FONT, 13));
        newsText.setWrapText(true);
        newsText.setEditable(false);
        newsText.setPrefRowCount(8);
        Button refresh = new Button("刷新");
        refresh.setFont(Font.font(UI_FONT, 13));
        refresh.getStyleClass().addAll("art-button", "panel-action-button");
        refresh.setOnAction(event -> {
            newsText.setText("查询中...");
            runAsync(() -> {
                String result = apiClient.dailyNews();
                Platform.runLater(() -> newsText.setText(result));
            });
        });
        newsPanel.getChildren().addAll(title, newsText, buttonWithCaption(refresh, "刷新"));
    }

    private void buildProfilePanel() {
        profilePanel.getChildren().clear();
        Label title = panelTitle("用户画像");
        profileEditor.getStyleClass().add("transparent-text");
        profileEditor.setFont(Font.font(UI_FONT, 13));
        profileEditor.setWrapText(true);
        profileEditor.setPrefRowCount(5);
        profileEditor.setPrefHeight(120);
        Button save = new Button("保存画像");
        save.setFont(Font.font(UI_FONT, 13));
        save.getStyleClass().addAll("art-button", "panel-action-button");
        save.setOnAction(event -> {
            String profileText = profileEditor.getText();
            runAsync(() -> {
                String saved = apiClient.saveProfile(profileText);
                Platform.runLater(() -> {
                    profileEditor.setText(saved);
                    appendAssistant("用户画像已更新。");
                });
            });
        });
        profilePanel.getChildren().addAll(title, profileEditor, buttonWithCaption(save, "保存"));
        runAsync(() -> {
            String profile = apiClient.profile();
            Platform.runLater(() -> profileEditor.setText(profile));
        });
    }

    private void filePanelVisible() {
        TextField sourceRoot = new TextField();
        sourceRoot.setPromptText("例如 D:\\papers 或 /mnt/d/papers");
        TextField extensions = new TextField("pdf");
        TextArea instruction = new TextArea("请找出与密钥生成有关的 PDF 文献并按主题分类");
        instruction.setWrapText(true);
        instruction.setPrefRowCount(4);
        Label allowedRoots = new Label("当前白名单：加载中...");
        allowedRoots.setWrapText(true);
        Button replaceAllowedRoots = new Button("使用当前源目录覆盖白名单");
        replaceAllowedRoots.setOnAction(event -> {
            String path = sourceRoot.getText().trim();
            if (path.isEmpty()) {
                showError("请先填写源目录");
                return;
            }
            runAsync(() -> {
                FileOrganizer.AllowedRootsResult result = apiClient.replaceAllowedRoots(List.of(path));
                Platform.runLater(() -> {
                    allowedRoots.setText("当前白名单：" + formatAllowedRoots(result));
                    appendAssistant("文件整理白名单已更新为：" + formatAllowedRoots(result));
                });
            });
        });
        VBox content = new VBox(
                8,
                allowedRoots,
                replaceAllowedRoots,
                new Label("源目录"),
                sourceRoot,
                new Label("扩展名"),
                extensions,
                new Label("分类要求"),
                instruction
        );
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("整理文件");
        dialog.setHeaderText("先生成预览，确认后才复制归档");
        dialog.getDialogPane().setContent(content);
        runAsync(() -> {
            FileOrganizer.AllowedRootsResult result = apiClient.allowedRoots();
            Platform.runLater(() -> allowedRoots.setText("当前白名单：" + formatAllowedRoots(result)));
        });
        dialog.showAndWait()
                .filter(buttonType -> buttonType == ButtonType.OK)
                .ifPresent(buttonType -> previewAndConfirmFiles(sourceRoot.getText(), extensions.getText(), instruction.getText()));
    }

    private void sendCurrentMessage() {
        String message = chatInput.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        chatInput.clear();
        appendUser(message);
        setSprite(thinkingSprite);
        runAsync(() -> {
            ChatResponse response = apiClient.chat(message);
            Platform.runLater(() -> {
                appendAssistant(response.reply());
                refreshScheduleAfterChat(response.reply());
                setSprite(speakingSprite);
                PauseTransition pause = new PauseTransition(Duration.seconds(1.0));
                pause.setOnFinished(done -> setSprite(defaultSprite));
                pause.play();
            });
        });
    }

    private void addSchedule(TextField input) {
        String title = input.getText().trim();
        if (title.isEmpty()) {
            return;
        }
        runAsync(() -> {
            List<ScheduleItem> items = apiClient.addTodaySchedule(title);
            Platform.runLater(() -> {
                input.clear();
                renderSchedule(items);
            });
        });
    }

    private void refreshScheduleAfterChat(String reply) {
        if (reply == null) {
            return;
        }
        if (reply.contains("日程")) {
            refreshScheduleFromBackend();
        }
    }

    private void previewAndConfirmFiles(String sourceRoot, String extensions, String instruction) {
        String path = sourceRoot.trim();
        String suffixes = extensions.trim();
        String request = instruction.trim();
        if (path.isEmpty() || suffixes.isEmpty() || request.isEmpty()) {
            return;
        }
        runAsync(() -> {
            FileOrganizer.PreviewResult preview = apiClient.previewFileOrganization(path, suffixes, request);
            Platform.runLater(() -> showOrganizationPreview(preview));
        });
    }

    private void showOrganizationPreview(FileOrganizer.PreviewResult preview) {
        TextArea text = new TextArea(summarizePreview(preview));
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefRowCount(14);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("确认整理");
        dialog.setHeaderText("命中 " + preview.candidates().size() + " 个文件，确认后复制归档并生成目录.xlsx");
        dialog.getDialogPane().setContent(text);
        dialog.showAndWait()
                .filter(buttonType -> buttonType == ButtonType.OK)
                .ifPresent(buttonType -> runAsync(() -> {
                    FileOrganizer.ConfirmResult result = apiClient.confirmFileOrganization(preview.previewId());
                    Platform.runLater(() -> appendAssistant(summarizeConfirm(result)));
                }));
    }

    private String summarizePreview(FileOrganizer.PreviewResult preview) {
        StringBuilder builder = new StringBuilder();
        builder.append("扫描目录：").append(preview.sourceRoot()).append("\n");
        builder.append("分类要求：").append(preview.instruction()).append("\n");
        builder.append("扫描文件：").append(preview.scannedCount()).append(" 个\n");
        builder.append("命中文献：").append(preview.candidates().size()).append(" 个\n\n");
        for (FileOrganizer.Candidate candidate : preview.candidates()) {
            builder.append("[").append(candidate.category()).append("] ")
                    .append(candidate.fileName()).append("\n")
                    .append(candidate.summary()).append("\n")
                    .append(candidate.reason()).append("\n\n");
        }
        if (!preview.failures().isEmpty()) {
            builder.append("失败项：").append(preview.failures().size()).append(" 个\n");
        }
        return builder.toString();
    }

    private String summarizeConfirm(FileOrganizer.ConfirmResult result) {
        return "整理完成：复制 " + result.copiedCount()
                + " 个文件，输出目录：" + result.outputRoot()
                + "，目录表：" + result.reportPath();
    }

    private String formatAllowedRoots(FileOrganizer.AllowedRootsResult result) {
        if (result.roots().isEmpty()) {
            return "未配置";
        }
        return String.join("；", result.roots());
    }

    private void appendUser(String text) {
        chatLog.appendText("你：" + text + "\n");
    }

    private void appendAssistant(String text) {
        chatLog.appendText(CHARACTER_NAME + "：" + text + "\n");
    }

    private void scheduleNextPrompt() {
        int seconds = 180 + random.nextInt(420);
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
            pause.setOnFinished(event -> runAsync(() -> {
                ChatResponse response = apiClient.chat("请生成一句简短主动提醒。");
                Platform.runLater(() -> {
                    appendAssistant(response.reply());
                    scheduleNextPrompt();
                });
            }));
            pause.play();
        });
    }

    private void runAsync(Runnable task) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Platform.runLater(() -> showError(e.getMessage()));
            }
        }, "desktop-pet-client-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        appendAssistant("后台任务失败：" + message);
        setSprite(errorSprite);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("操作失败");
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "未知错误" : message);
        alert.show();
    }

    private void setSprite(Image image) {
        petSprite.setImage(image == null ? defaultSprite : image);
    }

    private Label panelTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("panel-title");
        title.setFont(Font.font(UI_FONT, 13));
        return title;
    }

    private VBox buttonWithCaption(Button button, String text) {
        return buttonWithCaption(button, text, null);
    }

    private VBox buttonWithCaption(Button button, String text, String extraStyleClass) {
        button.setText("");
        Label caption = new Label(text);
        caption.getStyleClass().add("button-caption");
        caption.setFont(Font.font(UI_FONT, 12));
        caption.setOnMouseClicked(event -> {
            button.fire();
            event.consume();
        });
        VBox box = new VBox(2, button, caption);
        box.getStyleClass().add("captioned-button");
        if (extraStyleClass != null) {
            box.getStyleClass().add(extraStyleClass);
        }
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Image loadSprite(String filename) {
        return loadSprite(filename, null);
    }

    private Image loadSprite(String filename, String fallbackFilename) {
        var resource = PetWindow.class.getResource(SPRITE_BASE + filename);
        if (resource == null && fallbackFilename != null) {
            resource = PetWindow.class.getResource(SPRITE_BASE + fallbackFilename);
        }
        if (resource == null) {
            System.err.println("Sprite not found: " + filename);
            return null;
        }
        Image image = new Image(resource.toExternalForm(), true);
        image.errorProperty().addListener((observable, wasError, isError) -> {
            if (isError) {
                System.err.println("Sprite failed to load: " + filename + " - " + image.getException());
            }
        });
        return image;
    }

    private void enableDrag(Parent dragHandle) {
        dragHandle.setOnMousePressed(event -> {
            dragOffsetX = stage.getX() - event.getScreenX();
            dragOffsetY = stage.getY() - event.getScreenY();
            event.consume();
        });
        dragHandle.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() + dragOffsetX);
            stage.setY(event.getScreenY() + dragOffsetY);
            event.consume();
        });
    }

    private void bindVisibilityToLayout(Parent node) {
        node.managedProperty().bind(node.visibleProperty());
    }
}
