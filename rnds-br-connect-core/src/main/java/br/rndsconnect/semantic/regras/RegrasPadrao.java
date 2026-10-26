package br.rndsconnect.semantic.regras;

import br.rndsconnect.dto.RegistroAtendimentoClinicoDTO;
import br.rndsconnect.dto.RegistroDispensacaoMedicamentoDTO;
import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.dto.RegistroImunizacaoCampanhaDTO;
import br.rndsconnect.dto.RegistroImunizacaoDTO;
import br.rndsconnect.dto.RegistroRegulacaoAssistencialDTO;
import br.rndsconnect.dto.RegistroSumarioAltaDTO;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico;
import br.rndsconnect.model.EnrichedRegistroDispensacaoMedicamento;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial;
import br.rndsconnect.model.EnrichedRegistroImunizacao;
import br.rndsconnect.model.EnrichedRegistroImunizacaoCampanha;
import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta;
import br.rndsconnect.semantic.CadeiaDeValidacao;
import br.rndsconnect.semantic.ParDeCadeias;

/**
 * Cadeias padrão da Frente A, uma por serviço e por momento (A1 e A2).
 *
 * <h2>Por que a Frente A tem padrão e a Frente B não</h2>
 *
 * <p>A diferença é de <strong>origem</strong>, não de conveniência. As tabelas de mapeamento
 * da Frente B pertencem à instituição: cada hospital nomeia suas vacinas de um jeito, e não
 * existe padrão possível — por isso o framework recusa a montagem sem ao menos uma estratégia
 * registrada.
 *
 * <p>As regras da Frente A vêm dos <strong>manuais do Ministério da Saúde</strong>. São
 * nacionais e idênticas para toda instituição: o Módulo 11 do CNS não varia por hospital, e a
 * RN3 do REL vale para todo laboratório. Exigir que cada integrador as redeclare seria pedir
 * que reimplementasse conhecimento universal — exatamente o que a Seção 3.1 aponta como
 * desperdício.
 *
 * <p><strong>Duas regras ficam de fora do padrão</strong>, por dependerem de tabela clínica que
 * o framework não tem autoridade para fornecer (ADR-05): a compatibilidade exame × unidade
 * (elo 5), não implementada, e a {@link InterpretacaoPorDiagnostico} (elo 7, RN1), distribuída
 * com a tabela oficial. A Facade ainda não expõe ponto de registro para cadeias opcionais: a
 * forma decidida são sete métodos tipados no builder (Arquitetura §7.2.8).
 *
 * @author Igor Mendes Domingues Miras
 */
public final class RegrasPadrao {

    private RegrasPadrao() {}

    // ══ RIA-R ═══════════════════════════════════════════════════════════

    /** @return regras estruturais do RIA-R, aplicadas antes da tradução */
    public static CadeiaDeValidacao<RegistroImunizacaoDTO> imunizacaoRotinaA1() {
        return CadeiaDeValidacao.de(
                new CnsValido<>("cnsPaciente", RegistroImunizacaoDTO::getCnsPaciente, false),
                new CpfValido<>("cpfPaciente", RegistroImunizacaoDTO::getCpfPaciente, false),
                new CnsValido<>("cnsProfissional", RegistroImunizacaoDTO::getCnsProfissional, true),
                new DataNaoFutura<>("dataRegistroOrigem", RegistroImunizacaoDTO::getDataRegistroOrigem));
    }

    /** @return regras semânticas do RIA-R, aplicadas depois da tradução */
    public static CadeiaDeValidacao<EnrichedRegistroImunizacao> imunizacaoRotinaA2() {
        return CadeiaDeValidacao.de(new EstrategiaEspecialExigeCidECbo(), new TranscricaoRelaxaFabricanteELote());
    }

    // ══ RIA-C ═══════════════════════════════════════════════════════════

    /** @return regras estruturais do RIA-C */
    public static CadeiaDeValidacao<RegistroImunizacaoCampanhaDTO> imunizacaoCampanhaA1() {
        return CadeiaDeValidacao.de(
                new CnsValido<>("cnsPaciente", RegistroImunizacaoCampanhaDTO::getCnsPaciente, false),
                new CpfValido<>("cpfPaciente", RegistroImunizacaoCampanhaDTO::getCpfPaciente, false),
                new CnsValido<>("cnsProfissional", RegistroImunizacaoCampanhaDTO::getCnsProfissional, true),
                new DataNaoFutura<>("dataRegistroOrigem", RegistroImunizacaoCampanhaDTO::getDataRegistroOrigem));
    }

