#define _GNU_SOURCE
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <math.h>
#include <omp.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

typedef struct {
    int satellite_a_idx;
    int satellite_b_idx;
} SatellitePair;

static inline double clamp(double value, double min, double max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
}

static inline double normalize_angle(double angle) {
    while (angle > M_PI) angle -= 2.0 * M_PI;
    while (angle < -M_PI) angle += 2.0 * M_PI;
    return angle;
}

static inline double orbital_radius(double semi_major_axis, double eccentricity, double true_anomaly) {
    double p = semi_major_axis * (1.0 - eccentricity * eccentricity);
    return p / (1.0 + eccentricity * cos(true_anomaly));
}

static inline double compute_relative_inclination(double i_a, double i_b, double delta_raan) {
    double cos_rel_inc = cos(i_a) * cos(i_b) + sin(i_a) * sin(i_b) * cos(delta_raan);
    return acos(clamp(cos_rel_inc, -1.0, 1.0));
}

static inline double compute_alpha_a(double i_a, double i_b, double delta_raan) {
    double sin_i_b = sin(i_b);
    double cos_i_b = cos(i_b);
    double sin_i_a = sin(i_a);
    double cos_i_a = cos(i_a);
    double sin_delta_raan = sin(delta_raan);
    double cos_delta_raan = cos(delta_raan);

    double y = sin_i_b * sin_delta_raan;
    double x = sin_i_a * cos_i_b - cos_i_a * sin_i_b * cos_delta_raan;
    return atan2(y, x);
}

static inline double compute_alpha_b(double i_a, double i_b, double delta_raan) {
    double sin_i_b = sin(i_b);
    double cos_i_b = cos(i_b);
    double sin_i_a = sin(i_a);
    double cos_i_a = cos(i_a);
    double sin_delta_raan = sin(delta_raan);
    double cos_delta_raan = cos(delta_raan);

    double y = -sin_i_a * sin_delta_raan;
    double x = sin_i_b * cos_i_a - cos_i_b * sin_i_a * cos_delta_raan;
    return atan2(y, x);
}

static bool orbital_planes_miss(
    double inclination_a, double raan_a_deg, double arg_perigee_a,
    double e_a, double a_a,
    double inclination_b, double raan_b_deg, double arg_perigee_b,
    double e_b, double a_b,
    double tolerance_km)
{
    double i_a = inclination_a * (M_PI / 180.0);
    double i_b = inclination_b * (M_PI / 180.0);
    double omega_a = arg_perigee_a * (M_PI / 180.0);
    double omega_b = arg_perigee_b * (M_PI / 180.0);
    double delta_raan = (raan_a_deg - raan_b_deg) * (M_PI / 180.0);

    double relative_inclination = compute_relative_inclination(i_a, i_b, delta_raan);
    if (relative_inclination < (0.1 * (M_PI / 180.0))) {
        return false;
    }

    double alpha_a = compute_alpha_a(i_a, i_b, delta_raan);
    double alpha_b = compute_alpha_b(i_a, i_b, delta_raan);

    double nu_a1 = normalize_angle(alpha_a - omega_a);
    double nu_a2 = normalize_angle(alpha_a + M_PI - omega_a);
    double nu_b1 = normalize_angle(alpha_b - omega_b);
    double nu_b2 = normalize_angle(alpha_b + M_PI - omega_b);

    double r_a1 = orbital_radius(a_a, e_a, nu_a1);
    double r_a2 = orbital_radius(a_a, e_a, nu_a2);
    double r_b1 = orbital_radius(a_b, e_b, nu_b1);
    double r_b2 = orbital_radius(a_b, e_b, nu_b2);

    double diff1 = fabs(r_a1 - r_b1);
    double diff2 = fabs(r_a1 - r_b2);
    double diff3 = fabs(r_a2 - r_b1);
    double diff4 = fabs(r_a2 - r_b2);

    double min_diff = fmin(fmin(diff1, diff2), fmin(diff3, diff4));
    return min_diff > tolerance_km;
}

int filter_satellite_pairs(
    int n,
    const double* perigees,
    const double* apogees,
    const int* is_debris,
    const double* inclinations,
    const double* raans,
    const double* arg_perigees,
    const double* eccentricities,
    const double* semi_major_axes,
    double tolerance_km,
    SatellitePair* out_pairs)
{
    // physical cores only
    int num_threads = omp_get_num_procs() / 2;
    if (num_threads < 1) num_threads = 1;
    omp_set_num_threads(num_threads);

    // over-allocate double the survival rate
    long total_pairs = (long) n * (n - 1) / 2;
    int per_thread_cap = (int) (total_pairs / num_threads / 10) + 1024;

    int* thread_counts = (int*) calloc(num_threads, sizeof(int));
    SatellitePair** thread_buffers = (SatellitePair**) malloc(num_threads * sizeof(SatellitePair*));
    for (int t = 0; t < num_threads; t++) {
        thread_buffers[t] = (SatellitePair*) malloc(per_thread_cap * sizeof(SatellitePair));
    }

    #pragma omp parallel
    {
        int tid = omp_get_thread_num();
        SatellitePair* local_buf = thread_buffers[tid];
        int local_count = 0;
        int local_cap = per_thread_cap;

        #pragma omp for schedule(dynamic, 64) nowait
        for (int i = 0; i < n; i++) {
            double perigee_a = perigees[i];
            double apogee_a = apogees[i];
            int debris_a = is_debris[i];

            for (int j = i + 1; j < n; j++) {
                if ((apogee_a + tolerance_km < perigees[j])
                        || (apogees[j] + tolerance_km < perigee_a)
                        || (debris_a && is_debris[j])
                        || orbital_planes_miss(
                            inclinations[i], raans[i], arg_perigees[i],
                            eccentricities[i], semi_major_axes[i],
                            inclinations[j], raans[j], arg_perigees[j],
                            eccentricities[j], semi_major_axes[j],
                            tolerance_km)) {
                    continue;
                }

                if (local_count >= local_cap) {
                    local_cap *= 2;
                    local_buf = (SatellitePair*) realloc(local_buf, local_cap * sizeof(SatellitePair));
                    thread_buffers[tid] = local_buf;
                }

                local_buf[local_count].satellite_a_idx = i;
                local_buf[local_count].satellite_b_idx = j;
                local_count++;
            }
        }

        thread_counts[tid] = local_count;
    }

    // merge
    int pair_count = 0;
    for (int t = 0; t < num_threads; t++) {
        memcpy(out_pairs + pair_count, thread_buffers[t],
               thread_counts[t] * sizeof(SatellitePair));
        pair_count += thread_counts[t];
        free(thread_buffers[t]);
    }
    free(thread_buffers);
    free(thread_counts);

    return pair_count;
}
