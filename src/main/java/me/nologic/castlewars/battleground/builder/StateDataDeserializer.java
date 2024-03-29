package me.nologic.castlewars.battleground.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.block.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.UUID;

// TODO: Возможно, стоит придумать более подходящее название этому классу.
public class StateDataDeserializer {

    private String data;

    /* Универсальная десериализация. */
    public void deserialize(BlockState state, String data) {
        this.data = data;
        if (state instanceof Sign sign)           deserialize(sign);
        if (state instanceof Container container) deserialize(container);
        if (state instanceof Skull skull)         deserialize(skull);
        if (state instanceof Jukebox jukebox)     deserialize(jukebox);
        // TODO: Добавить поддержку других тайл-энтитей.
    }

    /* Десериализация голов. Возможно, не рабочая. */
    @SneakyThrows
    private void deserialize(Skull skull) {
        JsonObject obj = JsonParser.parseString(data).getAsJsonObject();

        final String texture = obj.get("texture").getAsString();
        if (texture.equals("none")) return;

        final PlayerProfile profile = Bukkit.getServer().createPlayerProfile(UUID.randomUUID());
            profile.getTextures().setSkin(new URL(obj.get("texture").getAsString()));

        skull.setOwnerProfile(profile);
        skull.update(true, false);
    }

    /* Десериализация табличек. */
    private void deserialize(Sign sign) {
        JsonObject obj = JsonParser.parseString(data).getAsJsonObject();
        sign.setGlowingText(obj.get("glow").getAsBoolean());
        String[] lines = obj.get("content").getAsString().split("\n");
        for (int i = 0; i < lines.length; i++)
            sign.setLine(i, lines[i]);
        sign.setColor(DyeColor.valueOf(obj.get("color").getAsString()));
        sign.update(true, false);
    }

    /* Десериализация контейнеров (блоки, имеющие инвентарь). */
    private void deserialize(Container container) {
        container.getInventory().setContents(this.readInventory(data).getContents());
    }

    /* Десериализация проигрывателя. */
    private void deserialize(Jukebox jukebox) {
        String serialized = JsonParser.parseString(data).getAsJsonObject().get("record").getAsString();
        jukebox.setRecord(this.getItemStackFromBase64String(serialized));
    }

    private Inventory readInventory(String inventoryJson) {
        JsonObject json = JsonParser.parseString(inventoryJson).getAsJsonObject();
        Inventory inventory = Bukkit.createInventory(null, InventoryType.valueOf(json.get("type").getAsString()));

        JsonArray items = json.get("items").getAsJsonArray();
        for (JsonElement element : items) {
            JsonObject jsonItem = element.getAsJsonObject();
            ItemStack item = this.getItemStackFromBase64String(jsonItem.get("data").getAsString());
            inventory.setItem(jsonItem.get("slot").getAsInt(), item);
        }

        return inventory;
    }

    @SneakyThrows
    private ItemStack getItemStackFromBase64String(String base64) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(base64));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }

}