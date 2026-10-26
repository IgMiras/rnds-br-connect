package br.rndsconnect.semantic.regras;

import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Elo 3 (A1) — nenhum evento clínico ocorre no futuro.
 *
 * <p>Data futura em registro clínico é quase sempre erro de digitação — ano trocado, fuso
 * invertido — e nenhum perfil FHIR a impede: {@code dateTime} aceita qualquer instante.
 * Um registro de vacinação datado de 2027 é estruturalmente perfeito e epidemiologicamente
 * inútil.
 *
 * <p>A tolerância existe por causa de fuso: um sistema em Rio Branco (UTC−5) enviando para um
 * servidor em UTC pode legitimamente produzir instante alguns minutos à frente do relógio de
 * quem valida. O padrão é de um dia, largo o bastante para qualquer fuso do país e estreito o
 * bastante para pegar erro de ano.
 *
 * @param <T> tipo do DTO
 * @author Igor Mendes Domingues Miras
 */
public class DataNaoFutura<T> implements RegraValidacao<T> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-3/DATA-FUTURA";

    private static final long TOLERANCIA_SEGUNDOS = 86_400L;

    private final Function<T, Instant> extrator;
    private final String campo;
    private final Supplier<Instant> agora;

    /**
     * @param campo nome do campo no DTO
     * @param extrator como obter o instante
     */
    public DataNaoFutura(String campo, Function<T, Instant> extrator) {
        this(campo, extrator, Instant::now);
    }

    /**
     * @param campo nome do campo no DTO
     * @param extrator como obter o instante
     * @param agora relógio, injetável para testes determinísticos
     */
    public DataNaoFutura(String campo, Function<T, Instant> extrator, Supplier<Instant> agora) {
        this.campo = Objects.requireNonNull(campo);
        this.extrator = Objects.requireNonNull(extrator);
        this.agora = Objects.requireNonNull(agora);
    }

    @Override
    public void validar(T alvo) {
        Instant valor = extrator.apply(alvo);
        if (valor == null) {
            return; // obrigatoriedade é do Builder; aqui só se valida o que existe
        }
        Instant limite = agora.get().plusSeconds(TOLERANCIA_SEGUNDOS);
        if (valor.isAfter(limite)) {
            throw new RndsValidationException(
                    campo,
                    REGRA,
                    "evento clínico datado no futuro — nenhum perfil FHIR impede isso, "
                            + "e o dado chega íntegro e inutilizável",
                    valor.toString());
        }
    }

    @Override
    public String identificacao() {
        return REGRA + "(" + campo + ")";
    }
}
