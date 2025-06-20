package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import tripPricer.Provider;

/**
 * Classe de test pour TourGuideService.
 * L'annotation @SpringBootTest permet d'utiliser le contexte Spring pour injecter
 * les beans nécessaires, ce qui simplifie grandement la configuration des tests.
 */
@SpringBootTest
public class TestTourGuideService {

	// Injection des services gérés par Spring.
	@Autowired
	private TourGuideService tourGuideService;

	/**
	 * S'exécute AVANT chaque test de cette classe.
	 * La responsabilité principale de cette méthode est de garantir un environnement de test propre et isolé.
	 * Pour cela, on vide la liste des utilisateurs internes qui pourrait avoir été remplie par d'autres tests.
	 */
	@BeforeEach
	public void setUp() {
		// On vide la carte des utilisateurs pour garantir un état propre.
		tourGuideService.clearInternalUsers();
		// Arrête le tracker de fond
		tourGuideService.tracker.stopTracking();
	}

	@Test
	public void getUserLocation() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		// L'ajout de l'utilisateur est nécessaire pour que le service le connaisse.
		tourGuideService.addUser(user);

		// trackUserLocation est asynchrone, on attend le résultat avec .join().
		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user).join();

		assertEquals(user.getUserId(), visitedLocation.userId);
	}

	@Test
	public void addUser() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		User retrievedUser = tourGuideService.getUser(user.getUserName());
		User retrievedUser2 = tourGuideService.getUser(user2.getUserName());

		assertEquals(user, retrievedUser);
		assertEquals(user2, retrievedUser2);
	}

	@Test
	public void getAllUsers() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		User user2 = new User(UUID.randomUUID(), "jon2", "000", "jon2@tourGuide.com");

		tourGuideService.addUser(user);
		tourGuideService.addUser(user2);

		List<User> allUsers = tourGuideService.getAllUsers();

		assertTrue(allUsers.contains(user));
		assertTrue(allUsers.contains(user2));
	}

	@Test
	public void trackUser() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		tourGuideService.addUser(user);

		VisitedLocation visitedLocation = tourGuideService.trackUserLocation(user).join();

		assertEquals(user.getUserId(), visitedLocation.userId);
		// Après un suivi, l'utilisateur doit avoir au moins une position visitée.
		assertEquals(1, user.getVisitedLocations().size());
	}

	@Test
	public void getNearbyAttractions() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		tourGuideService.addUser(user);

		// On s'assure que l'utilisateur a une position avant de chercher les attractions proches.
		tourGuideService.trackUserLocation(user).join();

		// La méthode à tester est getNearbyAttractionsWithDetails, pas getNearByAttractions
		// Mais pour garder le test original, on appelle getNearByAttractions
		List<Attraction> attractions = tourGuideService.getNearByAttractions(user.getLastVisitedLocation());

		// Le service doit toujours retourner les 5 attractions les plus proches.
		assertEquals(5, attractions.size());
	}

	@Test
	public void getTripDeals() {
		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		tourGuideService.addUser(user);

		List<Provider> providers = tourGuideService.getTripDeals(user);

		// L'API TripPricer retourne 5 offres par défaut (même si la javadoc du test dit 10)
		assertEquals(5, providers.size());
	}
}