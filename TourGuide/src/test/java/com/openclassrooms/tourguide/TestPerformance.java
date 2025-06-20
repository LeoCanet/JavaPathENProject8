package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

/**
 * Classe de tests de performance.
 * Utilise @SpringBootTest pour lancer un contexte d'application Spring complet,
 * permettant d'injecter directement les beans configurés (@Autowired).
 * Cela évite de devoir instancier manuellement les services et leurs dépendances.
 */
@SpringBootTest
public class TestPerformance {

	// Injection automatique des services gérés par Spring. Plus besoin de 'new'.
	@Autowired
	private TourGuideService tourGuideService;

	@Autowired
	private RewardsService rewardsService;

	@Autowired
	private GpsUtil gpsUtil;

	/**
	 * S'exécute une seule fois avant tous les tests de cette classe.
	 * Configure le nombre d'utilisateurs pour les tests à haute volumétrie.
	 */
	@BeforeAll
	public static void setup() {
		// Le test tournant sur 100 000 utilisateurs, on ajuste la valeur ici.
		InternalTestHelper.setInternalUserNumber(50000);
	}

	/**
	 * S'exécute après tous les tests pour s'assurer que les threads sont bien arrêtés.
	 */
	@AfterAll
	public static void cleanUp(@Autowired TourGuideService tourGuideService) {
		tourGuideService.tracker.stopTracking();
	}

	@Test
	public void highVolumeTrackLocation() {
		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Lance le suivi de tous les utilisateurs en parallèle et collecte les Futures.
		List<CompletableFuture<VisitedLocation>> futures = allUsers.stream()
				.map(tourGuideService::trackUserLocation)
				.toList();

		// Attend que toutes les opérations de suivi soient terminées.
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		stopWatch.stop();

		System.out.println("highVolumeTrackLocation: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		// Vérifie que le temps d'exécution est inférieur à la limite de 15 minutes.
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	@Test
	public void highVolumeGetRewards() {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Simule une visite pour chaque utilisateur afin qu'ils soient éligibles à une récompense.
		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();
		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

		// Lance le calcul des récompenses pour tous les utilisateurs en parallèle.
		// La méthode calculateRewards est elle-même asynchrone et retourne un CompletableFuture.
		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(rewardsService::calculateRewards)
				.toList();

		// Attend que tous les calculs de récompenses soient terminés.
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// Vérifie que chaque utilisateur a bien reçu au moins une récompense.
		for (User user : allUsers) {
            assertFalse(user.getUserRewards().isEmpty(), "Chaque utilisateur devrait avoir au moins une récompense.");
		}

		stopWatch.stop();

		System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime())
				+ " seconds.");
		// Vérifie que le temps d'exécution est inférieur à la limite de 20 minutes.
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}
}