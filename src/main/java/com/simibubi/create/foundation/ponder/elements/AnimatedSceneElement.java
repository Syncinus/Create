package com.simibubi.create.foundation.ponder.elements;

import com.jozufozu.flywheel.util.transform.MatrixTransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.ponder.PonderWorld;
import com.simibubi.create.foundation.utility.animation.LerpedFloat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public abstract class AnimatedSceneElement extends PonderSceneElement {

	protected Vec3 fadeVec;
	protected LerpedFloat fade;

	public AnimatedSceneElement() {
		fade = LerpedFloat.linear()
			.startWithValue(0);
	}

	public void forceApplyFade(float fade) {
		this.fade.startWithValue(fade);
	}

	public void setFade(float fade) {
		this.fade.setValue(fade);
	}

	public void setFadeVec(Vec3 fadeVec) {
		this.fadeVec = fadeVec;
	}

	@Override
	public final void renderFirst(PonderWorld world, MultiBufferSource buffer, PoseStack ms, float pt) {
		ms.pushPose();
		float currentFade = applyFade(ms, pt);
		renderFirst(world, buffer, ms, currentFade, pt);
		ms.popPose();
	}

	@Override
	public final void renderLayer(PonderWorld world, MultiBufferSource buffer, RenderType type, PoseStack ms,
		float pt) {
		ms.pushPose();
		float currentFade = applyFade(ms, pt);
		renderLayer(world, buffer, type, ms, currentFade, pt);
		ms.popPose();
	}

	@Override
	public final void renderLast(PonderWorld world, MultiBufferSource buffer, PoseStack ms, float pt) {
		ms.pushPose();
		float currentFade = applyFade(ms, pt);
		renderLast(world, buffer, ms, currentFade, pt);
		ms.popPose();
	}

	protected float applyFade(PoseStack ms, float pt) {
		float currentFade = fade.getValue(pt);
		if (fadeVec != null) {
			Vec3 vec = fadeVec.scale(-1 + currentFade);
			MatrixTransformStack.of(ms)
					.translate(vec.x(), vec.y(), vec.z());
		}
		return currentFade;
	}

	protected void renderLayer(PonderWorld world, MultiBufferSource buffer, RenderType type, PoseStack ms, float fade,
		float pt) {}

	protected void renderFirst(PonderWorld world, MultiBufferSource buffer, PoseStack ms, float fade, float pt) {}

	protected void renderLast(PonderWorld world, MultiBufferSource buffer, PoseStack ms, float fade, float pt) {}

	protected int lightCoordsFromFade(float fade) {
		int light = 0xF000F0;
		if (fade != 1) {
			light = (int) (Mth.lerp(fade, 5, 0xF));
			light = light << 4 | light << 20;
		}
		return light;
	}

}
