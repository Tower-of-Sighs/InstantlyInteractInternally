package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VirtualWorldStorage {

    private VirtualWorldStorage() {
    }

    record StoredEntry(String key, String blockId, CompoundTag beTag) {
    }

    record StoredContext(List<StoredEntry> entries, int nextIndex) {
    }

    static void save(MinecraftServer server, UUID playerId, VirtualWorldManager.Context ctx) {
        try {
            Path file = resolveFile(server, playerId);
            Files.createDirectories(file.getParent());

            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();

            for (Map.Entry<String, BlockPos> e : ctx.keyToPos.entrySet()) {
                String key = e.getKey();
                var pos = e.getValue();
                var state = ctx.level.getBlockState(pos);
                var block = state.getBlock();
                var id = ForgeRegistries.BLOCKS.getKey(block);
                if (id == null) continue;

                CompoundTag beTag = null;
                var be = ctx.level.getBlockEntity(pos);
                if (be != null) {
                    beTag = be.saveWithFullMetadata();
                }

                CompoundTag item = new CompoundTag();
                item.putString("key", key);
                item.putString("block", id.toString());
                if (beTag != null) {
                    item.put("be", beTag);
                }
                list.add(item);
            }
            root.put("entries", list);
            root.putInt("nextIndex", ctx.nextIndex);

            NbtIo.writeCompressed(root, file.toFile());
        } catch (Throwable t) {
            Instantlyinteractinternally.LOGGER.warn("Failed to save FakeLevel data for player: {}", playerId, t);
        }
    }

    static StoredContext load(MinecraftServer server, UUID playerId) {
        try {
            Path file = resolveFile(server, playerId);
            if (!Files.exists(file)) return null;

            CompoundTag root = NbtIo.readCompressed(file.toFile());
            ListTag list = root.getList("entries", Tag.TAG_COMPOUND);
            int nextIndex = root.contains("nextIndex", Tag.TAG_INT) ? root.getInt("nextIndex") : list.size();

            List<StoredEntry> entries = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag item = list.getCompound(i);
                String key = item.getString("key");
                String blockId = item.getString("block");
                CompoundTag beTag = item.contains("be", Tag.TAG_COMPOUND) ? item.getCompound("be") : null;

                if (key == null || key.isEmpty() || blockId == null || blockId.isEmpty()) continue;
                entries.add(new StoredEntry(key, blockId, beTag));
            }
            return new StoredContext(entries, nextIndex);
        } catch (Throwable t) {
            Instantlyinteractinternally.LOGGER.warn("Failed to load FakeLevel data for player: {}", playerId, t);
            return null;
        }
    }

    private static Path resolveFile(MinecraftServer server, UUID playerId) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path dir = root.resolve("data").resolve(Instantlyinteractinternally.MODID).resolve("fakelevel");
        return dir.resolve(playerId.toString() + ".nbt");
    }
}