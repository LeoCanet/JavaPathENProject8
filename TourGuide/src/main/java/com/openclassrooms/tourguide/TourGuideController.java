package com.openclassrooms.tourguide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openclassrooms.tourguide.service.RewardsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;

    @Autowired
    RewardsService rewardsService;
	
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }
    
    @RequestMapping("/getLocation") 
    public VisitedLocation getLocation(@RequestParam String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }
    
    //  TODO: Change this method to no longer return a List of Attractions.
 	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
 	//  Return a new JSON object that contains:
    	// Name of Tourist attraction, 
        // Tourist attractions lat/long, 
        // The user's location lat/long, 
        // The distance in miles between the user's location and each of the attractions.
        // The reward points for visiting each Attraction.
        //    Note: Attraction reward points can be gathered from RewardsCentral

    /**
     * Point d'entrée API pour récupérer les 5 attractions les plus proches d'un utilisateur.
     * Renvoie les informations détaillées sur chaque attraction
     *
     * @param userName Nom de l'utilisateur
     * @return Liste d'objets JSON contenant les détails de chaque attraction
     */
    @RequestMapping("/getNearbyAttractions")
    public List<HashMap<String, Object>> getNearbyAttractions(@RequestParam String userName) {
        // Récupérer l'utilisateur et sa dernière position connue
        User user = getUser(userName);
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);

        // Obtenir les 5 attractions les plus proches
        List<Attraction> attractions = tourGuideService.getNearByAttractions(visitedLocation);

        // Préparer la liste de résultats avec une capacité initiale de 5 pour éviter les redimensionnements
        List<HashMap<String, Object>> nearbyAttractions = new ArrayList<>(5);

        // Pour chaque attraction, créer un objet JSON avec toutes les informations demandées
        for (Attraction attraction : attractions) {
            // Calcule la distance entre l'utilisateur et l'attraction
            double distance = rewardsService.getDistance(attraction, visitedLocation.location);
            // Récupère les points de récompense pour cette attraction
            int rewardPoints = rewardsService.getRewardPoints(attraction, user);

            // Créer une HashMap avec les informations requises
            // Capacité initiale de 5 pour les 5 propriétés
            HashMap<String, Object> attractionData = new HashMap<>(5);
            attractionData.put("name", attraction.attractionName);

            // Coordonnées de l'attraction
            attractionData.put("attractionLocation", Map.of(
                    "lat", attraction.latitude,
                    "long", attraction.longitude
            ));

            // Coordonnées de l'utilisateur
            attractionData.put("userLocation", Map.of(
                    "lat", visitedLocation.location.latitude,
                    "long", visitedLocation.location.longitude
            ));

            // Distance entre l'utilisateur et l'attraction
            attractionData.put("distance", distance);

            // Points de récompense pour la visite de cette attraction
            attractionData.put("rewardPoints", rewardPoints);

            // Ajoute cet objet à la liste des résultats
            nearbyAttractions.add(attractionData);
        }

        return nearbyAttractions;
    }
    
    @RequestMapping("/getRewards") 
    public List<UserReward> getRewards(@RequestParam String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }
       
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }
    
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}