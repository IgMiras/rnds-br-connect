package br.rndsconnect.semantic;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import java.util.Map;
import java.util.Objects;

/**
 * Estratégia padrão: tradução por consulta a uma tabela explícita.
 *
 * <p>Cobre o caso majoritário — correspondência 1:1 registrada e revisada. A tabela é
 * carregada uma vez, na construção, e a busca é O(1).
 *
 * <p><strong>Não existe caminho alternativo.</strong> Se a chave não está na tabela, o
 * método lança. Não há {@code getOrDefault}, não há repasse do código local, não há flag de
 * modo permissivo. Essa ausência é deliberada e é o núcleo do argumento P2 da Seção 3.1 —
 * ver {@link UnmappedTerminologyException} para o porquê.
 *
 * @author Igor Mendes Domingues Miras
 */
public class SourceBackedMappingStrategy implements SemanticMappingStrategy {

    private final MappingSource source;
    private final Map<String, CodedTarget> tabela;

    /**
     * @param source fonte da tabela de correspondência
     */
    public SourceBackedMappingStrategy(MappingSource source) {
        this.source = Objects.requireNonNull(source, "source é obrigatória");
        this.tabela = source.load();
    }

    @Override
    public MappedValue map(RawValue rawValue, MappingContext context) {
        Objects.requireNonNull(rawValue, "rawValue é obrigatório");
        Objects.requireNonNull(context, "context é obrigatório");

        if (!supports(rawValue.domain())) {
            throw new IllegalArgumentException("Esta estratégia atende o domínio " + source.getDomain()
                    + ", mas recebeu um código do domínio " + rawValue.domain() + ".");
        }

        CodedTarget alvo = tabela.get(rawValue.value());
        if (alvo == null) {
            throw new UnmappedTerminologyException(
                    rawValue.domain(), rawValue.value(), source.getVersion(), tabela.size());
        }

        MappingProvenance proveniencia = MappingProvenance.builder()
                .domain(rawValue.domain())
                .sourceValue(rawValue.value())
                .targetSystem(alvo.system())
                .targetValue(alvo.code())
                .strategyUsed(getClass().getSimpleName())
                .sourceVersion(source.getVersion())
                .mappedAt(context.mappedAt())
                .idLocalRegistro(context.idLocalRegistro())
                .indiceItem(context.indiceItem())
                .confidence(1.0) // tabela explícita revisada: não há inferência envolvida
                .validatedBy(alvo.validatedBy())
                .build();

        return new MappedValue(alvo, proveniencia);
    }

    @Override
    public boolean supports(TerminologyDomain domain) {
        return source.getDomain() == domain;
    }

    /** @return a fonte que alimenta esta estratégia */
    public MappingSource getSource() {
        return source;
    }
}
