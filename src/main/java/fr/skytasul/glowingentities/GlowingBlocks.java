package fr.skytasul.glowingentities;

import fr.skytasul.glowingentities.GlowingEntities.Packets;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An extension of {@link GlowingEntities} to make blocks glow as well!
 * <p>
 * <i>Can only be used on Paper-based servers.</i>
 *
 * @author SkytAsul
 */
public class GlowingBlocks implements Listener {

	private final @NotNull GlowingEntities entities;
	private Map<Player, PlayerData> glowing;
	private boolean enabled = false;

	/**
	 * Initializes the Glowing blocks API.
	 *
	 * @param plugin plugin that will be used to register the events.
	 */
	public GlowingBlocks(@NotNull Plugin plugin) {
		testForPaper();

		this.entities = new GlowingEntities(plugin);

		enable();
	}

	/**
	 * Enables the Glowing blocks API.
	 *
	 * @see #disable()
	 */
	public void enable() {
		if (enabled)
			throw new IllegalStateException("The Glowing Blocks API has already been enabled.");

		entities.plugin.getServer().getPluginManager().registerEvents(this, entities.plugin);
		if (!entities.enabled)
			entities.enable();
		glowing = new HashMap<>();
		enabled = true;
	}

	/**
	 * Disables the API.
	 * <p>
	 * Methods such as {@link #setGlowing(Location, Player, ChatColor)} and
	 * {@link #unsetGlowing(Location, Player)} will no longer be usable.
	 *
	 * @see #enable()
	 */
	public void disable() {
		if (!enabled)
			return;
		HandlerList.unregisterAll(this);
		glowing.values().forEach(playerData -> {
			playerData.datas.values().forEach(glowingData -> {
				try {
					glowingData.remove();
				} catch (ReflectiveOperationException e) {
					e.printStackTrace();
				}
			});
		});
		entities.disable();
		glowing = null;
		enabled = false;
	}

	private void ensureEnabled() {
		if (!enabled)
			throw new IllegalStateException("The Glowing Blocks API is not enabled.");
	}

	private void testForPaper() {
		try {
			Class.forName("io.papermc.paper.event.packet.PlayerChunkLoadEvent"); // NOSONAR
		} catch (ClassNotFoundException ex) {
			throw new UnsupportedOperationException("The GlowingBlocks util can only be used on a Paper server.");
		}
	}

