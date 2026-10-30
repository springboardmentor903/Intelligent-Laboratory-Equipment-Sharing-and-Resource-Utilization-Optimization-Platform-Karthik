package com.labresource.platform.equipment.search;

import com.labresource.platform.equipment.entity.Equipment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentSearchServiceTests {

    @Mock
    private ElasticsearchOperations operations;

    @Mock
    private SearchHits<EquipmentDocument> searchHits;

    private ElasticsearchEquipmentSearchService searchServiceEnabled;
    private ElasticsearchEquipmentSearchService searchServiceDisabled;

    @BeforeEach
    void setUp() {
        searchServiceEnabled = new ElasticsearchEquipmentSearchService(operations, true);
        searchServiceDisabled = new ElasticsearchEquipmentSearchService(operations, false);
    }

    @Test
    void isEnabledReturnsFalseWhenDisabledByProperty() {
        assertFalse(searchServiceDisabled.isEnabled());
        ElasticsearchEquipmentSearchService nullOps = new ElasticsearchEquipmentSearchService(null, true);
        assertFalse(nullOps.isEnabled());
    }

    @Test
    void isEnabledReturnsTrueWhenEnabledAndOperationsPresent() {
        assertTrue(searchServiceEnabled.isEnabled());
    }

    @Test
    void disabledServiceDoesNotIndexOrDeleteOrSearch() {
        Equipment equipment = new Equipment();
        equipment.setId(1L);
        equipment.setName("Centrifuge");

        searchServiceDisabled.indexEquipment(equipment);
        verify(operations, never()).save(any(EquipmentDocument.class));

        searchServiceDisabled.deleteEquipment(1L);
        verify(operations, never()).delete(anyString(), eq(EquipmentDocument.class));

        Optional<List<Long>> result = searchServiceDisabled.searchEquipmentIds(
                "Centrifuge", null, null, null, null, 1L, null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void indexEquipmentMapsAllDomainFieldsAndSaves() {
        Equipment equipment = new Equipment();
        equipment.setId(10L);
        equipment.setName("Fluorescence Spectrometer");
        equipment.setCategory("Spectroscopy");
        equipment.setDescription("High precision spectrometer");
        equipment.setInstitutionId(1L);
        equipment.setDepartmentId(5L);
        equipment.setLabLocation("Room 302");
        equipment.setStatus("AVAILABLE");
        equipment.setQuantity(2);
        equipment.setTags(Set.of("optics", "spectroscopy"));

        searchServiceEnabled.indexEquipment(equipment);

        ArgumentCaptor<EquipmentDocument> captor = ArgumentCaptor.forClass(EquipmentDocument.class);
        verify(operations).save(captor.capture());

        EquipmentDocument doc = captor.getValue();
        assertEquals(10L, doc.getId());
        assertEquals("Fluorescence Spectrometer", doc.getName());
        assertEquals("Spectroscopy", doc.getCategory());
        assertEquals("High precision spectrometer", doc.getDescription());
        assertEquals(1L, doc.getInstitutionId());
        assertEquals(5L, doc.getDepartmentId());
        assertEquals("Room 302", doc.getLabLocation());
        assertEquals("AVAILABLE", doc.getStatus());
        assertEquals(2, doc.getQuantity());
        assertEquals(Set.of("optics", "spectroscopy"), doc.getTags());
    }

    @Test
    void deleteEquipmentDeletesByIdString() {
        searchServiceEnabled.deleteEquipment(99L);
        verify(operations).delete("99", EquipmentDocument.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEquipmentIdsBuildsQueryAndExtractsMatchingIds() {
        SearchHit<EquipmentDocument> hit1 = mock(SearchHit.class);
        SearchHit<EquipmentDocument> hit2 = mock(SearchHit.class);
        EquipmentDocument doc1 = EquipmentDocument.builder().id(101L).build();
        EquipmentDocument doc2 = EquipmentDocument.builder().id(102L).build();

        when(hit1.getContent()).thenReturn(doc1);
        when(hit2.getContent()).thenReturn(doc2);
        when(searchHits.getSearchHits()).thenReturn(List.of(hit1, hit2));
        when(operations.search(any(Query.class), eq(EquipmentDocument.class))).thenReturn(searchHits);

        Optional<List<Long>> matchIds = searchServiceEnabled.searchEquipmentIds(
                "Centrifuge", "Separation", "benchtop", "Lab A", "AVAILABLE", 2L, 7L, "biology");

        assertTrue(matchIds.isPresent());
        assertEquals(List.of(101L, 102L), matchIds.get());
        verify(operations).search(any(Query.class), eq(EquipmentDocument.class));
    }

    @Test
    void searchEquipmentCatchesExceptionAndReturnsEmptyForRelationalFallback() {
        when(operations.search(any(Query.class), eq(EquipmentDocument.class)))
                .thenThrow(new RuntimeException("Elasticsearch cluster connection timeout"));

        Optional<List<Long>> matchIds = searchServiceEnabled.searchEquipmentIds(
                "Oscilloscope", null, null, null, null, null, null, null);

        assertTrue(matchIds.isEmpty());
    }
}
