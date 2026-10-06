package fr.skytasul.glowingentities;

import fr.skytasul.glowingentities.GlowingEntities.EntityIdentifierEID;
import fr.skytasul.glowingentities.GlowingEntities.Packets;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes blocks glow for individual players using invisible Shulkers or BlockDisplays.
 * Calling a set method again for the same block position and player updates the effect.
 * <p>
 * <i>Can only be used on Paper-based servers.</i>
 *
 * @author SkytAsul
 */
public class GlowingBlocks implements Listener {

	private static final BlockFace[] FACES = {BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
			BlockFace.WEST, BlockFace.EAST};

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

		// Avoid scanning the Display.Brightness signatures on older servers.
		entities.plugin.getServer().getPluginManager().registerEvent(PlayerChunkLoadEvent.class, this, EventPriority.NORMAL,
				(listener, event) -> onPlayerChunkLoad((PlayerChunkLoadEvent) event), entities.plugin);
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
	 * <p>
	 * On Minecraft 1.19.4 and newer, partial blocks use a BlockDisplay. Full blocks, air and
	 * liquids use a Shulker, as do all blocks on older servers.
	 *
	 * @param block location of the block to make glow
	 * @param receiver player which will see the block glowing
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(@NotNull Location block, @NotNull Player receiver, @NotNull ChatColor color)
			throws ReflectiveOperationException {
		setGlowingBlock(block, receiver, Mode.AUTO, color, null, null);
	}

	/**
	 * Shows a glowing cube outline around the {@link Block} using a Shulker.
	 *
	 * @param block block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void setGlowingShulker(@NotNull Block block, @NotNull Player receiver, @NotNull ChatColor color)
			throws ReflectiveOperationException {
		setGlowingShulker(block.getLocation(), receiver, color);
	}

	/**
	 * Shows a glowing cube outline at the given location using a Shulker.
	 *
	 * @param location location of the block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void setGlowingShulker(@NotNull Location location, @NotNull Player receiver, @NotNull ChatColor color)
			throws ReflectiveOperationException {
		setGlowingBlock(location, receiver, Mode.SHULKER, color, null, null);
	}

	/**
	 * Shows the glowing block model with the specified color and automatic brightness.
	 *
	 * @param block block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color color of the glowing effect
	 * @throws UnsupportedOperationException if the server is older than Minecraft 1.19.4
	 * @throws ReflectiveOperationException
	 * @see #setGlowingDisplay(Location, Player, ChatColor, Brightness)
	 */
	public void setGlowingDisplay(@NotNull Block block, @NotNull Player receiver, @NotNull ChatColor color)
			throws ReflectiveOperationException {
		setGlowingDisplay(block.getLocation(), receiver, color);
	}

	/**
	 * Shows the glowing block model at the given location with the specified color and automatic brightness.
	 *
	 * @param location location of the block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color color of the glowing effect
	 * @throws UnsupportedOperationException if the server is older than Minecraft 1.19.4
	 * @throws ReflectiveOperationException
	 * @see #setGlowingDisplay(Location, Player, ChatColor, Brightness)
	 */
	public void setGlowingDisplay(@NotNull Location location, @NotNull Player receiver, @NotNull ChatColor color)
			throws ReflectiveOperationException {
		setGlowingDisplay(location, receiver, color, null);
	}

	/**
	 * Shows the glowing block model with the specified color and automatic brightness.
	 *
	 * @param block block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color RGB color of the glowing effect
	 * @throws UnsupportedOperationException if the server is older than Minecraft 1.19.4
	 * @throws ReflectiveOperationException
	 * @see #setGlowingDisplay(Location, Player, ChatColor, Brightness)
	 */
	public void setGlowingDisplay(@NotNull Block block, @NotNull Player receiver, @NotNull Color color)
			throws ReflectiveOperationException {
		setGlowingDisplay(block.getLocation(), receiver, color);
	}

	/**
	 * Shows the glowing block model at the given location with the specified color and automatic brightness.
	 *
	 * @param location location of the block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color RGB color of the glowing effect
	 * @throws UnsupportedOperationException if the server is older than Minecraft 1.19.4
	 * @throws ReflectiveOperationException
	 * @see #setGlowingDisplay(Location, Player, ChatColor, Brightness)
	 */
	public void setGlowingDisplay(@NotNull Location location, @NotNull Player receiver, @NotNull Color color)
			throws ReflectiveOperationException {
		setGlowingDisplay(location, receiver, color, null);
	}

