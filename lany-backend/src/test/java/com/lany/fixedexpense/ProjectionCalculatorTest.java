package com.lany.fixedexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lany.shared.calendar.BudgetCalendar;
import com.lany.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests du calculateur de projection.
 * Emplacement : backend/src/test/java/com/lany/fixedexpense/ProjectionCalculatorTest.java
 * Classes testées : ProjectionCalculator, ProjectedRule, SimulationResult.
 *
 * Trois règles de l'exemple du guide, au vendredi 9 octobre 2026 :
 *   Riz      2 000 Ar / jour        -> 16 jours restants     = 32 000
 *   Légumes 10 000 Ar / semaine     -> 4 semaines restantes  = 40 000
 *   Loyer  200 000 Ar / mois        -> 1 mois restant        = 200 000
 */
class ProjectionCalculatorTest {

    private static final ZoneId MADAGASCAR = ZoneId.of("Indian/Antananarivo");
    private static final String FRIDAY_OCT_9 = "2026-10-09T09:00:00Z";

    private static final YearMonth SEP_2026 = YearMonth.of(2026, 9);
    private static final YearMonth OCT_2026 = YearMonth.of(2026, 10);
    private static final YearMonth NOV_2026 = YearMonth.of(2026, 11);

    private final ProjectionCalculator calculator = new ProjectionCalculator();

    // ---------- Outils de test ----------

    private static BudgetCalendar calendarAt(int budgetDaysPerWeek, String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new BudgetCalendar(budgetDaysPerWeek, MADAGASCAR, clock);
    }

    private static BudgetCalendar calendarAt(String instant) {
        return calendarAt(5, instant);
    }

    private static Money money(String amount) {
        return Money.of(amount);
    }

    private static LocalDate d(String isoDate) {
        return LocalDate.parse(isoDate);
    }

    private static Set<LocalDate> dates(String... isoDates) {
        return Arrays.stream(isoDates).map(LocalDate::parse).collect(Collectors.toSet());
    }

    private static ProjectedRule rice(String... coveredStarts) {
        return new ProjectedRule(money("2000"), Frequency.DAILY, dates(coveredStarts));
    }

    private static ProjectedRule vegetables(String... coveredStarts) {
        return new ProjectedRule(money("10000"), Frequency.WEEKLY, dates(coveredStarts));
    }

    private static ProjectedRule rent(String... coveredStarts) {
        return new ProjectedRule(money("200000"), Frequency.MONTHLY, dates(coveredStarts));
    }

    // =====================================================================
    // 1. Une règle seule
    // =====================================================================

