# -*- coding: utf-8 -*-
"""
Trois corrections de l'ecran de demarrage, apres visionnage sur telephone.

1. La bille etait dessinee loin a droite du mot. Sa position horizontale
   est calculee en PIXELS ECRAN — les arcs viennent des lettres deja
   mesurees — mais le trace la repassait par px(), qui multiplie encore par
   l'echelle. Sur un ecran de 590 px, l'abscisse etait donc multipliee par
   1,47 de trop. La verticale, elle, est bien en unites du repere.

2. Le « i » n'apparaissait jamais. Sa tige etait tiree de la table ORDRE,
   qui ne liste que les lettres touchees par un rebond — et le « i » n'en
   fait pas partie, puisque la bille s'y POSE au lieu d'y rebondir. Elle
   retrouve son propre calendrier.

3. Le son etait inaudible sur haut-parleur de telephone : 55 % de son
   energie tombait entre 200 et 500 Hz, une bande que ces haut-parleurs ne
   restituent pas. La gamme monte d'une octave et le passe-bas s'ouvre.
"""

import io
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def corriger(chemin, paires):
    p = os.path.join(RACINE, chemin)
    s = io.open(p, encoding="utf-8").read()
    for avant, apres, nom in paires:
        if avant not in s:
            raise SystemExit("INTROUVABLE (%s) dans %s" % (nom, chemin))
        s = s.replace(avant, apres, 1)
        print("ok :", nom)
    io.open(p, "w", encoding="utf-8").write(s)


# ── 1 et 2 : l'ecran de demarrage ─────────────────────────────────────
corriger("app/src/main/java/com/diabeto/ui/screens/SplashScreen.kt", [
    (
        """                val rr = px(etat.rayon)
                translate(px(etat.x), py(etat.y)) {""",
        """                val rr = px(etat.rayon)
                // `etat.x` est deja en pixels ecran : les arcs sont bâtis sur
                // les lettres mesurees. Le repasser par px() le multipliait
                // une seconde fois par l'echelle, et la bille partait loin a
                // droite du mot. Seule la verticale est en unites du repere.
                translate(etat.x, py(etat.y)) {""",
        "abscisse de la bille",
    ),
    (
        """            val avancee = etat.avancee
            lettres.forEachIndexed { i, lettre ->
                val rang = ORDRE.indexOf(i)
                if (rang < 0 || etat.contacts <= rang) return@forEachIndexed
                val jeune = bornes((avancee - rang) / 0.32f, 0f, 1f)
                val monte = melange(11f, 0f, sortie(jeune))
                if (lettre.tige) {
                    val lt = px(CORPS * TIGE_L)
                    val hx = px(CORPS * HAUT_X)
                    drawRoundRect(
                        color = BLANC.copy(alpha = melange(0.2f, 1f, jeune)),
                        topLeft = Offset(
                            lettre.x + lettre.largeur / 2f - lt / 2f,
                            py(SOL) - hx + px(monte),
                        ),
                        size = Size(lt, hx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(lt / 2f),
                    )
                } else {
                    val mise = lettre.mise ?: return@forEachIndexed
                    drawText(
                        textLayoutResult = mise,
                        topLeft = Offset(
                            lettre.x,
                            py(SOL) - mise.firstBaseline + px(monte),
                        ),
                        alpha = melange(0.2f, 1f, jeune),
                    )
                }
            }""",
        """            val avancee = etat.avancee
            lettres.forEachIndexed { i, lettre ->
                /* La tige du « i » ne figure pas dans ORDRE : cette table ne
                   liste que les lettres qu'un rebond fait surgir, et la bille
                   ne rebondit pas sur le « i », elle s'y pose. Tirée de cette
                   table, la tige recevait le rang -1 et n'était jamais
                   dessinée — le nom s'affichait « D aSmart ». Elle suit donc
                   son propre calendrier, calé sur la pose de la bille. */
                if (lettre.tige) {
                    val venue = bornes(prog(t, T_REBONDS_FIN + 350f, T_POSE), 0f, 1f)
                    if (venue <= 0f) return@forEachIndexed
                    val lt = px(CORPS * TIGE_L)
                    val hx = px(CORPS * HAUT_X)
                    drawRoundRect(
                        color = BLANC.copy(alpha = melange(0.2f, 1f, venue)),
                        topLeft = Offset(
                            lettre.x + lettre.largeur / 2f - lt / 2f,
                            py(SOL) - hx + px(melange(9f, 0f, sortie(venue))),
                        ),
                        size = Size(lt, hx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(lt / 2f),
                    )
                    return@forEachIndexed
                }

                val rang = ORDRE.indexOf(i)
                if (rang < 0 || etat.contacts <= rang) return@forEachIndexed
                val jeune = bornes((avancee - rang) / 0.32f, 0f, 1f)
                val mise = lettre.mise ?: return@forEachIndexed
                drawText(
                    textLayoutResult = mise,
                    topLeft = Offset(
                        lettre.x,
                        py(SOL) - mise.firstBaseline + px(melange(11f, 0f, sortie(jeune))),
                    ),
                    alpha = melange(0.2f, 1f, jeune),
                )
            }""",
        "tige du « i »",
    ),
    (
        """            val couleurs = listOf(
                BLANC, BLANC, BLANC, BLEU_MOT, BLEU_MOT, BLEU_MOT, VIOLET_MOT, VIOLET_MOT
            )""",
        """            // « Dia » en blanc, « Smart » en dégradé du bleu au violet.
            // Une lettre ne peut porter qu'une couleur : on échantillonne le
            // dégradé lettre par lettre plutôt que de le couper en deux blocs.
            val couleurs = listOf(BLANC, BLANC, BLANC) +
                (0..4).map { k ->
                    androidx.compose.ui.graphics.lerp(BLEU_MOT, VIOLET_MOT, k / 4f)
                }""",
        "dégradé de « Smart »",
    ),
])

print()

# ── 3 : le registre de la sonorite ────────────────────────────────────
corriger("assets-logo/son/generer_son.py", [
    (
        "GAMME = [261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33]",
        """# Une octave plus haut qu'a la premiere version. Releve sur un
# enregistrement telephone : 55 % de l'energie tombait entre 200 et 500 Hz,
# bande qu'un haut-parleur de telephone ne restitue pas. Le son existait
# dans le fichier et pas dans la piece.
GAMME = [523.25, 587.33, 659.25, 783.99, 880.00, 1046.50, 1174.66]""",
        "gamme montee d'une octave",
    ),
    ("poser(cloche(783.99), T_POSE_I)", "poser(cloche(1567.98), T_POSE_I)", "cloche"),
    (
        'poser(impact(1174.66, duree=0.10, gain=0.10), T_POSE_I + 150)',
        'poser(impact(2349.32, duree=0.10, gain=0.10), T_POSE_I + 150)',
        "premier micro-contact",
    ),
    (
        'poser(impact(1174.66, duree=0.08, gain=0.05), T_POSE_I + 268)',
        'poser(impact(2349.32, duree=0.08, gain=0.05), T_POSE_I + 268)',
        "second micro-contact",
    ),
    (
        "COUPURE = 3200.0",
        """# Le passe-bas s'ouvre : a 3,2 kHz il rabotait les attaques, or c'est
# justement ce que le haut-parleur restitue le mieux.
COUPURE = 7000.0""",
        "passe-bas ouvert",
    ),
])
