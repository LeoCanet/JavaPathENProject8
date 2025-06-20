package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import com.openclassrooms.tourguide.user.UserReward;

/**
 * Classe de test pour RewardsService.
 * Utilise @SpringBootTest pour injecter les services via @Autowired,
 * garantissant que les tests s'exécutent avec la même configuration que l'application.
 */
@SpringBootTest
public class TestRewardsService {

	@Autowired
	private GpsUtil gpsUtil;

	@Autowired
	private RewardsService rewardsService;

	@Autowired
	private TourGuideService tourGuideService;

	/**
	 * Méthode de nettoyage exécutée APRÈS chaque test.
	 * C'est la solution pour éviter les fuites d'état entre les tests.
	 * On réinitialise ici le buffer de proximité à sa valeur par défaut
	 * pour que les tests ne s'influencent pas mutuellement.
	 * On arrête aussi le tracker.
	 */
	@AfterEach
	public void tearDown() {
		rewardsService.setDefaultProximityBuffer(); // <-- LA CORRECTION CLÉ !
		tourGuideService.tracker.stopTracking();
	}

	@Test
	public void userGetRewards() {
		// Configure le test pour ne pas avoir d'utilisateurs internes par défaut.
		InternalTestHelper.setInternalUserNumber(0);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		Attraction attraction = gpsUtil.getAttractions().get(0);
		user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));

		// trackUserLocation déclenche le calcul des récompenses. On attend sa complétion.
		tourGuideService.trackUserLocation(user).join();

		List<UserReward> userRewards = user.getUserRewards();

		// Vérifie que l'utilisateur a bien reçu une récompense.
		assertEquals(1, userRewards.size());
	}

	@Test
	public void isWithinAttractionProximity() {
		Attraction attraction = gpsUtil.getAttractions().get(0);
		// Une attraction est toujours à proximité d'elle-même (distance 0).
		assertTrue(rewardsService.isWithinAttractionProximity(attraction, attraction));
	}

	@Test
	public void nearAllAttractions() {
		// Augmente la proximité au maximum pour que toutes les attractions soient considérées "proches".
		rewardsService.setProximityBuffer(Integer.MAX_VALUE);
		InternalTestHelper.setInternalUserNumber(1);

		// Récupère le premier utilisateur de test.
		User user = tourGuideService.getAllUsers().get(0);

		// Calcule les récompenses et attend la fin de l'opération asynchrone.
		rewardsService.calculateRewards(user).join();

		List<UserReward> userRewards = tourGuideService.getUserRewards(user);

		// Vérifie que l'utilisateur a reçu une récompense pour chaque attraction existante.
		assertEquals(gpsUtil.getAttractions().size(), userRewards.size());
	}
}