    @Test
    @DisplayName("Riz journalier le 9 octobre : 16 jours x 2 000 = 32 000")
    void dailyRuleProjection() {
        assertThat(calculator.remainingProjection(rice(), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("32000"));
    }

    @Test
    @DisplayName("Légumes hebdomadaires : 4 semaines x 10 000 = 40 000 (la semaine en cours compte)")
    void weeklyRuleProjection() {
        assertThat(calculator.remainingProjection(vegetables(), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("40000"));
    }

    @Test
    @DisplayName("Loyer mensuel : 1 mois x 200 000 = 200 000")
    void monthlyRuleProjection() {
        assertThat(calculator.remainingProjection(rent(), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("200000"));
    }

    @Test
    @DisplayName("Un montant décimal est multiplié sans arrondi intermédiaire")
    void decimalAmount() {
        ProjectedRule rule = new ProjectedRule(money("1000.50"), Frequency.DAILY, Set.of());

        Money projection = calculator.remainingProjection(rule, OCT_2026, calendarAt(FRIDAY_OCT_9));

        assertThat(projection.toApiString()).isEqualTo("16008.00");
    }

    @Test
    @DisplayName("Foyer à 7 jours par semaine : le Riz compte 23 jours du 9 au 31 octobre")
    void sevenBudgetDaysPerWeek() {
        assertThat(calculator.remainingProjection(rice(), OCT_2026, calendarAt(7, FRIDAY_OCT_9)))
                .isEqualTo(money("46000"));
    }

    // =====================================================================
    // 2. Périodes déjà couvertes : pas de double comptage
    // =====================================================================

    @Test
    @DisplayName("Riz réalisé pour aujourd'hui : il reste 15 jours = 30 000")
    void todayCoveredIsNotProjectedAgain() {
        assertThat(calculator.remainingProjection(rice("2026-10-09"), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("30000"));
    }

    @Test
    @DisplayName("Un jour réalisé à l'avance (le 14) est déduit")
    void daysRealizedInAdvanceAreDeducted() {
        assertThat(calculator.remainingProjection(rice("2026-10-14"), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("30000"));
    }

    @Test
    @DisplayName("Un jour passé réalisé ne change rien : il n'était déjà plus dans la prévision")
    void coveredPastDayChangesNothing() {
        assertThat(calculator.remainingProjection(rice("2026-10-08"), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("32000"));
    }

    @Test
    @DisplayName("Une date qui n'est pas une période restante (samedi) est ignorée")
    void coveredDateOutsideBudgetDaysIsIgnored() {
        assertThat(calculator.remainingProjection(rice("2026-10-10"), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("32000"));
    }

    @Test
    @DisplayName("uncoveredPeriods() : liste exacte des périodes restantes non couvertes")
    void uncoveredPeriodsList() {
        List<LocalDate> periods = calculator.uncoveredPeriods(
                rice("2026-10-09", "2026-10-12"), OCT_2026, calendarAt(FRIDAY_OCT_9));

        assertThat(periods).hasSize(14);
        assertThat(periods.getFirst()).isEqualTo(d("2026-10-13"));
    }

    @Test
    @DisplayName("Le lundi 12, la semaine du 5 n'est plus projetée : 3 semaines = 30 000")
    void weeklyRuleOnMonday() {
        assertThat(calculator.remainingProjection(vegetables(), OCT_2026, calendarAt("2026-10-12T09:00:00Z")))
                .isEqualTo(money("30000"));
    }

    @Test
    @DisplayName("Semaine du 12 réalisée : il reste 2 semaines = 20 000")
    void weeklyRuleWithCurrentWeekCovered() {
        assertThat(calculator.remainingProjection(
                        vegetables("2026-10-12"), OCT_2026, calendarAt("2026-10-12T09:00:00Z")))
                .isEqualTo(money("20000"));
    }

    @Test
    @DisplayName("Semaine à cheval : le dimanche 1er novembre, celle du 26 octobre compte encore pour octobre")
    void straddlingWeek() {
        BudgetCalendar sundayNov1 = calendarAt("2026-11-01T09:00:00Z");

        assertThat(calculator.remainingProjection(vegetables(), OCT_2026, sundayNov1))
                .isEqualTo(money("10000"));
        assertThat(calculator.remainingProjection(vegetables("2026-10-26"), OCT_2026, sundayNov1))
                .isEqualTo(money("0"));
    }

    @Test
    @DisplayName("Loyer d'octobre vu le 1er novembre : plus rien à prévoir")
    void monthlyRuleOnceTheMonthIsOver() {
        assertThat(calculator.remainingProjection(rent(), OCT_2026, calendarAt("2026-11-01T09:00:00Z")))
                .isEqualTo(money("0"));
    }

    @Test
    @DisplayName("Loyer réalisé : il ne reste rien à prévoir")
    void monthlyRuleCovered() {
        assertThat(calculator.remainingProjection(rent("2026-10-01"), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("0"));
    }

    // =====================================================================
    // 3. Autres mois
    // =====================================================================

    @Test
    @DisplayName("Mois futur (novembre) : tout est à prévoir, dont 5 semaines")
    void futureMonth() {
        BudgetCalendar calendar = calendarAt(FRIDAY_OCT_9);

        assertThat(calculator.remainingProjection(rice(), NOV_2026, calendar)).isEqualTo(money("42000"));
        assertThat(calculator.remainingProjection(vegetables(), NOV_2026, calendar)).isEqualTo(money("50000"));
        assertThat(calculator.remainingProjection(rent(), NOV_2026, calendar)).isEqualTo(money("200000"));
    }

    @Test
    @DisplayName("Mois passé (septembre) : aucune prévision")
    void pastMonth() {
        assertThat(calculator.totalRemainingProjection(
                        List.of(rice(), vegetables(), rent()), SEP_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("0"));
    }

    // =====================================================================
    // 4. Total et simulation : le scénario complet du guide
    // =====================================================================

    @Test
    @DisplayName("Total des prévisions de novembre : 42 000 + 50 000 + 200 000 = 292 000")
    void totalForNovember() {
        assertThat(calculator.totalRemainingProjection(
                        List.of(rice(), vegetables(), rent()), NOV_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(money("292000"));
    }

    @Test
    @DisplayName("Aucune règle : prévision nulle")
    void noRules() {
        assertThat(calculator.totalRemainingProjection(List.of(), OCT_2026, calendarAt(FRIDAY_OCT_9)))
                .isEqualTo(Money.zero());
    }

    @Test
    @DisplayName("Étape 1 : rien de réalisé. Reste réel 300 000, prévision 272 000, simulé 28 000")
    void scenarioStep1() {
        SimulationResult result = calculator.simulate(
                money("300000"), List.of(rice(), vegetables(), rent()), OCT_2026, calendarAt(FRIDAY_OCT_9));

        assertThat(result.realBalance()).isEqualTo(money("300000"));
        assertThat(result.projectedFixed()).isEqualTo(money("272000"));
        assertThat(result.simulatedBalance()).isEqualTo(money("28000"));
    }

    @Test
    @DisplayName("Étape 2 : Riz réalisé (2 000). Le reste simulé ne bouge pas")
    void scenarioStep2() {
        SimulationResult result = calculator.simulate(
                money("298000"), List.of(rice("2026-10-09"), vegetables(), rent()),
                OCT_2026, calendarAt(FRIDAY_OCT_9));

        assertThat(result.projectedFixed()).isEqualTo(money("270000"));
        assertThat(result.simulatedBalance()).isEqualTo(money("28000"));
    }

    @Test
    @DisplayName("Étape 3 : Légumes réalisés (10 000). Le reste simulé ne bouge pas")
    void scenarioStep3() {
        SimulationResult result = calculator.simulate(
                money("288000"), List.of(rice("2026-10-09"), vegetables("2026-10-05"), rent()),
                OCT_2026, calendarAt(FRIDAY_OCT_9));

        assertThat(result.projectedFixed()).isEqualTo(money("260000"));
        assertThat(result.simulatedBalance()).isEqualTo(money("28000"));
    }

    @Test
    @DisplayName("Étape 4 : Loyer réalisé (200 000). Le reste simulé ne bouge toujours pas")
    void scenarioStep4() {
        SimulationResult result = calculator.simulate(
                money("88000"),
                List.of(rice("2026-10-09"), vegetables("2026-10-05"), rent("2026-10-01")),
                OCT_2026, calendarAt(FRIDAY_OCT_9));

        assertThat(result.projectedFixed()).isEqualTo(money("60000"));
        assertThat(result.simulatedBalance()).isEqualTo(money("28000"));
    }

    @Test
    @DisplayName("Étape 5 : cigarettes (8 000, dépense variable) : le reste simulé baisse à 20 000")
    void scenarioStep5() {
        SimulationResult result = calculator.simulate(
                money("80000"),
                List.of(rice("2026-10-09"), vegetables("2026-10-05"), rent("2026-10-01")),
                OCT_2026, calendarAt(FRIDAY_OCT_9));

        assertThat(result.simulatedBalance()).isEqualTo(money("20000"));
    }

    @Test
    @DisplayName("Un déficit prévisible donne un reste simulé négatif")
    void negativeSimulatedBalance() {
        SimulationResult result = calculator.simulate(
                money("100000"), List.of(rice(), vegetables(), rent()), OCT_2026, calendarAt(FRIDAY_OCT_9));

        assertThat(result.simulatedBalance().isNegative()).isTrue();
        assertThat(result.simulatedBalance().toApiString()).isEqualTo("-172000.00");
    }

    // =====================================================================
    // 5. ProjectedRule : un objet toujours valide et immuable
    // =====================================================================

    @Test
    @DisplayName("ProjectedRule garde une copie : modifier le Set d'origine ne la change pas")
    void projectedRuleMakesADefensiveCopy() {
        Set<LocalDate> original = new HashSet<>(Set.of(d("2026-10-09")));
        ProjectedRule rule = new ProjectedRule(money("2000"), Frequency.DAILY, original);

        original.add(d("2026-10-12"));

        assertThat(rule.coveredPeriodStarts()).containsExactly(d("2026-10-09"));
    }

    @Test
    @DisplayName("Les dates couvertes d'une ProjectedRule ne sont pas modifiables")
    void coveredStartsAreUnmodifiable() {
        ProjectedRule rule = rice("2026-10-09");

        assertThatThrownBy(() -> rule.coveredPeriodStarts().add(d("2026-10-13")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("ProjectedRule refuse null")
    void projectedRuleRejectsNull() {
        assertThatThrownBy(() -> new ProjectedRule(null, Frequency.DAILY, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProjectedRule(money("1"), null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProjectedRule(money("1"), Frequency.DAILY, null))
                .isInstanceOf(NullPointerException.class);
    }
}