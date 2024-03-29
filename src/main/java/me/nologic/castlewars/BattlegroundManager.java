package me.nologic.castlewars;

import lombok.Getter;
import me.nologic.castlewars.battleground.Battleground;
import me.nologic.castlewars.battleground.BattlegroundPlayer;
import me.nologic.castlewars.battleground.BattlegroundTeam;
import me.nologic.castlewars.battleground.Multiground;
import me.nologic.castlewars.battleground.builder.BattlegroundBuilder;
import me.nologic.castlewars.battleground.editor.BattlegroundEditor;
import me.nologic.castlewars.battleground.editor.loadout.BattlegroundLoadout;
import me.nologic.castlewars.battleground.util.BattlegroundValidator;
import me.nologic.castlewars.util.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

public class BattlegroundManager {

    private final HashMap<String, Battleground> enabledBattlegrounds;
    private final HashMap<String, Multiground>  enabledMultigrounds;

    private final BattlegroundBuilder builder;
    private final @Getter BattlegroundValidator validator;
    private final @Getter BattlegroundEditor    editor;

    private final VaultEconomyProvider vault;

    private final CastleWars plugin;

    public BattlegroundManager(CastleWars plugin) {
        this.plugin = plugin;
        this.validator = new BattlegroundValidator();
        this.editor = new BattlegroundEditor();
        this.builder = new BattlegroundBuilder();
        this.enabledBattlegrounds = new HashMap<>();
        this.enabledMultigrounds = new HashMap<>();
        this.vault = new VaultEconomyProvider();
    }

    public List<Battleground> getLoadedBattlegrounds() {
        return enabledBattlegrounds.values().stream().toList();
    }

    public List<Multiground> getMultigrounds() {
        return this.enabledMultigrounds.values().stream().toList();
    }

    @Nullable
    public Multiground getMultiground(String name) {
        return this.enabledMultigrounds.get(name);
    }

    public void resetBattleground(final Battleground battleground) {
        this.disable(battleground);
        this.enable(battleground.getBattlegroundName());
    }

    public void launchMultiground(String name, List<String> battlegrounds) {
        Multiground multiground = new Multiground(name);
        battlegrounds.forEach(multiground::add);
        this.enabledMultigrounds.put(name, multiground);
        multiground.launchRandomly();
    }

    /* Returns true if not null */
    public boolean enable(String name) {
        return this.enable(name, null) != null;
    }

    public Battleground enable(String name, @Nullable Multiground multiground) {
        Battleground battleground = this.builder.build(name, multiground);

        // Если BattlegroundBuilder вернул null, то это значит лишь одно: арена запускается
        // вручную, а не через мультиграунд, при этом являясь его частью. (IS_MULTIGROUND)
        if (battleground == null) return null;

        battleground.setEnabled(true);
        battleground.setMultiground(multiground);

        this.enabledBattlegrounds.put(battleground.getBattlegroundName(), battleground);
        Bukkit.getServer().getPluginManager().registerEvents(battleground.getPreferences(), plugin);

        return battleground;
    }

    public void disable(final Battleground battleground) {

        battleground.setEnabled(false);

        plugin.getGameMaster().getObjectManager().getFlags().removeIf(flag -> flag.getBattleground().equals(battleground));

        // Кикаем всех игроков с арены
        for (final BattlegroundPlayer player : battleground.getBattlegroundPlayers()) {
            player.disconnect();
        }

        HandlerList.unregisterAll(battleground.getPreferences());
        this.enabledBattlegrounds.remove(battleground.getBattlegroundName());

        // Останавливаем все BukkitRunnable из правил автовыдачи
        for (final BattlegroundTeam team : battleground.getTeams()) {

            for (BattlegroundLoadout loadout : team.getLoadouts()) {
                for (BukkitRunnable task : loadout.getTasks()) {
                    task.cancel();
                }
            }
        }

    }

    @Nullable
    public Economy getEconomyManager() {
        return this.vault.getEconomy();
    }

    @Nullable
    public Battleground getBattlegroundByName(String name) {
        return this.enabledBattlegrounds.get(name);
    }

}