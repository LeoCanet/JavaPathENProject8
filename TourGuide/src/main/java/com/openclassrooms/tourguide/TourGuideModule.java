package com.openclassrooms.tourguide;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import gpsUtil.GpsUtil;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.service.RewardsService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class TourGuideModule {
	
	@Bean
	public GpsUtil getGpsUtil() {
		return new GpsUtil();
	}

	@Bean
	public ExecutorService getExecutorService() {
		// Utilise la même configuration pour TourGuideService et RewardsService
		return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 8);
	}

	/**
	 * Configure et expose le bean RewardsService pour l'application.
	 *
	 * Cette méthode utilise l'injection de dépendances par les paramètres, une bonne
	 * pratique de Spring. Au lieu d'appeler manuellement `getGpsUtil()` etc., nous
	 * déclarons les dépendances requises dans la signature de la méthode. Spring se charge
	 * alors de trouver les beans correspondants dans son contexte et de les "injecter"
	 * en tant qu'arguments.
	 *
	 * Avantages de cette approche :
	 * - Améliore le découplage : la méthode ne sait pas comment les dépendances sont créées.
	 * - Augmente la lisibilité : les dépendances sont clairement visibles dans la signature.
	 * - Facilite la maintenance et les tests.
	 */
	@Bean
	public RewardsService getRewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral, ExecutorService executorService) {
		return new RewardsService(gpsUtil, rewardCentral, executorService);
	}
	
	@Bean
	public RewardCentral getRewardCentral() {
		return new RewardCentral();
	}
	
}
