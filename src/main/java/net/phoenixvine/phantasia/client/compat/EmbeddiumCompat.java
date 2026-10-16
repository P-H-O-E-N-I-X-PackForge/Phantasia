package net.phoenixvine.phantasia.client.compat;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.embeddedt.embeddium.api.render.texture.SpriteUtil;

@OnlyIn(Dist.CLIENT)
public final class EmbeddiumCompat {

    public static void markSpriteActive(TextureAtlasSprite sprite) {
        SpriteUtil.markSpriteActive(sprite);
    }

    public static boolean hasAnimation(TextureAtlasSprite sprite) {
        return SpriteUtil.hasAnimation(sprite);
    }
}
