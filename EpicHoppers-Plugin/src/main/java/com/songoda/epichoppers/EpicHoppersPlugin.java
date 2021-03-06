package com.songoda.epichoppers;

import com.google.common.base.Preconditions;
import com.songoda.arconix.api.mcupdate.MCUpdate;
import com.songoda.arconix.api.utils.ConfigWrapper;
import com.songoda.arconix.plugin.Arconix;
import com.songoda.epichoppers.api.EpicHoppers;
import com.songoda.epichoppers.api.EpicHoppersAPI;
import com.songoda.epichoppers.api.hopper.*;
import com.songoda.epichoppers.api.hopper.levels.Level;
import com.songoda.epichoppers.api.hopper.levels.LevelManager;
import com.songoda.epichoppers.api.hopper.levels.modules.Module;
import com.songoda.epichoppers.api.utils.ClaimableProtectionPluginHook;
import com.songoda.epichoppers.api.utils.ProtectionPluginHook;
import com.songoda.epichoppers.boost.BoostData;
import com.songoda.epichoppers.boost.BoostManager;
import com.songoda.epichoppers.command.CommandManager;
import com.songoda.epichoppers.handlers.EnchantmentHandler;
import com.songoda.epichoppers.handlers.HopHandler;
import com.songoda.epichoppers.handlers.TeleportHandler;
import com.songoda.epichoppers.hooks.*;
import com.songoda.epichoppers.hopper.EFilter;
import com.songoda.epichoppers.hopper.EHopper;
import com.songoda.epichoppers.hopper.EHopperManager;
import com.songoda.epichoppers.hopper.levels.ELevelManager;
import com.songoda.epichoppers.hopper.levels.modules.ModuleAutoCrafting;
import com.songoda.epichoppers.hopper.levels.modules.ModuleBlockBreak;
import com.songoda.epichoppers.hopper.levels.modules.ModuleSuction;
import com.songoda.epichoppers.listeners.BlockListeners;
import com.songoda.epichoppers.listeners.HopperListeners;
import com.songoda.epichoppers.listeners.InteractListeners;
import com.songoda.epichoppers.listeners.InventoryListeners;
import com.songoda.epichoppers.player.PlayerDataManager;
import com.songoda.epichoppers.utils.Methods;
import com.songoda.epichoppers.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;


public class EpicHoppersPlugin extends JavaPlugin implements EpicHoppers {
    private static CommandSender console = Bukkit.getConsoleSender();

    private static EpicHoppersPlugin INSTANCE;
    public References references = null;
    public EnchantmentHandler enchantmentHandler;
    private List<ProtectionPluginHook> protectionHooks = new ArrayList<>();
    private ClaimableProtectionPluginHook factionsHook, townyHook, aSkyblockHook, uSkyblockHook;
    private SettingsManager settingsManager;
    private ConfigWrapper hooksFile = new ConfigWrapper(this, "", "hooks.yml");
    private ConfigWrapper dataFile = new ConfigWrapper(this, "", "data.yml");
    private Locale locale;

    private HopperManager hopperManager;
    private LevelManager levelManager;
    private BoostManager boostManager;
    private PlayerDataManager playerDataManager;

    private TeleportHandler teleportHandler;

    public static EpicHoppersPlugin getInstance() {
        return INSTANCE;
    }

