package com.mamoki.tour.domain.parking.importer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.parking.entity.ParkingLot;
import com.mamoki.tour.domain.parking.entity.ParkingLotSnapshot;
import com.mamoki.tour.domain.parking.repository.ParkingLotRepository;
import com.mamoki.tour.domain.parking.repository.ParkingLotSnapshotRepository;
import com.mamoki.tour.domain.parking.service.ParkingLotSnapshotService;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 읽어낸 강원 주차장을 스냅샷으로 기록한다.
 *
 * <p>성공 기록과 실패 기록은 트랜잭션을 나눈다. 실패를 같은 트랜잭션에 적으면 롤백될 때
 * 이력까지 사라져 무엇이 왜 실패했는지 남지 않는다. 입장객통계 임포터와 같은 구조다.
 */
@Service
public class ParkingCatalogSnapshotWriter {

    private final ParkingLotSnapshotRepository snapshotRepository;
    private final ParkingLotRepository lotRepository;
    private final ParkingLotSnapshotService snapshotService;

    public ParkingCatalogSnapshotWriter(ParkingLotSnapshotRepository snapshotRepository,
                                        ParkingLotRepository lotRepository,
                                        ParkingLotSnapshotService snapshotService) {
        this.snapshotRepository = snapshotRepository;
        this.lotRepository = lotRepository;
        this.snapshotService = snapshotService;
    }

    @Transactional
    public ParkingCatalogImportResult persist(List<ParkingCatalogRow> rows, String sourceName,
                                              LocalDate downloadedOn) {

        LocalDate dataBaseDate = newestDataBaseDate(rows);

        ParkingLotSnapshot snapshot = snapshotRepository.save(ParkingLotSnapshot.builder()
                .version(nextVersion(dataBaseDate, downloadedOn))
                .dataBaseDate(dataBaseDate)
                .downloadedOn(downloadedOn)
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName(sourceName)
                .rowCount(0)
                .build());

        Set<String> districts = new HashSet<>();
        int withCoordinates = 0;

        for (ParkingCatalogRow row : rows) {
            lotRepository.save(ParkingLot.builder()
                    .snapshot(snapshot)
                    .managementNumber(row.managementNumber())
                    .name(row.name())
                    .category(row.category())
                    .parkingType(row.parkingType())
                    .roadAddress(row.roadAddress())
                    .lotAddress(row.lotAddress())
                    .capacity(row.capacity())
                    .operatingDays(row.operatingDays())
                    .weekdayOpenTime(row.weekdayOpenTime())
                    .weekdayCloseTime(row.weekdayCloseTime())
                    .saturdayOpenTime(row.saturdayOpenTime())
                    .saturdayCloseTime(row.saturdayCloseTime())
                    .holidayOpenTime(row.holidayOpenTime())
                    .holidayCloseTime(row.holidayCloseTime())
                    .feeInfo(row.feeInfo())
                    .latitude(row.latitude())
                    .longitude(row.longitude())
                    .dataBaseDate(row.dataBaseDate())
                    .providerCode(row.providerCode())
                    .providerName(row.providerName())
                    .build());

            districts.add(row.providerName());

            if (row.hasCoordinates()) {
                withCoordinates++;
            }
        }

        snapshotService.activate(snapshot);

        return new ParkingCatalogImportResult(snapshot, rows.size(), withCoordinates, districts.size());
    }

    /** 실패 이력은 본 트랜잭션과 분리해 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ParkingLotSnapshot recordFailure(String sourceName, LocalDate downloadedOn, String reason) {
        ParkingLotSnapshot failed = snapshotRepository.save(ParkingLotSnapshot.builder()
                .version(nextVersion(downloadedOn, downloadedOn))
                .dataBaseDate(downloadedOn)
                .downloadedOn(downloadedOn)
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName(sourceName)
                .rowCount(0)
                .build());

        failed.fail(reason);
        return failed;
    }

    /**
     * 파일 하나에 시·군마다 다른 기준일이 섞여 있다. 스냅샷을 대표하는 값으로는 가장 새로운
     * 날을 쓴다. 평균이나 최빈값을 만들면 실제로 존재하지 않는 날짜가 기준일이 된다.
     */
    private static LocalDate newestDataBaseDate(List<ParkingCatalogRow> rows) {
        return rows.stream()
                .map(ParkingCatalogRow::dataBaseDate)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new ParkingCatalogImportException("적재할 행이 없습니다."));
    }

    private String nextVersion(LocalDate dataBaseDate, LocalDate downloadedOn) {
        String base = dataBaseDate + "_" + downloadedOn;

        if (!snapshotRepository.existsByVersion(base)) {
            return base;
        }

        for (int suffix = 2; suffix < 100; suffix++) {
            String candidate = base + "-" + suffix;

            if (!snapshotRepository.existsByVersion(candidate)) {
                return candidate;
            }
        }

        throw new ParkingCatalogImportException("같은 날 같은 기준일을 너무 많이 적재했습니다: " + base);
    }
}
