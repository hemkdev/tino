package financebot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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
        assertNull(item.data());
        assertNull(item.descricao());
        assertFalse(item.ganho());
    }

    // ------------------------------------------------------------------ data

    @Test
    void campoComDigitoDepoisDaCategoriaEhData() {
        Entrada.Item item = Entrada.item("50 mercado 07/08");

        assertEquals("mercado", item.categoria());
        assertEquals("07/08", item.data());
        assertNull(item.descricao());
    }

    @Test
    void descricaoVemDepoisDaData() {
        Entrada.Item item = Entrada.item("50 mercado 07/08 pão e leite");

        assertEquals("mercado", item.categoria());
        assertEquals("07/08", item.data());
        assertEquals("pão e leite", item.descricao());
    }

    @Test
    void campoComLetraDepoisDaCategoriaEhDescricao() {
        Entrada.Item item = Entrada.item("50 mercado pão e leite");

        assertEquals("mercado", item.categoria());
        assertNull(item.data());
        assertEquals("pão e leite", item.descricao());
    }

    @Test
    void aceitaDataIsoEDiaSolto() {
        assertEquals("2026-08-07", Entrada.item("50 mercado 2026-08-07").data());
        assertEquals("7", Entrada.item("50 mercado 7").data());
    }

    @Test
    void tracoAntigoContinuaFuncionando() {
        assertEquals("pão", Entrada.item("50 mercado - pão").descricao());
    }

    // ------------------------------------------------------------ categorias

    @Test
    void costuraCaminhoDeSubcategoria() {
        Entrada.Item item = Entrada.item("50 alimentação > mercado 07/08 feira");

        assertEquals("alimentação > mercado", item.categoria());
        assertEquals("07/08", item.data());
        assertEquals("feira", item.descricao());
    }

    @Test
    void aceitaCaminhoSemEspacoNoSeparador() {
        assertEquals("alimentação>mercado", Entrada.item("50 alimentação>mercado").categoria());
        assertEquals("alimentação> mercado", Entrada.item("50 alimentação> mercado").categoria());
        assertEquals("alimentação >mercado", Entrada.item("50 alimentação >mercado").categoria());
    }

    @Test
    void semContextoCategoriaEhUmCampoSo() {
        // sem ">", aspas ou categoria conhecida, a segunda palavra já é descrição
        Entrada.Item item = Entrada.item("50 bem estar");

        assertEquals("bem", item.categoria());
        assertEquals("estar", item.descricao());
    }

    @Test
    void aspasAgrupamNomeComEspaco() {
        Entrada.Item item = Entrada.item("50 \"bem estar\" 07/08 massagem");

        assertEquals("bem estar", item.categoria());
        assertEquals("07/08", item.data());
        assertEquals("massagem", item.descricao());
    }

    @Test
    void categoriaConhecidaComEspacoDispensaAspas() {
        Entrada.Item item = Entrada.item("50 bem estar massagem", Set.of("Bem Estar"));

        assertEquals("bem estar", item.categoria());
        assertEquals("massagem", item.descricao());
    }

    @Test
    void casaOMaiorNomeConhecidoPrimeiro() {
        Entrada.Item item = Entrada.item("50 bem estar animal ração",
                Set.of("Bem Estar", "Bem Estar Animal"));

        assertEquals("bem estar animal", item.categoria());
        assertEquals("ração", item.descricao());
    }

    @Test
    void categoriaConhecidaNaoEngoleAData() {
        Entrada.Item item = Entrada.item("50 bem estar 19/07", Set.of("Bem Estar"));

        assertEquals("bem estar", item.categoria());
        assertEquals("19/07", item.data());
    }

    @Test
    void reconheceConhecidaIgnorandoCaixa() {
        assertEquals("BEM ESTAR", Entrada.item("50 BEM ESTAR", Set.of("Bem Estar")).categoria());
    }

    // ------------------------------------------------------------ capitalizar

    @Test
    void capitalizaCadaPalavra() {
        assertEquals("Bem Estar", Entrada.capitalizar("bem estar"));
        assertEquals("Mercado", Entrada.capitalizar("MERCADO"));
        assertEquals("Alimentação", Entrada.capitalizar("alimentação"));
        assertEquals("Alimentação", Entrada.capitalizar("ALIMENTAÇÃO"));
    }

    @Test
    void mantemConectivoEmMinuscula() {
        assertEquals("Cartão de Crédito", Entrada.capitalizar("cartão de crédito"));
        assertEquals("Contas da Casa", Entrada.capitalizar("CONTAS DA CASA"));
    }

    @Test
    void conectivoNoInicioAindaEhCapitalizado() {
        assertEquals("De Fulano", Entrada.capitalizar("de fulano"));
    }

    @Test
    void enxugaEspacosSobrando() {
        assertEquals("Bem Estar", Entrada.capitalizar("  bem   estar  "));
    }

    @Test
    void semCategoriaDeixaNuloParaQuemChamaDecidir() {
        assertNull(Entrada.item("50").categoria());
    }

    // ----------------------------------------------------------------- sinal

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

    // ----------------------------------------------------------------- lista

    @Test
    void virgulaSeparaVariosLancamentos() {
        List<Entrada.Item> itens = Entrada.lista("50 mercado, 30 uber, +3000 salário");

        assertEquals(3, itens.size());
        assertEquals("uber", itens.get(1).categoria());
        assertTrue(itens.get(2).ganho());
    }

    @Test
    void cadaItemTemSuaPropriaData() {
        List<Entrada.Item> itens = Entrada.lista("50 mercado 05/08, 30 uber 06/08 corrida");

        assertEquals("05/08", itens.get(0).data());
        assertEquals("06/08", itens.get(1).data());
        assertEquals("corrida", itens.get(1).descricao());
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
