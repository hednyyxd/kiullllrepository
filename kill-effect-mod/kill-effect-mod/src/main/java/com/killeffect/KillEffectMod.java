package com.killeffect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Когда игрок убивает другого игрока, на месте смерти проигрывается анимация:
 * вспышка, расходящееся кольцо, две спирали из душ, взлёт вверх и финальный салют.
 * Всё делается на сервере, поэтому клиентам не нужны ресурспаки.
 */
public class KillEffectMod implements ModInitializer {

	/** Поставь true, чтобы эффект был и при убийстве мобов. */
	private static final boolean EFFECT_FOR_MOBS = false;

	/** Сколько тиков (20 тиков = 1 секунда) длится анимация. */
	private static final int DURATION = 45;

	private static final List<Effect> ACTIVE = new ArrayList<>();

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) -> {
			Entity attacker = source.getEntity();
			if (!(attacker instanceof ServerPlayer killer)) {
				return;
			}
			if (!(victim.level() instanceof ServerLevel level)) {
				return;
			}
			if (!EFFECT_FOR_MOBS && !(victim instanceof ServerPlayer)) {
				return;
			}
			if (victim == killer) {
				return;
			}
			ACTIVE.add(new Effect(level, victim.getX(), victim.getY(), victim.getZ(), killer));
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Effect> it = ACTIVE.iterator();
			while (it.hasNext()) {
				Effect effect = it.next();
				effect.tick();
				if (effect.age >= DURATION) {
					it.remove();
				}
			}
		});
	}

	private static final class Effect {
		final ServerLevel level;
		final double x, y, z;
		final ServerPlayer killer;
		int age = 0;

		Effect(ServerLevel level, double x, double y, double z, ServerPlayer killer) {
			this.level = level;
			this.x = x;
			this.y = y;
			this.z = z;
			this.killer = killer;
		}

		void tick() {
			if (age == 0) {
				start();
			}

			// Расходящееся кольцо
			double ringRadius = age * 0.13;
			if (ringRadius < 4.5) {
				int points = 20;
				for (int i = 0; i < points; i++) {
					double a = (Math.PI * 2 * i) / points;
					level.sendParticles(ParticleTypes.END_ROD,
							x + Math.cos(a) * ringRadius, y + 0.1, z + Math.sin(a) * ringRadius,
							1, 0, 0, 0, 0);
				}
			}

			// Две спирали из душ, поднимающиеся вверх
			double height = age * 0.075;
			double spiralRadius = 0.9 - age * 0.012;
			if (spiralRadius < 0.15) {
				spiralRadius = 0.15;
			}
			for (int arm = 0; arm < 2; arm++) {
				double a = age * 0.55 + arm * Math.PI;
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
						x + Math.cos(a) * spiralRadius, y + height, z + Math.sin(a) * spiralRadius,
						1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.END_ROD,
						x + Math.cos(a) * spiralRadius * 0.5, y + height, z + Math.sin(a) * spiralRadius * 0.5,
						1, 0, 0, 0, 0);
			}

			// Столб из душ в центре
			if (age % 2 == 0) {
				level.sendParticles(ParticleTypes.SOUL, x, y + height * 0.5, z, 1, 0.1, 0.2, 0.1, 0.01);
			}

			// Финал
			if (age == 32) {
				finish();
			}

			age++;
		}

		void start() {
			double cy = y + 1.0;
			level.sendParticles(ParticleTypes.EXPLOSION, x, cy, z, 3, 0.3, 0.3, 0.3, 0);
			level.sendParticles(ParticleTypes.CLOUD, x, cy, z, 25, 0.3, 0.4, 0.3, 0.12);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, cy, z, 40, 0.2, 0.4, 0.2, 0.15);

			level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.6f);
			level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.15f, 1.8f);
			killer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
		}

		void finish() {
			double cy = y + 2.6;
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, cy, z, 90, 0.4, 0.5, 0.4, 0.45);
			level.sendParticles(ParticleTypes.END_ROD, x, cy, z, 60, 0.2, 0.2, 0.2, 0.35);
			level.sendParticles(ParticleTypes.FIREWORK, x, cy, z, 50, 0.2, 0.2, 0.2, 0.25);
			level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 1.0f);
			level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
	}
}
