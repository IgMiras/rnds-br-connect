package br.rndsconnect.tools;

import br.rndsconnect.builder.RacBundleBuilder;
import br.rndsconnect.builder.RedfmBundleBuilder;
import br.rndsconnect.builder.SaBundleBuilder;
import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico;
import br.rndsconnect.model.EnrichedRegistroDispensacaoMedicamento;
import br.rndsconnect.model.EnrichedRegistroDispensacaoMedicamento.Prescricao;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta;
import br.rndsconnect.model.VocabularioClinicoFhir;
import ca.uhn.fhir.context.FhirContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exporta os Bundles que os Builders produzem, para submissao ao validador desktop do DATASUS.
 *
 * <p>Nao verifica nada -- e ferramenta, nao teste de comportamento. O que ela permite e
 * confrontar o veredicto da Camada 4 com o do programa a que um integrador real e mandado:
 * o {@code SOA-RNDS_ValidadorLocal_Aplicativo}, aplicacao Swing cuja classe de entrada e
 * {@code ui.FhirValidacaoWindow}.
 *
 * <p><strong>O que esse confronto e, e o que nao e.</strong> Medido em 12/10 no {@code libs/}
 * do aplicativo: ele embute <strong>HAPI FHIR 5.1.0</strong> -- o mesmo
 * {@code InstanceValidator} que a Camada 4 usa na versao 7.6.0. Nao e implementacao
 * independente, e chamar assim seria contradizer o que o Javadoc do
 * {@code LocalFhirValidator} ja dizia: «mesmo motor de validacao». A independencia que resta
 * e tripla, e vale: o pacote (349 arquivos soltos contra o fecho estrito de 142), a geracao
 * do motor (duas versoes maiores de distancia) e o argumento institucional -- e o validador
 * do proprio orgao.
 *
 * <p>No caso do RAC a pergunta era mais forte: o documento sem identificador nacional -- a
 * oitava demonstracao -- nunca havia sido visto por nada fora do framework. <strong>Veredicto
 * de 12/10: os dois Bundles aprovados sem erro</strong>, contra os 349 artefatos do
 * {@code arquivos_definicoes_SA}. O pacote NAO e o {@code datasus-pacote-completo}: ali o
 * {@code BRRegistroAtendimentoClinico} e 3.0 e nenhuma secao chega a ser avaliada, de modo
 * que o veredicto seria sobre outro contrato (ADR-09, Secao 8.9). E o nome nao descreve o
 * conteudo -- o "completo" tem 343 XML, e o do servico, 349.
 *
 * <p>No caso do REDFM, a pergunta e especifica: o modo {@code --estrito} do fecho ignora
 * {@code targetProfile} e bindings nao-{@code required}. Um ValueSet ausente num binding fraco
 * pode gerar <em>warning</em> em vez de erro, e warnings sao filtrados. Se o validador oficial
 * aprovar o mesmo Bundle que a Camada 4 aprova, a exclusao nao escondeu nada.
 *
 * @author Igor Mendes Domingues Miras
 */
class ExportarBundlesTest {

    private static final Path DESTINO = Paths.get(System.getProperty("java.io.tmpdir"));
    private static final Instant QUANDO = Instant.parse("2024-12-30T14:46:12Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static FhirContext ctx;

    @BeforeAll
    static void preparar() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        ctx = FhirContext.forR4();
    }

    @Test
    @DisplayName("Exporta o Bundle do REDFM com receita embutida e com receita na RNDS")
    void exportarRedfm() throws Exception {
        RedfmBundleBuilder builder = new RedfmBundleBuilder(
                "12345", () -> String.format("ff000000-0000-4000-8000-%012d", SEQ.incrementAndGet()), () -> QUANDO);

        gravar("redfm-receita-embutida.json", builder.build(dispensacao().build()));

        SEQ.set(100);
        gravar(
                "redfm-receita-na-rnds.json",
                builder.build(dispensacao()
                        .prescricao(null)
                        .idPrescricaoNaRnds("563BFD7432")
                        .build()));
    }

    @Test
    @DisplayName("Exporta o Bundle do SA com todos os opcionais, e a sexta demonstração")
    void exportarSa() throws Exception {
        SaBundleBuilder builder = new SaBundleBuilder(
                "12345", () -> String.format("5a000000-0000-4000-8000-%012d", SEQ.incrementAndGet()), () -> QUANDO);

        // Máximo: todos os opcionais preenchidos. Além do Builder, verifica que o pacote estrito
        // do SA — com o BREntregaSuprimentos presente só pela estrutura — não escondeu erro
        // que o validador oficial, com o pacote completo, apontaria.
        SEQ.set(200);
        gravar("sa-gerado.json", builder.build(sumario("BR0267778").build()));

        // Sexta demonstração: dipirona prescrita a paciente com anafilaxia por dipirona.
        SEQ.set(300);
        gravar(
                "sa-alergia-contraditoria-gerado.json",
                builder.build(sumario("BR0267203").build()));
    }

