package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	// ExecutorService pour la parallélisation
	private final ExecutorService executorService;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService, ExecutorService executorService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		this.executorService = executorService;

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		// Si l'utilisateur a des emplacements visités, utiliser le dernier
		if (!user.getVisitedLocations().isEmpty()) {
			return user.getLastVisitedLocation();
		}
		// Sinon, traquer l'emplacement et attendre le résultat
		return trackUserLocation(user).join();
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return new ArrayList<>(internalUserMap.values());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(UserReward::getRewardPoints).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * Suit la position d'un utilisateur et calcule ses récompenses de manière asynchrone.
	 * <p>
	 * Cette méthode effectue deux opérations principales de façon asynchrone :
	 * 1. Obtenir et enregistrer la position actuelle de l'utilisateur via gpsUtil
	 * 2. Calculer les récompenses appropriées pour l'utilisateur basées sur sa nouvelle position
	 * <p>
	 * L'utilisation de CompletableFuture permet à ces opérations potentiellement lentes
	 * de s'exécuter en parallèle pour de nombreux utilisateurs, améliorant considérablement
	 * les performances lors du traitement d'un grand nombre d'utilisateurs simultanément.
	 *
	 * @param user L'utilisateur dont la position doit être suivie
	 * @return Un CompletableFuture contenant la position visitée,
	 *         qui sera complété une fois le suivi et le calcul des récompenses terminés
	 */
	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
		// Première étape : récupérer et enregistrer la position de l'utilisateur
		return CompletableFuture.supplyAsync(() -> {
			// Appel à gpsUtil pour obtenir la position actuelle (opération potentiellement lente)
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());

			// Enregistrer cette position dans l'historique de l'utilisateur
			user.addToVisitedLocations(visitedLocation);
//			System.out.println("GPS Util" + Thread.currentThread().getName());
			return visitedLocation;
		}, executorService).thenCompose(visitedLocation -> {
			// Deuxième étape : calculer les récompenses basées sur la nouvelle position
			// thenCompose permet d'enchaîner une autre opération asynchrone tout en gardant le flux
			return rewardsService.calculateRewards(user).thenApply(v -> visitedLocation);
		});
	}

	/**
	 * Obtient les 5 attractions les plus proches de l'utilisateur avec toutes les informations détaillées.
	 */
	public List<NearbyAttractionDTO> getNearbyAttractionsWithDetails(User user) {
		// Obtenir la dernière position de l'utilisateur
		VisitedLocation visitedLocation = getUserLocation(user);

		// Obtenir les 5 attractions les plus proches
		List<Attraction> nearbyAttractions = getNearByAttractions(visitedLocation);

		// Créer la liste de résultats sous forme de DTOs
		List<NearbyAttractionDTO> result = new ArrayList<>(5);

		// Pour chaque attraction, créer un DTO avec toutes les informations demandées
		for (Attraction attraction : nearbyAttractions) {
			// Calcule la distance entre l'utilisateur et l'attraction
			double distance = rewardsService.getDistance(attraction, visitedLocation.location);
			// Récupère les points de récompense pour cette attraction
			int rewardPoints = rewardsService.getRewardPoints(attraction, user);

			// Créer un DTO et l'ajouter à la liste
			NearbyAttractionDTO dto = new NearbyAttractionDTO(
					attraction.attractionName,
					new Location(attraction.latitude, attraction.longitude),
					visitedLocation.location,
					distance,
					rewardPoints
			);

			result.add(dto);
		}

		return result;
	}

	/**
	 * Retourne les 5 attractions les plus proches de l'emplacement donné, quelle que soit leur distance.
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> allAttractions = gpsUtil.getAttractions();

		// Utiliser PriorityQueue pour trouver efficacement les 5 attractions les plus proches
		PriorityQueue<Map.Entry<Attraction, Double>> nearestAttractions =
				new PriorityQueue<>(5, Comparator.comparingDouble(Map.Entry::getValue));

		for (Attraction attraction : allAttractions) {
			double distance = rewardsService.getDistance(attraction, visitedLocation.location);
			if (nearestAttractions.size() < 5) {
				nearestAttractions.add(new AbstractMap.SimpleEntry<>(attraction, distance));
			} else if (distance < nearestAttractions.peek().getValue()) {
				nearestAttractions.poll();
				nearestAttractions.add(new AbstractMap.SimpleEntry<>(attraction, distance));
			}
		}

		List<Attraction> result = new ArrayList<>(5);
		while (!nearestAttractions.isEmpty()) {
			result.add(nearestAttractions.poll().getKey());
		}
		Collections.reverse(result);
		return result;
	}

	// Méthode pour fermer proprement l'ExecutorService
	public void shutdownExecutorService() {
		executorService.shutdown();
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
				shutdownExecutorService();
			}
		});
	}

	/**********************************************************************************
	 *
	 * Methods Below: For Internal Testing
	 *
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	/**
	 * Méthode utilitaire réservée aux tests pour vider la carte des utilisateurs internes.
	 * Permet d'isoler les tests les uns des autres en garantissant un état propre.
	 */
	public void clearInternalUsers() {
		internalUserMap.clear();
	}

}