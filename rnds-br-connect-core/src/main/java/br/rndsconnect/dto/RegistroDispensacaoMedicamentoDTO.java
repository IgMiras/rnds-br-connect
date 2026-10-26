package br.rndsconnect.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

/**
 * Modelo canônico do registro de dispensação de medicamento — entrada pública do serviço
 * REDFM.
 *
 * <p>Todos os códigos estão no vocabulário do sistema cliente, como nos demais DTOs.
 *
 * <p>A estrutura acompanha a do documento: uma dispensação, o medicamento entregue e a receita
 * que a autoriza — <strong>ou</strong> referenciada na RNDS, <strong>ou</strong> embutida. As
 * duas formas são mutuamente exclusivas, e o Builder recusa a construção quando as duas vêm
 * juntas ou nenhuma vem.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class RegistroDispensacaoMedicamentoDTO {

    String idLocalRegistro;
    /** Identificador do item dispensado no sistema de origem. */
    String idItemDispensado;

    String cnsPaciente;
    String cpfPaciente;

    /** CNES do estabelecimento que dispensou. */
    String cnesEstabelecimento;

    Instant dataRegistroOrigem;
    /** Quando o medicamento foi entregue ao paciente. */
    Instant dataEntrega;

    // ── Medicamento ─────────────────────────────────────────────────────
    /** Medicamento no vocabulário local. */
    String medicamentoLocal;
    /** Texto do medicamento, para exibição. */
    String descricaoMedicamento;
    /** Forma farmacêutica no vocabulário local. */
    String formaFarmaceuticaLocal;

    String cnpjFabricante;
    String lote;
    LocalDate validade;

    // ── Dispensação ─────────────────────────────────────────────────────
    /** Ambiente da dispensação, no vocabulário local. */
    String categoriaDispensacaoLocal;
    /** Dispensação total, parcial ou não realizada, no vocabulário local. */
    String tipoDispensacaoLocal;

    BigDecimal quantidade;
    /** Unidade da quantidade, em texto livre. */
    String unidadeQuantidade;
    /** Observação sobre a dispensação. Opcional. */
    String observacaoDispensacao;
    /** Identificador, na RNDS, do documento que este substitui. Opcional. */
    String substituiDocumentoRnds;

    // ── Farmacêutico ────────────────────────────────────────────────────
    String cnsFarmaceutico;
    /** CBO do farmacêutico, no vocabulário local. */
    String cboFarmaceuticoLocal;
    /** Conselho, ex.: {@code "crf-sp"}. */
    String conselhoFarmaceutico;

    String inscricaoFarmaceutico;

    // ── A receita: uma das duas formas ──────────────────────────────────
    /**
     * Identificador da prescrição <strong>na RNDS</strong>.
     *
     * <p>Mutuamente exclusivo com {@link #prescricao}.
     */
    String idPrescricaoNaRnds;

    /**
     * Dados da receita, quando ela <strong>não</strong> está na RNDS.
     *
     * <p>Mutuamente exclusivo com {@link #idPrescricaoNaRnds}.
     */
    PrescricaoDTO prescricao;

    /** A receita embutida, no vocabulário do sistema cliente. */
    @Value
    @Builder
    public static class PrescricaoDTO {
        String idItemPrescrito;
        Instant dataPrescricao;
        String cnsPrescritor;
        /** Conselho, ex.: {@code "crm-sp"}. */
        String conselhoPrescritor;

        String inscricaoPrescritor;
        /** CBO do prescritor, no vocabulário local. */
        String cboPrescritorLocal;

        String cnesLotacaoPrescritor;
        /** CID que motivou a prescrição, no vocabulário local. */
        String cid10Local;
        /** Via de administração, no vocabulário local. */
        String viaAdministracaoLocal;

        BigDecimal dose;
        /** Unidade da dose, no vocabulário local. */
        String unidadeDoseLocal;
        /** Texto da unidade da dose. */
        String descricaoUnidadeDose;
        /** Observação sobre a prescrição. Opcional. */
        String observacao;
    }
}
