package br.rndsconnect.semantic;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Fonte em {@code .properties} — correspondências 1:1 contra um único CodeSystem.
 *
 * <p>Formato:
 *
 * <pre>
 * _system      = http://www.saude.gov.br/fhir/r4/CodeSystem/BRDose
 * _validatedBy = SMS-Presidente Prudente · Enf. resp. imunização · COREN-SP 123456
 *
 * D1 = 1|1ª Dose
 * D2 = 2|2ª Dose
 * REF = 5|Reforço
 * </pre>
 *
 * <p>Chaves iniciadas por {@code _} são cabeçalho e valem para o arquivo inteiro:
 * {@code _system} declara o CodeSystem de todas as entradas (obrigatória) e
 * {@code _validatedBy} registra quem validou clinicamente a tabela (opcional). O valor de
 * cada entrada é o código da RNDS, opcionalmente seguido de {@code |} e do display oficial.
 *
 * <p><strong>Por que {@code _validatedBy} existe aqui.</strong> Sem ele, o formato mais
 * simples de usar seria também o único incapaz de registrar responsabilidade clínica — e o
 * integrador abriria mão dessa rastreabilidade sem perceber, só por ter escolhido o caminho
 * mais fácil. O ADR-04 exige que a proveniência responda <em>quem validou</em>; um formato
 * que não pode responder empurra o framework para uma trilha incompleta.
 *
 * <p><strong>Limite deliberado:</strong> este formato não expressa alvos em CodeSystems
 * diferentes dentro do mesmo domínio. Os domínios {@code NOME_EXAME}, {@code TIPO_AMOSTRA} e
 * {@code RESULTADO_QUALITATIVO} precisam disso — seus ValueSets compõem dois, dois e nove
 * CodeSystems respectivamente — e devem usar {@link YamlMappingSource}. Manter o formato
 * simples de fato simples é preferível a criar uma sintaxe que ninguém lê sem manual.
 *
 * @author Igor Mendes Domingues Miras
 */
public class PropertiesMappingSource extends AbstractFileMappingSource {

    private static final String PREFIXO_CABECALHO = "_";
    private static final String CHAVE_SYSTEM = "_system";
    private static final String CHAVE_VALIDATED_BY = "_validatedBy";

    /**
     * @param domain domínio que esta fonte alimenta
     * @param recurso caminho do {@code .properties} no classpath
     */
    public PropertiesMappingSource(TerminologyDomain domain, String recurso) {
        super(domain, recurso);
    }

    @Override
    protected Map<String, CodedTarget> parse(byte[] conteudo) {
        Properties props = new Properties();
        try (var reader = new InputStreamReader(new ByteArrayInputStream(conteudo), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new MappingSourceException("Properties malformado: " + getRecurso(), e);
        }

        String system = props.getProperty(CHAVE_SYSTEM);
        if (system == null || system.isBlank()) {
            throw new MappingSourceException(
                    "A chave obrigatória '" + CHAVE_SYSTEM + "' está ausente em " + getRecurso()
                            + ". Sem ela não há como saber a qual CodeSystem os códigos pertencem, "
                            + "e um código sem system correto reprova o binding required na Camada 4.");
        }

        String validadoPor = normalizar(props.getProperty(CHAVE_VALIDATED_BY));

        Map<String, CodedTarget> tabela = new LinkedHashMap<>();
        for (String chave : props.stringPropertyNames()) {
            // Cabeçalho: vale para o arquivo, não é uma correspondência.
            if (chave.startsWith(PREFIXO_CABECALHO)) {
                continue;
            }
            String bruto = props.getProperty(chave).trim();
            if (bruto.isEmpty()) {
                throw new MappingSourceException("Correspondência vazia para \"" + chave + "\" em " + getRecurso()
                        + ". Remova a linha ou informe o código de destino.");
            }
            int barra = bruto.indexOf('|');
            String code = barra >= 0 ? bruto.substring(0, barra).trim() : bruto;
            String display = barra >= 0 ? normalizar(bruto.substring(barra + 1)) : null;
            tabela.put(chave.trim(), new CodedTarget(system.trim(), code, display, validadoPor));
        }
        return tabela;
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
