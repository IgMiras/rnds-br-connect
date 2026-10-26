package br.rndsconnect.semantic.regras;

import br.rndsconnect.model.EnrichedRegistroImunizacao;
import br.rndsconnect.semantic.RegraValidacao;
import br.rndsconnect.semantic.RndsValidationException;

/**
 * Elo 8 (A2) — fabricante e lote são obrigatórios, <em>exceto</em> em transcrição de caderneta.
 *
 * <p>O perfil do RIA-R declara {@code Immunization.manufacturer} e
 * {@code Immunization.lotNumber} como {@code 0..1}. O Modelo de Informação é mais exigente:
 * os dois são obrigatórios no registro comum, e o manual abre exceção explícita —
 * <em>"Para registro de transcrição de caderneta de vacinação, este campo é opcional"</em>.
 *
 * <p>A regra faz as duas coisas ao mesmo tempo: <strong>exige</strong> no caso comum, o que o
 * perfil não faz, e <strong>libera</strong> na transcrição, sem o que o framework não
 * conseguiria representar uma transcrição legítima. Sem este elo, teríamos de escolher entre
 * recusar transcrições ou aceitar registros comuns incompletos.
 *
 * <p><strong>Por que é A2.</strong> A origem do registro chega como código local
 * ({@code registroOrigemLocal}) e é traduzida para o {@code BRRegistroOrigem}, que tem dois
 * códigos: {@code 01}, registro anterior ou transcrição de caderneta, e {@code 02}, vacina no
 * exterior. Um booleano de transcrição não representaria o segundo — e o código {@code 01}
 * só é conhecido depois da tradução (Arquitetura §6.4).
 *
 * @author Igor Mendes Domingues Miras
 */
public class TranscricaoRelaxaFabricanteELote implements RegraValidacao<EnrichedRegistroImunizacao> {

    /** Identificação da regra nas mensagens de erro. */
    public static final String REGRA = "ELO-8/TRANSCRICAO-CADERNETA";

    /** Código de transcrição de caderneta no {@code BRRegistroOrigem}. */
    public static final String CODIGO_TRANSCRICAO = "01";

    @Override
    public void validar(EnrichedRegistroImunizacao dto) {
        boolean transcricao = dto.getRegistroOrigem() != null
                && CODIGO_TRANSCRICAO.equals(dto.getRegistroOrigem().code());
        if (transcricao) {
            return; // o manual libera os dois campos
        }
        if (dto.getCodigoFabricante() == null || dto.getCodigoFabricante().isBlank()) {
            throw new RndsValidationException(
                    "fabricanteLocal",
                    REGRA,
                    "fabricante é obrigatório fora de transcrição de caderneta — o perfil o "
                            + "declara 0..1, mas o Modelo de Informação o exige",
                    null);
        }
        if (dto.getLote() == null || dto.getLote().isBlank()) {
            throw new RndsValidationException(
                    "lote",
                    REGRA,
                    "lote é obrigatório fora de transcrição de caderneta — sem ele não há "
                            + "farmacovigilância possível sobre o imunobiológico aplicado",
                    null);
        }
    }

    @Override
    public String identificacao() {
        return REGRA;
    }
}
