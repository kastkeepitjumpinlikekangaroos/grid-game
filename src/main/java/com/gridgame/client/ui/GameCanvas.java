package com.gridgame.client.ui;

import com.gridgame.client.GameClient;
import com.gridgame.common.Constants;
import com.gridgame.common.model.Player;
import com.gridgame.common.model.Position;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.UUID;

/**
 * JavaFX Canvas for rendering the game grid with viewport and scrolling.
 * Displays a 25x25 cell viewport of the 500x500 grid, centered on the local player.
 */
public class GameCanvas extends Canvas {
    private final GameClient client;
    private final GraphicsContext gc;

    public GameCanvas(GameClient client) {
        super(Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX);
        this.client = client;
        this.gc = getGraphicsContext2D();
    }

    /**
     * Render the game state.
     */
    public void render() {
        // Clear canvas
        gc.clearRect(0, 0, getWidth(), getHeight());

        Position localPos = client.getLocalPosition();

        // Calculate viewport bounds (centered on local player)
        int viewportX = calculateViewportOffset(localPos.getX());
        int viewportY = calculateViewportOffset(localPos.getY());

        // Draw grid
        drawGrid(viewportX, viewportY);

        // Draw all remote players within viewport
        for (Player player : client.getPlayers().values()) {
            Position pos = player.getPosition();
            if (isInViewport(pos, viewportX, viewportY)) {
                drawPlayer(player, viewportX, viewportY, false);
            }
        }

        // Draw local player (always in viewport)
        drawLocalPlayer(localPos, viewportX, viewportY);

        // Draw viewport coordinates
        drawCoordinates(viewportX, viewportY);
    }

    /**
     * Calculate the viewport offset for a player coordinate.
     * Keeps the player centered in the viewport when possible.
     */
    private int calculateViewportOffset(int playerCoord) {
        int offset = playerCoord - (Constants.VIEWPORT_CELLS / 2);
        // Clamp to valid range [0, GRID_SIZE - VIEWPORT_CELLS]
        return Math.max(0, Math.min(Constants.GRID_SIZE - Constants.VIEWPORT_CELLS, offset));
    }

    /**
     * Check if a position is within the current viewport.
     */
    private boolean isInViewport(Position pos, int viewportX, int viewportY) {
        return pos.getX() >= viewportX &&
               pos.getX() < viewportX + Constants.VIEWPORT_CELLS &&
               pos.getY() >= viewportY &&
               pos.getY() < viewportY + Constants.VIEWPORT_CELLS;
    }

    /**
     * Draw the grid lines.
     */
    private void drawGrid(int viewportX, int viewportY) {
        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(0.5);

        // Vertical lines
        for (int i = 0; i <= Constants.VIEWPORT_CELLS; i++) {
            double x = i * Constants.CELL_SIZE_PX;
            gc.strokeLine(x, 0, x, Constants.VIEWPORT_SIZE_PX);
        }

        // Horizontal lines
        for (int i = 0; i <= Constants.VIEWPORT_CELLS; i++) {
            double y = i * Constants.CELL_SIZE_PX;
            gc.strokeLine(0, y, Constants.VIEWPORT_SIZE_PX, y);
        }
    }

    /**
     * Draw a player square.
     */
    private void drawPlayer(Player player, int viewportX, int viewportY, boolean isLocal) {
        Position pos = player.getPosition();

        // Convert grid position to screen coordinates
        int screenX = (pos.getX() - viewportX) * Constants.CELL_SIZE_PX;
        int screenY = (pos.getY() - viewportY) * Constants.CELL_SIZE_PX;

        // Draw filled square with player's color
        gc.setFill(intToColor(player.getColorRGB()));
        gc.fillRect(screenX + 1, screenY + 1,
                    Constants.CELL_SIZE_PX - 2, Constants.CELL_SIZE_PX - 2);

        // Highlight local player with white border
        if (isLocal) {
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeRect(screenX + 2, screenY + 2,
                         Constants.CELL_SIZE_PX - 4, Constants.CELL_SIZE_PX - 4);
        }
    }

    /**
     * Draw the local player.
     */
    private void drawLocalPlayer(Position pos, int viewportX, int viewportY) {
        int screenX = (pos.getX() - viewportX) * Constants.CELL_SIZE_PX;
        int screenY = (pos.getY() - viewportY) * Constants.CELL_SIZE_PX;

        // Draw filled square with local player's color
        gc.setFill(intToColor(client.getLocalColorRGB()));
        gc.fillRect(screenX + 1, screenY + 1,
                    Constants.CELL_SIZE_PX - 2, Constants.CELL_SIZE_PX - 2);

        // Highlight with white border
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(3);
        gc.strokeRect(screenX + 2, screenY + 2,
                     Constants.CELL_SIZE_PX - 4, Constants.CELL_SIZE_PX - 4);
    }

    /**
     * Draw viewport coordinates in the corner.
     */
    private void drawCoordinates(int viewportX, int viewportY) {
        Position localPos = client.getLocalPosition();
        int playerCount = client.getPlayers().size();

        gc.setFill(Color.BLACK);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);

        String coordText = String.format("Position: (%d, %d)", localPos.getX(), localPos.getY());
        String viewportText = String.format("Viewport: [%d-%d, %d-%d]",
            viewportX, viewportX + Constants.VIEWPORT_CELLS - 1,
            viewportY, viewportY + Constants.VIEWPORT_CELLS - 1);
        String playersText = String.format("Players: %d", playerCount + 1); // +1 for local player

        // Draw text with outline for visibility
        drawOutlinedText(coordText, 10, 20);
        drawOutlinedText(viewportText, 10, 40);
        drawOutlinedText(playersText, 10, 60);
    }

    /**
     * Draw text with a white outline for better visibility.
     */
    private void drawOutlinedText(String text, double x, double y) {
        // Draw outline
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(3);
        gc.strokeText(text, x, y);

        // Draw fill
        gc.setFill(Color.BLACK);
        gc.fillText(text, x, y);
    }

    /**
     * Convert ARGB integer to JavaFX Color.
     */
    private Color intToColor(int argb) {
        int alpha = (argb >> 24) & 0xFF;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return Color.rgb(red, green, blue, alpha / 255.0);
    }
}
