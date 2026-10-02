package dev.xeaf.simpleveinenchants;

import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Orientable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleVeinEnchants extends JavaPlugin implements Listener {

    public static final TypedKey<Enchantment> VEINMINE_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:veinmine"));
    public static final TypedKey<Enchantment> LUMBERJACK_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:lumberjack"));
    public static final TypedKey<Enchantment> HARVEST_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:harvest"));
    public static final TypedKey<Enchantment> EXCAVATOR_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:excavator"));
    public static final TypedKey<Enchantment> ANTIGRAVITY_KEY = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("xeaf:antigravity"));

    private static final List<TypedKey<Enchantment>> CUSTOM_ENCHANT_KEYS =
            List.of(VEINMINE_KEY, LUMBERJACK_KEY, HARVEST_KEY, EXCAVATOR_KEY, ANTIGRAVITY_KEY);
    private static final Random RANDOM = new Random();

    // Players for whom we are currently firing BlockBreakEvent for the *extra* blocks of a multi-break.
    // Our own onBlockBreak must ignore those events, otherwise it would recurse.
    private final Set<UUID> internalBreaks = ConcurrentHashMap.newKeySet();

    // True only if the Floodgate plugin is actually installed. Guards every Bedrock-only
    // code path below, and BedrockCompat (the only class that references Floodgate types)
    // is never touched unless this is true - so servers without Floodgate never try to
    // load a Floodgate class at all.
    private boolean hasFloodgate = false;

    private static final boolean IS_FOLIA = checkFolia();
    private static Method isOwnedMethod;

    static {
        if (IS_FOLIA) {
            try {
                isOwnedMethod = org.bukkit.Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            } catch (Exception e) {
                isOwnedMethod = null;
            }
        }
    }

    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isSafeToProcess(Block block) {
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return false;
        if (IS_FOLIA && isOwnedMethod != null) {
            try {
                return (boolean) isOwnedMethod.invoke(null, block.getLocation());
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        hasFloodgate = getServer().getPluginManager().getPlugin("floodgate") != null;
        if (hasFloodgate) {
            getLogger().info("Floodgate detected - Bedrock enchanting-table compatibility fix enabled.");
        } else {
            getLogger().info("Floodgate not found - running in Java-only mode (no Bedrock compatibility fixes needed).");
        }
        getLogger().info("SimpleVeinEnchants Loaded!");
    }

    private int getEnchantLevel(ItemStack tool, TypedKey<Enchantment> typedKey) {
        if (tool == null || !tool.hasItemMeta()) return 0;
        Enchantment enchant = Registry.ENCHANTMENT.get(typedKey.key());
        if (enchant == null) return 0;
        return tool.getEnchantmentLevel(enchant);
    }

    private boolean isCustomEnchant(Enchantment enchant) {
        if (enchant == null) return false;
        String key = enchant.getKey().asString();
        return key.equals("xeaf:veinmine") || key.equals("xeaf:lumberjack")
                || key.equals("xeaf:harvest") || key.equals("xeaf:excavator")
                || key.equals("xeaf:antigravity");
    }

    private boolean isPickaxe(ItemStack tool) {
        return tool != null && tool.getType().name().endsWith("_PICKAXE");
    }

    private boolean isAxe(ItemStack tool) {
        return tool != null && tool.getType().name().endsWith("_AXE");
    }

    private boolean isHoe(ItemStack tool) {
        return tool != null && tool.getType().name().endsWith("_HOE");
    }

    private boolean isShovel(ItemStack tool) {
        return tool != null && (tool.getType().name().endsWith("_SHOVEL") || tool.getType().name().endsWith("_SPADE"));
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS || name.endsWith("RAW_COPPER_BLOCK") || name.endsWith("RAW_IRON_BLOCK") || name.endsWith("RAW_GOLD_BLOCK");
    }

    private static final Set<Material> PICKAXE_EXCAVATOR_BLOCKS = buildPickaxeExcavatorBlocks();
    private static final Set<Material> SHOVEL_EXCAVATOR_BLOCKS = buildShovelExcavatorBlocks();
    // Filtered subset of SHOVEL_EXCAVATOR_BLOCKS that are strictly affected by gravity
    // (suspicious sand/gravel are intentionally left out, they are archaeology blocks)
    private static final Set<Material> ANTIGRAVITY_BLOCKS = buildAntigravityBlocks();

    private static boolean isModernName(Material m) {
        return !m.name().startsWith("LEGACY_");
    }

    private static Set<Material> buildPickaxeExcavatorBlocks() {
        Set<Material> set = EnumSet.of(
                Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF,
                Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.CALCITE, Material.DRIPSTONE_BLOCK,
                Material.NETHERRACK, Material.BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE, Material.GILDED_BLACKSTONE,
                Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM, Material.MAGMA_BLOCK, Material.BONE_BLOCK, Material.GLOWSTONE,
                Material.END_STONE, Material.REINFORCED_DEEPSLATE,
                Material.SANDSTONE, Material.RED_SANDSTONE,
                Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE
        );
        for (Material m : Material.values()) {
            if (!isModernName(m)) continue;
            String n = m.name();
            // All infested blocks (silverfish are spawned on break, see below)
            if (n.startsWith("INFESTED_")) set.add(m);
            // Plain + colored terracotta, but not glazed terracotta (crafted, not natural)
            if (n.equals("TERRACOTTA") || (n.endsWith("_TERRACOTTA") && !n.contains("GLAZED"))) set.add(m);
        }
        return set;
    }

    private static Set<Material> buildShovelExcavatorBlocks() {
        Set<Material> set = EnumSet.of(
                Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT, Material.ROOTED_DIRT,
                Material.DIRT_PATH, Material.FARMLAND,
                Material.MYCELIUM, Material.MUD, Material.MUDDY_MANGROVE_ROOTS,
                Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
                Material.SNOW, Material.SNOW_BLOCK, Material.SOUL_SAND, Material.SOUL_SOIL
        );
        set.addAll(buildAntigravityBlocks());
        return set;
    }

    private static Set<Material> buildAntigravityBlocks() {
        Set<Material> set = EnumSet.of(Material.SAND, Material.RED_SAND, Material.GRAVEL);
        for (Material m : Material.values()) {
            if (isModernName(m) && m.name().endsWith("_CONCRETE_POWDER")) set.add(m);
        }
        return set;
    }

    private boolean isExcavatorBlock(ItemStack tool, Material material) {
        if (isPickaxe(tool)) {
            return PICKAXE_EXCAVATOR_BLOCKS.contains(material);
        } else if (isShovel(tool)) {
            return SHOVEL_EXCAVATOR_BLOCKS.contains(material);
        }
        return false;
    }

    private boolean isAntigravityBlock(Material material) {
        return ANTIGRAVITY_BLOCKS.contains(material);
    }

    private String getBaseOreName(Material material) {
        return material.name().replace("DEEPSLATE_", "").replace("STONE_", "").replace("NETHER_", "");
    }

    // Everything Lumberjack can chop: overworld logs/wood (incl. stripped, cherry, pale oak, mangrove),
    // nether stems/hyphae (crimson, warped), bamboo blocks, mangrove roots, shroomlight,
    // giant mushroom blocks and chorus plants.
    // Note: explicit check for STEM so melon/pumpkin/mushroom stems are not matched.
    private boolean isTreeMaterial(Material material) {
        String name = material.name();
        if (name.endsWith("_LOG") || name.endsWith("_WOOD")) return true;
        if (name.endsWith("_HYPHAE")) return true;
        if (name.endsWith("_STEM") && (name.contains("CRIMSON") || name.contains("WARPED"))) return true;
        if (name.endsWith("BAMBOO_BLOCK")) return true;
        return isUnorientedTreeMaterial(material);
    }

    // Tree-like blocks that have no log axis (so the "vertical" check does not apply to them)
    private boolean isUnorientedTreeMaterial(Material material) {
        return material == Material.MANGROVE_ROOTS || material == Material.SHROOMLIGHT
                || material == Material.RED_MUSHROOM_BLOCK || material == Material.BROWN_MUSHROOM_BLOCK
                || material == Material.MUSHROOM_STEM || material == Material.CHORUS_PLANT;
    }

    private boolean isVerticalLog(Block block) {
        Material type = block.getType();
        if (!isTreeMaterial(type)) return false;
        // Roots, shroomlight, giant mushrooms and chorus plants have no orientation, so they always count
        if (isUnorientedTreeMaterial(type)) return true;
        return block.getBlockData() instanceof Orientable orientable && orientable.getAxis() == Axis.Y;
    }

    // Ageable blocks that are not crops and would misbehave with the "reset age = replant" logic:
    // fire and frosted ice would be kept alive instead of removed, and cave vines / weeping / twisting
    // vines would keep dropping items (glow berries, fortune-boosted vines) without ever being consumed.
    private static final Set<Material> HARVEST_BLACKLIST = EnumSet.of(
            Material.FIRE, Material.FROSTED_ICE, Material.CAVE_VINES, Material.WEEPING_VINES, Material.TWISTING_VINES
    );

    // Blocks Harvest simply clears (no replanting): leaves, nether wart blocks, melons and pumpkins
    private boolean isHarvestClearBlock(Material material) {
        return material.name().endsWith("_LEAVES") || material == Material.NETHER_WART_BLOCK
                || material == Material.WARPED_WART_BLOCK || isFlatHarvestBlock(material);
    }

    // Melons and pumpkins grow flat on the ground, so only spread horizontally (like crops)
    private boolean isFlatHarvestBlock(Material material) {
        return material == Material.MELON || material == Material.PUMPKIN;
    }

    private boolean isMatureCrop(Block block) {
        if (HARVEST_BLACKLIST.contains(block.getType())) return false;
        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        return false;
    }

    private Material getSeedMaterial(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            case PITCHER_CROP -> Material.PITCHER_POD;
            case TORCHFLOWER_CROP -> Material.TORCHFLOWER_SEEDS;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> crop;
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (internalBreaks.contains(player.getUniqueId())) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block startBlock = event.getBlock();

        int vLvl = getEnchantLevel(tool, VEINMINE_KEY);
        int lLvl = getEnchantLevel(tool, LUMBERJACK_KEY);
        int hLvl = getEnchantLevel(tool, HARVEST_KEY);
        int eLvl = getEnchantLevel(tool, EXCAVATOR_KEY);
        int aLvl = getEnchantLevel(tool, ANTIGRAVITY_KEY);

        String mode = "single";
        int maxBlocks = 0;
        Material targetMat = startBlock.getType();

        // Check tool restrictions & sneak status before setting mode
        if (vLvl > 0 && isPickaxe(tool) && isOre(targetMat)) {
            if (player.isSneaking()) return; // Ignore veinmine, break normally
            mode = "veinmine"; maxBlocks = vLvl * 64;
        } else if (lLvl > 0 && isAxe(tool) && isVerticalLog(startBlock)) {
            if (player.isSneaking()) return; // Ignore lumberjack, break normally
            mode = "lumberjack"; maxBlocks = lLvl * 64;
        } else if (hLvl > 0 && isHoe(tool) && isMatureCrop(startBlock)) {
            // Do not return on sneak here! We still want the chain-break effect.
            mode = "harvest"; maxBlocks = hLvl * 64;
        } else if (hLvl > 0 && isHoe(tool) && isHarvestClearBlock(targetMat)) {
            if (player.isSneaking()) return; // Nothing to replant here, so sneaking just breaks normally
            mode = "clear"; maxBlocks = hLvl * 64;
        } else if (eLvl > 0 && isExcavatorBlock(tool, targetMat)) {
            if (player.isSneaking()) return; // Ignore excavator, break normally
            mode = "excavator"; maxBlocks = -1; // Fixed bounds system
        }

        boolean hasAntigrav = (aLvl > 0 && (isShovel(tool) || isPickaxe(tool)) && !player.isSneaking());

        // If no enchantments applied at all, exit out and let vanilla handle the single block.
        if (mode.equals("single") && !hasAntigrav) {
            return;
        }

        Set<Block> toBreak = new HashSet<>();

        if (mode.equals("excavator")) {
            toBreak.add(startBlock);
            Vector dir = player.getLocation().getDirection();
            int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;

            if (eLvl == 1) {
                minY = -1; // 1x2 Vertical
            } else if (eLvl == 2 || eLvl == 3) {
                // 2x2 cross-section mapped to player direction
                if (Math.abs(dir.getY()) > 0.5) { maxX = 1; maxZ = 1; }
                else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) { maxY = -1; maxZ = 1; }
                else { maxX = 1; maxY = -1; }

                if (eLvl == 3) {
                    // 2x2x2: extend depth axis forward 1 block
                    if (Math.abs(dir.getY()) > 0.5) {
                        if (dir.getY() > 0) { maxY = 1; } else { minY = -1; }
                    } else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
                        if (dir.getX() > 0) { maxX = 1; } else { minX = -1; }
                    } else {
                        if (dir.getZ() > 0) { maxZ = 1; } else { minZ = -1; }
                    }
                }
            } else if (eLvl == 4 || eLvl >= 5) {
                // 3x3 cross-section centered on target block
                if (Math.abs(dir.getY()) > 0.5) { minX = -1; maxX = 1; minZ = -1; maxZ = 1; }
                else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) { minY = -1; maxY = 1; minZ = -1; maxZ = 1; }
                else { minX = -1; maxX = 1; minY = -1; maxY = 1; }

                if (eLvl >= 5) {
                    // 3x3x3: extend depth axis forward 2 blocks
                    if (Math.abs(dir.getY()) > 0.5) {
                        if (dir.getY() > 0) { maxY = 2; } else { minY = -2; }
                    } else if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
                        if (dir.getX() > 0) { maxX = 2; } else { minX = -2; }
                    } else {
                        if (dir.getZ() > 0) { maxZ = 2; } else { minZ = -2; }
                    }
                }
            }

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dy = minY; dy <= maxY; dy++) {
                    for (int dz = minZ; dz <= maxZ; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = startBlock.getRelative(dx, dy, dz);

                        if (isExcavatorBlock(tool, neighbor.getType()) && isSafeToProcess(neighbor)) {
                            toBreak.add(neighbor);
                        }
                    }
                }
            }
        } else if (mode.equals("single")) {
            toBreak.add(startBlock); // Baseline block to sweep upwards from
        } else {
            // Standard Breadth-First Search for Veinmine, Lumberjack, and Harvest
            Queue<Block> queue = new LinkedList<>();
            Set<Block> visited = new HashSet<>();

            queue.add(startBlock);
            visited.add(startBlock);

            // Crops, melons and pumpkins spread horizontally only; everything else spreads in 3D
            int dyRange = (mode.equals("harvest") || (mode.equals("clear") && isFlatHarvestBlock(targetMat))) ? 0 : 1;

            while (!queue.isEmpty() && toBreak.size() < maxBlocks) {
                Block current = queue.poll();
                toBreak.add(current);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -dyRange; dy <= dyRange; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            Block neighbor = current.getRelative(dx, dy, dz);

                            if (!visited.add(neighbor)) continue;
                            if (!isSafeToProcess(neighbor)) continue;

                            boolean isValid = switch (mode) {
                                case "veinmine" -> getBaseOreName(neighbor.getType()).equals(getBaseOreName(targetMat)) && isOre(neighbor.getType());
                                case "lumberjack" -> neighbor.getType() == targetMat && isVerticalLog(neighbor);
                                case "harvest" -> neighbor.getType() == targetMat && isMatureCrop(neighbor);
                                case "clear" -> neighbor.getType() == targetMat;
                                default -> false;
                            };

                            if (isValid) queue.add(neighbor);
                        }
                    }
                }
            }
        }

        // Secondary Phase: Sweep upwards and collect attached gravity blocks
        int addedGravity = 0;
        if (hasAntigrav) {
            int maxGravity = aLvl * 5;
            Set<Block> antigravBlocks = new HashSet<>();

            for (Block b : new ArrayList<>(toBreak)) {
                if (addedGravity >= maxGravity) break;
                Block above = b.getRelative(0, 1, 0);

                while (isAntigravityBlock(above.getType()) && isSafeToProcess(above)) {
                    if (addedGravity >= maxGravity) break;
                    if (antigravBlocks.add(above)) {
                        addedGravity++;
                    }
                    above = above.getRelative(0, 1, 0);
                }
            }
            toBreak.addAll(antigravBlocks);
        }

        // If ONLY antigravity was a valid enchantment, but there were no blocks above to grab, exit out.
        if (mode.equals("single") && addedGravity == 0) {
            return;
        }

        // Harvest must cancel the vanilla break, because the crop is replanted instead of removed.
        // Every other mode lets vanilla break the clicked block itself, so its loot, XP, Silk Touch,
        // BlockDropItemEvent and tool damage behave exactly like a normal break. We only handle the extras.
        boolean vanillaBreaksStart = !mode.equals("harvest");
        if (vanillaBreaksStart) {
            toBreak.remove(startBlock);
            if (toBreak.isEmpty()) return;
        } else {
            event.setCancelled(true);
        }

        Enchantment silkTouch = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("silk_touch"));
        boolean hasSilkTouch = silkTouch != null && tool.getEnchantmentLevel(silkTouch) > 0;
        int baseExp = mode.equals("veinmine") ? event.getExpToDrop() : 0;

        int broken = 0;
        internalBreaks.add(player.getUniqueId());
        try {
            for (Block block : toBreak) {
                boolean isStart = block.equals(startBlock);
                Material brokenType = block.getType();
                BlockState state = block.getState();
                int exp = isStart ? event.getExpToDrop() : baseExp;
                boolean dropItems = true;

                // Extra blocks go through a real BlockBreakEvent so protection plugins can veto them
                // and other plugins (e.g. Kite scripts) can adjust drops. The clicked block was already
                // covered by the original event.
                if (!isStart) {
                    BlockBreakEvent sub = new BlockBreakEvent(block, player);
                    sub.setExpToDrop(baseExp);
                    getServer().getPluginManager().callEvent(sub);
                    if (sub.isCancelled()) continue;
                    exp = sub.getExpToDrop();
                    dropItems = sub.isDropItems();
                }

                List<ItemStack> drops = dropItems ? new ArrayList<>(blockDrops(block, tool, player, hasSilkTouch)) : new ArrayList<>();
                broken++;

                if (mode.equals("harvest") && !player.isSneaking()) {
                    Ageable data = (Ageable) block.getBlockData();
                    data.setAge(0);
                    block.setBlockData(data);

                    Material requiredSeed = getSeedMaterial(targetMat);
                    boolean seedRemoved = false;
                    for (ItemStack drop : drops) {
                        if (!seedRemoved && drop.getType() == requiredSeed) {
                            drop.setAmount(drop.getAmount() - 1);
                            seedRemoved = true;
                        }
                    }
                    dropBlockItems(player, block, state, drops);
                } else {
                    // Keep vanilla break behavior: regular ice leaves water (not in the nether, not with silk touch)
                    Material replacement = Material.AIR;
                    if (brokenType == Material.ICE && !hasSilkTouch
                            && block.getWorld().getEnvironment() != World.Environment.NETHER) {
                        Block below = block.getRelative(0, -1, 0);
                        if (below.getType().isSolid() || below.isLiquid()) replacement = Material.WATER;
                    }
                    block.setType(replacement);

                    // Fires BlockDropItemEvent so that listeners (e.g. auto-smelt) see these drops too.
                    dropBlockItems(player, block, state, drops);

                    if (exp > 0 && dropItems) {
                        ExperienceOrb orb = block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ExperienceOrb.class);
                        orb.setExperience(exp);
                    }

                    // Keep vanilla break behavior: infested blocks release a silverfish unless mined with silk touch
                    if (!hasSilkTouch && brokenType.name().startsWith("INFESTED_")) {
                        block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), Silverfish.class);
                    }
                }
            }
        } finally {
            internalBreaks.remove(player.getUniqueId());
        }

        if (player.getGameMode() != GameMode.CREATIVE && broken > 0) {
            ItemMeta meta = tool.getItemMeta();
            if (meta instanceof Damageable damageable) {
                Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
                int unbreakingLevel = unbreaking != null ? tool.getEnchantmentLevel(unbreaking) : 0;

                // Vanilla already damages the tool once for the clicked block when it breaks it itself,
                // so we only charge for the blocks we broke.
                int damageToApply = 0;
                for (int i = 0; i < broken; i++) {
                    if (unbreakingLevel <= 0 || RANDOM.nextInt(unbreakingLevel + 1) == 0) {
                        damageToApply++;
                    }
                }

                if (damageToApply > 0) {
                    damageable.setDamage(damageable.getDamage() + damageToApply);
                    tool.setItemMeta(meta);
                    if (damageable.getDamage() > tool.getType().getMaxDurability()) {
                        tool.setAmount(0);
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                    }
                }
            }
        }
    }

    /**
     * Loot for a block we break ourselves. Reinforced deepslate has no loot table, so
     * Block#getDrops returns nothing even with Silk Touch; handle it explicitly.
     */
    private Collection<ItemStack> blockDrops(Block block, ItemStack tool, Player player, boolean silkTouch) {
        if (block.getType() == Material.REINFORCED_DEEPSLATE) {
            return silkTouch ? List.of(new ItemStack(Material.REINFORCED_DEEPSLATE)) : List.of();
        }
        return block.getDrops(tool, player);
    }

    /**
     * Spawns block drops the same way vanilla does: as not-yet-spawned Item entities that are passed
     * through BlockDropItemEvent first, so listeners can modify (smelt, etc.) or cancel them.
     */
    private void dropBlockItems(Player player, Block block, BlockState state, List<ItemStack> drops) {
        World world = block.getWorld();
        List<Item> items = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
            Location loc = block.getLocation().add(
                    0.25 + RANDOM.nextDouble() * 0.5, 0.25 + RANDOM.nextDouble() * 0.5, 0.25 + RANDOM.nextDouble() * 0.5);
            Item item = world.createEntity(loc, Item.class);
            item.setItemStack(drop);
            item.setPickupDelay(10);
            items.add(item);
        }
        if (items.isEmpty()) return;

        BlockDropItemEvent dropEvent = new BlockDropItemEvent(block, state, player, items);
        getServer().getPluginManager().callEvent(dropEvent);
        if (dropEvent.isCancelled()) return;
        for (Item item : dropEvent.getItems()) {
            world.addEntity(item);
        }
    }

    /**
     * Lets librarian villagers occasionally offer one of our custom enchantment books.
     */
    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.getProfession() != Villager.Profession.LIBRARIAN) return;

        MerchantRecipe original = event.getRecipe();
        if (original.getResult().getType() != Material.ENCHANTED_BOOK) return;

        if (RANDOM.nextInt(6) != 0) return;

        TypedKey<Enchantment> chosenKey = CUSTOM_ENCHANT_KEYS.get(RANDOM.nextInt(CUSTOM_ENCHANT_KEYS.size()));
        Enchantment enchant = Registry.ENCHANTMENT.get(chosenKey.key());
        if (enchant == null) return;

        int level = 1 + RANDOM.nextInt(enchant.getMaxLevel());

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) book.getItemMeta();
        bookMeta.addStoredEnchant(enchant, level, true);
        book.setItemMeta(bookMeta);

        int emeraldPrice = Math.min(64, 8 + level * 10);

        MerchantRecipe custom = new MerchantRecipe(
                book,
                0,
                original.getMaxUses(),
                true,
                5 + level * 4,
                original.getPriceMultiplier(),
                0,
                0,
                false
        );
        custom.addIngredient(new ItemStack(Material.EMERALD, emeraldPrice));
        custom.addIngredient(new ItemStack(Material.BOOK, 1));

        event.setRecipe(custom);
    }

    /**
     * Bedrock/Geyser compatibility fix for the enchanting table.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!hasFloodgate) return;
        Player player = event.getEnchanter();
        if (!BedrockCompat.isBedrockPlayer(player)) return;

        boolean hasCustom = event.getEnchantsToAdd().keySet().stream().anyMatch(this::isCustomEnchant);
        if (!hasCustom) return;

        event.setCancelled(true);

        ItemStack enchanted = event.getItem().clone();
        ItemMeta enchMeta = enchanted.getItemMeta();
        for (Map.Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
            if (enchMeta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            } else {
                enchMeta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        enchanted.setItemMeta(enchMeta);

        ItemStack lapis = event.getInventory().getItem(1);
        int lapisCost = event.whichButton() + 1;
        if (lapis != null) {
            lapis.setAmount(Math.max(0, lapis.getAmount() - lapisCost));
            event.getInventory().setItem(1, lapis.getAmount() <= 0 ? null : lapis);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setLevel(Math.max(0, player.getLevel() - event.getExpLevelCost()));
        }

        event.getInventory().setItem(0, null);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(enchanted);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        player.updateInventory();
    }
}
