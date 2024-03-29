package me.nologic.castlewars;

import co.aikar.commands.PaperCommandManager;
import lombok.Getter;
import me.nologic.castlewars.battleground.Battleground;
import me.nologic.castlewars.battleground.BattlegroundPlayer;
import me.nologic.castlewars.battleground.BattlegroundTeam;
import me.nologic.castlewars.battleground.Multiground;
import me.nologic.castlewars.battleground.editor.BattlegroundEditor;
import me.nologic.castlewars.battleground.editor.PlayerEditSession;
import me.nologic.castlewars.battleground.editor.task.AddNeutralFlagTask;
import me.nologic.castlewars.battleground.editor.task.AddTeamFlagTask;
import me.nologic.castlewars.battleground.editor.task.SaveVolumeTask;
import me.nologic.castlewars.battleground.util.BattlegroundValidator;
import me.nologic.castlewars.command.CastleWarsCommand;
import me.nologic.castlewars.game.EventDrivenGameMaster;
import me.nologic.castlewars.game.PlayerKDAHandler;
import me.nologic.castlewars.game.object.NeutralBattlegroundFlag;
import me.nologic.castlewars.game.object.TeamBattlegroundFlag;
import me.nologic.castlewars.game.object.base.BattlegroundFlag;
import me.nologic.castlewars.util.PlaceholderProvider;
import me.nologic.minority.MinorityExtension;
import me.nologic.minority.MinorityFeature;
import me.nologic.minority.annotations.Translatable;
import me.nologic.minority.annotations.TranslationKey;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Random;

@Getter @Translatable
public final class CastleWars extends MinorityExtension implements MinorityFeature {

    private boolean disabling = false;

    @Getter
    private static CastleWars instance;

    private final Random random = new Random();

    private EventDrivenGameMaster gameMaster;
    private BattlegroundManager   battlegrounder;
    private PaperCommandManager   commandManager;

    @Override
    public void onLoad() {
        super.getConfigurationWizard().generate(this.getClass());
        super.getConfigurationWizard().generate(EventDrivenGameMaster.class);
        super.getConfigurationWizard().generate(CastleWarsCommand.class);
        super.getConfigurationWizard().generate(PlayerEditSession.class);
        super.getConfigurationWizard().generate(BattlegroundPlayer.class);
        super.getConfigurationWizard().generate(BattlegroundEditor.class);
        super.getConfigurationWizard().generate(BattlegroundValidator.class);
        super.getConfigurationWizard().generate(AddTeamFlagTask.class);
        super.getConfigurationWizard().generate(PlayerKDAHandler.class);
        super.getConfigurationWizard().generate(BattlegroundTeam.class);
        super.getConfigurationWizard().generate(AddNeutralFlagTask.class);
        super.getConfigurationWizard().generate(BattlegroundFlag.class);
        super.getConfigurationWizard().generate(SaveVolumeTask.class);
        super.getConfigurationWizard().generate(NeutralBattlegroundFlag.class);
        super.getConfigurationWizard().generate(TeamBattlegroundFlag.class);
    }

    @Override
    public void onEnable() {
        instance = this;

        this.init(this, this.getClass(), this);

        File maps = new File(super.getDataFolder() + "/battlegrounds");
        if (!maps.exists()) {
            if (maps.mkdir()) {
                super.getLogger().info(battlegroundFolderMessage);
            }
        }

        this.battlegrounder = new BattlegroundManager(this);
        this.gameMaster = new EventDrivenGameMaster();
        this.commandManager = new PaperCommandManager(this);

        commandManager.registerCommand(new CastleWarsCommand(this));

        getServer().getPluginManager().registerEvents(gameMaster, this);

        if (super.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            final PlaceholderProvider mpe = new PlaceholderProvider();
            if (!mpe.isRegistered()) mpe.register();
        }
    }

    // TODO: 21.09.2023 Необходимо добавить автоматическую загрузку арен.

    @Override
    public void onDisable() {

        this.disabling = true;
        super.getLogger().info(disableMessage);

        for (final PlayerEditSession session : battlegrounder.getEditor().getEditSessionList()) {
            session.setActive(false);
        }

        for (Multiground multiground : battlegrounder.getMultigrounds()) {
            battlegrounder.disable(multiground.getBattleground());
        }

        for (Battleground battleground : battlegrounder.getLoadedBattlegrounds()) {
            battlegrounder.disable(battleground);
        }

    }

    public void broadcast(final String message) {
        for (Player player : super.getServer().getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    @TranslationKey(section = "log-info-messages", name = "battleground-folder-created", value = "CastleWars created a battlegrounds folder.")
    private String battlegroundFolderMessage;

    @TranslationKey(section = "log-info-messages", name = "battleground-load", value = "CastleWars is trying to load battleground %s.")
    private String battlegroundLoadMessage;

    @TranslationKey(section = "log-info-messages", name = "plugin-disable", value = "CastleWars will be disabled. All running battlegrounds will be stopped.")
    private String disableMessage;

}