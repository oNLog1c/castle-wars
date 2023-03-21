package me.nologic.minespades.battleground;

import me.nologic.minespades.Minespades;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.sql.*;
import java.util.Arrays;

/**
 * BattlegroundLoader подготавливает арены к использованию по их прямому назначению.
 * В этом классе создаются новые инстанции арен, загружаются их настройки и прочая
 * информация, которая требуется для игры: блоки арены, команды, расположение
 * точек респавна, мета-данные тайл-энтитей и т. д.
 * <p> Редактирование информации об уже существующих аренах, а так же само создание
 * этих арен происходит в отдельном классе BattlegroundEditor.
 * @see BattlegroundEditor
 * */
public class BattlegroundLoader {

    private final Minespades plugin;
    private Battleground battleground;

    public Battleground load(String name) {
        this.battleground = new Battleground(name);
        return prepareBattleground();
    }

    // TODO: Загрузка тайл-энтитей и их даты.
    private Battleground prepareBattleground() {
        this.loadSettings();
        this.construct();
        this.setupTeams();
        return battleground;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/battlegrounds/" + battleground.getName() + ".db");
    }

    /* Инициализация полей арены. */
    private void loadSettings() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet prefs = statement.executeQuery(Table.PREFERENCES.getSelectStatement());
            prefs.next();
            this.battleground.setWorld(Bukkit.getWorld(prefs.getString("world")));
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /* Размещение блоков в мире, который указан в настроках, по координатам, которые считываются из таблицы volume. */
    private void construct() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet blocks = statement.executeQuery(Table.VOLUME.getSelectStatement());
            while(blocks.next()) {
                int x = blocks.getInt("x"), y = blocks.getInt("y"), z = blocks.getInt("z");
                Material material = Material.valueOf(blocks.getString("material"));
                this.battleground.getWorld().setType(x, y, z, material);
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private void setupTeams() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet teams = statement.executeQuery(Table.TEAMS.getSelectStatement());
            while (teams.next()) {
                Team team = new Team(teams.getString("name"), teams.getInt("lifepool"), teams.getString("color"));
                Arrays.stream(teams.getString("loadouts").split(", ")).toList().forEach(inv -> team.add(decodeInventory(inv)));
                Arrays.stream(teams.getString("respawnLocations").split(", ")).toList().forEach(loc -> team.add(decodeLocation(loc)));
                this.battleground.addTeam(team);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

    }

    private Location decodeLocation(String encoded) {
        String s = Base64Coder.decodeString(encoded);
        String[] split = s.split(", ");

        double x = Double.parseDouble(split[0]), y = Double.parseDouble(split[1]), z = Double.parseDouble(split[2]);
        float yaw = Float.parseFloat(split[3]), pitch = Float.parseFloat(split[4]);

        return new Location(battleground.getWorld(), x, y, z, yaw, pitch);
    }

    private Inventory decodeInventory(String encoded) {
        Inventory inventory = Bukkit.getServer().createInventory(null, InventoryType.PLAYER);

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(encoded));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);


            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, (ItemStack) dataInput.readObject());
            }

            dataInput.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return inventory;
    }

    public BattlegroundLoader(Minespades plugin) {
        this.plugin = plugin;
    }

}