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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
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

	// Cache pour les emplacements récents des utilisateurs
	private final Map<UUID, CachedLocation> locationCache = new ConcurrentHashMap<>();

	// Classe interne pour représenter un emplacement mis en cache avec un timestamp
	private static class CachedLocation {
		private final VisitedLocation location;
		private final long timestamp;

		public CachedLocation(VisitedLocation location) {
			this.location = location;
			this.timestamp = System.currentTimeMillis();
		}

		// Le cache est valide pendant 5 minutes (correspondant à l'intervalle de tracking)
		public boolean isValid() {
			return System.currentTimeMillis() - timestamp < 5 * 60 * 1000; // 5 minutes en millisecondes
		}
	}

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		// Optimiser le nombre de threads en fonction des cœurs disponibles
		int coreCount = Runtime.getRuntime().availableProcessors();
		this.executorService = Executors.newFixedThreadPool(coreCount * 8); // 8 threads par cœur

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
		// Vérifier si l'utilisateur a des emplacements visités
		if (user.getVisitedLocations().size() > 0) {
			// Vérifier le cache d'abord
			CachedLocation cached = locationCache.get(user.getUserId());
			if (cached != null && cached.isValid()) {
				return cached.location;
			}
			// Si pas de cache valide, utiliser le dernier emplacement visité
			return user.getLastVisitedLocation();
		}
		// Si aucun emplacement visité, traquer l'emplacement et attendre le résultat
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
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * Version asynchrone de trackUserLocation qui utilise CompletableFuture pour la parallélisation.
	 */
	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
		return CompletableFuture.supplyAsync(() -> {
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			user.addToVisitedLocations(visitedLocation);
			locationCache.put(user.getUserId(), new CachedLocation(visitedLocation));
			return visitedLocation;
		}, executorService).thenCompose(visitedLocation -> {
			// Enchaîner le calcul des récompenses de manière asynchrone
			return CompletableFuture.supplyAsync(() -> {
				rewardsService.calculateRewards(user);
				return visitedLocation;
			}, executorService);
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
}