package com.lany.fixedexpense;

import com.lany.shared.calendar.BudgetCalendar;
import com.lany.shared.money.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Calcule la prévision des dépenses fixes restantes et le reste simulé.
 *
 * Domaine pur, sans état : ni Spring, ni base. Les règles arrivent déjà filtrées par le Service
 * (actives, non archivées) ; le calendrier fournit « aujourd'hui » et les périodes restantes.
 *
 * Idée centrale (voir le guide) :
 *   prévision d'une règle = montant x (périodes restantes NON COUVERTES)
 *   reste simulé          = reste réel - prévision totale
 */
public final class ProjectionCalculator {

    /** Dates de début des périodes encore à prévoir : restantes ET pas déjà réalisées. */
    public List<LocalDate> uncoveredPeriods(ProjectedRule rule, YearMonth month, BudgetCalendar calendar) {
        return rule.frequency().remainingPeriodStarts(calendar, month).stream()
                // Une période est couverte si une transaction a la même date de début.
                // C'est ce qui évite le double comptage (prévu + réalisé).
                .filter(start -> !rule.coveredPeriodStarts().contains(start))
                .toList();
    }

    /** Prévision d'une règle : montant x nombre de périodes non couvertes. */
    public Money remainingProjection(ProjectedRule rule, YearMonth month, BudgetCalendar calendar) {
        int periods = uncoveredPeriods(rule, month, calendar).size();
        return rule.amount().times(periods); // pleine précision, aucun arrondi ici
    }

    /** Somme des prévisions de toutes les règles (zéro s'il n'y en a aucune). */
    public Money totalRemainingProjection(List<ProjectedRule> rules, YearMonth month, BudgetCalendar calendar) {
        Money total = Money.zero();
        for (ProjectedRule rule : rules) {
            total = total.plus(remainingProjection(rule, month, calendar));
        }
        return total;
    }

    /** Reste simulé = reste réel - prévision restante. Le résultat peut être négatif. */
    public SimulationResult simulate(
            Money realBalance, List<ProjectedRule> rules, YearMonth month, BudgetCalendar calendar) {
        Money projected = totalRemainingProjection(rules, month, calendar);
        return new SimulationResult(realBalance, projected, realBalance.minus(projected));
    }
}