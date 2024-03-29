package me.nologic.castlewars.battleground.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.SneakyThrows;
import me.nologic.castlewars.CastleWars;
import me.nologic.castlewars.battleground.editor.PlayerEditSession;
import me.nologic.minority.MinorityFeature;
import me.nologic.minority.annotations.Translatable;
import me.nologic.minority.annotations.TranslationKey;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;

@Translatable
public class BattlegroundValidator implements MinorityFeature {

    private static final CastleWars plugin = CastleWars.getPlugin(CastleWars.class);

    @TranslationKey(section = "validate-error-messages", name = "battleground-is-already-launched", value = "Error. Battleground with name &3%s &ris already launched.")
    private String battlegroundAlreadyLaunched;

    @TranslationKey(section = "validate-error-messages", name = "battleground-does-not-exist", value = "Error. Battleground with name &3%s &rdoesn't exist.")
    private String battlegroundNotExistMessage;

    @TranslationKey(section = "validate-error-messages", name = "loadout-does-not-exist", value = "Error. Loadout with name &3%s &rdoesn't exist.")
    private String nonExistingLoadoutMessage;

    @TranslationKey(section = "validate-error-messages", name = "supple-does-not-exist", value = "Error. Supply with name &3%s &rdoesn't exist.")
    private String nonExistingSupplyMessage;

    @TranslationKey(section = "validate-error-messages", name = "team-does-not-exist", value = "Error. Team with name &3%s &rdoesn't exist.")
    private String nonExistingTeamMessage;

    @TranslationKey(section = "validate-error-messages", name = "battleground-less-than-two-teams", value = "Error. Battleground &3%s &rdoesn't have two teams &7(which is required minimum)&r.")
    private String lessThanTwoTeamsMessage;

    @TranslationKey(section = "validate-error-messages", name = "team-without-respawn-point", value = "Error. Team &3%s &ron battleground &6%s &rdoesn't have any respawn points. Create a new one using &3/ms add respawn&r.")
    private String noRespawnPointMessage;

    @TranslationKey(section = "validate-error-messages", name = "team-without-loadout", value = "Error. Team &3%s &ron battleground &6%s &rdoesn't have any loadouts. Create a new one using &3/ms add loadout <name>&r.")
    private String noLoadoutMessage;

    @TranslationKey(section = "validate-error-messages", name = "team-without-flag", value = "Error. Team &3%s &ron battleground &6%s &rdoesn't have any flag to delete.")
    private String teamWithoutFlagMessage;

    @TranslationKey(section = "validate-error-messages", name = "neutral-flag-not-found", value = "Error. Can't find a neutral flag on battleground &3%s &rat %s.")
    private String neutralFlagNotFound;

    public BattlegroundValidator() {
        plugin.getConfigurationWizard().generate(this.getClass());
        this.init(this, this.getClass(), plugin);
    }

    // TODO: Имеет смысл переписать валидацию. Дублирование кода меня как-то не очень радует.
    @SneakyThrows
    public boolean isValid(final String battlegroundName) {

        // Checking for existence
        if (!this.isBattlegroundExist(battlegroundName)) {
            return false;
        }

        // Counting teams, if 0 or less than 2 then return false.
        final BattlegroundDataDriver driver = new BattlegroundDataDriver().connect(battlegroundName);
        try (final ResultSet result = driver.executeQuery("SELECT count(*) AS rows FROM teams;")) {
            if (result.next()) {
                final int teams = result.getInt("rows");
                if (teams < 2) {
                    return false;
                }
            }
        }

        try (final ResultSet teams = driver.executeQuery("SELECT * FROM teams;")) {

            while (teams.next()) {
                final String teamName = teams.getString("name");

                // Team respawn point validation
                try (final ResultSet result = driver.executeQuery("SELECT count(respawnPoints) AS respawns FROM teams WHERE name = ?;", teamName)) {
                    if (result.next()) {
                        final int respawns = result.getInt("respawns");
                        if (respawns == 0) {
                            return false;
                        }
                    }
                }

                // Loadout validation
                try (final ResultSet result = driver.executeQuery("SELECT * FROM teams WHERE name = ?;", teamName)) {
                    if (!result.next() || result.getString("loadouts") == null || Objects.equals(result.getString("loadouts"), "\n")) {
                        return false;
                    }
                }

            }
        }

        return true;
    }

    @SneakyThrows
    public boolean isValid(final Player player, final String battlegroundName) {

        // Checking for existence
        if (!this.isBattlegroundExist(battlegroundName)) {
            player.sendMessage(String.format(battlegroundNotExistMessage, battlegroundName));
            return false;
        }

        // Already launched check
        if (plugin.getGameMaster().getBattlegrounder().getLoadedBattlegrounds().stream().anyMatch(battleground -> battleground.getBattlegroundName().equals(battlegroundName))) {
            player.sendMessage(String.format(battlegroundAlreadyLaunched, battlegroundName));
            return false;
        }

        // Counting teams, if 0 or less than 2 then return false.
        final BattlegroundDataDriver driver = new BattlegroundDataDriver().connect(battlegroundName);
        try (final ResultSet result = driver.executeQuery("SELECT count(*) AS rows FROM teams;")) {
            if (result.next()) {
                final int teams = result.getInt("rows");
                if (teams < 2) {
                    player.sendMessage(String.format(lessThanTwoTeamsMessage, battlegroundName));
                    return false;
                }
            }
        }

        try (final ResultSet teams = driver.executeQuery("SELECT * FROM teams;")) {

            while (teams.next()) {
                final String teamName = teams.getString("name");

                // Team respawn point validation
                try (final ResultSet result = driver.executeQuery("SELECT count(respawnPoints) AS respawns FROM teams WHERE name = ?;", teamName)) {
                    if (result.next()) {
                        final int respawns = result.getInt("respawns");
                        if (respawns == 0) {
                            player.sendMessage(String.format(noRespawnPointMessage, teamName, battlegroundName));
                            return false;
                        }
                    }
                }

                // Loadout validation
                try (final ResultSet result = driver.executeQuery("SELECT * FROM teams WHERE name = ?;", teamName)) {
                    if (!result.next() || result.getString("loadouts") == null || Objects.equals(result.getString("loadouts"), "\n")) {
                        player.sendMessage(String.format(noLoadoutMessage, teamName, battlegroundName));
                        return false;
                    }
                }

            }
        }

        return true;
    }

