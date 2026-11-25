package fr.annuaire.widget;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotWidgets extends JpaRepository<Widget, UUID> {
}
