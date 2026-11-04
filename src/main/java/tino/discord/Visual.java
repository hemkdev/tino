package tino.discord;

import tino.nucleo.Dinheiro;
import tino.nucleo.Lancamento;
import tino.nucleo.Linha;
import tino.nucleo.Periodo;
import tino.nucleo.Recorrente;
import tino.nucleo.Resumo;
import tino.nucleo.Tino;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    private static String intervalo(Periodo p) {
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
                .setAuthor(gasto ? "Anotado!" : "Entrou!")
                .setDescription("## %s\n%s %s".formatted(
                        Dinheiro.formatar(Math.abs(centavos)), emoji(categoria), categoria))
                .setFooter("#%d · %s".formatted(id, data.format(DIA_MES)));

        if (descricao != null && !descricao.isBlank()) {
            e.appendDescription("\n_" + descricao + "_");
        }
        if (acumulado > 0) {
            e.appendDescription("\n\nJá %s **%s** aqui no mês".formatted(
                    gasto ? "são" : "entraram", Dinheiro.formatar(acumulado)));
        }
        // Só alerta com histórico suficiente: um valor 5x acima do normal costuma
        // ser dedo gordo (5000 no lugar de 50,00).
        if (tipico > 0 && Math.abs(centavos) > tipico * 5) {
            e.appendDescription("\n⚠️ bem acima do de sempre por aqui (média %s) — confere aí, ou é só desfazer"
                    .formatted(Dinheiro.formatar(tipico)));
        }
        if (categoriaNova != null) {
            e.appendDescription("\n💡 criei **%s** — parece com **%s**, renomeie se foi engano"
                    .formatted(categoria, categoriaNova));
        }
        return e.build();
    }

    static MessageEmbed registro(Tino.Registro r) {
        return registro(r.centavos(), r.categoria(), r.descricao(), r.data(), r.id(),
                r.acumulado(), r.tipico(), r.parecida());
    }

    static MessageEmbed lote(List<Tino.Registro> registros) {
        List<String> linhas = new ArrayList<>();
        long totalGasto = 0;
        long totalGanho = 0;
        for (Tino.Registro r : registros) {
            if (r.centavos() > 0) {
                totalGanho += r.centavos();
            } else {
                totalGasto -= r.centavos();
            }
            linhas.add("`#%d` %s **%s** · %s".formatted(
                    r.id(), emoji(r.categoria()), comSinal(r.centavos()), r.categoria()));
        }
        return new EmbedBuilder()
                .setColor(totalGasto > totalGanho ? VERMELHO : VERDE)
                .setAuthor("Anotei %d de uma vez!".formatted(linhas.size()))
                .setDescription(String.join("\n", linhas))
                .setFooter("gastos %s · ganhos %s · veja em /lancamentos".formatted(
                        Dinheiro.formatar(totalGasto), Dinheiro.formatar(totalGanho)))
                .build();
    }

    // ---------------------------------------------------------------- resumo

    static MessageEmbed resumo(Periodo p, Resumo atual, Resumo anterior,
                               Tino.Ritmo ritmo) {
        String titulo = switch (p.unidade()) {
            case DAYS -> "Resumo de hoje";
            case WEEKS -> "Resumo da semana";
            case YEARS -> "Resumo do ano";
            default -> "Resumo do mês";
        };

        if (atual.vazio()) {
            return new EmbedBuilder()
                    .setColor(CINZA)
                    .setTitle("📊 " + titulo)
                    .setDescription("Nada por aqui ainda.\nManda um `50 mercado` que eu anoto.")
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
                        variacao(atual.despesas(), anterior.despesas()),
                        variacao(atual.ganhos(), anterior.ganhos())));

        if (ritmo != null) {
            e.addField("Ritmo", "%s por dia · projeção de **%s** até %s".formatted(
                    Dinheiro.formatar(ritmo.mediaDiaria()), Dinheiro.formatar(ritmo.projecao()),
                    p.fim().format(DIA_MES)), false);
        }
        return e.build();
    }

    /** Variação percentual. Sem base de comparação devolve travessão, nunca divisão por zero. */
    static String variacao(long atual, long anterior) {
        if (anterior == 0) {
            return "—";
        }
        double pct = (atual - anterior) * 100.0 / Math.abs(anterior);
        return String.format(Locale.of("pt", "BR"), "%+.1f%%", pct);
    }

    private static String listaCategorias(List<Linha> linhas, long total) {
        long maior = linhas.get(0).total();
        StringBuilder sb = new StringBuilder();
        for (Linha linha : linhas) {
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

    static MessageEmbed lancamentos(Periodo p, List<Lancamento> lista,
                                    int offset, boolean temProxima) {
        if (lista.isEmpty()) {
            return new EmbedBuilder()
                    .setColor(CINZA)
                    .setTitle("🧾 Lançamentos")
                    .setDescription("Nenhum lançamento nesse período.")
                    .setFooter(intervalo(p))
                    .build();
        }

        StringBuilder sb = new StringBuilder();
        for (Lancamento l : lista) {
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
    static Modal edicao(String usuario, Lancamento l) {
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

    static MessageEmbed categorias(Tino.Categorias c) {
        return new EmbedBuilder()
                .setColor(AZUL)
                .setTitle("🗂️ Categorias")
                .addField("Gastos · %d/%d principais, %d/%d sub".formatted(
                                c.raizGasto(), Tino.MAX_RAIZ, c.subGasto(), Tino.MAX_SUB),
                        arvore(c.gastos()), false)
                .addField("Ganhos · %d/%d principais, %d/%d sub".formatted(
                                c.raizGanho(), Tino.MAX_RAIZ, c.subGanho(), Tino.MAX_SUB),
                        arvore(c.ganhos()), false)
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

    static MessageEmbed recorrentes(List<Recorrente> lista) {
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
        for (Recorrente r : lista) {
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

    // --------------------------------------------------------- boas-vindas

    static MessageEmbed boasVindas() {
        return new EmbedBuilder()
                .setColor(VERDE)
                .setTitle("👋 Oi, eu sou o Tino!")
                .setDescription("""
                        Me manda o que você gastou e eu anoto. Assim, sem comando:
                        ```
                        50 mercado
                        50 mercado 07/08 pão e leite
                        +3000 salário
                        50 mercado, 30 uber, 12 café
                        ```
                        Depois é só perguntar: **quanto gastei esse mês?**, \
                        **resumo da semana**, **extrato de hoje**.""")
                .addField("Já deixei prontas",
                        "🍽️ Alimentação · 🚗 Transporte · 🏠 Moradia · 💊 Saúde\n"
                                + "🎮 Lazer · 📚 Educação · 📦 Outros\n"
                                + "💰 Salário · 💼 Freelance · 📈 Investimentos", false)
                .setFooter("digite ajuda quando quiser ver tudo que eu faço")
                .build();
    }

    // ----------------------------------------------------------------- ajuda

    static MessageEmbed ajuda() {
        return new EmbedBuilder()
                .setColor(AZUL)
                .setTitle("💡 Como falar comigo")
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
