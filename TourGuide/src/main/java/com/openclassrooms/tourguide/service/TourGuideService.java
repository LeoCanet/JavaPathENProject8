package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
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

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
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
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
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

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/**
	 * Obtient les 5 attractions les plus proches de l'utilisateur avec toutes les informations détaillées.
	 * Cette méthode encapsule la logique de calcul des distances et des points de récompense.
	 *
	 * @param user Utilisateur pour lequel on cherche les attractions
	 * @return Liste de DTOs contenant les informations détaillées sur chaque attraction
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
	 * Utilise un min-heap pour une performance optimale avec une complexité de O(n log 5).
	 *
	 * @param visitedLocation Emplacement visité par l'utilisateur
	 * @return Une liste de 5 attractions, triées de la plus proche à la plus éloignée
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		// Récupère toutes les attractions depuis gpsUtil
		List<Attraction> allAttractions = gpsUtil.getAttractions();

		// Créer un min-heap pour stocker les attractions avec leur distance
		// Ce PriorityQueue est configuré pour trier par distance (la valeur dans Map.Entry)
		// La plus petite distance sera à la tête de la file
		PriorityQueue<Map.Entry<Attraction, Double>> nearestAttractions =
				new PriorityQueue<>(5, Comparator.comparingDouble(Map.Entry::getValue));

		// Parcourir toutes les attractions pour trouver les 5 plus proches
		for (Attraction attraction : allAttractions) {
			// Calculer la distance entre l'attraction et la position de l'utilisateur
			double distance = rewardsService.getDistance(attraction, visitedLocation.location);

			// Si on n'a pas encore 5 attractions dans le heap, ajouter simplement celle-ci
			if (nearestAttractions.size() < 5) {
				nearestAttractions.add(new AbstractMap.SimpleEntry<>(attraction, distance));
			}
			// Sinon, vérifier si cette attraction est plus proche que la plus éloignée des 5 actuelles
			// peek() nous donne l'élément avec la valeur la plus petite (la plus courte distance)
			else if (distance < nearestAttractions.peek().getValue()) {
				nearestAttractions.poll(); // Enlever l'attraction avec la plus petite distance (la plus proche)
				nearestAttractions.add(new AbstractMap.SimpleEntry<>(attraction, distance));
			}
		}

		// Convertir le PriorityQueue en liste
		// Quand on vide le heap avec poll(), on obtient les éléments du plus proche au plus éloigné
		List<Attraction> result = new ArrayList<>(5);
		while (!nearestAttractions.isEmpty()) {
			result.add(nearestAttractions.poll().getKey());
		}

		// Inverser la liste pour avoir les attractions dans l'ordre de la plus proche à la plus éloignée
		// Nécessaire car un min-heap retourne les éléments dans l'ordre croissant (distance croissante)
		Collections.reverse(result);
		return result;
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
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
