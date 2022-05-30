package fr.skytasul.glowingentities;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * A Spigot util to easily make entities glow.
 * <p>
 * <b>1.17 -> 1.18.2</b>
 * 
 * @version 1.0.0
 * @author SkytAsul
 */
public class GlowingEntities implements Listener {
	
	private Map<Player, PlayerData> glowing = new HashMap<>();
	
	public GlowingEntities(Plugin plugin) {
		if (!Packets.enabled) throw new IllegalStateException("The Glowing Entities API is disabled. An error has occured during initialization.");
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}
	
	public void disable() {
		HandlerList.unregisterAll(this);
		glowing.values().forEach(playerData -> {
			try {
				Packets.removePacketsHandler(playerData);
			}catch (ReflectiveOperationException e) {
				e.printStackTrace();
			}
		});
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		glowing.remove(event.getPlayer());
	}
	
	public void setGlowing(Entity entity, Player receiver) throws ReflectiveOperationException {
		setGlowing(entity, receiver, ChatColor.WHITE);
	}
	
	public void setGlowing(Entity entity, Player receiver, ChatColor color) throws ReflectiveOperationException {
		setGlowing(entity.getEntityId(), receiver, color, Packets.getEntityFlags(entity));
	}
	
	public void setGlowing(int entityID, Player receiver) throws ReflectiveOperationException {
		setGlowing(entityID, receiver, ChatColor.WHITE, (byte) 0);
	}
	
	public void setGlowing(int entityID, Player receiver, ChatColor color) throws ReflectiveOperationException {
		setGlowing(entityID, receiver, color, (byte) 0);
	}

	public void setGlowing(int entityID, Player receiver, ChatColor color, byte otherFlags) throws ReflectiveOperationException {
		PlayerData playerData = glowing.get(receiver);
		if (playerData == null) {
			playerData = new PlayerData(receiver);
			Packets.addPacketsHandler(playerData);
			glowing.put(receiver, playerData);
		}
		
		GlowingData glowingData = playerData.glowingDatas.get(entityID);
		if (glowingData == null) {
			// the player did not have datas related to the entity: we must create the glowing status
			glowingData = new GlowingData(playerData, entityID, color, otherFlags);
			playerData.glowingDatas.put(entityID, glowingData);
			
			Packets.createGlowing(glowingData);
		}else {
			// the player already had datas related to the entity: we must update the glowing status
			
			if (glowingData.color.equals(color)) return; // nothing changed
			
			glowingData.color = color;
			Packets.updateGlowingColor(glowingData);
		}
	}
	
	public void unsetGlowing(Entity entity, Player receiver) throws ReflectiveOperationException {
		unsetGlowing(entity.getEntityId(), receiver);
	}
	
	public void unsetGlowing(int entityID, Player receiver) throws ReflectiveOperationException {
		PlayerData playerData = glowing.get(receiver);
		if (playerData == null) return; // the player do not have any entity glowing
		
		GlowingData glowingData = playerData.glowingDatas.remove(entityID);
		if (glowingData == null) return; // the player did not have this entity glowing
		
		Packets.removeGlowing(glowingData);
		
		if (playerData.glowingDatas.isEmpty()) {
			// if the player do not have any other entity glowing,
			// we can safely remove all of its data to free some memory
			Packets.removePacketsHandler(playerData);
			glowing.remove(receiver);
		}
	}
	
	private static class PlayerData {
		
		final Player player;
		final Map<Integer, GlowingData> glowingDatas;
		ChannelHandler packetsHandler;
		
		PlayerData(Player player) {
			this.player = player;
			this.glowingDatas = new HashMap<>();
		}
		
	}
	
	private static class GlowingData {
		// unfortunately this cannot be a Java Record
		// as the "color" field is not final
		
		final PlayerData player;
		final int entityID;
		ChatColor color;
		byte otherFlags;
		boolean enabled;
		
		GlowingData(PlayerData player, int entityID, ChatColor color, byte otherFlags) {
			this.player = player;
			this.entityID = entityID;
			this.color = color;
			this.otherFlags = otherFlags;
			this.enabled = true;
		}
		
	}
	
	private static class Packets {
		
		private static final byte GLOWING_FLAG = 1 << 6;
		
		private static Cache<Object, Object> packets = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();
		private static Object dummy = new Object();
		
		private static Logger logger;
		private static int version;
		private static int versionMinor;
		private static String cpack = Bukkit.getServer().getClass().getPackage().getName() + ".";
		private static ProtocolMappings mappings;
		public static boolean enabled = false;
		
		private static Method getHandle;
		private static Method getDataWatcher;
		
		private static Object watcherObjectFlags;
		private static Object watcherDummy;
		private static Method watcherGet;
		private static Constructor<?> watcherItemConstructor;
		private static Method watcherItemObject;
		private static Method watcherItemDataGet;
		
		private static Field playerConnection;
		private static Method sendPacket;
		private static Field networkManager;
		private static Field channelField;
		
		private static Class<?> packetMetadata;
		private static Constructor<?> packetMetadataConstructor;
		private static Field packetMetadataEntity;
		private static Field packetMetadataItems;
		
