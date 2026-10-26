package br.rndsconnect.semantic;

import br.rndsconnect.dto.RegistroAtendimentoClinicoDTO;
import br.rndsconnect.dto.RegistroDispensacaoMedicamentoDTO;
import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.dto.RegistroImunizacaoCampanhaDTO;
import br.rndsconnect.dto.RegistroImunizacaoDTO;
import br.rndsconnect.dto.RegistroRegulacaoAssistencialDTO;
import br.rndsconnect.dto.RegistroSumarioAltaDTO;
import br.rndsconnect.model.CodedValue;
import br.rndsconnect.model.EnrichedRegistroAtendimentoClinico;
import br.rndsconnect.model.EnrichedRegistroDispensacaoMedicamento;
import br.rndsconnect.model.EnrichedRegistroExameLaboratorial;
import br.rndsconnect.model.EnrichedRegistroImunizacao;
import br.rndsconnect.model.EnrichedRegistroImunizacaoCampanha;
import br.rndsconnect.model.EnrichedRegistroRegulacaoAssistencial;
import br.rndsconnect.model.EnrichedRegistroSumarioAlta;
import br.rndsconnect.model.TerminologyDomain;
import br.rndsconnect.semantic.regras.RegrasPadrao;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orquestrador da Camada 2: percorre o DTO canônico, traduz cada campo que exige tradução e
 * produz o DTO enriquecido junto com a trilha de auditoria.
 *
 * <p>É a peça que transforma o {@link TerminologyRegistry} — um mapa de estratégias — em uma
 * operação sobre um registro clínico inteiro. A Facade delega a ele e recebe de volta o par
 * ({@code EnrichedDTO}, {@code MappingAuditLog}), exatamente como descrito na Seção 7.1 da Arquitetura.
 *
 * <p><strong>Fail-fast por construção.</strong> Um único código sem correspondência
 * interrompe o enriquecimento inteiro com {@link UnmappedTerminologyException}. Não há
 * enriquecimento parcial: um registro com metade dos campos traduzidos e metade em branco é
 * pior que registro nenhum, porque parece completo.
 *
 * <p><strong>Ordem A1 → B → A2.</strong> As regras da Frente A que operam sobre a forma do
 * dado rodam antes da tradução, sobre o DTO canônico; as escritas no vocabulário da RNDS
 * rodam depois, sobre o DTO enriquecido. Os pares de cadeias vêm de {@link RegrasPadrao}, um
 * por documento.
 *
 * @author Igor Mendes Domingues Miras
 */
public class MappingOrchestrator {

    private final TerminologyRegistry registry;

    // Um par por serviço, não dois campos por serviço: com sete serviços o construtor de
    // injeção teria catorze posições do mesmo tipo genérico, e trocar duas delas compila.
    private final ParDeCadeias<RegistroImunizacaoDTO, EnrichedRegistroImunizacao> ria;
    private final ParDeCadeias<RegistroImunizacaoCampanhaDTO, EnrichedRegistroImunizacaoCampanha> riaCampanha;
    private final ParDeCadeias<RegistroExameLaboratorialDTO, EnrichedRegistroExameLaboratorial> rel;
    private final ParDeCadeias<RegistroRegulacaoAssistencialDTO, EnrichedRegistroRegulacaoAssistencial> rira;
    private final ParDeCadeias<RegistroDispensacaoMedicamentoDTO, EnrichedRegistroDispensacaoMedicamento> redfm;
    private final ParDeCadeias<RegistroSumarioAltaDTO, EnrichedRegistroSumarioAlta> sa;
    private final ParDeCadeias<RegistroAtendimentoClinicoDTO, EnrichedRegistroAtendimentoClinico> rac;

    /**
     * Orquestrador com as cadeias padrão da Frente A.
     *
     * @param registry registro de estratégias por domínio
     */
    public MappingOrchestrator(TerminologyRegistry registry) {
        this(
                registry,
                RegrasPadrao.imunizacaoRotina(),
                RegrasPadrao.imunizacaoCampanha(),
                RegrasPadrao.exameLaboratorial(),
                RegrasPadrao.regulacaoAssistencial(),
                RegrasPadrao.dispensacaoMedicamento(),
                RegrasPadrao.sumarioAlta(),
                RegrasPadrao.atendimentoClinico());
    }

    /**
     * Orquestrador com cadeias explícitas — permite acrescentar os elos opt-in que dependem
     * de tabela clínica, ou substituir a cadeia de um serviço.
     *
     * <p><strong>Ressalva honesta sobre este construtor:</strong> ele é hoje inalcançável de
     * fora do framework. O {@code RndsFramework.Builder} chama {@code new
     * MappingOrchestrator(registry)} fixo, e a implementação da Facade é um {@code record}
     * privado — não existe caminho para o integrador injetar cadeia própria. O ponto de
     * extensão que este Javadoc promete não existe até o Builder ganhar um gancho.
     *
     * @param registry registro de estratégias por domínio
     * @param ria par A1/A2 do RIA-R
     * @param riaCampanha par A1/A2 do RIA-C
     * @param rel par A1/A2 do REL
     * @param rira par A1/A2 do RIRA
     * @param redfm par A1/A2 do REDFM
     * @param sa par A1/A2 do Sumário de Alta
     * @param rac par A1/A2 do Registro de Atendimento Clínico
     */
    public MappingOrchestrator(
            TerminologyRegistry registry,
            ParDeCadeias<RegistroImunizacaoDTO, EnrichedRegistroImunizacao> ria,
            ParDeCadeias<RegistroImunizacaoCampanhaDTO, EnrichedRegistroImunizacaoCampanha> riaCampanha,
            ParDeCadeias<RegistroExameLaboratorialDTO, EnrichedRegistroExameLaboratorial> rel,
            ParDeCadeias<RegistroRegulacaoAssistencialDTO, EnrichedRegistroRegulacaoAssistencial> rira,
            ParDeCadeias<RegistroDispensacaoMedicamentoDTO, EnrichedRegistroDispensacaoMedicamento> redfm,
            ParDeCadeias<RegistroSumarioAltaDTO, EnrichedRegistroSumarioAlta> sa,
            ParDeCadeias<RegistroAtendimentoClinicoDTO, EnrichedRegistroAtendimentoClinico> rac) {
        this.registry = Objects.requireNonNull(registry, "registry é obrigatório");
        this.ria = Objects.requireNonNull(ria);
        this.riaCampanha = Objects.requireNonNull(riaCampanha);
        this.rel = Objects.requireNonNull(rel);
        this.rira = Objects.requireNonNull(rira);
        this.redfm = Objects.requireNonNull(redfm);
        this.sa = Objects.requireNonNull(sa);
        this.rac = Objects.requireNonNull(rac);
    }

