package com.codewithsavage.majipoa.rider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByIdAndRiderId(Long id, Long riderId);

    Optional<Delivery> findFirstByRiderIdAndStatusAndOfferExpiresAtAfterOrderByOfferedAtDesc(
            Long riderId, DeliveryStatus status, LocalDateTime now);

    Optional<Delivery> findFirstByRiderIdAndStatusInOrderByAcceptedAtDesc(
            Long riderId, Collection<DeliveryStatus> statuses);

    List<Delivery> findByRiderIdAndStatusAndDeliveredAtBetweenOrderByDeliveredAtDesc(
            Long riderId, DeliveryStatus status, LocalDateTime from, LocalDateTime to);

    List<Delivery> findTop50ByRiderIdAndStatusAndRatingIsNotNullOrderByDeliveredAtDesc(
            Long riderId, DeliveryStatus status);

    long countByRiderIdAndStatus(Long riderId, DeliveryStatus status);

    long countByRiderIdAndStatusAndOnTimeTrue(Long riderId, DeliveryStatus status);

    @Query("select avg(d.rating) from Delivery d where d.rider.id = :riderId and d.rating is not null")
    Double averageRating(@Param("riderId") Long riderId);

    @Query("""
            select d from Delivery d
            where d.rider.id = :riderId
              and d.status = :status
              and d.deliveredAt >= :from
              and (lower(d.orderNumber) like lower(concat('%', :q, '%'))
                   or lower(d.vendorName) like lower(concat('%', :q, '%'))
                   or lower(coalesce(d.dropoffArea, '')) like lower(concat('%', :q, '%')))
            order by d.deliveredAt desc
            """)
    List<Delivery> searchHistory(@Param("riderId") Long riderId,
                                 @Param("status") DeliveryStatus status,
                                 @Param("from") LocalDateTime from,
                                 @Param("q") String q);

    @Modifying(clearAutomatically = true)
    @Query("update Delivery d set d.status = :expired where d.status = :offered and d.offerExpiresAt < :now")
    int expireOverdue(@Param("expired") DeliveryStatus expired,
                      @Param("offered") DeliveryStatus offered,
                      @Param("now") LocalDateTime now);

    // ---- admin riders page ----
    long countByRiderIdAndStatusAndDeliveredAtBetween(Long riderId, DeliveryStatus status,
                                                      LocalDateTime from, LocalDateTime to);

    long countByStatusAndDeliveredAtBetween(DeliveryStatus status, LocalDateTime from, LocalDateTime to);

    long countByStatusAndOnTimeFalseAndDeliveredAtBetween(DeliveryStatus status,
                                                          LocalDateTime from, LocalDateTime to);

    long countByStatusAndOfferedAtBetween(DeliveryStatus status, LocalDateTime from, LocalDateTime to);

    long countByStatusIn(Collection<DeliveryStatus> statuses);

    List<Delivery> findTop100ByRiderIdAndStatusOrderByDeliveredAtDesc(Long riderId, DeliveryStatus status);

    Optional<Delivery> findFirstByRiderIdAndDropoffAreaIsNotNullOrderByOfferedAtDesc(Long riderId);

    @Query("select avg(d.durationMin) from Delivery d "
         + "where d.status = :status and d.deliveredAt >= :from and d.deliveredAt < :to")
    Double averageDurationMinutes(@Param("status") DeliveryStatus status,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);
                                  // ASSUMPTION: Delivery has rider, status (DeliveryStatus) and deliveredAt (LocalDateTime)
List<Delivery> findByRiderIdAndStatusAndDeliveredAtBetween(
        Long riderId, DeliveryStatus status, LocalDateTime from, LocalDateTime to);
}