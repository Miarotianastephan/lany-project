package com.lany.fixedexpense;

import com.lany.shared.money.Money;

/**
 * Résultat de la simulation de fin de mois.
 *
 * @param realBalance      reste réel (transactions réelles uniquement), fourni par la base
 * @param projectedFixed   dépenses fixes qu'il reste à prévoir (périodes non couvertes)
 * @param simulatedBalance reste réel moins prévision ; peut être négatif (déficit prévisible)
 */
public record SimulationResult(Money realBalance, Money projectedFixed, Money simulatedBalance) {
}