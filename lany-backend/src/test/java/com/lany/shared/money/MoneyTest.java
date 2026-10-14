package com.lany.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spécification exécutable de Money.
 * Emplacement : backend/src/test/java/com/lany/shared/money/MoneyTest.java
 * Classe à écrire : backend/src/main/java/com/lany/shared/money/Money.java
 */
class MoneyTest {

    // ---------- Création ----------

    @Test
    @DisplayName("Crée un montant depuis une chaîne canonique")
    void createsFromString() {
        assertThat(Money.of("12000.50").toApiString()).isEqualTo("12000.50");
    }

    @Test
    @DisplayName("Crée un montant depuis un BigDecimal")
    void createsFromBigDecimal() {
        assertThat(Money.of(new BigDecimal("5")).toApiString()).isEqualTo("5.00");
    }

    @Test
    @DisplayName("Refuse une chaîne qui n'est pas un nombre")
    void rejectsInvalidString() {
        assertThatThrownBy(() -> Money.of("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Refuse la virgule décimale : le frontend normalise la saisie avant l'envoi")
    void rejectsCommaDecimal() {
        assertThatThrownBy(() -> Money.of("12,5")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Refuse null")
    void rejectsNull() {
        assertThatThrownBy(() -> Money.of((String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of((BigDecimal) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("zero() vaut 0.00")
    void zeroIsZero() {
        assertThat(Money.zero().isZero()).isTrue();
        assertThat(Money.zero().toApiString()).isEqualTo("0.00");
    }

    // ---------- Égalité ----------

    @Test
    @DisplayName("2.0 et 2.00 sont le même montant (BigDecimal.equals les distinguerait)")
    void equalsIgnoresScale() {
        assertThat(Money.of("2.0")).isEqualTo(Money.of("2.00"));
        assertThat(Money.of("2.0").hashCode()).isEqualTo(Money.of("2.00").hashCode());
    }

    @Test
    @DisplayName("Deux montants différents ne sont pas égaux")
    void differentAmountsAreNotEqual() {
        assertThat(Money.of("2")).isNotEqualTo(Money.of("3"));
    }

    @Test
    @DisplayName("compareTo ignore aussi l'échelle")
    void compareToIgnoresScale() {
        assertThat(Money.of("2.0").compareTo(Money.of("2.00"))).isZero();
        assertThat(Money.of("1").compareTo(Money.of("2"))).isNegative();
        assertThat(Money.of("3").compareTo(Money.of("2"))).isPositive();
    }

    // ---------- Arithmétique ----------

    @Test
    @DisplayName("Addition")
    void adds() {
        assertThat(Money.of("1000.25").plus(Money.of("0.75"))).isEqualTo(Money.of("1001"));
    }

    @Test
    @DisplayName("Soustraction : le résultat peut être négatif (déficit)")
    void subtractsToNegative() {
        Money result = Money.of("100").minus(Money.of("250"));

        assertThat(result.isNegative()).isTrue();
        assertThat(result.toApiString()).isEqualTo("-150.00");
    }

    @Test
    @DisplayName("Multiplication par un entier : 2 000 Ar x 20 jours")
    void multipliesByCount() {
        assertThat(Money.of("2000").times(20)).isEqualTo(Money.of("40000"));
    }

    @Test
    @DisplayName("Division exacte : 200 000 Ar / 20 jours")
    void dividesExactly() {
        assertThat(Money.of("200000").dividedBy(20)).isEqualTo(Money.of("10000"));
    }

    @Test
    @DisplayName("Division par zéro refusée avec un message clair")
    void rejectsDivisionByZero() {
        assertThatThrownBy(() -> Money.of("100").dividedBy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Money est immuable : une opération ne modifie pas l'original")
    void isImmutable() {
        Money original = Money.of("5");

        original.plus(Money.of("1"));

        assertThat(original).isEqualTo(Money.of("5"));
    }

    // ---------- Arrondi ----------

    @Test
    @DisplayName("HALF_UP : 2.345 -> 2.35")
    void roundsHalfUp() {
        assertThat(Money.of("2.345").toApiString()).isEqualTo("2.35");
    }

    @Test
    @DisplayName("2.344 -> 2.34")
    void roundsDown() {
        assertThat(Money.of("2.344").toApiString()).isEqualTo("2.34");
    }

    @Test
    @DisplayName("HALF_UP sur un négatif : -2.345 -> -2.35 (on s'éloigne de zéro)")
    void roundsNegativeTieAwayFromZero() {
        assertThat(Money.of("-2.345").toApiString()).isEqualTo("-2.35");
    }

    @Test
    @DisplayName("Complète toujours à 2 décimales")
    void padsToTwoDecimals() {
        assertThat(Money.of("12000").toApiString()).isEqualTo("12000.00");
    }

    @Test
    @DisplayName("Le format API n'utilise jamais la notation scientifique")
    void neverUsesScientificNotation() {
        assertThat(Money.of("20000").toApiString()).isEqualTo("20000.00");
        assertThat(Money.of("1E+4").toApiString()).isEqualTo("10000.00");
    }

    @Test
    @DisplayName("rounded() renvoie un Money arrondi à 2 décimales")
    void roundedReturnsRoundedMoney() {
        assertThat(Money.of("10").dividedBy(3).rounded()).isEqualTo(Money.of("3.33"));
    }

    @Test
    @DisplayName("Valeur maximale de NUMERIC(15,2)")
    void supportsColumnMaximum() {
        assertThat(Money.of("9999999999999.99").toApiString()).isEqualTo("9999999999999.99");
    }

    // ---------- Règle centrale : ne jamais arrondir un résultat intermédiaire ----------

    @Test
    @DisplayName("(10 000 / 3) x 3 arrondi à la fin = 10 000.00 : pas de perte")
    void roundsOnlyAtTheEnd() {
        Money result = Money.of("10000").dividedBy(3).times(3);

        assertThat(result.toApiString()).isEqualTo("10000.00");
    }

    @Test
    @DisplayName("Contre-exemple : arrondir trop tôt fait perdre 0.01")
    void earlyRoundingLosesMoney() {
        Money result = Money.of("10000").dividedBy(3).rounded().times(3);

        assertThat(result.toApiString()).isEqualTo("9999.99");
    }

    // ---------- Signes ----------

    @Test
    @DisplayName("-0.00 n'est ni négatif ni autre chose que zéro")
    void negativeZeroIsZero() {
        assertThat(Money.of("-0.00").isNegative()).isFalse();
        assertThat(Money.of("-0.00").isZero()).isTrue();
    }
}