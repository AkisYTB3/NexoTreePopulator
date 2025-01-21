package org.notionsmp.nexoTreePopulator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nexomc.nexo.api.NexoBlocks;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public final class NexoTreePopulator extends JavaPlugin {

    private FileConfiguration treesConfig;
    private final Map<String, Map<String, List<int[]>>> treeTypes = new HashMap<>();

    @Override
    public void onEnable() {
        loadTreesConfig();
        loadTreeTypes();

        for (World world : Bukkit.getWorlds()) {
            List<BlockPopulator> populators = new ArrayList<>(world.getPopulators());
            for (BlockPopulator populator : populators) {
                world.getPopulators().remove(populator);
            }

            world.getPopulators().add(new TreePopulator());
        }
    }


    @Override
    public void onDisable() {
    }

    private void loadTreesConfig() {
        File configFile = new File(getDataFolder(), "trees.yml");
        if (!configFile.exists()) {
            saveResource("trees.yml", false);
        }
        treesConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadTreeTypes() {
        File typesFolder = new File(getDataFolder(), "types");
        if (!typesFolder.exists()) {
            typesFolder.mkdirs();
            saveResource("types/generic.json", false);
        }

        Gson gson = new Gson();
        File[] files = typesFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            getLogger().warning("No tree type files found in 'types/' directory. Add custom tree types to enable them.");
            return;
        }

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, List<int[]>>>() {}.getType();
                Map<String, List<int[]>> treeType = gson.fromJson(reader, type);
                if (treeType != null) {
                    String typeName = file.getName().replace(".json", "");
                    treeTypes.put(typeName, treeType);
                    getLogger().info("Loaded tree type: " + typeName);
                } else {
                    getLogger().warning("Failed to parse tree type file: " + file.getName());
                }
            } catch (IOException e) {
                getLogger().severe("Error reading tree type file: " + file.getName() + ". " + e.getMessage());
            }
        }
    }


    private class TreePopulator extends BlockPopulator {

        @Override
        public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
            for (String treeId : treesConfig.getKeys(false)) {
                String configPath = treeId + ".";

                List<String> worlds = treesConfig.getStringList(configPath + "worlds");
                if (!worlds.contains(world.getName())) continue;

                int maxY = treesConfig.getInt(configPath + "maxY");
                int minY = treesConfig.getInt(configPath + "minY");
                List<String> biomes = treesConfig.getStringList(configPath + "biomes");
                double chance = treesConfig.getDouble(configPath + "chance");
                int amount = treesConfig.getInt(configPath + "amount");
                String logs = treesConfig.getString(configPath + "logs");
                String leaves = treesConfig.getString(configPath + "leaves");
                List<String> placeOn = treesConfig.getStringList(configPath + "place_on");
                String type = treesConfig.getString(configPath + "type");

                if (random.nextDouble() > chance) continue;

                Map<String, List<int[]>> structure = treeTypes.get(type);
                if (structure == null) continue;

                for (int i = 0; i < amount; i++) {
                    int x = chunk.getX() * 16 + random.nextInt(16);
                    int z = chunk.getZ() * 16 + random.nextInt(16);

                    for (int y = minY; y <= maxY; y++) {
                        Block block = world.getBlockAt(x, y, z);

                        if (block.getType() == Material.AIR &&
                                placeOn.contains(block.getRelative(0, -1, 0).getType().toString())) {

                            if (!biomes.isEmpty() && !biomes.contains(block.getBiome().toString())) {
                                continue;
                            }

                            placeTree(structure, block, logs, leaves);
                            break;
                        }
                    }
                }
            }
        }

        private void placeTree(Map<String, List<int[]>> structure, Block base, String logs, String leaves) {
            for (int[] logOffset : structure.getOrDefault("logs", Collections.emptyList())) {
                Block logBlock = base.getRelative(logOffset[0], logOffset[1], logOffset[2]);
                NexoBlocks.place(logs, logBlock.getLocation());
            }

            for (int[] leafOffset : structure.getOrDefault("leaves", Collections.emptyList())) {
                Block leafBlock = base.getRelative(leafOffset[0], leafOffset[1], leafOffset[2]);
                NexoBlocks.place(leaves, leafBlock.getLocation());
            }
        }
    }

}