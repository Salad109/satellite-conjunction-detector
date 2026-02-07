package io.salad109.conjunctiondetector.conjunction.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "conjunction")
public class Conjunction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object1_norad_id", nullable = false)
    private Integer object1NoradId;

    @Column(name = "object2_norad_id", nullable = false)
    private Integer object2NoradId;

    @Column(name = "miss_distance_km", nullable = false)
    private double missDistanceKm;

    @Column(name = "tca", nullable = false)
    private OffsetDateTime tca;

    @Column(name = "relative_velocity_m_s", nullable = false)
    private double relativeVelocityMS;

    @Column(name = "collision_probability", nullable = false)
    private double collisionProbability;
}
