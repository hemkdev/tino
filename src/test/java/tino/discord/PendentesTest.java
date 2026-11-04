package tino.discord;

import org.junit.jupiter.api.Test;
import tino.nucleo.Pedido;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PendentesTest {

    private static final Pedido PEDIDO = new Pedido(-500_000, "Mercado", null, LocalDate.of(2026, 8, 7));

    @Test
    void soADonaRetiraOPedido() {
        Pendentes p = new Pendentes();
        String chave = p.guardar("A", PEDIDO);

        assertNull(p.retirar("B", chave));
        assertEquals(PEDIDO, p.retirar("A", chave));
    }

    @Test
    void retirarDuasVezesDevolveNulo() {
        Pendentes p = new Pendentes();
        String chave = p.guardar("A", PEDIDO);
        p.retirar("A", chave);

        assertNull(p.retirar("A", chave));
    }

    @Test
    void chaveNaoSeRepeteEntreInstancias() {
        // Um reinício cria outra instância: a chave de um botão antigo não pode
        // coincidir com a de um pedido novo.
        assertNotEquals(new Pendentes().guardar("A", PEDIDO), new Pendentes().guardar("A", PEDIDO));
    }
}
