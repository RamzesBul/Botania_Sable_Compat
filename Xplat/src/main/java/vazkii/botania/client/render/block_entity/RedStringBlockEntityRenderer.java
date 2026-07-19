/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.client.core.handler.ClientTickHandler;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.common.block.block_entity.red_string.RedStringBlockEntity;
import vazkii.botania.common.helper.PlayerHelper;
import vazkii.botania.common.item.WandOfTheForestItem;

import java.util.Random;

public class RedStringBlockEntityRenderer<T extends RedStringBlockEntity> implements BlockEntityRenderer<T> {
	// 0 -> none, 10 -> full
	private static int transparency = 0;

	public static void tick() {
		Player player = Minecraft.getInstance().player;
		boolean hasWand = player != null && PlayerHelper.hasHeldItemClass(player, WandOfTheForestItem.class);
		if (transparency > 0 && !hasWand) {
			transparency--;
		} else if (transparency < 10 && hasWand) {
			transparency++;
		}
	}

	public RedStringBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

	@Override
	public void render(RedStringBlockEntity tile, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
		if (transparency <= 0) {
			return;
		}

		Direction dir = tile.getOrientation();
		BlockPos bind = tile.getBinding();

		if (bind != null) {
			ms.pushPose();
			ms.translate(0.5, 0.5, 0.5);
			// The string is drawn in the block's own frame (the PoseStack already carries its sub-level pose), but
			// a cross-level binding stores the target in its own frame. Express the target in this block's local
			// frame so the string points at it: take the target to world space (via its sub-level pose), then into
			// this block's frame. Both steps are identities on the same level, so world/same-sub-level is unchanged.
			Vec3 worldBind = SableCompat.transformFromSable(tile.getLevel(), Vec3.atCenterOf(bind));
			Vec3 localBind = SableCompat.toSableLocalFrame(tile.getLevel(), worldBind, tile.getBlockPos());
			Vec3 span = localBind.subtract(Vec3.atCenterOf(tile.getBlockPos()));
			Vec3 step = span.normalize().scale(0.025);
			Vec3 cur = step;

			int stepCount = (int) (span.length() / step.length());

			double len = (double) -(ClientTickHandler.getEntityTicksInGame() + partialTicks) / 100F
					+ new Random(dir.ordinal() ^ tile.getBlockPos().asLong()).nextInt(10000);
			double add = step.length();
			double rand = Math.random() - 0.5;
			VertexConsumer buffer = buffers.getBuffer(RenderHelper.RED_STRING);
			for (int i = 0; i < stepCount; i++) {
				vertex(ms, buffer, dir, cur.x, cur.y, cur.z, rand, len);
				rand = Math.random() - 0.5;
				cur = cur.add(step);
				len += add;
				vertex(ms, buffer, dir, cur.x, cur.y, cur.z, rand, len);
			}

			ms.popPose();
		}
	}

	/**
	 * Add a vertex at the given position, but spiraled out perpendicular to {@code dir}
	 */
	private static void vertex(PoseStack ms, VertexConsumer buffer, Direction dir,
			double xpos, double ypos, double zpos,
			double rand, double l) {
		float sizeAlpha = transparency / 10.0F;
		float ampl = (float) (0.15 * (Mth.sin((float) l * 2F) * 0.5 + 0.5) + 0.1) * sizeAlpha;

		float trigInput = (float) (l * 20.0);
		float sin = Mth.sin(trigInput);
		float cos = Mth.cos(trigInput);
		float lastTerm = (float) (rand * 0.05);

		float x = (float) xpos
				+ sin * ampl * killNonZero(dir.getStepX())
				+ lastTerm;
		float y = (float) ypos
				+ cos * ampl * killNonZero(dir.getStepY())
				+ lastTerm;
		float z = (float) zpos
				+ (dir.getStepY() == 0 ? sin : cos) * ampl * killNonZero(dir.getStepZ())
				+ lastTerm;

		buffer.addVertex(ms.last().pose(), x, y, z).setColor(0xFF, 0, 0, FastColor.as8BitChannel(sizeAlpha));
		switch (dir.getAxis().getPlane()) {
			case HORIZONTAL -> buffer.setNormal(ms.last(), 0, 1, 0);
			case VERTICAL -> buffer.setNormal(ms.last(), 1, 0, 0);
		}
	}

	private static int killNonZero(int diff) {
		if (diff != 0) {
			return 0;
		} else {
			return 1;
		}
	}

}
