package com.lany.shared.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Montant d'argent immuable.
 *
 * Règle centrale : le montant est conservé à PLEINE PRÉCISION. L'arrondi
 * (HALF_UP, 2 décimales) n'intervient qu'à la sortie : rounded() et toApiString().
 * Les montants négatifs sont autorisés (déficit, reste négatif) ; c'est aux
 * DTO et aux Services de refuser un montant négatif là où il n'a pas de sens.
 */
public final class Money implements Comparable<Money> {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // 34 chiffres significatifs : 10000 / 3 ne produit plus d'exception (résultat infini)
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL128;

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount;
    }

    // ---------- Création : validation seulement, jamais d'arrondi ----------

    public static Money of(String amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        try {
            return new Money(new BigDecimal(amount));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format: " + amount, e);
        }
    }

    public static Money of(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        return new Money(amount); // BigDecimal est immuable : pas besoin de le copier
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    // ---------- Arithmétique : chaque opération renvoie un NOUVEAU Money ----------

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(long factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)));
    }

    public Money dividedBy(long divisor) {
        if (divisor == 0) {
            throw new IllegalArgumentException("Cannot divide an amount by zero");
        }
        return new Money(amount.divide(BigDecimal.valueOf(divisor), DIVISION_PRECISION));
    }

    // ---------- Sortie : c'est ICI que l'on arrondit ----------

    public Money rounded() {
        return new Money(amount.setScale(SCALE, ROUNDING));
    }

    /** Format envoyé au frontend : 2 décimales, jamais de notation scientifique. */
    public String toApiString() {
        return amount.setScale(SCALE, ROUNDING).toPlainString();
    }

    // ---------- Signe ----------

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    // ---------- Égalité : insensible à l'échelle (2.0 == 2.00) ----------

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        // Doit être cohérent avec equals : 2.0 et 2.00 ont le même hashCode
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString(); // pleine précision, utile au débogage
    }
}