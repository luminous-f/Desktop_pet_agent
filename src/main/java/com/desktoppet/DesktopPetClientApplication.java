package com.desktoppet;

import com.desktoppet.client.BackendApiClient;
import com.desktoppet.config.AppConfig;
import com.desktoppet.ui.PetWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class DesktopPetClientApplication extends Application {
    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load();
        BackendApiClient apiClient = new BackendApiClient(config.backendBaseUrl());
        PetWindow petWindow = new PetWindow(stage, apiClient);
        boolean transparentWindow = Boolean.parseBoolean(System.getenv().getOrDefault("DESKTOP_PET_TRANSPARENT", "true"));
        boolean alwaysOnTop = Boolean.parseBoolean(System.getenv().getOrDefault("DESKTOP_PET_ALWAYS_ON_TOP", "false"));

        Scene scene = new Scene(petWindow.view(), 980, 640);
        scene.setFill(transparentWindow ? Color.TRANSPARENT : Color.WHITE);
        scene.getStylesheets().add(DesktopPetClientApplication.class.getResource("/styles/app.css").toExternalForm());

        if (transparentWindow) {
            stage.initStyle(StageStyle.TRANSPARENT);
        }
        stage.setAlwaysOnTop(alwaysOnTop);
        stage.setTitle("Desktop Pet Agent");
        stage.setScene(scene);
        stage.show();

        petWindow.startProactiveScheduler();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