		static {
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
				
				// e.g. Bukkit.getServer().getClass().getPackage().getName() -> org.bukkit.craftbukkit.v1_17_R1
				String[] versions = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3].substring(1).split("_");
				version = Integer.parseInt(versions[1]); // 1.X
				if (version >= 17) {
					// e.g. Bukkit.getBukkitVersion() -> 1.17.1-R0.1-SNAPSHOT
					versions = Bukkit.getBukkitVersion().split("-R")[0].split("\\.");
					versionMinor = versions.length <= 2 ? 0 : Integer.parseInt(versions[2]);
				}else versionMinor = Integer.parseInt(versions[2].substring(1)); // 1.X.Y
				logger.info("Found server version 1." + version + "." + versionMinor);
				
				mappings = ProtocolMappings.getMappings(version);
				if (mappings == null) {
					mappings = ProtocolMappings.values()[ProtocolMappings.values().length - 1];
					logger.warning("Loaded not matching version of the mappings for your server version (1." + version + "." + versionMinor + ")");
				}
				logger.info("Loaded mappings " + mappings.name());
				
				/* Global variables */

				Class<?> entityClass = getNMSClass("world.entity", "Entity");
				Class<?> entityTypesClass = getNMSClass("world.entity", "EntityTypes");
				Object markerEntity = getNMSClass("world.entity", "Marker").getDeclaredConstructors()[0].newInstance(getField(entityTypesClass, mappings.getMarkerTypeId(), null), null);
				
				getHandle = getCraftClass("entity", "CraftEntity").getDeclaredMethod("getHandle");
				getDataWatcher = entityClass.getDeclaredMethod(mappings.getWatcherAccessor());
				
				/* DataWatchers */
				
				Class<?> dataWatcherClass = getNMSClass("network.syncher", "DataWatcher");
				
				watcherObjectFlags = getField(entityClass, mappings.getWatcherFlags(), null);
				watcherDummy = dataWatcherClass.getDeclaredConstructor(entityClass).newInstance(markerEntity);
				watcherGet = version >= 18 ? dataWatcherClass.getDeclaredMethod("a", watcherObjectFlags.getClass()) : getMethod(dataWatcherClass, "get");
				
				Class<?> watcherItem = getNMSClass("network.syncher", "DataWatcher$Item");
				watcherItemConstructor = watcherItem.getDeclaredConstructor(watcherObjectFlags.getClass(), Object.class);
				watcherItemObject = watcherItem.getDeclaredMethod("a");
				watcherItemDataGet = watcherItem.getDeclaredMethod("b");
				
				/* Connections */
				
				playerConnection = getNMSClass("server.level", "EntityPlayer").getDeclaredField(mappings.getPlayerConnection());
				sendPacket = getNMSClass("server.network", "PlayerConnection").getMethod(mappings.getSendPacket(), getNMSClass("network.protocol", "Packet"));
				networkManager = getNMSClass("server.network", "PlayerConnection").getDeclaredField("a");
				channelField = getNMSClass("network", "NetworkManager").getDeclaredField(mappings.getChannel());
				
				/* Packets */
				
				packetMetadata = getNMSClass("network.protocol.game", "PacketPlayOutEntityMetadata");
				packetMetadataConstructor = packetMetadata.getDeclaredConstructor(int.class, dataWatcherClass, boolean.class);
				packetMetadataEntity = getField(packetMetadata, "a");
				packetMetadataItems = getField(packetMetadata, "b");
				
