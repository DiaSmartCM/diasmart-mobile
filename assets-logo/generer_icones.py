# -*- coding: utf-8 -*-
"""
Jeu d'icones de lancement DiaSmart, a partir du D detoure.

Trois familles a produire, et elles n'obeissent pas aux memes regles :

  ICONE ADAPTATIVE (Android 8+). Toile de 108 dp dont seul le centre est
  garanti visible : le lanceur y applique son propre masque, rond chez l'un,
  en goutte chez l'autre. Le logo doit donc tenir dans la zone sure, environ
  66 dp, sinon il sera rogne sur certains telephones. D'ou un D a 60 % de la
  toile et non plein cadre.

  ICONES CLASSIQUES (Android 7 et anterieur). Le systeme n'applique aucun
  masque : c'est l'image qui porte sa forme. On dessine donc le fond avec
  ses coins arrondis, et une variante ronde a part.

  BOUTIQUE. 512 px, carre plein, sans transparence.

Le fond est un violet profond pris dans la palette de l'application. Il doit
rester nettement plus sombre que le violet du D lui-meme, sinon le bas de la
marque se fond dedans.
"""

import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.stdout.reconfigure(encoding="utf-8")

DOSSIER = os.path.dirname(os.path.abspath(__file__))
MARQUE = os.path.join(DOSSIER, "diasmart_d.png")
RES = os.path.join(os.path.dirname(DOSSIER), "app", "src", "main", "res")

# Violet profond, diagonale. Le D descend jusqu'a #5B3FEB : le fond reste
# bien en dessous en clarte pour que la marque s'en detache.
VIOLET_HAUT = (53, 25, 122)
VIOLET_BAS = (25, 11, 60)

DENSITES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def fond(taille):
    """Degrade diagonal, du violet clair en haut a gauche au sombre en bas."""
    y, x = np.mgrid[0:taille, 0:taille]
    p = ((x / taille) * 0.42 + (y / taille) * 0.58)
    haut = np.array(VIOLET_HAUT, dtype=float)
    bas = np.array(VIOLET_BAS, dtype=float)
    arr = haut[None, None, :] + (bas - haut)[None, None, :] * p[:, :, None]
    return Image.fromarray(arr.astype(np.uint8), "RGB")


def marque(hauteur):
    """La marque detouree, mise a l'echelle sur sa hauteur."""
    d = Image.open(MARQUE).convert("RGBA")
    larg = int(round(hauteur * d.width / d.height))
    return d.resize((larg, hauteur), Image.LANCZOS)


def poser_centre(support, calque):
    support.alpha_composite(
        calque,
        ((support.width - calque.width) // 2, (support.height - calque.height) // 2),
    )


def coins_arrondis(img, rayon):
    masque = Image.new("L", img.size, 0)
    ImageDraw.Draw(masque).rounded_rectangle(
        [0, 0, img.width - 1, img.height - 1], radius=rayon, fill=255
    )
    sortie = img.convert("RGBA")
    sortie.putalpha(masque)
    return sortie


def disque(img):
    masque = Image.new("L", img.size, 0)
    ImageDraw.Draw(masque).ellipse([0, 0, img.width - 1, img.height - 1], fill=255)
    sortie = img.convert("RGBA")
    sortie.putalpha(masque)
    return sortie


def ecrire(img, dossier, nom, webp=True):
    chemin = os.path.join(RES, dossier, nom + (".webp" if webp else ".png"))
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    if webp:
        img.save(chemin, "WEBP", quality=95, method=6)
    else:
        img.save(chemin, "PNG", optimize=True)
    return chemin


def produire(installer=True, apercu=None):
    faits = []
    for densite, facteur in DENSITES.items():
        # ── Icone adaptative : toile de 108 dp ──
        toile = int(round(108 * facteur))
        ecrire(fond(toile), f"mipmap-{densite}", "diasmart_background")

        # Zone sure : 60 % de la toile. Au-dela, les masques agressifs rognent.
        av = Image.new("RGBA", (toile, toile), (0, 0, 0, 0))
        poser_centre(av, marque(int(toile * 0.60)))
        ecrire(av, f"mipmap-{densite}", "diasmart_foreground")

        # ── Icones classiques : l'image porte sa propre forme ──
        legacy = int(round(48 * facteur))
        plein = fond(legacy).convert("RGBA")
        poser_centre(plein, marque(int(legacy * 0.64)))
        ecrire(coins_arrondis(plein, int(legacy * 0.22)), f"mipmap-{densite}", "diasmart")
        ecrire(disque(plein), f"mipmap-{densite}", "diasmart_round")
        faits.append(f"{densite}: adaptative {toile}px, classique {legacy}px")

    # ── Boutique ──
    boutique = fond(512).convert("RGBA")
    poser_centre(boutique, marque(int(512 * 0.64)))
    chemin = os.path.join(DOSSIER, "playstore_512.png")
    boutique.convert("RGB").save(chemin, "PNG", optimize=True)
    faits.append("boutique : playstore_512.png")

    if apercu:
        # Planche : le carre, le rond, et le rendu adaptatif masque en rond
        # comme le ferait un lanceur.
        t = 216
        ad = fond(t).convert("RGBA")
        poser_centre(ad, marque(int(t * 0.60)))
        cases = [
            coins_arrondis(fond(t).convert("RGBA"), int(t * 0.22)),
            disque(fond(t).convert("RGBA")),
            disque(ad),
        ]
        for i, c in enumerate(cases[:2]):
            poser_centre(c, marque(int(t * 0.64)))
        pl = Image.new("RGB", (t * 3, t), (18, 16, 34))
        for i, c in enumerate(cases):
            pl.paste(c.convert("RGB"), (i * t, 0), c)
        pl.save(apercu)
    return faits


if __name__ == "__main__":
    for ligne in produire(apercu=os.path.join(DOSSIER, "_apercu_icones.png")):
        print(" ", ligne)
