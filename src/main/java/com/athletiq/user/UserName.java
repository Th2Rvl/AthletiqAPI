package com.athletiq.user;

/** Prénom et nom d'un compte, pour l'affichage par les autres fonctionnalités (aucune donnée sensible). */
public record UserName(String firstName, String lastName) {
}
