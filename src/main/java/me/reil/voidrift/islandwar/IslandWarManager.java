package me.reil.voidrift.islandwar;

import me.reil.voidrift.VoidRiftPlugin;
import me.reil.voidrift.event.ActiveEvent;
import me.reil.voidrift.event.EventType;
import me.reil.voidrift.integration.SkyBoundHook;
import me.reil.voidrift.integration.SkyBoundIsland;
import me.reil.voidrift.integration.SopCustomBlocksHook;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Island War event lifecycle.
 */
public final class IslandWarManager {

    public static final String DEFAULT_HEART_BLOCK_ID = "island_heart";
    public static final int DEFAULT_HEART_HP = 100;
    public static final double DEFAULT_MONEY_PERCENT = 10.0;
    public static final long DEFAULT_XP_PERCENT = 3;
    public static final int DEFAULT_PRESTIGE_TRANSFER = 1;
    public static final int DEFAULT_HEART_OFFSET_Y = 1;

    private final VoidRiftPlugin plugin;
    /** eventId -> session */
    private final Map<String, IslandWarSession> sessions = new LinkedHashMap<String, IslandWarSession>();

    public IslandWarManager(VoidRiftPlugin plugin) {
        this.plugin = plugin;
    }

    public IslandWarSession getSession(String eventId) {
        return sessions.get(eventId);
    }

    public IslandWarSession findSessionByIsland(String islandId) {
        for (IslandWarSession s : sessions.values()) {
            if (!s.isFinished() && s.getHeart(islandId) != null) return s;
        }
        return null;
    }

    public IslandHeart findHeartAt(Location loc) {
        if (loc == null) return null;
        for (IslandWarSession s : sessions.values()) {
            if (s.isFinished()) continue;
            for (IslandHeart h : s.getAllHearts()) {
                Location hLoc = h.getLocation();
                if (hLoc.getWorld() == null || loc.getWorld() == null) continue;
                if (!hLoc.getWorld().equals(loc.getWorld())) continue;
                if (hLoc.getBlockX() == loc.getBlockX()
                        && hLoc.getBlockY() == loc.getBlockY()
                        && hLoc.getBlockZ() == loc.getBlockZ()) {
                    return h;
                }
            }
        }
        return null;
    }

