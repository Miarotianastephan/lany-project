package com.lany.shared.calendar;

/**
 * Les deux constantes budgétaires d'un foyer (household.days_per_week et
 * household.weeks_per_month).
 *
 * Elles servent à l'ÉQUIVALENCE (2 000 Ar/jour = 40 000 Ar/mois avec 5 x 4),
 * jamais à la projection, qui suit le calendrier réel.
 *
 * Un record génère automatiquement constructeur, accesseurs (daysPerWeek(), weeksPerMonth()),
 * equals, hashCode et toString : idéal pour un petit objet-valeur immuable.
 */
public record BudgetRules(int daysPerWeek, int weeksPerMonth) {

    // Constructeur COMPACT : pas de paramètres entre parenthèses, on ne fait que valider.
    // Les champs sont affectés automatiquement à la fin. Mêmes bornes que les CHECK de V1.
    public BudgetRules {
        if (daysPerWeek < 1 || daysPerWeek > 7) {
            throw new IllegalArgumentException("daysPerWeek must be between 1 and 7");
        }
        if (weeksPerMonth < 1 || weeksPerMonth > 5) {
            throw new IllegalArgumentException("weeksPerMonth must be between 1 and 5");
        }
    }

    /** Convention par défaut de Lany : 5 jours par semaine, 4 semaines par mois. */
    public static BudgetRules defaults() {
        return new BudgetRules(5, 4);
    }

    /** Jours budgétaires d'un mois : 5 x 4 = 20 par défaut. */
    public int daysPerMonth() {
        return daysPerWeek * weeksPerMonth;
    }
}