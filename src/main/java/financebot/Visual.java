package financebot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Monta o que o usuário vê. Nada aqui toca o banco. */
final class Visual {

    private static final int VERMELHO = 0xE74C3C;
    private static final int VERDE = 0x2ECC71;
    private static final int AZUL = 0x5865F2;
    private static final int CINZA = 0x99AAB5;
    private static final int AMARELO = 0xFEE75C;

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final Locale BR = Locale.of("pt", "BR");
    private static final int LARGURA_BARRA = 8;

    private static final Map<String, String> EMOJIS = Map.ofEntries(
            Map.entry("alimentação", "🍽️"),
            Map.entry("transporte", "🚗"),
            Map.entry("moradia", "🏠"),
            Map.entry("saúde", "💊"),
            Map.entry("lazer", "🎮"),
            Map.entry("educação", "📚"),
            Map.entry("outros", "📦"),
            Map.entry("salário", "💰"),
            Map.entry("freelance", "💼"),
            Map.entry("investimentos", "📈"));

    private Visual() {}

    // ----------------------------------------------------------------- peças

    /** Emoji da categoria raiz; subcategoria herda o do pai. */
    static String emoji(String caminho) {
        int corte = caminho.indexOf('>');
        String raiz = (corte < 0 ? caminho : caminho.substring(0, corte)).trim();
        return EMOJIS.getOrDefault(raiz.toLowerCase(BR), "🔸");
    }

    /**
     * Barra proporcional à maior linha, não ao total — serve para comparar
     * categorias entre si de relance. O percentual ao lado é sobre o total.
     */
    static String barra(long valor, long maior) {
        if (maior <= 0) {
            return "▱".repeat(LARGURA_BARRA);
        }
        int cheios = (int) Math.round((double) valor * LARGURA_BARRA / maior);
        cheios = Math.clamp(cheios, 1, LARGURA_BARRA);
        return "▰".repeat(cheios) + "▱".repeat(LARGURA_BARRA - cheios);
    }

    private static String intervalo(Financas.Periodo p) {
        return "%s a %s".formatted(p.inicio().format(DIA_MES), p.fim().format(DIA_MES));
    }

    private static String comSinal(long centavos) {
        return (centavos < 0 ? "-" : "+") + Dinheiro.formatar(Math.abs(centavos));
    }

    // ------------------------------------------------------------- registros

    static MessageEmbed registro(long centavos, String categoria, String descricao,
                                 java.time.LocalDate data, long id,
                                 long acumulado, long tipico, String categoriaNova) {
        boolean gasto = centavos < 0;
        EmbedBuilder e = new EmbedBuilder()
                .setColor(gasto ? VERMELHO : VERDE)
                .setAuthor(gasto ? "Gasto registrado" : "Ganho registrado")
                .setDescription("## %s\n%s %s".formatted(
                        Dinheiro.formatar(Math.abs(centavos)), emoji(categoria), categoria))
                .setFooter("#%d · %s".formatted(id, data.format(DIA_MES)));

        if (descricao != null && !descricao.isBlank()) {
            e.appendDescription("\n_" + descricao + "_");
        }
        if (acumulado > 0) {
            e.appendDescription("\n\n**%s** %s no mês".formatted(
                    Dinheiro.formatar(acumulado), gasto ? "gastos" : "ganhos"));
        }
        // Só alerta com histórico suficiente: um valor 5x acima do normal costuma
        // ser dedo gordo (5000 no lugar de 50,00).
        if (tipico > 0 && Math.abs(centavos) > tipico * 5) {
            e.appendDescription("\n⚠️ bem acima do normal aqui (média %s) — confira, ou use Desfazer"
                    .formatted(Dinheiro.formatar(tipico)));
        }
        if (categoriaNova != null) {
            e.appendDescription("\n💡 criei a categoria **%s** — parecida com **%s**, renomeie se foi engano"
                    .formatted(categoria, categoriaNova));
        }
        return e.build();
    }

    static MessageEmbed lote(List<String> linhas, long totalGasto, long totalGanho) {
        return new EmbedBuilder()
                .setColor(totalGasto > totalGanho ? VERMELHO : VERDE)
                .setAuthor("%d lançamentos registrados".formatted(linhas.size()))
                .setDescription(String.join("\n", linhas))
                .setFooter("gastos %s · ganhos %s · veja em /lancamentos".formatted(
                        Dinheiro.formatar(totalGasto), Dinheiro.formatar(totalGanho)))
                .build();
    }

