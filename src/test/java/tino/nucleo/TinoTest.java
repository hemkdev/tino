package tino.nucleo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O núcleo, sem canal nenhum: o que Discord, site e MCP vão compartilhar. */
class TinoTest {

    private static final String EU = "111";
    private static final String OUTRO = "222";
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 7);
    private static final Clock MEIO_DIA = relogio("2026-08-07T12:00");
    private static final Periodo AGOSTO = new Periodo(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), ChronoUnit.MONTHS);

    private Connection conexao;
    private Tino tino;

    private static Clock relogio(String horaLocal) {
        return Clock.fixed(LocalDateTime.parse(horaLocal).atZone(SP).toInstant(), SP);
    }

    @BeforeEach
    void abrir() throws SQLException {
        conexao = DriverManager.getConnection("jdbc:sqlite::memory:");
        Financas.criarTabelas(conexao);
        tino = new Tino(conexao, MEIO_DIA);
        tino.preparar(EU);
        tino.preparar(OUTRO);
    }

    @Test
    void prepararSoEhPrimeiraVezUmaVez() throws SQLException {
        assertTrue(tino.preparar("333"));
        assertFalse(tino.preparar("333"));
    }

    @Test
    void registrarSemCategoriaVaiParaOutros() throws SQLException {
        Tino.Registro r = tino.registrar(EU, -5_000, null, null, HOJE);

        assertEquals("Outros", r.categoria());
        assertEquals(-5_000, r.centavos());
    }

    @Test
    void registrarDevolveAcumuladoDoMes() throws SQLException {
        tino.registrar(EU, -5_000, "Alimentação", null, HOJE);
        Tino.Registro r = tino.registrar(EU, -3_000, "Alimentação", null, HOJE);

        assertEquals(8_000, r.acumulado());
    }

    @Test
    void registrarAvisaCategoriaParecida() throws SQLException {
        assertEquals("Transporte", tino.registrar(EU, -5_000, "Transprte", null, HOJE).parecida());
        assertNull(tino.registrar(EU, -5_000, "Transporte", null, HOJE).parecida());
    }

    @Test
    void lancarGravaCadaItemComSeuSinal() throws SQLException {
        List<Evento> eventos = tino.lancar(EU, "50 mercado, +3000 salário");

        assertEquals(2, eventos.size());
        assertEquals(-5_000, registrado(eventos.get(0)).centavos());
        assertEquals(300_000, registrado(eventos.get(1)).centavos());
        assertEquals(2, tino.pagina(EU, Escala.MES, 0).itens().size());
    }

    @Test
    void editarMantemOSinalOriginal() throws SQLException {
        long id = tino.registrar(EU, -5_000, "Lazer", null, HOJE).id();

        Tino.Registro r = tino.editar(EU, id, new Pedido(-7_000, "Lazer", "cinema", HOJE));

        assertEquals(-7_000, r.centavos());
        assertEquals(-7_000, tino.buscar(EU, id).orElseThrow().centavos());
    }

    @Test
    void editarDeOutroUsuarioEhRecusado() throws SQLException {
        long id = tino.registrar(EU, -5_000, "Lazer", null, HOJE).id();

        assertThrows(IllegalArgumentException.class,
                () -> tino.editar(OUTRO, id, new Pedido(-1_000, "Lazer", null, HOJE)));
    }

    @Test
    void paginaSinalizaQuandoHaProxima() throws SQLException {
        for (int i = 0; i < Tino.LIMITE_PAGINA + 1; i++) {
            tino.registrar(EU, -100, "Lazer", null, HOJE);
        }

        Tino.Pagina primeira = tino.pagina(EU, Escala.MES, 0);
        Tino.Pagina segunda = tino.pagina(EU, Escala.MES, Tino.LIMITE_PAGINA);

        assertEquals(Tino.LIMITE_PAGINA, primeira.itens().size());
        assertTrue(primeira.temProxima());
        assertEquals(1, segunda.itens().size());
        assertFalse(segunda.temProxima());
    }

    @Test
    void resumoTrazPeriodoAtualEAnterior() throws SQLException {
        tino.registrar(EU, -5_000, "Lazer", null, HOJE);
        tino.registrar(EU, -2_000, "Lazer", null, HOJE.minusMonths(1));

        Tino.Comparativo c = tino.resumo(EU, Escala.MES);

        assertEquals(5_000, c.atual().despesas());
        assertEquals(2_000, c.anterior().despesas());
    }

    @Test
    void fixoUsaOSinalParaEscolherOTipo() throws SQLException {
        tino.adicionarFixo(EU, 300_000, "Salário", null);

        assertEquals(1, tino.lancarFixos(EU));
        assertEquals(300_000, tino.resumo(EU, Escala.MES).atual().ganhos());
    }

    @Test
    void projetaOMesComBaseNaMediaDiaria() {
        // 7 dias decorridos, R$ 700 gastos → R$ 100/dia → R$ 3.100 em 31 dias
        Tino.Ritmo ritmo = Tino.ritmo(AGOSTO, 70_000, HOJE);

        assertEquals(10_000, ritmo.mediaDiaria());
        assertEquals(310_000, ritmo.projecao());
    }

    @Test
    void naoProjetaPeriodoJaEncerrado() {
        assertNull(Tino.ritmo(AGOSTO, 70_000, LocalDate.of(2026, 9, 15)));
    }

    @Test
    void naoProjetaNoUltimoDiaDoPeriodo() {
        assertNull(Tino.ritmo(AGOSTO, 70_000, LocalDate.of(2026, 8, 31)));
    }

    @Test
    void naoProjetaSemGasto() {
        assertNull(Tino.ritmo(AGOSTO, 0, HOJE));
    }

    @Test
    void csvTemCabecalhoEValorComPontoDecimal() {
        String csv = Tino.csv(List.of(
                new Lancamento(1, HOJE, -123_456, "Moradia", "aluguel")));

        assertTrue(csv.startsWith("id,data,valor,categoria,descricao\n"));
        assertTrue(csv.contains("-1234.56"));
        assertTrue(csv.contains("\"Moradia\""));
    }

    @Test
    void csvEscapaAspasDaDescricao() {
        String csv = Tino.csv(List.of(
                new Lancamento(1, HOJE, -100, "Lazer", "show do \"grupo\"")));

        assertTrue(csv.contains("\"show do \"\"grupo\"\"\""));
    }

    @Test
    void csvNaoQuebraComDescricaoNula() {
        assertTrue(Tino.csv(List.of(
                new Lancamento(1, HOJE, -100, "Lazer", null))).endsWith(",\n"));
    }

    @Test
    void resumoTrazORitmoDoPeriodoEmAndamento() throws SQLException {
        tino.registrar(EU, -70_000, "Lazer", null, HOJE);

        assertEquals(310_000, tino.resumo(EU, Escala.MES).ritmo().projecao());
    }

    @Test
    void exportarTrazCsvSoDoUsuario() throws SQLException {
        tino.registrar(EU, -5_000, "Lazer", "cinema", HOJE);
        tino.registrar(OUTRO, -9_900, "Lazer", "segredo", HOJE);

        Tino.Exportacao x = tino.exportar(EU, Escala.MES);

        assertEquals(1, x.quantidade());
        assertEquals(LocalDate.of(2026, 8, 1), x.periodo().inicio());
        assertTrue(x.csv().contains("cinema"));
        assertFalse(x.csv().contains("segredo"));
    }

    @Test
    void exportarSemLancamentoTemQuantidadeZero() throws SQLException {
        assertEquals(0, tino.exportar(EU, Escala.MES).quantidade());
    }

    // ------------------------------------------------------- fora do normal

    private static Tino.Registro registrado(Evento e) {
        return assertInstanceOf(Evento.Registrado.class, e).registro();
    }

    private void historico(String categoria, long centavos, int vezes) throws SQLException {
        for (int i = 0; i < vezes; i++) {
            tino.registrar(EU, centavos, categoria, null, HOJE);
        }
    }

    @Test
    void valorMuitoAcimaDoNormalNaoGravaSemConfirmar() throws SQLException {
        historico("Mercado", -5_000, 3);

        Evento e = tino.lancar(EU, new Pedido(-500_000, "Mercado", null, HOJE));

        Evento.ForaDoNormal fora = assertInstanceOf(Evento.ForaDoNormal.class, e);
        assertEquals(5_000, fora.tipico());
        assertEquals(-500_000, fora.pedido().centavos());
        assertEquals(3, tino.pagina(EU, Escala.MES, 0).itens().size());   // nada gravado
    }


    @Test
    void valorDentroDoNormalGravaDireto() throws SQLException {
        historico("Mercado", -5_000, 3);

        assertEquals(-20_000, registrado(tino.lancar(EU, new Pedido(-20_000, "Mercado", null, HOJE))).centavos());
    }

    @Test
    void semHistoricoNadaEhForaDoNormal() throws SQLException {
        historico("Mercado", -5_000, 2);   // menos de 3 não forma padrão

        registrado(tino.lancar(EU, new Pedido(-500_000, "Mercado", null, HOJE)));
    }

    @Test
    void loteGravaOsNormaisEDeixaOForaPendente() throws SQLException {
        historico("Mercado", -5_000, 3);

        List<Evento> eventos = tino.lancar(EU, "30 uber, 5000 mercado");

        registrado(eventos.get(0));
        String chave = assertInstanceOf(Evento.ForaDoNormal.class, eventos.get(1)).chave();
        assertTrue(tino.confirmar(EU, chave).isPresent());
        assertEquals(5, tino.pagina(EU, Escala.MES, 0).itens().size());
    }

    @Test
    void lancarAvisaCategoriaParecida() throws SQLException {
        assertEquals("Transporte",
                registrado(tino.lancar(EU, new Pedido(-5_000, "Transprte", null, HOJE))).parecida());
    }

    @Test
    void foraDoNormalSemCategoriaFalaDeOutros() throws SQLException {
        historico(null, -5_000, 3);

        Evento.ForaDoNormal fora = assertInstanceOf(Evento.ForaDoNormal.class,
                tino.lancar(EU, new Pedido(-500_000, null, null, HOJE)));

        assertEquals(Tino.PADRAO, fora.pedido().categoria());
    }

    // ------------------------------------------------- porta dos fundos fechada

    private String pendente() throws SQLException {
        historico("Mercado", -5_000, 3);
        return assertInstanceOf(Evento.ForaDoNormal.class,
                tino.lancar(EU, new Pedido(-500_000, "Mercado", null, HOJE))).chave();
    }

    private int gravados() throws SQLException {
        return tino.pagina(EU, Escala.MES, 0).itens().size();
    }

    @Test
    void foraDoNormalNaoGravaEDevolveChave() throws SQLException {
        String chave = pendente();

        assertNotNull(chave);
        assertEquals(3, gravados());
    }

    @Test
    void confirmarGravaOPendente() throws SQLException {
        String chave = pendente();

        Tino.Registro r = tino.confirmar(EU, chave).orElseThrow();

        assertEquals(-500_000, r.centavos());
        assertEquals("Mercado", r.categoria());
        assertEquals(4, gravados());
    }

    @Test
    void confirmarDuasVezesGravaUmaSo() throws SQLException {
        String chave = pendente();
        tino.confirmar(EU, chave);

        assertEquals(Optional.empty(), tino.confirmar(EU, chave));
        assertEquals(4, gravados());
    }

    @Test
    void confirmarDeOutroUsuarioEhRecusado() throws SQLException {
        String chave = pendente();

        assertEquals(Optional.empty(), tino.confirmar(OUTRO, chave));
        assertTrue(tino.confirmar(EU, chave).isPresent());
    }

    @Test
    void descartarApagaOPendente() throws SQLException {
        String chave = pendente();

        tino.descartar(EU, chave);

        assertEquals(Optional.empty(), tino.confirmar(EU, chave));
        assertEquals(3, gravados());
    }

    @Test
    void pendenteExpiraEm24h() throws SQLException {
        String chave = pendente();
        Tino depois = new Tino(conexao, Clock.offset(MEIO_DIA, Duration.ofHours(24).plusMinutes(1)));

        assertEquals(Optional.empty(), depois.confirmar(EU, chave));
    }

    @Test
    void pendenteAinda23h59Confirma() throws SQLException {
        String chave = pendente();
        Tino depois = new Tino(conexao, Clock.offset(MEIO_DIA, Duration.ofHours(23).plusMinutes(59)));

        assertTrue(depois.confirmar(EU, chave).isPresent());
    }

    @Test
    void abrirUsaOFusoDeSaoPaulo(@TempDir Path pasta) throws SQLException {
        assertEquals(SP, Tino.abrir(pasta.resolve("tino.db").toString()).fuso());
    }

    @Test
    void pedidoRecusaDescricaoLonga() {
        assertThrows(IllegalArgumentException.class,
                () -> new Pedido(-100, "Mercado", "x".repeat(201), HOJE));
    }

    @Test
    void loteQueFalhaDesfazOPendenteCriado() throws SQLException {
        historico("Mercado", -5_000, 3);

        assertThrows(IllegalArgumentException.class,
                () -> tino.lancar(EU, "5000 mercado, 30 uber 2099-12-31"));

        assertEquals(0, contarPendentes());
    }

    private int contarPendentes() throws SQLException {
        try (var rs = conexao.createStatement().executeQuery("SELECT COUNT(*) FROM pendente")) {
            return rs.getInt(1);
        }
    }

    @Test
    void pendenteSobreviveAReabrir(@TempDir Path pasta) throws SQLException {
        String caminho = pasta.resolve("tino.db").toString();
        Tino antes = Tino.abrir(caminho, MEIO_DIA);
        antes.preparar(EU);
        for (int i = 0; i < 3; i++) {
            antes.lancar(EU, new Pedido(-5_000, "Mercado", null, HOJE));
        }
        String chave = assertInstanceOf(Evento.ForaDoNormal.class,
                antes.lancar(EU, new Pedido(-500_000, "Mercado", null, HOJE))).chave();

        assertTrue(Tino.abrir(caminho, MEIO_DIA).confirmar(EU, chave).isPresent());
    }

    // ------------------------------------------------------------ pedido válido

    @Test
    void pedidoRecusaValorZero() {
        assertThrows(IllegalArgumentException.class, () -> new Pedido(0, "Mercado", null, HOJE));
    }

    @Test
    void pedidoRecusaDataNula() {
        assertThrows(IllegalArgumentException.class, () -> new Pedido(-100, "Mercado", null, null));
    }

    @Test
    void editarComDescricaoEmBrancoGravaSemDescricao() throws SQLException {
        long id = tino.registrar(EU, -5_000, "Lazer", "cinema", HOJE).id();

        tino.editar(EU, id, new Pedido(-5_000, "Lazer", "  ", HOJE));

        assertNull(tino.buscar(EU, id).orElseThrow().descricao());
    }

    @Test
    void fixoComDescricaoEmBrancoGravaSemDescricao() throws SQLException {
        tino.adicionarFixo(EU, -100_000, "Moradia", " ");

        assertNull(tino.fixos(EU).getFirst().descricao());
    }

    @Test
    void fixoPassaPelasMesmasRegrasDoPedido() {
        assertThrows(IllegalArgumentException.class, () -> tino.adicionarFixo(EU, 0, "Moradia", null));
        assertThrows(IllegalArgumentException.class,
                () -> tino.adicionarFixo(EU, -100, "Moradia", "x".repeat(201)));
    }

    @Test
    void lancarRecusaDataFutura() throws SQLException {
        assertThrows(IllegalArgumentException.class,
                () -> tino.lancar(EU, new Pedido(-100, "Mercado", null, HOJE.plusDays(1))));
        assertEquals(0, gravados());
    }

    // ------------------------------------------------------ relógio e madrugada

    @Test
    void registroDeMadrugada() throws SQLException {
        Pedido p = new Pedido(-100, "Mercado", null, HOJE);

        assertTrue(registrado(new Tino(conexao, relogio("2026-08-07T00:00")).lancar(EU, p)).madrugada());
        assertTrue(registrado(new Tino(conexao, relogio("2026-08-07T05:59")).lancar(EU, p)).madrugada());
        assertFalse(registrado(new Tino(conexao, relogio("2026-08-07T06:00")).lancar(EU, p)).madrugada());
        assertFalse(registrado(tino.lancar(EU, p)).madrugada());
    }

    @Test
    void hojeSegueOFusoDoTino() throws SQLException {
        // 01:00 UTC do dia 8 ainda é dia 7 em São Paulo.
        Tino utc = new Tino(conexao, Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"), SP));

        assertEquals(HOJE, registrado(utc.lancar(EU, "50 mercado").getFirst()).data());
    }

    // -------------------------------------------------------------------- sinal

    @Test
    void editarRecusaTrocaDeSinal() throws SQLException {
        long id = tino.registrar(EU, -5_000, "Lazer", null, HOJE).id();

        assertThrows(IllegalArgumentException.class,
                () -> tino.editar(EU, id, new Pedido(5_000, "Lazer", null, HOJE)));
        assertEquals(-5_000, tino.buscar(EU, id).orElseThrow().centavos());
    }

    // ------------------------------------------------------------------ período

    @Test
    void escalaNaoDependeDoLocale() {
        java.util.Locale antes = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
            assertEquals(Escala.DIA, Escala.de("dia"));
        } finally {
            java.util.Locale.setDefault(antes);
        }
    }

    @Test
    void escalaDesconhecidaEhRecusada() {
        assertThrows(IllegalArgumentException.class, () -> Escala.de("quinzena"));
    }

    // ------------------------------------------------------- lote tudo ou nada

    @Test
    void loteComItemInvalidoNaoGravaNada() throws SQLException {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> tino.lancar(EU, "50 mercado, 30 uber 2099-12-31, 20 pão"));

        assertTrue(e.getMessage().contains("2º item"), e.getMessage());
        assertEquals(0, gravados());
    }

    // ------------------------------------------------- normal com janela fechada

    @Test
    void normalIgnoraLancamentosPosteriores() throws SQLException {
        historico("Mercado", -5_000, 3);

        registrado(tino.lancar(EU, new Pedido(-500_000, "Mercado", null, HOJE.minusDays(10))));
    }
}
