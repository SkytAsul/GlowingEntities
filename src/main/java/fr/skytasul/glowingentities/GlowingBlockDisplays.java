package fr.skytasul.glowingentities;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import java.util.Objects;

/**
 * An extension of {@link GlowingBlocks} to display glowing block models per-player.
 * <p>
 * <i>Can only be used on Paper-based servers running Minecraft 1.19.4 or newer.</i>
 */
public class GlowingBlockDisplays extends GlowingBlocks {

	/**
	 * Initializes the Glowing block displays API.
	 *
	 * @param plugin plugin that will be used to register the events.
	 * @throws UnsupportedOperationException if the server version is older than 1.19.4
	 */
	public GlowingBlockDisplays(@NotNull Plugin plugin) {
		super(plugin, true);
	}

	/**
	 * Displays the current state of the block at the location with the specified glowing color.
	 *
	 * @param block location of the block to display
	 * @param receiver player which will see the glowing block model
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	@Override
	public void setGlowing(@NotNull Location block, @NotNull Player receiver, @NotNull ChatColor color)
			throws ReflectiveOperationException {
		block.checkFinite();
		setGlowing(block, block.getBlock().getBlockData(), receiver, color);
	}

	/**
	 * Displays the specified block state at the location with the specified glowing color.
	 * <p>
	 * The state is copied, including its properties. The world is not modified. Calling this method
	 * again updates the displayed state even if the glowing color does not change.
	 *
	 * @param location location at which to display the block model, normalized to block coordinates
	 * @param blockData state of the block model to display
	 * @param receiver player which will see the glowing block model
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 */
	public void setGlowing(@NotNull Location location, @NotNull BlockData blockData, @NotNull Player receiver,
			@NotNull ChatColor color) throws ReflectiveOperationException {
		setGlowingBlock(location, Objects.requireNonNull(blockData), receiver, color);
	}

	/**
	 * Displays the specified block state at the block's location with the specified glowing color.
	 *
	 * @param block block whose location will be used
	 * @param blockData state of the block model to display
	 * @param receiver player which will see the glowing block model
	 * @param color color of the glowing effect
	 * @throws ReflectiveOperationException
	 * @see #setGlowing(Location, BlockData, Player, ChatColor)
	 */
	public void setGlowing(@NotNull Block block, @NotNull BlockData blockData, @NotNull Player receiver,
			@NotNull ChatColor color) throws ReflectiveOperationException {
		setGlowing(block.getLocation(), blockData, receiver, color);
	}

}
