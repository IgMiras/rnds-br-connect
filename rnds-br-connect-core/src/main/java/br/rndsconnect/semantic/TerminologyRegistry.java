package br.rndsconnect.semantic;

import br.rndsconnect.model.TerminologyDomain;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registro das estratégias de mapeamento, uma por domínio.
 *
 * <p>É o ponto onde o sistema cliente declara <em>como</em> seu vocabulário se traduz para o
 * da RNDS. Montado uma vez na inicialização da aplicação e consultado a cada envio.
 *
 * <p><strong>Domínio sem estratégia registrada é erro, não omissão.</strong> Se o fluxo
 * precisa traduzir {@code VACINA} e ninguém registrou uma estratégia para {@code VACINA}, o
 * framework recusa — em vez de deixar o campo vazio, adivinhar, ou repassar o código local.
 * Vale a mesma lógica do {@link UnmappedTerminologyException}: o silêncio é o modo de falha
 * perigoso.
 *
 * <p>Uso típico:
 *
 * <pre>{@code
 * TerminologyRegistry registry = TerminologyRegistry.novo()
 *     .registrar(new SourceBackedMappingStrategy(
 *             new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml")))
 *     .registrar(new SourceBackedMappingStrategy(
 *             new PropertiesMappingSource(TerminologyDomain.DOSE, "mapeamentos/doses.properties")));
 * }</pre>
 *
 * @author Igor Mendes Domingues Miras
 */
public class TerminologyRegistry {

    private final Map<TerminologyDomain, SemanticMappingStrategy> estrategias = new EnumMap<>(TerminologyDomain.class);

    /** @return um registro vazio */
    public static TerminologyRegistry novo() {
        return new TerminologyRegistry();
    }

    /**
     * Registra uma estratégia para todos os domínios que ela declara atender.
     *
     * @param estrategia estratégia a registrar
     * @return este registro, para encadeamento
     * @throws IllegalStateException se já houver estratégia registrada para algum dos domínios
     */
    public TerminologyRegistry registrar(SemanticMappingStrategy estrategia) {
        Objects.requireNonNull(estrategia, "estrategia é obrigatória");
        for (TerminologyDomain d : TerminologyDomain.values()) {
            if (!estrategia.supports(d)) {
                continue;
            }
            SemanticMappingStrategy anterior = estrategias.putIfAbsent(d, estrategia);
            if (anterior != null) {
                throw new IllegalStateException("Já existe estratégia registrada para o domínio " + d + ": "
                        + anterior.getClass().getSimpleName()
                        + ". Duas estratégias para o mesmo domínio tornariam a tradução "
                        + "dependente da ordem de registro.");
            }
        }
        return this;
    }

    /**
     * Traduz um código, delegando à estratégia do domínio.
     *
     * @param rawValue código no vocabulário local
     * @param context contexto da operação
     * @return o alvo e a trilha de auditoria
     * @throws IllegalStateException se não houver estratégia para o domínio
     * @throws UnmappedTerminologyException se não houver correspondência para o código
     */
    public MappedValue map(RawValue rawValue, MappingContext context) {
        SemanticMappingStrategy estrategia = estrategias.get(rawValue.domain());
        if (estrategia == null) {
            throw new IllegalStateException(
                    "Nenhuma estratégia de mapeamento registrada para o domínio " + rawValue.domain()
                            + ". Registre uma antes de enviar registros que dependam deste vocabulário — "
                            + "o framework não adivinha correspondências nem repassa códigos locais.");
        }
        return estrategia.map(rawValue, context);
    }

    /**
     * Atalho que já registra a proveniência na trilha.
     *
     * @param rawValue código no vocabulário local
     * @param context contexto da operação
     * @param auditLog trilha em que a proveniência será acumulada
     * @return o alvo e a trilha de auditoria
     */
    public MappedValue map(RawValue rawValue, MappingContext context, MappingAuditLog auditLog) {
        MappedValue resultado = map(rawValue, context);
        auditLog.registrar(resultado);
        return resultado;
    }

    /**
     * @param domain domínio a verificar
     * @return {@code true} se há estratégia registrada
     */
    public boolean atende(TerminologyDomain domain) {
        return estrategias.containsKey(domain);
    }

    /** @return os domínios com estratégia registrada */
    public Set<TerminologyDomain> dominiosRegistrados() {
        return Collections.unmodifiableSet(estrategias.keySet());
    }
}
