package com.lany.fixedexpense;

import com.lany.shared.calendar.BudgetCalendar;
import com.lany.shared.calendar.BudgetRules;
import com.lany.shared.money.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Fréquence d'une dépense fixe : DAILY, WEEKLY ou MONTHLY.
 *
 * Chaque constante a son PROPRE comportement (méthodes abstraites implémentées dans un
 * corps { ... }). C'est du polymorphisme : on appelle frequency.periodOf(...) sans
 * switch, et le compilateur refuse une nouvelle constante qui oublierait une méthode.
 *
 * Deux notions à ne jamais mélanger :
 *  - ÉQUIVALENCE (budgetDays, convert)   -> BudgetRules, convention 5 jours x 4 semaines
 *  - PROJECTION (remainingPeriodStarts, periodOf) -> BudgetCalendar, calendrier réel
 *
 * Une "période" est identifiée par sa DATE DE DÉBUT, qui correspond à covered_from en base.
 */
public enum Frequency {

    DAILY("daily") {
        @Override
        public long budgetDays(BudgetRules rules) {
            return 1;
        }

        @Override
        public List<LocalDate> remainingPeriodStarts(BudgetCalendar calendar, YearMonth month) {
            // Un jour budgétaire = une période ; aujourd'hui inclus (le calendrier le gère)
            return calendar.remainingBudgetDays(month);
        }

        @Override
        public CoveredPeriod periodOf(LocalDate anchor, BudgetCalendar calendar) {
            return new CoveredPeriod(anchor, anchor);
        }
    },

    WEEKLY("weekly") {
        @Override
        public long budgetDays(BudgetRules rules) {
            return rules.daysPerWeek();
        }

        @Override
        public List<LocalDate> remainingPeriodStarts(BudgetCalendar calendar, YearMonth month) {
            // Un lundi par semaine, tant que le dimanche n'est pas passé
            return calendar.remainingWeekStarts(month);
        }

        @Override
        public CoveredPeriod periodOf(LocalDate anchor, BudgetCalendar calendar) {
            // N'importe quelle date de la semaine donne le même lundi -> dimanche
            return new CoveredPeriod(calendar.weekStart(anchor), calendar.weekEnd(anchor));
        }
    },

    MONTHLY("monthly") {
        @Override
        public long budgetDays(BudgetRules rules) {
            return rules.daysPerMonth();
        }

        @Override
        public List<LocalDate> remainingPeriodStarts(BudgetCalendar calendar, YearMonth month) {
            // Seul cas avec une vraie logique : le mois entier est UNE période,
            // à prévoir tant que le mois n'est pas terminé (son dernier jour compte encore).
            boolean monthIsOver = month.atEndOfMonth().isBefore(calendar.today());
            return monthIsOver ? List.of() : List.of(month.atDay(1));
        }

        @Override
        public CoveredPeriod periodOf(LocalDate anchor, BudgetCalendar calendar) {
            YearMonth month = YearMonth.from(anchor);
            return new CoveredPeriod(month.atDay(1), month.atEndOfMonth()); // gère les années bissextiles
        }
    };

    private final String dbValue;

    Frequency(String dbValue) {
        this.dbValue = dbValue;
    }

    // ---------- ÉQUIVALENCE budgétaire ----------

    /** Nombre de jours budgétaires que représente une période (1 / 5 / 20 par défaut). */
    public abstract long budgetDays(BudgetRules rules);

    /**
     * Convertit un montant d'une fréquence à une autre : 200 000 Ar/mois -> 10 000 Ar/jour.
     * Formule : montant x (jours de la cible) ÷ (jours de la source).
     * Aucun switch : les neuf conversions passent par budgetDays().
     * On multiplie avant de diviser et on n'arrondit jamais ici (Money garde la précision).
     */
    public Money convert(Money amount, Frequency target, BudgetRules rules) {
        return amount.times(target.budgetDays(rules)).dividedBy(this.budgetDays(rules));
    }

    // ---------- PROJECTION (calendrier réel) ----------

    /** Dates de début des périodes qu'il reste à prévoir dans ce mois. */
    public abstract List<LocalDate> remainingPeriodStarts(BudgetCalendar calendar, YearMonth month);

    /** Période naturelle couverte quand on réalise la règle (-> covered_from / covered_to). */
    public abstract CoveredPeriod periodOf(LocalDate anchor, BudgetCalendar calendar);

    // ---------- Correspondance avec la base ----------

    // Les CHECK de V1 n'acceptent que 'daily' / 'weekly' / 'monthly' en minuscules.
    // On ne persiste jamais name() (DAILY) ni ordinal() : un renommage ou un réordonnancement
    // casserait les données. Un AttributeConverter JPA utilisera ces deux méthodes (étape Entities).

    public String dbValue() {
        return dbValue;
    }

    public static Frequency fromDbValue(String value) {
        Objects.requireNonNull(value, "value cannot be null");
        for (Frequency frequency : values()) {
            if (frequency.dbValue.equals(value)) {
                return frequency;
            }
        }
        throw new IllegalArgumentException("Unknown frequency: " + value);
    }
}