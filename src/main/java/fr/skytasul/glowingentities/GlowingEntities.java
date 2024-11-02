package fr.skytasul.glowingentities;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import fr.skytasul.reflection.MappedReflectionAccessor;
import fr.skytasul.reflection.ReflectionAccessor;
import fr.skytasul.reflection.ReflectionAccessor.ClassAccessor;
import fr.skytasul.reflection.ReflectionAccessor.ClassAccessor.FieldAccessor;
import fr.skytasul.reflection.TransparentReflectionAccessor;
import fr.skytasul.reflection.Version;
import fr.skytasul.reflection.mappings.files.MappingFileReader;
import fr.skytasul.reflection.mappings.files.ProguardMapping;
import io.netty.channel.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A Spigot util to easily make entities glow.
 * <p>
 * <b>1.17 -> 1.21</b>
 *
 * @version 1.3.5
 * @author SkytAsul
 */
public class GlowingEntities implements Listener {

	protected final @NotNull Plugin plugin;
	private Map<Player, PlayerData> glowing;
	boolean enabled = false;

	private int uid;

	/**
	 * Initializes the Glowing API.
	 *
	 * @param plugin plugin that will be used to register the events.
	 */
	public GlowingEntities(@NotNull Plugin plugin) {
		Packets.ensureInitialized();

		this.plugin = Objects.requireNonNull(plugin);

		enable();
	}

	/**
	 * Enables the Glowing API.
	 *
	 * @see #disable()
	 */
	public void enable() {
		if (enabled)
			throw new IllegalStateException("The Glowing Entities API has already been enabled.");

		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		glowing = new HashMap<>();
		uid = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
		enabled = true;
	}

	/**
	 * Disables the API.
	 * <p>
	 * Methods such as {@link #setGlowing(int, String, Player, ChatColor, byte)} and
	 * {@link #unsetGlowing(int, Player)} will no longer be usable.
	 *
	 * @see #enable()
	 */
	public void disable() {
		if (!enabled)
			return;
		HandlerList.unregisterAll(this);
		glowing.values().forEach(playerData -> {
			try {
				Packets.removePacketsHandler(playerData);
			} catch (ReflectiveOperationException e) {
				e.printStackTrace();
			}
		});
		glowing = null;
		uid = 0;
		enabled = false;
	}

	private void ensureEnabled() {
		if (!enabled)
			throw new IllegalStateException("The Glowing Entities API is not enabled.");
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		glowing.remove(event.getPlayer());
	}

