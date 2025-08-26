package com.openclassrooms.tourguide.user;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;

public class User {
	private final UUID userId;
	private final String userName;
	private String phoneNumber;
	private String emailAddress;
	private Date latestLocationTimestamp;
	private List<VisitedLocation> visitedLocations = new CopyOnWriteArrayList<>();
	private List<UserReward> userRewards = new CopyOnWriteArrayList<>();
	private UserPreferences userPreferences = new UserPreferences();
	private List<Provider> tripDeals = new ArrayList<>();
	public User(UUID userId, String userName, String phoneNumber, String emailAddress) {
		this.userId = userId;
		this.userName = userName;
		this.phoneNumber = phoneNumber;
		this.emailAddress = emailAddress;
	}
	
	public UUID getUserId() {
		return userId;
	}
	
	public String getUserName() {
		return userName;
	}
	
	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}
	
	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}
	
	public String getEmailAddress() {
		return emailAddress;
	}
	
	public void setLatestLocationTimestamp(Date latestLocationTimestamp) {
		this.latestLocationTimestamp = latestLocationTimestamp;
	}
	
	public Date getLatestLocationTimestamp() {
		return latestLocationTimestamp;
	}
	
	public void addToVisitedLocations(VisitedLocation visitedLocation) {
		visitedLocations.add(visitedLocation);
	}
	
	public List<VisitedLocation> getVisitedLocations() {
		return visitedLocations;
	}
	
	public void clearVisitedLocations() {
		visitedLocations.clear();
	}

	/**
	 * Ajoute une récompense à l'utilisateur en évitant les doublons.
	 *
	 * Cette méthode implémente la règle métier : un utilisateur ne peut obtenir
	 * qu'une seule récompense par attraction. Les tentatives de doublons sont
	 * ignorées silencieusement.
	 *
	 * Note : Correction d'un bug critique où l'ancienne logique inversée empêchait
	 * l'ajout de récompenses. Utilise noneMatch() pour une vérification efficace
	 * avec court-circuit.
	 *
	 * @param userReward la récompense à ajouter
	 */
	public void addUserReward(UserReward userReward) {
		// Vérification de doublon : ajouter seulement si l'attraction n'existe pas déjà
		if(userRewards.stream().noneMatch(r -> r.attraction.attractionName.equals(userReward.attraction.attractionName))) {
			userRewards.add(userReward);
		}
	}
	
	public List<UserReward> getUserRewards() {
		return userRewards;
	}
	
	public UserPreferences getUserPreferences() {
		return userPreferences;
	}
	
	public void setUserPreferences(UserPreferences userPreferences) {
		this.userPreferences = userPreferences;
	}

	public VisitedLocation getLastVisitedLocation() {
		return visitedLocations.get(visitedLocations.size() - 1);
	}
	
	public void setTripDeals(List<Provider> tripDeals) {
		this.tripDeals = tripDeals;
	}
	
	public List<Provider> getTripDeals() {
		return tripDeals;
	}

}
