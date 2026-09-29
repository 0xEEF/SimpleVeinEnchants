# SimpleVeinEnchants
Very simple veinmining enchants for ores (Veinmine), trees (Lumberjack) and crops (Harvest) + a bonus Excavator enchant (3x3 mining)

## WARNING: Made with AI (cuz idk how to use Java)

I am a random dev, and you should really not trust random stuff you find on the Internet. Take a look at the source code which is only a few hundred lines long. (click on the GitHub "view source" link on the side). And if you prefer, compile the project yourself with `mvn clean package` (requires Java JDK 21+ and Maven installed).

## Docs

### Compatibility

Doesn't require any other plugin.
However if you use Geyser, Bedrock players won't be able to combine any item (tools or books) with the anvil, as Minecraft Bedrock has a client-side check for valid anvil combinations that cannot be altered.
Thus, you may install a compatibility plugin such as [Geyser Recipe Fix](https://modrinth.com/plugin/geyser-recipe-fix). Note that since that plugin is no longer updated, you may find my patches useful.
(see https://github.com/0xEAF/GeyserRecipeFixPatches - read the whole README)

### Enchants explanation

For all the enchants, you can sneak to disable them (except for harvest: sneaking with a harvest hoe will break all crops as expected but not replant them. Use your fist instead if you want to break block by block).

#### Veinmine (pickaxe)

Can mine up to 64 neighboring ores per level of enchant. Maxes out at level 5 (V) at 320 ores.

#### Lumberjack (axe)

Can mine up to 64 neighboring logs (including nether stems/hyphae, bamboo blocks, mangrove roots, shroomlight, giant mushrooms and chorus plants) per level of enchant. Maxes out at level 5 (V) at 320 logs.

#### Harvest (hoe)

Can mine up to 64 neighboring crops per level of enchant, and re-places them. Maxes out at level 5 (V) at 320 crops.
Also clears leaves, nether wart blocks, melons and pumpkins (no replanting needed; sneak to break them normally). Fire, frosted ice and cave/weeping/twisting vines are ignored.

#### Excavator (pickaxe and shovel)

Can mine up tunnels efficiently.
- Level I: breaks a 1x2 tunnel (a regular strip mine tunnel)
- Level II: breaks a 2x2 zone (in 2D)
- Level III: breaks a 2x2x2 zone (in 3D)
- Level IV: breaks a 3x3 zone (in 2D)
- Level V: breaks a 3x3x3 zone (in 3D) (regular excavator)

Vanilla break behavior is kept: ice leaves water (unless silk touch, or in the nether) and infested blocks release silverfish (unless silk touch). Obsidian is intentionally not included.

#### Antigravity (shovel)

Can mine all the gravity affected blocks that are going to fall after breaking the selected block. Replaces the "break block then put torch" loop to break falling blocks like gravel.
Can break up to 5 gravity-affected blocks that are breakable with a shovel (ie. gravel, sand but not anvil) per level of enchant, maxing at level 5 (V) at 25 falling blocks.