    /**
     * Cadeia vazia por decisão registrada, não por omissão.
     *
     * <p>Em campanha não há estratégia de vacinação — a campanha é a estratégia — nem
     * transcrição de caderneta, e fabricante e lote já são {@code 1..1} no perfil, o que faz a
     * Camada 4 cobri-los. As duas regras condicionais do RIA-R não têm análogo aqui.
     *
     * @return cadeia vazia
     */
    public static CadeiaDeValidacao<EnrichedRegistroImunizacaoCampanha> imunizacaoCampanhaA2() {
        return CadeiaDeValidacao.vazia();
    }

    // ══ REL ═════════════════════════════════════════════════════════════

    /** @return regras estruturais do REL */
    public static CadeiaDeValidacao<RegistroExameLaboratorialDTO> exameLaboratorialA1() {
        return CadeiaDeValidacao.de(
                new CnsValido<>("cnsPaciente", RegistroExameLaboratorialDTO::getCnsPaciente, false),
                new CpfValido<>("cpfPaciente", RegistroExameLaboratorialDTO::getCpfPaciente, false),
                new DataNaoFutura<>("dataRegistroOrigem", RegistroExameLaboratorialDTO::getDataRegistroOrigem),
                new ResultadoExclusivo(),
                new InterpretacaoObrigatoriaSeQuantitativo(),
                new DatasDosExames());
    }

    /**
     * Cadeia vazia no padrão: os dois elos A2 do REL — a compatibilidade exame × unidade (elo 5)
     * e a {@link InterpretacaoPorDiagnostico} (elo 7) — dependem de tabelas clínicas e
     * são opt-in.
     *
     * @return cadeia vazia
     */
    public static CadeiaDeValidacao<EnrichedRegistroExameLaboratorial> exameLaboratorialA2() {
        return CadeiaDeValidacao.vazia();
    }

    // ══ RIRA ════════════════════════════════════════════════════════════

    /** @return regras estruturais do RIRA */
    public static CadeiaDeValidacao<RegistroRegulacaoAssistencialDTO> regulacaoAssistencialA1() {
        return CadeiaDeValidacao.de(
                new CnsValido<>("cnsPaciente", RegistroRegulacaoAssistencialDTO::getCnsPaciente, false),
                new CpfValido<>("cpfPaciente", RegistroRegulacaoAssistencialDTO::getCpfPaciente, false),
                new DataNaoFutura<>("dataRegistroOrigem", RegistroRegulacaoAssistencialDTO::getDataRegistroOrigem),
                new DataNaoFutura<>("dataRequisicao", RegistroRegulacaoAssistencialDTO::getDataRequisicao),
                new OrdemCronologica<>(
                        "dataRequisicao",
                        RegistroRegulacaoAssistencialDTO::getDataRequisicao,
                        "inicioAgendamento",
                        RegistroRegulacaoAssistencialDTO::getInicioAgendamento));
    }

    /**
     * Regras semânticas do RIRA.
     *
     * <p>Diferente do elo 7 do REL, a RN03 <strong>integra a cadeia padrão</strong>: ela não
     * depende de tabela clínica externa, e sim da estrutura do próprio código da Tabela SUS,
     * cujos dois primeiros dígitos são o grupo. É conhecimento do domínio, não decisão do
     * integrador — e por isso não se enquadra na ressalva do ADR-05.
     *
     * @return cadeia com a RN03
     */
    public static CadeiaDeValidacao<EnrichedRegistroRegulacaoAssistencial> regulacaoAssistencialA2() {
        return CadeiaDeValidacao.de(new RN03ProcedimentoExigeCbo());
    }

    // ══ REDFM ═══════════════════════════════════════════════════════════