    /**
     * Resultado do enriquecimento: o DTO traduzido e a prova de como se chegou nele.
     *
     * @param dto DTO com os códigos já no vocabulário da RNDS
     * @param trilha proveniência de cada tradução realizada
     */
    public record Resultado(EnrichedRegistroImunizacao dto, MappingAuditLog trilha) {}

    /**
     * Resultado do enriquecimento de um registro de campanha.
     *
     * @param dto DTO com os códigos já no vocabulário da RNDS
     * @param trilha proveniência de cada tradução realizada
     */
    public record ResultadoCampanha(EnrichedRegistroImunizacaoCampanha dto, MappingAuditLog trilha) {}

    /**
     * Resultado do enriquecimento de um laudo laboratorial.
     *
     * @param dto DTO com os códigos já no vocabulário da RNDS
     * @param trilha proveniência de cada tradução realizada
     */
    public record ResultadoExame(EnrichedRegistroExameLaboratorial dto, MappingAuditLog trilha) {}

    /**
     * Resultado do enriquecimento de uma solicitação de regulação.
     *
     * @param dto DTO com os códigos já no vocabulário da RNDS
     * @param trilha proveniência de cada tradução realizada
     */
    public record ResultadoRegulacao(EnrichedRegistroRegulacaoAssistencial dto, MappingAuditLog trilha) {}

    /**
     * Resultado do enriquecimento de uma dispensação de medicamento.
     *
     * @param dto DTO com os códigos já no vocabulário da RNDS
     * @param trilha proveniência de cada tradução realizada
     */
    public record ResultadoDispensacao(EnrichedRegistroDispensacaoMedicamento dto, MappingAuditLog trilha) {}

    /**
     * Resultado do enriquecimento de um Sumário de Alta.
     *
     * @param dto DTO com os códigos já no vocabulário da RNDS
     * @param trilha proveniência de cada tradução realizada
     */
    public record ResultadoSumario(EnrichedRegistroSumarioAlta dto, MappingAuditLog trilha) {}

    /**
     * Resultado do enriquecimento de um Registro de Atendimento Clínico.
     *
     * @param dto DTO com os códigos já no vocabulário da RNDS
     * @param trilha proveniência de cada tradução realizada
     */
    public record ResultadoAtendimentoClinico(EnrichedRegistroAtendimentoClinico dto, MappingAuditLog trilha) {}

    /**
     * Traduz todos os códigos locais de um registro de imunização em rotina.
     *
     * @param bruto registro no vocabulário do sistema cliente
     * @return DTO enriquecido e trilha de auditoria
     * @throws UnmappedTerminologyException se qualquer código não tiver correspondência
     * @throws IllegalStateException se faltar estratégia para algum domínio exigido
     */
    public Resultado enriquecer(RegistroImunizacaoDTO bruto) {
        Objects.requireNonNull(bruto, "dto não pode ser nulo");

        ria.a1().validar(bruto); // A1 — antes da tradução

        MappingContext ctx = MappingContext.agora(bruto.getIdLocalRegistro());
        MappingAuditLog trilha = new MappingAuditLog();

        EnrichedRegistroImunizacao enriquecido = EnrichedRegistroImunizacao.builder()
                // ── copiados sem tradução ────────────────────────────────
                .idLocalRegistro(bruto.getIdLocalRegistro())
                .cnsPaciente(bruto.getCnsPaciente())
                .cpfPaciente(bruto.getCpfPaciente())
                .cnesEstabelecimento(bruto.getCnesEstabelecimento())
                .cnsProfissional(bruto.getCnsProfissional())
                .dataRegistroOrigem(bruto.getDataRegistroOrigem())
                .dataAplicacao(bruto.getDataAplicacao())
                .lote(bruto.getLote())
                .validadeLote(bruto.getValidadeLote())
                .primarySource(bruto.getPrimarySource())
                .contatoHanseniase(bruto.getContatoHanseniase())
                .dataInicioCondicao(bruto.getDataInicioCondicao())
                .pesquisaClinica(bruto.getPesquisaClinica())

                // ── traduções obrigatórias ───────────────────────────────
                .vacina(traduzir(TerminologyDomain.VACINA, bruto.getVacinaLocal(), ctx, trilha))
                .grupoAtendimento(
                        traduzir(TerminologyDomain.GRUPO_ATENDIMENTO, bruto.getGrupoAtendimentoLocal(), ctx, trilha))
                .condicaoMaternal(
                        traduzir(TerminologyDomain.CONDICAO_MATERNAL, bruto.getCondicaoMaternalLocal(), ctx, trilha))
                .estrategiaVacinacao(traduzir(
                        TerminologyDomain.ESTRATEGIA_VACINACAO, bruto.getEstrategiaVacinacaoLocal(), ctx, trilha))
                .localAplicacao(
                        traduzir(TerminologyDomain.LOCAL_APLICACAO, bruto.getLocalAplicacaoLocal(), ctx, trilha))
                .viaAdministracao(
                        traduzir(TerminologyDomain.VIA_ADMINISTRACAO, bruto.getViaAdministracaoLocal(), ctx, trilha))
                // doseNumber é String no perfil: o Builder emite doseNumberString.
                .doseNumber(traduzirCodigo(TerminologyDomain.DOSE, bruto.getDoseLocal(), ctx, trilha))

                // ── traduções opcionais ──────────────────────────────────
                // fabricante e país viajam em identifier.value, não em coding:
                // o alvo é o code puro, e o system é fixedUri no perfil.
                .codigoFabricante(traduzirCodigo(TerminologyDomain.FABRICANTE, bruto.getFabricanteLocal(), ctx, trilha))
                .codigoPaisOrigem(traduzirCodigo(TerminologyDomain.PAIS, bruto.getPaisOrigemLocal(), ctx, trilha))
                .registroOrigem(
                        traduzir(TerminologyDomain.REGISTRO_ORIGEM, bruto.getRegistroOrigemLocal(), ctx, trilha))
                .cboPrescritor(traduzir(TerminologyDomain.OCUPACAO_CBO, bruto.getCboPrescritorLocal(), ctx, trilha))
                .cid10(traduzir(TerminologyDomain.CID10, bruto.getCid10Local(), ctx, trilha))
                .observacaoCondicao(bruto.getObservacaoCondicao())
                .build();

        ria.a2().validar(enriquecido); // A2 — depois da tradução (Seção 7.1)
        return new Resultado(enriquecido, trilha);
    }

