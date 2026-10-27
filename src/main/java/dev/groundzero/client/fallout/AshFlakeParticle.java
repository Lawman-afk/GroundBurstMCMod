package dev.groundzero.client.fallout;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A flake of fallout ash (DESIGN.md §21.8, slice 12b): it sinks slowly, carried along by the wind it was given (its
 * spawner's x and z speeds, blocks a tick), fluttering and turning as it goes, and settles where it lands.
 */
public class AshFlakeParticle extends SingleQuadParticle {
    private final double driftX, driftZ;
    private final float phase, spin;
    private int settled;

    AshFlakeParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, double driftX, double driftZ) {
        super(level, x, y, z, sprite);
        this.driftX = driftX;
        this.driftZ = driftZ;
        this.lifetime = 160 + this.random.nextInt(80);
        this.gravity = 0;
        this.friction = 1;
        this.hasPhysics = true;
        this.quadSize = 0.06f + this.random.nextFloat() * 0.05f;
        this.setSize(0.02f, 0.02f);
        this.yd = -(0.04 + this.random.nextFloat() * 0.03);
        this.xd = driftX;
        this.zd = driftZ;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.spin = (this.random.nextFloat() - 0.5f) * 0.12f;
        float grey = 0.78f + this.random.nextFloat() * 0.2f;
        this.setColor(grey, grey * 0.99f, grey * 0.95f);
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            remove();
            return;
        }
        if (this.onGround) {
            // Settled: it lies a moment, then is gone.
            if (++this.settled > 20) {
                remove();
            }
            return;
        }
        float t = this.age * 0.15f + this.phase;
        this.xd = this.driftX + Mth.sin(t) * 0.012;
        this.zd = this.driftZ + Mth.cos(t * 0.8f) * 0.012;
        this.roll += this.spin;
        move(this.xd, this.yd, this.zd);
    }

    /** {@code xAux} and {@code zAux}: the wind's drift, blocks a tick. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux,
                double zAux, RandomSource random) {
            return new AshFlakeParticle(level, x, y, z, this.sprites.get(random), xAux, zAux);
        }
    }
}
