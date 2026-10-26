package br.rndsconnect.semantic;

import br.rndsconnect.model.TerminologyDomain;

/**
 * Não existe correspondência registrada para um código local.
 *
 * <p><strong>Esta exceção é a tese do trabalho em forma executável.</strong>
 *
 * <p>A alternativa — repassar o código local quando não há correspondência — parece
 * inofensiva e é o comportamento que emerge naturalmente de um {@code Map.getOrDefault}. Ela
 * não é inofensiva. Considere o código {@code "105"}: em muitas instalações do OpenMRS ele
 * identifica a vacina COVID-19 da Pfizer. No {@code BRImunobiologico} da RNDS,
 * {@code "105"} é um código <strong>válido</strong> — e significa COVID-19 MODERNA SPIKEVAX
 * BIVALENTE.
 *
 * <p>Repassar o código produz um Bundle que a Camada 4 aprova, que a RNDS aceita com
 * {@code HTTP 200}, e que registra na base nacional um imunobiológico diferente do que foi
 * aplicado no paciente. Nenhuma camada de validação estrutural detecta isso, porque não há
 * nada de errado com a estrutura — está errado o significado.
 *
 * <p>Por isso a Camada 2 é uma função <em>total</em> sobre o domínio registrado: ou existe
 * correspondência, ou o fluxo para aqui. Não há {@code else}, não há valor padrão, não há
 * modo permissivo configurável. Um dado clínico ausente é um problema operacional; um dado
 * clínico errado é um problema de segurança do paciente.
 *
 * @author Igor Mendes Domingues Miras
 */
public class UnmappedTerminologyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient TerminologyDomain domain;
    private final String codigoLocal;
    private final String sourceVersion;

    /**
     * @param domain domínio em que a busca falhou
     * @param codigoLocal código que não tem correspondência
     * @param sourceVersion identificação da fonte consultada
     * @param totalEntradas quantas correspondências a fonte declara
     */
    public UnmappedTerminologyException(
            TerminologyDomain domain, String codigoLocal, String sourceVersion, int totalEntradas) {
        super(montarMensagem(domain, codigoLocal, sourceVersion, totalEntradas));
        this.domain = domain;
        this.codigoLocal = codigoLocal;
        this.sourceVersion = sourceVersion;
    }

    private static String montarMensagem(
            TerminologyDomain domain, String codigoLocal, String sourceVersion, int totalEntradas) {
        String nl = System.lineSeparator();
        return "Nenhuma correspondência registrada para o código local \"" + codigoLocal
                + "\" no domínio " + domain + "." + nl
                + "  Fonte consultada: " + sourceVersion + " (" + totalEntradas + " entradas)." + nl
                + nl
                + "  O framework NÃO repassa o código local. Um código sem correspondência pode ser" + nl
                + "  um código válido de OUTRO conceito no vocabulário da RNDS: o Bundle seria aceito" + nl
                + "  com HTTP 200 e registraria informação clínica errada, sem que nada detectasse." + nl
                + nl
                + "  Registre a correspondência na fonte de mapeamento do domínio " + domain + ".";
    }

    /** @return o domínio em que a busca falhou */
    public TerminologyDomain getDomain() {
        return domain;
    }

    /** @return o código local sem correspondência */
    public String getCodigoLocal() {
        return codigoLocal;
    }

    /** @return identificação da fonte consultada */
    public String getSourceVersion() {
        return sourceVersion;
    }
}
