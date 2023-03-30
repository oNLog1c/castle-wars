package me.nologic.minespades.battleground.editor.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import me.nologic.minespades.Minespades;
import me.nologic.minespades.battleground.Table;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class SaveVolumeTask extends BaseEditorTask implements Callable<Boolean> {

    private final Location[] corners;

    public SaveVolumeTask(Minespades plugin, Player player, String name, Location[] corners) {
        super(plugin, player, name);
        this.corners = corners;
    }

    @SneakyThrows
    public Boolean call() {

        if (corners[0] == null || corners[1] == null) {
            player.sendMessage("§4Необходимо указать два угла кубоида.");
            return false;
        }

        final int minX = Math.min(corners[0].getBlockX(), corners[1].getBlockX()), maxX = Math.max(corners[0].getBlockX(), corners[1].getBlockX()), minY = Math.min(corners[0].getBlockY(), corners[1].getBlockY()), maxY = Math.max(corners[0].getBlockY(), corners[1].getBlockY()), minZ = Math.min(corners[0].getBlockZ(), corners[1].getBlockZ()), maxZ = Math.max(corners[0].getBlockZ(), corners[1].getBlockZ());

        int i = 0;
        long startTime = System.currentTimeMillis();
        Connection connection = super.connect();

        // Сохранение углов
        PreparedStatement saveCorners = connection.prepareStatement(Table.CORNERS.getInsertStatement());
        saveCorners.setInt(1, minX);
        saveCorners.setInt(2, minY);
        saveCorners.setInt(3, minZ);
        saveCorners.setInt(4, maxX);
        saveCorners.setInt(5, maxY);
        saveCorners.setInt(6, maxZ);
        saveCorners.executeUpdate();

        PreparedStatement bSt = connection.prepareStatement("INSERT INTO volume(x, y, z, material, data, content) VALUES(?,?,?,?,?,?);");
        connection.setAutoCommit(false);

        // Сохранение блоков
        World world = player.getWorld();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    bSt.setInt(1, x);
                    bSt.setInt(2, y);
                    bSt.setInt(3, z);

                    Block block = world.getBlockAt(x, y, z);

                    if (block.getType().isAir()) continue;

                    bSt.setString(4, block.getType().toString());
                    bSt.setString(5, block.getBlockData().getAsString());

                    bSt.setString(6, null);
                    bSt.addBatch();

                    if (++i % 5000 == 0) {
                        bSt.executeBatch();
                    }
                }
            }
        }

        bSt.executeBatch();
        connection.commit();

        // Сохранение тайлов
        Future<List<BlockState>> future = Bukkit.getServer().getScheduler().callSyncMethod(plugin, () -> {
            List<BlockState> tiles = new ArrayList<>();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType().isAir()) continue;
                        if (block.getState() instanceof Container || block.getState() instanceof Sign) {
                            tiles.add(block.getState());
                        }
                    }
                }
            }

            return tiles;
        });

        // Добавление тайлов в датабазу
        PreparedStatement tSt = connection.prepareStatement("UPDATE volume SET content = ? WHERE x = ? AND y = ? AND z = ?;");
        for (BlockState state : future.get()) {
            if (state instanceof Container container) {
                tSt.setString(1, this.save(container.getInventory()));
                tSt.setInt(2, container.getX());
                tSt.setInt(3, container.getY());
                tSt.setInt(4, container.getZ());
                tSt.addBatch();
            } else if (state instanceof Sign sign) {
                tSt.setString(1, this.save(sign));
                tSt.setInt(2, sign.getX());
                tSt.setInt(3, sign.getY());
                tSt.setInt(4, sign.getZ());
                tSt.addBatch();
            }

        }

        tSt.executeBatch();
        connection.commit();
        connection.close();
        long totalTime = System.currentTimeMillis() - startTime;
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 0F);
        player.sendMessage(String.format("§4§l[!] §7Карта успешно сохранена. §8(§33%dб.§8, §3%dс.§8)", i, totalTime / 1000));
        return true;
    }

    @SneakyThrows
    private String save(Inventory inventory) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", inventory.getType().name());
        obj.addProperty("size", inventory.getSize());

        JsonArray items = new JsonArray();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null) {
                JsonObject jitem = new JsonObject();
                jitem.addProperty("slot", i);
                String itemData = itemStackToBase64(item);
                jitem.addProperty("data", itemData);
                items.add(jitem);
            }
        }
        obj.add("items", items);
        return obj.toString();
    }

    private String save(Sign sign) {
        JsonObject obj = new JsonObject(); // TODO: sign.lines() or sign.getLines()?
        obj.addProperty("content", StringUtils.join(sign.lines(), "\n"));
        obj.addProperty("glow", sign.isGlowingText());
        obj.addProperty("color", Objects.requireNonNull(sign.getColor()).name());
        return obj.toString();
    }

}