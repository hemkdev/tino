package tino.nucleo;

/**
 * O que aconteceu ao pedir um lançamento. O núcleo decide o que aconteceu; cada canal
 * decide como mostrar (texto e botões no Discord, o estado do personagem no site).
 *
 * <p>{@code sealed}: o {@code switch} de quem consome é conferido pelo compilador, então
 * um evento novo não passa despercebido em nenhum canal.
 */
public sealed interface Evento {

    /** Gravado. */
    record Registrado(Tino.Registro registro) implements Evento {}

    /**
     * Não gravado: o valor está muito acima do que a pessoa costuma lançar nessa categoria,
     * e o mais provável é erro de digitação ({@code 5000} no lugar de {@code 50,00}). O canal
     * pergunta e responde com {@link Tino#confirmar(String, String)} ou
     * {@link Tino#descartar(String, String)}, usando a {@code chave}.
     */
    record ForaDoNormal(String chave, Pedido pedido, long tipico) implements Evento {}
}
