package tino.nucleo;

import java.util.Locale;

/** O tamanho do período consultado. */
public enum Escala {
    DIA, SEMANA, MES, ANO;

    /** Como a escala viaja em texto: opção de comando, id de botão. */
    public String codigo() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Escala de(String codigo) {
        for (Escala e : values()) {
            if (e.codigo().equals(codigo)) {
                return e;
            }
        }
        throw new IllegalArgumentException("período desconhecido: " + codigo);
    }
}