    /**
     * Regras estruturais do REDFM.
     *
     * <p><strong>Três CNS, não um:</strong> o paciente, o farmacêutico que dispensou e o
     * prescritor da receita embutida. Nenhum dos três passava pelo Módulo 11 antes desta
     * cadeia — a estrutura se apoiava apenas no {@code exigirObrigatorios} do Builder, que
     * verifica presença, não dígito verificador.
     *
     * <p><strong>A {@code validade} do lote fica de fora, e é deliberado.</strong> É data de
     * vencimento: tem de estar no <em>futuro</em>. Submetê-la ao {@code DataNaoFutura}
     * reprovaria todo lote válido. O elo inverso, se vier a existir, é outro elo.
     *
     * @return cadeia estrutural do REDFM
     */
    public static CadeiaDeValidacao<RegistroDispensacaoMedicamentoDTO> dispensacaoMedicamentoA1() {
        return CadeiaDeValidacao.de(
                new CnsValido<>("cnsPaciente", RegistroDispensacaoMedicamentoDTO::getCnsPaciente, false),
                new CpfValido<>("cpfPaciente", RegistroDispensacaoMedicamentoDTO::getCpfPaciente, false),
                new CnsValido<>("cnsFarmaceutico", RegistroDispensacaoMedicamentoDTO::getCnsFarmaceutico, false),
                new CnsValido<RegistroDispensacaoMedicamentoDTO>(
                        "prescricao.cnsPrescritor",
                        d -> d.getPrescricao() == null
                                ? null
                                : d.getPrescricao().getCnsPrescritor(),
                        false),
                new DataNaoFutura<>("dataRegistroOrigem", RegistroDispensacaoMedicamentoDTO::getDataRegistroOrigem),
                new DataNaoFutura<>("dataEntrega", RegistroDispensacaoMedicamentoDTO::getDataEntrega),
                new DataNaoFutura<RegistroDispensacaoMedicamentoDTO>(
                        "prescricao.dataPrescricao",
                        d -> d.getPrescricao() == null
                                ? null
                                : d.getPrescricao().getDataPrescricao()),
                new OrdemCronologica<RegistroDispensacaoMedicamentoDTO>(
                        "prescricao.dataPrescricao",
                        d -> d.getPrescricao() == null
                                ? null
                                : d.getPrescricao().getDataPrescricao(),
                        "dataEntrega",
                        RegistroDispensacaoMedicamentoDTO::getDataEntrega));
    }

    /**
     * Cadeia A2 do REDFM, vazia por ora.
     *
     * <p>Os candidatos conhecidos — coerência entre forma farmacêutica e unidade de dose,
     * e entre quantidade dispensada e quantidade prescrita — dependem de tabela de produto,
     * que o ADR-05 mantém fora do framework. Vazia declarada é melhor que ausência silenciosa.
     *
     * @return cadeia vazia
     */
    public static CadeiaDeValidacao<EnrichedRegistroDispensacaoMedicamento> dispensacaoMedicamentoA2() {
        return CadeiaDeValidacao.vazia();
    }

    // ══ SA ══════════════════════════════════════════════════════════════

    /**
     * Regras estruturais do SA.
     *
     * <p>{@code cnsPaciente} entra como <strong>não obrigatório</strong>, e isso não é
     * descuido: o SA admite CPF em vez de CNS, e exigir o CNS aqui recusaria integração
     * legítima. A obrigatoriedade de haver <em>um</em> identificador é do Builder.
     *
     * <p>As datas dos procedimentos e da alergia estão em listas e não cabem num extrator
     * escalar: vão no {@code ListasDoSumario}, um elo que itera, como o {@code DatasDosExames}
     * do REL.
     *
     * @return cadeia estrutural do SA
     */
    public static CadeiaDeValidacao<RegistroSumarioAltaDTO> sumarioAltaA1() {
        return CadeiaDeValidacao.de(
                new CnsValido<>("cnsPaciente", RegistroSumarioAltaDTO::getCnsPaciente, false),
                new CpfValido<>("cpfPaciente", RegistroSumarioAltaDTO::getCpfPaciente, false),
                new CnsValido<RegistroSumarioAltaDTO>(
                        "contato.cnsProfissionalAlta",
                        d -> d.getContato() == null ? null : d.getContato().getCnsProfissionalAlta(),
                        false),
                new CnsValido<RegistroSumarioAltaDTO>(
                        "prescricaoAlta.cnsPrescritor",
                        d -> d.getPrescricaoAlta() == null
                                ? null
                                : d.getPrescricaoAlta().getCnsPrescritor(),
                        false),
                new DataNaoFutura<>("dataRegistroOrigem", RegistroSumarioAltaDTO::getDataRegistroOrigem),
                new DataNaoFutura<RegistroSumarioAltaDTO>(
                        "contato.dataInicio",
                        d -> d.getContato() == null ? null : d.getContato().getDataInicio()),
                new DataNaoFutura<RegistroSumarioAltaDTO>(
                        "contato.dataFim",
                        d -> d.getContato() == null ? null : d.getContato().getDataFim()),
                new DataNaoFutura<RegistroSumarioAltaDTO>(
                        "prescricaoAlta.dataPrescricao",
                        d -> d.getPrescricaoAlta() == null
                                ? null
                                : d.getPrescricaoAlta().getDataPrescricao()),
                new OrdemCronologica<RegistroSumarioAltaDTO>(
                        "contato.dataInicio",
                        d -> d.getContato() == null ? null : d.getContato().getDataInicio(),
                        "contato.dataFim",
                        d -> d.getContato() == null ? null : d.getContato().getDataFim()),
                new ListasDoSumario());
    }

