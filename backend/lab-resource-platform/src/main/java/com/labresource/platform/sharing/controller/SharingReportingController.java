package com.labresource.platform.sharing.controller;

import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.booking.waitlist.repository.WaitlistRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.repository.*;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/sharing")
public class SharingReportingController {
    private final SharingAgreementRepository agreements;
    private final SharingAccessGrantRepository grants;
    private final SharingListingRepository listings;
    private final BookingRepository bookings;
    private final WaitlistRepository waitlist;
    private final UtilizationRepository usage;
    private final EquipmentRepository equipment;
    private final EquipmentOperatingHoursRepository hours;
    private final InstitutionRepository institutions;
    private final UserRepository users;

    public SharingReportingController(SharingAgreementRepository agreements, SharingAccessGrantRepository grants,
            SharingListingRepository listings, BookingRepository bookings, WaitlistRepository waitlist,
            UtilizationRepository usage, EquipmentRepository equipment, EquipmentOperatingHoursRepository hours,
            InstitutionRepository institutions, UserRepository users) {
        this.agreements=agreements; this.grants=grants; this.listings=listings; this.bookings=bookings;
        this.waitlist=waitlist; this.usage=usage; this.equipment=equipment; this.hours=hours;
        this.institutions=institutions; this.users=users;
    }

