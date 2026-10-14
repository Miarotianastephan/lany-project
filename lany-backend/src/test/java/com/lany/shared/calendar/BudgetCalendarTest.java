package com.lany.shared.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spécification exécutable de BudgetCalendar.
 * Emplacement : backend/src/test/java/com/lany/shared/calendar/BudgetCalendarTest.java
 * Classe à écrire : backend/src/main/java/com/lany/shared/calendar/BudgetCalendar.java
 *
 * Calendrier de référence : octobre 2026
 *   Oct 1 = jeudi, Oct 9 = vendredi, Oct 31 = samedi. Lundis : 5, 12, 19, 26.
 *   Novembre 2026 : Nov 1 = dimanche. Lundis : 2, 9, 16, 23, 30.
 * Madagascar = UTC+3 : minuit à Madagascar = 21:00 UTC la veille.
 */
class BudgetCalendarTest {

    private static final ZoneId MADAGASCAR = ZoneId.of("Indian/Antananarivo");

    // Vendredi 9 octobre 2026, 12:00 à Madagascar
    private static final String FRIDAY_OCT_9 = "2026-10-09T09:00:00Z";

    private static final YearMonth OCT_2026 = YearMonth.of(2026, 10);
    private static final YearMonth NOV_2026 = YearMonth.of(2026, 11);

    /** Horloge figée en UTC : le calendrier doit utiliser SON fuseau, pas celui de l'horloge. */
    private static BudgetCalendar calendarAt(int budgetDaysPerWeek, String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new BudgetCalendar(budgetDaysPerWeek, MADAGASCAR, clock);
    }

    private static BudgetCalendar calendarAt(String instant) {
        return calendarAt(5, instant);
    }

    private static LocalDate d(String isoDate) {
        return LocalDate.parse(isoDate);
    }

    // // ---------- Construction ----------

    @Test
    @DisplayName("Refuse un nombre de jours budgétaires hors de 1..7")
    void rejectsInvalidBudgetDaysPerWeek() {
        assertThatThrownBy(() -> calendarAt(0, FRIDAY_OCT_9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calendarAt(8, FRIDAY_OCT_9))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // // ---------- Aujourd'hui et jour métier (fuseau de Madagascar) ----------

    @Test
    @DisplayName("today() : 22:30 UTC le 8 octobre = 01:30 le 9 octobre à Madagascar")
    void todayUsesTheCalendarZone() {
        BudgetCalendar calendar = calendarAt("2026-10-08T22:30:00Z");

        assertThat(calendar.today()).isEqualTo(d("2026-10-09"));
    }

    @Test
    @DisplayName("today() : bascule exacte à minuit de Madagascar (21:00 UTC)")
    void todayChangesAtMadagascarMidnight() {
        assertThat(calendarAt("2026-10-09T20:59:59Z").today()).isEqualTo(d("2026-10-09"));
        assertThat(calendarAt("2026-10-09T21:00:00Z").today()).isEqualTo(d("2026-10-10"));
    }

    @Test
    @DisplayName("businessDay() : une dépense à 00:30 le 1er novembre appartient à novembre")
    void businessDayOfAnInstant() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(calendar.businessDay(Instant.parse("2026-10-31T21:30:00Z")))
                .isEqualTo(d("2026-11-01"));
        assertThat(calendar.businessDay(Instant.parse("2026-10-31T20:59:59Z")))
                .isEqualTo(d("2026-10-31"));
    }

    @Test
    @DisplayName("startOfDay() : le 9 octobre commence le 8 octobre à 21:00 UTC")
    void startOfDayInUtc() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(calendar.startOfDay(d("2026-10-09")))
                .isEqualTo(Instant.parse("2026-10-08T21:00:00Z"));
    }

    // // ---------- Jours budgétaires ----------