	/**
	 * Make the {@link Entity} passed as a parameter glow with its default team color.
	 *
	 * @param entity entity to make glow
	 * @param receiver player which will see the entity glowing
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(Entity entity, Player receiver) throws ReflectiveOperationException {
		setGlowing(entity, receiver, null);
	}

	/**
	 * Make the {@link Entity} passed as a parameter glow with the specified color.
	 *
	 * @param entity entity to make glow
	 * @param receiver player which will see the entity glowing
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(Entity entity, Player receiver, ChatColor color) throws ReflectiveOperationException {
		String teamID = entity instanceof Player ? entity.getName() : entity.getUniqueId().toString();
		setGlowing(entity.getEntityId(), teamID, receiver, color, Packets.getEntityFlags(entity));
	}

	/**
	 * Make the entity with specified entity ID glow with its default team color.
	 *
	 * @param entityID entity id of the entity to make glow
	 * @param teamID internal string used to add the entity to a team
	 * @param receiver player which will see the entity glowing
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(int entityID, String teamID, Player receiver) throws ReflectiveOperationException {
		setGlowing(entityID, teamID, receiver, null, (byte) 0);
	}

	/**
	 * Make the entity with specified entity ID glow with the specified color.
	 *
	 * @param entityID entity id of the entity to make glow
	 * @param teamID internal string used to add the entity to a team
	 * @param receiver player which will see the entity glowing
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(int entityID, String teamID, Player receiver, ChatColor color)
			throws ReflectiveOperationException {
		setGlowing(entityID, teamID, receiver, color, (byte) 0);
	}

	/**
	 * Make the entity with specified entity ID glow with the specified color, and keep some flags.
	 *
	 * @param entityID entity id of the entity to make glow
	 * @param teamID internal string used to add the entity to a team
	 * @param receiver player which will see the entity glowing
	 * @param color color of the glowing effect
	 * @param otherFlags internal flags that must be kept (on fire, crouching...). See
	 *        <a href="https://wiki.vg/Entity_metadata#Entity">wiki.vg</a> for more informations.
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(int entityID, String teamID, Player receiver, ChatColor color, byte otherFlags)
			throws ReflectiveOperationException {
		ensureEnabled();
		if (color != null && !color.isColor())
			throw new IllegalArgumentException("ChatColor must be a color format");

		PlayerData playerData = glowing.get(receiver);
		if (playerData == null) {
			playerData = new PlayerData(this, receiver);
			Packets.addPacketsHandler(playerData);
			glowing.put(receiver, playerData);
		}

		GlowingData glowingData = playerData.glowingDatas.get(entityID);
		if (glowingData == null) {
			// the player did not have datas related to the entity: we must create the glowing status
			glowingData = new GlowingData(playerData, entityID, teamID, color, otherFlags);
			playerData.glowingDatas.put(entityID, glowingData);

			Packets.createGlowing(glowingData);
			if (color != null)
				Packets.setGlowingColor(glowingData);
		} else {
			// the player already had datas related to the entity: we must update the glowing status

			if (Objects.equals(glowingData.color, color))
				return; // nothing changed

			if (color == null) {
				Packets.removeGlowingColor(glowingData);
				glowingData.color = color; // we must set the color after in order to fetch the previous team
			} else {
				glowingData.color = color;
				Packets.setGlowingColor(glowingData);
			}
		}
	}

	/**
	 * Make the {@link Entity} passed as a parameter loose its custom glowing effect.
	 * <p>
	 * This has <b>no effect</b> on glowing status given by another plugin or vanilla behavior.
	 *
	 * @param entity entity to remove glowing effect from
	 * @param receiver player which will no longer see the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void unsetGlowing(Entity entity, Player receiver) throws ReflectiveOperationException {
		unsetGlowing(entity.getEntityId(), receiver);
	}

	/**
	 * Make the entity with specified entity ID passed as a parameter loose its custom glowing effect.
	 * <p>
	 * This has <b>no effect</b> on glowing status given by another plugin or vanilla behavior.
	 *
	 * @param entityID entity id of the entity to remove glowing effect from
	 * @param receiver player which will no longer see the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void unsetGlowing(int entityID, Player receiver) throws ReflectiveOperationException {
		ensureEnabled();
		PlayerData playerData = glowing.get(receiver);
		if (playerData == null)
			return; // the player do not have any entity glowing

		GlowingData glowingData = playerData.glowingDatas.remove(entityID);
		if (glowingData == null)
			return; // the player did not have this entity glowing

		Packets.removeGlowing(glowingData);

		if (glowingData.color != null)
			Packets.removeGlowingColor(glowingData);

		/*
		 * if (playerData.glowingDatas.isEmpty()) { //NOSONAR // if the player do not have any other entity
		 * glowing, // we can safely remove all of its data to free some memory
		 * Packets.removePacketsHandler(playerData); glowing.remove(receiver); }
		 */
		// actually no, we should not remove the player datas
		// as it stores which teams did it receive.
		// if we do not save this information, team would be created
		// twice for the player, and BungeeCord does not like that
	}

	private static class PlayerData {

		final GlowingEntities instance;
		final Player player;
		final Map<Integer, GlowingData> glowingDatas;
		ChannelHandler packetsHandler;
		EnumSet<ChatColor> sentColors;

		PlayerData(GlowingEntities instance, Player player) {
			this.instance = instance;
			this.player = player;
			this.glowingDatas = new HashMap<>();
		}

	}

	private static class GlowingData {
		// unfortunately this cannot be a Java Record
		// as the "color" field is not final

		final PlayerData player;
		final int entityID;
		final String teamID;
		ChatColor color;
		byte otherFlags;
		boolean enabled;

		GlowingData(PlayerData player, int entityID, String teamID, ChatColor color, byte otherFlags) {
			this.player = player;
			this.entityID = entityID;
			this.teamID = teamID;
			this.color = color;
			this.otherFlags = otherFlags;
			this.enabled = true;
		}

	}

	protected static class Packets {

		private static final byte GLOWING_FLAG = 1 << 6;

		private static Cache<Object, Object> packets =
				CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();
		private static Object dummy = new Object();

		private static Logger logger;
		private static String cpack;
		private static Version version;

		private static boolean isEnabled = false;
		private static boolean hasInitialized = false;
		private static Throwable initializationError = null;

		private static Method getHandle;
		private static Method getDataWatcher;

		// Synched datas
		private static Object watcherObjectFlags;
		private static Object watcherDummy;
		private static Method watcherGet;

		private static Constructor<?> watcherItemConstructor;
		private static Method watcherItemObject;
		private static Method watcherItemDataGet;

		private static Method watcherBCreator;
		private static Method watcherBId;
		private static Method watcherBSerializer;
		private static Method watcherSerializerObject;

		// Networking
		private static Field playerConnection;
		private static Method sendPacket;
		private static Field networkManager;
		private static Field channelField;
		private static ClassAccessor packetBundle;
		private static Method packetBundlePackets;

		// Metadata
		private static ClassAccessor packetMetadata;
		private static Constructor<?> packetMetadataConstructor;
		private static Field packetMetadataEntity;
		private static Field packetMetadataItems;

		// Teams
		private static EnumMap<ChatColor, TeamData> teams = new EnumMap<>(ChatColor.class);

		private static Constructor<?> createTeamPacket;
		private static Constructor<?> createTeamPacketData;
		private static Constructor<?> createTeam;
		private static Object scoreboardDummy;
		private static Object pushNever;
		private static Method setTeamPush;
		private static Method setTeamColor;
		private static Method getColorConstant;

		// Entities
		protected static Object shulkerEntityType;
		private static Constructor<?> packetAddEntity;
		private static Constructor<?> packetRemove;
		private static Object vec3dZero;

		protected static void ensureInitialized() {
			if (!hasInitialized)
				initialize();

			if (!isEnabled)
				throw new IllegalStateException(
						"The Glowing Entities API is disabled. An error has occured during first initialization.",
						initializationError);
		}

		private static void initialize() {
			hasInitialized = true;
			try {
				logger = new Logger("GlowingEntities", null) {
					@Override
					public void log(LogRecord logRecord) {
						logRecord.setMessage("[GlowingEntities] " + logRecord.getMessage());
						super.log(logRecord);
					}
				};
				logger.setParent(Bukkit.getServer().getLogger());
				logger.setLevel(Level.ALL);

				// e.g. Bukkit.getBukkitVersion() -> 1.17.1-R0.1-SNAPSHOT
				var versionString = Bukkit.getBukkitVersion().split("-R")[0];
				var serverVersion = Version.parse(versionString);
				logger.info("Found server version " + serverVersion);

				cpack = Bukkit.getServer().getClass().getPackage().getName() + ".";

				boolean remapped = Bukkit.getServer().getClass().getPackage().getName().split("\\.").length == 3;
				ReflectionAccessor reflection;

				if (remapped) {
					version = serverVersion;
					reflection = new TransparentReflectionAccessor();
					logger.info("Loaded transparent mappings.");
				} else {
					var mappingsFile =
							new String(GlowingEntities.class.getResourceAsStream("mappings/spigot.txt").readAllBytes());
					var mappingsReader = new MappingFileReader(new ProguardMapping(false), mappingsFile.lines().toList());
					mappingsReader.readAvailableVersions();
					var foundVersion = mappingsReader.keepBestMatchedVersion(serverVersion);

					if (foundVersion.isEmpty())
						throw new UnsupportedOperationException("Cannot find mappings to match server version");

					if (!foundVersion.get().is(serverVersion))
						logger.warning("Loaded not matching version of the mappings for your server version");

					version = foundVersion.get();
					mappingsReader.parseMappings();
					var mappings = mappingsReader.getParsedMappings(foundVersion.get());
					logger.info("Loaded mappings for " + version);
					reflection = new MappedReflectionAccessor(mappings);
				}

				loadReflection(reflection, version);

				isEnabled = true;
			} catch (Exception ex) {
				initializationError = ex;

				String errorMsg =
						"Glowing Entities reflection failed to initialize. The util is disabled. Please ensure your version ("
								+ Bukkit.getBukkitVersion() + ") is supported.";
				if (logger == null) {
					ex.printStackTrace();
					System.err.println(errorMsg);
				} else {
					logger.log(Level.SEVERE, errorMsg, ex);
				}
			}
		}

		protected static void loadReflection(@NotNull ReflectionAccessor reflection, @NotNull Version version)
				throws ReflectiveOperationException {
			/* Global variables */

			var entityClass = getNMSClass(reflection, "world.entity", "Entity");
			var entityTypesClass = getNMSClass(reflection, "world.entity", "EntityType");
			Object markerEntity = getNMSClass(reflection, "world.entity", "Marker")
					.getConstructor(entityTypesClass, getNMSClass(reflection, "world.level", "Level"))
					.newInstance(entityTypesClass.getField("MARKER").get(null), null);

			getHandle = cpack == null ? null : getCraftClass("entity", "CraftEntity").getDeclaredMethod("getHandle");
			getDataWatcher = entityClass.getMethodInstance("getEntityData");

			/* Synched datas */

			ClassAccessor dataWatcherClass = getNMSClass(reflection, "network.syncher", "SynchedEntityData");

			if (version.isAfter(1, 20, 5)) {
				ClassAccessor dataWatcherBuilderClass =
						getNMSClass(reflection, "network.syncher", "SynchedEntityData$Builder");
				var watcherBuilder = dataWatcherBuilderClass
						.getConstructor(getNMSClass(reflection, "network.syncher", "SyncedDataHolder"))
						.newInstance(markerEntity);
				FieldAccessor watcherBuilderItems = dataWatcherBuilderClass.getField("itemsById");
				watcherBuilderItems.set(watcherBuilder,
						Array.newInstance(
								getNMSClass(reflection, "network.syncher", "SynchedEntityData$DataItem").getClassInstance(),
								0));
				watcherDummy = dataWatcherBuilderClass.getMethod("build").invoke(watcherBuilder);
			} else {
				watcherDummy = dataWatcherClass.getConstructor(entityClass).newInstance(markerEntity);
			}

			ClassAccessor entityDataAccessorClass = getNMSClass(reflection, "network.syncher", "EntityDataAccessor");
			watcherObjectFlags = entityClass.getField("DATA_SHARED_FLAGS_ID").get(null);
			watcherGet = dataWatcherClass.getMethodInstance("get", entityDataAccessorClass);

			if (!version.isAfter(1, 19, 3)) {
				ClassAccessor watcherItemClass = getNMSClass(reflection, "network.syncher", "SynchedEntityData$DataItem");
				watcherItemConstructor =
						watcherItemClass.getConstructorInstance(entityDataAccessorClass, Object.class);
				watcherItemObject = watcherItemClass.getMethodInstance("getAccessor");
				watcherItemDataGet = watcherItemClass.getMethodInstance("getValue");
			} else {
				ClassAccessor watcherValueClass = getNMSClass(reflection, "network.syncher", "SynchedEntityData$DataValue");
				watcherBCreator =
						watcherValueClass.getMethodInstance("create", entityDataAccessorClass, Object.class);
				watcherBId = watcherValueClass.getMethodInstance("id");
				watcherBSerializer = watcherValueClass.getMethodInstance("serializer");
				watcherItemDataGet = watcherValueClass.getMethodInstance("value");
				watcherSerializerObject = getNMSClass(reflection, "network.syncher", "EntityDataSerializer")
						.getMethodInstance("createAccessor", int.class);
			}

			/* Networking */

			playerConnection = getNMSClass(reflection, "server.level", "ServerPlayer").getFieldInstance("connection");
			ClassAccessor packetListenerClass =
					getNMSClass(reflection, "server.network", version.isAfter(1, 20, 2)
							? "ServerCommonPacketListenerImpl"
							: "ServerGamePacketListenerImpl");

			sendPacket =
					packetListenerClass.getMethodInstance("send", getNMSClass(reflection, "network.protocol", "Packet"));
			networkManager = packetListenerClass.getFieldInstance("connection");
			channelField = getNMSClass(reflection, "network", "Connection").getFieldInstance("channel");

			if (version.isAfter(1, 19, 4)) {
				packetBundle = getNMSClass(reflection, "network.protocol", "BundlePacket");
				packetBundlePackets = packetBundle.getMethodInstance("subPackets");
			}

			/* Metadata */

			packetMetadata = getNMSClass(reflection, "network.protocol.game", "ClientboundSetEntityDataPacket");
			packetMetadataEntity = packetMetadata.getFieldInstance("id");
			packetMetadataItems = packetMetadata.getFieldInstance("packedItems");
			if (version.isAfter(1, 19, 3)) {
				packetMetadataConstructor = packetMetadata.getConstructorInstance(int.class, List.class);
			} else {
				packetMetadataConstructor =
						packetMetadata.getConstructorInstance(int.class, dataWatcherClass, boolean.class);
			}

			/* Teams */

			ClassAccessor scoreboardClass = getNMSClass(reflection, "world.scores", "Scoreboard");
			ClassAccessor teamClass = getNMSClass(reflection, "world.scores", "PlayerTeam");
			ClassAccessor pushClass = getNMSClass(reflection, "world.scores", "Team$CollisionRule");
			ClassAccessor chatFormatClass = getNMSClass(reflection, "ChatFormatting");

			createTeamPacket = getNMSClass(reflection, "network.protocol.game", "ClientboundSetPlayerTeamPacket")
					.getConstructorInstance(String.class, int.class, Optional.class, Collection.class);
			createTeamPacketData =
					getNMSClass(reflection, "network.protocol.game", "ClientboundSetPlayerTeamPacket$Parameters")
							.getConstructorInstance(teamClass);
			createTeam = teamClass.getConstructorInstance(scoreboardClass, String.class);
			scoreboardDummy = scoreboardClass.getConstructor().newInstance();
			pushNever = pushClass.getField("NEVER").get(null);
			setTeamPush = teamClass.getMethodInstance("setCollisionRule", pushClass);
			setTeamColor = teamClass.getMethodInstance("setColor", chatFormatClass);
			getColorConstant = chatFormatClass.getMethodInstance("getByCode", char.class);

			/* Entities */

			shulkerEntityType = entityTypesClass.getField("SHULKER").get(null);

			ClassAccessor vec3dClass = getNMSClass(reflection, "world.phys", "Vec3");
			vec3dZero = vec3dClass.getConstructor(double.class, double.class, double.class).newInstance(0d, 0d, 0d);


			// arg10 was added after version 1.18.2
			ClassAccessor addEntityPacketClass =
					getNMSClass(reflection, "network.protocol.game", "ClientboundAddEntityPacket");
			if (version.isAfter(1, 19, 0)) {
				packetAddEntity = addEntityPacketClass.getConstructorInstance(int.class, UUID.class, double.class,
						double.class, double.class, float.class, float.class, entityTypesClass, int.class, vec3dClass,
						double.class);
			} else {
				packetAddEntity = addEntityPacketClass.getConstructorInstance(int.class, UUID.class, double.class,
						double.class, double.class, float.class, float.class, entityTypesClass, int.class, vec3dClass);
			}


			packetRemove = version.is(1, 17, 0)
					? getNMSClass(reflection, "network.protocol.game", "ClientboundRemoveEntityPacket")
							.getConstructorInstance(int.class)
					: getNMSClass(reflection, "network.protocol.game", "ClientboundRemoveEntitiesPacket")
							.getConstructorInstance(int[].class);
		}

		public static void sendPackets(Player p, Object... packets) throws ReflectiveOperationException {
			Object connection = playerConnection.get(getHandle.invoke(p));
			for (Object packet : packets) {
				if (packet == null)
					continue;
				sendPacket.invoke(connection, packet);
			}
		}

		public static byte getEntityFlags(Entity entity) throws ReflectiveOperationException {
			Object nmsEntity = getHandle.invoke(entity);
			Object dataWatcher = getDataWatcher.invoke(nmsEntity);
			return (byte) watcherGet.invoke(dataWatcher, watcherObjectFlags);
		}

		public static void createGlowing(GlowingData glowingData) throws ReflectiveOperationException {
			setMetadata(glowingData.player.player, glowingData.entityID, computeFlags(glowingData), true);
		}

		private static byte computeFlags(GlowingData glowingData) {
			byte newFlags = glowingData.otherFlags;
			if (glowingData.enabled) {
				newFlags |= GLOWING_FLAG;
			} else {
				newFlags &= ~GLOWING_FLAG;
			}
			return newFlags;
		}

		public static Object createFlagWatcherItem(byte newFlags) throws ReflectiveOperationException {
			return watcherItemConstructor != null ? watcherItemConstructor.newInstance(watcherObjectFlags, newFlags)
					: watcherBCreator.invoke(null, watcherObjectFlags, newFlags);
		}

		public static void removeGlowing(GlowingData glowingData) throws ReflectiveOperationException {
			setMetadata(glowingData.player.player, glowingData.entityID, glowingData.otherFlags, true);
		}

		public static void updateGlowingState(GlowingData glowingData) throws ReflectiveOperationException {
			if (glowingData.enabled)
				createGlowing(glowingData);
			else
				removeGlowing(glowingData);
		}

		public static void setMetadata(Player player, int entityId, byte flags, boolean ignore)
				throws ReflectiveOperationException {
			List<Object> dataItems = new ArrayList<>(1);
			dataItems.add(watcherItemConstructor != null ? watcherItemConstructor.newInstance(watcherObjectFlags, flags)
					: watcherBCreator.invoke(null, watcherObjectFlags, flags));

			Object packetMetadata;
			if (version.isBefore(1, 19, 3)) {
				packetMetadata = packetMetadataConstructor.newInstance(entityId, watcherDummy, false);
				packetMetadataItems.set(packetMetadata, dataItems);
			} else {
				packetMetadata = packetMetadataConstructor.newInstance(entityId, dataItems);
			}
			if (ignore)
				packets.put(packetMetadata, dummy);
			sendPackets(player, packetMetadata);
		}

		public static void setGlowingColor(GlowingData glowingData) throws ReflectiveOperationException {
			boolean sendCreation = false;
			if (glowingData.player.sentColors == null) {
				glowingData.player.sentColors = EnumSet.of(glowingData.color);
				sendCreation = true;
			} else if (glowingData.player.sentColors.add(glowingData.color)) {
				sendCreation = true;
			}

			TeamData teamData = teams.get(glowingData.color);
			if (teamData == null) {
				teamData = new TeamData(glowingData.player.instance.uid, glowingData.color);
				teams.put(glowingData.color, teamData);
			}

			Object entityAddPacket = teamData.getEntityAddPacket(glowingData.teamID);
			if (sendCreation) {
				sendPackets(glowingData.player.player, teamData.creationPacket, entityAddPacket);
			} else {
				sendPackets(glowingData.player.player, entityAddPacket);
			}
		}

		public static void removeGlowingColor(GlowingData glowingData) throws ReflectiveOperationException {
			TeamData teamData = teams.get(glowingData.color);
			if (teamData == null)
				return; // must not happen; this means the color has not been set previously

			sendPackets(glowingData.player.player, teamData.getEntityRemovePacket(glowingData.teamID));
		}

		public static void createEntity(Player player, int entityId, UUID entityUuid, Object entityType, Location location)
				throws IllegalArgumentException, ReflectiveOperationException {
			Object packet;
			if (version.isAfter(1, 19, 0)) {
				packet = packetAddEntity.newInstance(entityId, entityUuid, location.getX(), location.getY(),
						location.getZ(), location.getPitch(), location.getYaw(), entityType, 0, vec3dZero, 0d);
			} else {
				packet = packetAddEntity.newInstance(entityId, entityUuid, location.getX(), location.getY(),
						location.getZ(), location.getPitch(), location.getYaw(), entityType, 0, vec3dZero);
			}
			sendPackets(player, packet);
		}

		public static void removeEntities(Player player, int... entitiesId) throws ReflectiveOperationException {
			Object[] packets;
			if (version.is(1, 17, 0)) {
				packets = new Object[entitiesId.length];
				for (int i = 0; i < entitiesId.length; i++) {
					packets[i] = packetRemove.newInstance(entitiesId[i]);
				}
			} else {
				packets = new Object[] {packetRemove.newInstance(entitiesId)};
			}

			sendPackets(player, packets);
		}

		private static Channel getChannel(Player player) throws ReflectiveOperationException {
			return (Channel) channelField.get(networkManager.get(playerConnection.get(getHandle.invoke(player))));
		}

		public static void addPacketsHandler(PlayerData playerData) throws ReflectiveOperationException {
			playerData.packetsHandler = new ChannelDuplexHandler() {
				@Override
				public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
					if (msg.getClass().equals(packetMetadata.getClassInstance()) && packets.asMap().remove(msg) == null) {
						int entityID = packetMetadataEntity.getInt(msg);
						GlowingData glowingData = playerData.glowingDatas.get(entityID);
						if (glowingData != null) {

							@SuppressWarnings("unchecked")
							List<Object> items = (List<Object>) packetMetadataItems.get(msg);
							if (items != null) {

								boolean containsFlags = false;
								boolean edited = false;
								for (int i = 0; i < items.size(); i++) {
									Object item = items.get(i);
									Object watcherObject;
									if (watcherItemObject != null) {
										watcherObject = watcherItemObject.invoke(item);
									} else {
										Object serializer = watcherBSerializer.invoke(item);
										watcherObject = watcherSerializerObject.invoke(serializer, watcherBId.invoke(item));
									}

									if (watcherObject.equals(watcherObjectFlags)) {
										containsFlags = true;
										byte flags = (byte) watcherItemDataGet.invoke(item);
										glowingData.otherFlags = flags;
										byte newFlags = computeFlags(glowingData);
										if (newFlags != flags) {
											edited = true;
											items = new ArrayList<>(items);
											// we cannot simply edit the item as it may be backed in the datawatcher, so we
											// make a copy of the list
											items.set(i, createFlagWatcherItem(newFlags));
											break;
											// we can break right now as the "flags" datawatcher object may not be present
											// twice in the same packet
										}
									}
								}

								if (!edited && !containsFlags) {
									// if the packet does not contain any flag information, we are unsure if it is a packet
									// simply containing informations about another object's data update OR if it is a packet
									// containing all non-default informations of the entity. Such as packet can be sent when
									// the player has got far away from the entity and come in sight distance again.
									// In this case, we must add manually the "flags" object, otherwise it would stay 0 and
									// the entity would not be glowing.
									// Ideally, we should listen for an "entity add" packet to be sure we are in the case
									// above, but honestly it's annoying because there are multiple types of "entity add"
									// packets, so we do like this instead. Less performant, but not by far.
									byte flags = computeFlags(glowingData);
									if (flags != 0) {
										edited = true;
										items = new ArrayList<>(items);
										items.add(createFlagWatcherItem(flags));
									}
								}

								if (edited) {
									// some of the metadata packets are broadcasted to all players near the target entity.
									// hence, if we directly edit the packet, some users that were not intended to see the
									// glowing color will be able to see it. We should send a new packet to the viewer only.

									Object newMsg;
									if (version.isBefore(1, 19, 3)) {
										newMsg = packetMetadataConstructor.newInstance(entityID, watcherDummy, false);
										packetMetadataItems.set(newMsg, items);
									} else {
										newMsg = packetMetadataConstructor.newInstance(entityID, items);
									}
									packets.put(newMsg, dummy);
									sendPackets(playerData.player, newMsg);

									return; // we cancel the send of this packet
								}
							}
						}
					} else if (packetBundle != null && packetBundle.getClassInstance().isInstance(msg)) {
						handlePacketBundle(msg);
					}
					super.write(ctx, msg, promise);
				}

				@SuppressWarnings("rawtypes")
				private void handlePacketBundle(Object bundle) throws ReflectiveOperationException {
					Iterable subPackets = (Iterable) packetBundlePackets.invoke(bundle);
					for (Iterator iterator = subPackets.iterator(); iterator.hasNext();) {
						Object packet = iterator.next();

						if (packet.getClass().equals(packetMetadata)) {
							int entityID = packetMetadataEntity.getInt(packet);
							GlowingData glowingData = playerData.glowingDatas.get(entityID);
							if (glowingData != null) {
								// means the bundle packet contains metadata about an entity that must be glowing.
								// editing a bundle packet is annoying, so we'll let it go to the player
								// and then send a metadata packet containing the correct glowing flag.

								Bukkit.getScheduler().runTaskLaterAsynchronously(playerData.instance.plugin, () -> {
									try {
										updateGlowingState(glowingData);
									} catch (ReflectiveOperationException e) {
										e.printStackTrace();
									}
								}, 1L);
								return;
							}
						}
					}
				}

			};

			getChannel(playerData.player).pipeline().addBefore("packet_handler", null, playerData.packetsHandler);
		}

		public static void removePacketsHandler(PlayerData playerData) throws ReflectiveOperationException {
			if (playerData.packetsHandler != null) {
				getChannel(playerData.player).pipeline().remove(playerData.packetsHandler);
			}
		}

		/* Reflection utils */
		private static Class<?> getCraftClass(String craftPackage, String className) throws ClassNotFoundException {
			return Class.forName(cpack + craftPackage + "." + className);
		}

		private static @NotNull ClassAccessor getNMSClass(@NotNull ReflectionAccessor reflection, @NotNull String className)
				throws ClassNotFoundException {
			return reflection.getClass("net.minecraft." + className);
		}

		private static @NotNull ClassAccessor getNMSClass(@NotNull ReflectionAccessor reflection, @NotNull String nmPackage,
				@NotNull String className) throws ClassNotFoundException {
			return reflection.getClass("net.minecraft." + nmPackage + "." + className);
		}

		private static class TeamData {

			private final String id;
			private final Object creationPacket;

			private final Cache<String, Object> addPackets =
					CacheBuilder.newBuilder().expireAfterAccess(3, TimeUnit.MINUTES).build();
			private final Cache<String, Object> removePackets =
					CacheBuilder.newBuilder().expireAfterAccess(3, TimeUnit.MINUTES).build();

			public TeamData(int uid, ChatColor color) throws ReflectiveOperationException {
				if (!color.isColor())
					throw new IllegalArgumentException();
				id = "glow-" + uid + color.getChar();
				Object team = createTeam.newInstance(scoreboardDummy, id);
				setTeamPush.invoke(team, pushNever);
				setTeamColor.invoke(team, getColorConstant.invoke(null, color.getChar()));
				Object packetData = createTeamPacketData.newInstance(team);
				creationPacket = createTeamPacket.newInstance(id, 0, Optional.of(packetData), Collections.EMPTY_LIST);
			}

			public Object getEntityAddPacket(String teamID) throws ReflectiveOperationException {
				Object packet = addPackets.getIfPresent(teamID);
				if (packet == null) {
					packet = createTeamPacket.newInstance(id, 3, Optional.empty(), Arrays.asList(teamID));
					addPackets.put(teamID, packet);
				}
				return packet;
			}

			public Object getEntityRemovePacket(String teamID) throws ReflectiveOperationException {
				Object packet = removePackets.getIfPresent(teamID);
				if (packet == null) {
					packet = createTeamPacket.newInstance(id, 4, Optional.empty(), Arrays.asList(teamID));
					removePackets.put(teamID, packet);
				}
				return packet;
			}

		}

	}

}