    /**
     * Traduz os códigos locais de um registro de imunização em campanha (RIA-C).
     *
     * <p>São <strong>cinco</strong> domínios, contra sete do RIA-R. A diferença não é de
     * completude: o perfil do RIA-C proíbe {@code site} e {@code route}, e não tem extensão
     * de estratégia — em campanha, a campanha é a estratégia. Em compensação,
     * {@code FABRICANTE} passa de opcional a obrigatório.
     *
     * @param bruto registro no vocabulário do sistema cliente
     * @return DTO enriquecido e trilha de auditoria
     * @throws UnmappedTerminologyException se qualquer código não tiver correspondência
     * @throws IllegalStateException se faltar estratégia para algum domínio exigido
     */
    public ResultadoCampanha enriquecerCampanha(RegistroImunizacaoCampanhaDTO bruto) {
        Objects.requireNonNull(bruto, "dto não pode ser nulo");

        riaCampanha.a1().validar(bruto);

        MappingContext ctx = MappingContext.agora(bruto.getIdLocalRegistro());
        MappingAuditLog trilha = new MappingAuditLog();

        EnrichedRegistroImunizacaoCampanha enriquecido = EnrichedRegistroImunizacaoCampanha.builder()
                .idLocalRegistro(bruto.getIdLocalRegistro())
                .cnsPaciente(bruto.getCnsPaciente())
                .cpfPaciente(bruto.getCpfPaciente())
                .cnesEstabelecimento(bruto.getCnesEstabelecimento())
                .cnsProfissional(bruto.getCnsProfissional())
                .dataRegistroOrigem(bruto.getDataRegistroOrigem())
                .dataAplicacao(bruto.getDataAplicacao())
                .lote(bruto.getLote())
                .vacina(traduzir(TerminologyDomain.VACINA, bruto.getVacinaLocal(), ctx, trilha))
                .grupoAtendimento(
                        traduzir(TerminologyDomain.GRUPO_ATENDIMENTO, bruto.getGrupoAtendimentoLocal(), ctx, trilha))
                .condicaoMaternal(
                        traduzir(TerminologyDomain.CONDICAO_MATERNAL, bruto.getCondicaoMaternalLocal(), ctx, trilha))
                .doseNumber(traduzirCodigo(TerminologyDomain.DOSE, bruto.getDoseLocal(), ctx, trilha))
                .codigoFabricante(traduzirCodigo(TerminologyDomain.FABRICANTE, bruto.getFabricanteLocal(), ctx, trilha))
                .build();

        riaCampanha.a2().validar(enriquecido);
        return new ResultadoCampanha(enriquecido, trilha);
    }

    /**
     * Traduz os códigos locais de um laudo laboratorial (REL).
     *
     * <p>É o primeiro serviço com <strong>lista</strong>: a suspeita diagnóstica é traduzida
     * uma vez para o laudo, e os demais domínios uma vez <em>por exame</em>. Cada tradução de
     * item recebe o índice correspondente no {@link MappingContext}, sem o qual a trilha de
     * um laudo com vários exames seria ambígua.
     *
     * @param bruto laudo no vocabulário do sistema cliente
     * @return DTO enriquecido e trilha de auditoria
     * @throws UnmappedTerminologyException se qualquer código não tiver correspondência
     * @throws IllegalStateException se faltar estratégia para algum domínio exigido
     */
    public ResultadoExame enriquecerExameLaboratorial(RegistroExameLaboratorialDTO bruto) {
        Objects.requireNonNull(bruto, "dto não pode ser nulo");

        rel.a1().validar(bruto);

        MappingContext ctx = MappingContext.agora(bruto.getIdLocalRegistro());
        MappingAuditLog trilha = new MappingAuditLog();

        List<RegistroExameLaboratorialDTO.ExameDTO> brutos = bruto.getExames() == null ? List.of() : bruto.getExames();
        List<EnrichedRegistroExameLaboratorial.ExameLaboratorial> exames = new ArrayList<>();
        for (int i = 0; i < brutos.size(); i++) {
            exames.add(enriquecerExame(brutos.get(i), ctx.noItem(i), trilha));
        }

        EnrichedRegistroExameLaboratorial enriquecido = EnrichedRegistroExameLaboratorial.builder()
                .idLocalRegistro(bruto.getIdLocalRegistro())
                .cnsPaciente(bruto.getCnsPaciente())
                .cpfPaciente(bruto.getCpfPaciente())
                .cnpjLaboratorio(bruto.getCnpjLaboratorio())
                .cnesEstabelecimento(bruto.getCnesEstabelecimento())
                .dataRegistroOrigem(bruto.getDataRegistroOrigem())
                .suspeitaDiagnostica(traduzir(
                        TerminologyDomain.SUSPEITA_DIAGNOSTICA, bruto.getSuspeitaDiagnosticaLocal(), ctx, trilha))
                .exames(exames)
                .build();

        rel.a2().validar(enriquecido);
        return new ResultadoExame(enriquecido, trilha);
    }

