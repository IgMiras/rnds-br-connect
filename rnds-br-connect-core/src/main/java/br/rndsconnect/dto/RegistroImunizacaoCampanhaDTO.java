package br.rndsconnect.dto;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

/**
 * Modelo canônico do registro de imunização em campanha — entrada pública do serviço RIA-C.
 *
 * <p>Todos os códigos estão no vocabulário do sistema cliente, como no
 * {@link RegistroImunizacaoDTO}. A diferença entre os dois é de <strong>escopo</strong>, não
 * de detalhe: este DTO tem 12 campos contra 24, porque o perfil do RIA-C proíbe boa parte do
 * que o RIA-R permite.
 *
 * <p>Um integrador que tente informar local de aplicação numa campanha não recebe um aviso em
 * tempo de execução: <strong>o campo não existe</strong>. O erro vira erro de compilação.
 *
 * @author Igor Mendes Domingues Miras
 */
@Value
@Builder
public class RegistroImunizacaoCampanhaDTO {

    String idLocalRegistro;

    String cnsPaciente;
    String cpfPaciente;

    String cnesEstabelecimento;
    String cnsProfissional;
    Instant dataRegistroOrigem;
    LocalDate dataAplicacao;

    // ── Códigos locais, todos obrigatórios neste serviço ────────────────
    String vacinaLocal;
    String doseLocal;
    String grupoAtendimentoLocal;
    String condicaoMaternalLocal;
    String fabricanteLocal;

    /** Não passa por tradução, mas é {@code 1..1} no perfil. */
    String lote;
}
