package br.rndsconnect.model;

import java.util.Objects;

/**
 * Alvo de um mapeamento terminológico: o par ({@code system}, {@code code}) exigido pela
 * RNDS, acompanhado do {@code display} oficial.
 *
 * <p><strong>Por que o display existe aqui e não no {@link CodedValue}.</strong> O perfil do
 * RIA-R proíbe {@code coding.display} em praticamente todos os elementos codificados — o
 * Builder emite apenas {@code system} e {@code code}. Mas o display é indispensável na
 * <em>tabela de mapeamento</em>: é o que permite a um profissional de saúde revisar a
 * correspondência sem consultar o CodeSystem. É o argumento P1 da Seção 3.1 tornado
 * concreto: um enfermeiro do PNI não revisa {@code "105" → "87"}, mas revisa
 * {@code "105" → "87" (COVID-19 PFIZER - COMIRNATY)}.
 *
 * <p>O display, portanto, existe para o humano e morre na fronteira da Camada 3.
 *
 * @param system URL canônica do CodeSystem da RNDS
 * @param code código dentro daquele CodeSystem
 * @param display rótulo oficial do código; pode ser nulo, mas não deveria
 * @param validatedBy quem validou clinicamente esta correspondência; nulo em domínios automáticos
 * @author Igor Mendes Domingues Miras
 */
public record CodedTarget(String system, String code, String display, String validatedBy) {

    public CodedTarget {
        Objects.requireNonNull(system, "system do CodedTarget não pode ser nulo");
        Objects.requireNonNull(code, "code do CodedTarget não pode ser nulo");
    }

    public CodedTarget(String system, String code, String display) {
        this(system, code, display, null);
    }

    /** @return o par que a Camada 3 consome, sem o display */
    public CodedValue toCodedValue() {
        return new CodedValue(system, code);
    }

    @Override
    public String toString() {
        return code + (display != null ? " (" + display + ")" : "") + " @ " + system;
    }
}
