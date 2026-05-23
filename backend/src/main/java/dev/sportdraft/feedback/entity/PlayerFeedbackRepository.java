package dev.sportdraft.feedback.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerFeedbackRepository extends JpaRepository<PlayerFeedback, java.util.UUID> {
}
