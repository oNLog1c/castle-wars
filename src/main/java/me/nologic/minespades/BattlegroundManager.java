package me.nologic.minespades;

import me.nologic.minespades.battleground.*;
import me.nologic.minespades.battleground.editor.BattlegroundEditor;
import me.nologic.minespades.battleground.editor.loadout.BattlegroundLoadout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public class BattlegroundManager {

    private final HashMap<String, Battleground> enabledBattlegrounds;
    private final HashMap<String, Multiground>  enabledMultigrounds;

    private final BattlegroundEditor editor;
    private final BattlegroundLoader loader;

    private final Minespades plugin;

    public BattlegroundManager(Minespades plugin) {
        this.plugin = plugin;
        this.editor = new BattlegroundEditor();
        this.loader = new BattlegroundLoader(plugin);
        this.enabledBattlegrounds = new HashMap<>();
        this.enabledMultigrounds = new HashMap<>();
    }

    public BattlegroundEditor getEditor() {
        return editor;
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

    public void reset(Battleground battleground) {
        disable(battleground.getBattlegroundName());
        enable(battleground.getBattlegroundName());
    }

    public void launchMultiground(String name, List<String> battlegrounds) {
        Multiground multiground = new Multiground(name);
        battlegrounds.forEach(multiground::add);
        this.enabledMultigrounds.put(name, multiground);
        multiground.launchRandomly();
    }

    public void enable(String name) {
        this.enable(name, null);
    }

    public Battleground enable(String name, Multiground multiground) {
        Battleground battleground = this.load(name);
        battleground.setEnabled(true);
        battleground.setMultiground(multiground);
        this.enabledBattlegrounds.put(battleground.getBattlegroundName(), battleground);
        this.callToArms(battleground.getPreference(BattlegroundPreferences.Preference.JOIN_ONLY_FROM_MULTIGROUND) ? battleground.getMultiground().getName() : battleground.getBattlegroundName());
        Bukkit.getServer().getPluginManager().registerEvents(battleground.getPreferences(), plugin);
        List<String> saved = plugin.getConfig().getStringList("Battlegrounds");
        saved.add(name);
        plugin.getConfig().set("Battlegrounds", name);
        plugin.saveConfig();
        return battleground;
    }

    /* Загрузка арены из датабазы. */
    private Battleground load(String name) {
        return loader.load(name);
    }

    public void disable(Battleground battleground) {
        this.disable(battleground.getBattlegroundName());
    }

    public void disable(String battlegroundName) {
        Battleground battleground = this.getBattlegroundByName(battlegroundName);

        // Кикаем всех игроков с арены
        for (BattlegroundPlayer player : battleground.getPlayers()) {
            player.removeSidebar();
            plugin.getGameMaster().getPlayerManager().getPlayersInGame().remove(player);
            player.getBattleground().kick(player);
            plugin.getGameMaster().getPlayerManager().load(player.getBukkitPlayer());
            Player p = player.getBukkitPlayer();
            p.displayName(p.name().color(NamedTextColor.WHITE));
            p.playerListName(p.name().color(NamedTextColor.WHITE));
        }

        battleground.setEnabled(false);
        HandlerList.unregisterAll(battleground.getPreferences());

        // Останавливаем все BukkitRunnable из правил автовыдачи
        for (BattlegroundTeam team : battleground.getTeams()) {

            if (team.getFlag() != null)
                team.getFlag().getTick().cancel();

            for (TeamRespawnPoint respawnPoint : team.getRespawnPoints()) {
                respawnPoint.getTick().cancel();
            }

            for (BattlegroundLoadout loadout : team.getLoadouts()) {
                for (BukkitRunnable task : loadout.getTasks()) {
                    task.cancel();
                }
            }
        }
    }

    /**
     * Объявляет в чате о готовности арены, а так же отправляет звук.
     * */
    private void callToArms(String name) {
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_7, 1F, 1F));
        Bukkit.broadcast(Component.text("На арене " + StringUtils.capitalize(name) + " начинается новая битва!").color(TextColor.color(172, 127, 67)));
        Bukkit.broadcast(Component.text("Кликни, чтобы подключиться: ").color(TextColor.color(172, 127, 67))
                .append(Component.text("/ms join " + StringUtils.capitalize(name)).clickEvent(ClickEvent.suggestCommand("/ms join " + name)).color(TextColor.color(187, 151, 60)).decorate(TextDecoration.UNDERLINED)));
    }

    public boolean isBattlegroundExist(String battlegroundName) {
        return new File(plugin.getDataFolder() + "/battlegrounds/" + battlegroundName + ".db").exists();
    }

    public Battleground getBattlegroundByName(String name) {
        return this.enabledBattlegrounds.get(name);
    }

}