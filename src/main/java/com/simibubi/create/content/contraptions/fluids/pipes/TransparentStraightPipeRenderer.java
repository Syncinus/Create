package com.simibubi.create.content.contraptions.fluids.pipes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.contraptions.fluids.PipeConnection.Flow;
import com.simibubi.create.foundation.fluid.FluidRenderer;
import com.simibubi.create.foundation.tileEntity.TileEntityBehaviour;
import com.simibubi.create.foundation.tileEntity.renderer.SafeTileEntityRenderer;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.animation.LerpedFloat;
import io.github.fabricators_of_create.porting_lib.transfer.fluid.FluidStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class TransparentStraightPipeRenderer extends SafeTileEntityRenderer<StraightPipeTileEntity> {

	public TransparentStraightPipeRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	protected void renderSafe(StraightPipeTileEntity te, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		FluidTransportBehaviour pipe = te.getBehaviour(FluidTransportBehaviour.TYPE);
		if (pipe == null)
			return;

		for (Direction side : Iterate.directions) {

			Flow flow = pipe.getFlow(side);
			if (flow == null)
				continue;
			FluidStack fluidStack = flow.fluid;
			if (fluidStack.isEmpty())
				continue;
			LerpedFloat progress = flow.progress;
			if (progress == null)
				continue;

			float value = progress.getValue(partialTicks);
			boolean inbound = flow.inbound;
			if (value == 1) {
				if (inbound) {
					Flow opposite = pipe.getFlow(side.getOpposite());
					if (opposite == null)
						value -= 1e-6f;
				} else {
					FluidTransportBehaviour adjacent = TileEntityBehaviour.get(te.getLevel(), te.getBlockPos()
						.relative(side), FluidTransportBehaviour.TYPE);
					if (adjacent == null)
						value -= 1e-6f;
					else {
						Flow other = adjacent.getFlow(side.getOpposite());
						if (other == null || !other.inbound && !other.complete)
							value -= 1e-6f;
					}
				}
			}

			FluidRenderer.renderFluidStream(fluidStack, side, 3 / 16f, value, inbound, buffer, ms, light);
		}

	}

}
