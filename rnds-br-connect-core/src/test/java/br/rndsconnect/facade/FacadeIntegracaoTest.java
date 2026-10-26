package br.rndsconnect.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rndsconnect.dto.RegistroAtendimentoClinicoDTO;
import br.rndsconnect.dto.RegistroDispensacaoMedicamentoDTO;
import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.dto.RegistroImunizacaoCampanhaDTO;
import br.rndsconnect.dto.RegistroImunizacaoDTO;
import br.rndsconnect.dto.RegistroRegulacaoAssistencialDTO;
import br.rndsconnect.dto.RegistroSumarioAltaDTO;
import br.rndsconnect.model.EstadoRegulacao;
import br.rndsconnect.model.TerminologyDomain;
import br.rndsconnect.model.VocabularioClinicoFhir;
import br.rndsconnect.semantic.MappingProvenance;
import br.rndsconnect.semantic.PropertiesMappingSource;
import br.rndsconnect.semantic.UnmappedTerminologyException;
import br.rndsconnect.semantic.YamlMappingSource;
import br.rndsconnect.tools.AuditoriaEmissao;
import br.rndsconnect.validator.Servico;
import ca.uhn.fhir.context.FhirContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Immunization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Camada 1 — teste de integração das quatro camadas.
 *
 * <p>É o único teste do projeto que exercita o framework como um integrador o usaria: monta a
 * Facade uma vez, entrega um DTO com códigos locais e recebe um documento validado. Tudo
 * entre uma coisa e outra é interno.
 *
 * <p>Repare no que <strong>não</strong> aparece na montagem do DTO: nenhum {@code Bundle},
 * nenhum {@code CodeableConcept}, nenhuma URL de CodeSystem, nenhum perfil. O sistema cliente
 * fala de vacina, dose e local de aplicação nos termos que ele já usa.
 *
 * @author Igor Mendes Domingues Miras
 */
class FacadeIntegracaoTest {

    private static final String SOLICITANTE = "29376";

    /**
     * Gerador determinístico <strong>e inesgotável</strong> de UUIDs.
     *
     * <p>Uma fila de UUIDs pré-definidos seria determinística mas finita — e a Facade é
     * compartilhada por todos os testes desta classe. Um dublê de teste deve preservar as
     * propriedades do que substitui: {@code UUID.randomUUID()} nunca acaba, e este também
     * não. O padrão gerado é um UUID v4 válido pela RFC 4122.
     */
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    private static FhirContext ctx;
    private static RndsIntegrationFacade facade;

