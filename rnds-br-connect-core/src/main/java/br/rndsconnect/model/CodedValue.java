package br.rndsconnect.model;

import java.util.Objects;

/**
 * Um código terminológico já traduzido para o vocabulário oficial da RNDS.
 *
 * <p>O alvo do mapeamento <strong>não é um código isolado, e sim o par
 * ({@code system}, {@code code})</strong>. Três ValueSets do REL compõem mais de um
 * CodeSystem — {@code BRNomeExame-2.0} (LOINC ou GAL), {@code BRTipoAmostra-1.0}
 * (HL7 v2-0487 ou BRTipoAmostraGAL) e {@code BRResultadoQualitativoExame-2.0}
 * (CodeSystem base mais oito {@code BRTipoResultado*}) —, de modo que um código sem o
 * {@code system} correto reprova o binding {@code required} na Camada 4. Por isso este
 * tipo, e não {@code String}, é a moeda de troca entre as Camadas 2 e 3.
 *
 * @param system URL canônica do CodeSystem
 * @param code código dentro daquele CodeSystem
 * @author Igor Mendes Domingues Miras
 */
public record CodedValue(String system, String code) {

    public CodedValue {
        Objects.requireNonNull(system, "system do CodedValue não pode ser nulo");
        Objects.requireNonNull(code, "code do CodedValue não pode ser nulo");
    }

    /**
     * Atalho para CodeSystems do domínio da RNDS.
     *
     * @param nomeCodeSystem nome do CodeSystem, sem a URL base (ex.: {@code BRImunobiologico})
     * @param code o código
     * @return o par pronto
     */
    public static CodedValue rnds(String nomeCodeSystem, String code) {
        return new CodedValue("http://www.saude.gov.br/fhir/r4/CodeSystem/" + nomeCodeSystem, code);
    }
}
