package com.labresource.platform.equipment;

import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:live-status-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "jwt.secret=VGhpcyBpcyBhIHRlc3Qgc2VjcmV0IGtleSBmb3IgSldU"
})
@Transactional
class EquipmentLiveStatusRepositoryTests {

    @Autowired
    private EquipmentRepository equipmentRepository;

    private Equipment createEquipment(String name, String assetTag, Long institutionId, String status) {
        Equipment equipment = new Equipment();
        equipment.setName(name);
        equipment.setAssetTag(assetTag);
        equipment.setInstitutionId(institutionId);
        equipment.setQuantity(1);
        equipment.setStatus(status);
        return equipmentRepository.saveAndFlush(equipment);
    }

    @BeforeEach
    void setUp() {
        equipmentRepository.deleteAll();
    }

    @Test
    @DisplayName("Regression: All optional filters null returns all equipment across institutions")
    void returnsAllEquipmentWhenAllOptionalFiltersAreNull() {
        createEquipment("Electron Microscope", "MIC-001", 1L, "AVAILABLE");
        createEquipment("Fluorescence Microscope", "MIC-002", 1L, "BOOKED");
        createEquipment("High Speed Centrifuge", "CEN-001", 1L, "AVAILABLE");
        createEquipment("Thermal Cycler", "PCR-001", 2L, "AVAILABLE");
        createEquipment("Spectrophotometer", null, 1L, "UNDER_MAINTENANCE");

        Page<Equipment> page = equipmentRepository.findForLiveMonitoring(
                null, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(5);
    }

    @Test
    @DisplayName("Regression: Status filter only matches case-insensitively and excludes non-matching statuses")
    void filtersByStatusOnlyCaseInsensitively() {
        createEquipment("Electron Microscope", "MIC-001", 1L, "AVAILABLE");
        createEquipment("Fluorescence Microscope", "MIC-002", 1L, "BOOKED");
        createEquipment("High Speed Centrifuge", "CEN-001", 1L, "AVAILABLE");
        createEquipment("Thermal Cycler", "PCR-001", 2L, "AVAILABLE");
        createEquipment("Spectrophotometer", null, 1L, "UNDER_MAINTENANCE");

        // lowercase status query
        Page<Equipment> availablePage = equipmentRepository.findForLiveMonitoring(
                null, "available", null, PageRequest.of(0, 10));
        assertThat(availablePage.getTotalElements()).isEqualTo(3);
        assertThat(availablePage.getContent())
                .extracting(Equipment::getStatus)
                .containsOnly("AVAILABLE");

        // uppercase status query
        Page<Equipment> bookedPage = equipmentRepository.findForLiveMonitoring(
                null, "BOOKED", null, PageRequest.of(0, 10));
        assertThat(bookedPage.getTotalElements()).isEqualTo(1);
        assertThat(bookedPage.getContent())
                .extracting(Equipment::getAssetTag)
                .containsExactly("MIC-002");
    }

    @Test
    @DisplayName("Regression: Search filter only matches equipment name or asset tag and safely handles null tags")
    void filtersBySearchOnlyMatchingNameOrAssetTag() {
        createEquipment("Electron Microscope", "MIC-001", 1L, "AVAILABLE");
        createEquipment("Fluorescence Microscope", "MIC-002", 1L, "BOOKED");
        createEquipment("High Speed Centrifuge", "CEN-001", 1L, "AVAILABLE");
        createEquipment("Thermal Cycler", "PCR-001", 2L, "AVAILABLE");
        createEquipment("Spectrophotometer", null, 1L, "UNDER_MAINTENANCE");

        // Search by equipment name substring
        Page<Equipment> nameMatch = equipmentRepository.findForLiveMonitoring(
                null, null, "microscope", PageRequest.of(0, 10));
        assertThat(nameMatch.getTotalElements()).isEqualTo(2);
        assertThat(nameMatch.getContent())
                .extracting(Equipment::getAssetTag)
                .containsExactlyInAnyOrder("MIC-001", "MIC-002");

        // Search by asset tag substring
        Page<Equipment> assetTagMatch = equipmentRepository.findForLiveMonitoring(
                null, null, "CEN-001", PageRequest.of(0, 10));
        assertThat(assetTagMatch.getTotalElements()).isEqualTo(1);
        assertThat(assetTagMatch.getContent())
                .extracting(Equipment::getName)
                .containsExactly("High Speed Centrifuge");

        // Search item with null asset tag by name
        Page<Equipment> nullTagMatch = equipmentRepository.findForLiveMonitoring(
                null, null, "spectro", PageRequest.of(0, 10));
        assertThat(nullTagMatch.getTotalElements()).isEqualTo(1);
        assertThat(nullTagMatch.getContent())
                .extracting(Equipment::getName)
                .containsExactly("Spectrophotometer");
    }

    @Test
    @DisplayName("Regression: Status and search filter combined requires both conditions to match")
    void filtersByStatusAndSearchCombined() {
        createEquipment("Electron Microscope", "MIC-001", 1L, "AVAILABLE");
        createEquipment("Fluorescence Microscope", "MIC-002", 1L, "BOOKED");
        createEquipment("High Speed Centrifuge", "CEN-001", 1L, "AVAILABLE");

        Page<Equipment> combinedMatch = equipmentRepository.findForLiveMonitoring(
                null, "AVAILABLE", "microscope", PageRequest.of(0, 10));

        // Electron Microscope matches both AVAILABLE and microscope
        // Fluorescence Microscope matches microscope but is BOOKED (excluded)
        // High Speed Centrifuge matches AVAILABLE but not microscope (excluded)
        assertThat(combinedMatch.getTotalElements()).isEqualTo(1);
        assertThat(combinedMatch.getContent())
                .extracting(Equipment::getAssetTag)
                .containsExactly("MIC-001");
    }

    @Test
    @DisplayName("Regression: Institution-scoped results isolates data to specified institution")
    void filtersByInstitutionScope() {
        createEquipment("Electron Microscope", "MIC-001", 1L, "AVAILABLE");
        createEquipment("Fluorescence Microscope", "MIC-002", 1L, "BOOKED");
        createEquipment("High Speed Centrifuge", "CEN-001", 1L, "AVAILABLE");
        createEquipment("Thermal Cycler", "PCR-001", 2L, "AVAILABLE");
        createEquipment("Spectrophotometer", null, 1L, "UNDER_MAINTENANCE");

        // Scope to institution 1
        Page<Equipment> inst1Page = equipmentRepository.findForLiveMonitoring(
                1L, null, null, PageRequest.of(0, 10));
        assertThat(inst1Page.getTotalElements()).isEqualTo(4);
        assertThat(inst1Page.getContent())
                .extracting(Equipment::getInstitutionId)
                .containsOnly(1L);

        // Scope to institution 2
        Page<Equipment> inst2Page = equipmentRepository.findForLiveMonitoring(
                2L, null, null, PageRequest.of(0, 10));
        assertThat(inst2Page.getTotalElements()).isEqualTo(1);
        assertThat(inst2Page.getContent())
                .extracting(Equipment::getAssetTag)
                .containsExactly("PCR-001");
    }

    @Test
    @DisplayName("Regression: Combined institution, status, and search filters")
    void filtersByInstitutionStatusAndSearchTogether() {
        createEquipment("Electron Microscope Inst 1", "MIC-101", 1L, "AVAILABLE");
        createEquipment("Electron Microscope Inst 2", "MIC-201", 2L, "AVAILABLE");
        createEquipment("Centrifuge Inst 1", "CEN-101", 1L, "AVAILABLE");
        createEquipment("Electron Microscope Booked", "MIC-102", 1L, "BOOKED");

        Page<Equipment> result = equipmentRepository.findForLiveMonitoring(
                1L, "AVAILABLE", "microscope", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(Equipment::getAssetTag)
                .containsExactly("MIC-101");
    }
}
