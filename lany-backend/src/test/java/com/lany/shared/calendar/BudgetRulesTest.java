package com.lany.shared.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spécification exécutable de BudgetRules.
 * Emplacement : backend/src/test/java/com/lany/shared/calendar/BudgetRulesTest.java
 * Classe à écrire : backend/src/main/java/com/lany/shared/calendar/BudgetRules.java
 *
 * BudgetRules regroupe les deux constantes du foyer (household.days_per_week et
 * household.weeks_per_month). Les bornes reprennent les CHECK de V1 : 1..7 et 1..5.
 */
class BudgetRulesTest {

    @Test
    @DisplayName("Valeurs par défaut : 5 jours par semaine, 4 semaines par mois")
    void defaults() {
        assertThat(BudgetRules.defaults()).isEqualTo(new BudgetRules(5, 4));
    }

    @Test
    @DisplayName("Expose ses deux valeurs")
    void exposesItsValues() {
        BudgetRules rules = new BudgetRules(6, 3);

        assertThat(rules.daysPerWeek()).isEqualTo(6);
        assertThat(rules.weeksPerMonth()).isEqualTo(3);
    }

    @Test
    @DisplayName("daysPerMonth() = jours par semaine x semaines par mois")
    void computesDaysPerMonth() {
        assertThat(BudgetRules.defaults().daysPerMonth()).isEqualTo(20);
        assertThat(new BudgetRules(6, 4).daysPerMonth()).isEqualTo(24);
    }

    @Test
    @DisplayName("Refuse un nombre de jours par semaine hors de 1..7")
    void rejectsInvalidDaysPerWeek() {
        assertThatThrownBy(() -> new BudgetRules(0, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BudgetRules(8, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Refuse un nombre de semaines par mois hors de 1..5")
    void rejectsInvalidWeeksPerMonth() {
        assertThatThrownBy(() -> new BudgetRules(5, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BudgetRules(5, 6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Accepte les bornes exactes")
    void acceptsBoundaries() {
        assertThat(new BudgetRules(1, 1).daysPerMonth()).isEqualTo(1);
        assertThat(new BudgetRules(7, 5).daysPerMonth()).isEqualTo(35);
    }
}