    /**
     * Cadeia A2 do SA, vazia por ora. Ver {@link #dispensacaoMedicamentoA2()}.
     *
     * @return cadeia vazia
     */
    public static CadeiaDeValidacao<EnrichedRegistroSumarioAlta> sumarioAltaA2() {
        return CadeiaDeValidacao.vazia();
    }

    // ══ RAC ═════════════════════════════════════════════════════════════

    /**
     * Regras estruturais do RAC.
     *
     * <p>{@code cnsPaciente} <strong>tem</strong> de ser não obrigatório aqui, e a razão é mais
     * forte que no SA: o RAC é o único documento do escopo que representa o indivíduo não
     * identificado, que chega sem CNS e sem CPF (Seção 8.9, oitava demonstração). Marcá-lo
     * obrigatório reprovaria esse documento na Frente A, antes de qualquer Builder.
     *
     * <p>O {@code cnsExecutante} e a data de cada procedimento, e a data de início de cada
     * alergia, estão em listas: vão no {@code ListasDoAtendimento}. A data de início do
     * afastamento dos atestados fica de fora de propósito — atestado para repouso futuro é
     * correto.
     *
     * @return cadeia estrutural do RAC
     */
    public static CadeiaDeValidacao<RegistroAtendimentoClinicoDTO> atendimentoClinicoA1() {
        return CadeiaDeValidacao.de(
                new CnsValido<>("cnsPaciente", RegistroAtendimentoClinicoDTO::getCnsPaciente, false),
                new CpfValido<>("cpfPaciente", RegistroAtendimentoClinicoDTO::getCpfPaciente, false),
                new CnsValido<RegistroAtendimentoClinicoDTO>(
                        "contato.cnsProfissional",
                        d -> d.getContato() == null ? null : d.getContato().getCnsProfissional(),
                        false),
                new CnsValido<RegistroAtendimentoClinicoDTO>(
                        "prescricao.cnsPrescritor",
                        d -> d.getPrescricao() == null
                                ? null
                                : d.getPrescricao().getCnsPrescritor(),
                        false),
                new DataNaoFutura<>("dataRegistroOrigem", RegistroAtendimentoClinicoDTO::getDataRegistroOrigem),
                new DataNaoFutura<RegistroAtendimentoClinicoDTO>(
                        "contato.dataInicio",
                        d -> d.getContato() == null ? null : d.getContato().getDataInicio()),
                new DataNaoFutura<RegistroAtendimentoClinicoDTO>(
                        "contato.dataFim",
                        d -> d.getContato() == null ? null : d.getContato().getDataFim()),
                new DataNaoFutura<RegistroAtendimentoClinicoDTO>(
                        "prescricao.dataPrescricao",
                        d -> d.getPrescricao() == null
                                ? null
                                : d.getPrescricao().getDataPrescricao()),
                new OrdemCronologica<RegistroAtendimentoClinicoDTO>(
                        "contato.dataInicio",
                        d -> d.getContato() == null ? null : d.getContato().getDataInicio(),
                        "contato.dataFim",
                        d -> d.getContato() == null ? null : d.getContato().getDataFim()),
                new ListasDoAtendimento());
    }

    /**
     * Cadeia A2 do RAC, vazia por ora. Ver {@link #dispensacaoMedicamentoA2()}.
     *
     * @return cadeia vazia
     */
    public static CadeiaDeValidacao<EnrichedRegistroAtendimentoClinico> atendimentoClinicoA2() {
        return CadeiaDeValidacao.vazia();
    }

    // ══ Os pares, um por serviço ════════════════════════════════════════