    @BeforeAll
    static void montarFramework() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));

        // Um FhirContext só para a classe inteira: criar o segundo numa mesma JVM de teste
        // custa caro e já derrubou suíte por memória.
        ctx = FhirContext.forR4();

        facade = RndsFramework.builder()
                .solicitanteId(SOLICITANTE)
                .fhirContext(ctx)
                // RIA e REL vem do MESMO pacote publicado: apontam para o mesmo diretorio.
                // Cada um recebe seu proprio validador; o conteudo carregado e identico.
                .servico(Servico.RIA, "conformance")
                .servico(Servico.REL, "conformance")
                // O RIRA tem pacote PRÓPRIO, de 318 artefatos — os 113 do RIA e REL não bastam.
                .servico(Servico.RIRA, "conformance/rira")
                // O REDFM tem pacote PRÓPRIO, que combina XML do DATASUS com JSON do BR-Core.
                .servico(Servico.REDFM, "conformance/redfm")
                // O SA tem o maior pacote: 124 artefatos, com o BREntregaSuprimentos presente so
                // pela estrutura -- alvo de discriminador de fatia, sem os 64 MB de vocabularios.
                .servico(Servico.SA, "conformance/sa")
                // O RAC tem pacote PRÓPRIO, de 142 artefatos, com a MESMA origem do SA: o
                // BRRegistroAtendimentoClinico da distribuição do pacote completo não consegue
                // avaliar as seções do documento que ele mesmo distribui (ADR-09).
                .servico(Servico.RAC, "conformance/rac")
                .mapping(new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml"))
                .mapping(new PropertiesMappingSource(TerminologyDomain.DOSE, "mapeamentos/doses.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.ESTRATEGIA_VACINACAO, "mapeamentos/estrategias.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.GRUPO_ATENDIMENTO, "mapeamentos/grupos-atendimento.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.CONDICAO_MATERNAL, "mapeamentos/condicoes-maternais.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.LOCAL_APLICACAO, "mapeamentos/locais-aplicacao.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.VIA_ADMINISTRACAO, "mapeamentos/vias-administracao.properties"))
                // FABRICANTE: opcional no RIA-R, obrigatório no RIA-C.
                .mapping(
                        new PropertiesMappingSource(TerminologyDomain.FABRICANTE, "mapeamentos/fabricantes.properties"))
                // ── domínios do REL ──────────────────────────────────────
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.SUSPEITA_DIAGNOSTICA, "mapeamentos/suspeitas-diagnosticas.properties"))
                .mapping(new YamlMappingSource(TerminologyDomain.NOME_EXAME, "mapeamentos/exames-multi-codesystem.yml"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.SUBGRUPO_TABELA_SUS, "mapeamentos/categorias-exame.properties"))
                .mapping(new YamlMappingSource(TerminologyDomain.TIPO_AMOSTRA, "mapeamentos/tipos-amostra.yml"))
                .mapping(new YamlMappingSource(
                        TerminologyDomain.RESULTADO_QUALITATIVO, "mapeamentos/resultados-qualitativos.yml"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.INTERPRETACAO_RESULTADO, "mapeamentos/interpretacoes-resultado.properties"))
                .mapping(new PropertiesMappingSource(TerminologyDomain.PATOGENO, "mapeamentos/patogenos.properties"))
                // ── domínios do RIRA ─────────────────────────────────────
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.MODALIDADE_ASSISTENCIAL, "mapeamentos/modalidades-assistenciais.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.CARATER_ATENDIMENTO, "mapeamentos/carateres-atendimento.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.PROCEDIMENTO, "mapeamentos/procedimentos.properties"))
                .mapping(new PropertiesMappingSource(TerminologyDomain.OCUPACAO_CBO, "mapeamentos/cbos.properties"))
                .mapping(new PropertiesMappingSource(TerminologyDomain.CID10, "mapeamentos/cids.properties"))
                // ── domínios do REDFM ────────────────────────────────────
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.MEDICAMENTO, "mapeamentos/medicamentos.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.FORMA_FARMACEUTICA, "mapeamentos/formas-farmaceuticas.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.CATEGORIA_DISPENSACAO, "mapeamentos/categorias-dispensacao.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.TIPO_DISPENSACAO, "mapeamentos/tipos-dispensacao.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.UNIDADE_DOSE, "mapeamentos/formas-farmaceuticas.properties"))
                // ── domínios do SA ───────────────────────────────────────
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.CARATER_ATENDIMENTO_RNDS,
                        "mapeamentos/carateres-atendimento-rnds.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.PROCEDENCIA, "mapeamentos/procedencias.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.MOTIVO_DESFECHO, "mapeamentos/motivos-desfecho.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.FINANCIAMENTO, "mapeamentos/financiamentos.properties"))
                .mapping(new YamlMappingSource(TerminologyDomain.ALERGENO, "mapeamentos/alergenos.yml"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.MANIFESTACAO_REACAO, "mapeamentos/manifestacoes-reacao.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.MEDICAMENTO_CATMAT, "mapeamentos/medicamentos-catmat.properties"))
                .mapping(new PropertiesMappingSource(TerminologyDomain.TURNO, "mapeamentos/turnos.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.JUSTIFICATIVA_NAO_IDENTIFICADO,
                        "mapeamentos/justificativas-nao-identificado.properties"))
                // ── domínios do RAC ──────────────────────────────────────
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.RESPONSABILIDADE_PARTICIPANTE,
                        "mapeamentos/responsabilidades-participante.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.CATEGORIA_DIAGNOSTICO, "mapeamentos/categorias-diagnostico.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.LOCAL_AFERICAO, "mapeamentos/locais-afericao.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.ORIGEM_MEDIDA, "mapeamentos/origens-medida.properties"))
                .mapping(new PropertiesMappingSource(
                        TerminologyDomain.ROUPAS_USADAS_MEDICAO, "mapeamentos/roupas-usadas-medicao.properties"))
                .mapping(new YamlMappingSource(
                        TerminologyDomain.PROBLEMA_DIAGNOSTICO, "mapeamentos/problemas-diagnosticos.yml"))
                .mapping(new YamlMappingSource(
                        TerminologyDomain.PAPEL_PROBLEMA_DIAGNOSTICO, "mapeamentos/papeis-problema-diagnostico.yml"))
                .mapping(new YamlMappingSource(TerminologyDomain.TIPO_OBSERVACAO, "mapeamentos/tipos-observacao.yml"))
                .mapping(new YamlMappingSource(
                        TerminologyDomain.POSICAO_INDIVIDUO, "mapeamentos/posicoes-individuo.yml"))
                .mapping(new YamlMappingSource(
                        TerminologyDomain.FREQUENCIA_USO_SUBSTANCIA, "mapeamentos/frequencias-uso-substancia.yml"))
                .mapping(new YamlMappingSource(TerminologyDomain.VALOR_MEDIDA, "mapeamentos/valores-medida.yml"))
                .deterministico(
                        () -> String.format("aa000000-0000-4000-8000-%012d", SEQUENCIA.incrementAndGet()),
                        () -> Instant.parse("2024-04-01T17:59:51.283Z"))
                .build();
    }

    /**
     * Um registro como o sistema cliente o conhece.
     *
     * <p>Gestante recebendo a segunda dose de COVID-19 no deltóide esquerdo, por via
     * intramuscular. Nenhum destes códigos existe na RNDS: são do vocabulário local.
     */
    private static RegistroImunizacaoDTO.RegistroImunizacaoDTOBuilder registroLocal() {
        return RegistroImunizacaoDTO.builder()
                .idLocalRegistro("REG-2024-0042")
                .cpfPaciente("01234567890")
                .cnesEstabelecimento("0123456")
                .cnsProfissional("898005900000003")
                .dataRegistroOrigem(Instant.parse("2024-04-01T11:18:30Z"))
                .dataAplicacao(LocalDate.of(2024, 4, 1))
                // Elo 8: fora de transcricao, fabricante e lote sao exigidos pelo MI.
                .fabricanteLocal("PFIZER")
                .vacinaLocal("105")
                .doseLocal("D2")
                .estrategiaVacinacaoLocal("ROTINA")
                .grupoAtendimentoLocal("GESTANTE")
                .condicaoMaternalLocal("GESTANTE")
                .localAplicacaoLocal("DELTOIDE_ESQ")
                .viaAdministracaoLocal("IM")
                .lote("220262");
    }

    @Test
    @DisplayName("Ponta a ponta: DTO com códigos locais entra, documento conforme sai")
    void pontaAPonta() {
        DocumentoPreparado doc = facade.prepararImunizacaoRotina(registroLocal().build());

        assertThat(doc.bundle()).isNotNull();
        assertThat(doc.bundle().getEntry()).hasSize(2);
        assertThat(doc.bundle().getIdentifier().getSystem()).endsWith("BRRNDS-" + SOLICITANTE);
        assertThat(doc.bundle().getIdentifier().getValue()).isEqualTo("REG-2024-0042");

        // O documento passou pela Camada 4 dentro da Facade: se não fosse conforme,
        // prepararImunizacaoRotina teria lançado FhirConformanceException.
    }

    @Test
    @DisplayName("Os códigos locais foram traduzidos — nenhum sobreviveu ao caminho")
    void codigosLocaisNaoSobrevivem() {
        DocumentoPreparado doc = facade.prepararImunizacaoRotina(registroLocal().build());
        Immunization imm = (Immunization) doc.bundle().getEntry().get(1).getResource();

        assertThat(imm.getVaccineCode().getCodingFirstRep().getCode())
                .as("\"105\" local (Pfizer) -> \"87\" na RNDS; repassar \"105\" registraria Moderna")
                .isEqualTo("87");
        assertThat(imm.getSite().getCodingFirstRep().getCode()).isEqualTo("2"); // Deltóide Esquerdo
        assertThat(imm.getRoute().getCodingFirstRep().getCode()).isEqualTo("10890"); // Intramuscular
        assertThat(imm.getProtocolAppliedFirstRep().getDoseNumber().primitiveValue())
                .isEqualTo("2"); // 2ª Dose

        String json = FhirContext.forR4().newJsonParser().encodeResourceToString(doc.bundle());
        assertThat(json)
                .as("nenhum código do vocabulário local pode aparecer no documento")
                .doesNotContain("DELTOIDE_ESQ")
                .doesNotContain("GESTANTE")
                .doesNotContain("ROTINA")
                .doesNotContain("\"D2\"");
    }

    @Test
    @DisplayName("A trilha de auditoria acompanha o documento, com hash de cada fonte")
    void trilhaAcompanhaODocumento() {
        DocumentoPreparado doc = facade.prepararImunizacaoRotina(registroLocal().build());

        assertThat(doc.trilha().size())
                .as("sete domínios obrigatórios mais FABRICANTE, que o Elo 8 passou a exigir "
                        + "fora de transcrição de caderneta")
                .isEqualTo(8);
        assertThat(doc.trilha().getRegistros()).allSatisfy(p -> {
            assertThat(p.getIdLocalRegistro()).isEqualTo("REG-2024-0042");
            assertThat(p.getSourceVersion()).contains("sha256:");
            assertThat(p.getConfidence()).isEqualTo(1.0);
        });

        // Seis dos sete domínios declaram responsável clínico; DOSE não, de propósito.
        // Uma trilha em que quase nenhuma tradução tem responsável identificado esvazia
        // a promessa de rastreabilidade do ADR-04.
        assertThat(doc.trilha().getRegistros())
                .filteredOn(p -> p.getValidatedBy() != null)
                .as("apenas a correspondência de DOSE é operacional o bastante para dispensar " + "validação clínica")
                .hasSize(7);

        System.out.println("[Facade] trilha de REG-2024-0042:");
        System.out.println(doc.trilha().toRelatorio());
    }

    @Test
    @DisplayName("Código local sem correspondência para o fluxo — nenhum Bundle é construído")
    void codigoSemCorrespondenciaParaTudo() {
        RegistroImunizacaoDTO comCodigoDesconhecido =
                registroLocal().vacinaLocal("VACINA_QUE_NINGUEM_MAPEOU").build();

        assertThatThrownBy(() -> facade.prepararImunizacaoRotina(comCodigoDesconhecido))
                .isInstanceOf(UnmappedTerminologyException.class)
                .hasMessageContaining("VACINA_QUE_NINGUEM_MAPEOU")
                .hasMessageContaining("vacinas.yml");
    }

    @Test
    @DisplayName("Campos opcionais omitidos não viram tradução nem entrada no Bundle")
    void opcionaisOmitidosNaoSaoTraduzidos() {
        DocumentoPreparado doc = facade.prepararImunizacaoRotina(registroLocal().build());
        Immunization imm = (Immunization) doc.bundle().getEntry().get(1).getResource();

        assertThat(imm.hasReportOrigin()).isFalse();
        assertThat(imm.hasLocation()).isFalse();
        assertThat(imm.hasManufacturer())
                .as("fabricante deixou de ser omissível: o Elo 8 o exige fora de transcrição")
                .isTrue();
        assertThat(doc.bundle().getEntry())
                .as("sem CID informado, não há Condition")
                .hasSize(2);
    }

    // ══ RIA-C: o mesmo caminho, outro serviço ═══════════════════════════

    /** Campanha de gripe: mesma paciente, outro serviço, outro conjunto de campos. */
    private static RegistroImunizacaoCampanhaDTO.RegistroImunizacaoCampanhaDTOBuilder campanhaLocal() {
        return RegistroImunizacaoCampanhaDTO.builder()
                .idLocalRegistro("CAMP-2024-0007")
                .cpfPaciente("01234567890")
                .cnesEstabelecimento("0123456")
                .cnsProfissional("898005900000003")
                .dataRegistroOrigem(Instant.parse("2024-04-01T11:18:30Z"))
                .dataAplicacao(LocalDate.of(2024, 4, 1))
                .vacinaLocal("106")
                .doseLocal("DU")
                .grupoAtendimentoLocal("GESTANTE")
                .condicaoMaternalLocal("GESTANTE")
                .fabricanteLocal("JANSSEN")
                .lote("220262");
    }

    @Test
    @DisplayName("PRINCÍPIO DE OURO: novo serviço reusa Camadas 2 e 4 sem alteração alguma")
    void novoServicoReusaCamadasDoisEQuatro() {
        DocumentoPreparado doc =
                facade.prepararImunizacaoCampanha(campanhaLocal().build());

        assertThat(doc.bundle().getEntry()).hasSize(2);
        assertThat(doc.bundle().getIdentifier().getValue()).isEqualTo("CAMP-2024-0007");

        Immunization imm = (Immunization) doc.bundle().getEntry().get(1).getResource();
        assertThat(imm.getVaccineCode().getCodingFirstRep().getCode()).isEqualTo("88");
        assertThat(imm.getManufacturer().getIdentifier().getValue()).isEqualTo("30587");
        assertThat(imm.getLotNumber()).isEqualTo("220262");
        assertThat(imm.hasSite()).as("site é proibido em campanha").isFalse();
        assertThat(imm.hasRoute()).as("route é proibido em campanha").isFalse();
    }

    @Test
    @DisplayName("A campanha traduz cinco domínios; a rotina, sete")
    void campanhaTraduzMenosDominios() {
        DocumentoPreparado campanha =
                facade.prepararImunizacaoCampanha(campanhaLocal().build());
        DocumentoPreparado rotina =
                facade.prepararImunizacaoRotina(registroLocal().build());

        assertThat(campanha.trilha().size())
                .as("VACINA, GRUPO_ATENDIMENTO, CONDICAO_MATERNAL, DOSE, FABRICANTE")
                .isEqualTo(5);
        assertThat(rotina.trilha().size())
                .as("os cinco da campanha mais ESTRATEGIA, LOCAL e VIA")
                .isEqualTo(8);

        System.out.println("[Facade] trilha de campanha CAMP-2024-0007:");
        System.out.println(campanha.trilha().toRelatorio());
    }

    @Test
    @DisplayName("As mesmas fontes de mapeamento servem aos dois serviços")
    void fontesSaoCompartilhadasEntreServicos() {
        DocumentoPreparado campanha =
                facade.prepararImunizacaoCampanha(campanhaLocal().build());
        DocumentoPreparado rotina =
                facade.prepararImunizacaoRotina(registroLocal().build());

        String fonteCampanha = campanha.trilha().getRegistros().stream()
                .filter(p -> p.getDomain() == TerminologyDomain.VACINA)
                .findFirst()
                .orElseThrow()
                .getSourceVersion();
        String fonteRotina = rotina.trilha().getRegistros().stream()
                .filter(p -> p.getDomain() == TerminologyDomain.VACINA)
                .findFirst()
                .orElseThrow()
                .getSourceVersion();

        assertThat(fonteCampanha)
                .as("um vocabulário local de vacina serve aos dois serviços — a tabela é da "
                        + "instituição, não do serviço da RNDS")
                .isEqualTo(fonteRotina);
    }

    @Test
    @DisplayName("Campanha sem lote falha na construção — o campo é 1..1 neste perfil")
    void campanhaSemLoteFalha() {
        assertThatThrownBy(() -> facade.prepararImunizacaoCampanha(
                        campanhaLocal().lote(null).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lote");
    }

    // ══ REL: cardinalidade variável e trilha por item ═══════════════════

    private static RegistroExameLaboratorialDTO.ExameDTO.ExameDTOBuilder exameLocal() {
        return RegistroExameLaboratorialDTO.ExameDTO.builder()
                .nomeExameLocal("COVID_PCR")
                .categoriaExameLocal("LAB_CLINICO")
                .tipoAmostraLocal("SWAB_NASOFARINGE")
                .dataColeta(Instant.parse("2024-04-01T11:18:30Z"))
                .dataLiberacaoLaudo(Instant.parse("2024-04-01T14:18:30Z"))
                .metodo("RT-PCR em tempo real.")
                .valorReferencia("Não detectável em indivíduos não infectados.")
                .resultadoQualitativoLocal("NAO_DETECTAVEL")
                .patogenoLocal("ORTOPOXVIRUS");
    }

    private static RegistroExameLaboratorialDTO.RegistroExameLaboratorialDTOBuilder laudoLocal() {
        return RegistroExameLaboratorialDTO.builder()
                .idLocalRegistro("LAUDO-2024-0099")
                .cpfPaciente("01234567890")
                .cnpjLaboratorio("00394544000185")
                .cnesEstabelecimento("0123456")
                .dataRegistroOrigem(Instant.parse("2024-04-01T11:18:30Z"))
                .suspeitaDiagnosticaLocal("MONKEYPOX");
    }

    @Test
    @DisplayName("PRINCÍPIO DE OURO: o terceiro serviço também não altera as Camadas 2 e 4")
    void terceiroServicoReusaCamadasDoisEQuatro() {
        DocumentoPreparado doc = facade.prepararExameLaboratorial(
                laudoLocal().exames(List.of(exameLocal().build())).build());

        assertThat(doc.bundle().getEntry())
                .as("Composition, Condition, Observation, Specimen")
                .hasSize(4);
        assertThat(doc.bundle().getIdentifier().getValue()).isEqualTo("LAUDO-2024-0099");
    }

    @Test
    @DisplayName("Laudo com três exames: 8 resources e trilha discriminada por item")
    void trilhaDiscriminaCadaExame() {
        DocumentoPreparado doc = facade.prepararExameLaboratorial(laudoLocal()
                .exames(List.of(
                        exameLocal().build(),
                        exameLocal().tipoAmostraLocal("SORO").build(),
                        exameLocal().nomeExameLocal("DENGUE_NS1").build()))
                .build());

        assertThat(doc.bundle().getEntry()).as("2 + 2×3").hasSize(8);

        // 1 suspeita + 3 × (nome, categoria, amostra, resultado, patógeno)
        assertThat(doc.trilha().size()).isEqualTo(16);

        assertThat(doc.trilha().getRegistros())
                .filteredOn(p -> p.getDomain() == TerminologyDomain.TIPO_AMOSTRA)
                .as("sem o índice, três traduções idênticas de amostra seriam indistinguíveis")
                .extracting(MappingProvenance::getIndiceItem)
                .containsExactly(0, 1, 2);

        assertThat(doc.trilha().getRegistros())
                .filteredOn(p -> p.getDomain() == TerminologyDomain.SUSPEITA_DIAGNOSTICA)
                .allSatisfy(p -> assertThat(p.getIndiceItem())
                        .as("a suspeita é do laudo, não de um exame")
                        .isNull());

        System.out.println("[Facade] trilha do laudo LAUDO-2024-0099:");
        System.out.println(doc.trilha().toRelatorio());
    }

    @Test
    @DisplayName("Domínios multi-CodeSystem: o system faz parte do alvo")
    void multiCodeSystemPreservaOSystem() {
        DocumentoPreparado doc = facade.prepararExameLaboratorial(laudoLocal()
                .exames(List.of(
                        exameLocal().build(),
                        exameLocal().tipoAmostraLocal("SORO").build()))
                .build());

        List<MappingProvenance> amostras = doc.trilha().getRegistros().stream()
                .filter(p -> p.getDomain() == TerminologyDomain.TIPO_AMOSTRA)
                .toList();

        assertThat(amostras.get(0).getTargetSystem()).endsWith("/BRTipoAmostraGAL");
        assertThat(amostras.get(1).getTargetSystem())
                .as("BRTipoAmostra-1.0 compõe dois CodeSystems: GAL e HL7 v2-0487")
                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0487");
    }

    @Test
    @DisplayName("Suspeita diagnóstica traduz para o system do BRCID10, não o do ValueSet")
    void suspeitaDiagnosticaUsaSystemDoCid10() {
        DocumentoPreparado doc = facade.prepararExameLaboratorial(
                laudoLocal().exames(List.of(exameLocal().build())).build());

        MappingProvenance p = doc.trilha().getRegistros().stream()
                .filter(x -> x.getDomain() == TerminologyDomain.SUSPEITA_DIAGNOSTICA)
                .findFirst()
                .orElseThrow();
        assertThat(p.getTargetSystem())
                .as("o exemplo oficial usa o nome do ValueSet como se fosse CodeSystem "
                        + "(defeito nº 8); a URL correta é a do BRCID10")
                .endsWith("/CodeSystem/BRCID10");
        assertThat(p.getTargetValue()).isEqualTo("B04");
    }

    // ══ RIRA: máquina de estados e pacote próprio ═══════════════════════

    private static RegistroRegulacaoAssistencialDTO.RegistroRegulacaoAssistencialDTOBuilder regulacaoLocal() {
        Instant quando = Instant.parse("2024-04-01T11:18:30Z");
        return RegistroRegulacaoAssistencialDTO.builder()
                .idLocalRegistro("RIRA-2024-0001")
                .cpfPaciente("01234567890")
                .cnesSolicitante("0123456")
                .cnesExecutante("0123456")
                .dataRegistroOrigem(quando)
                .dataRequisicao(quando)
                .inicioAgendamento(quando)
                .fimAgendamento(quando)
                .modalidadeAssistencialLocal("AMBULATORIAL")
                .caraterAtendimentoLocal("ELETIVO")
                .procedimentoLocal("CIRURGIA_CIA")
                .cboExecutanteLocal("CARDIOLOGISTA")
                .cid10Local("VARIZES_ESOFAGIANAS")
                .eventos(List.of(
                        RegistroRegulacaoAssistencialDTO.EventoDTO.builder()
                                .estado(EstadoRegulacao.PENDENTE)
                                .quando(quando)
                                .build(),
                        RegistroRegulacaoAssistencialDTO.EventoDTO.builder()
                                .estado(EstadoRegulacao.AGENDADA)
                                .quando(quando)
                                .build()));
    }

    @Test
    @DisplayName("PRINCÍPIO DE OURO: o quarto serviço usa PACOTE PRÓPRIO, e as Camadas 2 e 3 não mudam")
    void quartoServicoUsaPacoteProprio() {
        DocumentoPreparado doc =
                facade.prepararRegulacaoAssistencial(regulacaoLocal().build());

        assertThat(doc.bundle().getEntry())
                .as("Composition, Appointment, ServiceRequest, Condition")
                .hasSize(4);
        assertThat(doc.bundle().getIdentifier().getValue()).isEqualTo("RIRA-2024-0001");

        // O RIRA validou contra conformance/rira, e RIA e REL contra conformance —
        // pacotes distintos, no mesmo framework. É a Fase 0 exercida por um serviço real.
        //
        // E o contraste de cardinalidade e informativo: o RIA-R sem CID produz DOIS
        // resources, porque ali o Condition e condicional. No RIRA sao sempre QUATRO --
        // reasonReference e 1..1 no Appointment e no ServiceRequest, e uma solicitacao de
        // regulacao sem motivo clinico nao existe.
        assertThat(facade.prepararImunizacaoRotina(registroLocal().build())
                        .bundle()
                        .getEntry())
                .as("RIA-R sem CID: Composition + Immunization")
                .hasSize(2);
    }

    @Test
    @DisplayName("O histórico de estados atravessa as quatro camadas")
    void historicoDeEstadosAtravessaAsCamadas() {
        DocumentoPreparado doc =
                facade.prepararRegulacaoAssistencial(regulacaoLocal().build());

        org.hl7.fhir.r4.model.Composition c = (org.hl7.fhir.r4.model.Composition)
                doc.bundle().getEntry().get(0).getResource();
        assertThat(c.getEvent())
                .extracting(e -> e.getCodeFirstRep().getCodingFirstRep().getCode())
                .containsExactly("pending", "booked");

        // Cinco domínios traduzidos; os estados vêm do enum e não entram na trilha.
        assertThat(doc.trilha().size()).isEqualTo(5);
        System.out.println("[Facade] trilha do RIRA-2024-0001:");
        System.out.println(doc.trilha().toRelatorio());
    }

    @Test
    @DisplayName("RN03: cirurgia sem CBO é reprovada pela Frente A, antes de qualquer FHIR")
    void rn03ReprovaCirurgiaSemCbo() {
        assertThatThrownBy(() -> facade.prepararRegulacaoAssistencial(
                        regulacaoLocal().cboExecutanteLocal(null).build()))
                .isInstanceOf(br.rndsconnect.semantic.RndsValidationException.class)
                .hasMessageContaining("ELO-10/RN03")
                .hasMessageContaining("grupo 04");

        // Procedimento do grupo 02 dispensa o CBO.
        assertThatCode(() -> facade.prepararRegulacaoAssistencial(regulacaoLocal()
                        .procedimentoLocal("BIOPSIA_TIREOIDE")
                        .cboExecutanteLocal(null)
                        .build()))
                .doesNotThrowAnyException();
    }

    // ══ REDFM: contained, e as duas formas de receita ═══════════════════

    private static RegistroDispensacaoMedicamentoDTO.RegistroDispensacaoMedicamentoDTOBuilder dispensacaoLocal() {
        Instant quando = Instant.parse("2024-12-30T14:46:12Z");
        return RegistroDispensacaoMedicamentoDTO.builder()
                .idLocalRegistro("REDFM-2024-0001")
                .idItemDispensado("e6f89964-b218-4fb3-9603-e0897c5c2181")
                .cpfPaciente("01234567890")
                .cnesEstabelecimento("0123456")
                .dataRegistroOrigem(quando)
                .dataEntrega(quando)
                .medicamentoLocal("AMOXICILINA_500")
                .descricaoMedicamento("Amoxicilina 500 mg cápsula")
                .formaFarmaceuticaLocal("CAPSULA")
                .cnpjFabricante("21341776000145")
                .lote("587AT765H")
                .validade(LocalDate.of(2025, 10, 31))
                .categoriaDispensacaoLocal("AMBULATORIAL")
                .tipoDispensacaoLocal("TOTAL")
                .quantidade(new BigDecimal("30"))
                .unidadeQuantidade("Cápsula")
                .cnsFarmaceutico("898005900000003")
                .cboFarmaceuticoLocal("FARMACEUTICO")
                .conselhoFarmaceutico("crf-sp")
                .inscricaoFarmaceutico("154735")
                .prescricao(RegistroDispensacaoMedicamentoDTO.PrescricaoDTO.builder()
                        .idItemPrescrito("e6f89964-b218-4fb3-9603-e0897c5c2181")
                        .dataPrescricao(quando)
                        .cnsPrescritor("898005900000003")
                        .conselhoPrescritor("crm-sp")
                        .inscricaoPrescritor("154735")
                        .cboPrescritorLocal("ALERGISTA")
                        .cnesLotacaoPrescritor("0123456")
                        .cid10Local("HIPERTENSAO")
                        .viaAdministracaoLocal("VO")
                        .dose(BigDecimal.ONE)
                        .unidadeDoseLocal("CAPSULA")
                        .descricaoUnidadeDose("Cápsula")
                        .build());
    }

    @Test
    @DisplayName("PRINCÍPIO DE OURO: o quinto serviço combina dois formatos e duas fundações")
    void quintoServicoCombinaDoisFormatos() {
        DocumentoPreparado doc =
                facade.prepararDispensacaoMedicamento(dispensacaoLocal().build());

        assertThat(doc.bundle().getEntry())
                .as("Composition, MedicationDispense, Medication")
                .hasSize(3);
        assertThat(doc.bundle().getIdentifier().getValue()).isEqualTo("REDFM-2024-0001");

        // O REDFM validou contra conformance/redfm — pacote que reúne XML do DATASUS e JSON
        // do IG BR-Core, e cujos perfis derivam do BR-Core, não do R4 puro. RIA, REL e RIRA
        // continuam validando contra os seus, no mesmo framework.
        assertThat(facade.prepararImunizacaoRotina(registroLocal().build())
                        .bundle()
                        .getEntry())
                .hasSize(2);
    }

    @Test
    @DisplayName("Com receita embutida, o MedicationDispense carrega quatro resources contidos")
    void receitaEmbutidaGeraContidos() {
        DocumentoPreparado doc =
                facade.prepararDispensacaoMedicamento(dispensacaoLocal().build());

        org.hl7.fhir.r4.model.MedicationDispense d = (org.hl7.fhir.r4.model.MedicationDispense)
                doc.bundle().getEntry().get(1).getResource();
        // getId() devolve o id COM o prefixo "#" quando o recurso esta contido: e assim que
        // o FHIR distingue referencia interna de referencia a entrada do Bundle.
        assertThat(d.getContained())
                .extracting(org.hl7.fhir.r4.model.Resource::getId)
                .containsExactly("#prescription", "#prescriber-identifier", "#prescriber-specialty", "#pharmacist-crf");

        // Nove domínios traduzidos: cinco na dispensação, quatro na receita.
        assertThat(doc.trilha().size()).isEqualTo(9);
        System.out.println("[Facade] trilha do REDFM-2024-0001:");
        System.out.println(doc.trilha().toRelatorio());
    }

    @Test
    @DisplayName("Com receita na RNDS, não há contidos do prescritor e a trilha encolhe")
    void receitaNaRndsEncolheATrilha() {
        DocumentoPreparado doc = facade.prepararDispensacaoMedicamento(dispensacaoLocal()
                .prescricao(null)
                .idPrescricaoNaRnds("563BFD7432")
                .build());

        org.hl7.fhir.r4.model.MedicationDispense d = (org.hl7.fhir.r4.model.MedicationDispense)
                doc.bundle().getEntry().get(1).getResource();
        assertThat(d.getContained())
                .as("se a receita está na RNDS, os dados de quem prescreveu vêm com ela")
                .extracting(org.hl7.fhir.r4.model.Resource::getId)
                .containsExactly("#pharmacist-crf");

        assertThat(doc.trilha().size())
                .as("cinco domínios; os quatro da receita não existem")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("As duas formas de receita são mutuamente exclusivas")
    void asDuasFormasDeReceitaSaoExclusivas() {
        assertThatThrownBy(() -> facade.prepararDispensacaoMedicamento(
                        dispensacaoLocal().idPrescricaoNaRnds("563BFD7432").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutuamente");
    }

    // ══ SA: o maior documento ═══════════════════════════════════════════

    private static RegistroSumarioAltaDTO.ItemPrescricaoDTO itemLocal(String medicamento) {
        return RegistroSumarioAltaDTO.ItemPrescricaoDTO.builder()
                .medicamentoLocal(medicamento)
                .formaFarmaceuticaLocal("COMPRIMIDO")
                .viaAdministracaoLocal("VO")
                .dose(BigDecimal.ONE)
                .unidadeDoseLocal("CAPSULA")
                .doseMaximaPorAdministracao(BigDecimal.ONE)
                .frequencia(1)
                .vezes(1)
                .vezesMaximo(1)
                .turnoLocal("NOITE")
                .situacao(VocabularioClinicoFhir.SituacaoPrescricao.ATIVA)
                .build();
    }

    private static RegistroSumarioAltaDTO.RegistroSumarioAltaDTOBuilder sumarioLocal(String medicamento) {
        Instant alta = Instant.parse("2021-01-25T11:00:00Z");
        return RegistroSumarioAltaDTO.builder()
                .idLocalRegistro("SA-2021-0001")
                .cpfPaciente("01234567890")
                .cnesEstabelecimento("0123456")
                .dataRegistroOrigem(alta)
                .modalidadeAssistencialLocal("HOSPITALAR")
                .contato(RegistroSumarioAltaDTO.ContatoAssistencialDTO.builder()
                        .caraterAtendimentoLocal("URGENCIA")
                        .dataInicio(Instant.parse("2021-01-18T11:00:00Z"))
                        .dataFim(alta)
                        .procedenciaLocal("DEMANDA_ESPONTANEA")
                        .motivoDesfechoLocal("ENCAMINHAMENTO")
                        .cnsProfissionalAlta("898005900000003")
                        .cboProfissionalAltaLocal("CLINICO_GERAL")
                        .build())
                .diagnosticos(List.of(
                        RegistroSumarioAltaDTO.DiagnosticoDTO.builder()
                                .cid10Local("PNEUMONIA_VSR")
                                .ordem(1)
                                .presenteNaAdmissao(true)
                                .build(),
                        RegistroSumarioAltaDTO.DiagnosticoDTO.builder()
                                .cid10Local("COVID_NE")
                                .ordem(2)
                                .build()))
                .procedimentos(List.of(RegistroSumarioAltaDTO.ProcedimentoDTO.builder()
                        .procedimentoLocal("TC_TORAX")
                        .dataRealizacao(Instant.parse("2021-01-20T12:00:00Z"))
                        .financiamentoLocal("PLANO_PUBLICO")
                        .cboExecutanteLocal("CLINICO_GERAL")
                        .quantidade(1)
                        .build()))
                .alergia(RegistroSumarioAltaDTO.AlergiaDTO.builder()
                        .agenteLocal("DIPIRONA_SOLUCAO")
                        .categoria(VocabularioClinicoFhir.CategoriaAlergia.MEDICAMENTO)
                        .criticidade(VocabularioClinicoFhir.CriticidadeAlergia.ALTA)
                        .verificacao(VocabularioClinicoFhir.VerificacaoAlergia.CONFIRMADA)
                        .manifestacaoLocal("ANAFILAXIA")
                        .build())
                .prescricaoAlta(RegistroSumarioAltaDTO.PrescricaoAltaDTO.builder()
                        .cnsPrescritor("898005900000003")
                        .dataPrescricao(alta)
                        .itens(List.of(itemLocal(medicamento)))
                        .build())
                .resumoEvolucaoClinica("Evoluiu com melhora do quadro respiratório.")
                .planoCuidados("Retorno ambulatorial em 15 dias.")
                .informacoesAdicionais("Alta com encaminhamento à unidade básica.");
    }

    @Test
    @DisplayName("O Sumário de Alta sem diagnóstico atravessa a Camada 4")
    void sumarioSemDiagnosticoAtravessaACamadaQuatro() {
        // Medido em 13/10 no snapshot do pacote do SA: a seção problemasDiagnosticosAvaliados é
        // 0..*, e o Encounter.diagnosis — 1..*, fatiado por condition.resolve() — tem a fatia
        // problemAndDiagnosis 0..* e a fatia procedure 1..*. Quem satisfaz o mínimo do pai é o
        // procedimento, não o diagnóstico. Chegar ao fim deste método já é o veredicto: a
        // Camada 4 valida antes de a Facade devolver o documento.
        int comDiagnostico = facade.prepararSumarioAlta(
                        sumarioLocal("PARACETAMOL_500").build())
                .bundle()
                .getEntry()
                .size();

        DocumentoPreparado doc = facade.prepararSumarioAlta(
                sumarioLocal("PARACETAMOL_500").diagnosticos(null).build());
        org.hl7.fhir.r4.model.Bundle b = doc.bundle();

        // Afirmação relativa, e não um número mágico: saem exatamente os dois Condition.
        assertThat(b.getEntry()).as("duas entradas menos").hasSize(comDiagnostico - 2);
        assertThat(b.getEntry().stream()
                        .map(org.hl7.fhir.r4.model.Bundle.BundleEntryComponent::getResource)
                        .filter(r -> r instanceof org.hl7.fhir.r4.model.Condition)
                        .toList())
                .as("nenhum Condition sobra no Bundle")
                .isEmpty();

        org.hl7.fhir.r4.model.Encounter enc = (org.hl7.fhir.r4.model.Encounter) b.getEntry().stream()
                .map(org.hl7.fhir.r4.model.Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof org.hl7.fhir.r4.model.Encounter)
                .findFirst()
                .orElseThrow();
        assertThat(enc.getDiagnosis())
                .as("o mínimo 1..* do diagnosis vem da fatia de procedimento")
                .hasSize(1);
    }

    @Test
    @DisplayName("PRINCÍPIO DE OURO: o sexto serviço, o maior documento, pelas mesmas três linhas")
    void sextoServicoPontaAPonta() {
        DocumentoPreparado doc =
                facade.prepararSumarioAlta(sumarioLocal("PARACETAMOL_500").build());

        // Sem restrição funcional informada: doze entradas, e não as treze do golden.
        assertThat(doc.bundle().getEntry()).hasSize(12);
        // Dezessete traduções: modalidade, quatro no contato, dois diagnósticos, três no
        // procedimento, duas na alergia e cinco no item de prescrição.
        assertThat(doc.trilha().size()).isEqualTo(17);

        org.hl7.fhir.r4.model.Encounter e =
                (org.hl7.fhir.r4.model.Encounter) doc.bundle().getEntry().get(1).getResource();
        assertThat(e.getPriority().getCodingFirstRep().getSystem())
                .as("no SA, caráter de atendimento é BRCaraterAtendimento; no RIRA, request-priority do HL7")
                .endsWith("/BRCaraterAtendimento");
    }

    @Test
    @DisplayName(
            "SEXTA DEMONSTRAÇÃO, ponta a ponta: dipirona a paciente com anafilaxia por dipirona atravessa todas as camadas")
    void alergiaContraditoriaAtravessaOFramework() {
        // Alergia: DIPIRONA_SOLUCAO -> BRMedicamento#BR0267205U0063, manifestação ANAFILAXIA,
        // criticidade ALTA. Prescrição: DIPIRONA_500 -> BRObmCATMAT#BR0267203. Nenhuma camada
        // do framework percebe o conflito, porque nenhum dos dois códigos informa o princípio
        // ativo. Detectá-lo exigiria tabela de substâncias — decisão em aberto, pelo ADR-05.
        DocumentoPreparado doc =
                facade.prepararSumarioAlta(sumarioLocal("DIPIRONA_500").build());
        assertThat(doc.bundle().getEntry()).isNotEmpty();
    }

    // REMOVIDO em 10/10, com o motivo medido: o Sumário de Alta não representa indivíduo não
    // identificado. A seção resumoEvolucaoClinica é 1..1, o ClinicalImpression.subject é 1..1
    // no R4 base, a extensão BRIndividuoNaoIdentificado-1.0 não é permitida nesse contexto e
    // subject.display é max=0 em todos os perfis do SA — não sobra onde pôr o indivíduo. Este
    // teste afirmava o contrário e foi reprovado pela própria Camada 4: defeito nº 20, com os
    // doze MustSupport declarados como exclusão no CoberturaMustSupportTest. O caso volta no
    // RAC, onde as três seções obrigatórias são contextos permitidos para a extensão.

    @Test
    @DisplayName("OITAVA DEMONSTRAÇÃO, ponta a ponta: o RAC representa o indivíduo que o SA não representa")
    void individuoNaoIdentificadoAtravessaORac() {
        // Mesmo atendimento, sem CNS nem CPF. Saem os blocos cujos perfis exigem
        // subject.identifier 1..1 — alergia, prescrição, plano de cuidados e atestado. Ficam as
        // três seções obrigatórias e as medidas observadas, que não emitem subject.
        DocumentoPreparado doc = facade.prepararAtendimentoClinico(atendimentoLocal(peso("88.15", "kg"))
                .cnsPaciente(null)
                .individuoNaoIdentificado(RegistroAtendimentoClinicoDTO.IndividuoNaoIdentificadoDTO.builder()
                        .sexo(VocabularioClinicoFhir.SexoIndividuo.MASCULINO)
                        .anoNascimento(1980)
                        .justificativaLocal("COMATOSO")
                        .build())
                .alergias(null)
                .prescricao(null)
                .planoCuidados(null)
                .atestados(null)
                .build());

        // Chegar aqui já é o veredicto: a Camada 4 valida antes de devolver o documento. O que
        // segue confere que a extensão está nos QUATRO contextos permitidos, e que nenhum deles
        // levou identificador — as duas formas são excludentes.
        org.hl7.fhir.r4.model.Bundle b = doc.bundle();
        org.hl7.fhir.r4.model.Composition comp =
                (org.hl7.fhir.r4.model.Composition) b.getEntry().get(0).getResource();
        org.hl7.fhir.r4.model.Encounter enc =
                (org.hl7.fhir.r4.model.Encounter) b.getEntry().get(1).getResource();
        org.hl7.fhir.r4.model.Condition cond = (org.hl7.fhir.r4.model.Condition) b.getEntry().stream()
                .map(org.hl7.fhir.r4.model.Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof org.hl7.fhir.r4.model.Condition)
                .findFirst()
                .orElseThrow();
        org.hl7.fhir.r4.model.Procedure proc = (org.hl7.fhir.r4.model.Procedure) b.getEntry().stream()
                .map(org.hl7.fhir.r4.model.Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof org.hl7.fhir.r4.model.Procedure)
                .findFirst()
                .orElseThrow();

        for (org.hl7.fhir.r4.model.Reference s :
                List.of(comp.getSubject(), enc.getSubject(), cond.getSubject(), proc.getSubject())) {
            assertThat(s.hasIdentifier()).as("o subject não leva identificador").isFalse();
            org.hl7.fhir.r4.model.Extension ni = s.getExtensionByUrl(
                    "http://www.saude.gov.br/fhir/r4/StructureDefinition/BRIndividuoNaoIdentificado-1.0");
            assertThat(ni).as("extensão de indivíduo não identificado").isNotNull();
            assertThat(ni.getExtension()).as("gender, birthYear e reason").hasSize(3);
        }

        // A justificativa atravessou o Motor Semântico: COMATOSO -> BRJustificativaIndividuoNaoIdentificado.
        org.hl7.fhir.r4.model.Extension razao = enc.getSubject()
                .getExtensionByUrl("http://www.saude.gov.br/fhir/r4/StructureDefinition/BRIndividuoNaoIdentificado-1.0")
                .getExtensionByUrl("reason");
        assertThat(((org.hl7.fhir.r4.model.CodeableConcept) razao.getValue())
                        .getCodingFirstRep()
                        .getSystem())
                .endsWith("/BRJustificativaIndividuoNaoIdentificado");
    }

    @Test
    @DisplayName("No RAC, não identificado é excludente com o identificador e com quatro blocos")
    void naoIdentificadoNoRacRecusaOIncompativel() {
        // As duas formas juntas.
        assertThatThrownBy(() -> facade.prepararAtendimentoClinico(atendimentoLocal(peso("88.15", "kg"))
                        .individuoNaoIdentificado(RegistroAtendimentoClinicoDTO.IndividuoNaoIdentificadoDTO.builder()
                                .sexo(VocabularioClinicoFhir.SexoIndividuo.IGNORADO)
                                .anoNascimento(1990)
                                .justificativaLocal("COMATOSO")
                                .build())
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nunca os dois juntos");

        // E os blocos que exigem identificador: a alergia basta para provar a regra.
        assertThatThrownBy(() -> facade.prepararAtendimentoClinico(atendimentoLocal(peso("88.15", "kg"))
                        .cnsPaciente(null)
                        .individuoNaoIdentificado(RegistroAtendimentoClinicoDTO.IndividuoNaoIdentificadoDTO.builder()
                                .sexo(VocabularioClinicoFhir.SexoIndividuo.IGNORADO)
                                .anoNascimento(1990)
                                .justificativaLocal("COMATOSO")
                                .build())
                        .prescricao(null)
                        .planoCuidados(null)
                        .atestados(null)
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exigem subject.identifier 1..1");
    }

    @Test
    @DisplayName("Indivíduo não identificado e identificador juntos são recusados na construção")
    void naoIdentificadoEhExcludenteComIdentificador() {
        assertThatThrownBy(() -> facade.prepararSumarioAlta(sumarioLocal("PARACETAMOL_500")
                        .individuoNaoIdentificado(RegistroSumarioAltaDTO.IndividuoNaoIdentificadoDTO.builder()
                                .sexo(VocabularioClinicoFhir.SexoIndividuo.IGNORADO)
                                .anoNascimento(1990)
                                .justificativaLocal("COMATOSO")
                                .build())
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nunca os dois juntos");

        // E a alergia, cujo perfil exige identifier, é incompatível com a extensão.
        assertThatThrownBy(() -> facade.prepararSumarioAlta(sumarioLocal("PARACETAMOL_500")
                        .cpfPaciente(null)
                        .individuoNaoIdentificado(RegistroSumarioAltaDTO.IndividuoNaoIdentificadoDTO.builder()
                                .sexo(VocabularioClinicoFhir.SexoIndividuo.IGNORADO)
                                .anoNascimento(1990)
                                .justificativaLocal("COMATOSO")
                                .build())
                        .prescricaoAlta(null)
                        .planoCuidados(null)
                        .informacoesAdicionais(null)
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exigem subject.identifier");
    }

    @Test
    @DisplayName("A construção exige Identificador Solicitante e pelo menos uma estratégia")
    void construcaoIncompletaFalhaCedo() {
        assertThatThrownBy(() -> RndsFramework.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Identificador Solicitante");

        assertThatThrownBy(() -> RndsFramework.builder()
                        .solicitanteId("29376")
                        .servico(Servico.RIA, "conformance")
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhuma estratégia de mapeamento registrada");
    }

    @Test
    @DisplayName("Domínio exigido sem estratégia registrada é recusado, não ignorado")
    void dominioExigidoSemEstrategiaFalha() {
        RndsIntegrationFacade incompleta = RndsFramework.builder()
                .solicitanteId(SOLICITANTE)
                .fhirContext(FhirContext.forR4())
                .servico(Servico.RIA, "conformance")
                .mapping(new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml"))
                .build();

        assertThatThrownBy(() ->
                        incompleta.prepararImunizacaoRotina(registroLocal().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhuma estratégia de mapeamento registrada")
                .hasMessageContaining("GRUPO_ATENDIMENTO");
    }

    @Test
    @DisplayName("A proveniência distingue o código de origem do alvo, campo a campo")
    void provenienciaDistingueOrigemEAlvo() {
        DocumentoPreparado doc = facade.prepararImunizacaoRotina(registroLocal().build());

        MappingProvenance vacina = doc.trilha().getRegistros().stream()
                .filter(p -> p.getDomain() == TerminologyDomain.VACINA)
                .findFirst()
                .orElseThrow();

        assertThat(vacina.getSourceValue()).isEqualTo("105");
        assertThat(vacina.getTargetValue()).isEqualTo("87");
        assertThat(vacina.getValidatedBy()).contains("COREN-SP 123456");
    }

    // ══ RAC: o sétimo serviço ═══════════════════════════════════════════

    private static RegistroAtendimentoClinicoDTO.MedidaObservadaDTO peso(String valor, String unidade) {
        return RegistroAtendimentoClinicoDTO.MedidaObservadaDTO.builder()
                .medidaLocal("PESO")
                .valor(new BigDecimal(valor))
                .unidadeUcum(unidade)
                .metodoLocal("DE_PE")
                .origemMedidaLocal("MEDIDO")
                .build();
    }

    private static RegistroAtendimentoClinicoDTO.RegistroAtendimentoClinicoDTOBuilder atendimentoLocal(
            RegistroAtendimentoClinicoDTO.MedidaObservadaDTO medida) {
        Instant consulta = Instant.parse("2022-05-09T12:00:00Z");
        return RegistroAtendimentoClinicoDTO.builder()
                .idLocalRegistro("RAC-2022-0001")
                .cnsPaciente("819217217061857")
                .cnesEstabelecimento("0123456")
                .dataRegistroOrigem(consulta)
                .modalidadeAssistencialLocal("HOSPITALAR")
                .contato(RegistroAtendimentoClinicoDTO.ContatoAssistencialDTO.builder()
                        .caraterAtendimentoLocal("ELETIVO")
                        .dataInicio(Instant.parse("2022-05-09T11:00:00Z"))
                        .dataFim(consulta)
                        .procedenciaLocal("DEMANDA_ESPONTANEA")
                        .motivoDesfechoLocal("ENCAMINHAMENTO")
                        .responsabilidadeParticipanteLocal("ATENDIMENTO")
                        .cnsProfissional("898005900000003")
                        .cboProfissionalLocal("CLINICO_GERAL")
                        .outrasInformacoes("Paciente refere dor há três dias.")
                        .build())
                .diagnosticos(List.of(RegistroAtendimentoClinicoDTO.DiagnosticoDTO.builder()
                        .problemaDiagnosticoLocal("VARIZES_ESOFAGO")
                        .ordem(1)
                        .papelLocal("NAO_PRESENTE_ADMISSAO")
                        .build()))
                .procedimentos(List.of(RegistroAtendimentoClinicoDTO.ProcedimentoDTO.builder()
                        .procedimentoLocal("CONSULTA_NS_AE")
                        .dataRealizacao(consulta)
                        .financiamentoLocal("FEDERAL_SUS")
                        .cboExecutanteLocal("CLINICO_GERAL")
                        .quantidade(1)
                        .build()))
                .medidas(List.of(
                        medida,
                        RegistroAtendimentoClinicoDTO.MedidaObservadaDTO.builder()
                                .medidaLocal("EXPOSICAO_SUBSTANCIAS")
                                .valorCodigoLocal("ALCOOL")
                                .frequenciaUsoLocal("RARO")
                                .build()))
                .alergias(List.of(RegistroAtendimentoClinicoDTO.AlergiaDTO.builder()
                        .agenteLocal("SORO_ANTITETANICO")
                        .categoria(VocabularioClinicoFhir.CategoriaAlergia.BIOLOGICO)
                        .criticidade(VocabularioClinicoFhir.CriticidadeAlergia.ALTA)
                        .verificacao(VocabularioClinicoFhir.VerificacaoAlergia.CONFIRMADA)
                        .manifestacaoLocal("ANAFILAXIA")
                        .build()))
                .prescricao(RegistroAtendimentoClinicoDTO.PrescricaoDTO.builder()
                        .cnsPrescritor("898005900000003")
                        .dataPrescricao(consulta)
                        .itens(List.of(RegistroAtendimentoClinicoDTO.ItemPrescricaoDTO.builder()
                                .medicamentoLocal("PARACETAMOL_500")
                                .formaFarmaceuticaLocal("COMPRIMIDO")
                                .viaAdministracaoLocal("VO")
                                .dose(BigDecimal.ONE)
                                .unidadeDoseLocal("CAPSULA")
                                .doseMaximaPorAdministracao(BigDecimal.ONE)
                                .frequencia(1)
                                .vezes(1)
                                .vezesMaximo(1)
                                .turnoLocal("NOITE")
                                .situacao(VocabularioClinicoFhir.SituacaoPrescricao.ATIVA)
                                .build()))
                        .build())
                .planoCuidados("Retorno em 15 dias.")
                .atestados(List.of(RegistroAtendimentoClinicoDTO.AtestadoDTO.builder()
                        .dataInicioAfastamento(consulta)
                        .diasAfastamento(3)
                        .informacoesAdicionais("Afastamento por três dias.")
                        .diagnosticos(List.of(RegistroAtendimentoClinicoDTO.CidAvaliadoDTO.builder()
                                .cid10Local("VARIZES_ESOFAGO")
                                .categoriaDiagnosticoLocal("PRINCIPAL")
                                .build()))
                        .build()));
    }

    @Test
    @DisplayName("PRINCÍPIO DE OURO: o sétimo e último serviço do escopo, pelas mesmas três linhas")
    void setimoServicoPontaAPonta() {
        DocumentoPreparado doc = facade.prepararAtendimentoClinico(
                atendimentoLocal(peso("88.15", "kg")).build());

        // Treze entradas: sem local de atendimento nem motivo, que são opcionais.
        assertThat(doc.bundle().getEntry()).hasSize(13);
        // Vinte e seis traduções, em onze domínios — o maior número do escopo. A medida de
        // peso sozinha são três: o código, a posição do indivíduo e a origem do dado.
        assertThat(doc.trilha().size()).isEqualTo(26);

        // A condição endereçada pelo atestado entra no Bundle, mas não é seção.
        org.hl7.fhir.r4.model.Composition rac = (org.hl7.fhir.r4.model.Composition)
                doc.bundle().getEntry().get(0).getResource();
        assertThat(rac.getSection()).hasSize(9);
    }

    @Test
    @DisplayName("SÉTIMA DEMONSTRAÇÃO: peso corporal de 1,83 metro atravessa as quatro camadas")
    void pesoEmMetrosAtravessaOFramework() {
        // O código LOINC 29463-7 é Peso Corporal. O valor, 1,83 m, é uma altura. O perfil
        // exige que o código esteja no ValueSet e que o valor seja uma Quantity — não que a
        // unidade combine com o conceito medido. Nem a Camada 2, que traduziu "PESO" para o
        // código certo, nem a Camada 4 têm como perceber.
        //
        // Não foi construído para a tese: é o que o EXEMPLO OFICIAL do RAC faz, aprovado pelo
        // validador do DATASUS. Encontrado ao buscar o display oficial do código para escrever
        // a fixture terminológica — depois de o documento ter passado por dois validadores.
        DocumentoPreparado doc = facade.prepararAtendimentoClinico(
                atendimentoLocal(peso("1.83", "m")).build());

        org.hl7.fhir.r4.model.Observation medida = (org.hl7.fhir.r4.model.Observation)
                doc.bundle().getEntry().get(4).getResource();
        assertThat(medida.getCode().getCodingFirstRep().getCode()).isEqualTo("29463-7");
        assertThat(((org.hl7.fhir.r4.model.Quantity) medida.getValue()).getCode())
                .isEqualTo("m");
        assertThat(doc.bundle().getEntry())
                .as("conformidade estrutural não é correção clínica")
                .hasSize(13);
    }

    // ══ Auditoria de emissão ════════════════════════════════════════════

    /**
     * Os sete documentos no máximo que o DTO permite — todo opcional preenchido.
     *
     * <p>São fixtures próprias da auditoria, e não as dos outros testes: ali o documento típico
     * é o que interessa, aqui é o documento completo. Um opcional que a fixture não preenche
     * aparece no relatório como se o Builder não soubesse emiti-lo, e foi isso que inflou a
     * primeira rodada.
     *
     * <p>Dois campos continuam de fora, por falta de infraestrutura e não de decisão:
     * {@code interpretacaoLocal} no REL, cujo domínio {@code INTERPRETACAO_RESULTADO} não tem
     * fixture nem estratégia registrada, e a família do indivíduo não identificado, que não
     * existe em DTO nenhum.
     */
    private static Map<String, org.hl7.fhir.r4.model.Bundle> documentosMaximos() {
        Instant quando = Instant.parse("2024-12-30T14:46:12Z");
        Map<String, org.hl7.fhir.r4.model.Bundle> documentos = new LinkedHashMap<>();

        documentos.put(
                "RIA-R",
                facade.prepararImunizacaoRotina(registroLocal()
                                .cid10Local("PNEUMONIA_VSR")
                                .dataInicioCondicao(Instant.parse("2021-01-10T10:00:00Z"))
                                .observacaoCondicao("Confirmada em prontuário.")
                                .build())
                        .bundle());

        documentos.put(
                "RIA-C",
                facade.prepararImunizacaoCampanha(campanhaLocal().build()).bundle());

        documentos.put(
                "REL",
                facade.prepararExameLaboratorial(laudoLocal()
                                .exames(List.of(exameLocal()
                                        .interpretacaoLocal("DETECTAVEL")
                                        .observacao("Amostra levemente hemolisada.")
                                        .build()))
                                .build())
                        .bundle());

        documentos.put(
                "RIRA",
                facade.prepararRegulacaoAssistencial(regulacaoLocal()
                                .observacaoCondicao("Encaminhada pela UBS.")
                                .build())
                        .bundle());

        documentos.put(
                "REDFM",
                facade.prepararDispensacaoMedicamento(dispensacaoLocal()
                                .observacaoDispensacao("Entrega parcial autorizada pelo prescritor.")
                                .substituiDocumentoRnds("Composition/REDFM-2024-0000")
                                .prescricao(RegistroDispensacaoMedicamentoDTO.PrescricaoDTO.builder()
                                        .idItemPrescrito("e6f89964-b218-4fb3-9603-e0897c5c2181")
                                        .dataPrescricao(quando)
                                        .cnsPrescritor("898005900000003")
                                        .conselhoPrescritor("crm-sp")
                                        .inscricaoPrescritor("154735")
                                        .cboPrescritorLocal("ALERGISTA")
                                        .cnesLotacaoPrescritor("0123456")
                                        .cid10Local("HIPERTENSAO")
                                        .viaAdministracaoLocal("VO")
                                        .dose(BigDecimal.ONE)
                                        .unidadeDoseLocal("CAPSULA")
                                        .descricaoUnidadeDose("Cápsula")
                                        .observacao("Tomar com alimento.")
                                        .build())
                                .build())
                        .bundle());

        documentos.put(
                "SA",
                facade.prepararSumarioAlta(sumarioLocal("PARACETAMOL_500")
                                .contato(RegistroSumarioAltaDTO.ContatoAssistencialDTO.builder()
                                        .caraterAtendimentoLocal("URGENCIA")
                                        .dataInicio(Instant.parse("2021-01-18T11:00:00Z"))
                                        .dataFim(Instant.parse("2021-01-25T11:00:00Z"))
                                        .procedenciaLocal("DEMANDA_ESPONTANEA")
                                        .motivoDesfechoLocal("ENCAMINHAMENTO")
                                        .cnsProfissionalAlta("898005900000003")
                                        .cnesLotacaoProfissionalAlta("0123456")
                                        .cboProfissionalAltaLocal("CLINICO_GERAL")
                                        .equipe(7)
                                        .localAtendimento("Enfermaria")
                                        .motivoAtendimento("Dispneia progressiva há três dias.")
                                        .build())
                                .diagnosticos(List.of(RegistroSumarioAltaDTO.DiagnosticoDTO.builder()
                                        .cid10Local("PNEUMONIA_VSR")
                                        .ordem(1)
                                        .presenteNaAdmissao(true)
                                        .observacao("Confirmada por imagem.")
                                        .build()))
                                .procedimentos(List.of(RegistroSumarioAltaDTO.ProcedimentoDTO.builder()
                                        .procedimentoLocal("TC_TORAX")
                                        .dataRealizacao(Instant.parse("2021-01-20T12:00:00Z"))
                                        .numeroAutorizacao("462921-M-13-17-026312-9159748-3")
                                        .financiamentoLocal("PLANO_PUBLICO")
                                        .cboExecutanteLocal("CLINICO_GERAL")
                                        .quantidade(1)
                                        .cnesEmNomeDe("0123456")
                                        .equipe(7)
                                        .observacao("Sem intercorrências.")
                                        .build()))
                                .alergia(RegistroSumarioAltaDTO.AlergiaDTO.builder()
                                        .agenteLocal("DIPIRONA_SOLUCAO")
                                        .descricaoAgente("Dipirona sódica 500 mg/mL solução oral")
                                        .categoria(VocabularioClinicoFhir.CategoriaAlergia.MEDICAMENTO)
                                        .criticidade(VocabularioClinicoFhir.CriticidadeAlergia.ALTA)
                                        .tipo(VocabularioClinicoFhir.TipoAlergia.ALERGIA)
                                        .verificacao(VocabularioClinicoFhir.VerificacaoAlergia.CONFIRMADA)
                                        .dataInicio(LocalDate.of(2019, 3, 12))
                                        .manifestacaoLocal("ANAFILAXIA")
                                        .observacao("Relatada pela família.")
                                        .build())
                                .restricaoFuncional("Deambulação limitada a curtas distâncias.")
                                .build())
                        .bundle());

        documentos.put(
                "RAC",
                facade.prepararAtendimentoClinico(atendimentoLocal(peso("88.15", "kg"))
                                .contato(RegistroAtendimentoClinicoDTO.ContatoAssistencialDTO.builder()
                                        .caraterAtendimentoLocal("ELETIVO")
                                        .dataInicio(Instant.parse("2022-05-09T11:00:00Z"))
                                        .dataFim(Instant.parse("2022-05-09T12:00:00Z"))
                                        .procedenciaLocal("DEMANDA_ESPONTANEA")
                                        .motivoDesfechoLocal("ENCAMINHAMENTO")
                                        .responsabilidadeParticipanteLocal("ATENDIMENTO")
                                        .cnsProfissional("898005900000003")
                                        .cboProfissionalLocal("CLINICO_GERAL")
                                        .equipe(2)
                                        .localAtendimento("Consultório")
                                        .motivoAtendimento("Dor epigástrica há três dias.")
                                        .outrasInformacoes("Paciente refere dor há três dias.")
                                        .build())
                                .diagnosticos(List.of(RegistroAtendimentoClinicoDTO.DiagnosticoDTO.builder()
                                        .problemaDiagnosticoLocal("VARIZES_ESOFAGO")
                                        .ordem(1)
                                        .papelLocal("NAO_PRESENTE_ADMISSAO")
                                        .observacao("Sem sinais de sangramento.")
                                        .build()))
                                .procedimentos(List.of(RegistroAtendimentoClinicoDTO.ProcedimentoDTO.builder()
                                        .procedimentoLocal("CONSULTA_NS_AE")
                                        .dataRealizacao(Instant.parse("2022-05-09T12:00:00Z"))
                                        .numeroAutorizacao("462921-M-13-17-026312-9159748-3")
                                        .financiamentoLocal("FEDERAL_SUS")
                                        .cboExecutanteLocal("CLINICO_GERAL")
                                        .cnsExecutante("898005900000003")
                                        .cnesLotacaoExecutante("0123456")
                                        .cnesEmNomeDe("0123456")
                                        .equipe(2)
                                        .quantidade(1)
                                        .observacao("Procedimento sem intercorrências.")
                                        .build()))
                                .alergias(List.of(RegistroAtendimentoClinicoDTO.AlergiaDTO.builder()
                                        .agenteLocal("SORO_ANTITETANICO")
                                        .descricaoAgente("Soro antitetânico heterólogo")
                                        .categoria(VocabularioClinicoFhir.CategoriaAlergia.BIOLOGICO)
                                        .criticidade(VocabularioClinicoFhir.CriticidadeAlergia.ALTA)
                                        .tipo(VocabularioClinicoFhir.TipoAlergia.ALERGIA)
                                        .verificacao(VocabularioClinicoFhir.VerificacaoAlergia.CONFIRMADA)
                                        .dataInicio(LocalDate.of(2020, 7, 4))
                                        .manifestacaoLocal("ANAFILAXIA")
                                        .observacao("Soro antitetânico")
                                        .build()))
                                .atestados(List.of(RegistroAtendimentoClinicoDTO.AtestadoDTO.builder()
                                        .dataInicioAfastamento(Instant.parse("2022-05-09T12:00:00Z"))
                                        .diasAfastamento(3)
                                        .informacoesAdicionais("Afastamento por três dias.")
                                        .diagnosticos(List.of(RegistroAtendimentoClinicoDTO.CidAvaliadoDTO.builder()
                                                .cid10Local("VARIZES_ESOFAGO")
                                                .categoriaDiagnosticoLocal("PRINCIPAL")
                                                .observacao("Avaliado em consulta ambulatorial.")
                                                .build()))
                                        .build()))
                                .build())
                        .bundle());

        return documentos;
    }

    /**
     * As formas que não cabem no mesmo documento — hoje, nenhuma.
     *
     * <p>O {@code subject} leva o identificador nacional OU a extensão de indivíduo não
     * identificado, nunca os dois: um documento só jamais emitiria as duas famílias de
     * elementos, e por isso a auditoria aceita uma segunda forma do mesmo serviço.
     *
     * <p>O mapa está vazio porque o Sumário de Alta — único serviço em que a segunda forma foi
     * tentada — não a representa. A seção resumoEvolucaoClinica é 1..1, o
     * {@code ClinicalImpression.subject} é 1..1 no R4 base, a extensão não é permitida nesse
     * contexto e {@code subject.display} é max=0 em todos os perfis: sem CNS nem CPF não resta
     * forma de preencher o subject. Medido pela Camada 4 em 10/10 — defeito nº 20.
     *
     * <p>O RAC entra aqui quando a família for implementada lá: suas três seções obrigatórias
     * — informacoesContatoAssistencial, problemasDiagnosticosAvaliados e
     * procedimentosRealizados — são todas contextos permitidos para a extensão.
     */
    private static Map<String, org.hl7.fhir.r4.model.Bundle> variantesNaoIdentificado() {
        Map<String, org.hl7.fhir.r4.model.Bundle> variantes = new LinkedHashMap<>();
        variantes.put(
                "RAC",
                facade.prepararAtendimentoClinico(atendimentoLocal(peso("88.15", "kg"))
                                .cnsPaciente(null)
                                .individuoNaoIdentificado(
                                        RegistroAtendimentoClinicoDTO.IndividuoNaoIdentificadoDTO.builder()
                                                .sexo(VocabularioClinicoFhir.SexoIndividuo.MASCULINO)
                                                .anoNascimento(1980)
                                                .justificativaLocal("COMATOSO")
                                                .build())
                                .alergias(null)
                                .prescricao(null)
                                .planoCuidados(null)
                                .atestados(null)
                                .build())
                        .bundle());
        return variantes;
    }

    @Test
    @DisplayName("GATE: os MustSupport sem emissão são exatamente os declarados inexequíveis")
    void gateDeEmissaoMustSupport() {
        Map<String, org.hl7.fhir.r4.model.Bundle> documentos = documentosMaximos();
        Map<String, org.hl7.fhir.r4.model.Bundle> variantes = variantesNaoIdentificado();

        List<String> medidas = new java.util.ArrayList<>();
        for (Map.Entry<String, org.hl7.fhir.r4.model.Bundle> e : documentos.entrySet()) {
            org.hl7.fhir.r4.model.Bundle outraForma = variantes.get(e.getKey());
            (outraForma == null
                            ? AuditoriaEmissao.auditarEImprimir(ctx, e.getKey(), e.getValue())
                            : AuditoriaEmissao.auditarEImprimir(ctx, e.getKey(), e.getValue(), outraForma))
                    .forEach(l -> medidas.add(e.getKey() + " · " + l.perfil() + " · " + l.elemento()));
        }
        System.out.printf("%n══ MustSupport sem emissão nos sete documentos: %d%n", medidas.size());

        // Deixou de ser relatório. A igualdade vale nos dois sentidos de propósito: lacuna nova
        // reprova, e lacuna que deixou de existir reprova também — quem implementa um MustSupport
        // declarado inexequível tem de apagar a declaração, e quem apaga uma declaração sem
        // implementar vê a suíte vermelha. Era este o passo que faltava para o número 30 deixar
        // de ser alegação.
        assertThat(documentos)
                .as("os sete documentos do escopo foram auditados")
                .hasSize(7);
        assertThat(medidas)
                .as("toda lacuna medida é declarada inexequível, e toda declarada ainda é lacuna")
                .containsExactlyInAnyOrderElementsOf(AuditoriaEmissao.LACUNAS_DECLARADAS.keySet());
    }
}
