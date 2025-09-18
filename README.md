## Instantly Interact Internally

This mod allows most non-decorative items and blocks to be interacted with directly from inventories and other GUIs, without needing to place or hold them.

For example, you can interact with a Shulker Box item in your inventory to instantly open its container GUI, then interact with a splash healing potion inside that GUI to throw it immediately.

### Main Features

The mod provides a hotkey (default: **SHIFT + Right Click**) for quick interactions, enabling instant use of items in any GUI.

To illustrate, here are representative use cases:

**Blocks:**

* For interactive blocks such as Note Blocks and Bells, their item forms can be interacted with directly in the inventory to play sounds, without placing them in the world.
* For GUI blocks such as Crafting Tables, Smithing Tables, Anvils, and Enchanting Tables, quick interaction opens their respective GUIs.
* For container blocks such as Barrels, Dispensers, Furnaces, and Shulker Boxes, quick interaction opens their container GUI and allows normal usage. For example, you can open a Barrel in your inventory and use it as extra storage. Items stored remain when the block is placed, but are lost when broken (except for Shulker Boxes).
* For processing blocks such as Furnaces, Blast Furnaces, and Brewing Stands, quick interaction allows normal processing. For example, inserting materials into a Furnace from your inventory continues smelting even after the GUI is closed.
* These features are not hardcoded to specific blocks — other mods’ blocks with standard implementations will also work.
* Container items with unique states (e.g., Barrels storing different contents) cannot stack due to NBT data, unless emptied.

**Items:**

* For functional right-click items such as Ender Eyes, Snowballs, Goat Horns, and Fishing Rods, quick interaction in a GUI uses them without needing to equip them.
* Food and potions can also be consumed directly from a GUI. (Note: consumption delays are not yet synced to quick interactions.)

### Other Notes

* Some modded blocks or items use unique implementations. This mod will only add compatibility where necessary.
* An API is provided for other mods or modpacks to extend or customize functionality.

### Planned Features

* Syncing food and potion consumption delays with quick interaction.
* Blacklist and whitelist support.
