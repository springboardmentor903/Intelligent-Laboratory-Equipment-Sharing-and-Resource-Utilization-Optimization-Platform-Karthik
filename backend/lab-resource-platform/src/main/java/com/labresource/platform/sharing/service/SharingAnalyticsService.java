package com.labresource.platform.sharing.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.entity.SharingListing;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.sharing.repository.SharingListingRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SharingAnalyticsService {
    private final SharingAgreementRepository agreements;
    private final BookingRepository bookings;
    private final UtilizationRepository usage;
    private final EquipmentRepository equipment;
    private final InstitutionRepository institutions;
    private final SharingListingRepository listings;
    private final UserRepository users;

    public SharingAnalyticsService(SharingAgreementRepository agreements, BookingRepository bookings,
            UtilizationRepository usage, EquipmentRepository equipment, InstitutionRepository institutions,
            SharingListingRepository listings, UserRepository users) {
        this.agreements = agreements;
        this.bookings = bookings;
        this.usage = usage;
        this.equipment = equipment;
        this.institutions = institutions;
        this.listings = listings;
        this.users = users;
    }

    public Long resolveInstitution(Long requestedId, Authentication auth) {
        boolean system = hasRole(auth, "SYSTEM_ADMIN");
        if (system) {
            if (requestedId == null) throw new IllegalArgumentException("institutionId is required for a system administrator.");
            return requestedId;
        }
        if (!hasRole(auth, "INSTITUTION_ADMIN")) throw new AccessDeniedException("Institution Administrator access is required.");
        User user = users.findByEmail(auth.getName().trim().toLowerCase()).orElseThrow(() -> new AccessDeniedException("User account was not found."));
        if (user.getInstitutionId() == null) throw new AccessDeniedException("Your account has no institution scope.");
        if (requestedId != null && !requestedId.equals(user.getInstitutionId())) throw new AccessDeniedException("You can only view your own institution's sharing analytics.");
        return user.getInstitutionId();
    }

    public void authorizePair(Long firstId, Long secondId, Authentication auth) {
        if (Objects.equals(firstId, secondId)) throw new IllegalArgumentException("Select two different institutions.");
        if (hasRole(auth, "SYSTEM_ADMIN")) return;
        if (!hasRole(auth, "INSTITUTION_ADMIN")) throw new AccessDeniedException("Institution Administrator access is required.");
        User user = users.findByEmail(auth.getName().trim().toLowerCase()).orElseThrow(() -> new AccessDeniedException("User account was not found."));
        if (user.getInstitutionId() == null || (!user.getInstitutionId().equals(firstId) && !user.getInstitutionId().equals(secondId)))
            throw new AccessDeniedException("The report must include your institution.");
    }

    public Map<String, Object> summary(Long institutionId, LocalDate from, LocalDate to) {
        List<SharingAgreement> rows = forInstitution(institutionId);
        Data data = load(rows, institutionId);
        Metrics provided = metrics(rows, data, institutionId, from, to, true);
        Metrics used = metrics(rows, data, institutionId, from, to, false);
        long totalEquipmentMinutes = data.institutionUsage.stream().mapToLong(u -> minutes(u, from, to)).sum();
        long sharedMinutes = provided.minutes;
        long bookingCount = data.bookings.stream().filter(b -> inPeriod(b.getStartTime(), from, to)).count();
        long noShows = data.bookings.stream().filter(b -> inPeriod(b.getStartTime(), from, to) && "NO_SHOW".equalsIgnoreCase(b.getStatus())).count();
        long completedOrNoShow = data.bookings.stream().filter(b -> inPeriod(b.getStartTime(), from, to) && ("COMPLETED".equalsIgnoreCase(b.getStatus()) || "IN_USE".equalsIgnoreCase(b.getStatus()) || "NO_SHOW".equalsIgnoreCase(b.getStatus()))).count();
        long partners = rows.stream().filter(a -> active(a) && !a.getEndDate().isBefore(LocalDateTime.now()))
                .map(a -> counterpart(a, institutionId)).filter(Objects::nonNull).distinct().count();
        long expiring = rows.stream().filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus())
                && !a.getEndDate().isBefore(LocalDateTime.now()) && !a.getEndDate().isAfter(LocalDateTime.now().plusDays(30))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("institutionId", institutionId);
        result.put("hoursProvided", hours(provided.minutes));
        result.put("hoursUsed", hours(used.minutes));
        result.put("feesEarned", money(provided.fees));
        result.put("feesOwed", money(used.fees));
        result.put("expectedFees", money(provided.expected.add(used.expected)));
        result.put("bookingCount", bookingCount);
        result.put("noShowCount", noShows);
        result.put("noShowRate", percent(noShows, completedOrNoShow));
        result.put("activeAgreements", rows.stream().filter(a -> active(a) && !a.getEndDate().isBefore(LocalDateTime.now())).count());
        result.put("partnerCount", partners);
        result.put("agreementsExpiringWithin30Days", expiring);
        result.put("externalShareOfUsage", percent(sharedMinutes, totalEquipmentMinutes));
        result.put("totalEquipmentUsageMinutes", totalEquipmentMinutes);
        result.put("providedMinutes", provided.minutes);
        result.put("usedMinutes", used.minutes);
        return result;
    }

    public List<Map<String, Object>> trends(Long institutionId, LocalDate from, LocalDate to, String grouping) {
        List<SharingAgreement> rows = forInstitution(institutionId);
        Data data = load(rows, institutionId);
        LocalDate cursor = "WEEKLY".equalsIgnoreCase(grouping)
                ? from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : from.withDayOfMonth(1);
        TreeMap<LocalDate, Trend> result = new TreeMap<>();
        while (!cursor.isAfter(to)) {
            result.put(cursor, new Trend());
            cursor = "WEEKLY".equalsIgnoreCase(grouping) ? cursor.plusWeeks(1) : cursor.plusMonths(1);
        }
        Map<Long, SharingAgreement> agreementById = rows.stream().collect(Collectors.toMap(SharingAgreement::getId, a -> a, (a, b) -> a));
        for (Booking booking : data.bookings) {
            SharingAgreement agreement = agreementById.get(booking.getSharingAgreementId());
            if (agreement == null) continue;
            boolean provided = agreement.getOwnerInstitutionId().equals(institutionId);
            for (Utilization log : data.usageByBooking.getOrDefault(booking.getId(), List.of())) {
                LocalDateTime logEnd = log.getEndTime() == null ? LocalDateTime.now() : log.getEndTime();
                LocalDateTime begin = max(log.getStartTime(), from.atStartOfDay());
                LocalDateTime end = min(logEnd, to.plusDays(1).atStartOfDay());
                for (LocalDate day = begin.toLocalDate(); end.isAfter(begin) && day.atStartOfDay().isBefore(end); day = day.plusDays(1)) {
                    LocalDate bucket = "WEEKLY".equalsIgnoreCase(grouping)
                            ? day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : day.withDayOfMonth(1);
                    Trend trend = result.get(bucket);
                    if (trend == null) continue;
                    long part = Duration.between(max(begin, day.atStartOfDay()), min(end, day.plusDays(1).atStartOfDay())).toMinutes();
                    if (part <= 0) continue;
                    BigDecimal partFee = rawFee(part, agreement.getFeePerHour());
                    if (provided) { trend.provided += part; trend.earned = trend.earned.add(partFee); }
                    else { trend.used += part; trend.owed = trend.owed.add(partFee); }
                }
            }
        }
        return result.entrySet().stream().map(entry -> {
            Trend value = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("periodStart", entry.getKey());
            row.put("hoursProvided", hours(value.provided));
            row.put("hoursUsed", hours(value.used));
            row.put("feesEarned", money(value.earned));
            row.put("feesOwed", money(value.owed));
            row.put("providedMinutes", value.provided);
            row.put("usedMinutes", value.used);
            return row;
        }).toList();
    }

    public Map<String, Object> partners(Long institutionId, LocalDate from, LocalDate to, int page, int size, String sort, String direction) {
        List<SharingAgreement> rows = forInstitution(institutionId);
        Data data = load(rows, institutionId);
        Map<Long, List<SharingAgreement>> grouped = rows.stream().collect(Collectors.groupingBy(a -> counterpart(a, institutionId)));
        List<Map<String, Object>> content = new ArrayList<>();
        grouped.forEach((partnerId, partnerAgreements) -> {
            if (partnerId == null) return;
            Metrics provided = metrics(partnerAgreements, data, institutionId, from, to, true);
            Metrics used = metrics(partnerAgreements, data, institutionId, from, to, false);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partnerInstitutionId", partnerId);
            row.put("partnerInstitution", data.institutionNames.getOrDefault(partnerId, "Institution #" + partnerId));
            row.put("hoursProvided", hours(provided.minutes));
            row.put("hoursUsed", hours(used.minutes));
            row.put("feesEarned", money(provided.fees));
            row.put("feesOwed", money(used.fees));
            row.put("balance", money(provided.fees.subtract(used.fees)));
            row.put("agreementCount", partnerAgreements.size());
            content.add(row);
        });
        Comparator<Map<String, Object>> comparator = partnerComparator(sort);
        content.sort(comparator);
        if ("desc".equalsIgnoreCase(direction)) Collections.reverse(content);
        return page(content, page, size);
    }

    public Map<String, Object> partnershipReport(Long firstId, Long secondId, LocalDate from, LocalDate to, String grouping) {
        List<SharingAgreement> pair = new ArrayList<>();
        pair.addAll(agreements.findByOwnerInstitutionIdAndPartnerInstitutionIdIn(firstId, List.of(secondId)));
        pair.addAll(agreements.findByOwnerInstitutionIdAndPartnerInstitutionIdIn(secondId, List.of(firstId)));
        Data data = load(pair, null);
        Map<String, Object> firstToSecond = direction(pair, data, firstId, secondId, from, to);
        Map<String, Object> secondToFirst = direction(pair, data, secondId, firstId, from, to);
        BigDecimal balance = ((BigDecimal) firstToSecond.get("feesEarned"))
                .subtract((BigDecimal) secondToFirst.get("feesEarned"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("firstInstitutionId", firstId);
        result.put("firstInstitution", data.institutionNames.getOrDefault(firstId, "Institution #" + firstId));
        result.put("secondInstitutionId", secondId);
        result.put("secondInstitution", data.institutionNames.getOrDefault(secondId, "Institution #" + secondId));
        result.put("firstToSecond", firstToSecond);
        result.put("secondToFirst", secondToFirst);
        result.put("balanceForFirstInstitution", money(balance));
        result.put("bookingCount", data.bookings.stream().filter(b -> inPeriod(b.getStartTime(), from, to)).count());
        long noShows = data.bookings.stream().filter(b -> inPeriod(b.getStartTime(), from, to) && "NO_SHOW".equalsIgnoreCase(b.getStatus())).count();
        long completedOrNoShow = data.bookings.stream().filter(b -> inPeriod(b.getStartTime(), from, to) && ("COMPLETED".equalsIgnoreCase(b.getStatus()) || "IN_USE".equalsIgnoreCase(b.getStatus()) || "NO_SHOW".equalsIgnoreCase(b.getStatus()))).count();
        result.put("noShowCount", noShows);
        result.put("noShowRate", percent(noShows, completedOrNoShow));
        result.put("monthlyTrends", trendsForAgreements(pair, data, firstId, from, to));
        result.put("agreements", pair.stream().map(a -> agreementRow(a, data)).toList());
        return result;
    }

    public Map<String, Object> mostSharedEquipment(Long institutionId, LocalDate from, LocalDate to, int page,
            int size, String sort, String direction) {
        List<SharingAgreement> rows = forInstitution(institutionId);
        Data data = load(rows, institutionId);
        Map<Long, SharingAgreement> agreementById = rows.stream().collect(Collectors.toMap(SharingAgreement::getId, a -> a, (a, b) -> a));
        Map<Long, EquipmentUsage> grouped = new HashMap<>();
        for (Booking booking : data.bookings) {
            SharingAgreement agreement = agreementById.get(booking.getSharingAgreementId());
            if (agreement == null || !agreement.getOwnerInstitutionId().equals(institutionId)) continue;
            for (Utilization log : data.usageByBooking.getOrDefault(booking.getId(), List.of())) {
                long minutes = minutes(log, from, to);
                if (minutes == 0) continue;
                EquipmentUsage value = grouped.computeIfAbsent(agreement.getEquipmentId(), id -> new EquipmentUsage());
                value.minutes += minutes;
                value.fees = value.fees.add(fee(minutes, agreement.getFeePerHour()));
                value.share = data.sharePercent.getOrDefault(agreement.getEquipmentId(), BigDecimal.ZERO);
            }
        }
        List<Map<String, Object>> content = grouped.entrySet().stream().map(entry -> {
            Equipment item = data.equipmentById.get(entry.getKey());
            EquipmentUsage value = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("equipmentId", entry.getKey());
            row.put("equipmentName", item == null ? "Equipment #" + entry.getKey() : item.getName());
            row.put("assetTag", item == null ? null : item.getAssetTag());
            row.put("providedMinutes", value.minutes);
            row.put("feesEarned", money(value.fees));
            row.put("externalSharePercentage", value.share);
            return row;
        }).collect(Collectors.toCollection(ArrayList::new));
        Comparator<Map<String, Object>> comparator = equipmentComparator(sort);
        content.sort(comparator);
        if ("desc".equalsIgnoreCase(direction)) Collections.reverse(content);
        return page(content, page, size);
    }

    private Map<String, Object> direction(List<SharingAgreement> all, Data data, Long owner, Long partner, LocalDate from, LocalDate to) {
        List<SharingAgreement> selected = all.stream().filter(a -> a.getOwnerInstitutionId().equals(owner) && a.getPartnerInstitutionId().equals(partner)).toList();
        Metrics provided = metrics(selected, data, owner, from, to, true);
        Metrics used = metrics(selected, data, partner, from, to, false);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ownerInstitutionId", owner); row.put("partnerInstitutionId", partner);
        row.put("hoursProvided", hours(provided.minutes)); row.put("hoursUsed", hours(used.minutes));
        row.put("feesEarned", money(provided.fees)); row.put("feesOwed", money(used.fees));
        row.put("balance", money(provided.fees.subtract(used.fees)));
        row.put("bookingCount", countBookings(selected, data, from, to));
        row.put("noShowRate", noShowRate(selected, data, from, to));
        return row;
    }

    private List<Map<String, Object>> trendsForAgreements(List<SharingAgreement> rows, Data data, Long firstInstitutionId, LocalDate from, LocalDate to) {
        Map<Long, SharingAgreement> byId = rows.stream().collect(Collectors.toMap(SharingAgreement::getId, a -> a, (a,b)->a));
        LocalDate cursor = from.withDayOfMonth(1);
        List<Map<String, Object>> out = new ArrayList<>();
        while (!cursor.isAfter(to)) {
            LocalDate monthEnd = cursor.plusMonths(1).minusDays(1);
            long provided = 0, used = 0; BigDecimal earned = BigDecimal.ZERO, owed = BigDecimal.ZERO;
            for (Booking booking : data.bookings) {
                SharingAgreement agreement = byId.get(booking.getSharingAgreementId());
                if (agreement == null) continue;
                for (Utilization log : data.usageByBooking.getOrDefault(booking.getId(), List.of())) {
                    long minutes = minutes(log, cursor.isBefore(from) ? from : cursor, monthEnd.isAfter(to) ? to : monthEnd);
                    if (minutes == 0) continue;
                    if (agreement.getOwnerInstitutionId().equals(firstInstitutionId)) {
                        provided += minutes; earned = earned.add(rawFee(minutes, agreement.getFeePerHour()));
                    } else {
                        used += minutes; owed = owed.add(rawFee(minutes, agreement.getFeePerHour()));
                    }
                }
            }
            Map<String,Object> value = new LinkedHashMap<>(); value.put("periodStart", cursor);
            value.put("hoursProvided", hours(provided)); value.put("hoursUsed", hours(used));
            value.put("feesEarned", money(earned)); value.put("feesOwed", money(owed)); out.add(value);
            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    private Map<String, Object> agreementRow(SharingAgreement a, Data data) {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("agreementId", a.getId()); row.put("equipmentId", a.getEquipmentId());
        Equipment item = data.equipmentById.get(a.getEquipmentId());
        row.put("equipmentName", item == null ? null : item.getName());
        row.put("ownerInstitutionId", a.getOwnerInstitutionId()); row.put("partnerInstitutionId", a.getPartnerInstitutionId());
        row.put("ownerInstitution", data.institutionNames.get(a.getOwnerInstitutionId()));
        row.put("partnerInstitution", data.institutionNames.get(a.getPartnerInstitutionId()));
        row.put("startDate", a.getStartDate()); row.put("endDate", a.getEndDate());
        row.put("status", "REVOKED".equalsIgnoreCase(a.getStatus()) ? "REVOKED" : a.getEndDate().isBefore(LocalDateTime.now()) ? "EXPIRED" : "ACTIVE");
        row.put("feePerHour", a.getFeePerHour()); return row;
    }

    private List<SharingAgreement> forInstitution(Long institutionId) {
        Map<Long, SharingAgreement> found = new LinkedHashMap<>();
        agreements.findByOwnerInstitutionId(institutionId).forEach(a -> found.put(a.getId(), a));
        agreements.findByPartnerInstitutionId(institutionId).forEach(a -> found.put(a.getId(), a));
        return new ArrayList<>(found.values());
    }

    private Data load(List<SharingAgreement> rows, Long institutionUsageOwnerId) {
        Data data = new Data();
        if (rows.isEmpty()) return data;
        List<Long> agreementIds = rows.stream().map(SharingAgreement::getId).toList();
        data.bookings = bookings.findBySharingAgreementIdIn(agreementIds);
        List<Long> bookingIds = data.bookings.stream().map(Booking::getId).toList();
        List<Utilization> logs = bookingIds.isEmpty() ? List.of() : usage.findByBookingIdIn(bookingIds);
        data.usageByBooking = logs.stream().collect(Collectors.groupingBy(Utilization::getBookingId));
        List<Long> equipmentIds = rows.stream().map(SharingAgreement::getEquipmentId).distinct().toList();
        equipment.findAllById(equipmentIds).forEach(e -> data.equipmentById.put(e.getId(), e));
        List<Long> institutionIds = rows.stream().flatMap(a -> java.util.stream.Stream.of(a.getOwnerInstitutionId(), a.getPartnerInstitutionId())).distinct().toList();
        institutions.findAllById(institutionIds).forEach(i -> data.institutionNames.put(i.getId(), i.getName()));
        listings.findByEquipmentIdIn(equipmentIds).forEach(l -> data.sharePercent.put(l.getEquipmentId(), l.getExternalSharePercentage()));
        List<Long> ownedEquipmentIds = data.equipmentById.values().stream().filter(e -> institutionUsageOwnerId == null
                        ? rows.stream().anyMatch(a -> a.getOwnerInstitutionId().equals(e.getInstitutionId()))
                        : institutionUsageOwnerId.equals(e.getInstitutionId()))
                .map(Equipment::getId).distinct().toList();
        if (!ownedEquipmentIds.isEmpty()) data.institutionUsage = usage.findByEquipmentIdIn(ownedEquipmentIds);
        return data;
    }

    private Metrics metrics(List<SharingAgreement> rows, Data data, Long institutionId, LocalDate from, LocalDate to, boolean provided) {
        Set<Long> ids = rows.stream().filter(a -> provided ? a.getOwnerInstitutionId().equals(institutionId) : a.getPartnerInstitutionId().equals(institutionId))
                .map(SharingAgreement::getId).collect(Collectors.toSet());
        Metrics value = new Metrics();
        Map<Long, SharingAgreement> byId = rows.stream().filter(a -> ids.contains(a.getId())).collect(Collectors.toMap(SharingAgreement::getId, a -> a, (a,b)->a));
        for (Booking booking : data.bookings) {
            SharingAgreement agreement = byId.get(booking.getSharingAgreementId());
            if (agreement == null) continue;
            List<Utilization> logs = data.usageByBooking.getOrDefault(booking.getId(), List.of());
            for (Utilization log : logs) {
                long usedMinutes = minutes(log, from, to);
                value.minutes += usedMinutes;
                value.fees = value.fees.add(fee(usedMinutes, agreement.getFeePerHour()));
            }
            if (Set.of("PENDING", "PENDING_APPROVAL", "APPROVED", "CONFIRMED").contains(upper(booking.getStatus()))
                    && inPeriod(booking.getStartTime(), from, to)) {
                value.expected = value.expected.add(fee(Duration.between(booking.getStartTime(), booking.getEndTime()).toMinutes(), agreement.getFeePerHour()));
            }
        }
        return value;
    }

    private long countBookings(List<SharingAgreement> rows, Data data, LocalDate from, LocalDate to) {
        Set<Long> ids = rows.stream().map(SharingAgreement::getId).collect(Collectors.toSet());
        return data.bookings.stream().filter(b -> ids.contains(b.getSharingAgreementId()) && inPeriod(b.getStartTime(), from, to)).count();
    }

    private double noShowRate(List<SharingAgreement> rows, Data data, LocalDate from, LocalDate to) {
        Set<Long> ids = rows.stream().map(SharingAgreement::getId).collect(Collectors.toSet());
        List<Booking> selected = data.bookings.stream().filter(b -> ids.contains(b.getSharingAgreementId()) && inPeriod(b.getStartTime(), from, to)).toList();
        long noShows = selected.stream().filter(b -> "NO_SHOW".equalsIgnoreCase(b.getStatus())).count();
        long completedOrNoShow = selected.stream().filter(b -> "COMPLETED".equalsIgnoreCase(b.getStatus()) || "IN_USE".equalsIgnoreCase(b.getStatus()) || "NO_SHOW".equalsIgnoreCase(b.getStatus())).count();
        return percent(noShows, completedOrNoShow);
    }

    private Long counterpart(SharingAgreement a, Long institutionId) {
        if (a.getOwnerInstitutionId().equals(institutionId)) return a.getPartnerInstitutionId();
        if (a.getPartnerInstitutionId().equals(institutionId)) return a.getOwnerInstitutionId();
        return null;
    }
    private boolean hasRole(Authentication auth, String role) { return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role)); }
    private boolean active(SharingAgreement a) { return "ACTIVE".equalsIgnoreCase(a.getStatus()); }
    private boolean inPeriod(LocalDateTime at, LocalDate from, LocalDate to) { return at != null && !at.isBefore(from.atStartOfDay()) && at.isBefore(to.plusDays(1).atStartOfDay()); }
    private long minutes(Utilization log, LocalDate from, LocalDate to) {
        LocalDateTime end = log.getEndTime() == null ? LocalDateTime.now() : log.getEndTime();
        LocalDateTime start = max(log.getStartTime(), from.atStartOfDay()); end = min(end, to.plusDays(1).atStartOfDay());
        return end.isAfter(start) ? Duration.between(start, end).toMinutes() : 0;
    }
    private LocalDateTime max(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }
    private LocalDateTime min(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }
    private BigDecimal fee(long minutes, BigDecimal rate) { return rawFee(minutes, rate); }
    private BigDecimal rawFee(long minutes, BigDecimal rate) { return BigDecimal.valueOf(minutes).multiply(rate == null ? BigDecimal.ZERO : rate).divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP); }
    private BigDecimal money(BigDecimal amount) { return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP); }
    private double hours(long minutes) { return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).doubleValue(); }
    private double percent(long numerator, long denominator) { return denominator == 0 ? 0 : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP).doubleValue(); }
    private String upper(String value) { return value == null ? "" : value.toUpperCase(Locale.ROOT); }
    private Map<String,Object> page(List<Map<String,Object>> values, int page, int size) {
        int safeSize = Math.max(1, Math.min(100, size)), safePage = Math.max(0, page);
        int from = Math.min(values.size(), safePage * safeSize), to = Math.min(values.size(), from + safeSize);
        Map<String,Object> result = new LinkedHashMap<>(); result.put("content", values.subList(from, to));
        result.put("page", safePage); result.put("size", safeSize); result.put("totalElements", values.size());
        result.put("totalPages", (int) Math.ceil(values.size() / (double) safeSize)); return result;
    }
    private Comparator<Map<String,Object>> partnerComparator(String sort) {
        String field = Set.of("partnerInstitution", "hoursProvided", "hoursUsed", "feesEarned", "feesOwed", "balance", "agreementCount").contains(sort) ? sort : "hoursProvided";
        return (left, right) -> compare(left.get(field), right.get(field));
    }
    private Comparator<Map<String,Object>> equipmentComparator(String sort) {
        String field = Set.of("equipmentName", "providedMinutes", "feesEarned", "externalSharePercentage").contains(sort) ? sort : "providedMinutes";
        return (left, right) -> compare(left.get(field), right.get(field));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compare(Object left, Object right) {
        if (left == right) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        if (left instanceof Number a && right instanceof Number b)
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) return comparable.compareTo(right);
        return left.toString().compareToIgnoreCase(right.toString());
    }

    private static final class Metrics { long minutes; BigDecimal fees = BigDecimal.ZERO; BigDecimal expected = BigDecimal.ZERO; }
    private static final class Trend { long provided; long used; BigDecimal earned = BigDecimal.ZERO; BigDecimal owed = BigDecimal.ZERO; }
    private static final class EquipmentUsage { long minutes; BigDecimal fees = BigDecimal.ZERO; BigDecimal share = BigDecimal.ZERO; }
    private static final class Data {
        List<Booking> bookings = List.of();
        List<Utilization> institutionUsage = List.of();
        Map<Long,List<Utilization>> usageByBooking = new HashMap<>();
        Map<Long,Equipment> equipmentById = new HashMap<>();
        Map<Long,String> institutionNames = new HashMap<>();
        Map<Long,BigDecimal> sharePercent = new HashMap<>();
    }
}