    private EnrichedRegistroExameLaboratorial.ExameLaboratorial enriquecerExame(
            RegistroExameLaboratorialDTO.ExameDTO e, MappingContext ctx, MappingAuditLog trilha) {
        return EnrichedRegistroExameLaboratorial.ExameLaboratorial.builder()
                // Não passam por tradução: o perfil os exige como texto livre.
                .metodo(e.getMetodo())
                .valorReferencia(e.getValorReferencia())
                .observacao(e.getObservacao())
                .dataColeta(e.getDataColeta())
                .dataLiberacaoLaudo(e.getDataLiberacaoLaudo())
                // O resultado quantitativo também não: número e unidade UCUM viajam crus.
                // A compatibilidade exame × unidade seria o elo 5 da Frente A, não implementado:
                // o defeito nº 9 e o ADR-05 o deixam de fora (Arquitetura §7.2.2).
                .resultadoQuantitativo(e.getResultadoQuantitativo())
                .unidadeMedida(e.getUnidadeMedida())
                // Traduzidos:
                .nomeExame(traduzir(TerminologyDomain.NOME_EXAME, e.getNomeExameLocal(), ctx, trilha))
                .categoriaExame(
                        traduzir(TerminologyDomain.SUBGRUPO_TABELA_SUS, e.getCategoriaExameLocal(), ctx, trilha))
                .tipoAmostra(traduzir(TerminologyDomain.TIPO_AMOSTRA, e.getTipoAmostraLocal(), ctx, trilha))
                .resultadoQualitativo(traduzir(
                        TerminologyDomain.RESULTADO_QUALITATIVO, e.getResultadoQualitativoLocal(), ctx, trilha))
                .interpretacao(
                        traduzir(TerminologyDomain.INTERPRETACAO_RESULTADO, e.getInterpretacaoLocal(), ctx, trilha))
                .patogeno(traduzir(TerminologyDomain.PATOGENO, e.getPatogenoLocal(), ctx, trilha))
                .build();
    }

    /**
     * Traduz os códigos locais de uma solicitação de regulação assistencial (RIRA).
     *
     * <p>São seis domínios. Os <strong>estados</strong> da solicitação não passam por aqui:
     * vêm do enum {@code EstadoRegulacao}, porque o perfil os enumera exaustivamente em sete
     * fatias de {@code Composition.event}. Mapear vocabulário fechado que o próprio perfil
     * define seria transferir trabalho sem transferir escolha.
     *
     * @param bruto solicitação no vocabulário do sistema cliente
     * @return DTO enriquecido e trilha de auditoria
     * @throws UnmappedTerminologyException se qualquer código não tiver correspondência
     * @throws IllegalStateException se faltar estratégia para algum domínio exigido
     */
    public ResultadoRegulacao enriquecerRegulacaoAssistencial(RegistroRegulacaoAssistencialDTO bruto) {
        Objects.requireNonNull(bruto, "dto não pode ser nulo");

        rira.a1().validar(bruto);

        MappingContext ctx = MappingContext.agora(bruto.getIdLocalRegistro());
        MappingAuditLog trilha = new MappingAuditLog();

        EnrichedRegistroRegulacaoAssistencial enriquecido = EnrichedRegistroRegulacaoAssistencial.builder()
                .idLocalRegistro(bruto.getIdLocalRegistro())
                .cnsPaciente(bruto.getCnsPaciente())
                .cpfPaciente(bruto.getCpfPaciente())
                .cnesSolicitante(bruto.getCnesSolicitante())
                .cnesExecutante(bruto.getCnesExecutante())
                .dataRegistroOrigem(bruto.getDataRegistroOrigem())
                .dataRequisicao(bruto.getDataRequisicao())
                .inicioAgendamento(bruto.getInicioAgendamento())
                .fimAgendamento(bruto.getFimAgendamento())
                // Os estados vêm do enum, não da Frente B.
                .eventos(
                        bruto.getEventos() == null
                                ? List.of()
                                : bruto.getEventos().stream()
                                        .map(e -> EnrichedRegistroRegulacaoAssistencial.EventoRegulacao.builder()
                                                .estado(e.getEstado())
                                                .quando(e.getQuando())
                                                .build())
                                        .toList())
                .modalidadeAssistencial(traduzir(
                        TerminologyDomain.MODALIDADE_ASSISTENCIAL, bruto.getModalidadeAssistencialLocal(), ctx, trilha))
                .caraterAtendimento(traduzir(
                        TerminologyDomain.CARATER_ATENDIMENTO, bruto.getCaraterAtendimentoLocal(), ctx, trilha))
                .procedimento(traduzir(TerminologyDomain.PROCEDIMENTO, bruto.getProcedimentoLocal(), ctx, trilha))
                .cboExecutante(traduzir(TerminologyDomain.OCUPACAO_CBO, bruto.getCboExecutanteLocal(), ctx, trilha))
                .cid10(traduzir(TerminologyDomain.CID10, bruto.getCid10Local(), ctx, trilha))
                .observacaoCondicao(bruto.getObservacaoCondicao())
                .build();

        rira.a2().validar(enriquecido);
        return new ResultadoRegulacao(enriquecido, trilha);
    }

