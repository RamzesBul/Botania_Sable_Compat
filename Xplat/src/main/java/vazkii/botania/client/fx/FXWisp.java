/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;

import org.lwjgl.opengl.GL11;

import vazkii.botania.xplat.ClientXplatAbstractions;

public class FXWisp extends TextureSheetParticle implements ParticleSubLevelKickable {
	private final boolean depthTest;
	private final float moteParticleScale;
	private final int moteHalfLife;

	public FXWisp(ClientLevel world, double d, double d1, double d2, double xSpeed, double ySpeed, double zSpeed,
			float size, float red, float green, float blue, boolean depthTest, float maxAgeMul, boolean noClip, float g) {
		super(world, d, d1, d2);
		// super applies wiggle to motion so set it here instead
		xd = xSpeed;
		yd = ySpeed;
		zd = zSpeed;
		rCol = red;
		gCol = green;
		bCol = blue;
		alpha = 0.375F;
		gravity = g;
		quadSize = (this.random.nextFloat() * 0.5f + 0.5f) * 2 * size;
		moteParticleScale = quadSize;
		lifetime = (int) (maxAgeMul * 28 / (this.random.nextFloat() * 0.3f + 0.7f));
		this.depthTest = depthTest;

		moteHalfLife = lifetime / 2;
		setSize(0.01f, 0.01f);

		xo = x;
		yo = y;
		zo = z;
		this.hasPhysics = !noClip;
	}

	// No-clip wisps (e.g. the Alfheim portal's pylon beam) must stay bound only to the sub-level they were
	// spawned into and not be grabbed by other sub-levels they fly over/near. This only skips picking up
	// intersecting sub-levels; a wisp kicked out into its own sub-level (via initialKickOut) still follows it,
	// so sub-level-local effects are unaffected. Clip wisps keep the default behavior.
	@Override
	public boolean sable$shouldCareAboutIntersectingSubLevels() {
		return hasPhysics;
	}

	// Stay glued to the tracked sub-level even when the wisp drifts away from its spawn anchor. Otherwise Sable
	// detaches a tracked wisp once it moves >0.5 block (e.g. the pylon's rising spiral wisps drift upward),
	// after which it flies free and scatters off a moving/rotating sub-level instead of staying on the pylon.
	@Override
	public boolean sable$shouldKickFromTracking() {
		return false;
	}

	@Override
	public boolean sable$shouldCollideWithTrackingSubLevel() {
		return hasPhysics;
	}

	@Override
	public float getQuadSize(float scaleFactor) {
		float agescale = (float) age / moteHalfLife;
		if (agescale > 1) {
			agescale = 2 - agescale;
		}

		quadSize = moteParticleScale * agescale * 0.5f;
		return quadSize;
	}

	@Override
	protected int getLightColor(float partialTicks) {
		return 0xF000F0;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return depthTest ? NORMAL_RENDER : DIW_RENDER;
	}

	// [VanillaCopy] of super, without drag when onGround is true
	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		if (this.age++ >= this.lifetime) {
			this.remove();
		}

		this.yd -= this.gravity;
		this.move(this.xd, this.yd, this.zd);
		if (gravity == 0) {
			this.xd *= 0.98;
			this.yd *= 0.98;
			this.zd *= 0.98;
		}
	}

	public void setGravity(float value) {
		gravity = value;
	}

	private static BufferBuilder beginRenderCommon(Tesselator tesselator, TextureManager textureManager) {
		Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

		RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
		AbstractTexture tex = textureManager.getTexture(TextureAtlas.LOCATION_PARTICLES);
		ClientXplatAbstractions.INSTANCE.setFilterSave(tex, true, false);
		return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
	}

	//TODO there's end method in ParticleRenderType anymore. Check where the method below should be used instead
	private static void endRenderCommon() {
		AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
		ClientXplatAbstractions.INSTANCE.restoreLastFilter(tex);
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
	}

	public static final ParticleRenderType NORMAL_RENDER = new BotaniaParticleRenderType() {
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.enableDepthTest();
			return beginRenderCommon(tesselator, textureManager);
		}

		@Override
		public void end() {
			endRenderCommon();
		}

		@Override
		public String toString() {
			return "botania:wisp";
		}
	};

	public static final ParticleRenderType DIW_RENDER = new BotaniaParticleRenderType() {
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.disableDepthTest();
			return beginRenderCommon(tesselator, textureManager);

		}

		@Override
		public void end() {
			RenderSystem.enableDepthTest();
			endRenderCommon();
		}

		@Override
		public String toString() {
			return "botania:depth_ignoring_wisp";
		}
	};
}
