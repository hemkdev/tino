package financebot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Conversão entre texto digitado pelo usuário e centavos. */
final class Dinheiro {

    /** R$ 1.000.000,00 — acima disso é quase certamente erro de digitação. */
    static final long LIMITE = 100_000_000L;

    private static final Locale BR = Locale.of("pt", "BR");

    private Dinheiro() {}

    /** Aceita "50", "50,00", "1.234,56", "1234.56" e "R$ 50". Sempre positivo. */
    static long paraCentavos(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("informe um valor");
        }

        String limpo = texto.replace("R$", "").replace(" ", "").trim();

        // Com vírgula, ela é o separador decimal e o ponto é milhar: "1.234,56" → "1234.56"
        if (limpo.indexOf(',') >= 0) {
            limpo = limpo.replace(".", "").replace(',', '.');
        }

        BigDecimal valor;
        try {
            valor = new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("valor inválido: " + texto);
        }

        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("o valor deve ser maior que zero");
        }

        long centavos = valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        if (centavos > LIMITE) {
            throw new IllegalArgumentException("valor acima do limite de " + formatar(LIMITE));
        }
        return centavos;
    }

    static String formatar(long centavos) {
        return String.format(BR, "R$ %,.2f", BigDecimal.valueOf(centavos, 2));
    }
}
