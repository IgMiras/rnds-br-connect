package br.rndsconnect.tools;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureDefinition;

/**
 * Auditoria de <strong>emissão</strong> de {@code MustSupport}: dado um documento pronto,
 * responde quais elementos marcados pelos perfis nunca aparecem nele.
 *
 * <h2>Por que isto existe</h2>
 *
 * <p>O {@code CoberturaMustSupportTest} conta os {@code MustSupport} dos perfis e verifica que
 * toda exclusão declarada tem justificativa. Ele <strong>não</strong> verifica emissão — o
 * cruzamento com os Builders sempre foi manual, e o manual falha: a família
 * {@code subject.extension:unidentifiedPatient} atravessa quatro serviços sem nunca ter sido
 * emitida nem declarada, e foi encontrada por acaso, enquanto se procurava o display de um
 * código LOINC.
 *
 * <p>Um número de cobertura que ninguém consegue reproduzir é uma alegação. O que vale é um
 * teste que falha quando um {@code MustSupport} deixa de ser emitido.
 *
 * <h2>Como funciona</h2>
 *
 * <p>Para cada perfil que o serviço instancia, lê o {@code snapshot} direto dos arquivos do
 * pacote — sem montar {@code ConformancePackage}, que carregaria dezenas de MB de vocabulário
 * para ler cardinalidade — e converte o {@code id} de cada elemento {@code MustSupport} numa
 * expressão FHIRPath, avaliada contra os recursos do Bundle que declaram aquele perfil.
 *
 * <p><strong>Snapshot, e não {@code differential}: medido em 13/10.</strong> O
 * {@code differential} só traz o que o perfil <em>redeclara</em>, e por isso esconde tudo o que
 * ele herda. O caso extremo é o {@code BRSumarioAlta}: differential com <strong>zero</strong>
 * elementos {@code MustSupport} e snapshot com <strong>31</strong>, herdados do
 * {@code BRConjuntoMinimoDados-1.1}. A Arquitetura v4.2 afirmava que esse perfil «não marca nenhum
 * elemento» — verdade sobre o differential, falso sobre o perfil.
 *
 * <p><strong>Sub-elemento de fatia {@code contained:} não conta.</strong> O snapshot expande
 * cada fatia de {@code contained} a partir do perfil-alvo dela, e esses alvos já são auditados
 * como perfil próprio do serviço. No {@code RNDSMedicamentoDispensadoFornecido} são 18 linhas
 * — 10 do {@code RNDSPrescricaoMedicamentoDispensado}, 4 do {@code RNDSLotacaoProfissional} e
 * 2+2 do {@code RNDSProfissional}, que sairia <em>três</em> vezes. Denominador inflado por
 * duplicata é medição pior que o differential, não melhor.
 *
 * <p><strong>Perfil sem snapshot cai no {@code differential}, e a auditoria diz quantos.</strong>
 * O pacote do RIA e do REL não publica snapshot nenhum, e os demais publicam uma minoria (RIRA
 * 11 de 127, SA 8 de 143, REDFM 26 de 281). Desde 12/11/2026 o
 * {@code scripts/gerar-snapshots.sh} gera, com o HAPI FHIR 5.1.0, o snapshot de todos os perfis
 * dos cinco pacotes, e nenhum cai no fallback. Antes, os 12 perfis auditados sem snapshot
 * derivavam todos de resource base do FHIR, que não marca {@code MustSupport}, e o fallback não
 * perdia nada: com o snapshot, os números do RIA, do REL e do RIRA são os mesmos. Ele continua
 * <strong>relatado</strong>, porque medir parcialmente e parecer completo é o erro que a
 * Arquitetura registra (§12.7).
 *
 * <p><strong>Os números mudaram com os snapshots de 12/11/2026, e não por versão.</strong> O
 * snapshot gravado do {@code BRRegistroAtendimentoClinico} estava desatualizado: trazia 7
 * elementos {@code MustSupport}, e o perfil tem 33, herdados do {@code BRConjuntoMinimoDados-1.1}
 * e das seções que o RAC fatia. A 7.6.0 e a 5.1.0, rodadas sobre o pacote atual, geram o mesmo
 * snapshot. O RAC passou de 103 para 127 elementos.
 *
 * <p><strong>E mudaram de novo quando a auditoria passou a aplicar o discriminador das
 * fatias</strong> (12/11/2026). Nove elementos que ela dava como emitidos não estavam nos
 * documentos: as seções de OPM do SA e do RAC (três cada), a de marcos de desenvolvimento do
 * RAC, a lotação do executante no REL e o número do SNCR na dispensação e na prescrição do
 * REDFM. Os três últimos foram implementados no mesmo dia; os outros, fora do escopo, estão
 * declarados em {@link #LACUNAS_DECLARADAS}. O total é de 408 elementos, 366 emitidos e 42 sem
 * emissão.
 *
 * <h2>O que ela não garante</h2>
 *
 * <p>Três limites, todos por baixo — ela subestima as lacunas, nunca as inventa:
 *
 * <ul>
 *   <li><strong>Fatias sem discriminador avaliável.</strong> Fatia de extensão é resolvida pela
 *       URL, e fatia comum, pelo discriminador do perfil: {@code profile} sobre
 *       {@code X.resolve()} e {@code value} ou {@code pattern} com valor fixo. Outro tipo de
 *       discriminador cai no caminho base, e o fatiamento aparece no relatório. Até 12/11/2026
 *       toda fatia comum caía no caminho base, e nove lacunas ficavam escondidas: as seções de
 *       OPM e de marcos de desenvolvimento, a lotação do executante no REL e o número do SNCR
 *       no REDFM.
 *   <li><strong>Depende da fixture.</strong> Um campo opcional que o Builder sabe emitir, mas
 *       que a fixture do teste não preenche, aparece como lacuna. É o comportamento desejado:
 *       obriga a fixture a exercitar tudo.
 *   <li><strong>Presença, não correção.</strong> Responde se o elemento foi emitido, não se o
 *       valor faz sentido — como o resto da Camada 4. O peso de 1,83 metro passa aqui também.
 * </ul>
 *
 * @author Igor Mendes Domingues Miras
 */
