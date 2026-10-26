package br.rndsconnect.semantic;

import br.rndsconnect.model.TerminologyDomain;

/**
 * Contrato 3 da arquitetura — ponto de extensão do mapeamento terminológico (ADR-02).
 *
 * <p>É aqui que mora a contribuição do trabalho. A tradução entre vocabulário local e
 * vocabulário nacional não é detalhe de implementação a ser resolvido com um {@code switch}
 * no meio do código de envio: é uma <strong>decisão arquitetural</strong> que varia por
 * instituição, muda com o tempo, precisa de revisão clínica e precisa ser auditável.
 *
 * <p>Uma implementação pode ler uma tabela YAML, consultar um servidor de terminologia FHIR,
 * chamar um serviço interno da instituição ou combinar as três coisas. O framework não
 * sabe e não precisa saber — ele exige apenas que a operação seja <strong>total</strong>
 * sobre o domínio: ou devolve um alvo, ou lança {@link UnmappedTerminologyException}.
 *
 * @author Igor Mendes Domingues Miras
 */
public interface SemanticMappingStrategy {

    /**
     * Traduz um código local para o vocabulário da RNDS.
     *
     * @param rawValue código no vocabulário local
     * @param context contexto da operação, para a proveniência
     * @return o alvo e a trilha de auditoria
     * @throws UnmappedTerminologyException se não houver correspondência — nunca devolva um
     *     valor padrão nem repasse o código local
     */
    MappedValue map(RawValue rawValue, MappingContext context);

    /**
     * @param domain domínio a verificar
     * @return {@code true} se esta estratégia atende o domínio
     */
    boolean supports(TerminologyDomain domain);
}
