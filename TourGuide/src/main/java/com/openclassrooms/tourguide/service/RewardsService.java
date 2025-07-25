package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
	private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ExecutorService executorService;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral, ExecutorService executorService) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		this.executorService = executorService;
	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calcule les récompenses pour un utilisateur.
	 * Cette version optimisée évite les boucles inutiles et les vérifications redondantes.
	 * Elle est conçue pour être appelée en parallèle pour de nombreux utilisateurs.
	 */
	public CompletableFuture<Void> calculateRewards(User user) {
		return CompletableFuture.runAsync(() -> {
			// La logique optimisée que nous avons écrite précédemment
			List<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
			List<Attraction> attractions = gpsUtil.getAttractions();

			Set<String> rewardedAttractions = user.getUserRewards().stream()
					.map(r -> r.attraction.attractionName)
					.collect(Collectors.toSet());

			for (VisitedLocation visitedLocation : userLocations) {
				for (Attraction attraction : attractions) {
					if (rewardedAttractions.contains(attraction.attractionName)) {
						continue;
					}
					if (nearAttraction(visitedLocation, attraction)) {
						int rewardPoints = getRewardPoints(attraction, user);
						user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
						rewardedAttractions.add(attraction.attractionName);
					}
				}
			}
		}, executorService); // Utilise l'executor injecté
	}


	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) <= attractionProximityRange;
	}

	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
        return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}
}