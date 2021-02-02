package org.dave.compactmachines3.misc;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.dave.compactmachines3.CompactMachines3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigurationHandler {
    public static Configuration configuration;
    public static File cmDirectory;

    public static final String CATEGORY_CLIENT = "Client";
    public static final String CATEGORY_COMPAT = "Compatibility";
    public static final String CATEGORY_INTERNAL = "Internal";
    public static final String CATEGORY_MACHINES = "Machines";
    public static final String CATEGORY_MINIATURIZATION = "Miniaturization";

    public static File schemaDirectory;
    public static File recipeDirectory;

    public static void init(File configFile) {
        if (configuration != null) {
            return;
        }

        cmDirectory = new File(configFile.getParentFile(), "compactmachines3");
        if (!cmDirectory.exists()) {
            cmDirectory.mkdir();
        }

        configuration = new Configuration(new File(cmDirectory, "settings.cfg"), null, true);
        loadConfiguration();

        recipeDirectory = new File(cmDirectory, "recipes");
        if (!recipeDirectory.exists()) {
            recipeDirectory.mkdir();
        }

        schemaDirectory = new File(cmDirectory, "schemas");
        if (!schemaDirectory.exists()) {
            schemaDirectory.mkdir();
        }

    }

    private static void loadConfiguration() {
        CompactMachines3.logger.info("Loading configuration");
        String langKeyPrefix = "compactmachines3.config.";

        createCategory(CATEGORY_CLIENT, "Client rendering settings");
        createCategory(CATEGORY_COMPAT, "Modpack compatibility settings");
        createCategory(CATEGORY_INTERNAL, "Internal values that should generally not be changed");
        createCategory(CATEGORY_MACHINES, "Machine Block settings that relate to machine rooms");
        createCategory(CATEGORY_MINIATURIZATION, "Miniaturization projector settings");

        // Client Category
        // NOTE: Legacy versions had this in the "Machines" category, supports backwards compatibility
        ConfigCategory machineCat = configuration.getCategory(CATEGORY_MACHINES);
        configuration.moveProperty(CATEGORY_MACHINES, "autoUpdateRate", CATEGORY_CLIENT);
        machineCat.remove("autoUpdateRate");
        MachineSettings.autoUpdateRate = configuration.getInt(
                "autoUpdateRate",
                CATEGORY_CLIENT,
                20,
                0, Integer.MAX_VALUE,
                "Updates the Machine Preview GUI every n ticks. Set to 0 to disable. Lower values decrease performance significantly!",
                langKeyPrefix + "autoUpdateRate"
        );

        String rendering = "Client-side rendering option, might give a performance boost when opening a Machine Preview GUI.";
        configuration.moveProperty(CATEGORY_MACHINES, "renderLivingEntitiesInGUI", CATEGORY_CLIENT);
        machineCat.remove("renderLivingEntitiesInGUI");
        MachineSettings.renderLivingEntitiesInGUI = configuration.getBoolean(
                "renderLivingEntitiesInGUI",
                CATEGORY_CLIENT,
                true,
                rendering,
                langKeyPrefix + "renderLivingEntitiesInGUI"
        );

        configuration.moveProperty(CATEGORY_MACHINES, "renderTileEntitiesInGUI", CATEGORY_CLIENT);
        machineCat.remove("renderTileEntitiesInGUI");
        MachineSettings.renderTileEntitiesInGUI = configuration.getBoolean(
                "renderTileEntitiesInGUI",
                CATEGORY_CLIENT,
                true,
                rendering,
                langKeyPrefix + "renderTileEntitiesInGUI"
        );

        // Compatibility Category
        CompatSettings.doesWaterVaporize = configuration.getBoolean(
                "doesWaterVaporize",
                CATEGORY_COMPAT,
                false,
                "Forces water to vaporize inside Compact Machines. Used for Forever Stranded: Lost Souls.",
                langKeyPrefix + "doesWaterVaporize"
        );

        // Internal Category
        Settings.dimensionId = configuration.getInt(
                "dimensionId",
                CATEGORY_INTERNAL,
                144,
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                "Dimension used for machines. Do not change this unless it is somehow conflicting!",
                langKeyPrefix + "dimensionId"
        );

        Settings.dimensionTypeId = configuration.getInt(
                "dimensionTypeId",
                CATEGORY_INTERNAL,
                144,
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                "Dimension type used for machines. Do not change this unless it is somehow conflicting!",
                langKeyPrefix + "dimensionTypeId"
        );

        Settings.forceLoadChunks = configuration.getBoolean(
                "forceLoadChunks",
                CATEGORY_INTERNAL,
                false,
                "Whether the interior of all Compact Machines should be chunk loaded always. Otherwise they will only chunkload when the CM itself is chunkloaded.",
                langKeyPrefix + "forceLoadChunks"
        );

        // Machines Category
        MachineSettings.allowEnteringWithoutPSD = configuration.getBoolean(
                "allowEnteringWithoutPSD",
                CATEGORY_MACHINES,
                true,
                "Allow players to enter machines with teleportation commands, etc. from other mods without kicking them out.",
                langKeyPrefix + "allowEnteringWithoutPSD"
        );

        MachineSettings.allowHostileSpawns = configuration.getBoolean(
                "allowHostileSpawns",
                CATEGORY_MACHINES,
                true,
                "Allow hostile creatures to spawn inside of machines.",
                langKeyPrefix + "allowHostileSpawns"
        );

        MachineSettings.allowPeacefulSpawns = configuration.getBoolean(
                "allowPeacefulSpawns",
                CATEGORY_MACHINES,
                true,
                "Allow peaceful creatures to spawn inside of machines.",
                langKeyPrefix + "allowPeacefulSpawns"
        );

        MachineSettings.allowPickupEmptyMachines = configuration.getBoolean(
                "allowPickupEmptyMachines",
                CATEGORY_MACHINES,
                false,
                "If set to true, breaking a machine block that was never used will clear the NBT data.\n" +
                        "This is disabled by default because it clears the owner of the machine block which may be an unintended side-effect.\n" +
                        "This can be helpful to players who want to pick up empty machines and stack them since items with different NBT do not stack.",
                langKeyPrefix + "allowPickupEmptyMachines"
        );

        MachineSettings.allowRespawning = configuration.getBoolean(
                "allowRespawning",
                CATEGORY_MACHINES,
                true,
                "Whether players can respawn inside of Compact Machines, i.e. place beds and sleep there.",
                langKeyPrefix + "allowRespawning"
        );

        MachineSettings.keepPlayersInside = configuration.getBoolean(
                "keepPlayersInside",
                CATEGORY_MACHINES,
                true,
                "Block players from leaving the confines of the box of the Compact Machine they should currently be in, only applies to players not in creative/spectator.",
                langKeyPrefix + "keepPlayersInside"
        );

        MachineSettings.spawnRate = configuration.getInt(
                "spawnRate",
                CATEGORY_MACHINES,
                1200,
                1,
                Integer.MAX_VALUE,
                "How often to try spawning entities inside of machines in ticks.",
                langKeyPrefix + "spawnRate"
        );

        // Miniaturization Category
        Settings.maximumCraftingAreaSize = configuration.getInt(
                "maximumCraftingAreaSize",
                CATEGORY_MINIATURIZATION,
                15,
                5, 20,
                "Maximum size the field projectors can cover.",
                langKeyPrefix + "maximumCraftingAreaSize"
        );

        Settings.maximumCraftingCatalystAge = configuration.getInt(
                "maximumCraftingCatalystAge",
                CATEGORY_MINIATURIZATION,
                60,
                20, Integer.MAX_VALUE,
                "Maximum age in ticks in which an item is valid for acting as a catalyst.",
                langKeyPrefix + "maximumCraftingCatalystAge"
        );

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    private static void createCategory(String category, String comment) {
        String langKey = "compactmachines3.config.category." + category.toLowerCase();
        ConfigCategory configCat = configuration.getCategory(category);

        // Copy old settings from legacy case-insensitive configs
        if (configuration.hasCategory(category.toLowerCase())) {
            ConfigCategory oldCat = configuration.getCategory(category.toLowerCase());
            for (Map.Entry<String, Property> entry : oldCat.entrySet()) {
                configCat.put(entry.getKey(), entry.getValue());
            }
            configuration.removeCategory(oldCat);
        }

        configCat
                .setLanguageKey(langKey) // Used in the in-game config menu when hovering over the category
                .setComment(comment); // Used in the config file itself
    }

    public static void saveConfiguration() {
        CompactMachines3.logger.info("Saving configuration");
        configuration.save();
    }

    @SubscribeEvent
    public static void onConfigurationChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!event.getModID().equalsIgnoreCase(CompactMachines3.MODID)) {
            return;
        }

        loadConfiguration();
    }

    public static List<IConfigElement> getConfigElements() {
        List<IConfigElement> result = new ArrayList<>();
        result.add(new ConfigElement(configuration.getCategory(CATEGORY_CLIENT)));
        result.add(new ConfigElement(configuration.getCategory(CATEGORY_COMPAT)));
        result.add(new ConfigElement(configuration.getCategory(CATEGORY_INTERNAL)));
        result.add(new ConfigElement(configuration.getCategory(CATEGORY_MACHINES)));
        result.add(new ConfigElement(configuration.getCategory(CATEGORY_MINIATURIZATION)));

        return result;
    }

    public static class CompatSettings {
        public static boolean doesWaterVaporize;
    }

    public static class MachineSettings {
        public static boolean allowRespawning;
        public static boolean keepPlayersInside;
        public static int spawnRate;
        public static boolean allowPeacefulSpawns;
        public static boolean allowHostileSpawns;
        public static boolean allowEnteringWithoutPSD;
        public static boolean renderTileEntitiesInGUI;
        public static boolean renderLivingEntitiesInGUI;
        public static boolean allowPickupEmptyMachines;
        public static int autoUpdateRate;
    }

    public static class Settings {
        public static int dimensionId;
        public static int dimensionTypeId;
        public static boolean forceLoadChunks;
        public static int maximumCraftingAreaSize;
        public static int maximumCraftingCatalystAge;

        public static int getMaximumMagnitude() {
            // magnitude -> field size = (2 * magnitude) - 1
            // field size -> magnitude = (field size + 1) / 2
            // This means this method is trying to get the magnitude from the field size, which was incorrectly calculated.
            // This has been fixed.
            return (ConfigurationHandler.Settings.maximumCraftingAreaSize + 1) / 2;
        }
    }
}
