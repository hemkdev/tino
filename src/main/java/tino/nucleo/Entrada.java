package tino.nucleo;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta o que o usuário digita em texto corrido.
 *
 * <p>Formato: {@code [+]valor categoria [data] [descrição]}, com espaço separando
 * os campos e vários lançamentos separados por vírgula. O {@code +} marca ganho;
 * sem sinal é gasto, que é o caso comum.
 *
 * <p>Depois da categoria, o campo é lido como data quando começa com dígito e
 * como descrição caso contrário — por isso a data, se houver, vem antes.
 *
 * <p>Nome de categoria com espaço vale sem aspas quando ela já existe; para criar
 * uma nova, use aspas: {@code 50 "bem estar" 07/08}.
 *
 * <pre>
 * 50 mercado
 * 50 mercado 07/08
 * 50 mercado 07/08 pão e leite
 * 50 mercado pão e leite
 * 50 alimentação &gt; mercado 07/08 feira
 * +3000 salário
 * 50 mercado, 30 uber, 12 café
 * </pre>
 */
public final class Entrada {

    private static final String EXEMPLO = "não entendi — tente `50 mercado` ou `+3000 salário`";

    private static final Locale BR = Locale.of("pt", "BR");

    /** Conectivos ficam em minúscula no meio do nome: "Cartão de Crédito". */
    private static final Set<String> CONECTIVOS =
            Set.of("de", "da", "do", "das", "dos", "e", "com", "para", "em", "no", "na");

    /** Campos separados por espaço, com aspas agrupando: {@code "bem estar"} é um campo só. */
    private static final Pattern CAMPO = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    /** Quantos campos seguidos vale a pena testar como nome de categoria composta. */
    private static final int MAX_CAMPOS_CATEGORIA = 4;

    public record Item(long centavos, String categoria, String data, String descricao, boolean ganho) {}

    private Entrada() {}

    /**
     * Forma canônica do nome de uma categoria. Como a comparação passa a ser
     * entre nomes já normalizados, "MERCADO" e "mercado" viram o mesmo — inclusive
     * com acento, que o COLLATE NOCASE do SQLite não cobre.
     */
    static String capitalizar(String nome) {
        if (nome == null) {
            return null;
        }
        StringBuilder saida = new StringBuilder();
        String[] palavras = nome.trim().toLowerCase(BR).split("\\s+");
        for (int i = 0; i < palavras.length; i++) {
            if (palavras[i].isEmpty()) {
                continue;
            }
            if (!saida.isEmpty()) {
                saida.append(' ');
            }
            saida.append(i > 0 && CONECTIVOS.contains(palavras[i])
                    ? palavras[i]
                    : Character.toUpperCase(palavras[i].charAt(0)) + palavras[i].substring(1));
        }
        return saida.toString();
    }

    static List<Item> lista(String texto) {
        return lista(texto, Set.of());
    }

