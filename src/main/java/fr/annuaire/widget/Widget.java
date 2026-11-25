package fr.annuaire.widget;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * La configuration d'un widget, enregistrée en PostgreSQL.
 * Un administrateur la crée dans l'interface ; le site partenaire n'a qu'une balise à poser.
 */
@Entity
@Table(name = "widget")
public class Widget {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String nom;

    /** « annuaire » ou « carte ». */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 80)
    private String domaine;

    /** Identifiants des champs à afficher, séparés par des virgules. */
    @Column(nullable = false, length = 1000)
    private String champs = "";

    /** Sites autorisés à afficher le widget, séparés par des virgules. « * » : tous. */
    @Column(nullable = false, length = 1000)
    private String origines = "*";

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected Widget() {
    }

    public Widget(String nom, String type, String domaine, String champs, String origines) {
        this.id = UUID.randomUUID();
        this.nom = nom;
        this.type = type;
        this.domaine = domaine;
        this.champs = champs;
        this.origines = origines;
        this.creeLe = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getType() {
        return type;
    }

    public String getDomaine() {
        return domaine;
    }

    public String getChamps() {
        return champs;
    }

    public String getOrigines() {
        return origines;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
