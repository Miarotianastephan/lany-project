package com.lany.fixedexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lany.shared.calendar.BudgetCalendar;
import com.lany.shared.calendar.BudgetRules;
import com.lany.shared.money.Money;
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
 * Spécification exécutable de Frequency et CoveredPeriod.
 * Emplacement : backend/src/test/java/com/lany/fixedexpense/FrequencyTest.java
 * Classes à écrire (dans backend/src/main/java/com/lany/...) :
 *   - fixedexpense/Frequency.java
 *   - fixedexpense/CoveredPeriod.java
 *   - shared/calendar/BudgetRules.java   (voir BudgetRulesTest)
 *
 * Deux comportements distincts, à ne pas mélanger :
 *   1. ÉQUIVALENCE budgétaire : convert(...) et budgetDays(...) utilisent BudgetRules (5 x 4 par défaut).
 *   2. PROJECTION : remainingPeriodStarts(...) et periodOf(...) utilisent le calendrier réel.
 *
 * Calendrier de référence : octobre 2026 (Oct 1 = jeudi, Oct 9 = vendredi, lundis : 5, 12, 19, 26).
 */
class FrequencyTest {

    private static final ZoneId MADAGASCAR = ZoneId.of("Indian/Antananarivo");
    private static final String FRIDAY_OCT_9 = "2026-10-09T09:00:00Z";

    private static final YearMonth SEP_2026 = YearMonth.of(2026, 9);
    private static final YearMonth OCT_2026 = YearMonth.of(2026, 10);
    private static final YearMonth NOV_2026 = YearMonth.of(2026, 11);

    private static final BudgetRules DEFAULT_RULES = BudgetRules.defaults(); // 5 jours x 4 semaines

