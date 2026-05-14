package fr.skytasul.glowingentities;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for TAB compatibility fallback.
 * <p>
 * Exposes `%glowingentities_glowcolor%` as legacy color code (for example {@code &c}).
 */
public class GlowingEntitiesPlaceholderExpansion extends PlaceholderExpansion {

	private final @NotNull Plugin plugin;
	private final @NotNull GlowingEntities glowingEntities;

	public GlowingEntitiesPlaceholderExpansion(@NotNull Plugin plugin, @NotNull GlowingEntities glowingEntities) {
		this.plugin = plugin;
		this.glowingEntities = glowingEntities;
	}

	@Override
	public @NotNull String getIdentifier() {
		return "glowingentities";
	}

	@Override
	public @NotNull String getAuthor() {
		return "NicDevTV";
	}

	@Override
	public @NotNull String getVersion() {
		return plugin.getPluginMeta().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
		if (player == null)
			return "";

		if (params.equalsIgnoreCase("color"))
			return glowingEntities.getGlowColorPlaceholder(player);

		return null;
	}

}
