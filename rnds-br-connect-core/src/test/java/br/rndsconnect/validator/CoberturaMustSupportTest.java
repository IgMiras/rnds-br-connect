package br.rndsconnect.validator;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mede a cobertura de elementos {@code MustSupport} nos perfis implementados.
 *
 * <h2>Por que isto existe</h2>
 *
 * <p>Um framework que expõe apenas os campos <strong>obrigatórios</strong> de um perfil cria
 * uma limitação silenciosa: o integrador que precisa de um campo opcional não recebe erro —
 * simplesmente não encontra por onde informá-lo. Não há mensagem, não há aviso, e a
 * descoberta se dá lendo o código-fonte.
 *
 * <p>Os perfis da RNDS marcam com {@code mustSupport} os elementos que uma implementação
 * <em>deve saber tratar</em>, obrigatórios ou não. São, portanto, a medida natural de
 * cobertura — e o que este teste conta.
 *
 * <p>O que este teste <strong>verifica</strong> e o que ele apenas <strong>relata</strong>
 * precisa ficar claro, porque a diferença importa numa arguição:
 *
 * <ul>
 *   <li><strong>Verifica</strong> que toda exclusão de {@code MustSupport} tem justificativa
 *       escrita, e que o conjunto de exclusões é exatamente o registrado — acrescentar ou
 *       remover uma exige alterar este arquivo, e portanto é decisão consciente.
 *   <li><strong>Relata</strong> a contagem de {@code MustSupport} por perfil instanciado,
 *       lida no {@code differential}, sem afirmar total. A métrica de cobertura do projeto é
 *       o portão da {@code AuditoriaEmissao}, que lê o snapshot e exige igualdade entre as
 *       lacunas medidas e as declaradas (Arquitetura §12.3); este teste é o relatório por
 *       família, em prosa, das exclusões justificadas.
 * </ul>
 *
 * <p>A métrica conta os elementos marcados no {@code differential} dos perfis que o serviço
 * instancia. Não inclui os herdados de perfis-base (o CMD, no SA; o BR-Core, no REDFM) — é a
 * definição adotada desde a primeira medição, mantida para que os números sejam comparáveis.
 *
 * @author Igor Mendes Domingues Miras
 */
class CoberturaMustSupportTest {

    /**
     * Elementos {@code MustSupport} que os Builders <strong>não</strong> emitem, por decisão
     * registrada — chave {@code "SERVIÇO · elemento"}, valor com a justificativa.
     *
     * <p>Os quatro que faltavam na primeira medição — {@code Observation.note} no REL,
     * {@code Composition.relatesTo}, {@code MedicationDispense.note} e
     * {@code MedicationRequest.note} no REDFM — foram implementados, e não constam daqui.
     *
     * <p>Uma exclusão fica <em>aqui</em>, com o motivo, em vez de ficar implícita no silêncio do
     * DTO: o integrador que precisa do campo não recebe erro, apenas não encontra por onde
     * informá-lo.
     */
    private static final Map<String, String> NAO_COBERTOS = Map.of(
            "SA · Encounter.partOf",
            "Referencia outro BRContatoAssistencial-1.0 no mesmo Bundle — o contato do qual a "
                    + "internação faz parte. Emiti-lo exige um segundo Encounter completo, com "
                    + "diagnósticos e procedimentos próprios, o que não cabe num sumário de alta de "
                    + "um único contato. Registrado também no Javadoc de RegistroSumarioAltaDTO.",
            "SA · subject.extension:unidentifiedPatient (12 elementos)",
            "O BRContatoAssistencial-1.0, o BRProblemaDiagnostico e o BRProcedimentoRealizado-1.0 "
                    + "marcam a extensão como MustSupport, mas o Sumário de Alta não consegue "
                    + "carregá-la: a seção resumoEvolucaoClinica é 1..1, o ClinicalImpression.subject "
                    + "é 1..1 no R4 base, a extensão não é permitida nesse contexto e subject.display "
                    + "é max=0 em todos os perfis. Sem CNS nem CPF não resta forma de preencher o "
                    + "subject. Medido pela Camada 4 em 10/10 — defeito nº 20.",
            "RAC · Encounter.partOf",
            "Mesmo elemento, mesmo perfil, mesma razão: o BRContatoAssistencial-1.0 é o mesmo "
                    + "do SA. Um atendimento clínico que faça parte de outro contato exigiria o "
                    + "segundo Encounter inteiro no Bundle.",
            "RIA-R · subject.extension:unidentifiedPatient (4 elementos)",
            "O BRCID10Avaliado-1.0 exige subject.identifier 1..1 e veda display e reference, e ao "
                    + "mesmo tempo marca a extensão como MustSupport: ela só caberia ao lado do "
                    + "identificador que existe para substituir. Contradição no próprio perfil — "
                    + "defeito nº 21, medido em 11/10.",
            "RIRA · subject.extension:unidentifiedPatient (8 elementos)",
            "Quatro pelo defeito nº 21, no mesmo BRCID10Avaliado-1.0. Os outros quatro são do "
                    + "BRRegulacaoAssistencial, onde a extensão é permitida mas o documento não é "
                    + "representável: o alcance obrigatório passa pelo "
                    + "BRRequisicaoRegulacaoAssistencial, cujo ServiceRequest.subject só aceita "
                    + "identifier, e pelo próprio BRCID10Avaliado-1.0.",
            "RAC · subject.extension:unidentifiedPatient (4 elementos)",
            "Só as do BRCID10Avaliado-1.0, pelo defeito nº 21 — e aqui o perfil mora no atestado, "
                    + "que é 0..*. As outras doze do RAC foram IMPLEMENTADAS: o contato "
                    + "assistencial, o problema/diagnóstico e o procedimento emitem a extensão, e a "
                    + "Camada 4 aprova o documento. É o único serviço que representa indivíduo não "
                    + "identificado.");

