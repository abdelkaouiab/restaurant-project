package com.restaurant.management.validation;

import com.restaurant.management.enums.UniteMesure;

import java.math.BigDecimal;

public final class QuantiteIngredient {

    public static final String MESSAGE_QUANTITE_ENTIERE =
            "Une quantité mesurée à l’unité doit être un nombre entier.";

    private QuantiteIngredient() {
    }

    public static void verifier(UniteMesure unite, BigDecimal quantite) {
        if (unite == UniteMesure.UNITE && quantite.remainder(BigDecimal.ONE).signum() != 0) {
            throw new IllegalArgumentException(MESSAGE_QUANTITE_ENTIERE);
        }
    }
}
