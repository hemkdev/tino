package tino.nucleo;

import java.time.LocalDate;

/** Um lançamento pedido e ainda não gravado. Valor negativo é gasto, positivo é ganho. */
public record Pedido(long centavos, String categoria, String descricao, LocalDate data) {

    public Pedido {
        if (centavos == 0) {
            throw new IllegalArgumentException("o valor precisa ser maior que zero");
        }
        if (data == null) {
            throw new IllegalArgumentException("falta a data");
        }
        if (descricao != null && descricao.length() > 200) {
            throw new IllegalArgumentException("descrição muito longa");
        }
    }
}
