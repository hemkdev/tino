package financebot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntradaTest {

    @Test
    void valorEcategoria() {
        Entrada.Item item = Entrada.item("50 mercado");

        assertEquals(5_000, item.centavos());
        assertEquals("mercado", item.categoria());
        assertNull(item.descricao());
        assertFalse(item.ganho());
    }

    @Test
    void separaDescricaoDepoisDoTraco() {
        Entrada.Item item = Entrada.item("50 mercado - pão e leite");

        assertEquals("mercado", item.categoria());
        assertEquals("pão e leite", item.descricao());
    }

    @Test
    void hifenDentroDoNomeNaoViraSeparador() {
        assertEquals("bem-estar", Entrada.item("50 bem-estar").categoria());
    }

    @Test
    void maisMarcaGanho() {
        Entrada.Item item = Entrada.item("+3000 salário");

        assertTrue(item.ganho());
        assertEquals(300_000, item.centavos());
        assertEquals("salário", item.categoria());
    }

    @Test
    void semSinalEhGasto() {
        assertFalse(Entrada.item("50 mercado").ganho());
        assertFalse(Entrada.item("-50 mercado").ganho());
    }

    @Test
    void aceitaCaminhoDeSubcategoria() {
        assertEquals("alimentação > mercado", Entrada.item("50 alimentação > mercado").categoria());
    }

    @Test
    void semCategoriaDeixaNuloParaQuemChamaDecidir() {
        assertNull(Entrada.item("50").categoria());
    }

    @Test
    void virgulaSeparaVariosLancamentos() {
        List<Entrada.Item> itens = Entrada.lista("50 mercado, 30 uber, +3000 salário");

        assertEquals(3, itens.size());
        assertEquals("uber", itens.get(1).categoria());
        assertTrue(itens.get(2).ganho());
    }

    @Test
    void virgulaSoltaNaoViraItemVazio() {
        assertEquals(2, Entrada.lista("50 mercado, 30 uber,").size());
    }

    @Test
    void aceitaOsFormatosDeValorDoDinheiro() {
        assertEquals(123_456, Entrada.item("1.234,56 moradia").centavos());
        assertEquals(5_000, Entrada.item("50,00 lazer").centavos());
    }

    @Test
    void rejeitaTextoSemValor() {
        assertThrows(IllegalArgumentException.class, () -> Entrada.lista("mercado"));
        assertThrows(IllegalArgumentException.class, () -> Entrada.lista(""));
        assertThrows(IllegalArgumentException.class, () -> Entrada.lista(null));
    }

    @Test
    void reconheceOQueParecelancamento() {
        assertTrue(Entrada.pareceLancamento("50 mercado"));
        assertTrue(Entrada.pareceLancamento("+3000 salário"));
        assertTrue(Entrada.pareceLancamento("1.234,56 moradia"));
    }

    @Test
    void conversaSoltaNaoViraLancamento() {
        assertFalse(Entrada.pareceLancamento("oi"));
        assertFalse(Entrada.pareceLancamento("quanto gastei esse mês?"));
        assertFalse(Entrada.pareceLancamento("obrigado"));
        assertFalse(Entrada.pareceLancamento(""));
        assertFalse(Entrada.pareceLancamento(null));
    }
}