    /**
     * Traduz os códigos locais de uma dispensação de medicamento (REDFM).
     *
     * <p>São até <strong>nove</strong> domínios, e o número varia: cinco na dispensação em si,
     * mais quatro na receita, que só existem quando ela vem embutida. Se o integrador
     * referencia a prescrição já registrada na RNDS, os dados de quem prescreveu vêm com ela,
     * e não há o que traduzir.
     *
     * @param bruto dispensação no vocabulário do sistema cliente
     * @return DTO enriquecido e trilha de auditoria
     * @throws UnmappedTerminologyException se qualquer código não tiver correspondência
     * @throws IllegalStateException se faltar estratégia para algum domínio exigido
     */
    public ResultadoDispensacao enriquecerDispensacaoMedicamento(RegistroDispensacaoMedicamentoDTO bruto) {
        Objects.requireNonNull(bruto, "dto não pode ser nulo");
        redfm.a1().validar(bruto); // A1 — antes da tradução

        MappingContext ctx = MappingContext.agora(bruto.getIdLocalRegistro());
        MappingAuditLog trilha = new MappingAuditLog();

        EnrichedRegistroDispensacaoMedicamento.Prescricao prescricao = null;
        if (bruto.getPrescricao() != null) {
            RegistroDispensacaoMedicamentoDTO.PrescricaoDTO p = bruto.getPrescricao();
            prescricao = EnrichedRegistroDispensacaoMedicamento.Prescricao.builder()
                    .idItemPrescrito(p.getIdItemPrescrito())
                    .dataPrescricao(p.getDataPrescricao())
                    .cnsPrescritor(p.getCnsPrescritor())
                    .conselhoPrescritor(p.getConselhoPrescritor())
                    .inscricaoPrescritor(p.getInscricaoPrescritor())
                    .cnesLotacaoPrescritor(p.getCnesLotacaoPrescritor())
                    .dose(p.getDose())
                    .descricaoUnidadeDose(p.getDescricaoUnidadeDose())
                    .observacao(p.getObservacao())
                    .cboPrescritor(traduzir(TerminologyDomain.OCUPACAO_CBO, p.getCboPrescritorLocal(), ctx, trilha))
                    .cid10(traduzir(TerminologyDomain.CID10, p.getCid10Local(), ctx, trilha))
                    .viaAdministracao(
                            traduzir(TerminologyDomain.VIA_ADMINISTRACAO, p.getViaAdministracaoLocal(), ctx, trilha))
                    .unidadeDose(traduzir(TerminologyDomain.UNIDADE_DOSE, p.getUnidadeDoseLocal(), ctx, trilha))
                    .build();
        }

        EnrichedRegistroDispensacaoMedicamento enriquecido = EnrichedRegistroDispensacaoMedicamento.builder()
                .idLocalRegistro(bruto.getIdLocalRegistro())
                .idItemDispensado(bruto.getIdItemDispensado())
                .cnsPaciente(bruto.getCnsPaciente())
                .cpfPaciente(bruto.getCpfPaciente())
                .cnesEstabelecimento(bruto.getCnesEstabelecimento())
                .dataRegistroOrigem(bruto.getDataRegistroOrigem())
                .dataEntrega(bruto.getDataEntrega())
                .descricaoMedicamento(bruto.getDescricaoMedicamento())
                .cnpjFabricante(bruto.getCnpjFabricante())
                .lote(bruto.getLote())
                .validade(bruto.getValidade())
                .quantidade(bruto.getQuantidade())
                .unidadeQuantidade(bruto.getUnidadeQuantidade())
                .observacaoDispensacao(bruto.getObservacaoDispensacao())
                .substituiDocumentoRnds(bruto.getSubstituiDocumentoRnds())
                .cnsFarmaceutico(bruto.getCnsFarmaceutico())
                .conselhoFarmaceutico(bruto.getConselhoFarmaceutico())
                .inscricaoFarmaceutico(bruto.getInscricaoFarmaceutico())
                .idPrescricaoNaRnds(bruto.getIdPrescricaoNaRnds())
                .prescricao(prescricao)
                .medicamento(traduzir(TerminologyDomain.MEDICAMENTO, bruto.getMedicamentoLocal(), ctx, trilha))
                .formaFarmaceutica(
                        traduzir(TerminologyDomain.FORMA_FARMACEUTICA, bruto.getFormaFarmaceuticaLocal(), ctx, trilha))
                .categoriaDispensacao(traduzir(
                        TerminologyDomain.CATEGORIA_DISPENSACAO, bruto.getCategoriaDispensacaoLocal(), ctx, trilha))
                .tipoDispensacao(
                        traduzir(TerminologyDomain.TIPO_DISPENSACAO, bruto.getTipoDispensacaoLocal(), ctx, trilha))
                .cboFarmaceutico(traduzir(TerminologyDomain.OCUPACAO_CBO, bruto.getCboFarmaceuticoLocal(), ctx, trilha))
                .build();

        redfm.a2().validar(enriquecido);
        return new ResultadoDispensacao(enriquecido, trilha);
    }

