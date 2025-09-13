package com.mafuyu404.instantlyinteractinternally.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeLevel extends Level {

    private final ServerLevel delegate;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

    public FakeLevel(ServerLevel delegate) {
        super(
                (WritableLevelData) delegate.getLevelData(),
                delegate.dimension(),
                delegate.registryAccess(),
                delegate.dimensionTypeRegistration(),
                delegate.getProfilerSupplier(),
                false, // isClientSide
                delegate.isDebug(),
                delegate.getSeed(),
                delegate.getServer().getMaxChainedNeighborUpdates()
        );
        this.delegate = delegate;
    }

    // 在指定位置放置（仅内存）
    public void putBlock(BlockPos pos, BlockState newState) {
        BlockState oldState = blocks.get(pos);
        if (oldState != null && oldState != newState) {
            oldState.onRemove(this, pos, newState, false);
        }
        blocks.put(pos, newState);

        if (newState.getBlock() instanceof BaseEntityBlock beb) {
            BlockEntity be = blockEntities.get(pos);
            if (be == null || be.getType() == null || !be.getType().isValid(newState)) {
                be = beb.newBlockEntity(pos, newState);
            }
            if (be != null) {
                be.setLevel(this);
                blockEntities.put(pos, be);
            } else {
                blockEntities.remove(pos);
            }
        } else {
            blockEntities.remove(pos);
        }

        if (oldState == null || oldState.getBlock() != newState.getBlock()) {
            newState.onPlace(this, pos, oldState == null ? Blocks.AIR.defaultBlockState() : oldState, false);
        }
    }

    @Override
    public @Nullable MinecraftServer getServer() {
        return delegate.getServer();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int p_204159_, int p_204160_, int p_204161_) {
        return delegate.getUncachedNoiseBiome(p_204159_, p_204160_, p_204161_);
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public float getShade(Direction p_45522_, boolean p_45523_) {
        return 0;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public LevelChunk getChunk(int x, int z) {
        return delegate.getChunk(x, z);
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return delegate.enabledFeatures();
    }

    @Override
    public LevelChunk getChunkAt(BlockPos pos) {
        return delegate.getChunkAt(pos);
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        // 对于虚拟坐标总是视为已加载
        return true;
    }

    @Override
    public @Nullable Entity getEntity(int p_46492_) {
        return delegate.getEntity(p_46492_);
    }

    @Override
    public @Nullable MapItemSavedData getMapData(String p_46650_) {
        return delegate.getMapData(p_46650_);
    }

    @Override
    public void setMapData(String p_151533_, MapItemSavedData p_151534_) {
        delegate.setMapData(p_151533_, p_151534_);
    }

    @Override
    public int getFreeMapId() {
        return delegate.getFreeMapId();
    }

    @Override
    public void destroyBlockProgress(int p_46506_, BlockPos p_46507_, int p_46508_) {
        delegate.destroyBlockProgress(p_46506_, p_46507_, p_46508_);
    }

    @Override
    public Scoreboard getScoreboard() {
        return delegate.getScoreboard();
    }

    @Override
    public RecipeManager getRecipeManager() {
        return delegate.getRecipeManager();
    }

    // TODO 如果要在数据、cap做到完美就得实现一个FakeServerLevel，但风险高难度大，有待委托到此实现
    public DimensionDataStorage getDataStorage() {
        return delegate.getDataStorage();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return delegate.getCapability(cap, side);
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return delegate.getEntities();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = blocks.get(pos);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public void playSeededSound(@Nullable Player p_262953_, double p_263004_, double p_263398_, double p_263376_, Holder<SoundEvent> p_263359_, SoundSource p_263020_, float p_263055_, float p_262914_, long p_262991_) {
        delegate.playSeededSound(p_262953_, p_263004_, p_263398_, p_263376_, p_263359_, p_263020_, p_263055_, p_262914_, p_262991_);
    }

    @Override
    public void playSeededSound(@Nullable Player p_220372_, Entity p_220373_, Holder<SoundEvent> p_263500_, SoundSource p_220375_, float p_220376_, float p_220377_, long p_220378_) {
        delegate.playSeededSound(p_220372_, p_220373_, p_263500_, p_220375_, p_220376_, p_220377_, p_220378_);
    }

    @Override
    public String gatherChunkSourceStats() {
        return delegate.gatherChunkSourceStats();
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        putBlock(pos, state);
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        BlockState oldState = blocks.remove(pos);
        BlockState newState = Blocks.AIR.defaultBlockState();
        blockEntities.remove(pos);
        if (oldState != null) {
            oldState.onRemove(this, pos, newState, isMoving);
        }
        return true;
    }

    public void moveBlockWithBE(BlockPos from, BlockPos to) {
        if (from.equals(to)) return;

        BlockState state = blocks.get(from);
        BlockEntity oldBe = blockEntities.get(from);

        BlockState toOld = blocks.get(to);
        if (toOld != null) {
            removeBlock(to, false);
        }

        if (state == null) {
            removeBlock(from, false);
            return;
        }

        blocks.put(to, state);
        BlockEntity newBe = null;
        if (oldBe != null) {
            CompoundTag tag = oldBe.saveWithFullMetadata();
            newBe = BlockEntity.loadStatic(to, state, tag);
            if (newBe == null && state.getBlock() instanceof BaseEntityBlock beb) {
                newBe = beb.newBlockEntity(to, state);
            }
        } else if (state.getBlock() instanceof BaseEntityBlock beb) {
            newBe = beb.newBlockEntity(to, state);
        }
        if (newBe != null) {
            newBe.setLevel(this);
            blockEntities.put(to, newBe);
        } else {
            blockEntities.remove(to);
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        state.onPlace(this, to, air, false);
        BlockState fromOld = blocks.remove(from);
        blockEntities.remove(from);
        if (fromOld != null) {
            fromOld.onRemove(this, from, air, false);
        }
    }


    @Override
    public void sendBlockUpdated(BlockPos p_46612_, BlockState p_46613_, BlockState p_46614_, int p_46615_) {
        delegate.sendBlockUpdated(p_46612_, p_46613_, p_46614_, p_46615_);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        blockEntities.put(blockEntity.getBlockPos(), blockEntity);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        blockEntities.remove(pos);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        return false;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return delegate.getBlockTicks();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return delegate.getFluidTicks();
    }

    @Override
    public ChunkSource getChunkSource() {
        return delegate.getChunkSource();
    }

    @Override
    public void levelEvent(@Nullable Player p_46771_, int p_46772_, BlockPos p_46773_, int p_46774_) {
        delegate.levelEvent(p_46771_, p_46772_, p_46773_, p_46774_);
    }

    @Override
    public void gameEvent(GameEvent p_220404_, Vec3 p_220405_, GameEvent.Context p_220406_) {
        delegate.gameEvent(p_220404_, p_220405_, p_220406_);
    }

    @Override
    public List<? extends Player> players() {
        return delegate.players();
    }
}