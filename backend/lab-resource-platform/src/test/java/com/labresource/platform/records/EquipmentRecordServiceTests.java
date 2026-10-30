package com.labresource.platform.records;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.repository.CalibrationRecordRepository;
import com.labresource.platform.calibration.service.CalibrationRecordService;
import com.labresource.platform.certification.entity.CertificationRecord;
import com.labresource.platform.certification.repository.CertificationRecordRepository;
import com.labresource.platform.certification.service.CertificationRecordService;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentRecordServiceTests {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private CalibrationRecordRepository calibrationRepository;

    @Mock
    private CertificationRecordRepository certificationRepository;

    private CalibrationRecordService calibrationService;
    private CertificationRecordService certificationService;
    private Equipment equipment;

    @BeforeEach
    void setUp() {
        calibrationService = new CalibrationRecordService(
                calibrationRepository, equipmentRepository);
        certificationService = new CertificationRecordService(
                certificationRepository, equipmentRepository);
        equipment = new Equipment();
        equipment.setId(21L);
    }

    @Test
    void createCalibrationLinksEquipmentAndNormalizesResult() {
        when(equipmentRepository.findById(21L))
                .thenReturn(Optional.of(equipment));
        when(calibrationRepository.save(any(CalibrationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CalibrationRecord record = new CalibrationRecord();
        record.setCalibrationDate(LocalDate.of(2026, 1, 10));
        record.setNextDueDate(LocalDate.of(2027, 1, 10));
        record.setResult(" pass ");

        CalibrationRecord saved = calibrationService.create(21L, record);

        assertEquals(21L, saved.getEquipmentId());
        assertEquals("PASS", saved.getResult());
        verify(calibrationRepository).save(record);
    }

    @Test
    void calibrationRejectsDueDateBeforeCalibrationDate() {
        when(equipmentRepository.findById(21L))
                .thenReturn(Optional.of(equipment));
        CalibrationRecord record = new CalibrationRecord();
        record.setCalibrationDate(LocalDate.of(2026, 2, 1));
        record.setNextDueDate(LocalDate.of(2026, 1, 31));
        record.setResult("PASS");

        assertThrows(IllegalArgumentException.class,
                () -> calibrationService.create(21L, record));

        verify(calibrationRepository, never()).save(any());
    }

    @Test
    void calibrationRejectsUnknownEquipment() {
        CalibrationRecord record = new CalibrationRecord();
        when(equipmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> calibrationService.create(404L, record));

        verify(calibrationRepository, never()).save(any());
    }

    @Test
    void getCalibrationRecordsQueriesByEquipmentId() {
        when(equipmentRepository.findById(21L))
                .thenReturn(Optional.of(equipment));
        CalibrationRecord newest = new CalibrationRecord();
        newest.setEquipment(equipment);
        newest.setCalibrationDate(LocalDate.of(2026, 2, 1));
        CalibrationRecord older = new CalibrationRecord();
        older.setEquipment(equipment);
        older.setCalibrationDate(LocalDate.of(2026, 1, 1));
        List<CalibrationRecord> expected = List.of(newest, older);
        when(calibrationRepository
                .findByEquipment_IdOrderByCalibrationDateDesc(21L))
                .thenReturn(expected);

        List<CalibrationRecord> records =
                calibrationService.getByEquipment(21L);

        assertEquals(expected, records);
        verify(calibrationRepository)
                .findByEquipment_IdOrderByCalibrationDateDesc(21L);
    }

    @Test
    void createCertificationPersistsValidatedRecord() {
        when(equipmentRepository.findById(21L))
                .thenReturn(Optional.of(equipment));
        when(certificationRepository.save(any(CertificationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CertificationRecord record = new CertificationRecord();
        record.setCertificateType("Electrical Safety");
        record.setIssuingOrganization("  Lab Standards Org  ");
        record.setIssueDate(LocalDate.of(2026, 1, 1));
        record.setExpiryDate(LocalDate.of(2027, 1, 1));

        CertificationRecord saved = certificationService.create(21L, record);

        assertEquals(21L, saved.getEquipmentId());
        assertEquals("Lab Standards Org", saved.getIssuingOrganization());
        verify(certificationRepository).save(record);
    }

    @Test
    void getCertificationRecordsQueriesByEquipmentId() {
        when(equipmentRepository.findById(21L))
                .thenReturn(Optional.of(equipment));
        CertificationRecord newest = new CertificationRecord();
        newest.setEquipment(equipment);
        newest.setIssueDate(LocalDate.of(2026, 2, 1));
        CertificationRecord older = new CertificationRecord();
        older.setEquipment(equipment);
        older.setIssueDate(LocalDate.of(2026, 1, 1));
        List<CertificationRecord> expected = List.of(newest, older);
        when(certificationRepository
                .findByEquipment_IdOrderByIssueDateDesc(21L))
                .thenReturn(expected);

        List<CertificationRecord> records =
                certificationService.getByEquipment(21L);

        assertEquals(expected, records);
        verify(certificationRepository)
                .findByEquipment_IdOrderByIssueDateDesc(21L);
    }

    @Test
    void certificationRejectsExpiryOnOrBeforeIssueDate() {
        when(equipmentRepository.findById(21L))
                .thenReturn(Optional.of(equipment));
        CertificationRecord record = new CertificationRecord();
        record.setCertificateType("Electrical Safety");
        record.setIssuingOrganization("Lab Standards Org");
        record.setIssueDate(LocalDate.of(2026, 1, 1));
        record.setExpiryDate(LocalDate.of(2026, 1, 1));

        assertThrows(IllegalArgumentException.class,
                () -> certificationService.create(21L, record));

        verify(certificationRepository, never()).save(any());
    }
}