    /**
     * Traduz os códigos locais de um Sumário de Alta (SA).
     *
     * <p>O serviço com mais domínios do framework — até dezessete traduções num sumário com dois
     * diagnósticos, um procedimento e um medicamento, porque o número cresce com as listas.
     * Campos de texto livre (resumo, plano, restrição funcional, local e motivo do atendimento)
     * não passam por tradução: o perfil os exige como texto.
     *
     * @param bruto sumário no vocabulário do sistema cliente
     * @return DTO enriquecido e trilha de auditoria
     * @throws UnmappedTerminologyException se qualquer código não tiver correspondência
     */
    public ResultadoSumario enriquecerSumarioAlta(RegistroSumarioAltaDTO bruto) {
        Objects.requireNonNull(bruto, "dto não pode ser nulo");
        sa.a1().validar(bruto); // A1 — antes da tradução
        MappingContext ctx = MappingContext.agora(bruto.getIdLocalRegistro());
        MappingAuditLog trilha = new MappingAuditLog();

        RegistroSumarioAltaDTO.ContatoAssistencialDTO c = bruto.getContato();
        EnrichedRegistroSumarioAlta.ContatoAssistencial contato = c == null
                ? null
                : EnrichedRegistroSumarioAlta.ContatoAssistencial.builder()
                        .caraterAtendimento(traduzir(
                                TerminologyDomain.CARATER_ATENDIMENTO_RNDS,
                                c.getCaraterAtendimentoLocal(),
                                ctx,
                                trilha))
                        .dataInicio(c.getDataInicio())
                        .dataFim(c.getDataFim())
                        .procedencia(traduzir(TerminologyDomain.PROCEDENCIA, c.getProcedenciaLocal(), ctx, trilha))
                        .motivoDesfecho(
                                traduzir(TerminologyDomain.MOTIVO_DESFECHO, c.getMotivoDesfechoLocal(), ctx, trilha))
                        .cnsProfissionalAlta(c.getCnsProfissionalAlta())
                        .cnesLotacaoProfissionalAlta(c.getCnesLotacaoProfissionalAlta())
                        .cboProfissionalAlta(
                                traduzir(TerminologyDomain.OCUPACAO_CBO, c.getCboProfissionalAltaLocal(), ctx, trilha))
                        .equipe(c.getEquipe())
                        .localAtendimento(c.getLocalAtendimento())
                        .motivoAtendimento(c.getMotivoAtendimento())
                        .build();

        List<EnrichedRegistroSumarioAlta.Diagnostico> diagnosticos = ouVazia(bruto.getDiagnosticos()).stream()
                .map(d -> EnrichedRegistroSumarioAlta.Diagnostico.builder()
                        .cid10(traduzir(TerminologyDomain.CID10, d.getCid10Local(), ctx, trilha))
                        .ordem(d.getOrdem())
                        .presenteNaAdmissao(d.getPresenteNaAdmissao())
                        .situacaoClinica(d.getSituacaoClinica())
                        .observacao(d.getObservacao())
                        .build())
                .toList();

        List<EnrichedRegistroSumarioAlta.Procedimento> procedimentos = ouVazia(bruto.getProcedimentos()).stream()
                .map(p -> EnrichedRegistroSumarioAlta.Procedimento.builder()
                        .procedimento(traduzir(TerminologyDomain.PROCEDIMENTO, p.getProcedimentoLocal(), ctx, trilha))
                        .dataRealizacao(p.getDataRealizacao())
                        .numeroAutorizacao(p.getNumeroAutorizacao())
                        .quantidade(p.getQuantidade())
                        .financiamento(
                                traduzir(TerminologyDomain.FINANCIAMENTO, p.getFinanciamentoLocal(), ctx, trilha))
                        .cboExecutante(traduzir(TerminologyDomain.OCUPACAO_CBO, p.getCboExecutanteLocal(), ctx, trilha))
                        .cnesExecutante(p.getCnesExecutante())
                        .cnesEmNomeDe(p.getCnesEmNomeDe())
                        .equipe(p.getEquipe())
                        .observacao(p.getObservacao())
                        .build())
                .toList();

        RegistroSumarioAltaDTO.AlergiaDTO a = bruto.getAlergia();
        EnrichedRegistroSumarioAlta.Alergia alergia = a == null
                ? null
                : EnrichedRegistroSumarioAlta.Alergia.builder()
                        .agente(traduzir(TerminologyDomain.ALERGENO, a.getAgenteLocal(), ctx, trilha))
                        .descricaoAgente(a.getDescricaoAgente())
                        .categoria(a.getCategoria())
                        .criticidade(a.getCriticidade())
                        .tipo(a.getTipo())
                        .verificacao(a.getVerificacao())
                        .dataInicio(a.getDataInicio())
                        .manifestacao(
                                traduzir(TerminologyDomain.MANIFESTACAO_REACAO, a.getManifestacaoLocal(), ctx, trilha))
                        .observacao(a.getObservacao())
                        .build();

        RegistroSumarioAltaDTO.PrescricaoAltaDTO pa = bruto.getPrescricaoAlta();
        EnrichedRegistroSumarioAlta.PrescricaoAlta prescricao = pa == null
                ? null
                : EnrichedRegistroSumarioAlta.PrescricaoAlta.builder()
                        .cnsPrescritor(pa.getCnsPrescritor())
                        .dataPrescricao(pa.getDataPrescricao())
                        .itens(ouVazia(pa.getItens()).stream()
                                .map(i -> EnrichedRegistroSumarioAlta.ItemPrescricao.builder()
                                        .medicamento(traduzir(
                                                TerminologyDomain.MEDICAMENTO_CATMAT,
                                                i.getMedicamentoLocal(),
                                                ctx,
                                                trilha))
                                        .formaFarmaceutica(traduzir(
                                                TerminologyDomain.FORMA_FARMACEUTICA,
                                                i.getFormaFarmaceuticaLocal(),
                                                ctx,
                                                trilha))
                                        .viaAdministracao(traduzir(
                                                TerminologyDomain.VIA_ADMINISTRACAO,
                                                i.getViaAdministracaoLocal(),
                                                ctx,
                                                trilha))
                                        .dose(i.getDose())
                                        .unidadeDose(traduzir(
                                                TerminologyDomain.UNIDADE_DOSE, i.getUnidadeDoseLocal(), ctx, trilha))
                                        .doseMaximaPorAdministracao(i.getDoseMaximaPorAdministracao())
                                        .frequencia(i.getFrequencia())
                                        .vezes(i.getVezes())
                                        .vezesMaximo(i.getVezesMaximo())
                                        .turno(traduzir(TerminologyDomain.TURNO, i.getTurnoLocal(), ctx, trilha))
                                        .periodo(i.getPeriodo())
                                        .unidadePeriodo(i.getUnidadePeriodo())
                                        .seNecessario(i.getSeNecessario())
                                        .instrucaoPaciente(i.getInstrucaoPaciente())
                                        .situacao(i.getSituacao())
                                        .observacao(i.getObservacao())
                                        .build())
                                .toList())
                        .build();

        RegistroSumarioAltaDTO.IndividuoNaoIdentificadoDTO ni = bruto.getIndividuoNaoIdentificado();
        EnrichedRegistroSumarioAlta.IndividuoNaoIdentificado naoIdentificado = ni == null
                ? null
                : EnrichedRegistroSumarioAlta.IndividuoNaoIdentificado.builder()
                        .sexo(ni.getSexo())
                        .anoNascimento(ni.getAnoNascimento())
                        .justificativa(traduzir(
                                TerminologyDomain.JUSTIFICATIVA_NAO_IDENTIFICADO,
                                ni.getJustificativaLocal(),
                                ctx,
                                trilha))
                        .build();

        EnrichedRegistroSumarioAlta enriquecido = EnrichedRegistroSumarioAlta.builder()
                .idLocalRegistro(bruto.getIdLocalRegistro())
                .cnsPaciente(bruto.getCnsPaciente())
                .cpfPaciente(bruto.getCpfPaciente())
                .individuoNaoIdentificado(naoIdentificado)
                .cnesEstabelecimento(bruto.getCnesEstabelecimento())
                .dataRegistroOrigem(bruto.getDataRegistroOrigem())
                .modalidadeAssistencial(traduzir(
                        TerminologyDomain.MODALIDADE_ASSISTENCIAL, bruto.getModalidadeAssistencialLocal(), ctx, trilha))
                .contato(contato)
                .diagnosticos(diagnosticos)
                .procedimentos(procedimentos)
                .restricaoFuncional(bruto.getRestricaoFuncional())
                .alergia(alergia)
                .prescricaoAlta(prescricao)
                .resumoEvolucaoClinica(bruto.getResumoEvolucaoClinica())
                .planoCuidados(bruto.getPlanoCuidados())
                .informacoesAdicionais(bruto.getInformacoesAdicionais())
                .build();
        sa.a2().validar(enriquecido);
        return new ResultadoSumario(enriquecido, trilha);
    }

