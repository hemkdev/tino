package tino.nucleo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Intervalo fechado de datas: um dia, uma semana (segunda a domingo), um mês ou um ano. */
public record Periodo(LocalDate inicio, LocalDate fim, ChronoUnit unidade) {

    public static Periodo de(Escala escala, LocalDate hoje) {
        return switch (escala) {
            case DIA -> new Periodo(hoje, hoje, ChronoUnit.DAYS);
            case SEMANA -> new Periodo(
                    hoje.with(DayOfWeek.MONDAY), hoje.with(DayOfWeek.SUNDAY), ChronoUnit.WEEKS);
            case ANO -> new Periodo(
                    hoje.withDayOfYear(1), hoje.withDayOfYear(hoje.lengthOfYear()), ChronoUnit.YEARS);
            case MES -> new Periodo(
                    hoje.withDayOfMonth(1), hoje.withDayOfMonth(hoje.lengthOfMonth()), ChronoUnit.MONTHS);
        };
    }

    public Periodo anterior() {
        return switch (unidade) {
            case DAYS -> new Periodo(inicio.minusDays(1), fim.minusDays(1), unidade);
            case WEEKS -> new Periodo(inicio.minusWeeks(1), fim.minusWeeks(1), unidade);
            case YEARS -> new Periodo(inicio.minusYears(1), fim.minusYears(1), unidade);
            default -> {
                LocalDate i = inicio.minusMonths(1);
                yield new Periodo(i, i.withDayOfMonth(i.lengthOfMonth()), unidade);
            }
        };
    }
}
