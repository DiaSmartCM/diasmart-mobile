# -*- coding: utf-8 -*-
"""
Sonorite d'ouverture de DiaSmart, composee sur la timeline de l'animation.

Le principe : ne pas plaquer un bruitage generique par-dessus l'image, mais
calculer les instants sonores a partir des MEMES constantes que le mouvement.
Les durees des rebonds sont geometriques, de raison egale au coefficient de
restitution ; les impacts tombent donc exactement la ou la bille touche.

Si le mouvement est retouche, il suffit de rejouer ce script : le son suit.

Palette : une gamme pentatonique ascendante. Chaque lettre ecrite monte d'un
degre, ce qui fait entendre la construction du mot. La derniere note, quand
la bille se pose sur le « i », est une cloche qui resout.
"""

import os
import sys
import wave

import numpy as np

sys.stdout.reconfigure(encoding="utf-8")

FE = 44100          # frequence d'echantillonnage
DUREE = 4.6         # secondes

# ── Constantes reprises telles quelles de l'animation ─────────────────
REST = 0.74
T_CHUTE_FIN = 1350          # premier contact, le « t »
T_REBONDS = 1550            # budget des six arcs
N_ARCS = 6
T_POSE_I = 3700             # la bille se pose sur le « i »
T_SORTIE = 800              # elle se degage de la marque


def instants_contacts():
    """Les sept impacts, en millisecondes, deduits des memes durees."""
    durees = [REST ** k for k in range(N_ARCS)]
    somme = sum(durees)
    durees = [d / somme * T_REBONDS for d in durees]
    ts, courant = [T_CHUTE_FIN], T_CHUTE_FIN
    for d in durees:
        courant += d
        ts.append(courant)
    return ts


piste = np.zeros(int(FE * DUREE), dtype=np.float64)


def poser(signal, ms):
    """Additionne un signal a l'instant donne, sans deborder."""
    i = int(FE * ms / 1000.0)
    fin = min(i + len(signal), len(piste))
    if i < len(piste):
        piste[i:fin] += signal[: fin - i]


def enveloppe(n, attaque, chute):
    """Attaque courte, decroissance exponentielle."""
    t = np.arange(n) / FE
    a = np.clip(t / max(attaque, 1e-5), 0, 1)
    return a * np.exp(-t / chute)


def impact(freq, duree=0.34, gain=0.5):
    """
    Percussion accordee, volontairement ronde.

    Trois reglages font la douceur : peu d'harmoniques hautes, une attaque
    de six millisecondes au lieu d'une, et un clic tres attenue. Le clic
    reste necessaire — sans lui on entend une note, pas un contact — mais
    a un quart de son niveau precedent il ne pique plus.
    """
    n = int(FE * duree)
    t = np.arange(n) / FE
    corps = (np.sin(2 * np.pi * freq * t)
             + 0.22 * np.sin(2 * np.pi * freq * 1.5 * t)
             + 0.07 * np.sin(2 * np.pi * freq * 2.0 * t))
    clic = np.random.default_rng(7).standard_normal(n) * np.exp(-t / 0.007)
    return gain * (corps * enveloppe(n, 0.006, 0.15) + 0.06 * clic)


def cloche(freq, duree=1.5, gain=0.42):
    """Partiels inharmoniques et longue decroissance."""
    n = int(FE * duree)
    t = np.arange(n) / FE
    rangs = [1.0, 2.01, 2.98, 4.16]
    poids = [1.0, 0.34, 0.15, 0.05]
    duree_partiel = [1.0, 0.72, 0.55, 0.38, 0.26]
    s = np.zeros(n)
    for r, p, d in zip(rangs, poids, duree_partiel):
        s += p * np.sin(2 * np.pi * freq * r * t) * np.exp(-t / (duree * 0.30 * d))
    return gain * s * np.clip(t / 0.010, 0, 1)


def souffle(duree=0.42, gain=0.09):
    """Bruit filtre en cloche : la bille qui se degage de la marque."""
    n = int(FE * duree)
    t = np.arange(n) / FE
    rng = np.random.default_rng(3)
    bruit = rng.standard_normal(n)
    # Filtrage passe-bas simple par moyenne glissante, puis mise en forme.
    noyau = np.ones(60) / 60
    bruit = np.convolve(bruit, noyau, mode="same")
    forme = np.sin(np.pi * np.clip(t / duree, 0, 1)) ** 1.6
    return gain * bruit * forme


# ── Composition ───────────────────────────────────────────────────────
# Pentatonique de do majeur, sept degres ascendants.
GAMME = [261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33]

poser(souffle(), T_SORTIE - 120)

contacts = instants_contacts()
print("impacts (ms) :", " ".join(f"{c:.0f}" for c in contacts))
for k, ms in enumerate(contacts):
    # Le premier choc, apres la chute, est le plus appuye ; les suivants
    # s'allegent comme l'energie de la bille.
    gain = 0.44 * (REST ** (k * 0.7))
    poser(impact(GAMME[k], gain=gain), ms)

# La pose sur le « i » resout la montee.
poser(cloche(783.99), T_POSE_I)
# Deux micro-contacts du tassement, a peine audibles.
poser(impact(1174.66, duree=0.10, gain=0.10), T_POSE_I + 150)
poser(impact(1174.66, duree=0.08, gain=0.05), T_POSE_I + 268)

# ── Finition ──────────────────────────────────────────────────────────
# Passe-bas a un pole : rabote les aigus residuels du clic et des partiels.
# Une sonorite d'ouverture doit s'entendre sans se faire remarquer.
COUPURE = 3200.0
alpha = 1.0 - np.exp(-2 * np.pi * COUPURE / FE)
lisse = np.empty_like(piste)
acc = 0.0
for i, v in enumerate(piste):
    acc += alpha * (v - acc)
    lisse[i] = acc
piste = lisse

# Fondu de sortie pour ne pas couper la cloche net.
q = int(FE * 0.25)
piste[-q:] *= np.linspace(1, 0, q)

crete = np.max(np.abs(piste))
# 0,72 etait encore fort pour un son entendu a chaque ouverture.
piste = piste / crete * 0.40
print(f"crete avant normalisation : {crete:.3f}")

pcm = (piste * 32767).astype(np.int16)
DOSSIER = os.path.dirname(os.path.abspath(__file__))
chemin = os.path.join(DOSSIER, "diasmart_ouverture.wav")
with wave.open(chemin, "wb") as f:
    f.setnchannels(1)
    f.setsampwidth(2)
    f.setframerate(FE)
    f.writeframes(pcm.tobytes())
print("ecrit :", chemin, os.path.getsize(chemin) // 1024, "Ko")