    @Test
    @DisplayName("Exporta o Bundle completo do RAC e o do indivíduo não identificado")
    void exportarRac() throws Exception {
        RacBundleBuilder builder = new RacBundleBuilder(
                "12345", () -> String.format("7a000000-0000-4000-8000-%012d", SEQ.incrementAndGet()), () -> QUANDO);

        // Completo: 24 entradas, 18 seções. Carrega a sétima demonstração — o peso de 1,83 m
        // está entre as onze medidas observadas, com o mesmo código LOINC do peso de verdade.
        SEQ.set(400);
        gravar("rac-gerado.json", builder.build(racCompleto().build()));

        // Oitava demonstração: sem CNS nem CPF. Saem alergia, prescrição, plano de cuidados,
        // atestado e motivo de atendimento — os perfis deles exigem subject.identifier 1..1 ou
        // não permitem a extensão. As medidas ficam: o BRMedidaObservada não emite subject.
        SEQ.set(500);
        gravar(
                "rac-nao-identificado-gerado.json",
                builder.build(racMinimo()
                        .cnsPaciente(null)
                        .individuoNaoIdentificado(EnrichedRegistroAtendimentoClinico.IndividuoNaoIdentificado.builder()
                                .sexo(VocabularioClinicoFhir.SexoIndividuo.MASCULINO)
                                .anoNascimento(1980)
                                .justificativa(rnds("BRJustificativaIndividuoNaoIdentificado", "03"))
                                .build())
                        .contato(racContato()
                                .localAtendimento("Consultório")
                                .outrasInformacoes("Paciente trazido inconsciente pelo resgate.")
                                .build())
                        .medidas(racMedidas())
                        .build()));
    }

    private static CodedValue rnds(String cs, String code) {
        return CodedValue.rnds(cs, code);
    }

