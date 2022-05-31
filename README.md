# GlowingEntities

An util to easily set glowing entities per-player on a Spigot server.

No ProtocolLib, no dependency, compatible from Minecraft 1.17 to 1.18.2!

![Glowing entities animation](demo.gif)

## How to install?
### 1st method: copying the class
Copy the [GlowingEntities.java class](src/main/java/fr/skytasul/glowingentities/GlowingEntities.java) to your project.

### 2nd method: using maven
Add this requirement to your maven `pom.xml` file:

```xml
<dependency>
  <groupId>io.github.skytasul</groupId>
  <artifactId>glowingentities</artifactId>
  <version>1.0.0</version>
  <scope>compile</scope>
</dependency>
```
Additionnally, you can use the maven shade plugin to relocate the class location.

## How to use?
1. Initialize the `GlowingEntities` object somewhere where you can easily get it, using `new GlowingEntities(plugin)`.
It is not recommended to create multiple `GlowingEntities` instances!

2. Use `GlowingEntities#setGlowing(Entity entity, Player receiver, ChatColor color)` to make an entity glow a color for a player!

3. You can change its glowing color by reusing the same method but changing the `color` parameter.

4. If you no longer wants your entity to glow, use `GlowingEntities#unsetGlowing(Entity entity, Player receiver)`.

5. When you are completely done with the glowing API (for instance, when your plugin is shutting down), remember to use `GlowingEntities#disable()`.