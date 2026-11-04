package financebot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O núcleo, sem canal nenhum: o que Discord, site e MCP vão compartilhar. */
class TinoTest {

    private static final String EU = "111";
    private static final String OUTRO = "222";
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 7);
    private static final Financas.Periodo AGOSTO = new Financas.Periodo(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), ChronoUnit.MONTHS);

    private Tino tino;

    @BeforeEach
    void abrir() throws SQLException {
        var c = DriverManager.getConnection("jdbc:sqlite::memory:");
        Financas.criarTabelas(c);
        tino = new Tino(c);
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
        List<Tino.Registro> rs = tino.lancar(EU, "50 mercado, +3000 salário", HOJE);

        assertEquals(2, rs.size());
        assertEquals(-5_000, rs.get(0).centavos());
        assertEquals(300_000, rs.get(1).centavos());
        assertEquals(2, tino.pagina(EU, "mes", 0, HOJE).itens().size());
    }

    @Test
    void editarMantemOSinalOriginal() throws SQLException {
        long id = tino.registrar(EU, -5_000, "Lazer", null, HOJE).id();

        Tino.Registro r = tino.editar(EU, id, 7_000, "Lazer", "cinema", HOJE);

        assertEquals(-7_000, r.centavos());
        assertEquals(-7_000, tino.buscar(EU, id).orElseThrow().centavos());
    }

    @Test
    void editarDeOutroUsuarioEhRecusado() throws SQLException {
        long id = tino.registrar(EU, -5_000, "Lazer", null, HOJE).id();

        assertThrows(IllegalArgumentException.class,
                () -> tino.editar(OUTRO, id, 1_000, "Lazer", null, HOJE));
    }

    @Test
    void paginaSinalizaQuandoHaProxima() throws SQLException {
        for (int i = 0; i < Tino.LIMITE_PAGINA + 1; i++) {
            tino.registrar(EU, -100, "Lazer", null, HOJE);
        }

        Tino.Pagina primeira = tino.pagina(EU, "mes", 0, HOJE);
        Tino.Pagina segunda = tino.pagina(EU, "mes", Tino.LIMITE_PAGINA, HOJE);

        assertEquals(Tino.LIMITE_PAGINA, primeira.itens().size());
        assertTrue(primeira.temProxima());
        assertEquals(1, segunda.itens().size());
        assertFalse(segunda.temProxima());
    }

    @Test
    void resumoTrazPeriodoAtualEAnterior() throws SQLException {
        tino.registrar(EU, -5_000, "Lazer", null, HOJE);
        tino.registrar(EU, -2_000, "Lazer", null, HOJE.minusMonths(1));

        Tino.Comparativo c = tino.resumo(EU, "mes", HOJE);

        assertEquals(5_000, c.atual().despesas());
        assertEquals(2_000, c.anterior().despesas());
    }

    @Test
    void fixoUsaOSinalParaEscolherOTipo() throws SQLException {
        tino.adicionarFixo(EU, 300_000, "Salário", null);

        assertEquals(1, tino.lancarFixos(EU, HOJE));
        assertEquals(300_000, tino.resumo(EU, "mes", HOJE).atual().ganhos());
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
                new Financas.Lancamento(1, HOJE, -123_456, "Moradia", "aluguel")));

        assertTrue(csv.startsWith("id,data,valor,categoria,descricao\n"));
        assertTrue(csv.contains("-1234.56"));
        assertTrue(csv.contains("\"Moradia\""));
    }

    @Test
    void csvEscapaAspasDaDescricao() {
        String csv = Tino.csv(List.of(
                new Financas.Lancamento(1, HOJE, -100, "Lazer", "show do \"grupo\"")));

        assertTrue(csv.contains("\"show do \"\"grupo\"\"\""));
    }

    @Test
    void csvNaoQuebraComDescricaoNula() {
        assertTrue(Tino.csv(List.of(
                new Financas.Lancamento(1, HOJE, -100, "Lazer", null))).endsWith(",\n"));
    }

    @Test
    void resumoTrazORitmoDoPeriodoEmAndamento() throws SQLException {
        tino.registrar(EU, -70_000, "Lazer", null, HOJE);

        assertEquals(310_000, tino.resumo(EU, "mes", HOJE).ritmo().projecao());
    }

    @Test
    void exportarTrazCsvSoDoUsuario() throws SQLException {
        tino.registrar(EU, -5_000, "Lazer", "cinema", HOJE);
        tino.registrar(OUTRO, -9_900, "Lazer", "segredo", HOJE);

        Tino.Exportacao x = tino.exportar(EU, "mes", HOJE);

        assertEquals(1, x.quantidade());
        assertEquals(LocalDate.of(2026, 8, 1), x.periodo().inicio());
        assertTrue(x.csv().contains("cinema"));
        assertFalse(x.csv().contains("segredo"));
    }

    @Test
    void exportarSemLancamentoTemQuantidadeZero() throws SQLException {
        assertEquals(0, tino.exportar(EU, "mes", HOJE).quantidade());
    }
}