    @GetMapping("/agreements/{id}/fees")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> fees(@PathVariable Long id, @RequestParam(required=false) LocalDate from,
                                  @RequestParam(required=false) LocalDate to, Authentication auth) {
        SharingAgreement agreement=agreements.findById(id).orElseThrow();
        var user=users.findByEmail(auth.getName().trim().toLowerCase()).orElseThrow();
        boolean system=hasRole(auth,"SYSTEM_ADMIN");
        boolean institutionAdmin=hasRole(auth,"INSTITUTION_ADMIN")
                && (Objects.equals(user.getInstitutionId(),agreement.getOwnerInstitutionId())
                    || Objects.equals(user.getInstitutionId(),agreement.getPartnerInstitutionId()));
        if (!system && !institutionAdmin && grants.findByAgreementIdAndUserId(id,user.getId()).isEmpty())
            return ResponseEntity.status(403).body(Map.of("message","You cannot view these usage fees."));
        List<Map<String,Object>> rows=new ArrayList<>(); long bookedTotal=0,usedTotal=0;
        BigDecimal estimatedTotal=BigDecimal.ZERO,actualTotal=BigDecimal.ZERO;
        List<com.labresource.platform.booking.entity.Booking> agreementBookings=bookings.findBySharingAgreementId(id);
        List<Long> bookingIds=agreementBookings.stream().map(com.labresource.platform.booking.entity.Booking::getId).toList();
        Map<Long,List<com.labresource.platform.utilization.entity.Utilization>> usageByBooking=bookingIds.isEmpty()?Map.of():usage.findByBookingIdIn(bookingIds).stream().collect(java.util.stream.Collectors.groupingBy(com.labresource.platform.utilization.entity.Utilization::getBookingId));
        for(var booking:agreementBookings) {
            LocalDate bookingDate=booking.getStartTime().toLocalDate();
            if((from!=null&&bookingDate.isBefore(from))||(to!=null&&bookingDate.isAfter(to))) continue;
            long booked=Duration.between(booking.getStartTime(),booking.getEndTime()).toMinutes();
            long used=usageByBooking.getOrDefault(booking.getId(),List.of()).stream().filter(u->u.getEndTime()!=null)
                    .mapToLong(u->Duration.between(u.getStartTime(),u.getEndTime()).toMinutes()).sum();
            BigDecimal estimate=fee(booked,agreement.getFeePerHour());
            BigDecimal actual="COMPLETED".equals(booking.getStatus())?fee(used,agreement.getFeePerHour()):null;
            Map<String,Object> row=new LinkedHashMap<>(); row.put("bookingId",booking.getId());
            row.put("bookingDate",booking.getStartTime()); row.put("bookedMinutes",booked); row.put("usedMinutes",used);
            row.put("estimatedFee",estimate); row.put("actualFee",actual); rows.add(row);
            row.put("totalFee",actual==null?estimate:actual);
            bookedTotal+=booked; usedTotal+=used; estimatedTotal=estimatedTotal.add(estimate);
            if(actual!=null) actualTotal=actualTotal.add(actual);
        }
        Map<String,Object> response=new LinkedHashMap<>(); response.put("agreementId",id);
        response.put("feePerHour",agreement.getFeePerHour()); response.put("bookedMinutes",bookedTotal);
        response.put("usedMinutes",usedTotal); response.put("estimatedFee",estimatedTotal);
        response.put("actualFee",actualTotal); response.put("bookings",rows);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/equipment/{id}/schedule")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> schedule(@PathVariable Long id,@RequestParam LocalDate from,@RequestParam LocalDate to,
                                      Authentication auth) {
        Equipment item=equipment.findById(id).orElseThrow();
        var viewer=users.findByEmail(auth.getName().trim().toLowerCase()).orElseThrow();
        boolean manager=Objects.equals(viewer.getInstitutionId(),item.getInstitutionId())
                && (hasRole(auth,"LAB_MANAGER")||hasRole(auth,"INSTITUTION_ADMIN")||hasRole(auth,"SYSTEM_ADMIN"));
        LocalDateTime start=from.atStartOfDay(),end=to.plusDays(1).atStartOfDay();
        List<Map<String,Object>> slots=new ArrayList<>(); long externalMinutes=0;
        List<com.labresource.platform.booking.entity.Booking> visibleBookings=bookings.findOverlappingBookingsForEquipment(id,start,end);
        List<Long> agreementIds=visibleBookings.stream().map(b->b.getSharingAgreementId()).filter(Objects::nonNull).distinct().toList();
        Map<Long,SharingAgreement> agreementById=new HashMap<>();agreements.findAllById(agreementIds).forEach(a->agreementById.put(a.getId(),a));
        List<Long> partnerIds=agreementById.values().stream().map(SharingAgreement::getPartnerInstitutionId).distinct().toList();
        Map<Long,String> partnerNames=new HashMap<>();institutions.findAllById(partnerIds).forEach(i->partnerNames.put(i.getId(),i.getName()));
        for(var booking:visibleBookings) {
            if(!List.of("PENDING","PENDING_APPROVAL","APPROVED","CONFIRMED","IN_USE","COMPLETED").contains(booking.getStatus())) continue;
            boolean external=booking.getSharingAgreementId()!=null;
            Map<String,Object> slot=new LinkedHashMap<>(); slot.put("start",booking.getStartTime());
            slot.put("end",booking.getEndTime()); slot.put("type",external?"EXTERNAL":"INTERNAL");
            if(external) {
                LocalDateTime s=booking.getStartTime().isAfter(start)?booking.getStartTime():start;
                LocalDateTime e=booking.getEndTime().isBefore(end)?booking.getEndTime():end;
                externalMinutes+=Duration.between(s,e).toMinutes();
                if(manager) { SharingAgreement agreement=agreementById.get(booking.getSharingAgreementId());
                    if(agreement!=null)slot.put("partnerInstitution",partnerNames.getOrDefault(agreement.getPartnerInstitutionId(),"Partner institution")); }
            }
            slots.add(slot);
        }
        for(var hold:waitlist.findActiveOffersOverlapping(id,start,end,LocalDateTime.now())) {
            Map<String,Object> slot=new LinkedHashMap<>();slot.put("start",hold.getRequestedStartTime());
            slot.put("end",hold.getRequestedEndTime());slot.put("type","INTERNAL");slot.put("hold",true);slots.add(slot);
        }
        var hoursForItem=hours.findByEquipmentIdOrderByDayOfWeekAsc(id);
        Map<Integer,com.labresource.platform.equipment.entity.EquipmentOperatingHours> hoursByDay=new HashMap<>();hoursForItem.forEach(h->hoursByDay.put(h.getDayOfWeek(),h));
        long daily=hoursForItem.stream().filter(h->!Boolean.TRUE.equals(h.getIsClosed()))
                .mapToLong(h->Duration.between(h.getOpenTime(),h.getCloseTime()).toMinutes()).sum();
        if(daily==0) daily=7*8*60L;
        LocalDate monday=from.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime weekStart=monday.atStartOfDay(),weekEnd=monday.plusDays(7).atStartOfDay();
        externalMinutes=bookings.findOverlappingBookingsForEquipment(id,weekStart,weekEnd).stream()
                .filter(b->b.getSharingAgreementId()!=null&&List.of("PENDING","PENDING_APPROVAL","APPROVED","CONFIRMED","IN_USE","COMPLETED").contains(b.getStatus()))
                .mapToLong(b->operatingMinutes(b.getStartTime(),b.getEndTime(),weekStart,weekEnd,hoursByDay)).sum();
        BigDecimal share=listings.findByEquipmentId(id).map(x->x.getExternalSharePercentage()).orElse(BigDecimal.ZERO);
        long weeklyLimit=BigDecimal.valueOf(daily*Math.max(1,item.getQuantity()==null?1:item.getQuantity()))
                .multiply(share).divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN).longValue();
        List<Map<String,Object>> allowed=new ArrayList<>();List<Long> viewerAgreementIds=grants.findByUserId(viewer.getId()).stream().filter(g->"ACTIVE".equals(g.getStatus())).map(g->g.getAgreementId()).distinct().toList();
        for(SharingAgreement a:agreements.findAllById(viewerAgreementIds)) if(a.getEquipmentId().equals(id)
                    &&"ACTIVE".equals(a.getStatus())&&!a.getEndDate().isBefore(LocalDateTime.now())) {
                Map<String,Object> window=new LinkedHashMap<>(); window.put("start",a.getStartDate());
                window.put("end",a.getEndDate()); window.put("allowedStartTime",a.getAllowedStartTime());
                window.put("allowedEndTime",a.getAllowedEndTime()); allowed.add(window);
        }
        Map<String,Object> result=new LinkedHashMap<>(); result.put("equipmentId",id); result.put("from",from);
        result.put("to",to); result.put("slots",slots); result.put("allowedWindows",allowed);
        result.put("externalMinutesUsed",externalMinutes); result.put("weeklyExternalShareLimit",weeklyLimit);
        result.put("externalSharePercentage",share); return ResponseEntity.ok(result);
    }

    @GetMapping("/equipment/{id}/holds")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> activeHolds(@PathVariable Long id,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        LocalDateTime start=from.atStartOfDay(),end=to.plusDays(1).atStartOfDay();
        return ResponseEntity.ok(waitlist.findActiveOffersOverlapping(id,start,end,LocalDateTime.now()).stream()
                .map(h->Map.of("start",h.getRequestedStartTime(),"end",h.getRequestedEndTime(),"type","INTERNAL","hold",true)).toList());
    }

    private BigDecimal fee(long minutes,BigDecimal rate) {
        return BigDecimal.valueOf(minutes).multiply(rate).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP);
    }
    private long operatingMinutes(LocalDateTime start,LocalDateTime end,LocalDateTime windowStart,LocalDateTime windowEnd,
                                  Map<Integer,com.labresource.platform.equipment.entity.EquipmentOperatingHours> byDay) {
        LocalDateTime from=start.isAfter(windowStart)?start:windowStart,until=end.isBefore(windowEnd)?end:windowEnd;
        long total=0;
        for(LocalDate day=from.toLocalDate();!day.isAfter(until.toLocalDate());day=day.plusDays(1)) {
            var hours=byDay.get(day.getDayOfWeek().getValue());
            if(hours!=null&&Boolean.TRUE.equals(hours.getIsClosed()))continue;
            LocalTime open=hours==null?LocalTime.of(9,0):hours.getOpenTime();
            LocalTime close=hours==null?LocalTime.of(17,0):hours.getCloseTime();
            LocalDateTime openAt=day.atTime(open),closeAt=day.atTime(close);
            LocalDateTime a=from.isAfter(openAt)?from:openAt,b=until.isBefore(closeAt)?until:closeAt;
            if(b.isAfter(a))total+=Duration.between(a,b).toMinutes();
        }
        return total;
    }
    private boolean hasRole(Authentication auth,String role) {
        return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));
    }
}
