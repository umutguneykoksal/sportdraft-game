package com.example.auction.simulation.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.bidding.entity.PlayerAttributes;
import com.example.auction.bidding.entity.Position;
import org.junit.jupiter.api.Test;

class PlayerStrengthTest {

    @Test
    void forwardAttackEmphasizesShooting() {
        PlayerAttributes striker = new PlayerAttributes(85, 92, 70, 45, 80);
        PlayerAttributes defender = new PlayerAttributes(70, 45, 65, 90, 85);

        assertThat(PlayerStrength.attack(Position.FWD, striker))
            .isGreaterThan(PlayerStrength.attack(Position.FWD, defender));
    }

    @Test
    void goalkeeperDefenseBeatsOutfieldDefendingProfile() {
        PlayerAttributes gk = PlayerAttributes.fromOverall(Position.GK, 88);
        PlayerAttributes def = PlayerAttributes.fromOverall(Position.DEF, 88);

        assertThat(PlayerStrength.defense(Position.GK, gk))
            .isGreaterThan(PlayerStrength.defense(Position.DEF, def));
    }
}
