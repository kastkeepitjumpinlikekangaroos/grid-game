package com.gridgame.client.input;

import com.gridgame.client.GameClient;
import com.gridgame.common.Constants;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles keyboard input for player movement.
 * Implements rate limiting to prevent spam (max 10 moves per second).
 */
public class KeyboardHandler implements EventHandler<KeyEvent> {
    private final GameClient client;
    private final Set<KeyCode> pressedKeys;
    private long lastMoveTime;

    public KeyboardHandler(GameClient client) {
        this.client = client;
        this.pressedKeys = new HashSet<>();
        this.lastMoveTime = 0;
    }

    @Override
    public void handle(KeyEvent event) {
        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            pressedKeys.add(event.getCode());
            processMovement();
        } else if (event.getEventType() == KeyEvent.KEY_RELEASED) {
            pressedKeys.remove(event.getCode());
        }
    }

    /**
     * Process movement based on currently pressed keys.
     * Rate limited to max 10 moves per second (100ms delay).
     */
    private void processMovement() {
        long now = System.currentTimeMillis();

        // Rate limiting
        if (now - lastMoveTime < Constants.MOVE_RATE_LIMIT_MS) {
            return;
        }

        int dx = 0;
        int dy = 0;

        // Calculate movement delta
        if (pressedKeys.contains(KeyCode.W)) {
            dy = -1;
        }
        if (pressedKeys.contains(KeyCode.S)) {
            dy = 1;
        }
        if (pressedKeys.contains(KeyCode.A)) {
            dx = -1;
        }
        if (pressedKeys.contains(KeyCode.D)) {
            dx = 1;
        }

        // Move player if there's any movement
        if (dx != 0 || dy != 0) {
            client.movePlayer(dx, dy);
            lastMoveTime = now;
        }
    }
}