    /** @return o par A1/A2 do RIA-R */
    public static ParDeCadeias<RegistroImunizacaoDTO, EnrichedRegistroImunizacao> imunizacaoRotina() {
        return new ParDeCadeias<>(imunizacaoRotinaA1(), imunizacaoRotinaA2());
    }

    /** @return o par A1/A2 do RIA-C */
    public static ParDeCadeias<RegistroImunizacaoCampanhaDTO, EnrichedRegistroImunizacaoCampanha> imunizacaoCampanha() {
        return new ParDeCadeias<>(imunizacaoCampanhaA1(), imunizacaoCampanhaA2());
    }

    /** @return o par A1/A2 do REL */
    public static ParDeCadeias<RegistroExameLaboratorialDTO, EnrichedRegistroExameLaboratorial> exameLaboratorial() {
        return new ParDeCadeias<>(exameLaboratorialA1(), exameLaboratorialA2());
    }

    /** @return o par A1/A2 do RIRA */
    public static ParDeCadeias<RegistroRegulacaoAssistencialDTO, EnrichedRegistroRegulacaoAssistencial>
            regulacaoAssistencial() {
        return new ParDeCadeias<>(regulacaoAssistencialA1(), regulacaoAssistencialA2());
    }

    /** @return o par A1/A2 do REDFM */
    public static ParDeCadeias<RegistroDispensacaoMedicamentoDTO, EnrichedRegistroDispensacaoMedicamento>
            dispensacaoMedicamento() {
        return new ParDeCadeias<>(dispensacaoMedicamentoA1(), dispensacaoMedicamentoA2());
    }

    /** @return o par A1/A2 do SA */
    public static ParDeCadeias<RegistroSumarioAltaDTO, EnrichedRegistroSumarioAlta> sumarioAlta() {
        return new ParDeCadeias<>(sumarioAltaA1(), sumarioAltaA2());
    }

    /** @return o par A1/A2 do RAC */
    public static ParDeCadeias<RegistroAtendimentoClinicoDTO, EnrichedRegistroAtendimentoClinico> atendimentoClinico() {
        return new ParDeCadeias<>(atendimentoClinicoA1(), atendimentoClinicoA2());
    }

    /**
     * Elos escalares aplicados a cada elemento das listas do SA.
     *
     * <p>Um extrator escalar não alcança {@code procedimentos[3].dataRealizacao}. Este elo
     * itera e reaproveita os mesmos {@code DataNaoFutura} por elemento, nomeando a posição no
     * diagnóstico — é o padrão do {@code DatasDosExames} do REL.
     *
     * <p>A {@code alergia.dataInicio} é {@code LocalDate}, e o {@code DataNaoFutura} é de
     * {@code Instant}. A conversão usa o início do dia em UTC, e isso é seguro porque a
     * tolerância do elo é de <strong>um dia inteiro</strong>, dimensionada justamente para
     * absorver fuso: o que a regra persegue é erro de ano, não deslocamento de horas.
     */
    static final class ListasDoSumario implements br.rndsconnect.semantic.RegraValidacao<RegistroSumarioAltaDTO> {

        @Override
        public void validar(RegistroSumarioAltaDTO dto) {
            if (dto.getProcedimentos() != null) {
                for (int i = 0; i < dto.getProcedimentos().size(); i++) {
                    new DataNaoFutura<RegistroSumarioAltaDTO.ProcedimentoDTO>(
                                    "procedimentos[" + i + "].dataRealizacao",
                                    RegistroSumarioAltaDTO.ProcedimentoDTO::getDataRealizacao)
                            .validar(dto.getProcedimentos().get(i));
                }
            }
            if (dto.getAlergia() != null) {
                new DataNaoFutura<RegistroSumarioAltaDTO.AlergiaDTO>("alergia.dataInicio", ListasDoSumario::inicioDoDia)
                        .validar(dto.getAlergia());
            }
        }

        @Override
        public String identificacao() {
            return "ELO-3/LISTAS-DO-SUMARIO";
        }

        /** A data de início da alergia é {@code LocalDate}; o elo é de {@code Instant}. */
        static java.time.Instant inicioDoDia(RegistroSumarioAltaDTO.AlergiaDTO a) {
            return a.getDataInicio() == null
                    ? null
                    : a.getDataInicio().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        }
    }

