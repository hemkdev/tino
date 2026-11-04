package tino.nucleo;

import java.time.LocalDate;

public record Lancamento(long id, LocalDate data, long centavos, String categoria,
                         String descricao) {}
