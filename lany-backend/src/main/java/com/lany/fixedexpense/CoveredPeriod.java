package com.lany.fixedexpense;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Plage de dates [from, to] (bornes incluses) qu'une dépense fixe réalisée couvre.
 * C'est le pendant Java des colonnes covered_from / covered_to de financial_transaction.
 *
 * Exemples : un jour = [9 oct, 9 oct] ; une semaine = [lundi, dimanche] ; un mois = [1er, dernier jour].
 */
public record CoveredPeriod(LocalDate from, LocalDate to) {

    public CoveredPeriod {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        // Même règle que la contrainte SQL : covered_from <= covered_to
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }
}