package me.nologic.minespades.battleground.editor.loadout;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nologic.minespades.BattlegroundManager;
import me.nologic.minespades.Minespades;
import me.nologic.minespades.battleground.Battleground;
import me.nologic.minespades.battleground.BattlegroundPlayer;
import me.nologic.minespades.battleground.BattlegroundTeam;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor @Getter
public class Loadout {

    private final String    name;
    private final Inventory inventory;
    private final BattlegroundTeam team;

    private List<LoadoutSupplyRule> supplyRules;

    public void addSupplyRule(LoadoutSupplyRule rule) {
        this.supplyRules.add(rule);
    }

    public void acceptSupplyRules() {
        for (LoadoutSupplyRule rule : this.supplyRules) {
            BukkitRunnable runnable = new BukkitRunnable() {

                @Override
                public void run() {
                    for (BattlegroundPlayer player : Minespades.getPlugin(Minespades.class).getGameMaster().getPlayerManager().getPlayersInGame()) {
                        if (player.getLoadout().getName().equals(rule.targetLoadout())) {
                            ItemStack item = rule.getItemStack();
                            PlayerInventory inventory = player.getPlayer().getInventory();
                            if (inventory.contains(item)) {
                                if (!inventory.contains(item, rule.getMaximum())) {
                                    item.setAmount(rule.getAmount());
                                    inventory.addItem(item);
                                }
                            }
                        }
                    }
                }

            };

            runnable.runTaskTimer(Minespades.getPlugin(Minespades.class),0, rule.interval());
        }
    }

    public static boolean exists(String name) {
        final BattlegroundManager battlegrounder = Minespades.getPlugin(Minespades.class).getBattlegrounder();
        for (Battleground battleground : battlegrounder.getEnabledBattlegrounds()) {
            for (BattlegroundTeam team : battleground.getTeams()) {
                for (Loadout loadout : team.getLoadouts()) {
                    if (Objects.equals(name, loadout.name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}