    public void onStart(ActiveEvent event) {
        SkyBoundHook sbHook = plugin.getSkyBoundHook();
        if (!sbHook.isAvailable()) {
            plugin.getLogger().warning("Island War event started but SkyBound is not available - skipping.");
            return;
        }

        String eventId = event.getDefinition().getId();
        sessions.put(eventId, new IslandWarSession(eventId));

        Map<String, String> vars = vars("event", eventId);
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.island-war.registration-open"));
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.island-war.registration-command", vars));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
        }
    }

    public boolean registerIsland(ActiveEvent event, Player player) {
        if (!plugin.getSkyBoundHook().isAvailable()) return false;
        if (event.getDefinition().getType() != EventType.ISLAND_WAR) return false;

        IslandWarSession session = sessions.get(event.getDefinition().getId());
        if (session == null || session.isFinished()) return false;

        SkyBoundIsland island = plugin.getSkyBoundHook().getPlayerIsland(player);
        if (island == null) {
            player.sendMessage(plugin.getLang().msgFor(player, "messages.island-war.no-island"));
            return false;
        }
        if (!island.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().msgFor(player, "messages.island-war.owner-only"));
            return false;
        }
        if (session.getHeart(island.getId()) != null) {
            player.sendMessage(plugin.getLang().msgFor(player, "messages.island-war.already-registered"));
            return false;
        }

        Location center = island.getCenter();
        if (center == null || center.getWorld() == null) {
            player.sendMessage(plugin.getLang().msgFor(player, "messages.island-war.no-center"));
            return false;
        }

        for (UUID memberId : island.getMembers()) {
            event.addParticipant(memberId);
        }

        Location heartLoc = center.clone().add(0, getHeartOffsetY(), 0);
        IslandHeart heart = new IslandHeart(island.getId(), heartLoc, getHeartHp());
        session.registerHeart(heart);
        placeHeartBlock(heartLoc);

        Map<String, String> locVars = vars("x", String.valueOf(heartLoc.getBlockX()));
        locVars.put("y", String.valueOf(heartLoc.getBlockY()));
        locVars.put("z", String.valueOf(heartLoc.getBlockZ()));

        for (UUID memberId : island.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendTitle(
                        plugin.getLang().msgFor(member, "messages.island-war.join-title"),
                        plugin.getLang().msgFor(member, "messages.island-war.join-subtitle"),
                        10, 60, 20);
                member.playSound(member.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.0f);
                member.sendMessage(plugin.getLang().msgFor(member, "messages.island-war.heart-location", locVars));
            }
        }

        Map<String, String> joinVars = vars("island", island.getName() != null ? island.getName() : island.getId());
        joinVars.put("count", String.valueOf(session.getAllHearts().size()));
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.island-war.island-joined", joinVars));
        return true;
    }

    public void onEnd(ActiveEvent event) {
        IslandWarSession session = sessions.remove(event.getDefinition().getId());
        if (session == null) return;
        session.finish();

        for (IslandHeart heart : session.getAllHearts()) {
            if (!heart.isDestroyed()) removeHeartBlock(heart.getLocation());
        }

        String winnerIslandId = session.getLastSurvivor();
        if (winnerIslandId == null) {
            int best = -1;
            for (Map.Entry<String, Integer> e : session.getAllKills().entrySet()) {
                if (e.getValue() > best) {
                    best = e.getValue();
                    winnerIslandId = e.getKey();
                }
            }
        }

        if (winnerIslandId == null) {
            Bukkit.broadcastMessage(plugin.getLang().msg("messages.island-war.ended-no-winner"));
            return;
        }

        SkyBoundIsland winner = plugin.getSkyBoundHook().getIsland(winnerIslandId);
        String name = winner != null && winner.getName() != null ? winner.getName() : winnerIslandId;
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.island-war.ended-winner", vars("island", name)));

        if (winner != null) {
            for (UUID memberId : winner.getMembers()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) {
                    p.sendTitle(
                            plugin.getLang().msgFor(p, "messages.island-war.victory-title"),
                            plugin.getLang().msgFor(p, "messages.island-war.victory-subtitle"),
                            10, 80, 20);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
        }
    }

    public boolean damageHeart(IslandHeart heart, Player attacker, int amount) {
        if (heart == null || heart.isDestroyed()) return false;
        boolean destroyed = heart.damage(amount);

        SkyBoundIsland target = plugin.getSkyBoundHook().getIsland(heart.getIslandId());
        Map<String, String> hpVars = vars("hp", String.valueOf(heart.getHp()));
        hpVars.put("max", String.valueOf(heart.getMaxHp()));

        if (target != null) {
            for (UUID memberId : target.getMembers()) {
                Player def = Bukkit.getPlayer(memberId);
                if (def != null) {
                    sendActionBar(def, plugin.getLang().msgFor(def, "messages.island-war.heart-under-attack", hpVars));
                    def.playSound(def.getLocation(), Sound.BLOCK_ANVIL_HIT, 0.5f, 0.7f);
                }
            }
        }

        sendActionBar(attacker, plugin.getLang().msgFor(attacker, "messages.island-war.heart-hit", hpVars));

        if (destroyed) {
            onHeartDestroyed(heart, attacker);
        }
        return destroyed;
    }

    private void onHeartDestroyed(IslandHeart heart, Player attacker) {
        IslandWarSession session = findSessionByIsland(heart.getIslandId());
        removeHeartBlock(heart.getLocation());

        SkyBoundIsland victim = plugin.getSkyBoundHook().getIsland(heart.getIslandId());
        SkyBoundIsland attackerIsland = plugin.getSkyBoundHook().getPlayerIsland(attacker);
        if (victim == null) return;

        if (session != null && attackerIsland != null) {
            session.addKill(attackerIsland.getId());
        }

        transferRewards(victim, attackerIsland);

        String victimName = victim.getName() != null ? victim.getName() : victim.getId();
        Map<String, String> destroyVars = vars("player", attacker.getName());
        destroyVars.put("island", victimName);
        Bukkit.broadcastMessage(plugin.getLang().msg("messages.island-war.heart-broken", destroyVars));

        for (UUID memberId : victim.getMembers()) {
            Player def = Bukkit.getPlayer(memberId);
            if (def != null) {
                def.sendTitle(
                        plugin.getLang().msgFor(def, "messages.island-war.defeat-title"),
                        plugin.getLang().msgFor(def, "messages.island-war.defeat-subtitle"),
                        10, 60, 20);
                def.playSound(def.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 0.6f);
            }
        }

        if (attackerIsland != null) {
            for (UUID memberId : attackerIsland.getMembers()) {
                Player atk = Bukkit.getPlayer(memberId);
                if (atk != null) {
                    atk.sendTitle(
                            plugin.getLang().msgFor(atk, "messages.island-war.kill-title"),
                            plugin.getLang().msgFor(atk, "messages.island-war.kill-subtitle", vars("island", victimName)),
                            10, 60, 20);
                    atk.playSound(atk.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                }
            }
        }

        ActiveEvent ae = plugin.getEventManager().getActiveEvent(session != null ? session.getEventId() : "");
        if (ae != null) {
            ae.addScore(attacker.getUniqueId(), 100);
        }

        if (session != null && session.getLastSurvivor() != null && ae != null) {
            final String eid = session.getEventId();
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    plugin.getEventManager().stopEvent(eid);
                }
            }, 100L);
        }
    }

    private void transferRewards(SkyBoundIsland victim, SkyBoundIsland attackerIsland) {
        double moneyAmount = victim.getBankBalance() * (getMoneyPercent() / 100.0);
        long xpAmount = (long) (victim.getExperience() * (getXpPercent() / 100.0));

        if (moneyAmount > 0) {
            victim.setBankBalance(Math.max(0.0, victim.getBankBalance() - moneyAmount));
            if (attackerIsland != null) {
                attackerIsland.setBankBalance(attackerIsland.getBankBalance() + moneyAmount);
            }
        }

        if (xpAmount > 0 && attackerIsland != null) {
            attackerIsland.addExperience(xpAmount);
        }

        if (attackerIsland != null) {
            plugin.getSkyBoundHook().transferPrestige(victim.getId(), attackerIsland.getId(), getPrestigeTransfer());
        }
    }

    public List<IslandHeart> getAttackTargets(Player attacker) {
        List<IslandHeart> result = new ArrayList<IslandHeart>();
        SkyBoundIsland myIsland = plugin.getSkyBoundHook().getPlayerIsland(attacker);
        String myIslandId = myIsland != null ? myIsland.getId() : null;

        for (IslandWarSession session : sessions.values()) {
            if (session.isFinished()) continue;
            for (IslandHeart heart : session.getAllHearts()) {
                if (heart.isDestroyed()) continue;
                if (myIslandId != null && heart.getIslandId().equals(myIslandId)) continue;
                result.add(heart);
            }
        }
        return result;
    }

    public IslandHeart getOwnHeart(Player player) {
        SkyBoundIsland myIsland = plugin.getSkyBoundHook().getPlayerIsland(player);
        if (myIsland == null) return null;
        for (IslandWarSession session : sessions.values()) {
            if (session.isFinished()) continue;
            IslandHeart h = session.getHeart(myIsland.getId());
            if (h != null) return h;
        }
        return null;
    }

    public boolean isWarActive() {
        for (IslandWarSession s : sessions.values()) {
            if (!s.isFinished()) return true;
        }
        return false;
    }

    private void placeHeartBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        SopCustomBlocksHook hook = plugin.getSopCustomBlocksHook();
        boolean placed = false;
        if (hook != null && hook.isAvailable()) {
            placed = hook.placeBlock(getHeartBlockId(), loc);
        }
        if (!placed) {
            loc.getBlock().setType(Material.BEACON, false);
        }
    }

    private void removeHeartBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        SopCustomBlocksHook hook = plugin.getSopCustomBlocksHook();
        if (hook != null && hook.isAvailable()) {
            hook.removeBlock(loc);
        }
        loc.getBlock().setType(Material.AIR, false);
    }

    private void sendActionBar(Player p, String msg) {
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', msg)));
        } catch (Throwable ignored) {}
    }

    private String getHeartBlockId() {
        return plugin.getConfig().getString("island-war.heart.custom-block-id", DEFAULT_HEART_BLOCK_ID);
    }

    private int getHeartHp() {
        return plugin.getConfig().getInt("island-war.heart.hp", DEFAULT_HEART_HP);
    }

    private int getHeartOffsetY() {
        return plugin.getConfig().getInt("island-war.heart.offset-y", DEFAULT_HEART_OFFSET_Y);
    }

    private double getMoneyPercent() {
        return plugin.getConfig().getDouble("island-war.rewards.money-percent", DEFAULT_MONEY_PERCENT);
    }

    private long getXpPercent() {
        return plugin.getConfig().getLong("island-war.rewards.xp-percent", DEFAULT_XP_PERCENT);
    }

    private int getPrestigeTransfer() {
        return plugin.getConfig().getInt("island-war.rewards.prestige-transfer", DEFAULT_PRESTIGE_TRANSFER);
    }

    private Map<String, String> vars(String key, String value) {
        Map<String, String> vars = new LinkedHashMap<String, String>();
        vars.put(key, value);
        return vars;
    }
}
