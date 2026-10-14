package com.lany.fixedexpense;

import com.lany.shared.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Une règle fixe telle que le calculateur de projection la voit : trois informations, rien d'autre.
 *
 * Volontairement indépendante des entités JPA : le Service construit ces objets à partir de la base
 * (règles actives et non archivées, périodes déjà couvertes par des transactions liées).
 * Le calculateur reste ainsi du domaine pur, testable sans Spring ni base.
 *
 * @param amount               montant par période de la fréquence (ex. 2 000 si DAILY)
 * @param frequency            DAILY, WEEKLY ou MONTHLY
 * @param coveredPeriodStarts  dates de DÉBUT des périodes déjà réalisées (= covered_from en base)
 */
public record ProjectedRule(Money amount, Frequency frequency, Set<LocalDate> coveredPeriodStarts) {

    public ProjectedRule {
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(frequency, "frequency cannot be null");
        Objects.requireNonNull(coveredPeriodStarts, "coveredPeriodStarts cannot be null");
        // Copie défensive immuable : si l'appelant modifie son Set ensuite, la règle ne change pas
        coveredPeriodStarts = Set.copyOf(coveredPeriodStarts);
    }
}