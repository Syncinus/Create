package com.simibubi.create.content.contraptions.components.structureMovement;

import com.simibubi.create.content.contraptions.components.structureMovement.sync.ContraptionFluidPacket;
import com.simibubi.create.content.contraptions.fluids.tank.CreativeFluidTankTileEntity;
import com.simibubi.create.content.contraptions.fluids.tank.CreativeFluidTankTileEntity.CreativeSmartFluidTank;
import com.simibubi.create.content.contraptions.fluids.tank.FluidTankTileEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.gui.widgets.InterpolatedChasingValue;
import com.simibubi.create.foundation.networking.AllPackets;
import com.simibubi.create.foundation.utility.NBTHelper;
import com.simibubi.create.lib.lba.fluid.FluidVolume;
import com.simibubi.create.lib.lba.fluid.FixedFluidInv;
import com.simibubi.create.lib.lba.fluid.SimpleFluidTank;
import com.simibubi.create.lib.utility.LazyOptional;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class MountedFluidStorage {

	SmartFluidTank tank;
	private boolean valid;
	private TileEntity te;

	private int packetCooldown = 0;
	private boolean sendPacket = false;

	public static boolean canUseAsStorage(TileEntity te) {
		if (te instanceof FluidTankTileEntity)
			return ((FluidTankTileEntity) te).isController();
		return false;
	}

	public MountedFluidStorage(TileEntity te) {
		assignTileEntity(te);
	}

	public void assignTileEntity(TileEntity te) {
		this.te = te;
		tank = createMountedTank(te);
	}

	private SmartFluidTank createMountedTank(TileEntity te) {
		if (te instanceof CreativeFluidTankTileEntity)
			return new CreativeSmartFluidTank(
				((FluidTankTileEntity) te).getTotalTankSize() * FluidTankTileEntity.getCapacityMultiplier(), $ -> {
				});
		if (te instanceof FluidTankTileEntity)
			return new SmartFluidTank(
				((FluidTankTileEntity) te).getTotalTankSize() * FluidTankTileEntity.getCapacityMultiplier(),
				this::onFluidVolumeChanged);
		return null;
	}

	public void tick(Entity entity, BlockPos pos, boolean isRemote) {
		if (!isRemote) {
			if (packetCooldown > 0)
				packetCooldown--;
			else if (sendPacket) {
				sendPacket = false;
				AllPackets.channel.sendToClientsTracking(
					new ContraptionFluidPacket(entity.getEntityId(), pos, tank.getFluid()), entity);
				packetCooldown = 8;
			}
			return;
		}

		if (!(te instanceof FluidTankTileEntity))
			return;
		FluidTankTileEntity tank = (FluidTankTileEntity) te;
		tank.getFluidLevel()
			.tick();
	}

	public void updateFluid(FluidVolume fluid) {
		tank.setFluid(fluid);
		if (!(te instanceof FluidTankTileEntity))
			return;
		float fillState = tank.getFluidAmount() / (float) tank.getCapacity();
		FluidTankTileEntity tank = (FluidTankTileEntity) te;
		if (tank.getFluidLevel() == null)
			tank.setFluidLevel(new InterpolatedChasingValue().start(fillState));
		tank.getFluidLevel()
			.target(fillState);
		SimpleFluidTank tankInventory = tank.getTankInventory();
		if (tankInventory instanceof SmartFluidTank)
			((SmartFluidTank) tankInventory).setFluid(fluid);
	}

	public void removeStorageFromWorld() {
		valid = false;
		if (te == null)
			return;

		FixedFluidInv teHandler = TransferUtil.getFluidHandler(te)
			.orElse(null);
		if (!(teHandler instanceof SmartFluidTank))
			return;
		SmartFluidTank smartTank = (SmartFluidTank) teHandler;
		tank.setFluid(smartTank.getFluid());
		sendPacket = false;
		valid = true;
	}

	private void onFluidVolumeChanged(FluidVolume fs) {
		sendPacket = true;
	}

	public void addStorageToWorld(TileEntity te) {
		if (tank instanceof CreativeSmartFluidTank)
			return;

		LazyOptional<FixedFluidInv> capability = TransferUtil.getFluidHandler(te);
		FixedFluidInv teHandler = capability.orElse(null);
		if (!(teHandler instanceof SmartFluidTank))
			return;

		SmartFluidTank inv = (SmartFluidTank) teHandler;
		inv.setFluid((FluidVolume) tank.getFluid().copy());
	}

	public FixedFluidInv getFluidHandler() {
		return (FixedFluidInv) tank;
	}

	public CompoundNBT serialize() {
		if (!valid)
			return null;
		CompoundNBT tag = tank.writeToNBT(new CompoundNBT());
		tag.putInt("Capacity", tank.getCapacity());

		if (tank instanceof CreativeSmartFluidTank) {
			NBTHelper.putMarker(tag, "Bottomless");
			tag.put("ProvidedStack", tank.getFluid()
				.writeToNBT(new CompoundNBT()));
		}
		return tag;
	}

	public static MountedFluidStorage deserialize(CompoundNBT nbt) {
		MountedFluidStorage storage = new MountedFluidStorage(null);
		if (nbt == null)
			return storage;

		int capacity = nbt.getInt("Capacity");
		storage.tank = new SmartFluidTank(capacity, storage::onFluidVolumeChanged);
		storage.valid = true;

		if (nbt.contains("Bottomless")) {
			FluidVolume providedStack = FluidVolume.loadFluidVolumeFromNBT(nbt.getCompound("ProvidedStack"));
			CreativeSmartFluidTank creativeSmartFluidTank = new CreativeSmartFluidTank(capacity, $ -> {
			});
			creativeSmartFluidTank.setContainedFluid(providedStack);
			storage.tank = creativeSmartFluidTank;
			return storage;
		}

		storage.tank.readFromNBT(nbt);
		return storage;
	}

	public boolean isValid() {
		return valid;
	}

}
