package br.rndsconnect.dto;

import br.rndsconnect.model.EstadoRegulacao;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Modelo canônico do registro de regulação assistencial — entrada pública do serviço RIRA.
 *
 * <p>Todos os códigos estão no vocabulário do sistema cliente, como nos demais DTOs. A
 * exceção é a lista de eventos: {@link EstadoRegulacao} é um enum de sete valores fixos
 * definidos pelo perfil, e não um vocabulário a mapear.
 *
 * <p>A estrutura acompanha a natureza do serviço: uma solicitação que caminha por estados, com
 * a requisição e o agendamento como partes do mesmo documento.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class RegistroRegulacaoAssistencialDTO {

    String idLocalRegistro;

    String cnsPaciente;
    String cpfPaciente;

    /** CNES do estabelecimento solicitante. */
    String cnesSolicitante;

    Instant dataRegistroOrigem;

    /** Modalidade assistencial no vocabulário local. */
    String modalidadeAssistencialLocal;

    /**
     * Caráter do atendimento, no vocabulário local.
     *
     * <p>Traduz para {@code routine} ou {@code urgent}: o ValueSet do perfil compõe o
     * vocabulário do HL7, e não o {@code BRCaraterAtendimento} da RNDS.
     */
    String caraterAtendimentoLocal;

    // ── Requisição ──────────────────────────────────────────────────────
    /** Procedimento solicitado, no vocabulário local. */
    String procedimentoLocal;
    /** CBO do executante; obrigatório nos grupos 03 e 04 da Tabela SUS (RN03). */
    String cboExecutanteLocal;

    Instant dataRequisicao;

    // ── Agendamento ─────────────────────────────────────────────────────
    Instant inicioAgendamento;
    Instant fimAgendamento;
    /** CNES do estabelecimento executante. */
    String cnesExecutante;

    // ── Motivo ──────────────────────────────────────────────────────────
    /** CID que motivou a solicitação, no vocabulário local. */
    String cid10Local;

    /**
     * Observação sobre a condição, em texto livre. O perfil marca {@code Condition.note} como
     * MustSupport, e a auditoria de emissão mostrou que nenhum dos dois serviços o emitia.
     */
    String observacaoCondicao;

    // ── Histórico de estados ────────────────────────────────────────────
    /**
     * Os estados por que a solicitação passou, em ordem cronológica.
     *
     * <p>Pelo menos um é obrigatório: {@code Composition.event} é {@code 1..*}.
     */
    List<EventoDTO> eventos;

    /** Um estado, com a data em que foi atingido. */
    @Value
    @Builder
    public static class EventoDTO {
        EstadoRegulacao estado;
        Instant quando;
    }
}