    @Test
    @DisplayName("Toda exclusão de MustSupport tem justificativa, e o conjunto é o registrado")
    void exclusoesTemJustificativaEConjuntoConhecido() {
        assertThat(NAO_COBERTOS).allSatisfy((elemento, motivo) -> assertThat(motivo)
                .as("exclusão sem justificativa: %s", elemento)
                .isNotBlank());
        assertThat(NAO_COBERTOS.keySet())
                .as("acrescentar ou remover uma exclusão é decisão consciente, não efeito colateral")
                .containsExactlyInAnyOrder(
                        "SA · Encounter.partOf",
                        "RAC · Encounter.partOf",
                        "SA · subject.extension:unidentifiedPatient (12 elementos)",
                        "RIA-R · subject.extension:unidentifiedPatient (4 elementos)",
                        "RIRA · subject.extension:unidentifiedPatient (8 elementos)",
                        "RAC · subject.extension:unidentifiedPatient (4 elementos)");
    }

    /**
     * Os perfis que cada serviço <strong>instancia</strong> — os únicos que contam.
     *
     * <p>Contar todos os perfis do pacote daria número enganoso: o pacote do RIRA tem 68
     * perfis com {@code MustSupport}, dos quais o serviço usa três. Os demais pertencem ao
     * REL, ao CMD e a outros serviços que a distribuição arrasta junto — e nenhum deles é
     * responsabilidade do Builder do RIRA.
     *
     * <p>A diferença entre as duas contagens é, ela própria, medida do quanto os pacotes
     * oficiais excedem o que cada serviço precisa.
     */
    private static final Map<Servico, List<String>> PERFIS_INSTANCIADOS = Map.of(
            // Os tres servicos que emitem Condition usam perfis DIFERENTES para ele, e as
            // regras se contradizem: clinicalStatus e obrigatorio no BRCID10Avaliado-1.0 e
            // PROIBIDO no BRCondicaoSaude. E a terceira inversao documentada entre servicos.
            // A constante RIA cobre as DUAS variantes, rotina e campanha, como o Javadoc do
            // enum Servico declara. Os dois perfis da campanha ficaram fora desta lista desde o
            // início: 26 MustSupport de um serviço inteiro invisíveis para esta métrica, o que
            // só apareceu quando a auditoria de emissão passou a medir os sete documentos
            // separadamente. A linha do RIA imprime, agora, a união das duas variantes.
            Servico.RIA,
                    List.of(
                            "BRRegistroImunobiologicoAdministradoRotina-2.0",
                            "BRImunobiologicoAdministrado-3.0",
                            "BRCID10Avaliado-1.0",
                            "BRRegistroImunobiologicoAdministradoCampanha-2.0",
                            "BRImunobiologicoAdministradoCampanha-2.0"),
            Servico.REL,
                    List.of(
                            "BRResultadoExameLaboratorial-3.2.1",
                            "BRDiagnosticoLaboratorioClinico-3.2.1",
                            "BRCondicaoSaude",
                            "BRAmostraBiologica-1.0"),
            Servico.RIRA,
                    List.of(
                            "BRRegulacaoAssistencial",
                            "BRAgendamentoRegulacaoAssistencial",
                            "BRRequisicaoRegulacaoAssistencial",
                            "BRCID10Avaliado-1.0"),
            Servico.REDFM,
                    List.of(
                            "RNDSRegistroEletronicoDispensacaoFornecimentoMedicamento",
                            "RNDSMedicamentoDispensadoFornecido",
                            "RNDSMedicamentoDispensado",
                            "RNDSPrescricaoMedicamentoDispensado",
                            "RNDSProfissional",
                            "RNDSLotacaoProfissional"),
            Servico.SA,
                    List.of(
                            "BRSumarioAlta",
                            "BRContatoAssistencial-1.0",
                            "BRProblemaDiagnostico",
                            "BRProcedimentoRealizado-1.0",
                            "BRRestricaoFuncionalIncapacidadeSaude-1.0",
                            "BRAlergiaReacaoAdversa-1.0",
                            "BRRegistroPrescricaoMedicamento",
                            "BRPrescricaoMedicamento",
                            "BRMedicamento",
                            "BRResumoEvolucaoClinica",
                            "BRPlanoCuidados-1.0",
                            "BRObservacaoDescritiva-1.0",
                            "BRLocalAtendimento-1.0"),
            Servico.RAC,
                    List.of(
                            "BRRegistroAtendimentoClinico",
                            "BRContatoAssistencial-1.0",
                            "BRProblemaDiagnostico",
                            "BRProcedimentoRealizado-1.0",
                            "BRMedidaObservada",
                            "BRAlergiaReacaoAdversa-1.0",
                            "BRRegistroPrescricaoMedicamento",
                            "BRPrescricaoMedicamento",
                            "BRMedicamento",
                            "BRPlanoCuidados-1.0",
                            "BRAtestado",
                            "BRCID10Avaliado-1.0",
                            "BRObservacaoDescritiva-1.0",
                            "BRLocalAtendimento-1.0"));

