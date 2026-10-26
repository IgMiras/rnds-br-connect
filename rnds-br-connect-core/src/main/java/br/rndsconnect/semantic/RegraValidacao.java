package br.rndsconnect.semantic;

/**
 * Um elo da Chain of Responsibility da Frente A.
 *
 * <p>Cada elo verifica uma regra e, ao falhar, lança {@link RndsValidationException} com
 * diagnóstico preciso. Elos não devolvem valor nem alteram o alvo: a Frente A valida, não
 * transforma.
 *
 * <p>O tipo genérico existe porque os elos operam sobre DTOs diferentes conforme o momento:
 * os de <strong>A1</strong> recebem o DTO canônico, no vocabulário local; os de
 * <strong>A2</strong> recebem o DTO enriquecido, com os códigos já traduzidos. Ver Seção
 * 7.1 da Arquitetura — a ordem é A1 → B → A2.
 *
 * @param <T> tipo do alvo da validação
 * @author Igor Mendes Domingues Miras
 */
@FunctionalInterface
public interface RegraValidacao<T> {

    /**
     * @param alvo objeto a validar
     * @throws RndsValidationException se a regra for violada
     */
    void validar(T alvo);

    /** @return identificação da regra, para log e diagnóstico */
    default String identificacao() {
        return getClass().getSimpleName();
    }
}