    private static EnrichedRegistroSumarioAlta.EnrichedRegistroSumarioAltaBuilder sumario(String catmat) {
        Instant alta = Instant.parse("2021-01-25T11:00:00Z");
        return EnrichedRegistroSumarioAlta.builder()
                .idLocalRegistro("SA-EXPORT-0001")
                .cnsPaciente("706004324048847")
                .cnesEstabelecimento("1113333")
                .dataRegistroOrigem(alta)
                .modalidadeAssistencial(rnds("BRModalidadeAssistencial", "04"))
                .contato(EnrichedRegistroSumarioAlta.ContatoAssistencial.builder()
                        .caraterAtendimento(rnds("BRCaraterAtendimento", "02"))
                        .dataInicio(Instant.parse("2021-01-18T11:00:00Z"))
                        .dataFim(alta)
                        .procedencia(rnds("BRProcedencia", "09"))
                        .motivoDesfecho(rnds("BRMotivoDesfecho", "03"))
                        .cnsProfissionalAlta("815113499149874")
                        .cnesLotacaoProfissionalAlta("3191384")
                        .cboProfissionalAlta(rnds("BRCBO", "225125"))
                        .localAtendimento("Unidade de Terapia Intensiva")
                        .motivoAtendimento("Febre persistente e dispneia.")
                        .build())
                .diagnosticos(List.of(
                        EnrichedRegistroSumarioAlta.Diagnostico.builder()
                                .cid10(rnds("BRCID10", "J121"))
                                .ordem(1)
                                .presenteNaAdmissao(true)
                                .observacao("Diagnóstico principal")
                                .build(),
                        EnrichedRegistroSumarioAlta.Diagnostico.builder()
                                .cid10(rnds("BRCID10", "B342"))
                                .ordem(2)
                                .build()))
                .procedimentos(List.of(EnrichedRegistroSumarioAlta.Procedimento.builder()
                        .procedimento(rnds("BRTabelaSUS", "0206020040"))
                        .dataRealizacao(Instant.parse("2021-01-20T12:20:32Z"))
                        .numeroAutorizacao("350001-M-24-01-000001-0000001-5")
                        .quantidade(8)
                        .financiamento(rnds("BRFinanciamento", "02"))
                        .cboExecutante(rnds("BRCBO", "225125"))
                        .cnesExecutante("1113333")
                        .cnesEmNomeDe("1113333")
                        .observacao("Texto livre")
                        .build()))
                .restricaoFuncional("Mobilidade reduzida no período de internação")
                .alergia(EnrichedRegistroSumarioAlta.Alergia.builder()
                        .agente(rnds("BRMedicamento", "BR0267205U0063"))
                        .categoria(VocabularioClinicoFhir.CategoriaAlergia.MEDICAMENTO)
                        .criticidade(VocabularioClinicoFhir.CriticidadeAlergia.ALTA)
                        .tipo(VocabularioClinicoFhir.TipoAlergia.ALERGIA)
                        .verificacao(VocabularioClinicoFhir.VerificacaoAlergia.CONFIRMADA)
                        .dataInicio(LocalDate.of(2025, 9, 17))
                        .manifestacao(rnds("BRMedDRA", "10002198"))
                        .observacao("Alergia a dipirona sódica")
                        .build())
                .prescricaoAlta(EnrichedRegistroSumarioAlta.PrescricaoAlta.builder()
                        .cnsPrescritor("898005900000003")
                        .dataPrescricao(alta)
                        .itens(List.of(EnrichedRegistroSumarioAlta.ItemPrescricao.builder()
                                .medicamento(rnds("BRObmCATMAT", catmat))
                                .formaFarmaceutica(rnds("BRUnidadeMedida", "19"))
                                .viaAdministracao(rnds("BRViaAdministracao", "10907"))
                                .dose(new BigDecimal("10"))
                                .unidadeDose(rnds("BRUnidadeMedida", "13"))
                                .doseMaximaPorAdministracao(new BigDecimal("10"))
                                .frequencia(12)
                                .vezes(1)
                                .vezesMaximo(1)
                                .turno(rnds("BRTurno", "3"))
                                .periodo(1)
                                .unidadePeriodo(VocabularioClinicoFhir.UnidadeTempo.MINUTO)
                                .seNecessario(true)
                                .instrucaoPaciente("Tomar uma vez ao dia via oral.")
                                .situacao(VocabularioClinicoFhir.SituacaoPrescricao.CONCLUIDA)
                                .observacao("Tratamento da febre")
                                .build()))
                        .build())
                .resumoEvolucaoClinica(
                        "Evoluiu com agravamento do desconforto respiratório, com melhora após tratamento.")
                .planoCuidados("Repouso pós-medicação intramuscular")
                .informacoesAdicionais("Após 7 dias de tratamento recebeu alta com encaminhamento.");
    }

    // ══ Fixtures do RAC, copiadas valor por valor do RacBundleBuilderTest ═══

    private static final String RAC_CNS = "819217217061851";
    private static final String RAC_CNES = "3191384";
    private static final String RAC_PROF = "815113499149874";
    private static final Instant RAC_ATENDIMENTO = Instant.parse("2022-05-09T12:00:00Z");

    /** O {@code BRTipoObservacao-1.0} exige a barra final — {@code http://loinc.org} reprova. */
    private static CodedValue loinc(String code) {
        return new CodedValue("https://loinc.org/", code);
    }

    private static EnrichedRegistroAtendimentoClinico.ContatoAssistencial.ContatoAssistencialBuilder racContato() {
        return EnrichedRegistroAtendimentoClinico.ContatoAssistencial.builder()
                .caraterAtendimento(rnds("BRCaraterAtendimento", "01"))
                .dataInicio(Instant.parse("2022-04-09T12:00:00Z"))
                .dataFim(RAC_ATENDIMENTO)
                .procedencia(rnds("BRProcedencia", "99"))
                .motivoDesfecho(rnds("BRMotivoDesfecho", "06"))
                .responsabilidadeParticipante(rnds("BRResponsabilidadeParticipante", "solicitante"))
                .cnsProfissional(RAC_PROF)
                .cnesLotacaoProfissional(RAC_CNES)
                .cboProfissional(rnds("BRCBO", "223505"))
                .equipe(2);
    }

