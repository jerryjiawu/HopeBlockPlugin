package org.hopeblock.hopeBlockPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
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
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public final class HopeBlockPlugin extends JavaPlugin implements Listener {

    private Map<String, String> playerTimezones = new HashMap<>();
    private Map<String, String> timezoneOptions = new LinkedHashMap<>();
    private File timezoneFile;
    private FileConfiguration timezoneConfig;
    private boolean countdownActive = true;
    private BukkitRunnable countdownTask;
    private Set<String> playersWhoSawNewYear = new HashSet<>();
    private boolean testCountdownActive = false;
    private int testCountdownSeconds = 20;
    private BukkitRunnable testCountdownTask;
    private boolean finalCountdownActive = false;
    private int finalCountdownSeconds = 20;
    private BossBar donationBar;
    private BukkitRunnable donationTask;
    private double currentDonationAmount = 0.0;
    private final double DONATION_GOAL = 1000.0;
    private HttpClient httpClient;
    private Gson gson;

    @Override
    public void onEnable() {
        // Initialize HTTP client and Gson
        httpClient = HttpClient.newHttpClient();
        gson = new Gson();
        
        // Initialize timezone options
        initializeTimezones();
        
        // Load timezone data
        loadTimezoneData();
        
        // Register this class as event listener
        getServer().getPluginManager().registerEvents(this, this);
        
        // Save default config
        saveDefaultConfig();
        setDefaultConfig();
        
        // Start countdown task
        startCountdownTask();
        
        // Create and start donation tracking
        createDonationBar();
        startDonationTask();
        
        getLogger().info("HopeBlock Compass Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        saveTimezoneData();
        
        // Cancel countdown tasks
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        if (testCountdownTask != null) {
            testCountdownTask.cancel();
        }
        if (donationTask != null) {
            donationTask.cancel();
        }
        
        // Remove donation bar from all players
        if (donationBar != null) {
            donationBar.removeAll();
        }
        
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
        
        // Add player to donation bar
        if (donationBar != null) {
            donationBar.addPlayer(player);
        }
        
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
    
    private void startCountdownTask() {
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!countdownActive) {
                    return;
                }
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateCountdownDisplay(player);
                }
            }
        };
        countdownTask.runTaskTimer(this, 0L, 20L); // Run every second
    }
    
    private void updateCountdownDisplay(Player player) {
        String playerId = player.getUniqueId().toString();
        
        // Skip if player already saw New Year celebration
        if (playersWhoSawNewYear.contains(playerId)) {
            return;
        }
        
        // If test countdown is active, show test countdown instead
        if (testCountdownActive) {
            updateTestCountdownDisplay(player);
            return;
        }
        
        String timezone = getPlayerTimezone(player);
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        
        // Calculate time until New Year (January 1st of next year)
        ZonedDateTime newYear = ZonedDateTime.of(now.getYear() + 1, 1, 1, 0, 0, 0, 0, zoneId);
        Duration duration = Duration.between(now, newYear);
        
        if (duration.isNegative() || duration.isZero()) {
            // New Year reached!
            player.sendTitle("§c§lHAPPY NEW YEAR!", "§e" + now.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss z")), 10, 70, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // Mark player as having seen New Year celebration
            playersWhoSawNewYear.add(playerId);
            return;
        }
        
        // Check if we're within 20 seconds of New Year
        long totalSeconds = duration.getSeconds();
        if (totalSeconds <= 20 && !finalCountdownActive) {
            // Start the final 20-second countdown
            startFinalCountdown();
            return;
        }
        
        if (finalCountdownActive) {
            updateFinalCountdownDisplay(player);
            return;
        }
        
        // Format regular countdown
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        String countdown = String.format("§c⏰ %dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        String currentTime = "§e🕒 " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss z"));
        
        // Use action bar for always-visible display
        player.sendActionBar(countdown + " §7| " + currentTime);
    }
    
    private void updateFinalCountdownDisplay(Player player) {
        if (finalCountdownSeconds <= 0) {
            return;
        }
        
        String currentTime = "§e" + getCurrentTime(player);
        
        // Display large countdown in center of screen
        String countdownText = "§c§l" + finalCountdownSeconds;
        player.sendTitle(countdownText, currentTime, 0, 25, 5);
    }
    
    private void startFinalCountdown() {
        finalCountdownActive = true;
        finalCountdownSeconds = 20;
        
        BukkitRunnable finalCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!finalCountdownActive) {
                    cancel();
                    return;
                }
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateFinalCountdownDisplay(player);
                }
                
                // Decrement countdown
                finalCountdownSeconds--;
                
                // Check if countdown reached 0
                if (finalCountdownSeconds <= 0) {
                    // Show Happy New Year to all players
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle("§c§lHAPPY NEW YEAR!", "§e" + getCurrentTime(player), 10, 70, 20);
                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        
                        // Mark all players as having seen New Year celebration
                        playersWhoSawNewYear.add(player.getUniqueId().toString());
                    }
                    
                    // Launch fireworks for New Year celebration
                    startFireworkProcedure();
                    
                    // Stop the final countdown
                    finalCountdownActive = false;
                    finalCountdownSeconds = 20;
                    cancel();
                }
            }
        };
        finalCountdownTask.runTaskTimer(this, 0L, 20L); // Run every second
    }
    
    private void updateTestCountdownDisplay(Player player) {
        if (testCountdownSeconds <= 0) {
            return;
        }
        
        String currentTime = "§e" + getCurrentTime(player);
        
        // Display large countdown in center of screen
        String countdownText = "§c§l" + testCountdownSeconds;
        player.sendTitle(countdownText, currentTime, 0, 25, 5);
    }
    
    private void startTestCountdown() {
        testCountdownActive = true;
        testCountdownSeconds = 20;
        
        testCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!testCountdownActive) {
                    return;
                }
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateTestCountdownDisplay(player);
                }
                
                // Decrement countdown
                testCountdownSeconds--;
                
                // Check if countdown reached 0
                if (testCountdownSeconds <= 0) {
                    // Show Happy New Year to all players
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle("§c§lHAPPY NEW YEAR!", "§e" + getCurrentTime(player), 10, 70, 20);
                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                    
                    // Launch fireworks for test celebration
                    startFireworkProcedure();
                    
                    // Stop the test countdown
                    testCountdownActive = false;
                    testCountdownSeconds = 20;
                    cancel();
                }
            }
        };
        testCountdownTask.runTaskTimer(this, 0L, 20L); // Run every second
    }
    
    private void createDonationBar() {
        donationBar = Bukkit.createBossBar("§6§lDonation Progress: §e$0.00 CAD §6/ §e$1,000.00 CAD", BarColor.YELLOW, BarStyle.SOLID);
        donationBar.setProgress(0.0);
        
        // Add all online players to the boss bar
        for (Player player : Bukkit.getOnlinePlayers()) {
            donationBar.addPlayer(player);
        }
    }
    
    private void startDonationTask() {
        donationTask = new BukkitRunnable() {
            @Override
            public void run() {
                fetchDonationAmount();
            }
        };
        // Update every 30 seconds (600 ticks)
        donationTask.runTaskTimerAsynchronously(this, 0L, 600L);
    }
    
    private void fetchDonationAmount() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.hopeblock.org/total_donations"))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String jsonResponse = response.body();
                parseAndUpdateDonations(jsonResponse);
            } else {
                getLogger().warning("Failed to fetch donation data. Status code: " + response.statusCode());
            }
        } catch (Exception e) {
            getLogger().warning("Error fetching donation data: " + e.getMessage());
        }
    }
    
    private void parseAndUpdateDonations(String jsonResponse) {
        try {
            // Simple JSON parsing for {"amount":"0.00"}
            String amount = jsonResponse.replaceAll(".*\"amount\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            currentDonationAmount = Double.parseDouble(amount);
            
            // Update boss bar on main thread
            Bukkit.getScheduler().runTask(this, () -> updateDonationBar());
            
        } catch (Exception e) {
            getLogger().warning("Error parsing donation data: " + e.getMessage());
        }
    }
    
    private void updateDonationBar() {
        if (donationBar == null) return;
        
        double progress = Math.min(currentDonationAmount / DONATION_GOAL, 1.0);
        donationBar.setProgress(progress);
        
        String formattedCurrent = String.format("%.2f", currentDonationAmount);
        String formattedGoal = String.format("%.2f", DONATION_GOAL);
        
        BarColor color;
        if (progress >= 1.0) {
            color = BarColor.GREEN;
        } else if (progress >= 0.75) {
            color = BarColor.BLUE;
        } else if (progress >= 0.5) {
            color = BarColor.YELLOW;
        } else if (progress >= 0.25) {
            color = BarColor.RED;
        } else {
            color = BarColor.RED;
        }
        
        donationBar.setColor(color);
        donationBar.setTitle("§6§lDonation Progress: §e$" + formattedCurrent + " CAD §6/ §e$" + formattedGoal + " CAD");
    }
    
    private void startFireworkProcedure() {
        getLogger().info("Starting firework procedure...");
        
        // Place obsidian block to block beacon beam
        World world = Bukkit.getWorld("world");
        if (world == null) {
            getLogger().warning("World not found for firework procedure!");
            return;
        }
        
        Location obsidianLocation = new Location(world, 620, 65, -38);
        obsidianLocation.getBlock().setType(Material.OBSIDIAN);
        getLogger().info("Placed obsidian block at " + obsidianLocation);
        
        // Fetch firework orders asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                fetchAndLaunchFireworks(obsidianLocation);
            }
        }.runTaskAsynchronously(this);
    }
    
    private void fetchAndLaunchFireworks(Location obsidianLocation) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.hopeblock.org/admin/orders?api_key=a22bfa003a7e375c04e3c9f2e5482047105b34d6abd22885f2dc7168f239a9f4"))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String jsonResponse = response.body();
                parseAndLaunchFireworks(jsonResponse, obsidianLocation);
            } else {
                getLogger().warning("Failed to fetch firework orders. Status code: " + response.statusCode());
                // Remove obsidian block on failure
                Bukkit.getScheduler().runTask(this, () -> obsidianLocation.getBlock().setType(Material.AIR));
            }
        } catch (Exception e) {
            getLogger().warning("Error fetching firework orders: " + e.getMessage());
            // Remove obsidian block on failure
            Bukkit.getScheduler().runTask(this, () -> obsidianLocation.getBlock().setType(Material.AIR));
        }
    }
    
    private void parseAndLaunchFireworks(String jsonResponse, Location obsidianLocation) {
        try {
            List<FireworkOrder> orders = parseFireworkOrders(jsonResponse);
            getLogger().info("Found " + orders.size() + " firework orders");
            
            // Launch fireworks on main thread
            Bukkit.getScheduler().runTask(this, () -> launchOrderedFireworks(orders, obsidianLocation));
            
        } catch (Exception e) {
            getLogger().warning("Error parsing firework orders: " + e.getMessage());
            // Remove obsidian block on failure
            Bukkit.getScheduler().runTask(this, () -> obsidianLocation.getBlock().setType(Material.AIR));
        }
    }
    
    private List<FireworkOrder> parseFireworkOrders(String jsonResponse) {
        List<FireworkOrder> orders = new ArrayList<>();
        
        try {
            getLogger().info("Parsing JSON response with Gson...");
            
            // Parse the JSON response using Gson
            FireworkResponse response = gson.fromJson(jsonResponse, FireworkResponse.class);
            
            if (response == null || response.orders == null) {
                getLogger().warning("Invalid response structure");
                return orders;
            }
            
            getLogger().info("Found " + response.orders.size() + " orders in response");
            
            for (FireworkOrderJson orderJson : response.orders) {
                List<FireworkCharge> charges = new ArrayList<>();
                
                if (orderJson.charges != null) {
                    for (FireworkChargeJson chargeJson : orderJson.charges) {
                        String colorHex = chargeJson.color != null ? chargeJson.color.hex : "#FFFFFF";
                        String typeName = chargeJson.type != null ? chargeJson.type.name : "Normal";
                        String effectName = chargeJson.effects != null ? chargeJson.effects.name : "Normal";
                        
                        getLogger().info("Parsed charge: " + typeName + " - " + colorHex + " (" + effectName + ")");
                        charges.add(new FireworkCharge(colorHex, typeName, effectName));
                    }
                }
                
                FireworkOrder order = new FireworkOrder(orderJson.quantity, charges);
                orders.add(order);
                getLogger().info("Successfully parsed order with " + order.quantity + " fireworks and " + order.charges.size() + " charges");
            }
            
            getLogger().info("Total orders parsed: " + orders.size());
            return orders;
            
        } catch (JsonSyntaxException e) {
            getLogger().warning("JSON parsing error: " + e.getMessage());
            e.printStackTrace();
            return orders;
        } catch (Exception e) {
            getLogger().warning("Error parsing orders: " + e.getMessage());
            e.printStackTrace();
            return orders;
        }
    }
    
    private void launchOrderedFireworks(List<FireworkOrder> orders, Location obsidianLocation) {
        int totalFireworks = orders.stream().mapToInt(order -> order.quantity).sum();
        getLogger().info("Launching " + totalFireworks + " fireworks total across " + orders.size() + " orders");
        
        new BukkitRunnable() {
            private int currentOrderIndex = 0;
            private int remainingInCurrentOrder = 0;
            private int totalLaunched = 0;
            
            @Override
            public void run() {
                // Check if we need to start a new order
                if (remainingInCurrentOrder <= 0) {
                    if (currentOrderIndex >= orders.size()) {
                        // All orders complete, remove obsidian
                        obsidianLocation.getBlock().setType(Material.AIR);
                        getLogger().info("Firework procedure complete! Launched " + totalLaunched + " fireworks total.");
                        cancel();
                        return;
                    }
                    
                    // Start next order
                    FireworkOrder currentOrder = orders.get(currentOrderIndex);
                    remainingInCurrentOrder = currentOrder.quantity;
                    getLogger().info("Starting order " + (currentOrderIndex + 1) + "/" + orders.size() + " with " + remainingInCurrentOrder + " fireworks");
                    currentOrderIndex++;
                }
                
                // Launch one firework from current order
                FireworkOrder currentOrder = orders.get(currentOrderIndex - 1);
                launchFirework(currentOrder.charges, obsidianLocation);
                remainingInCurrentOrder--;
                totalLaunched++;
                
                getLogger().info("Launched firework " + totalLaunched + "/" + totalFireworks + " (" + remainingInCurrentOrder + " remaining in current order)");
            }
        }.runTaskTimer(this, 0L, ThreadLocalRandom.current().nextInt(20, 81)); // 1-4 second intervals
    }
    
    private void launchFirework(List<FireworkCharge> charges, Location baseLocation) {
        World world = baseLocation.getWorld();
        if (world == null) return;
        
        // Random location within 6 block radius, 3 blocks higher
        double x = baseLocation.getX() + ThreadLocalRandom.current().nextDouble(-6.0, 6.0);
        double y = baseLocation.getY() + 3 + ThreadLocalRandom.current().nextDouble(0, 2.0); // 3-5 blocks higher
        double z = baseLocation.getZ() + ThreadLocalRandom.current().nextDouble(-6.0, 6.0);
        
        Location launchLocation = new Location(world, x, y, z);
        Firework firework = world.spawn(launchLocation, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        
        // Convert charges to Bukkit FireworkEffects
        for (FireworkCharge charge : charges) {
            FireworkEffect effect = createFireworkEffect(charge);
            if (effect != null) {
                meta.addEffect(effect);
            }
        }
        
        // Random power/height (1-3)
        meta.setPower(ThreadLocalRandom.current().nextInt(1, 4));
        firework.setFireworkMeta(meta);
        
        getLogger().info("Launched firework at " + launchLocation + " with " + charges.size() + " effects");
    }
    
    private FireworkEffect createFireworkEffect(FireworkCharge charge) {
        try {
            // Parse color from hex
            Color color = Color.fromRGB(Integer.parseInt(charge.colorHex.substring(1), 16));
            
            // Determine type - mapping API names to Minecraft firework types
            FireworkEffect.Type type;
            String typeNameLower = charge.typeName.toLowerCase();
            getLogger().info("Mapping firework type: '" + charge.typeName + "' -> '" + typeNameLower + "'");
            
            switch (typeNameLower) {
                case "star":
                    type = FireworkEffect.Type.STAR;  // Star-shaped explosion
                    getLogger().info("Using STAR type");
                    break;
                case "creeper":
                    type = FireworkEffect.Type.CREEPER;  // Creeper face explosion
                    getLogger().info("Using CREEPER type");
                    break;
                case "fire charge":
                    type = FireworkEffect.Type.BALL_LARGE;  // Large ball explosion
                    getLogger().info("Using BALL_LARGE type");
                    break;
                case "burst":
                    type = FireworkEffect.Type.BURST;  // Burst explosion
                    getLogger().info("Using BURST type");
                    break;
                case "normal":
                    type = FireworkEffect.Type.BALL;  // Normal small ball explosion
                    getLogger().info("Using BALL type for Normal");
                    break;
                default:
                    type = FireworkEffect.Type.BALL;  // Default fallback
                    getLogger().warning("Unknown firework type '" + charge.typeName + "', using BALL as fallback");
                    break;
            }
            
            FireworkEffect.Builder builder = FireworkEffect.builder()
                    .with(type)
                    .withColor(color)
                    .withFade(Color.WHITE); // Add fade color for better visibility
            
            // Add effects based on effect name
            String effectLower = charge.effectName.toLowerCase();
            if (effectLower.contains("trail") && effectLower.contains("twinkle")) {
                builder.withTrail();
                builder.withFlicker();
                getLogger().info("Added trail + twinkle effects to firework");
            } else if (effectLower.contains("trail")) {
                builder.withTrail();
                getLogger().info("Added trail effect to firework");
            } else if (effectLower.contains("twinkle")) {
                builder.withFlicker();
                getLogger().info("Added twinkle effect to firework");
            }
            
            FireworkEffect effect = builder.build();
            getLogger().info("Created firework effect: " + type + " with color " + charge.colorHex + " and effect " + charge.effectName);
            return effect;
        } catch (Exception e) {
            getLogger().warning("Error creating firework effect for " + charge.colorHex + ": " + e.getMessage());
            // Return a basic effect as fallback
            return FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL)
                    .withColor(Color.WHITE)
                    .build();
        }
    }
    
    // JSON response classes for Gson
    private static class FireworkResponse {
        List<FireworkOrderJson> orders;
        StatsJson stats;
        int total;
    }
    
    private static class FireworkOrderJson {
        int charge_count;
        List<FireworkChargeJson> charges;
        String created_at;
        String donated_amount;
        String donation_status;
        String donor_name;
        String order_id;
        int quantity;
        String total_price;
    }
    
    private static class FireworkChargeJson {
        ColorJson color;
        EffectJson effects;
        TypeJson type;
    }
    
    private static class ColorJson {
        String hex;
        String name;
    }
    
    private static class EffectJson {
        int id;
        String name;
    }
    
    private static class TypeJson {
        int id;
        String name;
    }
    
    private static class StatsJson {
        String avg_order_value;
        int completed_donations;
        String total_donated;
        int total_fireworks_ordered;
        int total_orders;
    }
    
    // Helper classes for firework data
    private static class FireworkOrder {
        final int quantity;
        final List<FireworkCharge> charges;
        
        FireworkOrder(int quantity, List<FireworkCharge> charges) {
            this.quantity = quantity;
            this.charges = charges;
        }
    }
    
    private static class FireworkCharge {
        final String colorHex;
        final String typeName;
        final String effectName;
        
        FireworkCharge(String colorHex, String typeName, String effectName) {
            this.colorHex = colorHex;
            this.typeName = typeName;
            this.effectName = effectName;
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("countdown")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (args.length == 0) {
                player.sendMessage("§e/countdown toggle - Toggle countdown display");
                player.sendMessage("§e/countdown test - Test New Year celebration");
                player.sendMessage("§e/countdown fireworks - Launch donation fireworks");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("toggle")) {
                countdownActive = !countdownActive;
                if (countdownActive) {
                    player.sendMessage("§a§lCountdown display activated!");
                    // Show countdown for all players
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        updateCountdownDisplay(p);
                    }
                } else {
                    player.sendMessage("§c§lCountdown display deactivated!");
                    // Clear action bars for all players
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendActionBar("");
                    }
                    // Reset New Year tracking
                    playersWhoSawNewYear.clear();
                }
                return true;
            }
            
            if (args[0].equalsIgnoreCase("test")) {
                if (testCountdownActive) {
                    player.sendMessage("§c§lTest countdown already running!");
                    return true;
                }
                
                player.sendMessage("§a§lStarting test countdown from 20 seconds!");
                startTestCountdown();
                return true;
            }
            
            if (args[0].equalsIgnoreCase("fireworks")) {
                if (!player.hasPermission("hopeblock.admin")) {
                    player.sendMessage("§c§lYou don't have permission to use this command!");
                    return true;
                }
                
                player.sendMessage("§a§lStarting firework procedure...");
                startFireworkProcedure();
                return true;
            }
            
            return true;
        }
        
        return false;
    }
}
