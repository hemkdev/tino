package financebot;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntradaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 7);

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
    void virgulaNaoSeparaDecimal() {
        List<Entrada.Item> itens = Entrada.lista("50 mercado, 1.234,56 uber,300 salario");

        assertEquals(3, itens.size());
        assertEquals("uber", itens.get(1).categoria());
        assertEquals(123456, itens.get(1).centavos());
        assertEquals(30000, itens.get(2).centavos());
    }

    // Limitação conhecida, categoria terminada em dígito
    // com virgula e valor do próximo lançamento é tratado como decimal
    @Test
    void virgulaNaoSeparaCategoriaTerminadaEmDigito() {
        List<Entrada.Item> itens = Entrada.lista("50 posto24,30 uber");

        assertEquals(1, itens.size());
        assertEquals("posto24,30", itens.get(0).categoria());
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
    void aspasVaziasDeixamCategoriaNula() {
        Entrada.Item item = Entrada.item("50 \"\" 07/08");

        assertNull(item.categoria());
        assertEquals("07/08", item.data());
    }

    @Test
    void aspasVaziasDepoisDaCategoriaNaoDerrubam() {
        Entrada.Item item = Entrada.item("50 mercado \"\" pão");

        assertNull(item.data());
        assertEquals("pão", item.descricao());
    }

    @Test
    void sinalSemValorEhErroDeValidacao() {
        assertThrows(IllegalArgumentException.class, () -> Entrada.lista("50 mercado, +"));
    }

    @Test
    void reconheceOQueParecelancamento() {
        assertTrue(Entrada.pareceLancamento("50 mercado"));
        assertTrue(Entrada.pareceLancamento("+3000 salário"));
        assertTrue(Entrada.pareceLancamento("1.234,56 moradia"));
    }

    // -------------------------------------------------------------- consulta

    @Test
    void reconhecePedidoDeResumo() {
        assertEquals("resumo", Entrada.consulta("resumo").tipo());
        assertEquals("resumo", Entrada.consulta("quanto gastei esse mês?").tipo());
        assertEquals("resumo", Entrada.consulta("Qual meu saldo").tipo());
        assertEquals("resumo", Entrada.consulta("como estou").tipo());
    }

    @Test
    void reconhecePedidoDeExtrato() {
        assertEquals("lancamentos", Entrada.consulta("extrato").tipo());
        assertEquals("lancamentos", Entrada.consulta("últimos lançamentos").tipo());
    }

    @Test
    void reconheceAjudaCategoriasEFixos() {
        assertEquals("ajuda", Entrada.consulta("ajuda").tipo());
        assertEquals("ajuda", Entrada.consulta("como funciona?").tipo());
        assertEquals("categorias", Entrada.consulta("categorias").tipo());
        assertEquals("fixos", Entrada.consulta("meus fixos").tipo());
    }

    @Test
    void extraiOPeriodoDaFrase() {
        assertEquals("dia", Entrada.consulta("quanto gastei hoje").periodo());
        assertEquals("semana", Entrada.consulta("resumo da semana").periodo());
        assertEquals("ano", Entrada.consulta("quanto gastei esse ano").periodo());
        assertEquals("mes", Entrada.consulta("resumo").periodo());   // padrão
    }

    @Test
    void periodoMaisCurtoGanhaQuandoHaMaisDeUm() {
        assertEquals("dia", Entrada.consulta("resumo de hoje e do mes").periodo());
        assertEquals("semana", Entrada.consulta("resumo da semana desse ano").periodo());
        assertEquals("mes", Entrada.consulta("resumo do mes desse ano").periodo());
    }

    @Test
    void empateDeGatilhoTemVencedorFixo() {
        // "listar" e "gastos" têm o mesmo tamanho; vence o tipo declarado primeiro
        assertEquals("resumo", Entrada.consulta("listar gastos").tipo());
    }

    @Test
    void ignoraAcentoECaixaNoPedido() {
        assertEquals("resumo", Entrada.consulta("QUANTO GASTEI").tipo());
        assertEquals("lancamentos", Entrada.consulta("Histórico").tipo());
    }

    @Test
    void gatilhoMaisLongoGanha() {
        // "quanto gastei" é mais específico que "gastos"
        assertEquals("resumo", Entrada.consulta("quanto gastei com gastos").tipo());
    }

    @Test
    void conversaSemGatilhoNaoViraConsulta() {
        assertNull(Entrada.consulta("oi"));
        assertNull(Entrada.consulta("obrigado"));
        assertNull(Entrada.consulta("bom dia"));
        assertNull(Entrada.consulta(""));
        assertNull(Entrada.consulta(null));
    }

    @Test
    void textoLongoDemaisNaoViraConsulta() {
        assertNull(Entrada.consulta("resumo ".repeat(40)));
    }

    @Test
    void palavraDentroDeOutraNaoDispara() {
        assertNull(Entrada.consulta("resumindo a historia"));
    }

    @Test
    void conversaSoltaNaoViraLancamento() {
        assertFalse(Entrada.pareceLancamento("oi"));
        assertFalse(Entrada.pareceLancamento("quanto gastei esse mês?"));
        assertFalse(Entrada.pareceLancamento("obrigado"));
        assertFalse(Entrada.pareceLancamento(""));
        assertFalse(Entrada.pareceLancamento(null));
    }

    // ----------------------------------------------------------------- datas

    @Test
    void dataVaziaViraHoje() {
        assertEquals(HOJE, Entrada.data(null, HOJE));
        assertEquals(HOJE, Entrada.data("", HOJE));
    }

    @Test
    void aceitaDiaBarraMesEIso() {
        assertEquals(LocalDate.of(2026, 8, 1), Entrada.data("1/8", HOJE));
        assertEquals(LocalDate.of(2026, 7, 15), Entrada.data("2026-07-15", HOJE));
    }

    @Test
    void diaSoltoUsaOMesCorrente() {
        assertEquals(LocalDate.of(2026, 8, 5), Entrada.data("5", HOJE));
        assertEquals(LocalDate.of(2026, 8, 5), Entrada.data("05", HOJE));
    }

    @Test
    void diaBarraMesFuturoCaiNoAnoAnterior() {
        assertEquals(LocalDate.of(2025, 12, 25), Entrada.data("25/12", HOJE));
    }

    @Test
    void rejeitaDataFuturaExplicita() {
        assertThrows(IllegalArgumentException.class, () -> Entrada.data("2026-12-25", HOJE));
    }

    @Test
    void rejeitaDataInvalida() {
        assertThrows(IllegalArgumentException.class, () -> Entrada.data("ontem", HOJE));
    }
}
