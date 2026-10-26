package br.rndsconnect.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.LenientErrorHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;

/**
 * Pacote de conformidade de <strong>um serviço</strong> da RNDS.
 *
 * <h2>Por que é por serviço, e não global</h2>
 *
 * <p>Até a revisão anterior, o pacote era único: os 113 artefatos serviam ao RIA e ao REL
 * simultaneamente. Funcionava porque os dois vêm do <strong>mesmo</strong> pacote publicado.
 * A extensão do escopo aos seis serviços revelou que a premissa não generaliza.
 *
 * <p>O DATASUS distribui <strong>um pacote de artefatos por serviço</strong>, e eles divergem
 * entre si: <strong>153 de 402 URLs canônicas carregam conteúdos diferentes conforme o pacote
 * de origem</strong> — com a <strong>mesma versão declarada</strong>. É o defeito nº 11, e o
 * mais grave dos doze documentados, porque ataca o próprio mecanismo de rastreabilidade.
 *
 * <p>O caso mais claro é o {@code BRImunobiologico}, versão {@code 02.16} e data
 * {@code 2023-01-12} em todos os pacotes:
 *
 * <table border="1">
 *   <caption>Mesma versão declarada, conteúdos distintos</caption>
 *   <tr><th>Pacote</th><th>Conceitos</th><th>Diferença</th></tr>
 *   <tr><td>RIA e REL</td><td>114</td><td>referência</td></tr>
 *   <tr><td>SA</td><td>116</td><td>inclui NIRSEVIMABE ({@code 115}, {@code 116})</td></tr>
 *   <tr><td>REDFM</td><td>106</td><td>omite oito códigos ({@code 107} a {@code 114})</td></tr>
 * </table>
 *
 * <p>Há ainda 48 códigos com {@code display} divergente — inclusive o {@code "105"}, que
 * sustenta a demonstração central da Frente B.
 *
 * <p><strong>Mesclar seria arbitrar.</strong> Escolher, para cada uma das 153 URLs
 * conflitantes, qual conteúdo prevalece é decisão que não cabe ao framework, pelo mesmo
 * princípio que rege o ADR-05. E há o argumento de homologação, que é decisivo: quem homologa
 * o SA o faz contra os artefatos que o DATASUS entregou para o SA. Se a Camada 4 validasse
 * contra um pacote mesclado que escolheu outro {@code BRImunobiologico}, a validação local
 * <strong>divergiria do que a RNDS faz</strong> — e a camada perderia a razão de existir.
 *
 * <h2>XML e JSON no mesmo pacote</h2>
 *
 * <p>Os pacotes do DATASUS são XML; o IG BR-Core, do qual o REDFM deriva, é distribuído em
 * JSON pelo Simplifier. O parser é escolhido pela extensão, de modo que um pacote pode
 * combinar os dois formatos — que é exatamente o caso do REDFM.
 *
 * <h2>Duas características dos artefatos que exigem tratamento</h2>
 *
 * <ol>
 *   <li><em>Nenhum StructureDefinition da RNDS traz {@code snapshot}</em> — todos publicam
 *       apenas {@code differential}. Isso não é tratado aqui, e sim na cadeia de validação,
 *       pelo elo {@code SnapshotGeneratingValidationSupport} (ver {@link LocalFhirValidator}).
 *   <li><em>A maioria dos ValueSets declara {@code compose.include.version = "*"}</em> — ver
 *       {@link #normalizarVersaoCoringa(ValueSet)}.
 * </ol>
 *
 * <p>Como o classpath não é enumerável de forma portátil quando empacotado em JAR, a lista de
 * arquivos de cada diretório é lida de um índice textual ({@code index.txt}) gerado pelo
 * script {@code scripts/copiar-conformance.sh}.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class ConformancePackage {

    /** Valor de versão que a RNDS usa para dizer "todas as versões do CodeSystem". */
    private static final String VERSAO_CORINGA = "*";

    private final Servico servico;
    private final List<String> diretorios;
    private final Map<String, String> urlParaOrigem = new LinkedHashMap<>();

    private final List<StructureDefinition> structureDefinitions = new ArrayList<>();
    private final List<ValueSet> valueSets = new ArrayList<>();
    private final List<CodeSystem> codeSystems = new ArrayList<>();
    private final List<String> ignorados = new ArrayList<>();
    private final List<String> valueSetsNormalizados = new ArrayList<>();
    private int referenciasCoringaRemovidas;
    private final long tempoCargaMillis;

    private ConformancePackage(FhirContext ctx, Servico servico, List<String> diretorios) {
        this.servico = servico;
        this.diretorios = List.copyOf(diretorios);

        long inicio = System.currentTimeMillis();
        // Os pacotes do DATASUS sao XML; o IG BR-Core, exigido pelo REDFM, vem em JSON
        // pelo Simplifier. O parser e escolhido pela extensao do arquivo.
        IParser xml = ctx.newXmlParser();
        xml.setParserErrorHandler(new LenientErrorHandler());
        IParser json = ctx.newJsonParser();
        json.setParserErrorHandler(new LenientErrorHandler());

        for (String diretorio : diretorios) {
            for (String arquivo : lerIndice(diretorio)) {
                String recurso = diretorio + "/" + arquivo;
                IParser parser = recurso.toLowerCase().endsWith(".json") ? json : xml;
                try (InputStream in = semBom(abrir(recurso))) {
                    Object parsed = parser.parseResource(new InputStreamReader(in, StandardCharsets.UTF_8));
                    if (parsed instanceof StructureDefinition sd) {
                        registrarOrigem(sd.getUrl(), recurso);
                        structureDefinitions.add(sd);
                    } else if (parsed instanceof ValueSet vs) {
                        registrarOrigem(vs.getUrl(), recurso);
                        int removidas = normalizarVersaoCoringa(vs);
                        if (removidas > 0) {
                            valueSetsNormalizados.add(vs.getUrl());
                            referenciasCoringaRemovidas += removidas;
                        }
                        valueSets.add(vs);
                    } else if (parsed instanceof CodeSystem cs) {
                        registrarOrigem(cs.getUrl(), recurso);
                        codeSystems.add(cs);
                    } else {
                        ignorados.add(arquivo);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Falha ao ler o artefato de conformidade: " + recurso, e);
                }
            }
        }
        this.tempoCargaMillis = System.currentTimeMillis() - inicio;
    }

    /**
     * Registra a origem de cada URL canônica e recusa duplicatas <strong>dentro</strong> do
     * mesmo pacote.
     *
     * <p>A guarda vale por pacote, e não entre pacotes: colisão entre pacotes de serviços
     * distintos é o comportamento <em>esperado</em>, e é justamente o motivo de eles serem
     * separados. Duas versões do mesmo artefato <em>no mesmo pacote</em>, por outro lado,
     * tornariam a validação dependente da ordem de leitura dos arquivos.
     */
    private void registrarOrigem(String url, String recurso) {
        if (url == null) {
            return;
        }
        String anterior = urlParaOrigem.putIfAbsent(url, recurso);
        if (anterior != null) {
            throw new IllegalStateException("URL canônica duplicada no pacote do serviço " + servico + ":"
                    + System.lineSeparator() + "  " + url
                    + System.lineSeparator() + "  em " + anterior
                    + System.lineSeparator() + "  e em " + recurso
                    + System.lineSeparator()
                    + "O PrePopulatedValidationSupport resolve por URL; duas versões do mesmo "
                    + "artefato tornariam a validação dependente da ordem de leitura.");
        }
    }

    /**
     * Remove o valor <code>"*"</code> de <code>compose.include.version</code> e
     * <code>compose.exclude.version</code>.
     *
     * <p><strong>Por que isto é necessário.</strong> O FHIR R4 admite o valor especial
     * <code>"*"</code> em <code>ValueSet.compose.include.version</code>, com o significado
     * de "códigos de todas as versões do CodeSystem", e deixa expressamente a cargo do
     * servidor decidir como tratá-lo. Os ValueSets da RNDS usam esse recurso: 35 dos 38.
     *
     * <p>O <code>InMemoryTerminologyServerValidationSupport</code> do HAPI não implementa
     * essa semântica — trata o <code>"*"</code> como versão literal e busca por
     * <code>{url}|*</code>. Como os 39 CodeSystems da RNDS declaram versões concretas
     * (<code>BRImunobiologico</code> é <code>02.16</code>, <code>BRCBO</code> é
     * <code>201701A</code>, e assim por diante), a busca falha e <em>todo</em> binding
     * <code>required</code> é reprovado com <code>HAPI-0702</code>.
     *
     * <p>Removendo o <code>"*"</code>, o HAPI passa a resolver o CodeSystem pela URL
     * canônica, sem restrição de versão. A troca é semanticamente segura <em>neste
     * contexto</em>: o pacote carrega exatamente uma versão de cada CodeSystem, de modo que
     * "todas as versões" e "a versão carregada" descrevem o mesmo conjunto de códigos.
     *
     * <p>A normalização é contabilizada e exposta em {@link #valueSetsNormalizados()},
     * porque alterar artefato oficial em memória é decisão de projeto que precisa ser
     * auditável — e não um efeito colateral silencioso da carga.
     *
     * @param vs ValueSet recém-parseado
     * @return quantas referências de versão coringa foram removidas
     */
    private static int normalizarVersaoCoringa(ValueSet vs) {
        if (!vs.hasCompose()) {
            return 0;
        }
        int removidas = 0;
        for (ValueSet.ConceptSetComponent inc : vs.getCompose().getInclude()) {
            if (VERSAO_CORINGA.equals(inc.getVersion())) {
                inc.setVersion(null);
                removidas++;
            }
        }
        for (ValueSet.ConceptSetComponent exc : vs.getCompose().getExclude()) {
            if (VERSAO_CORINGA.equals(exc.getVersion())) {
                exc.setVersion(null);
                removidas++;
            }
        }
        return removidas;
    }

    /**
     * Carrega o pacote de um serviço a partir de um ou mais diretórios do classpath.
     *
     * <p>Aceita vários diretórios porque o REDFM precisa do seu próprio pacote
     * <strong>mais</strong> o IG BR-Core, do qual oito de seus perfis derivam.
     *
     * @param ctx contexto FHIR R4 já inicializado
     * @param servico o serviço a que este pacote atende
     * @param diretorios caminhos no classpath, sem barra inicial nem final
     * @return pacote pronto para alimentar o {@link LocalFhirValidator}
     */
    public static ConformancePackage paraServico(FhirContext ctx, Servico servico, String... diretorios) {
        Objects.requireNonNull(ctx, "ctx é obrigatório");
        Objects.requireNonNull(servico, "servico é obrigatório");
        if (diretorios == null || diretorios.length == 0) {
            throw new IllegalArgumentException(
                    "Informe ao menos um diretório de artefatos para o serviço " + servico + ".");
        }
        return new ConformancePackage(ctx, servico, List.of(diretorios));
    }

    private static List<String> lerIndice(String diretorio) {
        String indice = diretorio + "/index.txt";
        List<String> arquivos = new ArrayList<>();
        try (InputStream in = abrir(indice);
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = r.readLine()) != null) {
                String limpa = linha.trim();
                if (!limpa.isEmpty() && !limpa.startsWith("#")) {
                    arquivos.add(limpa);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o índice de conformidade: " + indice, e);
        }
        if (arquivos.isEmpty()) {
            throw new IllegalStateException(
                    "Índice de conformidade vazio: " + indice + ". Execute scripts/copiar-conformance.sh.");
        }
        return arquivos;
    }

    /**
     * Descarta a marca de ordem de bytes (BOM) UTF-8, quando presente.
     *
     * <p>Quatro artefatos do pacote do REDFM — os CodeSystems de medicamento
     * {@code codesystem_vmp_catmat}, {@code codesystem_ampp_obm}, {@code codesystem_vmp_obm} e
     * {@code codesystem_ampp_ean} — são gravados com BOM. O parser XML do HAPI recusa os três
     * bytes {@code EF BB BF} antes do prólogo com <em>«Content is not allowed in prolog»</em>,
     * e a carga inteira do pacote falha.
     *
     * <p>O BOM é legal em UTF-8 e comum em arquivos gerados no Windows; a especificação XML o
     * admite. Quem não o admite é o {@code XMLStreamReader} quando recebe um
     * {@code InputStreamReader} já decodificado — a decodificação transforma os três bytes num
     * caractere {@code U+FEFF}, que aí sim é conteúdo antes do prólogo. Descartá-lo na leitura
     * resolve sem alterar o artefato.
     *
     * @param in fluxo do artefato
     * @return o mesmo fluxo, posicionado após o BOM se houver
     */
    private static InputStream semBom(InputStream in) {
        try {
            java.io.PushbackInputStream p = new java.io.PushbackInputStream(in, 3);
            byte[] bom = new byte[3];
            int lidos = p.read(bom, 0, 3);
            if (lidos < 3 || (bom[0] & 0xFF) != 0xEF || (bom[1] & 0xFF) != 0xBB || (bom[2] & 0xFF) != 0xBF) {
                if (lidos > 0) {
                    p.unread(bom, 0, lidos);
                }
            }
            return p;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao inspecionar o início do artefato", e);
        }
    }

    private static InputStream abrir(String recurso) {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(recurso);
        if (in == null) {
            in = ConformancePackage.class.getClassLoader().getResourceAsStream(recurso);
        }
        if (in == null) {
            throw new IllegalStateException("Artefato de conformidade ausente no classpath: " + recurso);
        }
        return in;
    }

    /** @return o serviço a que este pacote atende */
    public Servico servico() {
        return servico;
    }

    /** @return os diretórios de classpath que compõem este pacote */
    public List<String> diretorios() {
        return diretorios;
    }

    /**
     * De qual arquivo veio determinada URL canônica.
     *
     * <p>Permite demonstrar, em auditoria, contra qual cópia de um artefato divergente este
     * serviço foi validado — o que a versão declarada, por si só, não identifica (defeito
     * nº 11).
     *
     * @param url URL canônica do artefato
     * @return caminho do recurso no classpath, ou {@code null} se ausente
     */
    public String origemDe(String url) {
        return urlParaOrigem.get(url);
    }

    /** @return os StructureDefinition carregados (perfis e extensões da RNDS) */
    public List<StructureDefinition> structureDefinitions() {
        return Collections.unmodifiableList(structureDefinitions);
    }

    /** @return os ValueSet carregados, já normalizados */
    public List<ValueSet> valueSets() {
        return Collections.unmodifiableList(valueSets);
    }

    /** @return os CodeSystem carregados */
    public List<CodeSystem> codeSystems() {
        return Collections.unmodifiableList(codeSystems);
    }

    /** @return arquivos do índice cujo tipo de recurso não é de conformidade */
    public List<String> ignorados() {
        return Collections.unmodifiableList(ignorados);
    }

    /**
     * URLs canônicas dos ValueSets que sofreram normalização de versão coringa.
     *
     * <p>É a evidência auditável da decisão descrita em
     * {@link #normalizarVersaoCoringa(ValueSet)}.
     *
     * @return lista das URLs normalizadas
     */
    public List<String> valueSetsNormalizados() {
        return Collections.unmodifiableList(valueSetsNormalizados);
    }

    /** @return total de referências de versão {@code "*"} removidas na carga */
    public int referenciasCoringaRemovidas() {
        return referenciasCoringaRemovidas;
    }

    /**
     * Tempo de carga dos artefatos, em milissegundos.
     *
     * <p>Este número é o custo de inicialização declarado na Arquitetura §9.7 e no ADR-03.
     * Registrá-lo aqui é o que permite citá-lo no TCC como medição própria.
     *
     * @return duração da carga em milissegundos
     */
    public long tempoCargaMillis() {
        return tempoCargaMillis;
    }

    /** @return total de artefatos efetivamente carregados */
    public int total() {
        return structureDefinitions.size() + valueSets.size() + codeSystems.size();
    }
}
