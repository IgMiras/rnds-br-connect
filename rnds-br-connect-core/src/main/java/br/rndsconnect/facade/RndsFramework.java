package br.rndsconnect.facade;

import br.rndsconnect.builder.RacBundleBuilder;
import br.rndsconnect.builder.RedfmBundleBuilder;
import br.rndsconnect.builder.RelBundleBuilder;
import br.rndsconnect.builder.RiaBundleBuilder;
import br.rndsconnect.builder.RiaCampanhaBundleBuilder;
import br.rndsconnect.builder.RiraBundleBuilder;
import br.rndsconnect.builder.SaBundleBuilder;
import br.rndsconnect.dto.RegistroAtendimentoClinicoDTO;
import br.rndsconnect.dto.RegistroDispensacaoMedicamentoDTO;
import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.dto.RegistroImunizacaoCampanhaDTO;
import br.rndsconnect.dto.RegistroImunizacaoDTO;
import br.rndsconnect.dto.RegistroRegulacaoAssistencialDTO;
import br.rndsconnect.dto.RegistroSumarioAltaDTO;
import br.rndsconnect.semantic.MappingOrchestrator;
import br.rndsconnect.semantic.MappingSource;
import br.rndsconnect.semantic.SemanticMappingStrategy;
import br.rndsconnect.semantic.SourceBackedMappingStrategy;
import br.rndsconnect.semantic.TerminologyRegistry;
import br.rndsconnect.validator.ConformancePackage;
import br.rndsconnect.validator.LocalFhirValidator;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.Bundle;