    public boolean isBattlegroundExist(final String battleground) {
        return new File(plugin.getDataFolder() + "/battlegrounds/" + battleground + ".db").exists();
    }

    public boolean isTeamExist(final Player player, final String teamName) {
        final PlayerEditSession session = plugin.getBattlegrounder().getEditor().editSession(player);
        final BattlegroundDataDriver driver = new BattlegroundDataDriver().connect(session.getTargetBattleground());
        try (final ResultSet result = driver.executeQuery("SELECT * FROM teams WHERE name = ?", teamName)) {
            if (result.next() && result.getString("name").equals(teamName)) {
                driver.closeConnection();
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        player.sendMessage(String.format(nonExistingTeamMessage, teamName));
        return false;
    }

    public boolean isNeutralFlagAt(Player player, final int x, final int y, final int z) {
        final PlayerEditSession session = plugin.getBattlegrounder().getEditor().editSession(player);
        final BattlegroundDataDriver driver = new BattlegroundDataDriver().connect(session.getTargetBattleground());

        try (final ResultSet result = driver.executeQuery("SELECT * FROM objects WHERE x = ? AND y = ? AND z = ?;", x, y, z)) {
            if (result.next()) {
                driver.closeConnection();
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        driver.closeConnection();
        player.sendMessage(String.format(neutralFlagNotFound, String.format("&ex&b%s&f, &ey&b%s&f, &ez&b%s&f", x, y, z), session.getTargetBattleground()));
        return false;
    }

    // TODO: Та же история. Какого чёрта код дублируется?
    public boolean isTeamHaveFlag(Player player, String teamName) {
        final PlayerEditSession session = plugin.getBattlegrounder().getEditor().editSession(player);
        final BattlegroundDataDriver driver = new BattlegroundDataDriver().connect(session.getTargetBattleground());

        try (final ResultSet result = driver.executeQuery("SELECT * FROM teams WHERE name = ?", teamName)) {
            if (result.next() && result.getString("flag") != null) {
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        driver.closeConnection();
        player.sendMessage(String.format(teamWithoutFlagMessage, teamName, session.getTargetBattleground()));
        return false;
    }

    public boolean isTeamHaveFlag(String battlegroundName, String teamName) {
        final BattlegroundDataDriver driver = new BattlegroundDataDriver().connect(battlegroundName);

        try (final ResultSet result = driver.executeQuery("SELECT * FROM teams WHERE name = ?", teamName)) {
            if (result.next() && result.getString("flag") != null) {
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        driver.closeConnection();
        return false;
    }

    @SneakyThrows
    public boolean isLoadoutExist(Player player, String loadoutName) {
        try (Connection connection = connect(plugin.getBattlegrounder().getEditor().editSession(player).getTargetBattleground())) {
            PreparedStatement listStatement = connection.prepareStatement("SELECT * FROM teams WHERE name = ?;");
            listStatement.setString(1, plugin.getBattlegrounder().getEditor().editSession(player).getTargetTeam());
            ResultSet data = listStatement.executeQuery(); data.next();

            if (data.getString("loadouts") == null) {
                player.sendMessage(String.format(nonExistingLoadoutMessage, loadoutName));
                return false;
            }

            final JsonArray loadouts = JsonParser.parseString(data.getString("loadouts")).getAsJsonArray();

            for (JsonElement loadoutElement : loadouts) {
                JsonObject loadout = loadoutElement.getAsJsonObject();
                if (loadoutName.equals(loadout.get("name").getAsString())) return true;
            }

            player.sendMessage(String.format(nonExistingLoadoutMessage, loadoutName));
            return false;
        }
    }

    @SneakyThrows
    public boolean isSupplyExist(final Player player, final String targetSupply) {
        final PlayerEditSession session = plugin.getBattlegrounder().getEditor().editSession(player);
        final BattlegroundDataDriver driver = new BattlegroundDataDriver().connect(session.getTargetBattleground());
        try (final ResultSet result = driver.executeQuery("SELECT * FROM teams WHERE name = ?;", session.getTargetTeam())) {
            result.next(); final JsonArray loadouts = JsonParser.parseString(result.getString("loadouts")).getAsJsonArray();
            for (JsonElement loadoutElement : loadouts) {
                JsonObject jsonLoadout = loadoutElement.getAsJsonObject();
                String loadoutName = jsonLoadout.get("name").getAsString();
                if (loadoutName.equals(session.getTargetLoadout())) {
                    JsonArray supplies = jsonLoadout.get("supplies").getAsJsonArray();
                    for (JsonElement supplyElement : supplies) {
                        JsonObject supplyRule = supplyElement.getAsJsonObject();
                        String supplyName = supplyRule.get("name").getAsString();
                        if (supplyName.equals(targetSupply)) {
                            return true;
                        }
                    }
                }
            }
        }
        driver.closeConnection();
        player.sendMessage(String.format(nonExistingSupplyMessage, targetSupply));
        return false;
    }

    @SneakyThrows
    private Connection connect(String name) {
        return DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/battlegrounds/" + name + ".db");
    }

}