    @Test
    @DisplayName("Relatório de MustSupport dos perfis que cada serviço instancia")
    void relatorioDeMustSupport() {
        FhirContext ctx = FhirContext.forR4();
        Map<Servico, String> pacotes = Map.of(
                Servico.RIA, "conformance",
                Servico.REL, "conformance",
                Servico.RIRA, "conformance/rira",
                Servico.REDFM, "conformance/redfm",
                Servico.SA, "conformance/sa",
                Servico.RAC, "conformance/rac");

        System.out.println();
        System.out.printf("%-8s %-10s %-10s %s%n", "serviço", "instancia", "no pacote", "MustSupport");
        System.out.println("-".repeat(58));

        for (Servico servico :
                new Servico[] {Servico.RIA, Servico.REL, Servico.RIRA, Servico.REDFM, Servico.SA, Servico.RAC}) {
            ConformancePackage p = ConformancePackage.paraServico(ctx, servico, pacotes.get(servico));
            List<String> usados = PERFIS_INSTANCIADOS.get(servico);

            int msServico = 0;
            int msPacote = 0;
            List<String> detalhe = new ArrayList<>();
            for (StructureDefinition sd : p.structureDefinitions()) {
                int n = contarMustSupport(sd);
                msPacote += n;
                String nome = sd.getUrl().substring(sd.getUrl().lastIndexOf('/') + 1);
                if (usados.contains(nome) && n > 0) {
                    msServico += n;
                    detalhe.add("    " + nome + ": " + n);
                }
            }
            System.out.printf(
                    "%-8s %-10d %-10d %d (pacote inteiro: %d)%n",
                    servico, usados.size(), p.structureDefinitions().size(), msServico, msPacote);
            detalhe.forEach(System.out::println);
        }
    }

    private static int contarMustSupport(StructureDefinition sd) {
        int n = 0;
        for (ElementDefinition el : sd.getDifferential().getElement()) {
            if ("0".equals(el.getMax())) {
                continue;
            }
            if (el.hasMustSupport() && el.getMustSupport()) {
                n++;
            }
        }
        return n;
    }
}