    /**
     * Traduz todos os códigos locais de um Registro de Atendimento Clínico.
     *
     * <p>Onze domínios, o maior número do escopo — e dez deles traduzem para um par
     * ({@code system}, {@code code}) que a Camada 4 confere contra um binding. O décimo
     * primeiro, {@link TerminologyDomain#VALOR_MEDIDA}, não: o {@code Observation.value[x]}
     * do {@code BRMedidaObservada} não tem binding nenhum, e a tradução existe para montar o
     * par, não para que alguém o valide.
     *
     * @param bruto registro no vocabulário do sistema cliente
     * @return DTO enriquecido e trilha de auditoria
     * @throws UnmappedTerminologyException se qualquer código não tiver correspondência
     * @throws IllegalStateException se faltar estratégia para algum domínio exigido
     */
    public ResultadoAtendimentoClinico enriquecerAtendimentoClinico(RegistroAtendimentoClinicoDTO bruto) {
        Objects.requireNonNull(bruto, "dto não pode ser nulo");
        rac.a1().validar(bruto); // A1 — antes da tradução
        MappingContext ctx = MappingContext.agora(bruto.getIdLocalRegistro());
        MappingAuditLog trilha = new MappingAuditLog();

        RegistroAtendimentoClinicoDTO.ContatoAssistencialDTO c = bruto.getContato();
        EnrichedRegistroAtendimentoClinico.ContatoAssistencial contato = c == null
                ? null
                : EnrichedRegistroAtendimentoClinico.ContatoAssistencial.builder()
                        .caraterAtendimento(traduzir(
                                TerminologyDomain.CARATER_ATENDIMENTO_RNDS,
                                c.getCaraterAtendimentoLocal(),
                                ctx,
                                trilha))
                        .dataInicio(c.getDataInicio())
                        .dataFim(c.getDataFim())
                        .procedencia(traduzir(TerminologyDomain.PROCEDENCIA, c.getProcedenciaLocal(), ctx, trilha))
                        .motivoDesfecho(
                                traduzir(TerminologyDomain.MOTIVO_DESFECHO, c.getMotivoDesfechoLocal(), ctx, trilha))
                        .outrasInformacoes(c.getOutrasInformacoes())
                        .responsabilidadeParticipante(traduzir(
                                TerminologyDomain.RESPONSABILIDADE_PARTICIPANTE,
                                c.getResponsabilidadeParticipanteLocal(),
                                ctx,
                                trilha))
                        .cnsProfissional(c.getCnsProfissional())
                        .cnesLotacaoProfissional(c.getCnesLotacaoProfissional())
                        .cboProfissional(
                                traduzir(TerminologyDomain.OCUPACAO_CBO, c.getCboProfissionalLocal(), ctx, trilha))
                        .equipe(c.getEquipe())
                        .localAtendimento(c.getLocalAtendimento())
                        .motivoAtendimento(c.getMotivoAtendimento())
                        .build();

        List<EnrichedRegistroAtendimentoClinico.Diagnostico> diagnosticos = ouVazia(bruto.getDiagnosticos()).stream()
                .map(d -> EnrichedRegistroAtendimentoClinico.Diagnostico.builder()
                        .problemaDiagnostico(traduzir(
                                TerminologyDomain.PROBLEMA_DIAGNOSTICO, d.getProblemaDiagnosticoLocal(), ctx, trilha))
                        .ordem(d.getOrdem())
                        .papel(traduzir(TerminologyDomain.PAPEL_PROBLEMA_DIAGNOSTICO, d.getPapelLocal(), ctx, trilha))
                        .situacaoClinica(d.getSituacaoClinica())
                        .observacao(d.getObservacao())
                        .build())
                .toList();

        List<EnrichedRegistroAtendimentoClinico.Procedimento> procedimentos = ouVazia(bruto.getProcedimentos()).stream()
                .map(p -> EnrichedRegistroAtendimentoClinico.Procedimento.builder()
                        .procedimento(traduzir(TerminologyDomain.PROCEDIMENTO, p.getProcedimentoLocal(), ctx, trilha))
                        .dataRealizacao(p.getDataRealizacao())
                        .numeroAutorizacao(p.getNumeroAutorizacao())
                        .quantidade(p.getQuantidade())
                        .financiamento(
                                traduzir(TerminologyDomain.FINANCIAMENTO, p.getFinanciamentoLocal(), ctx, trilha))
                        .cboExecutante(traduzir(TerminologyDomain.OCUPACAO_CBO, p.getCboExecutanteLocal(), ctx, trilha))
                        .cnsExecutante(p.getCnsExecutante())
                        .cnesLotacaoExecutante(p.getCnesLotacaoExecutante())
                        .cnesEmNomeDe(p.getCnesEmNomeDe())
                        .equipe(p.getEquipe())
                        .observacao(p.getObservacao())
                        .build())
                .toList();

        List<EnrichedRegistroAtendimentoClinico.MedidaObservada> medidas = ouVazia(bruto.getMedidas()).stream()
                .map(m -> EnrichedRegistroAtendimentoClinico.MedidaObservada.builder()
                        .medida(traduzir(TerminologyDomain.TIPO_OBSERVACAO, m.getMedidaLocal(), ctx, trilha))
                        .valor(m.getValor())
                        .unidadeUcum(m.getUnidadeUcum())
                        .valorData(m.getValorData())
                        .valorCodigo(traduzir(TerminologyDomain.VALOR_MEDIDA, m.getValorCodigoLocal(), ctx, trilha))
                        .metodo(traduzir(TerminologyDomain.POSICAO_INDIVIDUO, m.getMetodoLocal(), ctx, trilha))
                        .localAfericao(
                                traduzir(TerminologyDomain.LOCAL_AFERICAO, m.getLocalAfericaoLocal(), ctx, trilha))
                        .origemMedida(traduzir(TerminologyDomain.ORIGEM_MEDIDA, m.getOrigemMedidaLocal(), ctx, trilha))
                        .roupasUsadas(traduzir(
                                TerminologyDomain.ROUPAS_USADAS_MEDICAO, m.getRoupasUsadasLocal(), ctx, trilha))
                        .frequenciaUso(traduzir(
                                TerminologyDomain.FREQUENCIA_USO_SUBSTANCIA, m.getFrequenciaUsoLocal(), ctx, trilha))
                        .observacao(m.getObservacao())
                        .build())
                .toList();

        List<EnrichedRegistroAtendimentoClinico.Alergia> alergias = ouVazia(bruto.getAlergias()).stream()
                .map(a -> EnrichedRegistroAtendimentoClinico.Alergia.builder()
                        .agente(traduzir(TerminologyDomain.ALERGENO, a.getAgenteLocal(), ctx, trilha))
                        .descricaoAgente(a.getDescricaoAgente())
                        .categoria(a.getCategoria())
                        .criticidade(a.getCriticidade())
                        .tipo(a.getTipo())
                        .verificacao(a.getVerificacao())
                        .dataInicio(a.getDataInicio())
                        .manifestacao(
                                traduzir(TerminologyDomain.MANIFESTACAO_REACAO, a.getManifestacaoLocal(), ctx, trilha))
                        .observacao(a.getObservacao())
                        .build())
                .toList();

        RegistroAtendimentoClinicoDTO.PrescricaoDTO pr = bruto.getPrescricao();
        EnrichedRegistroAtendimentoClinico.Prescricao prescricao = pr == null
                ? null
                : EnrichedRegistroAtendimentoClinico.Prescricao.builder()
                        .cnsPrescritor(pr.getCnsPrescritor())
                        .dataPrescricao(pr.getDataPrescricao())
                        .itens(ouVazia(pr.getItens()).stream()
                                .map(i -> EnrichedRegistroAtendimentoClinico.ItemPrescricao.builder()
                                        .medicamento(traduzir(
                                                TerminologyDomain.MEDICAMENTO_CATMAT,
                                                i.getMedicamentoLocal(),
                                                ctx,
                                                trilha))
                                        .formaFarmaceutica(traduzir(
                                                TerminologyDomain.FORMA_FARMACEUTICA,
                                                i.getFormaFarmaceuticaLocal(),
                                                ctx,
                                                trilha))
                                        .viaAdministracao(traduzir(
                                                TerminologyDomain.VIA_ADMINISTRACAO,
                                                i.getViaAdministracaoLocal(),
                                                ctx,
                                                trilha))
                                        .dose(i.getDose())
                                        .unidadeDose(traduzir(
                                                TerminologyDomain.UNIDADE_DOSE, i.getUnidadeDoseLocal(), ctx, trilha))
                                        .doseMaximaPorAdministracao(i.getDoseMaximaPorAdministracao())
                                        .frequencia(i.getFrequencia())
                                        .vezes(i.getVezes())
                                        .vezesMaximo(i.getVezesMaximo())
                                        .turno(traduzir(TerminologyDomain.TURNO, i.getTurnoLocal(), ctx, trilha))
                                        .periodo(i.getPeriodo())
                                        .unidadePeriodo(i.getUnidadePeriodo())
                                        .seNecessario(i.getSeNecessario())
                                        .instrucaoPaciente(i.getInstrucaoPaciente())
                                        .situacao(i.getSituacao())
                                        .observacao(i.getObservacao())
                                        .build())
                                .toList())
                        .build();

        List<EnrichedRegistroAtendimentoClinico.Atestado> atestados = ouVazia(bruto.getAtestados()).stream()
                .map(a -> EnrichedRegistroAtendimentoClinico.Atestado.builder()
                        .dataInicioAfastamento(a.getDataInicioAfastamento())
                        .diasAfastamento(a.getDiasAfastamento())
                        .informacoesAdicionais(a.getInformacoesAdicionais())
                        .diagnosticos(ouVazia(a.getDiagnosticos()).stream()
                                .map(d -> EnrichedRegistroAtendimentoClinico.CidAvaliado.builder()
                                        .cid10(traduzir(TerminologyDomain.CID10, d.getCid10Local(), ctx, trilha))
                                        .categoriaDiagnostico(traduzir(
                                                TerminologyDomain.CATEGORIA_DIAGNOSTICO,
                                                d.getCategoriaDiagnosticoLocal(),
                                                ctx,
                                                trilha))
                                        .situacaoClinica(d.getSituacaoClinica())
                                        .observacao(d.getObservacao())
                                        .build())
                                .toList())
                        .build())
                .toList();

        RegistroAtendimentoClinicoDTO.IndividuoNaoIdentificadoDTO ni = bruto.getIndividuoNaoIdentificado();
        EnrichedRegistroAtendimentoClinico.IndividuoNaoIdentificado naoIdentificado = ni == null
                ? null
                : EnrichedRegistroAtendimentoClinico.IndividuoNaoIdentificado.builder()
                        .sexo(ni.getSexo())
                        .anoNascimento(ni.getAnoNascimento())
                        .justificativa(traduzir(
                                TerminologyDomain.JUSTIFICATIVA_NAO_IDENTIFICADO,
                                ni.getJustificativaLocal(),
                                ctx,
                                trilha))
                        .build();

        EnrichedRegistroAtendimentoClinico enriquecido = EnrichedRegistroAtendimentoClinico.builder()
                .idLocalRegistro(bruto.getIdLocalRegistro())
                .cnsPaciente(bruto.getCnsPaciente())
                .cpfPaciente(bruto.getCpfPaciente())
                .individuoNaoIdentificado(naoIdentificado)
                .cnesEstabelecimento(bruto.getCnesEstabelecimento())
                .dataRegistroOrigem(bruto.getDataRegistroOrigem())
                .modalidadeAssistencial(traduzir(
                        TerminologyDomain.MODALIDADE_ASSISTENCIAL, bruto.getModalidadeAssistencialLocal(), ctx, trilha))
                .contato(contato)
                .diagnosticos(diagnosticos)
                .procedimentos(procedimentos)
                .medidas(medidas)
                .alergias(alergias)
                .prescricao(prescricao)
                .planoCuidados(bruto.getPlanoCuidados())
                .atestados(atestados)
                .build();
        rac.a2().validar(enriquecido);
        return new ResultadoAtendimentoClinico(enriquecido, trilha);
    }

    private static <T> List<T> ouVazia(List<T> l) {
        return l == null ? List.of() : l;
    }

    /**
     * Traduz um código, ou devolve {@code null} se o campo não foi informado.
     *
     * <p>Campo ausente e campo sem correspondência são coisas diferentes: o primeiro é
     * omissão legítima de um campo opcional; o segundo é erro. Só o segundo lança.
     */
    private CodedValue traduzir(
            TerminologyDomain domain, String codigoLocal, MappingContext ctx, MappingAuditLog trilha) {
        if (vazio(codigoLocal)) {
            return null;
        }
        return registry.map(new RawValue(domain, codigoLocal), ctx, trilha)
                .target()
                .toCodedValue();
    }

    private String traduzirCodigo(
            TerminologyDomain domain, String codigoLocal, MappingContext ctx, MappingAuditLog trilha) {
        if (vazio(codigoLocal)) {
            return null;
        }
        return registry.map(new RawValue(domain, codigoLocal), ctx, trilha)
                .target()
                .code();
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
