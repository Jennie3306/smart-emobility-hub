package com.hcmut.emobility.m4parking.repository;

import com.hcmut.emobility.m4parking.domain.ParkingSpace;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {

    List<ParkingSpace> findByHubIdOrderById(Long hubId);

    /** Khoa dong cho do: moi thao tac doi trang thai cua cung mot cho chay lan luot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSpace s where s.id = :id")
    Optional<ParkingSpace> lockById(@Param("id") Long id);

    List<ParkingSpace> findByHubIdAndTypeAndStatusOrderById(Long hubId, ParkingSpace.Type type,
                                                            ParkingSpace.Status status);

    Optional<ParkingSpace> findByChargerPointId(Long chargerPointId);

    Optional<ParkingSpace> findByCurrentReservationId(Long reservationId);

    /** BR-M4-08: the space a rental vehicle stands on. */
    Optional<ParkingSpace> findByDockedVehicleId(Long vehicleId);

    /** BR-M4-01 + BR-M4-08: rental vehicles standing at the Hub take capacity out of every time window. */
    long countByHubIdAndDockedVehicleIdIsNotNull(Long hubId);
}
