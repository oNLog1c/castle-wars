package me.nologic.castlewars.battleground.editor.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import me.nologic.castlewars.CastleWars;
import me.nologic.castlewars.battleground.util.BattlegroundDataDriver;
import me.nologic.castlewars.battleground.util.DatabaseTableHelper;
import me.nologic.castlewars.util.BossBar;
import me.nologic.minority.MinorityFeature;
import me.nologic.minority.annotations.Translatable;
import me.nologic.minority.annotations.TranslationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.util.BoundingBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

@Translatable
public class SaveVolumeTask extends BaseEditorTask implements Runnable, MinorityFeature {

    private final BossBar    completeBar;
    private final String     battlegroundName;
    private final Location[] corners;

    public SaveVolumeTask(final String battlegroundName, Player player, Location[] corners) {
        super(player);
        this.init(this, this.getClass(), CastleWars.getInstance());
        this.battlegroundName = battlegroundName;
        this.completeBar      = BossBar.bossBar(battlegroundName, 0.0F, BarColor.YELLOW, BarStyle.SEGMENTED_20);
        this.corners          = corners;
    }

    @SneakyThrows
    public void run() {

        if (corners[0] == null || corners[1] == null) {
            player.sendMessage(selectTwoCornersMessage);
            return;
        }

        editor.setSaving(true);
        completeBar.addViewer(player).visible(true);

        // Удаляем старое содержимое (возможно имеет смысл сохранять это куда-нибудь, но это не такая уж и обязательная фича, да и лень мне)
        final BattlegroundDataDriver driver = new BattlegroundDataDriver().connect(battlegroundName);
        driver.executeUpdate("DELETE FROM volume;");
        driver.executeUpdate("DELETE FROM corners;");
        driver.executeUpdate("VACUUM");

        final float volume = (float) BoundingBox.of(corners[0], corners[1]).getVolume();
        final float step = 1.0f / volume;

        final int minX = Math.min(corners[0].getBlockX(), corners[1].getBlockX()), maxX = Math.max(corners[0].getBlockX(), corners[1].getBlockX()), minY = Math.min(corners[0].getBlockY(), corners[1].getBlockY()), maxY = Math.max(corners[0].getBlockY(), corners[1].getBlockY()), minZ = Math.min(corners[0].getBlockZ(), corners[1].getBlockZ()), maxZ = Math.max(corners[0].getBlockZ(), corners[1].getBlockZ());

        int i = 0;
        int b = 0;
        long startTime = System.currentTimeMillis();
        Connection connection = super.connect();

        // Сохранение углов
        PreparedStatement saveCorners = connection.prepareStatement(DatabaseTableHelper.CORNERS.getInsertStatement());
        saveCorners.setInt(1, minX);
        saveCorners.setInt(2, minY);
        saveCorners.setInt(3, minZ);
        saveCorners.setInt(4, maxX);
        saveCorners.setInt(5, maxY);
        saveCorners.setInt(6, maxZ);
        saveCorners.executeUpdate();

        PreparedStatement bSt = connection.prepareStatement("INSERT INTO volume(x, y, z, material, data, content) VALUES(?,?,?,?,?,?);");
        connection.setAutoCommit(false);

        final int size = 10000;
        World world = player.getWorld();

        this.completeBar.title(String.format(saveStateBarTitle, battlegroundName));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    bSt.setInt(1, x);
                    bSt.setInt(2, y);
                    bSt.setInt(3, z);

                    Block block = world.getBlockAt(x, y, z);
                    if (++b % size == 0) {
                        final double progress = this.completeBar.progress() + step * size;
                        this.completeBar.progress(Math.min(progress, 1.0f));
                    }

                    if (block.getType().isAir()) continue;

                    bSt.setString(4, block.getType().toString());
                    bSt.setString(5, block.getBlockData().getAsString());

                    // Сохранение тайл-энтитей оставляем на потом
                    bSt.setString(6, null);
                    bSt.addBatch();

                    if (++i % size == 0) {
                        bSt.executeBatch();
                    }
                }
            }
        }

        // Сохраняем остатки и коммитим
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
                        if (block.getState() instanceof Container
                                || block.getState() instanceof Sign
                                || block.getState() instanceof Skull
                                || block.getState() instanceof Jukebox) {
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
                tSt.setString(1,  Bukkit.getServer().getScheduler().callSyncMethod(plugin, () -> this.save(container.getInventory())).get());
                tSt.setInt(2, container.getX());
                tSt.setInt(3, container.getY());
                tSt.setInt(4, container.getZ());
                tSt.addBatch();
            } else if (state instanceof Sign sign) {
                tSt.setString(1, Bukkit.getServer().getScheduler().callSyncMethod(plugin, () -> this.save(sign)).get());
                tSt.setInt(2, sign.getX());
                tSt.setInt(3, sign.getY());
                tSt.setInt(4, sign.getZ());
                tSt.addBatch();
            } else if (state instanceof Skull skull) {
                tSt.setString(1, Bukkit.getServer().getScheduler().callSyncMethod(plugin, () -> this.save(skull)).get());
                tSt.setInt(2, skull.getX());
                tSt.setInt(3, skull.getY());
                tSt.setInt(4, skull.getZ());
                tSt.addBatch();
            } else if (state instanceof Jukebox jukebox) {
                tSt.setString(1, Bukkit.getServer().getScheduler().callSyncMethod(plugin, () -> this.save(jukebox)).get());
                tSt.setInt(2, jukebox.getX());
                tSt.setInt(3, jukebox.getY());
                tSt.setInt(4, jukebox.getZ());
                tSt.addBatch();
            }
        }

        tSt.executeBatch();
        connection.commit();
        connection.close();
        long totalTime = System.currentTimeMillis() - startTime;
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 0F);
        player.sendMessage(String.format(volumeSavedMessage, i, totalTime / 1000));
        completeBar.cleanViewers().visible(false);
        editor.editSession(player).resetCorners();
        editor.setSaving(false);
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
                String itemData = serializeItemStack(item);
                jitem.addProperty("data", itemData);
                items.add(jitem);
            }
        }
        obj.add("items", items);
        return obj.toString();
    }

    @SneakyThrows
    private String save(Jukebox jukebox) {

        final ItemStack record     = jukebox.getRecord();
        final String    serialized = serializeItemStack(record);

        JsonObject obj = new JsonObject();
        obj.addProperty("record", serialized);
        return obj.toString();
    }

    private String save(Sign sign) {

        StringBuilder lines = new StringBuilder();
        for (String line : sign.getLines()) lines.append(line).append("\n");

        JsonObject obj = new JsonObject();
            obj.addProperty("content", lines.toString());
            obj.addProperty("glow", sign.isGlowingText());
            obj.addProperty("color", Objects.requireNonNull(sign.getColor()).name());

        return obj.toString();
    }

    private String save(final Skull skull) {

        final PlayerProfile profile = skull.getOwnerProfile();

        String texture = "none";

        if (profile != null && profile.getTextures().getSkin() != null) {
            texture = profile.getTextures().getSkin().toString();
        }

        JsonObject obj = new JsonObject();
            obj.addProperty("texture", texture);

        return obj.toString();
    }

    @TranslationKey(section = "editor-info-messages", name = "save-state-boss-bar-title", value = "#fcd617%s&7: &eSaving blocks...")
    private String saveStateBarTitle;

    @TranslationKey(section = "editor-info-messages", name = "battleground-volume-successfully-saved", value = "&7Successfully saved the volume. &8(&33%db.&8, &3%ds.&8)")
    private String volumeSavedMessage;

    @TranslationKey(section = "editor-error-messages", name = "select-two-corners", value = "&cYou must select two corners before saving the battleground volume. Use &lRMB/LMB &cwhile holding a &e&lgolden sword &cin your main hand.")
    private String selectTwoCornersMessage;

}