    /**
     * Elos escalares aplicados a cada elemento das listas do RAC.
     *
     * <p>Fecha o <strong>último CNS do escopo</strong> que não passava pelo Módulo 11: o
     * {@code procedimentos[].cnsExecutante}. Com ele, os sete documentos verificam o dígito
     * de todo CNS que emitem.
     *
     * <p><strong>O {@code atestados[].dataInicioAfastamento} fica de fora, e é deliberado.</strong>
     * Afastamento pode legitimamente começar depois da consulta — atestado emitido hoje para
     * repouso a partir de segunda é correto. Submetê-lo ao {@code DataNaoFutura} reprovaria
     * atestado válido, que é o erro mais caro: nega atendimento.
     *
     * <p>Também fica de fora exigir que o procedimento caia dentro da janela do contato
     * assistencial. Seria regra <em>inventada</em>: nenhum manual a enuncia, e a Frente A só
     * carrega regra de manual (ver o cabeçalho desta classe).
     */
    static final class ListasDoAtendimento
            implements br.rndsconnect.semantic.RegraValidacao<RegistroAtendimentoClinicoDTO> {

        @Override
        public void validar(RegistroAtendimentoClinicoDTO dto) {
            if (dto.getProcedimentos() != null) {
                for (int i = 0; i < dto.getProcedimentos().size(); i++) {
                    RegistroAtendimentoClinicoDTO.ProcedimentoDTO p =
                            dto.getProcedimentos().get(i);
                    String pos = "procedimentos[" + i + "].";
                    new DataNaoFutura<RegistroAtendimentoClinicoDTO.ProcedimentoDTO>(
                                    pos + "dataRealizacao",
                                    RegistroAtendimentoClinicoDTO.ProcedimentoDTO::getDataRealizacao)
                            .validar(p);
                    new CnsValido<RegistroAtendimentoClinicoDTO.ProcedimentoDTO>(
                                    pos + "cnsExecutante",
                                    RegistroAtendimentoClinicoDTO.ProcedimentoDTO::getCnsExecutante,
                                    false)
                            .validar(p);
                }
            }
            if (dto.getAlergias() != null) {
                for (int i = 0; i < dto.getAlergias().size(); i++) {
                    new DataNaoFutura<RegistroAtendimentoClinicoDTO.AlergiaDTO>(
                                    "alergias[" + i + "].dataInicio", ListasDoAtendimento::inicioDoDia)
                            .validar(dto.getAlergias().get(i));
                }
            }
        }

        @Override
        public String identificacao() {
            return "ELO-1+3/LISTAS-DO-ATENDIMENTO";
        }

        /** A data de início da alergia é {@code LocalDate}; o elo é de {@code Instant}. */
        static java.time.Instant inicioDoDia(RegistroAtendimentoClinicoDTO.AlergiaDTO a) {
            return a.getDataInicio() == null
                    ? null
                    : a.getDataInicio().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        }
    }

    /**
     * Elo 3b aplicado a cada exame do laudo: o resultado não pode ser liberado antes da
     * coleta da amostra, e nenhuma das duas datas pode estar no futuro.
     */
    static final class DatasDosExames implements br.rndsconnect.semantic.RegraValidacao<RegistroExameLaboratorialDTO> {

        @Override
        public void validar(RegistroExameLaboratorialDTO dto) {
            if (dto.getExames() == null) {
                return;
            }
            for (int i = 0; i < dto.getExames().size(); i++) {
                RegistroExameLaboratorialDTO.ExameDTO e = dto.getExames().get(i);
                String pos = "exames[" + i + "].";
                new DataNaoFutura<RegistroExameLaboratorialDTO.ExameDTO>(
                                pos + "dataColeta", RegistroExameLaboratorialDTO.ExameDTO::getDataColeta)
                        .validar(e);
                new DataNaoFutura<RegistroExameLaboratorialDTO.ExameDTO>(
                                pos + "dataLiberacaoLaudo",
                                RegistroExameLaboratorialDTO.ExameDTO::getDataLiberacaoLaudo)
                        .validar(e);
                new OrdemCronologica<RegistroExameLaboratorialDTO.ExameDTO>(
                                pos + "dataColeta",
                                RegistroExameLaboratorialDTO.ExameDTO::getDataColeta,
                                pos + "dataLiberacaoLaudo",
                                RegistroExameLaboratorialDTO.ExameDTO::getDataLiberacaoLaudo)
                        .validar(e);
            }
        }

        @Override
        public String identificacao() {
            return "ELO-3+3B/DATAS-DOS-EXAMES";
        }
    }
}
