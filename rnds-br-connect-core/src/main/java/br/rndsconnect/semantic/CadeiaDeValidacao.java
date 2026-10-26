package br.rndsconnect.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chain of Responsibility da Frente A: uma sequência de regras aplicada a um alvo.
 *
 * <p><strong>Fail-fast, não coleta de erros.</strong> A primeira regra violada interrompe a
 * cadeia. Acumular todas as violações produziria uma lista em que a segunda em diante pode
 * ser consequência da primeira — e um diagnóstico com cinco erros dos quais quatro são
 * derivados é pior que um diagnóstico com um erro.
 *
 * <p>A cadeia é imutável e reutilizável entre chamadas.
 *
 * @param <T> tipo do alvo
 * @author Igor Mendes Domingues Miras
 */
public final class CadeiaDeValidacao<T> {

    private final List<RegraValidacao<T>> elos;

    private CadeiaDeValidacao(List<RegraValidacao<T>> elos) {
        this.elos = Collections.unmodifiableList(new ArrayList<>(elos));
    }

    /**
     * @param elos regras, na ordem de aplicação
     * @param <T> tipo do alvo
     * @return cadeia pronta
     */
    @SafeVarargs
    public static <T> CadeiaDeValidacao<T> de(RegraValidacao<T>... elos) {
        return new CadeiaDeValidacao<>(List.of(elos));
    }

    /** @return cadeia vazia, que aceita tudo */
    public static <T> CadeiaDeValidacao<T> vazia() {
        return new CadeiaDeValidacao<>(List.of());
    }

    /**
     * @param alvo objeto a validar
     * @throws RndsValidationException na primeira regra violada
     */
    public void validar(T alvo) {
        for (RegraValidacao<T> elo : elos) {
            elo.validar(alvo);
        }
    }

    /** @return quantidade de elos */
    public int size() {
        return elos.size();
    }

    /** @return os elos, em ordem */
    public List<RegraValidacao<T>> elos() {
        return elos;
    }
}