    /** As onze medidas do artefato de referência, nas três formas de valor que o perfil admite. */
    private static List<EnrichedRegistroAtendimentoClinico.MedidaObservada> racMedidas() {
        return List.of(
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("29463-7"))
                        .valor(new BigDecimal("1.83"))
                        .unidadeUcum("m")
                        .metodo(loinc("LA11870-5"))
                        .origemMedida(loinc("3137-7"))
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("8280-0"))
                        .valor(new BigDecimal("135.5"))
                        .unidadeUcum("cm")
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("8665-2"))
                        .valorData(Instant.parse("2022-05-23T00:00:00Z"))
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("56832-9"))
                        .valorCodigo(rnds("BRTipoSubstanciaUso", "alcool"))
                        .frequenciaUso(rnds("BRFrequenciaUsoSubstancia", "uma-ou-duas"))
                        .observacao("Uso ocasional.")
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("11885-1"))
                        .valor(new BigDecimal("3.5"))
                        .unidadeUcum("wk")
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("9843-4"))
                        .valor(new BigDecimal("45.5"))
                        .unidadeUcum("cm")
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("29463-7"))
                        .valor(new BigDecimal("88.15"))
                        .unidadeUcum("kg")
                        .metodo(loinc("LA11870-5"))
                        .origemMedida(loinc("3138-5"))
                        .roupasUsadas(loinc("LA11873-9"))
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("8480-6"))
                        .valor(new BigDecimal("0.15"))
                        .unidadeUcum("mm[Hg]")
                        .metodo(loinc("LA11868-9"))
                        .localAfericao(rnds("BRLocalAfericao", "6"))
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("11612-9"))
                        .valor(new BigDecimal("1"))
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("11996-6"))
                        .valor(new BigDecimal("2"))
                        .build(),
                EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(loinc("11996-6"))
                        .valorCodigo(rnds("BRTipoAleitamentoMaterno", "predominante"))
                        .build());
    }

    /** Só o que o perfil obriga: contato, um diagnóstico e um procedimento. */
    private static EnrichedRegistroAtendimentoClinico.EnrichedRegistroAtendimentoClinicoBuilder racMinimo() {
        return EnrichedRegistroAtendimentoClinico.builder()
                .idLocalRegistro("RAC-EXPORT-0001")
                .cnsPaciente(RAC_CNS)
                .cnesEstabelecimento(RAC_CNES)
                .dataRegistroOrigem(RAC_ATENDIMENTO)
                .modalidadeAssistencial(rnds("BRModalidadeAssistencial", "04"))
                .contato(racContato().build())
                .diagnosticos(List.of(EnrichedRegistroAtendimentoClinico.Diagnostico.builder()
                        .problemaDiagnostico(rnds("BRCID10", "I859"))
                        .ordem(1)
                        .papel(rnds("BRPapelProblemaDiagnostico", "NAD"))
                        .observacao("Varizes esofagianas sem sangramento.")
                        .build()))
                .procedimentos(List.of(EnrichedRegistroAtendimentoClinico.Procedimento.builder()
                        .procedimento(rnds("BRTabelaSUS", "0301010048"))
                        .dataRealizacao(RAC_ATENDIMENTO)
                        .numeroAutorizacao("462921-M-13-17-026312-9159748-3")
                        .quantidade(23)
                        .financiamento(rnds("BRFinanciamento", "01"))
                        .cboExecutante(rnds("BRCBO", "223605"))
                        .cnsExecutante(RAC_PROF)
                        .cnesLotacaoExecutante(RAC_CNES)
                        .cnesEmNomeDe("162338254590005")
                        .equipe(2)
                        .build()));
    }

    /** O documento inteiro, como o artefato de referência: 24 entradas e 18 seções. */
    private static EnrichedRegistroAtendimentoClinico.EnrichedRegistroAtendimentoClinicoBuilder racCompleto() {
        return racMinimo()
                .contato(racContato()
                        .localAtendimento("Consultório")
                        .motivoAtendimento("Varizes esofagianas sem sangramento.")
                        .outrasInformacoes("Paciente se queixando da presença de sangue na saliva.")
                        .build())
                .medidas(racMedidas())
                .alergias(List.of(EnrichedRegistroAtendimentoClinico.Alergia.builder()
                        .agente(rnds("BRImunobiologico", "2"))
                        .categoria(VocabularioClinicoFhir.CategoriaAlergia.ALIMENTO)
                        .criticidade(VocabularioClinicoFhir.CriticidadeAlergia.BAIXA)
                        .verificacao(VocabularioClinicoFhir.VerificacaoAlergia.CONFIRMADA)
                        .dataInicio(LocalDate.of(2022, 6, 1))
                        .observacao("Soro antitetânico")
                        .build()))
                .prescricao(EnrichedRegistroAtendimentoClinico.Prescricao.builder()
                        .cnsPrescritor("898005900000003")
                        .dataPrescricao(RAC_ATENDIMENTO)
                        .itens(List.of(EnrichedRegistroAtendimentoClinico.ItemPrescricao.builder()
                                .medicamento(rnds("BRObmCATMAT", "BR0267778"))
                                .formaFarmaceutica(rnds("BRUnidadeMedida", "19"))
                                .viaAdministracao(rnds("BRViaAdministracao", "10907"))
                                .dose(new BigDecimal("10"))
                                .unidadeDose(rnds("BRUnidadeMedida", "13"))
                                .doseMaximaPorAdministracao(new BigDecimal("10"))
                                .frequencia(12)
                                .vezes(1)
                                .vezesMaximo(1)
                                .turno(rnds("BRTurno", "3"))
                                .periodo(1)
                                .unidadePeriodo(VocabularioClinicoFhir.UnidadeTempo.MINUTO)
                                .seNecessario(true)
                                .instrucaoPaciente("Tomar uma vez ao dia via oral.")
                                .situacao(VocabularioClinicoFhir.SituacaoPrescricao.CONCLUIDA)
                                .build()))
                        .build())
                .planoCuidados("Repouso pós-medicação intramuscular")
                .atestados(List.of(EnrichedRegistroAtendimentoClinico.Atestado.builder()
                        .dataInicioAfastamento(RAC_ATENDIMENTO)
                        .diasAfastamento(1)
                        .informacoesAdicionais("Nada digno de nota.")
                        .diagnosticos(List.of(EnrichedRegistroAtendimentoClinico.CidAvaliado.builder()
                                .cid10(rnds("BRCID10", "I859"))
                                .categoriaDiagnostico(rnds("BRCategoriaDiagnostico", "01"))
                                .observacao("Varizes esofagianas sem sangramento.")
                                .build()))
                        .build()));
    }

    private void gravar(String nome, Bundle b) throws Exception {
        Path arquivo = DESTINO.resolve(nome);
        Files.writeString(
                arquivo, ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(b), StandardCharsets.UTF_8);
        System.out.printf(
                "%n[exportado] %s  (%d entradas)%n", arquivo, b.getEntry().size());
    }

    // ══ Fixtures, iguais as do RedfmBundleBuilderTest ═══════════════════

    private static Prescricao.PrescricaoBuilder prescricao() {
        return Prescricao.builder()
                .idItemPrescrito("e6f89964-b218-4fb3-9603-e0897c5c2181")
                .dataPrescricao(QUANDO)
                .cnsPrescritor("704200519817090")
                .conselhoPrescritor("crm-sp")
                .inscricaoPrescritor("154735")
                .cboPrescritor(CodedValue.rnds("BRCBO", "225110"))
                .cnesLotacaoPrescritor("6156002")
                .cid10(CodedValue.rnds("BRCID10", "I10"))
                .viaAdministracao(CodedValue.rnds("BRViaAdministracao", "10907"))
                .dose(BigDecimal.ONE)
                .unidadeDose(CodedValue.rnds("BRUnidadeMedida", "13"))
                .descricaoUnidadeDose("Comprimido");
    }

    private static EnrichedRegistroDispensacaoMedicamento.EnrichedRegistroDispensacaoMedicamentoBuilder dispensacao() {
        return EnrichedRegistroDispensacaoMedicamento.builder()
                .idLocalRegistro("73549927")
                .idItemDispensado("e6f89964-b218-4fb3-9603-e0897c5c2181")
                .cnsPaciente("712176901347489")
                .cnesEstabelecimento("5354072")
                .dataRegistroOrigem(QUANDO)
                .dataEntrega(QUANDO)
                .medicamento(CodedValue.rnds("BRObmVMP", "@brasil12898180271"))
                .descricaoMedicamento("Amoxicilina 500 mg cápsula")
                .formaFarmaceutica(CodedValue.rnds("BRUnidadeMedida", "13"))
                .cnpjFabricante("21341776000145")
                .lote("587AT765H")
                .validade(LocalDate.of(2025, 10, 31))
                .categoriaDispensacao(new CodedValue(
                        "http://terminology.hl7.org/CodeSystem/medicationdispense-category", "community"))
                .tipoDispensacao(CodedValue.rnds("BRTipoDispensacaoRealizada", "fully-dispensing"))
                .quantidade(new BigDecimal("30"))
                .unidadeQuantidade("Cápsula")
                .cnsFarmaceutico("704200519817090")
                .cboFarmaceutico(CodedValue.rnds("BRCBO", "223405"))
                .conselhoFarmaceutico("crf-sp")
                .inscricaoFarmaceutico("154735")
                .prescricao(prescricao().build());
    }
}
