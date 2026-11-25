package fr.annuaire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.annuaire.modele.Modele.Champ;
import fr.annuaire.modele.Modele.Domaine;
import fr.annuaire.modele.Modele.DonneeRefusee;
import fr.annuaire.modele.Modele.Entite;
import fr.annuaire.modele.Modele.NouveauChamp;
import fr.annuaire.modele.Modele.TypeChamp;
import fr.annuaire.semantique.BaseRdf;
import fr.annuaire.semantique.Entites;
import fr.annuaire.semantique.Ontologies;
import fr.annuaire.semantique.Vocabulaire;

/** Tests de la couche sémantique, sur une base RDF en mémoire : ni serveur ni réseau. */
class AnnuaireSemantiqueApplicationTests {

    private Ontologies ontologies;
    private Entites entites;

    @BeforeEach
    void preparer() throws IOException {
        BaseRdf base = new BaseRdf("", "");
        ontologies = new Ontologies(base);
        entites = new Entites(base, ontologies);
    }

    @Test
    void identifiantSansAccentNiEspace() {
        assertThat(Vocabulaire.identifiant("Année de création")).isEqualTo("annee-de-creation");
        assertThat(Vocabulaire.identifiant("  Énergies marines ! ")).isEqualTo("energies-marines");
        assertThatThrownBy(() -> Vocabulaire.identifiant("???")).isInstanceOf(DonneeRefusee.class);
    }

    @Test
    void unAdministrateurAjouteUnChampSansRedeploiement() {
        ontologies.creerDomaine("Nautisme");
        ontologies.ajouterChamp("nautisme", new NouveauChamp("Activité", TypeChamp.TEXTE, true));
        Domaine d = ontologies.ajouterChamp("nautisme", new NouveauChamp("Effectif", TypeChamp.NOMBRE, true));

        assertThat(d.champs()).extracting(Champ::id).containsExactly("nautisme-activite", "nautisme-effectif");
        assertThat(d.champs()).extracting(Champ::ordre).containsExactly(1, 2);
        // Une facette n'est gardée que sur un champ texte.
        assertThat(d.champs()).extracting(Champ::facette).containsExactly(true, false);
        assertThatThrownBy(() -> ontologies.ajouterChamp("nautisme", new NouveauChamp("Activité", TypeChamp.TEXTE, false)))
                .isInstanceOf(DonneeRefusee.class);
    }

    @Test
    void uneEntiteEstRelueAvecSesChampsTypes() {
        domaineNautisme();
        Entite e = entites.enregistrer(new Entite(null, " Chantier Exemple ", "123456789", "Brest", "29200",
                48.39, -4.48, List.of("nautisme"),
                Map.of("nautisme-activite", "Voilerie", "nautisme-effectif", "12", "nautisme-exporte", "true")));

        Entite relue = entites.parId(e.id()).orElseThrow();
        assertThat(relue.nom()).isEqualTo("Chantier Exemple");
        assertThat(relue.domaines()).containsExactly("nautisme");
        assertThat(relue.latitude()).isEqualTo(48.39);
        assertThat(relue.champs()).containsEntry("nautisme-activite", "Voilerie")
                .containsEntry("nautisme-effectif", 12L).containsEntry("nautisme-exporte", true);
        assertThat(entites.toutes()).hasSize(1);
    }

    @Test
    void shaclRefuseUneValeurQuiNEstPasDuTypeDeclare() {
        domaineNautisme();
        assertThatThrownBy(() -> entites.enregistrer(new Entite(null, "Chantier", null, null, null, null, null,
                List.of("nautisme"), Map.of("nautisme-effectif", "une dizaine"))))
                .isInstanceOf(DonneeRefusee.class).hasMessageContaining("Effectif");
        assertThat(entites.toutes()).isEmpty();
    }

