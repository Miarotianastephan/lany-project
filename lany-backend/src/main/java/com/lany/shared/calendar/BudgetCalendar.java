package com.lany.shared.calendar;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Calendrier budgétaire d'un foyer : jour courant, jours budgétaires, semaines ISO.
 *
 * Domaine pur : aucune dépendance à Spring ni à la base. Le temps vient d'une Clock
 * injectée, ce qui permet aux tests de fixer « nous sommes le 9 octobre 2026 ».
 *
 * Règles :
 *  - Un jour budgétaire est l'un des N premiers jours de la semaine ISO (lundi = 1).
 *  - Une semaine va du lundi au dimanche et appartient au mois de son LUNDI.
 *  - Les listes renvoyées ne sont pas modifiables.
 */
public final class BudgetCalendar {

    private final int budgetDaysPerWeek;
    private final ZoneId zone;
    private final Clock clock;

    public BudgetCalendar(int budgetDaysPerWeek, ZoneId zone, Clock clock) {
        if (budgetDaysPerWeek < 1 || budgetDaysPerWeek > 7) {
            throw new IllegalArgumentException("budgetDaysPerWeek must be between 1 and 7");
        }
        this.budgetDaysPerWeek = budgetDaysPerWeek;
        this.zone = Objects.requireNonNull(zone, "zone cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    // ---------- Temps : toujours dans le fuseau du calendrier ----------

    // Today without calendar timezone
    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    // The date of the given instant in the calendar timzone (fuseau)
    public LocalDate businessDay(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    // Minuit local de ce jour, en instant 
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    // ---------- Jours budgétaires ----------

    public boolean isBudgetDay(LocalDate date) {
        return date.getDayOfWeek().getValue() <= budgetDaysPerWeek; // 1 = lundi ... 7 = dimanche
    }

    /** Tous les jours budgétaires du mois. */
    public List<LocalDate> budgetDays(YearMonth month) {
        return budgetDaysBetween(month.atDay(1), month.atEndOfMonth());
    }

    /** Jours budgétaires de aujourd'hui (inclus) à la fin du mois. Mois passé : vide. */
    public List<LocalDate> remainingBudgetDays(YearMonth month) {
        LocalDate from = max(today(), month.atDay(1));
        return budgetDaysBetween( from,month.atEndOfMonth());
    }

    /** Jours budgétaires avant aujourd'hui (exclu), à partir de notBefore (inclus). */
    public List<LocalDate> pastBudgetDays(YearMonth month, LocalDate notBefore) {
        Objects.requireNonNull(notBefore, "notBefore cannot be null");
        LocalDate from = max(notBefore, month.atDay(1));
        LocalDate to = min(today().minusDays(1), month.atEndOfMonth());
        return budgetDaysBetween(from, to);
    }

    // ---------- Semaines ISO (lundi -> dimanche) ----------

    public LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate weekEnd(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /** Mois auquel est rattachée la semaine de cette date : le mois de son lundi. */
    public YearMonth monthOfWeek(LocalDate date) {
        return YearMonth.from(weekStart(date));
    }

    /** Les lundis du mois : une semaine par lundi (4 ou 5 selon le mois). */
    public List<LocalDate> weekStartsOf(YearMonth month) {
        return datesBetween(month.atDay(1), month.atEndOfMonth())
                .filter(date -> date.getDayOfWeek() == DayOfWeek.MONDAY)
                .toList();
    }

    /** Semaines du mois dont le dimanche n'est pas encore passé (la semaine en cours compte). */
    public List<LocalDate> remainingWeekStarts(YearMonth month) {
        LocalDate today = today(); // lu une seule fois : cohérent sur tout le calcul
        return weekStartsOf(month).stream()
                .filter(weekStart -> !weekEnd(weekStart).isBefore(today))
                .toList();
    }

    // ---------- Utilitaires privés ----------

    private List<LocalDate> budgetDaysBetween(LocalDate from, LocalDate toInclusive) {
        return datesBetween(from, toInclusive)
                .filter(this::isBudgetDay)
                .toList();
    }

    /** Dates de from à toInclusive (bornes incluses). Vide si from est après toInclusive. */
    private static Stream<LocalDate> datesBetween(LocalDate from, LocalDate toInclusive) {
        if (from.isAfter(toInclusive)) {
            return Stream.empty(); // datesUntil lèverait une exception si la fin précède le début
        }
        return from.datesUntil(toInclusive.plusDays(1));
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}