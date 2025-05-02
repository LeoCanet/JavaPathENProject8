package com.openclassrooms.tourguide.dto;

import gpsUtil.location.Location;

/**
 * Record représentant les informations d'une attraction à proximité d'un utilisateur.
 * Les records sont immutables et parfaits pour les DTOs.
 */
public record NearbyAttractionDTO(
        String name,
        Location attractionLocation,
        Location userLocation,
        double distance,
        int rewardPoints
) {
    // Les records fournissent automatiquement :
    // - Un constructeur
    // - Des méthodes d'accès (getters, nommés comme les champs)
    // - equals(), hashCode(), et toString() bien implémentés
}