public final class AuditoriaEmissao {

    /** Um elemento {@code MustSupport} que o documento não traz. */
    public record Lacuna(String perfil, String elemento, String expressao) {}

    /** Onde está o pacote de um serviço e quais perfis ele instancia. */
    public record Servico(String diretorio, List<String> perfis) {}

    private static final String SD = "http://www.saude.gov.br/fhir/r4/StructureDefinition/";

    /**
     * Os perfis que cada documento instancia — os únicos que contam.
     *
     * <p>Mesma lista do {@code CoberturaMustSupportTest}, acrescida do RIA-C e do RAC. Contar os
     * perfis do pacote inteiro daria número enganoso: o do RIRA tem 125 perfis para quatro
     * instanciados.
     */
    public static final Map<String, Servico> SERVICOS = Map.of(
            "RIA-R",
                    new Servico(
                            "conformance",
                            List.of(
                                    "BRRegistroImunobiologicoAdministradoRotina-2.0",
                                    "BRImunobiologicoAdministrado-3.0",
                                    "BRCID10Avaliado-1.0")),
            "RIA-C",
                    new Servico(
                            "conformance",
                            List.of(
                                    "BRRegistroImunobiologicoAdministradoCampanha-2.0",
                                    "BRImunobiologicoAdministradoCampanha-2.0")),
            "REL",
                    new Servico(
                            "conformance",
                            List.of(
                                    "BRResultadoExameLaboratorial-3.2.1",
                                    "BRDiagnosticoLaboratorioClinico-3.2.1",
                                    "BRCondicaoSaude",
                                    "BRAmostraBiologica-1.0")),
            "RIRA",
                    new Servico(
                            "conformance/rira",
                            List.of(
                                    "BRRegulacaoAssistencial",
                                    "BRAgendamentoRegulacaoAssistencial",
                                    "BRRequisicaoRegulacaoAssistencial",
                                    "BRCID10Avaliado-1.0")),
            "REDFM",
                    new Servico(
                            "conformance/redfm",
                            List.of(
                                    "RNDSRegistroEletronicoDispensacaoFornecimentoMedicamento",
                                    "RNDSMedicamentoDispensadoFornecido",
                                    "RNDSMedicamentoDispensado",
                                    "RNDSPrescricaoMedicamentoDispensado",
                                    "RNDSProfissional",
                                    "RNDSLotacaoProfissional")),
            "SA",
                    new Servico(
                            "conformance/sa",
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
                                    "BRLocalAtendimento-1.0")),
            "RAC",
                    new Servico(
                            "conformance/rac",
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
                                    "BRLocalAtendimento-1.0")));

    private static final String DEFEITO_21 =
            "Contradição no próprio perfil: o BRCID10Avaliado-1.0 exige subject.identifier 1..1, com "
                    + "system e value, veda display e reference (max=0) e AINDA marca a extensão de "
                    + "indivíduo não identificado como MustSupport. A extensão existe, pela definição "
                    + "dela, para quem não pode ser identificado — satisfazer o MustSupport exigiria "
                    + "emiti-la ao lado do identificador que ela substitui. Defeito nº 21, medido nos "
                    + "pacotes do RIA-R, do RIRA e do RAC.";

    private static final String DEFEITO_20 =
            "A extensão é permitida neste perfil, mas o Sumário de Alta não a alcança: a seção "
                    + "resumoEvolucaoClinica é 1..1, o ClinicalImpression.subject é 1..1 no R4 base, a "
                    + "extensão não lista esse contexto entre os cinco que declara e subject.display é "
                    + "max=0. Sem CNS nem CPF não resta o que pôr no subject, e o documento inteiro "
                    + "fica irrepresentável. Defeito nº 20.";

    private static final String RIRA_BLOQUEADO =
            "A extensão é permitida no Composition.subject, mas o alcance obrigatório do documento "
                    + "passa pelo BRRequisicaoRegulacaoAssistencial — cujo ServiceRequest.subject só "
                    + "aceita identifier, porque a extensão não lista esse contexto — e pelo "
                    + "BRCID10Avaliado-1.0 do defeito nº 21. Nenhuma das duas barreiras depende do "
                    + "framework.";

    private static final String MEDICATION_STATUS =
            "O Medication.status é o estado do PRODUTO — active | inactive | entered-in-error —, "
                    + "informação do cadastro do medicamento e não do ato de dispensação. O sistema "
                    + "dispensador não a conhece, e fixar 'active' seria afirmar sobre o produto o que "
                    + "o registro não sabe. O DTO não expõe o campo. Note que aqui o binding é o "
                    + "medication-status correto do R4: os defeitos nº 17 e nº 18, que são do "
                    + "BRMedicamento do SA, NÃO se aplicam a este perfil — a geração RNDS* corrigiu "
                    + "o vínculo.";

    private static final String OPM =
            "Fora do escopo. A fatia informacoesOPM é herdada do BRConjuntoMinimoDados-1.1 e exige "
                    + "uma entrada BREntregaSuprimentos: órteses, próteses e materiais especiais "
                    + "entregues no atendimento. É outro modelo de informação, o SupplyDelivery, que o "
                    + "framework não implementa, e nenhum DTO tem por onde informá-lo.";

    private static final String MARCOS =
            "Fora do escopo. A fatia marcosDesenvolvimento exige um QuestionnaireResponse, os "
                    + "marcos de desenvolvimento da criança: outro modelo de informação, como registra "
                    + "o Javadoc do RegistroAtendimentoClinicoDTO.";

    private static final String PART_OF =
            "Referencia outro BRContatoAssistencial-1.0 no mesmo Bundle. Emiti-lo exige um segundo "
                    + "Encounter completo, com diagnósticos e procedimentos próprios — exclusão de "
                    + "desenho, declarada também no CoberturaMustSupportTest e no Javadoc do DTO.";

    /**
     * As lacunas de emissão <strong>declaradas inexequíveis</strong>, elemento a elemento.
     *
     * <p>É a contrapartida do relatório: o gate exige que a lista medida seja igual a esta, nos
     * dois sentidos. Lacuna nova reprova a suíte; lacuna que deixou de existir também reprova, e
     * obriga a apagar a declaração. Uma cobertura que ninguém consegue reproduzir é alegação —
     * esta lista é o que transforma o número em verificação.
     *
     * <p>Granularidade proposital: aqui é por elemento, para a máquina; no
     * {@code CoberturaMustSupportTest} é por família, em prosa, para quem lê. As duas listas
     * descrevem os mesmos fatos para leitores diferentes.
     */
    public static final Map<String, String> LACUNAS_DECLARADAS = declaradas();

    private static Map<String, String> declaradas() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String servico : List.of("RIA-R", "RIRA", "RAC")) {
            familia(m, servico, "BRCID10Avaliado-1.0", "Condition", DEFEITO_21);
        }
        // Só o snapshot enxerga estes: o differential do BRSumarioAlta não redeclara nada.
        familia(m, "SA", "BRSumarioAlta", "Composition", DEFEITO_20);
        // Até 12/11/2026 a informacoesOPM não entrava aqui: a auditoria não distinguia uma seção
        // da outra e contava a fatia como emitida. Desde que ela aplica o discriminador das
        // fatias, a lacuna aparece, e é declarada como o que é: fora do escopo.
        secao(m, "SA", "BRSumarioAlta", "informacoesOPM", true, OPM);
        secao(m, "RAC", "BRRegistroAtendimentoClinico", "informacoesOPM", true, OPM);
        secao(m, "RAC", "BRRegistroAtendimentoClinico", "marcosDesenvolvimento", false, MARCOS);
        // A lotação do executante no REL e o número do SNCR no REDFM também apareceram com a
        // auditoria das fatias, e foram implementados no mesmo dia: não constam daqui.
        m.put("REDFM · RNDSMedicamentoDispensado · Medication.status", MEDICATION_STATUS);
        familia(m, "SA", "BRContatoAssistencial-1.0", "Encounter", DEFEITO_20);
        familia(m, "SA", "BRProblemaDiagnostico", "Condition", DEFEITO_20);
        familia(m, "SA", "BRProcedimentoRealizado-1.0", "Procedure", DEFEITO_20);
        familia(m, "RIRA", "BRRegulacaoAssistencial", "Composition", RIRA_BLOQUEADO);
        m.put("SA · BRContatoAssistencial-1.0 · Encounter.partOf", PART_OF);
        m.put("RAC · BRContatoAssistencial-1.0 · Encounter.partOf", PART_OF);
        return Collections.unmodifiableMap(m);
    }

    /** A seção fatiada e, se ela exige entrada, a entrada e a referência. */
    private static void secao(
            Map<String, String> m, String servico, String perfil, String fatia, boolean comEntrada, String motivo) {
        String base = "Composition.section:" + fatia;
        List<String> els = comEntrada ? List.of(base, base + ".entry", base + ".entry.reference") : List.of(base);
        els.forEach(el -> m.put(servico + " · " + perfil + " · " + el, motivo));
    }

    /** As quatro linhas da família: a extensão e os três filhos que ela exige. */
    private static void familia(Map<String, String> m, String servico, String perfil, String recurso, String motivo) {
        String base = recurso + ".subject.extension:unidentifiedPatient";
        for (String el :
                List.of(base, base + ".extension:gender", base + ".extension:birthYear", base + ".extension:reason")) {
            m.put(servico + " · " + perfil + " · " + el, motivo);
        }
    }

    private AuditoriaEmissao() {}

    /**
     * Audita os documentos de um serviço e imprime o resultado. Devolve as lacunas.
     *
     * <p>Vários documentos por serviço, e não um: formas excludentes do mesmo dado não cabem
     * no mesmo Bundle. O indivíduo identificado e o não identificado são o caso — o
     * {@code subject} leva o identificador OU a extensão, nunca os dois —, e um documento só
     * jamais emitiria as duas famílias de elementos. Um elemento conta como emitido se
     * aparecer em qualquer um dos documentos do serviço.
     */
    public static List<Lacuna> auditarEImprimir(FhirContext ctx, String servico, Bundle... documentos) {
        Servico s = SERVICOS.get(servico);
        if (s == null) {
            throw new IllegalArgumentException("Serviço desconhecido na auditoria: " + servico);
        }
        List<StructureDefinition> perfis = carregarPerfis(ctx, s);
        IFhirPath fhirPath = ctx.newFhirPath();
        List<Lacuna> lacunas = new ArrayList<>();
        int totalMs = 0;

        List<String> naoExercitados = new ArrayList<>();
        List<String> inexequiveis = new ArrayList<>();
        int msNaoExercitados = 0;
        int contidos = 0;
        List<String> fallback = new ArrayList<>();
        // Fatias cujo discriminador a auditoria não avalia: medidas pelo caminho base, e relatadas.
        Set<String> naoDiscriminadas = new java.util.TreeSet<>();
        Map<String, Resource> porReferencia = porReferencia(documentos);

        for (StructureDefinition sd : perfis) {
            if (semSnapshot(sd)) {
                fallback.add(nomeCurto(sd.getUrl()));
            }
            List<Resource> alvos = recursosCom(documentos, sd.getUrl());
            if (alvos.isEmpty()) {
                // Perfil que o serviço instancia, mas que ESTE documento não traz: a fixture não
                // exercita o opcional. Não é lacuna do Builder, e misturar as duas coisas foi o
                // que inflou o primeiro relatório.
                Set<String> proibidosAqui = proibidos(sd);
                int n = (int) elementos(sd).stream()
                        .filter(e -> e.hasMustSupport() && e.getMustSupport() && !"0".equals(e.getMax()))
                        .filter(e -> !emContido(e.getId()))
                        .filter(e -> !sobProibido(proibidosAqui, e.getId()))
                        .count();
                naoExercitados.add(nomeCurto(sd.getUrl()) + " (" + n + ")");
                msNaoExercitados += n;
                totalMs += n;
                continue;
            }
            Set<String> proibidos = proibidos(sd);
            for (ElementDefinition el : elementos(sd)) {
                if (!el.hasMustSupport() || !el.getMustSupport() || "0".equals(el.getMax())) {
                    continue;
                }
                if (emContido(el.getId())) {
                    contidos++;
                    continue;
                }
                if (sobProibido(proibidos, el.getId())) {
                    // Filho de elemento com max=0: o perfil marca como MustSupport algo que ele
                    // mesmo proíbe. Não é lacuna do Builder, é defeito do artefato — emitir
                    // reprovaria na Camada 4.
                    inexequiveis.add(nomeCurto(sd.getUrl()) + " · " + el.getId());
                    continue;
                }
                totalMs++;
                String id = el.getId();
                if (!id.contains(".")) {
                    continue; // a raiz do recurso: presente por construção
                }
                String expressao = fhirPath(sd, el);
                if (!presente(sd, el, fhirPath, alvos, porReferencia, naoDiscriminadas)) {
                    lacunas.add(new Lacuna(nomeCurto(sd.getUrl()), id, expressao));
                }
            }
        }

        List<String> ausentes = new ArrayList<>(s.perfis());
        perfis.forEach(sd -> ausentes.remove(nomeCurto(sd.getUrl())));

        System.out.printf(
                "%n── %s: %d MustSupport · %d emitidos · %d não exercitados pela fixture · %d sem emissão%n",
                servico, totalMs, totalMs - lacunas.size() - msNaoExercitados, msNaoExercitados, lacunas.size());
        // Medir parcialmente e parecer completo é o erro da Arquitetura §12.7: as duas linhas abaixo
        // dizem o que ficou de fora da conta e por quê.
        if (contidos > 0) {
            System.out.printf(
                    "   %d MustSupport em fatia contained: não contados (auditados no perfil-alvo)%n", contidos);
        }
        if (!fallback.isEmpty()) {
            System.out.printf(
                    "   %d perfil(is) sem snapshot, medidos pelo differential: %s%n",
                    fallback.size(), String.join(", ", fallback));
        }
        if (!naoDiscriminadas.isEmpty()) {
            System.out.printf(
                    "   %d fatiamento(s) sem discriminador avaliável, medidos pelo caminho base: %s%n",
                    naoDiscriminadas.size(), String.join(", ", naoDiscriminadas));
        }
        if (!naoExercitados.isEmpty()) {
            System.out.println("   perfis ausentes deste documento: " + naoExercitados);
        }
        if (!inexequiveis.isEmpty()) {
            System.out.println("   MustSupport inexequíveis (pai com max=0): " + inexequiveis);
        }
        if (!ausentes.isEmpty()) {
            System.out.println("   perfis não encontrados no pacote: " + ausentes);
        }
        Map<String, List<String>> porPerfil = new LinkedHashMap<>();
        lacunas.forEach(l ->
                porPerfil.computeIfAbsent(l.perfil(), k -> new ArrayList<>()).add(l.elemento()));
        porPerfil.forEach((perfil, els) -> {
            System.out.println("   " + perfil);
            els.forEach(e -> System.out.println("      " + e));
        });
        return lacunas;
    }

    // ══ Leitura dos perfis ══════════════════════════════════════════════

    private static List<StructureDefinition> carregarPerfis(FhirContext ctx, Servico s) {
        List<StructureDefinition> out = new ArrayList<>();
        for (String linha : indice(s.diretorio())) {
            if (linha.isBlank()
                    || linha.startsWith("#")
                    || linha.startsWith("ValueSet/")
                    || linha.startsWith("CodeSystem/")) {
                continue;
            }
            String caminho = s.diretorio() + "/" + linha.trim();
            try (InputStream in = AuditoriaEmissao.class.getClassLoader().getResourceAsStream(caminho)) {
                if (in == null) {
                    continue;
                }
                String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
                // Guarda barata: um CodeSystem cadastral tem dezenas de MB e não interessa aqui.
                String amostra = texto.length() > 2000 ? texto.substring(0, 2000) : texto;
                if (!amostra.contains("StructureDefinition")) {
                    continue;
                }
                IBaseResource r =
                        (caminho.endsWith(".json") ? ctx.newJsonParser() : ctx.newXmlParser()).parseResource(texto);
                if (r instanceof StructureDefinition sd
                        && sd.hasUrl()
                        && s.perfis().contains(nomeCurto(sd.getUrl()))) {
                    out.add(sd);
                }
            } catch (Exception ignorado) {
                // Artefato ilegível não é assunto desta auditoria: o ConformancePackage o reporta.
            }
        }
        return out;
    }

    private static List<String> indice(String diretorio) {
        try (InputStream in = AuditoriaEmissao.class.getClassLoader().getResourceAsStream(diretorio + "/index.txt")) {
            if (in == null) {
                throw new IllegalStateException("index.txt ausente em " + diretorio);
            }
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler o índice de " + diretorio, e);
        }
    }

    // ══ Do id do elemento à expressão FHIRPath ══════════════════════════

    private static String fhirPath(StructureDefinition sd, ElementDefinition el) {
        String[] seg = el.getId().split("\\.");
        StringBuilder expr = new StringBuilder(seg[0]);
        // O id parcial acompanha a expressão: cada fatia de extensão é resolvida pelo elemento
        // que a DEFINE, não pelo elemento que está sendo auditado.
        StringBuilder idAteAqui = new StringBuilder(seg[0]);
        for (int i = 1; i < seg.length; i++) {
            String s = seg[i];
            idAteAqui.append('.').append(s);
            int dp = s.indexOf(':');
            if (dp < 0) {
                expr.append('.').append(s.replace("[x]", ""));
                continue;
            }
            String base = s.substring(0, dp);
            String fatia = s.substring(dp + 1);
            if (base.equals("extension") || base.equals("modifierExtension")) {
                expr.append('.')
                        .append(base)
                        .append("('")
                        .append(urlDaExtensao(sd, idAteAqui.toString(), fatia))
                        .append("')");
            } else {
                // FHIRPath não seleciona fatia comum. Esta expressão é só a que o relatório
                // mostra; a presença é decidida em presente(), que aplica o discriminador.
                expr.append('.').append(base).append("[").append(fatia).append("]");
            }
        }
        return expr.toString();
    }

    /**
     * A URL de uma fatia de extensão, resolvida pelo elemento que define a fatia.
     *
     * <p>Ler o {@code type.profile} do elemento <em>auditado</em> foi um erro que custou um
     * relatório inteiro: para {@code subject.extension:unidentifiedPatient.extension:gender}, o
     * elemento auditado é o {@code gender}, que não tem {@code profile}, e a URL da extensão
     * externa caía no nome da fatia — {@code extension('unidentifiedPatient')}, que não casa com
     * nada. Os três filhos apareciam como não emitidos num documento que os emitia, e só o
     * confronto com um teste verde revelou de que lado estava o erro (10/10).
     *
     * <p>Ordem de resolução: o {@code type.profile} do elemento da fatia; depois o
     * {@code fixedUri} do elemento {@code <fatia>.url}, que é onde as sub-extensões de uma
     * extensão complexa guardam a URL — relativa, como {@code "gender"}; e só então o nome da
     * fatia, que nos pacotes da RNDS coincide com a URL, mas por convenção, não por garantia.
     */
    private static String urlDaExtensao(StructureDefinition sd, String idDaFatia, String fatia) {
        ElementDefinition dona = porId(sd, idDaFatia);
        if (dona != null) {
            for (ElementDefinition.TypeRefComponent t : dona.getType()) {
                if (!t.getProfile().isEmpty()) {
                    String url = t.getProfile().get(0).getValue();
                    return url.contains("|") ? url.substring(0, url.indexOf('|')) : url;
                }
            }
        }
        ElementDefinition elUrl = porId(sd, idDaFatia + ".url");
        if (elUrl != null
                && elUrl.hasFixed()
                && elUrl.getFixed() instanceof org.hl7.fhir.r4.model.UriType u
                && u.getValue() != null) {
            return u.getValue();
        }
        return fatia;
    }

    private static ElementDefinition porId(StructureDefinition sd, String id) {
        for (ElementDefinition e : elementos(sd)) {
            if (id.equals(e.getId())) {
                return e;
            }
        }
        return null;
    }

    // ══ Avaliação ═══════════════════════════════════════════════════════

    /**
     * O elemento aparece em algum dos recursos?
     *
     * <p>Percorre o id do elemento segmento a segmento. Fatia de extensão é resolvida pela URL.
     * <strong>Fatia comum é resolvida pelo discriminador que o próprio perfil declara</strong>:
     * {@code profile} sobre {@code X.resolve()} confere o perfil do recurso referenciado em
     * {@code X}, e {@code value} ou {@code pattern} conferem o valor fixo que a fatia exige no
     * caminho do discriminador. Até 12/11/2026 a fatia comum caía no caminho base: qualquer seção
     * contava como a {@code section:informacoesOPM}, e a auditoria dava como emitidas fatias que o
     * documento não traz. Discriminador que a auditoria não sabe avaliar volta ao caminho base, e o
     * fatiamento aparece no relatório.
     */
    private static boolean presente(
            StructureDefinition sd,
            ElementDefinition el,
            IFhirPath fhirPath,
            List<Resource> alvos,
            Map<String, Resource> porReferencia,
            Set<String> naoDiscriminadas) {
        String[] seg = el.getId().split("\\.");
        List<Base> atual = new ArrayList<>(alvos);
        StringBuilder idAteAqui = new StringBuilder(seg[0]);
        try {
            for (int i = 1; i < seg.length && !atual.isEmpty(); i++) {
                String s = seg[i];
                String idPai = idAteAqui.toString();
                idAteAqui.append('.').append(s);
                int dp = s.indexOf(':');
                if (dp < 0) {
                    atual = avaliar(fhirPath, atual, s.replace("[x]", ""));
                    continue;
                }
                String base = s.substring(0, dp);
                String fatia = s.substring(dp + 1);
                if (base.equals("extension") || base.equals("modifierExtension")) {
                    atual = avaliar(
                            fhirPath, atual, base + "('" + urlDaExtensao(sd, idAteAqui.toString(), fatia) + "')");
                    continue;
                }
                List<Base> candidatos = avaliar(fhirPath, atual, base);
                List<Base> naFatia = filtrarFatia(
                        sd, porId(sd, idPai + "." + base), idAteAqui.toString(), candidatos, fhirPath, porReferencia);
                if (naFatia == null) {
                    naoDiscriminadas.add(nomeCurto(sd.getUrl()) + " · " + idPai + "." + base);
                    atual = candidatos;
                } else {
                    atual = naFatia;
                }
            }
        } catch (Exception e) {
            // Expressão que o motor não resolve conta como não emitida, e aparece no
            // relatório com a expressão ao lado — é sinal de limite da auditoria, não do
            // Builder. Melhor um falso positivo visível do que um silêncio.
            return false;
        }
        return !atual.isEmpty();
    }

    private static List<Base> avaliar(IFhirPath fhirPath, List<Base> origens, String expressao) {
        List<Base> out = new ArrayList<>();
        for (Base b : origens) {
            out.addAll(fhirPath.evaluate(b, expressao, Base.class));
        }
        return out;
    }

    /**
     * Os candidatos que pertencem à fatia, pelo discriminador do elemento fatiado.
     *
     * @return os que pertencem, ou {@code null} se algum discriminador não é avaliável
     */
    private static List<Base> filtrarFatia(
            StructureDefinition sd,
            ElementDefinition fatiado,
            String idFatia,
            List<Base> candidatos,
            IFhirPath fhirPath,
            Map<String, Resource> porReferencia) {
        if (fatiado == null || !fatiado.hasSlicing() || !fatiado.getSlicing().hasDiscriminator()) {
            return null;
        }
        List<Base> out = new ArrayList<>(candidatos);
        for (ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent d :
                fatiado.getSlicing().getDiscriminator()) {
            String caminho = d.getPath();
            if (d.getType() == ElementDefinition.DiscriminatorType.PROFILE && caminho.endsWith(".resolve()")) {
                String caminhoRef = caminho.substring(0, caminho.length() - ".resolve()".length());
                ElementDefinition ref = porId(sd, idFatia + "." + caminhoRef);
                Set<String> perfis = ref == null
                        ? Set.of()
                        : ref.getType().stream()
                                .flatMap(t -> t.getTargetProfile().stream())
                                .map(p -> p.getValue())
                                .collect(Collectors.toSet());
                if (perfis.isEmpty()) {
                    return null;
                }
                out.removeIf(item -> !referenciaPerfil(fhirPath, item, caminhoRef, perfis, porReferencia));
            } else if (d.getType() == ElementDefinition.DiscriminatorType.VALUE
                    || d.getType() == ElementDefinition.DiscriminatorType.PATTERN) {
                ElementDefinition alvo = porId(sd, idFatia + "." + caminho);
                org.hl7.fhir.r4.model.Type esperado = alvo == null
                        ? null
                        : alvo.hasFixed() ? alvo.getFixed() : alvo.hasPattern() ? alvo.getPattern() : null;
                if (esperado == null || !esperado.isPrimitive()) {
                    return null;
                }
                String valor = esperado.primitiveValue();
                out.removeIf(item -> avaliar(fhirPath, List.of(item), caminho).stream()
                        .noneMatch(v -> v.isPrimitive() && valor.equals(v.primitiveValue())));
            } else {
                return null;
            }
        }
        return out;
    }

    /** Alguma referência em {@code caminhoRef} aponta para um recurso com um dos perfis? */
    private static boolean referenciaPerfil(
            IFhirPath fhirPath,
            Base item,
            String caminhoRef,
            Set<String> perfis,
            Map<String, Resource> porReferencia) {
        for (Base b : avaliar(fhirPath, List.of(item), caminhoRef)) {
            if (!(b instanceof org.hl7.fhir.r4.model.Reference r)) {
                continue;
            }
            Resource alvo = r.getResource() instanceof Resource em
                    ? em
                    : r.hasReference() ? porReferencia.get(r.getReference()) : null;
            if (alvo != null
                    && alvo.hasMeta()
                    && alvo.getMeta().getProfile().stream().anyMatch(p -> perfis.contains(p.getValue()))) {
                return true;
            }
        }
        return false;
    }

    /** Cada entrada dos documentos, pelo {@code fullUrl} e por {@code Tipo/id}. */
    private static Map<String, Resource> porReferencia(Bundle[] documentos) {
        Map<String, Resource> out = new java.util.HashMap<>();
        for (Bundle documento : documentos) {
            for (Bundle.BundleEntryComponent e : documento.getEntry()) {
                Resource r = e.getResource();
                if (r == null) {
                    continue;
                }
                if (e.hasFullUrl()) {
                    out.putIfAbsent(e.getFullUrl(), r);
                }
                if (r.hasIdElement() && r.getIdElement().hasIdPart()) {
                    out.putIfAbsent(r.fhirType() + "/" + r.getIdElement().getIdPart(), r);
                }
            }
        }
        return out;
    }

    /**
     * Os recursos do documento que declaram um perfil — <strong>inclusive os contidos</strong>.
     *
     * <p>O REDFM carrega até quatro recursos dentro do {@code MedicationDispense}, referenciados
     * por {@code #id}: eles nunca são entrada do Bundle. A primeira versão desta auditoria só
     * olhava as entradas, e por isso acusou dezesseis lacunas inexistentes no REDFM.
     */
    private static List<Resource> recursosCom(Bundle[] documentos, String urlPerfil) {
        List<Resource> out = new ArrayList<>();
        for (Bundle documento : documentos) {
            for (Bundle.BundleEntryComponent e : documento.getEntry()) {
                coletar(e.getResource(), urlPerfil, out);
            }
        }
        return out;
    }

    private static void coletar(Resource r, String urlPerfil, List<Resource> out) {
        if (r == null) {
            return;
        }
        if (r.hasMeta() && r.getMeta().getProfile().stream().anyMatch(p -> urlPerfil.equals(p.getValue()))) {
            out.add(r);
        }
        if (r instanceof DomainResource dr) {
            dr.getContained().forEach(c -> coletar(c, urlPerfil, out));
        }
    }

    /**
     * Os elementos que valem a medição: {@code snapshot} quando o arquivo o traz,
     * {@code differential} quando não.
     *
     * <p>O fallback é contado e relatado por {@link #auditarEImprimir}. Ver o cabeçalho da
     * classe para o porquê de ele não perder nada nos pacotes atuais.
     *
     * @param sd perfil
     * @return a lista de elementos a medir
     */
    private static List<ElementDefinition> elementos(StructureDefinition sd) {
        return sd.hasSnapshot() && !sd.getSnapshot().getElement().isEmpty()
                ? sd.getSnapshot().getElement()
                : sd.getDifferential().getElement();
    }

    /** @return {@code true} se o perfil não traz snapshot e a medição caiu no differential */
    private static boolean semSnapshot(StructureDefinition sd) {
        return !sd.hasSnapshot() || sd.getSnapshot().getElement().isEmpty();
    }

    /**
     * Sub-elemento de fatia {@code contained:} — expansão do perfil-alvo da fatia, já auditado
     * como perfil próprio. Ver o cabeçalho da classe: contá-lo aqui duplicaria o elemento.
     */
    private static boolean emContido(String id) {
        return id != null && id.contains(".contained:");
    }

    /** Ids de elementos que o perfil proíbe ({@code max=0}). */
    private static Set<String> proibidos(StructureDefinition sd) {
        return elementos(sd).stream()
                .filter(e -> "0".equals(e.getMax()))
                .map(ElementDefinition::getId)
                .collect(Collectors.toSet());
    }

    private static boolean sobProibido(Set<String> proibidos, String id) {
        return proibidos.stream().anyMatch(p -> id.startsWith(p + "."));
    }

    private static String nomeCurto(String url) {
        return url.startsWith(SD) ? url.substring(SD.length()) : url.substring(url.lastIndexOf('/') + 1);
    }
}
