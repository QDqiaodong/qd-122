package com.spring.transfer.repository;

import com.spring.transfer.common.MeterShift;
import com.spring.transfer.entity.MeterReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {
    boolean existsByReadingNo(String readingNo);

    /** 该产线指定日期是否已有某班次抄录（每条产线每班一条） */
    boolean existsByLineIdAndReadingDateAndShift(Long lineId, LocalDate readingDate, MeterShift shift);

    /** 该产线指定日期某班次抄录（唯一） */
    Optional<MeterReading> findByLineIdAndReadingDateAndShift(Long lineId, LocalDate readingDate, MeterShift shift);

    /** 指定日期全部产线某班次抄录（夜班复核批量判定当天白班是否已抄） */
    List<MeterReading> findByReadingDateAndShift(LocalDate readingDate, MeterShift shift);

    /** 该产线最近一条抄录（按抄表时间、ID 倒序），用于提交时与上一条比对跳变 */
    Optional<MeterReading> findTopByLineIdOrderByReadTimeDescIdDesc(Long lineId);

    /** 该产线全部抄录（抄表时间倒序，最新的在前） */
    List<MeterReading> findByLineIdOrderByReadTimeDescIdDesc(Long lineId);
}
