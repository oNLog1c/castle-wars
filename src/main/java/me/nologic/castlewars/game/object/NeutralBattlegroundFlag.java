package me.nologic.castlewars.game.object;

import lombok.Getter;
import me.nologic.castlewars.CastleWars;
import me.nologic.castlewars.battleground.Battleground;
import me.nologic.castlewars.battleground.BattlegroundPlayer;
import me.nologic.castlewars.battleground.BattlegroundPreferences;
import me.nologic.castlewars.game.object.base.BattlegroundFlag;
import me.nologic.castlewars.util.BossBar;
import me.nologic.minority.MinorityFeature;
import me.nologic.minority.annotations.Translatable;
import me.nologic.minority.annotations.TranslationKey;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

@Translatable
public class NeutralBattlegroundFlag extends BattlegroundFlag implements MinorityFeature {

    private BukkitRunnable flagRecoveryTimer;

    @Getter
    private BossBar recoveryBossBar;

    public NeutralBattlegroundFlag(final Battleground battleground, final Location base, final ItemStack flagBannerItem) {
        super(battleground, base, flagBannerItem, new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.4F)); // todo particles from config
        this.init(this, this.getClass(), CastleWars.getInstance());
        Bukkit.getServer().getScheduler().runTaskTimer(plugin, (task) -> {
            if (!battleground.isEnabled()) task.cancel();
            this.tick(this);
        }, 0, 5L);
    }

    /* A neutral flag may be carried by a player from any team. */
    @Override
    protected void tick(final BattlegroundFlag flag) {
        if (flag.isOnGround()) {
            flag.playParticles();
            flag.getCollidingPlayers().stream().filter(player -> !player.isCarryingFlag()).findFirst().ifPresent(flag::pickup);
        }
    }

    @Override
    public void pickup(final BattlegroundPlayer carrier) {

        this.carrier = carrier;

        carrier.setFlag(this);
        carrier.setCarryingFlag(true);
        battleground.broadcast(String.format(neutralFlagStolenMessage, carrier.getDisplayName()));

        if (battleground.getPreferences().get(BattlegroundPreferences.Preference.FLAG_CARRIER_GLOW).getAsBoolean()) {
            carrier.getBukkitPlayer().setGlowing(true);
        }

        super.playFlagEquipSound();

        Player player = carrier.getBukkitPlayer();
        player.getInventory().setHelmet(flagBannerItem);

        if (currentPosition != null) {
            currentPosition.getBlock().setType(Material.AIR);
            currentPosition = null;
        }

        boundingBox = null;
        if (flagRecoveryTimer != null) {
            this.flagRecoveryTimer.cancel();
            this.recoveryBossBar.cleanViewers();
            recoveryBossBar = null;
            flagRecoveryTimer = null;
        }
    }

    // TODO: дроп флага в воздухе и в лаве должен обрабатываться отдельно
    public void drop() {

        if (carrier == null)
            return;

        Player player = carrier.getBukkitPlayer();
        if (player.getLastDamageCause() != null && Objects.equals(player.getLastDamageCause().getCause(), EntityDamageEvent.DamageCause.LAVA) || player.getLocation().getBlock().getType() == Material.LAVA) {
            this.reset();
            return;
        }

        if (battleground.getPreferences().get(BattlegroundPreferences.Preference.FLAG_CARRIER_GLOW).getAsBoolean()) {
            carrier.getBukkitPlayer().setGlowing(false);
        }

        carrier.setFlag(null);
        carrier.setCarryingFlag(false);
        battleground.broadcast(String.format(neutralFlagDropMessage, carrier.getDisplayName()));

        currentPosition = player.getLocation().getBlock().getLocation();
        this.updateBoundingBox();

        // FIXME: Необходимо сохранять предыдущий шлем игрока, дабы он не исчезал, как слёзы во время дождя. (што)
        player.getInventory().setHelmet(new ItemStack(Material.AIR));
        carrier = null;

        this.validateBannerData();

        // Запускаем таймер, который отсчитывает время до ресета флага. Если флаг лежит на земле слишком долго, целесообразно восстановить его изначальную позицию.
        this.flagRecoveryTimer = new BukkitRunnable() {

            final int timeToReset = 45;
            int timer = timeToReset * 20;

            final BossBar bossBar = BossBar.bossBar(String.format(restorationCount, timer / 20), 1.0f, BarColor.BLUE, BarStyle.SEGMENTED_20);

            @Override
            public void run() {
                NeutralBattlegroundFlag.this.recoveryBossBar = bossBar;
                timer = timer - 20;
                bossBar.title(String.format(restorationCount, timer / 20));

                if (timer != 0) {
                    bossBar.progress(bossBar.progress() - 1.0f / timeToReset);
                }

                if (timer <= 100 && timer != 0) {
                    for (BattlegroundPlayer bgPlayer : battleground.getBattlegroundPlayers()) {
                        bgPlayer.getBukkitPlayer().playSound(bgPlayer.getBukkitPlayer().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 2 / (timer / 10f));
                    }
                }

                if (timer == 0) {

                    // Попытаемся сделать всё красиво, нам нужно заполнить боссбар, изменить сообщение на ФЛАГ ВОССТАНОВЛЕН! и сменить цвет
                    bossBar.title(flagRestoredTitle);
                    bossBar.color(BarColor.RED);
                    bossBar.progress(0);

                    NeutralBattlegroundFlag.this.reset();

                    BukkitRunnable smoothFillerTask = new BukkitRunnable() {

                        @Override
                        public void run() {
                            // Число, на которое увеличивается прогресс боссбара каждый тик
                            float number = 0.03f;
                            if (bossBar.progress() + number < 1.0f) {
                                bossBar.progress(bossBar.progress() + number);
                            } else  {
                                // Скрываем боссбар через полторы секунды после заполнения
                                bossBar.progress(1.0f);
                                bossBar.color(BarColor.GREEN);
                                Bukkit.getScheduler().runTaskLater(CastleWars.getPlugin(CastleWars.class), bossBar::cleanViewers, 30);
                                this.cancel();
                            }
                        }

                    };

                    smoothFillerTask.runTaskTimer(CastleWars.getPlugin(CastleWars.class), 0, 1);
                    this.cancel();
                }

                for (BattlegroundPlayer bgPlayer : battleground.getBattlegroundPlayers()) {
                    Player player = bgPlayer.getBukkitPlayer();
                    bossBar.addViewer(player);
                }

                bossBar.visible(true);
            }

        };

        flagRecoveryTimer.runTaskTimer(CastleWars.getPlugin(CastleWars.class), 0, 20);
    }

    public void reset() {
        Bukkit.getServer().getScheduler().runTask(CastleWars.getInstance(), () -> {
            if (currentPosition != null) currentPosition.getBlock().setType(Material.AIR);
            currentPosition = basePosition.clone();
            if (carrier != null) {
                carrier.getBukkitPlayer().getInventory().setHelmet(new ItemStack(Material.AIR));
                carrier.setFlag(null);
                carrier.setCarryingFlag(false);
                carrier = null;
            }

            updateBoundingBox();
            validateBannerData();

            if (flagRecoveryTimer != null) {
                flagRecoveryTimer.cancel();
                recoveryBossBar = null;
            }
        });
    }

    @TranslationKey(section = "regular-messages", name = "player-stole-neutral-flag", value = "%s &rstole the neutral flag!")
    private String neutralFlagStolenMessage;

    @TranslationKey(section = "regular-messages", name = "player-drop-neutral-flag", value = "%s &rdrop the neutral flag!")
    private String neutralFlagDropMessage;

    @TranslationKey(section = "regular-messages", name = "neutral-flag-restoration-counter", value = "&cNeutral &rflag will be restored in &e%ss&r!..")
    private String restorationCount;

    @TranslationKey(section = "regular-messages", name = "flag-is-restored", value = "&lFlag was restored!")
    private String flagRestoredTitle;

}