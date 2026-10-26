package br.rndsconnect.semantic.regras;

import br.rndsconnect.model.EnrichedRegistroImunizacao;
import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;

/**
 * Elo 9 (A2) — na estratégia Especial, CID e CBO do prescritor são obrigatórios.
 *
 * <p>O Modelo de Informação do RIA-R condiciona dois campos à estratégia de vacinação: quando
 * a estratégia é <strong>Especial</strong> (código {@code "2"} do {@code BREstrategiaVacinacao}),
 * a indicação precisa ser justificada por um CID e prescrita por um profissional identificado
 * pelo CBO.
 *
 * <p><strong>A Camada 4 não alcança esta regra.</strong> No perfil,
 * {@code Immunization.reasonReference} é {@code 0..1} e {@code performer.function} é
 * {@code 0..1}: um registro de estratégia Especial sem CID e sem CBO passa em qualquer
 * validador FHIR. A regra está na prosa do manual, não no perfil.
 *
 * <p><strong>Por que é A2.</strong> A regra fala do código {@code "2"} da RNDS. O DTO canônico
 * chega com {@code "ESPECIAL"}, {@code "ESP"} ou {@code cod_estr_02}, conforme a instituição —
 * avaliá-la antes da tradução é impossível, não apenas inconveniente (Seção 7.1).
 *
 * @author Igor Mendes Domingues Miras
 */
public class EstrategiaEspecialExigeCidECbo implements RegraValidacao<EnrichedRegistroImunizacao> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-9/ESTRATEGIA-ESPECIAL";

    /** Código da estratégia Especial no {@code BREstrategiaVacinacao}. */
    public static final String CODIGO_ESPECIAL = "2";

    @Override
    public void validar(EnrichedRegistroImunizacao dto) {
        if (dto.getEstrategiaVacinacao() == null
                || !CODIGO_ESPECIAL.equals(dto.getEstrategiaVacinacao().code())) {
            return;
        }
        if (dto.getCid10() == null) {
            throw new RndsValidationException(
                    "cid10Local",
                    REGRA,
                    "estratégia Especial exige CID que justifique a indicação; "
                            + "Immunization.reasonReference é 0..1 no perfil e a Camada 4 não pega isso",
                    null);
        }
        if (dto.getCboPrescritor() == null) {
            throw new RndsValidationException(
                    "cboPrescritorLocal",
                    REGRA,
                    "estratégia Especial exige o CBO do profissional prescritor; "
                            + "Immunization.performer.function é 0..1 no perfil",
                    null);
        }
    }

    @Override
    public String identificacao() {
        return REGRA;
    }
}
