package me.nologic.minespades.game.object;

import lombok.Getter;
import me.nologic.minespades.Minespades;
import me.nologic.minespades.battleground.Battleground;
import me.nologic.minespades.battleground.BattlegroundPlayer;
import me.nologic.minespades.battleground.BattlegroundPreferences;
import me.nologic.minespades.util.BossBar;
import me.nologic.minority.MinorityFeature;
import me.nologic.minority.annotations.Translatable;
import me.nologic.minority.annotations.TranslationKey;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

@Translatable
public class NeutralBattlegroundFlag extends BattlegroundFlag implements Listener, MinorityFeature {

    private boolean particle = false;
    private BukkitRunnable flagRecoveryTimer;

    @Getter
    private BossBar recoveryBossBar;

    public NeutralBattlegroundFlag(final Battleground battleground, final Location base, final ItemStack flag) {
        super(battleground, base, flag);
        this.init(this, this.getClass(), Minespades.getInstance());

        Bukkit.getPluginManager().registerEvents(this, Minespades.getPlugin(Minespades.class));

        final Particle.DustOptions options = new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.4F);
        this.tick = new BukkitRunnable() {

            // Каждые 5 тиков внутри BoundingBox'а проверяются энтити.
            // Если энтитя == игрок, скорборды совпадают, но разные команды, то pickup
            @Override
            public void run() {
                if (currentPosition != null && boundingBox != null && particle) {

                    battleground.getWorld().spawnParticle(Particle.REDSTONE, currentPosition.clone().add(0.5, 0.5, 0.5), 2, 0.5, 1, 0.5, options);
                    for (Entity entity : battleground.getWorld().getNearbyEntities(boundingBox)) {
                        if (entity instanceof Player player) {
                            if (battleground.getScoreboard().equals(player.getScoreboard())) {
                                if (!BattlegroundPlayer.getBattlegroundPlayer(player).isCarryingFlag()) {
                                    if (player.getGameMode().equals(GameMode.SURVIVAL)) {
                                        pickup(BattlegroundPlayer.getBattlegroundPlayer(player));
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }

        };

        tick.runTaskTimer(Minespades.getPlugin(Minespades.class), 0, 5);
    }

    @EventHandler
    private void onBlockBreak(BlockBreakEvent event) {

        // Флаг должен быть неразрушаемым
        if (Objects.equals(event.getBlock().getLocation(), currentPosition)) {
            event.setCancelled(true);
        }

        // Блок под флагом тоже должен быть неразрушаемым
        if (currentPosition != null) {
            if (Objects.equals(event.getBlock().getLocation(), currentPosition.getBlock().getRelative(BlockFace.DOWN).getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    protected void pickup(final BattlegroundPlayer carrier) {
        this.carrier = carrier;

        carrier.setFlag(this);
        carrier.setCarryingFlag(true);
        battleground.broadcast(String.format(neutralFlagStolenMessage, carrier.getDisplayName()));

        if (battleground.getPreferences().get(BattlegroundPreferences.Preference.FLAG_CARRIER_GLOW)) {
            carrier.getBukkitPlayer().setGlowing(true);
        }

        super.playFlagEquipSound();

        Player player = carrier.getBukkitPlayer();
        player.getInventory().setHelmet(flagItem);

        if (currentPosition != null) {
            currentPosition.getBlock().setType(Material.AIR);
            currentPosition = null;
        }

        boundingBox = null;
        if (flagRecoveryTimer != null) {
            flagRecoveryTimer.cancel();

            // Не забываем скрывать таймер, если флаг был поднят
            Bukkit.getScheduler().runTask(Minespades.getPlugin(Minespades.class), () -> {
                this.recoveryBossBar.cleanViewers();
                recoveryBossBar = null;
            });
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

        if (battleground.getPreferences().get(BattlegroundPreferences.Preference.FLAG_CARRIER_GLOW)) {
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
                                Bukkit.getScheduler().runTaskLater(Minespades.getPlugin(Minespades.class), () -> bossBar.cleanViewers(), 30);
                                this.cancel();
                            }
                        }

                    };

                    smoothFillerTask.runTaskTimer(Minespades.getPlugin(Minespades.class), 0, 1);
                    this.cancel();
                }

                for (BattlegroundPlayer bgPlayer : battleground.getBattlegroundPlayers()) {
                    Player player = bgPlayer.getBukkitPlayer();
                    bossBar.addViewer(player);
                }

                bossBar.visible(true);
            }

        };

        flagRecoveryTimer.runTaskTimer(Minespades.getPlugin(Minespades.class), 0, 20);
    }

    public void reset() {
        Bukkit.getServer().getScheduler().runTask(Minespades.getInstance(), () -> {
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
            particle = true;

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