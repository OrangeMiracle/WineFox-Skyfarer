package dev.winefox.skyfarer.network;

import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.*;

/** Registry only. Providers and rendering are loaded exclusively on the client. */
public final class CometParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES=DeferredRegister.create(Registries.PARTICLE_TYPE,"winefox_skyfarer");
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> TAIL=TYPES.register("comet_tail",()->new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> RING=TYPES.register("comet_ring",()->new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> FOX3_EMBLEM=TYPES.register("fox3_emblem",()->new SimpleParticleType(false));
    private CometParticles() {}
}