    // ---------------------------------------------------------------- resumo

    static MessageEmbed resumo(Financas.Periodo p, Financas.Resumo atual, Financas.Resumo anterior,
                               LocalDate hoje) {
        String titulo = switch (p.unidade()) {
            case WEEKS -> "Resumo da semana";
            case YEARS -> "Resumo do ano";
            default -> "Resumo do mês";
        };

        if (atual.vazio()) {
            return new EmbedBuilder()
                    .setColor(CINZA)
                    .setTitle("📊 " + titulo)
                    .setDescription("Nenhum lançamento por aqui ainda.\nComece com `/gasto` ou `/ganho`.")
                    .setFooter(intervalo(p))
                    .build();
        }

        StringBuilder corpo = new StringBuilder();
        if (!atual.porDespesa().isEmpty()) {
            corpo.append("**Gastos**\n").append(listaCategorias(atual.porDespesa(), atual.despesas()));
        }
        if (!atual.porGanho().isEmpty()) {
            if (!corpo.isEmpty()) {
                corpo.append('\n');
            }
            corpo.append("**Ganhos**\n").append(listaCategorias(atual.porGanho(), atual.ganhos()));
        }

        EmbedBuilder e = new EmbedBuilder()
                .setColor(atual.saldo() >= 0 ? VERDE : VERMELHO)
                .setTitle("📊 " + titulo)
                .setDescription(corpo.toString())
                .addField("Ganhos", Dinheiro.formatar(atual.ganhos()), true)
                .addField("Gastos", Dinheiro.formatar(atual.despesas()), true)
                .addField("Saldo", comSinal(atual.saldo()), true)
                .setFooter("%s · gastos %s · ganhos %s (vs. período anterior)".formatted(
                        intervalo(p),
                        Financas.variacao(atual.despesas(), anterior.despesas()),
                        Financas.variacao(atual.ganhos(), anterior.ganhos())));

        String ritmo = ritmo(p, atual.despesas(), hoje);
        if (ritmo != null) {
            e.addField("Ritmo", ritmo, false);
        }
        return e.build();
    }

    /**
     * Média diária e projeção até o fim do período. Só faz sentido com o período
     * em andamento — período encerrado já tem o total real, não uma estimativa.
     */
    static String ritmo(Financas.Periodo p, long despesas, LocalDate hoje) {
        if (despesas <= 0 || hoje.isBefore(p.inicio()) || hoje.isAfter(p.fim())) {
            return null;
        }
        long decorridos = ChronoUnit.DAYS.between(p.inicio(), hoje) + 1;
        long totais = ChronoUnit.DAYS.between(p.inicio(), p.fim()) + 1;
        if (decorridos >= totais) {
            return null;   // último dia: a projeção seria o próprio total
        }
        long media = despesas / decorridos;
        return "%s por dia · projeção de **%s** até %s".formatted(
                Dinheiro.formatar(media), Dinheiro.formatar(media * totais), p.fim().format(DIA_MES));
    }

    private static String listaCategorias(List<Financas.Linha> linhas, long total) {
        long maior = linhas.get(0).total();
        StringBuilder sb = new StringBuilder();
        for (Financas.Linha linha : linhas) {
            sb.append(String.format(BR, "%s `%s` **%s** · %s · %.1f%%%n",
                    emoji(linha.categoria()),
                    barra(linha.total(), maior),
                    linha.categoria(),
                    Dinheiro.formatar(linha.total()),
                    100.0 * linha.total() / total));
        }
        return sb.toString();
    }

    // ----------------------------------------------------------- lançamentos