				enabled = true;
			}catch (Exception ex) {
				String errorMsg = "Laser Beam reflection failed to initialize. The util is disabled. Please ensure your version (" + Bukkit.getServer().getClass().getPackage().getName() + ") is supported.";
				if (logger == null) {
					ex.printStackTrace();
					System.err.println(errorMsg);
				}else {
					logger.log(Level.SEVERE, errorMsg, ex);
				}
			}
		}

		public static void sendPackets(Player p, Object... packets) throws ReflectiveOperationException {
			Object connection = playerConnection.get(getHandle.invoke(p));
			for (Object packet : packets) {
				if (packet == null) continue;
				sendPacket.invoke(connection, packet);
			}
		}
		
		public static byte getEntityFlags(Entity entity) throws ReflectiveOperationException {
			Object nmsEntity = getHandle.invoke(entity);
			Object dataWatcher = getDataWatcher.invoke(nmsEntity);
			return (byte) watcherGet.invoke(dataWatcher, watcherObjectFlags);
		}
		
		public static void createGlowing(GlowingData glowingData) throws ReflectiveOperationException {
			setMetadata(glowingData, computeFlags(glowingData));
		}
		
		private static byte computeFlags(GlowingData glowingData) {
			byte newFlags = glowingData.otherFlags;
			if (glowingData.enabled) {
				newFlags |= GLOWING_FLAG;
			}else {
				newFlags &= ~GLOWING_FLAG;
			}
			return newFlags;
		}
		
		public static void removeGlowing(GlowingData glowingData) throws ReflectiveOperationException {
			setMetadata(glowingData, glowingData.otherFlags);
		}

		private static void setMetadata(GlowingData glowingData, byte flags) throws ReflectiveOperationException {
			logger.info("Setting metadata on " + glowingData.entityID + " to " + flags);
			
			List<Object> dataItems = new ArrayList<>(1);
			dataItems.add(watcherItemConstructor.newInstance(watcherObjectFlags, flags));
			
			Object packetMetadata = packetMetadataConstructor.newInstance(glowingData.entityID, watcherDummy, false);
			packetMetadataItems.set(packetMetadata, dataItems);
			packets.put(packetMetadata, dummy);
			sendPackets(glowingData.player.player, packetMetadata);
		}
		
		public static void updateGlowingColor(GlowingData glowingData) {
			// TODO
		}
		
		private static byte receivedFlagsUpdate(PlayerData playerData, int entityID, byte flags) {
			logger.info("Metadata with flags for " + entityID + " with " + flags);
			GlowingData glowingData = playerData.glowingDatas.get(entityID);
			if (glowingData == null) return flags;
			
			glowingData.otherFlags = flags;
			return computeFlags(glowingData);
		}
		
		private static Channel getChannel(Player player) throws ReflectiveOperationException {
			return (Channel) channelField.get(networkManager.get(playerConnection.get(getHandle.invoke(player))));
		}
		
		public static void addPacketsHandler(PlayerData playerData) throws ReflectiveOperationException {
			playerData.packetsHandler = new ChannelDuplexHandler() {
				@Override
				public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
					if (msg.getClass().equals(packetMetadata) && packets.asMap().remove(msg) == null) {
						List<Object> items = (List<Object>) packetMetadataItems.get(msg);
						if (items != null) {
							List<Object> copy = null;
							for (int i = 0; i < items.size(); i++) {
								Object item = items.get(i);
								if (watcherItemObject.invoke(item).equals(watcherObjectFlags)) {
									byte flags = (byte) watcherItemDataGet.invoke(item);
									byte newFlags = receivedFlagsUpdate(playerData, packetMetadataEntity.getInt(msg), flags);
									if (newFlags != flags) {
										if (copy == null) copy = new ArrayList<>(items);
										copy.set(i, watcherItemConstructor.newInstance(watcherObjectFlags, newFlags));
										// we cannot simply edit the item as it may be backed in the datawatcher
									}
								}
							}
							if (copy != null) packetMetadataItems.set(msg, copy);
						}
					}
					super.write(ctx, msg, promise);
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
		private static Method getMethod(Class<?> clazz, String name) throws NoSuchMethodException {
			for (Method m : clazz.getDeclaredMethods()) {
				if (m.getName().equals(name)) return m;
			}
			throw new NoSuchMethodException(name + " in " + clazz.getName());
		}
		
		@Deprecated
		private static Object getField(Class<?> clazz, String name, Object instance) throws ReflectiveOperationException {
			return getField(clazz, name).get(instance);
		}
		
		private static Field getField(Class<?> clazz, String name) throws ReflectiveOperationException {
			Field field = clazz.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		}
		
		private static Class<?> getCraftClass(String craftPackage, String className) throws ClassNotFoundException {
			return Class.forName(cpack + craftPackage + "." + className);
		}
		
		private static Class<?> getNMSClass(String nmPackage, String className) throws ClassNotFoundException {
			return Class.forName("net.minecraft." + nmPackage + "." + className);
		}
		
		private enum ProtocolMappings {
			
			V1_17(
					17,
					"Z",
					"Y",
					"getDataWatcher",
					"b",
					"sendPacket",
					"k"),
			V1_18(
					18,
					"Z",
					"Y",
					"ai",
					"b",
					"a",
					"m"),
			;

			private final int major;
			private final String watcherFlags;
			private String markerTypeId;
			private String watcherAccessor;
			private String playerConnection;
			private String sendPacket;
			private String channel;

			private ProtocolMappings(int major, String watcherFlags, String markerTypeId, String watcherAccessor, String playerConnection, String sendPacket, String channel) {
				this.major = major;
				this.watcherFlags = watcherFlags;
				this.markerTypeId = markerTypeId;
				this.watcherAccessor = watcherAccessor;
				this.playerConnection = playerConnection;
				this.sendPacket = sendPacket;
				this.channel = channel;
			}
			
			public int getMajor() {
				return major;
			}
			
			public String getWatcherFlags() {
				return watcherFlags;
			}
			
			public String getMarkerTypeId() {
				return markerTypeId;
			}
			
			public String getWatcherAccessor() {
				return watcherAccessor;
			}
			
			public String getPlayerConnection() {
				return playerConnection;
			}
			
			public String getSendPacket() {
				return sendPacket;
			}
			
			public String getChannel() {
				return channel;
			}
			
			public static ProtocolMappings getMappings(int major) {
				for (ProtocolMappings map : values()) {
					if (major == map.getMajor()) return map;
				}
				return null;
			}
			
		}
		
	}
	
}
