package tino.nucleo;

import java.time.LocalDate;

/** Um lançamento pedido e ainda não gravado. Valor negativo é gasto, positivo é ganho. */
public record Pedido(long centavos, String categoria, String descricao, LocalDate data) {}