    private boolean checkVersion() {
        int workingVersion = 13;
        int currentVersion = Integer.parseInt(Bukkit.getServer().getClass()
                .getPackage().getName().split("\\.")[3].split("_")[1]);

        if (currentVersion < workingVersion) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                Bukkit.getConsoleSender().sendMessage("");
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "You installed the 1." + workingVersion + "+ only version of " + this.getDescription().getName() + " on a 1." + currentVersion + " server. Since you are on the wrong version we disabled the plugin for you. Please install correct version to continue using " + this.getDescription().getName() + ".");
                Bukkit.getConsoleSender().sendMessage("");
            }, 20L);
            return false;
        }
        return true;
    }

    @Override
    public void onEnable() {
        // Check to make sure the Bukkit version is compatible.
        if (!checkVersion()) return;

        INSTANCE = this;
        EpicHoppersAPI.setImplementation(this);

        Arconix.pl().hook(this);

        console.sendMessage(Arconix.pl().getApi().format().formatText("&a============================="));
        console.sendMessage(Arconix.pl().getApi().format().formatText("&7EpicHoppers " + this.getDescription().getVersion() + " by &5Brianna <3&7!"));
        console.sendMessage(Arconix.pl().getApi().format().formatText("&7Action: &aEnabling&7..."));

        settingsManager = new SettingsManager(this);
        boostManager = new BoostManager();
        setupConfig();
        enchantmentHandler = new EnchantmentHandler();
        playerDataManager = new PlayerDataManager();

        // Locales
        Locale.init(this);
        Locale.saveDefaultLocale("en_US");
        this.locale = Locale.getLocale(this.getConfig().getString("Locale", "en_US"));

        loadLevelManager();

        hopperManager = new EHopperManager();

        /*
         * Register hoppers into HopperManger from configuration
         */
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (dataFile.getConfig().contains("data.sync")) {
                for (String locationStr : dataFile.getConfig().getConfigurationSection("data.sync").getKeys(false)) {
                    Location location = Arconix.pl().getApi().serialize().unserializeLocation(locationStr);
                    if (location == null || location.getBlock() == null) return;

                    int level = dataFile.getConfig().getInt("data.sync." + locationStr + ".level");

                    String blockLoc = dataFile.getConfig().getString("data.sync." + locationStr + ".block");
                    Block block = blockLoc == null ? null : Arconix.pl().getApi().serialize().unserializeLocation(dataFile.getConfig().getString("data.sync." + locationStr + ".block")).getBlock();

                    TeleportTrigger teleportTrigger = TeleportTrigger.valueOf(dataFile.getConfig().getString("data.sync." + locationStr + ".teleportTrigger"));

                    String playerStr = dataFile.getConfig().getString("data.sync." + locationStr + ".player");
                    String placedByStr = dataFile.getConfig().getString("data.sync." + locationStr + ".placedBy");
                    UUID lastPlayer = playerStr == null ? null : UUID.fromString(playerStr);
                    UUID placedBy = placedByStr == null ? null : UUID.fromString(placedByStr);

                    List<ItemStack> whiteList = (ArrayList<ItemStack>) dataFile.getConfig().getList("data.sync." + locationStr + ".whitelist");
                    List<ItemStack> blackList = (ArrayList<ItemStack>) dataFile.getConfig().getList("data.sync." + locationStr + ".blacklist");
                    List<ItemStack> voidList = (ArrayList<ItemStack>) dataFile.getConfig().getList("data.sync." + locationStr + ".void");

                    Material autoCrafting = Material.valueOf(dataFile.getConfig().getString("data.sync." + locationStr + ".autoCrafting", "AIR"));

                    String blackLoc = dataFile.getConfig().getString("data.sync." + locationStr + ".black");
                    Block black = blackLoc == null ? null : Arconix.pl().getApi().serialize().unserializeLocation(dataFile.getConfig().getString("data.sync." + locationStr + ".black")).getBlock();

                    EFilter filter = new EFilter();

                    filter.setWhiteList(whiteList);
                    filter.setBlackList(blackList);
                    filter.setVoidList(voidList);
                    filter.setEndPoint(black);

                    EHopper hopper = new EHopper(location, levelManager.getLevel(level), lastPlayer, placedBy, block, filter, teleportTrigger, autoCrafting);

                    hopperManager.addHopper(location, hopper);
                }
            }

            // Adding in Boosts
            if (dataFile.getConfig().contains("data.boosts")) {
                for (String key : dataFile.getConfig().getConfigurationSection("data.boosts").getKeys(false)) {
                    if (!dataFile.getConfig().contains("data.boosts." + key + ".Player")) continue;
                    BoostData boostData = new BoostData(
                            dataFile.getConfig().getInt("data.boosts." + key + ".Amount"),
                            Long.parseLong(key),
                            UUID.fromString(dataFile.getConfig().getString("data.boosts." + key + ".Player")));

                    this.boostManager.addBoostToPlayer(boostData);
                }
            }

        }, 10);

        references = new References();

        new HopHandler(this);
        teleportHandler = new TeleportHandler(this);

        new MCUpdate(this, true);
        //new MassiveStats(this, 9000);

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::saveToFile, 6000, 6000);

        PluginManager pluginManager = Bukkit.getPluginManager();

        // Register Listeners
        pluginManager.registerEvents(new HopperListeners(this), this);
        pluginManager.registerEvents(new BlockListeners(this), this);
        pluginManager.registerEvents(new InteractListeners(this), this);
        pluginManager.registerEvents(new InventoryListeners(this), this);

        // Command registration
        this.getCommand("EpicHoppers").setExecutor(new CommandManager(this));

        // Register default hooks
        if (pluginManager.isPluginEnabled("ASkyBlock")) this.register(HookASkyBlock::new);
        if (pluginManager.isPluginEnabled("Factions")) this.register(HookFactions::new);
        if (pluginManager.isPluginEnabled("GriefPrevention")) this.register(HookGriefPrevention::new);
        if (pluginManager.isPluginEnabled("Kingdoms")) this.register(HookKingdoms::new);
        if (pluginManager.isPluginEnabled("PlotSquared")) this.register(HookPlotSquared::new);
        if (pluginManager.isPluginEnabled("RedProtect")) this.register(HookRedProtect::new);
        if (pluginManager.isPluginEnabled("Towny")) this.register(HookTowny::new);
        if (pluginManager.isPluginEnabled("USkyBlock")) this.register(HookUSkyBlock::new);
        if (pluginManager.isPluginEnabled("WorldGuard")) this.register(HookWorldGuard::new);


        console.sendMessage(Arconix.pl().getApi().format().formatText("&a============================="));
    }

    public void onDisable() {
        saveToFile();
        this.protectionHooks.clear();
        console.sendMessage(Arconix.pl().getApi().format().formatText("&a============================="));
        console.sendMessage(Arconix.pl().getApi().format().formatText("&7EpicHoppers " + this.getDescription().getVersion() + " by &5Brianna <3!"));
        console.sendMessage(Arconix.pl().getApi().format().formatText("&7Action: &cDisabling&7..."));
        console.sendMessage(Arconix.pl().getApi().format().formatText("&a============================="));
        dataFile.saveConfig();
    }

    /*
     * Saves registered hopper to file.
     */
    private void saveToFile() {

        // Wipe old hopper information
        dataFile.getConfig().set("data.sync", null);

        /*
         * Dump HopperManager to file.
         */
        for (Hopper hopper : hopperManager.getHoppers().values()) {
            if (hopper.getLevel() == null || hopper.getLocation() == null || hopper.getLocation().getChunk() == null)
                continue;
            String locationStr = Arconix.pl().getApi().serialize().serializeLocation(hopper.getLocation());

            ConfigurationSection sync = dataFile.getConfig().createSection("data.sync." + locationStr);

            sync.set(".level", hopper.getLevel().getLevel());
            sync.set(".block", hopper.getSyncedBlock() == null ? null : Arconix.pl().getApi().serialize().serializeLocation(hopper.getSyncedBlock().getLocation()));
            sync.set(".player", hopper.getLastPlayer() == null ? null : hopper.getLastPlayer().toString());
            sync.set(".placedBy", hopper.getPlacedBy() == null ? null : hopper.getPlacedBy().toString());
            sync.set(".teleportTrigger", hopper.getTeleportTrigger().toString());

            sync.set(".autoCrafting", hopper.getAutoCrafting() == null || hopper.getAutoCrafting() == Material.AIR ? null : hopper.getAutoCrafting().name());
            sync.set(".whitelist", hopper.getFilter().getWhiteList());
            sync.set(".blacklist", hopper.getFilter().getBlackList());
            sync.set(".void", hopper.getFilter().getVoidList());
            sync.set(".black", hopper.getFilter().getEndPoint() == null ? null : Arconix.pl().getApi().serialize().serializeLocation(hopper.getFilter().getEndPoint().getLocation()));
        }

        /*
         * Dump BoostManager to file.
         */
        for (BoostData boostData : boostManager.getBoosts()) {
            String endTime = String.valueOf(boostData.getEndTime());
            dataFile.getConfig().set("data.boosts." + endTime + ".Player", boostData.getPlayer().toString());
            dataFile.getConfig().set("data.boosts." + endTime + ".Amount", boostData.getMultiplier());
        }

        //Save to file
        dataFile.saveConfig();
    }

    private void loadLevelManager() {
        // Load an instance of LevelManager
        levelManager = new ELevelManager();
        /*
         * Register Levels into LevelManager from configuration.
         */
        ((ELevelManager) levelManager).clear();
        for (String levelName : getConfig().getConfigurationSection("settings.levels").getKeys(false)) {
            int level = Integer.valueOf(levelName.split("-")[1]);

            ConfigurationSection levels = getConfig().getConfigurationSection("settings.levels." + levelName);
            
            int radius = levels.getInt("Range");
            int amount = levels.getInt("Amount");
            boolean filter = levels.getBoolean("Filter");
            boolean teleport = levels.getBoolean("Teleport");
            int costExperiance = levels.getInt("Cost-xp");
            int costEconomy = levels.getInt("Cost-eco");

            ArrayList<Module> modules = new ArrayList<>();

            for (String key : levels.getKeys(false)) {
                if (key.equals("Suction") && levels.getInt("Suction") != 0) {
                    modules.add(new ModuleSuction(levels.getInt("Suction")));
                } else if (key.equals("BlockBreak") && levels.getInt("BlockBreak") != 0) {
                    modules.add(new ModuleBlockBreak(levels.getInt("BlockBreak")));
                } else if (key.equals("AutoCrafting")) {
                    modules.add(new ModuleAutoCrafting());
                }

            }
            
            levelManager.addLevel(level, costExperiance, costEconomy, radius, amount, filter, teleport, modules);
        }
    }

    private void setupConfig() {
        settingsManager.updateSettings();

        ConfigurationSection levels = getConfig().createSection("settings.levels");

        if (!levels.contains("Level-1")) {
            levels.set("Level-1.Range", 10);
            levels.set("Level-1.Amount", 1);
            levels.set("Level-1.Cost-xp", 20);
            levels.set("Level-1.Cost-eco", 5000);

            levels.set("Level-2.Range", 20);
            levels.set("Level-2.Amount", 2);
            levels.set("Level-2.Cost-xp", 25);
            levels.set("Level-2.Cost-eco", 7500);

            levels.set("Level-3.Range", 30);
            levels.set("Level-3.Amount", 3);
            levels.set("Level-3.Suction", 1);
            levels.set("Level-3.Cost-xp", 30);
            levels.set("Level-3.Cost-eco", 10000);

            levels.set("Level-4.Range", 40);
            levels.set("Level-4.Amount", 4);
            levels.set("Level-4.Suction", 2);
            levels.set("Level-4.BlockBreak", 4);
            levels.set("Level-4.Cost-xp", 35);
            levels.set("Level-4.Cost-eco", 12000);

            levels.set("Level-5.Range", 50);
            levels.set("Level-5.Amount", 5);
            levels.set("Level-5.Suction", 3);
            levels.set("Level-5.BlockBreak", 2);
            levels.set("Level-5.Cost-xp", 40);
            levels.set("Level-5.Cost-eco", 15000);

            levels.set("Level-6.Range", 60);
            levels.set("Level-6.Amount", 5);
            levels.set("Level-6.Suction", 3);
            levels.set("Level-6.BlockBreak", 2);
            levels.set("Level-6.Filter", true);
            levels.set("Level-6.Teleport", true);
            levels.set("Level-6.Cost-xp", 45);
            levels.set("Level-6.Cost-eco", 20000);

            levels.set("Level-7.Range", 70);
            levels.set("Level-7.Amount", 5);
            levels.set("Level-7.Suction", 3);
            levels.set("Level-7.BlockBreak", 2);
            levels.set("Level-7.Filter", true);
            levels.set("Level-7.Teleport", true);
            levels.set("Level-7.AutoCrafting", true);
            levels.set("Level-7.Cost-xp", 50);
            levels.set("Level-7.Cost-eco", 30000);

        }
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
    }

    public void reload() {
        locale.reloadMessages();
        references = new References();
        reloadConfig();
        saveConfig();
        loadLevelManager();
    }

    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission(getDescription().getName() + ".bypass")) {
            return true;
        }

        for (ProtectionPluginHook hook : protectionHooks)
            if (!hook.canBuild(player, location)) return false;
        return true;
    }

    public boolean isInFaction(String name, Location l) {
        return factionsHook != null && factionsHook.isInClaim(l, name);
    }

    public String getFactionId(String name) {
        return (factionsHook != null) ? factionsHook.getClaimID(name) : null;
    }

    public boolean isInTown(String name, Location l) {
        return townyHook != null && townyHook.isInClaim(l, name);
    }

    public String getTownId(String name) {
        return (townyHook != null) ? townyHook.getClaimID(name) : null;
    }

    @SuppressWarnings("deprecation")
    public String getIslandId(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId().toString();
    }

    @Override
    public Level getLevelFromItem(ItemStack item) {
        if (item.getItemMeta().getDisplayName().contains(":")) {
            String arr[] = item.getItemMeta().getDisplayName().replace("§", "").split(":");
            return getLevelManager().getLevel(Integer.parseInt(arr[0]));
        } else {
            return getLevelManager().getLowestLevel();
        }
    }

    @Override
    public ItemStack newHopperItem(Level level) {
        ItemStack item = new ItemStack(Material.HOPPER, 1);
        ItemMeta itemmeta = item.getItemMeta();
        itemmeta.setDisplayName(Arconix.pl().getApi().format().formatText(Methods.formatName(level.getLevel(), true)));
        item.setItemMeta(itemmeta);
        return item;
    }

    public Locale getLocale() {
        return locale;
    }

    public TeleportHandler getTeleportHandler() {
        return teleportHandler;
    }

    public BoostManager getBoostManager() {
        return boostManager;
    }

    @Override
    public LevelManager getLevelManager() {
        return levelManager;
    }

    @Override
    public HopperManager getHopperManager() {
        return hopperManager;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    @Override
    public void register(Supplier<ProtectionPluginHook> hookSupplier) {
        this.registerProtectionHook(hookSupplier.get());
    }

    public ConfigWrapper getDataFile() {
        return dataFile;
    }

    @Override
    public void registerProtectionHook(ProtectionPluginHook hook) {
        Preconditions.checkNotNull(hook, "Cannot register null hook");
        Preconditions.checkNotNull(hook.getPlugin(), "Protection plugin hook returns null plugin instance (#getPlugin())");

        JavaPlugin hookPlugin = hook.getPlugin();
        for (ProtectionPluginHook existingHook : protectionHooks) {
            if (existingHook.getPlugin().equals(hookPlugin)) {
                throw new IllegalArgumentException("Hook already registered");
            }
        }

        this.hooksFile.getConfig().addDefault("hooks." + hookPlugin.getName(), true);
        if (!hooksFile.getConfig().getBoolean("hooks." + hookPlugin.getName(), true)) return;
        this.hooksFile.getConfig().options().copyDefaults(true);
        this.hooksFile.saveConfig();

        this.protectionHooks.add(hook);
        this.getLogger().info("Registered protection hook for plugin: " + hook.getPlugin().getName());
    }

}
