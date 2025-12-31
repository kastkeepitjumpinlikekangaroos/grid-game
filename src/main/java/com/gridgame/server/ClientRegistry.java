package com.gridgame.server;

import com.gridgame.common.Constants;
import com.gridgame.common.model.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for tracking connected clients.
 * Manages player state and heartbeat monitoring.
 */
public class ClientRegistry {
    private final ConcurrentHashMap<UUID, Player> players;

    public ClientRegistry() {
        this.players = new ConcurrentHashMap<>();
    }

    /**
     * Add or update a player in the registry.
     */
    public void add(Player player) {
        players.put(player.getId(), player);
    }

    /**
     * Remove a player from the registry.
     */
    public void remove(UUID playerId) {
        players.remove(playerId);
    }

    /**
     * Get a player by ID.
     */
    public Player get(UUID playerId) {
        return players.get(playerId);
    }

    /**
     * Get all players in the registry.
     */
    public List<Player> getAll() {
        return new ArrayList<>(players.values());
    }

    /**
     * Check if a player exists in the registry.
     */
    public boolean contains(UUID playerId) {
        return players.containsKey(playerId);
    }

    /**
     * Update the heartbeat timestamp for a player.
     */
    public void updateHeartbeat(UUID playerId) {
        Player player = players.get(playerId);
        if (player != null) {
            player.updateHeartbeat();
        }
    }

    /**
     * Get list of player IDs that have timed out.
     * A player is considered timed out if their last update was more than
     * CLIENT_TIMEOUT_MS milliseconds ago.
     */
    public List<UUID> getTimedOutClients() {
        long now = System.currentTimeMillis();
        long timeout = Constants.CLIENT_TIMEOUT_MS;
        List<UUID> timedOut = new ArrayList<>();

        for (Player player : players.values()) {
            if (now - player.getLastUpdateTime() > timeout) {
                timedOut.add(player.getId());
            }
        }

        return timedOut;
    }

    /**
     * Get the number of connected players.
     */
    public int size() {
        return players.size();
    }

    /**
     * Clear all players from the registry.
     */
    public void clear() {
        players.clear();
    }
}
