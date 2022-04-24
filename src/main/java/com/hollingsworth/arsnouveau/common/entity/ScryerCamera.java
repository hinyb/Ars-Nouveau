package com.hollingsworth.arsnouveau.common.entity;


import com.hollingsworth.arsnouveau.ArsNouveau;
import net.geforcemods.securitycraft.SCContent;
import net.geforcemods.securitycraft.SecurityCraft;
import net.geforcemods.securitycraft.api.IModuleInventory;
import net.geforcemods.securitycraft.blockentities.SecurityCameraBlockEntity;
import net.geforcemods.securitycraft.blocks.SecurityCameraBlock;
import net.geforcemods.securitycraft.misc.ModuleType;
import net.geforcemods.securitycraft.network.client.SetCameraView;
import net.geforcemods.securitycraft.network.server.GiveNightVision;
import net.geforcemods.securitycraft.network.server.SetCameraPowered;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.network.PacketDistributor;

/**
 * Camera work is taken from SecurityCraft:
 * https://github.com/Geforce132/SecurityCraft/blob/1.18.2/src/main/java/net/geforcemods/securitycraft/entity/camera/SecurityCamera.java
 */
public class ScryerCamera extends Entity {
    protected final double cameraSpeed;
    public int screenshotSoundCooldown;
    protected int redstoneCooldown;
    protected int toggleNightVisionCooldown;
    private boolean shouldProvideNightVision;
    protected float zoomAmount;
    protected boolean zooming;
    private int viewDistance;
    private boolean loadedChunks;

    public ScryerCamera(EntityType<ScryerCamera> type, Level level) {
        super(type, level);
        this.cameraSpeed = 2.0D;
        this.screenshotSoundCooldown = 0;
        this.redstoneCooldown = 0;
        this.toggleNightVisionCooldown = 0;
        this.shouldProvideNightVision = false;
        this.zoomAmount = 1.0F;
        this.zooming = false;
        this.viewDistance = -1;
        this.loadedChunks = false;
        this.noPhysics = true;
    }

    public ScryerCamera(Level level, BlockPos pos) {
        this(ModEntities.SCRYER_CAMERA, level);
        BlockEntity var4 = level.getBlockEntity(pos);
        if (var4 instanceof SecurityCameraBlockEntity) {
            SecurityCameraBlockEntity cam = (SecurityCameraBlockEntity)var4;
            double x = (double)pos.getX() + 0.5D;
            double y = (double)pos.getY() + 0.5D;
            double z = (double)pos.getZ() + 0.5D;
            if (cam.down) {
                y += 0.25D;
            }

            this.setPos(x, y, z);
            this.setInitialPitchYaw();
        } else {
            this.discard();
        }
    }

    public ScryerCamera(Level level, BlockPos pos, ScryerCamera oldCamera) {
        this(level, pos);
        oldCamera.discardCamera();
    }

    private void setInitialPitchYaw() {
        this.setXRot(30.0F);
        Direction facing = (Direction)this.level.getBlockState(this.blockPosition()).getValue(SecurityCameraBlock.FACING);
        if (facing == Direction.NORTH) {
            this.setYRot(180.0F);
        } else if (facing == Direction.WEST) {
            this.setYRot(90.0F);
        } else if (facing == Direction.SOUTH) {
            this.setYRot(0.0F);
        } else if (facing == Direction.EAST) {
            this.setYRot(270.0F);
        } else if (facing == Direction.DOWN) {
            this.setXRot(75.0F);
        }

    }

    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    public void tick() {
        if (this.level.isClientSide) {
            if (this.screenshotSoundCooldown > 0) {
                --this.screenshotSoundCooldown;
            }

            if (this.redstoneCooldown > 0) {
                --this.redstoneCooldown;
            }

            if (this.toggleNightVisionCooldown > 0) {
                --this.toggleNightVisionCooldown;
            }

            if (this.shouldProvideNightVision) {
                SecurityCraft.channel.sendToServer(new GiveNightVision());
            }
        } else if (this.level.getBlockState(this.blockPosition()).getBlock() != SCContent.SECURITY_CAMERA.get()) {
            this.discard();
        }

    }

    public void toggleRedstonePower() {
        BlockPos pos = this.blockPosition();
        if (((IModuleInventory)this.level.getBlockEntity(pos)).hasModule(ModuleType.REDSTONE)) {
            SecurityCraft.channel.sendToServer(new SetCameraPowered(pos, !(Boolean)this.level.getBlockState(pos).getValue(SecurityCameraBlock.POWERED)));
        }

    }

    public void toggleNightVision() {
        this.toggleNightVisionCooldown = 30;
        this.shouldProvideNightVision = !this.shouldProvideNightVision;
    }

    public float getZoomAmount() {
        return this.zoomAmount;
    }

    public boolean isCameraDown() {
        BlockEntity var2 = this.level.getBlockEntity(this.blockPosition());
        boolean var10000;
        if (var2 instanceof SecurityCameraBlockEntity) {
            SecurityCameraBlockEntity cam = (SecurityCameraBlockEntity)var2;
            if (cam.down) {
                var10000 = true;
                return var10000;
            }
        }

        var10000 = false;
        return var10000;
    }

    public void setRotation(float yaw, float pitch) {
        this.setRot(yaw, pitch);
    }

    public void stopViewing(ServerPlayer player) {
        if (!this.level.isClientSide) {
            this.discardCamera();
            player.camera = player;
            SecurityCraft.channel.send(PacketDistributor.PLAYER.with(() -> {
                return player;
            }), new SetCameraView(player));
        }

    }

    public void discardCamera() {
        if (!this.level.isClientSide) {
            BlockEntity var2 = this.level.getBlockEntity(this.blockPosition());
            if (var2 instanceof SecurityCameraBlockEntity) {
                SecurityCameraBlockEntity camBe = (SecurityCameraBlockEntity)var2;
                camBe.stopViewing();
            }

            SectionPos chunkPos = SectionPos.of(this.blockPosition());
            int viewDistance = this.viewDistance <= 0 ? this.level.getServer().getPlayerList().getViewDistance() : this.viewDistance;

            for(int x = chunkPos.getX() - viewDistance; x <= chunkPos.getX() + viewDistance; ++x) {
                for(int z = chunkPos.getZ() - viewDistance; z <= chunkPos.getZ() + viewDistance; ++z) {
                    ForgeChunkManager.forceChunk((ServerLevel)this.level, ArsNouveau.MODID, this, x, z, false, false);
                }
            }
        }

        this.discard();
    }

    public void setHasLoadedChunks(int initialViewDistance) {
        this.loadedChunks = true;
        this.viewDistance = initialViewDistance;
    }

    public boolean hasLoadedChunks() {
        return this.loadedChunks;
    }

    protected void defineSynchedData() {
    }

    public void addAdditionalSaveData(CompoundTag tag) {
    }

    public void readAdditionalSaveData(CompoundTag tag) {
    }

    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    public boolean isAlwaysTicking() {
        return true;
    }
}