    private static BudgetCalendar calendarAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new BudgetCalendar(5, MADAGASCAR, clock);
    }

    private static LocalDate d(String isoDate) {
        return LocalDate.parse(isoDate);
    }

    private static Money money(String amount) {
        return Money.of(amount);
    }

    // =====================================================================
    // 1. ÉQUIVALENCE BUDGÉTAIRE
    // =====================================================================

    @Test
    @DisplayName("budgetDays() : jours budgétaires que représente une période (1 / 5 / 20 par défaut)")
    void budgetDaysWithDefaultRules() {
        assertThat(Frequency.DAILY.budgetDays(DEFAULT_RULES)).isEqualTo(1);
        assertThat(Frequency.WEEKLY.budgetDays(DEFAULT_RULES)).isEqualTo(5);
        assertThat(Frequency.MONTHLY.budgetDays(DEFAULT_RULES)).isEqualTo(20);
    }

    @Test
    @DisplayName("budgetDays() suit la configuration du foyer")
    void budgetDaysFollowTheHouseholdRules() {
        assertThat(Frequency.WEEKLY.budgetDays(new BudgetRules(6, 4))).isEqualTo(6);
        assertThat(Frequency.MONTHLY.budgetDays(new BudgetRules(6, 4))).isEqualTo(24);
        assertThat(Frequency.MONTHLY.budgetDays(new BudgetRules(7, 5))).isEqualTo(35);
    }

    @Test
    @DisplayName("Journalière : 2 000 Ar/jour = 10 000 Ar/semaine = 40 000 Ar/mois")
    void convertsFromDaily() {
        Money daily = money("2000");

        assertThat(Frequency.DAILY.convert(daily, Frequency.WEEKLY, DEFAULT_RULES))
                .isEqualTo(money("10000"));
        assertThat(Frequency.DAILY.convert(daily, Frequency.MONTHLY, DEFAULT_RULES))
                .isEqualTo(money("40000"));
    }

    @Test
    @DisplayName("Hebdomadaire : 10 000 Ar/semaine = 2 000 Ar/jour = 40 000 Ar/mois")
    void convertsFromWeekly() {
        Money weekly = money("10000");

        assertThat(Frequency.WEEKLY.convert(weekly, Frequency.DAILY, DEFAULT_RULES))
                .isEqualTo(money("2000"));
        assertThat(Frequency.WEEKLY.convert(weekly, Frequency.MONTHLY, DEFAULT_RULES))
                .isEqualTo(money("40000"));
    }

    @Test
    @DisplayName("Mensuelle : 200 000 Ar/mois = 10 000 Ar/jour = 50 000 Ar/semaine")
    void convertsFromMonthly() {
        Money monthly = money("200000");

        assertThat(Frequency.MONTHLY.convert(monthly, Frequency.DAILY, DEFAULT_RULES))
                .isEqualTo(money("10000"));
        assertThat(Frequency.MONTHLY.convert(monthly, Frequency.WEEKLY, DEFAULT_RULES))
                .isEqualTo(money("50000"));
    }

    @Test
    @DisplayName("Convertir vers sa propre fréquence ne change rien")
    void convertingToTheSameFrequencyKeepsTheAmount() {
        assertThat(Frequency.MONTHLY.convert(money("123.45"), Frequency.MONTHLY, DEFAULT_RULES))
                .isEqualTo(money("123.45"));
        assertThat(Frequency.DAILY.convert(money("77"), Frequency.DAILY, DEFAULT_RULES))
                .isEqualTo(money("77"));
    }

    @Test
    @DisplayName("Les constantes du foyer sont respectées (6 jours x 4 semaines)")
    void conversionUsesTheHouseholdRules() {
        BudgetRules sixDays = new BudgetRules(6, 4);

        assertThat(Frequency.DAILY.convert(money("2000"), Frequency.MONTHLY, sixDays))
                .isEqualTo(money("48000"));
        assertThat(Frequency.WEEKLY.convert(money("12000"), Frequency.DAILY, sixDays))
                .isEqualTo(money("2000"));
        assertThat(Frequency.WEEKLY.convert(money("10000"), Frequency.MONTHLY, new BudgetRules(7, 5)))
                .isEqualTo(money("50000"));
    }

    @Test
    @DisplayName("Division non entière : 10 000 Ar/mois sur 3 jours -> 3 333.33 à l'affichage")
    void nonExactConversion() {
        BudgetRules threeDaysOneWeek = new BudgetRules(3, 1);

        Money daily = Frequency.MONTHLY.convert(money("10000"), Frequency.DAILY, threeDaysOneWeek);

        assertThat(daily.toApiString()).isEqualTo("3333.33");
    }

    @Test
    @DisplayName("Aucune perte : mensuel -> journalier -> mensuel redonne 10 000.00")
    void conversionKeepsFullPrecision() {
        BudgetRules threeDaysOneWeek = new BudgetRules(3, 1);

        Money daily = Frequency.MONTHLY.convert(money("10000"), Frequency.DAILY, threeDaysOneWeek);
        Money backToMonthly = Frequency.DAILY.convert(daily, Frequency.MONTHLY, threeDaysOneWeek);

        assertThat(backToMonthly.toApiString()).isEqualTo("10000.00");
    }

    // =====================================================================
    // 2. PROJECTION : périodes restantes (calendrier réel)
    // =====================================================================

    @Test
    @DisplayName("DAILY : un jour budgétaire restant par jour, aujourd'hui inclus (16 au 9 octobre)")
    void remainingDailyPeriods() {
        List<LocalDate> periods =
                Frequency.DAILY.remainingPeriodStarts(calendarAt(FRIDAY_OCT_9), OCT_2026);

        assertThat(periods).hasSize(16);
        assertThat(periods.getFirst()).isEqualTo(d("2026-10-09"));
        assertThat(periods.getLast()).isEqualTo(d("2026-10-30"));
    }

    @Test
    @DisplayName("DAILY un samedi : la première période est le lundi suivant")
    void remainingDailyPeriodsOnASaturday() {
        List<LocalDate> periods =
                Frequency.DAILY.remainingPeriodStarts(calendarAt("2026-10-10T09:00:00Z"), OCT_2026);

        assertThat(periods.getFirst()).isEqualTo(d("2026-10-12"));
    }

    @Test
    @DisplayName("WEEKLY : une période par lundi, la semaine en cours comptant jusqu'à son dimanche")
    void remainingWeeklyPeriods() {
        assertThat(Frequency.WEEKLY.remainingPeriodStarts(calendarAt(FRIDAY_OCT_9), OCT_2026))
                .containsExactly(d("2026-10-05"), d("2026-10-12"), d("2026-10-19"), d("2026-10-26"));
    }

    @Test
    @DisplayName("WEEKLY le lundi 12 : la semaine du 5 n'est plus comptée")
    void remainingWeeklyPeriodsOnMonday() {
        assertThat(Frequency.WEEKLY.remainingPeriodStarts(calendarAt("2026-10-12T09:00:00Z"), OCT_2026))
                .containsExactly(d("2026-10-12"), d("2026-10-19"), d("2026-10-26"));
    }

    @Test
    @DisplayName("MONTHLY : le mois entier est une seule période, tant que le mois n'est pas terminé")
    void remainingMonthlyPeriod() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(Frequency.MONTHLY.remainingPeriodStarts(calendar, OCT_2026))
                .containsExactly(d("2026-10-01"));
    }

    @Test
    @DisplayName("MONTHLY : mois passé = aucune période, mois futur = une période")
    void remainingMonthlyPeriodForPastAndFutureMonths() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(Frequency.MONTHLY.remainingPeriodStarts(calendar, SEP_2026)).isEmpty();
        assertThat(Frequency.MONTHLY.remainingPeriodStarts(calendar, NOV_2026))
                .containsExactly(d("2026-11-01"));
    }

    @Test
    @DisplayName("MONTHLY : encore comptée le dernier jour du mois, plus le lendemain")
    void monthlyPeriodEndsWithTheMonth() {
        assertThat(Frequency.MONTHLY.remainingPeriodStarts(calendarAt("2026-10-31T09:00:00Z"), OCT_2026))
                .containsExactly(d("2026-10-01"));
        assertThat(Frequency.MONTHLY.remainingPeriodStarts(calendarAt("2026-11-01T09:00:00Z"), OCT_2026))
                .isEmpty();
    }

    // =====================================================================
    // 3. PROJECTION : période couverte par une réalisation
    // =====================================================================

    @Test
    @DisplayName("DAILY : la période couverte est le jour lui-même")
    void dailyPeriod() {
        assertThat(Frequency.DAILY.periodOf(d("2026-10-09"), calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(new CoveredPeriod(d("2026-10-09"), d("2026-10-09")));
    }

    @Test
    @DisplayName("WEEKLY : lundi -> dimanche, quelle que soit la date de la semaine")
    void weeklyPeriod() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);
        CoveredPeriod week = new CoveredPeriod(d("2026-10-05"), d("2026-10-11"));

        assertThat(Frequency.WEEKLY.periodOf(d("2026-10-05"), calendar)).isEqualTo(week);
        assertThat(Frequency.WEEKLY.periodOf(d("2026-10-07"), calendar)).isEqualTo(week);
    }

    @Test
    @DisplayName("WEEKLY : une semaine à cheval sur deux mois est stockée telle quelle")
    void weeklyPeriodStraddlingTwoMonths() {
        assertThat(Frequency.WEEKLY.periodOf(d("2026-10-26"), calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(new CoveredPeriod(d("2026-10-26"), d("2026-11-01")));
    }

    @Test
    @DisplayName("MONTHLY : du 1er au dernier jour du mois (février bissextile compris)")
    void monthlyPeriod() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(Frequency.MONTHLY.periodOf(d("2026-10-09"), calendar))
                .isEqualTo(new CoveredPeriod(d("2026-10-01"), d("2026-10-31")));
        assertThat(Frequency.MONTHLY.periodOf(d("2027-02-10"), calendar))
                .isEqualTo(new CoveredPeriod(d("2027-02-01"), d("2027-02-28")));
        assertThat(Frequency.MONTHLY.periodOf(d("2028-02-10"), calendar))
                .isEqualTo(new CoveredPeriod(d("2028-02-01"), d("2028-02-29")));
    }

    @Test
    @DisplayName("CoveredPeriod refuse un début postérieur à la fin (comme la contrainte SQL)")
    void coveredPeriodRejectsInvertedBounds() {
        assertThatThrownBy(() -> new CoveredPeriod(d("2026-10-10"), d("2026-10-09")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =====================================================================
    // 4. Correspondance avec la base de données
    // =====================================================================

    @Test
    @DisplayName("dbValue() : les valeurs exactes de la contrainte CHECK de V1")
    void databaseValues() {
        assertThat(Frequency.DAILY.dbValue()).isEqualTo("daily");
        assertThat(Frequency.WEEKLY.dbValue()).isEqualTo("weekly");
        assertThat(Frequency.MONTHLY.dbValue()).isEqualTo("monthly");
    }

    @Test
    @DisplayName("fromDbValue() retrouve la fréquence depuis la valeur en base")
    void readsFromDatabaseValue() {
        assertThat(Frequency.fromDbValue("weekly")).isEqualTo(Frequency.WEEKLY);
    }

    @Test
    @DisplayName("fromDbValue() refuse une valeur inconnue, y compris la casse du nom Java")
    void rejectsUnknownDatabaseValue() {
        assertThatThrownBy(() -> Frequency.fromDbValue("yearly"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Frequency.fromDbValue("DAILY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Frequency.fromDbValue(null))
                .isInstanceOf(NullPointerException.class);
    }
}