package br.rndsconnect.dto;

import br.rndsconnect.model.PesquisaClinica;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

/**
 * Modelo canônico do registro de imunização em rotina — a entrada pública do framework.
 *
 * <p><strong>Todos os códigos aqui estão no vocabulário do sistema cliente.</strong> É a
 * diferença essencial em relação ao {@code EnrichedRegistroImunizacao}: neste DTO,
 * {@code vacinaLocal} pode ser {@code "105"}, {@code "PFIZER_COVID"} ou
 * {@code "cod_vac_0087"} — o que quer que a instituição use na própria base. A tradução para
 * o vocabulário da RNDS é responsabilidade da Camada 2.
 *
 * <p>É por isso que os campos são {@code String} e não {@code CodedValue}: o sistema cliente
 * não conhece — e não deve conhecer — os CodeSystems da RNDS. Essa é a fronteira que a Seção
 * 3.2 descreve entre <em>o que o dado parece</em> e <em>o que o dado significa</em>, e a
 * razão da inversão de dependência do ADR-01.
 *
 * <p>Nenhum campo desta classe menciona FHIR. O sistema cliente entrega dados clínicos e não
 * precisa saber que Bundles, perfis ou CodeableConcepts existem.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class RegistroImunizacaoDTO {

    // ── Identificação do registro ───────────────────────────────────────
    /** Identifica univocamente o registro na base de origem. */
    String idLocalRegistro;

    // ── Indivíduo — exatamente um dos dois ──────────────────────────────
    String cnsPaciente;
    String cpfPaciente;

    // ── Contato assistencial ────────────────────────────────────────────
    /** CNES do estabelecimento. */
    String cnesEstabelecimento;
    /** CNS do profissional que aplicou. */
    String cnsProfissional;
    /** Quando o registro foi criado no sistema de origem. */
    Instant dataRegistroOrigem;

    LocalDate dataAplicacao;

    // ── Códigos locais obrigatórios ─────────────────────────────────────
    String vacinaLocal;
    String doseLocal;
    String estrategiaVacinacaoLocal;
    String grupoAtendimentoLocal;
    String condicaoMaternalLocal;
    String localAplicacaoLocal;
    String viaAdministracaoLocal;

    // ── Códigos locais opcionais ────────────────────────────────────────
    String fabricanteLocal;
    String registroOrigemLocal;
    String paisOrigemLocal;
    /** CBO do profissional <strong>prescritor</strong>, não do aplicador. */
    String cboPrescritorLocal;
    /** CID que motivou a indicação; obrigatório na estratégia Especial (Frente A). */
    String cid10Local;

    /**
     * Observação sobre a condição, em texto livre. O perfil marca {@code Condition.note} como
     * MustSupport, e a auditoria de emissão mostrou que nenhum dos dois serviços o emitia.
     */
    String observacaoCondicao;

    // ── Valores que não passam por tradução ─────────────────────────────
    String lote;
    LocalDate validadeLote;
    Boolean primarySource;
    Boolean contatoHanseniase;
    Instant dataInicioCondicao;
    PesquisaClinica pesquisaClinica;
}
