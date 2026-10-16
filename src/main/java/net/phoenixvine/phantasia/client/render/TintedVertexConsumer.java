package net.phoenixvine.phantasia.client.render;

import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class TintedVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private RenderType currentRenderType;

    private double offsetX, offsetY, offsetZ;

    private float r = 1f, g = 1f, b = 1f, a = 1f;

    public TintedVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    public void setCurrentRenderType(RenderType renderType) {
        this.currentRenderType = renderType;
    }

    public void setAlpha(float alpha) {
        this.a = alpha;
    }

    public void setTint(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public void resetTint() {
        r = 1f;
        g = 1f;
        b = 1f;
        a = 1f;
    }

    public void addOffset(double ox, double oy, double oz) {
        offsetX += ox;
        offsetY += oy;
        offsetZ += oz;
    }

    public void clearOffset() {
        offsetX = 0;
        offsetY = 0;
        offsetZ = 0;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        return delegate.addVertex(x + (float) offsetX, y + (float) offsetY, z + (float) offsetZ);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return delegate.setColor(
                (int) (red * r),
                (int) (green * g),
                (int) (blue * b),
                (int) (alpha * a));
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return delegate.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return delegate.setNormal(x, y, z);
    }
}
