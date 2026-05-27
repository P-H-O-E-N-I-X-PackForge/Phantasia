package net.phoenixvine.phantasia.client.render;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PhantasiaTrackedDummyWorld extends TrackedDummyWorld {

    public void tickAnimateForPos(BlockPos pos, RandomSource random) {
        BlockState state = getBlockState(pos);
        if (state.isAir()) return;
        state.getBlock().animateTick(state, this, pos, random);
    }
}
