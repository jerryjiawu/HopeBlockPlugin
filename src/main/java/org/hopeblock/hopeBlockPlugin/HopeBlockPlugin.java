package org.hopeblock.hopeBlockPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HopeBlockPlugin extends JavaPlugin implements Listener {

    private Map<String, String> playerTimezones = new HashMap<>();
    private Map<String, String> timezoneOptions = new LinkedHashMap<>();
    private File timezoneFile;
    private FileConfiguration timezoneConfig;

    @Override
    public void onEnable() {
        // Initialize timezone options
        initializeTimezones();
        
        // Load timezone data
        loadTimezoneData();
        
        // Register this class as event listener
        getServer().getPluginManager().registerEvents(this, this);
        
        // Save default config
        saveDefaultConfig();
        setDefaultConfig();
        
        getLogger().info("HopeBlock Compass Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        saveTimezoneData();
        getLogger().info("HopeBlock Compass Plugin has been disabled!");
    }
    
    private void initializeTimezones() {
        timezoneOptions.put("EST", "America/New_York");
        timezoneOptions.put("PST", "America/Los_Angeles");
        timezoneOptions.put("CST", "America/Chicago");
        timezoneOptions.put("MST", "America/Denver");
        timezoneOptions.put("GMT", "GMT");
        timezoneOptions.put("CET", "Europe/Berlin");
        timezoneOptions.put("JST", "Asia/Tokyo");
        timezoneOptions.put("AEST", "Australia/Sydney");
        timezoneOptions.put("IST", "Asia/Kolkata");
        timezoneOptions.put("UTC", "UTC");
    }
    
    private void setDefaultConfig() {
        FileConfiguration config = getConfig();
        
        if (!config.contains("lobby-spawn")) {
            config.set("lobby-spawn.world", "world");
            config.set("lobby-spawn.x", 0.0);
            config.set("lobby-spawn.y", 100.0);
            config.set("lobby-spawn.z", 0.0);
            config.set("lobby-spawn.yaw", 0.0f);
            config.set("lobby-spawn.pitch", 0.0f);
        }
        
        if (!config.contains("minigames")) {
            config.set("minigames.bedwars.world", "world");
            config.set("minigames.bedwars.x", 100.0);
            config.set("minigames.bedwars.y", 64.0);
            config.set("minigames.bedwars.z", 100.0);
            
            config.set("minigames.pvp.world", "world");
            config.set("minigames.pvp.x", -100.0);
            config.set("minigames.pvp.y", 64.0);
            config.set("minigames.pvp.z", -100.0);
            
            config.set("minigames.survival.world", "world");
            config.set("minigames.survival.x", 0.0);
            config.set("minigames.survival.y", 64.0);
            config.set("minigames.survival.z", 200.0);
        }
        
        saveConfig();
    }
    
    private void loadTimezoneData() {
        // Ensure the plugin data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        timezoneFile = new File(getDataFolder(), "timezones.yml");
        if (!timezoneFile.exists()) {
            try {
                timezoneFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create timezones.yml: " + e.getMessage());
            }
        }
        timezoneConfig = YamlConfiguration.loadConfiguration(timezoneFile);
        
        if (timezoneConfig.contains("player-timezones")) {
            for (String uuid : timezoneConfig.getConfigurationSection("player-timezones").getKeys(false)) {
                playerTimezones.put(uuid, timezoneConfig.getString("player-timezones." + uuid));
            }
        }
    }
    
    private void saveTimezoneData() {
        for (Map.Entry<String, String> entry : playerTimezones.entrySet()) {
            timezoneConfig.set("player-timezones." + entry.getKey(), entry.getValue());
        }
        try {
            timezoneConfig.save(timezoneFile);
        } catch (IOException e) {
            getLogger().warning("Could not save timezones.yml");
        }
    }
    
    private ItemStack createCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        
        meta.setDisplayName("§6§lHope Compass");
        meta.setLore(Arrays.asList(
            "§7Right-click to open the compass menu!",
            "§7• Select your timezone",
            "§7• Teleport to minigames",
            "§7• Return to lobby"
        ));
        
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        meta.setCustomModelData(12345);
        
        // Add NBT data to prevent FAWE interaction
        compass.setItemMeta(meta);
        return compass;
    }
    
    private boolean isHopeCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 12345;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Give compass to player
        ItemStack compass = createCompass();
        player.getInventory().setItem(8, compass);
        
        // Welcome message with current time
        String currentTime = getCurrentTime(player);
        player.sendMessage("§6Welcome to Hope Craft!");
        player.sendMessage("§7Your current time: §e" + currentTime);
        player.sendMessage("§7Use your compass to navigate and change settings!");
    }
    
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !isHopeCompass(item)) {
            return;
        }
        
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            openMainMenu(player);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        if (title.equals("§6§lHope Compass Menu")) {
            event.setCancelled(true);
            
            switch (clickedItem.getType()) {
                case CLOCK:
                    openTimezoneMenu(player);
                    break;
                case DIAMOND_SWORD:
                    openMinigamesMenu(player);
                    break;
                case EMERALD:
                    player.closeInventory();
                    teleportToLobby(player);
                    break;
                case BARRIER:
                    player.closeInventory();
                    break;
            }
        } else if (title.equals("§a§lSelect Timezone")) {
            event.setCancelled(true);
            
            if (clickedItem.getType() == Material.CLOCK) {
                String displayName = clickedItem.getItemMeta().getDisplayName().replace("§e", "");
                String timezone = timezoneOptions.get(displayName);
                
                if (timezone != null) {
                    setPlayerTimezone(player, timezone);
                    player.sendMessage("§a§lTimezone updated to " + displayName + "!");
                    String newTime = getCurrentTime(player);
                    player.sendMessage("§7Your current time: §e" + newTime);
                    openMainMenu(player);
                }
            } else if (clickedItem.getType() == Material.ARROW) {
                openMainMenu(player);
            }
        } else if (title.equals("§c§lMinigames")) {
            event.setCancelled(true);
            
            switch (clickedItem.getType()) {
                case RED_BED:
                    player.closeInventory();
                    teleportToMinigame(player, "bedwars");
                    break;
                case IRON_SWORD:
                    player.closeInventory();
                    teleportToMinigame(player, "pvp");
                    break;
                case GRASS_BLOCK:
                    player.closeInventory();
                    teleportToMinigame(player, "survival");
                    break;
                case ARROW:
                    openMainMenu(player);
                    break;
            }
        }
    }
    
    private void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lHope Compass Menu");
        
        // Timezone item
        ItemStack timezoneItem = new ItemStack(Material.CLOCK);
        ItemMeta timezoneMeta = timezoneItem.getItemMeta();
        timezoneMeta.setDisplayName("§a§lTimezone Settings");
        String currentTimezone = getTimezoneDisplayName(getPlayerTimezone(player));
        String currentTime = getCurrentTime(player);
        timezoneMeta.setLore(Arrays.asList(
            "§7Current timezone: §e" + currentTimezone,
            "§7Current time: §e" + currentTime,
            "",
            "§7Click to change your timezone!"
        ));
        timezoneItem.setItemMeta(timezoneMeta);
        
        // Minigames item
        ItemStack minigamesItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta minigamesMeta = minigamesItem.getItemMeta();
        minigamesMeta.setDisplayName("§c§lMinigames");
        minigamesMeta.setLore(Arrays.asList(
            "§7Teleport to different minigames!",
            "§7• Bedwars",
            "§7• PvP Arena", 
            "§7• Survival",
            "",
            "§7Click to view minigames!"
        ));
        minigamesItem.setItemMeta(minigamesMeta);
        
        // Lobby item
        ItemStack lobbyItem = new ItemStack(Material.EMERALD);
        ItemMeta lobbyMeta = lobbyItem.getItemMeta();
        lobbyMeta.setDisplayName("§2§lReturn to Lobby");
        lobbyMeta.setLore(Arrays.asList(
            "§7Teleport back to the lobby!",
            "",
            "§7Click to return to lobby!"
        ));
        lobbyItem.setItemMeta(lobbyMeta);
        
        // Close item
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c§lClose");
        closeMeta.setLore(Arrays.asList("§7Close this menu"));
        closeItem.setItemMeta(closeMeta);
        
        // Place items in inventory
        inv.setItem(11, timezoneItem);
        inv.setItem(13, minigamesItem);
        inv.setItem(15, lobbyItem);
        inv.setItem(26, closeItem);
        
        // Fill empty slots with glass panes
        fillEmptySlots(inv);
        
        player.openInventory(inv);
    }
    
    private void openTimezoneMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§a§lSelect Timezone");
        
        int slot = 10;
        for (String displayName : timezoneOptions.keySet()) {
            ItemStack timezoneItem = new ItemStack(Material.CLOCK);
            ItemMeta meta = timezoneItem.getItemMeta();
            meta.setDisplayName("§e" + displayName);
            
            String timezone = timezoneOptions.get(displayName);
            boolean isSelected = timezone.equals(getPlayerTimezone(player));
            
            if (isSelected) {
                meta.setLore(Arrays.asList(
                    "§7Timezone: §e" + timezone,
                    "",
                    "§a§l✓ Currently selected"
                ));
            } else {
                meta.setLore(Arrays.asList(
                    "§7Timezone: §e" + timezone,
                    "",
                    "§7Click to select this timezone!"
                ));
            }
            
            timezoneItem.setItemMeta(meta);
            inv.setItem(slot, timezoneItem);
            
            slot++;
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot == 35) slot = 37;
        }
        
        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§7« Back");
        backItem.setItemMeta(backMeta);
        inv.setItem(45, backItem);
        
        fillEmptySlots(inv);
        player.openInventory(inv);
    }
    
    private void openMinigamesMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§c§lMinigames");
        
        // Bedwars
        ItemStack bedwars = new ItemStack(Material.RED_BED);
        ItemMeta bedwarsMeta = bedwars.getItemMeta();
        bedwarsMeta.setDisplayName("§c§lBedwars");
        bedwarsMeta.setLore(Arrays.asList(
            "§7Protect your bed and destroy",
            "§7the enemy beds to win!",
            "",
            "§7Click to join!"
        ));
        bedwars.setItemMeta(bedwarsMeta);
        
        // PvP Arena
        ItemStack pvp = new ItemStack(Material.IRON_SWORD);
        ItemMeta pvpMeta = pvp.getItemMeta();
        pvpMeta.setDisplayName("§4§lPvP Arena");
        pvpMeta.setLore(Arrays.asList(
            "§7Fight other players in",
            "§7intense PvP battles!",
            "",
            "§7Click to join!"
        ));
        pvp.setItemMeta(pvpMeta);
        
        // Survival
        ItemStack survival = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta survivalMeta = survival.getItemMeta();
        survivalMeta.setDisplayName("§2§lSurvival");
        survivalMeta.setLore(Arrays.asList(
            "§7Build, mine, and survive",
            "§7in the wilderness!",
            "",
            "§7Click to join!"
        ));
        survival.setItemMeta(survivalMeta);
        
        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§7« Back");
        backItem.setItemMeta(backMeta);
        
        inv.setItem(11, bedwars);
        inv.setItem(13, pvp);
        inv.setItem(15, survival);
        inv.setItem(18, backItem);
        
        fillEmptySlots(inv);
        player.openInventory(inv);
    }
    
    private void teleportToLobby(Player player) {
        String worldName = getConfig().getString("lobby-spawn.world");
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            player.sendMessage("§cLobby world not found! Please contact an administrator.");
            return;
        }
        
        Location spawnLocation = world.getSpawnLocation();
        player.teleport(spawnLocation);
        player.sendMessage("§a§lTeleported to lobby!");
    }
    
    private void teleportToMinigame(Player player, String minigame) {
        String path = "minigames." + minigame;
        
        if (!getConfig().contains(path)) {
            player.sendMessage("§cMinigame '" + minigame + "' not found!");
            return;
        }
        
        String worldName = getConfig().getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            player.sendMessage("§cMinigame world not found! Please contact an administrator.");
            return;
        }
        
        double x = getConfig().getDouble(path + ".x");
        double y = getConfig().getDouble(path + ".y");
        double z = getConfig().getDouble(path + ".z");
        
        Location location = new Location(world, x, y, z);
        player.teleport(location);
        player.sendMessage("§a§lTeleported to " + minigame + "!");
    }
    
    private void fillEmptySlots(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }
    
    private void setPlayerTimezone(Player player, String timezone) {
        playerTimezones.put(player.getUniqueId().toString(), timezone);
        saveTimezoneData();
    }
    
    private String getPlayerTimezone(Player player) {
        return playerTimezones.getOrDefault(player.getUniqueId().toString(), "UTC");
    }
    
    private String getCurrentTime(Player player) {
        String timezone = getPlayerTimezone(player);
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            return now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss z"));
        } catch (Exception e) {
            // Fallback to UTC
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
            return now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss z"));
        }
    }
    
    private String getTimezoneDisplayName(String timezoneId) {
        for (Map.Entry<String, String> entry : timezoneOptions.entrySet()) {
            if (entry.getValue().equals(timezoneId)) {
                return entry.getKey();
            }
        }
        return timezoneId;
    }
}