    static MessageEmbed lancamentos(Financas.Periodo p, List<Financas.Lancamento> lista,
                                    int offset, boolean temProxima) {
        if (lista.isEmpty()) {
            return new EmbedBuilder()
                    .setColor(CINZA)
                    .setTitle("🧾 Lançamentos")
                    .setDescription("Nada registrado neste período.")
                    .setFooter(intervalo(p))
                    .build();
        }

        StringBuilder sb = new StringBuilder();
        for (Financas.Lancamento l : lista) {
            sb.append("`#%d` `%s` %s **%s** · %s".formatted(
                    l.id(),
                    l.data().format(DIA_MES),
                    emoji(l.categoria()),
                    comSinal(l.centavos()),
                    l.categoria()));
            if (l.descricao() != null && !l.descricao().isBlank()) {
                sb.append(" — _").append(l.descricao()).append('_');
            }
            sb.append('\n');
        }

        String faixa = "%d–%d".formatted(offset + 1, offset + lista.size());
        String rodape = "%s · itens %s%s · apague com /remover".formatted(
                intervalo(p), faixa, temProxima ? "" : " (fim)");

        return new EmbedBuilder()
                .setColor(AZUL)
                .setTitle("🧾 Lançamentos")
                .setDescription(sb.toString())
                .setFooter(rodape)
                .build();
    }

    // --------------------------------------------------------------- botões

    /**
     * O contexto viaja no id do botão, não em memória: sobrevive a reinício do
     * bot e não deixa mapa de telas abandonadas crescendo. Carrega o dono da
     * interação para recusar clique de terceiros.
     */
    static ActionRow acoesDoRegistro(String usuario, long lancamentoId) {
        return ActionRow.of(
                Button.danger("desfazer:%s:%d".formatted(usuario, lancamentoId), "Desfazer"),
                Button.secondary("editar:%s:%d".formatted(usuario, lancamentoId), "Editar"));
    }

    /** Modal já preenchido: campo em branco apaga a descrição, campo intocado mantém. */
    static Modal edicao(String usuario, Financas.Lancamento l) {
        return Modal.create("edicao:%s:%d".formatted(usuario, l.id()), "Editar #" + l.id())
                .addComponents(
                        linha("valor", "Valor", Dinheiro.formatar(Math.abs(l.centavos()))
                                .replace("R$ ", ""), true),
                        linha("categoria", "Categoria", l.categoria(), true),
                        linha("descricao", "Descrição", l.descricao(), false),
                        linha("data", "Data (DD/MM ou AAAA-MM-DD)", l.data().toString(), true))
                .build();
    }

    private static ActionRow linha(String id, String rotulo, String valor, boolean obrigatorio) {
        TextInput.Builder campo = TextInput.create(id, rotulo, TextInputStyle.SHORT)
                .setRequired(obrigatorio);
        if (valor != null && !valor.isBlank()) {
            campo.setValue(valor);
        }
        return ActionRow.of(campo.build());
    }

    static ActionRow paginacao(String usuario, String periodo, int offset, boolean temProxima,
                               int passo) {
        return ActionRow.of(
                Button.secondary("pag:%s:%s:%d".formatted(usuario, periodo, Math.max(0, offset - passo)),
                                "◀ Anterior")
                        .withDisabled(offset == 0),
                Button.secondary("pag:%s:%s:%d".formatted(usuario, periodo, offset + passo),
                                "Próximo ▶")
                        .withDisabled(!temProxima));
    }

    // ------------------------------------------------------------ categorias

    static MessageEmbed categorias(List<String> gastos, int raizGasto, List<String> ganhos,
                                   int raizGanho, int subGasto, int subGanho) {
        return new EmbedBuilder()
                .setColor(AZUL)
                .setTitle("🗂️ Categorias")
                .addField("Gastos · %d/%d principais, %d/%d sub".formatted(
                                raizGasto, Financas.MAX_RAIZ, subGasto, Financas.MAX_SUB),
                        arvore(gastos), false)
                .addField("Ganhos · %d/%d principais, %d/%d sub".formatted(
                                raizGanho, Financas.MAX_RAIZ, subGanho, Financas.MAX_SUB),
                        arvore(ganhos), false)
                .setFooter("gerencie com /categoria criar, renomear ou excluir")
                .build();
    }

    private static String arvore(List<String> caminhos) {
        if (caminhos.isEmpty()) {
            return "_nenhuma_";
        }
        StringBuilder sb = new StringBuilder();
        for (String caminho : caminhos) {
            int corte = caminho.indexOf('>');
            if (corte < 0) {
                sb.append(emoji(caminho)).append(' ').append(caminho).append('\n');
            } else {
                sb.append("　↳ ").append(caminho.substring(corte + 1).trim()).append('\n');
            }
        }
        return sb.toString();
    }

    // ----------------------------------------------------------- recorrentes

