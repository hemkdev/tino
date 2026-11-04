package tino.nucleo;

/** Gasto ou ganho. Categorias de um lado nunca são usadas do outro. */
public enum Tipo {
    GASTO, GANHO;

    /** Como o tipo é gravado no banco. */
    String codigo() {
        return name().toLowerCase();
    }

    public static Tipo de(String codigo) {
        return valueOf(codigo.toUpperCase());
    }
}
