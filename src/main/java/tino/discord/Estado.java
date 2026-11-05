package tino.discord;

/**
 * Como o Tino aparece na miniatura de cada resposta. Os GIFs moram em
 * {@code docs/marca/estados/} e são servidos pelo GitHub, então nenhuma mensagem
 * precisa anexar arquivo.
 */
enum Estado {
    OCIOSO, SUCESSO, ALERTA, ERRO, DORMINDO;

    private static final String BASE =
            "https://raw.githubusercontent.com/hemkdev/Tino/main/docs/marca/estados/";

    String url() {
        return BASE + name().toLowerCase() + ".gif";
    }

    /** Registro de madrugada pega o Tino dormindo; no resto do dia ele comemora. */
    static Estado aoRegistrar(boolean madrugada) {
        return madrugada ? DORMINDO : SUCESSO;
    }
}
