package me.nologic.castlewars.util;

import lombok.Getter;
import me.nologic.castlewars.CastleWars;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

@Getter
public class VaultEconomyProvider {

    @Nullable
    private Economy economy;

    public VaultEconomyProvider() {
        final CastleWars plugin = CastleWars.getInstance();

        RegisteredServiceProvider<Economy> rsp;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null || (rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class)) == null) {
            plugin.getLogger().info("Vault not found.");
            return;
        }

        economy = rsp.getProvider();
    }

}