	/**
	 * Shows the glowing block model with the specified color and brightness.
	 *
	 * @param block block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color color of the glowing effect
	 * @param brightness block and sky light levels, or {@code null} to estimate them
	 * @throws UnsupportedOperationException if the server is older than Minecraft 1.19.4
	 * @throws ReflectiveOperationException
	 * @see #setGlowingDisplay(Location, Player, ChatColor, Brightness)
	 */
	public void setGlowingDisplay(@NotNull Block block, @NotNull Player receiver, @NotNull ChatColor color,
			@Nullable Brightness brightness) throws ReflectiveOperationException {
		setGlowingDisplay(block.getLocation(), receiver, color, brightness);
	}

	/**
	 * Shows the glowing block model at the given location with the specified color and brightness.
	 * <p>
	 * Automatic brightness averages block and sky light separately from exposed sides. An explicit
	 * brightness is kept when the player reloads the chunk.
	 *
	 * @param location location of the block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color color of the glowing effect
	 * @param brightness block and sky light levels, or {@code null} to estimate them
	 * @throws UnsupportedOperationException if the server is older than Minecraft 1.19.4
	 * @throws ReflectiveOperationException
	 */
	public void setGlowingDisplay(@NotNull Location location, @NotNull Player receiver, @NotNull ChatColor color,
			@Nullable Brightness brightness) throws ReflectiveOperationException {
		Packets.ensureBlockDisplaysInitialized();
		setGlowingBlock(location, receiver, Mode.DISPLAY, color, null, brightness);
	}

	/**
	 * Shows the glowing block model with the specified color and brightness.
	 *
	 * @param block block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color RGB color of the glowing effect
	 * @param brightness block and sky light levels, or {@code null} to estimate them
	 * @throws UnsupportedOperationException if the server is older than Minecraft 1.19.4
	 * @throws ReflectiveOperationException
	 * @see #setGlowingDisplay(Location, Player, ChatColor, Brightness)
	 */
	public void setGlowingDisplay(@NotNull Block block, @NotNull Player receiver, @NotNull Color color,
			@Nullable Brightness brightness) throws ReflectiveOperationException {
		setGlowingDisplay(block.getLocation(), receiver, color, brightness);
	}

	/**
	 * Shows the glowing block model at the given location with the specified color and brightness.
	 *
	 * @param location location of the block to make glow
	 * @param receiver player who will see the block glowing
	 * @param color RGB color of the glowing effect
	 * @param brightness block and sky light levels, or {@code null} to estimate them
	 * @throws UnsupportedOperationException if the server is older than Minecraft 1.19.4
	 * @throws ReflectiveOperationException
	 * @see #setGlowingDisplay(Location, Player, ChatColor, Brightness)
	 */
	public void setGlowingDisplay(@NotNull Location location, @NotNull Player receiver, @NotNull Color color,
			@Nullable Brightness brightness) throws ReflectiveOperationException {
		Packets.ensureBlockDisplaysInitialized();
		setGlowingBlock(location, receiver, Mode.DISPLAY, null, color, brightness);
	}

	private void setGlowingBlock(Location location, Player receiver, Mode mode, @Nullable ChatColor color,
			@Nullable Color rgbColor, @Nullable Brightness brightness) throws ReflectiveOperationException {
		ensureEnabled();

		location = normalizeLocation(location);

		if (rgbColor == null && !Objects.requireNonNull(color).isColor())
			throw new IllegalArgumentException("ChatColor must be a color format");

		PlayerData playerData = glowing.computeIfAbsent(Objects.requireNonNull(receiver), PlayerData::new);
		GlowingBlockData blockData =
				playerData.datas.computeIfAbsent(location, key -> new GlowingBlockData(playerData.player, key, color));
		blockData.setGlow(mode, color, rgbColor == null ? 0 : rgbColor.asRGB(), brightness);
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

		GlowingBlockData blockData = playerData.datas.get(block);
		if (blockData == null)
			return; // the player did not have this block glowing

		blockData.remove();
		playerData.datas.remove(block);

		if (playerData.datas.isEmpty())
			glowing.remove(receiver);
	}

