/*
 * Copyright © Contributing Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional copyright and licensing notices may apply for content that was
 * included from other projects. For more information, see ATTRIBUTION.md.
 */

package grondag.canvas.apiimpl.rendercontext;

import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.vram.frex.api.material.MaterialConstants;
import io.vram.frex.api.material.MaterialMap;
import io.vram.frex.api.model.BlockModel;
import io.vram.frex.base.renderer.context.BaseBlockContext;
import io.vram.frex.base.renderer.mesh.BaseQuadEmitter;

/**
 * Context used when blocks are rendered as part of an entity.
 * Vanilla examples include blocks held be endermen, blocks in minecarts,
 * flowers held by iron golems and Mooshroom mushrooms.
 *
 * <p>Also handle rendering of the item frame which looks and acts like a block
 * and has a block JSON model but is an entity.
 */
public class EntityBlockRenderContext extends AbstractBlockRenderContext<BlockAndTintGetter> {
	private static final Supplier<ThreadLocal<EntityBlockRenderContext>> POOL_FACTORY = () -> ThreadLocal.withInitial(() -> {
		final EntityBlockRenderContext result = new EntityBlockRenderContext();
		return result;
	});

	private static ThreadLocal<EntityBlockRenderContext> POOL = POOL_FACTORY.get();

	private int light;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private Level level;
	private float tickDelta;

	public EntityBlockRenderContext() {
		super("BlockRenderContext");
	}

	@Override
	protected BaseBlockContext<BlockAndTintGetter> createInputContext() {
		return new BaseBlockContext<>() {
			@Override
			protected int fastBrightness(BlockPos pos) {
				return light;
			}
		};
	}

	public static void reload() {
		POOL = POOL_FACTORY.get();
	}

	public static EntityBlockRenderContext get() {
		return POOL.get();
	}

	public void tickDelta(float tickDelta) {
		this.tickDelta = tickDelta;
	}

	public void setPosAndWorldFromEntity(Entity entity) {
		if (entity != null) {
			final float tickDelta = this.tickDelta;
			final double x = Mth.lerp(tickDelta, entity.xo, entity.getX());
			final double y = Mth.lerp(tickDelta, entity.yo, entity.getY()) + entity.getEyeHeight();
			final double z = Mth.lerp(tickDelta, entity.zo, entity.getZ());
			pos.set(x, y, z);
			level = entity.getCommandSenderWorld();
		}
	}

	/**
	 * Assumes region and block pos set earlier via {@link #setPosAndWorldFromEntity(Entity)}.
	 */
	public void render(ModelBlockRenderer vanillaRenderer, BakedModel model, BlockState state, PoseStack matrixStack, MultiBufferSource consumers, int overlay, int light) {
		defaultConsumer = consumers.getBuffer(ItemBlockRenderTypes.getRenderType(state, false));
		encodingContext.prepare(matrixStack);
		this.light = light;
		inputContext.prepareForWorld(level, false);
		prepareForBlock(state, pos, model.useAmbientOcclusion(), 42L, overlay);
		((BlockModel) model).renderAsBlock(inputContext, emitter());
		defaultConsumer = null;
	}

	// item frames don't have a block state but render like a block
	public void renderItemFrame(ModelBlockRenderer modelRenderer, BakedModel model, PoseStack matrixStack, MultiBufferSource consumers, int overlay, int light, ItemFrame itemFrameEntity) {
		defaultConsumer = consumers.getBuffer(Sheets.solidBlockSheet());
		encodingContext.prepare(matrixStack);
		this.light = light;
		inputContext.prepareForWorld(level, false);
		pos.set(itemFrameEntity.getX(), itemFrameEntity.getY(), itemFrameEntity.getZ());
		inputContext.prepareForBlock(Blocks.AIR.defaultBlockState(), pos, 42L, overlay);
		materialMap = MaterialMap.defaultMaterialMap();
		defaultAo = false;
		defaultPreset = MaterialConstants.PRESET_SOLID;

		((BlockModel) model).renderAsBlock(inputContext, emitter());
		defaultConsumer = null;
	}

	@Override
	public int brightness() {
		return light;
	}

	@Override
	protected void adjustMaterial() {
		super.adjustMaterial();
		finder.disableAo(true);
	}

	@Override
	public void computeAo(BaseQuadEmitter quad) {
		// NOOP
	}

	@Override
	public void computeFlat(BaseQuadEmitter quad) {
		computeFlatSimple(quad);
	}
}