    /**
     * @param conhecidas categorias já existentes do usuário, para reconhecer nomes
     *                   compostos sem exigir aspas. Pode vir vazio.
     */
    static List<Item> lista(String texto, Set<String> conhecidas) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(EXEMPLO);
        }
        List<Item> itens = new ArrayList<>();
        for (String parte : texto.split("(?<!\\d),|,(?!\\d)")) {
            if (!parte.isBlank()) {
                itens.add(item(parte, conhecidas));
            }
        }
        if (itens.isEmpty()) {
            throw new IllegalArgumentException(EXEMPLO);
        }
        return itens;
    }

    public static Item item(String texto) {
        return item(texto, Set.of());
    }

    static Item item(String texto, Set<String> conhecidas) {
        String resto = texto.trim();

        boolean ganho = resto.startsWith("+");
        if (ganho || resto.startsWith("-")) {
            resto = resto.substring(1).trim();
        }

        List<String> campos = campos(resto);
        if (campos.isEmpty()) {
            throw new IllegalArgumentException(EXEMPLO);
        }
        long centavos = Dinheiro.paraCentavos(campos.get(0));   // lança com mensagem própria

        int i = 1;
        String categoria = null;
        if (i < campos.size()) {
            int consumidos = casarConhecida(campos, i, conhecidas);
            if (consumidos > 0) {
                categoria = String.join(" ", campos.subList(i, i + consumidos));
                i += consumidos;
            } else {
                // "Alimentação > Mercado" ocupa três campos; o ">" costura os pedaços.
                StringBuilder caminho = new StringBuilder(campos.get(i++));
                while (i < campos.size() && (campos.get(i).startsWith(">")
                        || caminho.toString().endsWith(">"))) {
                    caminho.append(' ').append(campos.get(i++));
                }
                categoria = caminho.toString();
            }
        }

        // Começa com dígito, é data; começa com letra, já é a descrição.
        String data = null;
        if (i < campos.size() && campos.get(i).isEmpty()) {
            i++;   // aspas vazias: campo ausente
        }
        if (i < campos.size() && !campos.get(i).isEmpty() && Character.isDigit(campos.get(i).charAt(0))) {
            data = campos.get(i++);
        }

        if (i < campos.size() && campos.get(i).equals("-")) {
            i++;   // "50 mercado - pão" continua funcionando
        }
        String descricao = i < campos.size()
                ? String.join(" ", campos.subList(i, campos.size()))
                : null;

        return new Item(centavos,
                categoria == null || categoria.isBlank() ? null : categoria,
                data,
                descricao == null || descricao.isBlank() ? null : descricao,
                ganho);
    }

    /** Quantos campos a partir de {@code i} formam uma categoria que já existe. */
    private static int casarConhecida(List<String> campos, int i, Set<String> conhecidas) {
        if (conhecidas.isEmpty()) {
            return 0;
        }
        int maximo = Math.min(MAX_CAMPOS_CATEGORIA, campos.size() - i);
        // Do maior para o menor: "Bem Estar Animal" ganha de "Bem Estar".
        for (int n = maximo; n >= 1; n--) {
            if (conhecidas.contains(capitalizar(String.join(" ", campos.subList(i, i + n))))) {
                return n;
            }
        }
        return 0;
    }

    private static List<String> campos(String texto) {
        List<String> campos = new ArrayList<>();
        Matcher m = CAMPO.matcher(texto);
        while (m.find()) {
            campos.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return campos;
    }

    /** Vale a pena tratar como lançamento? Usado para ignorar conversa solta na DM. */
    public static boolean pareceLancamento(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String primeiro = texto.trim().replaceFirst("^[+-]", "").trim().split("\\s+")[0];
        return primeiro.matches("[0-9]+([.,][0-9]+)*");
    }

    // --------------------------------------------------------------- consulta

    /** O que a pessoa quis ver, quando escreveu em vez de usar um comando. */
    public record Consulta(String tipo, String periodo) {}

    // Listas de entradas, e não Map.of, porque a ordem desempata: Map.of itera em ordem aleatória.
    private static final List<Map.Entry<String, List<String>>> GATILHOS = List.of(
            Map.entry("resumo", List.of("resumo", "quanto gastei", "quanto ganhei", "gastos", "gasto total",
                    "saldo", "balanco", "relatorio", "como estou", "situacao")),
            Map.entry("lancamentos", List.of("lancamentos", "lancamento", "extrato", "ultimos", "listar",
                    "historico", "o que gastei")),
            Map.entry("categorias", List.of("categorias", "categoria")),
            Map.entry("fixos", List.of("fixos", "fixo", "recorrentes", "assinaturas")),
            Map.entry("ajuda", List.of("ajuda", "help", "como usa", "como funciona", "comandos", "o que faz")));

    /** Do mais curto para o mais longo: com dois períodos na frase, o mais específico vence. */
    private static final List<Map.Entry<String, List<String>>> PERIODOS = List.of(
            Map.entry("dia", List.of("hoje", "de hoje", "do dia")),
            Map.entry("semana", List.of("semana")),
            Map.entry("mes", List.of("mes")),
            Map.entry("ano", List.of("ano")));

    /**
     * Reconhece pedidos escritos em texto: "resumo", "quanto gastei essa semana",
     * "extrato de hoje". Devolve {@code null} quando não é nada disso — conversa
     * solta não deve gerar resposta.
     */
    public static Consulta consulta(String texto) {
        if (texto == null || texto.isBlank() || texto.length() > 120) {
            return null;
        }
        String limpo = semAcento(texto.toLowerCase(BR)).replaceAll("[?!.,]", " ").trim();

        String tipo = null;
        int melhor = -1;
        for (var entrada : GATILHOS) {
            for (String gatilho : entrada.getValue()) {
                // O gatilho mais longo ganha: "quanto gastei" vence "gastos".
                if (contemPalavra(limpo, gatilho) && gatilho.length() > melhor) {
                    melhor = gatilho.length();
                    tipo = entrada.getKey();
                }
            }
        }
        if (tipo == null) {
            return null;
        }

        for (var entrada : PERIODOS) {
            for (String palavra : entrada.getValue()) {
                if (contemPalavra(limpo, palavra)) {
                    return new Consulta(tipo, entrada.getKey());
                }
            }
        }
        return new Consulta(tipo, "mes");
    }

    /** Evita que "categoria" case dentro de outra palavra maior. */
    private static boolean contemPalavra(String texto, String alvo) {
        return (" " + texto + " ").contains(" " + alvo + " ")
                || texto.startsWith(alvo + " ")
                || texto.endsWith(" " + alvo)
                || texto.equals(alvo);
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // ----------------------------------------------------------------- datas

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d/M/yyyy");

    /** Aceita "DD/MM", "AAAA-MM-DD" ou vazio (hoje). Rejeita data futura. */
    public static LocalDate data(String texto, LocalDate hoje) {
        if (texto == null || texto.isBlank()) {
            return hoje;
        }
        String limpo = texto.trim();
        if (limpo.matches("\\d{1,2}")) {
            limpo = limpo + "/" + hoje.getMonthValue();   // só o dia: mês corrente
        }
        LocalDate data;
        try {
            data = limpo.contains("/")
                    ? LocalDate.parse(limpo + "/" + hoje.getYear(), DIA_MES)
                    : LocalDate.parse(limpo);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("data inválida: use DD/MM ou AAAA-MM-DD");
        }
        // "25/12" digitado em janeiro é dezembro do ano passado, não do que vem.
        if (data.isAfter(hoje) && limpo.contains("/")) {
            data = data.minusYears(1);
        }
        if (data.isAfter(hoje)) {
            throw new IllegalArgumentException("a data não pode ser futura");
        }
        return data;
    }
}
