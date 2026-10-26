package br.rndsconnect.semantic;

import br.rndsconnect.model.CodedTarget;
import br.rndsconnect.model.TerminologyDomain;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Fonte em YAML — o formato que sustenta o argumento P1 da Seção 3.1.
 *
 * <p>Formato:
 *
 * <pre>
 * domain: VACINA
 * system: http://www.saude.gov.br/fhir/r4/CodeSystem/BRImunobiologico
 * validatedBy: "SMS-SP · Enf. Ana Souza · COREN 123456"
 * mappings:
 *   "105":
 *     code: "87"
 *     display: "COVID-19 PFIZER - COMIRNATY"
 *   "COVID_PCR":
 *     system: http://www.saude.gov.br/fhir/r4/CodeSystem/BRNomeExameLOINC
 *     code: "94309-2"
 *     validatedBy: "Laboratório Central · Dra. Marta Lima · CRM 45678"
 * </pre>
 *
 * <p>{@code system} e {@code validatedBy} de topo são o padrão do arquivo; cada entrada pode
 * sobrescrever qualquer um dos dois. O mesmo modelo mental vale no
 * {@link PropertiesMappingSource}, onde as chaves de cabeçalho são {@code _system} e
 * {@code _validatedBy} — quem aprendeu um formato entende o outro.
 *
 * <p>O {@code system} por entrada é o que
 * permite atender os domínios cujo ValueSet compõe mais de um CodeSystem —
 * {@code NOME_EXAME} (LOINC ou GAL), {@code TIPO_AMOSTRA} (HL7 v2-0487 ou BRTipoAmostraGAL)
 * e {@code RESULTADO_QUALITATIVO} (CodeSystem base mais oito {@code BRTipoResultado*}).
 *
 * <p><strong>Por que este formato importa para a tese.</strong> A Seção 3.1 argumenta que a
 * tabela de correspondência precisa ser revisável por quem tem competência clínica para
 * revisá-la. Um enfermeiro do PNI não audita um {@code switch} em Java. Ele audita
 * {@code "105" → "87" (COVID-19 PFIZER - COMIRNATY)}, assina embaixo no campo
 * {@code validatedBy}, e a assinatura chega intacta à trilha de auditoria de cada registro
 * enviado. O display existe exatamente para tornar essa revisão possível — e é descartado na
 * fronteira da Camada 3, porque o perfil proíbe {@code coding.display}.
 *
 * @author Igor Mendes Domingues Miras
 */
public class YamlMappingSource extends AbstractFileMappingSource {

    /**
     * @param domain domínio que esta fonte alimenta
     * @param recurso caminho do {@code .yml} no classpath
     */
    public YamlMappingSource(TerminologyDomain domain, String recurso) {
        super(domain, recurso);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, CodedTarget> parse(byte[] conteudo) {
        // SafeConstructor: o YAML é dado de configuração, não código. Desabilitar a
        // construção arbitrária de objetos elimina a classe de ataque de desserialização.
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object raiz;
        try {
            raiz = yaml.load(new ByteArrayInputStream(conteudo));
        } catch (RuntimeException e) {
            throw new MappingSourceException("YAML malformado: " + getRecurso(), e);
        }
        if (!(raiz instanceof Map<?, ?> mapaRaiz)) {
            throw new MappingSourceException("YAML de mapeamento deve ter um objeto na raiz: " + getRecurso());
        }

        String systemPadrao = texto(mapaRaiz.get("system"));
        String validadoPorPadrao = texto(mapaRaiz.get("validatedBy"));
        String dominioDeclarado = texto(mapaRaiz.get("domain"));
        if (dominioDeclarado != null && !dominioDeclarado.equals(getDomain().name())) {
            throw new MappingSourceException("O arquivo " + getRecurso() + " declara domain: " + dominioDeclarado
                    + ", mas foi registrado para o domínio " + getDomain()
                    + ". Um arquivo registrado no domínio errado traduz códigos "
                    + "silenciosamente para o vocabulário errado.");
        }

        Object bruto = mapaRaiz.get("mappings");
        if (!(bruto instanceof Map<?, ?> mapeamentos)) {
            throw new MappingSourceException("Chave obrigatória 'mappings' ausente ou inválida em " + getRecurso());
        }

        Map<String, CodedTarget> tabela = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entrada : mapeamentos.entrySet()) {
            String chave = String.valueOf(entrada.getKey());
            if (!(entrada.getValue() instanceof Map<?, ?> alvo)) {
                throw new MappingSourceException("A entrada \"" + chave + "\" em " + getRecurso()
                        + " deve ser um objeto com pelo menos a chave 'code'.");
            }
            String system = texto(alvo.get("system"));
            if (system == null) {
                system = systemPadrao;
            }
            if (system == null) {
                throw new MappingSourceException("A entrada \"" + chave + "\" em " + getRecurso()
                        + " não tem 'system', " + "e o arquivo não declara um 'system' padrão no topo.");
            }
            String code = texto(alvo.get("code"));
            if (code == null) {
                throw new MappingSourceException("A entrada \"" + chave + "\" em " + getRecurso() + " não tem 'code'.");
            }
            String validadoPor = texto(alvo.get("validatedBy"));
            if (validadoPor == null) {
                validadoPor = validadoPorPadrao;
            }
            tabela.put(chave, new CodedTarget(system, code, texto(alvo.get("display")), validadoPor));
        }
        return tabela;
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = String.valueOf(valor).trim();
        return s.isEmpty() ? null : s;
    }
}
