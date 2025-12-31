package com.gridgame.client;

import com.gridgame.client.input.KeyboardHandler;
import com.gridgame.client.ui.GameCanvas;
import com.gridgame.common.Constants;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

/**
 * Main entry point for the JavaFX client application.
 */
public class ClientMain extends Application {

    private GameClient client;
    private GameCanvas canvas;

    @Override
    public void start(Stage primaryStage) {
        // Parse command line parameters
        Parameters params = getParameters();
        Map<String, String> named = params.getNamed();
        List<String> unnamed = params.getUnnamed();

        String serverHost = named.getOrDefault("host", "localhost");
        int serverPort = Integer.parseInt(named.getOrDefault("port", String.valueOf(Constants.SERVER_PORT)));

        // Create game client
        client = new GameClient(serverHost, serverPort);

        // Create canvas
        canvas = new GameCanvas(client);

        // Create scene
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX);

        // Setup keyboard handler for both KEY_PRESSED and KEY_RELEASED
        KeyboardHandler keyHandler = new KeyboardHandler(client);
        scene.setOnKeyPressed(keyHandler);
        scene.setOnKeyReleased(keyHandler);

        // Setup stage
        primaryStage.setTitle("Grid Game - Multiplayer 2D");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // Connect to server
        client.connect();

        // Start rendering loop
        AnimationTimer renderLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                canvas.render();
            }
        };
        renderLoop.start();

        // Shutdown hook
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Closing application...");
            client.disconnect();
            renderLoop.stop();
        });

        System.out.println("Client started successfully!");
        System.out.println("Use WASD to move your character.");
    }

    @Override
    public void stop() {
        if (client != null) {
            client.disconnect();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
