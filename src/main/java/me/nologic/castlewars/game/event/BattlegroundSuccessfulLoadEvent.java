package me.nologic.castlewars.game.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nologic.castlewars.battleground.Battleground;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public class BattlegroundSuccessfulLoadEvent extends Event {

    private final Battleground battleground;

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
    private static final HandlerList HANDLERS = new HandlerList();

}