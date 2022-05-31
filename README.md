# GlowingEntities

An util to easily set glowing entities per-player on a Spigot server.

No ProtocolLib, no dependency, compatible from Minecraft 1.17 to 1.18.2!

## How to install?
### 1st method: copying the class
Copy the [GlowingEntities.java class](src/main/java/fr/skytasul/glowingentities/GlowingEntities.java) to your project.

## How to use?
1. Initialize the `GlowingEntities` object somewhere where you can easily get it, using `new GlowingEntities(plugin)`.
It is not recommended to create multiple `GlowingEntities` instances!

2. Use `GlowingEntities#setGlowing(Entity entity, Player receiver, ChatColor color)` to make an entity glow a color for a player!

3. You can change its glowing color by reusing the same method but changing the `color` parameter.

4. If you no longer wants your entity to glow, use `GlowingEntities#unsetGlowing(Entity entity, Player receiver)`.

5. When you are completely done with the glowing API (for instance, when your plugin is shutting down), remember to use `GlowingEntities#disable()`.