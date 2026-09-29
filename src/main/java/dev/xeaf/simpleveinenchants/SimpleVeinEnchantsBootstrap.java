package dev.xeaf.simpleveinenchants;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry.EnchantmentCost;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.EquipmentSlotGroup;

public class SimpleVeinEnchantsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {

        context.getLogger().info(">>> BOOTSTRAPPER IS RUNNING! INJECTING ENCHANTMENTS! <<<");

        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {

            // Grab the vanilla tags for the single-tool enchantments
            var pickaxesTag = event.getOrCreateTag(ItemTypeTagKeys.PICKAXES);
            var axesTag = event.getOrCreateTag(ItemTypeTagKeys.AXES);
            var hoesTag = event.getOrCreateTag(ItemTypeTagKeys.HOES);
            var shovelsTag = event.getOrCreateTag(ItemTypeTagKeys.SHOVELS);

            // Construct a custom RegistryKeySet combining all Pickaxes and Shovels for Excavator
            var excavatorSet = RegistrySet.keySet(RegistryKey.ITEM,
                    ItemTypeKeys.WOODEN_PICKAXE, ItemTypeKeys.STONE_PICKAXE, ItemTypeKeys.IRON_PICKAXE, ItemTypeKeys.GOLDEN_PICKAXE, ItemTypeKeys.DIAMOND_PICKAXE, ItemTypeKeys.NETHERITE_PICKAXE,
                    ItemTypeKeys.WOODEN_SHOVEL, ItemTypeKeys.STONE_SHOVEL, ItemTypeKeys.IRON_SHOVEL, ItemTypeKeys.GOLDEN_SHOVEL, ItemTypeKeys.DIAMOND_SHOVEL, ItemTypeKeys.NETHERITE_SHOVEL
            );

            event.registry().register(
                SimpleVeinEnchants.VEINMINE_KEY,
                b -> b.description(Component.text("Veinmine").color(NamedTextColor.GRAY))
                      .supportedItems(pickaxesTag)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );

            event.registry().register(
                SimpleVeinEnchants.LUMBERJACK_KEY,
                b -> b.description(Component.text("Lumberjack").color(NamedTextColor.GRAY))
                      .supportedItems(axesTag)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );

            event.registry().register(
                SimpleVeinEnchants.HARVEST_KEY,
                b -> b.description(Component.text("Harvest").color(NamedTextColor.GRAY))
                      .supportedItems(hoesTag)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );

            event.registry().register(
                SimpleVeinEnchants.EXCAVATOR_KEY,
                b -> b.description(Component.text("Excavator").color(NamedTextColor.GRAY))
                      .supportedItems(excavatorSet)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );

            event.registry().register(
                SimpleVeinEnchants.ANTIGRAVITY_KEY,
                b -> b.description(Component.text("Antigravity").color(NamedTextColor.GRAY))
                      .supportedItems(excavatorSet)
                      .weight(2)
                      .maxLevel(5)
                      .minimumCost(EnchantmentCost.of(15, 9))
                      .maximumCost(EnchantmentCost.of(65, 9))
                      .anvilCost(4)
                      .activeSlots(EquipmentSlotGroup.MAINHAND)
            );
        }));
    }
}
