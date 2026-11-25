package fr.annuaire.widget;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import fr.annuaire.modele.Modele.Champ;
import fr.annuaire.modele.Modele.Domaine;
import fr.annuaire.modele.Modele.DonneeRefusee;
import fr.annuaire.modele.Modele.Entite;
import fr.annuaire.modele.Modele.Facette;
import fr.annuaire.modele.Modele.Introuvable;
import fr.annuaire.modele.Modele.Recherche;
import fr.annuaire.modele.Modele.ResultatRecherche;
import fr.annuaire.recherche.IndexRecherche;
import fr.annuaire.semantique.Ontologies;

@Service
public class Widgets {

    public record NouveauWidget(String nom, String type, String domaine, List<String> champs, List<String> origines) {
    }

    public record Colonne(String id, String libelle) {
    }

    /** Ce que le widget reçoit : uniquement les champs que l'administrateur a choisi de publier. */
    public record Donnees(String nom, String type, List<Colonne> colonnes, long total,
                          List<Map<String, Object>> entites, List<Facette> facettes) {
    }

    private static final Set<String> TYPES = Set.of("annuaire", "carte");

    private final DepotWidgets depot;
    private final Ontologies ontologies;
    private final IndexRecherche index;

    public Widgets(DepotWidgets depot, Ontologies ontologies, IndexRecherche index) {
        this.depot = depot;
        this.ontologies = ontologies;
        this.index = index;
    }

    public List<Widget> tous() {
        return depot.findAll();
    }

    public Widget exiger(UUID id) {
        return depot.findById(id).orElseThrow(() -> new Introuvable("Widget inconnu"));
    }

    public Widget creer(NouveauWidget n) {
        if (n.nom() == null || n.nom().isBlank() || n.nom().length() > 80) {
            throw new DonneeRefusee("Le nom du widget est obligatoire (80 caractères au plus).");
        }
        if (n.type() == null || !TYPES.contains(n.type())) {
            throw new DonneeRefusee("Le type doit être « annuaire » ou « carte ».");
        }
        Domaine domaine = ontologies.exiger(n.domaine());
        Set<String> connus = domaine.champs().stream().map(Champ::id).collect(Collectors.toSet());
        List<String> champs = n.champs() == null ? List.of() : n.champs();
        champs.stream().filter(c -> !connus.contains(c)).findFirst().ifPresent(c -> {
            throw new DonneeRefusee("Champ inconnu dans ce domaine : " + c);
        });
        List<String> origines = n.origines() == null || n.origines().isEmpty() ? List.of("*") : n.origines();
        return depot.save(new Widget(n.nom().strip(), n.type(), domaine.id(),
                String.join(",", champs), String.join(",", origines)));
    }

    public void supprimer(UUID id) {
        depot.delete(exiger(id));
    }

    public Donnees donnees(Widget w, String texte, Map<String, String> filtres, int page) {
        Domaine domaine = ontologies.exiger(w.getDomaine());
        Set<String> publies = Arrays.stream(w.getChamps().split(",")).filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        List<Colonne> colonnes = domaine.champs().stream().filter(c -> publies.contains(c.id()))
                .map(c -> new Colonne(c.id(), c.libelle())).toList();

        int taille = "carte".equals(w.getType()) ? 200 : 20;
        ResultatRecherche r = index.chercher(new Recherche(texte, domaine.id(), filtres, page, taille));

        List<Map<String, Object>> entites = r.entites().stream().map(e -> publier(e, publies)).toList();
        // Les facettes sur des champs non publiés révéleraient leurs valeurs : on les écarte.
        List<Facette> facettes = r.facettes().stream()
                .filter(f -> f.champ().equals("commune") || publies.contains(f.champ())).toList();
        return new Donnees(w.getNom(), w.getType(), colonnes, r.total(), entites, facettes);
    }

    public boolean origineAutorisee(Widget w, String origine) {
        List<String> permises = Arrays.stream(w.getOrigines().split(",")).map(String::strip).toList();
        return permises.contains("*") || (origine != null && permises.contains(origine));
    }

    private static Map<String, Object> publier(Entite e, Set<String> publies) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nom", e.nom());
        m.put("commune", e.commune());
        m.put("codePostal", e.codePostal());
        m.put("latitude", e.latitude());
        m.put("longitude", e.longitude());
        Map<String, Object> champs = new LinkedHashMap<>();
        e.champs().forEach((id, v) -> {
            if (publies.contains(id)) {
                champs.put(id, v);
            }
        });
        m.put("champs", champs);
        return m;
    }
}