	/**
	 * Makes the {@link Block} passed as a parameter glow with the specified color.
	 *
	 * @param block block to make glow
	 * @param receiver player which will see the block glowing
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(@NotNull Block block, @NotNull Player receiver, @NotNull ChatColor color)
			throws ReflectiveOperationException {
		setGlowing(block.getLocation(), receiver, color);
	}

	/**
	 * Makes the block at the location passed as a parameter glow with the specified color.
	 *
	 * @param block location of the block to make glow
	 * @param receiver player which will see the block glowing
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(@NotNull Location block, @NotNull Player receiver, @NotNull ChatColor color)
			throws ReflectiveOperationException {
		ensureEnabled();

		block = normalizeLocation(block);

		if (!color.isColor())
			throw new IllegalArgumentException("ChatColor must be a color format");

		PlayerData playerData = glowing.computeIfAbsent(Objects.requireNonNull(receiver), PlayerData::new);

		GlowingBlockData blockData = playerData.datas.get(block);
		if (blockData == null) {
			blockData = new GlowingBlockData(receiver, block, color);
			playerData.datas.put(block, blockData);
			if (canSee(receiver, block))
				blockData.spawn();
		} else {
			blockData.setColor(color);
		}
	}

	/**
	 * Makes the {@link Block} passed as a parameter loose its glowing effect.
	 *
	 * @param block block to remove glowing effect from
	 * @param receiver player which will no longer see the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void unsetGlowing(@NotNull Block block, @NotNull Player receiver) throws ReflectiveOperationException {
		unsetGlowing(block.getLocation(), receiver);
	}

	/**
	 * Makes the block at the location passed as a parameter loose its glowing effect.
	 *
	 * @param block location of the block to remove glowing effect from
	 * @param receiver player which will no longer see the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void unsetGlowing(@NotNull Location block, @NotNull Player receiver) throws ReflectiveOperationException {
		ensureEnabled();

		block = normalizeLocation(block);

		PlayerData playerData = glowing.get(receiver);
		if (playerData == null)
			return;

		GlowingBlockData blockData = playerData.datas.remove(block);
		if (blockData == null)
			return; // the player did not have this block glowing

		blockData.remove();

		if (playerData.datas.isEmpty())
			glowing.remove(receiver);
	}

	private @NotNull Location normalizeLocation(@NotNull Location location) {
		// we normalize here all locations to be on the corner of the block
		location.checkFinite();
		return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	private boolean canSee(Player player, Location location) {
		// little Pythagorean theorem with 1 chunk as the unit distance
		int viewDistance = Math.min(player.getViewDistance(), Bukkit.getViewDistance());
		int deltaChunkX = (player.getLocation().getBlockX() >> 4) - (location.getBlockX() >> 4);
		int deltaChunkZ = (player.getLocation().getBlockZ() >> 4) - (location.getBlockZ() >> 4);
		int chunkDistanceSquared = deltaChunkX * deltaChunkX + deltaChunkZ * deltaChunkZ;
		return chunkDistanceSquared <= viewDistance * viewDistance;
	}

	@EventHandler
	public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
		PlayerData playerData = glowing.get(event.getPlayer());
		if (playerData == null)
			return;

		playerData.datas.forEach((location, blockData) -> {
			if (Objects.equals(location.getWorld(), event.getWorld())
					&& location.getBlockX() >> 4 == event.getChunk().getX()
					&& location.getBlockZ() >> 4 == event.getChunk().getZ()) {
				try {
					blockData.spawn();
				} catch (ReflectiveOperationException ex) {
					ex.printStackTrace();
				}
			}
		});
	}

	private record PlayerData(@NotNull Player player, @NotNull Map<Location, GlowingBlockData> datas) {

		public PlayerData(@NotNull Player player) {
			this(player, new HashMap<>());
		}

	}

	private class GlowingBlockData {

		private static final byte FLAGS = 1 << 5; // invisibility flag
		private static final AtomicInteger ENTITY_ID_COUNTER =
				new AtomicInteger(ThreadLocalRandom.current().nextInt(1_000_000, 2_000_000_000));

		private final @NotNull Player player;
		private final @NotNull Location location;

		private @NotNull ChatColor color;
		private int entityId;
		private UUID entityUuid;

		public GlowingBlockData(@NotNull Player player, @NotNull Location location, @NotNull ChatColor color) {
			this.player = player;
			this.location = location;
			this.color = color;
		}

		public void setColor(@NotNull ChatColor color) throws ReflectiveOperationException {
			this.color = color;

			if (entityUuid != null)
				entities.setGlowing(entityId, entityUuid.toString(), player, color, FLAGS);
		}

		public void spawn() throws ReflectiveOperationException {
			init();

			Packets.createEntity(player, entityId, entityUuid, Packets.shulkerEntityType, location);
			Packets.setMetadata(player, entityId, FLAGS, false);
			// this will take care of refreshing the color thanks to the packet handler in GlowingEntities
		}

		public void remove() throws ReflectiveOperationException {
			if (entityUuid == null)
				return;

			Packets.removeEntities(player, entityId);
			entities.unsetGlowing(entityId, player);
		}

		private void init() throws ReflectiveOperationException {
			if (entityUuid == null) {
				entityId = ENTITY_ID_COUNTER.getAndIncrement();
				entityUuid = UUID.randomUUID();

				setColor(color);
			}
		}

	}

}