	private @NotNull Location normalizeLocation(@NotNull Location location) {
		// we normalize here all locations to be on the corner of the block
		location.checkFinite();
		return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	private boolean canSee(Player player, Location location) {
		if (!Objects.equals(player.getWorld(), location.getWorld()))
			return false;

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
					blockData.show(true);
				} catch (ReflectiveOperationException ex) {
					ex.printStackTrace();
				}
			}
		});
	}

	private static boolean isChunkLoaded(Location location) {
		return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
	}

	private static boolean isPartialBlock(Block block) {
		if (block.getType().isAir() || block.isLiquid())
			return false;

		var data = block.getBlockData();
		for (BlockFace face : FACES) {
			if (!data.isFaceSturdy(face, BlockSupport.FULL))
				return true;
		}
		return false;
	}

	private static Brightness estimateBrightness(Block block) {
		var world = block.getWorld();
		int blockLight = 0;
		int skyLight = 0;
		int exposedFaces = 0;
		for (BlockFace face : FACES) {
			Block neighbour = block.getRelative(face);
			if (!world.isChunkLoaded(neighbour.getX() >> 4, neighbour.getZ() >> 4))
				continue;

			var neighbourData = neighbour.getBlockData();
			if (neighbourData.isOccluding() && neighbourData.isFaceSturdy(face.getOppositeFace(), BlockSupport.FULL))
				continue;

			blockLight += neighbour.getLightFromBlocks();
			skyLight += neighbour.getLightFromSky();
			exposedFaces++;
		}

		if (exposedFaces == 0)
			return new Brightness(block.getLightFromBlocks(), block.getLightFromSky());

		return new Brightness(Math.round((float) blockLight / exposedFaces),
				Math.round((float) skyLight / exposedFaces));
	}

	private enum Mode {
		AUTO, SHULKER, DISPLAY
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

		private @Nullable ChatColor color;
		private int entityId;
		private UUID entityUuid;
		private Mode mode;
		private int rgb;
		private @Nullable Brightness brightness;
		private boolean display;

		public GlowingBlockData(@NotNull Player player, @NotNull Location location, @Nullable ChatColor color) {
			this.player = player;
			this.location = location;
			this.color = color;
		}

		private EntityIdentifierEID getEntityIdentifier() {
			return new GlowingEntities.EntityIdentifierEID(entityId, location.getWorld());
		}

		public void setColor(@NotNull ChatColor color) throws ReflectiveOperationException {
			this.color = color;

			if (entityUuid != null && !display)
				entities.setGlowing(getEntityIdentifier(), entityUuid.toString(), player, color, FLAGS);
		}

		void setGlow(Mode mode, @Nullable ChatColor color, int rgb, @Nullable Brightness brightness)
				throws ReflectiveOperationException {
			this.mode = mode;
			this.color = color;
			this.rgb = rgb;
			this.brightness = brightness;

			if (canSee(player, location))
				show(false);
			else if (entityUuid != null && !display && mode != Mode.DISPLAY
					&& Objects.equals(player.getWorld(), location.getWorld()))
				setColor(Objects.requireNonNull(color));
		}

		public void spawn() throws ReflectiveOperationException {
			init();

			Packets.createEntity(player, entityId, entityUuid,
					display ? Packets.blockDisplayEntityType : Packets.shulkerEntityType, location);
			if (display) {
				sendDisplayMetadata();
			} else {
				Packets.setMetadata(player, getEntityIdentifier(), FLAGS, false);
				// this will take care of refreshing the color thanks to the packet handler in GlowingEntities
			}
		}

		private void show(boolean chunkLoad) throws ReflectiveOperationException {
			boolean autoDisplay = mode == Mode.AUTO && Packets.supportsBlockDisplays();
			if ((mode == Mode.DISPLAY || autoDisplay) && !isChunkLoaded(location))
				return;

			boolean useDisplay = mode == Mode.DISPLAY || autoDisplay && isPartialBlock(location.getBlock());
			if (useDisplay)
				Packets.ensureBlockDisplaysInitialized();

			if (entityUuid != null && display != useDisplay)
				remove();

			boolean firstSpawn = entityUuid == null;
			try {
				if (firstSpawn)
					display = useDisplay;
				else if (!display)
					setColor(Objects.requireNonNull(color));

				if (firstSpawn || chunkLoad)
					spawn();
				else if (display)
					sendDisplayMetadata();
			} catch (ReflectiveOperationException ex) {
				if (firstSpawn) {
					try {
						remove();
					} catch (ReflectiveOperationException cleanup) {
						ex.addSuppressed(cleanup);
					}
				}
				throw ex;
			}
		}

		private void sendDisplayMetadata() throws ReflectiveOperationException {
			Block block = location.getBlock();
			Brightness light = brightness == null ? estimateBrightness(block) : brightness;
			int displayColor = color == null ? rgb : color.asBungee().getColor().getRGB() & 0xFFFFFF;

			Packets.setBlockDisplayMetadata(player, getEntityIdentifier(), block.getBlockData(), displayColor,
					light.getBlockLight(), light.getSkyLight());
		}

		public void remove() throws ReflectiveOperationException {
			if (entityUuid == null)
				return;

			Packets.removeEntities(player, entityId);
			if (!display)
				entities.unsetGlowing(getEntityIdentifier(), player);
			entityUuid = null;
		}

		private void init() throws ReflectiveOperationException {
			if (entityUuid == null) {
				entityId = ENTITY_ID_COUNTER.getAndIncrement();
				entityUuid = UUID.randomUUID();

				if (!display)
					setColor(Objects.requireNonNull(color));
			}
		}

	}

}