/**
 * Ponto de entrada para montar o framework.
 *
 * <p>Uso típico:
 *
 * <pre>{@code
 * RndsIntegrationFacade facade = RndsFramework.builder()
 *     .solicitanteId("29376")
 *     .servico(Servico.RIA, "conformance")
 *     .mapping(new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml"))
 *     .mapping(new PropertiesMappingSource(TerminologyDomain.DOSE, "mapeamentos/doses.properties"))
 *     .build();
 * }</pre>
 *
 * <p>A construção é cara: cria o {@code FhirContext}, carrega o pacote de conformidade de
 * cada serviço declarado e lê todas as fontes de mapeamento. Faça uma vez, na inicialização da
 * aplicação, e reutilize — a Facade resultante é imutável e segura para uso concorrente.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class RndsFramework {

    private RndsFramework() {}

    /** @return um construtor vazio */
    public static Builder builder() {
        return new Builder();
    }

    /** Construtor da Facade. */
    public static final class Builder {

        private String solicitanteId;
        private final List<SemanticMappingStrategy> estrategias = new ArrayList<>();
        private final Map<Servico, String[]> pacotes = new EnumMap<>(Servico.class);
        private FhirContext ctx;
        private Supplier<String> geradorUuid = () -> UUID.randomUUID().toString();
        private Supplier<Instant> relogio = Instant::now;

        /**
         * @param solicitanteId Identificador Solicitante fornecido pela RNDS no credenciamento
         * @return este construtor
         */
        public Builder solicitanteId(String solicitanteId) {
            this.solicitanteId = solicitanteId;
            return this;
        }

        /**
         * Registra uma fonte de mapeamento, envolvendo-a na estratégia padrão.
         *
         * @param source fonte da tabela de correspondência
         * @return este construtor
         */
        public Builder mapping(MappingSource source) {
            return strategy(new SourceBackedMappingStrategy(source));
        }

        /**
         * Registra uma estratégia de mapeamento arbitrária.
         *
         * <p>É por aqui que entra o ponto de extensão do ADR-02: uma implementação que
         * consulte servidor de terminologia, banco versionado ou serviço interno da
         * instituição se registra do mesmo jeito que as fontes de arquivo.
         *
         * @param estrategia estratégia a registrar
         * @return este construtor
         */
        public Builder strategy(SemanticMappingStrategy estrategia) {
            estrategias.add(estrategia);
            return this;
        }

        /**
         * Declara um serviço e o diretório do seu pacote de conformidade.
         *
         * <p><strong>Só os serviços declarados são carregados.</strong> Os pacotes variam de
         * 3,4 MB (RIA e REL) a 86 MB (SA e RAC) — carregar todos penalizaria quem integra
         * apenas um. A carga é, portanto, proporcional ao que o integrador usa.
         *
         * <p>Serviços que compartilham o mesmo pacote publicado devem apontar para o mesmo
         * diretório: é o caso do RIA e do REL, ambos servidos pelos 113 artefatos do pacote
         * {@code br-core-ril-ria-rel}. Cada um recebe seu próprio validador, mas o conteúdo
         * carregado é idêntico.
         *
         * <p>Aceita mais de um diretório porque o REDFM precisa do seu pacote
         * <strong>mais</strong> o IG BR-Core, do qual seus perfis derivam.
         *
         * @param servico serviço a habilitar
         * @param diretorios caminhos dos artefatos no classpath
         * @return este construtor
         */
        public Builder servico(Servico servico, String... diretorios) {
            pacotes.put(servico, diretorios);
            return this;
        }

        /**
         * @param ctx contexto FHIR a reutilizar; se ausente, um novo é criado
         * @return este construtor
         */
        public Builder fhirContext(FhirContext ctx) {
            this.ctx = ctx;
            return this;
        }

        /**
         * Fontes de UUID e de instante, injetáveis para tornar a construção determinística
         * em teste. Em produção, os padrões são {@code UUID.randomUUID()} e
         * {@code Instant.now()}.
         *
         * @param geradorUuid fonte dos UUIDs transientes
         * @param relogio fonte do instante de montagem do Bundle
         * @return este construtor
         */
        public Builder deterministico(Supplier<String> geradorUuid, Supplier<Instant> relogio) {
            this.geradorUuid = geradorUuid;
            this.relogio = relogio;
            return this;
        }

        /**
         * @return a Facade pronta para uso
         * @throws IllegalStateException se faltar o Identificador Solicitante ou se nenhuma
         *     estratégia de mapeamento tiver sido registrada
         */
        public RndsIntegrationFacade build() {
            if (solicitanteId == null || solicitanteId.isBlank()) {
                throw new IllegalStateException(
                        "Identificador Solicitante é obrigatório: ele compõe o Bundle.identifier.system "
                                + "de todo envio e é fornecido pela RNDS no credenciamento.");
            }
            if (estrategias.isEmpty()) {
                throw new IllegalStateException(
                        "Nenhuma estratégia de mapeamento registrada. Sem tradução terminológica não há "
                                + "como converter o vocabulário local no da RNDS — e o framework não "
                                + "repassa códigos locais.");
            }

            if (pacotes.isEmpty()) {
                throw new IllegalStateException(
                        "Nenhum serviço declarado. Use .servico(Servico.X, \"caminho/dos/artefatos\") "
                                + "para cada serviço a habilitar — os pacotes de conformidade divergem "
                                + "entre si, e cada serviço precisa do seu.");
            }

            FhirContext contexto = ctx != null ? ctx : FhirContext.forR4();

            TerminologyRegistry registry = TerminologyRegistry.novo();
            estrategias.forEach(registry::registrar);

            // Um validador por serviço declarado. A LÓGICA da Camada 4 é a mesma — a cadeia
            // de cinco elos não muda; o que varia é o conjunto de artefatos que cada um
            // enxerga. É a revisão do Princípio de ouro exigida pelo defeito nº 11.
            Map<Servico, LocalFhirValidator> validadores = new EnumMap<>(Servico.class);
            pacotes.forEach((servico, dirs) -> validadores.put(
                    servico,
                    new LocalFhirValidator(contexto, ConformancePackage.paraServico(contexto, servico, dirs))));

            return new DefaultRndsIntegrationFacade(
                    new MappingOrchestrator(registry),
                    new RiaBundleBuilder(solicitanteId, geradorUuid, relogio),
                    new RiaCampanhaBundleBuilder(solicitanteId, geradorUuid, relogio),
                    new RelBundleBuilder(solicitanteId, geradorUuid, relogio),
                    new RiraBundleBuilder(solicitanteId, geradorUuid, relogio),
                    new RedfmBundleBuilder(solicitanteId, geradorUuid, relogio),
                    new SaBundleBuilder(solicitanteId, geradorUuid, relogio),
                    new RacBundleBuilder(solicitanteId, geradorUuid, relogio),
                    validadores);
        }
    }

    /**
     * Implementação da Facade: encadeia as Camadas 2, 3 e 4.
     *
     * <p>O corpo de {@code prepararImunizacaoRotina} é a arquitetura inteira em três linhas —
     * e essa brevidade é o ponto. Cada camada faz uma coisa, e a Facade só as ordena.
     */
    private record DefaultRndsIntegrationFacade(
            MappingOrchestrator orchestrator,
            RiaBundleBuilder riaBuilder,
            RiaCampanhaBundleBuilder riaCampanhaBuilder,
            RelBundleBuilder relBuilder,
            RiraBundleBuilder riraBuilder,
            RedfmBundleBuilder redfmBuilder,
            SaBundleBuilder saBuilder,
            RacBundleBuilder racBuilder,
            Map<Servico, LocalFhirValidator> validadores)
            implements RndsIntegrationFacade {

        private DefaultRndsIntegrationFacade {
            Objects.requireNonNull(orchestrator);
            Objects.requireNonNull(riaBuilder);
            Objects.requireNonNull(riaCampanhaBuilder);
            Objects.requireNonNull(relBuilder);
            Objects.requireNonNull(riraBuilder);
            Objects.requireNonNull(redfmBuilder);
            Objects.requireNonNull(saBuilder);
            Objects.requireNonNull(racBuilder);
            Objects.requireNonNull(validadores);
        }

        /**
         * O validador do serviço, ou falha se ele não foi declarado na construção.
         *
         * <p>Recusar é melhor que recair num validador genérico: os pacotes divergem, e
         * validar o SA contra os artefatos do RIA daria um veredicto que a RNDS não daria.
         */
        private LocalFhirValidator validadorDe(Servico servico) {
            LocalFhirValidator v = validadores.get(servico);
            if (v == null) {
                throw new IllegalStateException("O serviço " + servico + " (" + servico.descricao()
                        + ") não foi declarado na construção do framework. "
                        + "Use .servico(Servico." + servico + ", \"caminho/dos/artefatos\") no builder. "
                        + "Serviços declarados: " + validadores.keySet());
            }
            return v;
        }

        @Override
        public DocumentoPreparado prepararImunizacaoRotina(RegistroImunizacaoDTO registro) {
            // Camada 2 — traduz o vocabulário local e acumula a proveniência.
            MappingOrchestrator.Resultado enriquecido = orchestrator.enriquecer(registro);

            // Camada 3 — monta o Bundle conforme o perfil do RIA-R.
            Bundle bundle = riaBuilder.build(enriquecido.dto());

            // Camada 4 — Fail-Fast: nada sai daqui sem passar pelos perfis da RNDS,
            // e pelos artefatos DESTE serviço (ver ConformancePackage).
            validadorDe(Servico.RIA).validate(bundle);

            return new DocumentoPreparado(bundle, enriquecido.trilha());
        }

        @Override
        public DocumentoPreparado prepararImunizacaoCampanha(RegistroImunizacaoCampanhaDTO registro) {
            // Mesmas três camadas, outro Builder. A LÓGICA das Camadas 2 e 4 não muda; o
            // que varia é qual pacote de conformidade o validador enxerga (Princípio de
            // ouro revisado — ver Arquitetura §14).
            MappingOrchestrator.ResultadoCampanha enriquecido = orchestrator.enriquecerCampanha(registro);
            Bundle bundle = riaCampanhaBuilder.build(enriquecido.dto());
            validadorDe(Servico.RIA).validate(bundle);
            return new DocumentoPreparado(bundle, enriquecido.trilha());
        }

        @Override
        public DocumentoPreparado prepararExameLaboratorial(RegistroExameLaboratorialDTO registro) {
            // Terceiro serviço, mesmas três linhas. As Camadas 2 e 4 seguem intocadas.
            MappingOrchestrator.ResultadoExame enriquecido = orchestrator.enriquecerExameLaboratorial(registro);
            Bundle bundle = relBuilder.build(enriquecido.dto());
            validadorDe(Servico.REL).validate(bundle);
            return new DocumentoPreparado(bundle, enriquecido.trilha());
        }

        @Override
        public DocumentoPreparado prepararRegulacaoAssistencial(RegistroRegulacaoAssistencialDTO registro) {
            // Quarto serviço, mesmas três linhas.
            MappingOrchestrator.ResultadoRegulacao enriquecido = orchestrator.enriquecerRegulacaoAssistencial(registro);
            Bundle bundle = riraBuilder.build(enriquecido.dto());
            validadorDe(Servico.RIRA).validate(bundle);
            return new DocumentoPreparado(bundle, enriquecido.trilha());
        }

        @Override
        public DocumentoPreparado prepararDispensacaoMedicamento(RegistroDispensacaoMedicamentoDTO registro) {
            // Quinto servico, mesmas tres linhas -- e o primeiro cujo pacote de conformidade
            // combina dois formatos e duas fundacoes arquitetonicas.
            MappingOrchestrator.ResultadoDispensacao enriquecido =
                    orchestrator.enriquecerDispensacaoMedicamento(registro);
            Bundle bundle = redfmBuilder.build(enriquecido.dto());
            validadorDe(Servico.REDFM).validate(bundle);
            return new DocumentoPreparado(bundle, enriquecido.trilha());
        }

        @Override
        public DocumentoPreparado prepararSumarioAlta(RegistroSumarioAltaDTO registro) {
            // Sexto servico, as mesmas tres linhas -- para o maior documento do framework.
            MappingOrchestrator.ResultadoSumario enriquecido = orchestrator.enriquecerSumarioAlta(registro);
            Bundle bundle = saBuilder.build(enriquecido.dto());
            validadorDe(Servico.SA).validate(bundle);
            return new DocumentoPreparado(bundle, enriquecido.trilha());
        }

        @Override
        public DocumentoPreparado prepararAtendimentoClinico(RegistroAtendimentoClinicoDTO registro) {
            // Setimo e ultimo servico do escopo, e as mesmas tres linhas de sempre.
            MappingOrchestrator.ResultadoAtendimentoClinico enriquecido =
                    orchestrator.enriquecerAtendimentoClinico(registro);
            Bundle bundle = racBuilder.build(enriquecido.dto());
            validadorDe(Servico.RAC).validate(bundle);
            return new DocumentoPreparado(bundle, enriquecido.trilha());
        }
    }
}
