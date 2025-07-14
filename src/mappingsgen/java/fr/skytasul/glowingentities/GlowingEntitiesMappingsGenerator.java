package fr.skytasul.glowingentities;

import fr.skytasul.reflection.Version;
import fr.skytasul.reflection.mappings.files.ProguardMapping;
import fr.skytasul.reflection.shrieker.CustomMappings;
import fr.skytasul.reflection.shrieker.MappingsShrieker;
import fr.skytasul.reflection.shrieker.PipeMappings;
import fr.skytasul.reflection.shrieker.minecraft.MinecraftMappingsProvider;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class GlowingEntitiesMappingsGenerator {

	private static final @NotNull Logger LOGGER = Logger.getLogger("GlowingEntities-CodeGen");

	private final @NotNull Path dataFolder;
	private final @NotNull MinecraftMappingsProvider mappingsProvider;

	public GlowingEntitiesMappingsGenerator(@NotNull Path dataPath, @NotNull Path tmpPath) throws IOException {
		this.dataFolder = dataPath;
		this.mappingsProvider = new MinecraftMappingsProvider(tmpPath);
	}

	public void start() throws MappingGenerationException, IOException {
		var spigotShrieker = new MappingsShrieker(new ProguardMapping(true), GlowingEntities.Packets::loadReflection);

		for (var version : Version.parseArray(
				"1.17", "1.17.1",
				"1.18", "1.18.1", "1.18.2",
				"1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
				"1.20.1", "1.20.2", "1.20.4", "1.20.6",
				"1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7")) {
			try {
				LOGGER.info("Downloading mappings for " + version + "...");
				var minecraftMappings = mappingsProvider.loadMinecraftMappings(version);
				var spigotMappings = new CustomMappings(mappingsProvider.loadSpigotMappings(version));
				spigotMappings.getClassFromMapped("net.minecraft.server.network.PlayerConnection").inheritsFrom(
						spigotMappings.getClassFromMapped("net.minecraft.server.network.ServerPlayerConnection"));
				LOGGER.info("Shrieking mappings...");
				spigotShrieker.registerVersionMappings(version, new PipeMappings(minecraftMappings, spigotMappings));
			} catch (ReflectiveOperationException ex) {
				throw new MappingGenerationException(version, ex);
			}
		}

		LOGGER.info("\n\nWriting mappings files...");
		Files.createDirectories(dataFolder);
		spigotShrieker.writeMappingsFile(dataFolder.resolve("spigot.txt"));
		LOGGER.info("\nDone.");
	}

	public static void main(String[] args) throws MappingGenerationException, IOException {
		var mappingsPath = Path.of("src", "main", "resources", "fr", "skytasul", "glowingentities", "mappings");
		var tmpPath = Path.of("rawMappings");
		new GlowingEntitiesMappingsGenerator(mappingsPath, tmpPath).start();
	}

	private static class MappingGenerationException extends Exception {

		private static final long serialVersionUID = -7795392340156647315L;

		public MappingGenerationException(@NotNull Version version, Throwable cause) {
			super("Failed to generate mappings for version " + version, cause);
		}

	}

}