    @Test
    void unChampDUnAutreDomaineEstRefuse() {
        domaineNautisme();
        ontologies.creerDomaine("Cybersécurité");
        ontologies.ajouterChamp("cybersecurite", new NouveauChamp("Label", TypeChamp.TEXTE, true));
        assertThatThrownBy(() -> entites.enregistrer(new Entite(null, "Chantier", null, null, null, null, null,
                List.of("nautisme"), Map.of("cybersecurite-label", "X"))))
                .isInstanceOf(DonneeRefusee.class);
    }

    @Test
    void modifierRemplaceEtSupprimerEfface() {
        domaineNautisme();
        Entite e = entites.enregistrer(new Entite(null, "Avant", null, "Brest", null, null, null,
                List.of("nautisme"), Map.of("nautisme-activite", "Voilerie")));
        entites.enregistrer(new Entite(e.id(), "Après", null, null, null, null, null, List.of("nautisme"), Map.of()));

        Entite relue = entites.parId(e.id()).orElseThrow();
        assertThat(relue.nom()).isEqualTo("Après");
        assertThat(relue.commune()).isNull();
        assertThat(relue.champs()).isEmpty();
        assertThat(entites.supprimer(e.id())).isTrue();
        assertThat(entites.supprimer(e.id())).isFalse();
    }

    @Test
    void retirerUnChampRetireAussiSesValeurs() {
        domaineNautisme();
        Entite e = entites.enregistrer(new Entite(null, "Chantier", null, null, null, null, null,
                List.of("nautisme"), Map.of("nautisme-activite", "Voilerie", "nautisme-effectif", "3")));
        Domaine d = ontologies.retirerChamp("nautisme", "nautisme-activite");

        assertThat(d.champs()).extracting(Champ::id).doesNotContain("nautisme-activite");
        assertThat(entites.parId(e.id()).orElseThrow().champs()).containsOnlyKeys("nautisme-effectif");
    }

    @Test
    void uneValeurNePeutPasInjecterDeSparql() {
        domaineNautisme();
        String piege = "x\" } } ; DROP ALL ; INSERT DATA { GRAPH <urn:x> { <urn:a> <urn:b> \"";
        Entite e = entites.enregistrer(new Entite(null, piege, null, null, null, null, null, List.of("nautisme"),
                Map.of("nautisme-activite", piege)));
        assertThat(entites.parId(e.id()).orElseThrow().nom()).isEqualTo(piege.strip());
        assertThat(ontologies.domaines()).hasSize(1);
    }

    @Test
    void laBaseSurDisqueSeComporteCommeLaBaseEnMemoire() throws IOException {
        // Dossier sous target/ : Windows ne permet pas toujours de supprimer aussitôt les fichiers d'une base TDB2.
        java.nio.file.Path dossier = java.nio.file.Path.of("target", "tdb2-test-" + System.nanoTime());
        BaseRdf base = new BaseRdf("", dossier.toString());
        try {
            ontologies = new Ontologies(base);
            entites = new Entites(base, ontologies);
            domaineNautisme();
            Entite e = entites.enregistrer(new Entite(null, "Chantier", null, "Brest", null, null, null,
                    List.of("nautisme"), Map.of("nautisme-effectif", "7")));
            assertThat(entites.parId(e.id()).orElseThrow().champs()).containsEntry("nautisme-effectif", 7L);
            assertThatThrownBy(() -> entites.enregistrer(new Entite(null, "Autre", null, null, null, null, null,
                    List.of("nautisme"), Map.of("nautisme-effectif", "sept")))).isInstanceOf(DonneeRefusee.class);
        } finally {
            base.fermer();
        }
    }

    private void domaineNautisme() {
        ontologies.creerDomaine("Nautisme");
        ontologies.ajouterChamp("nautisme", new NouveauChamp("Activité", TypeChamp.TEXTE, true));
        ontologies.ajouterChamp("nautisme", new NouveauChamp("Effectif", TypeChamp.NOMBRE, false));
        ontologies.ajouterChamp("nautisme", new NouveauChamp("Exporte", TypeChamp.OUI_NON, false));
    }
}
