package br.rndsconnect.semantic;

import br.rndsconnect.model.TerminologyDomain;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * Trilha de auditoria de uma tradução terminológica (ADR-04).
 *
 * <p>Objeto de primeira classe, e não um log. A diferença importa: um log é texto que alguém
 * pode ou não ter configurado, sujeito a rotação e a nível de severidade. Esta classe é
 * parte do valor de retorno da Camada 2 — se a tradução aconteceu, a proveniência existe.
 *
 * <p>Responde, para cada código enviado à base nacional: <strong>de onde veio, por qual
 * regra, com qual confiança e quem validou.</strong> Em saúde pública essa pergunta é feita
 * anos depois, quando um estudo epidemiológico encontra uma anomalia e alguém precisa
 * decidir se é achado clínico ou erro de integração.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class MappingProvenance {

    /** Domínio traduzido. */
    TerminologyDomain domain;

    /** Código no vocabulário local, exatamente como chegou. */
    String sourceValue;

    /** {@code system} do alvo na RNDS. */
    String targetSystem;

    /** {@code code} do alvo na RNDS. */
    String targetValue;

    /** Nome da classe de estratégia que produziu a tradução. */
    String strategyUsed;

    /**
     * Identificação verificável da fonte, com SHA-256 do conteúdo.
     *
     * @see MappingSource#getVersion()
     */
    String sourceVersion;

    /** Instante da tradução. */
    Instant mappedAt;

    /** Identificador do registro na base de origem. */
    String idLocalRegistro;

    /**
     * Posição do item dentro do registro, quando o serviço admite lista; {@code null} para
     * traduções do registro como um todo.
     *
     * @see MappingContext#indiceItem()
     */
    Integer indiceItem;

    /**
     * Confiança na correspondência.
     *
     * <p>{@code 1.0} para tabela explícita revisada. Valores menores existem para estratégias
     * inferenciais que o integrador venha a plugar — e o número é justamente o que permite
     * filtrar depois o que foi traduzido com certeza do que foi adivinhado.
     */
    double confidence;

    /**
     * Quem validou clinicamente a correspondência, quando declarado na fonte.
     *
     * <p>Nulo não significa "não validado": significa "a fonte não declara". A distinção
     * é relevante numa auditoria.
     */
    String validatedBy;

    /**
     * Linha única para log ou anexo de evidência.
     *
     * @return representação legível da tradução
     */
    public String toLinha() {
        return String.format(
                "%s%s: \"%s\" -> %s#%s | %s | %s | confianca=%.2f%s",
                domain,
                indiceItem != null ? "[" + indiceItem + "]" : "",
                sourceValue,
                targetSystem,
                targetValue,
                strategyUsed,
                sourceVersion,
                confidence,
                validatedBy != null ? " | validado por: " + validatedBy : "");
    }
}