    static MessageEmbed recorrentes(List<Financas.Recorrente> lista) {
        if (lista.isEmpty()) {
            return new EmbedBuilder()
                    .setColor(CINZA)
                    .setTitle("🔁 Fixos")
                    .setDescription("Nenhum lançamento fixo.\n"
                            + "Cadastre com `/fixo adicionar` e lance o mês inteiro de uma vez.")
                    .build();
        }
        StringBuilder sb = new StringBuilder();
        long total = 0;
        for (Financas.Recorrente r : lista) {
            total += r.centavos();
            sb.append("`#%d` %s **%s** · %s".formatted(
                    r.id(), emoji(r.categoria()), comSinal(r.centavos()), r.categoria()));
            if (r.descricao() != null && !r.descricao().isBlank()) {
                sb.append(" — _").append(r.descricao()).append('_');
            }
            sb.append('\n');
        }
        return new EmbedBuilder()
                .setColor(AZUL)
                .setTitle("🔁 Fixos")
                .setDescription(sb.toString())
                .setFooter("somam %s · lance todos com /fixo lancar".formatted(comSinal(total)))
                .build();
    }

    // ------------------------------------------------------------------ CSV

    /** Uma linha por lançamento, com cabeçalho. Ponto decimal para abrir em planilha. */
    static String csv(List<Financas.Lancamento> lista) {
        StringBuilder sb = new StringBuilder("id,data,valor,categoria,descricao\n");
        for (Financas.Lancamento l : lista) {
            sb.append("%d,%s,%s,%s,%s%n".formatted(
                    l.id(),
                    l.data(),
                    java.math.BigDecimal.valueOf(l.centavos(), 2).toPlainString(),
                    aspas(l.categoria()),
                    aspas(l.descricao())));
        }
        return sb.toString();
    }

    private static String aspas(String valor) {
        return valor == null ? "" : '"' + valor.replace("\"", "\"\"") + '"';
    }

    // ----------------------------------------------------------------- ajuda

    static MessageEmbed ajuda() {
        return new EmbedBuilder()
                .setColor(AZUL)
                .setTitle("💡 Como usar")
                .setDescription("""
                        Na conversa comigo, é só digitar — sem barra:
                        ```
                        valor  categoria  [data]  [descrição]

                        50 mercado
                        50 mercado 07/08
                        50 mercado 07/08 pão e leite
                        50 mercado pão e leite
                        +3000 salário
                        50 mercado, 30 uber, 12 café
                        ```
                        O `+` marca ganho; sem sinal é gasto. Depois da categoria, \
                        campo que começa com número é data, com letra é descrição — \
                        por isso a data vem antes.""")
                .addField("Registrar",
                        "`/gasto 50 mercado` · `/ganho 3000 salário`\n"
                                + "Categoria é opcional — sem ela, vai para Outros.\n"
                                + "`/lancar` aceita vários de uma vez.", false)
                .addField("Ver",
                        "`/resumo` — por categoria, com projeção do mês\n"
                                + "`/lancamentos` — lista com os ids\n"
                                + "`/exportar` — planilha CSV", false)
                .addField("Organizar",
                        "`/categoria listar` · `criar` · `renomear` · `excluir`\n"
                                + "Subcategoria: escreva `Alimentação > Mercado`\n"
                                + "`/fixo` — aluguel e assinaturas, lançados de uma vez", false)
                .addField("Corrigir",
                        "Botão **Desfazer** e **Editar** logo abaixo de cada registro\n"
                                + "`/remover 12` apaga pelo id", false)
                .addField("Formatos aceitos",
                        "Valor: `50` · `50,00` · `1.234,56` · `R$ 50`\n"
                                + "Data: `DD/MM` ou `AAAA-MM-DD` (padrão: hoje)", false)
                .build();
    }

    // ------------------------------------------------------------- avulsos

    static MessageEmbed ok(String mensagem) {
        return new EmbedBuilder().setColor(VERDE).setDescription("✅ " + mensagem).build();
    }

    static MessageEmbed aviso(String mensagem) {
        return new EmbedBuilder().setColor(AMARELO).setDescription("⚠️ " + mensagem).build();
    }

    static MessageEmbed erro(String mensagem) {
        return new EmbedBuilder().setColor(VERMELHO).setDescription("❌ " + mensagem).build();
    }
}
