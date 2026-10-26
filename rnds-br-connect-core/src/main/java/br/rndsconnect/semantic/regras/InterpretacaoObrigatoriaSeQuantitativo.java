package br.rndsconnect.semantic.regras;

import br.rndsconnect.dto.RegistroExameLaboratorialDTO;
import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;

/**
 * Elo 6 (A1) — RN3 do manual REL: resultado quantitativo exige interpretação.
 *
 * <p>Um número sozinho não é laudo. "4,7 log10 cópias/mL" só vira informação clínica quando
 * acompanhado da leitura — detectável, não detectável, indeterminado. A RN3 do manual REL
 * torna a interpretação obrigatória sempre que o resultado enviado é quantitativo.
 *
 * <p><strong>A Camada 4 jamais pega isso.</strong> {@code Observation.interpretation} é
 * {@code 0..1} no perfil. Um laudo com carga viral e sem interpretação é estruturalmente
 * perfeito e viola a RN3 — é exatamente a lacuna que a Frente A ocupa.
 *
 * <p>É A1 porque "o resultado é quantitativo" é propriedade da <em>forma</em> do DTO: basta
 * saber qual campo está preenchido, sem consultar vocabulário nenhum.
 *
 * @author Igor Mendes Domingues Miras
 */
public class InterpretacaoObrigatoriaSeQuantitativo implements RegraValidacao<RegistroExameLaboratorialDTO> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-6/RN3-INTERPRETACAO";

    @Override
    public void validar(RegistroExameLaboratorialDTO dto) {
        if (dto.getExames() == null) {
            return;
        }
        for (int i = 0; i < dto.getExames().size(); i++) {
            RegistroExameLaboratorialDTO.ExameDTO e = dto.getExames().get(i);
            if (e.getResultadoQuantitativo() == null) {
                continue;
            }
            if (e.getInterpretacaoLocal() == null || e.getInterpretacaoLocal().isBlank()) {
                throw new RndsValidationException(
                        "exames[" + i + "].interpretacaoLocal",
                        REGRA,
                        "RN3 do manual REL: resultado quantitativo exige interpretação. "
                                + "Observation.interpretation é 0..1 no perfil e a Camada 4 não pega isso",
                        String.valueOf(e.getResultadoQuantitativo()));
            }
        }
    }

    @Override
    public String identificacao() {
        return REGRA;
    }
}
