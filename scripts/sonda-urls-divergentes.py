#!/usr/bin/env python3
"""Sonda do defeito nº 11: a mesma URL canônica com conteúdos diferentes entre distribuições.

Uso:
    python3 scripts/sonda-urls-divergentes.py <pasta do rnds-artefatos> [distribuição ...] [-v]

Sem distribuições na linha de comando, usa as quatro de serviço: Artefatos_Validador_RIRA,
arquivos_definicoes_SA, br-core-ril-ria-rel e modelagem-redfm. Com -v, lista as URLs divergentes.

Conta StructureDefinition, ValueSet e CodeSystem. O conteúdo é normalizado antes da comparação:
XML pela forma canônica (C14N 2.0, sem espaço entre elementos e sem comentários) e JSON com as
chaves ordenadas. Cópias XML e JSON do mesmo artefato contam, portanto, como conteúdos
diferentes. Os diretórios de controle do Simplifier são ignorados, como no copiar-conformance.sh.
"""
import argparse
import collections
import hashlib
import json
import os
import re
import xml.etree.ElementTree as ET

TIPOS = {"StructureDefinition", "ValueSet", "CodeSystem"}
IGNORAR = {".simplifier", ".forge-corrected-files", "arquivos-marcos-desenv", ".git"}
PADRAO = ["Artefatos_Validador_RIRA", "arquivos_definicoes_SA", "br-core-ril-ria-rel", "modelagem-redfm"]


def artefatos(pasta):
    """Devolve (url, versão, hash do conteúdo normalizado) de cada artefato da pasta."""
    for raiz, dirs, arquivos in os.walk(pasta):
        dirs[:] = [d for d in dirs if d not in IGNORAR]
        for nome in arquivos:
            if not nome.endswith((".xml", ".json")):
                continue
            with open(os.path.join(raiz, nome), encoding="utf-8-sig", errors="replace") as f:
                texto = f.read().lstrip()
            if texto.startswith("{"):
                try:
                    recurso = json.loads(texto)
                except ValueError:
                    continue
                tipo, url, versao = recurso.get("resourceType"), recurso.get("url"), recurso.get("version")
                canonico = json.dumps(recurso, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
            else:
                m_tipo = re.search(r'<(\w+)[^>]*xmlns="http://hl7.org/fhir"', texto[:3000])
                m_url = re.search(r'<url value="([^"]+)"', texto)
                m_ver = re.search(r'<version value="([^"]+)"', texto)
                tipo = m_tipo.group(1) if m_tipo else None
                url = m_url.group(1) if m_url else None
                versao = m_ver.group(1) if m_ver else None
                try:
                    canonico = ET.canonicalize(texto, strip_text=True, with_comments=False)
                except ET.ParseError:
                    continue
            if url and tipo in TIPOS:
                yield url, versao, hashlib.sha256(canonico.encode("utf-8")).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("base", help="pasta do repositório rnds-artefatos")
    p.add_argument("distribuicoes", nargs="*", help="subpastas a comparar (padrão: as quatro de serviço)")
    p.add_argument("-v", action="store_true", help="lista as URLs com mais de um conteúdo")
    args = p.parse_args()

    total = 0
    por_url = collections.defaultdict(set)
    por_url_versao = collections.defaultdict(set)
    for nome in args.distribuicoes or PADRAO:
        lista = list(artefatos(os.path.join(args.base, nome)))
        print(f"{nome}: {len(lista)} artefatos")
        total += len(lista)
        for url, versao, h in lista:
            por_url[url].add(h)
            por_url_versao[(url, versao)].add(h)

    divergentes = sorted(u for u, hs in por_url.items() if len(hs) > 1)
    mesma_versao = sum(1 for hs in por_url_versao.values() if len(hs) > 1)
    print(f"Artefatos somados: {total}")
    print(f"Pares (URL, conteúdo) distintos: {sum(len(hs) for hs in por_url.values())}")
    print(f"URLs com mais de um conteúdo: {len(divergentes)} de {len(por_url)} "
          f"({100 * len(divergentes) / len(por_url):.0f}%)")
    print(f"Pares (URL, versão) com mais de um conteúdo: {mesma_versao}")
    if args.v:
        print("\n".join(divergentes))


if __name__ == "__main__":
    main()
