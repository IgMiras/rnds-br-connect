package br.rndsconnect.semantic.regras;

import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;

/**
 * Elo 3b (A1) — dois instantes de um mesmo registro precisam respeitar a ordem causal.
 *
 * <p>No REL, o laudo não pode ser liberado antes de o exame ser realizado. O perfil declara
 * {@code Observation.effective[x]} e {@code Observation.issued} como {@code 1..1} e não diz
 * nada sobre a relação entre eles — um laudo emitido antes da coleta passa em qualquer
 * validador FHIR.
 *
 * @param <T> tipo do DTO
 * @author Igor Mendes Domingues Miras
 */
public class OrdemCronologica<T> implements RegraValidacao<T> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-3B/ORDEM-CRONOLOGICA";

    private final String campoAnterior;
    private final String campoPosterior;
    private final Function<T, Instant> anterior;
    private final Function<T, Instant> posterior;

    /**
     * @param campoAnterior nome do campo que deve vir antes
     * @param anterior extrator do instante anterior
     * @param campoPosterior nome do campo que deve vir depois
     * @param posterior extrator do instante posterior
     */
    public OrdemCronologica(
            String campoAnterior,
            Function<T, Instant> anterior,
            String campoPosterior,
            Function<T, Instant> posterior) {
        this.campoAnterior = Objects.requireNonNull(campoAnterior);
        this.campoPosterior = Objects.requireNonNull(campoPosterior);
        this.anterior = Objects.requireNonNull(anterior);
        this.posterior = Objects.requireNonNull(posterior);
    }

    @Override
    public void validar(T alvo) {
        Instant a = anterior.apply(alvo);
        Instant b = posterior.apply(alvo);
        if (a == null || b == null) {
            return;
        }
        if (b.isBefore(a)) {
            throw new RndsValidationException(
                    campoPosterior,
                    REGRA,
                    campoPosterior + " é anterior a " + campoAnterior + " (" + a + ") — inversão causal",
                    b.toString());
        }
    }

    @Override
    public String identificacao() {
        return REGRA + "(" + campoAnterior + " ≤ " + campoPosterior + ")";
    }
}