    @Test
    @DisplayName("isBudgetDay() avec 5 jours : lundi à vendredi")
    void budgetDaysWithFiveDaysPerWeek() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(calendar.isBudgetDay(d("2026-10-05"))).isTrue();   // lundi
        assertThat(calendar.isBudgetDay(d("2026-10-09"))).isTrue();   // vendredi
        assertThat(calendar.isBudgetDay(d("2026-10-10"))).isFalse();  // samedi
        assertThat(calendar.isBudgetDay(d("2026-10-11"))).isFalse();  // dimanche
    }

    @Test
    @DisplayName("isBudgetDay() avec 7 jours : tous les jours ; avec 1 jour : le lundi seulement")
    void budgetDaysAreConfigurable() {
        assertThat(calendarAt(7, FRIDAY_OCT_9).isBudgetDay(d("2026-10-10"))).isTrue();
        assertThat(calendarAt(1, FRIDAY_OCT_9).isBudgetDay(d("2026-10-06"))).isFalse(); // mardi
        assertThat(calendarAt(1, FRIDAY_OCT_9).isBudgetDay(d("2026-10-05"))).isTrue();  // lundi
    }

    @Test
    @DisplayName("budgetDays(octobre 2026) : 22 jours, du 1er au 30, aucun week-end")
    void budgetDaysOfOctober() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        List<LocalDate> days = calendar.budgetDays(OCT_2026);

        assertThat(days).hasSize(22);
        assertThat(days.getFirst()).isEqualTo(d("2026-10-01"));
        assertThat(days.getLast()).isEqualTo(d("2026-10-30"));
        assertThat(days).doesNotContain(d("2026-10-03"), d("2026-10-04"));
        assertThat(days).allMatch(calendar::isBudgetDay);
    }

    @Test
    @DisplayName("budgetDays(novembre 2026) : 21 jours, du 2 au 30")
    void budgetDaysOfNovember() {
        List<LocalDate> days = calendarAt(FRIDAY_OCT_9).budgetDays(NOV_2026);

        assertThat(days).hasSize(21);
        assertThat(days.getFirst()).isEqualTo(d("2026-11-02")); // le 1er est un dimanche
        assertThat(days.getLast()).isEqualTo(d("2026-11-30"));
    }

    @Test
    @DisplayName("budgetDays() suit le nombre de jours configuré (7 -> 31 jours, 1 -> 4 lundis)")
    void budgetDaysFollowConfiguration() {
        assertThat(calendarAt(7, FRIDAY_OCT_9).budgetDays(OCT_2026)).hasSize(31);
        assertThat(calendarAt(1, FRIDAY_OCT_9).budgetDays(OCT_2026))
                .containsExactly(d("2026-10-05"), d("2026-10-12"), d("2026-10-19"), d("2026-10-26"));
    }

    @Test
    @DisplayName("Les listes renvoyées ne sont pas modifiables")
    void returnedListsAreUnmodifiable() {
        List<LocalDate> days = calendarAt(FRIDAY_OCT_9).budgetDays(OCT_2026);

        assertThatThrownBy(() -> days.add(d("2026-10-31")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- Jours budgétaires restants (aujourd'hui inclus) ----------

    @Test
    @DisplayName("remainingBudgetDays() le vendredi 9 octobre : 16 jours, aujourd'hui inclus")
    void remainingBudgetDaysIncludeToday() {
        List<LocalDate> days = calendarAt(FRIDAY_OCT_9).remainingBudgetDays(OCT_2026);

        assertThat(days).hasSize(16);
        assertThat(days.getFirst()).isEqualTo(d("2026-10-09"));
        assertThat(days.getLast()).isEqualTo(d("2026-10-30"));
    }

    @Test
    @DisplayName("remainingBudgetDays() un samedi : démarre au lundi suivant")
    void remainingBudgetDaysOnASaturday() {
        List<LocalDate> days = calendarAt("2026-10-10T09:00:00Z").remainingBudgetDays(OCT_2026);

        assertThat(days).hasSize(15);
        assertThat(days.getFirst()).isEqualTo(d("2026-10-12"));
    }

    @Test
    @DisplayName("remainingBudgetDays() : mois passé = vide, mois futur = tous ses jours budgétaires")
    void remainingBudgetDaysForPastAndFutureMonths() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(calendar.remainingBudgetDays(YearMonth.of(2026, 9))).isEmpty();
        assertThat(calendar.remainingBudgetDays(NOV_2026)).hasSize(21);
    }

    // ---------- Jours budgétaires passés (alerte « fixes non enregistrées ») ----------

    @Test
    @DisplayName("pastBudgetDays() : jours budgétaires avant aujourd'hui, aujourd'hui exclu")
    void pastBudgetDaysExcludeToday() {
        List<LocalDate> days = calendarAt(FRIDAY_OCT_9).pastBudgetDays(OCT_2026, d("2026-10-01"));

        assertThat(days).containsExactly(
                d("2026-10-01"), d("2026-10-02"), d("2026-10-05"),
                d("2026-10-06"), d("2026-10-07"), d("2026-10-08"));
    }

    @Test
    @DisplayName("pastBudgetDays() : ignore les jours antérieurs à la création de la règle")
    void pastBudgetDaysStartAtRuleCreation() {
        List<LocalDate> days = calendarAt(FRIDAY_OCT_9).pastBudgetDays(OCT_2026, d("2026-10-06"));

        assertThat(days).containsExactly(d("2026-10-06"), d("2026-10-07"), d("2026-10-08"));
    }

    @Test
    @DisplayName("pastBudgetDays() : un mois futur n'a aucun jour passé")
    void pastBudgetDaysOfFutureMonthIsEmpty() {
        assertThat(calendarAt(FRIDAY_OCT_9).pastBudgetDays(NOV_2026, d("2026-01-01"))).isEmpty();
    }

    // ---------- Semaines ISO (lundi -> dimanche) ----------

    @Test
    @DisplayName("weekStart() et weekEnd() : lundi et dimanche de la semaine")
    void weekBoundaries() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(calendar.weekStart(d("2026-10-07"))).isEqualTo(d("2026-10-05")); // mercredi
        assertThat(calendar.weekEnd(d("2026-10-07"))).isEqualTo(d("2026-10-11"));
        assertThat(calendar.weekStart(d("2026-10-11"))).isEqualTo(d("2026-10-05")); // dimanche
        assertThat(calendar.weekStart(d("2026-10-12"))).isEqualTo(d("2026-10-12")); // lundi
    }

    @Test
    @DisplayName("monthOfWeek() : une semaine appartient au mois de son lundi")
    void weekBelongsToTheMonthOfItsMonday() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(calendar.monthOfWeek(d("2026-10-05"))).isEqualTo(OCT_2026);
        // Dimanche 1er novembre : sa semaine commence le lundi 26 octobre
        assertThat(calendar.monthOfWeek(d("2026-11-01"))).isEqualTo(OCT_2026);
        // Mercredi 2 décembre : sa semaine commence le lundi 30 novembre
        assertThat(calendar.monthOfWeek(d("2026-12-02"))).isEqualTo(NOV_2026);
        // Jeudi 1er octobre : sa semaine commence le lundi 28 septembre
        assertThat(calendar.monthOfWeek(d("2026-10-01"))).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    @DisplayName("weekStartsOf() : les lundis du mois (4 en octobre, 5 en novembre)")
    void weekStartsOfAMonth() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(calendar.weekStartsOf(OCT_2026)).containsExactly(
                d("2026-10-05"), d("2026-10-12"), d("2026-10-19"), d("2026-10-26"));
        assertThat(calendar.weekStartsOf(NOV_2026)).containsExactly(
                d("2026-11-02"), d("2026-11-09"), d("2026-11-16"),
                d("2026-11-23"), d("2026-11-30"));
    }

    // ---------- Semaines restantes : tant que le dimanche n'est pas passé ----------

    @Test
    @DisplayName("remainingWeekStarts() : la semaine en cours compte jusqu'à son dimanche inclus")
    void currentWeekCountsUntilItsSunday() {
        assertThat(calendarAt(FRIDAY_OCT_9).remainingWeekStarts(OCT_2026)).hasSize(4);
        assertThat(calendarAt("2026-10-11T09:00:00Z").remainingWeekStarts(OCT_2026)).hasSize(4);
    }

    @Test
    @DisplayName("remainingWeekStarts() : le lundi, la semaine précédente n'est plus comptée")
    void previousWeekIsDroppedOnMonday() {
        assertThat(calendarAt("2026-10-12T09:00:00Z").remainingWeekStarts(OCT_2026))
                .containsExactly(d("2026-10-12"), d("2026-10-19"), d("2026-10-26"));
    }

    @Test
    @DisplayName("remainingWeekStarts() : la dernière semaine d'octobre se termine en novembre")
    void lastWeekOfOctoberEndsInNovember() {
        // Dimanche 1er novembre : la semaine du 26 octobre est encore en cours
        assertThat(calendarAt("2026-11-01T09:00:00Z").remainingWeekStarts(OCT_2026))
                .containsExactly(d("2026-10-26"));
        // Lundi 2 novembre : elle est terminée
        assertThat(calendarAt("2026-11-02T09:00:00Z").remainingWeekStarts(OCT_2026)).isEmpty();
    }

    @Test
    @DisplayName("remainingWeekStarts() : un mois futur compte toutes ses semaines")
    void futureMonthKeepsAllItsWeeks() {
        assertThat(calendarAt("2026-11-01T09:00:00Z").remainingWeekStarts(NOV_2026)).hasSize(5);
    }
}