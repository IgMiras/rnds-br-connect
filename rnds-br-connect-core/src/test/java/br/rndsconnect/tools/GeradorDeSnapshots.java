package br.rndsconnect.tools;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.LenientErrorHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;

/**
 * Ferramenta de linha de comando: gera os {@code snapshot} de um pacote de conformidade e os
 * grava de volta nos arquivos.
 *
 * <p>Nao faz parte do framework -- vive em {@code src/test} porque e utilitario de
 * desenvolvimento, executado uma vez por pacote.
 *
 * <h2>O problema que resolve</h2>
 *
 * <p>Nenhum StructureDefinition da RNDS traz {@code snapshot}. Ate o RIRA, gera-los em tempo
 * de execucao custava pouco mais de um segundo. O REDFM mudou o quadro: seus perfis derivam do
 * IG BR-Core em cadeias de ate tres niveis, e a JVM morre com {@code OutOfMemoryError} mesmo
 * com 4 GB de heap -- com apenas 102 artefatos e 8 MB em disco.
 *
 * <p>A pilha do estouro mostra que a causa nao e volume: cada chamada a
 * {@code generateSnapshot} constroi um {@code FHIRPathEngine}, cujo construtor converte
 * <strong>todos</strong> os StructureDefinition conhecidos de R4 para R5 -- os 51 do pacote
 * mais os cerca de 650 do {@code DefaultProfileValidationSupport}. Gerar 51 snapshots numa
 * unica JVM sao dezenas de milhares de conversoes, cada uma criando uma arvore de objetos
 * completa. E custo quadratico dentro do HAPI.
 *
 * <p>Dai <strong>um perfil por execucao</strong>: cada JVM converte, grava e morre.
 *
 * <h2>Duas licoes que estao no codigo</h2>
 *
 * <p><strong>Le do sistema de arquivos, nao do classpath.</strong> Os snapshots sao gravados
 * em {@code src/main/resources}; se a leitura fosse pelo classpath, cada iteracao exigiria
 * recompilar os recursos para que a proxima enxergasse o que a anterior gravou.
 *
 * <p><strong>Localiza o arquivo de destino pela URL canonica do recurso parseado</strong>, e
 * nao procurando a URL no texto do arquivo. A primeira versao desta ferramenta fazia a busca
 * textual e gravou o {@code br-core-practitioner} por cima do
 * {@code StructureDefinition-br-core-medicationrequest.xml}, porque a URL do primeiro aparece
 * dentro do segundo num {@code targetProfile}. Corrompeu o pacote em duas execucoes.
 *
 * @author Igor Mendes Domingues Miras
 */
public final class GeradorDeSnapshots {

    private GeradorDeSnapshots() {}

    /**
     * @param args diretorio do pacote e, opcionalmente, a URL canonica de um unico perfil;
     *     sem ela, lista as URLs que ainda nao tem snapshot
     * @throws IOException se a leitura ou a gravacao falhar
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("uso: GeradorDeSnapshots <diretorio-do-pacote> [url-do-perfil]");
            System.err.println("  sem url  -> lista as URLs que ainda nao tem snapshot");
            System.err.println("  com url  -> gera o snapshot DAQUELE perfil e grava no arquivo");
            System.exit(2);
        }
        Path raiz = Paths.get(args[0]);
        String alvo = args.length > 1 ? args[1] : null;

        FhirContext ctx = FhirContext.forR4();
        IParser xml = ctx.newXmlParser();
        xml.setParserErrorHandler(new LenientErrorHandler());
        IParser json = ctx.newJsonParser();
        json.setParserErrorHandler(new LenientErrorHandler());

        Map<String, Path> arquivoDe = new LinkedHashMap<>();
        Map<String, IBaseResource> recursoDe = new LinkedHashMap<>();

        for (String linha : Files.readAllLines(raiz.resolve("index.txt"), StandardCharsets.UTF_8)) {
            String rel = linha.trim();
            if (rel.isEmpty() || rel.startsWith("#")) {
                continue;
            }
            Path arquivo = raiz.resolve(rel);
            if (!Files.exists(arquivo)) {
                continue;
            }
            IParser parser = rel.toLowerCase().endsWith(".json") ? json : xml;
            IBaseResource r;
            try {
                r = parser.parseResource(semBom(Files.readString(arquivo, StandardCharsets.UTF_8)));
            } catch (Exception e) {
                System.err.printf("  !! nao parseou: %s (%s)%n", rel, e.getMessage());
                continue;
            }
            String url = urlDe(r);
            if (url == null) {
                continue;
            }
            arquivoDe.putIfAbsent(url, arquivo);
            recursoDe.putIfAbsent(url, r);
        }

        List<StructureDefinition> sds = new ArrayList<>();
        for (IBaseResource r : recursoDe.values()) {
            if (r instanceof StructureDefinition sd) {
                sds.add(sd);
            }
        }

        if (alvo == null) {
            for (StructureDefinition sd : sds) {
                if (!sd.hasSnapshot() || sd.getSnapshot().getElement().isEmpty()) {
                    System.out.println(sd.getUrl());
                }
            }
            return;
        }

        StructureDefinition alvoSd = null;
        for (StructureDefinition sd : sds) {
            if (alvo.equals(sd.getUrl())) {
                alvoSd = sd;
                break;
            }
        }
        if (alvoSd == null) {
            System.err.println("  !! perfil nao esta no pacote: " + alvo);
            System.exit(3);
        }
        if (alvoSd.hasSnapshot() && !alvoSd.getSnapshot().getElement().isEmpty()) {
            System.out.println("   JA TEM  " + alvo);
            return;
        }

        PrePopulatedValidationSupport prePopulado = new PrePopulatedValidationSupport(ctx);
        for (IBaseResource r : recursoDe.values()) {
            if (r instanceof StructureDefinition sd) {
                prePopulado.addStructureDefinition(sd);
            } else if (r instanceof ValueSet vs) {
                prePopulado.addValueSet(vs);
            } else if (r instanceof CodeSystem cs) {
                prePopulado.addCodeSystem(cs);
            }
        }

        ValidationSupportChain cadeia = new ValidationSupportChain(
                prePopulado,
                new DefaultProfileValidationSupport(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx),
                new SnapshotGeneratingValidationSupport(ctx));

        StructureDefinition comSnapshot = (StructureDefinition) cadeia.generateSnapshot(
                new ValidationSupportContext(cadeia), alvoSd, alvoSd.getUrl(), null, alvoSd.getName());

        if (comSnapshot == null
                || !comSnapshot.hasSnapshot()
                || comSnapshot.getSnapshot().getElement().isEmpty()) {
            System.err.println("  !! snapshot vazio para " + alvo);
            System.exit(4);
        }

        Path destino = arquivoDe.get(alvo);
        boolean ehJson = destino.getFileName().toString().toLowerCase().endsWith(".json");
        String saida = ehJson
                ? ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(comSnapshot)
                : ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(comSnapshot);
        Files.writeString(destino, saida, StandardCharsets.UTF_8);

        System.out.printf(
                "   OK  %d elements  -> %s%n",
                comSnapshot.getSnapshot().getElement().size(), destino.getFileName());
    }

    private static String urlDe(IBaseResource r) {
        if (r instanceof StructureDefinition sd) {
            return sd.getUrl();
        }
        if (r instanceof ValueSet vs) {
            return vs.getUrl();
        }
        if (r instanceof CodeSystem cs) {
            return cs.getUrl();
        }
        return null;
    }

    /** Quatro artefatos do pacote do REDFM sao gravados com BOM UTF-8. */
    private static String semBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '\uFEFF' ? s.substring(1) : s;
    }
}
